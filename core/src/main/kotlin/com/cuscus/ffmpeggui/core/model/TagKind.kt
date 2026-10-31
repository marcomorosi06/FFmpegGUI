package com.cuscus.ffmpeggui.core.model

/**
 * What a tag says about the file, for grouping them and for taking away what
 * should not travel with it: [LOCATION] and [DEVICE] are [sensitive] (where it
 * was made, and with which phone or camera, down to its serial number).
 */
enum class TagKind(val sensitive: Boolean) {
    LOCATION(true),
    DEVICE(true),
    DATE(false),
    CAMERA(false),
    OTHER(false),
}

/** What the tag [key] (an FFmpeg key or an EXIF name) says, by its name. */
fun tagKind(key: String): TagKind {
    val k = key.trim().lowercase()
    return when {
        k.startsWith("gps") || k.startsWith("location") || k.contains("iso6709") || k.contains(".location") -> TagKind.LOCATION
        k in DEVICE_KEYS || k.endsWith(".make") || k.endsWith(".model") || k.endsWith(".manufacturer") ||
            k.endsWith(".software") || k.contains("serialnumber") || k.startsWith("lens") || k.contains("cameraowner") -> TagKind.DEVICE
        k.contains("date") || k.contains("time") && !k.contains("exposure") || k == "year" || k.startsWith("offsettime") || k.startsWith("subsec") -> TagKind.DATE
        k in CAMERA_KEYS -> TagKind.CAMERA
        else -> TagKind.OTHER
    }
}

private val DEVICE_KEYS = setOf("make", "model", "software", "imageuniqueid", "hostcomputer", "com.android.version")

private val CAMERA_KEYS = setOf(
    "exposuretime", "fnumber", "photographicsensitivity", "isospeedratings", "focallength", "focallengthin35mmfilm",
    "flash", "whitebalance", "exposureprogram", "exposurebiasvalue", "meteringmode", "aperturevalue",
    "shutterspeedvalue", "brightnessvalue", "maxaperturevalue", "scenecapturetype", "digitalzoomratio",
    "contrast", "saturation", "sharpness", "lightsource", "exposuremode", "subjectdistance", "com.android.capture.fps",
)

/**
 * The EXIF tags of a picture that are worth showing and keeping (by their
 * EXIF names, as the platform's ExifInterface names them): the date, the
 * camera, its settings, the place and a comment. Not those that describe the
 * file itself (its size, its orientation, which the export makes upright, its
 * thumbnail), which the export writes anew.
 */
val EXIF_TAGS: List<String> = listOf(
    // When.
    "DateTime", "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
    "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
    // With what.
    "Make", "Model", "Software", "LensMake", "LensModel", "LensSerialNumber", "BodySerialNumber", "CameraOwnerName",
    "ImageUniqueID",
    // How.
    "ExposureTime", "FNumber", "PhotographicSensitivity", "FocalLength", "FocalLengthIn35mmFilm", "Flash",
    "WhiteBalance", "ExposureProgram", "ExposureBiasValue", "ExposureMode", "MeteringMode", "ApertureValue",
    "ShutterSpeedValue", "BrightnessValue", "MaxApertureValue", "SceneCaptureType", "DigitalZoomRatio",
    "Contrast", "Saturation", "Sharpness", "LightSource", "SubjectDistance",
    // Where.
    "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
    "GPSTimeStamp", "GPSDateStamp", "GPSImgDirection", "GPSImgDirectionRef", "GPSSpeed", "GPSSpeedRef",
    "GPSProcessingMethod", "GPSDestBearing", "GPSDestBearingRef", "GPSMapDatum",
    // Words.
    "UserComment",
)
