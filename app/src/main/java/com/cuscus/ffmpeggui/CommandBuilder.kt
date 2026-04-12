package com.cuscus.ffmpeggui

object CommandBuilder {

    fun build(config: ConversionConfig, outputPath: String): Array<String> {
        if (config.isManualMode) {
            return buildCustomCommand(config.manualCommandOverride, config, outputPath)
        }
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return buildCustomCommand(config.customCommand, config, outputPath)
        }
        val args = mutableListOf<String>()
        args += "-y"

        if (config.startTime.isNotBlank() && config.startTime != "00:00:00") {
            args += "-ss"
            args += config.startTime
        }
        if (config.endTime.isNotBlank()) {
            args += "-to"
            args += config.endTime
        }
        args += "-i"
        args += "input_placeholder"

        if (config.audioTrackUri != null) {
            args += "-i"
            args += "audio_placeholder"
        }

        val finalDurationS = (config.activeVideoDurationMs / 1000f) / config.videoSpeed

        if (config.audioTrackUri != null) {
            val complexFilters = mutableListOf<String>()
            val baseAudioFilters = mutableListOf<String>()

            if (config.removeAudio) {
                baseAudioFilters.add("volume=0")
            } else {
                if (config.isReversed) baseAudioFilters.add("areverse")
                if (config.videoSpeed != 1.0f) {
                    val atempo = buildAtempo(config.videoSpeed)
                    if (atempo != null) baseAudioFilters.add(atempo)
                }
                if (config.volumeLevel != 1.0f) baseAudioFilters.add("volume=${config.volumeLevel}")
                if (config.normalizeAudio) baseAudioFilters.add("loudnorm")

                if (config.fadeInDuration > 0f) baseAudioFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
                if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                    val fadeOutStart = finalDurationS - config.fadeOutDuration
                    if (fadeOutStart > 0) baseAudioFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
                }
            }

            if (baseAudioFilters.isNotEmpty()) {
                complexFilters.add("[0:a]${baseAudioFilters.joinToString(",")}[a0]")
            } else {
                complexFilters.add("[0:a]anull[a0]")
            }

            val extFilters = mutableListOf<String>()
            extFilters.add("asetpts=N/SR/TB")

            val startMs = timeToMs(config.audioTrackTrimStart)
            val endMs = if (config.audioTrackTrimEnd.isNotBlank()) timeToMs(config.audioTrackTrimEnd) else 0L

            if (startMs > 0 || endMs > 0) {
                var trim = "atrim=start=${startMs / 1000f}"
                if (endMs > startMs) trim += ":end=${endMs / 1000f}"
                extFilters.add(trim)
            }

            extFilters.add("asetpts=PTS-STARTPTS")

            if (config.audioTrackVolume != 1.0f) extFilters.add("volume=${config.audioTrackVolume}")

            if (config.audioTrackDelay != "00:00:00") {
                val delayMs = timeToMs(config.audioTrackDelay)
                if (delayMs > 0) extFilters.add("adelay=$delayMs|$delayMs")
            }

            if (config.fadeInDuration > 0f) extFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
            if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                val fadeOutStart = finalDurationS - config.fadeOutDuration
                if (fadeOutStart > 0) extFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
            }

            extFilters.add("apad")
            complexFilters.add("[1:a]${extFilters.joinToString(",")}[a1]")

            complexFilters.add("[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[aout]")

            args += "-filter_complex"
            args += complexFilters.joinToString(";")
            args += "-map"
            args += "0:v"
            args += "-map"
            args += "[aout]"
        } else {
            if (config.removeAudio) {
                args += "-an"
            } else {
                val audioFilters = mutableListOf<String>()
                if (config.isReversed) audioFilters.add("areverse")
                if (config.videoSpeed != 1.0f) {
                    val atempo = buildAtempo(config.videoSpeed)
                    if (atempo != null) audioFilters.add(atempo)
                }
                if (config.volumeLevel != 1.0f) audioFilters.add("volume=${config.volumeLevel}")
                if (config.normalizeAudio) audioFilters.add("loudnorm")

                if (config.fadeInDuration > 0f) audioFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
                if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                    val fadeOutStart = finalDurationS - config.fadeOutDuration
                    if (fadeOutStart > 0) audioFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
                }

                if (audioFilters.isNotEmpty()) {
                    args += "-af"
                    args += audioFilters.joinToString(",")
                }
            }
        }

        when (config.outputFormat) {
            OutputFormat.GIF -> buildGifArgs(args, config)
            OutputFormat.WEBP -> buildWebpArgs(args, config)
            OutputFormat.MP3 -> buildAudioArgs(args, config, "libmp3lame", "mp3")
            OutputFormat.FLAC -> buildFlacArgs(args, config)
            OutputFormat.OGG -> buildAudioArgs(args, config, "libvorbis", "ogg")
            OutputFormat.MP4 -> buildVideoArgs(args, config, "libx264", "aac")
            OutputFormat.MKV -> buildVideoArgs(args, config, "libx264", "aac")
            OutputFormat.AVI -> buildVideoArgs(args, config, "libxvid", "libmp3lame")
            OutputFormat.CUSTOM -> {}
        }

        args += outputPath
        return args.toTypedArray()
    }

    fun buildPreview(config: ConversionConfig): String {
        if (config.outputFormat == OutputFormat.CUSTOM) {
            return config.customCommand.ifBlank { "ffmpeg -i input.${getInputExtension(config)} ..." }
        }

        val sb = StringBuilder("ffmpeg -y")

        if (config.startTime.isNotBlank() && config.startTime != "00:00:00") {
            sb.append(" -ss ${config.startTime}")
        }
        if (config.endTime.isNotBlank()) {
            sb.append(" -to ${config.endTime}")
        }
        sb.append(" -i input.${getInputExtension(config)}")

        if (config.audioTrackUri != null) {
            sb.append(" -i audio_track.mp3")
        }

        val finalDurationS = (config.activeVideoDurationMs / 1000f) / config.videoSpeed

        if (config.audioTrackUri != null) {
            val complexFilters = mutableListOf<String>()
            val baseAudioFilters = mutableListOf<String>()

            if (config.removeAudio) {
                baseAudioFilters.add("volume=0")
            } else {
                if (config.isReversed) baseAudioFilters.add("areverse")
                if (config.videoSpeed != 1.0f) {
                    val atempo = buildAtempo(config.videoSpeed)
                    if (atempo != null) baseAudioFilters.add(atempo)
                }
                if (config.volumeLevel != 1.0f) baseAudioFilters.add("volume=${config.volumeLevel}")
                if (config.normalizeAudio) baseAudioFilters.add("loudnorm")

                if (config.fadeInDuration > 0f) baseAudioFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
                if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                    val fadeOutStart = finalDurationS - config.fadeOutDuration
                    if (fadeOutStart > 0) baseAudioFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
                }
            }

            if (baseAudioFilters.isNotEmpty()) {
                complexFilters.add("[0:a]${baseAudioFilters.joinToString(",")}[a0]")
            } else {
                complexFilters.add("[0:a]anull[a0]")
            }

            val extFilters = mutableListOf<String>()
            extFilters.add("asetpts=N/SR/TB")

            val startMs = timeToMs(config.audioTrackTrimStart)
            val endMs = if (config.audioTrackTrimEnd.isNotBlank()) timeToMs(config.audioTrackTrimEnd) else 0L

            if (startMs > 0 || endMs > 0) {
                var trim = "atrim=start=${startMs / 1000f}"
                if (endMs > startMs) trim += ":end=${endMs / 1000f}"
                extFilters.add(trim)
            }

            extFilters.add("asetpts=PTS-STARTPTS")

            if (config.audioTrackVolume != 1.0f) extFilters.add("volume=${config.audioTrackVolume}")
            if (config.audioTrackDelay != "00:00:00") {
                val delayMs = timeToMs(config.audioTrackDelay)
                if (delayMs > 0) extFilters.add("adelay=$delayMs|$delayMs")
            }

            if (config.fadeInDuration > 0f) extFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
            if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                val fadeOutStart = finalDurationS - config.fadeOutDuration
                if (fadeOutStart > 0) extFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
            }

            extFilters.add("apad")
            complexFilters.add("[1:a]${extFilters.joinToString(",")}[a1]")

            complexFilters.add("[a0][a1]amix=inputs=2:duration=first:dropout_transition=2[aout]")

            sb.append(" -filter_complex \"${complexFilters.joinToString(";")}\" -map 0:v -map [aout]")
        } else {
            if (config.removeAudio) {
                sb.append(" -an")
            } else {
                val audioFilters = mutableListOf<String>()
                if (config.isReversed) audioFilters.add("areverse")
                if (config.videoSpeed != 1.0f) {
                    val atempo = buildAtempo(config.videoSpeed)
                    if (atempo != null) audioFilters.add(atempo)
                }
                if (config.volumeLevel != 1.0f) audioFilters.add("volume=${config.volumeLevel}")
                if (config.normalizeAudio) audioFilters.add("loudnorm")

                if (config.fadeInDuration > 0f) audioFilters.add(String.format(java.util.Locale.US, "afade=t=in:st=0:d=%.1f", config.fadeInDuration))
                if (config.fadeOutDuration > 0f && finalDurationS > 0) {
                    val fadeOutStart = finalDurationS - config.fadeOutDuration
                    if (fadeOutStart > 0) audioFilters.add(String.format(java.util.Locale.US, "afade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
                }

                if (audioFilters.isNotEmpty()) {
                    sb.append(" -af \"${audioFilters.joinToString(",")}\"")
                }
            }
        }

        val filterString = buildVideoFilter(config)

        when (config.outputFormat) {
            OutputFormat.GIF -> {
                val gifFilter = if (filterString != null) "$filterString,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse" else "split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                sb.append(" -vf \"$gifFilter\" -loop 0")
            }
            OutputFormat.WEBP -> {
                val quality = (config.qualityLevel * 100).toInt()
                if (filterString != null) sb.append(" -vf \"$filterString\"")
                sb.append(" -quality $quality -loop 0")
            }
            OutputFormat.MP3 -> {
                val bitrate = qualityToBitrate(config.qualityLevel)
                sb.append(" -vn -acodec libmp3lame -b:a ${bitrate}k")
            }
            OutputFormat.FLAC -> {
                sb.append(" -vn -acodec flac")
            }
            OutputFormat.OGG -> {
                val quality = (config.qualityLevel * 10).toInt()
                sb.append(" -vn -acodec libvorbis -q:a $quality")
            }
            OutputFormat.MP4, OutputFormat.MKV -> {
                val crf = qualityToCrf(config.qualityLevel)
                if (filterString != null) sb.append(" -vf \"$filterString\"")
                sb.append(" -vcodec libx264 -crf $crf -preset medium -acodec aac -b:a 128k")
            }
            OutputFormat.AVI -> {
                val qscale = (10 - config.qualityLevel * 9).toInt().coerceIn(1, 10)
                if (filterString != null) sb.append(" -vf \"$filterString\"")
                sb.append(" -vcodec libxvid -qscale:v $qscale -acodec libmp3lame")
            }
            OutputFormat.CUSTOM -> {}
        }

        sb.append(" output.${config.outputFormat.extension}")
        return sb.toString()
    }

    private fun buildAtempo(speed: Float): String? {
        if (speed == 1.0f) return null
        var s = speed
        val filters = mutableListOf<String>()
        while (s < 0.5f) {
            filters.add("atempo=0.5")
            s *= 2.0f
        }
        while (s > 100.0f) {
            filters.add("atempo=100.0")
            s /= 100.0f
        }
        if (s != 1.0f) {
            filters.add(String.format(java.util.Locale.US, "atempo=%.4f", s))
        }
        return if (filters.isNotEmpty()) filters.joinToString(",") else null
    }

    private fun buildGifArgs(args: MutableList<String>, config: ConversionConfig) {
        var filter = buildVideoFilter(config)
        if (filter == null) {
            filter = "scale=480:-1,fps=15"
        } else {
            if (!filter.contains("scale=")) filter += ",scale=480:-1"
            if (!filter.contains("fps=")) filter += ",fps=15"
        }
        args += "-vf"
        args += "$filter,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
        args += "-loop"
        args += "0"
    }

    private fun buildWebpArgs(args: MutableList<String>, config: ConversionConfig) {
        val quality = (config.qualityLevel * 100).toInt()
        val filters = buildVideoFilter(config)
        if (filters != null) {
            args += "-vf"
            args += filters
        }
        args += "-quality"
        args += quality.toString()
        args += "-loop"
        args += "0"
    }

    private fun buildAudioArgs(args: MutableList<String>, config: ConversionConfig, codec: String, format: String) {
        args += "-vn"
        args += "-acodec"
        args += codec
        if (codec == "libmp3lame") {
            args += "-b:a"
            args += "${qualityToBitrate(config.qualityLevel)}k"
        } else if (codec == "libvorbis") {
            args += "-q:a"
            args += (config.qualityLevel * 10).toInt().toString()
        }
        args += "-f"
        args += format
    }

    private fun buildFlacArgs(args: MutableList<String>, config: ConversionConfig) {
        args += "-vn"
        args += "-acodec"
        args += "flac"
    }

    private fun buildVideoArgs(args: MutableList<String>, config: ConversionConfig, vcodec: String, acodec: String) {
        val filters = buildVideoFilter(config)
        if (filters != null) {
            args += "-vf"
            args += filters
        }
        args += "-vcodec"
        args += vcodec
        if (vcodec == "libx264") {
            args += "-crf"
            args += qualityToCrf(config.qualityLevel).toString()
            args += "-preset"
            args += "medium"
        } else if (vcodec == "libxvid") {
            args += "-qscale:v"
            args += (10 - config.qualityLevel * 9).toInt().coerceIn(1, 10).toString()
        }
        args += "-acodec"
        args += acodec
        if (acodec == "aac") {
            args += "-b:a"
            args += "128k"
        }
    }

    private fun buildCustomCommand(cmdString: String, config: ConversionConfig, outputPath: String): Array<String> {
        val replacedCmd = cmdString
            .replace("input.${getInputExtension(config)}", "input_placeholder")
            .replace("output.${config.outputFormat.extension}", outputPath)
        return com.arthenica.ffmpegkit.FFmpegKitConfig.parseArguments(replacedCmd)
    }

    private fun buildVideoFilter(config: ConversionConfig): String? {
        val filters = mutableListOf<String>()

        if (config.isReversed) filters.add("reverse")

        if (config.flipHorizontal) filters.add("hflip")
        if (config.flipVertical) filters.add("vflip")

        when (config.rotation) {
            Rotation.DEG_90 -> filters.add("transpose=1")
            Rotation.DEG_180 -> { filters.add("transpose=1"); filters.add("transpose=1") }
            Rotation.DEG_270 -> filters.add("transpose=2")
            Rotation.NONE -> {}
        }

        if (config.cropW.isNotBlank() && config.cropH.isNotBlank() && config.cropX.isNotBlank() && config.cropY.isNotBlank()) {
            if (!(config.cropW == "1.00*in_w" && config.cropH == "1.00*in_h" && config.cropX == "0.00*in_w" && config.cropY == "0.00*in_h")) {
                filters.add("crop=trunc((${config.cropW})/2)*2:trunc((${config.cropH})/2)*2:${config.cropX}:${config.cropY}")
            }
        }

        val padFilter = when (config.projectRatio) {
            "1:1" -> "pad=width='ceil(max(iw,ih)/2)*2':height='ceil(max(ih,iw)/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
            "16:9" -> "pad=width='ceil(max(iw,ih*(16/9))/2)*2':height='ceil(max(ih,iw/(16/9))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
            "4:3" -> "pad=width='ceil(max(iw,ih*(4/3))/2)*2':height='ceil(max(ih,iw/(4/3))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
            "9:16" -> "pad=width='ceil(max(iw,ih*(9/16))/2)*2':height='ceil(max(ih,iw/(9/16))/2)*2':x='(ow-iw)/2':y='(oh-ih)/2':color=black"
            else -> null
        }
        if (padFilter != null) filters.add(padFilter)

        if (config.resolution != Resolution.ORIGINAL) {
            filters.add("scale=-2:${config.resolution.height}")
        }

        if (config.framerate != Framerate.ORIGINAL) {
            filters.add("fps=${config.framerate.value}")
        }

        if (config.brightness != 0f || config.contrast != 1f || config.saturation != 1f) {
            filters.add(String.format(java.util.Locale.US, "eq=brightness=%.2f:contrast=%.2f:saturation=%.2f", config.brightness, config.contrast, config.saturation))
        }

        if (config.textOverlays.isNotEmpty()) {
            val drawTexts = config.textOverlays.filter { it.text.isNotBlank() }.map { overlay ->
                val text = overlay.text.replace("'", "\\'").replace(":", "\\:")
                val xExpr = "(w-tw)*${overlay.x}"
                val yExpr = "(h-th)*${overlay.y}"
                val sizeExpr = "h*${overlay.size}"
                val hexColor = String.format("%06X", (overlay.color and 0xFFFFFF))
                val fontPath = getDeviceFontPath(overlay.isBold)

                String.format(
                    java.util.Locale.US,
                    "drawtext=fontfile='%s':text='%s':fontcolor=0x%s:fontsize=%s:x=%s:y=%s",
                    fontPath, text, hexColor, sizeExpr, xExpr, yExpr
                )
            }
            if (drawTexts.isNotEmpty()) {
                filters.addAll(drawTexts)
            }
        }

        if (config.fadeInDuration > 0f) {
            filters.add(String.format(java.util.Locale.US, "fade=t=in:st=0:d=%.1f", config.fadeInDuration))
        }

        val finalDurationS = (config.activeVideoDurationMs / 1000f) / config.videoSpeed
        if (config.fadeOutDuration > 0f && finalDurationS > 0) {
            val fadeOutStart = finalDurationS - config.fadeOutDuration
            if (fadeOutStart > 0) {
                filters.add(String.format(java.util.Locale.US, "fade=t=out:st=%.3f:d=%.1f", fadeOutStart, config.fadeOutDuration))
            }
        }

        if (config.videoSpeed != 1.0f) {
            val pts = 1.0f / config.videoSpeed
            filters.add(String.format(java.util.Locale.US, "setpts=%.4f*PTS", pts))
        }

        return if (filters.isNotEmpty()) filters.joinToString(",") else null
    }

    private fun qualityToCrf(quality: Float): Int {
        return (51 - quality * 46).toInt().coerceIn(0, 51)
    }

    private fun qualityToBitrate(quality: Float): Int {
        return (64 + quality * 256).toInt()
    }

    private fun getInputExtension(config: ConversionConfig): String {
        return config.inputFileName.substringAfterLast(".", "mp4")
    }

    private fun timeToMs(timeStr: String): Long {
        try {
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val sParts = parts[2].split(".")
                val s = sParts[0].toLongOrNull() ?: 0L
                val msStr = if (sParts.size > 1) sParts[1].padEnd(3, '0').substring(0, 3) else "000"
                val ms = msStr.toLongOrNull() ?: 0L
                return h * 3600000L + m * 60000L + s * 1000L + ms
            }
        } catch (e: Exception) {}
        return 0L
    }

    private fun getDeviceFontPath(isBold: Boolean): String {
        val boldFonts = listOf(
            "/system/fonts/Roboto-Bold.ttf",
            "/system/fonts/NotoSans-Bold.ttf",
            "/system/fonts/DroidSans-Bold.ttf"
        )
        val regularFonts = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf"
        )

        val pathsToTry = if (isBold) boldFonts + regularFonts else regularFonts + boldFonts

        for (path in pathsToTry) {
            val file = java.io.File(path)
            if (file.exists() && file.canRead()) {
                return path
            }
        }

        return "/system/fonts/Roboto-Regular.ttf"
    }
}