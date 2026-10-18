package com.cuscus.ffmpeggui.media

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.cuscus.ffmpeggui.core.command.FfmpegCommand
import com.cuscus.ffmpeggui.core.engine.ExecutionResult
import com.cuscus.ffmpeggui.core.engine.FfmpegExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [FfmpegExecutor] backed by FFmpegKit.
 *
 * This file is the only place in the project that talks to FFmpegKit, so
 * replacing the FFmpeg runtime means replacing it and one Gradle dependency.
 *
 * Each call gets its own session and log callback; no global FFmpegKit state is
 * touched, so the class is safe to instantiate more than once.
 */
class FfmpegKitExecutor : FfmpegExecutor {

    override suspend fun execute(command: FfmpegCommand, onLog: (String) -> Unit): ExecutionResult =
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                command.arguments.toTypedArray(),
                { finished ->
                    // FFmpegKitNext is written in Kotlin: its sessions have getters, not properties.
                    val returnCode = finished.getReturnCode()
                    val result = when {
                        ReturnCode.isSuccess(returnCode) -> ExecutionResult.Success
                        ReturnCode.isCancel(returnCode) -> ExecutionResult.Cancelled
                        else -> ExecutionResult.Failed(returnCode?.value ?: UNKNOWN_RETURN_CODE)
                    }
                    if (continuation.isActive) continuation.resume(result)
                },
                { log -> onLog(log.message) },
                null,
            )
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.getSessionId()) }
        }

    private companion object {
        const val UNKNOWN_RETURN_CODE = -1
    }
}

/**
 * Path FFmpeg can open to read [uri] in place, without copying it first.
 * Returns null when the provider behind the URI cannot hand out a file descriptor.
 */
internal fun ffmpegReadablePath(context: Context, uri: Uri): String? = try {
    FFmpegKitConfig.getSafParameterForRead(context, uri)
} catch (e: Exception) {
    null
}
