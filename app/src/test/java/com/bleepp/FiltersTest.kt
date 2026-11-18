package com.bleepp

import com.bleepp.alert.Pause
import com.bleepp.data.AppRule
import com.bleepp.data.Backup
import com.bleepp.data.BackupFile
import com.bleepp.data.BlockedFeature
import com.bleepp.data.LogCsv
import com.bleepp.data.LogEntry
import com.bleepp.listener.BleeppListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FiltersTest {
    @Test fun `hidden words match like keywords, ignoring case`() {
        assertTrue(BleeppListener.hasHiddenWord("charging, OTP", "Battery Charging 87%"))
        assertFalse(BleeppListener.hasHiddenWord("charging, OTP", "Dinner at 7?"))
        assertFalse(BleeppListener.hasHiddenWord("", "anything"))
    }

    @Test fun `CSV quotes fields so commas, quotes and new lines survive`() {
        val csv = LogCsv.encode(
            listOf(LogEntry(sbnKey = "k", pkg = "p", appName = "Chat", title = "Sam", text = "Hi, \"you\"\nthere", postedAt = 0, bookmarked = true)),
        ) { "12:00" }
        val lines = csv.split("\r\n")
        assertEquals("time,app,package,feature,title,message,bookmarked", lines[0])
        assertEquals("\"12:00\",\"Chat\",\"p\",\"\",\"Sam\",\"Hi, \"\"you\"\"\nthere\",\"yes\"", lines[1])
    }

    @Test fun `backups survive a round trip`() {
        val backup = Backup(
            rules = listOf(AppRule("voice", callSound = "ring", ringForCalls = true, keywords = "urgent")),
            excludedApps = listOf("spam"),
            blockedFeatures = listOf(BlockedFeature("android", "charging", "Charging", "Android System")),
            bookmarkedApps = listOf("voice"),
        )
        assertEquals(backup, BackupFile.decode(BackupFile.encode(backup)))
    }

    @Test fun `backups from a newer version with unknown fields still load`() {
        val text = """{"version": 9, "rules": [], "somethingNew": true}"""
        assertEquals(9, BackupFile.decode(text).version)
    }

    @Test fun `until morning is later today before 8 and tomorrow after`() {
        val c = Calendar.getInstance().apply { set(2026, 10, 17, 6, 30, 0); set(Calendar.MILLISECOND, 0) }
        val early = Pause.nextTime(c.timeInMillis, 8)
        assertEquals(90 * 60_000L, early - c.timeInMillis)
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 0)
        val late = Pause.nextTime(c.timeInMillis, 8)
        assertEquals(9 * 60 * 60_000L, late - c.timeInMillis)
    }
}
