package com.cuscus.ffmpeggui.core.model

/**
 * How the picture of footage is lit: SDR, or HDR by one of its two transfer
 * curves (both on BT.2020 colours). Every file the app writes is SDR, so HDR
 * footage is tone mapped first: its highlights brought down to what an SDR
 * picture holds, rather than its colours read as SDR ones (washed out, grey).
 */
enum class VideoHdr(
    /** The transfer curve's name for zscale. */
    val transfer: String?,
) {
    NONE(null),

    /** PQ (SMPTE ST 2084): HDR10, HDR10+, Dolby Vision 8.1. */
    PQ("smpte2084"),

    /** HLG (ARIB STD-B67): phones and broadcast, Dolby Vision 8.4. */
    HLG("arib-std-b67"),
}
