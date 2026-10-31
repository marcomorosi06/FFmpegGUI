package com.cuscus.ffmpeggui.core.command.studio

import com.cuscus.ffmpeggui.core.command.AliasKind
import com.cuscus.ffmpeggui.core.command.CommandAlias

/** What a recipe is for, to group them. */
enum class RecipeGroup { CUT, SHRINK, LOOK, SOUND, MAKE }

/**
 * A ready command to start from. [template] names files as {in} (the first
 * clip), {in2} (a second file: another clip, a picture, a sound or a file
 * added), {ext} (the first clip's extension) and {subs} (the subtitles).
 */
data class CommandRecipe(
    val id: String,
    val group: RecipeGroup,
    val title: String,
    val summary: String,
    val template: String,
) {
    val needsSecond: Boolean get() = "{in2}" in template
    val needsSubtitles: Boolean get() = "{subs}" in template

    /** The command for these files, or null when one it needs is missing. */
    fun fill(files: List<CommandAlias>): String? {
        val first = files.firstOrNull { it.kind == AliasKind.INPUT }?.alias ?: return null
        val second = files.filter { it.kind in SECOND && it.alias != first }.map { it.alias }.firstOrNull()
        val subs = files.firstOrNull { it.kind == AliasKind.SUBTITLES }?.alias
        if (needsSecond && second == null || needsSubtitles && subs == null) return null
        return "ffmpeg " + template
            .replace("{in2}", second.orEmpty())
            .replace("{in}", first)
            .replace("{ext}", first.substringAfterLast('.', "mp4"))
            .replace("{subs}", subs.orEmpty())
    }

    /** The encoders the recipe uses that this FFmpeg lacks (none before FFmpeg has been read). */
    fun missingEncoders(catalog: FfmpegCatalog): List<String> {
        if (catalog.encoders.isEmpty()) return emptyList()
        val reading = CommandReading("ffmpeg $template", catalog)
        return reading.spans.indices.mapNotNull { i ->
            val role = reading.roles[i] as? ArgRole.Value ?: return@mapNotNull null
            reading.spans[i].value.takeIf { CommandReading.valueKind(reading.spans[role.option].value) == ValueKind.ENCODER }
        }.filter { it != "copy" && catalog.encoder(it) == null }.distinct()
    }

    companion object {
        private val SECOND = listOf(AliasKind.INPUT, AliasKind.OVERLAY, AliasKind.SOUND, AliasKind.EXTRA)

        val ALL: List<CommandRecipe> = listOf(
            CommandRecipe("trim_copy", RecipeGroup.CUT, "Cut without re-encoding", "Instant, cuts on the nearest keyframe",
                "-ss 00:00:10 -to 00:00:20 -i {in} -c copy output.{ext}"),
            CommandRecipe("trim_exact", RecipeGroup.CUT, "Cut to the frame", "Re-encodes, exact to the frame",
                "-i {in} -ss 00:00:10 -to 00:00:20 -c:v libx264 -crf 20 -c:a aac output.mp4"),
            CommandRecipe("frame", RecipeGroup.CUT, "One frame as a picture", "The frame at 5 seconds",
                "-ss 00:00:05 -i {in} -frames:v 1 output.png"),
            CommandRecipe("frames", RecipeGroup.CUT, "A picture every second", "Numbered PNGs, packed in a ZIP",
                "-i {in} -vf fps=1 output_%03d.png"),
            CommandRecipe("h264", RecipeGroup.SHRINK, "Smaller H.264 MP4", "Plays everywhere",
                "-i {in} -c:v libx264 -crf 23 -preset medium -c:a aac -b:a 128k -movflags +faststart output.mp4"),
            CommandRecipe("h265", RecipeGroup.SHRINK, "Even smaller HEVC", "About half the size of H.264",
                "-i {in} -c:v libx265 -crf 28 -preset medium -tag:v hvc1 -c:a aac -b:a 128k output.mp4"),
            CommandRecipe("vp9", RecipeGroup.SHRINK, "WebM for the web", "VP9 and Opus",
                "-i {in} -c:v libvpx-vp9 -crf 32 -b:v 0 -c:a libopus -b:a 96k output.webm"),
            CommandRecipe("720p", RecipeGroup.SHRINK, "Down to 720p", "Keeps the shape, copies the sound",
                "-i {in} -vf scale=-2:720 -c:v libx264 -crf 23 -c:a copy output.mp4"),
            CommandRecipe("gif", RecipeGroup.MAKE, "Sharp GIF", "Its own palette, 12 frames a second",
                "-i {in} -filter_complex \"fps=12,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse\" output.gif"),
            CommandRecipe("square", RecipeGroup.LOOK, "Square crop", "The middle of the picture",
                "-i {in} -vf \"crop='min(iw,ih)':'min(iw,ih)'\" -c:a copy output.mp4"),
            CommandRecipe("rotate", RecipeGroup.LOOK, "Turn a quarter", "Clockwise",
                "-i {in} -vf transpose=1 -c:a copy output.mp4"),
            CommandRecipe("fade", RecipeGroup.LOOK, "Fade in", "Picture and sound, one second",
                "-i {in} -vf fade=t=in:st=0:d=1 -af afade=t=in:st=0:d=1 output.mp4"),
            CommandRecipe("speed", RecipeGroup.LOOK, "Twice as fast", "Picture and sound together",
                "-i {in} -filter_complex \"[0:v]setpts=0.5*PTS[v];[0:a]atempo=2.0[a]\" -map \"[v]\" -map \"[a]\" output.mp4"),
            CommandRecipe("reverse", RecipeGroup.LOOK, "Backwards", "Best on short clips",
                "-i {in} -vf reverse -af areverse output.mp4"),
            CommandRecipe("subs", RecipeGroup.LOOK, "Burn in the subtitles", "Drawn on the picture",
                "-i {in} -vf \"subtitles={subs}\" -c:a copy output.mp4"),
            CommandRecipe("pip", RecipeGroup.MAKE, "Picture in picture", "The second file small, in a corner",
                "-i {in} -i {in2} -filter_complex \"[1:v]scale=iw/4:-2[pip];[0:v][pip]overlay=W-w-24:H-h-24\" -c:a copy output.mp4"),
            CommandRecipe("mp3", RecipeGroup.SOUND, "Sound as MP3", "Very good quality",
                "-i {in} -vn -c:a libmp3lame -q:a 2 output.mp3"),
            CommandRecipe("mute", RecipeGroup.SOUND, "Without sound", "The picture copied as it is",
                "-i {in} -c:v copy -an output.{ext}"),
            CommandRecipe("loud", RecipeGroup.SOUND, "Even loudness", "EBU R128, for the web",
                "-i {in} -af loudnorm=I=-16:TP=-1.5:LRA=11 -c:v copy output.{ext}"),
            CommandRecipe("mix", RecipeGroup.SOUND, "Mix in a second sound", "Both together, as long as the first",
                "-i {in} -i {in2} -filter_complex \"[0:a][1:a]amix=inputs=2:duration=first[a]\" -map 0:v? -map \"[a]\" -c:v copy output.mp4"),
            CommandRecipe("wave", RecipeGroup.MAKE, "Picture of the waveform", "The whole sound in one PNG",
                "-i {in} -filter_complex showwavespic=s=1280x320 -frames:v 1 output.png"),
        )
    }
}
