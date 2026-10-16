# YuJian Build Log

This log records observed execution events. It is not a plan and it does not replace
PROJECT_MASTER_STATE.yaml.

## 2026-10-16 — Project Master State V1 bootstrap

### Observed baseline

- Repository: pan277942135/Yujian_App
- Branch: audit/design-system-v3-1-phase1-master-baseline
- Product/runtime execution baseline: a280699da7575aa332f9d00104969161bbe918bd
- PR: #145
- PR state: Draft / Open / Unmerged
- Base: main@7737f293ea3831aeea2c6f58308961cdb2ba7d01

### Existing Work state

- Work ID: RECOGNITION-FIRST-USE-GUIDE-V1
- Stage: E0
- Status: FIX_READY_FOR_EXACT_HEAD_CI
- Source: docs/qa/recognition_main_journey/WORK_EXECUTION_STATE.json
- Exact-head API28 runtime: not yet run after the latest fixes
- J01-J20: not run
- Local Android toolchain: unavailable in the recorded checkout
- Authoritative runtime: self-hosted API28 runner

### Existing exact-head CI

Run: 38839618733

Observed PASS:
- asset contracts
- model verification
- JVM tests
- Camera harness contract tests
- APK assembly
- AndroidTest APK compilation
- packaged asset checks
- model trace
- lint
- APK artifact generation

Observed SKIPPED:
- android-runtime

Interpretation:
The exact-head build gate is PASS. Runtime acceptance remains NOT_RUN.

### Existing exact-head artifacts

Run: 38839618733

- debug artifact: 12020716375
- full-surface artifact: 12020781309
- recognition QA artifact: 12020876202
- runtime APK pair: 12021175407

Artifact ZIP digests are recorded in PROJECT_MASTER_STATE.yaml. They are not treated
as APK SHA-256 values.

### Historical runtime

Run: 38765917873
Source SHA: 25203f8d18c831624aa0d5cc0bc6f4cff9cc91d2

Observed historical classifications:
- normal-home-v1: FAIL_EVIDENCE
- empty-home-v2: FAIL_EVIDENCE
- recognition-frozen: FAIL_TEST
- data-sanitization: FAIL_TEST
- login-v2: FAIL_TEST
- fish-guide-v1: RUNNER_SETUP_INCOMPLETE

These results are diagnostic/historical only.

## State bootstrap commits

1. f17f2fa962711e3db9c7d866c5723d535769c9ef
   - Added PROJECT_MASTER_STATE.yaml.

2. 01d504affdc426a11711b34380ab3fe85ebdcf6f
   - Added AGENTS.md repository startup and operating protocol.

3. 42d5ede0f1a9c4d150b8deb27261439b15e0b941
   - Added scripts/validate_project_master_state.py.

4. 73753eac0b15f85301a194d2762e6c73819ffeef
   - Added harness/STATE_UPDATE_PROTOCOL.md.

The state bootstrap is documentation/tooling only. It does not modify application
behavior, frozen assets, model thresholds, Backend/API/database contracts, or main.

## Next execution boundary

The next product/runtime action is:

Run the authoritative API28 runtime matrix against the tracked execution baseline
a280699da7575aa332f9d00104969161bbe918bd, then record the exact result and evidence
before promoting any runtime gate.

## 2026-10-16 — Harness bootstrap verification and state refinement

### Repository-native control files

Added:

- AGENTS.md — concise startup/recovery/operating protocol
- PROJECT_MASTER_STATE.yaml — V1 current-state snapshot
- harness/STATE_UPDATE_PROTOCOL.md — state ownership and transition rules
- harness/build-log.md — append-only observed execution log
- scripts/validate_project_master_state.py — deterministic state validator
- .github/workflows/project-master-state.yml — CI enforcement

### Validation observed

Project Master State workflow:

- Run: 38861933644
- Result: PASS
- Commit tested: 07ecfc6a23fe5c623678e469aa02fa557ce2c6c8

The validator confirmed the V1 state structure, P0-P9 coverage, J01-J20 route,
provenance rules, blockers, next actions, and protected invariants.

### Important state-model correction

The master state now distinguishes:

- execution baseline SHA: the product/runtime SHA being accepted;
- moving PR/branch HEAD: live Git state that must be re-read.

This avoids a self-referential state file. Documentation-only commits can move the
PR HEAD without falsely changing the product/runtime baseline.

Current tracked execution baseline remains:

a280699da7575aa332f9d00104969161bbe918bd

### New blocker observed on current branch

Design Governance run 38861933685 failed while validating the first-use-guide
visual registry.

Observed errors:

- VISUAL_MISSING requires SPEC_FROZEN status
- P01_First_Use_Camera_Guide_Frozen.png missing
- P02_First_Use_Camera_Guide_Frozen.png missing
- P03_First_Use_Camera_Guide_Frozen.png missing

This is recorded as blocker B008 in PROJECT_MASTER_STATE.yaml. The existing
first-use-guide task state already lists the three approved PNG paths and hashes,
but the binaries are not present at those registered repository paths.

This is a real acceptance blocker, not an inferred failure. No replacement artwork
was fabricated.

### Current state

- State system: IMPLEMENTED
- Validator: IMPLEMENTED
- CI enforcement: IMPLEMENTED
- Current execution baseline: a280699da7575aa332f9d00104969161bbe918bd
- Exact-head runtime: NOT_RUN
- J01-J20: NOT_RUN
- P0/P1 runtime blockers: OPEN
- Main merge: FORBIDDEN

### Next action

Work should resume from N001: execute the authoritative API28 runtime matrix on
the tracked exact execution baseline, while B008 is resolved independently.
