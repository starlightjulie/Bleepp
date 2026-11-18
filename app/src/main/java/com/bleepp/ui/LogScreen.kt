package com.bleepp.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
fun LogScreen(vm: MainViewModel, onAppearance: () -> Unit, onUnlock: () -> Unit, bottomPadding: PaddingValues) {
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    if (!unlocked) {
        LockedLog(onUnlock)
        return
    }
    val entries by vm.log.collectAsStateWithLifecycle()
    val apps by vm.loggedApps.collectAsStateWithLifecycle()
    val bookmarkedApps by vm.bookmarkedApps.collectAsStateWithLifecycle()
    val savedCount by vm.savedCount.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var details by remember { mutableStateOf<LogEntry?>(null) }
    val total = apps.sumOf { it.count }
    val appOnlyMessage = stringResource(R.string.open_app_only)
    val failedMessage = stringResource(R.string.open_failed)
    val list = entries
    val selecting = selected.isNotEmpty()
    val selectedEntries = list.orEmpty().filter { it.id in selected }

    BackHandler(enabled = selecting) { selected = emptySet() }
    // A search or filter can hide selected entries; never act on ones that aren't on screen
    LaunchedEffect(list) {
        val visible = list.orEmpty().mapTo(HashSet()) { it.id }
        if (selected.any { it !in visible }) selected = selected.filterTo(HashSet()) { it in visible }
    }

    fun open(entry: LogEntry) {
        when (NotificationOpener.open(context, entry)) {
            NotificationOpener.Result.EXACT -> Unit
            NotificationOpener.Result.APP -> Toast.makeText(context, appOnlyMessage, Toast.LENGTH_SHORT).show()
            NotificationOpener.Result.FAILED -> Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
        item {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    allBookmarked = selectedEntries.isNotEmpty() && selectedEntries.all { it.bookmarked },
                    onBookmark = { mark ->
                        vm.setBookmarked(selectedEntries, mark)
                        selected = emptySet()
                    },
                    onDelete = {
                        vm.deleteEntries(selectedEntries)
                        selected = emptySet()
                    },
                    onDetails = selectedEntries.singleOrNull()?.let { only -> { details = only } },
                    onClose = { selected = emptySet() },
                )
            } else {
                ScreenTitle(partOfDayTitle(), stringResource(R.string.log_count, total)) {
                    IconButton(onClick = onAppearance) {
                        Icon(Icons.Outlined.Palette, stringResource(R.string.appearance), tint = Sea.palette.text)
                    }
                }
            }
        }
        item {
            SeaField(query, vm::setQuery, stringResource(R.string.log_search), Modifier.padding(horizontal = 16.dp), leading = Icons.Outlined.Search)
        }
        item {
            val starred = apps.filter { it.pkg in bookmarkedApps }
            val others = apps.filterNot { it.pkg in bookmarkedApps }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip(stringResource(R.string.log_all_apps), filter == LogFilter.All) { vm.setFilter(LogFilter.All) } }
                if (savedCount > 0) {
                    item {
                        Chip(stringResource(R.string.log_saved, savedCount), filter == LogFilter.Saved) {
                            vm.setFilter(if (filter == LogFilter.Saved) LogFilter.All else LogFilter.Saved)
                        }
                    }
                }
                items(starred + others, key = { it.pkg }) { app ->
                    val chosen = filter == LogFilter.App(app.pkg)
                    Chip(if (app.pkg in bookmarkedApps) "★ ${app.appName}" else app.appName, chosen) {
                        vm.setFilter(if (chosen) LogFilter.All else LogFilter.App(app.pkg))
                    }
                }
            }
        }
        when {
            list == null -> Unit
            list.isEmpty() -> item { EmptyLog(searching = query.isNotBlank() || filter != LogFilter.All) }
            else -> {
                var lastDay = ""
                list.forEachIndexed { index, entry ->
                    val day = dayLabelKey(entry.postedAt)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") { DayLabel(entry.postedAt) }
                    }
                    item(key = entry.id) {
                        SwipeRow(
                            entry = entry,
                            enabled = !selecting,
                            onDelete = { vm.deleteEntries(listOf(entry)) },
                            onBookmark = { vm.setBookmarked(listOf(entry), !entry.bookmarked) },
                        ) {
                            LogRow(
                                entry = entry,
                                lit = index == 0 && query.isBlank() && filter == LogFilter.All && !selecting,
                                selected = entry.id in selected,
                                onOpen = {
                                    if (selecting) selected = selected.toggle(entry.id) else open(entry)
                                },
                                onLongPress = { selected = selected.toggle(entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    details?.let { entry ->
        DetailsSheet(
            entry = entry,
            onDismiss = { details = null },
            onOpen = {
                details = null
                selected = emptySet()
                open(entry)
            },
            onBookmark = {
                vm.setBookmarked(listOf(entry), !entry.bookmarked)
                details = null
                selected = emptySet()
            },
            onBlockFeature = {
                vm.blockFeature(entry)
                details = null
                selected = emptySet()
            },
            onDelete = {
                vm.deleteEntries(listOf(entry))
                details = null
                selected = emptySet()
            },
        )
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
private fun LockedLog(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, null, tint = Sea.palette.beam, modifier = Modifier.size(40.dp))
            Gap()
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, color = Sea.palette.text)
            Gap(6.dp)
            Text(stringResource(R.string.lock_body), style = MaterialTheme.typography.bodyMedium, color = Sea.palette.muted)
            Gap(20.dp)
            BeamButton(stringResource(R.string.lock_unlock), onUnlock, icon = Icons.Outlined.Lock)
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allBookmarked: Boolean,
    onBookmark: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDetails: (() -> Unit)?,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = Sea.palette.text) }
        Text(stringResource(R.string.selected_n, count), style = MaterialTheme.typography.headlineSmall, color = Sea.palette.text, modifier = Modifier.weight(1f))
        if (onDetails != null) {
            IconButton(onClick = onDetails) { Icon(Icons.Outlined.Info, stringResource(R.string.details), tint = Sea.palette.text) }
        }
        IconButton(onClick = { onBookmark(!allBookmarked) }) {
            Icon(
                if (allBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                stringResource(if (allBookmarked) R.string.unbookmark else R.string.bookmark),
                tint = Sea.palette.beam,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = Sea.palette.text) }
    }
}

/** Swipe left to delete, swipe right to bookmark. The row springs back after a bookmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(entry: LogEntry, enabled: Boolean, onDelete: () -> Unit, onBookmark: () -> Unit, content: @Composable () -> Unit) {
    // Not saved across recompositions: Undo brings the same entry back, and a saved "swiped away"
    // state would delete it again straight away. A bookmark swipe is refused at the last moment, so
    // the row springs back by itself and there's no half-finished state left to fire twice.
    val density = LocalDensity.current
    val bookmark by rememberUpdatedState(onBookmark)
    val delete by rememberUpdatedState(onDelete)
    val state = remember(entry.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        bookmark()
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        delete()
                        true
                    }
                    SwipeToDismissBoxValue.Settled -> true
                }
            },
            positionalThreshold = { it * 0.4f },
        )
    }
    SwipeToDismissBox(
        state = state,
        gesturesEnabled = enabled,
        backgroundContent = {
            val toDelete = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val toMark = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toDelete) Arrangement.End else Arrangement.Start,
            ) {
                when {
                    toDelete -> SwipeHint(Icons.Outlined.Delete, stringResource(R.string.delete))
                    toMark -> SwipeHint(
                        if (entry.bookmarked) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark,
                        stringResource(if (entry.bookmarked) R.string.unbookmark else R.string.bookmark),
                    )
                }
            }
        },
    ) { content() }
}

@Composable
private fun SwipeHint(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Sea.palette.beam)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Sea.palette.text)
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
private fun LogRow(entry: LogEntry, lit: Boolean, selected: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
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
            .then(if (selected) Modifier.border(2.dp, Sea.palette.beam, Sea.shape) else Modifier)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            AppIcon(entry.pkg)
            if (selected) {
                Box(Modifier.size(40.dp).clip(Sea.shape).background(Sea.palette.beam.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Check, null, tint = Sea.palette.onBeam)
                }
            }
        }
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
                if (entry.bookmarked) {
                    Icon(Icons.Outlined.Bookmark, stringResource(R.string.bookmarked), tint = if (lit) main else Sea.palette.beam, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
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
private fun Stamp(outcome: Int, colour: Color) {
    val text = when (outcome) {
        AlertOutcome.RANG -> R.string.stamp_rang
        AlertOutcome.BROKE_THROUGH -> R.string.stamp_broke
        AlertOutcome.BLOCKED -> R.string.stamp_blocked
        AlertOutcome.CALL -> R.string.stamp_call
        else -> return
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = colour, modifier = Modifier.padding(top = 3.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onBookmark: () -> Unit,
    onBlockFeature: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val copied = stringResource(R.string.copied)
    val when_ = DateFormat.getLongDateFormat(context).format(Date(entry.postedAt)) + ", " +
        DateFormat.getTimeFormat(context).format(Date(entry.postedAt))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Sea.palette.swell,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry.pkg)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.appName, style = MaterialTheme.typography.titleMedium, color = Sea.palette.text)
                    val feature = entry.channelName.ifEmpty { entry.channelId }
                    Text(
                        if (feature.isEmpty()) when_ else "$feature · $when_",
                        style = MaterialTheme.typography.bodySmall,
                        color = Sea.palette.muted,
                    )
                }
            }
            Gap()
            SelectionContainer {
                Column {
                    if (entry.title.isNotEmpty()) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium.followContent(), color = Sea.palette.text)
                    }
                    if (entry.text.isNotEmpty()) {
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium.followContent(), color = Sea.palette.text)
                    }
                }
            }
            Gap(16.dp)
            SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_it), onOpen)
            SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy_text)) {
                val clip = context.getSystemService(ClipboardManager::class.java)
                clip.setPrimaryClip(ClipData.newPlainText(entry.appName, listOf(entry.title, entry.text).filter { it.isNotEmpty() }.joinToString("\n")))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
            SheetAction(
                if (entry.bookmarked) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark,
                stringResource(if (entry.bookmarked) R.string.unbookmark else R.string.bookmark),
                onBookmark,
            )
            if (entry.channelId.isNotEmpty()) {
                SheetAction(
                    Icons.Outlined.Block,
                    stringResource(R.string.block_feature, entry.channelName.ifEmpty { entry.channelId }, entry.appName),
                    onBlockFeature,
                )
            }
            SheetAction(Icons.Outlined.Delete, stringResource(R.string.delete), onDelete)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    SettingRow(label, icon = icon, onClick = onClick)
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
                Gap(6.dp)
                Text(stringResource(R.string.log_swipe_hint), style = MaterialTheme.typography.bodySmall, color = Sea.palette.muted)
            }
        }
    }
}
