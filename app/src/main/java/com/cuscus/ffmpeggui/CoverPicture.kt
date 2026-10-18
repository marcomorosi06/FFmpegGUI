package com.cuscus.ffmpeggui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.cuscus.ffmpeggui.preview.smallPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The cover picture at [uri] (see ConversionSettings.coverUri), at most [boxPx]
 * on its longer side, read off the main thread. Null while it loads, when
 * there is none or when it cannot be read.
 */
@Composable
internal fun rememberCoverPicture(uri: Uri?, boxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val picture by produceState<ImageBitmap?>(initialValue = null, uri, boxPx) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { smallPicture(context, uri, boxPx)?.asImageBitmap() }
    }
    return picture
}
