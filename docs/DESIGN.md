# ClipMark — design notes

The reasoning behind the app, written before and during implementation. The README covers
*how* things work; this covers *why*.

## 1. The problem

> "I repeatedly create visual content and need consistent, subtle branding with almost zero friction."

Three words carry the design:

* **Repeatedly** → the second, tenth and hundredth use matter more than the first. Settings must
  be remembered; the fastest path must need no decisions.
* **Consistent** → the mark must look the same on a 1080×1350 post, a 16:9 screenshot and a 4K
  frame. That rules out pixel-based sizes and margins.
* **Subtle** → small, semi-transparent, in a corner, readable without shouting.

## 2. Feature set

| Feature | Why it earns its place |
|---|---|
| Quick Watermark (pick → saved) | The whole product in one tap for the 90 % case. |
| Select & preview | Trust: see exactly what will be saved, per photo. |
| Share-sheet target | Creators start in the gallery; Gallery → Share → ClipMark skips opening the app. |
| Sizes/margins as % of short edge | Identical branding on every resolution and aspect ratio (consistency). |
| 9 quick positions + drag with snapping | Fast and precise; snapping pulls back to positions that stay consistent. |
| Batch Preset Lock ("Same watermark on all N") | Guarantees identical branding; unlock to fix one awkward photo. |
| Smart Corner (optional) | Avoids covering the subject; *only* moves the watermark. |
| Contrast Assist (on by default) | Readable on bright skies and dark scenes without the user thinking about it. |
| Presets + remembered active preset | "Tomorrow, @animecliplabs is already ready." |
| Background batch with notification, cancel, resume | 100 photos while you switch to Instagram; nothing lost on a crash. |
| Result screen: Share all / Open / Process more | The next step after saving is almost always posting. |
| Duplicate-safe names in a dedicated folder | Never overwrite; never dump files in Downloads. |
| Location stripped by default | Public posts should not leak where a photo was taken. |
| Lightweight history (counts + links) | "Did I already do yesterday's batch?", without storing copies. |

Deliberately **left out**: filters, cropping, resizing, image watermarks/logos, cloud sync,
accounts, analytics, fonts downloads, per-image text editing beyond the lock toggle, a "templates
store". Each fails the "faster, safer, or more professional?" test or adds weight/permissions.

## 3. MVP vs polish

MVP (must work flawlessly): pick → preview → save, batch, presets, original-preserving export.
Polish layered on top: Smart Corner, Contrast Assist, snapping guides + haptics, compare-hold,
success animation, resumable batches, share target, history.

## 4. UX & navigation

```
Home ──Quick Watermark──▶ Photo Picker ──▶ Batch (progress → result)
  │ └─Select & preview──▶ Photo Picker ──▶ Editor ──Save N──▶ Batch
  ├─Adjust / New preset─▶ Editor (sample image, preset mode)
  ├─Presets / History / Settings
Gallery share sheet ──────────────────────▶ Editor
```

* **Home** leads with the *active preset rendered live* on a sample picture, so the user sees
  what "Quick" will do before tapping it.
* **Editor**: preview on top (thumb-reachable controls below), three tabs — Position / Style /
  Readability — to avoid a wall of sliders. Landscape puts preview and controls side by side.
* **Batch**: one screen that morphs running → done (or paused), so there is no navigation
  decision after saving.

## 5. Architecture

```
ui (Compose screens + ViewModels, StateFlow)
  │
data (repositories: presets, settings, history [DataStore]; batch job file + WorkManager)
  │
image (Android adapters: ImageDecoder loader, Canvas renderer, analyzer, EXIF copier, MediaStore store, processor)
  │
core (pure Kotlin: layout, snapping, smart corner, readability, naming, formats, metadata policy, batch runner)
```

`core` has no Android imports, so the logic that decides *where and how* the watermark is drawn,
file names, and batch behaviour is covered by fast JVM tests. There is no Hilt or Room: a
hand-written container and two small persistence mechanisms are enough.

## 6. Image pipeline

1. `ImageDecoder` → mutable software ARGB bitmap, **upright** (EXIF orientation applied), at full
   resolution unless the memory budget (≈35 % of available RAM, max 512 MB) forbids it.
2. `WatermarkAnalyzer` samples a coarse grid inside candidate boxes (Smart Corner) and under the
   final box (Contrast Assist). Read-only.
3. `WatermarkRenderer.draw` draws text into a *bounded* layer with the chosen opacity.
4. `Bitmap.compress` into a private temp file — PNG lossless, JPEG/WebP at quality 95 (or 100).
5. `ExifCopier` copies whitelisted tags, writes Orientation = normal and real dimensions.
6. `MediaStoreOutputStore` inserts with `IS_PENDING=1`, streams, then publishes.

The preview uses the same `WatermarkRenderer` on a downsampled bitmap; because layout is relative
to the surface, the preview is an exact scaled rendition of the export. The preview bitmap is
never exported.

## 7. Technical risks and how they were handled

| Risk | Mitigation | Verified by |
|---|---|---|
| EXIF-rotated JPEGs exported sideways or double-rotated | Decode upright + write Orientation=1 | `exifRotatedPhotoIsExportedUprightWithoutDoubleRotation` |
| OOM on 50–108 MP images | Pixel budget from available RAM, OOM retry at ¼ size, reported to user | `hugeImagesAreReducedOnlyWhenMemoryRequiresIt_keepingAspect` |
| Photo changed beyond the watermark | Draw on decoded pixels only inside a bounded layer; PNG lossless | `pngPixelsOutsideTheWatermarkAreBitIdentical` |
| Overwriting files | Unique names checked against MediaStore + in-batch reservations; MediaStore also uniquifies | `sameNameFromDifferentFoldersNeverCollides`, `savesIntoTheGalleryFolderWithDuplicateSafety` |
| One bad file kills the batch | Per-item outcome, exceptions converted to failures | `batchSurvivesABadImage`, `BatchRunnerTest` |
| Process death mid-batch | Job file saved after every image; WorkManager re-runs; done items skipped; picker grants persisted | `BatchRunnerTest` (resume, system stop) |
| Preview ≠ export | Same renderer, relative layout; Smart Corner choice from preview carried into the job | `WatermarkLayoutTest` (preview vs export) |
| Transparency flattened to black by JPEG | Alpha images always PNG | `transparencyIsPreserved` |
