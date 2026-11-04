# Features

Everything FFmpeg GUI does, in detail. The [README](../README.md) has the short version.

## Video
- A timeline of clips from one or more files: split, trim by dragging, reorder, duplicate, freeze frames, pictures as clips with Ken Burns motion
- Speed (with automatic `atempo` chaining) or a **speed curve** drawn along a clip, with ready-made ramps (montage, hero, bullet, flash in / out...), the pitch kept or following the speed like a tape; reverse, crop and aspect ratio, rotation, mirror, resolution and frame rate
- **Transitions** between clips, as in CapCut: 101 of them in twelve families (basic, camera, motion, slide, wipe, light, glitch, blur, shapes, masks, 3D, social): dissolves and dips, zooms, pulls, whip pans, spins, rolls, shake, impact shake and swirl, pendulums, bounce, rebounds and spring, slides, covers, reveals, splits and squeezes, straight, soft, corner and diagonal wipes, white flash, light leak, film burn, glow and lens flare, glitch, RGB split, wave, TV static and pixelate, zoom, motion and spin blur and defocus, diamond, hexagon, star, stripes, checkerboard, paper tear and brush stroke, doors and shutters, iris, diagonal split and luma fade, two 3D punches (a fist punches the clip away, or into the picture so that it shakes; the fist can be made smaller), a "technical difficulties" TV break (the channel changes, a test card plays its own looping tune for as long as you set, up to 15 s, the channel changes again), cube, flip and page turn, swipes and hearts. Each has its own length, up to half of the shorter clip; every choice plays in a loop on its tile and can be played in the preview, and on the timeline the clips' edges lean across each join as far as its transition lasts. The preview draws them live (shaders written from the same description as the export, Android 13 and later); the export uses `xfade` where FFmpeg has the transition and FFmpeg's own filters for the others (`perspective`, `remap`, `gblur`, `dblur`, `chromashift`, masks worked out small), with `acrossfade` for the sound. The punches' fist is rendered in 3D (ray-marched, with soft shadows, skin shading and motion blur) by the project's own script and laid over the clips with its own transparency
- Colour, fades and **keyframes**: clips zoom, pan and change colour and volume; texts and pictures move, grow, turn and fade; each keyframe moves on to the next with an easing (linear, smooth, ease in / out, overshoot, bounce, elastic, hold) or a curve drawn by hand, and is dragged along the timeline to another moment
- **Texts** and **pictures or videos laid over the video**, each on its own row of the timeline: crop, rounded or round shape, outline, shadow or glow, blur, layers and ready-made animations in and out; texts with an outline, a shadow or a box behind them, in the phone's fonts or a .ttf / .otf of your own
- **Shapes** laid over the video, **blend modes** for pictures, videos and shapes, and a **layers** list to reorder them by dragging, hide them and lock them in place
- **Effects** on their own lane, as in CapCut: sparks, fireworks, flames, smoke, snow, rain, confetti, dust in the light, bokeh, hearts and math (formulas and diagrams written on in light, as in the confused-maths meme), looping for as long as they last, with their strength and colour; and **magic transitions**: a fairy's wand, an explosion, a flash, the cut hidden under them. All drawn by the project's own script, light on black, screened over the video. And 14 **looks** that change the picture itself while they last, in a seamless loop: black and white, old film, VHS, glitch, TV static, prism, shake, pulse, rainbow, strobe, pixelate, invert, mirror and glow, each with its strength, intensity and speed (FFmpeg's own filters in the export, a shader in the preview)
- **Chroma key and colour key** on clips, the whole video and overlays, with spill removal and transparency
- **Denoise**, in the **Details** tool, on a clip or the whole video (`hqdn3d`, across each frame and from frame to frame), in four levels or any strength in between. A comparison shows it close up, at the footage's own pixels: a part of the frame under the playhead, before and after either side of a divider, made by FFmpeg with the export's own filter; tap the whole frame to choose the part, or touch and hold to drag it. A small badge on the preview, and a mark on the clip in the timeline, say where a denoise runs, since the preview cannot show it
- **Sounds** on their own rows, with their real waveform: volume, speed (keeping the pitch or not), pitch shift, fades, equaliser, repeat
- Burned-in subtitles (`.srt`, `.ass`), undo and redo of every edit
- HDR videos (HDR10, HLG) are tone mapped to SDR for the export (`zscale`, `tonemap`), so that their colours come out right instead of washed out
- **HDR look for SDR videos**, as for photos: the light parts shine brighter than white, saved as a real HDR video (HLG, BT.2020, HEVC Main 10, through a 3D LUT with `lut3d`). The preview tints the zones it brightens, live; on an HDR screen the frame under the playhead shows as it will be

## Smart features, on the phone
- **Subtitles from speech**: the video is listened to on the phone (25 European languages) and the words appear on the preview as they are found. Presets, colours, outline or box, the word being said lit up, lines to correct, export as `.srt`
- **Transcripts**: any audio or video file written down as `.txt` or `.srt`, read beside the player and corrected line by line
- **Subject cut-out** without a green screen, on photos, stickers and pictures laid over the video (the **Cut out** tool, which also does the chroma key); FFmpeg merges the mask in (`movie`, `alphamerge`). Three models to choose from: fast, precise, or the finest edges (BiRefNet-lite: hair, fur and thin parts come out whole)
- **Lift the subject**: in the photo editor, a long press on a subject lifts it off the picture as a sticker of its own, laid exactly where it was, its edges brought to the picture's own (EdgeTAM finds it from the one point); keep the finger down until it is done, let go earlier to call it off
- **Your stickers**: what is cut out (by the Cut out tool, corrected with the brushes or not) or lifted is saved with one tap into the app's own sticker gallery; the Sticker and Overlay buttons open it first, with a large button to take a file from the phone instead. A copy can also go to Pictures/FFmpegGUI/Stickers (Settings, off by default)
- **Subtitles translated** into other languages on the phone with Opus-MT, each line keeping its time: a `.srt` or `.ass` file becomes a new file in the new language, spoken subtitles get the new words. The language is found by itself; between two languages with no model of their own the way goes through English. Any language pair can be added
- **Dubbing in the subtitles' language**: a natural voice (Kokoro, 54 voices in English, Italian, Spanish, French, Portuguese, Hindi and Chinese, each one heard first) says every line of the subtitles where it is shown, a little faster where a line would not fit, and the dub becomes a sound of its own on the timeline. The video's own voice can be kept, lowered or taken out (it is set apart from the music first). With the translation, a video in one language is dubbed into another on the phone, in one tap with the editor's **Dub** tool: it listens to the video, translates it, sets the original voice apart (taken out or lowered) and lays the dub, each step shown as it goes and undone on its own. Besides Kokoro, Piper voices are picked from a list of 125 in 34 languages, the best first, downloaded in the browser and imported (any other one too: a sherpa-onnx `vits-piper` archive, or Piper's `.onnx` and `.onnx.json`, one trained at home too): many times quicker on a phone. Each line is said in one breath (no seams between its sentences), a voice whose licence forbids commercial use (or that is for research only, or whose licence is not known) is marked and asked about before every dub, and every dub, and every file exported with one, is marked as AI-generated in its tags and its XMP (AI Act, Article 50)
- **Voice and the rest apart**: the voice of a video or a song is set apart from the music and the noise on the phone (Spleeter), then each one gets its own level: the voice alone, no voice (karaoke), the voice up front for dialogue, the voice lower, or any mix. In the editor per clip, heard live in the preview. A file is done once, for every project; the export mixes the two with FFmpeg (`pan`)
- **Magic eraser**, in the picture editor and on pictures laid over the video: paint over what should go (the magic brush finds the whole of it, or start from the subject the cut-out finds) and it is filled in with what should be behind it, by MI-GAN (quick) or LaMa (precise), on the phone; what is exported from an erased picture says, inside the file, that a model changed it (AI Act, Article 50)
- **Cut-out corrected with two brushes**, as in a magic editor: green keeps, red removes. With the magic tool a tap or a rough stroke is enough: EdgeTAM, a Segment Anything 2 made for phones, finds the whole thing, only what the stroke touches is taken and its edge is brought to the picture's own edges (guided filter). The precise brush changes exactly what it covers; two fingers zoom in for the fine parts; undo and redo stroke by stroke

The models are not in the app and are never downloaded by it: the browser downloads them once, the app imports them and checks every file against its pinned SHA-256 (Settings > Smart features); a translation pair, which can be any Opus-MT model, has its files' SHA-256 taken when it is imported and checked again on request. Two or three models are offered for most features (each one, with its licence, source, hashes and training data: [MODELS.md](MODELS.md)):

| Feature | Model | Notes |
|---|---|---|
| Speech | Parakeet TDT 0.6B v3, int8 (sherpa-onnx) | Lighter |
| Speech | Parakeet TDT 0.6B v3, SmoothQuant | Best quality, a little heavier |
| Cut-out | U2-NetP | Fast, about 5 MB |
| Cut-out | IS-Net (general use) | Precise, about 170 MB |
| Cut-out | BiRefNet-lite (general use), ONNX by this project | Finest edges, about 170 MB; about 2.5 GB of memory while it works |
| Cut-out correction, lift the subject | EdgeTAM, ONNX by this project | Optional, about 37 MB |
| Translation | Opus-MT, one per language pair (ONNX by Xenova) | Any number of pairs, about 100 MB each |
| Voice separation | Spleeter 2stems, int8 (ONNX by sherpa-onnx) | One archive from GitHub, about 50 MB |
| Dubbing | Kokoro-82M v1.0, float32, multi-language (sherpa-onnx) | One archive from GitHub, about 350 MB (400 MB unpacked) |
| Dubbing, quick | Any Piper voice (VITS) | One archive or two files each, 20 to 120 MB |
| Magic eraser | MI-GAN (pipeline ONNX) | Quick, about 28 MB |
| Magic eraser | LaMa big-lama (ONNX by Carve) | Precise, about 210 MB |

Every model runs on the phone's processor, with ONNX Runtime's XNNPACK kernels where they help (one switch for all; the phone's GPU and NPU cannot be used by ONNX Runtime on Android for these networks).

Only one model is ever in memory, and only one works at a time: each is loaded for its work and let go of as soon as that is done (the correcting brushes keep theirs until the dialog closes), so their memory never adds up.

## Audio
- A dedicated editor with a large waveform of the exported sound, heard live as you edit
- Ten-band equaliser with a draggable curve and presets; low and high cut, noise gate, compressor, limiter; echo and reverb; chorus, flanger, phaser, tremolo, vibrato; bit crushing; stereo width, balance, mono
- Loudness levelling (`loudnorm`), silence trimming, splitting into parts
- Tags and cover art, written into MP3, M4A, FLAC, OGG and OPUS
- Bitrate, bit depth and sample rate per format

## Pictures
- A photo editor for any picture or any frame of a video. The exact frame is chosen first on a screen of its own: a strip of the video's frames, a wheel that ticks at every frame and spins on when thrown, one or ten frames at a time, two fingers to look closely
- **Every frame in a ZIP**: at the file's own rate or fewer a second, over the whole clip or a part, in the picture format chosen
- Crop with locked shapes, straighten, perspective
- Exposure, brightness, contrast, highlights, shadows, warmth, tint, hue and more
- Twelve filters with intensity and effects (blur, pixels, glitch, duotone, sketch...)
- Texts, stickers, drawing and borders
- **Layers**: every text, sticker and shape in a list, dragged into their order, each one shown or hidden (a hidden layer is kept but not exported) and locked so that it cannot be picked or moved on the picture by mistake; in the video editor too
- **Shapes** (rectangle, ellipse, star, heart, Material's expressive shapes...) with solid or gradient fill and outline, and **blend modes** (multiply, screen, overlay, difference...) for stickers and shapes
- Background removal by colour or by subject, kept transparent in PNG, WEBP and TIFF
- **SVG editor**: shapes, texts, curves drawn with the pen, lines point by point (curves, zig-zags, waves), connectors that follow the shapes, a paint bucket, snapping and layers; fills and outlines in **gradients** (linear or radial, at any angle, with as many stops as you like, each with its own opacity); several picked at once to **combine** (union, subtract, intersect, exclude, divide, as in Blender or Illustrator), line up, space out, mirror, group, make into paths, a stroke into a shape or a text into letters, and to place by numbers. **Live symmetry** (left and right, four quarters, 2 to 24 turns, kaleidoscope): draw on one side and it shows on all of them; a grid to snap to; repeat in a row, a grid or round the centre; mirrored copies; simplify, smooth, reverse, break apart and join paths; lock; align on the page. Any picture can be traced into one, its shaded shapes filled with gradients if you like
- **HDR look, saved as an Ultra HDR JPG** (the picture plus a gain map, readable by every viewer, brighter on HDR screens): worked out of the picture's light or, for an Ultra HDR photo, the camera's own, with its intensity, everywhere, on the subject, on the background or where it is painted. On an HDR screen (Android 14 and later) the editor shows it as it will be; on any screen it can tint the zones it brightens. Texts, stickers, the drawing and the border are left as they are

## GIF
- Its own editor and settings: width, frame rate, colours, dithering, loops, lighter file
- The preview plays the GIF as it will be, colours and dithering included

## Around the editors
- Opens or receives (Share) any supported video, audio or picture from other apps
- Recent projects, kept only on the phone
- Export progress in a notification, a Live Update on Android 16, with time left and cancel
- Background playback of the preview, with media notification and lock-screen controls
- A welcome tour that sets theme, colours, touch feedback and exports on the first launch
