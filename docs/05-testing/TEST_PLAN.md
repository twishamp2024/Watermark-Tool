# Test Plan — ClipMark

Status labels: **IMPLEMENTED**, **PLANNED**, **NOT PRESENT**. Counts were taken from the source on 2026-09-24.

## 1. Strategy

Decision logic lives in `core/` (pure Kotlin) and is tested exhaustively on the JVM. The Android image
pipeline is tested end-to-end on a device with **generated** images, so no binary fixtures are checked in.
The UI is verified manually.

| Level | Status | Location | Run with |
|---|---|---|---|
| Unit (JVM) | IMPLEMENTED: 63 tests in 8 classes | `app/src/test/` | `./gradlew testDebugUnitTest` |
| Integration / pipeline (instrumented) | IMPLEMENTED: 13 tests | `app/src/androidTest/.../WatermarkPipelineTest.kt` | `./gradlew connectedDebugAndroidTest` |
| UI tests (Compose) | NOT PRESENT (the `compose-ui-test-junit4` dependency is declared but unused) | — | — |
| System / end-to-end (picker → gallery) | Manual only, see §4 | — | — |
| Performance / memory benchmarks | NOT PRESENT (only the memory-budget logic is tested) | — | — |
| Security testing | NOT PRESENT as a suite; privacy properties are covered by metadata tests and the manifest | — | — |
| CI | NOT PRESENT (no CI configuration in the repo) | — | — |
| Coverage reporting | NOT CONFIGURED (`app/build/outputs/code_coverage` exists from a connected run, but no coverage task or threshold is defined) | — | — |

## 2. Unit tests (`app/src/test`)

`FakeMeasurer` (`TestSupport.kt`) replaces `Paint`: each glyph is 0.55 × size wide, ascent 0.8, descent 0.2.

| Class | Tests | Covers |
|---|---|---|
| `core/layout/WatermarkLayoutTest` | 14 | Default placement on margins; all anchors inside the safe area on several aspect ratios; resolution and preview/export parity; clamping; custom centre; anchor override; shrink-to-fit; effects don't move the box; value coercion; blank text |
| `core/layout/SnappingTest` | 4 | Anchor snap, Smart Corner turned off on drag, centre-line snap, stored clamped position |
| `core/analysis/AnalysisTest` | 11 | WCAG maths, region stats, Smart Corner choices and hysteresis, Contrast Assist both directions, assist off |
| `core/naming/FileNamerTest` | 9 | Suffix, counters, extension, unsafe characters, fallbacks, opaque Picker IDs, dots, truncation |
| `core/format/OutputFormatResolverTest` | 5 | Container mapping, fallbacks, transparency, explicit preference, case-insensitive MIME |
| `core/batch/BatchRunnerTest` | 9 | Bad-item isolation, per-image persistence, exception isolation, resume, name reservation, storage-full stop, user cancel vs system stop, progress |
| `core/batch/HistoryAndMetadataTest` | 4 | Day grouping, caps, metadata whitelist, location handling |
| `data/PresetCodecTest` | 7 | Default preset = brief, round-trip, old/new schema tolerance, corrupt storage, default output settings, batch job round-trip |

## 3. Instrumented tests (`WatermarkPipelineTest`)

These run the real `WatermarkProcessor` with `ImageLoader`, `WatermarkRenderer`, `WatermarkAnalyzer` and `ExifCopier`.
Most tests write to a temp folder through a `FolderStore` test double. One test uses the real `MediaStoreOutputStore`
and cleans up after itself.

| Test | Verifies |
|---|---|
| `jpegKeepsFullResolutionAspectAndFormat` | 4000×3000 JPEG stays 4000×3000 JPEG |
| `originalFileIsNeverModified` | SHA-256 of the source is unchanged |
| `pngPixelsOutsideTheWatermarkAreBitIdentical` | Lossless path touches only the watermark region |
| `webpStaysWebpAndKeepsSize` | WebP → WebP, same dimensions |
| `transparencyIsPreserved` | Alpha survives, even with JPEG forced |
| `exifRotatedPhotoIsExportedUprightWithoutDoubleRotation` | Rotate-90 JPEG → upright 300×400, Orientation = 1, dates/camera copied |
| `locationIsKeptOnlyWhenAllowed` | GPS stripped by default, kept when allowed |
| `corruptedAndTruncatedFilesFailGracefully` | `UNREADABLE`, no crash |
| `missingFileIsReportedAsNotFound` | `NOT_FOUND` |
| `batchSurvivesABadImage` | Runner + processor with a bad file in the middle |
| `sameNameFromDifferentFoldersNeverCollides` | `_2`, `_3`, no overwrite |
| `hugeImagesAreReducedOnlyWhenMemoryRequiresIt_keepingAspect` | Budget-driven downscale keeps aspect |
| `savesIntoTheGalleryFolderWithDuplicateSafety` | Real MediaStore insert in `Pictures/AnimeClips Watermarked/` |

**Test environment used:** a Pixel 8 AVD, API 37. Other API levels, especially the minSdk 29, have **not been verified**
according to the repository. **UNKNOWN / REQUIRES CONFIRMATION.**

## 4. Manual test checklist

These behaviours have no automated coverage. Use the images in `samples/`: 4000×3000 JPEG, 1920×1080 JPEG,
1080×1350 JPEG and 1080×1920 PNG.

- [ ] Quick Watermark: pick several photos, and they appear in `Pictures/AnimeClips Watermarked/` with no editor shown.
- [ ] First batch on Android 13+: the notification permission is asked exactly once. After a denial the batch still runs.
- [ ] Select & preview: drag snaps (with a haptic tick) and the dashed safe area appears; hold shows the original.
- [ ] Unlock "Same watermark on all", adjust one photo, and only that export differs. Re-locking clears the overrides and shows a message.
- [ ] Smart Corner on: the corner shown in the preview is the one in the export.
- [ ] Share from the gallery (single and multiple) opens the editor with those photos.
- [ ] During a batch, switch apps: the notification shows "Watermarking N of M". Cancel keeps the saved photos.
- [ ] Kill the process mid-batch: on reopen it either resumes automatically or Home shows "Batch paused — Resume". Nothing is saved twice.
- [ ] Result screen: Share all (multiple), Open, Process more (re-opens the picker), Retry N.
- [ ] Presets: create, rename, duplicate, delete; the last one can't be deleted; the active preset persists across restarts.
- [ ] Settings: format and quality options take effect; toggling "Keep camera info" and "Remove location" changes the EXIF in outputs.
- [ ] History: day grouping; sharing skips deleted exports; Clear.
- [ ] Rotate to landscape in the editor: side-by-side layout, and no state is lost.
- [ ] Dark theme renders correctly.

## 5. Regression policy

There is no formal policy in the repo. The recommended practice (see the [Development Guide](../04-development/DEVELOPMENT_GUIDE.md#8-workflow)):
run the unit tests on every change, and run the instrumented tests for changes to `image/`, `data/` or `work/`. A bug fix
in `core/` should come with a backtick-named unit test that reproduces it.

## 6. Gaps worth closing (PLANNED: suggestion only, not committed)

* Compose UI tests for the editor (lock/unlock, drag) and the batch screen states.
* A `WorkManager` test (`work-testing`) for `BatchWorker` cancel vs system-stop semantics. Today these are covered only at the `BatchRunner` level.
* An instrumented run on an API 29 emulator.
* A check that `core/` has no Android imports (for example, splitting `core` into a pure Kotlin/JVM Gradle module).
