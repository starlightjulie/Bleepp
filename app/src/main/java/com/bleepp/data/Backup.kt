package com.bleepp.data

import android.content.Context
import android.net.Uri
import com.bleepp.ui.theme.Look
import com.bleepp.ui.theme.SavedLook
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything worth keeping when moving to a new phone, except the log itself, in one JSON file. */
@Serializable
data class Backup(
    val version: Int = 1,
    val rules: List<AppRule> = emptyList(),
    val excludedApps: List<String> = emptyList(),
    val blockedFeatures: List<BlockedFeature> = emptyList(),
    val bookmarkedApps: List<String> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
    val look: Look? = null,
    val savedLooks: List<SavedLook> = emptyList(),
)

@Serializable
data class BackupSettings(
    val alertsOn: Boolean = true,
    val quietEnabled: Boolean = false,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 7 * 60,
    val cooldownSeconds: Int = 10,
    val logNoise: Boolean = false,
    val retentionDays: Int = 30,
    val keepLatestUpdate: Boolean = true,
    val hiddenWords: String = "",
    val lockLog: Boolean = false,
)

object BackupFile {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    suspend fun build(context: Context, look: Look, savedLooks: List<SavedLook>): Backup {
        val db = BleeppDb.get(context)
        val s = Settings(context)
        return Backup(
            rules = db.rules().all(),
            excludedApps = db.excluded().all(),
            blockedFeatures = db.blocked().all(),
            bookmarkedApps = db.bookmarkedApps().all(),
            settings = BackupSettings(
                alertsOn = s.alertsOn,
                quietEnabled = s.quietEnabled,
                quietStart = s.quietStart,
                quietEnd = s.quietEnd,
                cooldownSeconds = s.cooldownSeconds,
                logNoise = s.logNoise,
                retentionDays = s.retentionDays,
                keepLatestUpdate = s.keepLatestUpdate,
                hiddenWords = s.hiddenWords,
                lockLog = s.lockLog,
            ),
            look = look,
            savedLooks = savedLooks,
        )
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup = json.decodeFromString(Backup.serializer(), text)

    fun write(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: error("Couldn't open the file")
    }

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Couldn't open the file")

    /** Replaces rules, filters and settings with the backup's. The log is left alone. */
    suspend fun restore(context: Context, backup: Backup) {
        val db = BleeppDb.get(context)
        db.rules().clear()
        backup.rules.forEach { db.rules().save(it) }
        db.excluded().clear()
        backup.excludedApps.forEach { db.excluded().add(ExcludedApp(it)) }
        db.blocked().clear()
        backup.blockedFeatures.forEach { db.blocked().add(it) }
        db.bookmarkedApps().clear()
        backup.bookmarkedApps.forEach { db.bookmarkedApps().add(BookmarkedApp(it)) }
        val s = Settings(context)
        with(backup.settings) {
            s.alertsOn = alertsOn
            s.quietEnabled = quietEnabled
            s.quietStart = quietStart
            s.quietEnd = quietEnd
            s.cooldownSeconds = cooldownSeconds
            s.logNoise = logNoise
            s.retentionDays = retentionDays
            s.keepLatestUpdate = keepLatestUpdate
            s.hiddenWords = hiddenWords
            s.lockLog = lockLog
        }
    }
}

/** The log as a spreadsheet file. Quotes every field so commas and new lines in messages survive. */
object LogCsv {
    fun encode(entries: List<LogEntry>, formatTime: (Long) -> String): String = buildString {
        append("time,app,package,feature,title,message,bookmarked\r\n")
        for (e in entries) {
            listOf(formatTime(e.postedAt), e.appName, e.pkg, e.channelName, e.title, e.text, if (e.bookmarked) "yes" else "")
                .joinTo(this, ",") { field(it) }
            append("\r\n")
        }
    }

    fun field(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
