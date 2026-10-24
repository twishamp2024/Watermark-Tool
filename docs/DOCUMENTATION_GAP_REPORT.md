# Documentation Gap Report — ClipMark

Audit date: 2026-09-24. Scope: every source, resource, build and test file under `app/`, the root Gradle files, `README.md`
and `docs/`. Generated directories (`build/`, `.gradle/`, `.kotlin/`) were skipped, except that the merged release manifest
was checked for the permission list.

## 1. Existing documentation

| Document | Content | Assessment |
|---|---|---|
| `README.md` | Features, architecture tree, rendering, image pipeline, metadata, batching, build, environment, min SDK, permissions, known limitations, test inventory | High quality and accurate against the code (constants, thresholds, paths and test counts of 63/13 all verified). Kept as the primary entry point. |
| `docs/DESIGN.md` | Problem framing, feature rationale, out-of-scope list, MVP vs polish, navigation map, layered architecture, pipeline, risk → mitigation → test table | Accurate. The only place the *why* is recorded. Kept unchanged, and referenced from the PRD, ADRs and index. |
| KDoc / code comments | Rationale for most design choices (minSdk, no DI, system fonts, picker names, tolerant JSON, signing) | Used as the evidence for the ADRs. |
| `docs/screenshots/`, `samples/` | Visual assets and manual-test images | Referenced, not changed. |

There was no CHANGELOG, CONTRIBUTING, ADR, API spec, CI config, TODO/FIXME comment, prompt file or AI configuration.

## 2. Missing documentation (now created)

| Gap | Resolution |
|---|---|
| No documentation index | `docs/DOCUMENTATION_INDEX.md` |
| No numbered, traceable requirements | `docs/01-product/SRS.md`: every requirement maps to code and a test |
| Product scope and status were spread across README/DESIGN | `docs/01-product/PRD.md`: consolidates status, and links to DESIGN for the rationale instead of copying it |
| No component, sequence or state diagrams | `docs/02-architecture/SYSTEM_ARCHITECTURE.md` (Mermaid) |
| No persisted-format reference or compatibility rules | `docs/02-architecture/DATA_STORAGE.md` |
| No "where to change what", debugging or troubleshooting guide | `docs/04-development/DEVELOPMENT_GUIDE.md` |
| Conventions were implicit | `docs/04-development/CODING_CONVENTIONS.md` |
| No test levels, status, environment or manual checklist | `docs/05-testing/TEST_PLAN.md` |
| No release, signing, versioning or rollback documentation | `docs/06-deployment/DEPLOYMENT.md` |
| No decision records | `docs/07-decisions/ADR.md`: 13 ADRs, each citing its source |

## 3. Outdated or inaccurate documentation (corrected)

| Where | Issue | Fix |
|---|---|---|
| README → Permissions | Listed only the permissions declared by the app. The shipped APK also contains `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `ACCESS_NETWORK_STATE`, merged from WorkManager. The claim "no internet permission" remains true. | Added a row for the library-merged permissions. |
| README → How images are processed, step 5 | Implied Orientation/dimensions are always written. `ExifCopier.copy` writes nothing when the source has no whitelisted tags. The behaviour is harmless, but the wording was imprecise. | Added a one-line clarification. |

No other contradictions between the documentation and the code were found.

## 4. Incomplete areas (documented as UNKNOWN)

These cannot be determined from the repository and need a human decision:

1. **Audience and distribution:** a personal sideloaded tool or a store product? This determines signing, versioning and localisation. (PRD §2, DEPLOYMENT §2.)
2. **Release signing:** no release keystore strategy. (DEPLOYMENT §2.)
3. **Versioning convention and changelog:** `1.0.0` / `versionCode 1`, with no policy. (DEPLOYMENT §4.)
4. **Performance targets:** no throughput or latency goals. (SRS NFR-8.)
5. **Device coverage:** tests are recorded only on an API 37 emulator, and minSdk 29 behaviour is unverified. (TEST_PLAN §3.)
6. **Product success metrics:** none, and there are no analytics by design. (PRD §8.)
7. **Decision dates and authorship:** there is no version control history. (ADR preamble.)

## 5. Observations in the code (documented, not changed)

These are not bugs in the documentation. They are worth a look but were left untouched, because this task changes documentation only:

* **Inconsistent duplicate names:** Presets → Duplicate creates `"<name> copy"` (`PresetRepository.duplicate`), while the editor's
  "Save as new" suggests `"<name> variant"` (`R.string.preset_copy_name`).
* **An unused test dependency:** `compose-ui-test-junit4` is declared for `androidTest`, but no Compose UI test exists.
* **Not a git repository:** the project has a `.gitignore` but no `.git`, so no history, CI or code review trail exists.
* **`core` purity is convention only:** nothing in the build stops an Android import in `core/`. Currently none exist (verified with grep).

## 6. Documentation decisions

**Created:** the 10 documents listed in §2.

**Updated:** `README.md` (the two corrections in §3, plus a link to the documentation index).

**Preserved unchanged:** `docs/DESIGN.md`, which stays at its existing path because the README links to it. It is the canonical
"why" document. The PRD and ADRs reference it rather than duplicate it.

**Canonical-source rule:** the README stays the canonical description of user-visible behaviour and of the rendering, pipeline,
metadata and batch mechanics. The new docs link to those README sections instead of copying them. The SRS restates behaviour
only in the compact, testable form needed for traceability.

**Intentionally not created:**

| Template document | Reason |
|---|---|
| `DATABASE.md` | There is no database. Persisted state is documented as `DATA_STORAGE.md` instead. |
| `API.md` | There is no HTTP/RPC API or exported component beyond the intent filters, which are covered in SRS §4. |
| `03-ai/*` (model, prompts, evaluation, guardrails, RAG, agents) | The app uses no AI/ML model, LLM, prompts, embeddings or agents. Smart Corner and Contrast Assist are deterministic luminance heuristics, fully unit-tested. |
| `TEST_CASES.md` | The test inventory and manual checklist live in `TEST_PLAN.md`. A separate per-case list would duplicate the self-describing test names and drift. |
| `CHANGELOG.md` | There is no release history to record. It should be started with the next release. |
