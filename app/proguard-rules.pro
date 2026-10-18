# App-specific R8 rules.
#
# Nothing is needed here at the moment:
#  - the FFmpegKitNext keep rules ship with the :media module (media/consumer-rules.pro);
#  - AndroidX, Media3 and kotlinx.coroutines bring their own consumer rules.

# Keep line numbers so release stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# sherpa-onnx (speech recognition for the smart subtitles): its native code reads the
# fields of the configuration classes and builds the result objects through JNI, by
# name. Shrunk or renamed, they would crash the app inside the native library, and
# only in release builds.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ONNX Runtime (the subject cut-out model): its native code creates the tensors and
# results and reads the options through JNI, by name.
-keep class ai.onnxruntime.** { *; }
