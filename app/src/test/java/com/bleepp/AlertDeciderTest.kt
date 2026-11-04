package com.bleepp

import com.bleepp.alert.AlertDecider
import com.bleepp.alert.AlertDecision
import com.bleepp.alert.AlertRoute
import com.bleepp.alert.QuietHours
import com.bleepp.alert.QuietReason
import com.bleepp.alert.SoundState
import com.bleepp.data.AppRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDeciderTest {
    private val silent = SoundState(canRing = false, alarmMuted = false, inCall = false)
    private val totalSilence = silent.copy(alarmMuted = true)
    private val noQuietHours = QuietHours(false, 0, 0)
    private val rule = AppRule("app")

    private fun decide(
        rule: AppRule? = this.rule,
        text: String = "hello",
        masterOn: Boolean = true,
        sound: SoundState = silent,
        quietHours: QuietHours = noQuietHours,
        minute: Int = 600,
        sinceLast: Long = Long.MAX_VALUE,
    ) = AlertDecider.decide(rule, text, masterOn, sound, quietHours, minute, sinceLast, cooldownMillis = 10_000)

    private fun quiet(reason: QuietReason) = AlertDecision.Quiet(reason)

    @Test fun `plays on the alarm route when the phone is silent`() =
        assertEquals(AlertDecision.Play(AlertRoute.ALARM), decide())

    @Test fun `stays quiet without a rule`() = assertEquals(quiet(QuietReason.NO_RULE), decide(rule = null))

    @Test fun `stays quiet when the rule is off`() =
        assertEquals(quiet(QuietReason.NO_RULE), decide(rule = rule.copy(enabled = false)))

    @Test fun `master switch silences everything`() =
        assertEquals(quiet(QuietReason.ALERTS_OFF), decide(masterOn = false))

    @Test fun `leaves the sound to the app when the phone can ring`() =
        assertEquals(quiet(QuietReason.PHONE_CAN_RING), decide(sound = silent.copy(canRing = true)))

    @Test fun `never plays during a call`() =
        assertEquals(quiet(QuietReason.IN_CALL), decide(sound = silent.copy(inCall = true)))

    @Test fun `quiet hours win over a matching rule`() =
        assertEquals(quiet(QuietReason.QUIET_HOURS), decide(quietHours = QuietHours(true, 540, 660)))

    @Test fun `cooldown stops rapid repeats`() =
        assertEquals(quiet(QuietReason.COOLDOWN), decide(sinceLast = 9_999))

    @Test fun `plays again once the cooldown has passed`() =
        assertEquals(AlertDecision.Play(AlertRoute.ALARM), decide(sinceLast = 10_000))

    @Test fun `total silence blocks an app that has not opted in`() =
        assertEquals(quiet(QuietReason.TOTAL_SILENCE), decide(sound = totalSilence))

    @Test fun `total silence is broken only by opting in`() =
        assertEquals(
            AlertDecision.Play(AlertRoute.BREAKTHROUGH),
            decide(rule = rule.copy(breakthrough = true), sound = totalSilence),
        )

    @Test fun `opting in keeps the alarm route when alarms are allowed`() =
        assertEquals(AlertDecision.Play(AlertRoute.ALARM), decide(rule = rule.copy(breakthrough = true)))

    @Test fun `keyword miss stays quiet`() =
        assertEquals(quiet(QuietReason.KEYWORD_MISS), decide(rule = rule.copy(keywords = "urgent"), text = "lunch?"))

    @Test fun `keywords ignore case and accept commas and new lines`() {
        assertTrue(AlertDecider.matchesKeywords("urgent, mum\nserver down", "Message from MUM"))
        assertTrue(AlertDecider.matchesKeywords("urgent, mum\nserver down", "the server down again"))
        assertFalse(AlertDecider.matchesKeywords("urgent, mum", "lunch?"))
    }

    @Test fun `blank keywords match everything`() {
        assertTrue(AlertDecider.matchesKeywords("", "anything"))
        assertTrue(AlertDecider.matchesKeywords(" , \n ", "anything"))
    }

    @Test fun `quiet hours inside one day`() {
        val window = QuietHours(true, 540, 660)
        assertTrue(window.contains(540))
        assertTrue(window.contains(659))
        assertFalse(window.contains(660))
        assertFalse(window.contains(539))
    }

    @Test fun `quiet hours run past midnight`() {
        val window = QuietHours(true, 22 * 60, 7 * 60)
        assertTrue(window.contains(23 * 60))
        assertTrue(window.contains(0))
        assertTrue(window.contains(6 * 60 + 59))
        assertFalse(window.contains(7 * 60))
        assertFalse(window.contains(12 * 60))
    }

    @Test fun `disabled or empty quiet hours never match`() {
        assertFalse(QuietHours(false, 0, 1440).contains(600))
        assertFalse(QuietHours(true, 600, 600).contains(600))
    }
}
