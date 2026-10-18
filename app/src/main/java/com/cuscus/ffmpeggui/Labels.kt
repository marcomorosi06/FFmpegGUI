package com.cuscus.ffmpeggui

import androidx.annotation.StringRes
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation

/*
 * Display names for the :core enums. The enums themselves carry no text, so
 * this is the single place that ties them to string resources.
 */

@get:StringRes
val OutputFormat.labelRes: Int
    get() = when (this) {
        OutputFormat.MP4 -> R.string.format_mp4
        OutputFormat.MKV -> R.string.format_mkv
        OutputFormat.AVI -> R.string.format_avi
        OutputFormat.GIF -> R.string.format_gif
        OutputFormat.MP3 -> R.string.format_mp3
        OutputFormat.M4A -> R.string.format_m4a
        OutputFormat.OGG -> R.string.format_ogg
        OutputFormat.OPUS -> R.string.format_opus
        OutputFormat.FLAC -> R.string.format_flac
        OutputFormat.WAV -> R.string.format_wav
        OutputFormat.JPG -> R.string.format_jpg
        OutputFormat.PNG -> R.string.format_png
        OutputFormat.WEBP -> R.string.format_webp
        OutputFormat.BMP -> R.string.format_bmp
        OutputFormat.TIFF -> R.string.format_tiff
        OutputFormat.TXT -> R.string.format_txt
        OutputFormat.SRT -> R.string.format_srt
        OutputFormat.CUSTOM -> R.string.format_custom
    }

@get:StringRes
val FormatCategory.labelRes: Int
    get() = when (this) {
        FormatCategory.VIDEO -> R.string.format_category_video
        FormatCategory.ANIMATION -> R.string.format_category_animation
        FormatCategory.AUDIO -> R.string.format_category_audio
        FormatCategory.IMAGE -> R.string.format_category_image
        FormatCategory.TEXT -> R.string.format_category_text
        FormatCategory.CUSTOM -> R.string.format_category_custom
    }

@get:StringRes
val Resolution.labelRes: Int
    get() = when (this) {
        Resolution.P240 -> R.string.resolution_240p
        Resolution.P480 -> R.string.resolution_480p
        Resolution.P720 -> R.string.resolution_720p
        Resolution.P1080 -> R.string.resolution_1080p
        Resolution.ORIGINAL -> R.string.resolution_original
    }

@get:StringRes
val Framerate.labelRes: Int
    get() = when (this) {
        Framerate.FPS10 -> R.string.framerate_10
        Framerate.FPS15 -> R.string.framerate_15
        Framerate.FPS24 -> R.string.framerate_24
        Framerate.FPS30 -> R.string.framerate_30
        Framerate.ORIGINAL -> R.string.framerate_original
    }

@get:StringRes
val Rotation.labelRes: Int
    get() = when (this) {
        Rotation.NONE -> R.string.rotation_none
        Rotation.DEG_90 -> R.string.rotation_90
        Rotation.DEG_180 -> R.string.rotation_180
        Rotation.DEG_270 -> R.string.rotation_270
    }

@get:StringRes
val CanvasRatio.labelRes: Int
    get() = when (this) {
        CanvasRatio.FIT -> R.string.canvas_fit
        CanvasRatio.SQUARE -> R.string.ratio_1_1
        CanvasRatio.LANDSCAPE_16_9 -> R.string.ratio_16_9
        CanvasRatio.LANDSCAPE_4_3 -> R.string.ratio_4_3
        CanvasRatio.PORTRAIT_9_16 -> R.string.ratio_9_16
    }

@get:StringRes
val FrameExtractionMode.labelRes: Int
    get() = when (this) {
        FrameExtractionMode.DISABLED -> R.string.extract_mode_disabled
        FrameExtractionMode.SINGLE -> R.string.extract_mode_single
        FrameExtractionMode.SEQUENCE -> R.string.extract_mode_sequence
    }
