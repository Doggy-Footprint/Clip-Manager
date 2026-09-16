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

        val spec = intent?.let(::readSpec) ?: return START_NOT_STICKY
        val job = videoEditor?.start(spec) ?: return START_NOT_STICKY
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

    private fun readSpec(intent: Intent): EditSpec? {
        val inputPath = intent.getStringExtra(EXTRA_INPUT_PATH) ?: return null
        val starts = intent.getLongArrayExtra(EXTRA_STARTS) ?: return null
        val ends = intent.getLongArrayExtra(EXTRA_ENDS) ?: return null
        val cutMode = intent.getStringExtra(EXTRA_CUT_MODE)?.let(CutMode::valueOf) ?: return null
        val hasEffects = intent.getBooleanExtra(EXTRA_HAS_EFFECTS, false)
        val concatStrategy = intent.getStringExtra(EXTRA_CONCAT_STRATEGY)?.let(ConcatStrategy::valueOf)
            ?: ConcatStrategy.SINGLE_COMPOSITION
        val keepRanges = starts.indices.map { i -> TimeRange(starts[i], ends[i]) }
        return EditSpec(inputPath, keepRanges, cutMode, hasEffects, concatStrategy)
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
        private const val EXTRA_HAS_EFFECTS = "has_effects"
        private const val EXTRA_CONCAT_STRATEGY = "concat_strategy"

        fun start(context: Context, spec: EditSpec) {
            val intent = Intent(context, EditService::class.java).apply {
                putExtra(EXTRA_INPUT_PATH, spec.inputPath)
                putExtra(EXTRA_STARTS, spec.keepRanges.map { it.startUs }.toLongArray())
                putExtra(EXTRA_ENDS, spec.keepRanges.map { it.endUs }.toLongArray())
                putExtra(EXTRA_CUT_MODE, spec.cutMode.name)
                putExtra(EXTRA_HAS_EFFECTS, spec.hasEffects)
                putExtra(EXTRA_CONCAT_STRATEGY, spec.concatStrategy.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
