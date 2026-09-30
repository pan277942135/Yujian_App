# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Draft PR: #97 (open, draft)
- Current phase: P03 Normal Home / First Catch Home
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
| P02 Empty Home | PASS | `41299c653a6face735b0f07d14a2e290126d727c` | Local verifier PASS; prior Android CI run `36514649405` PASS; gate artifact `11011440570` | None for Empty Home | Reconcile existing Normal Home closure |
| P03 Normal Home / First Catch Home | IN_PROGRESS | — | Existing PR #76 merged; product implementation and most gates already closed | Required 1080×2340 physical capture is BLOCKED_INFRA (`DEVICE_CANNOT_CAPTURE_1080X2340`) | Record existing terminal evidence; do not rework |
| P04 Capture entry / shared Capture behavior | NOT_STARTED | — | — | — | — |
| P05 Recognition Processing | NOT_STARTED | — | — | — | — |
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
