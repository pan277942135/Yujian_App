# Iteration 001 — R03/R06 targeted source fixes and evidence pipeline

## Result

**Iteration status:** `SOURCE_FIXES_APPLIED · ROOT_CAUSE_NARROWED · VISUAL_DELTA_NOT_CONFIRMED`
**Device status:** `DEVICE_QA_PENDING_USER`
**PR:** #331 must remain Open / Draft / Unmerged.
**Baseline:** `762b656591be3129bf2ec02768a65109371fe4ab`
**Final source SHA:** pending exact-head commit and CI.

This round advances the code and the evidence needed for the next device pass. It does not claim that the current Orb looks better. The only accessible visual baseline, 264191.mp4, is a manually switched diagnostic screen recording and does not contain the same-mode, same-progress, advancing-clock sequence or path ablations required for attribution.

## Causal changes

| Area | Old behavior and failure mechanism | Change | Automated check | Actual result |
|---|---|---|---|---|
| R03 continuous phase | MAX phase used only stage progress. When progress stayed at 100% and manual preview paused the shared clock, phase was constant. | Added a continuous 0.65 cycles/second component derived from the existing scene elapsed time; wake strength stays tied to stage progress. Reduced Motion remains static. | New pure test compares same progress/different elapsed and Stage02-end/Stage03-start phase. | Source changed; runtime not yet executed. |
| R03 sampling | The sample coordinate was mixed toward the unwarped coordinate with `maxInteriorMix`; dynamic layer output then applied `maxInteriorMix` again. | Sample once at `wakeCoord`; apply mix only to the dynamic contribution. Raw Core and edge mask remain independent. | Existing fluid integration test now asserts the single-coordinate path and frozen premultiplied output. | Source contract only; no GPU output measured. |
| R06 Back glass | Body alpha included `radialMask²`, then the Paint radial gradient faded the edge again. Mid/near-edge formula showed duplicate suppression. | Keep one transmission factor (`0.80 × source Core alpha`) and one radial Paint mask. Keep original G01/A01 paths and Back identity. | New unit test validates the single source/transmission factor. | Source/math confirmed; final pixels pending. |
| Evidence capture | Old metadata was shared across mode changes and one Compose frame did not verify a target Orb draw/window commit. No individual-path final-pixel ablation existed. | Added CaptureId/frame-state validation, draw marker, Android frame-commit callback, PixelCopy crop validation, per-PNG metadata/hash, five same-frame path exclusions, R03 projected time samples, per-capture renderer metrics, and PixelCopy analysis. | New source-level assertions; AndroidTest adds real Compose final-image ablation cases. | Device capture not run. |

## Additional source review corrections

- Stage03 holds the Stage02 semantic flow phase at its terminal value while its MAX displacement fades over the existing 80 ms hand-off. The continuous component still uses the shared Scene Clock, so the semantic component does not reverse during release.
- Pixel evidence is labeled measured only when the pair is aligned and the corresponding ROI or ablation comparison reports `MEASURED`. Route eligibility and state inference remain separately labeled.
- Recurring renderer issues use a bounded, rate-limited trace. The JSONL ring buffer reports dropped-event overflow rather than growing without bound.

## Full chain findings

R03 runs from the first-photo-frame anchored controller clock through `RecognitionSceneFrame` and `WaterLightFrame`, `OrbInteriorOpticalField`, RuntimeShader uniforms, AGSL `main()`, the Core image RenderEffect, and final Compose composition. The implementation records SceneElapsed, phase, uniform revision, route/status, and draw submission separately. `READY`, a revision increment, and a Compose draw are not GPU-pixel evidence.

R06 continues to use the five frozen path IDs and current-frame valid runs. G01/A01 use Back Exterior plus Glass Transmission; G02/A02/C01 use Front. Canvas metrics now bind to CaptureId; final per-path contribution comes from `R06_FULL` versus `R06_WITHOUT_<path>` PixelCopy captures. `PATH_DRAWN`, `PIXEL_CONTRIBUTION_MEASURED`, and `VISUALLY_DISCERNIBLE` remain separate states.

The detailed chains and numeric glass-mask calculation are in [R03_RENDER_CHAIN_AUDIT.md](../R03_RENDER_CHAIN_AUDIT.md) and [R06_RENDER_CHAIN_AUDIT.md](../R06_RENDER_CHAIN_AUDIT.md). The issue ledger remains the single continuing list.

## Progress relative to iteration baseline

- **Root-cause localization progressed:** identified two concrete R03 code mechanisms (progress-only MAX phase and duplicate displacement attenuation) and one concrete R06 mechanism (squared radial factor followed by another edge fade).
- **Evidence capability progressed:** the exporter can now separate runtime eligibility, Canvas submission, PixelCopy result, per-path pixel difference and human judgement. It also flags when the current Attempt lacks a production AUTO Stage02 trace.
- **Visual delta:** `NOT_CONFIRMED`; no current-source PixelCopy or GPU instrumentation was executed.

## Key open findings

- R03 AUTO clock/uniform/draw/pixel chain must be verified on the final exact-source Debug APK.
- The formula correction may improve R06 glass pixels, but no current-source final screen attribution exists yet.
- G02/A02/C01 remain unlocated until per-path pixels and captured valid-run/body metrics are available.
- Frame marker and frame-commit fence are implemented, but not yet exercised on HUAWEI/API36.

## Delivery state

Static asset/profile/runtime contract checks and the existing camera/recognition harness contract tests passed locally. The local Unit Test task is blocked before Gradle starts because Gradle 9.3.1 is not cached and this environment cannot reach `services.gradle.org`; this is infrastructure-blocked, not a test failure. Exact-head CI, AndroidTest compilation, lint, Release Kotlin compile, packaged model checks, APK artifact and APK SHA-256 remain pending until the source is pushed. Instrumentation and device visual QA remain out of scope for Work and are not claimed.
