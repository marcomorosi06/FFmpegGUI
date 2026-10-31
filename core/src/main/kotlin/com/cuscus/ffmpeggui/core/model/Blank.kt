package com.cuscus.ffmpeggui.core.model

import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import com.cuscus.ffmpeggui.core.project.jsonObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * A picture started from nothing ("Create from scratch" on the home screen):
 * a size and what fills it, drawn into a PNG that is then opened like any
 * picture, and the user's own starting points (presets), kept in a file of
 * their own and shown on the home screen under the recent projects.
 */

/** What a blank is filled with. */
enum class BlankFillType { TRANSPARENT, SOLID, LINEAR, RADIAL }

/**
 * How a blank is filled: nothing, one colour ([from], ARGB, its opacity
 * included), or a gradient from [from] to [to]: a linear one across the
 * picture at [angleDegrees] (0: left to right, 90: top to bottom), or a radial
 * one from the middle out to the corners.
 */
data class BlankFill(
    val type: BlankFillType = BlankFillType.SOLID,
    val from: Long = 0xFFFFFFFFL,
    val to: Long = 0xFF6750A4L,
    val angleDegrees: Float = 90f,
) {
    /** True when some of it is see-through (only PNG, WEBP and TIFF keep that). */
    val seeThrough: Boolean
        get() = when (type) {
            BlankFillType.TRANSPARENT -> true
            BlankFillType.SOLID -> alpha(from) < 255
            else -> alpha(from) < 255 || alpha(to) < 255
        }

    /**
     * The colours of row [y] of a [width] x [height] blank, into [out]
     * (ARGB, not premultiplied): the gradient's position worked out at each
     * pixel's centre, the two colours mixed channel by channel.
     */
    fun row(y: Int, width: Int, height: Int, out: IntArray) {
        when (type) {
            BlankFillType.TRANSPARENT -> out.fill(0, 0, width)
            BlankFillType.SOLID -> out.fill(from.toInt(), 0, width)
            BlankFillType.LINEAR -> {
                val a = angleDegrees * PI / 180
                val dx = cos(a)
                val dy = sin(a)
                // The gradient spans the picture's extent along its direction.
                val half = (abs(dx) * width + abs(dy) * height) / 2
                val cy = y + 0.5 - height / 2.0
                for (x in 0 until width) {
                    val cx = x + 0.5 - width / 2.0
                    val t = if (half <= 0) 0.0 else ((cx * dx + cy * dy) / half + 1) / 2
                    out[x] = mix(t)
                }
            }
            BlankFillType.RADIAL -> {
                val reach = hypot(width / 2.0, height / 2.0)
                val cy = y + 0.5 - height / 2.0
                for (x in 0 until width) {
                    val cx = x + 0.5 - width / 2.0
                    out[x] = mix(if (reach <= 0) 0.0 else hypot(cx, cy) / reach)
                }
            }
        }
    }

    /** The colour [t] (0..1) of the way from [from] to [to]. */
    fun mix(t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val a = ((from ushr shift) and 0xFF).toDouble()
            val b = ((to ushr shift) and 0xFF).toDouble()
            return (a + (b - a) * k).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun alpha(argb: Long): Int = ((argb ushr 24) and 0xFF).toInt()
}

/** A blank picture: [width] x [height] pixels filled with [fill], called [name]. */
data class BlankSpec(
    val name: String = "",
    val width: Int = 1080,
    val height: Int = 1350,
    val fill: BlankFill = BlankFill(),
) {
    /** As it can be made: even sides (video encoders want them) from 16 to [MAX_SIDE]. */
    fun sane(): BlankSpec {
        fun side(v: Int) = (v.coerceIn(MIN_SIDE, MAX_SIDE) / 2) * 2
        return copy(width = side(width), height = side(height))
    }

    /** Width / height. */
    val ratio: Float get() = width.toFloat() / max(1, height)

    companion object {
        const val MIN_SIDE = 16

        /** A 4K frame: what the app keeps of a picture opened (see the still maker). */
        const val MAX_SIDE = 3840

        /** A blank of shape [ratio] (width / height) whose longer side is [side]. */
        fun of(ratio: Float, side: Int, fill: BlankFill, name: String = ""): BlankSpec {
            val w = if (ratio >= 1f) side else (side * ratio).roundToInt()
            val h = if (ratio >= 1f) (side / ratio).roundToInt() else side
            return BlankSpec(name, w, h, fill).sane()
        }
    }
}

/** The user's blanks, kept as JSON (a file of their own, not the settings nor a project). */
object BlankPresetsJson {

    fun write(presets: List<BlankSpec>): String = JsonText.write(
        jsonObject {
            put("version", 1)
            putList("presets", presets) { p ->
                jsonObject {
                    put("name", p.name)
                    put("width", p.width)
                    put("height", p.height)
                    put("fill", p.fill.type)
                    put("from", p.fill.from)
                    put("to", p.fill.to)
                    put("angle", p.fill.angleDegrees)
                }
            }
        },
    )

    /** The presets in [text]; none when it cannot be read. */
    fun read(text: String): List<BlankSpec> {
        val root = runCatching { JsonText.parse(text) as? Json.Obj }.getOrNull() ?: return emptyList()
        return root.list("presets", emptyList()) { item ->
            val o = item as? Json.Obj ?: return@list null
            val d = BlankSpec()
            BlankSpec(
                name = o.string("name", d.name),
                width = o.int("width", d.width),
                height = o.int("height", d.height),
                fill = BlankFill(
                    type = o.enum("fill", d.fill.type),
                    from = o.long("from", d.fill.from),
                    to = o.long("to", d.fill.to),
                    angleDegrees = o.float("angle", d.fill.angleDegrees),
                ),
            ).sane()
        }
    }
}
