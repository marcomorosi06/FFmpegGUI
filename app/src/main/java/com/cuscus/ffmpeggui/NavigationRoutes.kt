package com.cuscus.ffmpeggui

import com.cuscus.ffmpeggui.settings.SettingsCategory

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object FormatPicker : Screen("format_picker")
    data object Config : Screen("config")
    /** The exact frame of a video, before it becomes a picture. */
    data object FramePicker : Screen("frame_picker")
    data object Processing : Screen("processing")
    data object Transcript : Screen("transcript")
    data object Settings : Screen("settings")
    data object About : Screen("about")
    data object Welcome : Screen("welcome")

    companion object {
        /** The page of one settings category. */
        fun settingsCategory(category: SettingsCategory): String = "settings/${category.key}"
    }
}
