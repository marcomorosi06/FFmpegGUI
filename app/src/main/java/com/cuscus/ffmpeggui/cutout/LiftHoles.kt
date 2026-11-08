package com.cuscus.ffmpeggui.cutout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cuscus.ffmpeggui.core.image.LiftHole
import com.cuscus.ffmpeggui.core.image.RgbaPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * What a long press leaves in the photo where it lifted a subject, when the
 * settings ask for more than a copy (see LiftMode): the subject's own shape
 * (from SubjectLifter, on the photo before its turns and crop) as a cover of
 * the whole photo, then either a hole cut through the photo ([cut]: a new PNG,
 * see-through or a colour, see LiftHole.punch) or a mask for the magic
 * eraser ([eraseMask]).
 *
 * The files go with the app's own ([FOLDER]); a project keeps its own copy.
 */
class LiftHoles(context: Context) {

    private val folder = File(context.applicationContext.filesDir, FOLDER)
    private val resolver = context.applicationContext.contentResolver

    /** How much of the subject covers each pixel (0..255) of the picture it was lifted from. */
    class Cover(val bytes: ByteArray, val width: Int, val height: Int)

    /** The cover of [lifted] over the whole picture it came from. */
    suspend fun cover(lifted: SubjectLifter.Lifted): Cover = withContext(Dispatchers.IO) {
        val subject = BitmapFactory.decodeFile(lifted.file.path) ?: throw IOException("The subject cannot be read")
        try {
            val w = lifted.pictureWidth
            val h = lifted.pictureHeight
            val bytes = ByteArray(w * h)
            val row = IntArray(subject.width)
            for (y in 0 until subject.height) {
                val py = lifted.top + y
                if (py !in 0 until h) continue
                subject.getPixels(row, 0, subject.width, 0, y, subject.width, 1)
                for (x in 0 until subject.width) {
                    val px = lifted.left + x
                    if (px in 0 until w) bytes[py * w + px] = (row[x] ushr 24).toByte()
                }
            }
            Cover(bytes, w, h)
        } finally {
            subject.recycle()
        }
    }

    /**
     * The photo at [uri] with the hole [cover] marks cut through it, at its own
     * size: see-through ([transparent], [opaqueColor] under it for files that
     * hold no transparency) or [color]. A new PNG of the app's; its URI.
     */
    suspend fun cut(uri: Uri, cover: Cover, transparent: Boolean, color: Long, opaqueColor: Long): Uri = withContext(Dispatchers.Default) {
        val photo = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            }
        } ?: throw IOException("The photo cannot be read")
        val w = photo.width
        val h = photo.height
        // Not premultiplied: a see-through pixel keeps its colour.
        val pixels = IntArray(w * h).also { photo.getPixels(it, 0, w, 0, 0, w, h) }
        photo.recycle()
        val holes = LiftHole.scaled(cover.bytes, cover.width, cover.height, w, h)
        val punched = LiftHole.punch(pixels, holes, transparent, color, opaqueColor)
        prune()
        folder.mkdirs()
        val target = File(folder, "cut-${UUID.randomUUID().toString().take(8)}.png")
        withContext(Dispatchers.IO) {
            target.outputStream().buffered().use { RgbaPng.write(it, w, h, punched) }
        }
        Uri.fromFile(target)
    }

    /** [cover] as a mask for the magic eraser (white, as opaque as the subject is): its path. */
    suspend fun eraseMask(cover: Cover): String = withContext(Dispatchers.IO) {
        val pixels = IntArray(cover.width * cover.height) { i -> ((cover.bytes[i].toInt() and 0xFF) shl 24) or 0xFFFFFF }
        val mask = Bitmap.createBitmap(pixels, cover.width, cover.height, Bitmap.Config.ARGB_8888)
        prune()
        folder.mkdirs()
        val target = File(folder, "mask-${UUID.randomUUID().toString().take(8)}.png")
        try {
            target.outputStream().use { if (!mask.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Could not write the mask") }
        } finally {
            mask.recycle()
        }
        target.path
    }

    /** Masks older than a day go (they are used at once); the cut photos stay, a project may use them. */
    private fun prune() {
        val old = System.currentTimeMillis() - DAY_MS
        folder.listFiles()?.filter { it.name.startsWith("mask-") && it.lastModified() < old }?.forEach { it.delete() }
    }

    private companion object {
        const val FOLDER = "lift-holes"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
