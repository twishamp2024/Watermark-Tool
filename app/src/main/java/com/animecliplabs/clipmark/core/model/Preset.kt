package com.animecliplabs.clipmark.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val style: WatermarkStyle,
    val createdAt: Long = 0L,
) {
    companion object {
        const val DEFAULT_ID = "animeclips-default"

        val Default = Preset(id = DEFAULT_ID, name = "AnimeClips Default", style = WatermarkStyle())
    }
}

@Serializable
enum class OutputFormatPreference { MATCH_ORIGINAL, JPEG, PNG }

@Serializable
data class OutputSettings(
    val format: OutputFormatPreference = OutputFormatPreference.MATCH_ORIGINAL,
    /** Used for JPEG and lossy WebP. 95 is visually lossless for re-encoding; 100 for the paranoid. */
    val quality: Int = QUALITY_HIGH,
    /** Copy camera/date EXIF tags from the original. */
    val keepMetadata: Boolean = true,
    /** Drop GPS tags even when keeping metadata — creators post publicly. */
    val stripLocation: Boolean = true,
) {
    companion object {
        const val QUALITY_HIGH = 95
        const val QUALITY_MAX = 100
    }
}
