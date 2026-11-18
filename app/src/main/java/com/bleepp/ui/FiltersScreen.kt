package com.bleepp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleepp.R
import com.bleepp.ui.theme.Sea

private enum class Picker { FEATURES, BLOCK_APP, BOOKMARK_APP }

/** Lock options need a screen lock or fingerprint on the phone to exist first. */
fun canLock(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    ) == BiometricManager.BIOMETRIC_SUCCESS

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(vm: MainViewModel, bottomPadding: PaddingValues) {
    val context = LocalContext.current
    val s = vm.settings
    val blocked by vm.blockedFeatures.collectAsStateWithLifecycle()
    val excluded by vm.excluded.collectAsStateWithLifecycle()
    val bookmarked by vm.bookmarkedApps.collectAsStateWithLifecycle()
    val installed by vm.apps.collectAsStateWithLifecycle()
    val logged by vm.loggedApps.collectAsStateWithLifecycle()
    var keepLatest by remember { mutableStateOf(s.keepLatestUpdate) }
    var hiddenWords by remember { mutableStateOf(s.hiddenWords) }
    var retention by remember { mutableIntStateOf(s.retentionDays) }
    var logNoise by remember { mutableStateOf(s.logNoise) }
    var lock by remember { mutableStateOf(s.lockLog) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val lockAvailable = remember { canLock(context) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val names = remember(installed, logged) {
        logged.associate { it.pkg to it.appName } + installed.associate { it.pkg to it.name }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomPadding)) {
        ScreenTitle(stringResource(R.string.tab_filters), stringResource(R.string.filters_subtitle))

        SeaCard {
            SwitchRow(
                stringResource(R.string.keep_latest),
                keepLatest,
                {
                    keepLatest = it
                    s.keepLatestUpdate = it
                },
                stringResource(R.string.keep_latest_hint),
                icon = Icons.Outlined.Layers,
            )
        }

        SectionLabel(stringResource(R.string.blocked_features))
        Hint(stringResource(R.string.blocked_features_hint))
        SeaCard {
            blocked.forEach { feature ->
                RemovableRow(feature.channelName, feature.appName, feature.pkg) {
                    vm.setFeatureBlocked(feature.pkg, feature.appName, feature.channelId, feature.channelName, false)
                }
            }
            SettingRow(stringResource(R.string.block_a_feature), icon = Icons.Outlined.Add, onClick = { picker = Picker.FEATURES })
        }

        SectionLabel(stringResource(R.string.blocked_apps))
        SeaCard {
            excluded.sortedBy { names[it]?.lowercase() ?: it }.forEach { pkg ->
                RemovableRow(names[pkg] ?: pkg, null, pkg) { vm.setExcluded(pkg, false) }
            }
            SettingRow(stringResource(R.string.block_an_app), icon = Icons.Outlined.Add, onClick = { picker = Picker.BLOCK_APP })
        }

        SectionLabel(stringResource(R.string.bookmarked_apps))
        Hint(stringResource(R.string.bookmarked_apps_hint))
        SeaCard {
            bookmarked.sortedBy { names[it]?.lowercase() ?: it }.forEach { pkg ->
                RemovableRow(names[pkg] ?: pkg, null, pkg) { vm.setAppBookmarked(pkg, false) }
            }
            SettingRow(stringResource(R.string.bookmark_an_app), icon = Icons.Outlined.Add, onClick = { picker = Picker.BOOKMARK_APP })
        }

        SectionLabel(stringResource(R.string.hidden_words))
        SeaField(
            hiddenWords,
            {
                hiddenWords = it
                s.hiddenWords = it
            },
            stringResource(R.string.hidden_words_example),
            Modifier.padding(horizontal = 16.dp),
            singleLine = false,
        )
        Hint(stringResource(R.string.hidden_words_hint))

        SectionLabel(stringResource(R.string.set_log))
        SeaCard {
            Text(
                stringResource(R.string.log_private),
                style = MaterialTheme.typography.bodySmall,
                color = Sea.palette.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Text(
                stringResource(R.string.keep_for),
                style = MaterialTheme.typography.titleSmall,
                color = Sea.palette.text,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90, 365, 0).forEach { days ->
                    Chip(if (days == 0) stringResource(R.string.keep_forever) else stringResource(R.string.keep_days, days), retention == days) {
                        retention = days
                        s.retentionDays = days
                    }
                }
            }
            Text(
                stringResource(R.string.bookmarks_kept),
                style = MaterialTheme.typography.bodySmall,
                color = Sea.palette.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Gap(6.dp)
            SwitchRow(stringResource(R.string.log_noise), logNoise, {
                logNoise = it
                s.logNoise = it
            })
            SettingRow(stringResource(R.string.export_csv), stringResource(R.string.export_csv_hint), Icons.Outlined.FileDownload, onClick = {
                exporter.launch("bleepp-log.csv")
            })
            SettingRow(stringResource(R.string.log_clear), icon = Icons.Outlined.DeleteSweep, onClick = { confirmClear = true })
        }

        SectionLabel(stringResource(R.string.privacy))
        SeaCard {
            if (lockAvailable) {
                SwitchRow(stringResource(R.string.lock_log), lock, {
                    lock = it
                    vm.setLockLog(it)
                }, stringResource(R.string.lock_log_hint), icon = Icons.Outlined.Fingerprint)
            } else {
                SettingRow(stringResource(R.string.lock_log), stringResource(R.string.lock_unavailable), Icons.Outlined.Fingerprint)
            }
        }
        Gap(24.dp)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.log_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearLog()
                    confirmClear = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    when (picker) {
        Picker.FEATURES -> FeaturePicker(vm) { picker = null }
        Picker.BLOCK_APP -> AppPicker(
            stringResource(R.string.block_an_app),
            appChoices(installed, logged).filterNot { it.pkg in excluded },
            onDismiss = { picker = null },
        ) {
            vm.setExcluded(it, true)
            picker = null
        }
        Picker.BOOKMARK_APP -> AppPicker(
            stringResource(R.string.bookmark_an_app),
            appChoices(installed, logged).filterNot { it.pkg in bookmarked },
            onDismiss = { picker = null },
        ) {
            vm.setAppBookmarked(it, true)
            picker = null
        }
        null -> Unit
    }
}

/** Installed apps plus anything that has notified but has no launcher icon, like Android System. */
private fun appChoices(installed: List<InstalledApp>, logged: List<com.bleepp.data.LoggedApp>): List<InstalledApp> =
    (installed + logged.map { InstalledApp(it.pkg, it.appName) }).distinctBy { it.pkg }.sortedBy { it.name.lowercase() }

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Sea.palette.muted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 6.dp),
    )
}

@Composable
private fun RemovableRow(title: String, summary: String?, pkg: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Sea.palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = Sea.palette.muted, maxLines = 1)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove), tint = Sea.palette.muted) }
    }
}

@Composable
private fun AppPicker(title: String, apps: List<InstalledApp>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val shown = apps.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SeaField(search, { search = it }, stringResource(R.string.alerts_search), leading = Icons.Outlined.Search)
                Gap(8.dp)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(app.pkg) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Every feature seen in the log so far, grouped by app, each with its own on/off switch. */
@Composable
private fun FeaturePicker(vm: MainViewModel, onDismiss: () -> Unit) {
    val features by vm.features.collectAsStateWithLifecycle()
    val blocked by vm.blockedFeatures.collectAsStateWithLifecycle()
    val blockedKeys = blocked.map { it.pkg to it.channelId }.toSet()
    var search by remember { mutableStateOf("") }
    val shown = features.filter {
        search.isBlank() || it.appName.contains(search.trim(), true) || it.channelName.contains(search.trim(), true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_a_feature)) },
        text = {
            Column {
                SeaField(search, { search = it }, stringResource(R.string.feature_search), leading = Icons.Outlined.Search)
                Gap(8.dp)
                if (features.isEmpty()) {
                    Text(stringResource(R.string.features_empty), style = MaterialTheme.typography.bodyMedium, color = Sea.palette.muted)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.pkg + "/" + it.channelId }) { f ->
                        val isBlocked = (f.pkg to f.channelId) in blockedKeys
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setFeatureBlocked(f.pkg, f.appName, f.channelId, f.channelName.ifEmpty { f.channelId }, !isBlocked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(f.pkg, size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.channelName.ifEmpty { f.channelId }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.feature_count, f.appName, f.count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Sea.palette.muted,
                                    maxLines = 1,
                                )
                            }
                            Switch(
                                checked = isBlocked,
                                onCheckedChange = { vm.setFeatureBlocked(f.pkg, f.appName, f.channelId, f.channelName.ifEmpty { f.channelId }, it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Sea.palette.beam, checkedThumbColor = Sea.palette.onBeam),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
