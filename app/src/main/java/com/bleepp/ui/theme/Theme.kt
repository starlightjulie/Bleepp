package com.bleepp.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalLook = staticCompositionLocalOf { Look() }

object Sea {
    val palette: Palette
        @Composable get() = LocalLook.current.palette
    val shape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(LocalLook.current.corners.dp)
}

@Composable
fun BleeppTheme(look: Look, content: @Composable () -> Unit) {
    val p = look.palette
    val raised = p.text.copy(alpha = 0.08f).compositeOver(p.swell)
    val scheme = if (p.isLight) {
        lightColorScheme(
            primary = p.beam, onPrimary = p.onBeam, secondary = p.beam, onSecondary = p.onBeam,
            background = p.sea, onBackground = p.text, surface = p.swell, onSurface = p.text,
            surfaceVariant = raised, onSurfaceVariant = p.muted, surfaceContainerHigh = p.swell,
            surfaceContainer = p.swell, surfaceContainerHighest = raised, outline = p.line, outlineVariant = p.line,
        )
    } else {
        darkColorScheme(
            primary = p.beam, onPrimary = p.onBeam, secondary = p.beam, onSecondary = p.onBeam,
            background = p.sea, onBackground = p.text, surface = p.swell, onSurface = p.text,
            surfaceVariant = raised, onSurfaceVariant = p.muted, surfaceContainerHigh = p.swell,
            surfaceContainer = p.swell, surfaceContainerHighest = raised, outline = p.line, outlineVariant = p.line,
        )
    }
    val heading = if (look.serifHeadings) FontFamily.Serif else FontFamily.Default
    val typography = Typography(
        displaySmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
        headlineSmall = TextStyle(fontFamily = heading, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    )
    val shapes = Shapes(
        small = RoundedCornerShape((look.corners / 2).dp),
        medium = RoundedCornerShape(look.corners.dp),
        large = RoundedCornerShape((look.corners + 6).dp),
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = p.isLight
                isAppearanceLightNavigationBars = p.isLight
            }
        }
    }
    CompositionLocalProvider(LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

fun Color.over(base: Color): Color = compositeOver(base)
