package com.cuscus.ffmpeggui.core.preview

import com.cuscus.ffmpeggui.core.command.FfmpegCommand

/** Command that measures the loudness of [inputPath] without writing any file. */
fun loudnessAnalysisCommand(inputPath: String): FfmpegCommand = FfmpegCommand(
    listOf("-nostats", "-i", inputPath, "-vn", "-sn", "-dn", "-af", "ebur128=framelog=quiet", "-f", "null", "-"),
)

/**
 * Reads the integrated loudness (LUFS) from the summary the ebur128 filter
 * prints at the end of [log]. Null when the input has no audio.
 */
fun parseIntegratedLoudness(log: String): Double? =
    INTEGRATED_LOUDNESS.findAll(log).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()

private val INTEGRATED_LOUDNESS = Regex("""Integrated loudness:\s*I:\s*(-?\d+(?:\.\d+)?)\s*LUFS""")
