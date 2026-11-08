# Architecture

## Modules

```
:app    UI (Compose), view model, dependency wiring          Android application
:media  FFmpeg runtime, media probing, file staging, fonts   Android library
:core   models, command generation, conversion workflow      pure Kotlin (JVM)
```

Dependencies point one way only: `:app -> :media -> :core`.

`:core` knows nothing about Android. It describes what it needs from the
platform as small interfaces (`core/engine/Ports.kt`) and `:media` implements
them:

| Port (`:core`)            | Implementation (`:media`) | Purpose                                   |
|---------------------------|---------------------------|-------------------------------------------|
| `FfmpegExecutor`          | `FfmpegKitExecutor`       | run FFmpeg, stream its log, cancel        |
| `SourceStager`            | `CacheSourceStager`       | copy `content://` sources to local files  |
| `MediaProbe`              | `AndroidMediaProbe`       | duration, size, tracks, display name      |
| `FontProvider`            | `SystemFontProvider`      | font file for text overlays               |
| `OutputDirectoryProvider` | `AppOutputDirectory`      | where results are written                 |
| `LoudnessMeter`           | `FfmpegLoudnessMeter`     | source loudness, for the preview          |
| `TextSourceReader`        | `ContentTextReader`       | read subtitle files                       |
| `ScratchStorage`          | `CacheJanitor`            | camera recordings, stills, cache clean-up |
| `StillMaker`              | `AndroidStillMaker`       | pictures and freeze frames as PNG stills  |

There are no singletons. Every collaborator is a class with constructor
dependencies, created once in `app/.../di/AppContainer.kt`, which is owned by
`FfmpegGuiApp` and handed to the view model through `ConversionViewModel.Factory`.

## Data flow

```
ConversionSettings            editor state: content URIs, timecode strings   (:app)
      |  conversion/RequestMapping.kt
      v
ConversionRequest             what to do, no file locations                  (:core)
      |
      +--> + MediaPaths.aliasesFor(request)  -> CommandBuilder -> preview text
      |
      +--> ConversionJob(request, sources, mode) -> ConversionEngine.run()
               1. stage sources into a private cache directory
               2. re-time the subtitles to the edit; write the concat list
                  (only for stream copy of several clips)
               3. stabilization analysis pass (when requested)
               4. CommandBuilder.build(request, real paths)
                  or ManualCommandResolver for a hand-edited command
               5. FfmpegExecutor.execute(), progress parsed from the log
               6. numbered outputs (parts, frames): packed into one zip
               7. always: delete staged files; on failure/cancel: delete partial output
```

An output of several files (a video split into parts, a sequence of frames, a
hand-written command writing `output_%03d.png`) is written to a folder of its
own (`OutputPlanner`), then packed into a zip named like it
(`core/output/PartsArchive.kt`; the hand-written one only takes the files of
its own counter): a folder cannot be shared or saved, a zip can. Each part is
deleted once it is in the zip, so the storage needed stays about the size of
the parts; the zip uses the fastest level (media files are already
compressed). Packing is a stage of its own (`ConversionStage.PACKING`, shown on
the processing screen and in the notification) and stops when the export is
cancelled, leaving no zip behind; a packing that fails (storage full) is
`FailureReason.PACKING_FAILED`, and its files are removed as for any failed
export. The share sheet gets the file's real MIME type (`application/zip`),
so the apps that save files offer themselves.

The preview and the executed command come from the same `CommandBuilder.build`
call with different `MediaPaths`, so they cannot diverge. The aliases shown in
the preview (`input.mp4`, `overlay_0.png`, `output.mkv`, ...) are exactly what
`ManualCommandResolver` replaces when the user edits the command by hand.

## Timeline

The footage is a sequence of clips, as in a video editor
(`core/timeline/Timeline.kt`, immutable and unit tested):

- a `Clip` is a range of one source file (`sourceId`, `startMs`, `endMs`);
  an `EditorSource` (`app/.../EditorModels.kt`) is a file the clips can use;
- opening a file creates one clip over all of it; `splitAt`, `remove`,
  `duplicate`, `move`, `trim`, `append` (another file) and `insertAt` reshape
  the sequence;
- "+" picks one file or several at once (`OpenMultipleDocuments`;
  `ConversionViewModel.addClips`): they are inspected a few at a time
  (`probeAll`), pictures are made into stills one after the other, and the
  clips are appended in the order picked, as one undo step. Sounds
  (`addAudioClips`) and pictures or videos laid over the video (`addOverlays`)
  are picked the same way and go one after another from the playhead; in the
  picture editor the pictures all show at once, each a little lower and to the
  right of the one before (`STAGGER`). The file the project is opened with is
  still picked alone;
- a clip can show a *still*: a picture added with "+" or opened as the project,
  or a freeze frame (the "Freeze" tool cuts the clip under the playhead and puts
  that frame in between with `insertAt`). `StillMaker` writes every still as an
  upright PNG in the cache, so FFmpeg, the preview and the thumbnails all read
  the same file. A still has no length of its own (`MediaInfo.isStill`,
  `SourceInfo.isStill`): its clip lasts 3 s and the clip panel changes that. The
  export loops it (`-loop 1 -framerate <rate> -t <length>`) and gives it a silent
  track; a still is never reversed and never stream-copied. A still clip can
  have a slow camera move ("Ken Burns", `Effects.motion` = `StillMotion`: zoom
  in, zoom out or pan left / right / up / down, enlarged up to 2x). The export
  puts `zoompan=z=..:x=..:y=..:d=1:s=WxH:fps=F` at the head of the clip's chain,
  with the progress `p=min(on/(N-1),1)` over the clip's N frames; the preview
  scales and shifts the picture by the same `zoomAt` / `xAt` / `yAt`;
- the selected clip's edges on the editor's timeline are handles (tabs in the
  primary colour, rounded outside and straight inside, with a chevron; the one
  held grows wider and glows; texts, pictures and sounds wear the same):
  dragging one trims the clip (`Timeline.trim`, as the Trim tool does), the
  clip drawn as it will be while the finger moves with its length in a pill
  (and its neighbours with it: an end pushes or pulls the clips after it, a
  start the clips before it, so nothing overlaps or leaves a gap),
  the edge snapping to the centre line and the preview showing the frame under
  the edge; one undo step on release. While an edge or an item is dragged the
  view holds still, and when the finger rests near a side the timeline moves
  on by itself, frame by frame, faster the nearer it is (`edgeScroll`), the
  edge going with it; on release the playhead is brought to the view;
- a clip held a moment (any clip; on the selected one, not on its handles)
  is picked up and follows the finger, lifted on its shadow, while the others
  make room where it would land (`ClipMove`, `moveLayout` in
  `EditorTimeline.kt`); a tick marks each new place, the timeline moves on by
  itself near a side, and letting go moves it there (`Timeline.move`, one
  undo step), as a placed sound is moved by dragging it;
- "trim the whole video" does not touch the clips: `ConversionSettings` keeps
  `trimStartMs` / `trimEndMs` on the timeline and `Timeline.window()` returns the
  clips that really play (`ConversionSettings.playingClips`). The preview and
  the export both start from that list, so they treat any number of clips as one
  video.

`RequestMapping.kt` turns the playing clips into `ConversionRequest.segments`
(each pointing into `sources`, the distinct files in use). The export opens one
seeked input per segment and joins them with the `concat` *filter*, which is
frame accurate and independent of key frames. Clips taken from different files
are scaled and padded to the frame of the first one, and silent clips get a
silent track, so files of different size, frame rate or audio layout can be
mixed. Everything after the join (filters, overlays, fades, split) works on the
joined sequence. Subtitles are timed against the main file, so
`SubtitleRetimer` rewrites them to follow their footage through cuts, moves
and reversal before they are burnt in. It also drops what a SubStation Alpha
script carries embedded (its `[Fonts]` and `[Graphics]` sections): FFmpeg's
`subtitles` filter has libass extract such fonts, and FreeType would parse
them inside the app's process, where a crafted one could reach a bug of its
(CVE-2025-27363 affects the FreeType 2.13.0 of FFmpegKitNext 6.1.1). Fonts are
picked in the app instead, or come from the phone.

### Placed sounds

Music, voice-overs and effects are not tied to clips: like in CapCut they are
placed freely on the *exported video's* clock (`AudioClip` in
`EditorModels.kt`, its `SoundSpan` in `core/timeline/SoundSpan.kt`). A span
says which part of the file plays (`trimStartMs..trimEndMs`) and from when
(`startMs`); sounds may overlap. Cutting, moving or deleting clips does not move
them, exactly as in the usual editors. `SoundSpan` keeps every edit valid
(inside the file, never before the start, at least 100 ms), cuts a sound in two
and packs overlapping sounds onto rows (`SoundSpan.rows`), all unit tested.

Each sound has its own tools: trim, volume (with mute and loudness
levelling), speed (keeping the pitch or not), voice (a pitch shift in
semitones, with presets), fades, a three-band equaliser (with presets),
repeat (the trimmed part plays N times) and replace (another file, same
place and settings). Speed and repeats change the sound's length on the video;
`SoundSpan` takes them into account in every edit, so a dragged edge still
follows the finger. A repeated sound is not split.

`RequestMapping.kt` turns them into `ConversionRequest.sounds` (`PlacedSound`);
a muted sound is left out. The export gives each one an input of its own:
`atrim` for its part of the file; `aresample=48000` and `aloop` for repeats;
`asetrate` + `aresample` for the pitch and an `atempo` chain for the rest of
the speed (pitch factor = 2^(semitones/12), times the speed when the pitch
follows it); `bass` / `equalizer` / `treble`; its own effects (the same steps
as the final sound's, see "Audio files", cut at its end when they ring on);
`loudnorm`; its volume and fades;
`adelay=delays=<start>:all=1` to put it in place (timestamps renumbered after
it); the whole video's fades; then padded / cut to the output length. Every
input of the mix is made stereo with `pan=stereo|FL=FL+FC|FR=FR+FC`, which
keeps stereo as it is and puts mono on both sides at full level (as a player
plays it; FFmpeg's own conversion would lower it by 3 dB). The
source sound (if kept) and all the sounds are mixed with `amix ...
normalize=0`, so each keeps its own level. Whatever runs past the end of the
video is cut.

The timeline draws the sounds through `OutputClock` (`core/preview`), which
converts between the output clock and the timeline (clips at media length,
before the trim) and extends both ways past the ends, so a sound sticking out
is still drawn whole and can still be grabbed.

The editor side is described under "Editor layout" below.

### Audio files

When the output is an audio file (`OutputFormat.isAudioOnly`: MP3, M4A, OGG,
OPUS, FLAC, WAV) the editor works on the sound alone:

- the preview pane shows `AudioPreview.kt` instead of the picture: the
  exported sound as rounded bars (`preview/AudioEnvelope.kt` builds the outline
  of the output from the clips' and placed sounds' waveforms, through the
  output clock, with volumes, fades and levelling; values above 1 would clip
  and are drawn in the error tone), the playhead, dashed lines where the file
  will be cut into parts, the tags and format on top and the effects in use
  below. The bars at the playhead and a morphing badge follow the level that is
  playing (`LevelMeter`, fed by the players). Touching or dragging the waveform
  seeks. The player does not decode the picture at all;
- the timeline draws each clip's waveform instead of frames and shows only the
  sound lanes; the trim panels trim on the waveform too;
- the tools are those of `AudioPanels.kt`: silence (moves the whole-sequence
  trim past the silent start and end, measured on the waveforms), audio
  (volume, levelling, placed sounds), equaliser, clean up, dynamics, space,
  effects, pitch, speed, fades, stereo, tags and parts; a clip has trim, speed,
  volume and fades.

The same tools exist in the video editor: its "Audio" tool opens a row of
sound tools for the whole video (volume and sounds, equaliser, clean up,
dynamics, space, effects, pitch, stereo), and a selected placed sound has clean
up, dynamics, space, effects and stereo of its own (`AudioClip.effects`, next
to its three-band equaliser and voice).

The footage's sound is one hierarchy, and the audio panel (`AudioSectionContent`)
shows it as such. The whole video's track is the master: its switch
(`wholeEffects.muted`) drops the footage's sound of every clip, and its volume
multiplies each clip's (`Effects.volume`), exactly as the export and the preview
apply them. Under the master the panel lists every playing clip with sound
(`ClipSound`), recognisable at a glance: its first frame with its number on
the timeline (`rememberClipThumbnail`, as the clip strips draw it), its file
name and a tag on the clip under the playhead; then its switch, the level it
is heard at (its own volume times the master's) and, on a tap, its own volume
panel, with the clip selected and shown. A clip's panel says when the
master silences it, with the level actually heard when the master's volume is
not 100 %. Turning a clip on while the master is off lets only that clip be
heard (`ConversionViewModel.soloClipSound`, one undo step: the master back on,
every other clip muted, every volume kept). Turning the master off and on
again leaves each clip's own settings as they were.

What is done to the final sound is `AudioEffects` (`core/model/AudioEffects.kt`):
ten octave bands of equaliser, low and high cut, noise gate, compressor,
lo-fi bits, one modulation (chorus, flanger, phaser, tremolo, vibrato), echo,
reverb, stereo image (width, balance, swapped sides, mono), limiter and pitch.
`core/audio` turns it into a list of `AudioStage`s, each of which is both an
FFmpeg filter (`highpass`, `lowpass`, `equalizer`, `agate`, `acompressor`,
`acrusher`, `chorus`, `flanger`, `aphaser`, `tremolo`, `vibrato`, `aecho`,
`extrastereo`, `pan`) and a sample-by-sample Kotlin port of that filter's C
code (`core/audio/Dsp.kt`: same coefficients, tables, rounding and state).
The reverb is a dense `aecho`, so it is exact too. The export runs the stages
after the mix (`AudioFilters.masterChain`, on float samples), then the pitch
shift; echoes and choruses would ring past the end, so the sound is cut at the
output length. The preview runs the same stages in a Media3 `AudioProcessor`
(`preview/AudioEffectsProcessor.kt`) on the footage's player and on every placed
sound's player (where the sound's own three-band equaliser runs too, with the
export's own filters), and the pitch is the players' pitch. Checked against
FFmpeg 6.1 on a test signal, the ports match bit for bit except the biquads,
which differ by float rounding (below -70 dB).

How an audio file is encoded is `AudioEncoding` (bitrate of the lossy
formats, bit depth of WAV and FLAC, sample rate; Opus always runs at 48 kHz),
set in the export sheet: each choice says what it is good for, a five-bar meter shows where
the bitrate sits, and the file's size is estimated (lossy formats, and WAV at
a chosen sample rate).

The audio panels lean on `ValueSlider` (`ui/expressive/Panels.kt`): with a
`ValueInput` (unit, scale, step) it gets - and + buttons that repeat while held
and a field opened from its value pill to type the exact value, with the
accepted range and a sign button. Every slider ticks under the finger (rate
limited), ticks harder at its default, and gives TalkBack its value as the
state description. Parts show the result first (the number of files, the
sound as a bar cut into them, the length of each and of the last one, read out
by TalkBack as it changes) and set the length in minutes and seconds, on a
slider that is finer at the short end, or with quick lengths; equal parts have
a big count between repeating - and + buttons. Tags show the file as a player
lists it, with icons, clear buttons, next-field keys, a year check and genre
chips; silence draws the sound with its silent ends hatched and the button
says how much it cuts.

### Image files

When the output is a picture (`OutputFormat.isImage`: JPG, PNG, WEBP, BMP,
TIFF) the editor is `ImageEditorScreen.kt`, a photo editor: the picture fills
the top, the tool row and the open panel (`ImagePanels.kt`) sit below it, as in
the video editor. The picture is the first clip's file: a photo (opened as a
still), or the frame of a video at `ImageEdit.frameMs` (the Frame tool; the
export seeks there with `-ss`, kept 100 ms before the end of the clip by
`pictureFrameMs`).

What is done to it is `ImageEdit` (`core/model/ImageEdit.kt`) plus, for the
turn, mirrors and crop, the whole video's `Effects`; texts and stickers are the
project's own texts and pictures (whole video, no clock). In order:

1. geometry: mirrors, quarter turn, keystone (`perspective`), straighten
   (`rotate`, keeping the largest part of the same shape that the turned
   picture fills, so nothing is scaled up and the picture gets a little
   smaller), crop;
2. tone (exposure, brightness, contrast, highlights, shadows, warmth, tint,
   fade, with the chosen filter's tone added) as one `lutrgb` expression per
   channel;
3. colour (saturation, hue, black and white, sepia) as one
   `colorchannelmixer` matrix;
4. one effect (blur `boxblur`, pixels `pixelize`, glitch `rgbashift`, poster
   and invert and duotone as `lutrgb`, emboss and sketch as `convolution`),
   then sharpen (`convolution`), vignette and grain (`noise`);
5. texts (`drawtext`), stickers (`overlay`), then the drawing;
6. the border (`pad`) and the output size (`scale`, never larger).
7. rounded corners (`ImageEdit.corners`, `core/image/RoundCorners.kt`): one
   `geq` over the whole picture at its output size, from square (0) to half
   the shorter side (1: a square picture becomes a circle), its edge smoothed
   over a pixel; the cut-off parts of the colour chosen (`ImageEdit.cornerColor`,
   `cornerFillFor`): by default transparent in PNG, WEBP and TIFF, black in JPG
   and BMP. A colour in a format with transparency is mixed in RGBA, so the rest
   of the picture keeps its own (FFmpeg 6.1's rgba to gbrap conversion may move
   a half-transparent pixel's alpha by one). `i=nearest`, since geq's bilinear reading takes the last row
   and column from the ones before. `roundCorners` is its exact port (checked
   against FFmpeg pixel for pixel); the editor shows it as a rounded clip of
   the stage (with white behind it for a format without transparency).

`core/image` turns it into `ImageStage`s, each both an FFmpeg filter and a
Kotlin port of that filter's C code (`ImageStages.kt`), so the editor shows the
export's own result: `imageStages` builds them for the real picture's size in
the export and for the preview's smaller copy (at most 1080 px, rendered off
the main thread, `preview/ImageRendering.kt`) in the preview. `PictureRenderer`
keeps the geometry's result while only light, colour or effects change. Two
painters share the work: one follows the finger, making every look as soon as
the last one is done, at full size when that takes 30 ms or less and otherwise
on a smaller copy whose size adapts so that a picture takes about two frames;
the other makes the full-size picture once the look has rested for 150 ms and
drops it as soon as the look changes. A picture is shown only if nothing newer
is on screen. The command shown in the editor is rebuilt off the main thread
once the settings rest, so a slider never waits for it. The panels are built low (one slider over a row of round chips) and
never take more than about a third of the screen. Checked against
FFmpeg 6.1 on a test picture, tone, colour, filters, poster, invert, duotone,
turn, mirrors, crop, straighten, sharpen, emboss, sketch, pixels and glitch
are bit for bit equal; perspective, blur and vignette differ by at most one
level on some pixels; grain is FFmpeg's random noise, so the preview shows
noise of the same strength, not the same grains.

The Layers tool (`LayerPanels.kt`; the video editor has it too, for the
texts, pictures, videos and shapes of the whole video) lists every text and
sticker of the picture, the one in front at the top (the reverse of `stackOrder`), each with
its picture (or its text in its own colour and font) in a MaterialShapes badge.
A row dragged by its handle follows the finger while the others make way with
the theme's spatial springs; dropped, `placeLayer` puts it at that place of the
stack (`movedLayersTo`) and numbers every layer again, as one undo step. A tap
selects the layer on the picture too, and the selected row opens its own
actions (to the front, up, down, to the back, duplicate, delete); TalkBack gets
the moves as custom actions of the row.

Each row also has a lock and an eye (issue #30), as the SVG editor's layers
(`SvgLayers.kt`, where both were already): `TextOverlay.hidden` / `locked` and
`ImageOverlay.hidden` / `locked`, kept in projects, each switch one undo step.
A hidden layer stays in the project and the list (dimmed, "Hidden") but is left
out everywhere else: `StackedOverlays` does not draw it (both editors), the
video editor's preview neither draws nor plays it (`PreviewContainer`: its
sound, its picture look), the timeline draws it faded, and `RequestMapping`
leaves it out of the export (`footage`, `toRequestOf`, `lookSpans`). The
picture itself on its canvas (`PHOTO_LAYER_ID`) has no eye. A locked layer
cannot be picked nor moved on the picture: touches go through it (its tap and
transform gestures are not installed), and selected from the list or the
timeline it shows a small lock cookie where its resize grip would be; its
panels still change it, and the timeline still moves it in time (the lock is
for its place on the picture). Each switch is a round button that morphs into
a cookie, in the tertiary tone, while it is on; TalkBack reads it as a switch.

The drawing (`ImageEdit.strokes`: free strokes, lines, arrows, boxes, ovals,
eraser) is drawn by `StrokePainter` (`:media`) on the screen and, at export,
into a transparent PNG (`DrawingRenderer`) that the export lays over the
picture like a sticker, the whole picture wide.

**A canvas of its own** (`ImageEdit.canvas`, `PictureCanvas`: a size, a fill
colour or none). The file is then the canvas and the picture one of its
layers: in the editor it is the image overlay `PHOTO_LAYER_ID`, kept among the
stickers so that it is dragged, pinched, turned, given a border, a shadow, an
opacity or a blend and stacked in Layers exactly as they are, but drawn from
the edited picture (`StackedOverlays(pictures = ...)`) and never a file laid
over: `footage()` leaves it out of the pictures and the request carries it as
`ConversionRequest.photoPlacement`. The export makes the canvas with `color`
(transparent `black@0`, or white where the format holds no transparency),
makes the picture as always into the link `photo`, and stacks it with the
captions and stickers by its layer (`overlayGraph`, whose overlays are read
from a stream or a link), mixed in RGB; border, output size and corners then
apply to the canvas. `editedSize` is the canvas (the frame texts and the
drawing lie on), `pictureSize` the picture itself (the crop works on it; while
cropping the picture shows alone). Ultra HDR is not offered on a canvas.

Two fingers on the canvas zoom it (1x to 8x, around the fingers) and move it
(`StageZoom.kt`): a `graphicsLayer` scales the whole canvas (picture, texts,
stickers, drawing, crop frame), so every touch keeps landing in the canvas's
own coordinates and drawing zoomed in is exact. The stage's detector only
takes fingers nothing on the canvas took: once a layer consumed a move of the
gesture (a sticker or text dragged, pinched or turned, the crop frame), the
gesture is the layer's to the end, a second finger beside it included; and the
zoom starts only past 1.6 times the layers' slop, so a layer under the fingers
always claims them first (it sees every move before the stage). A stroke under way when a second finger lands is
dropped (`DrawingLayer`). The zoom pill springs it back to fit. Zoom is a view
of the canvas only: nothing of it reaches the project or the export.

Texts, stickers, shapes and the drawing are clipped to the picture on screen,
as the export clips them: a layer moved partly off the picture is cut at its
edge and never covers the editor (the file name, the tools).

The bundled FFmpeg has no WebP encoder: for WEBP the engine has FFmpeg write a
PNG in its working directory and `ImageRecoder` (`AndroidImageRecoder`,
`Bitmap.compress`) writes the WebP from it (lossless at quality 100 on Android
11 and later). JPG uses `mjpeg` (full-range 4:4:4), PNG, BMP and TIFF (deflate)
their native encoders.

### SVG

**Opening one.** FFmpeg cannot read an SVG: when the probe fails, the file is
tried as a picture the phone draws (`openPictureAsMain`); `AndroidStillMaker`
recognises an SVG (its type, name or first bytes) and draws it with AndroidSVG
(Apache 2.0) at 2048 px on its longer side, transparency kept, into the usual
PNG still. From then on it is a picture like any other.

**Saving one** (`OutputFormat.SVG`). FFmpeg makes the picture as a PNG, as for
a WEBP; the engine reads it (`ImageRecoder.read`) and traces it
(`core/vector/VectorTracer.kt`, `ImageEdit.trace`):

1. at most `maxSide` px on its longer side (box filtered, colours weighed by
   their opacity); pixels below half opacity stay empty;
2. a few colours: k-means (k-means++ with a fixed seed, so the same picture
   always traces the same) on a sample, the green weighed more; or, in black
   and white, the dark side of Otsu's threshold;
3. each colour followed along the pixel edges into closed outlines, with
   their holes (filled even-odd), turning right where two pixels touch by a
   corner so that they stay apart; specks below `speckle` px dropped;
4. each outline simplified (Ramer-Douglas-Peucker, 2.2 to 0.8 px by the
   detail) and, smooth, drawn as quadratic curves through the middles of its
   sides, turns sharper than about 70 degrees kept as corners;
5. colours laid largest first, each stroked with itself by half a pixel so
   that no hairline shows between neighbours;
6. with gradients asked for (`TraceSettings.gradients`, colours only), each
   colour's area split into its shapes (4-connected, as the outlines keep
   them apart); every shape of at least 120 px (and 1/4000 of the picture),
   the 400 largest, measured on the picture under it: a least-squares plane
   per channel gives the direction its colours change most, a least-squares
   bowl (`c = a + bx x + by y + q (x^2 + y^2)`, one centre for the three
   channels) the centre they fade from; the pixels along each are cut into
   five bins from the 2nd to the 98th percentile, each bin's mean colour a
   stop. The radial one wins when it leaves at most 80 % of the linear one's
   error and its centre is near the shape. The gradient is kept when it
   leaves at most 55 % of a flat colour's error and its ends differ by about
   12 levels a channel; the shape is then a layer of its own, just above the
   flat rest of its colour, filled and stroked with a `userSpaceOnUse`
   gradient (`ffg-trace-N`) in the traced pixels. Flat pictures trace exactly
   as without; a 1200 px photo takes up to about two thirds more time.

The shapes tool previews it over the stage (`VectorPreview`, the same path
steps) while it is open. Shared copies of an SVG go to Download (not every
gallery takes one).

### The SVG editor

A screen of its own (`app/.../svgedit/`), for what is inside an SVG rather
than the picture it makes. It opens from home (an SVG picked, or an empty
drawing) and when SVG is picked as the format of a picture: an SVG opens as
it is, any other picture is traced into it (`SvgEditorViewModel.trace`, the
same `VectorTracer`, one path per colour named after it) with a card to
change how until its shapes are edited. That card and the picture editor's
shapes tool share `TraceControls` (`TraceStudio.kt`): the kind of picture
first (logo, drawing, text, illustration, photo, pixel art, each a ready
`TraceSettings`; a photo's shapes are shaded with gradients), then what the
last trace made (its palette, its outlines, how many are shaded and how
heavy the file is), then its colours (with the gradients switch) and its
shapes, every slider naming what its two ends do. A shaded shape is named
after the colour it shades (`gradient-rrggbb`). A video's frame saved as an SVG
still goes through the picture editor. The toolbar adds shapes (a gallery
of basic ones, lines and every Material shape as an exact path), texts, and
curves drawn with the pen (`smoothStroke`: simplified, then Catmull-Rom
cubics); a sheet changes the selection's look; a tour shows the gestures the
first time.

**Finer tools** (`SvgShaping.kt`). The side handles stretch a node one way in
its own units (`resizeOwn`): a shape's geometry takes it (a circle becomes an
ellipse, a path is moved point by point), so its outline keeps its width.
A moved box snaps to the edges and middles of the page and of the other
nodes (`snapTargets`, `snapBox`); turning rests on every 15 degrees. A
connector is a line with `data-connect-from` / `data-connect-to` naming the
shapes it holds (ids are made up for shapes that had none); after every
change `reroute` puts its ends where the line between the shapes' middles
leaves their boxes, or on the anchor it was dropped on
(`data-connect-from-at`: fractions of the shape's own box, so it turns and
sizes with the shape); a connector may run straight, in square turns or as a
curve, leaving and reaching the shapes square to their sides, with chevron
arrows (`data-connect-style`, `data-connect-arrows`; a path connector keeps
its free ends in `data-connect-ends`).

**Points** (`SvgPathEdit.kt`). Any outline is edited as its points and the
pieces joining them (`EditPath`): straight, a cubic curve (its controls
dragged, or bent symmetrically), a zig-zag or a wave (how many, how tall),
written into the path as the lines and curves they make, how they were made
kept in `data-ffg-path` (dropped when another program changed the path). A
tap on a piece adds a point there (a curve split where it is); the points
of a new line are placed one tap each.

**Several at once** (`SvgArrange.kt`). "Pick several" makes a tap add a
node to the selection or take it out (`SvgEditorViewModel.also`, next to the
one picked first); they move, size and turn together by their bodies
(`transformAll` on the outermost of them, snapping to everything else), and
the look sheet changes them all. The "More tools" sheet (`SvgActions.kt`)
lines them up on the box around them (one alone: on the page), spaces them
out, mirrors and turns them, groups them (where the top one was, each keeping
its place on the drawing) or takes a group apart, makes a basic shape a path,
copies a look and pastes it, and places them by numbers.

**Booleans** (`SvgBooleans.kt`). Union, subtract (the top ones out of the
bottom one), intersect, exclude and divide, as in Blender or Illustrator: each
node's area on the drawing (its fill; for an outline with nothing inside, the
band its stroke paints, `Paint.getFillPath`; a text as its letters,
`Paint.getTextPath`; a group as everything in it) goes through Skia's
`Path.op`, and the result is read back as lines and curves (`PathIterator`,
conics as quadratics) into a path that takes the place of what was picked, in
the look of the bottom one (`replaceWithOutlines`: where the top one was, in
its units). Divide gives every piece the outlines cut, each in the look of
the top one over it, bits apart as shapes of their own. The same reading back
makes a stroke a shape ("Stroke to shape") and a text its letters ("Text to
shapes").

**Professional tools** (`SvgPro.kt`, tested; `SvgSymmetry.kt`, `SvgActions.kt`).
- *Live symmetry.* A group marked `data-ffg-symmetry="kind;count;cx;cy"`
  holds a source group and copies of it (`data-ffg-copy`), mirrored left and
  right, top and bottom, in four quarters, turned 2 to 24 times, or turned
  and mirrored (a kaleidoscope), about the page's centre. Whatever the editor
  adds goes into the source; after every change (live gestures too) the view
  model makes the copies again from it, only when the source changed, each
  copy keeping its keys where its tree has the same shape (the renderer's
  cache is by key). Copies are real SVG content (the file looks right in any
  viewer) but are skipped by `hits`, so only the source is picked; the
  layers leave them out. The stage draws the axes and the centre, and the
  pen's stroke on every side as it is drawn. Turned off, the copies are kept
  as ordinary groups or taken away.
- *Grid.* A number of squares across the shorter side; drawn on the page
  (every fourth line stronger) and, when asked, its lines added to the snap
  targets of a move (`SnapTargets.withGrid`).
- *Magnetic points* (`SvgPointSnap.kt`, tested). A point dragged in the
  points mode, or placed with a tap while building a line, lands on what is
  near it (`snapPoint`): another point of the drawing first (`pathPoints`,
  and the symmetry's centre); else the page's edges and middle and, when
  moves snap to the grid, its lines and the middles of its squares
  (`gridSnapLines`), each way on its own; else the symmetry's axes and, for
  a turn, the lines half way between them (`Symmetry.snapRays`), or where
  one crosses a line the point already rests on. The stage draws the guides
  and a ring that pops on the landing, with a haptic tick.
- *Locking.* `data-ffg-locked="1"`: skipped by `hits` and by select all;
  unlocked from the layers' padlock.
- *Repeat.* In a row (across or down, a gap between the boxes), in a grid,
  or turned round the page's centre: each copy duplicated from the last and
  placed from the original's place, so the stack stays in order.
- *Mirrored copy*: a copy flipped and laid beside the original.
- *Paths.* Simplify (each piece flattened, thinned within 0.4 % of the
  page by Ramer-Douglas-Peucker, smooth curves laid through), smooth (every
  corner a Catmull-Rom curve), reverse (each piece end to start, a curve's
  controls swapped), break apart (each piece a shape), join (several shapes
  one even-odd path, in the first one's units and style).
- *Align on the page*, for several, as well as on their box.

**Gradients** (`core/vector/svg/SvgGradients.kt`, tested;
`SvgGradientEditor.kt`). In the look sheet a fill or an outline is none, a
colour or a gradient (a line has no box for one: colours only). The editor
shows a gradient as a `GradientSpec`: linear or radial, an angle, stops
(offset, colour with its opacity). On the sheet: a tile painted as the shape
is, a bar of the stops (a tap adds one in the colour the gradient has there,
up to eight; a stop dragged along stays between its neighbours), the kind,
the angle (resting on every 15 degrees), and the chosen stop's colour,
opacity and place; reverse and remove. `paintWithGradient` writes it into
the definitions at the top (into the first `defs`, or a new one): in place
when the gradient is the node's alone and written plainly there, else as a
new `ffg-gradient-N`, so that a duplicate, a symmetry copy or a file's
shared gradient never changes with it. When only the stops change, the
gradient keeps where it lies as written (its units, coordinates and
transform, a traced one's pixels too); a new kind or angle lays it across
the node's box (`objectBoundingBox`, long enough to reach the far corners).
When the drawing is written, the app's own gradients (`ffg-` ids) that
nothing names any more are left out (`withoutUnusedGradients`); a file's
own are always kept, as some programs keep unused ones as swatches.

**Continue editing** (`SvgDrawingStore`). A moment after each change (and
when the editor is left) the drawing is kept in `files/drawings/<id>/`
(`drawing.svg`, `name.txt`, `thumb.png`) when the app keeps projects; its
cards (`ProjectCard.drawing`) join the recent projects on the home screen,
newest first, and open, copy and delete as they do.

**Paint bucket** (`core/vector/RegionFill.kt`). The drawing is drawn into a
picture (at most 1400 px), the space under the finger flood-filled up to any
other colour, gaps closed by growing the borders and growing the space back,
then traced (mono) into an even-odd path laid just above what is filled
there.

**The drawing** (`core/vector/svg/`). `Xml.kt` reads and writes the file by
hand (a doctype is skipped, its entities never expanded: nothing outside the
file is read). `SvgDocument` holds it as the editor sees it: groups, basic
shapes and paths, texts, pictures, each with its transform and its style;
definitions, styles, metadata and every element the editor does not know
(`SvgOther`: a use, a clip path...) are kept and written back as they were.
Styles are read as CSS orders them (presentation attributes, then the file's
simple class, tag and id rules, then the inline style) and written back
inline, so that what the editor changes wins over the file's rules.
`SvgGeometry.kt` has the transforms (`Affine`, SVG's matrix) and the whole
path grammar (relative commands, shorthand curves, arcs made into cubics).

**Editing** (`SvgEdits.kt`). Nothing changes in place: every change (move,
size, turn, hide, duplicate, delete, reorder) gives a new drawing, and undo
keeps the old one (`SvgEditorViewModel`, a step per gesture). A transform of
the drawing's pixels is laid on a node's own transform through what its
groups and the view box do (`transformInWorld`), so a node moves under the
finger whatever it sits in. A tap finds the node on top there (`hits`: filled
by the non-zero or even-odd rule, or near its outline); a tap on the
selection reaches the one under it.

**Drawing it** (`SvgRenderer`). From the editor's own model, onto the
stage's canvas, so that a change shows at once: outlines, solid colours,
gradients, dashes, opacity, texts and embedded pictures are drawn there;
`SvgOther` nodes and texts placed piece by piece are drawn by AndroidSVG
within the same transforms. Clip paths, masks and filters are not drawn while
editing (they stay in the file).

### ASCII and ANSI art

**Saving one** (`OutputFormat.ASCII`, a `.txt`; `OutputFormat.ANSI`, a
`.ans`). FFmpeg makes the picture as a PNG, as for an SVG, transparency kept
(a cut-out's background is left empty in the art); the engine reads it
(`ImageRecoder.read`) and writes it as text (`core/textart/`,
`ImageEdit.textArt`). Only what the text-art editor shows goes into the PNG:
`ConversionSettings.forTextArt` and `ImageEdit.forTextArt` leave out the
captions, stickers, drawing, border, corners, canvas, HDR look and output
size, and the command is always the one the app makes. The characters are
chosen with the system's monospaced font as `AndroidGlyphAtlas` (`:media`,
behind `GlyphAtlasSource`) measures it: each character the editor offers
drawn at 96 px and its ink averaged in 4 x 8 sub-cells, the characters it has
no glyph for and those drawn wider than a cell noted. The editor and the
export share that one measure, so they choose alike.

`TextArtRenderer.render` (deterministic, no randomness anywhere):

1. the grid: `columns` across, the rows from the picture's shape and a cell's
   width over its height (the font's, or `cellAspect`), or `rows` when set
   (the picture then stretched or squeezed to fill the grid);
2. the picture read once into samples, every pixel into exactly one, its
   colour weighed by its opacity: one a cell, or a part of one where the mode
   looks closer (two halves, two by two quadrants, two by four Braille dots,
   the atlas's sub-cells for shapes);
3. the tone, on OKLab's lightness: histogram equalisation, brightness,
   contrast, gamma, a five-point curve, negative, the paper (light ink on a
   dark page or dark on light), edges sharpened (unsharp mask);
4. what is left out (`ArtHide`): below the opacity cut, a band of lightness,
   arcs of hue (only where the chroma is above the band's minimum), colours
   within a tolerance in OKLab, all of it turned round on asking, then the
   brush's strokes in order (hiding or showing again);
5. the characters, by mode: the ramp (the alphabet sorted by measured ink,
   near duplicates merged), shapes (least squares against each glyph's
   sub-cells), lines along Sobel edges (`- / | \`, the rest from the ramp),
   shades, half blocks, quadrants, Braille, or the letters of a phrase; with
   the alphabet's limits (a bias a character, the largest share of the cells
   one may take, the longest run) and ordered (Bayer 4 and 8) or error
   diffusion (Floyd-Steinberg, Atkinson) dithering;
6. for an ANSI file the colours: saturation, the light left to the
   characters if asked, k-means down to a number of colours, then matched to
   16 (a terminal theme's), 256 (xterm's) or true colour by OKLab, CIEDE2000
   or RGB, entries banned or painted as others, dithered if asked (the error
   kept small). With the cells coloured too, a cell's two colours are the
   2-means of its samples, and the glyph is matched to where each sample lies
   between them; a flat cell is a space on its colour; of two halves or
   quadrants the lighter is the character, the darker the background (a 16
   colour background may have only the dark eight).

`TextArtLayout.compose` lays out the frame, margins, title and signature;
`TextArtWriter` writes UTF-8 text, or ANSI: SGR codes only where they change
(UTF-8), or a reset and every attribute at each change, bold for the bright
foregrounds and blink for the bright backgrounds, then the end-of-file mark
and a 128-byte SAUCE record without a date (code page 437, 16 colours,
Braille drawn as quadrants). `TextArtReport` is the editor's check: size,
characters and colours used, and what to look out for where the text is
going. `TextArtPresets` sets what each place (terminal, editor, Markdown,
chat, BBS) asks for.

The format picker shows the two among the text formats (`shownCategory`):
pictures to the engine, text to whoever picks them, offered for a file with
a picture and no sound (a still, or a silent video: no sound track, or a
track of silence, as a recording made muted has, found by listening to it
with `soundIsAudible` in `Waveforms.kt`: its first ten minutes, a peak
above -54 dBFS is sound, the reading stopping at the first one); a file
with sound gets the transcripts in Text instead. Then its banner plays the landscape of the
image film written in characters behind a scan line, coloured as ANSI art
(`TextArtShowcase` in `FormatShowcase.kt`), and says so; with sound it keeps
the transcript's film.

**The text-art editor** (`app/.../textart/`) opens instead of the photo editor
when ASCII or ANSI is picked. Its picture is the photo editor's
(`PictureRenderer`: the frame of a video, turned, cropped, cut out); the art
is made off the main thread from it at each change of the settings, laid out,
written and drawn (`TextArtPainter`: the characters in the monospaced font,
block elements and Braille dots as their shapes). The stage shows it in a
terminal window, alone, over the picture or beside it (a divider), typing
itself in row after row when its kind changes (not when animations are off);
a tap there picks a colour to leave out, a drag paints with the brush. The
tools: the picture (frame, turn, crop), the style (where it is going, the
mode with each drawn small, the size: columns and lines chained to keep the
picture's shape or set apart, a frame of the grid's shape and common widths;
the page, the cell's shape), the
characters (presets, groups, a key for each character with its measured ink,
switched off with a tap, preferred or avoided with a long press, the safety
filters and the limits), the light (tone, curve, edges, dithering), the
colours (ANSI: depth, theme, layers, matching, saturation, reduction, the
palette with banned and remapped entries), what is left out (the cut-out
panel of the photo editor, the picture's own colours, the hue ring, the
lightness band, the brush), the layout and the check (with copying the text,
as a Markdown code block, or with its colour codes).

### GIF

A GIF is pictures only. With GIF chosen the editor has no sound tools (no Audio
row, no sound lane, no volume of clips or of videos laid over the picture), no
frame extraction and no remux, and the preview plays without sound; the request
drops placed sounds, stream copy and frame extraction left over from another
format. Its own settings are `GifSettings` (`core/model/GifSettings.kt`), in the
GIF tool and in the export sheet in place of resolution, frame rate and
quality: width (`scale`, never larger than the video), frame rate (`fps`),
colours (`palettegen=max_colors`), dithering (`paletteuse=dither`: Sierra-2-4A,
Floyd-Steinberg, Bayer at scale 3, none), how many times it plays (`-loop`) and
a lighter file (`stats_mode=diff` and `diff_mode=rectangle`).

The preview plays the plain video, smooth and silent: it does not imitate the
GIF's size, colours, dithering or frame rate (those show in the exported file).

## Settings

`settings/` holds them: `AppSettings` (theme, wallpaper colours, pure black,
vibration, copies in the phone's folders, recent projects and how many to
keep, Live Updates) kept with Preferences
DataStore by `SettingsRepository` (a `StateFlow` that is null until read; the
splash screen waits for it so that the first frame has the chosen colours).
The splash screen itself (its animated shapes, their timing and the hand-over
in `MainActivity`) comes from WiFi Audio Streaming for Android
(https://github.com/marcomorosi06/WiFiAudioStreaming-Android), by the same author and under
the same licence (its copy of the EUPL in `assets/licenses/wifi-audio-streaming/`,
pinned in `scripts/licence-copies.lock` and checked by
`scripts/check-licence-copies.py` before every release); the glyph and the colours are this app's.
Where each one acts:

- theme, wallpaper colours, monochrome, pure black: `FFmpegGUITheme` and the
  editors' `EditorTheme` (`ui/theme/Theme.kt`: `appColorScheme`, `onBlack`).
  The Appearance page picks the palette on three tiles, each drawn with its own
  accents: the wallpaper's (Android 12+), the icon's teal, or monochrome
  (`MonochromeLightColorScheme` / `MonochromeDarkColorScheme` in
  `ui/theme/Color.kt`: greys only, built like Material's monochrome scheme, the
  tool families apart by tone, errors still red). Monochrome changes the app's
  colours, not its launcher icon. Without
  wallpaper colours (or before Android 12) the app wears the tonal-spot scheme
  of the icon's teal #00897B, generated with material-color-utilities
  (`ui/theme/Color.kt`). The icon follows too (`ui/theme/AppIcon.kt`): in the
  app (home title, About) through `appIconBackground`/`appIconGlyph`, and on the
  launcher through two activity aliases of `MainActivity`, `LauncherDynamic`
  (wallpaper colours) and `LauncherTeal` (`ic_launcher_teal`); `AppIcon.sync`
  enables the matching one in `onStop`, enabling before disabling so that the
  app always has a launcher entry;
- vibration: off, `MainActivity` provides a `LocalHapticFeedback` that does
  nothing, so every haptic of the app goes quiet at once;
- copies: after a successful export the view model copies the file through
  MediaStore (`export/PublicCopies.kt`, Android 10+, no permission) to Movies,
  Music, Pictures or Download, in an FFmpegGUI folder; the done screen says
  where. `export/ExportedFiles.kt` measures and empties the app's own exports;
- phone's video chip: the view model sets `EncodingSettings.hardwareVideo`
  on every request (the export and the command shown), see "Hardware video
  encoding" below;
- Live Updates: `AndroidExportNotifier` asks for promotion only when on;
- recent projects: see "Recent projects" below. Turning them off asks first,
  then deletes every project kept.
- with the screen on its side (Editor): `LandscapeLayout`. As it fits (the
  default) the editors lay themselves out by the window's size, as before; with
  the player on the left or on the right, a window wider than it is tall always
  puts the player beside the tools, half the width each, whatever the video's
  shape (the largest preview for a vertical one). `SideBySide`
  (`LayoutChoice.kt`) lays the two out without building the player again when it
  changes side (it would start over): the halves slide across with the
  theme's spatial spring. Turning the phone changes the layout itself (stacked,
  side by side, studio): the video editor's preview is movable content
  (`movableContentOf`), moved to its new place rather than built again, so the
  video goes on from the same instant, playing or paused. Should a preview be
  made again all the same, its new player starts where the playhead was
  (`EditorPreviewPlayer.startAt`), not at the beginning. On a landscape screen the editors' top bar has the
  switch (`ConnectedSwapSidesButton`) that moves the player to the other side
  and keeps the choice, joined to Export as one connected group. While the
  settings keep the player on a side, the group is led by the way back to the
  usual layout (`ConnectedUsualLayoutButton`, the setting back to "As it
  fits"), but only where that changes something (`offersUsualLayout`: not on a
  window so low that it would be side by side anyway, a phone on its side).
  Beside the player the tools have the whole height, so the video editor's
  timeline grows into it (`EditorTimeline`'s `maxHeight`, up to what its rows
  need) and shows more rows of lanes before they scroll, instead of leaving
  the space empty above the tool row;
- the editors' top bar (`EditorTopBar` in `ConfigScreen.kt`, shared by the
  video, audio, GIF and photo editors): a floating pill on `surfaceContainer`,
  Close a tonal button that squares up under the finger, the file's name cut in
  the middle (its extension stays in view) over its target format, Export the
  one filled button. Every target is 48 dp; the bar is at least
  `EDITOR_TOP_BAR_HEIGHT` tall and grows with large text instead of cutting it;
- the sound of the clips (Editor): the video editor's timeline draws each
  clip's own sound along the bottom of its frames (`drawClipSoundBand` in
  `EditorTimeline.kt`, from the same `WaveformCache` the placed sounds use: the
  peaks of the clip's part of its file, growing up from the bottom over a dark
  band, faint when the clip is muted). Not for a GIF, a picture or a file without
  sound; an audio file's clips are their waveform already.

The pages (`SettingsScreens.kt`, `SettingsUi.kt`) are grouped lists in the
Material 3 Expressive style: the rows of a group touch, very round at the
group's ends; each row's badge is a `MaterialShapes` shape that morphs into a
circle under the finger, with a spring squish and a haptic (toggle on/off for
switches, confirm and reject in dialogs). A list of categories (each with its
shape and colours), then the project (About, the source on GitHub, Ko-fi) and
a reset. Page titles are set in the display face (Google Sans Flex, rounded
and wide). `AboutScreen.kt` has no title, its hero says it: the logo grows in
with a spin and keeps morphing through full shapes (no deep pinches, the glyph
drawn smaller than on the launcher so it always fits) on a slow turn (a touch
skips ahead with a bounce and a tick);
the name in the display face; the motto ("Why would a video editing app need
internet permission?"); FFmpeg's version from FFmpegKit (`media/FfmpegInfo.kt`);
the licences (the typeface's OFL text from the assets). Links open in the
browser: the app itself never goes online.

**Settings pictures** (`SettingsArt.kt`). Every other category, and the
About page, opens on an `ExpressiveStage`: a very round card in a gradient of
two containers with three big library shapes behind, turning each its own way
and changing shape in turn. In front, what the category changes, made of
shapes and moved by the theme's springs (overshoot and all), following its
settings: the app in the current theme (the welcome's `PhonePreview`), clips
as pills under a gem playhead that hops between them (their sound bars only
when the waves are on), a phone in a blooming badge that kicks with each buzz
while shapes fly out (asleep with the taps off), a file flying in an arc into
one folder or two, the projects fanned or closed (`ProjectsArt`) and the
export's notification (`NotificationArt`). Hidden from screen readers, still
when the system's animations are off. The appearance and vibration pages use
the welcome's own controls (`ThemeTile`, `HapticPad`); About groups its
licences in two folding sections (in the app, models you download).

**Smart features** (`SmartHub.kt`, `SmartShowcase.kt`, `SmartSettings.kt`).
Not one long page but a hub and a page per feature. The hub: a hero (the
features' badges going round a turning shape, a ring of how many are ready,
the privacy promise, the speed switch for every model with a chip of nine cores lit in turn while it is on),
then a tile per feature (speech, translation, cut-out, eraser, voice apart,
dubbing), two by two, each playing its own small film drawn by the app in
the theme's colours (the welcome's art where there is one: the spoken words,
the cut-out), with a line and a status pill (ready and its size, not set
up, importing, needs a look); last, the models' licences. A tile opens the
feature's page: its film large, its badge blooming in, a line, its status,
and only its own cards. Pages come in sliding and growing, and back goes to
the hub (`BackHandler`). Screens that send the user to set something up name
the page (`SmartPageRequest`), so "Get the eraser model" lands on the
eraser's page.

## Recent projects

Every opened file is a project. Once something is changed (or exported),
`ConversionViewModel` hands it to `ProjectStore` (`app/.../projects/`) 0.8 s
after the edits rest, and once more when the project is closed (another file
opened, the app left). The home screen shows them as a "Continue editing"
carousel (`RecentProjects.kt`: a Material 3 multi-browse carousel, cards that
round into pills as they shrink; a tap opens the editor where it was left, a
long press opens a sheet to open, duplicate, share the last export or delete).
`openProject` restores the files, edits and format; the undo history is not
kept.

Privacy first: everything is in `files/projects/<id>/`, private to the app,
never backed up (`allowBackup="false"`) and never sent anywhere (the app has no
network permission). A project folder holds:

- `project.json`: the editor's state as plain, indented, ASCII JSON with the
  code's own field names and a header saying what the file is (`ProjectJson`;
  the generic JSON reader and writer and the :core models' mapping are
  `core/project/Json.kt` and `ModelJson.kt`, no library). Readers fall back on
  each model's defaults, so files of other versions still open;
- `thumb.jpg`: the first frame as it shows (mirrored, turned, cropped), made
  again only when that changes;
- `media/`: copies of what could not be opened again later: camera
  recordings, stills and covers (made in the cache, which is emptied) and files
  another app only lent (a file opened from a share). Files picked with the
  system picker are not copied: the store keeps their persistable read
  permission and gives it back when no project (and not the open editor) uses
  them any more. Files no longer used are removed when the project is closed
  (until then an undo may bring them back).

All the store's work runs in order on one background worker, so a save, a
deletion and the cache clean-up after a project is closed never overlap (the
cache files of a closed project are deleted only after its last save copied
them). Only the newest `projectsLimit` projects are kept; a project the user
deleted is never brought back by a late save. The settings page lists every
project with its size, missing files and copies, and deletes one or all.

## Frame by frame

Beside play / pause two arrows move the playhead one frame back or forward
(`FrameStepButtons.kt`), for cutting exactly where a finger on the timeline
would glide past; held, they step again and again, faster the longer they are.
`FrameStep` (`core/preview`, unit tested) counts the frames in each clip's own
file at its own rate (`MediaInfo.frameRate`, 30 a second when not known): a
step lands in the middle of the part of the next (or previous) frame the clip
shows, so that the preview's exact seek shows that very frame; past a clip's
end it goes on to the first frame of the next clip, before its start to the
last frame of the one before, and stops at either end of the video.
A tap is an exact seek; the steps of a hold are scrubs (see "Live preview"),
so the picture follows them frame by frame, and the frame it stops on is then
shown exactly.

The row under the preview never lets two controls overlap (`ControlRow` in
`ConfigScreen.kt`): the right group (the curve button of a keyframe, which
grows out of the diamond, the diamond, undo, redo) is laid first, play / pause
with its arrows stays in the middle while it fits and moves left when it does
not, and the clock takes what is left: on one line, on two (the time above the
length) or the time alone (`AdaptiveClock`).

## Live preview

The editor shows every setting before anything is rendered. No FFmpeg run is
involved: `EditorPreview.kt` nests the same layers as the export filter graph
(the clip on screen with its own geometry, colour, captions, fade and pictures,
fitted into the project frame; then the whole video's geometry, captions,
subtitles, fade, pictures and canvas). `preview/EditorPreviewPlayer.kt` drives
a player for the picture and source audio and one per placed sound
(`preview/SoundVoices.kt`; a sound's player exists only from a few seconds
before it starts to shortly after it ends, so only overlapping sounds hold a
decoder at the same time). A sound's speed and pitch are the player's
`PlaybackParameters` (set apart, as in the export), its repeats the player's
repeat mode, its equaliser the export's own filters run in an `AudioProcessor`
of its player (see "Audio files"), and its levelling a gain from the file's measured loudness; while playing, the
output clock is the master and each clip is driven to where it should be, at
its own rate or stepped backwards. The math that makes the preview agree with
the export lives in `:core`, package `preview`, and is unit tested:

| Setting                     | How it is previewed                                                    |
|-----------------------------|------------------------------------------------------------------------|
| clips, cuts, whole-video trim | clipped playlist; one output clock across all clips (`PreviewTimeline`) |
| stills (pictures, freeze frames) | timed image items keep the clock; the picture is drawn over the video surface with the clip's colour and geometry |
| rotation, flip, crop, canvas| nested view transforms and layout, clip inside whole (`PreviewGeometry`) |
| brightness/contrast/saturation | colour matrix equal to FFmpeg `eq`; clip and whole matrices combined |
| speed (clip and whole)      | player rate changes from clip to clip, pitch preserved like `atempo`   |
| reverse (clip and whole)    | the paused player is stepped backwards; source audio is muted          |
| fades (clip and whole)      | black layers + audio volume, same ramps as `fade`/`afade` (`fadeLevel`) |
| text, image overlays        | Compose layers using the export's position and size rules, shown during their windows of the output clock with the same opacity and fade ramps; an overlay's own colour matrix, turn (a layout that reports the turned box, as the export places it) and mirrors |
| video overlays              | a silent player per video on a `TextureView`, driven to the output clock every 50 ms (loaded 1 s before it shows); its sound plays as a placed sound |
| still motion (Ken Burns)    | the still is scaled and shifted with the clip's progress, the same curve as `zoompan` |
| subtitles                   | cues parsed by `SubtitleParser`, moved with their footage like the export; styled ones (spoken, or .srt) drawn as libass will (see "Smart subtitles") |
| volume above 100 %          | `LoudnessEnhancer` on the player's audio session                       |
| loudness normalisation      | source loudness is measured once (ebur128), then a gain to the loudnorm target |
| placed sounds               | a player per sound, kept in sync with the output clock (place, trim, volume, fades, speed, pitch, repeats, equaliser, levelling) |
| sound effects (final sound, each placed sound) | sample-exact ports of the FFmpeg filters in an `AudioProcessor` of every player; pitch as the players' pitch; the waveform view drawn from the output's outline |
| stream copy, frame extraction | filters are ignored, exactly as the export ignores them              |
| denoise (clip and whole)    | not live, as it works over time: a badge on the picture and a mark on the clip say it is there, and the Details panel shows it close up, made by FFmpeg with the export's own filter (see "Denoise") |

Dragging the timeline asks the preview for a frame about 20 times a second.
Plain exact seeks would each start over before the one before had shown
anything, so the picture stood still until the finger stopped; instead the
timeline's seeks are scrubs (`PreviewSeek.scrubbing`,
`EditorPreviewPlayer.scrubMediaFrame`): the main player goes into Media3's
scrubbing mode (`ExoPlayer.setScrubbingModeEnabled`, Media3 1.8 and later),
which shows each frame as soon as it can, and leaves it once no frame has been
asked for 180 ms, showing the last one exactly. A tap, a frame step or a seek
from the media notification stays a plain exact seek.

From one clip to the next the main player goes on along its playlist without
a seek. A clip rarely starts on a key frame, so its first frames have to be
decoded from the key frame before it; on one decoder that held the picture for
a moment at every cut. The main player is built with Media3's video renderer
prewarming (`processingPlayer(..., prewarm = true)`,
`DefaultRenderersFactory.experimentalSetEnableMediaCodecVideoRendererPrewarming`):
a second decoder gets the next item ready while the current one plays, and the
two take turns. The other players (placed sounds, the clip coming in through a
transition) play one item and do without.

At the end the video starts over, like a player on repeat, but the main
player does not repeat on its own (`REPEAT_MODE_OFF`): the output clock wraps
and seeks it to the start at once. On repeat, the prewarming decoder got the
first clip ready as the "next" item and the two decoders swapped on the
surface at every loop; after a few loops the picture stayed on the last
frame with no error (issue #32: the decoder reconnected to the surface,
showed a few frames and stopped). Stopped at the end of its last item,
perhaps a little before the clock (whose lengths are the editor's), the
player is not followed: the clock runs on to the end, the last frame held,
and wraps. A player that fails
(`onPlayerError`) would stop on its last frame for good, since seeking does
nothing until it is prepared again: it is prepared again where the clock is,
up to three times within ten seconds, and the error goes to Logcat (tag
`PreviewPlayer`).

The editor's timeline is one `Canvas` drawn again on every frame while the
preview plays (the playhead stays in the middle and everything moves under
it), so its drawing keeps the work per frame small: the bars of every waveform
(the clips' own sound, an audio file's clips, the placed sounds) are collected
in a `BarBatch` and drawn with a single `drawLines` call instead of a rounded
rectangle each, and stay anchored to their clip as it scrolls (one every few
pixels from the clip's left end, not from the screen's edge, so they no longer
shimmer); the dark band under a clip's sound reuses one gradient
(`BandShade`); the ruler writes its times without `String.format`.

What a live preview cannot be: reversed *audio* is not audible (muted), loudness
normalisation is a constant gain rather than loudnorm's dynamic processing, and
the styling of .ass files is not reproduced (libass does that at export; the
app's own styles, for spoken subtitles and .srt files, are).
The final sound's effects run on each player apart, so where sounds overlap the
gate, compressor, limiter and lo-fi (which are not linear) act on each sound
rather than on their mix; with the speed changed, echoes and modulation run at
the footage's own pace. The audio encoding (bitrate, bit depth, sample rate) is
not heard, like the picture's encoder settings.
Resolution, frame rate and compression are encoder settings chosen in the
export dialog; they do not change the picture shown in the editor.

## Text

All user-visible text is in `app/src/main/res/values/strings.xml` (English).
The `:core` enums carry no labels; `app/.../Labels.kt` maps them to string
resources, and `conversion/ConversionMessages.kt` does the same for engine
status and errors.

### Colour key (green screen)

A colour can be cut out of a clip, of the whole video, of a picture or video
laid over the video, of a photo and of its stickers: `ColorKey`
(`core/model/ColorKey.kt`) in `Effects.key`, `ImagePlacement.key` and
`ImageEdit.key`. It cuts by hue (`chromakey` on yuva444p: a green or blue
screen whatever its light) or by the exact colour (`colorkey` on rgba), with a
tolerance, a soft edge and spill removal (`despill`, on green and blue screens
only); see `core/command/KeyFilters.kt`. Where it goes:

- a clip or the whole video: after its colours, before captions; what is cut
  shows the key's background (black without one: a video file holds no
  transparency). In a filter chain that is a little graph of its own, the
  keyed picture split and laid over a copy filled with that colour
  (`drawbox ... t=fill`), its links named after the clip so they stay unique;
- a picture or video laid over the video: after its own colours and mirrors,
  before it is sized and turned; what is cut lets the video show through. Its
  own transparent parts (a PNG sticker) stay so: FFmpeg's keys write the alpha
  plane, so `keyKeepingAlpha` keys a copy and multiplies the two alpha planes
  (`alphaextract`, `blend=all_mode=multiply`, `alphamerge`), and the preview
  multiplies them the same way (`ColorKeying.apply(keepAlpha = true)`, the
  integer `a * b / 255` of blend);
- a photo: last of its steps (`ImageStage.Key`), before texts and stickers.
  The photo's steps run on rgb24, so they read a transparent picture opaque
  (`Raster.opaque()`, `keepAlpha = false`). A picture with an alpha channel of
  its own (`MediaInfo.hasAlpha`, told by the probe from a small decoded copy)
  saved as PNG, WEBP or TIFF keeps its transparency all the same
  (`ConversionRequest.keepsPictureAlpha`): the export splits its alpha off at the
  start (`alphaextract`), puts it through the same geometry as the picture
  (mirrors, turn, keystone, straighten, crop) and multiplies it back into the
  alpha the steps left (a key's, 255 without one) at the end
  (`blend=all_mode=multiply`, `alphamerge`); the editor does the same in Kotlin
  (`alphaOf`, `shapeAlpha`, `mergeAlpha` in `core/image/ImageEditing.kt`, in
  `PictureRenderer`) and shows the checkerboard through it. Saved as JPG or BMP
  the picture stays opaque, as before. The stickers laid on it keep their
  transparency as overlays do. PNG, WEBP and TIFF keep the cut transparent
  (`-pix_fmt rgba`, `overlay` with `format=auto` so the stickers keep it), or
  show a fill colour at an opacity (`ColorKey.backgroundOpacity`,
  `keyOverTranslucentBackground`: the keyed picture laid with `overlay` on
  packed RGB over a copy turned into the fill by `lutrgb` with constants,
  rgba); JPG and BMP show white (`ImageEdit.keyFor`) or the chosen colour,
  always opaque, in full colour (yuv444p).

`ColorKeying` (`core/preview`) does the same in Kotlin: the key colour's chroma
with chromakey's own fixed-point formula, each pixel's in TV range, the 3 x 3
average (neighbours outside the picture keep the value their slot last had, as
FFmpeg's loop does), the opacity ramp truncated as FFmpeg does, and despill's
float round trip; a see-through fill with overlay's own integer math
(`overlayStraight`: the overlay's alpha un-premultiplied against the main
picture's, `FAST_DIV255`, the alphas composited). Against FFmpeg 6.1 on a
noisy green screen the alpha is exact on 99.5 % of the pixels (at most 3 off),
the colours within 2 (FFmpeg goes through YUV and back); over a see-through
fill the premultiplied colours are within 3, and exact for an exact-colour
key. The previews use it:

- the photo editor runs it as one of the steps of its picture, and shows a
  checkerboard where the picture is transparent;
- pictures (stills, stickers, pictures laid over the video) are keyed once on
  the CPU, after their own colours;
- video, on Android 13 and later, is keyed on the GPU: a RuntimeShader (AGSL)
  with the same math as a RenderEffect of the layer (`preview/KeyEffect.kt`,
  `ColorKeyLayer.kt`), on the clip's layer, on the joined video's (whole
  video), on a video laid over the picture (then the colours of what it lies on
  as a colour filter after it). The shader keeps to the plainest AGSL (no
  loops, no ternaries, no branches on uniforms; checked with Skia against the
  Kotlin port) and is compiled once behind a guard (`KeyShader.compiles`): a
  device whose compiler refuses it gets the CPU path below instead of a crash.
  The uniforms are named by constants used both in the shader's text and
  where they are set: setting a uniform the shader lacks throws, and Android
  13 builds that exception's message from garbage, which aborts the app under
  CheckJNI ("input is not valid Modified UTF-8") instead of throwing - the
  crash a misspelt name once caused as soon as a video was keyed.
  Before Android 13, with a refused shader, and under a GIF's frames, the
  frames are grabbed from the surface and keyed on the CPU, at up to 480 px and
  24 frames a second (`ProcessedFrames`, which also makes the GIF frames; the
  pixels are read and keyed off the main thread).

The key panel (`KeyPanels.kt`) shows a sample of what is keyed as it looks
before the key (the frame under the playhead with its colours, or the picture
laid over the video), with the result on a checkerboard:

- switched on, the key starts from the colour the sample suggests
  (`ColorKeying.suggestedKey`: the commonest colour of the sample's border
  band, in 4-bit bins; a clearly green or blue one is cut by hue, anything
  else exactly); the Detect button does it again;
- touching or dragging on the sample picks the colour under the finger, live,
  with a loupe above the finger that magnifies the pixels there (sharp, the
  one taken framed) and is ringed in the colour taken; a held compare button
  shows the original; another takes the frame under the playhead again;
- the colour taken is always shown with its code and mode; quick colours
  (green screen, blue screen, white, black) set the mode that suits them;
- then the mode, the edges (tolerance, soft edge, spill where it applies) and
  the fill: transparent (the default where the output keeps transparency) or
  a colour, with its opacity where the result can hold it (a photo saved as
  PNG, WEBP or TIFF); a line says what the output does with it.

## Blend modes

A picture, a video or a shape laid over the picture or the video can mix with
what lies under it instead of covering it (`ImagePlacement.blend`,
`LayerBlend` in `core/model`): multiply, screen, overlay, darken, lighten,
colour dodge and burn, hard light, difference, exclusion, add. They are the
separable modes of the W3C compositing spec, which Android draws too.

**Export.** `layOver` (`CommandBuilder.kt`) lays a NORMAL picture with
`overlay` as before. A blended one is first laid on a see-through copy of the
frame (`colorchannelmixer=aa=0`, `overlay=...:format=rgb` with its own place,
time window, turn and fades, so it moves exactly as it would); the two are
mixed by `blend=all_mode=...` in planar RGB (`gbrap`), the picture as the top
input, and the mix is kept only where the picture is (`alphaextract`,
`alphamerge`, then `overlay` on the frame): the frame times (1 - alpha) plus
the mix times alpha, which is how Android composites a layer with a blend
mode. FFmpeg's `hardlight` tests its bottom input, so it is the spec's overlay,
and its `overlay` is the spec's hard light (`LayerBlend.ffmpegMode`). Soft light
is left out: FFmpeg's formula is not the spec's.

**Preview.** The picture's layer in `ImageOverlaysLayer` gets the mode as its
`blendMode` (`composeMode()`, `BlendPanels.kt`), drawn over the photo or the
video's `TextureView`. Texts are always laid normally (`drawtext` cannot blend).

**Panel.** `ImageBlendPanel`: the chosen mode large, in a badge that morphs into
the mode's own shape, and every mode on a tile that shows itself (a warm-to-cold
backdrop with a disc mixed onto it by that mode). In the picture editor it is a
tool of a sticker or a shape, in the video editor of a picture, a video or a
shape laid over it.

## Video effects

Sparks, fireworks, flames, smoke, snow, rain, confetti, dust in the light,
bokeh, hearts and math loop over the video for as long as they are laid; the fairy's
wand, an explosion and a flash are transitions laid over the cut between two
clips. None comes from elsewhere: `scripts/generate-effects.py` draws every
frame (numpy, scipy, fixed seeds) and encodes them with FFmpeg into
`app/src/main/assets/effects/` (H.264, 1280x720, 30 fps),
with `index.json` saying what each is (`kind` loop or transition, `frames`,
`fps`, for a transition the second of the `cut`, under its brightest frame,
and its `sheet`: a JPEG of small frames, 160x90 at 15 fps, eight a row). The
script is only run to make them again (`--index-only` makes the sheets and the
index from the videos there); the build uses the files.

**Math.** `scripts/math_effect.py` draws the formulas, question marks and
little diagrams (a right triangle, a sine wave, a circle and its radius, a
parabola, a turning wire cube) of the confused-maths meme with its own stroke
font (each glyph a few lines and arcs, no typeface): each item is written on
stroke by stroke with a bright chalk tip, drifts, turns and comes closer, and
fades; two share each cell of a 6x4 grid, one showing while the other is away,
so the frame never clumps. Points along the strokes are splatted into
`Canvas` (sharp near, softer far, with a glow), as the other effects.

**Light on black.** Each effect is drawn as light on black and laid with the
screen blend (`LayerBlend.SCREEN`): black disappears and the light adds up, so
no transparency is needed and every phone decodes the files. A looping effect
loops seamlessly: every particle has a phase in the loop, falling things cross
a whole number of frame heights per loop, and the noise of flames and smoke is
periodic in time.

**Library.** `EffectLibrary` (`app/.../effects/`) reads the index when the app
starts and copies each file into `files/effects/` (players and FFmpeg read a
file, not an asset), again when a new version brings a different one. Projects
keep these copies' URIs as they are (`ProjectStore.keep`, `EffectLibrary.owns`).
The gallery, the effect's panels, the layers list and the transition tiles never
decode a video: they draw the effect's sheet (`EffectLibrary.sheet`, read once,
RGB 565), one frame after the other at the effect's own speed, in the drawing
phase only (`EffectFrames`, `drawSheetFrame`), so they show at once.

**An effect laid over the video** is an `ImageOverlay` with `effect` (its name)
and `hue` (degrees), full frame, screened, on the effects lane of the timeline
(`LaneKind.EFFECT`, the first lane, as effects lie on top) and above everything
placed later (`effectLayer` leaves a thousand layers under it). Its tools are
which effect (`EffectChoiceContent`), its look (strength and colour,
`EffectLookContent`), fades and timing; it is added from the gallery
(`EffectPickerSheet`), every tile playing its own frames, opened from the tool
row ("Effects") or from the effects lane.

**Export.** `toPlacement` makes it an `ImagePlacement` with `cover`,
`hueDegrees` and `OverlayVideo(loop = true)`: the input is read with
`-stream_loop -1` (and `-t` for as long as it shows), moved to its start
(`setpts`), turned round the colour wheel (`hue=h=`), scaled to fill the frame
and cut (`coverScale`, from `wholeFrameSize`), then laid by `layOver` with its
opacity and fades.

**Preview.** `EffectsLayer` (`EffectOverlays.kt`), inside `StackedOverlays` in
the stacking order: a silent ExoPlayer per effect on screen, repeating, kept at
(now - start) modulo the loop's length; its `TextureView` fills the frame as
the export's cover does, with the hue as a colour matrix (`hueColorMatrix` in
`core/preview`, the chroma of BT.601 YUV turned as `hue` turns it) and
`BlendMode.Screen`.

**Transitions.** A `Transition` with an `effect` joins its clips with a cut
(`transitionLengths` gives it no overlap, so the preview and the export cut
there). `transitionEffects` (`app/.../effects/TransitionEffects.kt`) makes, for
each, an overlay of the whole video playing the effect once, its cut on the
cut (`OutputClock.cutOutputMs`): `footage()` in `RequestMapping` adds them to
the pictures the export lays, and the preview adds them to the pictures of the
whole video, so the two always agree and the editor never keeps them. The
transitions panel shows them as a "Magic" family, each tile cutting between its
two pictures under the effect's own frames.

**Punches: an effect with its own opacity.** The 3D fists of
`TransitionKind.PUNCH` and `PUNCH_BACK` are not light on black: a fist hides
what is behind it. `scripts/punch3d.py` draws them (a right fist built of
rounded shapes blended into one signed distance field, ray marched in numpy,
lit by a key, a fill and a rim light, with soft shadows, ambient occlusion,
skin that lets light through near its edges, ACES tone mapping, six-sample
motion blur and the fist's soft shadow on the picture), as colour plus
opacity. `generate-effects.py` packs each frame as its colour over its opacity
(grey) in a plain H.264 MP4 twice as tall (1280x1440), so every phone and
FFmpeg read it; `index.json` marks them `"kind": "companion"` and
`"alpha": true`, with a sheet of 512x288 frames with transparency (WebP, six a
row, 30 fps). They are not offered among the magic transitions: each belongs
to its kind (`TransitionKind.companionEffect`). A punch is a recipe like the
others (the clip leans into the blow, the new clip springs back from it, or
the clip shakes as if struck) and lasts `PUNCH_MS` whatever its length was
(`Transition.requestedMs`, `TransitionKind.fixedMs`); `transitionEffects`
lays its fist over the stretch the clips overlap
(`OutputClock.transitionOutputRange`), the fist's cut on the moment the recipe
swaps the clips (`companionSwitch`), as large as `Transition.size` (30 to
100 percent, the panel's "Fist size", kept in the project). The export reads
the two halves apart (`ImagePlacement.stackedAlpha`: `split`, `crop` each
half, the lower half's luma taken out as it is with `extractplanes` and
stretched from 16..235 by `lut`, `alphamerge`; a `format=gray` there would
turn the whole split grey) and lays the fist with the normal blend, scaled to
`coverSize` of the cover frame, in the middle; a smaller fist fades out towards
the edges of its frame (`COVER_FEATHER` of its shorter side, a `geq` on its
opacity), so that its arm and the flash are never cut sharp. The preview draws the fist from
its sheet, the frame of the file at the output clock (`AlphaEffectFrames`),
so it never drifts from the clips; the tiles draw it over their two pictures
(`TileCompanion`). Below Android 13 the clips cross-fade under the fist.

## Denoise

`ColorSettings.denoise` (0..`DENOISE_MAX`, 10) is FFmpeg's `hqdn3d` with that
as its spatial luma strength; the other three strengths follow from it as
FFmpeg works them out. A clip has its own, first in its chain, on untouched
frames; the whole video has one too, once the clips are joined (see
`VideoFilters.kt`). `denoiseFilter` in `VideoFilters.kt` writes the filter for
both, and for the comparison below, so all three run the very same filter.

The live preview does not show it: `hqdn3d` also works over time, each frame
blended with the ones before it, so no frame alone shows what it does. The
Details tool (`app/.../DenoisePanels.kt`, a clip's or the whole video's,
named as the photo editor's, where sharpen, blur, vignette and grain will
join it) shows it where it matters, close up: a square of the frame under
the playhead, at the footage's own pixels, before and after, side by side.

`core/preview/DenoiseSample.kt` builds the command, run by
`media/.../FfmpegDenoiseSampler.kt` (the `DenoiseSampler` port) into the
cache's `denoise-samples/`, and read and deleted by the panel:

- one decode and one `split`: the two halves come from the same frame and
  leave as one PNG (`hstack`), so they cannot be two different frames (two
  outputs of one run, each with `-frames:v 1`, were tried first: one of them
  did not match the export);
- over time: `hqdn3d` starts again at each clip's start and forgets older
  frames quickly, so the run seeks to `WARM_UP_MS` (500 ms) before the frame,
  or to the clip's start if nearer, and `trim` drops those frames only after
  the denoise has seen them;
- across the frame: its smoothing runs along rows and columns and fades within
  a few pixels, so the square (`SIDE_PX`, 512) is cut out with `MARGIN_PX`
  (32) more on every side, denoised, and the margin cut away; at the frame's
  own edge the edge is the frame's, as in the export;
- HDR footage that the export makes SDR is tone mapped first
  (`toneMapFilters`), as there; footage kept HDR is denoised as it is and tone
  mapped only to be shown, each half before `hstack` (which drops the colour
  tags `zscale` reads).

Checked with FFmpeg 6.1 on a noisy test video: the "after" half of a clip's
denoise matched, pixel for pixel, the same square of a run over the whole
video. The whole video's denoise is compared on the clip's own pixels, while
the export runs it on the joined (and sized) video, so it is close rather
than exact; the panel says so.

The panel, in the PICTURE family's tone: a connected button group of levels
(off, light 2, medium 4, strong 7) and a slider for the values in between
(tenths, as the command writes them), a warning from 6 on (detail goes too).
The comparison sits in a nine-sided cookie that swells into a soft burst while
FFmpeg works and settles back on a spring with the new comparison, which a
divider splits: dragged or tapped, read out by TalkBack as a value, it sweeps
from the right to the middle the first time. Under it, the whole frame, a
frame on the part compared that springs to wherever it is tapped, or is
dragged after a long press, so that a plain swipe still scrolls the panel
(`DenoiseSample.shownArea` and `focusFor`, the same math as the command). It
asks FFmpeg again 350 ms after the strength, the part compared or the frame
stop changing, and only while the playhead is still. Undo keeps the denoise
apart from the colours (its own step key, `videoDenoise`).

Since the preview does not show it, two signs say where it runs:

- on the picture, among the preview's status badges (`StatusBadges` in
  `EditorPreview.kt`), `DenoiseBadge`: a 24 dp cookie of the same dark glass
  with the grain in it, while the clip under the playhead (or the whole
  video) has a denoise and filters run (not with stream copy or frames
  taken out). It springs in from a circle, says "Denoise 4.0, in the
  export" for a moment (both strengths, "2.0 + 3.0", when the clip and the
  whole video each have one), then folds back to the cookie; it fades while
  the video plays. A tap opens the Details tool for that clip, or for the
  whole video when the denoise is its. What it shows is a `derivedStateOf`
  of the clock: it changes with the clip, not on every frame;
- on the timeline, the clip's corner carries the same cookie, small (16 dp,
  in the picture's tone), in place of the dot of clips with effects; the dot
  moves beside it when the clip has other effects too. A clip narrower than
  40 dp keeps the dot alone.

## The TV break

`TransitionKind.TECHNICAL_DIFFICULTIES` is an old TV losing its signal: the
channel changes (a click, the picture tears, snow and "CH 03"), a test card
("TECHNICAL DIFFICULTIES / PLEASE STAND BY") plays a little lounge tune, then
the channel changes again (snow, "CH 04", the screen coming on from a bright
line onto the next clip). It lasts as long as asked, within bounds of its own
(`minMs` 1.6 s, `maxMs` 15 s, `defaultMs` 3 s; `Transition.effectiveMs` takes
the kind), and the transitions panel's slider follows them.

**Its parts.** `scripts/tv_break.py` draws and composes everything: the card
(a test-card field, a plate with the title in its own stroke-font capitals, a
little TV set, an old screen's hum bar, flicker, grain, scan lines and curved
glass), the channel changes (with opacity, packed as the fists are) and the
sound. The tune is composed there (eight bars at 120 beats a minute in F:
vibraphone melody, electric piano, walking upright bass, brushes) and played
by small synthesizers; every note ringing past the loop's end, and the room's
echo, is wrapped round to its beginning, so it loops seamlessly. The library
has three companion effects with their own sound (`"audio": true`):
`tv_intro`, `tv_card` (16 s, the card's loop and the tune) and `tv_outro`.

**On the video.** The recipe is a plain cut halfway, always under the card.
`transitionEffects` lays the three parts over the stretch the clips overlap
(`tvBreak`): the card from `TV_BREAK_CARD_IN_MS` in to as far before the end
(its tune fading in and out under the snow), the channel changes over the two
ends (`TV_BREAK_PART_MS`, shorter on a sped-up video). Each is a video laid
over the whole video with `hasAudio`, so its sound is mixed in the export
(`overlaySounds`) and played in the preview (`previewSounds`, which now
takes the cut effects too). The clips' own sound gives way
(`TransitionKind.mutesClips`): in the export the joined sound fades out over
`CLIP_SOUND_FADE_MS` at the break's start, the next clip's fades in over its
end, and `acrossfade` adds them with `nofade` curves; the preview's player
follows `clipSoundShares`. In the preview the opaque card plays through its
own player with the normal blend (`EffectSurface` screens only light on
black), the channel changes from their frames (`AlphaEffectFrames`).

## Picture looks

Black and white, old film, VHS, glitch, TV static, prism, shake, pulse,
rainbow, strobe, pixelate, invert, mirror and glow (`PictureLook`,
`core/model/PictureLook.kt`) are effects of the library too, offered under
their own heading in the gallery: an `ImageOverlay` whose `effect` is the
look's name, on the effects lane, with its strength (opacity), fades and hue,
and two settings of its own: its intensity (`lookAmount`, 25 to 200 percent:
how far a glitch tears, how hard a strobe flashes, how big the pixels...) and
its speed (`lookSpeed`, 0.25x to 4x, for the looks that move: the time it
reads runs that much faster, so its loop still closes on a whole turn).
They have no file (`EffectLibrary.looks`; `ensure` has nothing to copy, the
`uri` is only a name the library owns): instead of being laid over the video
they change the picture itself, with its captions and pictures, while they
last.

**One description, two drawings.** `core/look/Looks.kt` tells each look as
FFmpeg filters (`lookSpanGraph`) and as an AGSL shader (`lookShader`), from
the same numbers. Whatever moves is a function of the time since the look
started, modulo its period (`PictureLook.periodSeconds`), so a look loops
seamlessly for as long as it lasts; noise is new on every frame. Random
choices (a glitch's bands and bursts, an old film's scratches) come from one
hash, `fract(sin(0.37 x + 0.3) * 43.758)`, whose numbers stay small enough
for a phone's single-precision floats to agree with FFmpeg's doubles. A time
that falls right on a step of the loop gets 0.1 ms added, so that every turn
of the loop counts it in the same step.

**Export.** `ConversionRequest.looks` (from `lookSpans` in `RequestMapping`)
are drawn after the captions and pictures and before the whole video's frame
rate and fades (`videoGraph`). Every filter runs on the whole video and is
switched on only while the look lasts (timeline `enable`): nothing is cut out
and put back, so no frame waits in a queue for the moment a look starts. The
moving parts use expressions of `t` (`eq`, `hue`, `overlay`, `crop`); shake
and pulse use `perspective`, which only counts frames, on a branch made
steady with `fps` and laid back with `overlay`. Bands and scratches are small
`color` sources laid with `shortest=1` (they never end). A look below full
strength, fading or turned is drawn on a copy, given an alpha (`lut`, `fade`)
and laid back over the picture.

**Preview.** `pictureLooks` (`app/.../PictureLooks.kt`) puts the looks on screen
now on the box that holds the picture and what lies on it, as one
`RenderEffect`: each look's shader from Android 13 (uniforms `uSize`, `uTime`,
`uStrength`, `uHue`, `uAmount`; `uTime` already times the speed), chained in
order, each shader kept by its overlay and its look (a look swapped for another
gets the other's shader); on Android 12 the looks that only
change colours (black and white, old film's sepia, invert, rainbow, strobe)
as colour matrices; before, nothing. The gallery's tiles and the look panel
draw a look on a little sunset of their own (`LookFrames`). Tests:
`LookGraphTest` (labels, windows, loops, the hash in single precision),
`LookExportTest`.

## Transitions told as layers

The first 25 `TransitionKind`s are FFmpeg's `xfade` transitions, drawn in the
preview by `transitionLook` (`core/preview/TransitionLook.kt`). The others are
told as layers: a `TransitionRecipe` (`core/transition/`) says what shows
behind, how each clip's picture is moved, bent, blurred, faded and tinted at
each point and moment, and what light is laid over them, as small expressions
(`Ex`, `core/transition/Ex.kt`) of the point (X, Y), the progress (T) and the
frame's size. One recipe gives everything:

- **The preview** (`TransitionLayers` in `app/.../TransitionPanels.kt`,
  `app/.../preview/TransitionShaders.kt`): each clip's layer runs an AGSL
  shader written from its part (`partShader`), the light one more
  (`overlayShader`), the progress a uniform, so a running transition never
  recomposes. Android 13 and later; before, or where a device refuses a
  shader, the layer is cross-faded (`transitionLook` gives a fade for these
  kinds) and the panel says so.
- **The export** (`nativeTransitionJoin`, `core/transition/TransitionGraph.kt`):
  the kinds whose `xfade` name is a transition of FFmpeg's own (dissolve,
  pixelize, the corner and diagonal wipes, cover and reveal up and down,
  squeezes, closing doors and shutters, soft wipes up and down) are exported by
  `xfade`, their recipe drawing the same for the preview. The rest
  (`TransitionKind.isCustom`) cannot be one `xfade` custom expression: worked
  out pixel by pixel at full size it takes minutes for a second of video. So
  the joined video is cut where the transition starts and the clip coming in
  where it ends (`split`, `trim` by frame), the two pieces that overlap make a
  region, and the three parts are joined again by `concat` (then `settb`, the
  clips' clock, which `xfade` after it needs). In the region each clip is
  drawn with FFmpeg's own filters, frame by frame:
  `perspective` where the recipe gives where the picture's corners go
  (`Part.corners`: moved, enlarged, turned, a face of a cube), with a
  transparent edge when it does not cover the frame; `remap` through maps of
  where each pixel comes from, worked out by `geq` at 192 pixels wide and
  enlarged (swirl, wave, spin with mirrored edges...); opacity and light as
  masks worked out by `geq` at 256 pixels wide (a tiny one when they do not
  change across the frame), enlarged and merged (`alphamerge`); `eq` for
  saturation and dips toward black or white; `gblur`, `dblur` and
  `chromashift` with their strength sent for each frame (`sendcmd`, the values
  worked out here); `mix` of copies for the zoom and spin blurs
  (`NativeBlur.Echo`); `noise` for TV static; `overlay` lays the layers. The
  region's frames run from T = 0 to T = 1, so it starts and ends on one clip.
  HDR videos (10 bits, which `perspective` does not take) get a cross-fade
  instead.

`TransitionRecipeTest` checks that every recipe starts on one clip and ends
on the other, that its corners agree with where it reads its picture and that
its shaders are whole; `TransitionGraphTest` that every graph makes and uses
each of its labels once.

The transitions panel groups the kinds in twelve families (`TransitionFamily`,
as CapCut does), two rows of tiles that scroll sideways, each tile playing its
transition through the same layers; the duration slider reaches as far as the
two clips allow (half of the shorter), and a button plays the transition in
the preview. On the timeline the edges of two clips joined by a transition
lean alike across the join (`transitionSlants` in `EditorTimeline.kt`), as
wide as the footage the transition takes from each, the join's button on the
middle of the slant.

## Shape layers

A shape (rectangle, ellipse, triangle, diamond, pentagon, hexagon, star, heart,
arrow, and Material's sun, cookie, clover, flower and burst) is a sticker whose
picture the app draws: `ImageOverlay.shape` (`ShapeLayer`, `ShapeLayers.kt`)
says what it is, how it is filled (a colour, a gradient at an angle, or none),
its outline, its proportions and how round its corners are, and its `uri` is a
transparent PNG `ShapeRenderer` draws with the stills (`CacheJanitor.newStill`,
1600 px on its longer side). So the export, the preview and every tool of a
picture (place, size, turn, opacity, blend, the outline and shadow of its look,
keyframes, fades, timing, layers) work on it as they are; it has no crop,
colours, key or Replace, since it is drawn as asked.

The view model draws the picture again whenever the shape changes
(`updateShape`, `syncShapes`): one drawing at a time per layer, the newest shape
asked for when it is done, so a slider dragged on every frame draws only a few.
The picture follows the shape as a change of its own (`mutate(record = false)`),
so undo always finds a shape and the picture drawn for it; a step undone or
redone before its picture was drawn has it drawn then. Projects keep the PNGs
like the stills, and their shapes trust them when opened. The panel
(`ShapeLayerPanel`) draws the shape live with the very path the PNG is made
from (`ShapePaths`), so it changes under the finger while the picture follows.

Both editors add a shape from the tool row ("Shape") and list it among the
layers; in the video editor it is laid at the playhead, like a picture.

## Starting from nothing

"Create from scratch" on the home screen, under the recent projects
(`BlankPanels.kt`): a new blank, then the user's own starting points. A blank
(`BlankSpec`, `core/model/Blank.kt`) is a size (a shape among square, post,
story, wide, photo, 4:3, A4, banner, this screen, or any size typed; its
longer side 1080, 2160 or 3840 px, sides even, at most 3840 as a still) and a
fill (`BlankFill`: transparent, a colour at any opacity, a linear gradient at
an angle across the picture's extent along it, or a radial one from the middle
to the corners, each colour with its own opacity). `AndroidBlankMaker` draws
it into a PNG still, row by row as `BlankFill.row` works it out (the sheet's
preview draws the same with Compose brushes), and it opens as a picture named
after the blank; its file type is picked afterwards, as for any file. The
presets are neither settings nor projects: `BlankPresetStore` keeps them in a
JSON file of their own (`BlankPresetsJson`), newest first, one per name; a tap
makes one at once, a long press edits or forgets it. Beside the new blank, ready ones
(transparent, a few colours, a gradient; a square of 1080 px) open at once,
or held, in the sheet to change them first.

Every row of colour swatches (`ColorSwatchRow`) ends with a colour wheel that
takes any colour by its hex code (`HexColorDialog`, `parseHexColor`); a colour
chosen that way shows chosen at the end of the row.

## Compressor

A tool of its own from home (`CompressScreen`): a file picked there is opened
as usual (`setInputUri`) and exported at once with `ConversionSettings.compression`
set for that export only (never saved). `Compression` (`core/model`) is a level
(same quality, balanced, smallest) or a size to fit:

- a video becomes an MP4 with HEVC by x265 (`-tag:v hvc1`, `+faststart`), never
  the phone's encoder: at CRF 24 / 28 / 32, or, to fit a size, in two passes
  (`buildFirstPass`: the same command with `-x265-params pass=1:stats=...` into
  `-f null`, then `pass=2` at the bitrate that fills the size, 3 % kept for the
  container, the AAC sound at most a fifth of it, 64 to 128 kbit/s); a lower
  resolution is the usual `Resolution`;
- a JPG at the level's quality, or fitted by the engine (`fitted`): made again
  at half the quality range [FIT_STEPS] times, the best that fits kept;
- a PNG with `-compression_level 9 -pred mixed`, or brought to 256 colours
  (`palettegen` / `paletteuse` dithered, transparency kept, `pal8`, unfiltered
  rows); a WEBP at the level's quality; a longer side as `ImageOutput.maxSide`;
- a sound in its own lossy format (a lossless one becomes M4A) at a lower
  bitrate (`Compression.audioKbps`), or the one that fills the size;
- with "without place and device", the tags go through `withoutSensitive`.

All of it was checked against FFmpeg 6.1 (x265's two passes, the palette).

## Background playback

The preview keeps playing when the app goes to the background or the screen
turns off, with the system's media notification (title, picture, play / pause,
seek bar), the lock screen controls and headphone buttons:

- the preview is not one player (see "Live preview"), so a stand-in faces the
  system: `playback/PreviewSessionPlayer.kt`, a Media3 `SimpleBasePlayer` with
  one item as long as the exported file. Its position is read from the
  preview's output clock whenever it is asked for; a jump (a seek in the
  editor) and a new length are reported as they happen. Play, pause and seeks
  from the notification go back to the editor's play state and to
  `EditorPreviewPlayer.seekToOutput`;
- `PreviewPlaybackService` (a Media3 `MediaSessionService`, foreground type
  `mediaPlayback`) holds the session. `PreviewSessions` starts it when an editor
  opens (the editor is on screen then, as the system requires) and stops it
  when the editor closes or the app is swiped away; Media3 brings it to the
  foreground while the preview plays, which keeps the process alive;
- the service is exported (the system binds to it), so its session only lets
  trusted controllers in (`TrustedControllersOnly`, `ControllerInfo.isTrusted`):
  the app and its notification, the system (System UI, lock screen, headphone
  and Bluetooth buttons), apps holding `MEDIA_CONTENT_CONTROL` and apps the user
  made notification listeners. Any other app is refused and cannot read the
  title, the artist or the frame the session shows;
- the notification shows the tags (title and artist, else the file name) and a
  picture: the cover of an audio file, the first frame of a video's first
  clip, else the app's icon (`PreviewMediaSession.kt`);
- while it can be heard the preview holds the audio focus like any media
  player: another app playing, a call or unplugged headphones pause it
  (`EditorPreviewPlayer.onInterrupted`); a silent preview (a GIF, a muted
  video) does not take the focus, and a GIF has no session at all;
- the CPU is kept awake while it plays (`WAKE_LOCK`, ExoPlayer's local wake
  mode). Media notifications need no notification permission on Android 13+.

## Export notification (Live Updates)

While an export runs, `ExportService` holds the foreground (type media
processing from Android 15, data sync before; it only keeps the app alive, the
export runs in the view model) with the notification of `AndroidExportNotifier`:
the file, the stage, the percentage, the time left once it can be told, a
cancel button, and the app opened when touched. On Android 16 it is a Live
Update, following developer.android.com ("Live Updates" and "Progress-centric
notifications"): `Notification.ProgressStyle` (indeterminate while preparing),
`android.requestPromotedOngoing` in the extras with the
`POST_PROMOTED_NOTIFICATIONS` permission, the percentage in the status bar chip
(`setShortCriticalText`), ongoing, with a title, no custom view, not a group
summary, not colorized, on a low-importance channel (not MIN). The system then
shows it promoted (top of the shade, lock screen, status bar chip) unless the
user turned Live Updates off for the app; it is then a normal notification with
the same bar. Before Android 16 it is an ongoing notification with a progress
bar. Updates come at most every 400 ms or on a new percentage (the system drops
faster ones). Once over, a short notification says it is ready or failed, only
when the app is not on screen, and goes when the app comes back. Android 13 and
later ask for the notification permission at the first export.

## Where things live

| I want to change...                         | Look at                                              |
|---------------------------------------------|------------------------------------------------------|
| a picture filter or the filter order        | `core/command/VideoFilters.kt`                       |
| an audio filter                             | `core/command/AudioFilters.kt`                       |
| an effect of an audio file (and its preview) | `core/audio/` (`AudioMastering.kt`, `AudioStage.kt`, `Dsp.kt`) |
| an edit of a picture (and its preview)      | `core/image/` (`ImageEditing.kt`, `ImageStages.kt`)  |
| the picture editor                          | `app/.../ImageEditorScreen.kt`, `app/.../ImagePanels.kt` |
| the layers of a picture (texts, stickers)   | `app/.../LayerPanels.kt`, `core/model/Stacking.kt`   |
| blend modes (export and preview)            | `core/model/LayerBlend.kt`, `layOver` in `CommandBuilder.kt`, `app/.../BlendPanels.kt` |
| shape layers                                | `app/.../ShapeLayers.kt`, `app/.../ShapePanels.kt`, `updateShape` in the view model |
| transitions between clips                   | `core/model/Transition.kt`, `core/transition/` (recipes, shaders, export graph), `transitionJoin` in `CommandBuilder.kt`, `app/.../TransitionPanels.kt` |
| the video effects and effect transitions    | `scripts/generate-effects.py`, `scripts/punch3d.py` (the punches' 3D fist), `scripts/math_effect.py` (the math formulas), `app/.../effects/`, `app/.../EffectPanels.kt`, `app/.../EffectOverlays.kt` |
| the TV break (technical difficulties) | `scripts/tv_break.py`, `app/.../effects/TransitionEffects.kt` (`tvBreak`), `core/model/Transition.kt` |
| the picture looks (black and white, glitch...) | `core/model/PictureLook.kt`, `core/look/Looks.kt`, `app/.../PictureLooks.kt` |
| the SVG editor                              | `core/vector/svg/`, `app/.../svgedit/`               |
| ASCII and ANSI art (engine, files, editor)  | `core/textart/`, `media/.../AndroidGlyphAtlas.kt`, `app/.../textart/` |
| subtitle translation (models, text, UI)     | `core/translate/`, `app/.../translate/`              |
| dubbing in the subtitles' language          | `core/dub/`, `app/.../dub/`                          |
| the marks of what AI made (AI Act Art. 50)  | `core/provenance/`, `core/marking/`, `seal` in `ConversionEngine.kt`, `app/.../provenance/`, `app/.../marking/`, `scripts/export-ai-markers.py` |
| the voice set apart from the rest           | `core/voice/`, `app/.../voice/`, `app/.../VoicePanels.kt`, `app/.../VoiceScreen.kt` |
| SVG gradients (editing, tracing)            | `core/vector/svg/SvgGradients.kt`, `app/.../svgedit/SvgGradientEditor.kt`, `core/vector/VectorTracer.kt` |
| SVG booleans, aligning, grouping            | `app/.../svgedit/SvgBooleans.kt`, `core/vector/svg/SvgArrange.kt`, `app/.../svgedit/SvgActions.kt` |
| the command studio (suggestions, checker)   | `core/command/studio/`, `app/.../command/`           |
| codecs, quality mapping, containers         | `core/command/EncoderArguments.kt`                   |
| inputs, stream mapping, the filter graph    | `core/command/CommandBuilder.kt`                     |
| output file names and directories           | `core/output/OutputPlanner.kt`                       |
| packing parts or frames into a zip          | `core/output/PartsArchive.kt`                        |
| the conversion steps or error handling      | `core/engine/ConversionEngine.kt`                    |
| how a new setting reaches the backend       | `app/.../conversion/RequestMapping.kt`               |
| the FFmpeg runtime itself                   | `media/.../FfmpegKitExecutor.kt` + `libs.versions.toml` |
| user-facing error/status wording            | `strings.xml` via `app/.../conversion/ConversionMessages.kt` |
| what the editor shows before export         | `app/.../EditorPreview.kt`, `app/.../preview/`, `core/preview/` |
| clip operations (split, move, trim...)      | `core/timeline/Timeline.kt`                          |
| the cover of an audio file                  | `core/command/CoverArt.kt`, `media/.../AndroidCoverArtist.kt`, `AudioCoverContent` in `app/.../AudioPanels.kt` |
| background playback, media notification     | `app/.../playback/`                                  |
| the denoise and its comparison              | `denoiseFilter` in `core/command/VideoFilters.kt`, `core/preview/DenoiseSample.kt`, `media/.../FfmpegDenoiseSampler.kt`, `app/.../DenoisePanels.kt` |
| the colour key (green screen)               | `core/command/KeyFilters.kt`, `core/preview/ColorKeying.kt`, `app/.../preview/KeyEffect.kt`, `app/.../KeyPanels.kt` |
| the export notification (Live Update)       | `app/.../export/`                                    |
| sound placement, trim, cut, rows            | `core/timeline/SoundSpan.kt`, `core/preview/OutputClock.kt` |
| the editor layout and tool row              | `app/.../ConfigScreen.kt`, `app/.../EditorToolbar.kt` |
| a tool panel or the export sheet            | `app/.../EditorPanels.kt`, `app/.../ExportSheet.kt`, `app/.../ui/expressive/Panels.kt` |
| how formats look (shape, colours, badge)    | `app/.../FormatVisuals.kt`                           |
| the timeline of the editor                  | `app/.../EditorTimeline.kt`, `app/.../TrimPanels.kt` |
| undo / redo                                 | `mutate` in `app/.../conversion/ConversionViewModel.kt` |
| what a project file holds, recent projects  | `app/.../projects/` (`ProjectJson.kt`, `ProjectStore.kt`), `core/project/` |
| how clips become FFmpeg inputs              | `footage()` in `core/command/CommandBuilder.kt`      |
| spoken subtitles: words, lines, the script  | `core/subtitles/` (`SpokenWords.kt`, `SubtitleLayout.kt`, `AssSubtitles.kt`, `FontFacts.kt`) |
| the speech model, its import and test       | `app/.../speech/`, `app/.../settings/SmartSettings.kt`, `core/archive/` |
| the subtitles panel and their preview       | `app/.../SubtitlePanels.kt`, `app/.../StyledSubtitles.kt` |

Adding a setting end to end: field in `ConversionSettings` -> setter in
`ConversionViewModel` -> field in `ConversionRequest` -> one line in
`RequestMapping.kt` -> filter/argument in `:core` -> a test in
`core/src/test` -> one line each way in `ProjectJson` (or `ModelJson` for a
:core model), so recent projects keep it.

## Tests

`./gradlew :core:test` runs on a plain JVM (no emulator). It covers command
generation for every mode, command-line quoting, the manual command resolver, the command studio (help parsing, suggestions, checker, recipes),
output naming, duration math, progress parsing, the timeline operations, the
preview math (with the denoise comparison's command, `DenoiseSampleTest`), the text-art engine and its files (`TextArtTest`), subtitle re-timing, the project files (`ProjectJsonTest`: every
field of every model, filled with random values, must come back from the
JSON text) and the whole engine workflow with fake ports.

## Storage

Nothing is kept in the cache longer than needed:

- sources are copied into `cache/staging/<session>` for each conversion and the
  directory is deleted when the run ends, whatever the outcome
  (`CacheSourceStager` + `ConversionEngine`);
- the denoise panel's comparisons go to `denoise-samples/` in the cache and are
  deleted as soon as they are read (or when FFmpeg fails or is cancelled);
- camera recordings go to `recordings/`, and stills (pictures, freeze frames)
  and covers of audio files to `stills/` in the cache (`CacheJanitor`); they are deleted as soon as their
  project is closed: another file is opened, the recording is cancelled, or the
  app is left for good (`ConversionViewModel`). Files only the undo history still
  refers to go with the project too;
- at start-up `FfmpegGuiApp` sweeps what a killed process or an older version
  left behind (orphaned working copies, old recordings, denoise comparisons, the
  time-stamped copies earlier versions wrote straight into the cache).

Exported files are results, not cache: they stay in the app's files directory
(`AppOutputDirectory`) until the user removes them. Recent projects are not
cache either: they live in `files/projects/` (see "Recent projects").

Files opened with the app or shared to it (`openedFile` in `MainActivity`) must
be `content://` URIs of another app (`isForeignContentUri`): any app can start
`MainActivity`, and a `file://` URI or one of the app's own `FileProvider`
would make it open its own private files (cache, exports, projects) on the
sender's behalf. Such an intent opens nothing. Files picked inside the app go
through the system picker and are not affected.

## Offline guarantee

The app requests no network permission:

- nothing network-related is declared in `app/src/main/AndroidManifest.xml`;
- Media3 declares `ACCESS_NETWORK_STATE` in its library manifest, which would be
  merged into the app; the app manifest strips it with `tools:node="remove"`;
- `verify<Variant>NoNetworkPermissions` (defined in `app/build.gradle.kts`) reads
  the merged manifest on every `assemble`/`bundle` and fails the build if any
  network, Wi-Fi, Bluetooth or NFC permission is present;
- cloud backup is disabled (`android:allowBackup="false"`).

The permissions of background playback and of the export notification
(`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`FOREGROUND_SERVICE_MEDIA_PROCESSING`, `FOREGROUND_SERVICE_DATA_SYNC`,
`WAKE_LOCK`, `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`) have nothing
to do with the network, and `media3-session` declares none of its own; the check above guards
it like any other dependency.

Without `INTERNET` the process cannot open sockets, so FFmpeg's network
protocols are inert as well.

The smart subtitles keep to this: the speech model is downloaded by the user's
browser (the settings only open the link) and imported as a file;
sherpa-onnx's AAR declares no permission (its manifest is empty), and the check
above guards it like any other dependency.

## Hardware video encoding

The bundled FFmpeg (n6.1, built with `--enable-mediacodec --enable-jni`) has
the MediaCodec encoders. FFmpeg 6.1's `h264_mediacodec` copies each frame into
the encoder's input buffer packed its own way; a chip that reads frames
another way makes a video torn into coloured streaks, with no error anywhere.
An emulation of FFmpeg's packing in Kotlin did not predict it on a real phone,
so the chip is calibrated for real instead (`media/.../HardwareCalibration.kt`),
from a test button in the settings: for each hardware H.264 encoder (at most
three), each input FFmpeg can give it (NV12, I420) and each size step (16, 32,
64, 128, 256), FFmpeg encodes a one-second `testsrc2` video with the chip, as an
export does, then decodes it and compares it with the original (`psnr`). Each
step is tried on a portrait and a landscape frame near a phone video's whose
sides are ODD multiples of the step (16: 1072 x 1904, 32: 1056 x 1888, 64:
1088 x 1856, 128: 1152 x 1920, 256: 1280 x 1792): a side that is also a
multiple of a larger step would pass for a chip that needs that larger step
(the first version tested 1088 x 1920 only, 17 x 64 by 15 x 128, passed a chip
at step 16, and exports at other widths came out torn). Every plane must reach
`MIN_PSNR_DB` (28 dB): checked here with FFmpeg, a correct encode is 38 to 46
dB on each plane, rows read with the wrong stride about 10, colours read from
the wrong rows about 20 on the colour planes only (an average would let that
through). The first way that passes on both sizes becomes `HardwareEncoder`
(codec name, pixel format, size step). The switch that turns the chip on can
only be turned on once a test has passed; a test that passes turns it on, one
that fails turns it off. The result is kept in the app's preferences, keyed by
the system build, so a system update asks for a new test. A test cannot start
while an export runs.

With a calibrated encoder and the setting on, `CommandBuilder` encodes the picture
of an MP4 or MKV with `h264_mediacodec -codec_name <the encoder checked>`, its
pixel format, `-bitrate_mode vbr` and `-g 60` (the default gives a key frame
every second), with the picture's sides scaled to the nearest multiples of the
encoder's alignment (the pixel aspect ratio keeps the shape). A phone encoder
has no CRF, so the quality slider becomes a bitrate (`hardwareVideoKbps`: 0.02
to 0.25 bits per pixel along a curve, for the first clip's frame at the chosen
height and frame rate; 0.7 is about 8 Mbit/s at 1080p30). Decoding and filters
stay on the processor, as before. When the processor encodes (the chip off,
not calibrated, or after it failed), x264 runs at the preset chosen in the settings
(`CpuEncoderSpeed`: ultrafast, veryfast, the default on the phone, medium,
slow). Not used for AVI, GIF, pictures, sound, remuxes, frames, or parts
(cutting into parts needs key frames exactly at the cuts, which only x264
places). If FFmpeg fails with the phone's encoder, `ConversionEngine` deletes
the partial file and runs the same request again with x264; it says so with
`ConversionEvent.Encoder`, the processing screen shows who encodes, and the
done screen says which one made the file (`VideoEncoderUse`).

## Models in memory

Every model of the app (speech, translation, voice, dubbing, cut-out,
EdgeTAM, the magic eraser, and the check of an imported translation model)
goes through one gate (`speech/ModelGate.kt`), so that their memory never
adds up: each takes from a hundred megabytes to over a gigabyte, and two
together would leave the phone short.

- **One model in memory.** Each engine has a `ModelSlot`; before its model
  runs, whichever other model is in memory is let go of (it loads again when
  next needed).
- **One run at a time.** A run waits for the one under way to end, whatever
  started it (an editor, a job in the background, a speed test), so two
  models never work side by side. A model is never let go of in the middle of
  a run: `unload` waits for it.
- **Let go of as soon as the work is done.** There is no timer: once a run
  ends (done, cancelled or failed) its model goes. A piece of work made of many
  runs holds it (`ModelSlot.hold`) until it ends: the correction dialog, from
  the first stroke to its closing (confirmed or not), and a lift across its two
  calls. A dub holds the voice only while the lines are said (the file is
  written after), the voice model only while the voice is set apart (not while
  FFmpeg takes the sound out or packs it), and the way through English loads
  its second model only after letting the first go.
- Settings shows a model in memory while it works, with Unload.

## Subtitle translation

Subtitles translated on the phone with Opus-MT (University of Helsinki, CC BY
4.0), as converted to ONNX for transformers.js by Xenova: one model per
language pair (or from many languages to English, or from English to many),
four files each (`vocab.json`, `source.spm`, the quantised encoder and
decoder; `config.json` is read when given, else the vocabulary's "</s>" and
"<pad>" say what it would, `MarianConfig.fromVocab`). The user's browser downloads them; any number of pairs
can be on the phone (`app/.../translate/`).

**Import** (`TranslationModelStore`). The files are picked at once and
known by their names (a browser's "vocab (1).json", or a .json saved as .txt); the import is put
together apart and takes its place only once the settings read as a Marian
model's, the vocabulary and the SentencePiece model parse and both ONNX files
open. Its languages come from its name on Hugging Face (given in the sheet, or
the settings' `_name_or_path`); a model that writes many languages lists them
by the ">>ita<<" tokens of its vocabulary. Unlike the other models, whose
files are pinned, any pair can be added, so each file's SHA-256 is taken at
import and checked again on request.

**Text** (`core/translate/`, pure Kotlin, tested). `SentencePiece.kt` reads
the .spm file (a protocol buffer, by hand) and cuts text as the unigram model
does (NFKC, spaces as "\u2581", Viterbi over the pieces' scores);
`MarianTokenizer` looks the pieces up in the vocabulary, puts the target's
token first for a model that writes many and the end of the sentence last,
and turns ids back into words. `GreedyDecoder` writes the likeliest id each
time, never the padding, until the end of the sentence (or a word stuck
four times).

**Running** (`TranslationEngine`). ONNX Runtime on the processor: each line is
encoded once, then the decoder (the one without a cache: subtitles are short)
is run for every id it writes, its last row of scores read. One model in
memory (the way through English lets the first go before the second loads),
let go of as soon as the lines are translated (see Models in memory).

**Which models** (`TranslationRoute`). A model that reads the subtitles'
language and writes the one asked; else two, through English. The language is
guessed (`LanguageGuess`): the alphabet first (Cyrillic, Arabic, CJK...), then
which language's commonest words the lines are made of; the user can change
it.

**What changes** (`ConversionViewModel.translateSubtitles`). Every line keeps
its time. A file (`SubtitleTranslation`) keeps everything but its words: a
SubRip file its numbers and times, a SubStation Alpha file its header, styles,
every field of its dialogue lines and the override tags that start a line;
long lines are broken near their middle; it becomes a new file in the app's
files (the project copies it), one step of undo. Spoken subtitles get, line by
line (as the subtitles show them), the translated words spread over the
line's time.

## Voice and the rest, and every part on its own track

The sound of a file split into stems, by two models this project converts
to ONNX and serves from its releases (`StemModels`, kept like the speech
model's files by `SpeechModelStore`; docs/MODELS.md has the record):
Bandit v2 (Watcharasupat, Wu, Orife; weights CC BY-SA 4.0) for the
dialogue, the music and the effects, and Spleeter's five stems (Deezer, MIT)
for the music's singing, piano, drums, bass and the rest. They replace
Spleeter's two stems, which `RetiredModels` deletes from the phone at the
first start (`RetiredModelsDialog` says so, with a way to the Voice page).

**Separation** (`core/stems/`, pure Kotlin, tested). `StemSeparation` is
one engine for both: a short-time Fourier transform of both channels at
once (left as the real part, right as the imaginary; a periodic Hann window
every quarter of it; `Fft` is the app's own radix-2), shown to the model a
window of frames at a time, a complex mask per stem, channel, frame and bin
back from it, each stem the sound's spectrum times its mask, back to sound by
overlap-add (the squared windows add up to 1.5). Each window reaches some
frames past both ends of what is kept of it, so that no mask is cut at its
edge and a recurrent network hears around; however long the file, memory
stays the same. One stem, the one of "everything else", is the sound less the
others: the stems always add up to the sound.

- `BanditMasks`: 48 kHz, 2048 points, hop 512, 1024 frames seen (about
  10.9 s) of which 48 at each end only heard around. The network is mono: it
  reads the middle of the two channels, scaled as torchaudio's "normalized"
  transform, laid out [1][bin][frame][re, im], and its masks are laid on each
  channel's own spectrum, so the stems stay stereo. Effects take what the
  dialogue and the music leave. `scripts/export-bandit.py` checks these
  transforms against the authors' model end to end. The first file
  (bandit-onnx-1, retired) gave its masks as doubles (the authors' frequency
  weights are float64, and carried the sums after them) and had some 23,000
  nodes; bandit-onnx-2 puts the masks together in one scatter-add per stem,
  in float32, and loads three times quicker. `VoiceSeparator` reads either.
  Its recurrent layers (GRU) take most of the time and do not run on
  XNNPACK, so its session stays on the plain kernels, threads spinning.
- `SpleeterMasks`: 44.1 kHz, 4096 points, hop 1024, 512 frames (32 heard
  around), the magnitudes of the first 1024 bins of both channels; each
  instrument keeps e^2 / sum(e^2) of every bin. Above the 1024 bins nothing
  is told apart: that top end goes with "other" (Spleeter drops it).

The sound is padded with N - hop zeros in front, so that every sample lies
under four frames.

**Files** (`VoiceSeparator`). FFmpeg takes the sound out (`s16le`, two
channels, at the model's rate, sample 0 at the file's time 0); Bandit's run
writes one stereo WAV per stem and the four-channel one the voice levels
and the dub read (the dialogue's left and right, then the music's and the
effects' together: `VoiceStem`), FFmpeg packs each into FLAC in
`files/voice-stems/`, by the media file's address: a file is split once, for
every project. The instruments are split from the music stem (read again at
44.1 kHz), only when asked. One model in memory at a time, through the
`ModelGate`. Four-channel files left by Spleeter's two stems keep playing;
asking for tracks makes them again with Bandit. The settings say how many
files are kept and remove them all.

**Tracks** (`core/stems/StemTracks.kt`, `ConversionViewModel.placeStemTracks`).
For each clip that plays, the part of its file it plays, from where it starts
on the output clock, at the speed it plays at: a stem file is on its media
file's clock, so the same part of it, laid there as an `AudioClip`, plays in
step with the picture. The clip goes silent; a track takes its clip's volume
and the voice's or the rest's level. Clips played backwards or on a speed
curve (or under a whole video so played) are left as they are and counted.
One step of undo.

**Export** (`CommandBuilder.footage`). A clip whose `Effects.voiceLevel` or
`backgroundLevel` is not 1 takes its sound from its file's stems
(`SourceInfo.voiceStem`, filled in by `withVoiceStems`), an input of its own
seeked like the clip (`-ss`, `-t`), made stereo again at the two levels
(`pan=stereo|c0=v*c0+b*c2|c1=v*c1+b*c3`) before the clip's own sound chain.
Without stems the clip keeps its file's sound.

**Preview** (`previewStemSounds`). Such a clip's own sound is off and its
part of the stems plays on a player of its own, where the clip is on the
output clock (as the placed sounds do, `SoundVoices`); `StemMixProcessor`
mixes the four channels down at the two levels for every buffer, so a slider
is heard at once.

**Where.** The editor's Stems tool (video and audio editor), per clip or
for every clip: on top the card of the split, with a strip of the parts it
gave (dialogue, music, effects, instruments) lit as they are there; then
"Quick mix" (the dialogue's level and the music's and effects' together,
live, with presets) or "Tracks" (`StemTracksCard`: how far to split, which
parts, one button).
On the timeline a placed stem carries its kind's icon before its name: known
by its file (`VoiceSeparator.stemKindOf`, its name in `voice-stems`) or by
its own name ("Dialogue (film)", `stemKindNamed`). A sound's name is in its
fill's "on" colour with a soft halo of the fill, so it reads over the
waveform without covering it.

## Dubbing

The subtitles said by a voice in their language, over the video: Kokoro-82M
v1.0 (hexgrad, Apache 2.0) in sherpa-onnx's multi-language float32 build (on
a phone's processor more than twice as fast as the int8 one, whose quantised
convolutions ONNX Runtime runs slowly, and less metallic), run by
sherpa-onnx's own text-to-speech (already in the app for speech recognition).

**Model** (`DubModel`, `DubModelStore`). One archive from sherpa-onnx's
releases; unlike the other models it is unpacked whole, folders kept
(eSpeak NG's data is a few hundred files), into `files/dub/`. The archive is
hashed as it is read and the main files (the network, the voices, the token
list, the lexicons, eSpeak NG's phoneme table) are pinned by their SHA-256.
Its state reads like the other models' (`SpeechModelStore.State`), so the
settings show it with the same card.

**Languages and voices** (`core/dub/DubVoices.kt`). The model's 54 voices in
its own order (their speaker ids); a name says the language and whether the
voice is a woman's or a man's. Each language is said through its eSpeak NG
voice, English through its lexicons first, Chinese through its lexicons,
jieba's dictionaries and the rules for numbers and dates. Japanese is left
out (its kanji are read as Chinese).

**Lines** (`ConversionViewModel.dubLines`). The subtitles as they are shown
on the edit: a file's cues moved onto the timeline (`onTimeline`), or the
spoken words laid out in lines (`SubtitleLayout`), then on the output clock
(divided by the whole video's speed). `DubTiming.spoken` keeps what can be
said (no tags, no "[music]").

**Timing** (`DubTiming`, pure Kotlin, tested). A line may take the time until
the next one starts (and never past the end of the video); a voice that
needs more is said again faster (the voice's own speed, up to 1.5 times),
and what still does not fit is made to fit (`DubFit`): its long pauses
shortened to 140 ms, then the whole line faster without a higher voice
(`TimeStretch`, WSOLA: 30 ms Hann frames, each moved up to 10 ms to where its
waveform best goes on; up to 1.6 times), and only past that its end cut,
faded. Nothing pushes the next lines later: every line starts with its
subtitle, so the dub cannot drift from them nor outlast the video. How
fast the voice speaks is learnt line after line (`DubPace`, characters a
second), so that a long line is said fast enough the first time: a line is
said a second time only when that was still not enough. Every line has its
silence cut at both ends and its ends faded (`DubAudio`): it starts when its
subtitle does, without a click.

In the preview a placed sound a little off its place (a long one, such as a
dub, drifts) is brought back by playing it 4 % faster or slower until it is
(`SoundVoices.nudge`), rather than by a seek, which is heard.

**Result** (`DubEngine`). Every line, at its place, in one mono WAV at 24 kHz
in `files/dubs/`, laid on the video as a placed sound (`AudioClip`): it is
moved, trimmed and levelled like any other, and a project keeps its own copy.
A new dub takes the place of the one made before. With "lower" or "take
out", every clip's `voiceLevel` is set (see "Voice and the rest"): the files
without stems are separated first, in the same run.

**In one go** (`ConversionViewModel.autoDub`, `AutoDubSectionContent`, the
editor's "Dub" tool). The language, the voice and what becomes of the
video's own voice (taken out or lowered) are picked; one button runs the
editor's own steps one after the other, each awaited: listening
(`generateSubtitles`, skipped when the video has subtitles), translating
(`translateSubtitles`, from the language guessed in what was heard; skipped
when it is the dub's already), then `dubSubtitles` (which sets the voice
apart first where it is not yet, then dubs). Each step shows in its own
place as it goes and keeps its own undo; the tool shows the four as a group
of rows that tick off, with the running one's progress read from that
step's own status. What can be known to be missing (the speech model, the
voice model, a translation model once the language is known, the voice) is
said before anything starts; a translation model for a language heard only
then stops the run after the listening, which stays.

**The two screens** (`DubCard` in the subtitles panel, `AutoDubSectionContent`;
their pieces in `DubPickers.kt`). Both are one group of joined blocks: the
language as a single sideways strip of tokens (its code in a shape that
blooms when chosen; the subtitles' own, found by itself, starred), the voice
as a carousel of cards (a woman's, a man's or a quick Piper voice, its kind,
a gavel when its licence has conditions) each with its own button to hear it
(`DubSampler`: one voice at a time, the card shows it loading and playing),
then how it sounds (speed and the video's own voice, a connected choice).
However many voices there are, they take one line; the chosen one is
scrolled into sight. While the card dubs, the group gives way to the voice
saying it, its language and speed, the share done and the way to stop. The
tool shows its plan before it starts as four nodes on a rail (struck off
where not needed, a warning where something is missing, said in full under
it); once it runs, the rows that tick off.

**One breath** (`DubTiming.oneBreath`). For eSpeak NG languages sherpa-onnx
cuts a text at every `.`, `!`, `?` (and `;`), says each piece on its own and
glues them: the seams are heard, the voice stops and starts again. So inside
a line those marks become commas (CJK ones the full-width comma) before it is
said, its own end kept: one line, one run of the network, one intonation.
Numbers ("3.5") and abbreviations without a space after them are left.

**Piper voices** (`core/dub/PiperVoice.kt`, `PiperVoiceStore`, `DubSpeaker`).
Any Piper voice (VITS, many times quicker than Kokoro on a phone's processor:
about 0.05 of real time against 0.35 on two desktop cores) is imported as
published: a sherpa-onnx `vits-piper-*.tar.bz2` archive, or Piper's own
`.onnx` and `.onnx.json` (a voice trained at home comes out so). sherpa-onnx
wants facts Piper keeps in the settings inside the network (its metadata:
`model_type=vits`, `comment=piper`, the eSpeak voice, the rate, the speakers)
and the phoneme table as a token list: `PiperVoiceConfig` makes both from the
settings, and `OnnxMetadata` appends the metadata to the file as more of the
protocol buffer (ModelProto's field 14), without reading the network into
memory. eSpeak NG's data is kept once for every voice in `files/dub/
espeak-ng-data` (from the first archive, or copied from Kokoro's), so the
voices outlive the Kokoro model. Nothing is pinned (a voice can be any
one): its SHA-256 and card are shown. `DubSpeaker` is one voice to dub with,
Kokoro's or a Piper speaker; `DubEngine` keeps one network loaded (Kokoro
for one language, or one Piper voice).

**The list** (`core/dub/PiperCatalog.kt`, `PiperAddSheet`). Adding a voice is
picking it: the language (the phone's first), its voices the best first,
with their quality, speakers and size, and a search across all of them. A
voice opens into its two steps: its sherpa-onnx archive to download in the
browser (or Piper's own three files on Hugging Face, once eSpeak NG's data is
on the phone), then the import. The list is Piper's voices.json (123 voices,
the one whose name is not ASCII left out) and OpenVoiceOS's two Italian
"high" voices that sherpa-onnx packs; the app never goes online for it.

**Licences** (`core/dub/VoiceLicence.kt`, `DubLicenceDialog`). A Piper voice
inherits the licence of its recordings, which its card states. On the phone
the card is read for it (tested): research only (the Blizzard licence and
the like), not for commercial use (CC BY-NC...), free (CC0, CC BY, public
domain, Apache, MIT...), or not known, which is treated as not free. A voice
that is not free shows a gavel on its chip and a tinted note under it, and
before every dub with it a dialog says what its licence allows, what counts
as earning from a video, that keeping to it is up to whoever publishes it,
and offers its card, a voice free to use, or the dub. Kokoro is Apache 2.0.

**Speed.** The network runs on the processor's cores; a phone's GPU or NPU
cannot be used: ONNX Runtime's Android build has no GPU provider, and NNAPI
takes only fixed shapes, which these networks are not. ONNX Runtime's XNNPACK
kernels are tried by default for every model but the invisible AI marks'
(always the plain kernels: see "The invisible marks") (`AiKernels`: one switch at the
top of the smart features, read by each engine when it loads; the ONNX
Runtime sessions get the XNNPACK provider with its own threads and ONNX
Runtime's left not spinning, the sherpa-onnx ones the "xnnpack" provider);
where they cannot run, the plain kernels are used, and a change lets go of
every model in memory. A dub says how long it took.

**Marked as AI-generated** (AI Act, Article 50(2)). The dub's WAV carries a
LIST INFO comment (`WavWriter`) saying "AI-generated voice", and an XMP
chunk saying it is all made by a model (IPTC `trainedAlgorithmicMedia`,
`RiffXmp`); no dub is kept without it. The clip keeps the voice's name
(`AudioClip.aiVoice`, in the project) and the request carries it
(`PlacedSound.aiVoice`): every export where a dub is heard is marked (see
"Marked as made with AI" below).

## Magic eraser

What is painted over a picture goes, filled in with what should be behind
it: in the picture editor (the picture itself, `ImageTool.ERASE`, and its
stickers) and on pictures laid over the video (`EditorTool.OVERLAY_ERASE`).

**Models** (`EraseModel`, two `SpeechModelStore`s, one chosen as the cut-out
is). MI-GAN (Picsart, MIT), its ONNX pipeline taking bytes (the mask 255
where the picture is kept); LaMa big-lama (Apache 2.0, ONNX by Carve), a
fixed 512 square of floats (the mask 1 where it is filled). MI-GAN's file is
pinned; LaMa's is published without a hash, so `FileCheck` takes an empty
pin as "there" and the card shows what was found.

**Choosing what goes** (`RefineMaskDialog`, `MaskKind.ERASE`). The cut-out's
dialog with its two brushes: the magic one (EdgeTAM) finds the whole of what
is tapped or scribbled on, the precise one takes exactly what it covers;
painted in raspberry, kept in green. It starts from a blank mask
(`HdrSources.blankMask`), or from the subject the cut-out model finds. The
dialog (shared by the cut-out and the HDR light) keeps the picture as large
as it can: its top bar has only Close, the title and Reset; what is going on
is a pill over the top of the picture (aside while a finger paints); view,
compare, fit, Undo and Redo float at its bottom, by the thumb; under it one
tray holds the tool, the brush and its size, and a large button saying what
it does ("Erase what is painted", or applying the correction). The panel
puts the two ways in first and the model under them, as two compact rows
(first, when the chosen model is not on the phone).

**Filling in** (`EraseEngine`, `core/erase/InpaintArea`, tested). The mask
grown by the picture's side over 150 (an outline or a shadow goes too), a
square around it of 2.2 times its size and at least 512 cut out and squeezed
to the model's square, the model's picture squeezed back and laid only where
the mask was, faded over the side over 220 outward; the picture's own alpha
kept. A new PNG in `files/erased/` takes the old picture's place
(`replacePicture` for the picture itself, the overlay's uri otherwise): one
step of undo. Through `AiKernels`, like every model. Beside it, in `files/erased/unmarked/`
under the same name, its twin before the invisible mark: never shared nor
exported (what leaves the app is the marked PNG), it is what the next erase
of that picture starts from, so that the result is marked once instead of a
new mark laid over the old (they add up and show as a pattern; a mark does
not survive another erase of more than about a twentieth of the picture,
so it cannot simply be kept).

**Memory** (issue 28). The Java heap is a few hundred megabytes whatever the
phone's memory, and a photo of 12 megapixels is 50 MB per copy as an int
array. So the picture (decoded mutable, at most 4096 pixels) and its mask
stay bitmaps; the mask's box is read a row at a time; only the crop is held
as arrays (the mask in it, grown), the fade is weighed only three feathers
past the erased part (its grow and blur reach two, so the weights are those of
the whole crop: tested), and the result is laid row by row into the picture
itself, which is then written. The app asks for the larger heap
(`largeHeap`) as the margin for what is left.

## Marked as made with AI

A file that holds what an AI model made says so, in a form programs can
read (Regulation (EU) 2024/1689, Article 50(2); the Commission's guidelines
on Article 50 count dubbing and the removal of things from a picture among
what is marked, and transcription, translation, background removal and
noise reduction among what is not). `core.provenance`, pure and tested:

- **What.** `AiDisclosure`: whether a dub is heard (a clip with an
  `aiVoice`) and whether a picture a model changed is seen
  (`ConversionRequest.aiEditedPictures`), in words ("AI-generated voice",
  "AI-generated inpainting") and as IPTC's Digital Source Type (`compositeWithTrainedAlgorithmicMedia`; a dub's own
  file `trainedAlgorithmicMedia`). `aiDisclosure()` keeps what the format
  carries; for a command written by hand, `aiSources()` takes everything
  and `AiSeal.disclosureFor` what a file of its extension can hold (sound,
  pictures, both, or nothing for text).
- **Nothing else.** No model, no voice (one the user imported may carry a
  person's name), no app, no version, no date, nothing of the phone or the
  person: as little as Article 50 asks for, so that the marks, alone or
  with other data, point to no one (GDPR, data minimisation). The app's own
  name is gone from the other files it writes too (the WAV's ISFT, the
  Ultra HDR XMP's `xmptk`, the subtitle script's header).
- **Where the pictures come from.** The magic eraser writes its PNG with
  the same XMP iTXt (`EraseEngine`);
  `AiMarksReader` reads the XMP of the PNG and JPEG files of a project (its
  files, the pictures laid over it, the picture on its canvas), its own
  results wherever a project copied them and pictures other programs marked
  as made with a trained model (IPTC's source type, nothing more), and
  `RequestMapping` sets `aiEditedPictures` (`AiEditLookup`, set from
  `AppContainer.aiMarks`, one read per file).
- **Pictures in words too.** FFmpeg writes no tags into a picture, so a
  picture a model changed gets its words after it, in its EXIF
  `UserComment` ("AI-generated inpainting", before a comment of the user's
  own; `withAiNote`), written whether or not the user set tags: what
  galleries and the app's own tag reader show, beside the XMP.
- **Two layers.** The tags `AI_GENERATED` and `DIGITAL_SOURCE_TYPE`, given
  by `CommandBuilder` after the user's own (MP4 and M4A with
  `use_metadata_tags`; FFmpeg keeps only the last `-movflags`, so the
  command gives them once, all together, `CommandBuilder.oneMovFlags`), and
  an XMP packet (`Xmp`) that `ConversionEngine.seal` writes into the
  finished file, after FFmpeg and after the picture tags, each part of a
  zip included: MP4 and QuickTime in a top-level uuid box, WAV and AVI in
  a `_PMX` chunk, PNG in iTXt, JPEG in APP1 (an Ultra HDR picture keeps its
  own XMP, which names its gain map, with the marks merged in, and its MPF
  index is set right; `UltraHdr.assemble` does the same), WebP in an
  `XMP ` chunk (a simple WebP made extended), GIF in an application
  extension, TIFF in tag 700, SVG in `<metadata>`. An XMP already there is
  put out of use. Matroska, WebM, MP3, FLAC and Ogg keep only the tags.
- **Not removable.** The settings cannot leave them out (the tags come
  last, the XMP after FFmpeg). A command written by hand is followed by a
  stream copy that writes the tags again (`AiSeal.remux`, for the formats
  whose marks are their tags, and MP4), then the XMP; the command studio
  says so where a command drops or sets them (`LintCode.AI_MARKS_KEPT`).
- **Never without them.** A format with no place for the marks (BMP,
  MPEG-TS and -PS, an extension `AiSeal` does not know) fails with
  `AI_MARKS_NO_PLACE`, a file they cannot be written into with
  `AI_MARKS_FAILED`; the unfinished file is deleted, as on any failure.

**The invisible marks** (`core/marking/`, pure and tested; `app/.../marking/`):

- **Voices: AudioSeal** (`MarkEngine.markSpeech`, from `DubEngine` once the
  voice is let go of, and for the voice previews too). Each line at 16 kHz
  (`Resampler`), in 5 s windows that overlap by 0.5 s (the ONNX files take a
  fixed window: `scripts/export-ai-markers.py`), each window's watermark
  from the generator with the message of zeros, joined with crossfades,
  back at the line's own rate and added to it: the speech itself is never
  resampled, the silences between lines stay silent. Then read back by the
  detector over every line: at least 80% of the speech (its 20 ms frames
  above -50 dBFS) marked, the message zeros, or the dub is not kept.
- **Pictures: TrustMark Q** (`MarkEngine.markPicture`, from `EraseEngine`
  before the PNG is written), as Adobe's `encode()`: the whole picture (its
  centre square when one side is more than twice the other) averaged down to
  256 x 256, the encoder's difference with each colour's mean taken away,
  stretched back bilinearly and added, fading in over the last 1% of each
  edge; the transparency left as it was. The payload is TrustMark's
  BCH_SUPER schema with the fixed 40 data bits 0101...01, their 56 BCH
  bits and the version 0000 (`Marks.PICTURE_CODEWORD`): Adobe's own reader
  finds it as such. Not all zeros: the encoder takes the bits through a
  linear layer, and with no bit set the mark came out too weak (6 to 11
  wrong bits right after marking on the export's test photos, often not
  found); with as many ones as zeros it read with none, also after JPEG 75
  and at half the size. Read back at once: at most 3 wrong bits of 96 (BCH
  corrects 8), or the result is not kept. A picture that already reads with
  at most 3 wrong bits (erased again after a first erase) is not marked
  again: the marks would add up and start to show. Before the picture is touched the
  mark is checked twice: its mean strength (at most 0.08 in -1..1 units;
  TrustMark's own measured 0.001 to 0.04) and its bits on the marked 256
  square; if either is wrong nothing is laid and nothing is kept. The marks'
  networks always run on ONNX Runtime's plain kernels, whatever the XNNPACK
  setting: with XNNPACK, TrustMark's encoder was seen on a phone to give a
  mark far too strong (burnt colours, bursts of blue). The log says the
  mark's strength, its wrong bits and the picture's size and colour space.
- **What was made before** (a dub, an erased picture, from an older version
  or another program that marked it as made with a trained model):
  `MarkGuard`, right before an export, reads its mark and, when it has none,
  marks a copy that the export uses (a dub line by line between its
  silences: `SpeechMarking.runs`); an export that cannot have them fails.
- **First of all** (`MarkingGate`, `MarkingSweep`): the marks' four files
  (`MarkerModel`, one import) come before any other model. Every store's
  import asks the gate; the refusal says why (`MarkersFirstDialog`). At
  every start, before any store reads its files, models without the marks,
  or older than them, all go and the user is told once
  (`ModelsClearedDialog`); removing the marks removes every model. The dub
  and the eraser refuse to start without them, and the engines throw rather
  than hand out something unmarked.
- **Nothing that tells anyone apart.** Both payloads are constants (the
  speech's zeros, the pictures' fixed codeword), the same for every user: no identifier of the phone, the person, the file or the
  model, no date, nothing random (a random value per file would make its
  copies linkable). The metadata are signed by no one, by choice: a key per
  phone would link every file of a person, a key shared by every copy of an
  open-source app is public and proves nothing, a server that signs would
  need the network the app does not have and would make whoever runs it the
  controller of what it is sent; a trusted time-stamp would add the date.
  The Code of Practice on AI-generated content suggests signed, time-stamped
  metadata; it is voluntary, the Act asks for marks "as far as technically
  feasible", and the GDPR (data minimisation, Article 5(1)(c); the AI Act
  does not override it, its Article 2(7)) asks for as little as needed: the
  robustness signing would add is what the invisible marks give.
- **That they carry nothing is not certain**, and the README says so to
  the user. The design leaves no identifier in them, but: the constant
  payloads and the XMP say "made with software that marks this way", and
  with few users that alone narrows down who made a file; the marks come
  from neural models that cannot be fully inspected, so nothing proves a
  mark carries no more than its bits, or that it does not tell which model
  files or app version wrote it (its residual depends on both, and on the
  content); and the rest of the file can identify its maker anyway: the
  place and device tags FFmpeg keeps unless "Remove sensitive"
  (`withoutSensitive`) is chosen, the encoder tags and x264/x265's settings
  SEI, the file's dates, the content itself.
- **Their files** are made by `.github/workflows/ai-markers.yml` (on every
  push that changes `scripts/ai-markers.lock`, the export script or the
  tools, and by hand): AudioSeal converted from Meta's weights, TrustMark's
  ONNX files as Adobe serves them, every input pinned by hash, built twice
  and compared, checked against PyTorch and against compression, published
  as the pre-release `ai-markers-1`. Until its first run has learned the
  pins (`scripts/pin-ai-markers.py`) nothing can be imported and no app
  release passes `scripts/check-model-pins.py`. See docs/MODELS.md.

The terms (README, About) ask not to remove the marks. Their limits: a
sound or a picture changed enough (re-recorded, heavily cropped, painted
over) can lose the invisible mark, as any mark today.

## Smart subtitles (speech)

Subtitles made from what is said in the video, on the phone. The idea, the
model and the way to run it come from the anti-vocale app
(github.com/RisorseArtificiali/anti-vocale, Apache 2.0); the code is this
app's own.

**The model.** NVIDIA's Parakeet TDT 0.6B v3 (CC BY 4.0, 25 European
languages, found by itself), as converted and quantised to int8 by sherpa-onnx
(`speech/SpeechModel.kt`). It is not shipped: Settings > Smart features opens
the sherpa-onnx release archive (487 MB, `.tar.bz2`) in the browser, and the
user imports the downloaded file (or the model's four files).
`speech/SpeechModelStore.kt` reads it once, as a stream: bzip2
(`core/archive/Bzip2InputStream.kt`, a plain decoder that checks every block's
CRC) and tar (`core/archive/TarReader.kt`) are undone on the fly, every file is
hashed as it is written into a temporary folder, and the folder takes the
model's place only when every file matches its pinned SHA-256 (the archive's
own hash is checked too and shown). The hashes are kept; "Check again" hashes
the files anew. Only files that passed are ever handed to the native runtime:
a damaged model makes ONNX Runtime abort the whole process.

**Running it.** sherpa-onnx 1.13.8 (Apache 2.0, with ONNX Runtime, MIT), the
AAR of its GitHub release in `app/libs` (not on Maven Central), pinned by its
SHA-256: the build checks it (`verifySherpaOnnx`) and
`scripts/fetch-sherpa-onnx.sh` downloads it. Its C and C++ API libraries are
left out of the APK (the app uses JNI only), and R8 keeps its classes (the
native code reads them by name). `speech/SpeechEngine.kt` loads the
`OfflineRecognizer` (greedy search, cores - 2 threads, between 2 and 4) when
it is needed and lets it go as soon as the file is heard (see Models in
memory); the speed test listens
to the four recordings of the archive and compares what it heard with what
they say.

**Listening.** `speech/SpeechTranscriber.kt` takes the sound out of a file
with FFmpeg (`media/.../FfmpegAudioExtractor.kt`: 16 kHz mono floats, padded
so that sample 0 is the file's time 0) and feeds it to the model 30 seconds at
a time (the model's memory grows with the square of what it hears at once).
Each chunk ends at the quietest 20 ms of its last two seconds, so that no word
is cut in two. The model returns word pieces with their start and duration
(TDT); `RecognizedWords` (`core/subtitles/SpokenWords.kt`) joins them into
words: a piece that starts with a space starts a word, punctuation joins the
word before without keeping it on screen longer. The words of every chunk
reach the preview as they are found (`liveWords` of the view model) and the
settings when a file is done (one undo step); they are kept per file, on the
file's own clock (`ConversionSettings.spokenWords`, saved in the project as a
line of times and a string of words).

**Lines and look.** Words follow their footage through the edit like
subtitle files (`onTimeline`), then `SubtitleLayout` makes the blocks shown at
once: a block ends at the end of a sentence, at a pause, after six seconds or
when its lines (one or two) are full; two lines are balanced. Lines break
where their width, added up from the glyph advances of the very font file the
export uses (`FontFacts` reads its `cmap`, `hmtx`, `OS/2` and `name` tables),
would leave 88 % of the frame; a single word too wide shrinks its block.
`SubtitleStyle` holds the size (a fraction of the frame height), the place
(the bottom middle of the text, dragged on the preview), colours, outline / box
/ shadow, bold, capitals and the highlight of the word being said (that word,
or every word so far). A SubRip file is drawn with the same style; a
SubStation Alpha file keeps its own.

**Export.** The engine writes the blocks as a SubStation Alpha script
(`AssSubtitles.kt`): a canvas 1080 lines high and as wide as the frame's shape,
every line placed with `\pos` from the bottom middle and broken with `\N`
(wrapping off), the highlight as colour changes, one event per word where it
moves. The font is copied alone into a folder handed to libass (`fontsdir`)
and named by its family; FFmpegKit's fontconfig also knows `/system/fonts`
(`media/.../FfmpegFonts.kt`) for files that name other fonts. The script is
burnt in where subtitle files are, before the speed change.

**Fonts.** Captions and subtitles choose a typeface (`TextFont`: sans, serif,
mono, typewriter, handwriting, casual, small caps), each one of the fonts AOSP
ships in `/system/fonts` (`SystemFontProvider`; a phone without one falls back
to the default font). drawtext, libass and the preview all draw from the same
file (`LocalExportFonts`).

**A caption's look and own font.** A caption can have an outline, a shadow
and a box behind it (`TextLook`, sizes as fractions of the font size, colours
ARGB). drawtext takes them in whole pixels (`borderw`, `shadowx`/`shadowy`,
`box`/`boxborderw`, colours as `0xRRGGBB@alpha`), so `VideoFilters.lookOptions`
works them out for the caption's starting size on the frame it lands on (the
clip's frame, the whole video's at the output height, the picture or its
canvas; 1080 when not known); the preview (`TextOverlaysLayer`) draws the box,
then the shadow, the outline (a stroke twice as wide) and the letters at that
same size. A font the user added (.ttf, .otf) is copied into the app
(`CaptionFonts`, checked with `Typeface.Builder`) and kept as the caption's
`fontFile`; `FontProvider.customFontPath` gives it to drawtext while it can be
read, else the caption's family is used, and the preview does the same. Such a
font is drawn as it is, without a made-up bold, in both.

**As texts.** "Make them texts" turns every line of every block into an
ordinary caption (`TextOverlay`), timed on the output clock, in the
subtitles' place, size, colour and font; the word highlight and the outline
stay with subtitles only.

**Transcript.** The TXT and SRT formats (category TEXT) are not written by
FFmpeg. The format picker greys them out until the model is set up; picking one
opens `TranscriptScreen`: the file plays (ExoPlayer) above its lines, which are
the blocks `SubtitleLayout.ofWords` makes, found live while the model listens.
The line being said follows the player and its word is lit; a tap on a word
seeks there, the pencil corrects a line in place (`editSpokenLine`, the same
edit as the subtitles panel). Saving runs the export, which reuses the words
already heard (and listens to any file not heard yet), moves them onto the edit
and writes either paragraphs with their times (`TranscriptText`) or SubRip cues
cut by the subtitle style (`SrtSubtitles`).

**All the tags.** Besides the usual fields, `MetadataTags.custom` holds every
other tag the file had (`AndroidMetadataReader`: FFprobe's tags of the file and
of its first sound stream by the names the file gives them, without those that
only describe the container; a picture's EXIF from the list `EXIF_TAGS`, not
its size, orientation or thumbnail) and those added by hand, grouped by
`tagKind` (place, device, date, camera settings, other; place and device are
sensitive). `sourceKeys` remembers which were read: one taken away is written
empty, which makes FFmpeg drop it. "Remove sensitive" (`withoutSensitive`)
drops those groups and also blanks the keys phones and cameras use even when
they were not read (`SENSITIVE_FFMPEG_KEYS`); "Remove all" (`cleared`) adds
`-map_metadata -1` (with a cover in a tags file, only that file's tags are
mapped). An MP4 or M4A keeps tags of its own only with `-movflags
+use_metadata_tags`, added when there are any. FFmpeg writes no EXIF: a
picture gets what is listed (`AndroidPictureTagger`, by EXIF name), so a
picture's GPS and camera now travel with it unless taken away.

**Lyrics.** The tags of an audio file hold its album artist (`album_artist`:
TPE2, aART, ALBUMARTIST) and its lyrics (`lyrics`: FFmpeg 6.1 writes it as an
MP3's USLT frame, an M4A's lyrics atom, a LYRICS comment in FLAC, Ogg and
Opus). The lyrics are typed, or written from what is sung
(`ConversionViewModel.lyricsFromSpeech`): the files not heard yet are listened
to as for subtitles, the words follow the edit onto the exported file's clock,
and `LyricsText` (`core/subtitles`) makes short lines (a pause, a sentence, a
comma once half full, 42 characters) and verses (3 s of silence), plain or
synced the LRC way ("[mm:ss.xx]line", an empty timed line where a verse stops),
which players that know LRC follow as the song plays. Read back from a file,
FFmpeg's "lyrics-eng" (an USLT frame) counts as the lyrics.

**Preview.** `StyledSubtitles.kt` draws the same blocks on the whole output
frame (canvas included) with the same font file, as libass measures it: a line
is as high as the font size and its baseline sits where the OS/2 ascent puts
it, the outline is a stroke of twice its width under the letters, the box
surrounds each line's advance width. With the subtitles panel open the text is
dragged into place (it sticks to the middle) and resized by its corner.

## Keyframes

A text, a picture (or video) laid over the video, a clip and a placed sound can
change over time, CapCut style: `core/model/Keyframes` holds keyframes, each the
values of some `KeyProperty` at one moment and the easing to the next (smooth by
default). Between two keyframes a value goes from one to the other; before the
first and after the last it holds; a property no keyframe holds keeps the thing's
own value. `valueAt` (the preview) and `expression` (an FFmpeg expression of the
time, nested `if(lt(...))` pieces with `clip` and smoothstep) follow the same curve.

Clocks: a text, picture or sound counts from its own start on the exported video
(its keyframes move with it; dragging only its start edge, or cutting it, keeps
them where they were on the video); a clip counts in its file, so its keyframes
stay with the footage when it is trimmed, split or the whole video is trimmed.

What animates:

- texts: place, size, opacity (`drawtext` with `x`, `y`, `fontsize` and `alpha`
  as expressions of `t - start`);
- pictures: place, size, turn, opacity (`CommandBuilder.animatedOverlay`): read
  as a stream (`-loop 1`), sized once at the largest width (`scale2ref`), the
  opacity multiplied in from a 2 x 2 grey picture worked out on every frame
  (`geq` with `T`, stretched, `blend=multiply` on the alpha), turned on every
  frame in a square that holds it at any angle (`rotate`, transparent corners),
  shrunk on every frame (`scale` with `eval=frame`), and placed by the tight box
  of the turned picture, as a still one is (its shape is `ImagePlacement.aspect`);
- clips: framing (`Effects.zoom`, `panX`, `panY`; an enlarged copy scaled frame by
  frame laid over the frame itself), colours (`eq` with `eval=frame`) and volume
  (`volume` with `eval=frame`); the picture's time is the file's (`t + start`, or
  `end - t` for a reversed clip, before the speed change), the sound's
  `start + t * speed`;
- placed sounds: volume, from their start.

The editor (`Keyframing.kt`): the diamond next to undo (`KeyframeButton`) puts a
keyframe at the playhead on what has the focus with its values there, or takes
the one there away (the last one taken away leaves the values of that moment).
Once something has keyframes the panels show its values at the playhead and a
change of a value it animates goes into the keyframe there (`keyedEdit`), made if
there is none; the preview's drags do the same. The selected item's keyframes are
diamonds on the timeline; a tap on one brings the playhead there, a drag moves it
(issue #31; `KeyframeDrag` in `EditorTimeline.kt`, `Keyframes.moved`). A drag is
picked up within 12 dp of a diamond, on a clip only along the bottom of the track
(higher up its edges always trim it), on a text, picture or sound on its row; it
is seen before every other gesture of the timeline once the finger moves, so a
tap still brings the playhead and a hold still lifts the clip. A keyframe at the
start or the end sits where the edge is grabbed too: there the edge wins, unless
the playhead is on the keyframe (drawn filled, within 6 dp). So a drag trims at
once, and a tap on the keyframe first, which brings the playhead onto it, makes
the next drag move the keyframe. With the playhead on such a keyframe the edge is
still grabbed higher up the clip, or a little outside the text, picture or sound
(past the keyframe's 12 dp, within the edge's 24 dp).

A trim can leave keyframes outside what they belong to (a clip's keyframes are
on its file's clock, a text's, picture's or sound's from its start). They still
count: the values head for them, in the preview and in the export. So they stay
in view where they are, drawn after every clip and item (they may lie over the
next one): hollow, their outline dashed and paler, tied to the edge they were
cut off at by a dashed line. A tap brings the playhead onto one and a drag
brings it back in (`moveBounds` always allows a keyframe its own time, so it
never jumps when picked up). The keyframe takes its values, easing and curve with it,
snaps to the playhead, the cuts and the edges as the other drags do, and stays
inside what it belongs to and between its neighbours, `MIN_GAP_MS` (80 ms) from
each (`Keyframes.moveBounds`): each keyframe's easing goes on to the next, so the
animation keeps its order and two keyframes never share a moment. While it moves
it is drawn larger with its time in a pill (from the start of the clip, text,
picture or sound), the preview shows the frame there and a tick says when it
snaps or meets a neighbour; on release it is one undo step and the playhead lands
on it. The preview
animates the texts and pictures (composed at their values on the clock), the
clip's framing (a layer scaled and moved while drawing), its colours and the
volumes (`ClipLevels.gainAt`, `PreviewSound.volumeAt`).

## Subject cut-out

A key of `KeyMode.SUBJECT` cuts around the subject of a still picture instead of
a colour: the main picture of the picture editor, its stickers, and pictures laid
over the video (never a video, whose subject moves). Its `mask` is a PNG as large
as the picture, white, whose alpha is how much of each pixel is the subject.

**The models** (`CutoutModel`, `CutoutVariant`; one chosen in the settings,
all three can be installed): U2-NetP (fast, 320 x 320) and IS-Net (precise,
1024 x 1024) of Xuebin Qin et al. (Apache 2.0), as converted to ONNX by rembg
(MIT); BiRefNet-lite (finest, 1024 x 1024) of Zheng Peng et al. (MIT), as
converted by this project (below). Like the speech model they are downloaded
by the browser (the app has no network access) and imported in Settings >
Smart features: `cutout/CutoutModelStore` copies the one file, hashes it on
the way and keeps it only when it matches the pinned SHA-256; it reports with
the speech model's states, so `SmartSettings` shows them with the same card.
`cutout/CutoutEngine` runs the chosen one through ONNX Runtime's Java API
(loaded when needed, let go of after two idle minutes; each model says how much
free memory it needs, and BiRefNet runs without ONNX Runtime's memory arena,
which keeps its peak near 2.1 GB rather than 2.5). `core/cutout/SubjectModelIo`
is the input and output as rembg has them (`SubjectNet`: the side, the mean and
deviation per channel after dividing by the largest value; IS-Net's and
U2-Net's output stretched to 0..255, BiRefNet's logits through a sigmoid and
not stretched, so that a picture with no subject stays empty).
`cutout/SubjectCutter` squeezes the picture (decoded as FFmpeg and the preview
read it, EXIF turn ignored, at most 4096 pixels), runs the model, stretches the
mask back to the picture's size and keeps it in `files/cutouts`, one per picture
and model. Once a picture is cut the model leaves memory at once (see Models
in memory).

**Correcting it, and lifting a subject** (`cutout/RefineEngine`,
`core/cutout/PromptSegmentation`, tested). EdgeTAM (Meta, Apache 2.0), a
Segment Anything 2 for phones, in two ONNX files: the image encoder (the
picture stretched to 1024 x 1024 as SAM 2 does, without keeping its
proportions; / 255, ImageNet mean and deviation), run once per picture, and
the prompt encoder with the mask decoder (points in the square's pixels, each
side stretched with the picture; labels 1 in, 0 out), which gives three masks
of 256 x 256 logits over the whole picture and a score for each.
`MaskRefiner` takes the mask that agrees with the strokes (`choose`), keeps
what touches the stroke and brings its edge to the picture's own
(`GuidedFilter`). `SubjectLifter`, behind the photo editor's long press
(`LiftSubject.kt`, only on the picture itself, not on a canvas, nor while
cropping or drawing): the picture made again at 2048 pixels with the same look
(`PictureRenderer`), one point, `chooseForTap` (of the masks holding the point,
the best scored that is not nearly the whole picture), the piece under the
finger, the guided filter, then that piece alone on a transparent background,
cut to its box, as a PNG in `files/lifted/` (older than 14 days: deleted; a
saved project keeps its own copy). `addPlacedOverlay` lays it as a sticker
exactly where it was (an overlay's x and y are shares of the free space around
it), one step of undo; it rises off the picture (a spring, a halo of its
outline, sparks) and settles. The lift lasts as long as the finger stays down:
let go before it is done and it is called off (the job cancelled, nothing laid,
the PNG written meanwhile deleted), with a firm haptic. While the model looks,
a badge sits under the finger (a nine-sided cookie turning slowly, a loading
indicator in it) and ripples spread from it as on a pond, each born as the
cookie and relaxing into a circle as it goes (`MorphShape` drawn as an
outline); called off, the same badge springs to rest and morphs into a red
four-leaf clover with a cross, the ripples turn red and are drawn back in, and
it shrinks away; done, it pops away as the subject rises. Done or called off, the
model and the picture it read leave memory at once (the lift holds the model
only across its two calls, `SubjectLifter.forget` the picture); it is let go
of after the step under way, never in the middle of one, and loads again for
the next press. The correction dialog holds it from the first stroke to its
closing, confirmed or not (see Models in memory).

**The project's own ONNX files** (docs/MODELS.md has the whole record).
EdgeTAM is published by Meta for PyTorch only, and BiRefNet-lite's own ONNX
needs more than 5.7 GB at 1024 x 1024 (its deformable convolutions written as
gathers). `scripts/export-edgetam.py` and `scripts/export-birefnet.py` convert
them from exactly what `scripts/edgetam-onnx.lock` and
`scripts/birefnet-onnx.lock` pin (upstream commit, weights and licence, each
by SHA-256), with the tools of `scripts/model-export-requirements.txt`
(`--require-hashes`): nothing trained or changed, constant folding off (the
same bytes on every computer), BiRefNet's deformable convolutions as ONNX's
`DeformConv` (opset 19). Each run checks the files against PyTorch (random
inputs and real photos) and writes, next to them, the upstream `LICENSE`, the
`NOTICE` of the changes (Apache 2.0 section 4b) and their `PROVENANCE`; the
licence and the notice also go inside each file (`doc_string`, metadata).
`.github/workflows/models-onnx.yml` builds them twice (two runner images, the
second on two cores) and publishes only byte-identical files, as the
pre-releases named by the locks' `release_tag`; the app downloads them from
there. `scripts/check-model-pins.py` (run by `release.yml` for every tag,
with `--online`) checks that the Kotlin pins, the locks,
the licences and notices bundled in `assets/licenses/`, the release files and
docs/MODELS.md all agree.

**The sticker gallery** (`stickers/StickerLibrary`, `stickers/StickerShelf`).
A picture saved as a sticker is decoded as the editors read it (at most 2048
pixels), cut by its key when it has one (the subject's mask, brush corrections
included, or a colour; the fill made transparent) after its own colours
(`keyedBitmap`), trimmed to what is left and written as a PNG in
`files/sticker-library/`, with a record beside it (`<id>.sig`): the signature
of what it was made from (`core/stickers/StickerSignature`, tested: the
picture's SHA-256, the key's settings and its mask's bytes, the colours) and
its own file's SHA-256. `savedAs` finds a picture cut the same way (or a
sticker laid as it is) among them: the editors' bookmark is outlined until it
is saved, filled once it is (and then takes it away), and a save of what is
there already writes nothing. With `AppSettings.stickerCopies` (off by default) a
copy goes to Pictures/FFmpegGUI/Stickers through MediaStore (`PublicCopies`,
no permission). `MainActivity` provides the gallery as `LocalStickerShelf`
(its list, save and delete); a save is confirmed by a haptic only, and a
short message says when it could not be made.
Saved from: the floating actions of a selected sticker or picture (a subject
just lifted with a long press is selected, its actions in view), and the Cut
out panel (`ColorKeyContent`'s `onSaveSticker`). The photo editor's Sticker and the video editor's Overlay open
`StickerSheet` first: the saved stickers on a checkerboard (a tap lays one, a
long press offers to take it away) and a large button to pick a file. A
sticker deleted from the gallery stays in the projects that use it (they keep
their own copy).

**Retired models** (`cutout/RetiredModels`). A model the app no longer uses
(SlimSAM, before EdgeTAM) is deleted at start when it is still on the phone,
from its folder and its settings; what was deleted waits in `retired_models`
until `RetiredModelsDialog` has said so once (which model, what replaced it,
the space freed, a way to set up the new one). The list only grows; the pin
check fails if a retired folder is ever one in use.

ONNX Runtime comes twice, inside the sherpa-onnx AAR (1.28.2) and with its Java
API (1.28.0): the build keeps one `libonnxruntime.so` (`pickFirsts`), which both
reach through the same C API version.

**Export.** `subjectFilters` (`KeyFilters.kt`) reads the mask with `movie`, takes
its alpha with `alphaextract`, puts it through the steps that turned, mirrored
and cut the picture before the key (the picture editor's geometry stages; an
overlay's mirrors), sizes it to the picture with `scale2ref` and merges it in
with `alphamerge` (which repeats its one frame for a looped picture). The fill,
the see-through fill and an overlay's own transparency then work as for a colour
key.

**Preview.** `ImageStage.Key` gets the mask as a raster as large as the picture
it starts from (`SubjectMasks`), puts it through the same geometry stages (kept
from the last time while they stay the same) and uses it as the key's alpha in
`ColorKeying.apply`. Stickers and pictures laid over the video are cut on the
CPU (`keyedBitmap`) with the mask read at their size; the GPU shader keys by
colour only.

**Panel.** `ColorKeyContent` shows a card to cut the subject out where it gets
the still picture's uri (`subjectUri`); `LocalSubjectCut` (provided by
`MainActivity`) carries the model's state, where the view model's
`cutOutSubject` is, and the actions. A subject key keeps only the fill.

## Command studio

The full-screen editor for a hand-written command (`app/command`), opened from
the command panel of every editor. Its rule: it never types for the user.
Suggestions sit in a strip above the keyboard and only a tap, or Tab on a
keyboard, takes one; whatever the studio changes on request (a recipe, a
history entry, laying the command out, renaming a file) can be undone.

- **What FFmpeg knows** (`core/command/studio/FfmpegCatalog.kt`): the bundled
  FFmpeg's own answers to `-h full`, `-filters`, `-encoders`, `-muxers` and
  `-pix_fmts`, read by `FfmpegHelpParser` into options (with their AVOption
  values, defaults and ranges), filters, encoders, formats and pixel formats.
  `-h filter=x`, `-h encoder=x` and `-h muxer=x` are asked when needed. The
  answers are kept in `files/ffmpeg_help/<install time>/`, so FFmpeg is asked
  once per installed version; nothing goes online. Before the first answer a
  built-in list of everyday options (`FfmpegCatalog.COMMON`) is used, which
  also ranks the suggestions.
- **Reading the command** (`CommandReading`): `spanCommandLine` splits it as a
  shell would and keeps where every argument is; each one is the program, an
  option, an option's value or a file on its own. A backslash before a line
  break joins two lines, so a command laid out on several lines runs as is.
- **Suggestions** (`CommandAssist`): for the word at the cursor, by context:
  options (the everyday ones first, then FFmpeg's), stream specifiers, the
  value an option takes (encoders of the right kind, formats, pixel formats,
  AVOption constants, flags with a `+`, usual sizes, rates and bitrates),
  inside a filtergraph the filters of the right kind, their options and the
  values of those, labels and inputs, then the project's file names. With
  nothing typed: what usually comes next. `applySuggestion` replaces exactly
  the word the suggestion names.
- **Checker** (`CommandLint`): unclosed quotes and brackets, options missing
  their value, unknown options, filters and encoders (only once FFmpeg has
  been read), inputs that do not exist (`-map 3`, `[2:v]`), files that are not
  the project's, no input, no output. It marks; a fix (the missing output) is
  offered, never applied.
- **Recipes** (`CommandRecipes.kt`): ready commands filled with the project's
  files; one needing an encoder this FFmpeg lacks says so.
- **Files**: besides the project's own names, files added in the studio are
  copied into `files/command_files/` and named for the command
  (`CommandFile`, kept with the project). `CommandMode.Manual` carries them
  and the engine stages them like any source before `ManualCommandResolver`
  swaps their names for paths (a name of the project's wins).
- **History and favourites**: `CommandStudioStore`, in the app's preferences.

## FFmpeg runtime

The original FFmpegKit was retired and its binaries removed from Maven Central.
The project uses **FFmpegKitNext** 6.1.1
([arthenica/ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next)),
the official continuation by FFmpegKit's author: LGPL 3.0, GPL 3.0 with the GPL
libraries enabled, same `com.arthenica.ffmpegkit` API (now written in Kotlin),
FFmpeg n6.1.6. It publishes **source only**, so the AAR is built here, from
pinned sources, and never downloaded:

- `scripts/ffmpeg-kit-next.lock` pins FFmpegKitNext and every source its Android
  build may download (FFmpeg and each external library) to a commit. The
  commits of FFmpeg, libass, dav1d, freetype, harfbuzz, fribidi, opus, vorbis,
  ogg, zimg, vid.stab, expat, libpng, cpu_features and x264 were checked against
  the upstream projects' own repositories.
- `scripts/build-ffmpeg-kit-next.sh` clones FFmpegKitNext at the locked commit,
  checks that the lock says what its `scripts/source.sh` asks for, fetches the
  sources the build needs and checks each commit **before** anything is
  compiled, runs `nix-android.sh -p android-r27d` (the toolchain, Android SDK,
  NDK r27d and CMake, pinned by FFmpegKitNext's `flake.lock`), checks every
  source the build used again and only then copies the AAR to
  `media/libs/ffmpeg-kit-next-6.1.1.aar`, with a `.provenance.txt` beside it
  (its SHA-256, the commits, the options). `media/libs/` is ignored by git.
- What could slip past those checks is closed off too:
  - the script runs again with a clean environment (`KEEP_ENV`): FFmpegKitNext
    reads `version_<lib>`, `REDOWNLOAD_<lib>`, `SKIP_ffmpeg`, `OK_<lib>`,
    `CFLAGS` and more from the environment, `nix develop` passes it through, and
    git reads `GIT_DIR`; none of them reaches git or the build;
  - FFmpegKitNext reuses any library left in its `prebuilt/` without asking
    which source built it, so `prebuilt/` is emptied whenever a source is
    fetched again, the lock, the options or the script change, or a check
    after the build fails (`prepare_prebuilt`, stamp in
    `prebuilt/.build-ffmpeg-kit-next-inputs`);
  - every source is put back exactly as its commit before each build
    (`git reset --hard`, `git clean -ffdx`), so a file changed by hand never
    gets built;
  - FFmpegKitNext's `nix.conf` sets `accept-flake-config = true`; the build
    runs with `NIX_CONFIG="accept-flake-config = false"`, so a `nixConfig`
    added to its `flake.nix` one day (extra binary caches) is not taken;
  - the work folder must be new or empty the first time; `--clean` only
    deletes a folder that holds the script's marker (`.build-ffmpeg-kit-next`),
    or one that holds FFmpegKitNext's clone alone (made by an earlier version);
  - git runs without the user's and the system's configuration
    (`GIT_CONFIG_GLOBAL=/dev/null`, `GIT_CONFIG_NOSYSTEM=1`): no hook or
    `core.fsmonitor` command of theirs runs on the sources;
  - FFmpegKitNext's own tracked files are put back as its commit at each run
    (its build edits `build.gradle`, the script adds a line to
    `gradle-wrapper.properties`);
  - the AAR is packed by FFmpegKitNext's Android project with Gradle, which
    neither Nix nor the commits pin. The Gradle distribution is pinned by the
    `gradle` line of the lock: the script adds its SHA-256 as
    `distributionSha256Sum`, so the wrapper checks the zip it downloads, and
    drops a distribution it did not see installed with that check (the wrapper
    checks downloads only, and deletes the zip once unpacked). The Android
    Gradle plugin, Kotlin, Dokka and every library they bring are pinned by
    `scripts/ffmpeg-kit-next.verification-metadata.xml` (Gradle's dependency
    verification), copied into the project before each build; it is made with
    `--write-gradle-verification` (trusted on first use: read it before
    committing). The project's `gradle-wrapper.jar` is Gradle 8.13's official
    one, and Gradle's home is inside the work folder, so `~/.gradle` does not
    take part;
  - that Gradle home is set up anew each run: its `gradle.properties` only
    sets `org.gradle.dependency.verification=strict` (nothing can make the
    verification lenient or turn it off), and its only init script keeps the
    project's unit tests from running. They do not change the AAR, and a test
    framework can download what it needs outside Gradle's verification (later
    FFmpegKitNext releases use Robolectric, which fetches Android jars by
    itself and trusts any copy in `~/.m2`). What the tests compile is still
    resolved and checked by Gradle.
- `settings.gradle.kts` serves that AAR as `com.arthenica:ffmpeg-kit-next`, from
  `media/libs` only (`exclusiveContent`). `verifyFfmpegKitNext` (in
  `media/build.gradle.kts`, before every build) fails if the AAR is missing,
  lacks one of the two ABIs, or if FFmpeg's libraries are not aligned for 16 KB
  pages. Its only dependency, `smart-exception-java`, comes from Maven Central.
- The build enables only what the app uses: x264, x265, xvidcore (GPL), lame,
  vorbis, opus, libass with fontconfig, freetype, fribidi and harfbuzz
  (subtitles and `drawtext`), vid.stab, zimg (`zscale`), dav1d (AV1 sources:
  FFmpeg 6.1 has no AV1 software decoder of its own), Android MediaCodec
  (`h264_mediacodec`) and the system zlib. Nothing network-related (no gnutls,
  openssl or srt). A hand-written command can use any of FFmpeg's built-in
  codecs and filters, but not libraries left out (libvpx, libaom, ...): add
  one with `scripts/build-ffmpeg-kit-next.sh -- --enable-libvpx`; its source is
  checked against the lock like the others.

The first build can take an hour or more and needs Nix on Linux (not NixOS) or
macOS; later builds reuse the checked sources in `.ffmpeg-kit-next/`.

**The AAR the app is built with is pinned, and reproducible.**

- `.github/workflows/ffmpeg-aar.yml` runs after a push to `master` that changes
  the AAR's inputs (`scripts/ffmpeg-kit-next-inputs.sh`: the lock, the build
  script, the Gradle verification file), and stops at once if the pin is already
  current. It builds the AAR twice, on ubuntu-24.04 and on ubuntu-26.04 limited
  to two CPU cores, and publishes it as an asset of a pre-release named after its
  SHA-256 only when both are byte-identical (otherwise it uploads a diffoscope
  report). It then commits the pin, unless `master` has moved on to other inputs
  meanwhile (that push's own run pins those).
- `scripts/ffmpeg-kit-next-aar.txt` pins that asset (URL, SHA-256 and the hash of
  its inputs); `scripts/fetch-ffmpeg-kit-next.sh` downloads and checks it.
  `verifyFfmpegKitNext` refuses any other AAR unless `-Pffmpeg.unpinned=true`
  (local experiments only).
- Nothing in the AAR depends on when it was built: FFmpegKitNext compiles the
  build day into `libffmpegkit.so` (`FFMPEG_KIT_BUILD_DATE` from `date`), so the
  script writes the day of FFmpegKitNext's commit instead (`pin_build_date`) and
  exports `SOURCE_DATE_EPOCH` as that commit's time.
- FFmpeg keeps its configure line, with the work folder's path, in its
  libraries: the published AAR is built in `/var/tmp/ffmpeg-kit-next`
  (`CANONICAL_WORK`), and a rebuild there gives the same file. The script says at
  the end whether its AAR is the pinned one.

**Releases** publish only what IzzyOnDroid's reproducible builds will accept,
and the release key stays on the maintainer's machine:

1. `.github/workflows/release.yml` (every `v*` tag) refuses the tag if its pinned
   AAR was built from other inputs, builds the unsigned APK in the environment
   IzzyOnDroid's recipes use (`debian:bookworm-slim`, OpenJDK 17, command line
   tools 12.0), has IzzyOnDroid's own builder, rbtlog (pinned commit, docker,
   default limits: 10 + 10 minutes of provisioning, 20 minutes of build), rebuild
   it from `.github/izzyondroid/`, and only if both are byte-identical puts it in
   a draft release whose notes give its SHA-256 (`unsigned-sha256:`).
2. The maintainer downloads that APK, signs it on their machine with `apksigner
   sign --alignment-preserved true --v1-signing-enabled false` (the signed APK is
   the unsigned one plus the signature), attaches it to the draft as
   `ffmpeg-gui-<tag>.apk` in place of the unsigned APK and publishes the release.
3. `.github/workflows/verify-release.yml` (release published) runs rbtlog on the
   recipe as it is: it downloads the published APK, rebuilds the tag and compares,
   as IzzyOnDroid's server will.

- It ships **arm64-v8a and x86_64 only**, so `app/build.gradle.kts` limits the
  APK to those ABIs (no 32-bit devices).
- It has no WebP encoder (`libwebp` is not built in): WEBP pictures are
  written by Android from a PNG that FFmpeg makes (see "Image files").
- FFmpegKitNext's classes are Kotlin: from Kotlin, their values are read with
  their getters (`session.getReturnCode()`, `info.getTags()`), not as
  properties.
- An earlier choice, `io.github.jamaismagic.ffmpeg:ffmpeg-kit-main-full-gpl-16kb`,
  cannot be loaded: its `libavdevice.so` references SDL's `PLATFORM_hid_*` and
  OpenSL ES symbols that no library provides (`dlopen` fails, FFmpegKit throws
  "failed to start"). Before changing runtime, check every `.so` for undefined
  symbols that neither its dependencies nor the Android system libraries define.

The version number selects the FFmpeg line (6.1.x = FFmpeg 6.1, 7.1.x, 8.1.x,
9.0.x): the filter graphs here were verified against FFmpeg 6.1 (`scale2ref` in
particular is deprecated in later majors), so moving line means re-running the
command checks. To move: change the `kit` line of the lock, regenerate its
`lib` lines from that release's `scripts/source.sh` (the build script refuses a
lock that disagrees with it), check them, and change `ffmpegKitNext` in
`gradle/libs.versions.toml`.

Only `:media` references it (`FfmpegKitExecutor`, `AndroidMetadataReader`,
`FfmpegFonts`, `FfmpegInfo`). Switching runtime means implementing
`FfmpegExecutor` again and changing one dependency.

## Upgrading the toolchain

The build uses AGP 9.1 / Gradle 9.3.1 / compileSdk 37 (targetSdk 36), which
Compose 1.12 and material3 1.5 require; it opens in Android Studio Panda 2
(2025.3.2) or newer. AGP 9 compiles Kotlin itself, so the modules apply no
`kotlin-android` plugin; `:core` stays a plain `kotlin-jvm` module and `:app`
applies the Compose compiler plugin.

Next steps, which have to move together:

1. Compose 1.13+ needs compileSdk 37.1: raise `compileSdk` and AGP to a
   version that supports it (and the Gradle wrapper to the version that AGP
   requires).
2. Then raise `composeBom`, `lifecycle` (2.11+), `navigationCompose` (2.10+),
   `coreKtx`, `media3`, and `material3` to 1.5.0 stable once it is published.

## Hidden corners

A few things for whoever pokes where they need not. None ever starts by
chance while working, none touches a project or an export, none needs the
network, and each keeps to the vibration setting and to the system's
animations (off: they stand still, or do not show).

- **Life, by FFmpeg** (`settings/AboutLife.kt`, `core/eggs/LifeGame`,
  `media/FfmpegLife`, tested). Seven taps on FFmpeg's version in About, as the
  build number unlocks Android's developer options (from the fourth, the row
  counts down). FFmpeg's own `life` source computes Conway's Game of Life on a
  32 x 20 grid with its edges joined, 48 generations a run as raw grey bytes,
  each run going on from the last grid (written as text for the source's
  `filename`); a game that dies or only blinks is shuffled again. A card under
  the row shows it (cells born grow from a dot into a rounded square, those
  dying shrink away, the badge changing shape every generation), the page's
  background plays it faintly, tiled; a tap on the grid drops a glider, from
  which FFmpeg goes on. The first generations tick softly, then it plays
  silently; leaving the page ends it, and the run's files go at once (and
  with the cache janitor).
- **The DJ's scratch** (`DjScratch.kt`, `core/eggs/ScratchDetector`, tested).
  Dragging the video editor's timeline back and forth quickly and widely
  (four turns within 1.2 seconds, each swing at least 56 dp) turns the play
  button into a vinyl for a moment: it follows the finger, ticks at every
  turn, spins on by itself and springs back into the button once the finger
  has rested. The playhead moves as any scrub moves it; nothing else changes.
- **The infinite zoom** (`EditorTimeline`). Pinching on past the widest zoom
  squeezes the ruler and has it count days, then years, centuries, ice ages,
  eras and eons (a tick for each new span); it springs back to the video's own
  time when the fingers leave.
- **The command studio's small talk** (`command/StudioReply.kt`,
  `core/command/studio/CommandJokes`, tested). A terminal classic typed as the
  whole command (sudo, rm -rf of the root, make me a sandwich, :wq, vim,
  ping, whoami, xyzzy, coffee and a few more) gets a reply in a bubble that
  springs out under the header, read out by TalkBack; such a command can
  never be run (Run is off), and a real command is never taken for one.

## Known limitations (backend)

- Transitions between clips (`xfade` and `acrossfade`, see
  `core/model/Transition.kt` and `transitionJoin` in `CommandBuilder.kt`) need
  the clips re-encoded: stream copy ignores them, as it ignores every filter.
  The transitions told as layers are cross-faded in an HDR video (10 bits)
  and, in the preview, before Android 13.
- `reverse` keeps a whole clip in memory while it is reversed: very long
  reversed clips can exhaust it on a phone.
- Stream copy can only cut and split on key frames, and with several clips it
  needs them to share codec and resolution (concat demuxer).
- The preview switches clips through the player's playlist, so a cut can show
  a short pause that the exported file does not have.
- Stabilization, and the video's sharpen, blur, vignette and film grain are
  implemented and tested in `:core` and have view model setters, but the
  video editor has no controls for them yet (the denoise has its own tool, see
  "Denoise") (the photo editor has its
  own sharpen, blur, vignette and grain, see "Image files"; splitting and tags
  have their panels, see "Audio files").

## Keep Android Open

The home shows, under the tagline, one card for Keep Android Open
(keepandroidopen.org), the campaign against Google's registration of every
Android developer (`KeepAndroidOpen.kt`). Folded, it is one row as small as
the home's other tiles (a little phone whose apps a padlock greys out, then
springs open, three times, then still; the name; one line), so that the app stays the
first thing on the screen. A tap unfolds what the registration means for an
app like this one (its key words in bold), what it means for this one (it
will not register with Google: once the lockdown reaches a country it
installs only through the extra steps Google allows, or on an Android
without Google such as GrapheneOS), and a button to the campaign's
site, opened in the browser (nothing is fetched by the app). Its cross hides
it for good (`AppSettings.keepAndroidOpenBanner`); Settings, Appearance,
"Home" brings it back.

## Links

Every link of the app says where it goes before it is followed: under it,
`LinkDestination` (ui/expressive) writes "Opens <site>: <address>", the
site named as people call it (GitHub, Hugging Face, Zenodo...). A settings
row that opens a page passes it as its `link`; a button has the line under
it. The pages open in the browser (`openLink`): the app itself never goes
online.
