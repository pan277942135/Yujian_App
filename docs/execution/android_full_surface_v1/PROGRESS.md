# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Draft PR: #97 (open, draft)
- Current phase: P08 My Catches
- Terminal status: IN_PROGRESS

## Recovery Contract and hard rules

Reconcile local/remote HEAD, worktree, last completed phase, PR and CI state before resuming. GitHub is authoritative. Preserve completed work. Do not restart/duplicate/overwrite/reset/revert unrelated work.

- **BASE FREEZE:** use `b54c2b936db5941294b216a356ad9e2bd4548f7c`; no moving-main chase or rebase; one final conflict-only merge reconciliation.
- **SCOPE FREEZE:** Android product surfaces only; no unrelated Runner/AVD/CI/backend/model redesign.
- **BOUNDED RETRY:** one initial attempt + at most two repairs per gate. Three no-job Android workflow attempts have been observed; stop manual retries and continue.
- **PROGRESSIVE COMMIT:** independent GitHub checkpoint for each meaningful phase; record exact remote SHA.
- **NO SILENCE:** publish phase checkpoints; never wait indefinitely.
- Terminal states: `COMPLETE`, `COMPLETE_WITH_BLOCKED_INFRA`, `PARTIAL_WITH_PRODUCT_BLOCKERS`, `BLOCKED_BASE`.

## P00 inventory and recovery findings

- Main was fetched and frozen at the Epic base above; branch was created from that SHA.
- Initial ledger checkpoint: `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`; Draft PR #97 opened.
- A GitHub API chunk-encoding error briefly produced intermediate commit `97ab4a702b5aba58d71d2c259a7f46f56e878f94`. Corrected in `009a85fac554dfcb4400cb5c5b59d72228aa89ee`; corrected local/remote tree SHA matched exactly at `edb3fe5a98e84f633aef1a9d7b00ba215b6c611a`. No unrelated work changed; no force-push/history rewrite.
- Registry contains 13 feature records. Empty Home is frozen; Normal Home design is frozen with runtime/evidence separate; Recognition Processing is in active runtime/evidence closure; Recognition Result is frozen with partial runtime/evidence; Fish Record Detail, B-side, My Catches, Fish Guide, Account & Privacy, Register and Profile Edit include partial items; User Agreement is runtime-only with missing visual authority.
- Existing Compose routes cover Login/Register, Home, capture/gallery, Recognition Processing/Issue/Result, My Catches, Fish Record Detail, Fish Guide/species detail, Account/Profile/password/privacy, Privacy Policy and User Agreement. Route presence alone is not acceptance.
- Fish Record Detail route currently wires share/edit/add-media callbacks as no-ops. P09 must follow frozen contracts and existing backend capability.
- Android workflow matrix has a `fish-guide-v1` row and a gate script, but `run_android_runtime_gate.sh` lacks a dispatch case; reconcile narrowly in P10.
- Local environment has Java 17 but no Gradle, adb or emulator. Use the repository Actions and API 28 runner.

## Phase ledger

| Phase | Status | Checkpoint SHA | Validation / evidence | Blocker | Next |
|---|---|---|---|---|---|
| P00 Inventory / Matrix / Shared Infrastructure | PUSHED | `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`, correction at `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Inventory committed; final tree exact-match verified | No-job Android workflow launch | Continue independent product work |
| P01 Authentication / Account Entry | BLOCKED_INFRA | `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Existing Login/Register screens and 13 instrumentation tests; Registry source pointer corrected | Runs `36697494308`, `36698295894`, `36698418377` failed with 0 jobs/0 artifacts; current-main run `36697719823` also had 0 jobs | Stop manual retry; continue |
| P02 Empty Home | PASS | `f3210a66e4657b59a2db350cf5952ae0aea68aab` | Local verifier PASS; prior Android CI run `36514649405` PASS; gate artifact `11011440570` | None for Empty Home | Continue P04 |
| P03 Normal Home / First Catch Home | BLOCKED_INFRA | `2eb538461888e8bcba1a1f027b130709ab498f46` | PR #76 baseline merged; NH02–NH06 fixes and targeted tests authored; Normal Home asset/source verifier PASS | Current Android suite unavailable; inherited 1080×2340 capture is BLOCKED_INFRA (`DEVICE_CANNOT_CAPTURE_1080X2340`) | Continue P04; preserve P03 gate for final matrix |
| P04 Capture entry / shared Capture behavior | BLOCKED_INFRA | `3a4d494308f390cb1f851335f391dab329639a53` | Existing camera/gallery route audited; camera failure containment and image-store tests added | Android instrumentation unavailable in this environment | Continue P05; carry P04 gate into final matrix |
| P05 Recognition Processing | BLOCKED_INFRA | `f13c71b029fdd03b1cd30f9e2e6a62ee0f9e6745` | Contract verifier PASS; cancellation fix and Back instrumentation authored | No local Gradle/Android runtime; Android test and runtime evidence gate unavailable after bounded no-job attempts | Continue P06 independently |
| P06 Recognition Result | BLOCKED_INFRA | `bf470596c35f39adccb185be04f3da774f0220a7` | Direct High/Medium/Low/No Fish/Image Quality instrumentation authored; font-scale candidate scrolling added; Recognition design closure verifier PASS | Android compile/instrumentation/visual evidence unavailable after bounded no-job attempts | Continue P07 independently |
| P07 Result Editing | BLOCKED_INFRA | `3063074c34f4f596a559cc35f68c20ddd7765859` | Contextual full selector, pinyin/alias search, device-local recents, numeric/location editing, safe save errors; static verifiers and source Actions PASS; targeted tests authored | No local Gradle/compiler/Android runtime after bounded no-job attempts; extra API catalog species need pinyin fields for full search/index grouping | Continue P08; retain P07 Android gate and pinyin coverage gap |
| P08 My Catches | IN_PROGRESS | — | Frozen-spec audit complete; implementation underway for search, F1 filters, timeline summaries/folding, Growth Marks and fixed capture action | Android compile/runtime and populated visual evidence unavailable locally | Complete implementation and static checks, then continue P09 |
| P09 Fish Record Detail | NOT_STARTED | — | — | — | — |
| P10 Fish Guide | NOT_STARTED | — | — | — | — |
| P11 Account & Privacy | NOT_STARTED | — | — | — | — |
| P12 Shared Component Parity | NOT_STARTED | — | — | — | — |
| P13 Cross-Journey Integration | NOT_STARTED | — | — | — | — |
| P14 Final Android Runtime Matrix | NOT_STARTED | — | — | — | — |
| P15 APK / Evidence / PR Closure | NOT_STARTED | — | — | — | — |

## P02 checkpoint

**PROGRESS CHECKPOINT**

Phase: P02
Feature: Empty Home
Status: PASS

Completed:
- Current `scripts/verify_empty_home_runtime_v2.py` returned PASS: V2.2, 1080×1920 reference, 11 assets and 16 runtime files.
- Current base HomeScreen diff since runtime evidence commit `38ba0e5` only changes the Normal Home background asset reference and removes a Normal Home record-days callback; Empty Home rendering branch and its tests are unchanged.
- Existing Android CI run `36514649405` completed success for `empty-home-v2` at API 28; exact evidence artifact ID `11011440570`, digest `sha256:a63234258d02fd0487e01f49e78d9299f14971419be49f76cac04f5bad97969a`.

Git:
- EPIC BASE: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Last verified local/remote HEAD: `41299c653a6face735b0f07d14a2e290126d727c`
- PR: #97 (draft)
- Worktree: clean at last verified checkpoint

Validation:
- Asset contract: PASS
- API 28 runtime: PASS (inherited current-code evidence; Empty Home path unchanged)
- Unit/compile/lint: verified through successful source run `36514649405`; no local Gradle available
- Current branch API 28 rerun: not available because Android workflow creates zero jobs; no manual retry

Current blocker:
- P01 auth runtime gate remains `BLOCKED_INFRA`; isolated from this Empty Home PASS.

Next:
- Reconcile P03 from merged PR #76 and mark its real-size capture gate accurately, then continue P04.

## P03 checkpoint

**PROGRESS CHECKPOINT**

Phase: P03

Feature: Normal Home / First Catch Home

Status: BLOCKED_INFRA

Completed in this branch:
- Replaced the unresolved archive's blank launch surface with the Normal Home environment, Header, quiet neutral Hero footprint, unresolved `—` statistics and active Capture Button. Recent-catch navigation and the normal CTA stay hidden until archive resolution.
- Moved archive loading/failure transitions into a small state holder. A refresh for the same owner keeps the resolved records/statistics and a failed refresh keeps that snapshot; switching owners clears it.
- Applied safe drawing insets before Normal Home sizing and implemented NH05's shared capped vertical shift for all core regions. Hero dimensions remain width-scaled.
- Kept NH02's one-record layout on a single page with no visible neighbors; added a swipe-stability test. Added local neutral media fallback while retaining the record, species and card geometry.
- Kept the logged-in default-avatar resource distinct from the Guest avatar and made the Normal Home profile fallback expose the same labeled profile action on image-missing and image-failure paths.
- Limited Hero species, measurement and time/location text to one line with end ellipsis per NH04.
- Added unit/instrumentation test coverage for refresh snapshot preservation, NH05 offset calculation, resolving placeholders, single-record pager stability, and unavailable catch media.
- `python3 scripts/verify_normal_home_runtime_v1.py` returned PASS: independent runtime assets and source wiring are closed.

Evidence boundary:
- PR #76 merged at `ac2fe3a7bbf6feb172910a980be731cd586483c1`; its build/unit/lint and Normal Home instrumentation results are inherited baseline evidence only.
- PR #76's real 1080×2340 capture remains BLOCKED_INFRA (`DEVICE_CANNOT_CAPTURE_1080X2340`, artifact `11076223576` from run `36665569750`). No substitute capture was accepted.
- The newly added Kotlin unit/instrumentation tests could not run here because Gradle, adb and an emulator are unavailable. Android workflow launches on this branch previously returned 0 jobs/0 artifacts; per bounded retry, no manual retry was made. Current source changes therefore remain unverified by Android execution.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P03 source checkpoint SHA: `2eb538461888e8bcba1a1f027b130709ab498f46`
- PR: #97 (draft)

Next:
- Continue P04 Capture entry / shared Capture behavior without modifying CI, AVD, runner, backend or model infrastructure.

## P04 checkpoint

**PROGRESS CHECKPOINT**

Phase: P04

Feature: Capture entry / shared Capture behavior

Status: BLOCKED_INFRA

Completed in this branch:
- Confirmed Empty Home and Normal Home continue to reuse the existing `identify` route; gallery entry uses the same screen with `openGallery=true`. No second CameraX or Picker implementation was added.
- Contained CameraX lifecycle bind and immediate capture-start exceptions. When the camera is unavailable, the screen retains a retry action and the existing gallery path.
- Replaced raw camera/picker/normalization exception text with short actionable copy; temporary camera output files are deleted after normalization or failure.
- Added `RecognitionImageStoreTest` for camera and gallery FileProvider normalization, retained source identity, normalized bitmap dimensions and empty camera output feedback. Added it to the existing `recognition-frozen` instrumentation gate.

Validation boundary:
- `git diff --check` passes.
- The new Android instrumentation tests were not executable here: no local Gradle, adb or emulator. The current Android workflow continues to have no job results for this branch; there is no device evidence for capture/permission behavior.
- P04 is therefore `BLOCKED_INFRA`, not runtime PASS. No AVD, runner, permission-policy or backend changes were made.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P04 source checkpoint SHA: `3a4d494308f390cb1f851335f391dab329639a53`
- PR: #97 (draft)

Next:
- Continue P05 Recognition Processing against the frozen state, motion and runtime contracts; retain both P03 and P04 checks in the final runtime matrix.

## P05 checkpoint

**PROGRESS CHECKPOINT**

Phase: P05

Feature: Recognition Processing and failure/retry boundary

Status: BLOCKED_INFRA

Completed in this branch:
- Audited the frozen three-state Processing contract and existing production pipeline, state controller, motion policy, Fish Focus implementation and frozen-flow evidence harness. The pipeline remains the source of detector/classifier truth; presented states remain `图片识别中`, `已定位到鱼体`, and `鱼种识别中`, followed by the existing Resolve handoff.
- Fixed a lifecycle edge: `CancellationException` from the Processing `LaunchedEffect` is rethrown so user Back/disposal does not invoke the technical-failure callback. Other recognition exceptions retain the existing failure presentation and routing.
- Applied the same cancellation propagation to optional fish-subject generation; actual subject-generation errors continue to degrade to the existing unavailable focus treatment.
- Added an instrumentation case that starts a suspended recognition call, leaves Processing through the labeled Back action, and asserts that technical failure is not reported.
- `python3 scripts/verify_recognition_runtime_contract.py` returned PASS for Recognition presentation contract V1.3; `git diff --check` passes.

Validation boundary:
- The new instrumentation test, Kotlin compile, unit tests and Android lint were not executed: this workspace has no `gradle`/Gradle wrapper, `adb` or emulator.
- Frozen runtime screenshot/video/timing/motion/accessibility evidence and visual parity require the existing Android gate. The branch has already used its three no-job Android workflow attempts; bounded retry is exhausted, so no manual rerun was started. P05 is therefore BLOCKED_INFRA, not PASS.
- No runtime screenshots, visual results or APK were fabricated. Existing `recognition-frozen` gate remains enabled.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P05 source checkpoint SHA: `f13c71b029fdd03b1cd30f9e2e6a62ee0f9e6745`
- P05 Actions observation: asset contract and Design Governance succeeded (`36704052070`, `36704052056`); no Android workflow/status was created.
- PR: #97 (draft)

Next:
- Continue P06 Recognition Result high/medium/low/no-fish/image-quality auditing and targeted implementation, preserving P05's Android evidence blocker for final matrix closure.

## P06 checkpoint

**PROGRESS CHECKPOINT**

Phase: P06

Feature: Recognition Result 3+2 states

Status: BLOCKED_INFRA

Completed in this branch:
- Audited `recognition_result_v1` state, behavior, geometry, candidate-card and Hero Media contracts against the live composables. The current source already has the shared Result navigation/actions, High dual CTA, explicit Medium confirmation, Low manual recovery, separate No Fish/Image Quality copy and source-photo Hero planner; the older runtime-alignment review predates these implementations.
- Added direct instrumentation for High identity/metadata/dual CTA, Medium no-selection then explicit candidate confirmation, Low no-save until manual species selection, and distinct No Fish/Image Quality recovery copy/actions.
- Added an accessibility font-scale policy: at font scale 1.3 and above, Medium candidates use fixed 104dp cards in a horizontally scrollable row. Added unit coverage for the policy boundary.
- `python3 scripts/verify_recognition_design_closure_v1_1.py` returned PASS for all 9 frozen references; `python3 scripts/verify_recognition_runtime_contract.py` returned PASS; `git diff --check` passes.
- Existing `recognition-frozen` gate already runs `RecognitionFrozenFlowEmulatorTest`; the new result tests are covered by that gate without introducing another runner path.

Validation boundary:
- Kotlin compile, unit and instrumentation tests have not run because this workspace has no Gradle/Gradle wrapper, adb or emulator.
- Result runtime screenshots, compact/large-font evidence and visual acceptance ROIs must come from the existing Android gate. No Android workflow job has been created for the current branch; the bounded no-job retry budget remains exhausted. P06 is therefore BLOCKED_INFRA, not PASS.
- Frozen references remain unchanged; no substitute runtime screenshots or parity results were created.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P06 source checkpoint SHA: `bf470596c35f39adccb185be04f3da774f0220a7`
- P06 Actions observation: Empty Home V2 Design Assets (`36705525128`) and Design Governance (`36705524991`) completed successfully; no Android workflow/status was created.
- PR: #97 (draft)

Next:
- Continue P07 against the frozen Species Selector, metadata-input and Result save contracts; verify permissions, correction feedback, record-before-memory ordering, duplicate-submit protection and retry state.

## P07 checkpoint

**PROGRESS CHECKPOINT**

Phase: P07

Feature: Species Selector, Result metadata editing and save/error path

Status: BLOCKED_INFRA

Completed in this branch:
- Replaced the prediction-only dialog with the frozen contextual full selector: formal-name/pinyin/initial/registered-alias search (300ms debounce), Recent (maximum three), Common Species responsive grid, grouped All Species, synchronized selected state, 120ms teal commit feedback, and High/Medium/Low-specific Back and unconfirmed behavior.
- Extended Fish Knowledge species parsing to preserve aliases and optional `pinyin` / `pinyin_initials`. The nine shipped local species have pinned search readings. Selector media continues to use Fish Knowledge cover URLs and the neutral placeholder path.
- Updated Length and Weight sheets to the frozen titles, autofocus/caret-at-end decimal input, IME Done validation, committed-value clear and draft-only clearing. Validation copy matches Metadata Edit Flow V1.
- Replaced manual location text editing with search-only query state, 300ms platform Geocoder lookup, a three-item device-local recent list, explicit-purpose/current-location permission flow, one-shot fresh fix with recent-cache fallback, immediate commit on selected results and explicit clear, and no query commit on dismissal.
- Sanitized save failures to `保存鱼获失败，请重试`; existing committed metadata survives a failed save and duplicate submit remains blocked. Record creation still completes before Memory navigation; consent-gated feedback upload remains after durable save.
- Added unit/instrumentation coverage for pinyin/alias matching, recent ordering, selector contexts, numeric validation, parser metadata, safe errors and duplicate submit.
- `scripts/verify_recognition_design_closure_v1_1.py`: PASS (9 frozen references); `scripts/verify_recognition_runtime_contract.py`: PASS; `git diff --check`: PASS.
- P07 source Actions: Empty Home V2 Design Assets (`36709257181`) and Design Governance (`36709257235`) both completed successfully. No Android workflow/status was created.

Validation boundary and remaining gap:
- Kotlin compile, unit tests, instrumentation and visual/runtime evidence are unavailable: this workspace has no Gradle wrapper, Gradle, Kotlin compiler, adb or emulator. Three no-job Android attempts have already exhausted the bounded retry budget; no manual Android rerun was started. P07 remains BLOCKED_INFRA, not runtime PASS.
- The shipped local catalog has pinyin readings for its nine fish. Other API species still support Chinese-name and returned-alias search; when the API omits `pinyin` and `pinyin_initials`, they cannot be fully pinyin-searched or grouped by formal-name pinyin initial. The parser consumes those optional fields when supplied; full catalog coverage remains a named data dependency.
- No replacement screenshots or fabricated runtime results were added; frozen design references remain unchanged.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P07 source checkpoint SHA: `3063074c34f4f596a559cc35f68c20ddd7765859`
- PR: #97 (draft)

Next:
- Continue P08 My Catches and preserve P07's Android execution blocker and catalog pinyin data dependency in the final matrix.
