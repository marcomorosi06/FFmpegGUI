package com.cuscus.ffmpeggui.preview

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.cuscus.ffmpeggui.core.model.TransitionKind
import com.cuscus.ffmpeggui.core.transition.TransitionShaderNames
import com.cuscus.ffmpeggui.core.transition.overlayShader
import com.cuscus.ffmpeggui.core.transition.partShader
import com.cuscus.ffmpeggui.core.transition.recipe

/*
 * The transitions told as layers (core.transition's TransitionRecipe) in the
 * preview: each clip's layer runs its own shader (AGSL, written out from the
 * recipe), and the light laid over them one more. Android 13 and later only;
 * before, and on a device that refuses a shader, the transition is drawn as a
 * cross-fade (see core.preview's transitionLook).
 */

/** True when the preview can draw the transitions told as layers: Android 13 and later. */
val gpuTransitions: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * The shaders of the transitions told as layers, written out once each. A
 * shader the device refuses is remembered, and never tried again.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object TransitionShaders {
    private val sources = HashMap<String, String?>()
    private val refused = HashSet<String>()

    /** A new shader for the layer of the clip going out (or coming in, when [incoming]) of [kind]; null when it has none. */
    fun part(kind: TransitionKind, incoming: Boolean): RuntimeShader? = make("$kind/$incoming") {
        val recipe = kind.recipe ?: return@make null
        val part = if (incoming) recipe.incoming else recipe.outgoing
        if (part.plain) null else partShader(part)
    }

    /** A new shader for the light laid over the clips of [kind]; null when it has none. */
    fun light(kind: TransitionKind): RuntimeShader? = make("$kind/light") {
        val overlays = kind.recipe?.overlays.orEmpty()
        if (overlays.isEmpty()) null else overlayShader(overlays)
    }

    private fun make(key: String, write: () -> String?): RuntimeShader? {
        val source = synchronized(this) {
            if (key in refused) return null
            if (key !in sources) sources[key] = write()
            sources[key]
        } ?: return null
        return try {
            RuntimeShader(source)
        } catch (e: RuntimeException) {
            Log.w("TransitionShaders", "Transition shader $key refused; drawn as a cross-fade", e)
            synchronized(this) { refused += key }
            null
        }
    }
}

/** This layer shader set to a frame of [width] x [height], [progress] of the way through. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RuntimeShader.setTransition(width: Float, height: Float, progress: Float) {
    // Every name is one the shaders are written with (see TransitionShaderNames): a name a shader
    // lacks throws, which Android 13 reports by aborting the app.
    setFloatUniform(TransitionShaderNames.SIZE, width, height)
    setFloatUniform(TransitionShaderNames.PROGRESS, progress.coerceIn(0f, 1f))
}

/** The effect that draws a layer through this shader (see [TransitionShaders.part]). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun RuntimeShader.layerEffect(width: Float, height: Float, progress: Float): RenderEffect {
    setTransition(width, height, progress)
    return RenderEffect.createRuntimeShaderEffect(this, TransitionShaderNames.CONTENT)
}
