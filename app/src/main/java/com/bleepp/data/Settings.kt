package com.bleepp.data

import android.content.Context
import androidx.core.content.edit
import com.bleepp.alert.QuietHours

/** Small app-wide preferences. Per-app rules and the log live in [BleeppDb]. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bleepp", Context.MODE_PRIVATE)

    var alertsOn: Boolean
        get() = prefs.getBoolean("alerts_on", true)
        set(v) = prefs.edit { putBoolean("alerts_on", v) }

    var quietEnabled: Boolean
        get() = prefs.getBoolean("quiet_enabled", false)
        set(v) = prefs.edit { putBoolean("quiet_enabled", v) }

    var quietStart: Int
        get() = prefs.getInt("quiet_start", 22 * 60)
        set(v) = prefs.edit { putInt("quiet_start", v) }

    var quietEnd: Int
        get() = prefs.getInt("quiet_end", 7 * 60)
        set(v) = prefs.edit { putInt("quiet_end", v) }

    val quietHours get() = QuietHours(quietEnabled, quietStart, quietEnd)

    var cooldownSeconds: Int
        get() = prefs.getInt("cooldown", 10)
        set(v) = prefs.edit { putInt("cooldown", v) }

    /** Keep ongoing, media and progress notifications in the log too. */
    var logNoise: Boolean
        get() = prefs.getBoolean("log_noise", false)
        set(v) = prefs.edit { putBoolean("log_noise", v) }

    /** Days to keep log entries; 0 keeps them forever. */
    var retentionDays: Int
        get() = prefs.getInt("retention_days", 30)
        set(v) = prefs.edit { putInt("retention_days", v) }

    var checkUpdates: Boolean
        get() = prefs.getBoolean("check_updates", true)
        set(v) = prefs.edit { putBoolean("check_updates", v) }

    var updateIntervalHours: Int
        get() = prefs.getInt("update_hours", 12)
        set(v) = prefs.edit { putInt("update_hours", v) }

    var notifiedVersion: String?
        get() = prefs.getString("notified_version", null)
        set(v) = prefs.edit { putString("notified_version", v) }

    /** The release the user said "remind me later" to; the pop-up waits for a newer one. */
    var snoozedVersion: String?
        get() = prefs.getString("snoozed_version", null)
        set(v) = prefs.edit { putString("snoozed_version", v) }

    var setupDismissed: Boolean
        get() = prefs.getBoolean("setup_dismissed", false)
        set(v) = prefs.edit { putBoolean("setup_dismissed", v) }

    var themeJson: String?
        get() = prefs.getString("theme", null)
        set(v) = prefs.edit { putString("theme", v) }

    var savedLooksJson: String?
        get() = prefs.getString("saved_looks", null)
        set(v) = prefs.edit { putString("saved_looks", v) }

    /**
     * The volume to put back if the process dies mid-alert, as "stream:index". Written before the
     * volume changes, so a crash can't leave someone's alarm stuck loud.
     */
    var pendingVolumeRestore: String?
        get() = prefs.getString("pending_volume", null)
        set(v) = prefs.edit(commit = true) { putString("pending_volume", v) }
}
