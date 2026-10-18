package com.cuscus.ffmpeggui.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import com.cuscus.ffmpeggui.core.model.HardwareEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Finds, on this phone, a way for FFmpeg to encode with the phone's video chip
 * that gives correct videos, by trying it for real.
 *
 * FFmpeg 6.1 (h264_mediacodec) hands the chip frames packed its own way; a chip
 * that reads them another way makes a video torn into coloured streaks, with no
 * error anywhere. Nothing short of an actual encode tells which way works, so
 * the calibration does exactly what an export does: for each hardware H.264
 * encoder, each input FFmpeg can give it (NV12, I420) and each size step (16,
 * 32, 64, 128, 256), FFmpeg encodes a one-second test video (`testsrc2`, a
 * portrait and a landscape frame whose sides are odd multiples of the step, see
 * [testSizes]) with the chip, then decodes it and
 * compares it with the original (FFmpeg's `psnr`). Every plane (luma and both
 * colours, so that colours read from the wrong rows show too) must be within
 * [MIN_PSNR_DB]; the first way that passes on both sizes is the one used. When
 * none does, the chip cannot be used and the processor encodes every video.
 *
 * It runs only when asked (the settings' test button), on its own scope so it
 * goes on while the settings are left; [state] tells how far it is. The answer
 * is kept in the app's private preferences, keyed by the system build, so an
 * update of the phone asks for a new test.
 */
class HardwareCalibration(context: Context, private val executor: FfmpegExecutor) {

    /** What a test found. */
    sealed interface Result {
        /** [encoder] makes correct videos; its worst plane was [psnrDb] from the original. */
        data class Works(val encoder: HardwareEncoder, val psnrDb: Double) : Result

        /** None of the [tried] ways made a correct video, for [reason]. */
        data class Unusable(val reason: String, val tried: Int) : Result
    }

    /** Where the test is. */
    sealed interface State {
        /** Never tested on this system build. */
        data object Untested : State

        /** Trying way [step] of [total]: [trying]. */
        data class Running(val step: Int, val total: Int, val trying: Way) : State

        data class Done(val result: Result) : State
    }

    /** One way of encoding with the chip. */
    data class Way(val codecName: String, val pixelFormat: String, val align: Int)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(stored()?.let { State.Done(it) } ?: State.Untested)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The encoder a test found working on this phone, or null (untested, or unusable). */
    fun knownEncoder(): HardwareEncoder? = ((_state.value as? State.Done)?.result as? Result.Works)?.encoder
        ?: (stored() as? Result.Works)?.encoder

    /** Starts a test (nothing happens while one runs). */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val result = try {
                calibrate()
            } catch (e: CancellationException) {
                _state.value = stored()?.let { State.Done(it) } ?: State.Untested
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Calibration failed", e)
                Result.Unusable("the test failed: ${e.javaClass.simpleName}", 0)
            }
            store(result)
            _state.value = State.Done(result)
            Log.i(TAG, "Calibration: $result")
        }
    }

    /** Stops a running test; what was known before stays. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    // -----------------------------------------------------------------
    // The test
    // -----------------------------------------------------------------

    private suspend fun calibrate(): Result {
        val codecs = hardwareEncoders()
        if (codecs.isEmpty()) return Result.Unusable("no hardware H.264 encoder", 0)
        val ways = codecs.flatMap { codec -> PIXEL_FORMATS.flatMap { format -> ALIGNMENTS.map { Way(codec, format, it) } } }
        val directory = File(appContext.cacheDir, DIRECTORY).apply { deleteRecursively(); mkdirs() }
        try {
            ways.forEachIndexed { index, way ->
                _state.value = State.Running(index + 1, ways.size, way)
                // Both sizes must come out right; the first that does not ends this way.
                var worst = Double.MAX_VALUE
                var passed = true
                for ((width, height) in testSizes(way.align)) {
                    val psnr = attempt(way, width, height, directory)
                    if (psnr == null || psnr < MIN_PSNR_DB) {
                        passed = false
                        break
                    }
                    worst = minOf(worst, psnr)
                }
                if (passed) return Result.Works(HardwareEncoder(way.codecName, way.pixelFormat, way.align, way.align), worst)
            }
        } finally {
            directory.deleteRecursively()
        }
        return Result.Unusable("every video came out damaged or could not be made", ways.size)
    }

    /**
     * The worst plane's PSNR (dB) of a [width] x [height] test video encoded by
     * the chip in [way] against the original; null when it could not be made.
     */
    private suspend fun attempt(way: Way, width: Int, height: Int, directory: File): Double? {
        val source = "testsrc2=size=${width}x$height:rate=$TEST_FPS:duration=$TEST_SECONDS"
        val output = File(directory, "test_${width}x$height.mp4")
        output.delete()
        val a = way.align
        val encode = FfmpegCommand(
            listOf(
                "-y", "-f", "lavfi", "-i", source,
                // As an export does: the sides brought to the step, then the chip.
                "-vf", "scale=trunc(iw/$a+0.5)*$a:trunc(ih/$a+0.5)*$a",
                "-vcodec", "h264_mediacodec", "-codec_name", way.codecName, "-pix_fmt", way.pixelFormat,
                "-b:v", "${TEST_KBPS}k", "-bitrate_mode", "vbr", "-g", "60",
                "-an", output.absolutePath,
            ),
        )
        if (executor.execute(encode) { } != ExecutionResult.Success || !output.isFile || output.length() == 0L) return null
        val log = StringBuilder()
        val compare = FfmpegCommand(
            listOf(
                "-i", output.absolutePath, "-f", "lavfi", "-i", source,
                "-filter_complex", "[0:v]scale=$width:$height:flags=bicubic,format=yuv420p[a];[1:v]format=yuv420p[b];[a][b]psnr",
                "-f", "null", "-",
            ),
        )
        val compared = executor.execute(compare) { line -> synchronized(log) { log.append(line) } }
        output.delete()
        if (compared != ExecutionResult.Success) return null
        return worstPlane(synchronized(log) { log.toString() })
    }

    private fun hardwareEncoders(): List<String> = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } && isHardware(info) }
            .map { it.name }
            .distinct()
            .take(MAX_ENCODERS)
    } catch (e: Exception) {
        Log.w(TAG, "Codec list unreadable", e)
        emptyList()
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        info.isHardwareAccelerated && !info.isSoftwareOnly && !info.isAlias

    // -----------------------------------------------------------------
    // Remembering
    // -----------------------------------------------------------------

    private val key: String get() = "${Build.FINGERPRINT}|$CALIBRATION_VERSION"

    private fun stored(): Result? {
        if (prefs.getString(KEY_FOR, null) != key) return null
        val parts = prefs.getString(KEY_RESULT, null)?.split('|') ?: return null
        return when (parts.firstOrNull()) {
            "works" -> if (parts.size == 6) {
                Result.Works(
                    HardwareEncoder(parts[1], parts[2], parts[3].toIntOrNull() ?: 16, parts[4].toIntOrNull() ?: 16),
                    parts[5].toDoubleOrNull() ?: 0.0,
                )
            } else {
                null
            }
            "unusable" -> if (parts.size >= 3) Result.Unusable(parts.drop(2).joinToString("|"), parts[1].toIntOrNull() ?: 0) else null
            else -> null
        }
    }

    private fun store(result: Result) {
        val value = when (result) {
            is Result.Works -> with(result.encoder) {
                "works|$codecName|$pixelFormat|$widthAlign|$heightAlign|${String.format(Locale.ROOT, "%.1f", result.psnrDb)}"
            }
            is Result.Unusable -> "unusable|${result.tried}|${result.reason}"
        }
        prefs.edit().putString(KEY_FOR, key).putString(KEY_RESULT, value).apply()
    }

    companion object {
        private const val TAG = "HardwareCalibration"
        private const val PREFS = "hardware_video"
        private const val KEY_FOR = "calibrated_for"
        private const val KEY_RESULT = "calibration"
        private const val DIRECTORY = "calibration"

        /** Raised when the test changes, so that phones are tested again. */
        private const val CALIBRATION_VERSION = 2

        private val PIXEL_FORMATS = listOf("nv12", "yuv420p")
        private val ALIGNMENTS get() = ALIGN_STEPS

        private val ALIGN_STEPS = listOf(16, 32, 64, 128, 256)

        /**
         * The sizes a size step [align] is tried at: a portrait and a landscape
         * frame near a phone video's, each side an ODD multiple of [align]. A side
         * that is also a multiple of a larger step (1088 is 17 x 64, 1920 is 15 x
         * 128) would pass for a chip that really needs that larger step, and the
         * exports at other sizes would then come out torn.
         */
        fun testSizes(align: Int): List<Pair<Int, Int>> {
            val short = oddMultiple(SHORT_SIDE, align)
            val long = oddMultiple(LONG_SIDE, align)
            return listOf(short to long, long to short)
        }

        /** The odd multiple of [step] nearest to [target]. */
        private fun oddMultiple(target: Int, step: Int): Int {
            val near = target / step
            return (near - 1..near + 2).filter { it >= 1 && it % 2 == 1 }.minBy { kotlin.math.abs(it * step - target) } * step
        }

        private const val SHORT_SIDE = 1080
        private const val LONG_SIDE = 1920
        private const val MAX_ENCODERS = 3
        private const val TEST_FPS = 30
        private const val TEST_SECONDS = 1
        private const val TEST_KBPS = 20_000

        /**
         * Below this, a plane is not the original's. A correct encode of the test
         * video is around 38 to 46 dB on every plane; rows read with the wrong
         * stride give about 10, colours read from the wrong rows about 20.
         */
        const val MIN_PSNR_DB = 28.0

        private val PSNR = Regex("""PSNR y:([0-9.]+|inf) u:([0-9.]+|inf) v:([0-9.]+|inf)""")

        /** The lowest of the three planes' PSNR in FFmpeg's [log] (inf, identical, counts as 100); null when absent. */
        fun worstPlane(log: String): Double? {
            val match = PSNR.findAll(log).lastOrNull() ?: return null
            return match.groupValues.drop(1).minOf { if (it == "inf") 100.0 else it.toDoubleOrNull() ?: 0.0 }
        }
    }
}
