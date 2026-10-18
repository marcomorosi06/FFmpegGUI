@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cuscus.ffmpeggui

import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.Layout
import com.cuscus.ffmpeggui.ui.theme.LocalAppSettings
import com.cuscus.ffmpeggui.settings.ThemeMode
import com.cuscus.ffmpeggui.core.model.VideoHdr
import com.cuscus.ffmpeggui.ui.theme.appColorScheme
import com.cuscus.ffmpeggui.ui.theme.onBlack
import android.net.Uri
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.runtime.produceState
import com.cuscus.ffmpeggui.core.model.ColorSettings
import com.cuscus.ffmpeggui.core.preview.combineColorMatrices
import com.cuscus.ffmpeggui.core.preview.eqMatrix
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.cuscus.ffmpeggui.preview.silentEnds
import com.cuscus.ffmpeggui.core.model.Split
import com.cuscus.ffmpeggui.core.model.SplitMode
import com.cuscus.ffmpeggui.core.model.MetadataTags
import com.cuscus.ffmpeggui.core.model.AudioEncoding
import com.cuscus.ffmpeggui.core.model.AudioEffects
import com.cuscus.ffmpeggui.core.model.CanvasRatio
import com.cuscus.ffmpeggui.core.model.FrameExtractionMode
import com.cuscus.ffmpeggui.core.model.GifSettings
import com.cuscus.ffmpeggui.core.model.Framerate
import com.cuscus.ffmpeggui.core.model.OutputFormat
import com.cuscus.ffmpeggui.core.model.Resolution
import com.cuscus.ffmpeggui.core.model.Rotation
import com.cuscus.ffmpeggui.core.model.TextOverlay
import com.cuscus.ffmpeggui.core.timeline.LaneSpan
import com.cuscus.ffmpeggui.core.timeline.TimeSpan
import com.cuscus.ffmpeggui.core.preview.SubtitleCue
import com.cuscus.ffmpeggui.core.model.CropRect
import com.cuscus.ffmpeggui.core.model.Effects
import com.cuscus.ffmpeggui.core.model.Transition
import com.cuscus.ffmpeggui.core.model.transitionLengths
import com.cuscus.ffmpeggui.core.model.stackOrder
import kotlinx.coroutines.launch
import com.cuscus.ffmpeggui.core.preview.turnedRatio
import com.cuscus.ffmpeggui.core.timeline.Clip
import com.cuscus.ffmpeggui.core.timeline.Timeline
import com.cuscus.ffmpeggui.preview.outputClock
import androidx.compose.runtime.saveable.rememberSaveable
import com.cuscus.ffmpeggui.ui.expressive.ConnectedChoiceGroup
import com.cuscus.ffmpeggui.ui.expressive.MorphingPlayButton
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.core.model.Keyframes
import com.cuscus.ffmpeggui.core.model.Keyframe
import com.cuscus.ffmpeggui.core.model.KeyEasing
import com.cuscus.ffmpeggui.core.model.EaseCurve
import com.cuscus.ffmpeggui.playback.Artwork
import com.cuscus.ffmpeggui.playback.NowPlaying
import com.cuscus.ffmpeggui.preview.PreviewSnapshot
import com.cuscus.ffmpeggui.ui.expressive.MorphingContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull

/** Shape assumed for a file whose size is not known yet. */
private const val DEFAULT_FRAME_RATIO = 16f / 9f

/**
 * The crop for a locked [aspect] on a frame of [frameRatio]: as large as fits,
 * centred where [current] was (or in the middle). A free aspect keeps [current].
 */
internal fun fitCrop(aspect: CropAspect, current: CropRect?, frameRatio: Float): CropRect? {
    val target = aspect.ratio ?: return current
    val width = if (frameRatio > target) target / frameRatio else 1f
    val height = if (frameRatio > target) 1f else frameRatio / target
    val centreX = current?.let { it.left + it.width / 2f } ?: 0.5f
    val centreY = current?.let { it.top + it.height / 2f } ?: 0.5f
    val x = centreX.coerceIn(width / 2f, 1f - width / 2f)
    val y = centreY.coerceIn(height / 2f, 1f - height / 2f)
    return CropRect(x - width / 2f, y - height / 2f, width, height)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    inputUri: Uri?,
    format: OutputFormat,
    fileName: String,
    /** The footage as a sequence of clips, and the files those clips come from. */
    timeline: Timeline,
    sources: List<EditorSource>,
    /** Trim of the whole sequence, in milliseconds on the timeline (end null = to the end). */
    trimStartMs: Long,
    trimEndMs: Long?,
    resolution: Resolution,
    framerate: Framerate,
    qualityLevel: Float,
    /** HDR footage stays HDR (see EncodingSettings.keepHdr). */
    keepHdr: Boolean,
    /** Effects of the whole video; those of each clip are in [timeline]. */
    wholeEffects: Effects,
    /** Changes the effects of one clip (its id) or, for null, of the whole video. */
    onUpdateEffects: (String?, (Effects) -> Effects) -> Unit,
    /** Gives every clip this transition into the next one (null: plain cuts). */
    onTransitionForAll: (Transition?) -> Unit = {},
    projectRatio: CanvasRatio,
    customCommand: String,
    commandPreview: String,
    onAddClip: (Uri, String) -> Unit,
    onSplitClip: (Long) -> Unit,
    /** Freezes the frame at this timeline position into a still clip; the second value names the still. */
    onFreezeFrame: (Long, String) -> Unit,
    onRemoveClip: (String) -> Unit,
    onDuplicateClip: (String) -> Unit,
    onMoveClip: (String, Int) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit,
    onTrimWindow: (Long, Long?) -> Unit,
    onResolution: (Resolution) -> Unit,
    onFramerate: (Framerate) -> Unit,
    onQualityLevel: (Float) -> Unit,
    onKeepHdr: (Boolean) -> Unit,
    onProjectRatioChange: (CanvasRatio) -> Unit,
    onCustomCommand: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    isManualMode: Boolean,
    manualCommand: String,
    onManualCommandChange: (String) -> Unit,
    onResetManualMode: () -> Unit,
    normalizeAudio: Boolean,
    onNormalizeAudio: (Boolean) -> Unit,
    /** What is done to the final sound (an audio file's tools). */
    audioEffects: AudioEffects = AudioEffects.NONE,
    /** Changes it; the key groups quick changes into one undo step. */
    onAudioEffects: (String?, (AudioEffects) -> AudioEffects) -> Unit = { _, _ -> },
    /** How a GIF is made (only for a GIF). */
    gif: GifSettings = GifSettings(),
    onGif: (String?, (GifSettings) -> GifSettings) -> Unit = { _, _ -> },
    /** How an audio file is encoded. */
    audioEncoding: AudioEncoding = AudioEncoding(),
    onAudioEncoding: ((AudioEncoding) -> AudioEncoding) -> Unit = {},
    /** The tags written into the file. */
    tags: MetadataTags = MetadataTags(),
    onTags: (String?, (MetadataTags) -> MetadataTags) -> Unit = { _, _ -> },
    /** The cover picture of an audio file, and how it is set (a chosen picture, or null for none). */
    coverUri: Uri? = null,
    onSetCover: (Uri?) -> Unit = {},
    /** How the output is cut into parts. */
    splitMode: SplitMode = SplitMode.DISABLED,
    splitValue: Int = 1,
    onSplit: (SplitMode, Int) -> Unit = { _, _ -> },
    /** Sounds placed freely on the video (music, voice-overs, effects). */
    audioClips: List<AudioClip>,
    /** Places a file's sound at an output instant; the callback gets the new sound's id. */
    onAddAudioClip: (Uri, String, Long, (String) -> Unit) -> Unit,
    /** Changes one sound; the key groups quick changes into one undo step. */
    onUpdateAudioClip: (String, String?, (AudioClip) -> AudioClip) -> Unit,
    onRemoveAudioClip: (String) -> Unit,
    /** Copies a sound right after itself; returns the copy's id. */
    onDuplicateAudioClip: (String) -> String?,
    /** Cuts a sound at an output instant; returns the id of the second half. */
    onSplitAudioClip: (String, Long) -> String?,
    /** Plays another file in a sound's place, keeping its settings. */
    onReplaceAudioClip: (String, Uri, String) -> Unit,
    /** Turns loudness levelling of a sound on or off (the file is measured once). */
    onAudioClipNormalize: (String, Boolean) -> Unit,
    /** Captions, each shown during its own time of the exported video. */
    textOverlays: List<TextOverlay>,
    /** Adds a caption to one clip (its id) or, for null, to the whole video. */
    /** Adds a caption at an output instant; returns its id. */
    onAddText: (String, Long) -> String,
    onRemoveTextOverlay: (String) -> Unit,
    /** Changes a caption; the key groups quick changes into one undo step. */
    onUpdateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit,
    /** Pictures laid over the video, each shown during its own time. */
    imageOverlays: List<ImageOverlay> = emptyList(),
    /**
     * Lays a picture or a video over the video at an output instant; the file is
     * looked at first, then the callback gets the new overlay's id.
     */
    onAddOverlay: (Uri, String, Long, (String) -> Unit, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    /** Shows another picture or video in an overlay's place, keeping its settings. */
    onReplaceOverlay: (String, Uri, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onRemoveImageOverlay: (String) -> Unit = {},
    onUpdateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit = { _, _, _ -> },
    /** Moves a picture among the pictures (layers). */
    /** Moves a caption or picture (its id) up or down among those laid with it. */
    onArrangeImage: (String, LayerMove) -> Unit = { _, _ -> },
    /** Moves or trims a text or overlay: its new span, as one undo step. */
    onSetOverlaySpan: (String, LaneSpan<*>) -> Unit = { _, _ -> },
    /** Copies a text or picture right after itself; returns the copy's id. */
    onDuplicateOverlay: (String) -> String? = { null },
    /** Cuts a text or picture at an output instant; returns the id of the second half. */
    onSplitOverlay: (String, Long) -> String? = { _, _ -> null },
    // Subtitles: a file, or made from what is said, and how they look
    subtitles: SubtitleEditing = SubtitleEditing(),
    // Stream copy
    useStreamCopy: Boolean = false,
    onUseStreamCopy: (Boolean) -> Unit = {},
    // Frame extraction
    frameExtractionMode: FrameExtractionMode = FrameExtractionMode.DISABLED,
    frameExtractionRate: Float = 1f,
    frameExtractionTimecode: String = "00:00:00",
    onFrameExtractionMode: (FrameExtractionMode) -> Unit = {},
    onFrameExtractionRate: (Float) -> Unit = {},
    onFrameExtractionTimecode: (String) -> Unit = {},
    // Live preview data
    /** Gain that previews loudness normalisation; 0 while the source has not been measured. */
    normalizationGainDb: Float = 0f,
    // Undo / redo
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
) {
    var selectedTool by rememberSaveable { mutableStateOf<EditorTool?>(null) }
    // The preview over the whole screen, without the editor around it.
    var previewFullscreen by rememberSaveable { mutableStateOf(false) }
    // The video itself picked up on the preview (a tap on it): moved and resized there by hand.
    var videoSelected by rememberSaveable { mutableStateOf(false) }
    // An audio file: the editor works on the sound alone (no picture, texts or overlays).
    val audioMode = format.isAudioOnly
    // A GIF: pictures only, so no sound tools at all (the preview plays without sound too).
    val gifMode = format == OutputFormat.GIF
    // The video editor's row of sound tools is open.
    var audioRowOpen by rememberSaveable { mutableStateOf(false) }
    // Level under which the start and end count as silence (the Silence tool).
    var silenceThresholdDb by rememberSaveable { mutableFloatStateOf(DEFAULT_SILENCE_DB) }
    var showExportDialog by remember { mutableStateOf(false) }
    // Bumped by undo / redo, so that panels holding their own copy of a value re-read it.
    var historyRevision by remember { mutableIntStateOf(0) }

    // The clips that really play: the timeline with the whole-sequence trim applied.
    val playingClips = remember(timeline, trimStartMs, trimEndMs) {
        timeline.window(trimStartMs, trimEndMs).ifEmpty { timeline.clips }
    }
    val playback = remember { PreviewPlayback() }
    var previewSeek by remember { mutableStateOf<PreviewSeek?>(null) }
    /** Shows a position of the playing sequence. */
    val seekPreview: (Long) -> Unit = { positionMs ->
        previewSeek = PreviewSeek(positionMs, (previewSeek?.token ?: 0) + 1)
    }
    /** Shows a position of the editor's timeline (the clips end to end, before the trim). */
    val seekTimeline: (Long) -> Unit = { timelineMs -> seekPreview((timelineMs - trimStartMs).coerceAtLeast(0L)) }

    // -- Keyframes: the playhead on their clocks --------------------------------------
    // While the preview stands still: playing, the panels keep the values they showed (the
    // preview itself moves), so that the clock does not recompose the whole editor.
    val keyClock by produceState(PreviewSnapshot(), playback) {
        snapshotFlow { if (playback.isPlaying.value) null else playback.snapshot.value }
            .collect { now -> if (now != null) value = now }
    }
    /**
     * Where the keyframes of something that starts at [startMs] and lasts [lengthMs] on the
     * video are at [now] (by default the standing playhead the panels show; a change made
     * while playing goes where the playhead is then).
     */
    fun keyMsOf(startMs: Long, lengthMs: Long, now: PreviewSnapshot): Long = (now.outputMs - startMs).coerceIn(0L, lengthMs.coerceAtLeast(0L))
    fun keyMsOf(text: TextOverlay, now: PreviewSnapshot = keyClock): Long = keyMsOf(text.startMs, text.span.lengthMs, now)
    fun keyMsOf(picture: ImageOverlay, now: PreviewSnapshot = keyClock): Long = keyMsOf(picture.startMs, picture.span.lengthMs, now)
    fun keyMsOf(sound: AudioClip, now: PreviewSnapshot = keyClock): Long = keyMsOf(sound.span.startMs, sound.span.endMs - sound.span.startMs, now)
    /** Where in its file clip [clip] is at [now]: the playhead's place when it is on the clip, else its start. */
    fun keyMsOf(clip: Clip, now: PreviewSnapshot = keyClock): Long =
        if (playingClips.getOrNull(now.segment)?.id == clip.id) now.clipFileMs else clip.startMs

    // -- What the tools edit: the selected clip or sound, or the whole video -----------
    // With keyframes, each as it is at the playhead: what the panels show and change.
    var selectedClipId by rememberSaveable(inputUri) { mutableStateOf<String?>(null) }
    // A sound on the timeline has the focus instead of a clip: the tools are then its own.
    var selectedSoundId by rememberSaveable(inputUri) { mutableStateOf<String?>(null) }
    val selectedSound = audioClips.firstOrNull { it.id == selectedSoundId }?.let { it.at(keyMsOf(it)) }
    // Or a text or picture.
    var selectedOverlayId by rememberSaveable(inputUri) { mutableStateOf<String?>(null) }
    val selectedText = textOverlays.firstOrNull { it.id == selectedOverlayId }?.let { it.at(keyMsOf(it)) }
    val selectedImage = imageOverlays.firstOrNull { it.id == selectedOverlayId }?.let { it.at(keyMsOf(it)) }
    val selectedIndex = selectedClipId?.let { id -> timeline.indexOf(id) } ?: -1
    val selectedClip = timeline.clips.getOrNull(selectedIndex)
    val scopeClipId = selectedClip?.id
    val scopeEffects = selectedClip?.let { it.effects.at(keyMsOf(it)) } ?: wholeEffects
    // Changes go through the keyframes: what something with keyframes animates is put in the
    // keyframe at the playhead (see Keyframing.kt).
    val updateScope: ((Effects) -> Effects) -> Unit = { update ->
        val clip = selectedClip
        if (clip == null) onUpdateEffects(null, update) else onUpdateEffects(clip.id) { it.keyedEdit(keyMsOf(clip, playback.snapshot.value), update) }
    }
    val updateText: (String, String?, (TextOverlay) -> TextOverlay) -> Unit = { id, step, change ->
        onUpdateText(id, step) { it.keyedEdit(keyMsOf(it, playback.snapshot.value), change) }
    }
    val updateImage: (String, String?, (ImageOverlay) -> ImageOverlay) -> Unit = { id, step, change ->
        onUpdateImage(id, step) { it.keyedEdit(keyMsOf(it, playback.snapshot.value), change) }
    }
    /** Changes to picture [id]'s look (one undo step per key, as its other settings). */
    fun lookChange(id: String): LookChange = { step, change -> updateImage(id, step?.let { "look:$it" }) { it.copy(look = change(it.look)) } }
    val updateSound: (String, String?, (AudioClip) -> AudioClip) -> Unit = { id, step, change ->
        onUpdateAudioClip(id, step) { it.keyedEdit(keyMsOf(it, playback.snapshot.value), change) }
    }

    /**
     * The diamond next to undo: what has the focus, at [now], can take a keyframe
     * (null: nothing to put one on, or the playhead is not on it), and whether one is there.
     */
    val keyframeAt: (PreviewSnapshot) -> Boolean? = { now ->
        fun on(keys: Keyframes, atMs: Long) = keys.at(atMs) != null
        fun inside(startMs: Long, lengthMs: Long) = now.outputMs in startMs..(startMs + lengthMs)
        val text = textOverlays.firstOrNull { it.id == selectedOverlayId }
        val picture = imageOverlays.firstOrNull { it.id == selectedOverlayId }
        val sound = audioClips.firstOrNull { it.id == selectedSoundId }
        val clip = timeline.clips.firstOrNull { it.id == selectedClipId }
        when {
            text != null && text.clipId == null ->
                if (inside(text.startMs, text.span.lengthMs)) on(text.keyframes, now.outputMs - text.startMs) else null
            picture != null && picture.clipId == null ->
                if (inside(picture.startMs, picture.span.lengthMs)) on(picture.keyframes, now.outputMs - picture.startMs) else null
            sound != null ->
                if (inside(sound.span.startMs, sound.span.endMs - sound.span.startMs)) on(sound.keyframes, now.outputMs - sound.span.startMs) else null
            clip != null ->
                if (playingClips.getOrNull(now.segment)?.id == clip.id) on(clip.effects.keyframes, now.clipFileMs) else null
            else -> null
        }
    }
    /** Puts a keyframe at the playhead on what has the focus, or takes away the one there. */
    val toggleKeyframe: () -> Unit = {
        val now = playback.snapshot.value
        val text = textOverlays.firstOrNull { it.id == selectedOverlayId }
        val picture = imageOverlays.firstOrNull { it.id == selectedOverlayId }
        val sound = audioClips.firstOrNull { it.id == selectedSoundId }
        val clip = timeline.clips.firstOrNull { it.id == selectedClipId }
        when {
            text != null -> onUpdateText(text.id, null) { it.toggledKeyframe(now.outputMs - it.startMs) }
            picture != null -> onUpdateImage(picture.id, null) { it.toggledKeyframe(now.outputMs - it.startMs) }
            sound != null -> onUpdateAudioClip(sound.id, null) { it.toggledKeyframe(now.outputMs - it.span.startMs) }
            clip != null -> onUpdateEffects(clip.id) { it.toggledKeyframe(now.clipFileMs) }
        }
    }
    /**
     * The keyframe at the playhead on what has the focus (as for the diamond), and
     * how to change the way it moves on to the next one.
     */
    val easingTarget: (PreviewSnapshot) -> EasingTarget? = { now ->
        fun target(keys: Keyframes, atMs: Long, change: ((Keyframes) -> Keyframes) -> Unit): EasingTarget? {
            val frame = keys.at(atMs) ?: return null
            return EasingTarget(frame, last = keys.next(frame.atMs) == null) { easing, curve ->
                change { it.withEasing(frame.atMs, easing, curve) }
            }
        }
        fun inside(startMs: Long, lengthMs: Long) = now.outputMs in startMs..(startMs + lengthMs)
        val text = textOverlays.firstOrNull { it.id == selectedOverlayId }
        val picture = imageOverlays.firstOrNull { it.id == selectedOverlayId }
        val sound = audioClips.firstOrNull { it.id == selectedSoundId }
        val clip = timeline.clips.firstOrNull { it.id == selectedClipId }
        when {
            text != null && text.clipId == null -> if (!inside(text.startMs, text.span.lengthMs)) null else
                target(text.keyframes, now.outputMs - text.startMs) { f -> onUpdateText(text.id, "easing") { it.copy(keyframes = f(it.keyframes)) } }
            picture != null && picture.clipId == null -> if (!inside(picture.startMs, picture.span.lengthMs)) null else
                target(picture.keyframes, now.outputMs - picture.startMs) { f -> onUpdateImage(picture.id, "easing") { it.copy(keyframes = f(it.keyframes)) } }
            sound != null -> if (!inside(sound.span.startMs, sound.span.endMs - sound.span.startMs)) null else
                target(sound.keyframes, now.outputMs - sound.span.startMs) { f -> onUpdateAudioClip(sound.id, "easing") { it.copy(keyframes = f(it.keyframes)) } }
            clip != null -> if (playingClips.getOrNull(now.segment)?.id != clip.id) null else
                target(clip.effects.keyframes, now.clipFileMs) { f -> onUpdateEffects(clip.id) { it.copy(keyframes = f(it.keyframes)) } }
            else -> null
        }
    }
    val untitledText = stringResource(R.string.tool_text)
    val scopeLabel = when {
        selectedSound != null -> selectedSound.name
        selectedText != null -> selectedText.text.ifBlank { untitledText }
        selectedImage != null -> selectedImage.fileName
        selectedClip != null -> stringResource(R.string.scope_clip, selectedIndex + 1)
        else -> stringResource(R.string.scope_whole)
    }
    /** Gives the focus to clip [clipId] (null = the whole video); a selected sound lets go. */
    val selectClip: (String?) -> Unit = { clipId ->
        selectedClipId = clipId
        selectedSoundId = null
        selectedOverlayId = null
        videoSelected = false
    }
    /** Gives the focus to sound [soundId]; a selected clip, text or picture lets go. */
    val selectSound: (String?) -> Unit = { soundId ->
        selectedSoundId = soundId
        if (soundId != null) {
            selectedClipId = null
            selectedOverlayId = null
            videoSelected = false
        }
    }
    /** Gives the focus to text or picture [overlayId]; a selected clip or sound lets go. */
    val selectOverlay: (String?) -> Unit = { overlayId ->
        selectedOverlayId = overlayId
        if (overlayId != null) {
            selectedClipId = null
            selectedSoundId = null
            videoSelected = false
        }
    }
    /** Picks the video itself up on the preview (or puts it down): anything else selected lets go. */
    val selectVideo: (Boolean) -> Unit = { picked ->
        videoSelected = picked
        if (picked) {
            selectedClipId = null
            selectedSoundId = null
            selectedOverlayId = null
        }
    }

    /**
     * A text or overlay touched in the preview gets the focus. The open panel stays
     * when it works for the new item too, and closes otherwise.
     */
    val selectFromPreview: (String) -> Unit = { overlayId ->
        if (overlayId != selectedOverlayId && !isManualMode) {
            val tools = textOverlays.firstOrNull { it.id == overlayId }?.let { TEXT_TOOLS }
                ?: imageOverlays.firstOrNull { it.id == overlayId }?.let { imageToolsFor(it, withSound = !gifMode) }
                ?: emptyList()
            if (selectedTool != null && selectedTool !in tools) selectedTool = null
            selectOverlay(overlayId)
        }
    }
    val selectedClipIsStill = selectedClip?.let { c -> sources.firstOrNull { it.id == c.sourceId }?.isStill } == true

    /** Where clip [clipId] starts in the playing sequence, or null when the trim leaves it out. */
    fun playingStartOf(clipId: String): Long? {
        val index = playingClips.indexOfFirst { it.id == clipId }
        return if (index < 0) null else playingClips.take(index).sumOf { it.lengthMs }
    }

    /** Opens [tool] for [clipId] (null = the whole video), showing that clip if the playhead is elsewhere. */
    val openTool: (EditorTool, String?) -> Unit = { tool, clipId ->
        selectedClipId = clipId
        selectedSoundId = null
        selectedOverlayId = null
        selectedTool = tool
        if (clipId != null) {
            val start = playingStartOf(clipId)
            val length = playingClips.firstOrNull { it.id == clipId }?.lengthMs ?: 0L
            val playhead = playback.playheadMs.longValue
            if (start != null && (playhead < start || playhead >= start + length)) seekPreview(start)
        }
    }

    /** Where caption or picture [id] lies among those of clip [clipId] (null: the whole video), bottom first, and how many there are. */
    fun stackPosition(id: String, clipId: String?): Pair<Int, Int> {
        val texts = textOverlays.filter { it.clipId == clipId }
        val images = imageOverlays.filter { it.clipId == clipId }
        val order = stackOrder(texts.map { it.layer }, images.map { it.layer })
        val position = order.indexOfFirst { slot -> if (slot.isText) texts[slot.index].id == id else images[slot.index].id == id }
        return position to order.size
    }

    // -- Transitions ------------------------------------------------------------------------
    val transitionScope = rememberCoroutineScope()
    /**
     * Plays the transition after clip [clipId] from a moment before it starts, so that
     * a transition just picked is seen at once.
     */
    fun playTransition(clipId: String) {
        val index = playingClips.indexOfFirst { it.id == clipId }
        if (index < 0 || index >= playingClips.lastIndex) return
        val clip = playingClips[index]
        val start = playingStartOf(clipId) ?: return
        val transitionMs = clip.effects.transition?.durationMs ?: 0L
        // Footage of the clip played in that time (its end in playing order).
        val lead = ((transitionMs + TRANSITION_LEAD_MS) * clip.effects.effectiveSpeed).toLong().coerceAtMost(clip.lengthMs)
        seekPreview(if (clip.effects.reversed) start + lead else start + clip.lengthMs - lead)
        transitionScope.launch {
            delay(TRANSITION_PLAY_DELAY_MS)
            playback.isPlaying.value = true
        }
    }
    /** The join after clip [clipId] was tapped on the timeline: its transition tool opens there. */
    val openTransition: (String) -> Unit = { clipId ->
        openTool(EditorTool.TRANSITION, clipId)
        val index = playingClips.indexOfFirst { it.id == clipId }
        val start = playingStartOf(clipId)
        if (index >= 0 && start != null) {
            val clip = playingClips[index]
            // Just before the join, on the clip going out.
            val lead = ((clip.effects.transition?.durationMs ?: 0L) * clip.effects.effectiveSpeed).toLong() + JOIN_LEAD_MS
            seekPreview(if (clip.effects.reversed) start + lead.coerceAtMost(clip.lengthMs) else (start + clip.lengthMs - lead).coerceAtLeast(start))
        }
    }

    // -- Crop -----------------------------------------------------------------------------
    // Shape of the frame being cropped, before the crop: the clip's file, or the project
    // frame (the first clip's file) for the whole video, turned as the scope says.
    fun fileRatio(clip: Clip?): Float? = clip?.let { c -> sources.firstOrNull { it.id == c.sourceId }?.frameRatio }
    val projectFrameRatio = fileRatio(playingClips.firstOrNull()) ?: DEFAULT_FRAME_RATIO
    // Width of the exported picture before a GIF makes it smaller: the first clip's file,
    // turned and cropped as the whole video is (0 while the file is not known).
    val gifVideoWidth = playingClips.firstOrNull()?.let { c -> sources.firstOrNull { it.id == c.sourceId }?.info }?.let { info ->
        val turned = wholeEffects.rotation == Rotation.DEG_90 || wholeEffects.rotation == Rotation.DEG_270
        val width = if (turned) info.height else info.width
        (width * (wholeEffects.effectiveCrop?.width ?: 1f)).toInt()
    } ?: 0
    val cropFrameRatio = turnedRatio(fileRatio(selectedClip) ?: projectFrameRatio, scopeEffects.rotation)

    // Locked crop shape, per clip ("" = the whole video).
    val cropAspects = remember(inputUri) { mutableStateMapOf<String, CropAspect>() }
    val cropAspect = cropAspects[scopeClipId ?: ""] ?: CropAspect.FREE

    // A locked shape is kept when the frame turns or another scope is chosen.
    LaunchedEffect(cropFrameRatio, cropAspect, scopeClipId) {
        val fitted = fitCrop(cropAspect, scopeEffects.crop, cropFrameRatio)
        if (cropAspect.ratio != null && fitted != scopeEffects.crop) updateScope { it.copy(crop = fitted) }
    }

    // The selected clip went away (deleted, or taken back by undo): its panel must not
    // carry on editing the whole video in its place.
    LaunchedEffect(selectedClipId, selectedClip == null) {
        if (selectedClipId != null && selectedClip == null) {
            selectedTool = null
            selectedClipId = null
        }
    }
    // The same for a sound, a text or a picture. One just added may reach the lists a
    // moment after it is selected, so the lists are looked at again after a grace time.
    val latestSounds by rememberUpdatedState(audioClips)
    val latestTexts by rememberUpdatedState(textOverlays)
    val latestImages by rememberUpdatedState(imageOverlays)
    LaunchedEffect(selectedSoundId, selectedSound == null) {
        val id = selectedSoundId
        if (id != null && selectedSound == null) {
            delay(SELECTION_GRACE_MS)
            if (latestSounds.none { it.id == id }) {
                if (selectedTool in SOUND_TOOLS || selectedTool in SOUND_EFFECT_TOOLS) selectedTool = null
                selectedSoundId = null
            }
        }
    }
    LaunchedEffect(selectedOverlayId, selectedText == null && selectedImage == null) {
        val id = selectedOverlayId
        if (id != null && selectedText == null && selectedImage == null) {
            delay(SELECTION_GRACE_MS)
            if (latestTexts.none { it.id == id } && latestImages.none { it.id == id }) {
                if (selectedTool in OVERLAY_TOOLS) selectedTool = null
                selectedOverlayId = null
            }
        }
    }

    val haptic = LocalHapticFeedback.current

    // -- Colour key sample ------------------------------------------------------------------
    // The picture the open key panel shows and picks colours from, as it looks before the
    // key: the frame under the playhead (of the selected clip, or of the clip there for the
    // whole video) with its colours, or the selected picture / video laid over the video.
    val context = LocalContext.current
    var keySampleRequest by remember { mutableIntStateOf(0) }
    val keySampleBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, listOf(selectedTool, scopeClipId, selectedOverlayId, keySampleRequest)) {
        value = null
        value = when (selectedTool) {
            EditorTool.KEY -> {
                val playhead = playback.playheadMs.longValue
                var start = 0L
                var found: Pair<Clip, Long>? = null
                for (clip in playingClips) {
                    val length = clip.lengthMs
                    val here = playhead >= start && playhead < start + length
                    if ((scopeClipId == null && here) || clip.id == scopeClipId) {
                        found = clip to clip.startMs + if (here) playhead - start else 0L
                        break
                    }
                    start += length
                }
                val (clip, at) = found
                    ?: (selectedClip ?: playingClips.lastOrNull())?.let { it to it.startMs }
                    ?: return@produceState
                val source = sources.firstOrNull { it.id == clip.sourceId } ?: return@produceState
                // The key comes after the colours: the clip's, and for the whole video its own too.
                val matrix = if (scopeClipId == null) combineColorMatrices(wholeEffects.eqMatrix(), clip.effects.eqMatrix()) else clip.effects.eqMatrix()
                keySample(context, source.uri, at, source.isStill, matrix)
            }
            EditorTool.OVERLAY_KEY -> {
                val image = imageOverlays.firstOrNull { it.id == selectedOverlayId }
                val uri = image?.uri ?: return@produceState
                val own = Effects(color = ColorSettings(brightness = image.brightness, contrast = image.contrast, saturation = image.saturation)).eqMatrix()
                keySample(context, uri, image.video?.trimStartMs ?: 0L, still = !image.isVideo, matrix = own)
            }
            else -> null
        }
    }

    // -- HDR look sample ----------------------------------------------------------------
    // The frame under the playhead (once it stops) with the colours, for the HDR panel to
    // show with the look: brighter on a screen that shows HDR.
    val screenHdr = remember(context) { com.cuscus.ffmpeggui.hdr.screenShowsHdr(context) }
    // The zones the look brightens, tinted on the preview: at first on a screen that cannot show it.
    var hdrZonesShown by rememberSaveable { mutableStateOf(!screenHdr) }
    val hdrToolOpen = selectedTool == EditorTool.HDR
    val latestPlayingClips by rememberUpdatedState(playingClips)
    val latestSources by rememberUpdatedState(sources)
    val latestWholeEffects by rememberUpdatedState(wholeEffects)
    val hdrSampleBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, hdrToolOpen) {
        if (!hdrToolOpen) {
            value = null
            return@produceState
        }
        snapshotFlow { if (playback.isPlaying.value) null else playback.playheadMs.longValue }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { playhead ->
                delay(HDR_SAMPLE_SETTLE_MS)
                var start = 0L
                var found: Pair<Clip, Long>? = null
                for (clip in latestPlayingClips) {
                    if (playhead >= start && playhead < start + clip.lengthMs) {
                        found = clip to clip.startMs + playhead - start
                        break
                    }
                    start += clip.lengthMs
                }
                val (clip, at) = found ?: latestPlayingClips.lastOrNull()?.let { it to it.startMs } ?: return@collectLatest
                val source = latestSources.firstOrNull { it.id == clip.sourceId } ?: return@collectLatest
                val matrix = combineColorMatrices(latestWholeEffects.eqMatrix(), clip.effects.eqMatrix())
                keySample(context, source.uri, at, source.isStill, matrix)?.let { value = it }
            }
    }
    // While the panel is open and the look on, the window asks for HDR: the sample shines.
    com.cuscus.ffmpeggui.hdr.HdrWindow(on = hdrToolOpen && wholeEffects.hdrLook != null, headroom = HDR_VIDEO_HEADROOM)
    val hdrHeadroom = if (hdrToolOpen) com.cuscus.ffmpeggui.hdr.rememberHdrHeadroom() else null

    // Back closes the open panel, then the clip or sound selection, then the editor.
    androidx.activity.compose.BackHandler(
        enabled = selectedTool != null || scopeClipId != null || selectedSoundId != null || selectedOverlayId != null,
    ) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when {
            selectedTool != null -> selectedTool = null
            selectedSoundId != null -> selectedSoundId = null
            selectedOverlayId != null -> selectedOverlayId = null
            else -> selectedClipId = null
        }
    }

    // Back puts the picked-up video down.
    androidx.activity.compose.BackHandler(enabled = videoSelected && !previewFullscreen) { videoSelected = false }

    // Back leaves the full screen preview before anything else.
    androidx.activity.compose.BackHandler(enabled = previewFullscreen) { previewFullscreen = false }

    val fallbackName = stringResource(R.string.timeline_fallback_name, timeline.clips.size + 1)
    // A new clip is a video or a picture (which becomes a still clip).
    val clipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onAddClip(uri, uri.lastPathSegment?.substringAfterLast("/") ?: fallbackName)
    }
    // A new sound: an audio file, or the sound of a video, placed at the playhead.
    val fallbackSoundName = stringResource(R.string.sound_fallback_name, audioClips.size + 1)
    val soundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val atMs = playback.snapshot.value.outputMs
            onAddAudioClip(uri, uri.lastPathSegment?.substringAfterLast("/") ?: fallbackSoundName, atMs) { id ->
                selectSound(id)
                selectedTool = null
            }
        }
    }
    val addSound: () -> Unit = { soundPicker.launch(arrayOf("audio/*", "video/*")) }
    // A new sound from the tool row too (the empty sound lane of the timeline still adds one).
    val addSoundItem = ToolbarItem(R.string.tool_add_sound, Icons.Rounded.LibraryMusic, enabled = !isManualMode, family = ToolFamily.SOUND, onClick = addSound)
    // A new caption, placed at the playhead and opened for typing.
    val newTextLabel = stringResource(R.string.text_placeholder)
    val addText: () -> Unit = {
        val id = onAddText(newTextLabel, playback.snapshot.value.outputMs)
        selectOverlay(id)
        selectedTool = EditorTool.TEXT_EDIT
    }
    // A new picture or video laid over the video, at the playhead; or another file for the selected one.
    val fallbackImageName = stringResource(R.string.image_fallback_name, imageOverlays.size + 1)
    val overlayReadFailed = stringResource(R.string.overlay_read_failed)
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast("/") ?: fallbackImageName
            onAddOverlay(
                uri,
                name,
                playback.snapshot.value.outputMs,
                { id ->
                    selectOverlay(id)
                    selectedTool = null
                },
                { android.widget.Toast.makeText(context, overlayReadFailed, android.widget.Toast.LENGTH_SHORT).show() },
            )
        }
    }
    val addImage: () -> Unit = { imagePicker.launch(OVERLAY_TYPES) }
    val imageReplacePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val id = selectedOverlayId
        if (uri != null && id != null) {
            onReplaceOverlay(id, uri, uri.lastPathSegment?.substringAfterLast("/") ?: fallbackImageName) {
                android.widget.Toast.makeText(context, overlayReadFailed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Another file for the selected sound.
    val replacePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val id = selectedSoundId
        if (uri != null && id != null) onReplaceAudioClip(id, uri, uri.lastPathSegment?.substringAfterLast("/") ?: fallbackSoundName)
    }

    // -- Tool rows ----------------------------------------------------------------------------
    // In manual mode only the command can change; everything else is frozen.
    fun item(tool: EditorTool, clipId: String?) = ToolbarItem(
        labelRes = tool.labelRes,
        icon = tool.icon,
        enabled = !isManualMode || tool == EditorTool.COMMAND,
        family = tool.family,
    ) { openTool(tool, clipId) }

    val splitHere = ToolbarItem(R.string.tool_split, Icons.Rounded.ContentCut, enabled = !isManualMode) {
        onSplitClip(trimStartMs + playback.playheadMs.longValue)
    }
    // The frame under the playhead becomes a still clip, cut into the sequence right there.
    val freezeName = stringResource(R.string.freeze_frame_name)
    val freezeHere = ToolbarItem(R.string.tool_freeze, Icons.Rounded.AcUnit, enabled = !isManualMode, family = ToolFamily.PICTURE) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onFreezeFrame(trimStartMs + playback.playheadMs.longValue, freezeName)
    }
    val audioWholeItems = listOf(
        addSoundItem,
        splitHere,
        item(EditorTool.TRIM_ALL, null),
        item(EditorTool.AUDIO_SILENCE, null),
        item(EditorTool.AUDIO, null),
        item(EditorTool.AUDIO_EQ, null),
        item(EditorTool.AUDIO_CLEAN, null),
        item(EditorTool.AUDIO_DYNAMICS, null),
        item(EditorTool.AUDIO_SPACE, null),
        item(EditorTool.AUDIO_FX, null),
        item(EditorTool.AUDIO_PITCH, null),
        item(EditorTool.SPEED, null),
        item(EditorTool.FADE, null),
        item(EditorTool.AUDIO_STEREO, null),
        item(EditorTool.AUDIO_TAGS, null),
        item(EditorTool.AUDIO_COVER, null),
        item(EditorTool.AUDIO_PARTS, null),
        item(EditorTool.COMMAND, null),
    )
    // In the video editor the sound's tools sit in a row of their own, opened by "Audio".
    val videoAudioItems = listOf(
        addSoundItem,
        item(EditorTool.VOLUME, null),
        item(EditorTool.AUDIO_EQ, null),
        item(EditorTool.AUDIO_CLEAN, null),
        item(EditorTool.AUDIO_DYNAMICS, null),
        item(EditorTool.AUDIO_SPACE, null),
        item(EditorTool.AUDIO_FX, null),
        item(EditorTool.AUDIO_PITCH, null),
        item(EditorTool.AUDIO_STEREO, null),
    )
    val wholeItems = if (audioMode) audioWholeItems else listOfNotNull(
        splitHere,
        freezeHere,
        item(EditorTool.TRIM_ALL, null),
        if (gifMode) item(EditorTool.GIF, null) else null,
        if (gifMode) {
            null
        } else {
            ToolbarItem(R.string.tool_audio, Icons.Rounded.GraphicEq, enabled = !isManualMode, family = ToolFamily.SOUND) {
                audioRowOpen = true
            }
        },
        if (gifMode) null else addSoundItem,
        ToolbarItem(R.string.tool_text, Icons.Rounded.Title, enabled = !isManualMode, family = ToolFamily.OVERLAY, onClick = addText),
        ToolbarItem(R.string.tool_image, Icons.Rounded.Image, enabled = !isManualMode, family = ToolFamily.OVERLAY, onClick = addImage),
        item(EditorTool.COLOR, null),
        item(EditorTool.KEY, null),
        // A GIF holds no HDR.
        if (gifMode) null else item(EditorTool.HDR, null),
        item(EditorTool.TRANSFORM, null),
        item(EditorTool.SPEED, null),
        item(EditorTool.FADE, null),
        item(EditorTool.CANVAS, null),
        item(EditorTool.SUBTITLE, null),
        // Tags go into every file that has sound (a GIF holds none).
        if (format.hasAudio) item(EditorTool.AUDIO_TAGS, null) else null,
        // A GIF is always encoded, as one file.
        if (gifMode) null else item(EditorTool.EXTRACT, null),
        if (gifMode) null else item(EditorTool.REMUX, null),
        item(EditorTool.COMMAND, null),
    )
    val clipItems = selectedClip?.let { clip ->
        if (audioMode) {
            return@let listOf(
                item(EditorTool.CLIP_TRIM, clip.id),
                item(EditorTool.SPEED, clip.id),
                item(EditorTool.VOLUME, clip.id),
                item(EditorTool.FADE, clip.id),
            )
        }
        listOfNotNull(
            item(EditorTool.CLIP_TRIM, clip.id),
            freezeHere,
            // A still picture can slowly zoom or pan over itself.
            if (selectedClipIsStill) item(EditorTool.MOTION, clip.id) else null,
            item(EditorTool.SPEED, clip.id),
            if (gifMode) null else item(EditorTool.VOLUME, clip.id),
            item(EditorTool.TRANSFORM, clip.id),
            item(EditorTool.FRAMING, clip.id),
            item(EditorTool.COLOR, clip.id),
            item(EditorTool.KEY, clip.id),
            item(EditorTool.FADE, clip.id),
            item(EditorTool.TRANSITION, clip.id),
        )
    }
    // The selected sound's own tools.
    fun soundItem(tool: EditorTool) = ToolbarItem(
        labelRes = tool.labelRes,
        icon = tool.icon,
        enabled = !isManualMode,
        family = tool.family,
    ) { selectedTool = tool }
    val soundItems = selectedSound?.let {
        listOf(
            soundItem(EditorTool.SOUND_TRIM),
            soundItem(EditorTool.SOUND_VOLUME),
            soundItem(EditorTool.SOUND_SPEED),
            soundItem(EditorTool.SOUND_VOICE),
            soundItem(EditorTool.SOUND_FADE),
            soundItem(EditorTool.SOUND_EQ),
            soundItem(EditorTool.AUDIO_CLEAN),
            soundItem(EditorTool.AUDIO_DYNAMICS),
            soundItem(EditorTool.AUDIO_SPACE),
            soundItem(EditorTool.AUDIO_FX),
            soundItem(EditorTool.AUDIO_STEREO),
            soundItem(EditorTool.SOUND_REPEAT),
            ToolbarItem(R.string.tool_replace, Icons.Rounded.SwapHoriz, enabled = !isManualMode, family = ToolFamily.SOUND) {
                replacePicker.launch(arrayOf("audio/*", "video/*"))
            },
        )
    }
    // The selected text's or picture's own tools.
    fun overlayItem(tool: EditorTool) = ToolbarItem(
        labelRes = tool.labelRes,
        icon = tool.icon,
        enabled = !isManualMode,
        family = tool.family,
    ) { selectedTool = tool }
    val overlayItems = when {
        selectedText != null -> TEXT_TOOLS.map(::overlayItem)
        selectedImage != null -> imageToolsFor(selectedImage, withSound = !gifMode).map(::overlayItem) +
                ToolbarItem(R.string.tool_replace, Icons.Rounded.SwapHoriz, enabled = !isManualMode, family = ToolFamily.OVERLAY) {
                    imageReplacePicker.launch(OVERLAY_TYPES)
                }
        else -> null
    }
    // What is done to the selected text or picture as a whole (the playhead is read on tap).
    val overlayActions = (selectedText?.id ?: selectedImage?.id)?.let { id ->
        listOf(
            ClipAction(R.string.tool_split, Icons.Rounded.ContentCut, enabled = !isManualMode) {
                onSplitOverlay(id, playback.snapshot.value.outputMs)
            },
            ClipAction(R.string.tool_duplicate, Icons.Rounded.ContentCopy, enabled = !isManualMode) {
                onDuplicateOverlay(id)?.let(selectOverlay)
            },
            ClipAction(R.string.tool_delete, Icons.Rounded.DeleteOutline, enabled = !isManualMode) {
                if (selectedText != null) onRemoveTextOverlay(id) else onRemoveImageOverlay(id)
                selectedOverlayId = null
            },
        )
    }

    // The effects tools edit the selected sound's own effects, or else the final sound's.
    val fxSound = selectedSound
    val fxEffects = fxSound?.effects ?: audioEffects
    val fxChange: AudioChange = if (fxSound != null) {
        { key, change -> updateSound(fxSound.id, key?.let { "fx:$it" }) { it.copy(effects = change(it.effects)) } }
    } else {
        onAudioEffects
    }

    // What is done to the selected sound as a whole, at the playhead for a cut.
    // (The playhead is read on tap, not here, so that playback does not recompose the editor.)
    val soundActions = selectedSound?.let { sound ->
        listOf(
            ClipAction(R.string.tool_split, Icons.Rounded.ContentCut, enabled = !isManualMode) {
                onSplitAudioClip(sound.id, playback.snapshot.value.outputMs)
            },
            ClipAction(R.string.tool_duplicate, Icons.Rounded.ContentCopy, enabled = !isManualMode) {
                onDuplicateAudioClip(sound.id)?.let(selectSound)
            },
            ClipAction(R.string.tool_delete, Icons.Rounded.DeleteOutline, enabled = !isManualMode) {
                onRemoveAudioClip(sound.id)
                selectedSoundId = null
            },
        )
    }

    // What is done to the selected clip as a whole: the floating toolbar over the preview.
    // The video picked up: back to the middle, or the canvas's shape.
    val videoActions = if (!videoSelected) null else listOf(
        ClipAction(R.string.placement_reset, Icons.Rounded.CenterFocusStrong, enabled = wholeEffects.placement != null) {
            onUpdateEffects(null) { it.copy(placement = null) }
        },
        ClipAction(R.string.tool_canvas, Icons.Rounded.AspectRatio, enabled = true) {
            selectedTool = EditorTool.CANVAS
        },
    )
    val clipActions = selectedClip?.let { clip ->
        listOf(
            ClipAction(R.string.tool_split, Icons.Rounded.ContentCut, enabled = !isManualMode) {
                onSplitClip(trimStartMs + playback.playheadMs.longValue)
            },
            ClipAction(R.string.tool_duplicate, Icons.Rounded.ContentCopy, enabled = !isManualMode) {
                onDuplicateClip(clip.id)
            },
            ClipAction(R.string.tool_move_left, Icons.Rounded.ChevronLeft, enabled = !isManualMode && selectedIndex > 0) {
                onMoveClip(clip.id, -1)
            },
            ClipAction(
                R.string.tool_move_right, Icons.Rounded.ChevronRight,
                enabled = !isManualMode && selectedIndex < timeline.clips.lastIndex,
            ) {
                onMoveClip(clip.id, 1)
            },
            ClipAction(R.string.tool_delete, Icons.Rounded.DeleteOutline, enabled = !isManualMode && timeline.clips.size > 1) {
                onRemoveClip(clip.id)
                selectedClipId = null
            },
        )
    }

    // Sounds are placed on the exported video's clock; the timeline draws them on its own,
    // which differs once clips are sped up or slowed down or the start is trimmed.
    val clock = remember(playingClips, wholeEffects, trimStartMs) { outputClock(playingClips, wholeEffects, trimStartMs) }
    val timelineState = rememberTimelineViewState()

    val contentAlpha by animateFloatAsState(if (isManualMode) 0.3f else 1f, label = "")
    // Full height of the open panel's content; the panel grows to show it all (see below).
    var panelContentPx by remember { mutableIntStateOf(0) }

    // The four parts of the editor, laid out below for the shape of the window.
    val topBar: @Composable () -> Unit = {
        EditorTopBar(
            fileName = fileName,
            formatLabel = stringResource(format.labelRes),
            onClose = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            },
            onExport = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showExportDialog = true
            },
        )
    }
    // What the sound view of an audio file shows around its waveform.
    val effectNames = if (audioMode) audioEffectNames(audioEffects, normalizeAudio) else emptyList()
    // The card shows what the top bar does not: the tags and how the sound is encoded.
    val formatLabel = when {
        format.isLossyAudio -> stringResource(R.string.audio_quality_lossy, audioEncoding.bitrateKbps)
        format.isLosslessAudio -> stringResource(R.string.value_bits, audioEncoding.bitDepth)
        else -> stringResource(format.labelRes)
    }
    // What the media notification shows: the tags, else the file; the cover of an audio
    // file, the first frame of a video, else the app's icon. A GIF has no sound to play on.
    val nowPlayingArtist = stringResource(R.string.now_playing_artist, stringResource(R.string.app_name))
    val nowPlaying = if (gifMode) null else NowPlaying(
        title = tags.title.ifBlank { fileName },
        artist = tags.artist.ifBlank { nowPlayingArtist },
        artwork = when {
            audioMode -> coverUri?.let { Artwork.Picture(it) } ?: Artwork.AppIcon
            else -> playingClips.firstOrNull()?.let { clip ->
                sources.firstOrNull { it.id == clip.sourceId }?.let { Artwork.Frame(it.uri, clip.startMs, it.isStill) }
            } ?: Artwork.AppIcon
        },
    )
    val audioCard = if (!audioMode) null else AudioCard(
        title = tags.title,
        subtitle = listOf(tags.artist, tags.album, tags.year).filter { it.isNotBlank() }.joinToString(" \u00B7 "),
        formatLabel = formatLabel,
        activeEffects = effectNames,
        cover = coverUri,
        split = when (splitMode) {
            SplitMode.DISABLED -> Split.Disabled
            SplitMode.BY_DURATION -> Split.ByDuration(splitValue)
            SplitMode.BY_PARTS -> Split.ByParts(splitValue)
        },
    )
    val previewPane: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier = modifier) {
            PreviewContainer(
                gif = if (gifMode) gif else null,
                clips = playingClips,
                sources = sources,
                playback = playback,
                format = format,
                wholeEffects = wholeEffects,
                canvas = projectRatio,
                editClipId = scopeClipId,
                showCropOverlay = selectedTool == EditorTool.TRANSFORM,
            // The video picked up with a tap (or the Canvas tool open): moved on its canvas by hand.
            placementEditable = !audioMode && !previewFullscreen && (videoSelected || selectedTool == EditorTool.CANVAS),
            hdrZones = wholeEffects.hdrLook?.takeIf { hdrToolOpen && hdrZonesShown && !previewFullscreen },
            onSelectVideo = if (audioMode || previewFullscreen || isManualMode) null else selectVideo,
            onPlacementChange = { placement -> onUpdateEffects(null) { it.copy(placement = placement.takeUnless { p -> p.isNeutral }) } },
                cropAspect = cropAspect,
                onCropChange = { crop -> updateScope { it.copy(crop = crop) } },
                previewSeek = previewSeek,
                normalizeAudio = normalizeAudio,
                normalizationGainDb = normalizationGainDb,
                audioClips = audioClips,
                textOverlays = textOverlays,
                imageOverlays = imageOverlays,
                subtitleCues = subtitles.fileCues,
                spokenWords = subtitles.shownWords,
                subtitleStyle = subtitles.style,
                subtitleFileStyled = subtitles.fileStyled,
                subtitleFonts = subtitles.fonts,
                subtitlesEditable = selectedTool == EditorTool.SUBTITLE,
                onSubtitleStyle = subtitles.onStyle,
                useStreamCopy = useStreamCopy,
                frameExtractionMode = frameExtractionMode,
                frameExtractionTimecode = frameExtractionTimecode,
                selectedOverlayId = selectedOverlayId,
                onUpdateText = updateText,
                onUpdateImage = updateImage,
                onSelectOverlay = selectFromPreview,
                trimStartMs = trimStartMs,
                audioEffects = audioEffects,
                waveformOf = { timelineState.waveform(it) },
                audioCard = audioCard,
                nowPlaying = nowPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .graphicsLayer(alpha = contentAlpha)
                    .pointerInput(isManualMode) {
                        if (isManualMode) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
            )
            // The selected clip's (or sound's) own actions float over the bottom of the
            // picture while no panel is open.
            val motion = MaterialTheme.motionScheme
            val floatingActions = clipActions ?: soundActions ?: overlayActions ?: videoActions
            androidx.compose.animation.AnimatedVisibility(
                visible = floatingActions != null && selectedTool == null && !previewFullscreen,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            ) {
                // While it slides away the selection is already gone: it keeps showing the
                // last actions, switched off (a plain holder, not state, so that nothing
                // recomposes).
                val lastActions = remember { arrayOf<List<ClipAction>>(emptyList()) }
                if (floatingActions != null) lastActions[0] = floatingActions
                ClipActionsToolbar(
                    actions = floatingActions ?: lastActions[0].map { ClipAction(it.labelRes, it.icon, enabled = false, onClick = it.onClick) },
                )
            }
            // Full screen: a round button in the bottom corner of the picture (a sound file has
            // none), making way for the actions of a selection.
            androidx.compose.animation.AnimatedVisibility(
                visible = !audioMode && !previewFullscreen && floatingActions == null && selectedTool != EditorTool.TRANSFORM,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        previewFullscreen = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    ),
                ) {
                    Icon(Icons.Rounded.Fullscreen, contentDescription = stringResource(R.string.preview_fullscreen))
                }
            }
        }
    }
    val controls: @Composable () -> Unit = {
        ControlRow(
            playback = playback,
            canUndo = canUndo && !isManualMode,
            canRedo = canRedo && !isManualMode,
            onUndo = {
                onUndo()
                historyRevision++
            },
            onRedo = {
                onRedo()
                historyRevision++
            },
            // Keyframes: not for an audio file (no picture to animate) nor while typing a command.
            keyframeAt = if (audioMode || isManualMode) { _ -> null } else keyframeAt,
            onToggleKeyframe = {
                toggleKeyframe()
                historyRevision++
            },
            // On a keyframe: how it moves on to the next.
            onEasing = {
                playback.isPlaying.value = false
                selectedTool = EditorTool.EASING
            },
        )
    }
    // Timeline and tools, or the panel of the open tool: always the same size, so the
    // preview never changes size.
    /** The timeline and the tool row under it. */
    val timelineArea: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            EditorTimeline(
                timeline = timeline,
                sources = sources,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                playback = playback,
                viewState = timelineState,
                selectedClipId = scopeClipId,
                textOverlays = if (audioMode) emptyList() else textOverlays,
                imageOverlays = if (audioMode) emptyList() else imageOverlays,
                sounds = audioClips,
                clock = clock,
                kinds = when {
                    audioMode -> listOf(LaneKind.SOUND)
                    gifMode -> LaneKind.entries.filter { it != LaneKind.SOUND }
                    else -> LaneKind.entries
                },
                soundClips = audioMode,
                selectedItemId = selectedSound?.id ?: selectedText?.id ?: selectedImage?.id,
                onSelectClip = selectClip,
                onTrimClip = onTrimClip,
                // Joins between clips: their transitions (none for a sound file's clips).
                onJunction = if (audioMode) null else openTransition,
                onSelectItem = { kind, id -> if (kind == LaneKind.SOUND) selectSound(id) else selectOverlay(id) },
                onEditSound = { id, span -> updateSound(id, null) { it.copy(span = span, keyframes = it.keyframes.respanned(it.span.startMs, it.span.endMs, span.startMs, span.endMs)) } },
                onEditOverlay = onSetOverlaySpan,
                onScrub = seekTimeline,
                onAddClip = { clipPicker.launch(if (audioMode) arrayOf("audio/*", "video/*") else arrayOf("video/*", "image/*")) },
                onAdd = { kind ->
                    when (kind) {
                        LaneKind.TEXT -> addText()
                        LaneKind.IMAGE -> addImage()
                        LaneKind.SOUND -> addSound()
                    }
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            EditorToolbar(
                items = soundItems ?: overlayItems ?: clipItems ?: (if (audioRowOpen && !audioMode) videoAudioItems else wholeItems),
                onBack = when {
                    selectedSound != null -> {
                        { selectedSoundId = null }
                    }
                    overlayItems != null -> {
                        { selectedOverlayId = null }
                    }
                    selectedClip != null -> {
                        { selectedClipId = null }
                    }
                    audioRowOpen && !audioMode -> {
                        { audioRowOpen = false }
                    }
                    else -> null
                },
            )
        }
    }
    /** The panel of [tool]; [side]: beside the preview (a large screen) rather than under it. */
    val panelArea: @Composable (EditorTool, Boolean) -> Unit = { tool, side ->
        // A panel on its way out reads the current selection, which may already
        // be another one (its clip undone, say): it takes no more touches, so it
        // cannot edit what it no longer belongs to.
        ToolPanel(
            title = stringResource(tool.labelRes),
            scope = scopeLabel,
            icon = tool.icon,
            family = tool.family,
            onContentHeight = if (tool == selectedTool) {
                { height -> panelContentPx = height }
            } else {
                null
            },
            onDone = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedTool = null
            },
            modifier = if (tool != selectedTool) Modifier.ignoringTouches() else Modifier,
            rounded = side,
        ) {
            when (tool) {
                EditorTool.TRIM_ALL -> WholeTrimPanel(
                    timeline = timeline,
                    sources = sources,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    revision = historyRevision,
                    onSeekPlaying = seekPreview,
                    onTrimWindow = onTrimWindow,
                    waveformOf = if (audioMode) ({ uri: Uri -> timelineState.waveform(uri) }) else null,
                )
                EditorTool.CLIP_TRIM -> {
                    val clip = selectedClip
                    val source = clip?.let { c -> sources.firstOrNull { it.id == c.sourceId } }
                    if (clip != null && source != null) {
                        ClipTrimPanel(
                            clip = clip,
                            source = source,
                            clipStartPlayingMs = playingStartOf(clip.id),
                            cutIntoMs = playingClips.firstOrNull { it.id == clip.id }
                                ?.let { (it.startMs - clip.startMs).coerceAtLeast(0L) } ?: 0L,
                            revision = historyRevision,
                            onTrimClip = { startMs, endMs -> onTrimClip(clip.id, startMs, endMs) },
                            onSeekPlaying = seekPreview,
                            soundOnly = audioMode,
                            waveform = if (audioMode) timelineState.waveform(source.uri) else null,
                        )
                    }
                }
                EditorTool.GIF -> GifSettingsContent(gif = gif, videoWidth = gifVideoWidth, onGif = onGif)
                EditorTool.CANVAS -> FormatSectionContent(
                    projectRatio = projectRatio,
                    onProjectRatioChange = onProjectRatioChange,
                    placement = wholeEffects.placement,
                    onPlacement = { placement -> onUpdateEffects(null) { it.copy(placement = placement?.takeUnless { p -> p.isNeutral }) } },
                )
                EditorTool.TRANSFORM -> TransformSectionContent(
                    rotation = scopeEffects.rotation,
                    flipHorizontal = scopeEffects.flipHorizontal,
                    flipVertical = scopeEffects.flipVertical,
                    aspectRatio = cropAspect,
                    onRotation = { rotation -> updateScope { it.copy(rotation = rotation) } },
                    onFlipHorizontal = { flip -> updateScope { it.copy(flipHorizontal = flip) } },
                    onFlipVertical = { flip -> updateScope { it.copy(flipVertical = flip) } },
                    onAspectRatio = { aspect ->
                        cropAspects[scopeClipId ?: ""] = aspect
                        // A new locked shape starts centred.
                        if (aspect.ratio != null) {
                            updateScope { it.copy(crop = fitCrop(aspect, null, cropFrameRatio)) }
                        }
                    },
                )
                EditorTool.AUDIO, EditorTool.VOLUME -> AudioSectionContent(
                    clipScope = scopeClipId != null,
                    removeAudio = scopeEffects.muted,
                    volumeLevel = scopeEffects.volume,
                    normalizeAudio = normalizeAudio,
                    sounds = audioClips,
                    onRemoveAudio = { muted -> updateScope { it.copy(muted = muted) } },
                    onVolumeLevel = { volume -> updateScope { it.copy(volume = volume) } },
                    onNormalizeAudio = onNormalizeAudio,
                    onAddSound = addSound,
                    onSelectSound = { id ->
                        selectedTool = null
                        selectSound(id)
                    },
                    canRemoveOriginal = !audioMode || scopeClipId != null,
                )
                EditorTool.AUDIO_SILENCE -> {
                    // Measured on what plays, against the threshold being set.
                    val threshold = Math.pow(10.0, silenceThresholdDb / 20.0).toFloat()
                    val ends = silentEnds(playingClips, sources, threshold) { timelineState.waveform(it) }
                    AudioSilenceContent(
                        thresholdDb = silenceThresholdDb,
                        onThreshold = { silenceThresholdDb = it },
                        leadMs = ends?.first,
                        trailMs = ends?.second,
                        onCut = {
                            if (ends != null) {
                                val end = trimEndMs ?: timeline.durationMs
                                onTrimWindow(trimStartMs + ends.first, end - ends.second)
                            }
                        },
                        durationMs = clock.outputDurationMs,
                    )
                }
                EditorTool.AUDIO_PITCH -> AudioPitchContent(audioEffects, onAudioEffects)
                EditorTool.AUDIO_EQ -> AudioEqualizerContent(fxEffects, fxChange)
                EditorTool.AUDIO_CLEAN -> AudioCleanUpContent(fxEffects, fxChange)
                EditorTool.AUDIO_DYNAMICS -> AudioDynamicsContent(fxEffects, fxChange)
                EditorTool.AUDIO_SPACE -> AudioSpaceContent(fxEffects, fxChange)
                EditorTool.AUDIO_FX -> AudioEffectsContent(fxEffects, fxChange)
                EditorTool.AUDIO_STEREO -> AudioStereoContent(fxEffects, fxChange)
                EditorTool.AUDIO_TAGS -> AudioTagsContent(tags, onTags)
                EditorTool.AUDIO_COVER -> AudioCoverContent(coverUri, format, splitMode != SplitMode.DISABLED, onSetCover)
                EditorTool.AUDIO_PARTS -> AudioPartsContent(splitMode, splitValue, clock.outputDurationMs, onSplit)
                EditorTool.SOUND_TRIM -> selectedSound?.let { sound ->
                    SoundTrimContent(
                        sound = sound,
                        waveform = timelineState.waveform(sound.uri),
                        outputDurationMs = clock.outputDurationMs,
                        revision = historyRevision,
                        onSpan = { key, span -> updateSound(sound.id, key) { it.copy(span = span, keyframes = it.keyframes.respanned(it.span.startMs, it.span.endMs, span.startMs, span.endMs)) } },
                    )
                }
                EditorTool.SOUND_VOLUME -> selectedSound?.let { sound ->
                    SoundVolumeContent(
                        sound = sound,
                        onVolume = { volume -> updateSound(sound.id, "volume") { it.copy(volume = volume) } },
                        onMuted = { muted -> updateSound(sound.id, null) { it.copy(muted = muted) } },
                        onNormalize = { on -> onAudioClipNormalize(sound.id, on) },
                    )
                }
                EditorTool.SOUND_SPEED -> selectedSound?.let { sound ->
                    SoundSpeedContent(
                        sound = sound,
                        onSpeed = { speed -> updateSound(sound.id, "speed") { it.copy(span = it.span.withSpeed(speed)) } },
                        onKeepPitch = { keep -> updateSound(sound.id, null) { it.copy(keepPitch = keep) } },
                    )
                }
                EditorTool.SOUND_VOICE -> selectedSound?.let { sound ->
                    SoundVoiceContent(
                        sound = sound,
                        onPitch = { semitones -> updateSound(sound.id, "pitch") { it.copy(pitchSemitones = semitones) } },
                    )
                }
                EditorTool.SOUND_EQ -> selectedSound?.let { sound ->
                    SoundEqualizerContent(
                        sound = sound,
                        onEq = { bass, mid, treble ->
                            updateSound(sound.id, "eq") { it.copy(bassDb = bass, midDb = mid, trebleDb = treble) }
                        },
                    )
                }
                EditorTool.SOUND_REPEAT -> selectedSound?.let { sound ->
                    SoundRepeatContent(
                        sound = sound,
                        onRepeats = { repeats -> updateSound(sound.id, "repeats") { it.copy(span = it.span.withRepeats(repeats)) } },
                    )
                }
                EditorTool.SOUND_FADE -> selectedSound?.let { sound ->
                    SoundFadeContent(
                        sound = sound,
                        onFadeIn = { seconds -> updateSound(sound.id, "fadeIn") { it.copy(fadeInSeconds = seconds) } },
                        onFadeOut = { seconds -> updateSound(sound.id, "fadeOut") { it.copy(fadeOutSeconds = seconds) } },
                    )
                }
                EditorTool.EASING -> {
                    val currentTarget by rememberUpdatedState(easingTarget)
                    // Only what is shown: the panel does not follow every frame.
                    val shown by remember { derivedStateOf { currentTarget(playback.snapshot.value)?.let { Triple(it.keyframe.easing, it.keyframe.curve, it.last) } } }
                    KeyEasingContent(
                        easing = shown?.first,
                        curve = shown?.second,
                        last = shown?.third == true,
                        onEasing = { easing, curve ->
                            easingTarget(playback.snapshot.value)?.set?.invoke(easing, curve)
                            historyRevision++
                        },
                    )
                }
                EditorTool.SPEED -> SpeedSectionContent(
                    videoSpeed = scopeEffects.speed,
                    isReversed = scopeEffects.reversed,
                    onVideoSpeed = { speed -> updateScope { it.copy(speed = speed) } },
                    onIsReversed = { reversed -> updateScope { it.copy(reversed = reversed) } },
                    keepPitch = scopeEffects.keepPitch,
                    onKeepPitch = { keep -> updateScope { it.copy(keepPitch = keep) } },
                    // A curve is drawn along one clip.
                    curve = selectedClip?.effects?.speedCurve,
                    curveAllowed = selectedClip != null,
                    onCurve = { curve -> updateScope { it.copy(speedCurve = curve) } },
                    footageMs = selectedClip?.lengthMs,
                    marker = {
                        val clip = selectedClip
                        val now = playback.snapshot.value
                        if (clip != null && clip.lengthMs > 0 && playingClips.getOrNull(now.segment)?.id == clip.id) {
                            val offset = (now.clipFileMs - clip.startMs).toFloat() / clip.lengthMs
                            (if (clip.effects.reversed) 1f - offset else offset).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                    },
                )
                EditorTool.TRANSITION -> selectedClip?.let { clip ->
                    val index = playingClips.indexOfFirst { it.id == clip.id }
                    val hasNext = index >= 0 && index < playingClips.lastIndex
                    val effective = if (hasNext) {
                        transitionLengths(playingClips.map { it.effects.transition }, playingClips.map { it.effects.playedMs(it.lengthMs) })[index]
                    } else {
                        0L
                    }
                    TransitionContent(
                        transition = clip.effects.transition,
                        hasNext = hasNext,
                        effectiveMs = effective,
                        onTransition = { transition ->
                            val another = transition?.kind != clip.effects.transition?.kind
                            onUpdateEffects(clip.id) { it.copy(transition = transition) }
                            if (another && transition != null) playTransition(clip.id)
                        },
                        onApplyToAll = { onTransitionForAll(clip.effects.transition) },
                    )
                }
                EditorTool.FADE -> FadeSectionContent(
                    fadeInSeconds = scopeEffects.fadeInSeconds,
                    fadeOutSeconds = scopeEffects.fadeOutSeconds,
                    onFadeIn = { seconds -> updateScope { it.copy(fadeInSeconds = seconds) } },
                    onFadeOut = { seconds -> updateScope { it.copy(fadeOutSeconds = seconds) } },
                )
                EditorTool.COLOR -> ColorSectionContent(
                    brightness = scopeEffects.color.brightness,
                    contrast = scopeEffects.color.contrast,
                    saturation = scopeEffects.color.saturation,
                    onBrightness = { value -> updateScope { it.copy(color = it.color.copy(brightness = value)) } },
                    onContrast = { value -> updateScope { it.copy(color = it.color.copy(contrast = value)) } },
                    onSaturation = { value -> updateScope { it.copy(color = it.color.copy(saturation = value)) } },
                )
                EditorTool.KEY -> ColorKeyContent(
                    key = scopeEffects.key,
                    onKey = { _, change -> updateScope { it.copy(key = change(it.key)) } },
                    sample = keySampleBitmap,
                    backgrounds = KEY_BACKGROUNDS,
                    newBackground = 0x000000,
                    onRefreshSample = { keySampleRequest++ },
                    fillHint = stringResource(R.string.key_fill_video_hint),
                )
                EditorTool.HDR -> HdrVideoPanel(
                    hdrLook = wholeEffects.hdrLook,
                    onHdrLook = { look -> onUpdateEffects(null) { it.copy(hdrLook = look) } },
                    format = format,
                    footageKeptHdr = keepHdr && footageHdr(timeline, sources).let { it != null && it != VideoHdr.NONE },
                    sample = hdrSampleBitmap,
                    screenHdr = screenHdr,
                    headroom = hdrHeadroom,
                    showZones = hdrZonesShown,
                    onShowZones = { hdrZonesShown = it },
                )
                EditorTool.OVERLAY_KEY -> selectedImage?.let { image ->
                    ColorKeyContent(
                        key = image.key,
                        onKey = { step, change -> updateImage(image.id, step?.let { "key:$it" }) { it.copy(key = change(it.key)) } },
                        sample = keySampleBitmap,
                        backgrounds = emptyList(),
                        newBackground = null,
                        fillHint = stringResource(R.string.key_overlay_hint),
                        subjectUri = image.uri?.takeIf { !image.isVideo },
                    )
                }
                EditorTool.FRAMING -> selectedClip?.let { clip ->
                    FramingSectionContent(
                        zoom = scopeEffects.zoom,
                        panX = scopeEffects.panX,
                        panY = scopeEffects.panY,
                        onZoom = { zoom -> updateScope { it.copy(zoom = zoom) } },
                        onPan = { x, y -> updateScope { it.copy(panX = x, panY = y) } },
                        keyed = !clip.effects.keyframes.isEmpty,
                    )
                }
                EditorTool.MOTION -> selectedClip?.let { clip ->
                    StillMotionContent(
                        motion = clip.effects.motion,
                        onMotion = { motion -> onUpdateEffects(clip.id) { it.copy(motion = motion) } },
                    )
                }
                EditorTool.OVERLAY_ADJUST -> selectedImage?.let { image ->
                    Column {
                        OverlayBlurCard(look = image.look, onLook = lookChange(image.id))
                        Spacer(modifier = Modifier.height(12.dp))
                        ColorSectionContent(
                            brightness = image.brightness,
                            contrast = image.contrast,
                            saturation = image.saturation,
                            onBrightness = { value -> updateImage(image.id, "brightness") { it.copy(brightness = value) } },
                            onContrast = { value -> updateImage(image.id, "contrast") { it.copy(contrast = value) } },
                            onSaturation = { value -> updateImage(image.id, "saturation") { it.copy(saturation = value) } },
                            hint = stringResource(R.string.overlay_adjust_hint),
                        )
                    }
                }
                EditorTool.OVERLAY_CROP -> selectedImage?.let { image ->
                    OverlayCropContent(overlay = image, onLook = lookChange(image.id))
                }
                EditorTool.OVERLAY_BORDER -> selectedImage?.let { image ->
                    OverlayBorderContent(look = image.look, onLook = lookChange(image.id))
                }
                EditorTool.OVERLAY_SHADOW -> selectedImage?.let { image ->
                    OverlayShadowContent(look = image.look, onLook = lookChange(image.id))
                }
                EditorTool.OVERLAY_ANIMATE -> {
                    val text = selectedText
                    val image = selectedImage
                    val id = text?.id ?: image?.id
                    val clipId = text?.clipId ?: image?.clipId
                    val lengthMs = if (clipId != null) {
                        timeline.clips.firstOrNull { it.id == clipId }?.let { it.effects.playedMs(it.lengthMs) } ?: 0L
                    } else {
                        text?.span?.lengthMs ?: image?.span?.lengthMs ?: 0L
                    }
                    if (id != null) {
                        OverlayAnimationContent(
                            overlayId = id,
                            lengthMs = lengthMs,
                            canTurn = image != null,
                            hasKeyframes = !(text?.keyframes ?: image?.keyframes ?: Keyframes.NONE).isEmpty,
                            onApply = { animation, entering, durationMs ->
                                if (text != null) {
                                    onUpdateText(id, null) { it.animated(animation, entering, durationMs, lengthMs) }
                                } else {
                                    onUpdateImage(id, null) { it.animated(animation, entering, durationMs, lengthMs) }
                                }
                            },
                            onClear = {
                                if (text != null) {
                                    onUpdateText(id, null) { it.withoutAnimation(lengthMs) }
                                } else {
                                    onUpdateImage(id, null) { it.withoutAnimation(lengthMs) }
                                }
                            },
                        )
                    }
                }
                EditorTool.OVERLAY_ROTATE -> selectedImage?.let { image ->
                    OverlayRotateContent(
                        degrees = image.rotationDegrees,
                        flipHorizontal = image.flipHorizontal,
                        flipVertical = image.flipVertical,
                        onDegrees = { value -> updateImage(image.id, "rotate") { it.copy(rotationDegrees = value) } },
                        onFlipHorizontal = { flip -> updateImage(image.id, null) { it.copy(flipHorizontal = flip) } },
                        onFlipVertical = { flip -> updateImage(image.id, null) { it.copy(flipVertical = flip) } },
                    )
                }
                EditorTool.OVERLAY_VOLUME -> selectedImage?.takeIf { it.isVideo }?.let { image ->
                    OverlayVolumeContent(
                        volume = image.volume,
                        muted = image.muted,
                        onVolume = { value -> updateImage(image.id, "volume") { it.copy(volume = value) } },
                        onMuted = { muted -> updateImage(image.id, null) { it.copy(muted = muted) } },
                    )
                }
                EditorTool.TEXT_EDIT -> selectedText?.let { text ->
                    TextEditContent(
                        text = text,
                        onText = { value -> updateText(text.id, "text") { it.copy(text = value) } },
                    )
                }
                EditorTool.TEXT_STYLE -> selectedText?.let { text ->
                    TextStyleContent(
                        text = text,
                        onColor = { color -> updateText(text.id, "color") { it.copy(color = color) } },
                        onBold = { bold -> updateText(text.id, null) { it.copy(isBold = bold) } },
                        onOpacity = { opacity -> updateText(text.id, "opacity") { it.copy(opacity = opacity) } },
                        onFont = { font -> updateText(text.id, null) { it.copy(font = font) } },
                    )
                }
                EditorTool.OVERLAY_LAYOUT -> when {
                    selectedText != null -> Column {
                        // Its place among the captions and pictures laid with it.
                        val (position, count) = stackPosition(selectedText.id, selectedText.clipId)
                        OverlayArrangeCard(
                            onFit = null,
                            onLayer = { move -> onArrangeImage(selectedText.id, move) },
                            canForward = position in 0 until count - 1,
                            canBackward = position > 0,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OverlayLayoutContent(
                            x = selectedText.x,
                            y = selectedText.y,
                            size = selectedText.size,
                            sizeRange = 0.02f..0.3f,
                            sizeDefault = 0.05f,
                            onSize = { size -> updateText(selectedText.id, "size") { it.copy(size = size) } },
                            onPosition = { x, y -> updateText(selectedText.id, "move") { it.copy(x = x, y = y) } },
                        )
                    }
                    selectedImage != null -> Column {
                        val (position, count) = stackPosition(selectedImage.id, selectedImage.clipId)
                        val frameRatio = selectedImage.clipId?.let { id -> fileRatio(timeline.clips.firstOrNull { it.id == id }) }
                            ?: projectRatio.ratio ?: projectFrameRatio
                        OverlayArrangeCard(
                            onFit = { mode -> updateImage(selectedImage.id, null) { it.fittedTo(mode, frameRatio) } },
                            onLayer = { move -> onArrangeImage(selectedImage.id, move) },
                            canForward = position in 0 until count - 1,
                            canBackward = position > 0,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OverlayLayoutContent(
                            x = selectedImage.x,
                            y = selectedImage.y,
                            size = selectedImage.scaleW,
                            sizeRange = 0.02f..maxOf(1f, selectedImage.scaleW),
                            sizeDefault = 0.33f,
                            onSize = { size -> updateImage(selectedImage.id, "size") { it.copy(scaleW = size) } },
                            onPosition = { x, y -> updateImage(selectedImage.id, "move") { it.copy(x = x, y = y) } },
                        )
                    }
                    else -> Unit
                }
                EditorTool.IMAGE_OPACITY -> selectedImage?.let { image ->
                    ImageOpacityContent(
                        opacity = image.opacity,
                        onOpacity = { opacity -> updateImage(image.id, "opacity") { it.copy(opacity = opacity) } },
                    )
                }
                EditorTool.OVERLAY_FADE -> when {
                    selectedText != null -> OverlayFadeContent(
                        fadeInSeconds = selectedText.fadeInSeconds,
                        fadeOutSeconds = selectedText.fadeOutSeconds,
                        lengthMs = selectedText.span.lengthMs,
                        onFadeIn = { s -> updateText(selectedText.id, "fadeIn") { it.copy(fadeInSeconds = s) } },
                        onFadeOut = { s -> updateText(selectedText.id, "fadeOut") { it.copy(fadeOutSeconds = s) } },
                    )
                    selectedImage != null -> OverlayFadeContent(
                        fadeInSeconds = selectedImage.fadeInSeconds,
                        fadeOutSeconds = selectedImage.fadeOutSeconds,
                        lengthMs = selectedImage.span.lengthMs,
                        onFadeIn = { s -> updateImage(selectedImage.id, "fadeIn") { it.copy(fadeInSeconds = s) } },
                        onFadeOut = { s -> updateImage(selectedImage.id, "fadeOut") { it.copy(fadeOutSeconds = s) } },
                    )
                    else -> Unit
                }
                EditorTool.OVERLAY_TIMING -> {
                    val id = selectedText?.id ?: selectedImage?.id
                    val span = selectedText?.span ?: selectedImage?.span
                    if (id != null && span != null) {
                        OverlayTimingContent(
                            span = span,
                            outputDurationMs = clock.outputDurationMs,
                            onSpan = { key, newSpan ->
                                if (selectedText != null) {
                                    updateText(id, key) { it.withSpan(newSpan).copy(keyframes = it.keyframes.respanned(it.span.startMs, it.span.endMs, newSpan.startMs, newSpan.endMs)) }
                                } else {
                                    updateImage(id, key) { it.withSpan(newSpan).copy(keyframes = it.keyframes.respanned(it.span.startMs, it.span.endMs, newSpan.startMs, newSpan.endMs)) }
                                }
                            },
                        )
                    }
                }
                EditorTool.COMMAND -> CommandSectionContent(
                    commandPreview = commandPreview,
                    isManualMode = isManualMode,
                    manualCommand = manualCommand,
                    onManualCommandChange = onManualCommandChange,
                    onResetManualMode = onResetManualMode,
                )
                EditorTool.SUBTITLE -> SubtitleSectionContent(subtitles)
                EditorTool.REMUX -> RemuxSectionContent(
                    useStreamCopy = useStreamCopy,
                    onUseStreamCopy = onUseStreamCopy,
                )
                EditorTool.EXTRACT -> FrameExtractionSectionContent(
                    frameExtractionMode = frameExtractionMode,
                    frameExtractionRate = frameExtractionRate,
                    frameExtractionTimecode = frameExtractionTimecode,
                    onFrameExtractionMode = onFrameExtractionMode,
                    onFrameExtractionRate = onFrameExtractionRate,
                    onFrameExtractionTimecode = onFrameExtractionTimecode,
                )
            }
        }
    }
    val toolsPane: @Composable (Modifier) -> Unit = { modifier ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = selectedTool,
            transitionSpec = {
                (slideInVertically(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            contentAlignment = Alignment.BottomCenter,
            label = "toolsPane",
            modifier = modifier,
        ) { tool ->
            if (tool == null) timelineArea() else panelArea(tool, false)
        }
    }

    EditorTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(if (previewFullscreen) Color.Black else MaterialTheme.colorScheme.background),
        ) {
            // A window too low for the preview above the tools (a phone on its side)
            // puts them side by side. Measured without the keyboard, so that opening it
            // never switches layouts (which would take the focus away from the field).
            val sideBySideNow = maxWidth > maxHeight && maxHeight < EDITOR_TOOLS_HEIGHT + EDITOR_CHROME_HEIGHT + MIN_PREVIEW_HEIGHT
            // A large screen (a tablet on its side, a monitor): the timeline across the whole
            // width at the bottom, the preview above it, and an open tool's panel beside the
            // preview, so that the timeline and the preview stay in sight while it is open.
            val studioNow = !sideBySideNow && maxWidth >= STUDIO_MIN_WIDTH && maxHeight >= STUDIO_MIN_HEIGHT
            // In full screen the layout stays the one it was entered from, whatever way the
            // phone is turned: the preview is never built again (it would start over).
            val layoutNow = sideBySideNow to studioNow
            val frozenLayout = remember(previewFullscreen) { layoutNow }
            val (sideBySide, studio) = if (previewFullscreen) frozenLayout else layoutNow
            val full = previewFullscreen
            if (studio) {
                val motion = MaterialTheme.motionScheme
                val panelWidth = (maxWidth * SIDE_PANEL_SHARE).coerceIn(SIDE_PANEL_MIN_WIDTH, SIDE_PANEL_MAX_WIDTH)
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    if (!full) topBar()
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            previewPane(Modifier.weight(1f).fillMaxWidth())
                            if (!full) controls()
                        }
                        if (!full) AnimatedContent(
                            targetState = selectedTool,
                            transitionSpec = {
                                (slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                                        fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
                            },
                            contentAlignment = Alignment.TopEnd,
                            label = "sidePanel",
                            modifier = Modifier.fillMaxHeight(),
                        ) { tool ->
                            if (tool != null) {
                                Box(modifier = Modifier.width(panelWidth).fillMaxHeight().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
                                    panelArea(tool, true)
                                }
                            }
                        }
                    }
                    if (!full) Box(modifier = Modifier.fillMaxWidth().height(EDITOR_TOOLS_HEIGHT)) { timelineArea() }
                }
            } else if (sideBySide) {
                Row(modifier = Modifier.fillMaxSize().imePadding()) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (!full) topBar()
                        previewPane(Modifier.weight(1f).fillMaxWidth())
                    }
                    if (!full) {
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            controls()
                            toolsPane(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            } else {
                // An open panel grows to show all of its content, up to what leaves the
                // preview a usable size; the timeline and tool row keep their height.
                val density = LocalDensity.current
                val tallestTools = (maxHeight - EDITOR_CHROME_HEIGHT - MIN_PREVIEW_HEIGHT).coerceAtLeast(EDITOR_TOOLS_HEIGHT)
                val wantedTools = if (selectedTool == null) {
                    EDITOR_TOOLS_HEIGHT
                } else {
                    (PANEL_HEADER_HEIGHT + with(density) { panelContentPx.toDp() } + 8.dp).coerceIn(EDITOR_TOOLS_HEIGHT, tallestTools)
                }
                val toolsHeight by animateDpAsState(
                    targetValue = wantedTools,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "toolsHeight",
                )
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    if (!full) topBar()
                    previewPane(Modifier.weight(1f).fillMaxWidth())
                    if (!full) controls()
                    if (!full) toolsPane(Modifier.fillMaxWidth().height(toolsHeight))
                }
            }
            if (full) {
                FullscreenControls(
                    playback = playback,
                    onSeek = { outputMs -> seekTimeline(Math.round(clock.timelineMs(outputMs))) },
                    onExit = { previewFullscreen = false },
                )
            }
        }

        if (showExportDialog) {
            ExportDialog(
                format = format,
                durationMs = clock.outputDurationMs,
                gif = gif,
                gifVideoWidth = gifVideoWidth,
                onGif = onGif,
                audioEncoding = audioEncoding,
                onAudioEncoding = onAudioEncoding,
                resolution = resolution,
                framerate = framerate,
                qualityLevel = qualityLevel,
                commandPreview = commandPreview,
                isManualMode = isManualMode,
                manualCommand = manualCommand,
                onResolution = onResolution,
                onFramerate = onFramerate,
                onQualityLevel = onQualityLevel,
                footageHdr = footageHdr(timeline, sources),
                keepHdr = keepHdr,
                onKeepHdr = onKeepHdr,
                onManualCommandChange = onManualCommandChange,
                onResetManualMode = onResetManualMode,
                onDismiss = { showExportDialog = false },
                onConfirm = {
                    showExportDialog = false
                    onStart()
                },
            )
        }
    }
}

/** How long a just-selected item may take to appear in the project before the selection is dropped. */
private const val SELECTION_GRACE_MS = 300L

/** The HDR panel's frame is read this long after the playhead stops. */
private const val HDR_SAMPLE_SETTLE_MS = 150L

/** The headroom the window asks for while the HDR look's frame shows: what an HLG video shows at most. */
private const val HDR_VIDEO_HEADROOM = 5f

/** How long before a transition picked the preview starts playing it. */
private const val TRANSITION_LEAD_MS = 700L

/** Time for the preview to land on that frame before it plays. */
private const val TRANSITION_PLAY_DELAY_MS = 200L

/** How far before a join tapped on the timeline the preview stops. */
private const val JOIN_LEAD_MS = 300L

/** The tools of a selected text or picture. */
/** The tools of a selected text, in toolbar order. */
private val TEXT_TOOLS = listOf(
    EditorTool.TEXT_EDIT, EditorTool.TEXT_STYLE, EditorTool.OVERLAY_LAYOUT, EditorTool.OVERLAY_ANIMATE, EditorTool.OVERLAY_FADE,
    EditorTool.OVERLAY_TIMING,
)

/** The tools of a selected picture or video overlay, in toolbar order (Replace comes after). */
private fun imageToolsFor(image: ImageOverlay, withSound: Boolean): List<EditorTool> = listOfNotNull(
    EditorTool.OVERLAY_LAYOUT,
    EditorTool.OVERLAY_CROP,
    EditorTool.OVERLAY_ROTATE,
    EditorTool.OVERLAY_BORDER,
    EditorTool.OVERLAY_SHADOW,
    EditorTool.OVERLAY_ADJUST,
    EditorTool.OVERLAY_KEY,
    EditorTool.IMAGE_OPACITY,
    if (withSound && image.isVideo && image.hasAudio) EditorTool.OVERLAY_VOLUME else null,
    EditorTool.OVERLAY_ANIMATE,
    EditorTool.OVERLAY_FADE,
    EditorTool.OVERLAY_TIMING,
)

/** What can be laid over the video, or put in an overlay's place. */
private val OVERLAY_TYPES = arrayOf("image/*", "video/*")

private val OVERLAY_TOOLS = setOf(
    EditorTool.TEXT_EDIT, EditorTool.TEXT_STYLE, EditorTool.OVERLAY_LAYOUT, EditorTool.IMAGE_OPACITY,
    EditorTool.OVERLAY_FADE, EditorTool.OVERLAY_TIMING, EditorTool.OVERLAY_ROTATE, EditorTool.OVERLAY_ADJUST,
    EditorTool.OVERLAY_VOLUME, EditorTool.OVERLAY_KEY, EditorTool.OVERLAY_CROP, EditorTool.OVERLAY_BORDER,
    EditorTool.OVERLAY_SHADOW, EditorTool.OVERLAY_ANIMATE,
)

/** The tools of a selected sound. */
/** The effects tools a selected sound shares with the final sound. */
private val SOUND_EFFECT_TOOLS = setOf(
    EditorTool.AUDIO_CLEAN, EditorTool.AUDIO_DYNAMICS, EditorTool.AUDIO_SPACE, EditorTool.AUDIO_FX, EditorTool.AUDIO_STEREO,
)

private val SOUND_TOOLS = setOf(
    EditorTool.SOUND_TRIM, EditorTool.SOUND_VOLUME, EditorTool.SOUND_SPEED, EditorTool.SOUND_VOICE,
    EditorTool.SOUND_FADE, EditorTool.SOUND_EQ, EditorTool.SOUND_REPEAT,
)

/** Swallows every touch before the content sees it: for a panel on its way out. */
private fun Modifier.ignoringTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/** Timeline and tool row, or the panel of the open tool. */
private val EDITOR_TOOLS_HEIGHT = TIMELINE_HEIGHT + TOOLBAR_HEIGHT + 8.dp
/** Height of the row with the clock, play / pause, undo and redo. */
private val CONTROL_ROW_HEIGHT = 56.dp
/** Top bar and control row. */
private val EDITOR_CHROME_HEIGHT = 52.dp + CONTROL_ROW_HEIGHT
/** Below this the preview is too small to be of use above the tools. */
private val MIN_PREVIEW_HEIGHT = 200.dp

/** From this size the editor lays itself out as on a large screen (see the layout). */
internal val STUDIO_MIN_WIDTH = 840.dp
internal val STUDIO_MIN_HEIGHT = 560.dp

/** The share of the width a tool's panel takes beside the preview, and its limits. */
internal const val SIDE_PANEL_SHARE = 0.32f
internal val SIDE_PANEL_MIN_WIDTH = 340.dp
internal val SIDE_PANEL_MAX_WIDTH = 460.dp

/**
 * The editors' colours: the app's own, light or dark as the settings say (the
 * picture keeps its black frame either way). In dark they stay on AMOLED black,
 * which makes the picture easier to judge. Material 3 Expressive, with the
 * expressive motion scheme like the rest of the app.
 */
@Composable
internal fun EditorTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = settings.dynamicColor
    // The wallpaper's colours, or the icon's teal when dynamic colour is off (see the settings) or missing.
    val scheme = remember(context, dark, dynamic) {
        val app = appColorScheme(context, dark = dark, dynamic = dynamic)
        if (dark) app.onBlack() else app
    }
    // The system bars take its background, their icons dark or light with it.
    com.cuscus.ffmpeggui.ui.theme.SystemBarsColor(scheme.background)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = MaterialTheme.typography,
        content = content,
    )
}

/**
 * The HDR kind of the clips' files: NONE when all are SDR (pictures are), the
 * kind when all are HDR of one kind, null when HDR and SDR clips are mixed.
 */
private fun footageHdr(timeline: Timeline, sources: List<EditorSource>): VideoHdr? {
    val kinds = timeline.clips
        .mapNotNull { clip -> sources.firstOrNull { it.id == clip.sourceId } }
        .map { if (it.isStill) VideoHdr.NONE else it.info?.hdr ?: VideoHdr.NONE }
        .distinct()
    return when {
        kinds.size <= 1 -> kinds.singleOrNull() ?: VideoHdr.NONE
        kinds.any { it != VideoHdr.NONE } -> null
        else -> VideoHdr.NONE
    }
}

/** Close on the left, the file and its target format, Export on the right. */
@Composable
internal fun EditorTopBar(
    fileName: String,
    formatLabel: String,
    onClose: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.editor_output_format, formatLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Expressive button: it changes shape (rounder to squarer) under the finger.
        Button(
            onClick = onExport,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.editor_export), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** Clock on the left, play / pause in the middle, the keyframe diamond, undo and redo on the right. */
@Composable
private fun ControlRow(
    playback: PreviewPlayback,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    /** Whether a keyframe is at the playhead on what has the focus; null when none can be put there. */
    keyframeAt: (PreviewSnapshot) -> Boolean? = { null },
    onToggleKeyframe: () -> Unit = {},
    onEasing: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    var isPlaying by playback.isPlaying
    val currentKeyframeAt by rememberUpdatedState(keyframeAt)
    val onKeyframe by remember(playback) { derivedStateOf { currentKeyframeAt(playback.snapshot.value) == true } }
    val easingLabel = stringResource(R.string.tool_easing)
    // The clock on the left, play / pause in the middle, the diamond, undo and redo on the
    // right; on a keyframe, its curve button halfway between play / pause and the diamond.
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTROL_ROW_HEIGHT)
            .padding(horizontal = 12.dp),
        content = {
            PlaybackClock(playback = playback)
            MorphingPlayButton(
                isPlaying = isPlaying,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPlaying = !isPlaying
                },
                playDescription = stringResource(R.string.action_play),
                pauseDescription = stringResource(R.string.action_pause),
            )
            // On a keyframe, the way it moves on to the next one can be chosen. Always in a
            // box of its own: hidden, the box is still there (empty), so the layout below
            // always finds its four children.
            Box {
                AnimatedVisibility(
                    visible = onKeyframe,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onEasing()
                    }) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.semantics { contentDescription = easingLabel },
                        ) {
                            EasingGlyph(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyframeButton(playback = playback, keyframeAt = keyframeAt, onToggle = onToggleKeyframe)
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (clock, play, curve, right) = measurables.map { it.measure(loose) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            fun centreY(p: Placeable) = (height - p.height) / 2
            clock.place(0, centreY(clock))
            val playLeft = (width - play.width) / 2
            play.place(playLeft, centreY(play))
            val rightLeft = width - right.width
            right.place(rightLeft, centreY(right))
            // Halfway between the end of play / pause and the start of the diamond.
            val gapCentre = (playLeft + play.width + rightLeft) / 2
            curve.place(gapCentre - curve.width / 2, centreY(curve))
        }
    }
}

/**
 * The keyframe diamond: an outline when a keyframe can be put at the playhead on
 * what has the focus, a filled gem (morphing from the diamond) when one is
 * there, dimmed when there is nothing to put one on. It follows the playhead,
 * so it reads the clock itself.
 */
@Composable
private fun KeyframeButton(playback: PreviewPlayback, keyframeAt: (PreviewSnapshot) -> Boolean?, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // Derived: the clock recomposes it only when the answer changes, not at every frame.
    val currentKeyframeAt by rememberUpdatedState(keyframeAt)
    val state by remember(playback) { derivedStateOf { currentKeyframeAt(playback.snapshot.value) } }
    val on = state == true
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = {
            haptic.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            // Paused on the keyframe being made, so that it is seen and can be changed at once.
            playback.isPlaying.value = false
            onToggle()
        },
        enabled = state != null,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
            MorphingContainer(
                from = MaterialShapes.Diamond,
                to = MaterialShapes.Gem,
                morphed = on,
                color = if (on) scheme.primary else Color.Transparent,
                modifier = Modifier.size(24.dp),
            ) {}
            if (!on) {
                Icon(
                    imageVector = Icons.Outlined.Diamond,
                    contentDescription = stringResource(R.string.keyframe_add),
                    tint = if (state == null) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Diamond,
                    contentDescription = stringResource(R.string.keyframe_remove),
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * What floats over the preview in full screen: play / pause, the clock, a bar to
 * move through the video and the way back. It fades away while the video plays
 * and comes back at a touch. The system bars are hidden meanwhile (a swipe from
 * an edge shows them for a moment).
 */
@Composable
private fun BoxScope.FullscreenControls(
    playback: PreviewPlayback,
    /** Goes to this instant of the exported video. */
    onSeek: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    var isPlaying by playback.isPlaying
    var shown by remember { mutableStateOf(true) }
    // Black behind the bars, and the bars away, while the preview fills the screen.
    com.cuscus.ffmpeggui.ui.theme.SystemBarsColor(Color.Black)
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
    // Out of the way after a moment while it plays.
    LaunchedEffect(shown, isPlaying) {
        if (shown && isPlaying) {
            kotlinx.coroutines.delay(FULLSCREEN_CONTROLS_MS)
            shown = false
        }
    }
    // A touch anywhere on the picture shows or hides them.
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) { detectTapGestures { shown = !shown } },
    )
    androidx.compose.animation.AnimatedVisibility(
        visible = shown,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(16.dp)
            .widthIn(max = PANEL_CONTENT_MAX_WIDTH),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                // Where the video is, read here only: the bar follows the clock without the rest.
                val position by remember(playback) {
                    derivedStateOf {
                        val now = playback.snapshot.value
                        if (now.outputDurationMs > 0) (now.outputMs.toFloat() / now.outputDurationMs).coerceIn(0f, 1f) else 0f
                    }
                }
                Slider(
                    value = position,
                    onValueChange = { fraction ->
                        shown = true
                        onSeek((fraction * playback.snapshot.value.outputDurationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    PlaybackClock(playback = playback, modifier = Modifier.weight(1f))
                    MorphingPlayButton(
                        isPlaying = isPlaying,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isPlaying = !isPlaying
                        },
                        playDescription = stringResource(R.string.action_play),
                        pauseDescription = stringResource(R.string.action_pause),
                    )
                    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.weight(1f)) {
                        FilledTonalIconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onExit()
                        }) {
                            Icon(Icons.Rounded.FullscreenExit, contentDescription = stringResource(R.string.preview_fullscreen_exit))
                        }
                    }
                }
            }
        }
    }
}

/** How long the full screen controls stay while the video plays untouched. */
private const val FULLSCREEN_CONTROLS_MS = 2_500L

/** Position and length of the exported file, in tenths of a second. */
@Composable
private fun PlaybackClock(playback: PreviewPlayback, modifier: Modifier = Modifier) {
    // Derived: the text changes ten times a second at most, not at every frame.
    val times by remember(playback) {
        derivedStateOf { playback.snapshot.value.let { formatClock(it.outputMs) to formatClock(it.outputDurationMs) } }
    }
    Text(
        text = stringResource(R.string.value_time_of_total, times.first, times.second),
        // Figures of equal width, so the clock does not jitter while it runs.
        style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** "01:05.3": minutes, seconds and tenths, as editors show it. */
private fun formatClock(ms: Long): String {
    val tenths = ms.coerceAtLeast(0L) / 100
    return String.format(java.util.Locale.US, "%02d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10)
}

/** Closest the two handles of a trimmer may come. */
private val TRIM_MIN_GAP = 32.dp

/** Longest side of the frames behind the clip trimmer. */
private const val TRIMMER_FRAME_PX = 200

@Composable
fun VideoTimelineTrimmer(
    inputUri: Uri?,
    durationMs: Long,
    startTimeMs: Long,
    endTimeMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    onScrub: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var frames by remember(inputUri) { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var internalStartPx by remember { mutableFloatStateOf(0f) }
    var internalEndPx by remember { mutableFloatStateOf(0f) }
    var isInitialized by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableIntStateOf(-1) }
    var lastScrubTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(inputUri) {
        if (inputUri != null && durationMs > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, inputUri)
                    val frameCount = 10
                    val interval = (durationMs * 1000) / frameCount
                    frames = (0 until frameCount).mapNotNull { i ->
                        com.cuscus.ffmpeggui.preview.smallFrameAt(retriever, i * interval, TRIMMER_FRAME_PX)
                    }
                } catch (e: Exception) { } finally { retriever.release() }
            }
        }
    }

    // The grabbed handle widens with the theme's springy motion.
    val handleWidth by animateDpAsState(
        targetValue = if (activeHandle != -1) 20.dp else 14.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "trimHandle",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val gripColor = MaterialTheme.colorScheme.onPrimary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onSizeChanged { size ->
                if (!isInitialized && durationMs > 0) {
                    internalStartPx = (startTimeMs.toFloat() / durationMs) * size.width
                    internalEndPx = (endTimeMs.takeIf { it > 0 } ?: durationMs).toFloat() / durationMs * size.width
                    isInitialized = true
                }
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val hW = with(density) { handleWidth.toPx() }
        val strokeW = with(density) { 2.dp.toPx() }

        Row(modifier = Modifier.fillMaxSize()) {
            frames.forEach { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Black.copy(0.55f),
                size = androidx.compose.ui.geometry.Size(internalStartPx, heightPx)
            )
            drawRect(
                color = Color.Black.copy(0.55f),
                topLeft = Offset(internalEndPx, 0f),
                size = androidx.compose.ui.geometry.Size(widthPx - internalEndPx, heightPx)
            )

            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(internalStartPx, 0f),
                size = androidx.compose.ui.geometry.Size(internalEndPx - internalStartPx, heightPx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                style = Stroke(width = strokeW * 1.5f)
            )

            // Pill handles with a grip line, as in the rest of the editor.
            listOf(internalStartPx, internalEndPx - hW).forEach { left ->
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(hW, heightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(hW / 2f)
                )
                drawLine(
                    color = gripColor,
                    start = Offset(left + hW / 2f, heightPx * 0.35f),
                    end = Offset(left + hW / 2f, heightPx * 0.65f),
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    // Horizontal only, so that a vertical swipe still scrolls the panel.
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val sDist = kotlin.math.abs(offset.x - internalStartPx)
                            val eDist = kotlin.math.abs(offset.x - internalEndPx)
                            activeHandle = when {
                                sDist < 100f && sDist < eDist -> 0
                                eDist < 100f -> 1
                                else -> -1
                            }
                            if (activeHandle != -1) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (activeHandle != -1) change.consume()
                            val now = System.currentTimeMillis()
                            val minGap = TRIM_MIN_GAP.toPx()

                            if (activeHandle == 0) {
                                internalStartPx = (internalStartPx + dragAmount).coerceIn(0f, (internalEndPx - minGap).coerceAtLeast(0f))
                                val newS = ((internalStartPx / widthPx) * durationMs).toLong()

                                if (now - lastScrubTime > 40) {
                                    onScrub(newS)
                                    onRangeChange(newS, ((internalEndPx / widthPx) * durationMs).toLong())
                                    lastScrubTime = now
                                }
                            } else if (activeHandle == 1) {
                                internalEndPx = (internalEndPx + dragAmount).coerceIn((internalStartPx + minGap).coerceAtMost(widthPx), widthPx)
                                val newE = ((internalEndPx / widthPx) * durationMs).toLong()

                                if (now - lastScrubTime > 40) {
                                    onScrub(newE)
                                    onRangeChange(((internalStartPx / widthPx) * durationMs).toLong(), newE)
                                    lastScrubTime = now
                                }
                            }
                        },
                        onDragEnd = {
                            if (activeHandle != -1) {
                                val targetPos = if (activeHandle == 0) ((internalStartPx / widthPx) * durationMs).toLong()
                                else ((internalEndPx / widthPx) * durationMs).toLong()
                                onScrub(targetPos)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRangeChange(
                                    ((internalStartPx / widthPx) * durationMs).toLong(),
                                    ((internalEndPx / widthPx) * durationMs).toLong()
                                )
                            }
                            activeHandle = -1
                        },
                        onDragCancel = { activeHandle = -1 }
                    )
                }
        )
    }
}

fun formatTimeMs(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "%02d:%02d:%02d.%03d", h, m, s, millis)
}

/**
 * The keyframe the easing panel works on: [keyframe], whether it is the [last]
 * one (nothing to move on to), and [set], which changes its easing (and drawn curve).
 */
private class EasingTarget(val keyframe: Keyframe, val last: Boolean, val set: (KeyEasing, EaseCurve?) -> Unit)