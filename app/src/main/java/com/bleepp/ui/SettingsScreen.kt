package com.bleepp.ui

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SystemSettings
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleepp.BuildConfig
import com.bleepp.R
import com.bleepp.data.Languages
import com.bleepp.data.NoteKind
import com.bleepp.data.NoteSection
import com.bleepp.data.OfficialBuild
import com.bleepp.data.ReleaseNotes
import com.bleepp.data.UpdateChecker
import com.bleepp.ui.theme.Sea
import java.util.Calendar

private const val KOFI_URL = "https://ko-fi.com/vvanadium"
private const val AUTHOR_URL = "https://github.com/starlightjulie"

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

fun openNotificationAccess(context: Context) {
    runCatching { context.startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
}

fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}

@SuppressLint("BatteryLife")
private fun askBattery(context: Context) {
    runCatching {
        context.startActivity(Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }
}

private fun timeLabel(context: Context, minuteOfDay: Int): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(c.time)
}

private fun pickTime(context: Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, DateFormat.is24HourFormat(context)).show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onAppearance: () -> Unit, onAskPost: () -> Unit, bottomPadding: PaddingValues) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val updateCheck by vm.updateCheck.collectAsStateWithLifecycle()
    val s = vm.settings
    var quietOn by remember { mutableStateOf(s.quietEnabled) }
    var quietStart by remember { mutableIntStateOf(s.quietStart) }
    var quietEnd by remember { mutableIntStateOf(s.quietEnd) }
    var cooldown by remember { mutableFloatStateOf(s.cooldownSeconds.toFloat()) }
    var retention by remember { mutableIntStateOf(s.retentionDays) }
    var logNoise by remember { mutableStateOf(s.logNoise) }
    var autoCheck by remember { mutableStateOf(s.checkUpdates) }
    var hours by remember { mutableIntStateOf(s.updateIntervalHours) }
    var confirmClear by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showRestricted by remember { mutableStateOf(false) }
    val official = remember { OfficialBuild.isOfficial(context) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomPadding)) {
        ScreenTitle(stringResource(R.string.tab_settings))

        SeaCard {
            SettingRow(stringResource(R.string.appearance), icon = Icons.Outlined.Palette, onClick = onAppearance)
        }

        SectionLabel(stringResource(R.string.set_setup))
        SeaCard {
            SetupRow(
                stringResource(R.string.setup_access),
                stringResource(if (status.hasAccess) R.string.setup_access_on else R.string.setup_access_off),
                status.hasAccess,
                Icons.Outlined.Visibility,
            ) { openNotificationAccess(context) }
            if (!status.hasAccess) {
                SettingRow(stringResource(R.string.setup_restricted_title), onClick = { showRestricted = true }, icon = Icons.Outlined.WarningAmber)
            }
            SetupRow(stringResource(R.string.setup_battery), stringResource(R.string.setup_battery_hint), status.batteryFree, Icons.Outlined.BatteryFull) { askBattery(context) }
            SetupRow(stringResource(R.string.setup_post), stringResource(R.string.setup_post_hint), status.canPost, Icons.Outlined.NotificationsNone, onAskPost)
        }

        SectionLabel(stringResource(R.string.set_quiet))
        SeaCard {
            SwitchRow(stringResource(R.string.quiet_on), quietOn, {
                quietOn = it
                s.quietEnabled = it
            }, icon = Icons.Outlined.Bedtime)
            if (quietOn) {
                SettingRow(stringResource(R.string.quiet_from), onClick = {
                    pickTime(context, quietStart) {
                        quietStart = it
                        s.quietStart = it
                    }
                }) { Text(timeLabel(context, quietStart), color = Sea.palette.beam) }
                SettingRow(stringResource(R.string.quiet_to), onClick = {
                    pickTime(context, quietEnd) {
                        quietEnd = it
                        s.quietEnd = it
                    }
                }) { Text(timeLabel(context, quietEnd), color = Sea.palette.beam) }
            }
            SliderRow(
                stringResource(R.string.cooldown_value, cooldown.toInt()),
                cooldown, 0f..120f, { cooldown = it },
                onDone = { s.cooldownSeconds = cooldown.toInt() },
            )
        }

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
            Gap(6.dp)
            SwitchRow(stringResource(R.string.log_noise), logNoise, {
                logNoise = it
                s.logNoise = it
            })
            SettingRow(stringResource(R.string.log_clear), onClick = { confirmClear = true })
        }

        SectionLabel(stringResource(R.string.set_language))
        SeaCard {
            SettingRow(
                stringResource(R.string.set_language),
                Languages.currentLanguage()?.nativeName ?: stringResource(R.string.lang_system),
                Icons.Outlined.Language,
                onClick = { pickLanguage = true },
            )
        }

        SectionLabel(stringResource(R.string.set_updates))
        SeaCard {
            SwitchRow(stringResource(R.string.upd_auto), autoCheck, {
                autoCheck = it
                vm.setUpdateSchedule(it, hours)
            })
            if (autoCheck) {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24, 72).forEach { h ->
                        Chip(stringResource(R.string.n_hours, h), hours == h) {
                            hours = h
                            vm.setUpdateSchedule(autoCheck, h)
                        }
                    }
                }
                Gap(6.dp)
            }
            val state = updateCheck
            SettingRow(
                stringResource(R.string.upd_check),
                when (state) {
                    UpdateCheckState.Idle -> null
                    UpdateCheckState.Checking -> stringResource(R.string.upd_checking)
                    is UpdateCheckState.Done -> state.message.text()
                },
                onClick = { vm.checkForUpdate(manual = true) },
            )
        }

        SectionLabel(stringResource(R.string.set_about))
        SeaCard {
            SettingRow(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                stringResource(if (official) R.string.about_official else R.string.about_unofficial),
                if (official) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            )
            SettingRow(stringResource(R.string.whats_new), onClick = { showNotes = true })
            SettingRow(stringResource(R.string.about_credit), stringResource(R.string.about_licence), onClick = { open(context, AUTHOR_URL) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Sea.palette.muted)
            }
            SettingRow(stringResource(R.string.about_kofi), icon = Icons.Outlined.Favorite, onClick = { open(context, KOFI_URL) })
            SettingRow(stringResource(R.string.about_github), onClick = { open(context, UpdateChecker.REPO_URL) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Sea.palette.muted)
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
    if (pickLanguage) LanguageDialog { pickLanguage = false }
    if (showRestricted) RestrictedDialog { showRestricted = false }
    if (showNotes) {
        val sections = remember { ReleaseNotes.bundled(context, BuildConfig.VERSION_NAME) }
        NotesDialog(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME), sections, onDismiss = { showNotes = false })
    }
}

@Composable
private fun SetupRow(title: String, summary: String, done: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onFix: () -> Unit) {
    SettingRow(title, summary, icon, onClick = if (done) null else onFix) {
        if (done) {
            Icon(Icons.Outlined.CheckCircle, stringResource(R.string.setup_done), tint = Sea.palette.beam)
        } else {
            Text(stringResource(R.string.setup_fix), style = MaterialTheme.typography.titleSmall, color = Sea.palette.beam)
        }
    }
}

@Composable
fun RestrictedDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_restricted_title)) },
        text = { Text(stringResource(R.string.setup_restricted_body)) },
        confirmButton = {
            TextButton(onClick = {
                openAppInfo(context)
                onDismiss()
            }) { Text(stringResource(R.string.setup_open_info)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = { Text(stringResource(R.string.set_language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LanguageRow(stringResource(R.string.lang_system)) {
                    Languages.apply("")
                    onDismiss()
                }
                Languages.all.forEach { language ->
                    LanguageRow(language.nativeName) {
                        Languages.apply(language.tag)
                        onDismiss()
                    }
                }
            }
        },
    )
}

@Composable
private fun LanguageRow(name: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NotesDialog(title: String, sections: List<NoteSection>, onDismiss: () -> Unit, actions: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sections.forEach { section ->
                    Text(
                        stringResource(
                            when (section.kind) {
                                NoteKind.NEW -> R.string.note_new
                                NoteKind.FIXED -> R.string.note_fixed
                                NoteKind.IMPROVED -> R.string.note_improved
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                    section.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { actions?.invoke() ?: TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
