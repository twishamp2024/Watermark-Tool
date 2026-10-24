# SRS — ClipMark

The software requirements as implemented. Every requirement names the code that implements it
and, where one exists, the test that verifies it (`U:` = JVM unit test, `I:` = instrumented test in
`WatermarkPipelineTest`). Product context is in [PRD.md](PRD.md).

Status: **IMPLEMENTED** unless marked otherwise. No planned requirements exist in the repository.

## 1. Scope

A single-module Android application (`:app`, package `com.animecliplabs.clipmark`). It has no server
component, no network access and no database server. See [System Architecture](../02-architecture/SYSTEM_ARCHITECTURE.md).

## 2. Functional requirements

### 2.1 Input

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-IN-1 | Photos are selected through the system Photo Picker (images only, multiple). | `HomeScreen` (`PickMultipleVisualMedia`, `ImageOnly`) | Manual |
| FR-IN-2 | The app accepts `ACTION_SEND` / `ACTION_SEND_MULTIPLE` with `image/*` and opens the editor. | `AndroidManifest.xml`, `MainActivity.handle` | Manual |
| FR-IN-3 | The editor's selection survives process death. | `EditorViewModel` (`SavedStateHandle` key `uris`) | Manual |
| FR-IN-4 | Decoding applies EXIF orientation, so pixels are upright. | `ImageLoader` (`ImageDecoder`) | I: `exifRotatedPhotoIsExportedUprightWithoutDoubleRotation` |

### 2.2 Watermark layout and rendering

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-WM-1 | Text size and margins are percentages of the image's short edge. Size is clamped to 1.2–8 %, margins to 1–15 %. | `WatermarkLayout`, `WatermarkStyle` | U: `text size is a percentage of the short edge`, `out-of-range values are coerced…` |
| FR-WM-2 | The default style is `@animecliplabs`, sans-serif-medium, 2.6 %, white, 80 % opacity, bottom-right, 3.5 % margins, shadow on, Contrast Assist on, Smart Corner off. | `WatermarkStyle()` defaults | U: `the default preset is exactly the brief` |
| FR-WM-3 | The watermark box (text + 20 % padding) never leaves the safe area. On tiny images the text shrinks to fit, and the result is flagged `SHRUNK_TO_FIT`. | `WatermarkLayout.place` | U: `every quick position stays inside…`, `text shrinks to fit…` |
| FR-WM-4 | Nine anchors plus a custom centre, stored as a fraction of the image. | `Anchor`, `NormalizedPoint` | U: `WatermarkLayoutTest` |
| FR-WM-5 | Dragging snaps to the nearest anchor within 22 dp, otherwise to the centre lines. Snapping gives a haptic tick. Dragging turns Smart Corner off. | `Snapping.drag`, `EditorPreview` | U: `SnappingTest` |
| FR-WM-6 | Opacity applies to the whole watermark (fill + shadow + outline) through a bounded `saveLayer`. | `WatermarkRenderer.draw` | I: `pngPixelsOutsideTheWatermarkAreBitIdentical` |
| FR-WM-7 | Preview and export use the same renderer and relative layout. | `WatermarkRenderer`, `WatermarkOverlay` | U: `preview size and export size give the same relative placement` |
| FR-WM-8 | Contrast Assist: when WCAG contrast < 3.0 it adds a shadow; when < 1.6 it also adds an outline. The effect colour is black for text luminance > 0.4, otherwise white. The text colour never changes. | `ReadabilityResolver` | U: `AnalysisTest` |
| FR-WM-9 | Smart Corner scores each corner on texture, edges and contrast (saturating at 4.5:1). It leaves the preferred anchor only when another corner beats it by ≥ 0.06. | `SmartCorner`, `WatermarkAnalyzer` | U: `AnalysisTest` |
| FR-WM-10 | A Smart Corner decision shown in the preview is carried into the batch, so the export matches. | `EditorViewModel.saveImages` → `BatchItem.resolvedAnchor` | Manual |
| FR-WM-11 | With "Same watermark on all" off, edits apply only to the current photo (`styleOverride`). Turning it back on clears the overrides. | `EditorViewModel.updateStyle`, `setLockAll` | Manual |

### 2.3 Output

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-OUT-1 | The original is never written to. | `WatermarkProcessor` (read-only open) | I: `originalFileIsNeverModified` |
| FR-OUT-2 | Output keeps full resolution unless the memory budget forces a reduced decode, which is reported as `DOWNSCALED_FOR_MEMORY`. | `ImageLoader.decodeFull`, `pixelBudget` | I: `jpegKeepsFullResolution…`, `hugeImagesAreReduced…` |
| FR-OUT-3 | Format mapping: JPEG→JPEG, PNG/GIF/BMP→PNG, WebP→WebP, other→JPEG (PNG if alpha). A forced JPEG still becomes PNG for images with alpha. | `OutputFormatResolver` | U: `OutputFormatResolverTest`; I: `transparencyIsPreserved`, `webpStaysWebp…` |
| FR-OUT-4 | JPEG/WebP quality is 95 (default) or 100. | `OutputSettings`, `WatermarkProcessor.encode` | — |
| FR-OUT-5 | Saved via MediaStore to `Pictures/AnimeClips Watermarked/` with `IS_PENDING` while writing. | `MediaStoreOutputStore` | I: `savesIntoTheGalleryFolderWithDuplicateSafety` |
| FR-OUT-6 | Names follow `<base>_watermarked.<ext>`, then `_2`, `_3`…, checked against MediaStore and names already reserved in the batch. Unsafe characters become `_`, the base is capped at 80 characters, and opaque Photo Picker IDs fall back to `animecliplabs_<yyyyMMdd_HHmmss>`. | `FileNamer`, `ImageLoader.meta` | U: `FileNamerTest`; I: `sameNameFromDifferentFoldersNeverCollides` |

### 2.4 Metadata

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-MD-1 | When "Keep camera info & dates" is on (default), whitelisted date and camera EXIF tags are copied. | `MetadataPolicy`, `ExifCopier` | U: `HistoryAndMetadataTest` |
| FR-MD-2 | GPS tags are copied only when "Remove location" is off (default on). | `MetadataPolicy.tagsToCopy` | U + I: `locationIsKeptOnlyWhenAllowed` |
| FR-MD-3 | When tags are copied, `Orientation=1` and the real pixel dimensions are written. If the source has none of the whitelisted tags, no EXIF is written at all. | `ExifCopier.copy` | I: `exifRotated…` |
| FR-MD-4 | A metadata failure never fails the image. It is noted as `METADATA_NOT_COPIED`. | `WatermarkProcessor` | — |

### 2.5 Batch processing

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-B-1 | Images are processed sequentially, one full-resolution bitmap at a time. | `BatchRunner.run` | U: `BatchRunnerTest` |
| FR-B-2 | The job state is persisted atomically after every image. | `BatchJobStore.save` (temp + rename) | U: `progress is persisted after every image…` |
| FR-B-3 | A failure (or exception) affects only that item and is classified into a `FailureKind`. | `BatchRunner`, `WatermarkProcessor.classifyRead/Write` | U: `fifteen photos, one bad…`; I: `batchSurvivesABadImage`, `corruptedAndTruncated…`, `missingFile…` |
| FR-B-4 | Storage full stops the batch, and the remaining items become `SKIPPED`. A pre-encode check requires free space > 2× estimate + 32 MB. | `BatchRunner`, `WatermarkProcessor.hasRoomFor` | U: `a full disk stops early…` |
| FR-B-5 | A user cancel keeps saved items and marks the rest `CANCELLED`. A system stop leaves the job resumable. | `BatchRunner`, `BatchWorker.isUserCancel` | U: `user cancel…`, `a system stop…` |
| FR-B-6 | Resume skips `DONE` items. Retry re-runs every non-`DONE` item as a new job. | `BatchRepository.resume/retryUnsaved` | U: `resuming skips…` |
| FR-B-7 | Only one batch runs at a time (unique work `watermark-batch`, `KEEP`). Starting a second shows "A batch is still running". | `BatchRepository.start` | Manual |
| FR-B-8 | Photo Picker read grants are persisted when a batch starts and released when it finishes. | `BatchRepository.persistReadAccess/releaseReadAccess` | Manual |
| FR-B-9 | The progress notification shows "Watermarking N of M" with a Cancel action. A finish notification appears only when notifications are permitted. | `BatchNotifications` | Manual |

### 2.6 Presets, settings, history

| ID | Requirement | Implementation | Verified by |
|---|---|---|---|
| FR-P-1 | At least one preset always exists. Deleting the last one is refused, and corrupt storage falls back to the default. | `PresetRepository.delete`, `PresetCodec.decode` | U: `corrupt or empty storage…` |
| FR-P-2 | The active (last-used) preset is persisted and used by Quick Watermark. | `PresetRepository.activePreset` | — |
| FR-S-1 | Stored JSON tolerates unknown keys, missing keys and unknown enum values. | `AppJson` | U: `older saved data…`, `data from a newer version…` |
| FR-H-1 | History keeps at most 60 entries, each with up to 100 output URIs, and no image data. It is grouped by day as Today/Yesterday/date. | `HistoryGrouping` | U: `history stays small…`, `history groups by day…` |
| FR-H-2 | Sharing from history includes only exports that still exist. | `HistoryViewModel.existingOutputs` | — |

## 3. Non-functional requirements

| ID | Category | Requirement | Evidence |
|---|---|---|---|
| NFR-1 | Privacy | No `INTERNET` permission. No accounts or analytics. | Merged release manifest (see [DEPLOYMENT.md](../06-deployment/DEPLOYMENT.md#6-permissions-in-the-shipped-apk)) |
| NFR-2 | Privacy | GPS is stripped from exports by default. | FR-MD-2 |
| NFR-3 | Security | No storage or media permission is needed. Access is per-photo via the Picker. | Manifest |
| NFR-4 | Memory | The export pixel budget is min(35 % of available RAM, 512 MB) / 4 bytes, but at least 12 MP. An OOM triggers a retry at ¼ of the budget. The preview decode is ≤ 2048 px. The thumbnail cache is 24 MB. | `ImageLoader` |
| NFR-5 | Reliability | The job survives process death. WorkManager is expedited, falling back to a normal request. | `BatchRepository.enqueue` |
| NFR-6 | Reliability | Stored settings never crash the app (tolerant decode + defaults). | FR-S-1 |
| NFR-7 | Performance | Analysis samples a 24×8 grid per candidate box, and editor analysis is debounced by 60 ms. | `WatermarkAnalyzer`, `EditorViewModel` |
| NFR-8 | Performance | No numeric throughput or latency targets are defined. **UNKNOWN / REQUIRES PRODUCT DECISION.** | — |
| NFR-9 | Compatibility | minSdk 29, targetSdk / compileSdk 37. Foreground service type `mediaProcessing` on API 35+, `dataSync` below. | `app/build.gradle.kts`, `BatchNotifications` |
| NFR-10 | Accessibility | The preview has a content description, and the selected/Smart/adjusted states are labelled. There is no formal accessibility target. | `strings.xml` (`a11y_*`) |
| NFR-11 | Backup | Only `files/datastore/` (presets, settings, history) is included in cloud backup and device transfer. | `res/xml/*_rules.xml` |

## 4. External interfaces

| Interface | Direction | Contract |
|---|---|---|
| Launcher intent | In | `MAIN`/`LAUNCHER` → `MainActivity` |
| Share intent | In | `SEND` / `SEND_MULTIPLE`, `image/*`, `EXTRA_STREAM` URIs |
| Notification tap | In | `MainActivity` with boolean extra `open_batch=true` → Batch screen |
| Photo Picker | In | `content://media/picker/...` URIs. Display names are treated as unknown. |
| MediaStore | Out | Inserts into `MediaStore.Images` (`VOLUME_EXTERNAL_PRIMARY`), `RELATIVE_PATH = Pictures/AnimeClips Watermarked/` |
| Share / view | Out | `ACTION_SEND(_MULTIPLE)` chooser and `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION` (`ui/common/Intents.kt`) |

The app has no HTTP API, no IPC service and no exported content provider.

## 5. Dependencies

These are listed in [gradle/libs.versions.toml](../../gradle/libs.versions.toml): AndroidX core, activity-compose,
lifecycle, navigation-compose, WorkManager, DataStore Preferences, ExifInterface, Compose (BOM) + Material 3,
kotlinx-serialization-json and kotlinx-coroutines. None of them are network services.

## 6. Known limitations

These are listed in the [README](../../README.md#known-limitations) and not repeated here.
