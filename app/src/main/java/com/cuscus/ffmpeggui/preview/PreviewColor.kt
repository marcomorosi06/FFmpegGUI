package com.cuscus.ffmpeggui.preview

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.TextureView

/**
 * Tints everything the view shows with a 4x5 colour matrix (see
 * `eqColorMatrix` in :core), or removes the tint when [matrix] is null.
 * A fresh Paint is passed on purpose: TextureView only notices a new instance.
 */
fun TextureView.setColorMatrix(matrix: FloatArray?) {
    setLayerPaint(
        if (matrix == null) null else Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix)) },
    )
}
