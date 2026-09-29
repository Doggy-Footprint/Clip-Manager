package com.doggy.clip_manager.core.data.operation

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.GZIPOutputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.XZOutputStream

/**
 * Independent test fixture builders: produce archives from scratch with third-party
 * libraries (zip4j / commons-compress / xz), never by round-tripping through
 * ArchiveRepository. Expected names/bytes for V5/V6/V7/V8 assertions come from the
 * `entries` maps passed in here, not from the implementation under test.
 */
object ArchiveFixtures {

    fun buildZip(target: File, entries: Map<String, ByteArray>, password: CharArray? = null): File {
        val zipFile = if (password != null) ZipFile(target, password) else ZipFile(target)
        entries.forEach { (path, bytes) ->
            val params = ZipParameters().apply {
                fileNameInZip = path
                if (password != null) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }
            zipFile.addStream(bytes.inputStream(), params)
        }
        return target
    }

    fun buildTarBytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(out).use { tar ->
            entries.forEach { (path, bytes) ->
                val entry = TarArchiveEntry(path)
                entry.size = bytes.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        return out.toByteArray()
    }

    fun buildTar(target: File, entries: Map<String, ByteArray>): File {
        target.writeBytes(buildTarBytes(entries))
        return target
    }

    fun buildTarGz(target: File, entries: Map<String, ByteArray>): File {
        val tarBytes = buildTarBytes(entries)
        GZIPOutputStream(target.outputStream()).use { it.write(tarBytes) }
        return target
    }

    fun buildTarXz(target: File, entries: Map<String, ByteArray>): File {
        val tarBytes = buildTarBytes(entries)
        XZOutputStream(target.outputStream(), org.tukaani.xz.LZMA2Options()).use { it.write(tarBytes) }
        return target
    }

    fun build7z(target: File, entries: Map<String, ByteArray>): File {
        SevenZOutputFile(target).use { sevenZ ->
            entries.forEach { (path, bytes) ->
                val entry = SevenZArchiveEntry()
                entry.name = path
                entry.size = bytes.size.toLong()
                sevenZ.putArchiveEntry(entry)
                sevenZ.write(bytes)
                sevenZ.closeArchiveEntry()
            }
        }
        return target
    }

    /** Truncates a valid archive's tail so its central directory / footer is unreadable. */
    fun corrupt(target: File) {
        RandomAccessFile(target, "rw").use { raf ->
            val newLength = (raf.length() - minOf(32L, raf.length() / 2)).coerceAtLeast(4L)
            raf.setLength(newLength)
        }
    }

    /**
     * Builds a ZIP whose single entry claims compression method 98 (PPMd) in both the
     * local file header and the central directory header. Neither java.util.zip nor
     * zip4j implement PPMd decompression, so any reader must surface this as an
     * unsupported-method failure rather than silently succeeding or crashing.
     */
    fun buildUnsupportedMethodZip(target: File, entryName: String = "unsupported.bin"): File {
        val payload = "unsupported-method-payload".toByteArray()
        java.util.zip.ZipOutputStream(target.outputStream()).use { zos ->
            val entry = java.util.zip.ZipEntry(entryName)
            entry.method = java.util.zip.ZipEntry.STORED
            entry.size = payload.size.toLong()
            entry.compressedSize = payload.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(payload)
            entry.crc = crc.value
            zos.putNextEntry(entry)
            zos.write(payload)
            zos.closeEntry()
        }
        patchZipMethodField(target, entryName, unsupportedMethod = 98)
        return target
    }

    private fun patchZipMethodField(target: File, entryName: String, unsupportedMethod: Int) {
        val bytes = target.readBytes()
        val nameBytes = entryName.toByteArray()
        // Local file header signature 0x04034b50 (little-endian on disk: 50 4b 03 04),
        // method field is 2 bytes at offset 8 within the header.
        val localSig = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val localIndex = indexOf(bytes, localSig, 0)
        require(localIndex >= 0) { "local file header not found in fixture" }
        bytes[localIndex + 8] = (unsupportedMethod and 0xFF).toByte()
        bytes[localIndex + 9] = ((unsupportedMethod shr 8) and 0xFF).toByte()

        // Central directory file header signature 0x02014b50, method field at offset 10.
        val centralSig = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val centralIndex = indexOf(bytes, centralSig, localIndex)
        require(centralIndex >= 0) { "central directory header not found in fixture" }
        bytes[centralIndex + 10] = (unsupportedMethod and 0xFF).toByte()
        bytes[centralIndex + 11] = ((unsupportedMethod shr 8) and 0xFF).toByte()

        target.writeBytes(bytes)
        check(indexOf(bytes, entryName.toByteArray(), 0) >= 0) { "sanity: entry name must remain present" }
        check(nameBytes.isNotEmpty())
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int): Int {
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
