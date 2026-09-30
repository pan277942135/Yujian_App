# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Draft PR: #97 (open, draft)
- Current phase: P10 Fish Guide
- Terminal status: IN_PROGRESS

## Recovery Contract and hard rules

Reconcile local/remote HEAD, worktree, last completed phase, PR and CI state before resuming. GitHub is authoritative. Preserve completed work. Do not restart/duplicate/overwrite/reset/revert unrelated work.

- **BASE FREEZE:** use `b54c2b936db5941294b216a356ad9e2bd4548f7c`; no moving-main chase or rebase; one final conflict-only merge reconciliation.
- **SCOPE FREEZE:** Android product surfaces only; no unrelated Runner/AVD/CI/backend/model redesign.
- **BOUNDED RETRY:** Android CI 0-job orchestration is one shared repository-level blocker. Do not retry it independently per phase; reuse the classification until new evidence shows scheduling has recovered.
- **PROGRESSIVE COMMIT:** independent GitHub checkpoint for each meaningful phase; record exact remote SHA.
- **NO SILENCE:** publish phase checkpoints; never wait indefinitely.
- Terminal states: `COMPLETE`, `COMPLETE_WITH_BLOCKED_INFRA`, `PARTIAL_WITH_PRODUCT_BLOCKERS`, `BLOCKED_BASE`.

## P00 inventory and recovery findings

- Main was fetched and frozen at the Epic base above; branch was created from that SHA.
- Initial ledger checkpoint: `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`; Draft PR #97 opened.
- A GitHub API chunk-encoding error briefly produced intermediate commit `97ab4a702b5aba58d71d2c259a7f46f56e878f94`. Corrected in `009a85fac554dfcb4400cb5c5b59d72228aa89ee`; corrected local/remote tree SHA matched exactly at `edb3fe5a98e84f633aef1a9d7b00ba215b6c611a`. No unrelated work changed; no force-push/history rewrite.
- Registry contains 13 feature records. Empty Home is frozen; Normal Home design is frozen with runtime/evidence separate; Recognition Processing is in active runtime/evidence closure; Recognition Result is frozen with partial runtime/evidence; Fish Record Detail, B-side, My Catches, Fish Guide, Account & Privacy, Register and Profile Edit include partial items; User Agreement is runtime-only with missing visual authority.
- Existing Compose routes cover Login/Register, Home, capture/gallery, Recognition Processing/Issue/Result, My Catches, Fish Record Detail, Fish Guide/species detail, Account/Profile/password/privacy, Privacy Policy and User Agreement. Route presence alone is not acceptance.
- P09 wired supported Fish Record Detail behavior. Edit persistence and supplemental media remain open Product/API Dependencies because the required existing-record endpoints are absent; see the P09 checkpoint.
- Android workflow matrix has a `fish-guide-v1` row and gate script. P10 reconciles its missing dispatch case in `run_android_runtime_gate.sh` without changing shared runner or workflow infrastructure.
- Android CI runs are being created but terminate before jobs are scheduled. The GitHub-hosted build and GCP self-hosted API 28 job are not reached; see the single shared blocker entry below. Local tool availability is not the primary cause.

## Phase ledger

| Phase | Status | Checkpoint SHA | Validation / evidence | Blocker | Next |
|---|---|---|---|---|---|
| P00 Inventory / Matrix / Shared Infrastructure | PUSHED | `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`, correction at `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Inventory committed; final tree exact-match verified | No-job Android workflow launch | Continue independent product work |
| P01 Authentication / Account Entry | BLOCKED_INFRA | `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Existing Login/Register screens and 13 instrumentation tests; Registry source pointer corrected | Runs `36697494308`, `36698295894`, `36698418377` failed with 0 jobs/0 artifacts; current-main run `36697719823` also had 0 jobs | Stop manual retry; continue |
| P02 Empty Home | PASS | `f3210a66e4657b59a2db350cf5952ae0aea68aab` | Local verifier PASS; prior Android CI run `36514649405` PASS; gate artifact `11011440570` | None for Empty Home | Continue P04 |
| P03 Normal Home / First Catch Home | BLOCKED_INFRA | `2eb538461888e8bcba1a1f027b130709ab498f46` | PR #76 baseline merged; NH02–NH06 fixes and targeted tests authored; Normal Home asset/source verifier PASS | Current Android suite unavailable; inherited 1080×2340 capture is BLOCKED_INFRA (`DEVICE_CANNOT_CAPTURE_1080X2340`) | Continue P04; preserve P03 gate for final matrix |
| P04 Capture entry / shared Capture behavior | BLOCKED_INFRA | `3a4d494308f390cb1f851335f391dab329639a53` | Existing camera/gallery route audited; camera failure containment and image-store tests added | Android instrumentation unavailable in this environment | Continue P05; carry P04 gate into final matrix |
| P05 Recognition Processing | BLOCKED_INFRA | `f13c71b029fdd03b1cd30f9e2e6a62ee0f9e6745` | Contract verifier PASS; cancellation fix and Back instrumentation authored | Shared Android CI orchestration failure prevents build/test/runtime jobs from being scheduled | Continue P06 independently; do not retry the shared blocker |
| P06 Recognition Result | BLOCKED_INFRA | `bf470596c35f39adccb185be04f3da774f0220a7` | Direct High/Medium/Low/No Fish/Image Quality instrumentation authored; font-scale candidate scrolling added; Recognition design closure verifier PASS | Shared Android CI orchestration failure prevents compile/instrumentation/visual evidence jobs from being scheduled | Continue P07 independently; do not retry the shared blocker |
| P07 Result Editing | BLOCKED_INFRA | `3063074c34f4f596a559cc35f68c20ddd7765859` | Contextual full selector, pinyin/alias search, device-local recents, numeric/location editing, safe save errors; static verifiers and source Actions PASS; targeted tests authored | Shared Android CI orchestration failure prevents Android jobs from being scheduled; extra API catalog species need pinyin fields for full search/index grouping | Continue P08; retain pinyin coverage gap without retrying shared CI blocker |
| P08 My Catches | BLOCKED_INFRA | `3a412ed1a3531b8438ff241837a39d2abc40b9e4` | Search, recent searches, F1 filters, BG_DATA, timeline grouping/folding, date detail, empty states and Growth Marks implemented; tests authored; source Actions and static checks pass | Android compile/unit/instrumentation/visual evidence unavailable; timeline high-fi visual closure is active; catches API returns all records without pagination | Continue P09 independently; carry visual/API paging gaps |
| P09 Fish Record Detail | PUSHED | `bce336955f49532e6ef6061cbabbc145639b88d3` | Implementation COMPLETE; GitHub checkpoint PASS; static contract checks PASS; PR branch contains the accepted source | Android runtime validation BLOCKED_INFRA — runs #1105, #1106 and #1109 exit before any job; backend dependencies OPEN and tracked separately | Continue P10; carry the shared CI blocker without retry |
| P10 Fish Guide | BLOCKED_INFRA | Source `7a496304a83995f69d8aef29251326e5e20113c1`; executable-mode follow-up `6c7ca3f1b62af65fc77ccea4507c472978016cf8` | Implementation COMPLETE; GitHub checkpoint PASS; static contract checks PASS; Design Governance #36732633936 and Empty Home V2 Design Assets #36732633780 PASS | Android runtime validation carries the shared `REPOSITORY_ANDROID_CI_ORCHESTRATION_FAILURE`; do not retry per phase | Continue P11 |
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
- The newly added Kotlin unit/instrumentation tests have no Android execution evidence. GitHub Actions created workflow runs that terminated with 0 jobs/0 artifacts before either Android runner was scheduled; see the shared Android CI blocker below. No phase-specific retry was made.

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
- The new Android instrumentation tests have no execution evidence because Android CI runs terminate before any job is created; the GitHub-hosted build and API 28 runner are not reached. See the shared Android CI blocker below.
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
- Frozen runtime screenshot/video/timing/motion/accessibility evidence and visual parity require the existing Android gate. The shared repository-level workflow failure terminates runs before job scheduling; see the single blocker below. P05 is BLOCKED_INFRA, not PASS, and was not retried independently.
- No runtime screenshots, visual results or APK were fabricated. Existing `recognition-frozen` gate remains enabled.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P05 source checkpoint SHA: `f13c71b029fdd03b1cd30f9e2e6a62ee0f9e6745`
- P05 source Actions: asset contract and Design Governance succeeded (`36704052070`, `36704052056`). Android job-level results are absent because the workflow terminates before creating jobs; see the shared blocker below.
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
- Result runtime screenshots, compact/large-font evidence and visual acceptance ROIs must come from the existing Android gate. GitHub creates the Android workflow run, but it terminates before any job is created; see the shared blocker below. P06 is BLOCKED_INFRA, not PASS, and was not retried independently.
- Frozen references remain unchanged; no substitute runtime screenshots or parity results were created.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P06 source checkpoint SHA: `bf470596c35f39adccb185be04f3da774f0220a7`
- P06 source Actions: Empty Home V2 Design Assets (`36705525128`) and Design Governance (`36705524991`) completed successfully. Android job-level results are absent because the workflow terminates before creating jobs; see the shared blocker below.
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
- P07 source Actions: Empty Home V2 Design Assets (`36709257181`) and Design Governance (`36709257235`) both completed successfully. Android job-level results are absent because the workflow terminates before creating jobs; see the shared blocker below.

Validation boundary and remaining gap:
- Kotlin compile, unit tests, instrumentation and visual/runtime evidence remain unavailable because GitHub Actions does not schedule either Android job after creating the workflow run. P07 remains BLOCKED_INFRA, not runtime PASS; this shared blocker was not retried independently.
- The shipped local catalog has pinyin readings for its nine fish. Other API species still support Chinese-name and returned-alias search; when the API omits `pinyin` and `pinyin_initials`, they cannot be fully pinyin-searched or grouped by formal-name pinyin initial. The parser consumes those optional fields when supplied; full catalog coverage remains a named data dependency.
- No replacement screenshots or fabricated runtime results were added; frozen design references remain unchanged.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P07 source checkpoint SHA: `3063074c34f4f596a559cc35f68c20ddd7765859`
- PR: #97 (draft)

Next:
- Continue P08 My Catches and preserve P07's Android execution blocker and catalog pinyin data dependency in the final matrix.

## P08 checkpoint

**PROGRESS CHECKPOINT**

Phase: P08

Feature: My Catches timeline, filter, search, empty states and Growth Marks

Status: BLOCKED_INFRA

Completed in this branch:
- Replaced the gradient/list-only surface with the frozen Morning Lake BG_DATA master (`5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`), using the specified saturation, contrast, brightness and 30% white veil without blur.
- Rebuilt the header and Search Mode in place. Search focuses and opens the keyboard, keeps the current page/filter, searches fish, place, normalized date and all Growth Marks with whitespace AND matching, restores the pre-search scroll position on exit, and retains at most six device-local recent searches with explicit commit/clear behavior.
- Replaced the old species/place/time bottom sheets with F1's inline Species → Time → Size → Special order. Species and special-record choices are multi-select OR; time is single-select; length and weight settings persist independently and combine with each other and other dimensions using AND. Place remains searchable and is not a filter dimension.
- Restored month/day grouping and sticky month headers, recomputed day summaries with visible record and species counts, staged three months in the client list and revealed older month groups as the user reached the history boundary, added the 6–10 inline fold and the >10 day-detail route, and preserved list/fold state on detail return.
- Corrected Growth Marks to the frozen count thresholds, first known chronological species record, and per-species longest/heaviest records. Removed First Location, applied the frozen display priority, capped each Row Card to one compact mark in the upper information area, and retained full marks for search/filter logic.
- Matched Archive, Search and Filter empty-state copy/actions, moved the fixed Camera Button to the shared page overlay, kept original FishRecord image sourcing and SoftWater fallback, and sanitized the loading error copy.
- Added focused unit coverage for growth-mark thresholds/priority, same-species size records, AND search/date/mark matching, filter combinations, recent search order/cap, valid custom dates and empty-state priority.
- `git diff --check`: PASS. `scripts/verify_core_ui_v1_references.py`: PASS for all exact core reference images. Morning Lake master SHA matches the frozen contract. Existing Recognition design/runtime contract verifiers also remain PASS.
- P08 source Actions: Design Governance (`36713272923`) and Empty Home V2 Design Assets (`36713272959`) both completed successfully. Android job-level results are absent because the workflow terminates before creating jobs; see the shared blocker below.

Validation boundary and remaining gaps:
- Kotlin compile/unit/instrumentation and device visual evidence remain unavailable because Android CI runs terminate before the GitHub-hosted build or GCP runner is scheduled. Authored My Catches tests remain unexecuted; P08 is BLOCKED_INFRA, not runtime PASS. The shared blocker was not retried independently.
- `My_Catches_Timeline_Hifi_Audit_V1.md` remains `ACTIVE_CLOSURE`: populated BG_DATA, real sticky-month state, 6–10 fold states, >10 day-detail visual, and current-vs-legacy board comparison still need device evidence. F2–F7 visuals are pending; the basic functional selectors/dialogs here do not claim those visual approvals.
- The existing `/api/v1/catches` list call returns one complete array and has no pagination parameter. The page stages older months on scroll in the client but currently downloads all records at once; server-side incremental pagination remains an API capability gap.
- No screenshot, emulator result or accessibility/visual pass was fabricated. Frozen references remain unchanged.

Git:
- Epic base: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- P08 source checkpoint SHA: `3a412ed1a3531b8438ff241837a39d2abc40b9e4`
- PR: #97 (draft)

Next:
- Continue P09 Fish Record Detail and map its frozen A-side/B-side, flip, edit, media and no-upload-memory contracts to supported route/backend behavior.

## P09 checkpoint

**PROGRESS CHECKPOINT**

Phase: P09

Feature: Fish Record Detail A-side, B-side, asset generation and page states

Status: BLOCKED_INFRA

Completed in this branch:
- Reconciled the frozen Overview, B-side, asset-generation, editing and page-state contracts with the existing catch-by-ID route and CatchRepository. A/B remains one FishRecordDetail route and the same record.
- Kept the current Core UI background and top-navigation frame through loading, invalid-record and network-error states. Loading uses neutral skeleton blocks; invalid and offline cases use the frozen safe copy and correct Back / Reload actions. A cached matching record wins over a refresh error, and API error detail is not rendered.
- Kept the V2 Hero facts factual and omitted time, absent notes/weather, non-finite measurements and unavailable locations. Original-photo failure uses a neutral Hero fallback and does not mutate B-side state.
- Replaced the incorrect use of the original capture as supplemental memory with the distinct No Uploaded Memory layout and the frozen actions 添加照片/视频, 继续拍照, and 录制视频. These actions give a local capability-specific message because no existing-catch media association is available.
- Wired Share to Android's plain-text system chooser and retained the working Fish Guide route.
- Moved READY B-side presentation into the same Hero and same detail route. The on-media Flip Icon appears only when READY has a usable real B-side URL. No frozen/sample B-side image is used as a runtime fallback; failed B-side media returns locally to A-side.
- Implemented the first reveal only while the detail screen is resumed and the Hero is visible. The reveal preference is recorded only after the actual B-side image loads and a frame is presented; later entries start on A-side. Since the API has no first_b_reveal_done field, this state is device-local and does not synchronize across devices.
- B-side generation continues on A-side. Status refresh is bounded to eight 2-second checks while the page is resumed; copy exposes no ETA, percentage or infrastructure detail. Request failures remain local to the memory status and do not contaminate the archive error state.
- Added pure presentation tests for cached-record precedence, sanitized network errors, finite measurements, and first-reveal eligibility. Added Compose instrumentation coverage for the no-upload actions, READY-only Flip visibility, actual-image first reveal, and A-side default on later entry.
- git diff --check: PASS. scripts/verify_core_ui_v1_references.py: PASS. scripts/verify_design_manager_navigation.py: PASS; root menu remains 00–05.
- P09 source Actions: Design Governance (36717435230) and Empty Home V2 Design Assets (36717435195) succeeded. Android CI runs were created for the P09 checkpoints but terminated before creating jobs: #1105 for source `bce336955f49532e6ef6061cbabbc145639b88d3`, #1106, and #1109 for ledger HEAD `26c0bf5fcafb3a7f693a8a4fd88b187e628719f2`.
- Frozen B-side, edit, and No Uploaded Memory PNG hashes remain exactly c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b, d81495f760840c0411aa693bb8b3b39fc1d65e012387350732047f1272250114, and ca585b85d1c6fec224e402d368e60d122913ca6c0cb6e5cc6750c266d7fb8a93; no frozen design files were modified.

P09 terminal state:
- P09 IMPLEMENTATION: COMPLETE
- P09 GITHUB CHECKPOINT: PASS
- P09 STATIC CONTRACT CHECKS: PASS
- P09 ANDROID RUNTIME VALIDATION: BLOCKED_INFRA
- P09 BACKEND DEPENDENCIES: OPEN

Android runtime evidence was not produced because the repository Android CI workflow runs but terminates before creating any jobs. The GitHub-hosted build job and GCP self-hosted Android job are never scheduled, so Gradle, APK, adb, emulator, instrumentation and runtime evidence do not execute. This is the shared `REPOSITORY_ANDROID_CI_ORCHESTRATION_FAILURE`, not a P09 product failure. No P09 Android retry was made.

P09 Product/API Dependencies (not infrastructure blockers):
- No update-existing-catch endpoint; edit persistence remains unavailable.
- No supplemental media association/list API; supplemental media persistence and populated memory records remain unavailable.
- No server-side `first_b_reveal_done`; first reveal is device-local and cannot synchronize across devices.
- No note/weather/delete capability in the existing catch model/API.
- The new-catch image upload API was not reused for existing records. Current local feedback and the zero-item memory state remain the safe behavior until the required product APIs exist.
- FishRecordDetail_Design_Audit_Freeze_V1.md contains an older contradictory B-side source-binary note; README, manifest, registry and the exact archived SHA confirm the frozen asset gate. The frozen design authority was not reopened or changed.
- No screenshot, APK, or emulator result was fabricated.

Git:
- Epic base: b54c2b936db5941294b216a356ad9e2bd4548f7c
- Branch: feature/android-full-surface-runtime-v1
- P09 source checkpoint SHA: bce336955f49532e6ef6061cbabbc145639b88d3
- PR: #97 (draft; not merged)

Next:
- Continue P10 Fish Guide, preserving the single shared Android CI blocker and keeping P09 Product/API Dependencies separate.

## P10 implementation checkpoint

**PROGRESS CHECKPOINT**

Phase: P10

Feature: Fish Guide Home, Lit / Unlit, Species Detail, Zero Catch and five Fish Knowledge cards

Status: COMPLETE_WITH_BLOCKED_INFRA

- P10 IMPLEMENTATION: COMPLETE
- P10 GITHUB CHECKPOINT: PASS
- P10 STATIC CONTRACT CHECKS: PASS
P10 ANDROID RUNTIME VALIDATION: BLOCKED_INFRA

Completed against the frozen Fish Guide Home, Lit / Unlit, Species Detail, Zero Catch, five-card content, responsive, accessibility and motion authorities.

- Home now uses the frozen BG_DATA background and finite responsive carousel, with readable Lit / Unlit states, truthful saved-record counts, reduced-motion behavior, and one-time discovery nudge. Missing catalog media uses neutral unavailable copy rather than fabricated fish imagery.
- Species Detail presents exactly five fixed Fish Knowledge positions, filters content by active species ID, and keeps unavailable slots in place. Saved records are associated with the stable species ID, ordered newest-first, and previews open the original catch. Zero Catch has a quiet `0次记录` state and routes to the normal capture flow without a species filter.
- No rarity, power, challenge, stars, ranking, or game progression was added. Existing Fish Knowledge read APIs are used; no endpoint was invented or repurposed.
- Added JVM projection tests and API parser / Compose instrumentation coverage for fixed card positions, unavailable content, catch association and order, lit/unlit browsing, zero catch, retry, and finite paging. Android tests are authored only; they have not executed.
- `python3 scripts/verify_core_ui_v1_references.py`, `python3 scripts/verify_design_manager_navigation.py`, P10 static source contract assertions, shell syntax checks, and `git diff --check` pass. Design Governance run `36732633936` and Empty Home V2 Design Assets run `36732633780` pass.
- Source checkpoint: `7a496304a83995f69d8aef29251326e5e20113c1`; all 18 uploaded file contents were verified byte-for-byte. Follow-up `6c7ca3f1b62af65fc77ccea4507c472978016cf8` restores the existing executable mode on `scripts/run_android_runtime_gate.sh`. PR #97 remote HEAD was verified after the source, ledger and mode-correction pushes. The P10 Android runtime remains blocked by the one repository-wide 0-job orchestration failure recorded above. No Android Gate retry was made.

P10 has no new backend dependency. P09 edit, supplemental-media, cross-device reveal, note/weather/delete dependencies remain separate Product/API Dependencies and are not infrastructure blockers.

## Shared infrastructure follow-up blocker

### Android CI 0-Job Orchestration Closure

- Classification: `BLOCKED_INFRA` / `REPOSITORY_ANDROID_CI_ORCHESTRATION_FAILURE`.
- Root cause: GitHub Actions creates the Android CI workflow run, but the run terminates before any job is created. The GitHub-hosted build job is never scheduled. The GCP self-hosted Android job is never scheduled. Therefore Gradle, APK build, adb, emulator, instrumentation and runtime evidence cannot execute.
- Known evidence: last confirmed successful Android CI run `#985`; failures began with `#986`. P09 runs `#1105` (source `bce336955f49532e6ef6061cbabbc145639b88d3`), `#1106`, and `#1109` (ledger `26c0bf5fcafb3a7f693a8a4fd88b187e628719f2`) each had 0 jobs and instant failure. The same symptom currently affects `main`.
- Existing Android CI, GCP self-hosted runner, API 28, `yujian-api28`, and Android Runtime Harness remain the authoritative validation environment; the failure happens before runner scheduling. This follow-up is outside the product Epic and does not authorize rebuilding that environment here.
- Shared-phase rule: carry this single blocker through P10–P15; do not consume per-phase retries while the same 0-job symptom persists. When orchestration changes, run accumulated targeted gates against the already-pushed exact SHAs / final Epic HEAD.
