# Runtime Acceptance Checkpoint — 2026-10-16

## Repository and tested code

- Repository: `pan277942135/Yujian_App`
- PR #145 remains **Draft / Open / Unmerged**; base is `main@7737f293ea3831aeea2c6f58308961cdb2ba7d01`.
- Runtime-tested Android code HEAD: `2f9d0c66c5bf818230fe0ee36ef9f5744d959475` (`test(recognition): fix API28 guide relaunch assertions`).
- Exact-head push Android CI: [run 38884262960](https://github.com/pan277942135/Yujian_App/actions/runs/38884262960), completed with overall **failure** because several broader, independent gates did not pass.
- Project Master State run 38884262938: **PASS**.
- Build, JVM tests, AndroidTest compilation, APK assembly, lint, packaged asset checks and model trace export: **PASS**.

## Three-page First-use Camera Guide

The active design scope is V1 P01/P02/P03: portrait phone + horizontal fish; portrait phone + vertical fish; landscape phone + horizontal fish. Frozen source paths and hashes are unchanged.

- `recognition-frozen`: **PASS**. API 28 instrumentation included `RecognitionFirstCaptureGuideRuntimeTest` and `RecognitionFrozenFlowEmulatorTest`; runtime evidence generation reported PASS (29 artifacts). [Gate evidence](https://github.com/pan277942135/Yujian_App/actions/runs/38884262960/artifacts/12039921401).
- `camera-runtime-v1`: instrumentation **PASS**, including `RecognitionCameraRuntimeTest` and `RecognitionFirstCaptureGuideRuntimeTest`; final gate classification **ASSET_SOURCE_BLOCKED**. Camera visual parity still lacks independent page-specific photo sources. Frozen full-page guide screenshots are not runtime photos. [Gate evidence](https://github.com/pan277942135/Yujian_App/actions/runs/38884262960/artifacts/12039549810).

**Scoped disposition:** Guide interaction and API 28 runtime assertions passed on the tested code HEAD. Camera runtime visual parity remains blocked by source assets. This is not full Recognition Main Journey acceptance.

## Exact-head API 28 matrix

| Gate | Result | Evidence / disposition |
|---|---|---|
| `normal-home-v1` | `FAIL_EVIDENCE` | Normal Home F01–F08 Hero fixture matrix remains unsatisfied; separate from the Guide scope. |
| `recognition-frozen` | `PASS` | Includes the three-page First-use Guide runtime test. |
| `camera-runtime-v1` | `ASSET_SOURCE_BLOCKED` | Instrumentation passed; camera visual parity lacks independent page-specific sources. |
| `empty-home-v2` | `FAIL_EVIDENCE` | 320×640 fontScale 1.0 capture passed; the fontScale 1.3 responsive capture was not produced. |
| `login-v2` | `PASS` | Instrumentation and Login/Register visual parity passed. |
| `data-sanitization` | `FAIL_TEST` | Independent Home/data gate; not part of the three-page Guide acceptance. |
| `fish-guide-v1` | `PASS` | Separate Fish Guide gate. |
| `runtime-parity` | `PASS` | Optional parity job passed. |

Do not describe the whole Android CI run as passing. The complete API 28 matrix is recorded above; only the scoped Guide interaction result is accepted.

## Broader journey boundary

This checkpoint does not close E0–E5 or J01–J20. Their end-to-end execution, persistence, broader visual/device cases, final evidence index and QA report remain pending. Normal Home Hero fixtures, the Empty Home large-font responsive capture, Camera visual source assets, and the separate data-sanitization test failure remain explicit follow-up items.

No frozen artwork, model thresholds, backend/API/database contracts, or `main` were changed by this acceptance update.
