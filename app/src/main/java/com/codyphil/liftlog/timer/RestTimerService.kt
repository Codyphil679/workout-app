package com.codyphil.liftlog.timer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.codyphil.liftlog.MainActivity
import com.codyphil.liftlog.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that shows a live countdown notification and fires a vibration + alert when rest is over.
 * A partial wake lock is held only for the duration of the countdown so the alert fires on time with the screen off.
 */
class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownJob: Job? = null
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD -> RestTimer.adjust(15)
            ACTION_SKIP -> RestTimer.skip()
        }
        val state = RestTimer.state.value
        // Must call startForeground promptly after startForegroundService, even if we are about to stop.
        ServiceCompat.startForeground(
            this, NOTIF_ID_RUNNING,
            runningNotification(state),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (state == null) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }
        if (observeJob == null) {
            observeJob = scope.launch {
                RestTimer.state.collect { s ->
                    if (s == null) stopSelfCleanly() else schedule(s)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun schedule(state: RestState) {
        countdownJob?.cancel()
        acquireWakeLock(state.remainingMillis() + 15_000)
        NotificationManagerCompat.from(this).cancel(NOTIF_ID_DONE)
        notifyIfAllowed(NOTIF_ID_RUNNING, runningNotification(state))
        countdownJob = scope.launch {
            delay(state.remainingMillis())
            alertDone(state)
            RestTimer.finished(state)
        }
    }

    private fun alertDone(state: RestState) {
        vibrate()
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Rest over — time to lift")
            .setContentText(state.label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(openAppIntent())
            .build()
        notifyIfAllowed(NOTIF_ID_DONE, n)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
    }

    private fun runningNotification(state: RestState?): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Resting")
            .setContentText(state?.label ?: "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openAppIntent())
        if (state != null) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(state.endAt)
                .setShowWhen(true)
                .addAction(0, "+15s", serviceIntent(ACTION_ADD, 1))
                .addAction(0, "Skip", serviceIntent(ACTION_SKIP, 2))
        }
        return b.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode, Intent(this, RestTimerService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(id: Int, notification: Notification) {
        val nm = NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) nm.notify(id, notification)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(timeoutMs: Long) {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiftLog:RestTimer")
            .apply { acquire(timeoutMs) }
    }

    private fun stopSelfCleanly() {
        countdownJob?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_RUNNING = "rest_running"
        private const val CHANNEL_DONE = "rest_done"
        private const val NOTIF_ID_RUNNING = 1001
        private const val NOTIF_ID_DONE = 1002
        private const val ACTION_ADD = "com.codyphil.liftlog.REST_ADD"
        private const val ACTION_SKIP = "com.codyphil.liftlog.REST_SKIP"

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Rest timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live countdown while you rest"
                    setShowBadge(false)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Rest finished", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts you when it's time for your next set"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
                    )
                }
            )
        }
    }
}
