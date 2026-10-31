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
    /** The compressor: a file made smaller in one go. */
    data object Compress : Screen("compress")
    /** The voice of a file set apart from the rest of its sound, and the two mixed, in one go. */
    /** The SVG editor: a drawing's shapes moved, sized, turned and arranged. */
    data object SvgEditor : Screen("svg_editor")
    /** The custom format: only the command studio, never the video editor. */
    data object CustomCommand : Screen("custom_command")

    companion object {
        /** The page of one settings category. */
        fun settingsCategory(category: SettingsCategory): String = "settings/${category.key}"
    }
}
