package com.animecliplabs.clipmark.core.format

import com.animecliplabs.clipmark.core.model.OutputFormatPreference.JPEG
import com.animecliplabs.clipmark.core.model.OutputFormatPreference.MATCH_ORIGINAL
import com.animecliplabs.clipmark.core.model.OutputFormatPreference.PNG
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputFormatResolverTest {
    private fun r(mime: String?, pref: com.animecliplabs.clipmark.core.model.OutputFormatPreference = MATCH_ORIGINAL, alpha: Boolean = false) =
        OutputFormatResolver.resolve(mime, pref, alpha)

    @Test
    fun `match original keeps the container`() {
        assertEquals(OutputFormat.JPEG, r("image/jpeg"))
        assertEquals(OutputFormat.PNG, r("image/png"))
        assertEquals(OutputFormat.WEBP, r("image/webp"))
    }

    @Test
    fun `formats Android cannot encode fall back sensibly`() {
        assertEquals(OutputFormat.JPEG, r("image/heif"))
        assertEquals(OutputFormat.JPEG, r("image/avif"))
        assertEquals(OutputFormat.PNG, r("image/gif"))
        assertEquals(OutputFormat.PNG, r("image/bmp"))
        assertEquals(OutputFormat.JPEG, r(null))
    }

    @Test
    fun `transparency is never flattened`() {
        assertEquals(OutputFormat.PNG, r("image/heif", alpha = true))
        assertEquals(OutputFormat.PNG, r("image/jpeg", pref = JPEG, alpha = true))
    }

    @Test
    fun `explicit preferences win otherwise`() {
        assertEquals(OutputFormat.JPEG, r("image/png", pref = JPEG))
        assertEquals(OutputFormat.PNG, r("image/jpeg", pref = PNG))
    }

    @Test
    fun `mime matching is case-insensitive`() {
        assertEquals(OutputFormat.JPEG, r("IMAGE/JPEG"))
    }
}
