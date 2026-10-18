package com.cuscus.ffmpeggui.core.engine

import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.hdr.GainMapMetadata
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Stroke

/*
 * Boundaries between the conversion logic and the platform. The core module
 * only knows these interfaces; the Android implementations live in :media.
 * Sources are identified by opaque URI strings so that nothing here depends on
 * android.net.Uri.
 */

/** Runs FFmpeg. */
fun interface FfmpegExecutor {
    /**
     * Runs [command] to completion, forwarding every log fragment to [onLog]
     * (possibly from another thread). Cancelling the calling coroutine must stop
     * the running process.
     */
    suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult
}

sealed interface ExecutionResult {
    data object Success : ExecutionResult
    data object Cancelled : ExecutionResult
    data class Failed(val returnCode: Int) : ExecutionResult
}

/** Makes sources readable by FFmpeg as plain files. */
fun interface SourceStager {
    /** Opens a private working area. Closing the session deletes everything in it. */
    fun open(): StagingSession
}

interface StagingSession : AutoCloseable {
    /**
     * Makes [sourceUri] available as a local file and returns its absolute path,
     * or null when the source cannot be read. [preferredName] only influences
     * the file name and extension.
     */
    fun stage(sourceUri: String, preferredName: String): String?

    /** Path of a new temporary file inside the session (not created yet). */
    fun scratchFile(name: String): String

    override fun close()
}

/** Reads basic facts about a media source. */
interface MediaProbe {
    fun describe(sourceUri: String): MediaInfo?

    /** Duration in milliseconds, or 0 when unknown. */
    fun durationMs(sourceUri: String): Long

    /** User-visible file name, or null when the source does not expose one. */
    fun displayName(sourceUri: String): String?
}

/**
 * Makes still pictures the editor can use as clips. Both kinds are written as
 * files the app owns (see [ScratchStorage.discard]), upright and of a size the
 * encoder accepts, so that the export and the preview treat them alike.
 */
interface StillMaker {
    /** A clip-ready copy of the picture at [sourceUri], or null when it is not a readable picture. */
    fun importPicture(sourceUri: String): String?

    /** The frame of the video at [sourceUri] shown at [positionMs] of the file, or null. */
    fun captureFrame(sourceUri: String, positionMs: Long): String?
}

/**
 * Writes a picture in a format the bundled FFmpeg has no encoder for (WEBP):
 * FFmpeg makes a PNG, the platform encodes it.
 */
fun interface ImageRecoder {
    /** Writes the PNG at [pngPath] as [format] to [targetPath] at [quality] 0..1; false when it cannot. */
    fun recode(pngPath: String, targetPath: String, format: OutputFormat, quality: Float): Boolean
}

/**
 * Draws the drawing of a picture ([com.cuscus.ffmpeggui.core.model.ImageEdit.strokes])
 * on a transparent PNG, which the export lays over the picture like any other.
 */
fun interface DrawingRenderer {
    /** A [width] x [height] picture of [strokes], as a source URI; null when it cannot be written. */
    fun render(strokes: List<Stroke>, width: Int, height: Int): String?
}

/**
 * Works out the gain map of an Ultra HDR picture (see
 * [com.cuscus.ffmpeggui.core.model.ImageHdr]) and writes it as a JPG.
 */
fun interface HdrPictureMaker {
    /**
     * The gain map of [job], written to [HdrJob.gainMapJpg]; how its values
     * are read, or null when it would change nothing (the picture is then
     * written as a plain JPG).
     */
    fun make(job: HdrJob): GainMapMetadata?
}

/**
 * What [HdrPictureMaker] works from: the picture as it comes out
 * ([HdrJob.finalPng]) and without what lies on it ([HdrJob.plainPng]), both
 * lossless and of the JPG's size; the edit that made it ([edit], [effects], the
 * source's [sourceWidth] x [sourceHeight]) to bring masks and a photo's own gain
 * map onto it; the source the picture came from ([sourceUri]).
 */
class HdrJob(
    val hdr: ImageHdr,
    val edit: ImageEdit,
    val effects: Effects,
    val sourceUri: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val finalPng: String,
    val plainPng: String,
    val gainMapJpg: String,
)

/** Reads the tags a file already has, so that the editor can show and change them. */
fun interface MetadataReader {
    /**
     * The tags of the file at [sourceUri], with [MetadataTags.fromSource] set; null
     * when they cannot be read (the file then keeps its own, see [MetadataTags]).
     */
    fun read(sourceUri: String): MetadataTags?
}

/**
 * Writes tags into a finished picture (EXIF): FFmpeg writes no tags into
 * pictures. Only some formats can hold them; see the implementation.
 */
fun interface PictureTagger {
    /** Writes [tags] into the picture at [path]; false when [format] cannot hold them here. */
    fun write(path: String, format: OutputFormat, tags: MetadataTags): Boolean
}

/**
 * Makes the cover picture of an audio file. Every cover is written as a file
 * the app owns (see [ScratchStorage.discard]): a JPEG small enough to sit in
 * the file's tags.
 */
interface CoverArtist {
    /** A cover made from the picture at [pictureUri], cut square around its centre; null when unreadable. */
    fun fromPicture(pictureUri: String): String?

    /** The cover the file at [sourceUri] already holds, or null when it has none. */
    fun embedded(sourceUri: String): String?
}

/** Tells the engine where finished files go. */
fun interface OutputDirectoryProvider {
    /** Absolute path of a writable directory. The engine creates it when missing. */
    fun outputDirectory(): String
}

/** Measures how loud a source is, for previewing loudness normalisation. */
fun interface LoudnessMeter {
    /** Integrated loudness in LUFS, or null when it cannot be measured (no audio, read error). */
    suspend fun integratedLufs(sourceUri: String): Double?
}

/** Reads small text sources such as subtitle files. */
fun interface TextSourceReader {
    /** Content decoded as UTF-8, or null when unreadable or larger than [maxBytes]. */
    fun readText(sourceUri: String, maxBytes: Int): String?
}

/**
 * Files the app creates for its own use, such as camera recordings, and that
 * must not outlive the project they belong to.
 */
interface ScratchStorage {
    /** A new, empty place to record a video into, as a content URI. */
    fun newRecording(): String

    /**
     * Deletes the file behind [sourceUri] when the app created it ([newRecording],
     * a [StillMaker] picture). Anything else (the user's own files) is never touched.
     */
    fun discard(sourceUri: String)

    /**
     * Removes what earlier runs left behind: working copies of a conversion the
     * system killed, recordings of projects that were never closed, files of
     * older app versions. Call it once at start-up, away from the main thread.
     */
    fun sweep()
}
