package com.bleepp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bleepp.R
import com.bleepp.ui.theme.Colourway
import com.bleepp.ui.theme.Colourways
import com.bleepp.ui.theme.CustomColours
import com.bleepp.ui.theme.LauncherIcon
import com.bleepp.ui.theme.Look
import com.bleepp.ui.theme.Palette
import com.bleepp.ui.theme.Sea
import com.bleepp.ui.theme.SeaBackground

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(vm: MainViewModel, onBack: () -> Unit, bottomPadding: PaddingValues) {
    val look by vm.look.collectAsStateWithLifecycle()
    val looks by vm.savedLooks.collectAsStateWithLifecycle()
    var speed by remember { mutableFloatStateOf(look.speed) }
    var strength by remember { mutableFloatStateOf(look.strength) }
    var corners by remember { mutableFloatStateOf(look.corners.toFloat()) }
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomPadding)) {
        BackBar(stringResource(R.string.appearance), onBack, Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))

        SectionLabel(stringResource(R.string.ap_colourway))
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2,
        ) {
            Colourways.forEach { way ->
                WayTile(way, look.custom == null && look.colourway == way.id, Modifier.weight(1f)) {
                    vm.setLook { it.copy(colourway = way.id, custom = null) }
                }
            }
        }
        Gap()
        SeaCard {
            SwitchRow(stringResource(R.string.ap_custom), look.custom != null, { on ->
                vm.setLook { l ->
                    val p = l.palette
                    l.copy(custom = if (on) CustomColours(p.sea.long(), p.swell.long(), p.beam.long(), p.onBeam.long(), p.text.long()) else null)
                }
            })
            look.custom?.let { custom ->
                listOf(
                    R.string.ap_sea to custom.sea,
                    R.string.ap_swell to custom.swell,
                    R.string.ap_beam to custom.beam,
                    R.string.ap_on_beam to custom.onBeam,
                    R.string.ap_text to custom.text,
                ).forEachIndexed { index, (label, value) ->
                    SettingRow(stringResource(label), onClick = { editing = index }) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(value)).border(1.dp, Sea.palette.line, CircleShape))
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.ap_background))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SeaBackground.entries.forEach { bg ->
                Chip(stringResource(bg.label), look.seaBackground == bg) { vm.setLook { it.copy(background = bg.name) } }
            }
        }
        Gap(6.dp)
        SeaCard {
            SliderRow(stringResource(R.string.ap_speed), speed, 0.25f..2f, { speed = it }, onDone = { vm.setLook { it.copy(speed = speed) } })
            SliderRow(stringResource(R.string.ap_strength), strength, 0.2f..1f, { strength = it }, onDone = { vm.setLook { it.copy(strength = strength) } })
        }

        SectionLabel(stringResource(R.string.ap_interface))
        SeaCard {
            SliderRow(stringResource(R.string.ap_corners), corners, 0f..28f, { corners = it }, onDone = { vm.setLook { it.copy(corners = corners.toInt()) } })
            SwitchRow(stringResource(R.string.ap_serif), look.serifHeadings, { on -> vm.setLook { it.copy(serifHeadings = on) } })
        }

        SectionLabel(stringResource(R.string.ap_icon))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LauncherIcon.entries.forEach { icon -> IconTile(icon) { vm.applyIcon(icon) } }
        }

        SectionLabel(stringResource(R.string.ap_looks))
        SeaCard {
            looks.forEach { saved ->
                SettingRow(saved.name, onClick = {
                    vm.setLook { saved.look }
                    speed = saved.look.speed
                    strength = saved.look.strength
                    corners = saved.look.corners.toFloat()
                }) {
                    IconButton(onClick = { vm.deleteLook(saved.name) }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.delete), tint = Sea.palette.muted)
                    }
                }
            }
            SettingRow(stringResource(R.string.ap_save_look), onClick = { naming = true })
            SettingRow(stringResource(R.string.ap_reset), onClick = {
                vm.setLook { Look() }
                speed = 1f
                strength = 0.8f
                corners = 18f
            })
        }
        Gap(24.dp)
    }

    if (naming) NameDialog(onDismiss = { naming = false }) {
        vm.saveLook(it)
        naming = false
    }
    val custom = look.custom
    val slot = editing
    if (custom != null && slot != null) {
        val current = listOf(custom.sea, custom.swell, custom.beam, custom.onBeam, custom.text)[slot]
        ColourDialog(current, onDismiss = { editing = null }) { picked ->
            vm.setLook {
                it.copy(
                    custom = when (slot) {
                        0 -> custom.copy(sea = picked)
                        1 -> custom.copy(swell = picked)
                        2 -> custom.copy(beam = picked)
                        3 -> custom.copy(onBeam = picked)
                        else -> custom.copy(text = picked)
                    },
                )
            }
            editing = null
        }
    }
}

private fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
private fun WayTile(way: Colourway, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = way.palette
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(p.sea)
            .border(if (selected) 2.dp else 1.dp, if (selected) Sea.palette.beam else Sea.palette.line, shape)
            .clickable(onClick = onClick),
    ) {
        MiniScene(p)
        Text(
            stringResource(way.label),
            style = MaterialTheme.typography.labelMedium,
            color = p.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun MiniScene(p: Palette) {
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.92f, size.height * 0.2f)
            lineTo(0f, size.height * 0.1f)
            lineTo(0f, size.height * 0.75f)
            close()
        }
        drawPath(path, p.beam.copy(alpha = 0.55f))
        drawCircle(p.beam, size.height * 0.09f, androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.2f))
        drawRect(p.swell, androidx.compose.ui.geometry.Offset(0f, size.height * 0.78f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.22f))
    }
}

@Composable
private fun IconTile(icon: LauncherIcon, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(icon.background)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                drawRoundRect(Color(icon.bubble), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.72f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f))
                val tail = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.7f)
                    lineTo(size.width * 0.22f, size.height)
                    lineTo(size.width * 0.52f, size.height * 0.7f)
                    close()
                }
                drawPath(tail, Color(icon.bubble))
            }
        }
        Text(stringResource(icon.label), style = MaterialTheme.typography.bodySmall, color = Sea.palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ap_save_look)) },
        text = { SeaField(name, { name = it }, stringResource(R.string.ap_look_name)) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ColourDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val start = Color(initial)
    var red by remember { mutableFloatStateOf(start.red) }
    var green by remember { mutableFloatStateOf(start.green) }
    var blue by remember { mutableFloatStateOf(start.blue) }
    val picked = Color(red, green, blue)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(picked).border(1.dp, Sea.palette.line, CircleShape))
                Text(
                    "#%06X".format(picked.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        },
        text = {
            Column {
                SliderRow(stringResource(R.string.ap_red), red, 0f..1f, { red = it })
                SliderRow(stringResource(R.string.ap_green), green, 0f..1f, { green = it })
                SliderRow(stringResource(R.string.ap_blue), blue, 0f..1f, { blue = it })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked.long()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
