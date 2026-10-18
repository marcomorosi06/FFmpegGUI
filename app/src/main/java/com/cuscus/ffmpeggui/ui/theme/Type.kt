package com.cuscus.ffmpeggui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cuscus.ffmpeggui.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

/**
 * The display face of the app's name and its big moments (the home title, the
 * About page): Google Sans Flex (SIL OFL 1.1, licence in assets/licenses),
 * fully rounded, wide and heavy. The bundled file holds the printable ASCII
 * letters only, with roundness pinned at its fullest and optical size at
 * display; weight (600..1000) and width (100..151) stay variable, and each
 * weight here sets its own width.
 */
val DisplayFamily: FontFamily by lazy {
    FontFamily(
        displayFont(weight = 640, width = 112f),
        displayFont(weight = 760, width = 118f),
        displayFont(weight = 1000, width = 151f),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun displayFont(weight: Int, width: Float): Font = Font(
    resId = R.font.google_sans_flex_title,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)
