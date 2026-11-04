package com.bleepp.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.bleepp.alert.Sounds
import com.bleepp.data.AppRule
import com.bleepp.ui.theme.Sea

@Composable
private fun soundName(sound: String): String =
    Sounds.builtIn.firstOrNull { it.id == sound }?.let { stringResource(it.label) } ?: stringResource(R.string.rule_custom_sound)

@Composable
fun AlertsScreen(vm: MainViewModel, onOpenRule: (String, String) -> Unit, bottomPadding: PaddingValues) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val alertsOn by vm.alertsOn.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    val matching = apps.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
    val chosen = matching.filter { it.pkg in rules }
    val others = matching.filterNot { it.pkg in rules }
    val line = stringResource(
        when {
            !status.hasAccess -> R.string.status_no_access
            !alertsOn -> R.string.status_off
            status.totalSilence -> R.string.status_total
            status.canRing -> R.string.status_ringing
            else -> R.string.status_silent
        },
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
        item { ScreenTitle(stringResource(R.string.alerts_title), line) }
        item {
            SeaCard {
                SwitchRow(stringResource(R.string.alerts_master), alertsOn, vm::setAlertsOn, icon = Icons.Outlined.NotificationsActive)
            }
        }
        item {
            Gap(6.dp)
            SeaField(search, { search = it }, stringResource(R.string.alerts_search), Modifier.padding(horizontal = 16.dp), leading = Icons.Outlined.Search)
        }
        item { SectionLabel(stringResource(R.string.alerts_chosen)) }
        if (chosen.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.alerts_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Sea.palette.muted,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(chosen, key = { "c" + it.pkg }) { app ->
            val rule = rules.getValue(app.pkg)
            AppRow(
                app,
                if (rule.enabled) stringResource(R.string.rule_summary, soundName(rule.sound), rule.volume) else stringResource(R.string.rule_off),
            ) { onOpenRule(app.pkg, app.name) }
        }
        item { SectionLabel(stringResource(R.string.alerts_other)) }
        items(others, key = { it.pkg }) { app -> AppRow(app, null) { onOpenRule(app.pkg, app.name) } }
    }
}

@Composable
private fun AppRow(app: InstalledApp, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, color = Sea.palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = Sea.palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleScreen(vm: MainViewModel, pkg: String, name: String, onBack: () -> Unit, bottomPadding: PaddingValues) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val excluded by vm.excluded.collectAsStateWithLifecycle()
    val saved = rules[pkg]
    val rule = saved ?: AppRule(pkg, enabled = false)
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.saveRule(rule.copy(sound = uri.toString(), enabled = true))
        }
    }
    var volume by remember(rule.volume) { mutableFloatStateOf(rule.volume.toFloat()) }
    var every by remember(rule.repeatEverySeconds) { mutableFloatStateOf(rule.repeatEverySeconds.toFloat()) }
    var max by remember(rule.repeatMax) { mutableFloatStateOf(rule.repeatMax.toFloat()) }
    var keywords by remember(pkg) { mutableStateOf(rule.keywords) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomPadding)) {
        BackBar(name, onBack, Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
        SeaCard {
            SwitchRow(stringResource(R.string.rule_enabled), rule.enabled, { vm.saveRule(rule.copy(enabled = it)) })
        }
        SectionLabel(stringResource(R.string.rule_sound))
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sounds.builtIn.forEach { sound ->
                Chip(stringResource(sound.label), rule.sound == sound.id) {
                    val changed = rule.copy(sound = sound.id)
                    vm.saveRule(changed)
                    vm.testRule(changed, name)
                }
            }
            Chip(stringResource(R.string.rule_pick_file), Sounds.isCustom(rule.sound)) { picker.launch(arrayOf("audio/*")) }
        }
        Gap()
        Row(Modifier.padding(horizontal = 16.dp)) {
            BeamButton(stringResource(R.string.rule_test), { vm.testRule(rule, name) }, icon = Icons.Outlined.PlayArrow)
        }
        Gap()
        SeaCard {
            SliderRow(
                stringResource(R.string.rule_volume) + " " + volume.toInt() + "%",
                volume, 10f..100f, { volume = it },
                onDone = { vm.saveRule(rule.copy(volume = volume.toInt())) },
            )
            Text(
                stringResource(R.string.rule_volume_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Sea.palette.muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            SwitchRow(stringResource(R.string.rule_vibrate), rule.vibrate, { vm.saveRule(rule.copy(vibrate = it)) })
        }
        SectionLabel(stringResource(R.string.rule_keywords))
        SeaField(
            keywords,
            {
                keywords = it
                vm.saveRule(rule.copy(keywords = it))
            },
            stringResource(R.string.rule_keywords_example),
            Modifier.padding(horizontal = 16.dp),
            singleLine = false,
        )
        Text(
            stringResource(R.string.rule_keywords_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Sea.palette.muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
        Gap()
        SeaCard {
            SwitchRow(stringResource(R.string.rule_repeat), rule.repeat, { vm.saveRule(rule.copy(repeat = it)) }, stringResource(R.string.rule_repeat_hint))
            if (rule.repeat) {
                SliderRow(
                    stringResource(R.string.rule_every, every.toInt()),
                    every, 10f..120f, { every = it },
                    onDone = { vm.saveRule(rule.copy(repeatEverySeconds = every.toInt())) },
                )
                SliderRow(
                    stringResource(R.string.rule_max, max.toInt()),
                    max, 2f..30f, { max = it },
                    onDone = { vm.saveRule(rule.copy(repeatMax = max.toInt())) },
                )
            }
        }
        SeaCard {
            SwitchRow(
                stringResource(R.string.rule_breakthrough),
                rule.breakthrough,
                { vm.saveRule(rule.copy(breakthrough = it)) },
                stringResource(R.string.rule_breakthrough_hint),
            )
        }
        SeaCard {
            SwitchRow(stringResource(R.string.rule_log), pkg !in excluded, { vm.setExcluded(pkg, !it) })
        }
        if (saved != null) {
            Gap()
            Row(Modifier.padding(horizontal = 16.dp)) {
                QuietButton(stringResource(R.string.rule_remove), {
                    vm.removeRule(pkg)
                    onBack()
                })
            }
        }
        Gap(24.dp)
    }
}
