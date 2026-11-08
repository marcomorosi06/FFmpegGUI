package com.cuscus.ffmpeggui.ui.expressive

import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cuscus.ffmpeggui.R

/**
 * Where a link goes, said under it before it is followed: "Opens GitHub:
 * https://github.com/...". Every link of the app (a row, a button, a card)
 * carries one, so that nothing opens a page the user did not see named.
 */
@Composable
fun LinkDestination(url: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val tint = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top, modifier = modifier.padding(top = 2.dp)) {
        Icon(Icons.Rounded.Link, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.link_opens, siteName(url), url),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/** The name of the site [url] is on, as people call it; its address when it is none of those. */
fun siteName(url: String): String {
    val host = Uri.parse(url).host.orEmpty().removePrefix("www.")
    return when {
        host == "k2-fsa.github.io" -> "sherpa-onnx"
        host == "github.com" || host.endsWith(".github.com") || host == "raw.githubusercontent.com" -> "GitHub"
        host == "huggingface.co" -> "Hugging Face"
        host == "ko-fi.com" -> "Ko-fi"
        host == "zenodo.org" -> "Zenodo"
        host == "eur-lex.europa.eu" -> "EUR-Lex"
        host == "creativecommons.org" -> "Creative Commons"
        host == "keepandroidopen.org" -> "Keep Android Open"
        host.isNotEmpty() -> host
        else -> url
    }
}
