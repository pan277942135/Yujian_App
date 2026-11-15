# PR #331 R03/R06 Iterative Visual Engineering Report — Iteration 001

**Task:** `YUJIAN_PR331_R03_R06_ITERATIVE_VISUAL_ENGINEERING_CLOSURE_V1`
**Repository / PR:** `pan277942135/Yujian_App` / #331
**Baseline:** `762b656591be3129bf2ec02768a65109371fe4ab`
**Implementation source checkpoint:** `2e11dea1761296cdd19625bfc45a6c47be1376d9`
**PR state requirement:** Open / Draft / Unmerged
**Device execution:** prohibited for Work; product-owner QA pending.

## Result

`SOURCE_FIXES_APPLIED · SOURCE_ROOT_CAUSES_NARROWED · EXACT_HEAD_CI_PASS · DEBUG_APK_DELIVERED · VISUAL_DELTA_NOT_CONFIRMED`

This iteration fixes two R03 source mechanisms and one R06 source mechanism, and adds exact-capture and per-path pixel evidence. No current-source device PixelCopy bundle exists, so there is no measured visual delta, GPU pixel pass, or human visibility verdict.

## Root-cause progress and code changes

| Area | Confirmed source mechanism | Targeted change | What remains unproven |
|---|---|---|---|
| R03 time | MAX Wake phase previously depended on StageProgress alone. A paused/manual preview at 100% therefore held that semantic phase constant. | Keep wake amplitude tied to stage progress; add a 0.65 cycles/s phase derived from the existing `WaterLightFrame.elapsedMs`. Stage03 holds the terminal semantic phase while the added displacement fades across the existing first 80 ms. | Whether production AUTO advances Scene Clock, uniforms, RenderEffect draws, and local water texture pixels on the final APK. |
| R03 resampling | AGSL attenuated the coordinate displacement with `maxInteriorMix` and then attenuated the dynamic contribution again. | Sample once at the bounded displaced coordinate; apply the mix only to the dynamic interior contribution. Raw Core remains independent and edge protection remains active. | GPU output and whether local motion is visually discernible. |
| R06 Back glass | G01/A01 body alpha applied `radialMask²` before the Paint radial gradient applied another edge fade. | Remove the duplicate scalar radial factor. Keep source Core alpha once and one smooth Paint edge mask. Back identities and frozen paths stay unchanged. | G01/A01 final PixelCopy contribution and human-visible glass transmission. |
| Evidence pipeline | Prior captures did not reliably bind each image to its own frame identity and lacked per-path final-image ablations. | Add per-capture IDs and metadata, matching Orb draw marker, Android window frame-commit fence, bounds checks, PixelCopy hashes, five path exclusions, projected fixed-progress R03 samples, and capture-bound Canvas metrics. | The new capture sequence has not yet run on HUAWEI/API36. |

## R03 chain and evidence grade

The source chain is Controller Scene Clock → `RecognitionSceneFrame` → `WaterLightFrame.elapsedMs` → `OrbInteriorOpticalField.frame()` → fluid/grass uniforms → AGSL `main()` → `RenderEffect` on the Core overlay → Compose/window composition. The scene clock and stage progress are distinct inputs: the former drives continuous phase; the latter drives wake amplitude. A projected T0/T+500/T+1500 sample holds StageProgress and samples the shared optical field at different elapsed values; it is explicitly not reported as production AUTO time advancement.

`ORB_R03_SHADER` reports runtime route/status, uniform update attempt/revision, and effect attachment. `ORB_R03_RENDER` reports Compose draw submission. Neither event proves GPU output. `ORB_R03_PIXEL` is produced only by image comparison. R03 is currently **L0 source-mechanism corrected; L2/L3 pixel behavior NOT_MEASURED**.

## R06 chain and evidence grade

Each of G01, G02, A01, A02, C01 continues through the existing frozen path spec, motion phase, effective trail, current-frame valid samples/runs, material layers, alpha, stroke, clipping, render pass, and Canvas draw. G01/A01 use Back Exterior and Glass Transmission; G02/A02/C01 remain Front. The glass attenuation math is documented in [R06_RENDER_CHAIN_AUDIT.md](R06_RENDER_CHAIN_AUDIT.md). The existing normalized middle-radius comparison gives about 0.0999 for the old duplicate-mask factor and 0.3996 after removal, before body alpha/source silhouette; this is L1 math, not measured screen output.

Capture-bound `ORB_R06_PATH`, `ORB_R06_MATERIAL`, and `ORB_R06_GLASS` values are explicitly Canvas/calculated metrics. Per-path final contribution requires aligned `R06_FULL` versus `R06_WITHOUT_<path>` PixelCopy frames. No such current-source ZIP was available; all five final pixel contributions are `NOT_MEASURED`, and human visibility is `NOT_ASSESSED`.

## Evidence source and test status

- Historical `264191.mp4` is retained as a screen-level diagnostic observation only. It is not a same-mode, fixed-progress advancing-clock capture set and has no per-path ablation.
- The source environment passed the Control Plane Admission before final source changes; re-run for the exact candidate is required.
- Locally passed: original Orb WebP SHA/alpha check; frozen R06 wake/release asset and material checks; generated runtime-profile parity; recognition runtime contract; camera/recognition harness contract test.
- Local `:app:testDebugUnitTest` was attempted and blocked before Gradle started: the Gradle 9.3.1 distribution is uncached and the workspace cannot reach `services.gradle.org`. This is `BLOCKED_INFRA`, not a source test failure or a PASS.
- Exact-head Android CI [43671108469](https://github.com/pan277942135/Yujian_App/actions/runs/43671108469) passed on `2e11dea1761296cdd19625bfc45a6c47be1376d9`: Unit Tests, Recognition/Camera Harness, AndroidTest APK compilation, packaged resource/model checks, lint, Release Kotlin compile, Debug APK assembly, and model-trace export.
- Debug APK artifact [14144831040](https://github.com/pan277942135/Yujian_App/actions/runs/43671108469/artifacts/14144831040); APK size 144,397,930 bytes, SHA-256 `4bdbb1ab4347bb8e325ec24369e20e08cea747ad460038f3cb2d38e740de6a3e`, embedded source revision `2e11dea1761296cdd19625bfc45a6c47be1376d9`.
- AndroidTest instrumentation was not run. Device/API36 jobs were skipped; PixelCopy, GPU pixels, and visual QA remain NOT_RUN.
- The separate Control Plane Admission workflow did not trigger because this PR's base is the work branch rather than `main`; its exact-head state is NOT_RUN (baseline admission had passed).
- AndroidTest instrumentation, ADB/device discovery, Emulator, API36 runner, PixelCopy, and visual QA were not run by Work.

## Next iteration decision

`EXACT_HEAD_CI_PASS · DEVICE_EVIDENCE_PENDING`. Install the exact-source artifact from the final Work handoff; record normal AUTO through Stage02, then in the same session export the pinned MAX diagnostic ZIP at `ORB_WAKE` 100%, candidate route READY, Reduced Motion off. Upload only the screen recording and the generated evidence ZIP. Do not treat CI as visual evidence. The next repair must follow the first failing link in the ZIP/trace.

## Current status

`CODE_BUILD_COMPLETE · DEVICE_QA_PENDING_USER · VISUAL_DELTA_NOT_CONFIRMED`

Do not report `R03_VISUAL_PASS`, `R06_VISUAL_PASS`, `GPU_PIXEL_PASS`, or `DEVICE_PASS` from this iteration.
