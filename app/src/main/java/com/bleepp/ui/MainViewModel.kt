package com.bleepp.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
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
import com.bleepp.data.AppRule
import com.bleepp.data.AppUpdate
import com.bleepp.data.BleeppDb
import com.bleepp.data.ExcludedApp
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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
    private val _filterPkg = MutableStateFlow<String?>(null)
    val filterPkg: StateFlow<String?> = _filterPkg.asStateFlow()

    val log: StateFlow<List<LogEntry>?> = combine(_query, _filterPkg) { q, pkg -> q to pkg }
        .flatMapLatest { (q, pkg) -> db.log().observe(q.trim(), pkg, LOG_LIMIT) }
        .map<List<LogEntry>, List<LogEntry>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val loggedApps = db.log().observeApps().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<Map<String, AppRule>> = db.rules().observeAll()
        .map { list -> list.associateBy { it.pkg } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val excluded: StateFlow<Set<String>> = db.excluded().observeAll()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _status = MutableStateFlow(PhoneStatus())
    val status: StateFlow<PhoneStatus> = _status.asStateFlow()

    private val _alertsOn = MutableStateFlow(settings.alertsOn)
    val alertsOn: StateFlow<Boolean> = _alertsOn.asStateFlow()

    private val _update = MutableStateFlow<AppUpdate?>(null)
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()
    private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

    init {
        refreshApps()
        refreshStatus()
        if (settings.checkUpdates) checkForUpdate(manual = false)
    }

    /** Apps can be installed or removed while Bleepp is in the background, so this runs on every return. */
    fun refreshApps() {
        viewModelScope.launch { _apps.value = withContext(Dispatchers.IO) { loadApps() } }
    }

    fun setQuery(text: String) { _query.value = text }
    fun setFilter(pkg: String?) { _filterPkg.value = pkg }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch { db.log().delete(entry.id) }
    fun clearLog() = viewModelScope.launch { db.log().clear() }

    fun setAlertsOn(on: Boolean) {
        settings.alertsOn = on
        _alertsOn.value = on
    }

    fun saveRule(rule: AppRule) = viewModelScope.launch { db.rules().save(rule) }
    fun removeRule(pkg: String) = viewModelScope.launch { db.rules().delete(pkg) }

    fun setExcluded(pkg: String, excluded: Boolean) = viewModelScope.launch {
        if (excluded) db.excluded().add(ExcludedApp(pkg)) else db.excluded().remove(pkg)
    }

    /** Plays a rule's sound now, on the route the phone would really use for it. */
    fun testRule(rule: AppRule, label: String) {
        val app = getApplication<Application>()
        val alarmMuted = app.getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_ALARM)
        AlertService.play(
            app,
            AlertRequest(
                key = "test",
                label = label,
                sound = rule.sound,
                volumePercent = rule.volume,
                route = if (alarmMuted && rule.breakthrough) AlertRoute.BREAKTHROUGH else AlertRoute.ALARM,
                vibrate = rule.vibrate,
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
        settings.savedLooksJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedLook.serializer()), saved)
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
        ?.let { runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedLook.serializer()), it) }.getOrNull() }
        .orEmpty()

    private companion object {
        const val LOG_LIMIT = 1000
    }
}
