package com.doggy.clip_manager.core.editor

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class VideoEditor(
    private val context: Context,
    private val timeModel: ExpectedTimeModel,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(spec: EditSpec): EditJob {
        EditPlanner.validateRanges(spec.keepRanges)

        val probeResult = runCatching { MediaProbe.probe(spec.inputPath) }
        val keepRangesResult = probeResult.mapCatching { probe ->
            EditPlanner.normalizeKeepRanges(probe.durationUs, spec.keepRanges)
        }
        // InvalidRangeException/EmptyEditException must surface synchronously, but only once the
        // real duration is known; InputNotReadableException is instead delivered via job state.
        if (probeResult.isSuccess) {
            keepRangesResult.getOrThrow()
        }

        val job = RunningEditJob(UUID.randomUUID().toString(), spec)
        EditJobs.setCurrent(job)
        job.begin(probeResult, keepRangesResult)
        return job
    }

    private inner class RunningEditJob(
        override val id: String,
        override val spec: EditSpec,
    ) : EditJob {
        private val _state = MutableStateFlow<EditState>(EditState.Idle)
        override val state: StateFlow<EditState> = _state.asStateFlow()
        private var coroutineJob: Job? = null

        override fun cancel() {
            coroutineJob?.cancel()
            // A Job cancelled before the dispatcher ever runs its body completes without
            // executing the try/catch below, so the state would otherwise be stuck at Idle (C42).
            _state.compareAndSet(EditState.Idle, EditState.Cancelled)
        }

        fun begin(probeResult: Result<ProbeResult>, keepRangesResult: Result<List<TimeRange>>) {
            coroutineJob = scope.launch {
                val startMs = clock()
                val tempDir = File(context.cacheDir, "editor_job_$id")
                var outputFile: File? = null
                try {
                    ensureActive()
                    val probe = probeResult.getOrElse { cause ->
                        if (cause is EditException) throw cause
                        throw InputNotReadableException("cannot read input: ${spec.inputPath}", cause)
                    }
                    val baseKeepRanges = keepRangesResult.getOrThrow()
                    val effectiveMode = EditPlanner.effectiveCutMode(spec.cutMode, spec.hasEffects)
                    val keepRanges = if (effectiveMode == CutMode.FAST) {
                        val keyframes = MediaProbe.videoKeyframesUs(spec.inputPath, probe.videoTrackIndex)
                        EditPlanner.snapToKeyframes(baseKeepRanges, keyframes)
                    } else {
                        baseKeepRanges
                    }

                    val outputDurationUs = EditPlanner.outputDurationUs(keepRanges)
                    val expectedMs = timeModel.expectedMs(effectiveMode, outputDurationUs, probe.width, probe.height)
                    val slowDetector = SlowDetector(expectedMs, startMs)

                    _state.value = EditState.Running(0f, SlowState.NORMAL)

                    tempDir.mkdirs()
                    outputFile = OutputNaming.outputFileFor(File(spec.inputPath), clock(), zone) { it.exists() }
                    val output = outputFile

                    val onProgress: suspend (Float) -> Unit = { fraction ->
                        val slowState = slowDetector.onProgress(clock(), fraction)
                        _state.value = EditState.Running(fraction, slowState)
                    }

                    when (effectiveMode) {
                        CutMode.FAST -> FastMuxEditor.edit(
                            spec.inputPath,
                            keepRanges,
                            output.path,
                            tempDir,
                            spec.concatStrategy,
                            onProgress,
                        )
                        CutMode.PRECISE -> PreciseTransformEditor.edit(
                            context,
                            spec.inputPath,
                            keepRanges,
                            output.path,
                            tempDir,
                            spec.concatStrategy,
                            onProgress,
                        )
                    }
                    ensureActive()

                    val elapsedMs = clock() - startMs
                    timeModel.record(effectiveMode, outputDurationUs, probe.width, probe.height, elapsedMs)
                    Log.i(BENCH_TAG, "strategy=${spec.concatStrategy} mode=$effectiveMode elapsedMs=$elapsedMs")
                    _state.value = EditState.Completed(output.path, elapsedMs)
                } catch (e: CancellationException) {
                    outputFile?.delete()
                    _state.value = EditState.Cancelled
                } catch (e: EditException) {
                    outputFile?.delete()
                    _state.value = EditState.Failed(e)
                } catch (e: Exception) {
                    outputFile?.delete()
                    _state.value = EditState.Failed(EncodingFailedException(e))
                } finally {
                    tempDir.deleteRecursively()
                }
            }
        }
    }

    private companion object {
        const val BENCH_TAG = "EditorBench"
    }
}
