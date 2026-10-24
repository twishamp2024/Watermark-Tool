# Coding Conventions — ClipMark

These conventions are observed in the existing code. There is no linter or formatter configured
beyond `kotlin.code.style=official` in `gradle.properties`.

## Architecture rules

1. **`core/` stays platform-free.** It may not import `android.*` or `androidx.*`, and it may use only Kotlin stdlib,
   `java.time`, kotlinx-serialization annotations and kotlinx-coroutines. Anything that needs Android is injected
   through a `fun interface` (`TextMeasurer`, `ItemProcessor`, `JobSink`). This rule is not enforced by the build;
   reviewers must check it.
2. **One renderer.** All watermark drawing goes through `WatermarkRenderer`, both for the preview and the export. Do not
   add a Compose-native text drawing path for the watermark.
3. **Relative units only.** Style values are percentages of the short edge or fractions of the image, never pixels.
4. **Originals are read-only.** Open sources only for reading, and write outputs only through `OutputStore`.
5. **Manual DI.** New singletons go into `AppContainer`. ViewModels are created with `containerViewModel { … }`.
6. **Unidirectional UI state.** Each ViewModel exposes a `StateFlow` (usually built with `stateIn(…, WhileSubscribed(5_000), …)`
   or a `MutableStateFlow` + `update`). Screens call ViewModel functions for state changes. Composables do read
   stateless services from `LocalAppContainer`: `renderer` and `analyzer` for previews, and `imageLoader` for
   thumbnails. One composable also uses a repository directly: `rememberNotificationGate` reads and writes `SettingsRepository`.

## Error handling

* Per-image problems are **returned** as `ItemOutcome.Failed(FailureKind)`, not thrown. `BatchRunner` also converts
  stray exceptions to `UNKNOWN`. Always rethrow `CancellationException`.
* Non-fatal issues on a saved image become an `ItemNote`, which the result screen shows to the user.
* Persisted data is decoded with `runCatching` + defaults, and a decode must never crash the app.
* Best-effort platform calls (URI permission persistence, MediaStore queries) are wrapped in `runCatching`.

## Serialization

* Persisted classes are `@Serializable` data classes, and **every property has a default** (except identity fields).
* Use the shared `AppJson` instance and do not create other `Json` configurations.
* Never rename a serialized property or enum constant without a migration (see [DATA_STORAGE.md](../02-architecture/DATA_STORAGE.md#4-serialization-rules-compatibility-contract)).

## Style

* KDoc explains **why** (intent, constraints, trade-offs), usually in one to three lines, on the class or non-obvious members.
  Inline comments are rare and explain surprising choices.
* Constants live in `companion object`s or `object`s with descriptive names (`SHADOW_BELOW`, `PREFERENCE_MARGIN`).
* All user-visible strings are in `res/values/strings.xml`, and counts use `<plurals>`.
* Unit test names are backtick sentences that state the behaviour, e.g.
  ``fun `a full disk stops early and leaves the rest retryable`()``. Instrumented test names use camelCase.
* Dependencies are declared only in `gradle/libs.versions.toml` (version catalog).

## Dependencies policy (inferred)

From the code comments and DESIGN.md: avoid adding libraries that add permissions, network access or significant APK
weight, and do not introduce Hilt, Room or font downloads. Any `INTERNET` permission would contradict the privacy
promise in the UI ("No uploads, no account, no analytics").
