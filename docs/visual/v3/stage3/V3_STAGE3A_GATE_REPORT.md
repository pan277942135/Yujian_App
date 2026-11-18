# V3 Stage 3-A Gate Report

## Stage 2 start gate

| Gate | Evidence state |
|---|---|
| Unified Runtime / shared SceneFrame | `PASS` — existing Stage 2 branch contract and exact-head CI evidence |
| Diagnostic foundation and persistent evidence sink | `PASS` — Stage 2 CI evidence |
| AUTO PixelCopy source wiring and automated contract | `PASS` — not equivalent to physical-device PixelCopy |
| Crash/process recovery automated contract | `PASS` — not equivalent to device fault injection |
| Exact-head Stage 2 CI and Debug APK | `PASS` — Stage 2 branch commit `5b23dcec3333520d45dd4c522d1d378cd68882b4`, CI run `44282342973` |
| API36 physical-device PixelCopy/recovery | `DEVICE_QA_PENDING_USER` — not run in this Work environment |

Stage 2 remains Draft in PR #341 and is not merged. `DEVICE_QA_PENDING_USER` and the separate Stage 1 visual-freeze blocker remain unchanged.

Stage 3-A was permitted to proceed under the instruction that the automated PixelCopy contract is sufficient while device QA remains pending. This report does not upgrade device QA to PASS.

## Stage 3-A implementation status

| Gate | State | Evidence / blocker |
|---|---|---|
| Single logical GL owner and shared SceneFrame | `SOURCE_CONFIRMED` | Existing controller/frame passed to `OrbCrystalGlRenderer`; EGL host only executes. Confirmed at final delivery HEAD `71d33d14720476fe38d7eb8bcec109c51653918f`. |
| Immutable RenderResult and pass status model | `TEST_PASS` | `OrbCrystalRenderResult.kt`; pass status/failure injection contract tests passed in the full Unit Test task. |
| EGL first-frame gate and Compose fallback recovery | `TEST_PASS` | `GlFramePresentationGate`; only a matching complete receipt plus successful EGL swap can release fallback. Failure cases keep Compose visible. The Unit Test also covers reject → valid retry → Context Lost fallback → retry and enforces one visible owner. |
| Frozen inputs not yet implemented in Stage 3-A | `NOT_READY` | Core texture/alpha and frozen Resident / later passes intentionally reject ownership |
| GL evidence recorder wiring | `SOURCE_CONFIRMED` | Existing recorder queues `gpu_trace.jsonl` events after identity/geometry validation; included in exact-head build. |
| Stage 2 targeted regression tests | `PASS` | Android CI run `44318149915`, source checkout `71d33d14720476fe38d7eb8bcec109c51653918f`. |
| Full Unit Tests | `PASS` | Android CI run `44318149915`; the Unit Test task completed successfully. |
| AndroidTest APK compile | `PASS` | `:app:assembleDebugAndroidTest` completed; instrumentation was not run on a device. |
| Android Lint / Debug and Release Kotlin | `PASS` | Android CI run `44318149915`. |
| Exact-head CI | `PASS` | Android CI run `44318149915`, checked out `71d33d14720476fe38d7eb8bcec109c51653918f`; Control Plane Admission run `44318149946` and Work Execution V2 run `44318149901` also passed. |
| Debug APK | `READY` | Installable Debug APK from Android CI run `44318149915`; APK build trace and model contract both identify source SHA `71d33d14720476fe38d7eb8bcec109c51653918f`. APK SHA-256 `25427c9da6d857b1b13d96f248cba0f7e167bb2c3d6adc112854b39b6d2d852f`; size 181,056,848 bytes. |
| API36 device QA | `DEVICE_QA_PENDING_USER` | No ADB/device/emulator use in this task |

## Current decision

`STAGE3A_CODE_CI_PASS / DEVICE_QA_PENDING_USER`

The CI pass confirms the Stage 3-A owner/result/fallback gate, not completed Orb visuals. Core texture/alpha and the Stage 3-B through 3-G passes remain `NOT_READY`, so production GL ownership remains with the Compose fallback until a later stage implements and validates those inputs. Stage 1 visual freeze remains independently blocked and is not changed by this branch. No API36 visual or PixelCopy device pass is claimed.

