package com.cuscus.ffmpeggui.core.command

/** An FFmpeg invocation as an argument vector, without the leading program name. */
data class FfmpegCommand(val arguments: List<String>) {

    /** Shell-style rendering ("ffmpeg -y -i ..."), suitable for display and editing. */
    fun toDisplayString(): String = renderCommandLine(listOf(PROGRAM_NAME) + arguments)

    companion object {
        const val PROGRAM_NAME = "ffmpeg"
    }
}
