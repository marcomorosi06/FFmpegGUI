package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.core.model.SpillScreen
import java.util.Locale

/*
 * The filters of a ColorKey. The preview does the same with
 * core.preview.ColorKeying (its notes say how each filter is imitated).
 */

/**
 * The colour cut out, the picture left with transparency: `chromakey` on
 * yuva444p (no chroma subsampling, so edges stay as sharp as the picture) or
 * `colorkey` on rgba, then `despill` when the key removes the screen's tint.
 * A subject key ([KeyMode.SUBJECT]) merges its mask in instead (see
 * [subjectFilters]); [label] makes the links of that little graph unique and
 * [maskGeometry] are the filters that turned, mirrored or cut the picture
 * before the key, done to the mask too.
 */
internal fun keyFilters(key: ColorKey, label: String = "ks", maskGeometry: List<String> = emptyList()): String {
    if (key.mode == KeyMode.SUBJECT) return subjectFilters(key.mask, label, maskGeometry)
    val color = String.format(Locale.US, "0x%06X", key.color and 0xFFFFFF)
    val filters = mutableListOf<String>()
    filters += when (key.mode) {
        KeyMode.CHROMA -> "format=yuva444p," + fmt("chromakey=color=%s:similarity=%.4f:blend=%.4f", color, key.effectiveSimilarity, key.effectiveBlend)
        KeyMode.COLOR -> "format=rgba," + fmt("colorkey=color=%s:similarity=%.4f:blend=%.4f", color, key.effectiveSimilarity, key.effectiveBlend)
        KeyMode.SUBJECT -> error("Handled above")
    }
    key.spillScreen?.let { screen ->
        val amount = fmt("%.4f", -key.spill.coerceIn(0f, 1f))
        filters += when (screen) {
            SpillScreen.GREEN -> "despill=type=green:mix=0.5:expand=0:red=0:green=$amount:blue=0"
            SpillScreen.BLUE -> "despill=type=blue:mix=0.5:expand=0:red=0:green=0:blue=$amount"
        }
    }
    return filters.joinToString(",")
}

/**
 * The subject kept and the rest made transparent, the picture left rgba: the
 * mask is read by `movie` (its alpha taken by `alphaextract`), turned, mirrored
 * and cut by [maskGeometry] as the picture was, sized to the picture by
 * `scale2ref` (it already is, but a picture read at another size still fits)
 * and merged in as its alpha (`alphamerge`). One link of a filter chain holding
 * a little graph of its own; [label] makes its links unique. Without a mask
 * nothing is cut. Only for a picture (a single frame): what comes out is one
 * frame.
 *
 * The picture and the mask are both repeated into scale2ref and the first
 * merged frame alone is kept (`trim`): in FFmpeg 6.1 scale2ref never ends when
 * one of its inputs is over while the other still has a frame to give (in
 * 6.1.6 it even closes the other one), and the export waited forever.
 */
internal fun subjectFilters(mask: String?, label: String, maskGeometry: List<String>): String {
    if (mask == null) return "format=rgba"
    val chain = (listOf("movie=" + filterOptionValue(mask), "alphaextract") + maskGeometry + SUBJECT_LOOP).joinToString(",")
    return "format=rgba,$SUBJECT_LOOP[${label}p];$chain[${label}m];" +
        "[${label}m][${label}p]scale2ref[${label}ms][${label}ps];" +
        "[${label}ps][${label}ms]alphamerge,trim=end_frame=1"
}

/** Repeats a single frame endlessly (see [subjectFilters]). */
private const val SUBJECT_LOOP = "loop=loop=-1:size=1"

/**
 * The key on a picture that may have transparent parts of its own (a sticker, a
 * PNG laid over the video), keeping them: the key's alpha and the picture's
 * are multiplied (`blend=all_mode=multiply` of the two alpha planes, then
 * `alphamerge`), so that the picture stays as transparent as it was wherever
 * the key leaves it. One link of a filter chain holding a little graph of its
 * own; [label] makes its links unique.
 */
internal fun keyKeepingAlpha(key: ColorKey, label: String, maskGeometry: List<String> = emptyList()): String =
    "format=rgba,split[${label}o][${label}s];" +
        "[${label}s]" + keyFilters(key, "${label}x", maskGeometry) + ",format=rgba,split[${label}k][${label}k2];" +
        "[${label}o]alphaextract[${label}oa];[${label}k2]alphaextract[${label}ka];" +
        "[${label}oa][${label}ka]blend=all_mode=multiply[${label}am];" +
        "[${label}k][${label}am]alphamerge"

/**
 * The key on an opaque picture with what it cuts showing its see-through
 * background ([ColorKey.hasTranslucentBackground]), the result rgba: the keyed
 * picture laid (`overlay` on packed RGB, straight alpha) over a copy of itself
 * turned into the background colour at its opacity (`lutrgb` with constants).
 * [label] makes the links of its little graph unique.
 */
internal fun keyOverTranslucentBackground(key: ColorKey, label: String, maskGeometry: List<String> = emptyList()): String {
    val background = key.background ?: 0
    val r = (background shr 16) and 0xFF
    val g = (background shr 8) and 0xFF
    val b = background and 0xFF
    return keyFilters(key, "${label}x", maskGeometry) + ",format=rgba,split[${label}k][${label}b];" +
        "[${label}b]lutrgb=r=$r:g=$g:b=$b:a=${key.backgroundAlpha}[${label}c];" +
        "[${label}c][${label}k]overlay=format=rgb,format=rgba"
}

/**
 * The key, with what it cuts showing its background (black without one): one
 * link of a filter chain that holds a little graph of its own, the keyed picture
 * split and laid over a copy of itself filled with that colour. [label] makes its
 * links unique in the graph. The result is yuv420p, or yuv444p with [fullColor]
 * (a picture file, which keeps all its colour).
 */
internal fun keyOverBackground(key: ColorKey, label: String, fullColor: Boolean = false, maskGeometry: List<String> = emptyList()): String {
    val background = String.format(Locale.US, "0x%06X", (key.background ?: 0) and 0xFFFFFF)
    val base = if (fullColor) "yuv444p" else "yuv420p"
    return keyFilters(key, "${label}x", maskGeometry) + ",split[${label}k][${label}b];" +
        "[${label}b]format=$base,drawbox=x=0:y=0:w=iw:h=ih:color=$background@1:t=fill[${label}c];" +
        "[${label}c][${label}k]overlay=format=auto"
}
