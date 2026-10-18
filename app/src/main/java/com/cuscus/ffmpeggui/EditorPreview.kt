@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap
import com.cuscus.ffmpeggui.core.model.GifSettings
import android.view.TextureView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.cuscus.ffmpeggui.core.model.ImagePlacement
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import com.cuscus.ffmpeggui.preview.outputEnvelope
import com.cuscus.ffmpeggui.preview.outputClock
import com.cuscus.ffmpeggui.preview.Waveform
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.audio.stages
import com.cuscus.ffmpeggui.core.audio.pitchFactor
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.StillMotion
import com.cuscus.ffmpeggui.core.model.ColorSettings
import kotlin.math.roundToInt
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.FormatCategory
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.model.VideoPlacement
import com.cuscus.ffmpeggui.core.model.stackOrder
import com.cuscus.ffmpeggui.core.preview.SnapBox
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.subtitles.SpokenWord
import com.cuscus.ffmpeggui.core.subtitles.SubtitleLayout
import com.cuscus.ffmpeggui.core.subtitles.SubtitleStyle
import com.cuscus.ffmpeggui.subtitles.SubtitleFont
import com.cuscus.ffmpeggui.subtitles.SubtitleFonts
import com.cuscus.ffmpeggui.core.preview.applyColorMatrix
import com.cuscus.ffmpeggui.core.preview.combineColorMatrices
import com.cuscus.ffmpeggui.core.preview.croppedRatio
import com.cuscus.ffmpeggui.core.preview.decibelsToVolume
import com.cuscus.ffmpeggui.core.preview.eqMatrix
import com.cuscus.ffmpeggui.core.preview.frameRatio
import com.cuscus.ffmpeggui.core.preview.textAt
import com.cuscus.ffmpeggui.core.preview.turnedRatio
import com.cuscus.ffmpeggui.core.time.parseTimecodeMs
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.playback.NowPlaying
import com.cuscus.ffmpeggui.playback.PreviewMediaSession
import com.cuscus.ffmpeggui.preview.EditorPreviewPlayer
import com.cuscus.ffmpeggui.preview.PictureCache
import com.cuscus.ffmpeggui.preview.PreviewSnapshot
import com.cuscus.ffmpeggui.preview.followingClips
import com.cuscus.ffmpeggui.preview.previewClipLevels
import com.cuscus.ffmpeggui.preview.previewClips
import com.cuscus.ffmpeggui.preview.ofSounds
import com.cuscus.ffmpeggui.preview.previewSounds
import com.cuscus.ffmpeggui.preview.setColorMatrix
import com.cuscus.ffmpeggui.preview.FrameKeyer
import com.cuscus.ffmpeggui.preview.applyColorMatrix
import com.cuscus.ffmpeggui.preview.gpuKeying
import com.cuscus.ffmpeggui.preview.keyedBitmap
import com.cuscus.ffmpeggui.preview.overVideo
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.preview.smallPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/*
 * Live preview of the edit.
 *
 * Nothing here is rendered by FFmpeg: every setting is reproduced on the fly
 * with the same math the export uses (see :core, package `preview`), so what is
 * on screen is what the conversion will produce. The layers are nested in the
 * order of the export filter graph:
 *
 *   clip on screen: picture (turn, crop, colour) -> its captions -> its fade -> its pictures
 *     -> fitted into the project frame (the first clip's file)
 *   whole video: turn, crop -> captions -> subtitles -> fade -> pictures -> canvas
 *
 * The colour of the whole video is combined with the clip's on the picture and
 * applied to the clip's own captions and pictures, which it covers in the export.
 *
 * Settings the export ignores are ignored here as well: with stream copy or
 * frame extraction the untouched source is shown.
 */

/** How often the player is polled and the fades / subtitles are refreshed. */
private const val TICK_MS = 33L

/** Ticks closer together than this (a screen faster than 60 Hz) are skipped. */
private const val MIN_TICK_NANOS = 15_000_000L

/** libass defaults for plain subtitles: font 16 and bottom margin 10 on a 288-line canvas. */
private const val SUBTITLE_FONT_FRACTION = 16f / 288f
private const val SUBTITLE_MARGIN_FRACTION = 10f / 288f

/** Shape assumed for a file whose size is not known yet. */
private const val DEFAULT_RATIO = 16f / 9f

/** Longest side of the cover read for the audio preview's badge. */
private const val AUDIO_BADGE_PX = 256

/**
 * A request to pause on a position of the playing sequence. [token] makes two
 * requests for the same position different, so that the second one is honoured
 * too (the player may have moved on in between).
 */
data class PreviewSeek(val positionMs: Long, val token: Int)

/**
 * Playback state shared by the preview and the controls around it (play button,
 * clock, timeline). The preview writes [snapshot] and [playheadMs] on every tick;
 * read them where they are drawn so that a moving playhead recomposes little.
 */
@Stable
class PreviewPlayback {
    /** Whether the preview plays; set it to start or pause. */
    val isPlaying = mutableStateOf(true)
    val snapshot = mutableStateOf(PreviewSnapshot())
    /** Position inside the playing clips (see [PreviewSnapshot.mediaMs]). */
    val playheadMs = mutableLongStateOf(0L)
}

@Composable
fun PreviewContainer(
    /** For a GIF, its settings: the preview plays the plain, smooth video, without sound (a GIF has none). */
    gif: GifSettings? = null,
    /** The clips that play, in order, with their own effects: the timeline with the whole-video trim applied. */
    clips: List<Clip>,
    sources: List<EditorSource>,
    /** Play state, clock and playhead, shared with the controls around the preview. */
    playback: PreviewPlayback,
    format: OutputFormat,
    /** Effects of the whole video. */
    wholeEffects: Effects,
    canvas: CanvasRatio,
    normalizeAudio: Boolean,
    normalizationGainDb: Float,
    /** Clip being edited (its crop, captions and pictures can be adjusted); null = the whole video. */
    editClipId: String?,
    /** Shows the frame being edited uncropped, with the crop rectangle on top. */
    showCropOverlay: Boolean,
    cropAspect: CropAspect,
    onCropChange: (CropRect) -> Unit,
    /**
     * The video is picked up (tapped, or the Canvas tool is open): it is outlined
     * and moved on its canvas by hand, each move handed to [onPlacementChange].
     */
    placementEditable: Boolean = false,
    onPlacementChange: (VideoPlacement) -> Unit = {},
    /** The video was tapped (true) or the canvas around it (false): it is picked up or put down. */
    onSelectVideo: ((Boolean) -> Unit)? = null,
    /** The HDR look's intensity while its zones are shown, tinted, over the picture; null when they are not. */
    hdrZones: Float? = null,
    /** When set, playback pauses on that position of [clips] (used while scrubbing). */
    previewSeek: PreviewSeek?,
    /** Sounds placed on the video, on the output clock. */
    audioClips: List<AudioClip>,
    /** Every caption, of the whole video and of the clips. */
    textOverlays: List<TextOverlay>,
    imageOverlays: List<ImageOverlay>,
    /** Lines of the subtitle file, on the clock of the project's main file. */
    subtitleCues: List<SubtitleCue>,
    /** Words heard in each file, by source id, on that file's own clock: the spoken subtitles. */
    spokenWords: Map<String, List<SpokenWord>> = emptyMap(),
    /** How spoken subtitles and SubRip files look. */
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** False for a SubStation Alpha file: it keeps its own look, drawn plainly here. */
    subtitleFileStyled: Boolean = true,
    /** The fonts the export burns subtitles with. */
    subtitleFonts: SubtitleFonts? = null,
    /** The subtitles can be dragged on the picture (their panel is open). */
    subtitlesEditable: Boolean = false,
    /** Changes how subtitles look; the key groups a drag into one undo step. */
    onSubtitleStyle: (String?, (SubtitleStyle) -> SubtitleStyle) -> Unit = { _, _ -> },
    useStreamCopy: Boolean,
    frameExtractionMode: FrameExtractionMode,
    frameExtractionTimecode: String,
    /** The text or picture selected on the timeline: it can be dragged and resized on the picture. */
    selectedOverlayId: String?,
    /** Changes a caption; the key groups a drag into one undo step. */
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    /** A text or picture was touched on the preview: it gets the focus in the editor. */
    onSelectOverlay: (String) -> Unit = {},
    /** Where the playing clips start on the timeline (for the audio preview's outline). */
    trimStartMs: Long = 0L,
    /** What is done to the final sound, heard as it plays. */
    audioEffects: AudioEffects = AudioEffects.NONE,
    /** A file's measured outline, null while it is being read (for the audio preview). */
    waveformOf: (Uri) -> Waveform? = { null },
    /** For an audio file: what the sound view shows around the waveform. */
    audioCard: AudioCard? = null,
    /**
     * What the media notification shows while the preview plays, also in the
     * background; null for a preview that has no sound to keep playing (a GIF).
     */
    nowPlaying: NowPlaying? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // -- What the export will really do with the current settings -----------------
    val extractingFrames = frameExtractionMode != FrameExtractionMode.DISABLED
    val filtersApply = !useStreamCopy && !extractingFrames
    val outputHasAudio = format.hasAudio && !extractingFrames

    val whole = if (filtersApply) wholeEffects else Effects.NONE
    val shownClips = remember(clips, filtersApply) {
        if (filtersApply) clips else clips.map { it.copy(effects = Effects.NONE) }
    }
    val shownCanvas = if (filtersApply) canvas else CanvasRatio.FIT
    val shownCues = if (filtersApply) subtitleCues else emptyList()
    // The export mixes placed sounds in only where it applies filters and keeps sound.
    val soundsHeard = filtersApply && outputHasAudio

    val sourceGain = when {
        !outputHasAudio -> 0f
        useStreamCopy -> 1f
        // Audio-only formats always keep the sound, as the export does.
        whole.muted && format.category != FormatCategory.AUDIO -> 0f
        // loudnorm sets the final level by itself, whatever the volume before it.
        normalizeAudio -> decibelsToVolume(normalizationGainDb)
        else -> whole.volume
    }
    val soundGain = if (soundsHeard) 1f else 0f

    // -- Footage and clocks ---------------------------------------------------------
    val frameTimecodeMs = remember(frameExtractionTimecode) { parseTimecodeMs(frameExtractionTimecode) }
    val playlist = remember(clips, sources) { previewClips(clips, sources) }
    val clipLevels = remember(shownClips, sources) { previewClipLevels(shownClips, sources) }
    // The subtitle lines follow their footage through every cut, as in the export.
    val timelineCues = remember(shownCues, shownClips, whole.reversed) { shownCues.followingClips(shownClips, whole.reversed) }
    // So do the words heard, each one with its own clip.
    val spoken = filtersApply && subtitleCues.isEmpty() && spokenWords.values.any { it.isNotEmpty() }
    // And those of the sounds placed on the video, where each one plays.
    val timelineWords = remember(spoken, spokenWords, shownClips, whole.reversed, whole.effectiveSpeed, audioClips) {
        if (spoken) {
            (spokenWords.followingClips(shownClips, whole.reversed) + spokenWords.ofSounds(audioClips, whole.effectiveSpeed)).sortedBy { it.startMs }
        } else {
            emptyList()
        }
    }
    // The font the export draws with, read once (it is a file).
    val subtitleFont by produceState<SubtitleFont?>(null, subtitleFonts, subtitleStyle.font, subtitleStyle.bold) {
        value = subtitleFonts?.let { fonts -> withContext(Dispatchers.IO) { fonts.of(subtitleStyle.font, subtitleStyle.bold) } }
    }
    val styledSubtitles = spoken || (subtitleFileStyled && timelineCues.isNotEmpty())
    val sounds = remember(audioClips, imageOverlays) { previewSounds(audioClips, imageOverlays) }

    // -- Player -----------------------------------------------------------------------
    val player = remember { EditorPreviewPlayer(context) }
    val pictures = remember { PictureCache(context) }
    var isPlaying by playback.isPlaying
    val snapshot = playback.snapshot
    var decodedRatio by remember { mutableFloatStateOf(0f) }
    var videoHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        player.onVideoSize = { width, height ->
            videoHeight = height
            decodedRatio = width.toFloat() / height.toFloat()
        }
        // A call, another app or unplugged headphones paused it.
        player.onInterrupted = { isPlaying = false }
        onDispose { player.release() }
    }

    LaunchedEffect(playlist, clipLevels) { player.setSequence(playlist, clipLevels) }
    LaunchedEffect(sounds) { player.setSounds(sounds) }
    LaunchedEffect(whole.speed, whole.reversed, whole.keepPitch) { player.setPlayback(whole.effectiveSpeed, whole.reversed, whole.keepPitch) }
    LaunchedEffect(sourceGain, soundGain, whole.fadeInSeconds, whole.fadeOutSeconds) {
        player.setLevels(sourceGain, soundGain, whole.fadeInSeconds, whole.fadeOutSeconds)
    }
    // Scrubbing the timeline or dragging a trim handle shows the frame under it.
    LaunchedEffect(previewSeek) {
        previewSeek?.let {
            isPlaying = false
            player.showMediaFrame(it.positionMs)
        }
    }
    // Single-frame extraction shows exactly the frame that will be saved.
    LaunchedEffect(frameExtractionMode, frameTimecodeMs) {
        if (frameExtractionMode == FrameExtractionMode.SINGLE) {
            isPlaying = false
            player.showMediaFrame(frameTimecodeMs)
        }
    }
    LaunchedEffect(isPlaying) { if (isPlaying) player.play() else player.pause() }
    // The final sound's effects, heard on every player; none where the export applies no filter.
    val finalSound = if (filtersApply && outputHasAudio) audioEffects else AudioEffects.NONE
    LaunchedEffect(finalSound) {
        player.setAudioEffects(finalSound.stages(), pitchFactor(finalSound.pitchSemitones.coerceIn(-12f, 12f)).toFloat())
    }
    LaunchedEffect(format.isAudioOnly) { player.setPictureEnabled(!format.isAudioOnly) }
    // Only a preview that can be heard takes the audio focus from other apps.
    val audible = gif == null && (sourceGain > 0f || soundGain > 0f)
    LaunchedEffect(audible) { player.setAudible(audible) }
    LaunchedEffect(player) {
        var lastTick = 0L
        while (true) {
            // On every frame the screen draws, so that the playhead, the waveform and the
            // timeline glide instead of moving in steps; when nothing is drawn (the app in
            // the background) still about 30 times a second.
            withTimeoutOrNull(TICK_MS) { withFrameNanos { } }
            val now = System.nanoTime()
            if (now - lastTick < MIN_TICK_NANOS) continue
            lastTick = now
            val current = player.tick()
            snapshot.value = current
            playback.playheadMs.longValue = current.mediaMs
        }
    }
    // The media notification, through which the preview keeps playing in the background.
    if (nowPlaying != null) {
        PreviewMediaSession(
            isPlaying = playback.isPlaying,
            snapshot = { snapshot.value },
            nowPlaying = nowPlaying,
            onSeek = { outputMs -> player.seekToOutput(outputMs) },
        )
    }

    // -- An audio file: the sound instead of the picture ---------------------------------
    if (format.isAudioOnly && audioCard != null) {
        val clock = remember(shownClips, whole, trimStartMs) { outputClock(shownClips, whole, trimStartMs) }
        val currentWaveformOf by rememberUpdatedState(waveformOf)
        val envelope by remember(shownClips, sources, clock, whole, sourceGain, sounds, soundGain) {
            derivedStateOf {
                outputEnvelope(shownClips, sources, clock, trimStartMs, whole, sourceGain, sounds, soundGain, currentWaveformOf)
            }
        }
        AudioPreview(
            title = audioCard.title,
            subtitle = audioCard.subtitle,
            formatLabel = audioCard.formatLabel,
            envelope = envelope,
            outputDurationMs = clock.outputDurationMs,
            positionMs = { snapshot.value.outputMs },
            isPlaying = isPlaying,
            meter = player.liveAudio.meter,
            partsMs = remember(audioCard.split, clock) { splitPoints(audioCard.split, clock.outputDurationMs) },
            activeEffects = audioCard.activeEffects,
            onSeek = { outputMs -> player.seekToOutput(outputMs) },
            modifier = modifier,
            cover = rememberCoverPicture(audioCard.cover, AUDIO_BADGE_PX),
        )
        return
    }

    // -- The clip on screen -------------------------------------------------------------
    // While a clip is being cropped it is the one shown, paused on its first frame;
    // a clip that does not play (trimmed away) cannot be cropped here.
    val editIndex = if (editClipId == null) -1 else shownClips.indexOfFirst { it.id == editClipId }
    val croppingClip = showCropOverlay && editIndex >= 0
    val croppingWhole = showCropOverlay && editClipId == null
    val cropOverlayShown = croppingClip || croppingWhole
    LaunchedEffect(croppingClip, editIndex) {
        if (croppingClip) {
            isPlaying = false
            player.showMediaFrame(shownClips.take(editIndex).sumOf { it.lengthMs })
        }
    }
    // A clip being cropped stays on screen.
    LaunchedEffect(croppingClip, isPlaying) { if (croppingClip && isPlaying) isPlaying = false }

    // Read through derivedStateOf so that only a change of clip recomposes, not every tick.
    val segment by remember { derivedStateOf { snapshot.value.segment } }
    val onScreen = (if (croppingClip) shownClips[editIndex] else shownClips.getOrNull(segment)) ?: shownClips.firstOrNull()
    val clipEffects = onScreen?.effects ?: Effects.NONE

    fun sourceRatio(clip: Clip?): Float {
        val known = clip?.let { c -> sources.firstOrNull { it.id == c.sourceId }?.frameRatio }
        return known ?: decodedRatio.takeIf { it > 0f } ?: DEFAULT_RATIO
    }
    // A still on screen is drawn here, over the video surface (see StillPicture).
    val onScreenSource = onScreen?.let { clip -> sources.firstOrNull { it.id == clip.sourceId } }
    val stillOnScreen = onScreenSource?.takeIf { it.isStill }
    // The decoder knows the clip on screen best; the probe is used for the others, and
    // while cropping (the crop tool computes its shapes from the probe too). A still is
    // not decoded by the player, which keeps the size of the last video it showed.
    val clipRatio = if (decodedRatio > 0f && !croppingClip && stillOnScreen == null) decodedRatio else sourceRatio(onScreen)
    // Every clip is fitted into the frame of the first clip's file, as the export does.
    val projectRatio = sourceRatio(shownClips.firstOrNull())

    // While cropping, the frame being cropped is shown whole: the clip on its own, or the
    // joined video without the crop and canvas of the whole video.
    val joinedRatio = if (croppingClip) turnedRatio(clipRatio, clipEffects.rotation) else projectRatio
    val wholeGeometry = if (croppingClip) Effects.NONE else whole
    val wholeCrop = if (croppingWhole) null else wholeGeometry.effectiveCrop
    val clipCrop = if (croppingClip) null else clipEffects.effectiveCrop
    val wholeTurned = turnedRatio(joinedRatio, wholeGeometry.rotation)
    val wholeRatio = croppedRatio(wholeTurned, wholeCrop)
    val clipShownRatio = croppedRatio(turnedRatio(clipRatio, clipEffects.rotation), clipCrop)
    val canvasRatio = if (cropOverlayShown) wholeRatio else shownCanvas.ratio ?: wholeRatio
    val shownCrop = if (croppingClip) clipEffects.crop else whole.crop
    // The video where it lies on its canvas; whole while it is cropped.
    val shownPlacement = if (cropOverlayShown) null else whole.placement?.takeUnless { it.isNeutral }
    // The video can be picked up with a tap, and moved once it is (not while cropping).
    val videoTappable = onSelectVideo != null && filtersApply && !cropOverlayShown
    val videoMovable = placementEditable && filtersApply && !cropOverlayShown
    val currentPutDown by rememberUpdatedState(onSelectVideo)
    // What lies on the canvas (the video and what is laid over it), to line up with.
    val wholeScene = rememberSnapScene()
    val currentHaptic = LocalHapticFeedback.current
    val currentWholeRatioForMove by rememberUpdatedState(wholeRatio)
    val currentOnPlacement by rememberUpdatedState(onPlacementChange)
    val currentMovedPlacement by rememberUpdatedState(whole.placement ?: VideoPlacement())

    val shownHeight = stillOnScreen?.info?.height ?: videoHeight
    val highDefinition = shownHeight == 0 || shownHeight >= 720
    val wholeColor = remember(whole.color, highDefinition) { whole.eqMatrix(highDefinition) }
    // Colour keys of the clip and of the whole video, over their backgrounds (black when
    // they have none). On the GPU they key the layers below; before Android 13, and under
    // a GIF's frames, the picture's pixels are keyed on the CPU instead.
    val clipKey = (if (croppingClip) null else clipEffects.key)?.overVideo()
    val wholeKey = (if (croppingClip) null else whole.key)?.overVideo()

    // Captions and pictures of the whole video show during their own windows of the output.
    val outputNow: () -> Long = remember(snapshot) { { snapshot.value.outputMs } }
    val clipId = onScreen?.id
    val clipTexts = if (filtersApply && clipId != null) textOverlays.filter { it.clipId == clipId } else emptyList()
    val clipImages = if (filtersApply && clipId != null) imageOverlays.filter { it.clipId == clipId } else emptyList()
    val wholeTexts = if (filtersApply) textOverlays.filter { it.clipId == null } else emptyList()

    // -- A transition -------------------------------------------------------------------
    // Read through derivedStateOf: only its start and end recompose, not every tick.
    val transitionOnScreen by remember { derivedStateOf { snapshot.value.transition } }
    val incomingIndex by remember { derivedStateOf { snapshot.value.incomingLoaded } }
    // The clip coming in, shown by the second player while the two overlap (never while cropping).
    val incomingClip = if (croppingClip) null else shownClips.getOrNull(incomingIndex)
    val incomingShown = incomingClip?.let { clip ->
        val effects = clip.effects
        val source = sources.firstOrNull { it.id == clip.sourceId }
        val crop = effects.effectiveCrop
        ShownClip(
            effects = effects,
            framing = effects,
            crop = crop,
            ratio = croppedRatio(turnedRatio(sourceRatio(clip), effects.rotation), crop),
            key = effects.key?.overVideo(),
            still = source?.takeIf { it.isStill },
            texts = if (filtersApply) textOverlays.filter { it.clipId == clip.id } else emptyList(),
            images = if (filtersApply) imageOverlays.filter { it.clipId == clip.id } else emptyList(),
            overlays = true,
        )
    }
    val wholeImages = if (filtersApply) imageOverlays.filter { it.clipId == null } else emptyList()

    // -- Layout -----------------------------------------------------------------------
    run {
        // Large rounded corners (Material 3 Expressive); square while cropping, so
        // that the crop handles reach the true corners. The change springs.
        val cornerRadius by animateDpAsState(
            targetValue = if (cropOverlayShown) 0.dp else 20.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "previewCorner",
        )

        // While cropping the frame steps back from the edges, so that its handles are easy to take.
        val cropInset by animateDpAsState(
            targetValue = if (cropOverlayShown) CROP_FRAME_INSET else 0.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "cropInset",
        )
        BoxWithConstraints(
            modifier = modifier.padding(cropInset.coerceAtLeast(0.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val parentRatio = maxWidth / maxHeight
            val frameModifier = if (canvasRatio > parentRatio) {
                Modifier.fillMaxWidth().aspectRatio(canvasRatio)
            } else {
                Modifier.fillMaxHeight().aspectRatio(canvasRatio)
            }

            // The output frame: the canvas, black around the picture.
            var frameWidth by remember { mutableFloatStateOf(0f) }
            var frameHeight by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = frameModifier
                    // The spring may overshoot below zero on its way to square corners.
                    .clip(RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)))
                    .background(Color.Black)
                    // The zones the HDR look brightens, over everything the export brightens.
                    .hdrZones(if (cropOverlayShown) null else hdrZones)
                    .onSizeChanged { size ->
                        frameWidth = size.width.toFloat()
                        frameHeight = size.height.toFloat()
                    }
                    // Picked up, a tap beside the video (what the video does not take) puts it down,
                    // and a drag anywhere on the canvas no caption or picture takes moves it (two
                    // fingers also resize and turn it); it sticks to the guides, its turn to whole steps.
                    .pointerInput(videoMovable) {
                        if (!videoMovable) return@pointerInput
                        detectTapGestures { currentPutDown?.invoke(false) }
                    }
                    .pointerInput(videoMovable) {
                        if (!videoMovable) return@pointerInput
                        var start = VideoPlacement()
                        detectTotalTransform(
                            onStart = {
                                start = currentMovedPlacement
                                currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onMove = { pan, zoom, rotation ->
                                val width = frameWidth
                                val height = frameHeight
                                if (width <= 0f || height <= 0f) return@detectTotalTransform
                                val scale = (start.scale * zoom).coerceIn(VideoPlacement.MIN_SCALE, VideoPlacement.MAX_SCALE)
                                val raw = start.rotationDegrees + rotation
                                val turn = if (kotlin.math.abs(rotation) > 0.5f) wholeScene.turn(raw, currentHaptic) else raw
                                val cx = start.centerX * width + pan.x
                                val cy = start.centerY * height + pan.y
                                val box = videoBox(width, height, currentWholeRatioForMove, cx / width, cy / height, scale, turn)
                                val snap = wholeScene.snap(VIDEO_SNAP_ID, box, width, height, snapThreshold(this), currentHaptic)
                                currentOnPlacement(
                                    VideoPlacement((cx + snap.dx) / width, (cy + snap.dy) / height, scale, turn).normalized(),
                                )
                            },
                            onEnd = {
                                wholeScene.clear()
                                currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                val keysOnGpu = gpuKeying
                val placementNow by rememberUpdatedState(shownPlacement)
                val haptic = LocalHapticFeedback.current
                val currentOnSelectVideo by rememberUpdatedState(onSelectVideo)
                val outline = MaterialTheme.colorScheme.primary
                // Where the video lies on the canvas, for what is laid over it to line up with.
                val currentWholeRatio by rememberUpdatedState(wholeRatio)
                DisposableEffect(wholeScene) {
                    wholeScene.register(VIDEO_SNAP_ID) {
                        val placed = currentMovedPlacement
                        if (frameWidth <= 0f || frameHeight <= 0f) {
                            null
                        } else {
                            videoBox(frameWidth, frameHeight, currentWholeRatio, placed.centerX, placed.centerY, placed.scale, placed.turn)
                        }
                    }
                    onDispose { wholeScene.unregister(VIDEO_SNAP_ID) }
                }

                // The whole video's picture, as cropped, where it lies on its canvas.
                Box(
                    modifier = Modifier
                        .aspectRatio(wholeRatio)
                        .graphicsLayer {
                            val placed = placementNow ?: return@graphicsLayer
                            translationX = (placed.centerX - 0.5f) * frameWidth
                            translationY = (placed.centerY - 0.5f) * frameHeight
                            scaleX = placed.scale
                            scaleY = placed.scale
                            rotationZ = placed.turn
                        }
                        // Its outline keeps one width, whatever its size.
                        .then(if (videoMovable) Modifier.border(2.dp / (shownPlacement?.scale ?: 1f), outline) else Modifier)
                        // A tap picks it up (moved then by the canvas around, below). Captions and
                        // pictures on it come first: they take their own touches.
                        .pointerInput(videoTappable) {
                            if (!videoTappable) return@pointerInput
                            detectTapGestures {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelectVideo?.invoke(true)
                            }
                        },
                ) {
                    Cropped(crop = wholeCrop, modifier = Modifier.fillMaxSize()) {
                        Turned(effects = wholeGeometry, modifier = Modifier.fillMaxSize()) {
                            // The joined video: every clip fitted into the project frame.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .colorKey(if (keysOnGpu) wholeKey else null)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                // The clip on screen; during a transition, with the clip coming in.
                                TransitionLayers(
                                    // A clip being cropped is shown alone.
                                    kind = if (croppingClip) null else transitionOnScreen,
                                    progress = { snapshot.value.transitionProgress },
                                    outgoing = {
                                        ClipPicture(
                                            shown = ShownClip(
                                                effects = clipEffects,
                                                framing = if (croppingClip) Effects.NONE else clipEffects,
                                                crop = clipCrop,
                                                ratio = clipShownRatio,
                                                key = clipKey,
                                                still = stillOnScreen,
                                                texts = clipTexts,
                                                images = clipImages,
                                                overlays = !croppingClip,
                                            ),
                                            player = player.videoPlayer,
                                            pictures = pictures,
                                            wholeColor = wholeColor,
                                            highDefinition = highDefinition,
                                            keysOnGpu = keysOnGpu,
                                            wholeKey = wholeKey,
                                            fileMs = { snapshot.value.clipFileMs },
                                            progress = { snapshot.value.clipProgress },
                                            playedMs = { snapshot.value.clipPlayedMs },
                                            fadeLevel = { snapshot.value.clipFadeLevel },
                                            selectedOverlayId = selectedOverlayId,
                                            onUpdateText = onUpdateText,
                                            onUpdateImage = onUpdateImage,
                                            onSelectOverlay = onSelectOverlay,
                                            playing = { playback.isPlaying.value },
                                        )
                                    },
                                    // Loaded a little before its transition, so that its first frame is ready.
                                    incoming = if (incomingShown == null) {
                                        null
                                    } else {
                                        {
                                            key(incomingClip?.id) {
                                                ClipPicture(
                                                    shown = incomingShown,
                                                    player = player.incomingPlayer,
                                                    pictures = pictures,
                                                    wholeColor = wholeColor,
                                                    highDefinition = highDefinition,
                                                    keysOnGpu = keysOnGpu,
                                                    wholeKey = wholeKey,
                                                    fileMs = { snapshot.value.incomingFileMs },
                                                    progress = { snapshot.value.incomingProgress },
                                                    playedMs = { snapshot.value.incomingPlayedMs },
                                                    fadeLevel = { snapshot.value.incomingFadeLevel },
                                                    selectedOverlayId = selectedOverlayId,
                                                    onUpdateText = onUpdateText,
                                                    onUpdateImage = onUpdateImage,
                                                    onSelectOverlay = onSelectOverlay,
                                                    playing = { playback.isPlaying.value },
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (cropOverlayShown) {
                        val rect = shownCrop ?: CropRect()
                        CropOverlay(
                            cropRect = Rect(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height),
                            freeform = cropAspect == CropAspect.FREE,
                            onCropRectChange = { changed ->
                                onCropChange(CropRect(changed.left, changed.top, changed.width, changed.height))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // What lies on the whole video, on its canvas as the export draws it: the plain
                // subtitles, then the captions and pictures in their order, then the fade over all.
                // Not while cropping: the crop's handles are then on top.
                if (!cropOverlayShown) {
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        // A SubStation Alpha file keeps its own look: shown plainly here.
                        SubtitleLayer(
                            cues = if (styledSubtitles) emptyList() else timelineCues,
                            snapshot = snapshot,
                            canvasHeight = frameHeight,
                        )
                        StackedOverlays(
                            texts = wholeTexts,
                            images = wholeImages,
                            canvasWidth = frameWidth,
                            canvasHeight = frameHeight,
                            colorMatrix = null,
                            outputMs = outputNow,
                            clipMs = null,
                            selectedOverlayId = selectedOverlayId,
                            onUpdateText = onUpdateText,
                            onUpdateImage = onUpdateImage,
                            onSelectOverlay = onSelectOverlay,
                            playing = { playback.isPlaying.value },
                            fadeLevel = { snapshot.value.fadeLevel },
                            scene = wholeScene,
                        )
                    }
                }

                // Styled subtitles: on the whole output frame (the canvas included), as libass draws them.
                if (styledSubtitles && !cropOverlayShown) {
                    val subtitleBlocks = remember(timelineWords, timelineCues, spoken, subtitleStyle, canvasRatio, subtitleFont) {
                        if (spoken) {
                            SubtitleLayout.ofWords(timelineWords, subtitleStyle, canvasRatio, subtitleFont?.facts)
                        } else {
                            SubtitleLayout.ofCues(timelineCues, subtitleStyle, canvasRatio, subtitleFont?.facts)
                        }
                    }
                    StyledSubtitleLayer(
                        blocks = subtitleBlocks,
                        style = subtitleStyle,
                        font = subtitleFont,
                        timeMs = { snapshot.value.timelineMs },
                        fadeLevel = { snapshot.value.fadeLevel },
                        editable = subtitlesEditable,
                        sampleText = stringResource(R.string.subtitle_sample),
                        onStyle = onSubtitleStyle,
                    )
                }
            }

            StatusBadges(
                clipCount = if (clips.size > 1) clips.size else 0,
                useStreamCopy = useStreamCopy,
                extractingFrames = extractingFrames,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// A clip
// ---------------------------------------------------------------------------

/** What [ClipPicture] draws of a clip, worked out by [PreviewContainer]. */
private class ShownClip(
    /** The clip's effects. */
    val effects: Effects,
    /** What its zoom and pan follow (none while it is cropped: it is shown whole). */
    val framing: Effects,
    val crop: CropRect?,
    /** Its shape on screen, turned and cropped. */
    val ratio: Float,
    /** Its colour key over its background, or null. */
    val key: ColorKey?,
    /** Its file, when it is a still picture (drawn here; the player shows nothing). */
    val still: EditorSource?,
    val texts: List<TextOverlay>,
    val images: List<ImageOverlay>,
    /** Its captions and pictures are drawn (not while it is cropped). */
    val overlays: Boolean,
)

/**
 * One clip of the joined video, in the order of the export's graph: its
 * picture enlarged and moved (its framing), cropped, turned, coloured (with
 * the colour of the whole video) and keyed, then its captions, its fade and
 * its pictures; fitted into the project frame. [player] shows its footage;
 * the clocks are those of [PreviewSnapshot] for this clip, read while drawing.
 */
@Composable
private fun ClipPicture(
    shown: ShownClip,
    player: ExoPlayer,
    pictures: PictureCache,
    wholeColor: FloatArray?,
    highDefinition: Boolean,
    keysOnGpu: Boolean,
    wholeKey: ColorKey?,
    fileMs: () -> Long,
    progress: () -> Float,
    playedMs: () -> Long,
    fadeLevel: () -> Float,
    selectedOverlayId: String?,
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    onSelectOverlay: (String) -> Unit,
    playing: () -> Boolean,
) {
    val effects = shown.effects
    val currentFileMs by rememberUpdatedState(fileMs)
    // A clip with keyframes: its colours as they are now (they change while it plays).
    val liveColor by remember(effects) {
        derivedStateOf { if (effects.keyframes.isEmpty) effects.color else effects.at(currentFileMs()).color }
    }
    val clipColor = remember(effects, liveColor, highDefinition) { effects.copy(color = liveColor).eqMatrix(highDefinition) }
    val pictureColor = remember(wholeColor, clipColor) { combineColorMatrices(wholeColor, clipColor) }
    // Before Android 13, and under a GIF's frames, the picture's pixels are keyed on the CPU.
    val cpuKeys = if (keysOnGpu) emptyList() else listOfNotNull(shown.key, wholeKey)
    val framing by rememberUpdatedState(shown.framing)
    var clipWidth by remember { mutableFloatStateOf(0f) }
    var clipHeight by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .aspectRatio(shown.ratio)
            .onSizeChanged { size ->
                clipWidth = size.width.toFloat()
                clipHeight = size.height.toFloat()
            },
    ) {
        // Enlarged and moved (the clip's framing, with its keyframes), as the export
        // does after the turn and crop: read while drawing, so the clock does not recompose.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    val now = framing.let { if (it.keyframes.isEmpty) it else it.at(currentFileMs()) }
                    val zoom = now.zoom.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = -(zoom - 1f) * size.width * now.panX.coerceIn(0f, 1f)
                    translationY = -(zoom - 1f) * size.height * now.panY.coerceIn(0f, 1f)
                },
        ) {
            Cropped(crop = shown.crop, modifier = Modifier.fillMaxSize()) {
                Turned(effects = effects, modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().colorKey(if (keysOnGpu) shown.key else null)) {
                        MediaPreview(
                            exoPlayer = player,
                            colorMatrix = pictureColor,
                            modifier = Modifier.fillMaxSize(),
                            keys = cpuKeys,
                        )
                        val still = shown.still
                        if (still != null) {
                            // A fresh state per picture: never a frame of the previous one.
                            key(still.uri) {
                                StillPicture(
                                    uri = still.uri,
                                    cache = pictures,
                                    colorMatrix = pictureColor,
                                    motion = effects.motion,
                                    progress = progress,
                                    modifier = Modifier.fillMaxSize(),
                                    keys = cpuKeys,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (shown.overlays) {
            Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                StackedOverlays(
                    texts = shown.texts,
                    images = shown.images,
                    canvasWidth = clipWidth,
                    canvasHeight = clipHeight,
                    colorMatrix = wholeColor,
                    outputMs = null,
                    clipMs = playedMs,
                    selectedOverlayId = selectedOverlayId,
                    onUpdateText = onUpdateText,
                    onUpdateImage = onUpdateImage,
                    onSelectOverlay = onSelectOverlay,
                    playing = playing,
                    fadeLevel = fadeLevel,
                )
            }
        }
    }
}

/**
 * Captions and pictures laid in their order (see stackOrder): each run of
 * captions or of pictures one after the other, bottom first; then the fade
 * at [fadeLevel], which covers them all, as in the export. [outputMs] times
 * those of the whole video; [clipMs] the keyframes of those of a clip.
 */
@Composable
internal fun BoxScope.StackedOverlays(
    texts: List<TextOverlay>,
    images: List<ImageOverlay>,
    canvasWidth: Float,
    canvasHeight: Float,
    colorMatrix: FloatArray?,
    outputMs: (() -> Long)?,
    clipMs: (() -> Long)?,
    selectedOverlayId: String?,
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    onSelectOverlay: (String) -> Unit,
    playing: () -> Boolean,
    fadeLevel: () -> Float,
    /** What lies on this frame, for things moved by hand to line up with. */
    scene: SnapScene = rememberSnapScene(),
) {
    val runs = remember(texts, images) {
        val order = stackOrder(texts.map { it.layer }, images.map { it.layer })
        val grouped = mutableListOf<Pair<Boolean, MutableList<Int>>>()
        for (slot in order) {
            val last = grouped.lastOrNull()
            if (last != null && last.first == slot.isText) last.second += slot.index else grouped += slot.isText to mutableListOf(slot.index)
        }
        grouped
    }
    for ((isText, indices) in runs) {
        // Kept by what it holds, so that a move up or down keeps each one's state.
        key(isText, indices.firstOrNull()?.let { if (isText) texts.getOrNull(it)?.id else images.getOrNull(it)?.id }) {
            if (isText) {
                TextOverlaysLayer(
                    overlays = indices.mapNotNull { texts.getOrNull(it) },
                    editableId = selectedOverlayId,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    colorMatrix = colorMatrix,
                    outputMs = outputMs,
                    onUpdate = onUpdateText,
                    onSelect = onSelectOverlay,
                    clipMs = clipMs,
                    scene = scene,
                )
            } else {
                ImageOverlaysLayer(
                    overlays = indices.mapNotNull { images.getOrNull(it) },
                    editableId = selectedOverlayId,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    colorMatrix = colorMatrix,
                    outputMs = outputMs,
                    onUpdate = onUpdateImage,
                    onSelect = onSelectOverlay,
                    playing = playing,
                    clipMs = clipMs,
                    scene = scene,
                )
            }
        }
    }
    FadeLayer(level = fadeLevel)
    // The lines what is being moved sticks to, over everything.
    SnapGuidesLayer(scene)
}

// ---------------------------------------------------------------------------
// Geometry
// ---------------------------------------------------------------------------

/**
 * Shows only [crop] of [content]: the content is enlarged and shifted so that
 * the rectangle fills this box. A null crop shows everything.
 *
 * The content is laid out at the enlarged size, so that it stays sharp. Crops
 * nested in crops multiply that size; past [MAX_LAYOUT_PX] the content is laid
 * out smaller and scaled up instead, which keeps the size measurable.
 */
@Composable
private fun Cropped(
    crop: CropRect?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.clipToBounds()) {
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                val width = if (crop != null && crop.width > 0f) constraints.maxWidth / crop.width else constraints.maxWidth.toFloat()
                val height = if (crop != null && crop.height > 0f) constraints.maxHeight / crop.height else constraints.maxHeight.toFloat()
                val shrink = maxOf(1f, maxOf(width, height) / MAX_LAYOUT_PX)
                val placeable = measurable.measure(
                    Constraints.fixed((width / shrink).toInt().coerceAtLeast(1), (height / shrink).toInt().coerceAtLeast(1)),
                )
                val x = -(width * (crop?.left ?: 0f)).toInt()
                val y = -(height * (crop?.top ?: 0f)).toInt()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    if (shrink > 1f) {
                        placeable.placeWithLayer(x, y) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = shrink
                            scaleY = shrink
                        }
                    } else {
                        placeable.place(x, y)
                    }
                }
            },
            content = content,
        )
    }
}

/** The id the video itself goes by among what lines up on the canvas. */
private const val VIDEO_SNAP_ID = "video"

/**
 * The box the video covers on a canvas of [width] x [height] pixels: of shape
 * [ratio], as large as fits, then [scale] times that around its middle at
 * ([centerX], [centerY]) of the canvas, turned [turn] degrees (the box holds it turned).
 */
private fun videoBox(width: Float, height: Float, ratio: Float, centerX: Float, centerY: Float, scale: Float, turn: Float): SnapBox {
    val fitsWide = ratio > width / height
    val baseWidth = if (fitsWide) width else height * ratio
    val baseHeight = if (fitsWide) width / ratio else height
    val radians = Math.toRadians(turn.toDouble())
    val cos = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
    val sin = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
    val w = baseWidth * scale
    val h = baseHeight * scale
    val boxWidth = w * cos + h * sin
    val boxHeight = w * sin + h * cos
    val cx = centerX * width
    val cy = centerY * height
    return SnapBox(cx - boxWidth / 2f, cy - boxHeight / 2f, cx + boxWidth / 2f, cy + boxHeight / 2f)
}

/** Largest side a cropped picture is laid out at before it is scaled instead. */
private const val MAX_LAYOUT_PX = 8192f

/**
 * Lays [content] out turned and flipped as [effects] says, so that once turned
 * it fills this box exactly: for a quarter turn the content is measured with
 * width and height swapped.
 */
@Composable
private fun Turned(
    effects: Effects,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val quarterTurn = effects.rotation == Rotation.DEG_90 || effects.rotation == Rotation.DEG_270
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = if (quarterTurn) {
                    measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        ),
                    )
                } else {
                    measurable.measure(constraints)
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (constraints.maxWidth - placeable.width) / 2,
                        (constraints.maxHeight - placeable.height) / 2,
                    )
                }
            }
            .graphicsLayer {
                rotationZ = effects.rotation.degrees.toFloat()
                scaleX = if (effects.flipHorizontal) -1f else 1f
                scaleY = if (effects.flipVertical) -1f else 1f
            },
        content = content,
    )
}

/**
 * The video surface, tinted with [colorMatrix]. Geometry is applied by the boxes
 * around it.
 */
@Composable
fun MediaPreview(
    exoPlayer: ExoPlayer,
    colorMatrix: FloatArray?,
    modifier: Modifier = Modifier,
    /** Colour keys done on the CPU, in order (see [gpuKeying]); each lays the picture over its background. */
    keys: List<ColorKey> = emptyList(),
) {
    val surface = remember { arrayOfNulls<TextureView>(1) }
    Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                TextureView(viewContext).apply {
                    exoPlayer.setVideoTextureView(this)
                    surface[0] = this
                }
            },
            update = { view -> view.setColorMatrix(colorMatrix) },
            modifier = Modifier.fillMaxSize(),
        )
        val grab = { surface[0]?.takeIf { it.isAvailable && it.width > 0 && it.height > 0 } }
        if (keys.isNotEmpty()) {
            // The keyed picture, over the video surface.
            val keyer = remember { FrameKeyer() }
            val currentKeys by rememberUpdatedState(keys)
            val currentMatrix by rememberUpdatedState(colorMatrix)
            ProcessedFrames(
                surface = grab,
                frameRate = { CPU_KEY_FPS },
                grabSize = ::cpuKeySize,
                process = { pixels, width, height ->
                    currentMatrix?.let { applyColorMatrix(pixels, width * height, it) }
                    keyer.key(pixels, width, height, currentKeys)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Frame rate of a video keyed on the CPU (Android 12 and earlier). */
private const val CPU_KEY_FPS = 24

/** Longest side a video keyed on the CPU is grabbed at. */
private const val CPU_KEY_PX = 480

/** Size a picture of [width] x [height] screen pixels is grabbed at to be keyed on the CPU. */
private fun cpuKeySize(width: Int, height: Int): Pair<Int, Int> {
    val scale = (CPU_KEY_PX.toFloat() / maxOf(width, height)).coerceAtMost(1f)
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

/**
 * Pictures made from a video surface, drawn over it, on their own clock: every
 * 1/[frameRate] of a second the [surface] is grabbed at [grabSize] into a bitmap
 * kept for it (on the main thread, a few milliseconds), made into the picture
 * shown by [process] off the main thread (it may change the pixels it gets and
 * may return an array it reuses), and drawn into one of two bitmaps that take
 * turns. Only the drawing is redone for a new frame, never the layout. Colour
 * keys before Android 13 are made this way.
 */
@Composable
private fun ProcessedFrames(
    surface: () -> TextureView?,
    frameRate: () -> Int,
    grabSize: (Int, Int) -> Pair<Int, Int>,
    process: (IntArray, Int, Int) -> IntArray,
    modifier: Modifier = Modifier,
) {
    val currentSurface by rememberUpdatedState(surface)
    val currentRate by rememberUpdatedState(frameRate)
    val currentSize by rememberUpdatedState(grabSize)
    val currentProcess by rememberUpdatedState(process)
    // The frame on show (one of two bitmaps), and a count that makes the canvas draw it again.
    val shown = remember { arrayOfNulls<Bitmap>(1) }
    var drawn by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var grabbed: Bitmap? = null
        val screens = arrayOfNulls<Bitmap>(2)
        var turn = 0
        var pixels = IntArray(0)
        var next = System.nanoTime()
        while (true) {
            val view = currentSurface()
            if (view != null) {
                val (width, height) = currentSize(view.width, view.height)
                val target = grabbed?.takeIf { it.width == width && it.height == height }
                    ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { grabbed = it }
                // Only the copy of the surface needs the main thread; the rest is done off it.
                view.getBitmap(target)
                val count = width * height
                if (pixels.size < count) pixels = IntArray(count)
                val work = currentProcess
                val read = pixels
                val frame = withContext(Dispatchers.Default) {
                    target.getPixels(read, 0, width, 0, 0, width, height)
                    work(read, width, height)
                }
                turn = 1 - turn
                val screen = screens[turn]?.takeIf { it.width == width && it.height == height }
                    ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { screens[turn] = it }
                screen.setPixels(frame, 0, width, 0, 0, width, height)
                shown[0] = screen
                drawn++
            }
            // On its own clock: one frame every 1/rate of a second, whatever the work took.
            next += 1_000_000_000L / currentRate().coerceIn(1, 60)
            val waitMs = (next - System.nanoTime()) / 1_000_000L
            if (waitMs > 0) delay(waitMs) else next = System.nanoTime()
        }
    }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG) }
    val area = remember { android.graphics.RectF() }
    Canvas(modifier = modifier) {
        drawn
        val frame = shown[0] ?: return@Canvas
        area.set(0f, 0f, size.width, size.height)
        drawIntoCanvas { it.nativeCanvas.drawBitmap(frame, null, area, paint) }
    }
}

/**
 * A still (a picture clip or a freeze frame), tinted with [colorMatrix] like the
 * video surface it covers. Black until the picture is decoded, so the last frame
 * of the video before it never shows through.
 */
@Composable
private fun StillPicture(
    uri: Uri,
    cache: PictureCache,
    colorMatrix: FloatArray?,
    /** The slow zoom or pan over it, if any, following [progress] through the clip. */
    motion: StillMotion?,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    /** Colour keys done on the CPU, in order (see [gpuKeying]). */
    keys: List<ColorKey> = emptyList(),
) {
    val bitmap by produceState(initialValue = cache.peek(uri), uri) {
        if (value == null) value = withContext(Dispatchers.IO) { cache.load(uri) }
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Keyed: its colours already changed; made again when the settings change.
    val made = keys.isNotEmpty()
    val madeImage by produceState<android.graphics.Bitmap?>(initialValue = null, listOf(bitmap, colorMatrix, boxSize, keys)) {
        val source = bitmap
        value = if (!made || source == null || boxSize.width <= 0) {
            null
        } else {
            withContext(Dispatchers.Default) {
                val (width, height) = cpuKeySize(boxSize.width, boxSize.height)
                var picture = android.graphics.Bitmap.createScaledBitmap(source, width, height, true)
                keys.forEachIndexed { index, key ->
                    // A still clip loses its own transparency in the export, before the key.
                    picture = keyedBitmap(picture, key, if (index == 0) colorMatrix else null, keepAlpha = false)
                }
                picture
            }
        }
    }
    val shown = if (made) madeImage else bitmap
    val image = remember(shown) { shown?.asImageBitmap() }
    val tint = remember(colorMatrix, made) { if (made) null else colorMatrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Box(modifier = modifier.background(Color.Black).clipToBounds().onSizeChanged { boxSize = it }) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                colorFilter = tint,
                // The box already has the picture's shape (the export fits it the same way).
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    // The view zoompan shows: the picture enlarged, the window placed as the
                    // motion says. Read while drawing, so playback does not recompose.
                    .graphicsLayer {
                        if (motion != null) {
                            val p = progress()
                            val zoom = motion.zoomAt(p)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom
                            scaleY = zoom
                            translationX = -(zoom - 1f) * motion.xAt(p) * size.width
                            translationY = -(zoom - 1f) * motion.yAt(p) * size.height
                        }
                    },
            )
        }
    }
}

/** Black over everything below it; [level] is read while drawing, so a fade does not recompose. */
@Composable
private fun FadeLayer(level: () -> Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - level() }
            .background(Color.Black),
    )
}

// ---------------------------------------------------------------------------
// Layers
// ---------------------------------------------------------------------------

/** Captions, drawn like drawtext places them; draggable and resizable while [editable]. */
@Composable
internal fun BoxScope.TextOverlaysLayer(
    overlays: List<TextOverlay>,
    /** The caption that can be dragged and resized; null = none. */
    editableId: String?,
    canvasWidth: Float,
    canvasHeight: Float,
    /** Colour change the text goes through afterwards (the whole video's, for a clip's captions). */
    colorMatrix: FloatArray?,
    /** The output clock, for captions with times of their own; null for a clip's captions. */
    outputMs: (() -> Long)?,
    onUpdate: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    /** A caption was touched: it gets the focus (and a drag moves it at once). */
    onSelect: (String) -> Unit,
    /** For a clip's captions: how long the clip has played (their keyframes' clock). */
    clipMs: (() -> Long)? = null,
    /** What a caption moved by hand lines up with (see SnapScene); null: nothing. */
    scene: SnapScene? = null,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = snapThreshold(density)
    val currentScene by rememberUpdatedState(scene)
    val currentCanvasWidth by rememberUpdatedState(canvasWidth)
    val currentCanvasHeight by rememberUpdatedState(canvasHeight)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Only the captions on screen now are composed; the set changes rarely, the clock often.
    val shown by remember(overlays, editableId, outputMs) {
        derivedStateOf {
            if (outputMs == null) overlays else overlays.filter { it.id == editableId || it.showsAt(outputMs()) }
        }
    }

    shown.forEach { overlay ->
        if (overlay.text.isNotBlank()) {
            key(overlay.id) {
                // With keyframes, as it is now on its clock: it moves while the preview plays. Its
                // place and opacity are read only while placing and drawing, so that the clock
                // recomposes nothing; a size keyframe recomposes the text as its size changes.
                val currentOverlay by rememberUpdatedState(overlay)
                val currentOutputMs by rememberUpdatedState(outputMs)
                val currentClipMs by rememberUpdatedState(clipMs)
                val liveState = remember {
                    derivedStateOf {
                        val now = currentOverlay
                        if (now.keyframes.isEmpty) now else now.at(keyClock(currentOutputMs, currentClipMs, now.startMs))
                    }
                }
                val current by liveState
                val liveSize by remember { derivedStateOf { liveState.value.size } }
                val editable = overlay.id == editableId
                val currentEditable by rememberUpdatedState(editable)
                var textWidth by remember { mutableFloatStateOf(0f) }
                var textHeight by remember { mutableFloatStateOf(0f) }
                val sizeAtDragStart = remember { floatArrayOf(0f) }
                // Where it lies, for what else is moved on this frame to line up with.
                DisposableEffect(scene, overlay.id) {
                    scene?.register(overlay.id) {
                        if (textWidth <= 0f) {
                            null
                        } else {
                            val left = (currentCanvasWidth - textWidth) * current.x
                            val top = (currentCanvasHeight - textHeight) * current.y
                            SnapBox(left, top, left + textWidth, top + textHeight)
                        }
                    }
                    onDispose { scene?.unregister(overlay.id) }
                }

                Box(
                    modifier = Modifier
                        .placedWithin({ current.x }, { current.y })
                        // Its opacity and fades, read while drawing so that the clock does not recompose.
                        .graphicsLayer {
                            alpha = current.opacity.coerceIn(0f, 1f) *
                                (if (outputMs != null) overlayFade(current.startMs, current.endMs, current.fadeInSeconds, current.fadeOutSeconds, outputMs()) else 1f)
                        }
                        .onSizeChanged {
                            textWidth = it.width.toFloat()
                            textHeight = it.height.toFloat()
                        }
                        // A touch selects it; a drag moves it, selecting it first if needed.
                        .pointerInput(overlay.id) {
                            detectTapGestures {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(current.id)
                            }
                        }
                        // One finger moves it, two also resize it; it sticks to the guides (SnapScene).
                        .pointerInput(overlay.id) {
                            var startX = 0f
                            var startY = 0f
                            var startSize = 0f
                            detectTotalTransform(
                                onStart = {
                                    if (!currentEditable) currentOnSelect(current.id)
                                    startX = current.x
                                    startY = current.y
                                    startSize = current.size
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onMove = { pan, zoom, _ ->
                                    val width = currentCanvasWidth
                                    val height = currentCanvasHeight
                                    val freeWidth = width - textWidth
                                    val freeHeight = height - textHeight
                                    val left = startX * freeWidth + pan.x
                                    val top = startY * freeHeight + pan.y
                                    val snap = currentScene?.snap(current.id, SnapBox(left, top, left + textWidth, top + textHeight), width, height, threshold, haptic)
                                    // It may go past the edges, where the frame cuts it.
                                    val x = if (freeWidth > 0f) ((left + (snap?.dx ?: 0f)) / freeWidth).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX) else current.x
                                    val y = if (freeHeight > 0f) ((top + (snap?.dy ?: 0f)) / freeHeight).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX) else current.y
                                    val size = if (zoom != 1f) (startSize * zoom).coerceIn(0.02f, 0.5f) else null
                                    currentOnUpdate(current.id, "move") { if (size != null) it.copy(x = x, y = y, size = size) else it.copy(x = x, y = y) }
                                },
                                onEnd = {
                                    currentScene?.clear()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                            )
                        },
                ) {
                    Text(
                        text = overlay.text,
                        color = Color(applyColorMatrix(overlay.color, colorMatrix)),
                        // drawtext sizes the font as a fraction of the frame height.
                        fontSize = with(density) { (canvasHeight * liveSize).toSp() },
                        fontWeight = if (overlay.isBold) FontWeight.Bold else FontWeight.Normal,
                        // The very file drawtext draws with.
                        fontFamily = rememberExportFont(overlay.font, overlay.isBold),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.border(
                            width = if (editable) 1.dp else 0.dp,
                            color = if (editable) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    )

                    if (editable) {
                        ResizeHandle(
                            dragKey = overlay.id,
                            onDragStart = {
                                sizeAtDragStart[0] = current.size
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { totalX, totalY ->
                                if (currentCanvasHeight > 0f) {
                                    val newSize = (sizeAtDragStart[0] + (totalX + totalY) / (currentCanvasHeight * 1.5f)).coerceIn(0.02f, 0.5f)
                                    currentOnUpdate(current.id, "size") { it.copy(size = newSize) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Burnt-in subtitles: the line that is on screen right now. [cues] are on the timeline clock. */
@Composable
private fun BoxScope.SubtitleLayer(
    cues: List<SubtitleCue>,
    snapshot: State<PreviewSnapshot>,
    canvasHeight: Float,
) {
    // Derived: the clock recomposes it only when the line changes.
    val text by remember(cues, snapshot) { derivedStateOf { cues.textAt(snapshot.value.timelineMs) } }
    if (cues.isEmpty() || canvasHeight <= 0f) return
    val line = text ?: return
    val density = LocalDensity.current

    Text(
        text = line,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontSize = with(density) { (canvasHeight * SUBTITLE_FONT_FRACTION).toSp() },
        style = MaterialTheme.typography.bodyMedium.copy(
            shadow = Shadow(color = Color.Black, blurRadius = 6f),
        ),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                bottom = with(density) { (canvasHeight * SUBTITLE_MARGIN_FRACTION).toDp() },
            ),
    )
}

/** Watermarks and logos, placed and sized like the export; adjustable while [editable]. */
@Composable
internal fun BoxScope.ImageOverlaysLayer(
    overlays: List<ImageOverlay>,
    /** The picture that can be dragged and resized; null = none. */
    editableId: String?,
    canvasWidth: Float,
    canvasHeight: Float,
    /** Colour change the pictures go through afterwards (the whole video's, for a clip's pictures). */
    colorMatrix: FloatArray?,
    /** The output clock, for pictures with times of their own; null for a clip's pictures. */
    outputMs: (() -> Long)?,
    onUpdate: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit,
    /** A picture was touched: it gets the focus (and a drag moves it at once). */
    onSelect: (String) -> Unit,
    /** Whether the preview plays, for the videos laid over it. */
    playing: () -> Boolean,
    /** For a clip's pictures: how long the clip has played (their keyframes' clock). */
    clipMs: (() -> Long)? = null,
    /** What a picture moved by hand lines up with (see SnapScene); null: nothing. */
    scene: SnapScene? = null,
) {
    val threshold = snapThreshold(LocalDensity.current)
    val currentScene by rememberUpdatedState(scene)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentCanvasWidth by rememberUpdatedState(canvasWidth)
    val currentCanvasHeight by rememberUpdatedState(canvasHeight)
    val currentOnUpdate by rememberUpdatedState(onUpdate)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // What is on screen now; a video also a moment before it starts, so that its player is
    // ready (it stays invisible until then).
    val shown by remember(overlays, editableId, outputMs) {
        derivedStateOf {
            if (outputMs == null) {
                overlays
            } else {
                val now = outputMs()
                overlays.filter {
                    it.id == editableId || overlayShowsAt(it.startMs - (if (it.isVideo) VIDEO_PRELOAD_MS else 0L), it.endMs, now)
                }
            }
        }
    }

    shown.forEach { overlay ->
        val uri = overlay.uri
        if (uri != null) {
            key(overlay.id) {
                // With keyframes, as it is now on its clock: it moves while the preview plays. It is
                // read only while laying out and drawing, so that the clock recomposes nothing.
                val currentOverlay by rememberUpdatedState(overlay)
                val currentOutputMs by rememberUpdatedState(outputMs)
                val currentClipMs by rememberUpdatedState(clipMs)
                val liveState = remember {
                    derivedStateOf {
                        val now = currentOverlay
                        if (now.keyframes.isEmpty) now else now.at(keyClock(currentOutputMs, currentClipMs, now.startMs))
                    }
                }
                val current by liveState
                val editable = overlay.id == editableId
                val currentEditable by rememberUpdatedState(editable)
                val scaleAtDragStart = remember { floatArrayOf(0f) }
                var boxWidth by remember { mutableFloatStateOf(0f) }
                var boxHeight by remember { mutableFloatStateOf(0f) }
                // Where it lies (the box holding it turned), for what else is moved here to line up with.
                DisposableEffect(scene, overlay.id) {
                    scene?.register(overlay.id) {
                        if (boxWidth <= 0f) {
                            null
                        } else {
                            val left = (currentCanvasWidth - boxWidth) * current.x
                            val top = (currentCanvasHeight - boxHeight) * current.y
                            SnapBox(left, top, left + boxWidth, top + boxHeight)
                        }
                    }
                    onDispose { scene?.unregister(overlay.id) }
                }
                // A picture is decoded off the main thread, at a size that suits a phone screen;
                // a video plays on a player of its own.
                val bitmap = if (overlay.isVideo) {
                    null
                } else {
                    produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
                        value = withContext(Dispatchers.IO) { smallPicture(context, uri, OVERLAY_DECODE_PX) }
                    }.value
                }
                // A video loading before its start is not there yet: touches go through it.
                // (Derived, so that only the moment it shows recomposes, not every tick.)
                val touchable by remember(outputMs, editableId) {
                    derivedStateOf {
                        val now = outputMs?.invoke()
                        now == null || current.id == editableId || overlayShowsAt(current.startMs, current.endMs, now)
                    }
                }
                val ratio = when {
                    overlay.isVideo -> overlay.aspect.takeIf { it > 0f } ?: DEFAULT_RATIO
                    bitmap != null && bitmap.height > 0 -> bitmap.width.toFloat() / bitmap.height.toFloat()
                    else -> null
                }
                // Its own colours, then those of what it lies on (for a clip's pictures).
                val ownColor = remember(overlay.brightness, overlay.contrast, overlay.saturation) {
                    Effects(color = ColorSettings(brightness = overlay.brightness, contrast = overlay.contrast, saturation = overlay.saturation)).eqMatrix()
                }
                val matrix = remember(colorMatrix, ownColor) { combineColorMatrices(colorMatrix, ownColor) }
                // Keyed, a picture is cut out after its own colours (once, on the CPU); those of
                // what it lies on come after.
                val cut = overlay.key
                val look = overlay.look
                // A look of its own: a still picture is made on the CPU as the export makes it.
                val decorated = !look.isPlain && !overlay.isVideo
                val keyed = if (cut == null || bitmap == null) {
                    null
                } else {
                    produceState<android.graphics.Bitmap?>(initialValue = null, bitmap, cut, ownColor) {
                        value = withContext(Dispatchers.Default) { keyedBitmap(bitmap, cut, ownColor) }
                    }.value
                }
                val tint = remember(matrix, colorMatrix, cut, decorated) {
                    (if (cut != null || decorated) colorMatrix else matrix)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
                }
                // The picture as it is, with its own colours (or keyed with them): what the live look draws.
                val liveTint = remember(matrix, colorMatrix, cut) {
                    (if (cut != null) colorMatrix else matrix)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
                }
                // The picture made with its look on the CPU, as the export makes it, at the size it
                // shows (in steps, so that a resize does not remake it at every pixel). It is shown
                // only while it was made for the look and picture there are now (a resize keeps it:
                // it only scales); until the next one is ready the look is drawn live (LookFrame),
                // so that a slider moves the picture at once and nothing is ever stretched.
                // (Derived: a size keyframe changes it only once per step, not at every frame.)
                val shownWidthPx by remember {
                    derivedStateOf {
                        (current.scaleW.coerceIn(0.01f, ImagePlacement.MAX_WIDTH) * currentCanvasWidth / LOOK_SIZE_STEP).roundToInt().coerceAtLeast(1) * LOOK_SIZE_STEP
                    }
                }
                val lookFor = listOf(bitmap, cut, ownColor, overlay.flipHorizontal, overlay.flipVertical, look)
                var lookPicture by remember(overlay.id) { mutableStateOf<MadeLook?>(null) }
                LaunchedEffect(bitmap, decorated, cut, ownColor, overlay.flipHorizontal, overlay.flipVertical, look, shownWidthPx) {
                    if (!decorated || bitmap == null) {
                        lookPicture = null
                        return@LaunchedEffect
                    }
                    // While a slider moves, only the last of its values is made (the live look meanwhile).
                    if (lookPicture != null) delay(LOOK_SETTLE_MS)
                    val made = withContext(Dispatchers.Default) {
                        decoratedPicture(bitmap, cut, ownColor, overlay.flipHorizontal, overlay.flipVertical, look, shownWidthPx)
                    }
                    lookPicture = MadeLook(made, lookFor)
                }
                val exactLook = lookPicture?.takeIf { it.madeFor == lookFor }
                if (ratio != null) {
                    // The box laid on the video: the picture as it shows (cropped), with the room
                    // its outline and shadow take around it, as the export lays it.
                    val laidRatio = look.laidAspect(ratio).takeIf { it > 0f } ?: ratio
                    // Its size is worked out while laying out: a size keyframe resizes it without
                    // recomposing it.
                    val laidSize = Modifier.layout { measurable, _ ->
                        val widthPx = current.scaleW.coerceIn(0.01f, ImagePlacement.MAX_WIDTH) * currentCanvasWidth * (1f + 2f * look.margin)
                        val width = widthPx.roundToInt().coerceAtLeast(1)
                        val height = (widthPx / laidRatio).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    }
                    Box(
                        modifier = Modifier
                            .placedOnCanvas({ current.x }, { current.y })
                            // Placed by the box that holds it turned, as the export places it.
                            .turned { current.rotationDegrees }
                            .onSizeChanged {
                                boxWidth = it.width.toFloat()
                                boxHeight = it.height.toFloat()
                            }
                            .graphicsLayer {
                                val now = outputMs?.invoke()
                                alpha = if (now == null) {
                                    current.opacity
                                } else if (current.id != editableId && !overlayShowsAt(current.startMs, current.endMs, now)) {
                                    0f
                                } else {
                                    current.opacity * overlayFade(current.startMs, current.endMs, current.fadeInSeconds, current.fadeOutSeconds, now)
                                }
                            }
                            // A touch selects it; a drag moves it, selecting it first if needed.
                            .pointerInput(overlay.id, touchable) {
                                if (!touchable) return@pointerInput
                                detectTapGestures {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSelect(current.id)
                                }
                            }
                            // One finger moves it, two also resize and turn it; it sticks to the guides
                            // (SnapScene) and its turn to whole steps.
                            .pointerInput(overlay.id, touchable) {
                                if (!touchable) return@pointerInput
                                var startX = 0f
                                var startY = 0f
                                var startScale = 0f
                                var startTurn = 0f
                                detectTotalTransform(
                                    onStart = {
                                        if (!currentEditable) currentOnSelect(current.id)
                                        startX = current.x
                                        startY = current.y
                                        startScale = current.scaleW
                                        startTurn = current.rotationDegrees
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onMove = { pan, zoom, rotation ->
                                        val width = currentCanvasWidth
                                        val height = currentCanvasHeight
                                        // Larger than the frame, the free space is negative: a drag pans it.
                                        val freeWidth = (width - boxWidth).let { if (kotlin.math.abs(it) < 1f) 1f else it }
                                        val freeHeight = (height - boxHeight).let { if (kotlin.math.abs(it) < 1f) 1f else it }
                                        val left = startX * freeWidth + pan.x
                                        val top = startY * freeHeight + pan.y
                                        val snap = currentScene?.snap(current.id, SnapBox(left, top, left + boxWidth, top + boxHeight), width, height, threshold, haptic)
                                        // It may go past the edges, where the frame cuts it.
                                        val x = ((left + (snap?.dx ?: 0f)) / freeWidth).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX)
                                        val y = ((top + (snap?.dy ?: 0f)) / freeHeight).coerceIn(ImagePlacement.POSITION_MIN, ImagePlacement.POSITION_MAX)
                                        val scale = if (zoom != 1f) (startScale * zoom).coerceIn(0.02f, ImagePlacement.MAX_WIDTH) else null
                                        val turn = if (kotlin.math.abs(rotation) > 0.5f) (currentScene?.turn(startTurn + rotation, haptic) ?: (startTurn + rotation)) else null
                                        currentOnUpdate(current.id, "move") {
                                            var moved = it.copy(x = x, y = y)
                                            if (scale != null) moved = moved.copy(scaleW = scale)
                                            if (turn != null) moved = moved.copy(rotationDegrees = turn)
                                            moved
                                        }
                                    },
                                    onEnd = {
                                        currentScene?.clear()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                )
                            },
                    ) {
                        // The upright picture, mirrored; the turn is the box's around it.
                        val selectedOutline = Modifier.border(
                            width = if (editable) 1.dp else 0.dp,
                            color = if (editable) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        )
                        when {
                            overlay.isVideo && !look.isPlain -> LookFrame(
                                look = look,
                                flipHorizontal = overlay.flipHorizontal,
                                flipVertical = overlay.flipVertical,
                                modifier = laidSize.then(selectedOutline),
                            ) {
                                VideoOverlaySurface(
                                    overlay = overlay,
                                    outputMs = outputMs,
                                    playing = playing,
                                    colorMatrix = if (cut != null) ownColor else matrix,
                                    outerMatrix = colorMatrix,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            decorated && exactLook != null -> Box(modifier = laidSize.then(selectedOutline)) {
                                Image(
                                    bitmap = exactLook.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    colorFilter = tint,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            decorated -> {
                                // Drawn live while the exact one is made. A picture with transparent
                                // parts (a sticker, a cut-out) gets its outline and shadow from the
                                // exact one only: drawn live they would follow its box, not its shape.
                                val source = if (cut != null) keyed else bitmap
                                val liveLook = if (source != null && source.hasAlpha()) look.copy(borderWidth = 0f, shadowOpacity = 0f) else look
                                LookFrame(
                                    look = liveLook,
                                    flipHorizontal = overlay.flipHorizontal,
                                    flipVertical = overlay.flipVertical,
                                    margin = look.margin,
                                    modifier = laidSize.then(selectedOutline),
                                ) {
                                    if (source != null) {
                                        Image(
                                            bitmap = source.asImageBitmap(),
                                            contentDescription = null,
                                            colorFilter = liveTint,
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                            else -> Box(
                                modifier = Modifier
                                    .then(laidSize)
                                    .graphicsLayer {
                                        scaleX = if (current.flipHorizontal) -1f else 1f
                                        scaleY = if (current.flipVertical) -1f else 1f
                                    }
                                    .then(selectedOutline),
                            ) {
                                if (overlay.isVideo) {
                                    VideoOverlaySurface(
                                        overlay = overlay,
                                        outputMs = outputMs,
                                        playing = playing,
                                        colorMatrix = if (cut != null) ownColor else matrix,
                                        outerMatrix = colorMatrix,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else if (bitmap != null && (cut == null || keyed != null)) {
                                    Image(
                                        bitmap = (keyed ?: bitmap).asImageBitmap(),
                                        contentDescription = null,
                                        colorFilter = tint,
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        if (editable) {
                            ResizeHandle(
                                dragKey = overlay.id,
                                onDragStart = {
                                    scaleAtDragStart[0] = current.scaleW
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { totalX, totalY ->
                                    if (currentCanvasWidth > 0f) {
                                        val newScale = (scaleAtDragStart[0] + (totalX + totalY) / (currentCanvasWidth * 2f)).coerceIn(0.02f, ImagePlacement.MAX_WIDTH)
                                        currentOnUpdate(current.id, "size") { it.copy(scaleW = newScale) }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Places its content at ([x], [y]) of the free space of the box it is in, like
 * a [BiasAlignment] (the content measured within the box), the place read only
 * while placing: a caption moving with keyframes is not recomposed.
 */
private fun Modifier.placedWithin(x: () -> Float, y: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    layout(width, height) {
        placeable.place(((width - placeable.width) * x()).roundToInt(), ((height - placeable.height) * y()).roundToInt())
    }
}

/**
 * Lays its content out upright and reports the box that holds it turned by
 * [degreesOf] (clockwise, read while laying out), drawing it turned in the
 * middle: the export places a turned picture by that box too.
 */
private fun Modifier.turned(degreesOf: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val degrees = degreesOf()
    val radians = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.abs(kotlin.math.cos(radians))
    val sin = kotlin.math.abs(kotlin.math.sin(radians))
    val width = (placeable.width * cos + placeable.height * sin).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = (placeable.width * sin + placeable.height * cos).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) { rotationZ = degrees }
    }
}

/**
 * A video laid over the picture, on a silent player of its own (its sound plays
 * with the placed sounds), kept where the output clock says: the part of its
 * file that has played since it started, still while the preview is paused.
 */
@Composable
private fun VideoOverlaySurface(
    overlay: ImageOverlay,
    outputMs: (() -> Long)?,
    playing: () -> Boolean,
    /** Its colours; with a key, its own only (the key comes after them). */
    colorMatrix: FloatArray?,
    /** With a key: the colour change after it (that of what it lies on), else null. */
    outerMatrix: FloatArray? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val media = overlay.video ?: return
    val uri = overlay.uri ?: return
    val player = remember(uri, media.trimStartMs, media.trimEndMs) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            setMediaItem(
                androidx.media3.common.MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(media.trimStartMs)
                            .setEndPositionMs(media.trimEndMs.coerceAtLeast(media.trimStartMs + 1))
                            .build(),
                    )
                    .build(),
            )
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val currentMedia by rememberUpdatedState(media)
    LaunchedEffect(player) {
        while (true) {
            val now = outputMs?.invoke() ?: 0L
            val span = currentMedia
            // Where in its part of the file it is (the player counts from the trim start).
            val position = span.fileMsAt(now) - span.trimStartMs
            val run = playing() && now >= span.startMs && now < span.endMs
            val loading = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            val drift = kotlin.math.abs(player.currentPosition - position)
            if (!run) {
                if (player.isPlaying) player.pause()
                if (!loading && drift > OVERLAY_PARKED_DRIFT_MS) player.seekTo(position)
            } else {
                if (!loading && drift > OVERLAY_DRIFT_MS) player.seekTo(position)
                if (!player.isPlaying && player.playbackState == androidx.media3.common.Player.STATE_READY) player.play()
            }
            delay(OVERLAY_SYNC_MS)
        }
    }
    val cut = overlay.key
    key(player) {
        val surface = remember { arrayOfNulls<TextureView>(1) }
        val cpuKey = cut?.takeIf { !gpuKeying }
        Box(modifier = modifier) {
            AndroidView(
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        player.setVideoTextureView(this)
                        surface[0] = this
                    }
                },
                update = { view -> view.setColorMatrix(colorMatrix) },
                modifier = Modifier
                    .fillMaxSize()
                    .colorKey(cut, outerMatrix)
                    // Keyed on the CPU, the surface is only read: all but invisible, still drawn.
                    .graphicsLayer { alpha = if (cpuKey != null) HIDDEN_ALPHA else 1f },
            )
            if (cpuKey != null) {
                val keyer = remember { FrameKeyer() }
                val currentKey by rememberUpdatedState(cpuKey)
                val currentOwn by rememberUpdatedState(colorMatrix)
                val currentOuter by rememberUpdatedState(outerMatrix)
                ProcessedFrames(
                    surface = { surface[0]?.takeIf { it.isAvailable && it.width > 0 && it.height > 0 } },
                    frameRate = { CPU_KEY_FPS },
                    grabSize = ::cpuKeySize,
                    process = { pixels, width, height ->
                        currentOwn?.let { applyColorMatrix(pixels, width * height, it) }
                        val keyed = keyer.key(pixels, width, height, listOf(currentKey))
                        currentOuter?.let { applyColorMatrix(keyed, width * height, it) }
                        keyed
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Alpha of a video surface that is only read (keyed on the CPU): above zero, so that it is still drawn. */
private const val HIDDEN_ALPHA = 1f / 255f

/** A video overlay's player is made ready this long before it shows. */
private const val VIDEO_PRELOAD_MS = 1_000L

/** How far a playing / waiting video overlay may drift before it is moved. */
private const val OVERLAY_DRIFT_MS = 300L
private const val OVERLAY_PARKED_DRIFT_MS = 80L

/** How often a video overlay is brought to the output clock. */
private const val OVERLAY_SYNC_MS = 50L

/** True when a caption or picture shown from [startMs] to [endMs] (null = to the end) is on screen at [atMs]. */
private fun overlayShowsAt(startMs: Long, endMs: Long?, atMs: Long): Boolean =
    atMs >= startMs && (endMs == null || atMs < endMs)

private fun TextOverlay.showsAt(atMs: Long): Boolean = overlayShowsAt(startMs, endMs, atMs)

/**
 * The clock of a text's or picture's keyframes: the time since it started on
 * the output, or for one of a clip since the clip started playing. Read in
 * composition, so that what has keyframes moves with the clock.
 */
private fun keyClock(outputMs: (() -> Long)?, clipMs: (() -> Long)?, startMs: Long): Long =
    outputMs?.let { it() - startMs } ?: clipMs?.invoke() ?: 0L

/**
 * How visible (0..1) an overlay with these fades is at [atMs]: the same ramps
 * as the export's alpha expression and picture fades.
 */
private fun overlayFade(startMs: Long, endMs: Long?, fadeInSeconds: Float, fadeOutSeconds: Float, atMs: Long): Float {
    var level = 1f
    if (fadeInSeconds > 0f) level = minOf(level, (atMs - startMs) / (fadeInSeconds * 1000f))
    if (fadeOutSeconds > 0f && endMs != null) level = minOf(level, (endMs - atMs) / (fadeOutSeconds * 1000f))
    return level.coerceIn(0f, 1f)
}

/** Longest side a picture laid over the preview is decoded at. */
private const val OVERLAY_DECODE_PX = 1280

/** A picture with a look is made again only when the width it shows at changes by this much. */
private const val LOOK_SIZE_STEP = 24

/** How long a look stays unchanged before its exact picture is made (a slider moving makes none). */
private const val LOOK_SETTLE_MS = 120L

/** A picture made with its look on the CPU, and what it was made for (to know when it no longer fits). */
private class MadeLook(val bitmap: android.graphics.Bitmap, val madeFor: List<Any?>)

/**
 * Round grip in the bottom-right corner of an overlay. [onDrag] receives the
 * distance travelled since the drag started, in pixels.
 */
@Composable
private fun BoxScope.ResizeHandle(
    dragKey: String,
    onDragStart: () -> Unit,
    onDrag: (totalX: Float, totalY: Float) -> Unit,
) {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = 6.dp, y = 6.dp)
            .size(16.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .pointerInput(dragKey) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        currentOnDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        currentOnDrag(totalX, totalY)
                    },
                )
            },
    )
}

// ---------------------------------------------------------------------------
// Chrome around the picture
// ---------------------------------------------------------------------------

/** Reminders for the modes that change what kind of output is produced. */
@Composable
private fun StatusBadges(
    clipCount: Int,
    useStreamCopy: Boolean,
    extractingFrames: Boolean,
    modifier: Modifier = Modifier,
) {
    val clipsLabel = stringResource(R.string.badge_clips, clipCount)
    val streamCopyLabel = stringResource(R.string.badge_stream_copy)
    val framesLabel = stringResource(R.string.badge_extract_frames)

    val badges = mutableListOf<Pair<ImageVector, String>>()
    if (clipCount > 0) badges += Icons.Rounded.VideoLibrary to clipsLabel
    if (useStreamCopy) badges += Icons.Rounded.Sync to streamCopyLabel
    if (extractingFrames) badges += Icons.Rounded.PhotoCamera to framesLabel
    if (badges.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        badges.forEach { (icon, label) ->
            Surface(
                // Dark glass over the picture, like the rest of the editor.
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

