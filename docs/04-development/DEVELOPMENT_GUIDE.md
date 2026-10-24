# Development Guide — ClipMark

## 1. Required tools

| Tool | Version | Source of truth |
|---|---|---|
| JDK | 17+ (Android Studio's bundled JBR works) | `compileOptions` / `jvmTarget` 17 in `app/build.gradle.kts` |
| Android SDK | Platform 37, build-tools 36.0.0 | `compileSdk = 37` |
| Gradle | 9.8.0 (via wrapper; do not install separately) | `gradle/wrapper/gradle-wrapper.properties` |
| AGP / Kotlin | AGP 9.4.1 with built-in Kotlin; Kotlin 2.4.20 | `gradle/libs.versions.toml` |
| Device / emulator | Android 10 (API 29) or newer; instrumented tests were run on an API 37 Pixel 8 AVD | README |

## 2. Setup

1. Clone or copy the project. It is not currently a git repository.
2. Create `local.properties` (it is ignored by `.gitignore`):
   ```properties
   sdk.dir=<path to Android SDK>
   ```
   Alternatively, set `ANDROID_HOME`.
3. Optionally set `GRADLE_USER_HOME` to keep caches off the system drive.

No environment variables, API keys or secrets are required.

**The original author's machine** uses the SDK at `D:\Android\Sdk`, Gradle home at `D:\Android\Gradle` and JDK at
`E:\AndroidStudio\jbr`. Details are in the [README build environment section](../../README.md#build-environment-used-for-this-project-non-system-drive).

## 3. Commands

```bash
./gradlew assembleDebug                 # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease               # minified + resource-shrunk, signed with the debug key
./gradlew testDebugUnitTest             # 63 JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest     # 13 instrumented tests (needs a device/emulator)
./gradlew installDebug                  # install on the connected device
```

On Windows PowerShell or cmd, use `.\gradlew.bat` or `gradlew`.

Gradle settings (`gradle.properties`): 4 GB heap, build cache and parallel builds enabled.

## 4. Project structure

```
app/src/main/java/com/animecliplabs/clipmark/
├── ClipMarkApplication.kt   AppContainer (manual DI), SelectionHolder
├── MainActivity.kt          intent handling (share, notification tap)
├── core/                    pure Kotlin logic, JVM-tested
├── image/                   Android image pipeline
├── data/                    DataStore repos, batch job file, WorkManager enqueue
├── work/                    BatchWorker, notifications
└── ui/                      Compose screens + ViewModels (home, editor, batch, presets, history, settings, common, theme)
app/src/test/                JVM unit tests (FakeMeasurer in TestSupport.kt)
app/src/androidTest/         WatermarkPipelineTest (instrumented)
samples/                     generated test images (4 sizes, JPEG + PNG) for manual testing
docs/                        documentation (see DOCUMENTATION_INDEX.md)
```

Layer responsibilities are described in [SYSTEM_ARCHITECTURE.md](../02-architecture/SYSTEM_ARCHITECTURE.md).

## 5. Where to make common changes

| Change | Start here | Also update |
|---|---|---|
| Default watermark look | `core/model/WatermarkStyle.kt` defaults | `PresetCodecTest` ("default preset is exactly the brief"), README, PRD |
| Placement / margins | `core/layout/WatermarkLayout.kt` | `WatermarkLayoutTest` |
| Contrast Assist thresholds | `core/analysis/Readability.kt` | `AnalysisTest` |
| Smart Corner scoring | `core/analysis/SmartCorner.kt` | `AnalysisTest` |
| Which EXIF tags are copied | `core/metadata/MetadataPolicy.kt` | `HistoryAndMetadataTest`, README metadata table |
| Output format rules | `core/format/OutputFormat.kt` | `OutputFormatResolverTest` |
| File naming | `core/naming/FileNamer.kt`, `image/ImageLoader.meta` | `FileNamerTest` |
| Output folder | `MediaStoreOutputStore.FOLDER_NAME` | README, strings, instrumented test |
| New persisted field | model class, with a default value | `PresetCodecTest`, [DATA_STORAGE.md](../02-architecture/DATA_STORAGE.md) |
| New user-visible text | `res/values/strings.xml` (no hard-coded strings in Compose) | — |

## 6. Debugging

* **Logs:** the app barely logs; only `ImageLoader` logs when a display name is unavailable (tag `ImageLoader`).
  Use `adb logcat` and filter by the `com.animecliplabs.clipmark` process.
* **Batch state:** inspect the job file:
  ```bash
  adb shell run-as com.animecliplabs.clipmark cat files/batch/current_batch.json
  ```
  (debug builds only).
* **WorkManager:** the unique work name is `watermark-batch`. Use Android Studio's App Inspection → Background Task Inspector.
* **Process-death resume:** start a batch, then run `adb shell am kill com.animecliplabs.clipmark` (with the app in the background).
  Reopen the app. Home should show "Batch paused — Resume", or WorkManager restarts the batch by itself.
* **Exports:** `adb shell ls "/sdcard/Pictures/AnimeClips Watermarked/"`.

## 7. Common problems

| Symptom | Cause / fix |
|---|---|
| `SDK location not found` | Missing `local.properties` / `ANDROID_HOME`. |
| Instrumented tests can't find a device | Start an emulator first. On the author's machine the AVD lives with the C: SDK. Launch it with that SDK and `-no-snapshot-save`. |
| Exports named `animecliplabs_<date>_<time>_watermarked.jpg` | Expected for Photo Picker input: the Picker hides real names. See README "Known limitations". |
| The Photo Picker on the emulator crashes on fast taps | Observed during development; it is a system Picker issue, not app code. |
| A resumed batch fails with "access expired" | The source grant could not be persisted (some share-intent providers). Expected. |

## 8. Workflow

There is no CI, no branch model and no contribution guide in the repository. The recommended minimum before a change is complete:

1. Run `./gradlew testDebugUnitTest`. It must pass.
2. For changes to `image/`, `data/` or `work/`, run `connectedDebugAndroidTest` on an emulator.
3. Do a manual check with the images in `samples/` (see [TEST_PLAN.md](../05-testing/TEST_PLAN.md#4-manual-test-checklist)).
4. Update the affected docs (see the table in §5).

Conventions are in [CODING_CONVENTIONS.md](CODING_CONVENTIONS.md).
