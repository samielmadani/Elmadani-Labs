package com.samielmadani.elmadanistudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import com.samielmadani.elmadanistudio.data.ThemeMode

@Composable
fun ElmadaniStudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = ThemeSettings.mode.value
    val dark = when (mode) { ThemeMode.DARK, ThemeMode.OLED -> true; else -> isSystemInDarkTheme() }
    val base = if (dark) {
        if (mode == ThemeMode.OLED) darkColorScheme(background = androidx.compose.ui.graphics.Color.Black, surface = androidx.compose.ui.graphics.Color.Black) else if (android.os.Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    } else {
        if (android.os.Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()
    }
    val colors = base.copy(primary = ThemeSettings.accent.value)
    MaterialTheme(
        colorScheme = colors,
        typography = Typography().copy(
            headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif),
            headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.Serif),
            titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.Serif)
        ),
        content = content
    )
}
