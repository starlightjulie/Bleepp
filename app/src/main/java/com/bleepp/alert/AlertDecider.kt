package com.bleepp.alert

import com.bleepp.data.AppRule

/** How a sound reaches the speaker. Both were checked by ear on real phones; see docs/AUDIO.md. */
enum class AlertRoute {
    /** Alarm audio: plays on silent, vibrate and most Do Not Disturb modes. */
    ALARM,

    /** Accessibility audio: the only route Total Silence doesn't mute. Follows media volume. */
    BREAKTHROUGH,
}

/** Why a matching rule stayed quiet. Stored with the log entry, so it can be shown later. */
enum class QuietReason {
    NO_RULE,
    ALERTS_OFF,
    KEYWORD_MISS,
    PHONE_CAN_RING,
    QUIET_HOURS,
    IN_CALL,
    COOLDOWN,
    TOTAL_SILENCE,
}

sealed interface AlertDecision {
    data class Play(val route: AlertRoute) : AlertDecision
    data class Quiet(val reason: QuietReason) : AlertDecision
}

/** The phone's sound state at the moment a notification arrives. */
data class SoundState(
    /** Ringer is on and no Do Not Disturb mode is filtering notifications, so apps can make their own sound. */
    val canRing: Boolean,
    /** True when Do Not Disturb has muted the alarm stream, as Total Silence does. */
    val alarmMuted: Boolean,
    val inCall: Boolean,
)

data class QuietHours(val enabled: Boolean, val startMinute: Int, val endMinute: Int) {
    /** [minuteOfDay] is inside the window. A window like 22:00 to 07:00 runs past midnight. */
    fun contains(minuteOfDay: Int): Boolean = when {
        !enabled || startMinute == endMinute -> false
        startMinute < endMinute -> minuteOfDay in startMinute until endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

object AlertDecider {

    fun decide(
        rule: AppRule?,
        text: String,
        masterOn: Boolean,
        sound: SoundState,
        quietHours: QuietHours,
        minuteOfDay: Int,
        millisSinceLastAlert: Long,
        cooldownMillis: Long,
    ): AlertDecision {
        if (rule == null || !rule.enabled) return AlertDecision.Quiet(QuietReason.NO_RULE)
        if (!masterOn) return AlertDecision.Quiet(QuietReason.ALERTS_OFF)
        if (!matchesKeywords(rule.keywords, text)) return AlertDecision.Quiet(QuietReason.KEYWORD_MISS)
        if (sound.canRing) return AlertDecision.Quiet(QuietReason.PHONE_CAN_RING)
        if (sound.inCall) return AlertDecision.Quiet(QuietReason.IN_CALL)
        if (quietHours.contains(minuteOfDay)) return AlertDecision.Quiet(QuietReason.QUIET_HOURS)
        if (millisSinceLastAlert < cooldownMillis) return AlertDecision.Quiet(QuietReason.COOLDOWN)
        if (!sound.alarmMuted) return AlertDecision.Play(AlertRoute.ALARM)
        return if (rule.breakthrough) AlertDecision.Play(AlertRoute.BREAKTHROUGH)
        else AlertDecision.Quiet(QuietReason.TOTAL_SILENCE)
    }

    /** Keywords are separated by commas or new lines. No keywords means everything matches. */
    fun matchesKeywords(keywords: String, text: String): Boolean {
        val words = parseKeywords(keywords)
        return words.isEmpty() || words.any { text.contains(it, ignoreCase = true) }
    }

    fun parseKeywords(keywords: String): List<String> =
        keywords.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}
