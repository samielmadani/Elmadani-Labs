package com.samielmadani.elmadanistudio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import com.samielmadani.elmadanistudio.data.ThemeMode

object ThemeSettings {
    val mode = mutableStateOf(ThemeMode.SYSTEM)
    val accent = mutableStateOf(Color(0xFF315F90))
    private var preferences: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences("store", Context.MODE_PRIVATE)
        mode.value = runCatching { ThemeMode.valueOf(preferences?.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
        accent.value = Color(preferences?.getInt("theme_accent", 0xFF315F90.toInt()) ?: 0xFF315F90.toInt())
    }

    fun setMode(value: ThemeMode) {
        mode.value = value
        preferences?.edit()?.putString("theme_mode", value.name)?.apply()
    }

    fun setAccent(value: Color) {
        accent.value = value
        preferences?.edit()?.putInt("theme_accent", value.value.toInt())?.apply()
    }
}
