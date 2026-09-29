package com.doggy.clip_manager.core.data.operation

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException as Zip4jException
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import org.apache.commons.compress.archivers.ArchiveEntry as CompressArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class RawArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val symlinkTarget: String? = null,
)

internal data class WritableArchiveEntry(val path: String, val isDirectory: Boolean, val bytes: ByteArray?)

internal enum class ArchiveFormat { ZIP, SEVEN_Z, TAR, TAR_GZ, TAR_XZ }

internal object ArchiveFormats {
    fun detect(fileName: String): ArchiveFormat? {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".zip") || name.endsWith(".cbz") -> ArchiveFormat.ZIP
            name.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            name.endsWith(".tar.gz") || name.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            name.endsWith(".tar.xz") -> ArchiveFormat.TAR_XZ
            name.endsWith(".tar") -> ArchiveFormat.TAR
            else -> null
        }
    }

    fun handlerFor(format: ArchiveFormat): ArchiveFormatHandler = when (format) {
        ArchiveFormat.ZIP -> ZipFormatHandler
        ArchiveFormat.SEVEN_Z -> SevenZFormatHandler
        ArchiveFormat.TAR -> TarFormatHandler(TarCompression.NONE)
        ArchiveFormat.TAR_GZ -> TarFormatHandler(TarCompression.GZIP)
        ArchiveFormat.TAR_XZ -> TarFormatHandler(TarCompression.XZ)
    }
}

internal interface ArchiveFormatHandler {
    fun readEntries(archive: File, password: CharArray?): List<RawArchiveEntry>
    fun isEncrypted(archive: File): Boolean
    fun readBytes(archive: File, path: String, password: CharArray?): ByteArray
    fun write(target: File, entries: List<WritableArchiveEntry>)

    /** Stored CRC32 where the container records one, or a streamed CRC32 otherwise; null if the entry is absent. */
    fun crc(archive: File, path: String, password: CharArray?): Long?
}

private fun streamedCrc32(bytes: ByteArray): Long {
    val crc = java.util.zip.CRC32()
    crc.update(bytes)
    return crc.value
}

private fun zip4jFailure(e: Zip4jException): ArchiveException {
    val typeName = e.type?.name.orEmpty()
    return ArchiveException(
        when {
            typeName.contains("PASSWORD") -> ArchiveFailure.WRONG_PASSWORD
            typeName.contains("UNSUPPORT") -> ArchiveFailure.UNSUPPORTED_METHOD
            else -> ArchiveFailure.CORRUPT
        },
    )
}

/** Compression method codes zip4j can actually decode: STORE, DEFLATE, and AES (encrypted entries). */
private val SUPPORTED_ZIP_METHODS = setOf(0, 8, 99)

private const val EOCD_SIGNATURE = 0x06054b50
private const val CEN_SIGNATURE = 0x02014b50
private const val EOCD_FIXED_SIZE = 22
private const val CEN_FIXED_SIZE = 46
private const val MAX_COMMENT_SIZE = 65535

private fun readLeShort(buffer: ByteArray, offset: Int): Int =
    (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

private fun readLeInt(buffer: ByteArray, offset: Int): Int =
    (buffer[offset].toInt() and 0xFF) or
        ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
        ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
        ((buffer[offset + 3].toInt() and 0xFF) shl 24)

private fun readLeUInt(buffer: ByteArray, offset: Int): Long = readLeInt(buffer, offset).toLong() and 0xFFFFFFFFL

/**
 * Both JDK's `java.util.zip.ZipFile` and zip4j refuse to open a central directory that names an
 * unrecognized compression method (e.g. PPMd=98) at all, so neither can be used to detect the
 * method: the method byte is read straight out of each central directory file header instead
 * (signature 0x02014b50, method at offset 10), independent of either library. ZIP64 records
 * aren't handled since the fixtures this guards against are small, single-disk archives; such a
 * structure falls through to null and the existing zip4j-based CORRUPT handling takes over.
 */
private fun readCentralDirectoryMethods(archive: File): List<Int>? =
    java.io.RandomAccessFile(archive, "r").use { raf ->
        val fileLength = raf.length()
        if (fileLength < EOCD_FIXED_SIZE) return null
        val scanSize = minOf(fileLength, (EOCD_FIXED_SIZE + MAX_COMMENT_SIZE).toLong()).toInt()
        val tail = ByteArray(scanSize)
        raf.seek(fileLength - scanSize)
        raf.readFully(tail)
        var eocdOffset = -1
        for (i in tail.size - EOCD_FIXED_SIZE downTo 0) {
            if (readLeInt(tail, i) == EOCD_SIGNATURE) {
                eocdOffset = i
                break
            }
        }
        if (eocdOffset < 0) return null
        val totalEntries = readLeShort(tail, eocdOffset + 10)
        val centralDirOffset = readLeUInt(tail, eocdOffset + 16)
        if (centralDirOffset < 0 || centralDirOffset >= fileLength) return null
        val methods = mutableListOf<Int>()
        raf.seek(centralDirOffset)
        val header = ByteArray(CEN_FIXED_SIZE)
        repeat(totalEntries) {
            raf.readFully(header)
            if (readLeInt(header, 0) != CEN_SIGNATURE) return null
            methods += readLeShort(header, 10)
            val nameLen = readLeShort(header, 28)
            val extraLen = readLeShort(header, 30)
            val commentLen = readLeShort(header, 32)
            raf.skipBytes(nameLen + extraLen + commentLen)
        }
        methods
    }

private fun rejectUnsupportedZipMethod(archive: File) {
    try {
        val methods = readCentralDirectoryMethods(archive) ?: return
        if (methods.any { it !in SUPPORTED_ZIP_METHODS }) {
            throw ArchiveException(ArchiveFailure.UNSUPPORTED_METHOD)
        }
    } catch (e: ArchiveException) {
        throw e
    } catch (e: Exception) {
        // Unparseable structure: the zip4j path below reports CORRUPT instead.
    }
}

internal object ZipFormatHandler : ArchiveFormatHandler {
    override fun readEntries(archive: File, password: CharArray?): List<RawArchiveEntry> {
        rejectUnsupportedZipMethod(archive)
        val zipFile = try {
            ZipFile(archive)
        } catch (e: Exception) {
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }
        val headers: List<FileHeader> = try {
            zipFile.fileHeaders
        } catch (e: Zip4jException) {
            throw zip4jFailure(e)
        } catch (e: Exception) {
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }
        val encrypted = headers.any { it.isEncrypted }
        if (encrypted) {
            if (password == null) throw ArchiveException(ArchiveFailure.PASSWORD_REQUIRED)
            val sample = headers.firstOrNull { !it.isDirectory }
            if (sample != null) {
                try {
                    zipFile.setPassword(password)
                    zipFile.getInputStream(sample).use { it.readBytes() }
                } catch (e: Zip4jException) {
                    throw zip4jFailure(e)
                } catch (e: Exception) {
                    throw ArchiveException(ArchiveFailure.CORRUPT)
                }
            }
        }
        return headers.map { RawArchiveEntry(it.fileName.trimEnd('/'), it.isDirectory, it.uncompressedSize) }
    }

    override fun isEncrypted(archive: File): Boolean = try {
        ZipFile(archive).fileHeaders.any { it.isEncrypted }
    } catch (e: Exception) {
        false
    }

    override fun readBytes(archive: File, path: String, password: CharArray?): ByteArray {
        rejectUnsupportedZipMethod(archive)
        val zipFile = try {
            ZipFile(archive)
        } catch (e: Exception) {
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }
        val header = try {
            zipFile.getFileHeader(path)
        } catch (e: Exception) {
            null
        } ?: throw ArchiveException(ArchiveFailure.CORRUPT)
        if (header.isEncrypted) {
            if (password == null) throw ArchiveException(ArchiveFailure.PASSWORD_REQUIRED)
            zipFile.setPassword(password)
        }
        return try {
            zipFile.getInputStream(header).use { it.readBytes() }
        } catch (e: Zip4jException) {
            throw zip4jFailure(e)
        } catch (e: Exception) {
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }
    }

    override fun write(target: File, entries: List<WritableArchiveEntry>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { out ->
            for (entry in entries) {
                if (entry.isDirectory) {
                    out.putNextEntry(ZipEntry(entry.path.trimEnd('/') + "/"))
                    out.closeEntry()
                } else {
                    out.putNextEntry(ZipEntry(entry.path))
                    entry.bytes?.let { out.write(it) }
                    out.closeEntry()
                }
            }
        }
    }

    override fun crc(archive: File, path: String, password: CharArray?): Long? = try {
        ZipFile(archive).getFileHeader(path)?.crc
    } catch (e: Exception) {
        null
    }
}

internal object SevenZFormatHandler : ArchiveFormatHandler {
    private fun openRaw(archive: File, password: CharArray?): SevenZFile {
        val builder = SevenZFile.builder().setFile(archive)
        if (password != null) builder.setPassword(password)
        return try {
            builder.get()
        } catch (e: Exception) {
            val message = e.message.orEmpty().lowercase()
            when {
                password == null && (message.contains("password") || message.contains("encrypt")) ->
                    throw ArchiveException(ArchiveFailure.PASSWORD_REQUIRED)
                password != null && (message.contains("password") || message.contains("checksum") || message.contains("crc")) ->
                    throw ArchiveException(ArchiveFailure.WRONG_PASSWORD)
                else -> throw ArchiveException(ArchiveFailure.CORRUPT)
            }
        }
    }

    /**
     * `-mhe=off` leaves the header unencrypted, so opening with a missing or wrong password
     * succeeds; only decoding an entry's content stream actually detects the mismatch. Runs on a
     * disposable probe instance so it never disturbs the cursor the real caller reads from.
     */
    private fun verifyContentPassword(archive: File, password: CharArray?) {
        val probe = openRaw(archive, password)
        try {
            var entry = probe.nextEntry
            while (entry != null) {
                if (entry.hasStream()) {
                    try {
                        probe.read(ByteArray(1))
                    } catch (e: Exception) {
                        throw if (password == null) {
                            ArchiveException(ArchiveFailure.PASSWORD_REQUIRED)
                        } else {
                            ArchiveException(ArchiveFailure.WRONG_PASSWORD)
                        }
                    }
                    return
                }
                entry = probe.nextEntry
            }
        } finally {
            probe.close()
        }
    }

    private fun open(archive: File, password: CharArray?): SevenZFile {
        verifyContentPassword(archive, password)
        return openRaw(archive, password)
    }

    override fun readEntries(archive: File, password: CharArray?): List<RawArchiveEntry> =
        open(archive, password).use { file ->
            file.entries.map { RawArchiveEntry(it.name.trimEnd('/'), it.isDirectory, if (it.hasStream()) it.size else 0L) }
        }

    override fun isEncrypted(archive: File): Boolean = try {
        open(archive, null).close()
        false
    } catch (e: ArchiveException) {
        e.failure == ArchiveFailure.PASSWORD_REQUIRED
    }

    override fun readBytes(archive: File, path: String, password: CharArray?): ByteArray =
        open(archive, password).use { file ->
            var entry = file.nextEntry
            while (entry != null) {
                if (entry.name.trimEnd('/') == path) {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = file.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                    return@use out.toByteArray()
                }
                entry = file.nextEntry
            }
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }

    override fun write(target: File, entries: List<WritableArchiveEntry>) {
        SevenZOutputFile(target).use { out ->
            for (entry in entries) {
                val archiveEntry = SevenZArchiveEntry()
                archiveEntry.name = if (entry.isDirectory) entry.path.trimEnd('/') + "/" else entry.path
                archiveEntry.isDirectory = entry.isDirectory
                if (!entry.isDirectory) archiveEntry.size = (entry.bytes?.size ?: 0).toLong()
                out.putArchiveEntry(archiveEntry)
                if (!entry.isDirectory) entry.bytes?.let { out.write(it) }
                out.closeArchiveEntry()
            }
        }
    }

    override fun crc(archive: File, path: String, password: CharArray?): Long? = try {
        open(archive, password).use { file ->
            var entry = file.nextEntry
            while (entry != null) {
                if (entry.name.trimEnd('/') == path) {
                    return@use if (entry.hasCrc) entry.crcValue else streamedCrc32(readBytes(archive, path, password))
                }
                entry = file.nextEntry
            }
            null
        }
    } catch (e: Exception) {
        null
    }
}

internal enum class TarCompression { NONE, GZIP, XZ }

internal class TarFormatHandler(private val compression: TarCompression) : ArchiveFormatHandler {
    private fun input(archive: File): java.io.InputStream {
        val base = BufferedInputStream(FileInputStream(archive))
        return when (compression) {
            TarCompression.NONE -> base
            TarCompression.GZIP -> GzipCompressorInputStream(base)
            TarCompression.XZ -> XZCompressorInputStream(base)
        }
    }

    private fun output(target: File): java.io.OutputStream {
        val base = BufferedOutputStream(FileOutputStream(target))
        return when (compression) {
            TarCompression.NONE -> base
            TarCompression.GZIP -> GzipCompressorOutputStream(base)
            TarCompression.XZ -> XZCompressorOutputStream(base)
        }
    }

    override fun readEntries(archive: File, password: CharArray?): List<RawArchiveEntry> = try {
        TarArchiveInputStream(input(archive)).use { tar ->
            val entries = mutableListOf<RawArchiveEntry>()
            var entry: TarArchiveEntry? = tar.nextTarEntry
            while (entry != null) {
                entries += RawArchiveEntry(
                    path = entry.name.trimEnd('/'),
                    isDirectory = entry.isDirectory,
                    size = entry.size,
                    symlinkTarget = if (entry.isSymbolicLink) entry.linkName else null,
                )
                entry = tar.nextTarEntry
            }
            entries
        }
    } catch (e: ArchiveException) {
        throw e
    } catch (e: Exception) {
        throw ArchiveException(ArchiveFailure.CORRUPT)
    }

    override fun isEncrypted(archive: File): Boolean = false

    override fun readBytes(archive: File, path: String, password: CharArray?): ByteArray = try {
        TarArchiveInputStream(input(archive)).use { tar ->
            var entry: TarArchiveEntry? = tar.nextTarEntry
            while (entry != null) {
                if (entry.name.trimEnd('/') == path) return@use tar.readBytes()
                entry = tar.nextTarEntry
            }
            throw ArchiveException(ArchiveFailure.CORRUPT)
        }
    } catch (e: ArchiveException) {
        throw e
    } catch (e: Exception) {
        throw ArchiveException(ArchiveFailure.CORRUPT)
    }

    override fun write(target: File, entries: List<WritableArchiveEntry>) {
        TarArchiveOutputStream(output(target)).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
            for (entry in entries) {
                val name = if (entry.isDirectory) entry.path.trimEnd('/') + "/" else entry.path
                val tarEntry = TarArchiveEntry(name)
                if (!entry.isDirectory) tarEntry.size = (entry.bytes?.size ?: 0).toLong()
                tar.putArchiveEntry(tarEntry)
                if (!entry.isDirectory) entry.bytes?.let { tar.write(it) }
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
    }

    override fun crc(archive: File, path: String, password: CharArray?): Long? = try {
        streamedCrc32(readBytes(archive, path, password))
    } catch (e: Exception) {
        null
    }
}
