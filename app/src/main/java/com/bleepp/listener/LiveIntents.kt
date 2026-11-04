package com.bleepp.listener

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bleepp.alert.AlertPlayer
import com.bleepp.data.LogEntry

/**
 * Tap actions for logged notifications. Android can't save a PendingIntent to disk, so these only
 * last until the app process ends; [NotificationOpener] falls back to opening the app.
 */
object LiveIntents {
    private const val CAPACITY = 1000
    private val intents = object : LinkedHashMap<Long, PendingIntent>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PendingIntent>?) = size > CAPACITY
    }

    @Synchronized fun put(id: Long, intent: PendingIntent) { intents[id] = intent }
    @Synchronized fun get(id: Long): PendingIntent? = intents[id]
}

object NotificationOpener {
    enum class Result { EXACT, APP, FAILED }

    /** Opens the place the notification pointed at, or at least its app. */
    fun open(context: Context, entry: LogEntry): Result {
        AlertPlayer.stopFor(context, entry.sbnKey)
        val target = LiveIntents.get(entry.id) ?: BleeppListener.activeIntent(entry.sbnKey)
        if (target != null && send(context, target)) return Result.EXACT
        val launch = context.packageManager.getLaunchIntentForPackage(entry.pkg) ?: return Result.FAILED
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.APP
        }.getOrDefault(Result.FAILED)
    }

    private fun send(context: Context, target: PendingIntent): Boolean = runCatching {
        // Android 14+ needs the sender to opt in before another app's activity can open from it
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
        target.send(context, 0, null, null, null, null, options)
        true
    }.getOrDefault(false)
}
