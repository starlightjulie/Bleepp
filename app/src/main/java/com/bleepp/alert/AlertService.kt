package com.bleepp.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bleepp.R

/**
 * Holds a media foreground service for exactly as long as an alert plays.
 *
 * Android 17 silences background audio from any app without a visible activity or a foreground
 * service, with no error to catch, so playback must always start in here.
 */
class AlertService : Service() {
    private val finished: () -> Unit = {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = intent?.toRequest()
        val shown = notification(request?.label.orEmpty())
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, shown, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, shown)
        }
        if (request == null) finished() else AlertPlayer.start(this, request, finished)
        return START_NOT_STICKY
    }

    private fun notification(label: String): Notification {
        val stop = PendingIntent.getBroadcast(
            this, 0, Intent(this, StopAlertReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bleepp)
            .setContentTitle(getString(R.string.alert_playing, label))
            .setSilent(true)
            .setOngoing(true)
            .addAction(0, getString(R.string.alert_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun Intent.toRequest(): AlertRequest? {
        val key = getStringExtra("key") ?: return null
        return AlertRequest(
            key = key,
            label = getStringExtra("label").orEmpty(),
            sound = getStringExtra("sound") ?: return null,
            volumePercent = getIntExtra("volume", 70),
            route = AlertRoute.entries[getIntExtra("route", 0)],
            repeat = getBooleanExtra("repeat", false),
            repeatEverySeconds = getIntExtra("every", 30),
            repeatMax = getIntExtra("max", 1),
            vibrate = getBooleanExtra("vibrate", false),
        )
    }

    companion object {
        private const val CHANNEL = "alert_playing"
        private const val NOTIFICATION_ID = 2

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }

        /** Plays [request]. If Android refuses the foreground service, plays anyway rather than lose the alert. */
        fun play(context: Context, request: AlertRequest) {
            val intent = Intent(context, AlertService::class.java)
                .putExtra("key", request.key)
                .putExtra("label", request.label)
                .putExtra("sound", request.sound)
                .putExtra("volume", request.volumePercent)
                .putExtra("route", request.route.ordinal)
                .putExtra("repeat", request.repeat)
                .putExtra("every", request.repeatEverySeconds)
                .putExtra("max", request.repeatMax)
                .putExtra("vibrate", request.vibrate)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { AlertPlayer.start(context, request) {} }
        }
    }
}

class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AlertPlayer.stop(context)
}
