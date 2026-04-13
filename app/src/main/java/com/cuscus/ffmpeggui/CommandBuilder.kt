package com.cuscus.ffmpeggui

/**
 * Builds FFmpeg argument arrays and human-readable previews from a [ConversionConfig].
 *
 * The two public entry points ([build] and [buildPreview]) share all filter-construction
 * logic via private helpers, so any change only needs to be made in one place.
 */
object CommandBuilder {

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the argument array to pass directly to FFmpegKit.
     * Input/output paths use sentinel values that the caller must replace:
     *   "input_placeholder"  → real input file path
     *   "audio_placeholder"  → SAF-resolved audio track path
     */
    fun build(config: ConversionConfig, outputPath: String): Array<String> {
        if (config.isManualMode) {
            return resolveCustomCommand(config.manualCommandOverride, config, outputPath)
        }
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return resolveCustomCommand(config.customCommand, config, outputPath)
        }

        val args = mutableListOf<String>()
        args += "-y"
        appendTrimArgs(args, config)
        args += "-i"
        args += "input_placeholder"
        if (config.audioTrackUri != null) {
            args += "-i"
            args += "audio_placeholder"
        }

        appendAudioArgs(args, config)
        appendFormatArgs(args, config)
        args += outputPath
        return args.toTypedArray()
    }

    /**
     * Returns a human-readable FFmpeg command string for display purposes only.
     * Not executable – uses placeholder filenames.
     */
    fun buildPreview(config: ConversionConfig): String {
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return config.customCommand.ifBlank { "ffmpeg -i input.${getInputExtension(config)} ..." }
        }

        val sb = StringBuilder("ffmpeg -y")
        appendTrimPreview(sb, config)
        sb.append(" -i input.${getInputExtension(config)}")
        if (config.audioTrackUri != null) sb.append(" -i audio_track.mp3")

        appendAudioPreview(sb, config)
        appendFormatPreview(sb, config)
        sb.append(" output.${config.outputFormat.extension}")
        return sb.toString()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Trim helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun appendTrimArgs(args: MutableList<String>, config: ConversionConfig) {
        if (config.startTime.isNotBlank() && config.startTime != "00:00:00") {
            args += "-ss"; args += config.startTime
        }
        if (config.endTime.isNotBlank()) {
            args += "-to"; args += config.endTime
        }
    }

    private fun appendTrimPreview(sb: StringBuilder, config: ConversionConfig) {
        if (config.startTime.isNotBlank() && config.startTime != "00:00:00") {
            sb.append(" -ss ${config.startTime}")
        }
        if (config.endTime.isNotBlank()) sb.append(" -to ${config.endTime}")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Audio helpers — both args and preview are derived from AudioFilterSpec
    // ──────────────────────────────────────────────────────────────────────────

    private data class AudioFilterSpec(
        val baseFilters: List<String>,     // filters on stream 0:a
        val extFilters: List<String>,      // filters on stream 1:a (external track)
        val hasExternalTrack: Boolean,
        val removeAudio: Boolean,
    )

    private fun buildAudioFilterSpec(config: ConversionConfig): AudioFilterSpec {
        val finalDurationS = (config.activeVideoDurationMs / 1000f) / config.videoSpeed

        val base = mutableListOf<String>()
        if (config.removeAudio) {
            base += "volume=0"
        } else {
            if (config.isReversed) base += "areverse"
            buildAtempo(config.videoSpeed)?.let { base += it }
            if (config.volumeLevel != 1.0f) base += "volume=${config.volumeLevel}"
            if (config.normalizeAudio) base += "loudnorm"
            appendFadeAudioFilters(base, config, finalDurationS)
        }

        val ext = mutableListOf<String>()
        if (config.audioTrackUri != null) {
            ext += "asetpts=N/SR/TB"
            val startMs = timeToMs(config.audioTrackTrimStart)
            val endMs = if (config.audioTrackTrimEnd.isNotBlank()) timeToMs(config.audioTrackTrimEnd) else 0L
            if (startMs > 0 || endMs > 0) {
                var trim = "atrim=start=${startMs / 1000f}"
                if (endMs > startMs) trim += ":end=${endMs / 1000f}"
                ext += trim
            }
            ext += "asetpts=PTS-STARTPTS"
            if (config.audioTrackVolume != 1.0f) ext += "volume=${config.audioTrackVolume}"
            if (config.audioTrackDelay != "00:00:00") {
                val delayMs = timeToMs(config.audioTrackDelay)
                if (delayMs > 0) ext += "adelay=$delayMs|$delayMs"
            }
            appendFadeAudioFilters(ext, config, finalDurationS)
            ext += "apad"
        }

        return AudioFilterSpec(
            baseFilters = base,
            extFilters = ext,
            hasExternalTrack = config.audioTrackUri != null,
            removeAudio = config.removeAudio,
        )
    }

    private fun appendFadeAudioFilters(
        list: MutableList<String>,
        config: ConversionConfig,
        finalDurationS: Float,
    ) {
        if (config.fadeInDuration > 0f) {
            list += String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration)
        }
        if (config.fadeOutDuration > 0f && finalDurationS > 0) {
            val fadeOutStart = finalDurationS - config.fadeOutDuration
            if (fadeOutStart > 0) {
                list += String.format(
                    java.util.Locale.US,
                    "afade=t=out:st=%.3f:d=%.1f",
                    fadeOutStart,
                    config.fadeOutDuration,
                )
            }
        }
    }

    private fun appendAudioArgs(args: MutableList<String>, config: ConversionConfig) {
        val spec = buildAudioFilterSpec(config)
        if (spec.hasExternalTrack) {
            // ── IMPORTANT ────────────────────────────────────────────────────
            // When filter_complex is used with explicit -map, FFmpeg does NOT
            // also apply a separate -vf. Any video filters MUST be included
            // inside the same filter_complex graph, mapped to [vout].
            // Using -map 0:v + a separate -vf after -filter_complex causes
            // the audio graph to be silently dropped on some FFmpeg builds.
            // ─────────────────────────────────────────────────────────────────
            val videoFilter = buildVideoFilter(config)
            val complex = buildComplexFilterWithVideo(spec, videoFilter)
            args += "-filter_complex"; args += complex.joinToString(";")
            args += "-map"; args += "[vout]"
            args += "-map"; args += "[aout]"
        } else {
            if (spec.removeAudio) {
                args += "-an"
            } else if (spec.baseFilters.isNotEmpty()) {
                args += "-af"; args += spec.baseFilters.joinToString(",")
            }
        }
    }

    private fun appendAudioPreview(sb: StringBuilder, config: ConversionConfig) {
        val spec = buildAudioFilterSpec(config)
        if (spec.hasExternalTrack) {
            val videoFilter = buildVideoFilter(config)
            val complex = buildComplexFilterWithVideo(spec, videoFilter)
            sb.append(" -filter_complex \"${complex.joinToString(";")}\" -map [vout] -map [aout]")
        } else {
            if (spec.removeAudio) {
                sb.append(" -an")
            } else if (spec.baseFilters.isNotEmpty()) {
                sb.append(" -af \"${spec.baseFilters.joinToString(",")}\"")
            }
        }
    }

    /**
     * Builds the full filter_complex graph when an external audio track is present.
     *
     * All video and audio filters are merged into one -filter_complex so that
     * -map [vout] and -map [aout] can both be used without any -vf conflict.
     *
     * Structure:
     *   [0:v]<video_filters>[vout]    — video chain (null passthrough if no filters)
     *   [0:a]<base_filters>[a0]       — primary audio chain
     *   [1:a]<ext_filters>[a1]        — external track chain
     *   [a0][a1]amix...[aout]         — stereo mix
     */
    private fun buildComplexFilterWithVideo(
        spec: AudioFilterSpec,
        videoFilter: String?,
    ): List<String> {
        val result = mutableListOf<String>()
        // Video leg — always produces [vout]
        result += if (videoFilter != null) "[0:v]$videoFilter[vout]" else "[0:v]null[vout]"
        // Primary audio leg
        result += if (spec.baseFilters.isNotEmpty())
            "[0:a]${spec.baseFilters.joinToString(",")}[a0]"
        else
            "[0:a]anull[a0]"
        // External audio leg
        result += "[1:a]${spec.extFilters.joinToString(",")}[a1]"
        // Mix
        result += "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[aout]"
        return result
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Format / video codec helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun appendFormatArgs(args: MutableList<String>, config: ConversionConfig) {
        // When an external audio track is present, video filters were already
        // embedded into filter_complex and mapped as [vout]. Passing -vf here
        // again would conflict with the explicit -map [vout].
        val videoAlreadyMapped = config.audioTrackUri != null
        when (config.outputFormat) {
            OutputFormat.GIF    -> buildGifArgs(args, config, skipVf = videoAlreadyMapped)
            OutputFormat.WEBP   -> buildWebpArgs(args, config, skipVf = videoAlreadyMapped)
            OutputFormat.MP3    -> buildAudioCodecArgs(args, config, "libmp3lame", "mp3")
            OutputFormat.FLAC   -> buildFlacArgs(args)
            OutputFormat.OGG    -> buildAudioCodecArgs(args, config, "libvorbis", "ogg")
            OutputFormat.MP4    -> buildVideoCodecArgs(args, config, "libx264", "aac", skipVf = videoAlreadyMapped)
            OutputFormat.MKV    -> buildVideoCodecArgs(args, config, "libx264", "aac", skipVf = videoAlreadyMapped)
            OutputFormat.AVI    -> buildVideoCodecArgs(args, config, "libxvid", "libmp3lame", skipVf = videoAlreadyMapped)
            OutputFormat.CUSTOM -> {}
        }
    }

    private fun appendFormatPreview(sb: StringBuilder, config: ConversionConfig) {
        val vf = buildVideoFilter(config)
        when (config.outputFormat) {
            OutputFormat.GIF -> {
                val gifFilter = if (vf != null) "$vf,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                else "split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                sb.append(" -vf \"$gifFilter\" -loop 0")
            }
            OutputFormat.WEBP -> {
                if (vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -quality ${(config.qualityLevel * 100).toInt()} -loop 0")
            }
            OutputFormat.MP3  -> sb.append(" -vn -acodec libmp3lame -b:a ${qualityToBitrate(config.qualityLevel)}k")
            OutputFormat.FLAC -> sb.append(" -vn -acodec flac")
            OutputFormat.OGG  -> sb.append(" -vn -acodec libvorbis -q:a ${(config.qualityLevel * 10).toInt()}")
            OutputFormat.MP4, OutputFormat.MKV -> {
                if (vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -vcodec libx264 -crf ${qualityToCrf(config.qualityLevel)} -preset medium -acodec aac -b:a 128k")
            }
            OutputFormat.AVI -> {
                if (vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -vcodec libxvid -qscale:v ${(10 - config.qualityLevel * 9).toInt().coerceIn(1, 10)} -acodec libmp3lame")
            }
            OutputFormat.CUSTOM -> {}
        }
    }

    private fun buildGifArgs(args: MutableList<String>, config: ConversionConfig, skipVf: Boolean = false) {
        if (!skipVf) {
            val base = buildVideoFilter(config) ?: ""
            val withScale = if (!base.contains("scale=")) base + (if (base.isEmpty()) "" else ",") + "scale=480:-1" else base
            val withFps = if (!withScale.contains("fps=")) "$withScale,fps=15" else withScale
            args += "-vf"; args += "$withFps,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
        }
        args += "-loop"; args += "0"
    }

    private fun buildWebpArgs(args: MutableList<String>, config: ConversionConfig, skipVf: Boolean = false) {
        if (!skipVf) buildVideoFilter(config)?.let { args += "-vf"; args += it }
        args += "-quality"; args += (config.qualityLevel * 100).toInt().toString()
        args += "-loop"; args += "0"
    }

    private fun buildAudioCodecArgs(
        args: MutableList<String>,
        config: ConversionConfig,
        codec: String,
        format: String,
    ) {
        args += "-vn"; args += "-acodec"; args += codec
        when (codec) {
            "libmp3lame" -> { args += "-b:a"; args += "${qualityToBitrate(config.qualityLevel)}k" }
            "libvorbis"  -> { args += "-q:a"; args += (config.qualityLevel * 10).toInt().toString() }
        }
        args += "-f"; args += format
    }

    private fun buildFlacArgs(args: MutableList<String>) {
        args += "-vn"; args += "-acodec"; args += "flac"
    }

    private fun buildVideoCodecArgs(
        args: MutableList<String>,
        config: ConversionConfig,
        vcodec: String,
        acodec: String,
        skipVf: Boolean = false,
    ) {
        if (!skipVf) buildVideoFilter(config)?.let { args += "-vf"; args += it }
        args += "-vcodec"; args += vcodec
        when (vcodec) {
            "libx264" -> {
                args += "-crf";     args += qualityToCrf(config.qualityLevel).toString()
                args += "-preset";  args += "medium"
            }
            "libxvid" -> {
                args += "-qscale:v"
                args += (10 - config.qualityLevel * 9).toInt().coerceIn(1, 10).toString()
            }
        }
        args += "-acodec"; args += acodec
        if (acodec == "aac") { args += "-b:a"; args += "128k" }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Video filter graph
    // ──────────────────────────────────────────────────────────────────────────

    internal fun buildVideoFilter(config: ConversionConfig): String? {
        val filters = mutableListOf<String>()

        if (config.isReversed)      filters += "reverse"
        if (config.flipHorizontal)  filters += "hflip"
        if (config.flipVertical)    filters += "vflip"

        when (config.rotation) {
            Rotation.DEG_90  -> filters += "transpose=1"
            Rotation.DEG_180 -> { filters += "transpose=1"; filters += "transpose=1" }
            Rotation.DEG_270 -> filters += "transpose=2"
            Rotation.NONE    -> {}
        }

        if (config.cropW.isNotBlank() && config.cropH.isNotBlank() &&
            config.cropX.isNotBlank() && config.cropY.isNotBlank()
        ) {
            val isDefaultCrop = config.cropW == "1.00*in_w" && config.cropH == "1.00*in_h" &&
                    config.cropX == "0.00*in_w" && config.cropY == "0.00*in_h"
            if (!isDefaultCrop) {
                filters += "crop=trunc((${config.cropW})/2)*2:trunc((${config.cropH})/2)*2:${config.cropX}:${config.cropY}"
            }
        }

        buildPadFilter(config.projectRatio)?.let { filters += it }

        if (config.resolution != Resolution.ORIGINAL) filters += "scale=-2:${config.resolution.height}"
        if (config.framerate != Framerate.ORIGINAL)   filters += "fps=${config.framerate.value}"

        if (config.brightness != 0f || config.contrast != 1f || config.saturation != 1f) {
            filters += String.format(
                java.util.Locale.US,
                "eq=brightness=%.2f:contrast=%.2f:saturation=%.2f",
                config.brightness, config.contrast, config.saturation,
            )
        }

        config.textOverlays.filter { it.text.isNotBlank() }.forEach { overlay ->
            filters += buildDrawtextFilter(overlay)
        }

        val finalDurationS = (config.activeVideoDurationMs / 1000f) / config.videoSpeed
        if (config.fadeInDuration > 0f) {
            filters += String.format(java.util.Locale.US, "fade=t=in:st=0:d=%.1f", config.fadeInDuration)
        }
        if (config.fadeOutDuration > 0f && finalDurationS > 0) {
            val fadeOutStart = finalDurationS - config.fadeOutDuration
            if (fadeOutStart > 0) {
                filters += String.format(java.util.Locale.US, "fade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration)
            }
        }
        if (config.videoSpeed != 1.0f) {
            filters += String.format(java.util.Locale.US, "setpts=%.4f*PTS", 1.0f / config.videoSpeed)
        }

        return if (filters.isNotEmpty()) filters.joinToString(",") else null
    }

    private fun buildPadFilter(ratio: String): String? = when (ratio) {
        "1:1"  -> "pad=width='ceil(max(iw,ih)/2)*2':height='ceil(max(ih,iw)/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
        "16:9" -> "pad=width='ceil(max(iw,ih*(16/9))/2)*2':height='ceil(max(ih,iw/(16/9))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
        "4:3"  -> "pad=width='ceil(max(iw,ih*(4/3))/2)*2':height='ceil(max(ih,iw/(4/3))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
        "9:16" -> "pad=width='ceil(max(iw,ih*(9/16))/2)*2':height='ceil(max(ih,iw/(9/16))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
        else   -> null
    }

    private fun buildDrawtextFilter(overlay: TextOverlay): String {
        val text = overlay.text.replace("'", "\\'").replace(":", "\\:")
        val hexColor = String.format("%06X", (overlay.color and 0xFFFFFF))
        val fontPath = FontResolver.resolve(overlay.isBold)
        return String.format(
            java.util.Locale.US,
            "drawtext=fontfile='%s':text='%s':fontcolor=0x%s:fontsize=h*%.4f:x=(w-tw)*%.4f:y=(h-th)*%.4f",
            fontPath, text, hexColor, overlay.size, overlay.x, overlay.y,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Custom command resolution
    // ──────────────────────────────────────────────────────────────────────────

    private fun resolveCustomCommand(
        cmdString: String,
        config: ConversionConfig,
        outputPath: String,
    ): Array<String> {
        val resolved = cmdString
            .replace("input.${getInputExtension(config)}", "input_placeholder")
            .replace("output.${config.outputFormat.extension}", outputPath)
        return com.arthenica.ffmpegkit.FFmpegKitConfig.parseArguments(resolved)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds a chain of atempo filters to handle speeds outside the [0.5, 2.0]
     * range that a single atempo filter supports.
     */
    private fun buildAtempo(speed: Float): String? {
        if (speed == 1.0f) return null
        var s = speed
        val filters = mutableListOf<String>()
        while (s < 0.5f)    { filters += "atempo=0.5";   s *= 2.0f }
        while (s > 100.0f)  { filters += "atempo=100.0"; s /= 100.0f }
        if (s != 1.0f) filters += String.format(java.util.Locale.US, "atempo=%.4f", s)
        return filters.joinToString(",").ifEmpty { null }
    }

    private fun qualityToCrf(quality: Float): Int = (51 - quality * 46).toInt().coerceIn(0, 51)
    private fun qualityToBitrate(quality: Float): Int = (64 + quality * 256).toInt()
    private fun getInputExtension(config: ConversionConfig): String =
        config.inputFileName.substringAfterLast(".", "mp4")

    internal fun timeToMs(timeStr: String): Long = try {
        val parts = timeStr.split(":")
        if (parts.size == 3) {
            val h = parts[0].toLongOrNull() ?: 0L
            val m = parts[1].toLongOrNull() ?: 0L
            val sParts = parts[2].split(".")
            val s = sParts[0].toLongOrNull() ?: 0L
            val msStr = if (sParts.size > 1) sParts[1].padEnd(3, '0').substring(0, 3) else "000"
            val ms = msStr.toLongOrNull() ?: 0L
            h * 3_600_000L + m * 60_000L + s * 1_000L + ms
        } else 0L
    } catch (_: Exception) { 0L }
}