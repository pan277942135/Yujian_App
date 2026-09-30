# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Platform: Android only
- Current phase: P00 Inventory / Matrix / Shared Infrastructure
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

## Phase ledger

| Phase | Status | Checkpoint SHA | Validation / evidence | Blocker | Next |
|---|---|---|---|---|---|
| P00 Inventory / Matrix / Shared Infrastructure | IN_PROGRESS | pending first push | Registry, routes, workflow and runtime harness inspected | None | Commit/push matrix, create Draft PR, begin P01 |
| P01 Authentication / Account Entry | NOT_STARTED | — | — | — | — |
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

Phase: P00
Feature: Epic inventory and execution ledger
Status: IN_PROGRESS

Completed:
- Frozen `main` at `b54c2b936db5941294b216a356ad9e2bd4548f7c`.
- Created the Epic branch from the frozen base.
- Audited the registry, Android routes, available tests, current workflow matrix, and runtime harness.
- Recorded current product gaps and the existing Fish Guide gate dispatch mismatch.

Git:
- EPIC BASE: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Local HEAD: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Remote HEAD: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Push: branch created; ledger commit pending
- Working tree: pending

Validation:
- Compile: not run (ledger-only phase)
- Unit: not run (ledger-only phase)
- Lint: not run (ledger-only phase)
- Instrumentation: not run (ledger-only phase)
- Visual: not run (ledger-only phase)
- Evidence: registry, route, workflow and harness audit recorded in `IMPLEMENTATION_MATRIX.md`

Current blocker:
- NONE

Next:
- Push the first P00 ledger checkpoint, verify exact remote SHA, create Draft PR, and proceed directly to P01.
