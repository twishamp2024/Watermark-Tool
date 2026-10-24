package com.animecliplabs.clipmark.core.batch

import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.OutputSettings
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import kotlinx.serialization.Serializable

@Serializable
enum class ItemStatus { PENDING, DONE, FAILED, SKIPPED, CANCELLED }

@Serializable
enum class FailureKind {
    /** Corrupted or truncated file. */
    UNREADABLE,
    /** A format Android cannot decode. */
    UNSUPPORTED,
    /** The photo permission expired (e.g. after a reboot) or was revoked. */
    ACCESS_LOST,
    /** The original was deleted or moved. */
    NOT_FOUND,
    /** Not enough memory even for a reduced decode. */
    TOO_LARGE,
    STORAGE_FULL,
    WRITE_FAILED,
    UNKNOWN,
}

/** Non-fatal things worth telling the user about a successfully saved image. */
@Serializable
enum class ItemNote {
    DOWNSCALED_FOR_MEMORY,
    METADATA_NOT_COPIED,
    SHRUNK_TO_FIT,
}

@Serializable
data class BatchItem(
    val sourceUri: String,
    val sourceName: String? = null,
    /** Set when "Same watermark on all" was off and this image was adjusted individually. */
    val styleOverride: WatermarkStyle? = null,
    /** Smart Corner decision already shown in the preview, so the export matches it exactly. */
    val resolvedAnchor: Anchor? = null,
    val status: ItemStatus = ItemStatus.PENDING,
    val outputUri: String? = null,
    val outputName: String? = null,
    val failure: FailureKind? = null,
    val notes: List<ItemNote> = emptyList(),
)

/**
 * A batch is persisted as a small JSON file and updated after every image. That makes it
 * resumable: if the process dies, the worker restarts and simply skips items already DONE.
 */
@Serializable
data class BatchJob(
    val id: String,
    val createdAt: Long,
    val presetName: String,
    val style: WatermarkStyle,
    val output: OutputSettings,
    val items: List<BatchItem>,
    val finishedAt: Long? = null,
    val cancelled: Boolean = false,
) {
    val total: Int get() = items.size
    val savedCount: Int get() = items.count { it.status == ItemStatus.DONE }
    val failedCount: Int get() = items.count { it.status == ItemStatus.FAILED }
    val pendingCount: Int get() = items.count { it.status == ItemStatus.PENDING }
    val processedCount: Int get() = total - pendingCount
    val isFinished: Boolean get() = finishedAt != null
    val stoppedForStorage: Boolean get() = items.any { it.status == ItemStatus.SKIPPED }

    fun styleFor(item: BatchItem): WatermarkStyle = item.styleOverride ?: style

    fun withItem(index: Int, item: BatchItem): BatchJob =
        copy(items = items.toMutableList().also { it[index] = item })

    fun markPending(status: ItemStatus): BatchJob =
        copy(items = items.map { if (it.status == ItemStatus.PENDING) it.copy(status = status) else it })
}

sealed interface ItemOutcome {
    data class Saved(val uri: String, val name: String, val notes: List<ItemNote> = emptyList()) : ItemOutcome
    data class Failed(val kind: FailureKind) : ItemOutcome
}

data class BatchProgress(val processed: Int, val total: Int, val failed: Int, val currentName: String?)
