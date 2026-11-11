FFmpeg GUI - licences and notices
=================================

This folder holds the licence of FFmpeg GUI and the licences and copyright
notices of every third-party component inside this app (the APK).

FFmpeg GUI
  Copyright (c) 2026 Marco Morosi.
  Source code: EUPL-1.2. The APK as a whole: GPL-3.0 (EUPL Article 5).
  Provided "as is", without any warranty, to the extent permitted by law
  (EUPL Articles 7 and 8, GPL-3.0 sections 15 and 16).
  Files: ffmpeg-gui/NOTICE.txt, ffmpeg-gui/EUPL-1.2.txt
  The video effects (assets/effects/) are part of FFmpeg GUI: every frame
  is drawn by its own scripts/generate-effects.py (the 3D fists of the
  punch transitions and the sound of their blow by scripts/punch3d.py,
  the formulas of the math effect by scripts/math_effect.py with its own
  stroke font, the TV
  break's test card, channel changes and tune by scripts/tv_break.py:
  the music is composed and played by that script; no recording is used
  for any sound),
  under the same licence.
  The splash screen comes from WiFi Audio Streaming for Android
  (https://github.com/marcomorosi06/WiFiAudioStreaming-Android),
  by the same author and under the same licence (EUPL-1.2):
  wifi-audio-streaming/LICENSE.txt (its own copy, as that repository has
  it); see ffmpeg-gui/NOTICE.txt, "CODE BASED ON OTHER PROJECTS".

Each component below keeps its own licence. Paths are relative to this
folder.


1. FFmpeg runtime (libavcodec, libavformat, libavfilter, libavutil,
   libavdevice, libswscale, libswresample, libffmpegkit)
-------------------------------------------------------------------------
FFmpegKitNext 6.1.1 ............ LGPL-3.0, GPL-3.0 with GPL libraries
                                 ffmpeg-kit-next/LICENSE.txt
                                 ffmpeg-libraries/ffmpeg-kit-next-GPL-3.0.txt
FFmpeg n6.1.6 .................. LGPL-2.1-or-later, GPL-2.0-or-later as built
                                 ffmpeg/LICENSE.md, ffmpeg/COPYING.*
smart-exception-java 0.2.1 and smart-exception-common, the same project
                                 BSD-3-Clause
                                 smart-exception/LICENSE.txt

Libraries built into FFmpeg (versions as pinned in
scripts/ffmpeg-kit-next.lock):
x264 (eaa68fa) ................. GPL-2.0-or-later  ffmpeg-libraries/x264.txt
x265 3.6 ....................... GPL-2.0-or-later  ffmpeg-libraries/x265.txt
Xvid 1.3.7 ..................... GPL-2.0-or-later  ffmpeg-libraries/xvidcore.txt
vid.stab 1.1.1 ................. GPL-2.0-or-later  ffmpeg-libraries/libvidstab.txt
LAME 3.100 ..................... LGPL-2.0-or-later ffmpeg-libraries/lame.txt
FriBidi 1.0.13 ................. LGPL-2.1-or-later ffmpeg-libraries/fribidi.txt
libiconv 1.17 .................. LGPL-2.1-or-later ffmpeg-libraries/libiconv-LGPL.txt,
                                                   ffmpeg-libraries/libiconv.txt
libass 0.17.1 .................. ISC               ffmpeg-libraries/libass.txt
dav1d 1.4.2 .................... BSD-2-Clause      ffmpeg-libraries/dav1d.txt
FreeType 2.13.0 ................ FTL               ffmpeg-libraries/freetype.txt
HarfBuzz 8.0.1 ................. MIT (Old MIT)     ffmpeg-libraries/harfbuzz.txt
Fontconfig 2.14.2 .............. MIT-style         ffmpeg-libraries/fontconfig.txt
Opus 1.4 ....................... BSD-3-Clause      ffmpeg-libraries/opus.txt
libvorbis 1.3.7 ................ BSD-3-Clause      ffmpeg-libraries/libvorbis.txt
libogg 1.3.5 ................... BSD-3-Clause      ffmpeg-libraries/libogg.txt
zimg 3.0.5 ..................... WTFPL             ffmpeg-libraries/zimg.txt
Expat 2.5.0 .................... MIT               ffmpeg-libraries/expat.txt
libpng 1.6.40 .................. libpng-2.0        ffmpeg-libraries/libpng.txt
libuuid 1.0.3 .................. BSD-3-Clause      ffmpeg-libraries/libuuid-BSD-3-Clause.txt,
                                                   ffmpeg-libraries/libuuid.txt
cpu_features 0.9.0 ............. Apache-2.0        ffmpeg-libraries/cpu_features.txt


2. Speech recognition (libsherpa-onnx-jni, com.k2fsa.sherpa.onnx)
-------------------------------------------------------------------------
sherpa-onnx 1.13.8 ............. Apache-2.0        sherpa-onnx/LICENSE.txt
Built into libsherpa-onnx-jni.so (versions as pinned by sherpa-onnx 1.13.8):
kaldi-native-fbank 1.22.3 ...... Apache-2.0        sherpa-onnx/kaldi-native-fbank/LICENSE.txt
kaldi-decoder 0.3.0 ............ Apache-2.0        sherpa-onnx/kaldi-decoder/LICENSE.txt
kaldifst 1.8.0 ................. Apache-2.0        sherpa-onnx/kaldifst/LICENSE.txt
OpenFst 1.8.5 .................. Apache-2.0        sherpa-onnx/openfst/COPYING.txt
simple-sentencepiece 0.7 ....... Apache-2.0        sherpa-onnx/simple-sentencepiece/LICENSE.txt
nlohmann/json 3.12.0 ........... MIT               sherpa-onnx/nlohmann-json/LICENSE.txt
eSpeak NG (ed530aa) ............ GPL-3.0-or-later, with parts under
                                 Apache-2.0, BSD-2-Clause and the Unicode
                                 licence           sherpa-onnx/espeak-ng/COPYING*.txt
piper-phonemize (f3ff95a) ...... MIT               sherpa-onnx/piper-phonemize/LICENSE.txt
hclust-cpp 2026-02-25 .......... BSD-2-Clause      sherpa-onnx/hclust-cpp/LICENSE.txt
Eigen (header-only) ............ MPL-2.0, with parts under BSD, LGPL and
                                 MINPACK licences  sherpa-onnx/eigen/COPYING*.txt

Modified by FFmpeg GUI: the app packs a copy of the sherpa-onnx 1.13.8
release AAR in which libsherpa-onnx-jni.so has only the version indexes
(.gnu.version) of the symbols it takes from libonnxruntime.so set to
"global", so that it runs on ONNX Runtime 1.28.0 (section 3), and from
which libonnxruntime.so (1.28.2), libsherpa-onnx-c-api.so and
libsherpa-onnx-cxx-api.so are removed. Changed by Marco Morosi, 2026, as
Apache-2.0 section 4(b) and GPL-3.0 section 5(a) ask: details in
ffmpeg-gui/NOTICE.txt ("MODIFIED THIRD-PARTY FILES").


3. ONNX Runtime (libonnxruntime, libonnxruntime4j_jni, ai.onnxruntime)
-------------------------------------------------------------------------
ONNX Runtime 1.28.0 ............ MIT               onnxruntime/LICENSE.txt
Libraries built into ONNX Runtime ...............  onnxruntime/ThirdPartyNotices.txt


4. Android, Kotlin and Jetpack libraries
-------------------------------------------------------------------------
Kotlin standard library 2.3.21, with the kotlin-parcelize and
  kotlin-android-extensions runtimes, parts of Kotlin
                                 Apache-2.0        kotlin/LICENSE.txt, kotlin/NOTICE.txt
  with code derived from GWT (Apache-2.0), ThreeTen backport (BSD-3-Clause),
  Guava (Apache-2.0) and Boost (BSL-1.0): kotlin/third_party/*
kotlinx.coroutines 1.10.2 ...... Apache-2.0        kotlinx-coroutines/LICENSE.txt,
                                                   kotlinx-coroutines/NOTICE.txt
kotlinx.serialization (core) 1.7.3, Copyright 2017-2024 JetBrains s.r.o.
  and contributors (used by AndroidX savedstate and lifecycle)
                                 Apache-2.0        kotlinx-serialization/LICENSE.txt
Okio 3.4.0, Copyright 2013 Square, Inc. (used by AndroidX DataStore)
                                 Apache-2.0        okio/LICENSE.txt
JetBrains annotations 23.0.0 ... Apache-2.0        androidx/LICENSE.txt (the same
                                                   Apache-2.0 text)
JSpecify annotations 1.0.0 ..... Apache-2.0        androidx/LICENSE.txt (the same
                                                   Apache-2.0 text)
Error Prone annotations 2.15.0 (used by Guava)
                                 Apache-2.0        guava/LICENSE.txt (the same
                                                   Apache-2.0 text)
  The three annotation libraries hold no code the app runs; R8 may leave
  them out of the APK. They are listed all the same.
AndroidX and Jetpack Compose ... Apache-2.0        androidx/LICENSE.txt
  activity 1.13.0, annotation, appcompat 1.7.0, arch core, autofill,
  cardview, collection, compose (animation, foundation, runtime, ui)
  1.12.1, compose material3 1.5.0-beta01, compose material icons 1.7.8,
  concurrent-futures, constraintlayout 2.0.1 (with constraintlayout-solver),
  coordinatorlayout, core 1.18.0, core-splashscreen 1.2.0, core-viewtree,
  cursoradapter, customview, datastore 1.1.7, documentfile, drawerlayout,
  dynamicanimation, emoji2, exifinterface, fragment, graphics-path,
  graphics-shapes 1.0.1, interpolator, legacy-support-core-utils,
  lifecycle 2.10.0, loader, localbroadcastmanager, media, navigation
  2.9.8, navigationevent, print, profileinstaller, recyclerview,
  resourceinspection-annotation, savedstate, startup, tracing,
  transition, vectordrawable, versionedparcelable, viewpager, viewpager2,
  window 1.5.0
  (with libandroidx.graphics.path.so and libdatastore_shared_counter.so)
Protocol Buffers (Java lite, inside AndroidX DataStore)
                                 BSD-3-Clause      protobuf/LICENSE.txt
Media3 1.10.1 (ExoPlayer, Session)
                                 Apache-2.0        media3/LICENSE.txt
Guava 33.3.1-android (used by Media3), with failureaccess and
  listenablefuture, parts of Guava
                                 Apache-2.0        guava/LICENSE.txt
Material Components for Android 1.12.0
                                 Apache-2.0        material-components/LICENSE.txt
AndroidSVG 1.4 (com.caverock:androidsvg-aar), Copyright 2013-2018 Cave Rock
  Software Ltd: opens SVG files and draws parts of drawings in the SVG editor
                                 Apache-2.0        androidsvg/LICENSE.txt
LLVM libc++ (libc++_shared.so, Android NDK)
                                 Apache-2.0 WITH LLVM-exception
                                                   llvm-libcxx/LICENSE.txt


5. Other content
-------------------------------------------------------------------------
Google Sans Flex (title font) .. OFL-1.1           GoogleSansFlex-OFL.txt
Parakeet TDT 0.6B v3 token list, by NVIDIA
                                 CC-BY-4.0         parakeet/ATTRIBUTION.txt,
                                                   parakeet/CC-BY-4.0.txt
anti-vocale (credited for the on-device transcription approach)
                                 Apache-2.0        anti-vocale/LICENSE.txt
IPTC Digital Source Type, two identifiers (the AI marks of exported files)
                                 CC-BY-4.0         iptc/ATTRIBUTION.txt,
                                                   parakeet/CC-BY-4.0.txt


Not part of the APK
-------------------------------------------------------------------------
The AI models are not in the app: the user downloads them, under their
own licences. The AI marks come first (imported before the models of the
dub and of the magic eraser, which need them; the others do not):
  AudioSeal, by Meta ....................... MIT        (ONNX by FFmpeg GUI, from
                                                       Meta's weights)
                                                       ai-markers/LICENSE-audioseal.txt
  TrustMark Q, by Adobe .................... MIT        (Adobe's ONNX files,
                                                       unchanged; T. Bui,
                                                       S. Agarwal, J. Collomosse,
                                                       ICCV 2025)
                                                       ai-markers/LICENSE-trustmark.txt
                                                       ai-markers/NOTICE.txt
  Parakeet TDT 0.6B v3, by NVIDIA ......... CC-BY-4.0  (int8 ONNX by sherpa-onnx,
                                                       served unchanged by
                                                       FFmpeg GUI's release
                                                       parakeet-onnx-1, with
                                                       its NOTICE)
                                                       parakeet/CC-BY-4.0.txt
  IS-Net and U2-NetP, by Xuebin Qin et al. . Apache-2.0 (ONNX by rembg, MIT)
  BiRefNet-lite, by Zheng Peng et al. ...... MIT        (ONNX by FFmpeg GUI, from
                                                       the author's weights)
                                                       birefnet/LICENSE.txt,
                                                       birefnet/NOTICE.txt
  EdgeTAM, by Meta ......................... Apache-2.0 (ONNX by FFmpeg GUI, from
                                                       Meta's checkpoint; the
                                                       changes in the notice)
                                                       edgetam/LICENSE.txt,
                                                       edgetam/NOTICE.txt
  Opus-MT, by the University of Helsinki ... CC-BY-4.0  (ONNX by Xenova)
  Bandit v2, by Karn N. Watcharasupat,
  Chih-Wei Wu and Iroro Orife .............. CC-BY-SA-4.0 (the weights; the
                                                       code Apache-2.0; ONNX by
                                                       FFmpeg GUI, from the
                                                       authors' checkpoint, under
                                                       CC-BY-SA-4.0 too)
                                                       bandit/LICENSE.txt,
                                                       bandit/NOTICE.txt
  Spleeter 5 stems, by Deezer .............. MIT        (ONNX by FFmpeg GUI, from
                                                       Deezer's checkpoint)
                                                       spleeter/LICENSE.txt,
                                                       spleeter/NOTICE.txt
  Kokoro-82M v1.0, by hexgrad .............. Apache-2.0 (multi-language build
                                                       by sherpa-onnx, Apache-2.0, with
                                                       eSpeak NG's data, GPL-3.0, and
                                                       jieba's dictionaries, MIT)
                                                       kokoro/LICENSE.txt,
                                                       sherpa-onnx/espeak-ng/
  MI-GAN, by Picsart AI Research ........... MIT        magic-eraser/README.txt
  LaMa, by Samsung AI Center ............... Apache-2.0 (ONNX by Carve)
                                                       magic-eraser/README.txt
  Piper voices (VITS), imported by the user  each its own licence (its
                                                       MODEL_CARD); Piper, MIT
                                                       piper/LICENSE.txt
BiRefNet-lite, EdgeTAM, Bandit v2 and Spleeter 5 stems are converted to
ONNX by this project (scripts/export-birefnet.py, scripts/export-edgetam.py,
scripts/export-bandit.py, scripts/export-spleeter.py) and served from its
own GitHub releases, with their licence, the notice of what the conversion
changed and their provenance next to the files and inside them; their
licences and notices are kept here too, as they are distributed by the same
hands as the app. SlimSAM (Apache-2.0, ONNX by Xenova), which corrected
cut-outs before EdgeTAM, and Spleeter 2stems (MIT, ONNX and int8 by
sherpa-onnx, Apache-2.0), which set voices apart before Bandit v2, are no
longer used: the app deletes them from the phone.
The record of every model (licences, sources, SHA-256, training data) is
docs/MODELS.md in the source.
The app reads their files with its own code (SentencePiece models and
vocabularies of Opus-MT included, and the spectrograms and masks of Bandit
and Spleeter, written in Kotlin from what their papers and code describe);
nothing of theirs is copied into it. The photos and the video in the store screenshots (fastlane/) come from
Pexels, under the Pexels licence: see fastlane/metadata/android/en-US/
images/CREDITS.txt in the source. Build tools (Gradle, the
Android Gradle Plugin, R8, the Nix toolchain, GitHub Actions) and test
libraries (JUnit) are used to build or test the app and are not
distributed with it.
