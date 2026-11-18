package com.bleepp.listener

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings as SystemSettings
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bleepp.alert.AlertDecider
import com.bleepp.alert.AlertDecision
import com.bleepp.alert.AlertPlayer
import com.bleepp.alert.AlertRequest
import com.bleepp.alert.AlertRoute
import com.bleepp.alert.AlertService
import com.bleepp.alert.Calls
import com.bleepp.alert.QuietReason
import com.bleepp.alert.SoundState
import com.bleepp.data.AlertOutcome
import com.bleepp.data.AppRule
import com.bleepp.data.BleeppDb
import com.bleepp.data.LogEntry
import com.bleepp.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Watches every notification: decides whether to sound an alert, then writes it to the log. */
class BleeppListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Posted>(Channel.UNLIMITED)
    private val repeats = RepeatFilter()
    private val lastAlert = ConcurrentHashMap<String, Long>()
    private val db by lazy { BleeppDb.get(this) }
    private val settings by lazy { Settings(this) }

    /** A notification plus what the system knew about it when it arrived. */
    private class Posted(val sbn: StatusBarNotification, val ranking: Ranking?)

    override fun onCreate() {
        super.onCreate()
        // One at a time, in order, so duplicate checks and cooldowns see notifications as they arrived
        scope.launch {
            for (posted in queue) {
                runCatching { handle(posted.sbn, posted.ranking) }
                    .onFailure { Log.w(TAG, "Couldn't handle a notification from ${posted.sbn.packageName}", it) }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        instance = this
        _connected.value = true
        AlertPlayer.recoverVolume(this)
        scope.launch { purge() }
    }

    override fun onListenerDisconnected() {
        instance = null
        _connected.value = false
        requestRebind(ComponentName(this, BleeppListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        val ranking = rankingMap?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) } }
        queue.trySend(Posted(sbn, ranking))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // However a ringing call's notification goes away, the call has stopped ringing
        AlertPlayer.stopCall(this, sbn.key)
        // An app clearing its own notification usually means it was read somewhere else
        if (reason in SEEN_REASONS) AlertPlayer.stopFor(this, sbn.key)
    }

    private suspend fun handle(sbn: StatusBarNotification, ranking: Ranking?) {
        val parsed = NotificationText.parse(sbn)
        val ringing = Calls.isRinging(sbn, ranking)
        // Answering turns a ringing call into an ongoing one, often by updating the same notification
        if (!ringing && AlertPlayer.isRingingFor(sbn.key)) AlertPlayer.stopCall(this, sbn.key)
        if (parsed.summary || (parsed.title.isEmpty() && parsed.text.isEmpty())) return
        if (parsed.noise && !ringing && !settings.logNoise) return
        val seenBefore = repeats.knows(sbn.key)
        if (!repeats.isNew(sbn.key, parsed.title, parsed.text) && !ringing) return

        val alertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val quietChannel = ranking != null && ranking.importance in 1 until NotificationManager.IMPORTANCE_DEFAULT
        val silentUpdate = alertOnce || parsed.noise || quietChannel
        val rule = db.rules().get(sbn.packageName)
        val decision = when {
            ringing && AlertPlayer.isRingingFor(sbn.key) -> AlertDecision.Quiet(QuietReason.NO_RULE)
            !ringing && (parsed.noise || (alertOnce && seenBefore)) -> AlertDecision.Quiet(QuietReason.NO_RULE)
            else -> decide(sbn.packageName, rule, "${parsed.title} ${parsed.text}", ringing)
        }

        val name = appName(sbn.packageName)
        if (decision is AlertDecision.Play && rule != null) {
            lastAlert[sbn.packageName] = System.currentTimeMillis()
            AlertService.play(this, rule.toRequest(sbn.key, name, decision.route, decision.call))
        }
        log(sbn, ranking, parsed, name, decision, silentUpdate)
    }

    private suspend fun log(
        sbn: StatusBarNotification,
        ranking: Ranking?,
        parsed: ParsedNotification,
        name: String,
        decision: AlertDecision,
        silentUpdate: Boolean,
    ) {
        val pkg = sbn.packageName
        val channelId = sbn.notification.channelId.orEmpty()
        if (db.excluded().isExcluded(pkg)) return
        if (channelId.isNotEmpty() && db.blocked().isBlocked(pkg, channelId)) return
        if (hasHiddenWord(settings.hiddenWords, "${parsed.title} ${parsed.text}")) return

        if (silentUpdate && settings.keepLatestUpdate) {
            val latest = db.log().latestFor(sbn.key)
            if (latest != null && sbn.postTime - latest.postedAt < UPDATE_WINDOW_MILLIS) {
                db.log().replaceText(latest.id, parsed.title, parsed.text, sbn.postTime)
                sbn.notification.contentIntent?.let { LiveIntents.put(latest.id, it) }
                return
            }
        }
        val id = db.log().insert(
            LogEntry(
                sbnKey = sbn.key,
                pkg = pkg,
                appName = name,
                title = parsed.title,
                text = parsed.text,
                postedAt = sbn.postTime,
                outcome = outcomeOf(decision),
                channelId = channelId,
                channelName = ranking?.channel?.name?.toString().orEmpty(),
            ),
        )
        sbn.notification.contentIntent?.let { LiveIntents.put(id, it) }
    }

    private fun decide(pkg: String, rule: AppRule?, text: String, ringing: Boolean): AlertDecision {
        val am = getSystemService(AudioManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        val now = Calendar.getInstance()
        return AlertDecider.decide(
            rule = rule,
            text = text,
            masterOn = settings.alertsOn,
            sound = SoundState(
                canRing = am.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                    nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL,
                alarmMuted = am.isStreamMute(AudioManager.STREAM_ALARM),
                inCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION,
            ),
            quietHours = settings.quietHours,
            minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
            millisSinceLastAlert = System.currentTimeMillis() - (lastAlert[pkg] ?: 0L),
            cooldownMillis = TimeUnit.SECONDS.toMillis(settings.cooldownSeconds.toLong()),
            paused = settings.isPaused,
            ringingCall = ringing,
        )
    }

    private fun outcomeOf(decision: AlertDecision) = when (decision) {
        is AlertDecision.Play -> when {
            decision.call -> AlertOutcome.CALL
            decision.route == AlertRoute.ALARM -> AlertOutcome.RANG
            else -> AlertOutcome.BROKE_THROUGH
        }
        is AlertDecision.Quiet ->
            if (decision.reason == QuietReason.TOTAL_SILENCE) AlertOutcome.BLOCKED else AlertOutcome.NONE
    }

    private fun appName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(pkg)

    private suspend fun purge() {
        val days = settings.retentionDays
        if (days > 0) db.log().deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
    }

    companion object {
        private const val TAG = "Bleepp"
        private val SEEN_REASONS = setOf(REASON_CLICK, REASON_CANCEL, REASON_CANCEL_ALL, REASON_APP_CANCEL, REASON_APP_CANCEL_ALL)

        /** A silent update replaces its earlier entry only if that entry is this recent. */
        private const val UPDATE_WINDOW_MILLIS = 6 * 60 * 60 * 1000L

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        @Volatile private var instance: BleeppListener? = null

        /** The tap action of a notification that's still showing, even after Bleepp restarted. */
        fun activeIntent(key: String): PendingIntent? = runCatching {
            instance?.getActiveNotifications(arrayOf(key))?.firstOrNull()?.notification?.contentIntent
        }.getOrNull()

        fun isEnabled(context: Context): Boolean {
            val enabled = SystemSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val me = ComponentName(context, BleeppListener::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Hidden words use the same format as keyword rules, but hide instead of match. */
        fun hasHiddenWord(words: String, text: String): Boolean =
            AlertDecider.parseKeywords(words).any { text.contains(it, ignoreCase = true) }
    }
}

fun AppRule.toRequest(key: String, label: String, route: AlertRoute, call: Boolean = false) = AlertRequest(
    key = key,
    label = label,
    sound = if (call) callSound else sound,
    volumePercent = volume,
    route = route,
    repeat = repeat && !call,
    repeatEverySeconds = repeatEverySeconds,
    repeatMax = repeatMax,
    vibrate = vibrate,
    ringMillis = if (call) AlertPlayer.CALL_RING_MILLIS else 0L,
)
