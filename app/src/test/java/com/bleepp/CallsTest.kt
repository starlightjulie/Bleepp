package com.bleepp

import com.bleepp.alert.AlertDecider
import com.bleepp.alert.AlertDecision
import com.bleepp.alert.AlertRoute
import com.bleepp.alert.Calls
import com.bleepp.alert.QuietHours
import com.bleepp.alert.QuietReason
import com.bleepp.alert.SoundState
import com.bleepp.data.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {
    private val ringtone = 6 // AudioAttributes.USAGE_NOTIFICATION_RINGTONE
    private val notification = 5 // AudioAttributes.USAGE_NOTIFICATION

    @Test fun `a ringtone channel is a ringing call, like Google Voice's Incoming call`() =
        assertTrue(Calls.isRinging(null, ringtone, false, null, insistent = false, fullScreen = false))

    @Test fun `missed and ongoing call channels don't ring`() =
        assertFalse(Calls.isRinging(null, notification, false, "call", insistent = false, fullScreen = false))

    @Test fun `CallStyle incoming rings`() =
        assertTrue(Calls.isRinging(Calls.TYPE_INCOMING, notification, false, "call", insistent = false, fullScreen = true))

    @Test fun `CallStyle ongoing never rings, even on a ringtone channel`() =
        assertFalse(Calls.isRinging(Calls.TYPE_ONGOING, ringtone, false, "call", insistent = true, fullScreen = true))

    @Test fun `CallStyle screening doesn't ring`() =
        assertFalse(Calls.isRinging(Calls.TYPE_SCREENING, ringtone, false, "call", insistent = false, fullScreen = false))

    @Test fun `older apps ring with the call category plus an insistent or full screen alert`() {
        assertTrue(Calls.isRinging(null, notification, false, "call", insistent = true, fullScreen = false))
        assertTrue(Calls.isRinging(null, notification, false, "call", insistent = false, fullScreen = true))
    }

    @Test fun `a ringtone sound rings even when Android hides the ringtone usage`() =
        assertTrue(Calls.isRinging(null, notification, true, "call", insistent = false, fullScreen = false))

    @Test fun `CallStyle ongoing wins over a ringtone sound`() =
        assertFalse(Calls.isRinging(Calls.TYPE_ONGOING, notification, true, "call", insistent = false, fullScreen = false))

    @Test fun `a plain message never rings`() =
        assertFalse(Calls.isRinging(null, notification, false, "msg", insistent = false, fullScreen = false))

    private val silent = SoundState(canRing = false, alarmMuted = false, inCall = false)
    private fun decide(rule: AppRule, call: Boolean, sinceLast: Long = Long.MAX_VALUE, paused: Boolean = false, text: String = "Incoming call") =
        AlertDecider.decide(rule, text, true, silent, QuietHours(false, 0, 0), 600, sinceLast, 10_000, paused, call)

    @Test fun `ringing calls are marked as calls`() =
        assertEquals(AlertDecision.Play(AlertRoute.ALARM, call = true), decide(AppRule("voice"), call = true))

    @Test fun `calls ignore keywords and the cooldown`() =
        assertEquals(
            AlertDecision.Play(AlertRoute.ALARM, call = true),
            decide(AppRule("voice", keywords = "urgent"), call = true, sinceLast = 1),
        )

    @Test fun `turning call ringing off treats calls as ordinary alerts`() =
        assertEquals(
            AlertDecision.Quiet(QuietReason.KEYWORD_MISS),
            decide(AppRule("voice", keywords = "urgent", ringForCalls = false), call = true),
        )

    @Test fun `pausing silences calls too`() =
        assertEquals(AlertDecision.Quiet(QuietReason.PAUSED), decide(AppRule("voice"), call = true, paused = true))
}
