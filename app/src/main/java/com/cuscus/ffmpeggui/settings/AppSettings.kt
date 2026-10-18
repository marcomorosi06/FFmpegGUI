package com.cuscus.ffmpeggui.settings

import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed

/** Light, dark, or as the system is. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * What the user chose in the settings. The defaults are what a fresh install
 * gets: wallpaper colours, the system's theme, vibration on, a copy of every
 * export in the phone's own folders, Live Updates where Android has them,
 * recent projects kept (only on the phone) so editing can go on later.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Colours from the wallpaper (Android 12+); off, the teal of the app's icon. */
    val dynamicColor: Boolean = true,
    /** Black backgrounds in the dark theme, for OLED screens. */
    val pureBlack: Boolean = false,
    /** Light taps under buttons, sliders and the timeline. */
    val haptics: Boolean = true,
    /** Every finished file also goes to Movies, Music, Pictures or Download (Android 10+). */
    val saveCopies: Boolean = true,
    /**
     * MP4 and MKV pictures encoded by the phone's video chip (the processor takes over
     * when it fails). Off until the chip's test in the settings has passed.
     */
    val hardwareVideo: Boolean = false,
    /** How hard x264 works when the processor encodes a video (also after the chip failed). */
    val cpuSpeed: CpuEncoderSpeed = CpuEncoderSpeed.FAST,
    /** The export's notification as a Live Update (Android 16+). */
    val liveUpdates: Boolean = true,
    /** The editor's work is kept as a recent project, in the app's private storage only. */
    val saveProjects: Boolean = true,
    /** How many recent projects are kept; the oldest goes when there are more. */
    val projectsLimit: Int = DEFAULT_PROJECTS_LIMIT,
    /** The welcome has been gone through (or skipped to its end): it shows on the first launch only. */
    val onboarded: Boolean = false,
) {
    companion object {
        const val DEFAULT_PROJECTS_LIMIT = 20

        /** The choices offered for [projectsLimit]. */
        val PROJECTS_LIMITS = listOf(5, 10, 20, 50)
    }
}

/** How much the exports kept in the app take. */
data class ExportsUsage(val files: Int, val bytes: Long)
