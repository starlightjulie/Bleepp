package com.bleepp

import com.bleepp.alert.AlertPlayer
import com.bleepp.listener.NotificationText
import com.bleepp.listener.RepeatFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    @Test fun `a chat's latest message beats the summary line`() =
        assertEquals("see you at 8", NotificationText.pickText("see you at 8", "", "2 new messages", emptyList(), ""))

    @Test fun `expanded text beats the short line`() =
        assertEquals("the whole story", NotificationText.pickText("", "the whole story", "the who…", emptyList(), ""))

    @Test fun `inbox style uses its newest line`() =
        assertEquals("newest", NotificationText.pickText("", "", "", listOf("older", "newest"), ""))

    @Test fun `falls back to sub text and trims`() =
        assertEquals("subtitle", NotificationText.pickText(" ", "", "", emptyList(), " subtitle "))

    @Test fun `nothing readable gives an empty string`() =
        assertEquals("", NotificationText.pickText("", "", "", emptyList(), ""))

    @Test fun `ongoing media and progress count as noise`() {
        assertTrue(NotificationText.isNoise(ongoing = true, category = null, hasMediaSession = false, hasProgress = false))
        assertTrue(NotificationText.isNoise(ongoing = false, category = null, hasMediaSession = true, hasProgress = false))
        assertTrue(NotificationText.isNoise(ongoing = false, category = null, hasMediaSession = false, hasProgress = true))
        assertTrue(NotificationText.isNoise(ongoing = false, category = "transport", hasMediaSession = false, hasProgress = false))
    }

    @Test fun `messages are not noise`() =
        assertFalse(NotificationText.isNoise(ongoing = false, category = "msg", hasMediaSession = false, hasProgress = false))

    @Test fun `identical re-posts are ignored but changed text is new`() {
        val filter = RepeatFilter()
        assertTrue(filter.isNew("k", "Ana", "hi"))
        assertFalse(filter.isNew("k", "Ana", "hi"))
        assertTrue(filter.isNew("k", "Ana", "hi again"))
    }

    @Test fun `different notifications are tracked separately`() {
        val filter = RepeatFilter()
        assertTrue(filter.isNew("a", "t", "x"))
        assertTrue(filter.isNew("b", "t", "x"))
    }

    @Test fun `the filter forgets the oldest key at capacity`() {
        val filter = RepeatFilter(capacity = 2)
        filter.isNew("a", "t", "x")
        filter.isNew("b", "t", "x")
        filter.isNew("c", "t", "x")
        assertFalse(filter.knows("a"))
        assertTrue(filter.knows("c"))
    }

    @Test fun `volume percent maps to a real stream index`() {
        assertEquals(5, AlertPlayer.targetIndex(7, 70))
        assertEquals(7, AlertPlayer.targetIndex(7, 100))
        assertEquals(1, AlertPlayer.targetIndex(15, 1))
        assertEquals(1, AlertPlayer.targetIndex(15, 0))
        assertEquals(15, AlertPlayer.targetIndex(15, 500))
    }
}
