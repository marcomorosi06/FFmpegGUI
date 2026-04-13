package com.cuscus.ffmpeggui

/**
 * Builds FFmpeg argument arrays and human-readable previews from a [ConversionConfig].
 *
 * The two public entry points ([build] and [buildPreview]) share all filter-construction
 * logic via private helpers, so any change only needs to be made in one place.
 *
 * Input / output path sentinels used in [build]:
 *   "input_placeholder"        → real input file path (or concat list path)
 *   "audio_placeholder"        → SAF-resolved external audio track path
 *   "overlay_N_placeholder"    → absolute path to the Nth image overlay (N = 0-based index)
 *
 * The caller (ViewModel) is responsible for replacing these sentinels before
 * passing the array to FFmpegKit.
 */
object CommandBuilder {

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    fun build(config: ConversionConfig, outputPath: String): Array<String> {
        if (config.isManualMode) {
            return resolveCustomCommand(config.manualCommandOverride, config, outputPath)
        }
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return resolveCustomCommand(config.customCommand, config, outputPath)
        }

        // Frame extraction is a completely separate operating mode
        if (config.frameExtractionMode != FrameExtractionMode.DISABLED) {
            return buildFrameExtraction(config, outputPath)
        }

        val args = mutableListOf<String>()
        args += "-y"

        // ── Inputs ────────────────────────────────────────────────────────────
        if (config.concatListPath.isNotBlank()) {
            // Multi-clip concat: the ViewModel has already written the concat list
            args += "-f";    args += "concat"
            args += "-safe"; args += "0"
            args += "-i";    args += config.concatListPath
        } else {
            appendTrimArgs(args, config)
            args += "-i"; args += "input_placeholder"
        }

        // One -i per image overlay (resolved by ViewModel at execution time)
        config.imageOverlays.forEachIndexed { idx, _ ->
            args += "-i"; args += "overlay_${idx}_placeholder"
        }

        // External audio track (must come after overlays so its stream index is correct)
        val audioInputIndex = 1 + config.imageOverlays.size
        if (config.audioTrackUri != null) {
            args += "-i"; args += "audio_placeholder"
        }

        // ── Fast remux – skip all filters ─────────────────────────────────────
        if (config.useStreamCopy) {
            args += "-c"; args += "copy"
            args += outputPath
            return args.toTypedArray()
        }

        // ── Filters + codec ───────────────────────────────────────────────────
        appendAudioAndVideoFilters(args, config, audioInputIndex)
        appendFormatArgs(args, config)
        args += outputPath
        return args.toTypedArray()
    }

    fun buildPreview(config: ConversionConfig): String {
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return config.customCommand.ifBlank { "ffmpeg -i input.${getInputExtension(config)} ..." }
        }
        if (config.frameExtractionMode != FrameExtractionMode.DISABLED) {
            return buildFrameExtractionPreview(config)
        }

        val sb = StringBuilder("ffmpeg -y")

        if (config.concatListPath.isNotBlank()) {
            sb.append(" -f concat -safe 0 -i concat_list.txt")
        } else {
            appendTrimPreview(sb, config)
            sb.append(" -i input.${getInputExtension(config)}")
        }

        config.imageOverlays.forEachIndexed { idx, ov ->
            sb.append(" -i ${ov.fileName.ifBlank { "overlay_$idx.png" }}")
        }
        if (config.audioTrackUri != null) sb.append(" -i audio_track.mp3")

        if (config.useStreamCopy) {
            sb.append(" -c copy")
        } else {
            appendAudioAndVideoFiltersPreview(sb, config)
            appendFormatPreview(sb, config)
        }

        val ext = if (config.frameExtractionMode == FrameExtractionMode.SEQUENCE) "png" else config.outputFormat.extension
        sb.append(" output.$ext")
        return sb.toString()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Frame extraction
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildFrameExtraction(config: ConversionConfig, outputPath: String): Array<String> {
        val args = mutableListOf<String>()
        args += "-y"
        when (config.frameExtractionMode) {
            FrameExtractionMode.SINGLE -> {
                if (config.frameExtractionTimecode != "00:00:00") {
                    args += "-ss"; args += config.frameExtractionTimecode
                }
                args += "-i"; args += "input_placeholder"
                args += "-frames:v"; args += "1"
                args += "-q:v"; args += "2"
            }
            FrameExtractionMode.SEQUENCE -> {
                args += "-i"; args += "input_placeholder"
                val fps = String.format(java.util.Locale.US, "%.4f", config.frameExtractionRate)
                args += "-vf"; args += "fps=$fps"
                args += "-q:v"; args += "2"
            }
            FrameExtractionMode.DISABLED -> { /* unreachable */ }
        }
        args += outputPath
        return args.toTypedArray()
    }

    private fun buildFrameExtractionPreview(config: ConversionConfig): String = when (config.frameExtractionMode) {
        FrameExtractionMode.SINGLE -> {
            val ss = if (config.frameExtractionTimecode != "00:00:00") " -ss ${config.frameExtractionTimecode}" else ""
            "ffmpeg -y$ss -i input.${getInputExtension(config)} -frames:v 1 -q:v 2 output.png"
        }
        FrameExtractionMode.SEQUENCE -> {
            val fps = String.format(java.util.Locale.US, "%.4f", config.frameExtractionRate)
            "ffmpeg -y -i input.${getInputExtension(config)} -vf fps=$fps -q:v 2 output_%03d.png"
        }
        FrameExtractionMode.DISABLED -> ""
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
    // Unified filter dispatcher
    // (decides between simple -vf/-af or full filter_complex)
    // ──────────────────────────────────────────────────────────────────────────

    private fun needsFilterComplex(config: ConversionConfig): Boolean =
        config.audioTrackUri != null || config.imageOverlays.isNotEmpty()

    private fun appendAudioAndVideoFilters(
        args: MutableList<String>,
        config: ConversionConfig,
        audioInputIndex: Int,
    ) {
        val spec = buildAudioFilterSpec(config)

        if (needsFilterComplex(config)) {
            val videoFilter = buildVideoFilter(config)
            val complex = buildComplexFilter(spec, videoFilter, config, audioInputIndex)
            args += "-filter_complex"; args += complex.joinToString(";")
            args += "-map"; args += "[vout]"
            when {
                spec.hasExternalTrack -> { args += "-map"; args += "[aout]" }
                spec.removeAudio      -> { /* intentionally no audio stream */ }
                else                  -> { args += "-map"; args += "0:a?" }
            }
        } else {
            // Simple path: -an / -af for audio; -vf goes inside appendFormatArgs
            if (spec.removeAudio) {
                args += "-an"
            } else if (spec.baseFilters.isNotEmpty()) {
                args += "-af"; args += spec.baseFilters.joinToString(",")
            }
        }
    }

    private fun appendAudioAndVideoFiltersPreview(sb: StringBuilder, config: ConversionConfig) {
        val spec = buildAudioFilterSpec(config)

        if (needsFilterComplex(config)) {
            val videoFilter = buildVideoFilter(config)
            // Build a representative audioInputIndex for the preview (1 + overlay count)
            val audioIdx = 1 + config.imageOverlays.size
            val complex = buildComplexFilter(spec, videoFilter, config, audioIdx)
            sb.append(" -filter_complex \"${complex.joinToString(";")}\"")
            sb.append(" -map [vout]")
            when {
                spec.hasExternalTrack -> sb.append(" -map [aout]")
                spec.removeAudio      -> { /* no audio */ }
                else                  -> sb.append(" -map 0:a?")
            }
        } else {
            if (spec.removeAudio) {
                sb.append(" -an")
            } else if (spec.baseFilters.isNotEmpty()) {
                sb.append(" -af \"${spec.baseFilters.joinToString(",")}\"")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Audio filter spec
    // ──────────────────────────────────────────────────────────────────────────

    private data class AudioFilterSpec(
        val baseFilters: List<String>,
        val extFilters: List<String>,
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
            val endMs   = if (config.audioTrackTrimEnd.isNotBlank()) timeToMs(config.audioTrackTrimEnd) else 0L
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
            baseFilters      = base,
            extFilters       = ext,
            hasExternalTrack = config.audioTrackUri != null,
            removeAudio      = config.removeAudio,
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

    // ──────────────────────────────────────────────────────────────────────────
    // filter_complex builder
    // Handles any combination of: image overlays, external audio track.
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Builds the full -filter_complex graph.
     *
     * Video chain:
     *   [0:v] → main video filters → [vmain]
     *        → overlay 0 (stream 1:v) at pixel position → [vtmp0]
     *        → overlay 1 (stream 2:v) at pixel position → [vtmp1]
     *        → ...
     *        → [vout]
     *
     * Audio chain (only when external track present):
     *   [0:a]   → base filters → [a0]
     *   [N+1:a] → ext  filters → [a1]
     *   [a0][a1] → amix → [aout]
     *
     * When image overlays are absent the video chain degenerates to the original
     * single-input form: [0:v]{main_filters}[vout].
     */
    private fun buildComplexFilter(
        spec: AudioFilterSpec,
        videoFilter: String?,
        config: ConversionConfig,
        audioInputIndex: Int,
    ): List<String> {
        val result = mutableListOf<String>()
        val overlays = config.imageOverlays

        // ── Video chain ────────────────────────────────────────────────────────
        if (overlays.isEmpty()) {
            result += if (videoFilter != null) "[0:v]$videoFilter[vout]" else "[0:v]null[vout]"
        } else {
            // Step 1: apply main video filters (always produce a named label)
            val firstVideoLabel = "vmain"
            result += if (videoFilter != null) "[0:v]$videoFilter[$firstVideoLabel]"
                      else                     "[0:v]null[$firstVideoLabel]"

            // Step 2: chain each image overlay
            val effectiveW = if (config.videoWidth  > 0) config.videoWidth  else 1920
            val effectiveH = if (config.videoHeight > 0) config.videoHeight else 1080

            var prevLabel = firstVideoLabel
            overlays.forEachIndexed { idx, overlay ->
                val inputStreamIdx = idx + 1  // overlays occupy streams 1…N
                val wPx = ((effectiveW * overlay.scaleW).toInt()).coerceAtLeast(4).let { if (it % 2 == 1) it + 1 else it }
                val xPx = (effectiveW * overlay.x).toInt().coerceAtLeast(0)
                val yPx = (effectiveH * overlay.y).toInt().coerceAtLeast(0)
                val ovLabel   = "ov$idx"
                val nextLabel = if (idx == overlays.lastIndex) "vout" else "vtmp$idx"

                // Scale overlay + optional opacity
                val scaleChain = buildString {
                    append("scale=$wPx:-2")
                    if (overlay.opacity < 0.999f) {
                        val a = String.format(java.util.Locale.US, "%.3f", overlay.opacity)
                        append(",format=rgba,colorchannelmixer=aa=$a")
                    }
                }
                result += "[$inputStreamIdx:v]$scaleChain[$ovLabel]"
                result += "[$prevLabel][$ovLabel]overlay=$xPx:$yPx[$nextLabel]"
                prevLabel = nextLabel
            }
        }

        // ── Audio chain ────────────────────────────────────────────────────────
        if (spec.hasExternalTrack) {
            if (config.inputHasAudio && !spec.removeAudio) {
                // Original video has audio → mix with external track at full volume
                val baseChain = if (spec.baseFilters.isNotEmpty())
                    spec.baseFilters.joinToString(",")
                else "anull"
                result += "[0:a]$baseChain[a0]"
                result += "[$audioInputIndex:a]${spec.extFilters.joinToString(",")}[a1]"
                result += "[a0][a1]amix=inputs=2:duration=first:dropout_transition=2:normalize=0[aout]"
            } else {
                // No original audio (or removeAudio) → use only the external track
                result += "[$audioInputIndex:a]${spec.extFilters.joinToString(",")}[aout]"
            }
        }

        return result
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Format / video codec helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun appendFormatArgs(args: MutableList<String>, config: ConversionConfig) {
        // When filter_complex was used, the -vf would conflict with the explicit -map [vout]
        val skipVf = needsFilterComplex(config)
        when (config.outputFormat) {
            OutputFormat.GIF    -> buildGifArgs(args, config, skipVf)
            OutputFormat.WEBP   -> buildWebpArgs(args, config, skipVf)
            OutputFormat.MP3    -> buildAudioCodecArgs(args, config, "libmp3lame", "mp3")
            OutputFormat.FLAC   -> buildFlacArgs(args)
            OutputFormat.OGG    -> buildAudioCodecArgs(args, config, "libvorbis", "ogg")
            OutputFormat.MP4    -> buildVideoCodecArgs(args, config, "libx264", "aac", skipVf)
            OutputFormat.MKV    -> buildVideoCodecArgs(args, config, "libx264", "aac", skipVf)
            OutputFormat.AVI    -> buildVideoCodecArgs(args, config, "libxvid", "libmp3lame", skipVf)
            OutputFormat.CUSTOM -> {}
        }
    }

    private fun appendFormatPreview(sb: StringBuilder, config: ConversionConfig) {
        val skipVf = needsFilterComplex(config)
        val vf = buildVideoFilter(config)
        when (config.outputFormat) {
            OutputFormat.GIF -> {
                if (!skipVf) {
                    val gifFilter = if (vf != null) "$vf,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                    else "split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                    sb.append(" -vf \"$gifFilter\"")
                }
                sb.append(" -loop 0")
            }
            OutputFormat.WEBP -> {
                if (!skipVf && vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -quality ${(config.qualityLevel * 100).toInt()} -loop 0")
            }
            OutputFormat.MP3  -> sb.append(" -vn -acodec libmp3lame -b:a ${qualityToBitrate(config.qualityLevel)}k")
            OutputFormat.FLAC -> sb.append(" -vn -acodec flac")
            OutputFormat.OGG  -> sb.append(" -vn -acodec libvorbis -q:a ${(config.qualityLevel * 10).toInt()}")
            OutputFormat.MP4, OutputFormat.MKV -> {
                if (!skipVf && vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -vcodec libx264 -crf ${qualityToCrf(config.qualityLevel)} -preset medium -acodec aac -b:a 128k")
            }
            OutputFormat.AVI -> {
                if (!skipVf && vf != null) sb.append(" -vf \"$vf\"")
                sb.append(" -vcodec libxvid -qscale:v ${(10 - config.qualityLevel * 9).toInt().coerceIn(1, 10)} -acodec libmp3lame")
            }
            OutputFormat.CUSTOM -> {}
        }
    }

    private fun buildGifArgs(args: MutableList<String>, config: ConversionConfig, skipVf: Boolean = false) {
        if (!skipVf) {
            val base      = buildVideoFilter(config) ?: ""
            val withScale = if (!base.contains("scale=")) base + (if (base.isEmpty()) "" else ",") + "scale=480:-1" else base
            val withFps   = if (!withScale.contains("fps=")) "$withScale,fps=15" else withScale
            args += "-vf"; args += "$withFps,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
        }
        args += "-loop"; args += "0"
    }

    private fun buildWebpArgs(args: MutableList<String>, config: ConversionConfig, skipVf: Boolean = false) {
        if (!skipVf) buildVideoFilter(config)?.let { args += "-vf"; args += it }
        args += "-quality"; args += (config.qualityLevel * 100).toInt().toString()
        args += "-loop";    args += "0"
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
                args += "-crf";    args += qualityToCrf(config.qualityLevel).toString()
                args += "-preset"; args += "medium"
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
    // Used both for standalone -vf and as a segment inside filter_complex.
    // ──────────────────────────────────────────────────────────────────────────

    internal fun buildVideoFilter(config: ConversionConfig): String? {
        val filters = mutableListOf<String>()

        if (config.isReversed)     filters += "reverse"
        if (config.flipHorizontal) filters += "hflip"
        if (config.flipVertical)   filters += "vflip"

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
        if (config.framerate  != Framerate.ORIGINAL)   filters += "fps=${config.framerate.value}"

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

        // ── Subtitle burn-in ────────────────────────────────────────────────
        if (config.subtitleCachePath.isNotBlank()) {
            val escapedPath = config.subtitleCachePath
                .replace("\\", "\\\\")
                .replace("'", "\\'")
            filters += "subtitles='$escapedPath'"
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
        val text     = overlay.text.replace("'", "\\'").replace(":", "\\:")
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
            val s  = sParts[0].toLongOrNull() ?: 0L
            val msStr = if (sParts.size > 1) sParts[1].padEnd(3, '0').substring(0, 3) else "000"
            val ms = msStr.toLongOrNull() ?: 0L
            h * 3_600_000L + m * 60_000L + s * 1_000L + ms
        } else 0L
    } catch (_: Exception) { 0L }
}
