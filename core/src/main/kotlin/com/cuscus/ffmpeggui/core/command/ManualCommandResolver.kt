package com.cuscus.ffmpeggui.core.command

/**
 * Turns a hand-written command line into something runnable.
 *
 * The user edits the command as shown in the preview, where files appear under
 * aliases ("input.mp4", "overlay_0.png", "output.mkv", ...). The resolver splits
 * the line into arguments, drops a leading "ffmpeg" and swaps every alias for
 * the real path.
 */
class ManualCommandResolver {

    sealed interface Result {
        /**
         * @param outputPath  the file (or numbered pattern) the command writes to.
         * @param isPattern   true when [outputPath] contains a "%03d"-style counter.
         */
        data class Resolved(
            val command: FfmpegCommand,
            val outputPath: String,
            val isPattern: Boolean,
        ) : Result

        data object Empty : Result

        /** No "output.<ext>" argument was found, so nothing would be written where expected. */
        data object MissingOutput : Result
    }

    /**
     * @param inputAliases  alias -> real path, see [MediaPaths.inputAliasesTo].
     * @param outputStem    real output path without extension; the extension (and
     *                      any counter) written by the user is kept.
     */
    fun resolve(commandLine: String, inputAliases: Map<String, String>, outputStem: String): Result {
        var tokens = tokenizeCommandLine(commandLine)
        if (tokens.firstOrNull() == FfmpegCommand.PROGRAM_NAME) tokens = tokens.drop(1)
        if (tokens.isEmpty()) return Result.Empty

        // Longest aliases first so that one alias never clobbers part of another.
        val aliases = inputAliases.entries.sortedByDescending { it.key.length }
        val matchers = aliases.map { (alias, target) -> aliasPattern(alias) to target }

        var outputPath: String? = null
        val resolved = tokens.map { token ->
            val output = OUTPUT_TOKEN.matchEntire(token)
            if (output != null) {
                val counter = output.groupValues[1]
                val extension = output.groupValues[2]
                "$outputStem$counter$extension".also { outputPath = it }
            } else {
                matchers.fold(token) { acc, (pattern, target) ->
                    pattern.replace(acc) { target }
                }
            }
        }

        val finalOutput = outputPath ?: return Result.MissingOutput
        return Result.Resolved(
            command = FfmpegCommand(resolved),
            outputPath = finalOutput,
            isPattern = '%' in finalOutput.substringAfterLast('/'),
        )
    }

    private companion object {
        /** "output", "output.mkv", "output_%03d.png", ... */
        val OUTPUT_TOKEN = Regex("""output(_%\d*d)?(\.[A-Za-z0-9]+)?""")

        /** Matches [alias] only when it is not glued to a longer file name. */
        fun aliasPattern(alias: String): Regex =
            Regex("""(?<![A-Za-z0-9_./\\-])${Regex.escape(alias)}(?![A-Za-z0-9_])""")
    }
}
