package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.CpuEncoderSpeed
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.OutputFormat

/**
 * Codec and container arguments for [format].
 *
 * @param quality    0..1, higher is better (picture formats).
 * @param withAudio  false when the output will carry no sound stream.
 * @param segmented  true when the segment muxer is in use, in which case the
 *                   container must be given as `-segment_format`.
 * @param audio      bitrate, bit depth and sample rate of an audio file.
 * @param gif        how a GIF is made (its loops; the rest is in the filters).
 * @param withCover  an audio file keeps a cover picture stream (see [CoverWriting]),
 *                   so pictures are not left out with `-vn`.
 * @param hardware   an MP4 or MKV picture encoded by this phone video encoder...
 * @param hardwareKbps ...at this bitrate (see [hardwareVideoKbps]); both null is x264.
 * @param x264Preset how hard x264 works (see [CpuEncoderSpeed]).
 */
internal fun encoderArguments(
    format: OutputFormat,
    quality: Float,
    withAudio: Boolean,
    segmented: Boolean,
    audio: AudioEncoding = AudioEncoding(),
    gif: GifSettings = GifSettings(),
    withCover: Boolean = false,
    hardware: HardwareEncoder? = null,
    hardwareKbps: Int? = null,
    x264Preset: String = CpuEncoderSpeed.BALANCED.preset,
    /** An HDR video: HEVC Main 10 by x265, which every phone that shows HDR plays. */
    hdr: Boolean = false,
): List<String> {
    val noPicture = if (withCover) emptyList() else listOf("-vn")
    val q = quality.coerceIn(0f, 1f)
    val args = mutableListOf<String>()
    val containerFlag = if (segmented) "-segment_format" else "-f"

    when (format) {
        OutputFormat.MP4, OutputFormat.MKV -> {
            args += if (hdr) {
                listOf(
                    "-vcodec", "libx265", "-crf", (qualityToCrf(q) + X265_CRF_OFFSET).toString(), "-preset", x264Preset,
                    "-pix_fmt", "yuv420p10le", "-profile:v", "main10",
                ) + (if (format == OutputFormat.MP4) listOf("-tag:v", "hvc1") else emptyList())
            } else if (hardware != null && hardwareKbps != null) {
                // The encoder and the input checked on this phone; a keyframe every 60
                // frames (two seconds at 30 fps) as x264 would place them, not the 1 s default.
                listOf(
                    "-vcodec", "h264_mediacodec", "-codec_name", hardware.codecName, "-pix_fmt", hardware.pixelFormat,
                    "-b:v", "${hardwareKbps}k", "-bitrate_mode", "vbr", "-g", HARDWARE_GOP.toString(),
                )
            } else {
                listOf("-vcodec", "libx264", "-crf", qualityToCrf(q).toString(), "-preset", x264Preset)
            }
            if (withAudio) args += listOf("-acodec", "aac", "-b:a", "128k")
        }
        OutputFormat.AVI -> {
            args += listOf("-vcodec", "libxvid", "-qscale:v", qualityToXvidScale(q).toString())
            if (withAudio) args += listOf("-acodec", "libmp3lame")
        }
        // 0 plays forever, -1 once, n that many more times.
        OutputFormat.GIF -> args += listOf("-loop", gif.loops.coerceIn(-1, 65535).toString())
        OutputFormat.MP3 -> args += lossy("libmp3lame", audio, 32, 320, noPicture) + sampleRate(audio) + listOf(containerFlag, "mp3")
        OutputFormat.M4A -> args += lossy("aac", audio, 32, 320, noPicture) + sampleRate(audio) + listOf(containerFlag, "ipod")
        OutputFormat.OGG -> args += lossy("libvorbis", audio, 64, 320, noPicture) + sampleRate(audio) + listOf(containerFlag, "ogg")
        // Opus always works at 48 kHz; FFmpeg resamples to it by itself.
        OutputFormat.OPUS -> args += lossy("libopus", audio, 16, 256, noPicture) + listOf(containerFlag, "opus")
        OutputFormat.FLAC -> {
            args += noPicture + listOf("-acodec", "flac")
            args += if (audio.bitDepth >= 24) listOf("-sample_fmt", "s32", "-bits_per_raw_sample", "24") else listOf("-sample_fmt", "s16")
            args += sampleRate(audio)
            args += listOf(containerFlag, "flac")
        }
        OutputFormat.WAV -> {
            args += listOf("-vn", "-acodec", if (audio.bitDepth >= 24) "pcm_s24le" else "pcm_s16le")
            args += sampleRate(audio)
            args += listOf(containerFlag, "wav")
        }
        OutputFormat.JPG, OutputFormat.PNG, OutputFormat.WEBP, OutputFormat.BMP, OutputFormat.TIFF ->
            args += imageEncoderArguments(format, quality)
        // A transcript is written by the speech model, never by FFmpeg.
        OutputFormat.TXT, OutputFormat.SRT -> Unit
        OutputFormat.CUSTOM ->
            throw IllegalArgumentException("The custom format has no generated encoder arguments")
    }
    return args
}

/**
 * Codec of a picture file. JPG keeps full colour (4:4:4) at a quality of
 * [quality] 0..1. WEBP has no encoder in the bundled FFmpeg: it writes a PNG,
 * which the platform's encoder then turns into WEBP. With [alpha] the picture
 * keeps its transparent parts (PNG, WEBP and TIFF only).
 */
internal fun imageEncoderArguments(format: OutputFormat, quality: Float, alpha: Boolean = false): List<String> {
    val rgb = if (alpha && format.holdsTransparency) "rgba" else "rgb24"
    return when (format) {
        OutputFormat.JPG -> listOf("-c:v", "mjpeg", "-q:v", qualityToJpegScale(quality).toString(), "-pix_fmt", "yuvj444p")
        OutputFormat.PNG -> listOf("-c:v", "png", "-pix_fmt", rgb)
        OutputFormat.WEBP -> listOf("-c:v", "png", "-pix_fmt", rgb, "-f", "image2")
        OutputFormat.BMP -> listOf("-c:v", "bmp", "-pix_fmt", "bgr24")
        OutputFormat.TIFF -> listOf("-c:v", "tiff", "-pix_fmt", rgb, "-compression_algo", "deflate")
        else -> throw IllegalArgumentException("$format is not a picture format")
    }
}

/** mjpeg quantiser: 31 (worst) .. 2 (best). */
internal fun qualityToJpegScale(quality: Float): Int = (31 - quality.coerceIn(0f, 1f) * 29).toInt().coerceIn(2, 31)

/** [noPicture], then [codec] at the chosen bitrate, kept within what it accepts. */
private fun lossy(codec: String, audio: AudioEncoding, minKbps: Int, maxKbps: Int, noPicture: List<String>): List<String> =
    noPicture + listOf("-acodec", codec, "-b:a", "${audio.bitrateKbps.coerceIn(minKbps, maxKbps)}k")

/** The output sample rate, when one is chosen. */
private fun sampleRate(audio: AudioEncoding): List<String> =
    if (audio.sampleRate > 0) listOf("-ar", audio.sampleRate.toString()) else emptyList()

/**
 * Bitrate asked of the phone's video encoder for a picture of [width] x [height]
 * at [fps] (an unknown size counts as 1080p): [quality] 0..1 as 0.02 to 0.25 bits
 * per pixel, along a curve (0.7 is about 8 Mbit/s at 1080p30, near what the
 * camera records), within 300 kbit/s .. 60 Mbit/s.
 */
internal fun hardwareVideoKbps(width: Int, height: Int, fps: Int, quality: Float): Int {
    val pixels = if (width > 0 && height > 0) width.toLong() * height else 1920L * 1080L
    val q = quality.coerceIn(0f, 1f)
    val bitsPerPixel = 0.02 + q * q * 0.23
    val kbps = pixels * fps.coerceIn(1, 240) * bitsPerPixel / 1000.0
    return Math.round(kbps).toInt().coerceIn(HARDWARE_MIN_KBPS, HARDWARE_MAX_KBPS)
}

private const val HARDWARE_GOP = 60
private const val HARDWARE_MIN_KBPS = 300
private const val HARDWARE_MAX_KBPS = 60_000

/** x265 reaches x264's quality at a CRF this much higher. */
private const val X265_CRF_OFFSET = 5

/** x264 CRF: 51 (worst) .. 5 (visually lossless). */
internal fun qualityToCrf(quality: Float): Int = (51 - quality * 46).toInt().coerceIn(0, 51)

/** Xvid quantiser scale: 10 (worst) .. 1 (best). */
internal fun qualityToXvidScale(quality: Float): Int = (10 - quality * 9).toInt().coerceIn(1, 10)
