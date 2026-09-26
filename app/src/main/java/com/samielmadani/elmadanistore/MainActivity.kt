package com.samielmadani.elmadanistudio

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.samielmadani.elmadanistudio.ui.StoreApp
import com.samielmadani.elmadanistudio.ui.theme.ElmadaniStudioTheme
import com.samielmadani.elmadanistudio.ui.theme.ThemeSettings
import com.samielmadani.elmadanistudio.worker.UpdateScheduler

class MainActivity : ComponentActivity() {
    private val openRepo = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        ThemeSettings.initialize(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dark = when (ThemeSettings.mode.value) {
            com.samielmadani.elmadanistudio.data.ThemeMode.DARK, com.samielmadani.elmadanistudio.data.ThemeMode.OLED -> true
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
        openRepo.value = intent.getStringExtra("open_repo")
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        UpdateScheduler.schedule(this)
        setContent {
            ElmadaniStudioTheme {
                StoreApp(initialRepo = openRepo.value)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRepo.value = intent.getStringExtra("open_repo")
    }
}
