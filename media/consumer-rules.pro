# FFmpegKitNext (com.arthenica.ffmpegkit) is driven through JNI: native code calls back into these classes by
# name, so they must survive shrinking and keep their names.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }
