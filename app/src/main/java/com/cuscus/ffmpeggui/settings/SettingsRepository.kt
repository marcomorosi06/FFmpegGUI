package com.cuscus.ffmpeggui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The settings, kept with DataStore (Preferences) in the app's own files.
 * [settings] is null until they have been read once (the splash screen waits
 * for it, so that the first frame already has the right colours); [current]
 * is the same with the defaults meanwhile, for code that cannot wait.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings?> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The settings now, the defaults until they are read. */
    val current: AppSettings get() = settings.value ?: AppSettings()

    /** Changes the settings with [change]; written in the background. */
    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch {
            store.edit { prefs -> prefs.write(change(prefs.toSettings())) }
        }
    }

    /** Back to the defaults (the welcome, once seen, does not come back by itself). */
    fun reset() {
        scope.launch {
            store.edit { prefs ->
                val onboarded = prefs[ONBOARDED]
                prefs.clear()
                if (onboarded != null) prefs[ONBOARDED] = onboarded
            }
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.themeMode,
            dynamicColor = this[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            pureBlack = this[PURE_BLACK] ?: defaults.pureBlack,
            haptics = this[HAPTICS] ?: defaults.haptics,
            saveCopies = this[SAVE_COPIES] ?: defaults.saveCopies,
            liveUpdates = this[LIVE_UPDATES] ?: defaults.liveUpdates,
            hardwareVideo = this[HARDWARE_VIDEO] ?: defaults.hardwareVideo,
            cpuSpeed = this[CPU_SPEED]?.let { name -> CpuEncoderSpeed.entries.firstOrNull { it.name == name } } ?: defaults.cpuSpeed,
            saveProjects = this[SAVE_PROJECTS] ?: defaults.saveProjects,
            projectsLimit = this[PROJECTS_LIMIT]?.takeIf { it in AppSettings.PROJECTS_LIMITS } ?: defaults.projectsLimit,
            onboarded = this[ONBOARDED] ?: defaults.onboarded,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[THEME] = settings.themeMode.name
        this[DYNAMIC_COLOR] = settings.dynamicColor
        this[PURE_BLACK] = settings.pureBlack
        this[HAPTICS] = settings.haptics
        this[SAVE_COPIES] = settings.saveCopies
        this[LIVE_UPDATES] = settings.liveUpdates
        this[HARDWARE_VIDEO] = settings.hardwareVideo
        this[CPU_SPEED] = settings.cpuSpeed.name
        this[SAVE_PROJECTS] = settings.saveProjects
        this[PROJECTS_LIMIT] = settings.projectsLimit
        this[ONBOARDED] = settings.onboarded
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SAVE_COPIES = booleanPreferencesKey("save_copies")
        val LIVE_UPDATES = booleanPreferencesKey("live_updates")
        val HARDWARE_VIDEO = booleanPreferencesKey("hardware_video")
        val CPU_SPEED = stringPreferencesKey("cpu_speed")
        val SAVE_PROJECTS = booleanPreferencesKey("save_projects")
        val PROJECTS_LIMIT = intPreferencesKey("projects_limit")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
