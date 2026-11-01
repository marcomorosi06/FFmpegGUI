package com.cuscus.ffmpeggui.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.JsonText
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/*
 * The effects the app ships with (assets/effects/, drawn by
 * scripts/generate-effects.py): sparks, flames, snow and the like that loop for
 * as long as they are laid over the video, and transitions (a fairy's wand, an
 * explosion, a flash) that play once over the cut between two clips.
 *
 * Each is light on black, laid with the screen blend: black disappears and the
 * light adds up. A few carry their own opacity instead (the 3D fists of the
 * punch transitions, drawn by scripts/punch3d.py): their file holds the
 * colours over the opacity, one above the other, laid as they are; their
 * small frames keep the opacity too. Those "companions" belong to a
 * transition told as layers (see TransitionKind.companionEffect) and are not
 * offered on their own. The players and FFmpeg read a file, not an asset, so each one
 * is copied once into the app's own files (again when a new version of the app
 * brings a different one). The picker shows each from a sheet of small frames
 * (a JPEG next to it, a WebP for one with its opacity), so that no video is
 * decoded to show it.
 */

/** One effect of the library. */
data class EffectInfo(
    /** Its name, as projects keep it (and its file's, without the extension). */
    val name: String,
    val file: String,
    /** A transition plays once over a cut; any other effect loops. */
    val transition: Boolean,
    val frames: Int,
    val fps: Int,
    /** For a transition: where the cut falls, ms from its start (under its brightest moment). */
    val cutMs: Long,
    /** Its small frames for the picker, if there are any. */
    val sheet: SheetInfo? = null,
    /** True when it carries its own opacity (colours over opacity in its file), laid without the screen blend. */
    val alpha: Boolean = false,
    /** True when it belongs to a transition told as layers (see TransitionKind.companionEffect), not offered alone. */
    val companion: Boolean = false,
) {
    /** How long it lasts (one turn of a loop). */
    val lengthMs: Long get() = if (fps > 0) frames * 1000L / fps else 0L
}

/**
 * Where an effect's small frames are: [file] holds [frames] frames of [width] x
 * [height], [columns] a row, at [fps] frames a second.
 */
data class SheetInfo(val file: String, val frames: Int, val columns: Int, val fps: Int, val width: Int, val height: Int)

/** An effect's small frames, read (see [EffectLibrary.sheet]). */
class EffectSheet(val bitmap: Bitmap, val about: SheetInfo) {
    /** How long one turn of its frames lasts, ms. */
    val lengthMs: Long get() = if (about.fps > 0) about.frames * 1000L / about.fps else 0L
}

object EffectLibrary {
    private const val TAG = "EffectLibrary"
    private const val ASSETS = "effects"
    private const val INDEX = "index.json"

    @Volatile private var appContext: Context? = null
    @Volatile private var index: List<EffectInfo> = emptyList()
    private val sheets = ConcurrentHashMap<String, EffectSheet>()

    /** Reads the library's index (a small file): call once, when the app starts. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        index = try {
            context.assets.open("$ASSETS/$INDEX").bufferedReader().use { parse(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "No effects index", e)
            emptyList()
        }
    }

    /** The effects that loop, in the library's order. */
    val loops: List<EffectInfo> get() = index.filter { !it.transition }

    /** The transitions offered on their own, in the library's order. */
    val transitions: List<EffectInfo> get() = index.filter { it.transition && !it.companion }

    fun info(name: String?): EffectInfo? = name?.let { n -> index.firstOrNull { it.name == n } }

    private fun dir(): File? = appContext?.let { File(it.filesDir, ASSETS) }

    /** True when [uri] is one of the library's copies (never copied into a project). */
    fun owns(uri: Uri): Boolean {
        val dir = dir() ?: return false
        return uri.scheme == "file" && uri.path.orEmpty().startsWith(dir.path + "/")
    }

    /** Where the copy of effect [name] is (see [ensure]); null for an unknown effect. */
    fun uri(name: String): Uri? {
        val effect = info(name) ?: return null
        val dir = dir() ?: return null
        return Uri.fromFile(File(dir, effect.file))
    }

    /**
     * Copies effect [name] out of the assets unless its copy is there already
     * (as long as the asset). Away from the main thread; one copy at a time, so
     * that two callers never write the same file. False when it could not be.
     */
    @Synchronized
    fun ensure(name: String): Boolean {
        val context = appContext ?: return false
        val effect = info(name) ?: return false
        val dir = dir() ?: return false
        val target = File(dir, effect.file)
        return try {
            val length = context.assets.openFd("$ASSETS/${effect.file}").use { it.length }
            if (target.isFile && target.length() == length) return true
            dir.mkdirs()
            val partial = File(dir, ".${effect.file}.part")
            context.assets.open("$ASSETS/${effect.file}").use { input -> partial.outputStream().use { input.copyTo(it) } }
            partial.renameTo(target) || run {
                target.delete()
                partial.renameTo(target)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy effect $name", e)
            false
        }
    }

    /** Copies every effect that is not there yet (see [ensure]). Away from the main thread. */
    fun ensureAll() {
        index.forEach { ensure(it.name) }
    }

    /**
     * The picker's frames of effect [name] (see [EffectSheet]), read from the
     * assets once and kept: no video is decoded. Away from the main thread;
     * null when there are none.
     */
    fun sheet(name: String): EffectSheet? {
        sheets[name]?.let { return it }
        val context = appContext ?: return null
        val effect = info(name) ?: return null
        val about = effect.sheet ?: return null
        // Light on black needs no opacity; a fist does.
        val config = if (effect.alpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val options = BitmapFactory.Options().apply { inPreferredConfig = config }
        val bitmap = try {
            context.assets.open("$ASSETS/${about.file}").use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            Log.w(TAG, "No frames for effect $name", e)
            null
        } ?: return null
        val sheet = EffectSheet(bitmap, about)
        sheets[name] = sheet
        return sheet
    }

    /** The sheet of effect [name] if it was read already, without reading it. */
    fun sheetIfRead(name: String): EffectSheet? = sheets[name]

    /**
     * The index as the generator writes it: a list of {name, file, kind, frames,
     * fps, cut, sheet, alpha}, kind one of "loop", "transition" and "companion".
     */
    internal fun parse(text: String): List<EffectInfo> {
        val list = JsonText.parse(text) as? Json.Arr ?: return emptyList()
        return list.items.mapNotNull { item ->
            val o = item as? Json.Obj ?: return@mapNotNull null
            val name = o.stringOrNull("name") ?: return@mapNotNull null
            val kind = o.string("kind", "loop")
            val companion = kind == "companion"
            val transition = kind == "transition" || companion
            EffectInfo(
                name = name,
                file = o.string("file", "$name.mp4"),
                transition = transition,
                frames = o.int("frames", 0),
                fps = o.int("fps", 30),
                cutMs = Math.round(o.double("cut", 0.0) * 1000.0),
                sheet = o.obj("sheet")?.let { sh ->
                    SheetInfo(
                        file = sh.string("file", "$name.jpg"),
                        frames = sh.int("frames", 0),
                        columns = sh.int("columns", 1).coerceAtLeast(1),
                        fps = sh.int("fps", 15),
                        width = sh.int("width", 0),
                        height = sh.int("height", 0),
                    ).takeIf { it.frames > 0 && it.width > 0 && it.height > 0 }
                },
                alpha = o.bool("alpha", false),
                companion = companion,
            ).takeIf { it.frames > 0 }
        }
    }
}
