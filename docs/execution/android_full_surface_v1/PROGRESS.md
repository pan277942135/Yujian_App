# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Draft PR: #97 (open, draft)
- Current phase: P05 Recognition Processing
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
| P05 Recognition Processing | IN_PROGRESS | — | Frozen runtime/state/motion contracts and existing Android gate identified | — | Reconcile frozen recognition runtime and current failures |
| P06 Recognition Result | NOT_STARTED | — | — | — | — |
| P07 Result Editing | NOT_STARTED | — | — | — | — |
| P08 My Catches | NOT_STARTED | — | — | — | — |
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
