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

Current tracked application-code baseline is:

e881c2a6772b418a49b6208ce44c8527fbeb97db

Reason: commits after e881c2a are documentation/design-manager/harness changes only; no application-code changes were observed in the e881c2a -> current branch comparison.

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

## 2026-10-16 — Final V1 state validation

Project Master State workflow:

- Run: 38862418629
- Job: Validate PROJECT_MASTER_STATE
- Result: PASS
- Validator output: PROJECT_MASTER_STATE_VALIDATION=PASS
- Tracked application-code baseline: e881c2a6772b418a49b6208ce44c8527fbeb97db
- Current PR: #145
- Blockers recorded: 8
- Next actions recorded: 10

This validation was executed by GitHub Actions with PyYAML installed and the repository
checked out from the PR merge ref. It validates the current state document and its
provenance rules; it does not imply that Android API28 runtime acceptance has passed.

Current live PR head must still be re-read before the next Work execution.

## 2026-10-16 — Post-refinement validator confirmation

The final Project Master State validation after the execution-baseline/schema
refinement completed successfully.

- Run: 38862479518
- Job: Validate PROJECT_MASTER_STATE
- Result: PASS
- Validator output: PROJECT_MASTER_STATE_VALIDATION=PASS
- Tracked application-code baseline: e881c2a6772b418a49b6208ce44c8527fbeb97db
- Current PR at observation: #145
- State blockers: 8
- Active next actions: 10

The validator passed after the state was changed from the original a280699 baseline
to the latest application-code baseline e881c2a. It also passed the separation between
the moving PR head and the stable execution baseline.

The latest live PR head must always be re-read before Work starts; it is intentionally
not treated as a self-referential field inside PROJECT_MASTER_STATE.yaml.


## 2026-10-16 — Unified Contract Layer V1

### Scope

Completed control-plane step ④: a machine-readable Unified Contract Registry and
resolver were added without replacing domain authorities.

### Added

- `harness/UNIFIED_CONTRACT_REGISTRY.yaml`
- `harness/UNIFIED_CONTRACT_SCHEMA_V1.md`
- `harness/CONTRACT_RESOLUTION_PROTOCOL.md`
- `scripts/validate_unified_contract.py`
- `harness/UNIFIED_CONTRACT_VALIDATION_20261016.md`

### Coverage

- Features: 15/15
- Journeys: J01-J20 = 20/20
- Child surfaces: 75
- Android routes: 22/22
- Phase/Task bindings: 45/45
- Gates: 11/11
- Contract domains: Product / Feature / Journey / Child Surface / Route / Page /
  Visual / Component / Token / Asset / State / Data-API / Model /
  Android Implementation / Runtime / Accessibility / Evidence / Release

### Resolver rule

The lookup key is:

`feature_id + child_surface_id + contract_domain`

Missing authorities remain MISSING. Conflicting authorities remain
NOT_CONFIRMED/BLOCKED. Historical evidence cannot promote current-head PASS.
Page-owned authorities override shared defaults only inside their declared scope.

### CI validation

Project Master State workflow:

- Run: 38870029013
- Job: 116871686066
- Result: PASS
- PROJECT_MASTER_STATE_VALIDATION=PASS
- FEATURE_MATRIX_VALIDATION=PASS
- PHASE_TASK_STATE_MACHINE_VALIDATION=PASS
- UNIFIED_CONTRACT_VALIDATION=PASS

The unified validator initially exposed two implementation defects in the new
validator itself (page-set lookup and matrix status-field naming). Both were repaired
before the final PASS. No product/runtime status was promoted by this control-plane
work.

### Current execution boundary

- Product/runtime execution baseline remains `e881c2a6772b418a49b6208ce44c8527fbeb97db`.
- PR #145 remains Draft / Open / Unmerged.
- Current executable task remains P8 / N001 / READY.
- API28 runtime acceptance remains NOT_RUN.
- J01-J20 runtime acceptance remains NOT_RUN.
- B001/B002/B003/B004/B006/B007/B008 remain governed by the existing state machine.
- No main merge, force-push, rebase, history rewrite, backend/API/database change, or
  frozen asset modification was performed by this stage.
