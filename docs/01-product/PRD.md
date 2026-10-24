# PRD — ClipMark

Product requirements for ClipMark, an offline Android app that adds a subtle text watermark to
photos. The *reasons* behind each feature are recorded in [DESIGN.md](../DESIGN.md) and are not
repeated here. For testable requirements with IDs, see [SRS.md](SRS.md).

Status labels: **IMPLEMENTED**, **PLANNED**, **UNKNOWN / REQUIRES PRODUCT DECISION**.

## 1. Purpose and problem

The problem, as quoted in DESIGN.md §1: *"I repeatedly create visual content and need consistent,
subtle branding with almost zero friction."*

ClipMark stamps a creator handle (default `@animecliplabs`) onto one photo or many. It saves new
copies to the gallery and never modifies the originals.

## 2. Target users

| User | Evidence in repo |
|---|---|
| A single creator who posts to Instagram under **@animecliplabs** | Default text, package `com.animecliplabs.clipmark`, output folder `AnimeClips Watermarked`, "Share all (for an Instagram carousel)" |
| Other creators | UNKNOWN / REQUIRES PRODUCT DECISION. The watermark text is editable, but branding, naming and the folder are specific to @animecliplabs. |

The release build is signed with the debug key "for sideloading" ([app/build.gradle.kts](../../app/build.gradle.kts)).
Today this is a **personal utility**, not a store product.

## 3. Goals

1. **Near-zero friction for repeated use.** One tap (Quick Watermark) → picker → saved. The last preset is remembered.
2. **Consistency.** The watermark looks the same at any resolution or aspect ratio.
3. **Subtlety.** The default is small (2.6 % of the short edge), white, 80 % opacity, in the bottom-right corner, with a soft shadow.
4. **Safety.** Originals are never changed or overwritten, and location is stripped by default.
5. **Privacy.** Everything is on-device. The app has no network permission, accounts or analytics.

## 4. Features

| Feature | Status | Where |
|---|---|---|
| Quick Watermark (pick → saved with the active preset, no preview) | IMPLEMENTED | `HomeViewModel.quickWatermark` |
| Select & preview editor: live preview, drag with snapping, hold to compare, per-photo overrides | IMPLEMENTED | `ui/editor/` |
| Share target (`SEND` / `SEND_MULTIPLE`, `image/*`) | IMPLEMENTED | `AndroidManifest.xml`, `MainActivity` |
| Background batch with progress notification, cancel, resume and retry | IMPLEMENTED | `data/BatchRepository`, `work/BatchWorker`, `core/batch/BatchRunner` |
| Named presets: create, update, rename, duplicate, delete (last one protected); active preset remembered | IMPLEMENTED | `data/Repositories.kt` (`PresetRepository`) |
| 9 anchor positions + custom dragged position | IMPLEMENTED | `core/layout/` |
| Smart Corner (optional, off by default) | IMPLEMENTED | `core/analysis/SmartCorner.kt` |
| Contrast Assist (on by default) | IMPLEMENTED | `core/analysis/Readability.kt` |
| 7 system fonts, bold, 8 colour swatches, opacity, shadow, outline, margins | IMPLEMENTED | `core/model/WatermarkStyle.kt`, `ui/editor/EditorControls.kt` |
| Output format: match original / always JPEG / always PNG; quality 95 or 100 | IMPLEMENTED | `ui/settings/`, `core/format/` |
| Metadata: keep camera info & dates (default on), strip location (default on) | IMPLEMENTED | `core/metadata/MetadataPolicy.kt` |
| Result screen: Share all / Open / Process more / Retry unsaved | IMPLEMENTED | `ui/batch/BatchScreen.kt` |
| History: per-day counts and links to exports; no image copies | IMPLEMENTED | `core/batch/History.kt`, `ui/history/` |
| Light/dark theme, landscape editor layout | IMPLEMENTED | `ui/theme/Theme.kt`, `EditorScreen.kt` |
| Image/logo watermarks, filters, cropping, resizing, cloud sync, accounts, analytics, font downloads | **Out of scope** (DESIGN.md §2) | — |
| Localisation beyond English | UNKNOWN / REQUIRES PRODUCT DECISION (only `values/strings.xml` exists) | — |
| Play Store distribution | UNKNOWN / REQUIRES PRODUCT DECISION | — |

No PLANNED features are recorded anywhere in the repository. There are no TODOs, issue links or a roadmap.

## 5. User stories

1. As a creator, I tap **Quick Watermark**, pick 20 photos, and they are saved with my usual watermark without further questions.
2. As a creator, I preview a set of photos, nudge the watermark on one awkward photo only, and save them all.
3. As a creator in my gallery app, I use **Share → Watermark with ClipMark** without opening ClipMark first.
4. As a creator, I start a 100-photo batch, switch to Instagram, and get notified when it is done.
5. As a creator, if the app is killed mid-batch, photos already saved are not redone, and I can resume.
6. As a creator, after saving I tap **Share all** to post the set as a carousel.
7. As a creator, I keep several presets (e.g. light and dark variants), and the last one I used is ready next time.
8. As a creator, I trust that no GPS location leaks into posted photos unless I allow it.

## 6. Requirements

Functional and non-functional requirements are specified in [SRS.md](SRS.md).

## 7. Constraints

* Android 10 (API 29) or newer. Target/compile API 37.
* No storage or media permission may be required (Photo Picker + MediaStore only).
* No `INTERNET` permission.
* Fonts are limited to Android system families.
* Output encoders are limited to what Android offers: JPEG, PNG and WebP.

## 8. Success criteria

The only verifiable acceptance criteria are the automated tests (see
[TEST_PLAN.md](../05-testing/TEST_PLAN.md)), e.g. "the default preset is exactly the brief" and "fifteen
photos, one bad → fourteen saved".

Product metrics (adoption, time-to-post, retention): **UNKNOWN / REQUIRES PRODUCT DECISION**. The app
collects no analytics by design, so any metric would have to be gathered manually.
