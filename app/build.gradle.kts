import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The sherpa-onnx release the smart subtitles run on (see "sherpa-onnx" below).
val SHERPA_ONNX_VERSION = "1.13.8"
val SHERPA_ONNX_AAR = "libs/sherpa-onnx-$SHERPA_ONNX_VERSION.aar"
val SHERPA_ONNX_SHA256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
// The same AAR as the app packs it (see "sherpa-onnx" below): made from the pinned one.
val SHERPA_ONNX_APP_REVISION = "$SHERPA_ONNX_VERSION-app"
val SHERPA_ONNX_APP_AAR = "libs/sherpa-onnx-$SHERPA_ONNX_APP_REVISION.aar"

android {
    namespace = "com.cuscus.ffmpeggui"
    // Compose 1.12 and material3 1.5 are built against API 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cuscus.ffmpeggui"
        // Android 10: the shared folders take the app's own files through MediaStore without
        // any storage permission, and storage is scoped for every app (see the manifest).
        minSdk = 29
        // The latest Android's sandbox and behaviour, the one the app is built against.
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-alpha"

        // The FFmpeg runtime ships 64-bit libraries only. Without this filter a 32-bit
        // device could install the app and then fail to load FFmpeg.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // One libonnxruntime.so only, that of ONNX Runtime's Java API: the AAR of sherpa-onnx
    // the app packs leaves its own out (see "sherpa-onnx" below). A second copy coming
    // back from any dependency would fail the build here rather than at run time.
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":media"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // LifecycleResumeEffect (the welcome reads the notification permission again on resume).
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // The settings.
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Editor preview player.
    implementation(libs.androidx.media3.exoplayer)
    // Its media session and notification, which keep it playing in the background.
    implementation(libs.androidx.media3.session)

    implementation(libs.google.material)

    // Speech recognition for the smart subtitles: sherpa-onnx (Apache 2.0) with ONNX
    // Runtime (MIT), the release AAR of k2-fsa/sherpa-onnx, pinned by its SHA-256 (see
    // SHERPA_ONNX_AAR below and scripts/fetch-sherpa-onnx.sh). It asks for no permission.
    // The subject cut-out model runs on ONNX Runtime (MIT) through its Java API. It asks
    // for no permission.
    implementation(libs.onnxruntime.android)

    // Served from app/libs by the "appLibs" repository of settings.gradle.kts: the copy
    // made from the pinned release (see "sherpa-onnx" below).
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:$SHERPA_ONNX_APP_REVISION@aar")
}

// ---------------------------------------------------------------------------
// sherpa-onnx
//
// Not on Maven Central: the AAR of its GitHub release is kept in app/libs. The
// build checks that it is there and that it is the very file the app was
// written against, before anything is compiled.
//
// ONNX Runtime comes twice: inside that AAR (a 1.28.2 build) and with its Java
// API from Maven Central (1.28.0, for the subject cut-out). A process can load
// only one libonnxruntime.so, and each build exports its functions under its
// own symbol version (VERS_1.28.2, VERS_1.28.0): sherpa-onnx's JNI library asks
// for OrtGetApiBase@VERS_1.28.2, which the Maven copy does not have, so loading
// the speech engine failed ("cannot locate symbol OrtGetApiBase").
//
// So the app packs a copy of the AAR made from the pinned one, the same but for
// its native part: without its libonnxruntime.so (and the C / C++ API libraries,
// which the app does not use), and with libsherpa-onnx-jni.so taking ONNX
// Runtime's functions under any version (their version index set to "global"
// in .gnu.version: the only change to the file). Both builds have the C API
// version 28 that sherpa-onnx asks of OrtGetApiBase, so it runs on the Maven one.
// The copy is made when the project is configured (it must exist before the
// dependencies are resolved) and only when it is missing or older than the AAR.
// ---------------------------------------------------------------------------

/**
 * In the ELF file [elf] (a shared library), makes every symbol it takes from
 * [library] with a version taken without one, so that any build of [library]
 * serves it. Returns how many symbols changed.
 */
fun unversionImportsFrom(elf: ByteArray, library: String): Int {
    require(elf.size > 0x40 && elf[0] == 0x7f.toByte() && elf[1] == 'E'.code.toByte() && elf[2] == 'L'.code.toByte() && elf[3] == 'F'.code.toByte()) { "not an ELF file" }
    require(elf[5].toInt() == 1) { "not a little-endian ELF file" }
    val is64 = elf[4].toInt() == 2
    val b = ByteBuffer.wrap(elf).order(ByteOrder.LITTLE_ENDIAN)
    fun u16(at: Long) = b.getShort(at.toInt()).toInt() and 0xffff
    fun u32(at: Long) = b.getInt(at.toInt()).toLong() and 0xffffffffL
    fun word(at: Long) = if (is64) b.getLong(at.toInt()) else u32(at)
    fun cString(at: Long): String {
        var end = at.toInt()
        while (elf[end] != 0.toByte()) end++
        return String(elf, at.toInt(), end - at.toInt(), Charsets.US_ASCII)
    }
    class Section(val type: Long, val offset: Long, val size: Long, val link: Int)
    val shoff = word(if (is64) 0x28 else 0x20)
    val shentsize = u16(if (is64) 0x3A else 0x2E)
    val shnum = u16(if (is64) 0x3C else 0x30)
    val sections = (0 until shnum).map { i ->
        val at = shoff + i.toLong() * shentsize
        if (is64) {
            Section(u32(at + 4), word(at + 0x18), word(at + 0x20), u32(at + 0x28).toInt())
        } else {
            Section(u32(at + 4), u32(at + 0x10), u32(at + 0x14), u32(at + 0x18).toInt())
        }
    }
    val dynsym = sections.firstOrNull { it.type == 11L } ?: return 0
    val versym = sections.firstOrNull { it.type == 0x6fffffffL } ?: return 0
    val verneed = sections.firstOrNull { it.type == 0x6ffffffeL } ?: return 0
    val strings = sections[verneed.link].offset
    // The version indexes the file asks of [library].
    val wanted = mutableSetOf<Int>()
    var need = verneed.offset
    while (true) {
        val count = u16(need + 2)
        val file = cString(strings + u32(need + 4))
        var aux = need + u32(need + 8)
        repeat(count) {
            if (file == library) wanted += u16(aux + 6)
            aux += u32(aux + 12)
        }
        val next = u32(need + 12)
        if (next == 0L) break
        need += next
    }
    if (wanted.isEmpty()) return 0
    // Each symbol taken from it with one of those versions: any version will do.
    val entry = if (is64) 24 else 16
    val shndxAt = if (is64) 6 else 14
    var changed = 0
    for (i in 0 until (dynsym.size / entry).toInt()) {
        val at = versym.offset + 2L * i
        val version = u16(at) and 0x7fff
        val undefined = u16(dynsym.offset + i.toLong() * entry + shndxAt) == 0
        if (undefined && version in wanted) {
            b.putShort(at.toInt(), 1)
            changed++
        }
    }
    return changed
}

/** The SHA-256 of [file], in hex. */
fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

/** Makes [target], the AAR the app packs, from [source], the pinned release (see above). */
fun prepareSherpaOnnx(source: File, target: File) {
    // Missing or not the pinned release: verifySherpaOnnx says so, before anything is built.
    if (!source.isFile) return
    if (target.isFile && target.lastModified() >= source.lastModified()) return
    if (sha256Of(source) != SHERPA_ONNX_SHA256) return
    val dropped = listOf("/libonnxruntime.so", "/libsherpa-onnx-c-api.so", "/libsherpa-onnx-cxx-api.so")
    val temp = File(target.path + ".part")
    ZipFile(source).use { zip ->
        ZipOutputStream(temp.outputStream().buffered()).use { out ->
            for (entry in zip.entries()) {
                val name = entry.name
                if (name.startsWith("jni/") && dropped.any { name.endsWith(it) }) continue
                out.putNextEntry(ZipEntry(name).apply { time = entry.time })
                if (!entry.isDirectory) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    if (name.startsWith("jni/") && name.endsWith("/libsherpa-onnx-jni.so") &&
                        unversionImportsFrom(bytes, "libonnxruntime.so") == 0
                    ) {
                        temp.delete()
                        throw GradleException("$name takes nothing from libonnxruntime.so with a version: check the sherpa-onnx release.")
                    }
                    out.write(bytes)
                }
                out.closeEntry()
            }
        }
    }
    target.delete()
    if (!temp.renameTo(target)) throw GradleException("Could not write app/$SHERPA_ONNX_APP_AAR")
}

prepareSherpaOnnx(file(SHERPA_ONNX_AAR), file(SHERPA_ONNX_APP_AAR))


abstract class VerifySherpaOnnx : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:Input
    abstract val sha256: Property<String>

    @TaskAction
    fun verify() {
        val file = aar.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (actual != sha256.get()) {
            throw GradleException(
                "${file.name} is not the expected sherpa-onnx release (SHA-256 $actual).\n" +
                    "Run scripts/fetch-sherpa-onnx.sh to download the right one.",
            )
        }
    }
}

val verifySherpaOnnx = tasks.register<VerifySherpaOnnx>("verifySherpaOnnx") {
    group = "verification"
    description = "Checks that app/$SHERPA_ONNX_AAR is the pinned sherpa-onnx release."
    val file = layout.projectDirectory.file(SHERPA_ONNX_AAR)
    if (!file.asFile.isFile) {
        throw GradleException("app/$SHERPA_ONNX_AAR is missing. Run scripts/fetch-sherpa-onnx.sh to download it.")
    }
    aar.set(file)
    sha256.set(SHERPA_ONNX_SHA256)
}
tasks.named("preBuild") { dependsOn(verifySherpaOnnx) }

// ---------------------------------------------------------------------------
// Offline guarantee
//
// The app must never be able to reach the network on its own. Library manifests
// are merged into the final one, so a dependency could silently add a network
// permission; this check inspects the merged manifest of every variant and
// fails the build if one shows up.
// ---------------------------------------------------------------------------

abstract class VerifyNoNetworkPermissions : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val found = FORBIDDEN.filter { manifest.contains("\"$it\"") }
        if (found.isNotEmpty()) {
            throw GradleException(
                "The merged manifest requests permissions the app must not have: $found.\n" +
                    "Remove them in app/src/main/AndroidManifest.xml with tools:node=\"remove\" " +
                    "or drop the dependency that declares them."
            )
        }
    }

    companion object {
        val FORBIDDEN = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.NFC",
            // Not network, but just as unneeded: files come only through the system's picker
            // and shares, a video only through the camera app (see the manifest).
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoNetworkPermissions>("verify${variantName}NoNetworkPermissions") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest requests a network permission."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        // Run it as part of every build of this variant.
        tasks.matching { it.name == "assemble$variantName" || it.name == "bundle$variantName" }
            .configureEach { dependsOn(verify) }
    }
}
