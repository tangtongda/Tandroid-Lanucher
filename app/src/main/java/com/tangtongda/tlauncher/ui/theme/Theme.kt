package com.tangtongda.tlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF0E1116)
val SurfaceColor = Color(0xFF1B2230)
val Accent = Color(0xFF4F8CFF)
val TextPrimary = Color(0xFFF2F4F8)
val TextSecondary = Color(0xFF9AA4B2)

@OptIn(ExperimentalTvMaterial3Api::class)
private val colors = darkColorScheme(
    primary = Accent,
    background = Background,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    onBackground = TextPrimary,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
