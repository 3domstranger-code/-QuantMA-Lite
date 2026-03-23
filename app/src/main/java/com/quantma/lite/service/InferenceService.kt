package com.quantma.lite.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.quantma.lite.MainActivity
import com.quantma.lite.R
import timber.log.Timber

/**
 * Foreground Service для фоновой генерации.
 * Показывает уведомление "Генерация..." и удерживает CPU активным
 * через PARTIAL_WAKE_LOCK, не позволяя OS убить процесс.
 *
 * Запускается в ChatViewModel при начале генерации,
 * останавливается при завершении или отмене.
 */
class InferenceService : Service() {

    companion object {
        const val CHANNEL_ID = "inference_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.quantma.lite.INFERENCE_START"
        const val ACTION_STOP  = "com.quantma.lite.INFERENCE_STOP"

        const val EXTRA_MODEL_NAME = "model_name"

        fun startIntent(context: Context, modelName: String = ""): Intent =
            Intent(context, InferenceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODEL_NAME, modelName)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, InferenceService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: ""
                Timber.i("InferenceService: starting foreground, model=$modelName")
                startForeground(NOTIFICATION_ID, buildNotification(modelName))
                acquireWakeLock()
            }
            ACTION_STOP -> {
                Timber.i("InferenceService: stopping")
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    // ---- Wake Lock ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuantMA:inference"
        ).apply {
            // Auto-release after 30 minutes as safety net
            acquire(30 * 60 * 1000L)
        }
        Timber.d("WakeLock acquired")
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Timber.d("WakeLock released")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to release WakeLock")
        }
        wakeLock = null
    }

    // ---- Notification ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Inference",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when AI is generating a response"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(modelName: String): Notification {
        // Tap notification → open app
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop button in notification
        val stopIntent = PendingIntent.getService(
            this, 1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (modelName.isNotEmpty())
            "Generating · $modelName"
        else
            "Generating response…"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Keep the device on or let it sleep — inference continues")
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}
