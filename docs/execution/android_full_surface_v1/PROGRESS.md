# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Platform: Android only
- Draft PR: #97 (open, draft)
- Current phase: P01 Authentication / Account Entry
- Terminal status: IN_PROGRESS

## Recovery Contract

Before continuing after any interruption, read the branch and remote state and reconcile: current local HEAD, remote branch HEAD, worktree status, last completed phase, open PR/check state, and next incomplete step. GitHub is authoritative. Preserve completed commits, CI runs, artifacts, and PRs; do not restart completed phases, duplicate commits/PRs/workflows, overwrite unknown work, or reset/revert unrelated work.

## Closure Hard Rules

1. **BASE FREEZE:** Epic base is `b54c2b936db5941294b216a356ad9e2bd4548f7c`. Do not rebase or chase newer `main`. Only one final merge reconciliation is allowed if GitHub reports a real conflict.
2. **SCOPE FREEZE:** Implement the Android product surfaces in the matrix. Do not expand page work into unrelated Runner, AVD, CI, backend, model, or infrastructure redesign.
3. **BOUNDED RETRY:** Each gate receives one initial attempt and no more than two repairs. On a third failure, stop that gate, capture evidence/root cause/classification, isolate it, and continue independent phases.
4. **PROGRESSIVE COMMIT:** Each meaningful phase gets its own GitHub checkpoint. Targeted verification → commit/push → verify remote SHA → update ledger/PR → continue.
5. **NO SILENCE:** Report a visible checkpoint after each phase. Never wait indefinitely on Actions, Runner, AVD, Gradle, adb, or network.
6. **TERMINAL STATES:** Finish as `COMPLETE`, `COMPLETE_WITH_BLOCKED_INFRA`, `PARTIAL_WITH_PRODUCT_BLOCKERS`, or `BLOCKED_BASE`; never report a false PASS.

## P00 inventory findings

- Repository main was fetched and verified at the frozen base above; the Epic branch was absent and has now been created from that exact SHA.
- `design/registry/experience_registry_v1.json` contains 13 feature records. Empty Home is fully frozen; Normal Home design is frozen while runtime/evidence are partial; Recognition Processing is in active runtime/evidence closure; Recognition Result is frozen with partial runtime/evidence; Fish Record Detail, B-side, My Catches, Fish Guide, Account & Privacy, Register and Profile Edit have partial runtime/design items; User Agreement is runtime-only with missing visual authority.
- Existing Compose screens/routes already cover Login, Register, Home, capture/gallery entry, Recognition Processing/Issue/Result, My Catches, Fish Record Detail, Fish Guide/species detail, Account/Profile, password/privacy, Privacy Policy and User Agreement. Baseline route presence does not count as a completed gate.
- Current detail route leaves share/edit/add-media callbacks empty. The user journey includes these only where supported by the registered Fish Record contracts and current API capabilities.
- Existing Android validation stack is `.github/workflows/android.yml`, `scripts/run_android_runtime_gate.sh`, and `scripts/android_runtime/gates/`. Runtime jobs use the existing self-hosted labels `yujian-android` and `api28`. A gate-dispatch mismatch for `fish-guide-v1` is recorded in the matrix for narrow P10 reconciliation.
- This scratch runtime has Java 17 but no Gradle, adb, or emulator. Runtime/build evidence must come from existing GitHub Actions and the current API 28 runner; no new validation environment is being created.
- No equivalent full-surface implementation matrix or progress ledger existed in `docs/execution/` at the frozen base.
- P00 checkpoint `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f` is pushed and Draft PR #97 is open.
- Android workflow run `36697494308` for P00 ended `failure` with 0 jobs and 0 artifacts. No product gate executed; operational classification is `BLOCKED_INFRA` pending a working run. No manual retry has been issued.

## Phase ledger

| Phase | Status | Checkpoint SHA | Validation / evidence | Blocker | Next |
|---|---|---|---|---|---|
| P00 Inventory / Matrix / Shared Infrastructure | PUSHED | `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f` | Registry/navigation/harness inventory committed; Android run 36697494308 produced 0 jobs/0 artifacts | Android workflow launch has not executed a product gate | Continue P01 without waiting on P00 workflow |
| P01 Authentication / Account Entry | IN_PROGRESS | — | Existing Login/Register screen and Android test coverage reviewed; stale Register registry source path being corrected | Targeted `login-v2` run pending | Correct authority pointer, verify existing gate, continue |
| P02 Empty Home | NOT_STARTED | — | — | — | — |
| P03 Normal Home / First Catch Home | NOT_STARTED | — | — | — | — |
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

## Current progress checkpoint

**PROGRESS CHECKPOINT**

Phase: P01
Feature: Authentication / Account Entry
Status: IN_PROGRESS

Completed:
- Completed and pushed P00 matrix/progress checkpoint `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`; opened Draft PR #97.
- P01 review confirms frozen Login V2 and Register V2 Compose screens and existing Android tests cover form validation, keyboard traversal, small-screen reachability, submit, error/loading, password visibility and captured screenshots.
- Correcting the Register V2 runtime source pointer in the active Registry; no frozen UI redesign is needed.

Git:
- EPIC BASE: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Local HEAD: `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`
- Remote HEAD: `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`
- PR: #97 (draft)
- Push: YES
- Working tree: clean at last checkpoint

Validation:
- Compile: P00 did not run; Android workflow run 36697494308 had 0 jobs
- Unit: not run
- Lint: not run
- Instrumentation: not run
- Visual: not run
- Evidence: inventory and current workflow result recorded in `IMPLEMENTATION_MATRIX.md`

Current blocker:
- P00 Android workflow run 36697494308 concluded failure with 0 jobs/0 artifacts, so no product gate ran. Continue independent implementation; observe the next normal run and classify it without manual rerun.

Next:
- Commit the corrected Register runtime authority pointer and this progress update; use the existing `login-v2` gate, classify the result, then continue to P02.
