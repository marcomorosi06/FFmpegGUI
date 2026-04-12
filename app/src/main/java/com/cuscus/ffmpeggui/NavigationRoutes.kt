package com.cuscus.ffmpeggui

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object FormatPicker : Screen("format_picker")
    data object Config : Screen("config")
    data object Processing : Screen("processing")
}