package com.samielmadani.elmadanistudio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.samielmadani.elmadanistudio.data.ThemeMode
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

object ThemeSettings {
    val mode = mutableStateOf(ThemeMode.SYSTEM)
    val accent = mutableStateOf(Color(0xFF315F90))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null
    private var initialized = false
    private val modeKey = stringPreferencesKey("theme_mode")
    private val accentKey = intPreferencesKey("theme_accent")

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        val store = appContext.themeDataStore
        dataStore = store
        val legacy = appContext.getSharedPreferences("store", Context.MODE_PRIVATE)
        scope.launch {
            store.edit { preferences ->
                if (!preferences.contains(modeKey)) {
                    preferences[modeKey] = runCatching {
                        ThemeMode.valueOf(legacy.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name).name
                    }.getOrDefault(ThemeMode.SYSTEM.name)
                }
                if (!preferences.contains(accentKey)) {
                    preferences[accentKey] = legacy.getInt("theme_accent", 0xFF315F90.toInt())
                }
            }
            store.data.catch { emit(emptyPreferences()) }.collect { preferences ->
                mode.value = runCatching { ThemeMode.valueOf(preferences[modeKey] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
                accent.value = Color(preferences[accentKey] ?: 0xFF315F90.toInt())
            }
        }
    }

    fun setMode(value: ThemeMode) {
        mode.value = value
        dataStore?.let { store -> scope.launch { runCatching { store.edit { it[modeKey] = value.name } } } }
    }

    fun setAccent(value: Color) {
        accent.value = value
        dataStore?.let { store -> scope.launch { runCatching { store.edit { it[accentKey] = value.toArgb() } } } }
    }
}
