package com.cuscus.ffmpeggui.media

import android.media.ExifInterface
import android.os.Build
import android.util.Log
import com.cuscus.ffmpeggui.core.engine.PictureTagger
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.OutputFormat

/**
 * [PictureTagger] on the platform's EXIF writer: the title goes into the image
 * description, then the artist and the copyright. JPG can always hold them,
 * PNG and WEBP from Android 11 ([canTag]); BMP and TIFF are not written.
 */
class AndroidPictureTagger : PictureTagger {

    override fun write(path: String, format: OutputFormat, tags: MetadataTags): Boolean {
        if (!canTag(format)) return false
        return try {
            val exif = ExifInterface(path)
            // A blank field removes the tag (null), as for sound and video.
            fun set(name: String, value: String) = exif.setAttribute(name, value.trim().ifEmpty { null })
            set(ExifInterface.TAG_IMAGE_DESCRIPTION, tags.title)
            set(ExifInterface.TAG_ARTIST, tags.artist)
            set(ExifInterface.TAG_COPYRIGHT, tags.copyright)
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not write tags: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "AndroidPictureTagger"

        /** Whether a picture in [format] can carry tags on this device. */
        fun canTag(format: OutputFormat): Boolean = when (format) {
            OutputFormat.JPG -> true
            OutputFormat.PNG, OutputFormat.WEBP -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            else -> false
        }
    }
}
