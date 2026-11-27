package com.isabgroup.pimsg.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PiColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF17171A)
    val SurfaceHigh = Color(0xFF232327)
    val Gray = Color(0xFF8E8E93)
    val GrayDark = Color(0xFF3A3A3F)
    val TextSecondary = Color(0xFFA1A1A8)
    val White = Color.White
    val Danger = Color(0xFFE5484D)
}

private val scheme = darkColorScheme(
    primary = PiColors.White,
    onPrimary = Color.Black,
    background = PiColors.Background,
    onBackground = PiColors.White,
    surface = PiColors.Surface,
    onSurface = PiColors.White,
    surfaceVariant = PiColors.SurfaceHigh,
    onSurfaceVariant = PiColors.TextSecondary,
)

@Composable
fun PiMsgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
