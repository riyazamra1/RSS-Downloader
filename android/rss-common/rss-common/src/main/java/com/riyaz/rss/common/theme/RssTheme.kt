package com.riyaz.rss.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RssGold = Color(0xFFC9A227)
private val LightBackground = Color(0xFFF7F7F7)
private val LightSurface = Color(0xFFFFFFFF)
private val DarkBackground = Color(0xFF0B0B0D)
private val DarkSurface = Color(0xFF151518)

private val LightColors = lightColorScheme(
    primary = RssGold,
    background = LightBackground,
    surface = LightSurface
)

private val DarkColors = darkColorScheme(
    primary = RssGold,
    background = DarkBackground,
    surface = DarkSurface
)

enum class RssThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun RssTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
