package com.cuscus.ffmpeggui.work

import android.content.Context
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.conversion.SpeechStatus
import com.cuscus.ffmpeggui.speech.SpeechTranscriber

/**
 * The editors' long work in words, for [WorkNotifier]: what subtitles being
 * made (or a transcript being heard) and a subject being cut out say in their
 * notifications while they run, and once they are over.
 */
class WorkReports(context: Context, private val notifier: WorkNotifier) {

    private val app = context.applicationContext

    /** Subtitles being made: where the run is now; [onStop] stops it. */
    fun speech(status: SpeechStatus.Working, onStop: () -> Unit) {
        val step = status.step
        val text = app.getString(
            when (step) {
                is SpeechTranscriber.Step.Extracting -> R.string.work_speech_extracting
                SpeechTranscriber.Step.Loading -> R.string.work_speech_loading
                is SpeechTranscriber.Step.Listening -> R.string.work_speech_listening
            },
        )
        val part = when {
            step is SpeechTranscriber.Step.Listening && step.parts > 1 -> app.getString(R.string.work_part, step.part, step.parts)
            status.fileCount > 1 -> app.getString(R.string.work_file, status.fileIndex + 1, status.fileCount)
            else -> null
        }
        // With several files, a mark where each one starts.
        val marks = if (status.fileCount > 1) (1 until status.fileCount).map { it.toFloat() / status.fileCount } else emptyList()
        notifier.show(
            WorkNotifier.Kind.SPEECH,
            WorkNotifier.Status(
                title = app.getString(R.string.work_speech_title, status.fileName),
                text = text,
                fraction = status.fraction,
                part = part,
                marks = marks,
            ),
            onStop,
        )
    }

    /** Subtitles are over: made ([made] true), failed (false) or stopped (null, nothing to say). */
    fun speechOver(made: Boolean?) {
        notifier.done(
            WorkNotifier.Kind.SPEECH,
            when (made) {
                true -> app.getString(R.string.work_speech_done_title) to app.getString(R.string.work_open_to_see)
                false -> app.getString(R.string.work_speech_failed_title) to app.getString(R.string.work_open_to_see_why)
                null -> null
            },
        )
    }

    /** A subject is being looked for; [onStop] stops it. */
    fun cutout(onStop: () -> Unit) {
        notifier.show(
            WorkNotifier.Kind.CUTOUT,
            WorkNotifier.Status(
                title = app.getString(R.string.work_cutout_title),
                text = app.getString(R.string.work_cutout_text),
                fraction = null,
            ),
            onStop,
        )
    }

    /** The cut-out is over: done ([made] true), failed (false) or stopped (null). */
    fun cutoutOver(made: Boolean?) {
        notifier.done(
            WorkNotifier.Kind.CUTOUT,
            when (made) {
                true -> app.getString(R.string.work_cutout_done_title) to app.getString(R.string.work_open_to_see)
                false -> app.getString(R.string.work_cutout_failed_title) to app.getString(R.string.work_open_to_see_why)
                null -> null
            },
        )
    }
}
