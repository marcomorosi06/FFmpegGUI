package com.cuscus.ffmpeggui.projects

import com.cuscus.ffmpeggui.core.model.OverlayLook
import android.net.Uri
import com.cuscus.ffmpeggui.AudioClip
import com.cuscus.ffmpeggui.ConversionSettings
import com.cuscus.ffmpeggui.EditorSource
import com.cuscus.ffmpeggui.ImageOverlay
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.project.Json
import com.cuscus.ffmpeggui.core.project.ModelJson
import com.cuscus.ffmpeggui.core.project.jsonObject

/**
 * A recent project as kept on the phone: the editor's state ([settings]) and
 * what the home screen shows about it. Undo history is not kept.
 */
data class SavedProject(
    val id: String,
    /** The name of the file the project was opened with. */
    val name: String,
    val format: OutputFormat,
    val createdAtMs: Long,
    val editedAtMs: Long,
    /** Absolute path of the last file exported from it, if any. */
    val lastExport: String? = null,
    val settings: ConversionSettings,
)

/**
 * The project file, `project.json`: plain, indented JSON with the editor's own
 * field names, so anyone can open it and see exactly what was kept. A header
 * says what the file is. Unknown fields are ignored and missing ones take
 * their defaults, so files of other versions of the app still open.
 */
object ProjectJson {

    const val KIND = "FFmpeg GUI project"
    const val VERSION = 1
    private const val ABOUT =
        "Kept only on this phone, in FFmpeg GUI's private storage, so that editing can go on later. " +
            "It never leaves the device: the app has no internet access. " +
            "Settings > Recent projects shows it and deletes it."

    fun write(project: SavedProject): Json.Obj = jsonObject {
        put("kind", KIND)
        put("version", VERSION)
        put("about", ABOUT)
        put("id", project.id)
        put("name", project.name)
        put("format", project.format)
        put("createdAtMs", project.createdAtMs)
        put("editedAtMs", project.editedAtMs)
        put("lastExport", project.lastExport)
        put("settings", write(project.settings))
    }

    /** The project in [o], or null when it is not one (another kind of file, no id, an unknown format). */
    fun project(o: Json.Obj): SavedProject? {
        if (o.string("kind", "") != KIND) return null
        val id = o.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val formatName = o.stringOrNull("format") ?: return null
        val format = OutputFormat.entries.firstOrNull { it.name == formatName } ?: return null
        val created = o.long("createdAtMs", 0L)
        return SavedProject(
            id = id,
            name = o.string("name", ""),
            format = format,
            createdAtMs = created,
            editedAtMs = o.long("editedAtMs", created),
            lastExport = o.stringOrNull("lastExport"),
            settings = o.obj("settings")?.let(::settings) ?: return null,
        )
    }

    // -----------------------------------------------------------------
    // The editor's state
    // -----------------------------------------------------------------

    fun write(s: ConversionSettings): Json.Obj = jsonObject {
        putList("sources", s.sources) { write(it) }
        put("timeline", ModelJson.write(s.timeline))
        put("trimStartMs", s.trimStartMs)
        put("trimEndMs", s.trimEndMs)
        put("resolution", s.resolution)
        put("framerate", s.framerate)
        put("qualityLevel", s.qualityLevel)
        put("keepHdr", s.keepHdr)
        put("wholeEffects", ModelJson.write(s.wholeEffects))
        put("projectRatio", s.projectRatio)
        put("normalizeAudio", s.normalizeAudio)
        put("audioEffects", ModelJson.write(s.audioEffects))
        put("audioEncoding", ModelJson.write(s.audioEncoding))
        put("gif", ModelJson.write(s.gif))
        put("imageEdit", ModelJson.write(s.imageEdit))
        putList("audioClips", s.audioClips) { write(it) }
        putList("textOverlays", s.textOverlays) { ModelJson.write(it) }
        put("customCommand", s.customCommand)
        put("isManualMode", s.isManualMode)
        put("manualCommandOverride", s.manualCommandOverride)
        putList("imageOverlays", s.imageOverlays) { write(it) }
        put("subtitleUri", s.subtitleUri?.toString())
        put("subtitleName", s.subtitleName)
        put("subtitleStyle", ModelJson.write(s.subtitleStyle))
        // The words heard in each file: their times on one line, their text in one string.
        putList("spokenWords", s.spokenWords.entries.toList()) { (sourceId, words) ->
            Json.Obj(mapOf("source" to Json.of(sourceId)) + ModelJson.writeWords(words).fields)
        }
        put("useStreamCopy", s.useStreamCopy)
        put("frameExtractionMode", s.frameExtractionMode)
        put("frameExtractionRate", s.frameExtractionRate)
        put("frameExtractionTimecode", s.frameExtractionTimecode)
        put("stabilize", s.stabilize)
        put("stabilizeShakiness", s.stabilizeShakiness)
        put("stabilizeSmoothing", s.stabilizeSmoothing)
        put("splitMode", s.splitMode)
        put("splitValue", s.splitValue)
        put("tags", ModelJson.write(s.tags))
        put("coverUri", s.coverUri?.toString())
    }

    fun settings(o: Json.Obj): ConversionSettings {
        val d = ConversionSettings()
        return ConversionSettings(
            sources = o.list("sources", d.sources) { (it as? Json.Obj)?.let(::source) },
            timeline = o.obj("timeline")?.let(ModelJson::timeline) ?: d.timeline,
            trimStartMs = o.long("trimStartMs", d.trimStartMs),
            trimEndMs = o.longOrNull("trimEndMs", d.trimEndMs),
            resolution = o.enum("resolution", d.resolution),
            framerate = o.enum("framerate", d.framerate),
            qualityLevel = o.float("qualityLevel", d.qualityLevel),
            keepHdr = o.bool("keepHdr", d.keepHdr),
            wholeEffects = o.obj("wholeEffects")?.let(ModelJson::effects) ?: d.wholeEffects,
            projectRatio = o.enum("projectRatio", d.projectRatio),
            normalizeAudio = o.bool("normalizeAudio", d.normalizeAudio),
            audioEffects = o.obj("audioEffects")?.let(ModelJson::audioEffects) ?: d.audioEffects,
            audioEncoding = o.obj("audioEncoding")?.let(ModelJson::audioEncoding) ?: d.audioEncoding,
            gif = o.obj("gif")?.let(ModelJson::gifSettings) ?: d.gif,
            imageEdit = o.obj("imageEdit")?.let(ModelJson::imageEdit) ?: d.imageEdit,
            audioClips = o.list("audioClips", d.audioClips) { (it as? Json.Obj)?.let(::audioClip) },
            textOverlays = o.list("textOverlays", d.textOverlays) { (it as? Json.Obj)?.let(ModelJson::textOverlay) },
            customCommand = o.string("customCommand", d.customCommand),
            isManualMode = o.bool("isManualMode", d.isManualMode),
            manualCommandOverride = o.string("manualCommandOverride", d.manualCommandOverride),
            imageOverlays = o.list("imageOverlays", d.imageOverlays) { (it as? Json.Obj)?.let(::imageOverlay) },
            subtitleUri = o.stringOrNull("subtitleUri")?.let(Uri::parse),
            subtitleName = o.string("subtitleName", d.subtitleName),
            subtitleStyle = o.obj("subtitleStyle")?.let(ModelJson::subtitleStyle) ?: d.subtitleStyle,
            spokenWords = o.list("spokenWords", emptyList<Pair<String, List<com.cuscus.ffmpeggui.core.subtitles.SpokenWord>>>()) { item ->
                val entry = item as? Json.Obj ?: return@list null
                val sourceId = entry.stringOrNull("source") ?: return@list null
                ModelJson.spokenWords(entry)?.let { sourceId to it }
            }.toMap(),
            useStreamCopy = o.bool("useStreamCopy", d.useStreamCopy),
            frameExtractionMode = o.enum("frameExtractionMode", d.frameExtractionMode),
            frameExtractionRate = o.float("frameExtractionRate", d.frameExtractionRate),
            frameExtractionTimecode = o.string("frameExtractionTimecode", d.frameExtractionTimecode),
            stabilize = o.bool("stabilize", d.stabilize),
            stabilizeShakiness = o.int("stabilizeShakiness", d.stabilizeShakiness),
            stabilizeSmoothing = o.int("stabilizeSmoothing", d.stabilizeSmoothing),
            splitMode = o.enum("splitMode", d.splitMode),
            splitValue = o.int("splitValue", d.splitValue),
            tags = o.obj("tags")?.let(ModelJson::metadataTags) ?: d.tags,
            coverUri = o.stringOrNull("coverUri")?.let(Uri::parse),
        )
    }

    fun write(s: EditorSource): Json.Obj = jsonObject {
        put("id", s.id)
        put("uri", s.uri.toString())
        put("fileName", s.fileName)
        put("info", s.info, ModelJson::write)
    }

    fun source(o: Json.Obj): EditorSource? {
        val id = o.stringOrNull("id") ?: return null
        val uri = o.stringOrNull("uri") ?: return null
        return EditorSource(
            id = id,
            uri = Uri.parse(uri),
            fileName = o.string("fileName", ""),
            info = o.objOrNull("info", null, ModelJson::mediaInfo),
        )
    }

    fun write(a: AudioClip): Json.Obj = jsonObject {
        put("id", a.id)
        put("uri", a.uri.toString())
        put("name", a.name)
        put("span", ModelJson.write(a.span))
        put("volume", a.volume)
        put("muted", a.muted)
        put("fadeInSeconds", a.fadeInSeconds)
        put("fadeOutSeconds", a.fadeOutSeconds)
        put("keepPitch", a.keepPitch)
        put("pitchSemitones", a.pitchSemitones)
        put("bassDb", a.bassDb)
        put("midDb", a.midDb)
        put("trebleDb", a.trebleDb)
        put("normalize", a.normalize)
        put("loudnessLufs", a.loudnessLufs)
        put("effects", ModelJson.write(a.effects))
        with(ModelJson) { putKeyframes(a.keyframes) }
    }

    fun audioClip(o: Json.Obj): AudioClip? {
        val id = o.stringOrNull("id") ?: return null
        val uri = o.stringOrNull("uri") ?: return null
        val span = o.obj("span")?.let(ModelJson::soundSpan) ?: return null
        val d = AudioClip(id = id, uri = Uri.parse(uri), name = "", span = span)
        return d.copy(
            name = o.string("name", d.name),
            volume = o.float("volume", d.volume),
            muted = o.bool("muted", d.muted),
            fadeInSeconds = o.float("fadeInSeconds", d.fadeInSeconds),
            fadeOutSeconds = o.float("fadeOutSeconds", d.fadeOutSeconds),
            keepPitch = o.bool("keepPitch", d.keepPitch),
            pitchSemitones = o.float("pitchSemitones", d.pitchSemitones),
            bassDb = o.float("bassDb", d.bassDb),
            midDb = o.float("midDb", d.midDb),
            trebleDb = o.float("trebleDb", d.trebleDb),
            normalize = o.bool("normalize", d.normalize),
            loudnessLufs = o.doubleOrNull("loudnessLufs", d.loudnessLufs),
            effects = o.obj("effects")?.let(ModelJson::audioEffects) ?: AudioEffects.NONE,
            keyframes = ModelJson.keyframes(o),
        )
    }

    fun write(i: ImageOverlay): Json.Obj = jsonObject {
        put("id", i.id)
        put("uri", i.uri?.toString())
        put("fileName", i.fileName)
        put("x", i.x)
        put("y", i.y)
        put("scaleW", i.scaleW)
        put("opacity", i.opacity)
        put("clipId", i.clipId)
        put("startMs", i.startMs)
        put("endMs", i.endMs)
        put("fadeInSeconds", i.fadeInSeconds)
        put("fadeOutSeconds", i.fadeOutSeconds)
        put("rotationDegrees", i.rotationDegrees)
        put("flipHorizontal", i.flipHorizontal)
        put("flipVertical", i.flipVertical)
        put("brightness", i.brightness)
        put("contrast", i.contrast)
        put("saturation", i.saturation)
        put("video", i.video, ModelJson::write)
        put("aspect", i.aspect)
        put("hasAudio", i.hasAudio)
        put("volume", i.volume)
        put("muted", i.muted)
        put("key", i.key, ModelJson::write)
        with(ModelJson) { putKeyframes(i.keyframes) }
        put("look", i.look.takeUnless { it == OverlayLook.NONE }, ModelJson::write)
        put("layer", i.layer)
    }

    fun imageOverlay(o: Json.Obj): ImageOverlay? {
        val id = o.stringOrNull("id") ?: return null
        val d = ImageOverlay(id = id)
        return ImageOverlay(
            id = id,
            uri = o.stringOrNull("uri")?.let(Uri::parse),
            fileName = o.string("fileName", d.fileName),
            x = o.float("x", d.x),
            y = o.float("y", d.y),
            scaleW = o.float("scaleW", d.scaleW),
            opacity = o.float("opacity", d.opacity),
            clipId = o.stringOrNull("clipId", d.clipId),
            startMs = o.long("startMs", d.startMs),
            endMs = o.longOrNull("endMs", d.endMs),
            fadeInSeconds = o.float("fadeInSeconds", d.fadeInSeconds),
            fadeOutSeconds = o.float("fadeOutSeconds", d.fadeOutSeconds),
            rotationDegrees = o.float("rotationDegrees", d.rotationDegrees),
            flipHorizontal = o.bool("flipHorizontal", d.flipHorizontal),
            flipVertical = o.bool("flipVertical", d.flipVertical),
            brightness = o.float("brightness", d.brightness),
            contrast = o.float("contrast", d.contrast),
            saturation = o.float("saturation", d.saturation),
            video = o.objOrNull("video", d.video, ModelJson::soundSpan),
            aspect = o.float("aspect", d.aspect),
            hasAudio = o.bool("hasAudio", d.hasAudio),
            volume = o.float("volume", d.volume),
            muted = o.bool("muted", d.muted),
            key = o.objOrNull("key", d.key, ModelJson::colorKey),
            keyframes = ModelJson.keyframes(o),
            look = o.objOrNull("look", null, ModelJson::overlayLook) ?: OverlayLook.NONE,
            layer = o.int("layer", d.layer),
        )
    }

    /** Every file the project refers to: its footage, sounds, pictures, subtitles and cover. */
    fun uris(s: ConversionSettings): List<Uri> = (
        s.sources.map { it.uri } +
            s.audioClips.map { it.uri } +
            s.imageOverlays.mapNotNull { it.uri } +
            listOfNotNull(s.subtitleUri, s.coverUri)
        ).distinct()

    /** [s] with every file URI passed through [map]. */
    fun mapUris(s: ConversionSettings, map: (Uri) -> Uri): ConversionSettings = s.copy(
        sources = s.sources.map { it.copy(uri = map(it.uri)) },
        audioClips = s.audioClips.map { it.copy(uri = map(it.uri)) },
        imageOverlays = s.imageOverlays.map { o -> o.uri?.let { o.copy(uri = map(it)) } ?: o },
        subtitleUri = s.subtitleUri?.let(map),
        coverUri = s.coverUri?.let(map),
    )
}
