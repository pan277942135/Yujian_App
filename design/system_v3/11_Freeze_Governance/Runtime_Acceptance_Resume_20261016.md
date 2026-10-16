# Runtime Acceptance Resume Checkpoint — 2026-10-16

## Purpose

This checkpoint resumes the interrupted Android runtime acceptance on PR #145 without changing Frozen UI artwork, model thresholds, Backend/API/database contracts, or merging `main`.

## Exact starting state

- Repository: `pan277942135/Yujian_App`
- PR: #145
- Branch: `audit/design-system-v3-1-phase1-master-baseline`
- PR state: Draft / Open / Unmerged
- Base: `main@7737f293ea3831aeea2c6f58308961cdb2ba7d01`
- Current HEAD at checkpoint creation: `89bc84c77dc42d346812c768045e142050f8695e`

## Verified latest commit

`89bc84c77dc42d346812c768045e142050f8695e` — `fix(android): stabilize runtime journey evidence`

This commit only:
1. stabilizes the Recognition runtime evidence assertion by waiting for the loading node and scrolling it into view;
2. makes Empty Home recording explicitly relaunch and wait for `MainActivity` to be resumed before capture.

No Frozen PNG, model, threshold, Backend/API/database contract, or product navigation contract is changed by this checkpoint.

## Exact-head CI status

The exact-head PR-triggered Android CI run completed successfully for the build job:

- Android CI run: `38837650473`
- Build / JVM tests: PASS
- Camera runtime harness contract tests: PASS
- Debug APK assembly: PASS
- AndroidTest APK compilation: PASS
- Packaged asset checks: PASS
- APK model trace export: PASS
- Android lint: PASS
- APK artifacts: generated
- PR-triggered `android-runtime` job: SKIPPED by workflow design

The workflow defines API28 device execution for branch pushes and workflow_dispatch. Therefore a successful PR synchronize/build run alone is not runtime acceptance evidence.

## Resume rule

The next acceptance baseline must be a branch push derived from this exact HEAD. The push-triggered Android CI must execute the API28 matrix:

1. `normal-home-v1`
2. `recognition-frozen`
3. `empty-home-v2`
4. `login-v2`
5. `data-sanitization`
6. `fish-guide-v1`
7. optional `runtime-parity` only when explicitly requested

Runtime classification must use the repository gate result JSON. A gate is PASS only when both the Harness action outcome and the gate classification are PASS.

## Current known acceptance boundary

- Normal Home Hero fixture authorization remains a separate external approval/fixture dependency and must remain BLOCKED unless the approved F01–F08 source/permission/inspection matrix is actually satisfied.
- Recognition runtime evidence must be judged from the new exact-head device run; prior failures are historical diagnostics only.
- J01–J20 must not be declared end-to-end PASS from static/unit coverage alone.
- Unrun or unavailable physical-device cases remain NOT_RUN/BLOCKED rather than inferred PASS.

## Resume invariant

Do not force-push, rewrite history, merge `main`, modify Frozen artwork, modify model thresholds, or convert the PR out of Draft.
