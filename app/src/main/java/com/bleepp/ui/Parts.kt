package com.bleepp.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bleepp.ui.theme.Sea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = Sea.palette.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sea.palette.muted)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = Sea.palette.text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** A flat card floating on the sea. The surface is slightly see-through so the scene stays alive behind it. */
@Composable
fun SeaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(Sea.shape)
            .background(Sea.palette.swell.copy(alpha = 0.88f)),
        content = content,
    )
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Sea.palette.beam, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Sea.palette.text)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = Sea.palette.muted)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null, icon: ImageVector? = null) {
    SettingRow(title, summary, icon, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Sea.palette.onBeam,
                checkedTrackColor = Sea.palette.beam,
                uncheckedThumbColor = Sea.palette.muted,
                uncheckedTrackColor = Sea.palette.line,
                uncheckedBorderColor = Sea.palette.line,
            ),
        )
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, steps: Int = 0, onDone: () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Sea.palette.text)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Sea.palette.beam,
                activeTrackColor = Sea.palette.beam,
                inactiveTrackColor = Sea.palette.line,
            ),
        )
    }
}

@Composable
fun BeamButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(Sea.palette.beam)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Sea.palette.onBeam, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sea.palette.onBeam)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .border(1.dp, Sea.palette.muted, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Sea.palette.text)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) Sea.palette.beam else Sea.palette.swell.copy(alpha = 0.88f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Sea.palette.onBeam else Sea.palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SeaField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, leading: ImageVector? = null) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = Sea.palette.muted) },
        singleLine = singleLine,
        leadingIcon = leading?.let { { Icon(it, null, tint = Sea.palette.muted) } },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Sea.palette.swell.copy(alpha = 0.88f),
            unfocusedContainerColor = Sea.palette.swell.copy(alpha = 0.88f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Sea.palette.beam,
            focusedTextColor = Sea.palette.text,
            unfocusedTextColor = Sea.palette.text,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, backIcon: ImageVector, backLabel: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(backIcon, backLabel, tint = Sea.palette.text) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Sea.palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private object IconCache {
    val bitmaps = LruCache<String, Bitmap>(160)

    fun load(context: Context, pkg: String): Bitmap? {
        bitmaps.get(pkg)?.let { return it }
        return runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
            ?.also { bitmaps.put(pkg, it) }
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    var icon by remember(pkg) { mutableStateOf(IconCache.bitmaps.get(pkg)) }
    LaunchedEffect(pkg) {
        if (icon == null) icon = withContext(Dispatchers.IO) { IconCache.load(context, pkg) }
    }
    val shown = icon
    if (shown != null) {
        Image(shown.asImageBitmap(), null, Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Sea.palette.line))
    }
}

@Composable
fun Gap(height: Dp = 12.dp) = Spacer(Modifier.height(height))
