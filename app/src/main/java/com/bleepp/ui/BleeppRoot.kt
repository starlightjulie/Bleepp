package com.bleepp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleepp.R
import com.bleepp.data.UpdateChecker
import com.bleepp.ui.theme.Sea
import com.bleepp.ui.theme.SeaScene

enum class Tab(val label: Int, val icon: ImageVector) {
    LOG(R.string.tab_log, Icons.Outlined.History),
    ALERTS(R.string.tab_alerts, Icons.Outlined.NotificationsActive),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Tune),
}

@Composable
fun BleeppRoot(vm: MainViewModel, onAskPost: () -> Unit) {
    val look by vm.look.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LOG) }
    var rulePkg by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleName by rememberSaveable { mutableStateOf("") }
    var appearance by rememberSaveable { mutableStateOf(false) }
    var setupHidden by rememberSaveable { mutableStateOf(vm.settings.setupDismissed) }
    var restricted by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler(enabled = rulePkg != null || appearance || tab != Tab.LOG) {
        when {
            rulePkg != null -> rulePkg = null
            appearance -> appearance = false
            else -> tab = Tab.LOG
        }
    }

    Box(Modifier.fillMaxSize()) {
        SeaScene(look)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (!status.hasAccess && !setupHidden) {
                SetupBanner(
                    onOpen = { openNotificationAccess(context) },
                    onHelp = { restricted = true },
                )
            }
            val page = rulePkg?.let { "rule:$it" } ?: if (appearance) "appearance" else tab.name
            val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val inner = PaddingValues(bottom = 96.dp + navBar)
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
                label = "page",
            ) { shown ->
                when {
                    shown.startsWith("rule:") -> RuleScreen(vm, shown.removePrefix("rule:"), ruleName, { rulePkg = null }, inner)
                    shown == "appearance" -> AppearanceScreen(vm, { appearance = false }, inner)
                    shown == Tab.ALERTS.name -> AlertsScreen(vm, { pkg, name ->
                        ruleName = name
                        rulePkg = pkg
                    }, inner)
                    shown == Tab.SETTINGS.name -> SettingsScreen(vm, { appearance = true }, onAskPost, inner)
                    else -> LogScreen(vm, { appearance = true }, inner)
                }
            }
        }
        BottomFade(Modifier.align(Alignment.BottomCenter))
        if (rulePkg == null && !appearance) {
            BeamBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (restricted) RestrictedDialog { restricted = false }
    update?.let { available ->
        NotesDialog(
            title = stringResource(R.string.upd_title, available.version),
            sections = available.sections,
            onDismiss = vm::dismissUpdate,
            actions = {
                Row {
                    TextButton(onClick = vm::snoozeUpdate) { Text(stringResource(R.string.upd_later)) }
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, UpdateChecker.LATEST_RELEASE_URL.toUri()))
                        }
                        vm.dismissUpdate()
                    }) { Text(stringResource(R.string.upd_now)) }
                }
            },
        )
    }
}

@Composable
private fun SetupBanner(onOpen: () -> Unit, onHelp: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(Sea.shape)
            .background(Sea.palette.beam)
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.setup_banner), style = MaterialTheme.typography.titleSmall, color = Sea.palette.onBeam)
        Row(Modifier.padding(top = 4.dp)) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.setup_fix), color = Sea.palette.onBeam) }
            TextButton(onClick = onHelp) { Text(stringResource(R.string.setup_restricted_title), color = Sea.palette.onBeam) }
        }
    }
}

/** Lets lists sink into the sea at the bottom instead of running under the system navigation. */
@Composable
private fun BottomFade(modifier: Modifier = Modifier) {
    val sea = Sea.palette.sea
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .background(Brush.verticalGradient(listOf(Color.Transparent, sea.copy(alpha = 0.92f), sea))),
    )
}

/** The tab bar. A small beam of light slides across to the tab you pick. */
@Composable
private fun BeamBar(current: Tab, onPick: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val tabs = Tab.entries
    val position by animateFloatAsState(tabs.indexOf(current).toFloat(), tween(320), label = "beam")
    val beam = Sea.palette.beam
    BoxWithConstraints(
        modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(CircleShape)
            .background(Sea.palette.swell.copy(alpha = 0.96f)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val slot = size.width / tabs.size
            val index = if (rtl) tabs.size - 1 - position else position
            val centre = slot * (index + 0.5f)
            val cone = Path().apply {
                moveTo(centre - 7.dp.toPx(), 0f)
                lineTo(centre + 7.dp.toPx(), 0f)
                lineTo(centre + slot * 0.42f, size.height)
                lineTo(centre - slot * 0.42f, size.height)
                close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(beam.copy(alpha = 0.5f), Color.Transparent), 0f, size.height))
            drawCircle(beam, 3.dp.toPx(), Offset(centre, 3.dp.toPx()))
        }
        Row(Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onPick(tab) }
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = if (selected) Sea.palette.beam else Sea.palette.muted, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Sea.palette.text else Sea.palette.muted,
                    )
                }
            }
        }
    }
}
