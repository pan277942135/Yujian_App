# YuJian Android Full-Surface Runtime — Progress

## Task identity

- Repository: `pan277942135/Yujian_App`
- Epic base SHA (frozen): `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Target: `main`
- Draft PR: #97 (open, draft)
- Current phase: P01 Authentication / Account Entry
- Terminal status: IN_PROGRESS

## Recovery Contract

Before continuing after an interruption, reconcile local/remote HEAD, worktree, last completed phase, PR and CI state. GitHub is authoritative. Preserve completed commits and evidence. Do not restart, duplicate, overwrite, reset, or revert unrelated work.

## Closure Hard Rules

1. **BASE FREEZE:** use `b54c2b936db5941294b216a356ad9e2bd4548f7c`; do not chase newer `main` or rebase. One final merge reconciliation only for a real conflict.
2. **SCOPE FREEZE:** Android product surfaces only; do not expand into unrelated Runner, AVD, CI, backend, model, or infrastructure redesign.
3. **BOUNDED RETRY:** one initial attempt plus at most two repairs per gate. After a third failure, stop manual retries, record evidence/classification, isolate and continue.
4. **PROGRESSIVE COMMIT:** every meaningful phase has an independent GitHub checkpoint with exact remote SHA.
5. **NO SILENCE:** publish a checkpoint after each phase; never wait indefinitely on Actions, Runner, AVD, Gradle, adb, or network.
6. **TERMINAL STATES:** `COMPLETE`, `COMPLETE_WITH_BLOCKED_INFRA`, `PARTIAL_WITH_PRODUCT_BLOCKERS`, or `BLOCKED_BASE`.

## P00 inventory and recovery findings

- Main was fetched and frozen at `b54c2b936db5941294b216a356ad9e2bd4548f7c`; the Epic branch was created from that exact SHA.
- The initial P00 ledger checkpoint is `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`; Draft PR #97 was opened.
- A GitHub API chunk-encoding error briefly produced an incorrect intermediate commit `97ab4a702b5aba58d71d2c259a7f46f56e878f94`. It was immediately followed by correction commit `009a85fac554dfcb4400cb5c5b59d72228aa89ee`. Corrected remote tree SHA `edb3fe5a98e84f633aef1a9d7b00ba215b6c611a` matches the local staged tree; Registry and ledger bytes are correct at the current branch head. No unrelated work was changed.
- The Registry has 13 feature records. Empty Home is frozen with runtime evidence PASS; Normal Home design is frozen and runtime/evidence remain separate; Recognition Processing is in active runtime/evidence closure; Recognition Result is frozen with partial runtime/evidence; Fish Record Detail, B-side, My Catches, Fish Guide, Account & Privacy, Register and Profile Edit contain partial runtime/design items; User Agreement is runtime-only with missing visual authority.
- Existing Compose routes already cover Login/Register, Home, capture/gallery entry, Recognition Processing/Issue/Result, My Catches, Fish Record Detail, Fish Guide/species detail, Account/Profile/password/privacy, Privacy Policy and User Agreement. Route presence is not acceptance.
- Current Fish Record Detail route wires share/edit/add-media callbacks as no-ops. P09 will implement only behavior supported by frozen contracts and existing API capabilities.
- Existing validation stack is `.github/workflows/android.yml`, `scripts/run_android_runtime_gate.sh`, and `scripts/android_runtime/gates/`; API 28 jobs use the existing self-hosted `yujian-android/api28` labels. Workflow matrix has `fish-guide-v1`, and a script exists, but runtime dispatcher lacks its case; reconcile only when P10 reaches that gate.
- Local execution has Java 17, but no Gradle, adb, or emulator. Use the existing GitHub Actions build and API 28 runner.

## Phase ledger

| Phase | Status | Checkpoint SHA | Validation / evidence | Blocker | Next |
|---|---|---|---|---|---|
| P00 Inventory / Matrix / Shared Infrastructure | PUSHED | `7fc8c7697b8ee577c5037dfbd4789da09ac8d89f`, corrected at `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Registry/routes/harness inventory; remote tree matches local staged tree | Android workflow run attempts produced no jobs | Continue independent product work |
| P01 Authentication / Account Entry | BLOCKED_INFRA | `009a85fac554dfcb4400cb5c5b59d72228aa89ee` | Existing Login/Register screens and 13 instrumentation cases reviewed; registry source pointer corrected | Android workflow attempts `36697494308`, `36698295894`, `36698418377` ended failure with 0 jobs/0 artifacts; current-main run `36697719823` also has 0 jobs. No product gate executed. | Stop manual retries and continue P02 |
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

## P01 checkpoint

**PROGRESS CHECKPOINT**

Phase: P01
Feature: Authentication / Account Entry
Status: BLOCKED_INFRA

Completed:
- Confirmed frozen Login V2 and Register V2 Compose screens and existing coverage for form validation, field/IME traversal, small-screen reachability, submit, errors/loading, password visibility and screenshots.
- Corrected `auth_register_v2.modalities.runtime.authority` to `app/src/main/java/com/yujian/ai/ui/auth/RegisterV2Screen.kt`.
- No Login/Register visual redesign was needed from the frozen authority review.

Git:
- EPIC BASE: `b54c2b936db5941294b216a356ad9e2bd4548f7c`
- Branch: `feature/android-full-surface-runtime-v1`
- Last verified local/remote HEAD: `009a85fac554dfcb4400cb5c5b59d72228aa89ee`
- PR: #97 (draft)
- Push: YES
- Worktree: clean at last verification

Validation:
- Compile: NOT RUN; Android workflow created zero jobs.
- Unit: NOT RUN.
- Lint: NOT RUN.
- Instrumentation: NOT RUN; `login-v2` could not start.
- Visual: NOT RUN on this branch; existing test source covers Login/Register screenshots.
- Evidence: current workflow runs have 0 jobs and 0 artifacts; see blocker IDs above.

Current blocker:
- `BLOCKED_INFRA` — Android workflow starts completed failed runs but returns no jobs or artifacts, including the frozen-base run. No runner/job logs are available to identify a narrower cause. Three no-job branch attempts have been observed; stop manual retries and keep implementing independent phases.

Next:
- Complete P02 using frozen Empty Home asset/runtime evidence; then reconcile P03 from its already-merged Normal Home PR without reworking it.
