package com.bleepp.alert

import android.app.Notification
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Whether a notification is an app ringing for a call, as opposed to a call already in progress or missed. */
object Calls {
    /** Values of Notification.EXTRA_CALL_TYPE (CallStyle, Android 12+). */
    const val TYPE_INCOMING = 1
    const val TYPE_ONGOING = 2
    const val TYPE_SCREENING = 3

    private const val CALL_STYLE = "android.app.Notification\$CallStyle"

    /**
     * Calling apps post ringing calls with CallStyle marked incoming, or on a channel whose sound is
     * the ringtone (Google Voice, WhatsApp, Signal and Revolut all do). Missed and ongoing calls use
     * other channels, so neither of them rings.
     *
     * The channel's audio usage can't be relied on alone: Android 16 shows listeners USAGE_NOTIFICATION
     * instead of USAGE_NOTIFICATION_RINGTONE unless the notification is CallStyle, but leaves its sound alone.
     */
    fun isRinging(
        callType: Int?,
        channelUsage: Int?,
        ringtoneSound: Boolean,
        category: String?,
        insistent: Boolean,
        fullScreen: Boolean,
    ): Boolean {
        if (callType != null && callType != 0) return callType == TYPE_INCOMING
        if (channelUsage == AudioAttributes.USAGE_NOTIFICATION_RINGTONE || ringtoneSound) return true
        return category == Notification.CATEGORY_CALL && (insistent || fullScreen)
    }

    fun isRinging(sbn: StatusBarNotification, ranking: NotificationListenerService.Ranking?): Boolean {
        val n = sbn.notification
        val extras = n.extras
        val callType = if (extras.getString(Notification.EXTRA_TEMPLATE) == CALL_STYLE) {
            extras.getInt("android.callType", 0)
        } else {
            null
        }
        val sound = ranking?.channel?.sound
        return isRinging(
            callType = callType,
            channelUsage = ranking?.channel?.audioAttributes?.usage,
            ringtoneSound = sound != null && RingtoneManager.getDefaultType(sound) == RingtoneManager.TYPE_RINGTONE,
            category = n.category,
            insistent = n.flags and Notification.FLAG_INSISTENT != 0,
            fullScreen = n.fullScreenIntent != null,
        )
    }
}
