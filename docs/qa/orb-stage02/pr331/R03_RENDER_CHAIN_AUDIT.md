# PR #331 R03 Render Chain Audit — Iteration 001

**Baseline:** `762b656591be3129bf2ec02768a65109371fe4ab`
**Scope:** Stage02 MAX fluid timing, AGSL resampling, Canvas/Compose composition, and Stage02→Stage03 transition.
**Evidence rule:** Source-confirmed behavior is not a GPU or product-visibility pass.

## Render chain

| Node | Source and function | Input → output | Clock / gate | Failure or limitation | Evidence |
|---|---|---|---|---|---|
| Scene clock | `RecognitionSceneAnimationController.sceneElapsedMs()` and `currentFrame()` | First-photo-frame anchored monotonic time → `RecognitionSceneFrame.sceneElapsedMs` | AUTO advances from the scene clock; pause/manual preview uses controller pause/preview state | MANUAL preview completion can stop the clock at 100%; this is diagnostic behavior, not proof of AUTO failure | L0 source |
| Stage progress | `stageProgress()`, then `RecognizingScreen` builds `WaterLightFrame` | Stage-local elapsed / 350 ms in AUTO, / 3000 ms in manual diagnostic → normalized progress | `RecognitionSceneFrame.stageProgress` is the semantic wake input | At a pinned diagnostic progress, the manual clock does not advance by itself | L0 source |
| Production frame | `RecognizingScreen` (`sceneBaseFrame`, `authoritativeSceneFrame`) | Controller frame + scene/optical elapsed + geometry + identities → `WaterLightFrame` | Normal AUTO uses controller `sceneElapsedMs`; diagnostic may be paused | A diagnostic frame cannot stand in for the production 350 ms sequence | L0 source |
| Shared R03/R06 field | `OrbInteriorOpticalField.frame()` | stage/progress/reduced-motion/profile/scene elapsed → wake, displacement, mix, phase | MAX only; wake envelope is Stage02 80–280 ms and Stage03 fades over 80 ms | The previous V3 phase was progress-only; at progress 100 its semantic phase became constant if the Scene Clock was paused | L0 source |
| Shader uniforms | `OrbFluidRefractionProgramV1.update()` called from `AIOrbCore` `SideEffect` | `WaterLightFrame` → `sceneSeconds`, MAX wake, phase, displacement, mix, edge bounds | Runs when Compose recomposes; RuntimeShader instance is reused by Session/Attempt | Uniform revision proves an update call, not that GPU pixels changed | L0 source |
| AGSL sampling | `orb_fluid_refraction_v1.agsl` `main()` | input coordinate + `liquidFlow()` + shared wake field → bounded resample and premultiplied output | `wakeOffset = flow × visibleDiameter × maxDisplacementD × interiorMask`; output mix is separately capped by `maxInteriorMix × interiorMask` | Prior code also interpolated the sample coordinate by `maxInteriorMix`, applying the same strength once to displacement and again to compositing | L0 source |
| Shader layer | `AIOrbCore` Raw Core / Shader Overlay group | Original WebP + Shader image with `RenderEffect` → offscreen Core group | Shader overlay only in NORMAL, optical gate open, instance/effect available, READY, alpha > 0 | Route/status and Compose draw submission are not GPU readback | L0 source |
| Final composition | `AIOrbCore` layer order | R06 back exterior → Core group → R03 Canvas optics → camera hero → R06 front | Same `WaterLightFrame`/geometry | R03 local movement can be diluted by output mix and Core overlay composition; exact final visibility needs device pixels | L0 source |
| Capture | `OrbStage02VisualEvidenceExport.share()` | Debug mode + projected sample → capture marker → window frame-commit callback → PixelCopy PNG | Stage02 only; fixed stage progress and explicitly projected shared optical sample for T0/T+500/T+1500 | No capture has yet run on the updated APK; frame-state capture is not a direct GPU shader dump or human judgement | L0 implementation; runtime NOT_RUN |

## Time and update findings

`RecognitionSceneAnimationController.currentFrame()` supplies both stage progress and scene elapsed. In normal AUTO rendering, `RecognizingScreen` samples `controller.sceneElapsedMs(visualNowMs)` and uses that same elapsed value in `WaterLightFrame`. Manual diagnostic completion can pause the shared clock, so progress 100% alone does not imply continuing scene time.

This iteration makes MAX `semanticPhase` the sum of the semantic wake phase and a continuous phase derived from that same `sceneElapsedMs` at 0.65 cycles/second. `maxFluidWakeEnvelope()` remains a pure stage-progress projection. Reduced Motion sets phase and displacement to zero. Stage02 end and Stage03 start use the same wake endpoint and scene elapsed, so the added phase is continuous at the boundary.

`AIOrbCore` updates uniforms in a Compose `SideEffect`. `sharedFrame.elapsedMs` participates in the composable's remembered frame trace and is passed into the fluid update; the R03 overlay draw callback records a capture-bound `ORB_R03_RENDER` submission and uniform revision. This proves source-level update/draw submission when the event exists. It does not prove SurfaceFlinger/GPU output changed.

## Composition finding and targeted change

Before this iteration, AGSL set `wakeCoord = boundedCoord(safeCoord + wakeOffset)`, then used `mix(safeCoord, wakeCoord, maxInteriorMix)` for the sampling coordinate, and also applied `maxInteriorMix × interiorMask` to output alpha. This adds a second attenuation to displacement. The iteration changes `finalCoord` to `wakeCoord`; interior mix now gates only the dynamic layer contribution. The original alpha silhouette remains premultiplied at output, the Raw Core remains a separate underlay, and edge protection remains in the displacement mask.

This is a source-confirmed correction. It has not been measured on the GPU yet, and the old code's 8% coordinate interpolation loss at the 0.92 cap alone cannot be called the visual root cause.

## Evidence from 264191.mp4

The accessible 46.911 s, 868×1920 clip is a diagnostic screen recording. It shows MAX and the candidate/READY status in the diagnostic UI and includes mode changes among full effects, R06-only, and R03-only. It does not provide a clean sequence with identical mode, StageProgress, geometry, and successively advancing production Scene Clock. The available clip therefore cannot confirm or disprove continuous R03 motion. The PixelCopy evidence ZIP is not available; no R03 pixel metrics are reported for this baseline.

## Iteration 001 R03 classification

| Finding | Classification | Evidence grade | Status |
|---|---|---|---|
| Completed manual Stage02 preview stops Scene Clock progression | R03-F | L0; observed diagnostic state in clip | Confirmed diagnostic behavior; production impact unconfirmed |
| Progress-only phase held fixed at Stage02 100% | R03-Candidate | L0 source | Corrected to include the existing Scene Clock; no device pixel proof |
| Duplicate attenuation on MAX displacement sampling | R03-D-Candidate | L0 source | Corrected in AGSL; no GPU pixel proof |
| AUTO Compose/Shader path updates every advancing frame | R03-U | L0 source tracing only | Runtime/actual layer refresh remains unmeasured |
| Local resampling creates perceptible motion | R03-U | No same-progress PixelCopy series | Not measured |

**Current R03 result:** `CODE_PATH_CORRECTED_PIXEL_DELTA_NOT_MEASURED`. The fixed-progress sample export is diagnostic projection, not proof that production AUTO advances at fixed progress.
