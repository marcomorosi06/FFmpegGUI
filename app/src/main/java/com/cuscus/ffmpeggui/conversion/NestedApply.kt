package com.cuscus.ffmpeggui.conversion

/**
 * Where applying an editor of the stack to the one under it is (see
 * ConversionViewModel.applyNested and core.stack).
 */
sealed interface NestedApply {
    data object Idle : NestedApply

    /** Being rendered: [progress] of it done, 0..1. */
    data class Rendering(val progress: Float) : NestedApply

    /** It could not be made; [message] says why when it is known (the AI marks missing, say). */
    data class Failed(val message: String?) : NestedApply
}
