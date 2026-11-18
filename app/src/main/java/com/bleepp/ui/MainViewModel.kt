package com.bleepp.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bleepp.BuildConfig
import com.bleepp.LauncherIcons
import com.bleepp.R
import com.bleepp.UpdateWorker
import com.bleepp.alert.AlertPlayer
import com.bleepp.alert.AlertRequest
import com.bleepp.alert.AlertRoute
import com.bleepp.alert.AlertService
import com.bleepp.alert.Pause
import com.bleepp.data.AppRule
import com.bleepp.data.AppUpdate
import com.bleepp.data.BackupFile
import com.bleepp.data.BleeppDb
import com.bleepp.data.BlockedFeature
import com.bleepp.data.BookmarkedApp
import com.bleepp.data.ExcludedApp
import com.bleepp.data.LogCsv
import com.bleepp.data.LogEntry
import com.bleepp.data.Settings
import com.bleepp.data.UiText
import com.bleepp.data.UpdateChecker
import com.bleepp.data.UpdateResult
import com.bleepp.listener.BleeppListener
import com.bleepp.ui.theme.LauncherIcon
import com.bleepp.ui.theme.Look
import com.bleepp.ui.theme.SavedLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Date

data class InstalledApp(val pkg: String, val name: String)

/** The phone's sound state and which setup steps are done, refreshed while the app is open. */
data class PhoneStatus(
    val hasAccess: Boolean = true,
    val canPost: Boolean = true,
    val batteryFree: Boolean = true,
    val canRing: Boolean = false,
    val totalSilence: Boolean = false,
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Done(val message: UiText) : UpdateCheckState
}

/** A short message at the bottom of the screen, optionally with an Undo button. */
class Snack(val text: UiText, val undo: (() -> Unit)? = null)

/** Which slice of the log is showing: everything, one app, or bookmarked notifications. */
sealed interface LogFilter {
    data object All : LogFilter
    data object Saved : LogFilter
    data class App(val pkg: String) : LogFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val db = BleeppDb.get(app)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _look = MutableStateFlow(loadLook())
    val look: StateFlow<Look> = _look.asStateFlow()

    private val _savedLooks = MutableStateFlow(loadSavedLooks())
    val savedLooks: StateFlow<List<SavedLook>> = _savedLooks.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow<LogFilter>(LogFilter.All)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    val log: StateFlow<List<LogEntry>?> = combine(_query, _filter) { q, f -> q to f }
        .flatMapLatest { (q, f) ->
            db.log().observe(q.trim(), (f as? LogFilter.App)?.pkg, f == LogFilter.Saved, LOG_LIMIT)
        }
        .map<List<LogEntry>, List<LogEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val loggedApps = db.log().observeApps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val features = db.log().observeFeatures().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedCount = db.log().observeSavedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rules: StateFlow<Map<String, AppRule>> = db.rules().observeAll()
        .map { list -> list.associateBy { it.pkg } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val excluded: StateFlow<Set<String>> = db.excluded().observeAll().map { it.toSet() }.eagerly(emptySet())
    val blockedFeatures: StateFlow<List<BlockedFeature>> = db.blocked().observeAll().eagerly(emptyList())
    val bookmarkedApps: StateFlow<Set<String>> = db.bookmarkedApps().observeAll().map { it.toSet() }.eagerly(emptySet())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _status = MutableStateFlow(PhoneStatus())
    val status: StateFlow<PhoneStatus> = _status.asStateFlow()

    private val _alertsOn = MutableStateFlow(settings.alertsOn)
    val alertsOn: StateFlow<Boolean> = _alertsOn.asStateFlow()
    val pausedUntil: StateFlow<Long> = Pause.until

    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()
    private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    /** The log stays hidden until a fingerprint or screen lock check, when the lock is on. */
    private val _unlocked = MutableStateFlow(!settings.lockLog)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _snacks = Channel<Snack>(Channel.BUFFERED)
    val snacks = _snacks.receiveAsFlow()

    init {
        Pause.load(app)
        refreshApps()
        refreshStatus()
        if (settings.checkUpdates) checkForUpdate(manual = false)
    }

    private fun <T> Flow<T>.eagerly(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    /** Apps can be installed or removed while Bleepp is in the background, so this runs on every return. */
    fun refreshApps() {
        viewModelScope.launch { _apps.value = withContext(Dispatchers.IO) { loadApps() } }
    }

    fun setQuery(text: String) { _query.value = text }
    fun setFilter(filter: LogFilter) { _filter.value = filter }

    fun deleteEntries(entries: List<LogEntry>) = viewModelScope.launch {
        db.log().delete(entries.map { it.id })
        _snacks.trySend(Snack(UiText(R.string.deleted_n, entries.size)) { viewModelScope.launch { db.log().restore(entries) } })
    }

    fun setBookmarked(entries: List<LogEntry>, bookmarked: Boolean) = viewModelScope.launch {
        db.log().setBookmarked(entries.map { it.id }, bookmarked)
        _snacks.trySend(Snack(UiText(if (bookmarked) R.string.bookmarked_n else R.string.unbookmarked_n, entries.size)))
    }

    fun clearLog() = viewModelScope.launch { db.log().clearUnsaved() }

    /** Stops logging this kind of notification, and tidies away the ones already logged. */
    fun blockFeature(entry: LogEntry) = viewModelScope.launch {
        if (entry.channelId.isEmpty()) return@launch
        db.blocked().add(BlockedFeature(entry.pkg, entry.channelId, entry.channelName.ifEmpty { entry.channelId }, entry.appName))
        db.log().deleteFeature(entry.pkg, entry.channelId)
        _snacks.trySend(Snack(UiText(R.string.feature_blocked, entry.channelName.ifEmpty { entry.channelId })))
    }

    fun setFeatureBlocked(pkg: String, appName: String, channelId: String, channelName: String, blocked: Boolean) = viewModelScope.launch {
        if (blocked) db.blocked().add(BlockedFeature(pkg, channelId, channelName, appName)) else db.blocked().remove(pkg, channelId)
    }

    fun setAppBookmarked(pkg: String, bookmarked: Boolean) = viewModelScope.launch {
        if (bookmarked) db.bookmarkedApps().add(BookmarkedApp(pkg)) else db.bookmarkedApps().remove(pkg)
    }

    fun setAlertsOn(on: Boolean) {
        settings.alertsOn = on
        _alertsOn.value = on
    }

    fun pauseFor(minutes: Int) = Pause.pauseFor(getApplication(), minutes * 60_000L)
    fun pauseUntilMorning() = Pause.pauseUntilMorning(getApplication())
    fun pauseIndefinitely() = Pause.pauseIndefinitely(getApplication())
    fun resume() = Pause.resume(getApplication())

    fun saveRule(rule: AppRule) = viewModelScope.launch { db.rules().save(rule) }
    fun removeRule(pkg: String) = viewModelScope.launch { db.rules().delete(pkg) }

    fun setExcluded(pkg: String, excluded: Boolean) = viewModelScope.launch {
        if (excluded) db.excluded().add(ExcludedApp(pkg)) else db.excluded().remove(pkg)
    }

    /** Plays a rule's sound now, on the route the phone would really use for it. */
    fun testRule(rule: AppRule, label: String, call: Boolean = false) {
        val app = getApplication<Application>()
        val alarmMuted = app.getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_ALARM)
        AlertService.play(
            app,
            AlertRequest(
                key = "test",
                label = label,
                sound = if (call) rule.callSound else rule.sound,
                volumePercent = rule.volume,
                route = if (alarmMuted && rule.breakthrough) AlertRoute.BREAKTHROUGH else AlertRoute.ALARM,
                vibrate = rule.vibrate,
                // Long enough to hear the ring loop round once
                ringMillis = if (call) 6_000L else 0L,
            ),
        )
    }

    fun stopTest() = AlertPlayer.stop(getApplication())

    fun refreshStatus() {
        val app = getApplication<Application>()
        val am = app.getSystemService(AudioManager::class.java)
        val nm = app.getSystemService(NotificationManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        _status.value = PhoneStatus(
            hasAccess = BleeppListener.isEnabled(app),
            canPost = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            batteryFree = power.isIgnoringBatteryOptimizations(app.packageName),
            canRing = am.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL,
            totalSilence = am.isStreamMute(AudioManager.STREAM_ALARM),
        )
        // A timed pause can end while the screen is open
        Pause.load(app)
    }

    fun setLockLog(on: Boolean) {
        settings.lockLog = on
        if (!on) _unlocked.value = true
    }

    fun unlock() { _unlocked.value = true }

    /** Called when Bleepp leaves the screen, so the log needs unlocking again next time. */
    fun relock() {
        if (settings.lockLog) _unlocked.value = false
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val dates = DateFormat.getDateFormat(app)
                val times = DateFormat.getTimeFormat(app)
                val entries = db.log().all()
                BackupFile.write(app, uri, LogCsv.encode(entries) { "${dates.format(Date(it))} ${times.format(Date(it))}" })
                entries.size
            }
        }
        _snacks.trySend(Snack(result.fold({ UiText(R.string.exported_n, it) }, { UiText(R.string.file_failed) })))
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val ok = runCatching {
            withContext(Dispatchers.IO) { BackupFile.write(app, uri, BackupFile.encode(BackupFile.build(app, _look.value, _savedLooks.value))) }
        }.isSuccess
        _snacks.trySend(Snack(UiText(if (ok) R.string.backup_saved else R.string.file_failed)))
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val backup = runCatching { withContext(Dispatchers.IO) { BackupFile.decode(BackupFile.read(app, uri)) } }.getOrNull()
        if (backup == null) {
            _snacks.trySend(Snack(UiText(R.string.restore_failed)))
            return@launch
        }
        withContext(Dispatchers.IO) { BackupFile.restore(app, backup) }
        backup.look?.let { look -> setLook { look } }
        _savedLooks.value = backup.savedLooks
        persistLooks(backup.savedLooks)
        _alertsOn.value = settings.alertsOn
        _unlocked.value = true
        _snacks.trySend(Snack(UiText(R.string.restore_done)))
    }

    fun setLook(change: (Look) -> Look) {
        _look.update(change)
        settings.themeJson = json.encodeToString(Look.serializer(), _look.value)
    }

    fun applyIcon(icon: LauncherIcon) = LauncherIcons.apply(getApplication(), icon)

    fun saveLook(name: String) {
        val saved = _savedLooks.value.filterNot { it.name == name } + SavedLook(name, _look.value)
        _savedLooks.value = saved
        persistLooks(saved)
    }

    fun deleteLook(name: String) {
        val saved = _savedLooks.value.filterNot { it.name == name }
        _savedLooks.value = saved
        persistLooks(saved)
    }

    private fun persistLooks(saved: List<SavedLook>) {
        settings.savedLooksJson = json.encodeToString(ListSerializer(SavedLook.serializer()), saved)
    }

    fun setUpdateSchedule(enabled: Boolean, hours: Int) {
        settings.checkUpdates = enabled
        settings.updateIntervalHours = hours
        UpdateWorker.schedule(getApplication(), replace = true)
    }

    fun checkForUpdate(manual: Boolean) = viewModelScope.launch {
        if (manual) _updateCheck.value = UpdateCheckState.Checking
        when (val result = UpdateChecker.check(BuildConfig.VERSION_NAME)) {
            is UpdateResult.Available -> {
                if (manual || settings.snoozedVersion != result.update.version) _update.value = result.update
                if (manual) _updateCheck.value = UpdateCheckState.Idle
            }
            UpdateResult.UpToDate -> if (manual) _updateCheck.value = UpdateCheckState.Done(UiText(R.string.upd_latest))
            is UpdateResult.Failed -> if (manual) _updateCheck.value = UpdateCheckState.Done(result.reason)
        }
    }

    fun snoozeUpdate() {
        settings.snoozedVersion = _update.value?.version
        _update.value = null
    }

    fun dismissUpdate() { _update.value = null }

    private fun loadApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != getApplication<Application>().packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.name.lowercase() }
    }

    private fun loadLook(): Look = settings.themeJson
        ?.let { runCatching { json.decodeFromString(Look.serializer(), it) }.getOrNull() }
        ?: Look()

    private fun loadSavedLooks(): List<SavedLook> = settings.savedLooksJson
        ?.let { runCatching { json.decodeFromString(ListSerializer(SavedLook.serializer()), it) }.getOrNull() }
        .orEmpty()

    private companion object {
        const val LOG_LIMIT = 1000
    }
}
