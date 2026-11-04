package com.bleepp.listener

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification

/** The readable parts of a notification. */
data class ParsedNotification(val title: String, val text: String, val noise: Boolean, val summary: Boolean)

object NotificationText {

    fun parse(sbn: StatusBarNotification): ParsedNotification {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.text(Notification.EXTRA_TITLE_BIG).ifEmpty { extras.text(Notification.EXTRA_TITLE) }
        val text = pickText(
            lastMessage = lastMessage(extras),
            bigText = extras.text(Notification.EXTRA_BIG_TEXT),
            text = extras.text(Notification.EXTRA_TEXT),
            lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty(),
            subText = extras.text(Notification.EXTRA_SUB_TEXT),
        )
        return ParsedNotification(
            title = title,
            text = text,
            noise = isNoise(
                ongoing = sbn.isOngoing,
                category = n.category,
                hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
                hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0,
            ),
            summary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /** The most specific text wins: a chat's latest message, then expanded text, then the short line. */
    fun pickText(lastMessage: String, bigText: String, text: String, lines: List<String>, subText: String): String =
        listOf(lastMessage, bigText, text, lines.lastOrNull().orEmpty(), subText).firstOrNull { it.isNotBlank() }.orEmpty().trim()

    fun isNoise(ongoing: Boolean, category: String?, hasMediaSession: Boolean, hasProgress: Boolean): Boolean =
        ongoing || hasMediaSession || hasProgress || category in NOISE_CATEGORIES

    private val NOISE_CATEGORIES = setOf("transport", "progress", "service", "sys")

    private fun lastMessage(extras: Bundle): String {
        val raw = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION") extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val last = raw?.lastOrNull() as? Bundle ?: return ""
        return last.getCharSequence("text")?.toString().orEmpty()
    }

    private fun Bundle.text(key: String): String = getCharSequence(key)?.toString().orEmpty()
}

/**
 * Apps often re-post the same notification (a chat updating its timestamp, say). This remembers
 * what each notification last said so those updates don't fill the log or ring again.
 */
class RepeatFilter(private val capacity: Int = 512) {
    private val seen = object : LinkedHashMap<String, Int>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > capacity
    }

    /** True the first time this [key] says this [title] and [text]. */
    @Synchronized
    fun isNew(key: String, title: String, text: String): Boolean {
        val hash = 31 * title.hashCode() + text.hashCode()
        return seen.put(key, hash) != hash
    }

    @Synchronized
    fun knows(key: String): Boolean = seen.containsKey(key)
}
