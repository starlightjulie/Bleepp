package com.bleepp.alert

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.bleepp.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/** Pausing all alerts for a while, from the app, the Quick Settings tile or an alert's notification. */
object Pause {
    private val _until = MutableStateFlow(0L)

    /** When alerts resume, so screens update the moment a pause starts or ends anywhere. */
    val until: StateFlow<Long> = _until.asStateFlow()

    fun load(context: Context) {
        _until.value = Settings(context).pausedUntil
    }

    fun pauseFor(context: Context, millis: Long) = set(context, System.currentTimeMillis() + millis)

    fun pauseUntilMorning(context: Context, hour: Int = 8) = set(context, nextTime(System.currentTimeMillis(), hour))

    fun pauseIndefinitely(context: Context) = set(context, Long.MAX_VALUE)

    fun resume(context: Context) = set(context, 0L)

    private fun set(context: Context, until: Long) {
        Settings(context).pausedUntil = until
        _until.value = until
        TileService.requestListeningState(context, ComponentName(context, AlertsTile::class.java))
    }

    /** The next time the clock reads [hour]:00, which is tomorrow if that's already passed today. */
    fun nextTime(now: Long, hour: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
