package com.bleepp.listener

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings as SystemSettings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.bleepp.alert.AlertDecider
import com.bleepp.alert.AlertDecision
import com.bleepp.alert.AlertPlayer
import com.bleepp.alert.AlertRequest
import com.bleepp.alert.AlertRoute
import com.bleepp.alert.AlertService
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
    private val queue = Channel<StatusBarNotification>(Channel.UNLIMITED)
    private val repeats = RepeatFilter()
    private val lastAlert = ConcurrentHashMap<String, Long>()
    private val db by lazy { BleeppDb.get(this) }
    private val settings by lazy { Settings(this) }

    override fun onCreate() {
        super.onCreate()
        // One at a time, in order, so duplicate checks and cooldowns see notifications as they arrived
        scope.launch { for (sbn in queue) runCatching { handle(sbn) } }
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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != packageName) queue.trySend(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // An app clearing its own notification usually means it was read somewhere else
        if (reason in SEEN_REASONS) AlertPlayer.stopFor(this, sbn.key)
    }

    private suspend fun handle(sbn: StatusBarNotification) {
        val parsed = NotificationText.parse(sbn)
        if (parsed.summary || (parsed.title.isEmpty() && parsed.text.isEmpty())) return
        if (parsed.noise && !settings.logNoise) return
        val seenBefore = repeats.knows(sbn.key)
        if (!repeats.isNew(sbn.key, parsed.title, parsed.text)) return

        val alertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        val rule = db.rules().get(sbn.packageName)
        val decision = if (parsed.noise || (alertOnce && seenBefore)) {
            AlertDecision.Quiet(QuietReason.NO_RULE)
        } else {
            decide(sbn.packageName, rule, "${parsed.title} ${parsed.text}")
        }

        val name = appName(sbn.packageName)
        if (decision is AlertDecision.Play && rule != null) {
            lastAlert[sbn.packageName] = System.currentTimeMillis()
            AlertService.play(this, rule.toRequest(sbn.key, name, decision.route))
        }
        if (db.excluded().isExcluded(sbn.packageName)) return
        val id = db.log().insert(
            LogEntry(
                sbnKey = sbn.key,
                pkg = sbn.packageName,
                appName = name,
                title = parsed.title,
                text = parsed.text,
                postedAt = sbn.postTime,
                outcome = outcomeOf(decision),
            ),
        )
        sbn.notification.contentIntent?.let { LiveIntents.put(id, it) }
    }

    private fun decide(pkg: String, rule: AppRule?, text: String): AlertDecision {
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
        )
    }

    private fun outcomeOf(decision: AlertDecision) = when (decision) {
        is AlertDecision.Play ->
            if (decision.route == AlertRoute.ALARM) AlertOutcome.RANG else AlertOutcome.BROKE_THROUGH
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
        private val SEEN_REASONS = setOf(REASON_CLICK, REASON_CANCEL, REASON_CANCEL_ALL, REASON_APP_CANCEL, REASON_APP_CANCEL_ALL)

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
    }
}

fun AppRule.toRequest(key: String, label: String, route: AlertRoute) = AlertRequest(
    key = key,
    label = label,
    sound = sound,
    volumePercent = volume,
    route = route,
    repeat = repeat,
    repeatEverySeconds = repeatEverySeconds,
    repeatMax = repeatMax,
    vibrate = vibrate,
)
