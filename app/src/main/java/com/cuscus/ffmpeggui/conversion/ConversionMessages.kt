package com.cuscus.ffmpeggui.conversion

import android.content.res.Resources
import com.cuscus.ffmpeggui.R
import com.cuscus.ffmpeggui.core.engine.ConversionOutcome
import com.cuscus.ffmpeggui.core.engine.ConversionStage
import com.cuscus.ffmpeggui.core.engine.FailureReason

/** Turns engine events into user-facing text taken from the string resources. */
class ConversionMessages(private val resources: Resources) {

    fun statusLine(stage: ConversionStage): String = resources.getString(
        when (stage) {
            ConversionStage.PREPARING -> R.string.status_preparing
            ConversionStage.ANALYZING -> R.string.status_analyzing
            ConversionStage.ENCODING -> R.string.status_encoding
            ConversionStage.PACKING -> R.string.status_packing
            ConversionStage.MARKING -> R.string.status_marking
        },
    )

    fun failure(failure: ConversionOutcome.Failure): String = when (failure.reason) {
        FailureReason.SOURCE_UNREADABLE -> resources.getString(R.string.error_source_unreadable, failure.detail)
        FailureReason.ANALYSIS_FAILED -> resources.getString(R.string.error_analysis_failed)
        FailureReason.ENCODING_FAILED -> resources.getString(R.string.error_encoding_failed)
        FailureReason.MANUAL_COMMAND_EMPTY -> resources.getString(R.string.error_manual_empty)
        FailureReason.MANUAL_COMMAND_WITHOUT_OUTPUT -> resources.getString(R.string.error_manual_without_output)
        FailureReason.PACKING_FAILED -> resources.getString(R.string.error_packing_failed)
        FailureReason.STORAGE_FULL -> resources.getString(R.string.error_storage_full)
        FailureReason.AI_MARKS_NO_PLACE -> resources.getString(R.string.error_ai_marks_no_place, failure.detail)
        FailureReason.AI_MARKS_FAILED -> resources.getString(R.string.error_ai_marks_failed)
    }

    /** Said in the export's log when what a model made before the marks got them now. */
    fun aiMarksAdded(): String = resources.getString(R.string.marks_added_for_export)

    /** The export stops: something a model made is in it and the marks are not installed. */
    fun aiMarksMissing(): String = resources.getString(R.string.marks_export_missing)

    /** The export stops: something a model made could not be given its mark. */
    fun aiMarksFailed(): String = resources.getString(R.string.marks_export_failed)

    /** Why a transcript (.txt) could not be made. */
    fun transcriptFailure(problem: SpeechProblem): String = resources.getString(
        when (problem) {
            SpeechProblem.NO_MODEL -> R.string.speech_problem_model
            SpeechProblem.NO_MEMORY -> R.string.speech_problem_memory
            SpeechProblem.FAILED -> R.string.error_transcript_failed
        },
    )

    fun transcriptListening(fileName: String): String = resources.getString(R.string.status_transcript_listening, fileName)
}
