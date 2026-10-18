package com.cuscus.ffmpeggui

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.cuscus.ffmpeggui.core.model.ColorKey
import com.cuscus.ffmpeggui.core.model.KeyMode
import com.cuscus.ffmpeggui.preview.gpuKeying
import com.cuscus.ffmpeggui.preview.keyRenderEffect

/**
 * Keys what this layer draws with [key] (null: nothing): over the key's
 * background, or transparent where it has none; then [outerMatrix], a colour
 * change that comes after the key (a 4 x 5 Android colour matrix). Does nothing
 * before Android 13 (see [gpuKeying]).
 */
internal fun Modifier.colorKey(key: ColorKey?, outerMatrix: FloatArray? = null): Modifier =
    // A subject key is never on a video (the shader keys by colour only): pictures are cut on the CPU.
    if (key == null || !gpuKeying || key.mode == KeyMode.SUBJECT) {
        this
    } else {
        composed {
            val effect = remember(key, outerMatrix?.toList()) { keyRenderEffect(key, outerMatrix).asComposeRenderEffect() }
            graphicsLayer { renderEffect = effect }
        }
    }
