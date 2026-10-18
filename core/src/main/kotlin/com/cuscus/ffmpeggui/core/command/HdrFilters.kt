package com.cuscus.ffmpeggui.core.command

import com.cuscus.ffmpeggui.core.model.VideoHdr

/**
 * HDR footage made SDR (see [VideoHdr]): to linear light (zscale, its BT.2020
 * colours and its transfer curve named, in case the frames do not say), the
 * highlights brought down with the Hable curve (colours kept, no
 * desaturation), then BT.709 as every SDR file is, in [pixelFormat]. Empty for
 * SDR footage.
 */
internal fun toneMapFilters(hdr: VideoHdr, pixelFormat: String = "yuv420p"): List<String> {
    val transfer = hdr.transfer ?: return emptyList()
    return listOf(
        "zscale=tin=$transfer:min=bt2020nc:pin=bt2020:t=linear:npl=$NOMINAL_PEAK",
        "format=gbrpf32le",
        "zscale=p=bt709",
        "tonemap=tonemap=hable:desat=0",
        "zscale=t=bt709:m=bt709:r=tv",
        "format=$pixelFormat",
    )
}

/** Nits of SDR white: linear 1.0 once the picture is in linear light. */
private const val NOMINAL_PEAK = 100
