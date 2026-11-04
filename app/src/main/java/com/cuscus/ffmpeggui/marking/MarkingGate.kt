package com.cuscus.ffmpeggui.marking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The rule that the AI marks come first: no model is imported before they
 * are installed and checked (EU AI Act, Article 50(2): what the app's models
 * make must carry a mark, so a model without them has nothing to put it in
 * with). Every model store asks [allowsImport] before it reads anything;
 * when the answer is no, [refusals] counts up and the app explains why
 * (MarkersFirstDialog). The marks' own import is the only one let through.
 */
object MarkingGate {
    /** Whether the marks are installed and checked; set by SmartFeatures. */
    @Volatile
    var ready: () -> Boolean = { false }

    private val _refusals = MutableStateFlow(0)

    /** How many imports were refused so far: each one shows why. */
    val refusals: StateFlow<Int> = _refusals.asStateFlow()

    /** True when a model may be imported now; otherwise the refusal is told. */
    fun allowsImport(): Boolean {
        if (ready()) return true
        _refusals.update { it + 1 }
        return false
    }
}
