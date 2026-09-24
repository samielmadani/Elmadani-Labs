package com.samielmadani.elmadanistore.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import com.samielmadani.elmadanistore.data.ThemeMode

object ThemeSettings {
    val mode = mutableStateOf(ThemeMode.SYSTEM)
    val accent = mutableStateOf(Color(0xFF315F90))
    fun setMode(value: ThemeMode) { mode.value = value }
    fun setAccent(value: Color) { accent.value = value }
}
