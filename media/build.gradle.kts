import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

// Android implementations of the interfaces declared in :core.
// This is the only module that depends on the FFmpeg runtime.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cuscus.ffmpeggui.media"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// ---------------------------------------------------------------------------
// FFmpeg runtime
//
// FFmpegKitNext publishes no binaries: scripts/build-ffmpeg-kit-next.sh builds
// it from the sources pinned in scripts/ffmpeg-kit-next.lock, and
// .github/workflows/ffmpeg-aar.yml publishes the AAR it builds twice the same.
// scripts/ffmpeg-kit-next-aar.txt pins that AAR by its SHA-256 and
// scripts/fetch-ffmpeg-kit-next.sh puts it here, in media/libs (ignored by git).
// settings.gradle.kts serves it from there, and nowhere else, as
// com.arthenica:ffmpeg-kit-next.
// ---------------------------------------------------------------------------

val ffmpegKitNextVersion: String = libs.versions.ffmpegKitNext.get()
val ffmpegKitNextAar = "libs/ffmpeg-kit-next-$ffmpegKitNextVersion.aar"
val ffmpegKitNextMissing =
    "media/$ffmpegKitNextAar is missing: fetch the pinned one with scripts/fetch-ffmpeg-kit-next.sh, " +
        "or build it from source with scripts/build-ffmpeg-kit-next.sh (see docs/ARCHITECTURE.md, \"FFmpeg runtime\")."
// The SHA-256 of the pinned AAR ("-" while none is published).
val ffmpegKitNextPinnedSha256: String = rootProject.file("scripts/ffmpeg-kit-next-aar.txt")
    .takeIf { it.isFile }?.readLines().orEmpty()
    .map { it.trim().split(Regex("\\s+")) }
    .firstOrNull { it.size >= 2 && it[0] == "sha256" }?.get(1).orEmpty()

if (!file(ffmpegKitNextAar).isFile) logger.warn("w: $ffmpegKitNextMissing")

dependencies {
    api(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    // FileProvider, for the content URIs handed to the camera.
    implementation(libs.androidx.core.ktx)
    implementation("com.arthenica:ffmpeg-kit-next:$ffmpegKitNextVersion@aar")
    // FFmpegKitNext's only dependency. The AAR comes without its POM, so it is declared here.
    implementation(libs.smart.exception.java)
}

/**
 * Checks the FFmpegKitNext AAR before anything is built with it: it is the one
 * pinned in scripts/ffmpeg-kit-next-aar.txt (or [allowUnpinned], for an AAR built
 * locally; never for a release), it has the native libraries of both ABIs the
 * app ships, and FFmpeg's own libraries are aligned for 16 KB memory pages
 * (Android 15+ devices that use them cannot load a library aligned for 4 KB pages).
 */
abstract class VerifyFfmpegKitNext : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aar: RegularFileProperty

    @get:Input
    abstract val pinnedSha256: Property<String>

    @get:Input
    abstract val allowUnpinned: Property<Boolean>

    @TaskAction
    fun verify() {
        val file = aar.get().asFile
        val pinned = pinnedSha256.get()
        if (pinned.matches(SHA256)) {
            val actual = sha256Of(file)
            if (actual != pinned) {
                val message = "${file.name} is not the pinned FFmpeg runtime (SHA-256 $actual; scripts/ffmpeg-kit-next-aar.txt pins $pinned)"
                if (!allowUnpinned.get()) {
                    throw GradleException("$message. Run scripts/fetch-ffmpeg-kit-next.sh, or build with -Pffmpeg.unpinned=true to use your own build (never for a release).")
                }
                logger.warn("w: $message: used anyway (-Pffmpeg.unpinned=true); not for a release.")
            }
        } else {
            logger.warn("w: no FFmpeg runtime is pinned in scripts/ffmpeg-kit-next-aar.txt yet: ${file.name} is not checked against one.")
        }
        ZipFile(file).use { zip ->
            for (abi in ABIS) {
                if (zip.getEntry("jni/$abi/libffmpegkit.so") == null) {
                    throw GradleException("${file.name} has no jni/$abi/libffmpegkit.so: build it again with scripts/build-ffmpeg-kit-next.sh.")
                }
            }
            for (entry in zip.entries()) {
                val name = entry.name
                if (!name.startsWith("jni/") || !name.endsWith(".so")) continue
                val alignment = smallestLoadAlignment(zip.getInputStream(entry).use { it.readBytes() })
                if (alignment >= PAGE_16K) continue
                val library = name.substringAfterLast('/')
                val message = "$name in ${file.name} is aligned to $alignment bytes, not 16 KB"
                if (FFMPEG_LIBRARIES.any { library.startsWith(it) }) throw GradleException("$message.")
                logger.warn("w: $message: it will not load on devices with 16 KB pages.")
            }
        }
    }

    private fun sha256Of(file: java.io.File): String {
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

    /** The smallest alignment of the loadable segments of the ELF file [elf]. */
    private fun smallestLoadAlignment(elf: ByteArray): Long {
        require(elf.size > 0x40 && elf[0] == 0x7f.toByte() && elf[1] == 'E'.code.toByte() && elf[2] == 'L'.code.toByte() && elf[3] == 'F'.code.toByte()) { "not an ELF file" }
        val is64 = elf[4].toInt() == 2
        val b = ByteBuffer.wrap(elf).order(if (elf[5].toInt() == 2) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val phoff = if (is64) b.getLong(0x20) else b.getInt(0x1C).toLong() and 0xffffffffL
        val phentsize = b.getShort(if (is64) 0x36 else 0x2A).toInt() and 0xffff
        val phnum = b.getShort(if (is64) 0x38 else 0x2C).toInt() and 0xffff
        var smallest = Long.MAX_VALUE
        for (i in 0 until phnum) {
            val at = (phoff + i.toLong() * phentsize).toInt()
            if (b.getInt(at) != PT_LOAD) continue
            val align = if (is64) b.getLong(at + 48) else b.getInt(at + 28).toLong() and 0xffffffffL
            smallest = minOf(smallest, align)
        }
        return if (smallest == Long.MAX_VALUE) 0 else smallest
    }

    companion object {
        val ABIS = listOf("arm64-v8a", "x86_64")
        /** The libraries FFmpegKitNext builds itself; others (libc++_shared.so) only warn. */
        val FFMPEG_LIBRARIES = listOf("libffmpegkit", "libavcodec", "libavdevice", "libavfilter", "libavformat", "libavutil", "libswresample", "libswscale")
        val PAGE_16K = 16_384L
        val PT_LOAD = 1
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

val verifyFfmpegKitNext = tasks.register<VerifyFfmpegKitNext>("verifyFfmpegKitNext") {
    group = "verification"
    description = "Checks media/$ffmpegKitNextAar, the FFmpeg runtime pinned in scripts/ffmpeg-kit-next-aar.txt."
    val file = layout.projectDirectory.file(ffmpegKitNextAar)
    if (!file.asFile.isFile) throw GradleException(ffmpegKitNextMissing)
    aar.set(file)
    pinnedSha256.set(ffmpegKitNextPinnedSha256)
    allowUnpinned.set(providers.gradleProperty("ffmpeg.unpinned").map { it.toBoolean() }.orElse(false))
}
tasks.named("preBuild") { dependsOn(verifyFfmpegKitNext) }
