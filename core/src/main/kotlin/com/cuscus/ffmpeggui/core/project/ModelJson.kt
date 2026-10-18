package com.cuscus.ffmpeggui.core.project

import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.model.Compressor
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.OverlayLook
import com.cuscus.ffmpeggui.core.model.Echo
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.ImageBorder
import com.cuscus.ffmpeggui.core.model.ImageEdit
import com.cuscus.ffmpeggui.core.model.ImageHdr
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.core.model.ImageOutput
import com.cuscus.ffmpeggui.core.model.ImageTone
import com.cuscus.ffmpeggui.core.model.EaseCurve
import com.cuscus.ffmpeggui.core.model.EasePoint
import com.cuscus.ffmpeggui.core.model.KeyEasing
import com.cuscus.ffmpeggui.core.model.KeyProperty
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.Limiter
import com.cuscus.ffmpeggui.core.model.MediaInfo
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.Modulation
import com.cuscus.ffmpeggui.core.model.ModulationKind
import com.cuscus.ffmpeggui.core.model.MotionKind
import com.cuscus.ffmpeggui.core.model.NoiseGate
import com.cuscus.ffmpeggui.core.model.Reverb
import com.cuscus.ffmpeggui.core.model.SpeedCurve
import com.cuscus.ffmpeggui.core.model.SpeedPoint
import com.cuscus.ffmpeggui.core.model.StereoImage
import com.cuscus.ffmpeggui.core.model.StillMotion
import com.cuscus.ffmpeggui.core.model.Stroke
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.model.VideoPlacement
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.SoundSpan
import com.cuscus.ffmpeggui.core.timeline.Timeline

/**
 * The editing models of :core as JSON, field by field and named as in the code,
 * so a project file reads like the editor's own state.
 *
 * Every reader falls back on the model's own defaults for what is missing, and
 * gives null for an object whose required fields are missing (the caller then
 * leaves it out or uses its default).
 */
object ModelJson {

    // -----------------------------------------------------------------
    // Effects
    // -----------------------------------------------------------------

    fun write(e: Effects): Json.Obj = jsonObject {
        put("rotation", e.rotation)
        put("flipHorizontal", e.flipHorizontal)
        put("flipVertical", e.flipVertical)
        put("crop", e.crop, ::write)
        put("color", write(e.color))
        put("speed", e.speed)
        put("reversed", e.reversed)
        put("fadeInSeconds", e.fadeInSeconds)
        put("fadeOutSeconds", e.fadeOutSeconds)
        put("volume", e.volume)
        put("muted", e.muted)
        put("audioDenoise", e.audioDenoise)
        put("motion", e.motion, ::write)
        put("key", e.key, ::write)
        put("zoom", e.zoom)
        put("panX", e.panX)
        put("panY", e.panY)
        putKeyframes(e.keyframes)
        put("speedCurve", e.speedCurve, ::write)
        put("keepPitch", e.keepPitch)
        put("transition", e.transition) { t -> jsonObject { put("kind", t.kind); put("durationMs", t.durationMs) } }
        put("hdrLook", e.hdrLook)
        put("placement", e.placement) { p ->
            jsonObject {
                put("centerX", p.centerX)
                put("centerY", p.centerY)
                put("scale", p.scale)
                put("rotationDegrees", p.rotationDegrees)
            }
        }
    }

    fun write(c: SpeedCurve): Json.Obj = jsonObject {
        putList("points", c.points) { point -> jsonObject { put("at", point.at); put("speed", point.speed) } }
    }

    fun speedCurve(o: Json.Obj): SpeedCurve {
        val points = o.list("points", emptyList()) { item ->
            val p = item as? Json.Obj ?: return@list null
            val at = p.doubleOrNull("at")?.toFloat() ?: return@list null
            val speed = p.doubleOrNull("speed")?.toFloat() ?: return@list null
            SpeedPoint(at, speed)
        }
        return SpeedCurve(points)
    }

    fun write(c: EaseCurve): Json.Obj = jsonObject {
        putList("points", c.points) { point -> jsonObject { put("x", point.x); put("y", point.y) } }
    }

    fun easeCurve(o: Json.Obj): EaseCurve = EaseCurve(
        o.list("points", emptyList()) { item ->
            val p = item as? Json.Obj ?: return@list null
            val x = p.doubleOrNull("x")?.toFloat() ?: return@list null
            val y = p.doubleOrNull("y")?.toFloat() ?: return@list null
            EasePoint(x, y)
        },
    )

    /** Keyframes, left out when there are none. */
    fun JsonObjectBuilder.putKeyframes(k: Keyframes) {
        if (!k.isEmpty) putList("keyframes", k.sorted, ::write)
    }

    fun write(k: Keyframe): Json.Obj = jsonObject {
        put("atMs", k.atMs)
        put("easing", k.easing)
        put("curve", k.curve, ::write)
        put("values", jsonObject { k.values.forEach { (property, value) -> put(property.name, value) } })
    }

    fun keyframe(o: Json.Obj): Keyframe? {
        val values = o.obj("values") ?: return null
        val read = KeyProperty.entries.mapNotNull { property ->
            val v = values.doubleOrNull(property.name)?.toFloat() ?: return@mapNotNull null
            if (v.isFinite()) property to v else null
        }.toMap()
        return Keyframe(
            atMs = o.long("atMs", 0L),
            values = read,
            easing = o.enum("easing", KeyEasing.SMOOTH),
            curve = o.objOrNull("curve", null, ::easeCurve),
        )
    }

    fun keyframes(o: Json.Obj): Keyframes =
        Keyframes(o.list("keyframes", emptyList()) { (it as? Json.Obj)?.let(::keyframe) }.sortedBy { it.atMs })

    fun effects(o: Json.Obj): Effects {
        val d = Effects.NONE
        return Effects(
            rotation = o.enum("rotation", d.rotation),
            flipHorizontal = o.bool("flipHorizontal", d.flipHorizontal),
            flipVertical = o.bool("flipVertical", d.flipVertical),
            crop = o.objOrNull("crop", d.crop, ::cropRect),
            color = o.obj("color")?.let(::colorSettings) ?: d.color,
            speed = o.float("speed", d.speed),
            reversed = o.bool("reversed", d.reversed),
            fadeInSeconds = o.float("fadeInSeconds", d.fadeInSeconds),
            fadeOutSeconds = o.float("fadeOutSeconds", d.fadeOutSeconds),
            volume = o.float("volume", d.volume),
            muted = o.bool("muted", d.muted),
            audioDenoise = o.float("audioDenoise", d.audioDenoise),
            motion = o.objOrNull("motion", d.motion, ::stillMotion),
            key = o.objOrNull("key", d.key, ::colorKey),
            zoom = o.float("zoom", d.zoom),
            panX = o.float("panX", d.panX),
            panY = o.float("panY", d.panY),
            keyframes = keyframes(o),
            speedCurve = o.obj("speedCurve")?.let(::speedCurve),
            keepPitch = o.bool("keepPitch", d.keepPitch),
            transition = o.objOrNull("transition", d.transition) { t ->
                Transition(kind = t.enum("kind", TransitionKind.FADE), durationMs = t.long("durationMs", Transition.DEFAULT_MS))
            },
            hdrLook = o.doubleOrNull("hdrLook", d.hdrLook?.toDouble())?.toFloat(),
            placement = o.objOrNull("placement", d.placement) { p ->
                val n = VideoPlacement()
                VideoPlacement(
                    centerX = p.float("centerX", n.centerX),
                    centerY = p.float("centerY", n.centerY),
                    scale = p.float("scale", n.scale),
                    rotationDegrees = p.float("rotationDegrees", n.rotationDegrees),
                )
            },
        )
    }

    fun write(c: CropRect): Json.Obj = jsonObject {
        put("left", c.left)
        put("top", c.top)
        put("width", c.width)
        put("height", c.height)
    }

    fun cropRect(o: Json.Obj): CropRect {
        val d = CropRect()
        return CropRect(
            left = o.float("left", d.left),
            top = o.float("top", d.top),
            width = o.float("width", d.width),
            height = o.float("height", d.height),
        )
    }

    fun write(l: OverlayLook): Json.Obj = jsonObject {
        put("crop", l.crop, ::write)
        put("shape", l.shape.name)
        put("cornerRadius", l.cornerRadius)
        put("blur", l.blur)
        put("borderWidth", l.borderWidth)
        put("borderColor", l.borderColor)
        put("shadowOpacity", l.shadowOpacity)
        put("shadowBlur", l.shadowBlur)
        put("shadowX", l.shadowX)
        put("shadowY", l.shadowY)
        put("shadowColor", l.shadowColor)
    }

    fun overlayLook(o: Json.Obj): OverlayLook {
        val d = OverlayLook()
        return OverlayLook(
            crop = o.objOrNull("crop", d.crop, ::cropRect),
            shape = o.enum("shape", d.shape),
            cornerRadius = o.float("cornerRadius", d.cornerRadius),
            blur = o.float("blur", d.blur),
            borderWidth = o.float("borderWidth", d.borderWidth),
            borderColor = o.int("borderColor", d.borderColor),
            shadowOpacity = o.float("shadowOpacity", d.shadowOpacity),
            shadowBlur = o.float("shadowBlur", d.shadowBlur),
            shadowX = o.float("shadowX", d.shadowX),
            shadowY = o.float("shadowY", d.shadowY),
            shadowColor = o.int("shadowColor", d.shadowColor),
        )
    }

    fun write(c: ColorSettings): Json.Obj = jsonObject {
        put("brightness", c.brightness)
        put("contrast", c.contrast)
        put("saturation", c.saturation)
        put("sharpness", c.sharpness)
        put("gaussianBlur", c.gaussianBlur)
        put("vignette", c.vignette)
        put("filmGrain", c.filmGrain)
        put("denoise", c.denoise)
    }

    fun colorSettings(o: Json.Obj): ColorSettings {
        val d = ColorSettings()
        return ColorSettings(
            brightness = o.float("brightness", d.brightness),
            contrast = o.float("contrast", d.contrast),
            saturation = o.float("saturation", d.saturation),
            sharpness = o.float("sharpness", d.sharpness),
            gaussianBlur = o.float("gaussianBlur", d.gaussianBlur),
            vignette = o.bool("vignette", d.vignette),
            filmGrain = o.float("filmGrain", d.filmGrain),
            denoise = o.float("denoise", d.denoise),
        )
    }

    fun write(m: StillMotion): Json.Obj = jsonObject {
        put("kind", m.kind)
        put("amount", m.amount)
    }

    fun stillMotion(o: Json.Obj): StillMotion? {
        val kindName = o.stringOrNull("kind") ?: return null
        val kind = MotionKind.entries.firstOrNull { it.name == kindName } ?: return null
        return StillMotion(kind, o.float("amount", StillMotion.DEFAULT_AMOUNT))
    }

    fun write(k: ColorKey): Json.Obj = jsonObject {
        put("mode", k.mode)
        put("color", k.color)
        put("similarity", k.similarity)
        put("blend", k.blend)
        put("spill", k.spill)
        put("background", k.background)
        put("backgroundOpacity", k.backgroundOpacity)
        put("mask", k.mask)
    }

    fun colorKey(o: Json.Obj): ColorKey {
        val d = ColorKey()
        return ColorKey(
            mode = o.enum("mode", d.mode),
            color = o.int("color", d.color),
            similarity = o.float("similarity", d.similarity),
            blend = o.float("blend", d.blend),
            spill = o.float("spill", d.spill),
            background = o.intOrNull("background", d.background),
            backgroundOpacity = o.float("backgroundOpacity", d.backgroundOpacity),
            mask = o.stringOrNull("mask", d.mask),
        )
    }

    // -----------------------------------------------------------------
    // Timeline
    // -----------------------------------------------------------------

    fun write(t: Timeline): Json.Obj = jsonObject {
        putList("clips", t.clips) { write(it) }
    }

    fun timeline(o: Json.Obj): Timeline = Timeline(o.list("clips", emptyList()) { (it as? Json.Obj)?.let(::clip) })

    fun write(c: Clip): Json.Obj = jsonObject {
        put("id", c.id)
        put("sourceId", c.sourceId)
        put("startMs", c.startMs)
        put("endMs", c.endMs)
        put("effects", write(c.effects))
    }

    fun clip(o: Json.Obj): Clip? {
        val id = o.stringOrNull("id") ?: return null
        val sourceId = o.stringOrNull("sourceId") ?: return null
        return Clip(
            id = id,
            sourceId = sourceId,
            startMs = o.long("startMs", 0L),
            endMs = o.longOrNull("endMs"),
            effects = o.obj("effects")?.let(::effects) ?: Effects.NONE,
        )
    }

    fun write(s: SoundSpan): Json.Obj = jsonObject {
        put("startMs", s.startMs)
        put("trimStartMs", s.trimStartMs)
        put("trimEndMs", s.trimEndMs)
        put("fileDurationMs", s.fileDurationMs)
        put("speed", s.speed)
        put("repeats", s.repeats)
    }

    fun soundSpan(o: Json.Obj): SoundSpan? {
        val trimEnd = o.longOrNull("trimEndMs") ?: return null
        return SoundSpan(
            startMs = o.long("startMs", 0L),
            trimStartMs = o.long("trimStartMs", 0L),
            trimEndMs = trimEnd,
            fileDurationMs = o.long("fileDurationMs", 0L),
            speed = o.float("speed", 1f),
            repeats = o.int("repeats", 1),
        )
    }

    // -----------------------------------------------------------------
    // Files, captions, tags
    // -----------------------------------------------------------------

    fun write(m: MediaInfo): Json.Obj = jsonObject {
        put("durationMs", m.durationMs)
        put("isVideo", m.isVideo)
        put("width", m.width)
        put("height", m.height)
        put("fileSizeBytes", m.fileSizeBytes)
        put("hasAudio", m.hasAudio)
        put("isStill", m.isStill)
        put("hdr", m.hdr)
        put("frameRate", m.frameRate)
    }

    /** The formatted fields are derived again, as the probe derives them. */
    fun mediaInfo(o: Json.Obj): MediaInfo? {
        val duration = o.longOrNull("durationMs") ?: return null
        return MediaInfo.of(
            durationMs = duration,
            isVideo = o.bool("isVideo", false),
            width = o.int("width", 0),
            height = o.int("height", 0),
            fileSizeBytes = o.long("fileSizeBytes", 0L),
            hasAudio = o.bool("hasAudio", true),
            isStill = o.bool("isStill", false),
            hdr = o.enum("hdr", VideoHdr.NONE),
            frameRate = o.float("frameRate", 0f),
        )
    }

    fun write(t: TextOverlay): Json.Obj = jsonObject {
        put("id", t.id)
        put("text", t.text)
        put("x", t.x)
        put("y", t.y)
        put("size", t.size)
        put("color", t.color)
        put("isBold", t.isBold)
        put("clipId", t.clipId)
        put("startMs", t.startMs)
        put("endMs", t.endMs)
        put("opacity", t.opacity)
        put("fadeInSeconds", t.fadeInSeconds)
        put("fadeOutSeconds", t.fadeOutSeconds)
        put("font", t.font)
        put("layer", t.layer)
        putKeyframes(t.keyframes)
    }

    fun textOverlay(o: Json.Obj): TextOverlay? {
        val id = o.stringOrNull("id") ?: return null
        val d = TextOverlay(id = id)
        return TextOverlay(
            id = id,
            text = o.string("text", d.text),
            x = o.float("x", d.x),
            y = o.float("y", d.y),
            size = o.float("size", d.size),
            color = o.long("color", d.color),
            isBold = o.bool("isBold", d.isBold),
            clipId = o.stringOrNull("clipId", d.clipId),
            startMs = o.long("startMs", d.startMs),
            endMs = o.longOrNull("endMs", d.endMs),
            opacity = o.float("opacity", d.opacity),
            fadeInSeconds = o.float("fadeInSeconds", d.fadeInSeconds),
            fadeOutSeconds = o.float("fadeOutSeconds", d.fadeOutSeconds),
            font = o.enum("font", d.font),
            keyframes = keyframes(o),
            layer = o.int("layer", d.layer),
        )
    }

    fun write(t: MetadataTags): Json.Obj = jsonObject {
        put("title", t.title)
        put("artist", t.artist)
        put("album", t.album)
        put("year", t.year)
        put("comment", t.comment)
        put("genre", t.genre)
        put("copyright", t.copyright)
        put("fromSource", t.fromSource)
    }

    fun metadataTags(o: Json.Obj): MetadataTags {
        val d = MetadataTags()
        return MetadataTags(
            title = o.string("title", d.title),
            artist = o.string("artist", d.artist),
            album = o.string("album", d.album),
            year = o.string("year", d.year),
            comment = o.string("comment", d.comment),
            genre = o.string("genre", d.genre),
            copyright = o.string("copyright", d.copyright),
            fromSource = o.bool("fromSource", d.fromSource),
        )
    }

    // -----------------------------------------------------------------
    // Sound
    // -----------------------------------------------------------------

    fun write(a: AudioEffects): Json.Obj = jsonObject {
        putList("equalizer", a.equalizer) { Json.of(it) }
        put("lowCutHz", a.lowCutHz)
        put("highCutHz", a.highCutHz)
        put("gate", a.gate) { g -> jsonObject { put("thresholdDb", g.thresholdDb); put("reductionDb", g.reductionDb) } }
        put("compressor", a.compressor) { c ->
            jsonObject {
                put("thresholdDb", c.thresholdDb)
                put("ratio", c.ratio)
                put("attackMs", c.attackMs)
                put("releaseMs", c.releaseMs)
                put("makeupDb", c.makeupDb)
            }
        }
        put("lofiBits", a.lofiBits)
        put("modulation", a.modulation) { m -> jsonObject { put("kind", m.kind); put("rateHz", m.rateHz); put("depth", m.depth) } }
        put("echo", a.echo) { e -> jsonObject { put("delayMs", e.delayMs); put("feedback", e.feedback) } }
        put("reverb", a.reverb) { r -> jsonObject { put("size", r.size); put("mix", r.mix) } }
        put("stereo", a.stereo) { s ->
            jsonObject {
                put("width", s.width)
                put("balance", s.balance)
                put("swapSides", s.swapSides)
                put("mono", s.mono)
            }
        }
        put("limiter", a.limiter) { l -> jsonObject { put("ceilingDb", l.ceilingDb) } }
        put("pitchSemitones", a.pitchSemitones)
    }

    fun audioEffects(o: Json.Obj): AudioEffects {
        val d = AudioEffects.NONE
        return AudioEffects(
            equalizer = o.list("equalizer", d.equalizer) { it.asDouble()?.toFloat() },
            lowCutHz = o.int("lowCutHz", d.lowCutHz),
            highCutHz = o.int("highCutHz", d.highCutHz),
            gate = o.objOrNull("gate", d.gate) { g ->
                val n = NoiseGate()
                NoiseGate(g.float("thresholdDb", n.thresholdDb), g.float("reductionDb", n.reductionDb))
            },
            compressor = o.objOrNull("compressor", d.compressor) { c ->
                val n = Compressor()
                Compressor(
                    thresholdDb = c.float("thresholdDb", n.thresholdDb),
                    ratio = c.float("ratio", n.ratio),
                    attackMs = c.float("attackMs", n.attackMs),
                    releaseMs = c.float("releaseMs", n.releaseMs),
                    makeupDb = c.float("makeupDb", n.makeupDb),
                )
            },
            lofiBits = o.int("lofiBits", d.lofiBits),
            modulation = o.objOrNull("modulation", d.modulation) { m ->
                val kind = m.stringOrNull("kind")?.let { k -> ModulationKind.entries.firstOrNull { it.name == k } }
                kind?.let { Modulation(it, m.float("rateHz", 1f), m.float("depth", 0.5f)) }
            },
            echo = o.objOrNull("echo", d.echo) { e ->
                val n = Echo()
                Echo(e.int("delayMs", n.delayMs), e.float("feedback", n.feedback))
            },
            reverb = o.objOrNull("reverb", d.reverb) { r ->
                val n = Reverb()
                Reverb(r.enum("size", n.size), r.float("mix", n.mix))
            },
            stereo = o.obj("stereo")?.let { s ->
                val n = StereoImage()
                StereoImage(
                    width = s.float("width", n.width),
                    balance = s.float("balance", n.balance),
                    swapSides = s.bool("swapSides", n.swapSides),
                    mono = s.bool("mono", n.mono),
                )
            } ?: d.stereo,
            limiter = o.objOrNull("limiter", d.limiter) { l -> Limiter(l.float("ceilingDb", Limiter().ceilingDb)) },
            pitchSemitones = o.float("pitchSemitones", d.pitchSemitones),
        )
    }

    fun write(a: AudioEncoding): Json.Obj = jsonObject {
        put("bitrateKbps", a.bitrateKbps)
        put("sampleRate", a.sampleRate)
        put("bitDepth", a.bitDepth)
    }

    fun audioEncoding(o: Json.Obj): AudioEncoding {
        val d = AudioEncoding()
        return AudioEncoding(
            bitrateKbps = o.int("bitrateKbps", d.bitrateKbps),
            sampleRate = o.int("sampleRate", d.sampleRate),
            bitDepth = o.int("bitDepth", d.bitDepth),
        )
    }

    // -----------------------------------------------------------------
    // GIF and pictures
    // -----------------------------------------------------------------

    fun write(g: GifSettings): Json.Obj = jsonObject {
        put("width", g.width)
        put("fps", g.fps)
        put("colors", g.colors)
        put("dither", g.dither)
        put("loops", g.loops)
        put("smaller", g.smaller)
    }

    fun gifSettings(o: Json.Obj): GifSettings {
        val d = GifSettings()
        return GifSettings(
            width = o.int("width", d.width),
            fps = o.int("fps", d.fps),
            colors = o.int("colors", d.colors),
            dither = o.enum("dither", d.dither),
            loops = o.int("loops", d.loops),
            smaller = o.bool("smaller", d.smaller),
        )
    }

    fun write(e: ImageEdit): Json.Obj = jsonObject {
        put("frameMs", e.frameMs)
        put("perspectiveH", e.perspectiveH)
        put("perspectiveV", e.perspectiveV)
        put("straightenDegrees", e.straightenDegrees)
        put("tone", write(e.tone))
        put("filter", e.filter)
        put("filterAmount", e.filterAmount)
        put("effect", e.effect)
        put("effectAmount", e.effectAmount)
        put("duotoneDark", e.duotoneDark)
        put("duotoneLight", e.duotoneLight)
        put("sharpen", e.sharpen)
        put("vignette", e.vignette)
        put("grain", e.grain)
        put("border", e.border) { b -> jsonObject { put("widthFraction", b.widthFraction); put("color", b.color) } }
        putList("strokes", e.strokes) { write(it) }
        put("output", jsonObject { put("maxSide", e.output.maxSide); put("quality", e.output.quality) })
        put("key", e.key, ::write)
        put("hdr", e.hdr) { h ->
            jsonObject {
                put("mode", h.mode)
                put("intensity", h.intensity)
                put("area", h.area)
                put("mask", h.mask)
                put("brush", h.brush)
            }
        }
    }

    fun imageEdit(o: Json.Obj): ImageEdit {
        val d = ImageEdit()
        return ImageEdit(
            frameMs = o.long("frameMs", d.frameMs),
            perspectiveH = o.float("perspectiveH", d.perspectiveH),
            perspectiveV = o.float("perspectiveV", d.perspectiveV),
            straightenDegrees = o.float("straightenDegrees", d.straightenDegrees),
            tone = o.obj("tone")?.let(::imageTone) ?: d.tone,
            filter = o.enum("filter", d.filter),
            filterAmount = o.float("filterAmount", d.filterAmount),
            effect = o.enum("effect", d.effect),
            effectAmount = o.float("effectAmount", d.effectAmount),
            duotoneDark = o.long("duotoneDark", d.duotoneDark),
            duotoneLight = o.long("duotoneLight", d.duotoneLight),
            sharpen = o.float("sharpen", d.sharpen),
            vignette = o.float("vignette", d.vignette),
            grain = o.float("grain", d.grain),
            border = o.objOrNull("border", d.border) { b ->
                val n = ImageBorder()
                ImageBorder(b.float("widthFraction", n.widthFraction), b.long("color", n.color))
            },
            strokes = o.list("strokes", d.strokes) { (it as? Json.Obj)?.let(::stroke) },
            output = o.obj("output")?.let { out ->
                val n = ImageOutput()
                ImageOutput(out.int("maxSide", n.maxSide), out.float("quality", n.quality))
            } ?: d.output,
            key = o.objOrNull("key", d.key, ::colorKey),
            hdr = o.objOrNull("hdr", d.hdr) { h ->
                val n = ImageHdr()
                ImageHdr(
                    mode = h.enum("mode", n.mode),
                    intensity = h.float("intensity", n.intensity),
                    area = h.enum("area", n.area),
                    mask = h.stringOrNull("mask", n.mask),
                    brush = h.stringOrNull("brush", n.brush),
                )
            },
        )
    }

    fun write(t: ImageTone): Json.Obj = jsonObject {
        put("exposure", t.exposure)
        put("brightness", t.brightness)
        put("contrast", t.contrast)
        put("highlights", t.highlights)
        put("shadows", t.shadows)
        put("saturation", t.saturation)
        put("warmth", t.warmth)
        put("tint", t.tint)
        put("hue", t.hue)
        put("fade", t.fade)
    }

    fun imageTone(o: Json.Obj): ImageTone {
        val d = ImageTone()
        return ImageTone(
            exposure = o.float("exposure", d.exposure),
            brightness = o.float("brightness", d.brightness),
            contrast = o.float("contrast", d.contrast),
            highlights = o.float("highlights", d.highlights),
            shadows = o.float("shadows", d.shadows),
            saturation = o.float("saturation", d.saturation),
            warmth = o.float("warmth", d.warmth),
            tint = o.float("tint", d.tint),
            hue = o.float("hue", d.hue),
            fade = o.float("fade", d.fade),
        )
    }

    fun write(s: Stroke): Json.Obj = jsonObject {
        putList("points", s.points) { Json.of(it) }
        put("color", s.color)
        put("width", s.width)
        put("opacity", s.opacity)
        put("shape", s.shape)
        put("eraser", s.eraser)
    }

    fun stroke(o: Json.Obj): Stroke? {
        val points = (o["points"] as? Json.Arr)?.items?.map { it.asDouble()?.toFloat() ?: return null } ?: return null
        val d = Stroke(points)
        return Stroke(
            points = points,
            color = o.long("color", d.color),
            width = o.float("width", d.width),
            opacity = o.float("opacity", d.opacity),
            shape = o.enum("shape", d.shape),
            eraser = o.bool("eraser", d.eraser),
        )
    }

    fun write(s: SubtitleStyle): Json.Obj = jsonObject {
        put("size", s.size)
        put("x", s.x)
        put("y", s.y)
        put("textColor", s.textColor)
        put("highlightColor", s.highlightColor)
        put("look", s.look)
        put("bold", s.bold)
        put("uppercase", s.uppercase)
        put("highlight", s.highlight)
        put("maxLines", s.maxLines)
        put("font", s.font)
    }

    fun subtitleStyle(o: Json.Obj): SubtitleStyle {
        val d = SubtitleStyle()
        return SubtitleStyle(
            size = o.float("size", d.size),
            x = o.float("x", d.x),
            y = o.float("y", d.y),
            textColor = o.long("textColor", d.textColor),
            highlightColor = o.long("highlightColor", d.highlightColor),
            look = o.enum("look", d.look),
            bold = o.bool("bold", d.bold),
            uppercase = o.bool("uppercase", d.uppercase),
            highlight = o.enum("highlight", d.highlight),
            maxLines = o.int("maxLines", d.maxLines),
            font = o.enum("font", d.font),
        ).sane()
    }

    /**
     * Words heard in a file: their times as one line of numbers (start and end
     * of each, in milliseconds) and their text as one string, a space between
     * two words (a word never holds one).
     */
    fun writeWords(words: List<SpokenWord>): Json.Obj = jsonObject {
        putList("times", words.flatMap { listOf(it.startMs, it.endMs) }) { Json.of(it) }
        put("text", words.joinToString(" ") { it.text })
    }

    /** The words written by [writeWords], or null when they do not line up. */
    fun spokenWords(o: Json.Obj): List<SpokenWord>? {
        val times = (o["times"] as? Json.Arr)?.items?.map { it.asLong() ?: return null } ?: return null
        val text = o.string("text", "")
        val texts = if (text.isEmpty()) emptyList() else text.split(' ')
        if (times.size != texts.size * 2) return null
        return texts.mapIndexed { i, word -> SpokenWord(times[i * 2], times[i * 2 + 1], word) }
    }
}
