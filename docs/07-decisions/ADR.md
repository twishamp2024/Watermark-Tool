# Architecture Decision Records — ClipMark

The repository has no version history (it is not a git repository), so the dates and authorship of these
decisions are **unknown**. Each record is reconstructed from evidence in the code comments, the build files
or [DESIGN.md](../DESIGN.md), and cites that source. Nothing here is invented rationale. Where the reason is not
written down, the record says so.

New decisions should be appended with the next number.

---

## ADR-001: Minimum SDK 29, with no storage permission

**Status:** Accepted

**Context:** The app must save to the shared gallery and read user-selected photos.

**Decision:** `minSdk = 29`. Photos come from the Photo Picker or share intents, and outputs are written with MediaStore
`RELATIVE_PATH` + `IS_PENDING`.

**Consequences:** The app needs no storage or media permission. Android 9 and lower are unsupported. Picker file names are
hidden (see ADR-009).

**Source:** Comment in `app/build.gradle.kts`, the manifest comment, and the README "Minimum Android version" section.

---

## ADR-002: No DI framework and no Room; a hand-written `AppContainer`

**Status:** Accepted

**Context:** The app has a handful of singletons and small amounts of persisted data.

**Decision:** Wire dependencies manually in `AppContainer`, and persist with DataStore Preferences (JSON values) plus one job file.

**Consequences:** No annotation processing and a fast build. Schema evolution relies on tolerant JSON rather than migrations
(see [DATA_STORAGE.md](../02-architecture/DATA_STORAGE.md#4-serialization-rules-compatibility-contract)).

**Source:** KDoc on `AppContainer` ("a DI framework would add more ceremony than it removes"), README Architecture, and DESIGN.md §5.

---

## ADR-003: Pure-Kotlin `core` package tested on the JVM

**Status:** Accepted

**Context:** Placement, readability, naming and batch semantics are the risky logic, and they must be tested quickly.

**Decision:** Keep this logic in `core/` with no Android imports, and inject platform services through `fun interface`s
(`TextMeasurer`, `ItemProcessor`, `JobSink`).

**Consequences:** 63 fast JVM tests. The rule is enforced only by convention, because `core` is a package, not a Gradle module.

**Source:** README Architecture ("Pure Kotlin — no Android imports") and DESIGN.md §5.

---

## ADR-004: Size and margins relative to the short edge

**Status:** Accepted

**Decision:** Text size and margins are percentages of `min(width, height)`. Custom positions are fractions of the image.

**Consequences:** Branding looks the same across resolutions, and preview/export parity follows. Custom positions land
proportionally (not pixel-identically) on different aspect ratios, which is documented in the README Known limitations.

**Source:** KDoc on `WatermarkStyle` and DESIGN.md §1 ("Consistent").

---

## ADR-005: One renderer for preview and export

**Status:** Accepted

**Decision:** `WatermarkRenderer.draw` (Android `Canvas`) is used on the export bitmap and, through `nativeCanvas`, in Compose previews.
Smart Corner decisions made on the preview are passed to the batch as `resolvedAnchor`.

**Consequences:** What the user sees is what is saved. Compose text APIs are not used for the watermark.

**Source:** KDoc on `WatermarkRenderer`, and the comment in `EditorViewModel.saveImages`.

---

## ADR-006: Android system fonts only

**Status:** Accepted

**Decision:** Seven system families (`sans-serif`, `sans-serif-medium`, `sans-serif-condensed`, `serif`, `monospace`, `cursive`, `casual`).

**Consequences:** No APK weight or downloads, and identical typefaces in preview and export. Limited typographic choice.
Rendering can differ between OEM font sets. That last point is not documented in the source; it is an observation.

**Source:** KDoc on `WatermarkFont`.

---

## ADR-007: A persisted job file + WorkManager foreground worker for batches

**Status:** Accepted

**Context:** Batches of up to ~100 photos must survive app switching and process death without redoing saved photos.

**Decision:** Write `files/batch/current_batch.json` atomically after every image. Run a unique, expedited WorkManager job
(`watermark-batch`, `KEEP`) as a foreground service. Process images sequentially. Persist the Picker URI grants for the batch's duration.

**Consequences:** Resumable batches with at most one full-size bitmap in memory. Only one batch runs at a time. The merged
manifest gains WorkManager's `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`.

**Source:** KDoc on `BatchJob`, `BatchRunner` and `BatchWorker`, and README "How batch processing works".

---

## ADR-008: Output format follows the original, with lossless or safe fallbacks

**Status:** Accepted

**Decision:** Keep JPEG/PNG/WebP. GIF/BMP → PNG. HEIC/AVIF/unknown → JPEG. Anything with alpha → PNG, even when JPEG is forced.
The default quality is 95.

**Consequences:** No transparency loss. HEIC users get JPEG files. Animated images become stills.

**Source:** KDoc on `OutputFormatResolver` and README "How images are processed".

---

## ADR-009: Don't use Photo Picker item IDs as file names

**Status:** Accepted

**Context:** The Photo Picker reports opaque names such as `41.jpg`.

**Decision:** Treat the display names of `media/picker` URIs as unknown, and fall back to `animecliplabs_<yyyyMMdd_HHmmss>_watermarked.<ext>`.
Bare numeric names are also treated as opaque.

**Consequences:** Meaningful but non-original names for Picker input. Shared-in files keep their real names. Requesting a
gallery-wide permission to get real names was rejected.

**Source:** Comments in `ImageLoader.meta` and `FileNamer.baseName`, and README Known limitations.

---

## ADR-010: A whitelist for metadata, with location stripped by default

**Status:** Accepted

**Decision:** Copy only the date and camera EXIF tags listed in `MetadataPolicy`. GPS is copied only when the user allows it.
Orientation is always normalised. Thumbnail, maker notes and XMP are dropped.

**Consequences:** Public posts don't leak location. XMP/IPTC data is lost.

**Source:** KDoc on `MetadataPolicy` and DESIGN.md §2.

---

## ADR-011: Offline only (no `INTERNET` permission)

**Status:** Accepted

**Decision:** The app declares no network permission and has no accounts, analytics or cloud sync.

**Consequences:** Privacy is guaranteed by the platform. Any future feature that needs the network would reverse a promise shown in the UI
(`privacy_body`: "No uploads, no account, no analytics").

**Source:** README Permissions, DESIGN.md §2 ("Deliberately left out"), and `strings.xml`.

---

## ADR-012: Release builds signed with the debug key

**Status:** Accepted (temporary by the author's own description)

**Decision:** `release.signingConfig = debug`.

**Consequences:** The APK installs directly as a personal sideloaded utility. It must be replaced before any distribution
(see [DEPLOYMENT.md](../06-deployment/DEPLOYMENT.md#2-signing-and-secrets)).

**Source:** Comment in `app/build.gradle.kts`.

---

## ADR-013: AGP 9 built-in Kotlin; the Kotlin plugin declared but not applied

**Status:** Accepted

**Decision:** Let AGP compile Kotlin. The root build declares `org.jetbrains.kotlin.android` with `apply false` only to pin the
Kotlin Gradle Plugin version, so that it matches the Compose compiler plugin.

**Source:** Comment in the root `build.gradle.kts`.
