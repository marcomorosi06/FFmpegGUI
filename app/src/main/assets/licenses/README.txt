FFmpeg GUI - licences and notices
=================================

This folder holds the licence of FFmpeg GUI and the licences and copyright
notices of every third-party component inside this app (the APK).

FFmpeg GUI
  Copyright (c) 2026 Marco Morosi.
  Source code: EUPL-1.2. The APK as a whole: GPL-3.0 (EUPL Article 5).
  Files: ffmpeg-gui/NOTICE.txt, ffmpeg-gui/EUPL-1.2.txt

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
smart-exception-java 0.2.1 ..... BSD-3-Clause
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


3. ONNX Runtime (libonnxruntime, libonnxruntime4j_jni, ai.onnxruntime)
-------------------------------------------------------------------------
ONNX Runtime 1.28.0 ............ MIT               onnxruntime/LICENSE.txt
Libraries built into ONNX Runtime ...............  onnxruntime/ThirdPartyNotices.txt


4. Android, Kotlin and Jetpack libraries
-------------------------------------------------------------------------
Kotlin standard library 2.3.21 . Apache-2.0        kotlin/LICENSE.txt, kotlin/NOTICE.txt
  with code derived from GWT (Apache-2.0), ThreeTen backport (BSD-3-Clause),
  Guava (Apache-2.0) and Boost (BSL-1.0): kotlin/third_party/*
kotlinx.coroutines 1.10.2 ...... Apache-2.0        kotlinx-coroutines/LICENSE.txt,
                                                   kotlinx-coroutines/NOTICE.txt
AndroidX and Jetpack Compose ... Apache-2.0        androidx/LICENSE.txt
  activity 1.13.0, annotation, appcompat 1.7.0, arch core, autofill,
  cardview, compose (animation, foundation, runtime, ui) 1.12.1,
  compose material3 1.5.0-beta01, compose material icons 1.7.8,
  coordinatorlayout, core 1.18.0, core-splashscreen 1.2.0, cursoradapter,
  customview, datastore 1.1.7, documentfile, drawerlayout,
  dynamicanimation, emoji2, exifinterface, fragment, graphics-path,
  graphics-shapes 1.0.1, interpolator, legacy-support-core-utils,
  lifecycle 2.10.0, loader, localbroadcastmanager, media, navigation
  2.9.8, navigationevent, print, profileinstaller, recyclerview,
  savedstate, startup, tracing, transition, vectordrawable,
  versionedparcelable, viewpager, viewpager2, window 1.5.0
  (with libandroidx.graphics.path.so and libdatastore_shared_counter.so)
Protocol Buffers (Java lite, inside AndroidX DataStore)
                                 BSD-3-Clause      protobuf/LICENSE.txt
Media3 1.10.1 (ExoPlayer, Session)
                                 Apache-2.0        media3/LICENSE.txt
Guava (used by Media3) ......... Apache-2.0        guava/LICENSE.txt
Material Components for Android 1.12.0
                                 Apache-2.0        material-components/LICENSE.txt
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


Not part of the APK
-------------------------------------------------------------------------
The AI models (Parakeet, IS-Net, U2-NetP, SlimSAM) are not in the app: the
user downloads them, under their own licences. Build tools (Gradle, the
Android Gradle Plugin, R8, the Nix toolchain, GitHub Actions) and test
libraries (JUnit) are used to build or test the app and are not
distributed with it.
