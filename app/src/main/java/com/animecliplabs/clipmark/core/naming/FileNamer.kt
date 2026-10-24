package com.animecliplabs.clipmark.core.naming

/**
 * Output names: `sunset.jpg` → `sunset_watermarked.jpg`, then `sunset_watermarked_2.jpg`, …
 * Nothing is ever overwritten: a name is only used once [isTaken] says it is free.
 */
object FileNamer {
    const val SUFFIX = "_watermarked"
    private const val MAX_BASE_LENGTH = 80
    private val ILLEGAL = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val OPAQUE_ID = Regex("""\d+""")
    private val KNOWN_EXTENSION = Regex("""\.(jpe?g|png|webp|heic|heif|avif|gif|bmp|dng)$""", RegexOption.IGNORE_CASE)

    /**
     * Base name without extension, safe for any filesystem. [fallback] is used when nothing usable
     * remains — including when the source only exposes an opaque id: newer Photo Pickers report
     * a bare number (e.g. "47") instead of the real file name, which would make a meaningless
     * "47_watermarked.jpg".
     */
    fun baseName(sourceDisplayName: String?, fallback: String): String {
        if (sourceDisplayName != null && OPAQUE_ID.matches(sourceDisplayName.trim())) return fallback
        val cleaned = sourceDisplayName
            ?.replace(KNOWN_EXTENSION, "")
            ?.replace(ILLEGAL, "_")
            ?.trim()
            ?.trim('.', ' ')
            ?.take(MAX_BASE_LENGTH)
            .orEmpty()
        return cleaned.ifEmpty { fallback }
    }

    fun candidate(base: String, extension: String, attempt: Int): String =
        if (attempt <= 1) "$base$SUFFIX.$extension" else "$base${SUFFIX}_$attempt.$extension"

    fun uniqueName(base: String, extension: String, maxAttempts: Int = 10_000, isTaken: (String) -> Boolean): String {
        for (attempt in 1..maxAttempts) {
            val name = candidate(base, extension, attempt)
            if (!isTaken(name)) return name
        }
        // Practically unreachable; still never collide.
        return "$base${SUFFIX}_${System.currentTimeMillis()}.$extension"
    }
}
