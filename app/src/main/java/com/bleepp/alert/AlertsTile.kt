package com.bleepp.alert

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import com.bleepp.R
import com.bleepp.data.Settings
import java.util.Date

/** Quick Settings tile: tap to pause all alerts until turned back on, tap again to resume. */
class AlertsTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val settings = Settings(this)
        if (settings.isPaused) Pause.resume(this) else Pause.pauseIndefinitely(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val settings = Settings(this)
        val paused = settings.isPaused || !settings.alertsOn
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_bleepp)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                !settings.alertsOn -> getString(R.string.tile_off)
                !settings.isPaused -> getString(R.string.tile_on)
                settings.pausedUntil == Long.MAX_VALUE -> getString(R.string.tile_paused)
                else -> getString(R.string.paused_until, DateFormat.getTimeFormat(this).format(Date(settings.pausedUntil)))
            }
        }
        tile.updateTile()
    }
}
