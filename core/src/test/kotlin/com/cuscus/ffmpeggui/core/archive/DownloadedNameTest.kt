package com.cuscus.ffmpeggui.core.archive

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedNameTest {

    @Test
    fun `the numbers browsers add go`() {
        assertEquals("encoder.int8.onnx", DownloadedName.of("encoder.int8 (1).onnx"))
        assertEquals("encoder.int8.onnx", DownloadedName.of("encoder.int8(12).onnx"))
        assertEquals("tokens.txt", DownloadedName.of("(2) tokens.txt"))
        assertEquals("tokens.txt", DownloadedName.of("1_tokens.txt"))
        assertEquals("en.wav", DownloadedName.of("en-3.wav"))
        assertEquals("decoder.int8.onnx", DownloadedName.of("Download/Decoder.INT8 [1].onnx"))
    }

    @Test
    fun `a name of its own is kept`() {
        assertEquals("encoder.int8.onnx", DownloadedName.of("encoder.int8.onnx"))
        assertEquals("u2netp.onnx", DownloadedName.of("u2netp.onnx"))
        assertEquals("sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2", DownloadedName.of("sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2"))
    }
}
