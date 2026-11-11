package com.cuscus.ffmpeggui.settings

import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed

/** Light, dark, or as the system is. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * How the editors lay themselves out on a screen wider than it is tall: as
 * the window's size suggests (side by side on a phone, the timeline across
 * the bottom on a tablet), or always side by side with the player on the left
 * or on the right of the tools.
 */
enum class LandscapeLayout { AUTO, PLAYER_LEFT, PLAYER_RIGHT }

/**
 * What a long press on a subject does in the photo editor: [COPY] lays a copy
 * of it as a sticker and leaves the photo as it was; [CUT] does the same and
 * leaves a hole where it was (see-through, or a colour); [ERASE] fills that
 * place in with the magic eraser (only while its model is on the phone).
 */
enum class LiftMode { COPY, CUT, ERASE }

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
    /**
     * Greys only, for every screen: no colour to take the eye off the work. It
     * takes the place of the wallpaper's colours or the teal while it is on.
     */
    val monochrome: Boolean = false,
    /** Black backgrounds in the dark theme, for OLED screens. */
    val pureBlack: Boolean = false,
    /** Light taps under buttons, sliders and the timeline. */
    val haptics: Boolean = true,
    /** Every finished file also goes to Movies, Music, Pictures or Download (Android 10+). */
    val saveCopies: Boolean = true,
    /**
     * Stickers saved to the app's own gallery also go to Pictures/FFmpegGUI/Stickers,
     * where the phone's gallery finds them. Off: they stay in the app only.
     */
    val stickerCopies: Boolean = false,
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
    /** The video editor's timeline draws each clip's own sound along the bottom of its frames. */
    val clipWaveforms: Boolean = true,
    /** The audio editor shows the spectrum of what plays under its waveform (when there is room). */
    val audioSpectrum: Boolean = true,
    /** Where the player goes when the screen is wider than it is tall. */
    val landscapeLayout: LandscapeLayout = LandscapeLayout.AUTO,
    /** The welcome has been gone through (or skipped to its end): it shows on the first launch only. */
    val onboarded: Boolean = false,
    /**
     * The home's card for Keep Android Open (keepandroidopen.org), the
     * campaign against Google's registration of every Android developer.
     * Closed from the home, it comes back from the settings (Appearance).
     */
    val keepAndroidOpenBanner: Boolean = true,
    /** What a long press on a subject does in the photo editor. */
    val liftMode: LiftMode = LiftMode.COPY,
    /** With [LiftMode.CUT]: the hole is see-through; else it is [liftHoleColor]. */
    val liftHoleTransparent: Boolean = true,
    /** The hole's colour when it is not see-through (ARGB). */
    val liftHoleColor: Long = 0xFFFFFFFFL,
    /** What shows in a see-through hole in a file that holds no transparency (JPG...), ARGB. */
    val liftHoleOpaque: Long = 0xFFFFFFFFL,
) {
    companion object {
        const val DEFAULT_PROJECTS_LIMIT = 20

        /** The choices offered for [projectsLimit]. */
        val PROJECTS_LIMITS = listOf(5, 10, 20, 50)
    }
}

/** How much the exports kept in the app take. */
data class ExportsUsage(val files: Int, val bytes: Long)
