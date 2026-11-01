package com.cuscus.ffmpeggui.core.model

/**
 * An effect that changes the picture itself while it lasts (black and white,
 * a glitch, TV static...), rather than light laid over it: on the effects lane
 * like the others, it works on everything under it, the whole frame. Each is
 * drawn by FFmpeg's own filters in the export and by a shader in the preview
 * (core.look). Whatever moves in it moves in a loop of [periodSeconds] from
 * where it starts, so that it can last as long as wanted.
 */
enum class PictureLook(
    /** Its name, as projects keep it (an effect's name, see ImageOverlay.effect). */
    val id: String,
    /** How long its loop lasts, seconds. */
    val periodSeconds: Double,
) {
    BLACK_WHITE("bw", 2.0),
    OLD_FILM("old_film", 2.0),
    VHS("vhs", 4.0),
    GLITCH("glitch", 2.0),
    TV_STATIC("tv_static", 2.0),
    PRISM("prism", 2.0),
    SHAKE("shake", 1.0),
    PULSE("pulse", 0.5),
    RAINBOW("rainbow", 3.0),
    STROBE("strobe", 0.5),
    PIXELATE("pixelate", 2.0),
    INVERT("invert", 2.0),
    MIRROR("mirror", 2.0),
    GLOW("glow", 2.0),
    ;

    /** True when something in it moves (its speed can be set). */
    val moves: Boolean get() = this != BLACK_WHITE && this != INVERT && this != MIRROR && this != PRISM && this != PIXELATE && this != GLOW

    /** True when how much it does can be set. */
    val hasAmount: Boolean get() = this != INVERT && this != MIRROR

    companion object {
        /** The slowest and fastest a look's loop runs, and the least and most it does. */
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val MIN_AMOUNT = 0.25f
        const val MAX_AMOUNT = 2f

        /** The look called [id], or null when it is not one. */
        fun of(id: String?): PictureLook? = id?.let { name -> entries.firstOrNull { it.id == name } }
    }
}

/**
 * A [look] over the whole video from [startMs] to [endMs] (on the exported
 * video's clock), at [strength] (0..1, mixed with the picture as it is),
 * faded in and out, its colours turned by [hueDegrees]; its loop runs [speed]
 * times as fast as made, and what it does (its shift, shake, noise, blocks,
 * flashes...) is [amount] times as much.
 */
data class LookSpan(
    val look: PictureLook,
    val startMs: Long,
    val endMs: Long,
    val strength: Float = 1f,
    val fadeInSeconds: Float = 0f,
    val fadeOutSeconds: Float = 0f,
    val hueDegrees: Float = 0f,
    val speed: Float = 1f,
    val amount: Float = 1f,
)
