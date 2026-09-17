package com.doggy.clip_manager.core.editor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Keeps an edit running while the app is minimized (I6): a foreground service with a progress notification. */
@UnstableApi
class EditService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var videoEditor: VideoEditor? = null
    private var currentJob: EditJob? = null
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        videoEditor = VideoEditor(
            applicationContext,
            ExpectedTimeModel(
                SharedPreferencesCoefficientStore(applicationContext),
                mapOf(CutMode.FAST to DEFAULT_FAST_MS_PER_SECOND, CutMode.PRECISE to DEFAULT_PRECISE_MS_PER_SECOND),
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            currentJob?.cancel()
            return START_NOT_STICKY
        }

        val spec = intent?.let(::decodeStartSpec) ?: return START_NOT_STICKY
        val job = try {
            videoEditor?.start(spec) ?: return START_NOT_STICKY
        } catch (_: InvalidEffectException) {
            return START_NOT_STICKY
        }
        currentJob = job

        startForeground(NOTIFICATION_ID, buildNotification(EditState.Running(0f, SlowState.NORMAL)))

        observerJob?.cancel()
        observerJob = serviceScope.launch {
            job.state.collect { state ->
                if (state is EditState.Completed || state is EditState.Failed || state is EditState.Cancelled) {
                    // Leave foreground and remove the progress notification (C41); no point
                    // posting a terminal-state notification just to remove it immediately after.
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: EditState): Notification {
        val progressPercent = ((state as? EditState.Running)?.progress ?: 0f).let { (it * 100).toInt() }
        val cancelIntent = Intent(this, EditService::class.java).setAction(ACTION_CANCEL)
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val cancelAction = Notification.Action.Builder(
            0,
            getString(R.string.editor_notification_cancel),
            cancelPendingIntent,
        ).build()
        return builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.editor_notification_title))
            .setContentText(getString(R.string.editor_notification_progress, progressPercent))
            .setOngoing(state is EditState.Running)
            .setProgress(100, progressPercent, false)
            .addAction(cancelAction)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.editor_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_CANCEL = "com.doggy.clip_manager.core.editor.action.CANCEL"
        const val NOTIFICATION_ID = 4271

        private const val CHANNEL_ID = "editor_progress"
        private const val DEFAULT_FAST_MS_PER_SECOND = 150.0
        private const val DEFAULT_PRECISE_MS_PER_SECOND = 1500.0

        private const val EXTRA_INPUT_PATH = "input_path"
        private const val EXTRA_STARTS = "starts"
        private const val EXTRA_ENDS = "ends"
        private const val EXTRA_CUT_MODE = "cut_mode"
        private const val EXTRA_CONCAT_STRATEGY = "concat_strategy"
        internal const val EXTRA_FRAME_WIDTH = "frame_width"
        internal const val EXTRA_FRAME_HEIGHT = "frame_height"
        internal const val EXTRA_FRAME_MODE = "frame_mode"
        internal const val EXTRA_CROP_X = "crop_x"
        internal const val EXTRA_CROP_Y = "crop_y"
        internal const val EXTRA_FLIP_STARTS = "flip_starts"
        internal const val EXTRA_FLIP_ENDS = "flip_ends"
        internal const val EXTRA_FLIP_HORIZONTAL = "flip_horizontal"
        internal const val EXTRA_FLIP_VERTICAL = "flip_vertical"
        internal const val EXTRA_SPEED_STARTS = "speed_starts"
        internal const val EXTRA_SPEED_ENDS = "speed_ends"
        internal const val EXTRA_SPEED_VALUES = "speed_values"

        internal fun encodeStartIntent(context: Context, spec: EditSpec): Intent =
            Intent(context, EditService::class.java).apply {
                putExtra(EXTRA_INPUT_PATH, spec.inputPath)
                putExtra(EXTRA_STARTS, spec.keepRanges.map { it.startUs }.toLongArray())
                putExtra(EXTRA_ENDS, spec.keepRanges.map { it.endUs }.toLongArray())
                putExtra(EXTRA_CUT_MODE, spec.cutMode.name)
                putExtra(EXTRA_CONCAT_STRATEGY, spec.concatStrategy.name)
                when (val layout = spec.effects.frameLayout) {
                    FrameLayout.Original -> Unit
                    is FrameLayout.Ratio -> {
                        putExtra(EXTRA_FRAME_WIDTH, layout.width)
                        putExtra(EXTRA_FRAME_HEIGHT, layout.height)
                        putExtra(EXTRA_FRAME_MODE, layout.mode.name)
                        putExtra(EXTRA_CROP_X, layout.cropCenter.x)
                        putExtra(EXTRA_CROP_Y, layout.cropCenter.y)
                    }
                }
                putExtra(EXTRA_FLIP_STARTS, spec.effects.flips.map { it.range.startUs }.toLongArray())
                putExtra(EXTRA_FLIP_ENDS, spec.effects.flips.map { it.range.endUs }.toLongArray())
                putExtra(EXTRA_FLIP_HORIZONTAL, spec.effects.flips.map { it.horizontal }.toBooleanArray())
                putExtra(EXTRA_FLIP_VERTICAL, spec.effects.flips.map { it.vertical }.toBooleanArray())
                putExtra(EXTRA_SPEED_STARTS, spec.effects.speeds.map { it.range.startUs }.toLongArray())
                putExtra(EXTRA_SPEED_ENDS, spec.effects.speeds.map { it.range.endUs }.toLongArray())
                putExtra(EXTRA_SPEED_VALUES, spec.effects.speeds.map { it.speed }.toFloatArray())
            }

        internal fun decodeStartSpec(intent: Intent): EditSpec? {
            val inputPath = intent.getStringExtra(EXTRA_INPUT_PATH) ?: return null
            val starts = intent.getLongArrayExtra(EXTRA_STARTS) ?: return null
            val ends = intent.getLongArrayExtra(EXTRA_ENDS) ?: return null
            val cutMode = intent.getStringExtra(EXTRA_CUT_MODE)?.let { runCatching { CutMode.valueOf(it) }.getOrNull() } ?: return null
            val concatStrategy = intent.getStringExtra(EXTRA_CONCAT_STRATEGY)?.let {
                runCatching { ConcatStrategy.valueOf(it) }.getOrNull()
            } ?: ConcatStrategy.SINGLE_COMPOSITION
            if (starts.size != ends.size) return null
            val layout = decodeLayout(intent) ?: return null
            val flips = decodeFlips(intent) ?: return null
            val speeds = decodeSpeeds(intent) ?: return null
            val effects = EditEffects(layout, flips, speeds)
            try {
                EditPlanner.validateEffects(effects)
            } catch (_: InvalidEffectException) {
                return null
            }
            return EditSpec(inputPath, starts.indices.map { TimeRange(starts[it], ends[it]) }, cutMode, effects, concatStrategy)
        }

        fun start(context: Context, spec: EditSpec) {
            val intent = encodeStartIntent(context, spec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun decodeLayout(intent: Intent): FrameLayout? {
            if (!intent.hasExtra(EXTRA_FRAME_WIDTH)) return FrameLayout.Original
            val mode = intent.getStringExtra(EXTRA_FRAME_MODE)?.let { runCatching { FrameMode.valueOf(it) }.getOrNull() } ?: return null
            return FrameLayout.Ratio(
                intent.getIntExtra(EXTRA_FRAME_WIDTH, 0), intent.getIntExtra(EXTRA_FRAME_HEIGHT, 0), mode,
                NormalizedPoint(intent.getFloatExtra(EXTRA_CROP_X, Float.NaN), intent.getFloatExtra(EXTRA_CROP_Y, Float.NaN)),
            )
        }

        private fun decodeFlips(intent: Intent): List<FlipRange>? {
            val keys = listOf(EXTRA_FLIP_STARTS, EXTRA_FLIP_ENDS, EXTRA_FLIP_HORIZONTAL, EXTRA_FLIP_VERTICAL)
            if (!keys.any(intent::hasExtra)) return emptyList()
            val starts = intent.getLongArrayExtra(EXTRA_FLIP_STARTS) ?: return null
            val ends = intent.getLongArrayExtra(EXTRA_FLIP_ENDS) ?: return null
            val horizontal = intent.getBooleanArrayExtra(EXTRA_FLIP_HORIZONTAL) ?: return null
            val vertical = intent.getBooleanArrayExtra(EXTRA_FLIP_VERTICAL) ?: return null
            if (starts.size != ends.size || starts.size != horizontal.size || starts.size != vertical.size) return null
            return starts.indices.map { FlipRange(TimeRange(starts[it], ends[it]), horizontal[it], vertical[it]) }
        }

        private fun decodeSpeeds(intent: Intent): List<SpeedRange>? {
            val keys = listOf(EXTRA_SPEED_STARTS, EXTRA_SPEED_ENDS, EXTRA_SPEED_VALUES)
            if (!keys.any(intent::hasExtra)) return emptyList()
            val starts = intent.getLongArrayExtra(EXTRA_SPEED_STARTS) ?: return null
            val ends = intent.getLongArrayExtra(EXTRA_SPEED_ENDS) ?: return null
            val speeds = intent.getFloatArrayExtra(EXTRA_SPEED_VALUES) ?: return null
            if (starts.size != ends.size || starts.size != speeds.size) return null
            return starts.indices.map { SpeedRange(TimeRange(starts[it], ends[it]), speeds[it]) }
        }
    }
}
