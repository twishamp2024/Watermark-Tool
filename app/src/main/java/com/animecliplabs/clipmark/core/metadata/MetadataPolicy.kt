package com.animecliplabs.clipmark.core.metadata

import com.animecliplabs.clipmark.core.model.OutputSettings

/**
 * Which EXIF tags travel from the original to the export. Values are the standard EXIF tag
 * names (identical to androidx ExifInterface.TAG_* constants), kept as plain strings so the
 * policy is unit-testable on the JVM.
 *
 * Deliberately *not* copied:
 *  - Orientation: pixels are exported upright, so the tag is always written as "normal".
 *  - Dimensions: written from the actual exported bitmap.
 *  - Embedded thumbnail, maker notes, XMP: may describe the un-watermarked original or contain
 *    edit history/location; they are dropped.
 */
object MetadataPolicy {
    val DATE_TAGS = listOf(
        "DateTime", "DateTimeOriginal", "DateTimeDigitized",
        "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
    )

    val CAMERA_TAGS = listOf(
        "Make", "Model", "LensMake", "LensModel", "BodySerialNumber",
        "FNumber", "ExposureTime", "PhotographicSensitivity", "FocalLength",
        "FocalLengthIn35mmFilm", "ExposureBiasValue", "ExposureProgram", "MeteringMode",
        "Flash", "WhiteBalance", "ApertureValue", "ShutterSpeedValue",
        "Artist", "Copyright", "ImageDescription", "ColorSpace",
    )

    val LOCATION_TAGS = listOf(
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude",
        "GPSAltitudeRef", "GPSAltitude", "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod",
        "GPSSpeedRef", "GPSSpeed", "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum",
        "GPSHPositioningError",
    )

    fun tagsToCopy(settings: OutputSettings): List<String> = when {
        !settings.keepMetadata -> emptyList()
        settings.stripLocation -> DATE_TAGS + CAMERA_TAGS
        else -> DATE_TAGS + CAMERA_TAGS + LOCATION_TAGS
    }
}
