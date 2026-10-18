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
and reversal before they are burnt in.

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

The drawing (`ImageEdit.strokes`: free strokes, lines, arrows, boxes, ovals,
eraser) is drawn by `StrokePainter` (`:media`) on the screen and, at export,
into a transparent PNG (`DrawingRenderer`) that the export lays over the
picture like a sticker, the whole picture wide.

The bundled FFmpeg has no WebP encoder: for WEBP the engine has FFmpeg write a
PNG in its working directory and `ImageRecoder` (`AndroidImageRecoder`,
`Bitmap.compress`) writes the WebP from it (lossless at quality 100 on Android
11 and later). JPG uses `mjpeg` (full-range 4:4:4), PNG, BMP and TIFF (deflate)
their native encoders.

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
Where each one acts:

- theme, wallpaper colours, pure black: `FFmpegGUITheme` and the editors'
  `EditorTheme` (`ui/theme/Theme.kt`: `appColorScheme`, `onBlack`). Without
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
  The photo's steps run on rgb24, so a transparent PNG opened as a photo is
  opaque from the start, in the export and in the editor alike
  (`Raster.opaque()`, `keepAlpha = false`); the stickers laid on it keep their
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
generation for every mode, command-line quoting, the manual command resolver,
output naming, duration math, progress parsing, the timeline operations, the
preview math, subtitle re-timing, the project files (`ProjectJsonTest`: every
field of every model, filled with random values, must come back from the
JSON text) and the whole engine workflow with fake ports.

## Storage

Nothing is kept in the cache longer than needed:

- sources are copied into `cache/staging/<session>` for each conversion and the
  directory is deleted when the run ends, whatever the outcome
  (`CacheSourceStager` + `ConversionEngine`);
- camera recordings go to `recordings/`, and stills (pictures, freeze frames)
  and covers of audio files to `stills/` in the cache (`CacheJanitor`); they are deleted as soon as their
  project is closed: another file is opened, the recording is cancelled, or the
  app is left for good (`ConversionViewModel`). Files only the undo history still
  refers to go with the project too;
- at start-up `FfmpegGuiApp` sweeps what a killed process or an older version
  left behind (orphaned working copies, old recordings, the time-stamped copies
  earlier versions wrote straight into the cache).

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
it is needed and lets it go after two minutes unused; the speed test listens
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
diamonds on the timeline; a tap on one brings the playhead there. The preview
animates the texts and pictures (composed at their values on the clock), the
clip's framing (a layer scaled and moved while drawing), its colours and the
volumes (`ClipLevels.gainAt`, `PreviewSound.volumeAt`).

## Subject cut-out

A key of `KeyMode.SUBJECT` cuts around the subject of a still picture instead of
a colour: the main picture of the picture editor, its stickers, and pictures laid
over the video (never a video, whose subject moves). Its `mask` is a PNG as large
as the picture, white, whose alpha is how much of each pixel is the subject.

**The model.** IS-Net (general use) of the DIS project (Apache 2.0), as converted
to ONNX by rembg (MIT). Like the speech model it is downloaded by the browser
(the app has no network access) and imported in Settings > Smart features:
`cutout/CutoutModelStore` copies the one file, hashes it on the way and keeps it
only when it matches the pinned SHA-256 (`CutoutModel`); it reports with the
speech model's states, so `SmartSettings` shows both with the same card.
`cutout/CutoutEngine` runs it through ONNX Runtime's Java API (loaded when
needed, let go of after two idle minutes); `core/cutout/SubjectModelIo` is the
input and output as rembg has them (1024 x 1024, divided by the largest value
and moved by -0.5, channels first; the output stretched to 0..255).
`cutout/SubjectCutter` squeezes the picture (decoded as FFmpeg and the preview
read it, EXIF turn ignored, at most 4096 pixels), runs the model, stretches the
mask back to the picture's size and keeps it in `files/cutouts`, one per picture.

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
2. `scripts/sign-release.sh`, on the maintainer's machine, downloads that APK and
   checks its SHA-256, signs it with `apksigner sign --alignment-preserved true
   --v1-signing-enabled false` (the signed APK is the unsigned one plus the
   signature), checks the signature as rbtlog does (`apksigcopier copy` onto the
   unsigned APK must give the signed APK back, byte for byte), then replaces the
   unsigned APK with `ffmpeg-gui-<tag>.apk` and publishes the release.
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

## Known limitations (backend)

- There are no transitions between clips (cross-fades); a fade to black at
  the end of one clip and from black at the start of the next is the closest.
- `reverse` keeps a whole clip in memory while it is reversed: very long
  reversed clips can exhaust it on a phone.
- Stream copy can only cut and split on key frames, and with several clips it
  needs them to share codec and resolution (concat demuxer).
- The preview switches clips through the player's playlist, so a cut can show
  a short pause that the exported file does not have.
- Stabilization, denoise, sharpen, blur, vignette, film grain, splitting and
  metadata tags are implemented and tested in `:core` and have view model
  setters, but the editor has no controls for them yet.
