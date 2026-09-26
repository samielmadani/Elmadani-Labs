package com.samielmadani.elmadanistudio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Typography
import androidx.core.view.WindowInsetsControllerCompat
import com.samielmadani.elmadanistudio.data.ThemeMode

val LocalLiquidGlass = staticCompositionLocalOf { false }

@Composable
fun ElmadaniStudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val mode = ThemeSettings.mode.value
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.SYSTEM, ThemeMode.LIQUID_GLASS -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.OLED -> true
    }
    val base = if (dark) {
        if (mode == ThemeMode.OLED) darkColorScheme(background = androidx.compose.ui.graphics.Color.Black, surface = androidx.compose.ui.graphics.Color.Black) else if (android.os.Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    } else {
        if (android.os.Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()
    }
    val glass = mode == ThemeMode.LIQUID_GLASS
    val colors = base.copy(
        primary = ThemeSettings.accent.value,
        background = if (glass) base.background.copy(alpha = 0.9f) else base.background,
        surface = if (glass) base.surface.copy(alpha = 0.78f) else base.surface,
        surfaceVariant = if (glass) base.surfaceVariant.copy(alpha = 0.7f) else base.surfaceVariant
    )
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalLiquidGlass provides glass) {
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
}
