package com.bleepp.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleepp.R
import com.bleepp.data.AlertOutcome
import com.bleepp.data.LogEntry
import com.bleepp.listener.NotificationOpener
import com.bleepp.ui.theme.Sea
import java.util.Calendar
import java.util.Date

/** Messages keep the direction of their own language, whatever language the app is in. */
private fun TextStyle.followContent() = copy(textDirection = TextDirection.Content)

@Composable
private fun partOfDayTitle(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.log_morning
            in 12..16 -> R.string.log_afternoon
            in 17..20 -> R.string.log_evening
            else -> R.string.log_night
        },
    )
}

@Composable
fun LogScreen(vm: MainViewModel, onAppearance: () -> Unit, bottomPadding: PaddingValues) {
    val entries by vm.log.collectAsStateWithLifecycle()
    val apps by vm.loggedApps.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filterPkg.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<LogEntry?>(null) }
    val total = apps.sumOf { it.count }
    val appOnlyMessage = stringResource(R.string.open_app_only)
    val failedMessage = stringResource(R.string.open_failed)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
        item {
            ScreenTitle(partOfDayTitle(), stringResource(R.string.log_count, total)) {
                IconButton(onClick = onAppearance) {
                    Icon(Icons.Outlined.Palette, stringResource(R.string.appearance), tint = Sea.palette.text)
                }
            }
        }
        item {
            SeaField(query, vm::setQuery, stringResource(R.string.log_search), Modifier.padding(horizontal = 16.dp), leading = Icons.Outlined.Search)
        }
        if (apps.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Chip(stringResource(R.string.log_all_apps), filter == null) { vm.setFilter(null) } }
                    items(apps, key = { it.pkg }) { app ->
                        Chip(app.appName, filter == app.pkg) { vm.setFilter(if (filter == app.pkg) null else app.pkg) }
                    }
                }
            }
        } else {
            item { Gap(10.dp) }
        }
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> item { EmptyLog(searching = query.isNotBlank() || filter != null) }
            else -> {
                var lastDay = ""
                list.forEachIndexed { index, entry ->
                    val day = dayLabelKey(entry.postedAt)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") { DayLabel(entry.postedAt) }
                    }
                    item(key = entry.id) {
                        LogRow(
                            entry = entry,
                            lit = index == 0 && query.isBlank() && filter == null,
                            onOpen = {
                                when (NotificationOpener.open(context, entry)) {
                                    NotificationOpener.Result.EXACT -> Unit
                                    NotificationOpener.Result.APP -> Toast.makeText(context, appOnlyMessage, Toast.LENGTH_SHORT).show()
                                    NotificationOpener.Result.FAILED -> Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongPress = { deleting = entry },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(entry.title.ifEmpty { entry.appName }, style = LocalTextStyle.current.followContent()) },
            text = { Text(entry.text, style = LocalTextStyle.current.followContent()) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entry)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun dayLabelKey(time: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = time }
    return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
}

@Composable
private fun DayLabel(time: Long) {
    val context = LocalContext.current
    val text = when {
        DateUtils.isToday(time) -> stringResource(R.string.log_today)
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.log_yesterday)
        else -> DateFormat.getMediumDateFormat(context).format(Date(time))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Sea.palette.muted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogRow(entry: LogEntry, lit: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val background = if (lit) Sea.palette.beam else Sea.palette.swell.copy(alpha = 0.88f)
    val main = if (lit) Sea.palette.onBeam else Sea.palette.text
    val soft = if (lit) Sea.palette.onBeam.copy(alpha = 0.75f) else Sea.palette.muted
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(Sea.shape)
            .background(background)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(entry.pkg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.appName,
                    style = MaterialTheme.typography.labelMedium,
                    color = soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(DateFormat.getTimeFormat(context).format(Date(entry.postedAt)), style = MaterialTheme.typography.labelMedium, color = soft)
            }
            if (entry.title.isNotEmpty()) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall.followContent(), color = main, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (entry.text.isNotEmpty()) {
                Text(entry.text, style = MaterialTheme.typography.bodyMedium.followContent(), color = main, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Stamp(entry.outcome, soft)
        }
    }
}

@Composable
private fun Stamp(outcome: Int, colour: androidx.compose.ui.graphics.Color) {
    val text = when (outcome) {
        AlertOutcome.RANG -> R.string.stamp_rang
        AlertOutcome.BROKE_THROUGH -> R.string.stamp_broke
        AlertOutcome.BLOCKED -> R.string.stamp_blocked
        else -> return
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = colour, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun EmptyLog(searching: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (searching) {
                Text(stringResource(R.string.log_no_results), style = MaterialTheme.typography.bodyMedium, color = Sea.palette.muted)
            } else {
                Text(stringResource(R.string.log_empty_title), style = MaterialTheme.typography.headlineSmall, color = Sea.palette.text)
                Gap(6.dp)
                Text(stringResource(R.string.log_empty_body), style = MaterialTheme.typography.bodyMedium, color = Sea.palette.muted)
            }
        }
    }
}
