package com.cuscus.ffmpeggui.marking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The rule that the AI marks come before the models that make voices and
 * pictures: none of the dub's models (Kokoro, the Piper voices) nor the magic
 * eraser's (MI-GAN, LaMa) is imported before the marks are installed and
 * checked (EU AI Act, Article 50(2): what those models make must carry a
 * mark, so without the marks there is nothing to put it in with). Their
 * stores ask [allowsImport] before they read anything (SpeechModel.generative,
 * DubModelStore, PiperVoiceStore); when the answer is no, [refusals] counts
 * up and the app explains why (MarkersFirstDialog).
 *
 * The other models (speech, translation, cut-out and its corrections, stems)
 * make nothing that passes for real and is marked: they do not ask, and are
 * imported with or without the marks.
 */
object MarkingGate {
    /** Whether the marks are installed and checked; set by SmartFeatures. */
    @Volatile
    var ready: () -> Boolean = { false }

    private val _refusals = MutableStateFlow(0)

    /** How many imports were refused so far: each one shows why. */
    val refusals: StateFlow<Int> = _refusals.asStateFlow()

    /** True when a dub or eraser model may be imported now; otherwise the refusal is told. */
    fun allowsImport(): Boolean {
        if (ready()) return true
        _refusals.update { it + 1 }
        return false
    }
}
