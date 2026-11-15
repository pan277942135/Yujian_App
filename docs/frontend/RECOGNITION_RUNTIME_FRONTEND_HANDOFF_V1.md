# Frontend Handoff — Recognition Runtime × AI Orb × WaterLight × Glyph × Result

**Repository:** `pan277942135/Yujian_App`  
**Integration PR:** #332 → `main`  
**Integration branch:** `qa/pr331-code-local-unified-20261115`  
**Purpose:** source-level entry map and frozen integration rules for the Android frontend engineer. This is not a visual acceptance report.

## 1. Production flow and ownership

`Capture / Gallery → stable photo first frame → Orb wake → Orb release → photo perimeter → nearest trusted fish-contour contact → Contour A/B/C → fish gold → same-path return to original Orb → Orb-to-Glyph morph → real species-name reveal → residue settle → Result Handoff → stable Result`.

The existing `RecognitionSceneAnimationController` is the sole owner of stage progression, scene time, stage-local time, pause/resume, model gates, event sequence and geometry version. Screens and renderers consume immutable frame samples; they do not own a second clock or decide production-stage completion.

Primary entry points:

- `app/src/main/java/com/yujian/ai/YujianApp.kt` — app/navigation integration and runtime injection.
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt` — Processing screen, photo-first-frame signal and visual host composition.
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneAnimationController.kt` — authoritative stage/event controller.
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt` — semantic stages, identity, local progress, gates and trace.
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightHost.kt` — ORB, PERIMETER, PATH, CONTOUR, RESIDUE and HANDOFF hosts.

## 2. Frozen 13-stage timing

The product contract is **6000ms nominal minimum presentation time** when real model and geometry gates are ready. It is not a hard global deadline. T0 is the first actually presented stable original-photo frame.

| Stage | Runtime enum | Minimum |
|---|---|---:|
| 01 | `PHOTO_STABLE` | 0ms (T0 anchor) |
| 02 | `ORB_WAKE` | 350ms |
| 03 | `ORB_RELEASE` | 600ms |
| 04 | `PERIMETER_ARRIVAL` | 125ms |
| 05 | `PATH_TO_FISH` | 125ms |
| 06 | `CONTOUR_A` | 450ms |
| 07 | `CONTOUR_B` | 450ms |
| 08 | `CONTOUR_C` | 450ms |
| 09 | `FISH_GOLD_PEAK` | 1000ms |
| 10 | `ORB_RETURN` | 350ms |
| 11 | `ORB_TO_NAME` | 850ms |
| 12 | `SPECIES_NAME_REVEAL` | 800ms |
| 13 | `RESIDUE_SETTLE` | 450ms |
| **Total** | | **6000ms** |

Model waits do not compress later stages. The 60,000ms watchdog is a failure/cancellation bound, not a success transition. Stage 13's five handoff milestones are event/geometry driven; do not divide its 450ms evenly or add a second timer.

Authority: `design/pages/recognition/runtime/RECOGNITION_13_STAGE_TIMING_CONTRACT_V1_0.md`.

## 3. AI Orb source map

| File | Responsibility |
|---|---|
| `ui/designsystem/components/aiorb/AIOrbCore.kt` | Reusable Orb host, rendering composition, identity-aware isolation and shader/fallback state |
| `ui/designsystem/components/aiorb/AIOrbMotion.kt` | Orb motion and entrance/activation projection |
| `ui/waterlight/renderer/OrbFilamentRenderer.kt` | Water-light ribbons and R06 path rendering; legacy/candidate modes and per-path diagnostics |
| `ui/waterlight/renderer/OrbCausticsAdapter.kt` | Orb caustics field sampling, bounded bitmap work and identity-aware cache |
| `ui/waterlight/renderer/OrbFluidRefractionProgramV1.kt` | Orb interior fluid/refraction RuntimeShader integration |
| `ui/waterlight/renderer/OrbWakeBreathReleaseV11.kt` | Wake, breathing and release behavior |
| `ui/waterlight/renderer/OrbStage02MaxVisualProfile.kt` | Debug/diagnostic MAX candidate profile; do not silently promote candidate tuning to frozen Release defaults |
| `ui/waterlight/renderer/OrbStage02RenderTrace.kt` | Stage02 rendering trace and capture-bound evidence |

Keep Orb core/source asset, front/rear ribbons and host geometry separate. RETURN and ORB_TO_NAME must retain the original Orb identity.

## 4. WaterLight source map and geometry rules

| File | Responsibility |
|---|---|
| `ui/waterlight/core/WaterLightHost.kt` | Host identity, material ownership and geometry-version gate |
| `ui/waterlight/core/WaterLightEnergy.kt` | RELEASE / ARRIVE / ABSORB / RETURN / TRANSLATE / REVEAL / SETTLE events |
| `ui/waterlight/core/WaterLightPathLedger.kt` | Stable path identity, P* contact point, stored path geometry and reverse RETURN route |
| `ui/recognition/RecognitionPerimeterShaderOverlay.kt` | Existing photo-perimeter GLES renderer |
| `ui/recognition/RecognitionFishFocus.kt` | Trusted Mask contour presentation and fish-focus material layers |

Frozen geometry rules:

- Stage 05 targets the nearest valid point on the current trusted Mask contour: `P* = argmin(P ∈ C_trusted) ||P - O_orb||₂`. Do not search for or hard-code a fish-tail anchor.
- PATH_TO_FISH, CONTOUR_A's initial contact and ORB_RETURN share the same P* and stable path identity.
- CONTOUR_A/B/C progressively present the same trusted Mask geometry. Preserve valid multiple contour loops.
- Never fabricate a fish contour from a Detector BBox. Untrusted/failed Mask follows the existing ORB_ONLY branch.
- FULL RETURN traverses the stored outward path in reverse to the same Orb; do not generate a new random return curve.
- Hosts share one frame but retain host-owned geometry, clipping and material profiles. Reject mismatched Session, Attempt, Image, photo SHA/content version or geometry version.

Authorities:
- `design/pages/recognition/processing/optics/YUJIAN_RECOGNITION_VISUAL_CONTRACT_FREEZE_ADDENDUM_V1.md`
- `design/pages/recognition/processing/optics/AI_ORB_WATERLIGHT_13_STAGE_STATE_MACHINE_V3.md`

## 5. Glyph and Result source map

| File | Responsibility |
|---|---|
| `ui/recognition/reveal/RecognitionRevealController.kt` | Pure projection from the shared scene frame; owns no timer |
| `ui/recognition/reveal/SpeciesNameReveal.kt` | Draws actual classifier-provided Chinese Glyph Path with water-gold sweep, PathMeasure and deterministic ink samples |
| `ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt` | Shared photo/optical continuity layer across Processing and Result |
| `ui/recognition/reveal/RecognitionResultRuntime.kt` | Result Handoff event contract, identity, ordering and five substages |
| `ui/recognition/reveal/RecognitionResultHandoffConsumer.kt` | Validates and commits handoff events to controller and session store |
| `ui/recognition/reveal/RecognitionRevealSessionStore.kt` | Active Session/Attempt payload, classifier result, Mask/path snapshot and handoff state |
| `ui/recognition/reveal/RecognitionResultProductionEvidenceWriter.kt` | Production Result/Handoff evidence output |
| `ui/screens/RecognitionResultScreen.kt` | Result UI, actual text-layout Glyph Path acquisition and Result milestone production |

The displayed species name comes from the current Classifier result. `RecognitionResultScreen` obtains the actual text-layout path; `SpeciesNameReveal` progressively renders that path. Do not substitute a fixed fish name, pre-rendered PNG, generic Text alpha fade or placeholder.

The five ordered Result Handoff substages are `NAME_STABLE` (13.1), `PHOTO_SETTLE` (13.2), `RESULT_SCENE_HANDOFF` (13.3), `RESULT_INFO_ARRIVAL` (13.4) and `RESULT_SCENE_STABLE` (13.5). Each accepted event must match the active Session/Attempt/Photo/Mask/Path/Geometry identity, use the next event sequence and have a strictly later monotonic timestamp. Result stability is not inferred from elapsed time alone.

## 6. Model boundary

The integration's documented APK package uses Detector `DET_FISH_v0.1`, Classifier `MODEL_CROP_M1_v0.2` and includes SlimSAM encoder/decoder assets.

**DET_FISH v0.3 is not included.** Its independent PR #329 remains subject to actual Android inference, orientation retry, BBox/Crop/Top1 and APK-internal model SHA verification. Do not describe this integration build as using Detector v0.3.

## 7. Build and verification

The repository's Android CI is the canonical build path (Gradle 9.3.1). It verifies runtime contracts, packaged model/assets, Orb resource/profile parity, unit tests, camera/recognition harness, Debug APK, AndroidTest APK compilation, lint and Release Kotlin compilation.

Local equivalents:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:lintDebug
./gradlew :app:compileReleaseKotlin
```

AndroidTest APK compilation is not instrumentation execution. Build success is not API36 GPU rendering or visual acceptance.

## 8. Handoff and remaining acceptance

- Exact-head Android CI and Control Plane Admission must pass before Main merge.
- Physical-device API36 instrumentation, Orb R03/R06 PixelCopy comparisons and owner visual acceptance remain separate pending gates. No device/visual PASS is claimed.
- The prior local integration report records 426 JVM tests and build/package checks as PASS on its recorded source SHA. Always use an APK whose embedded `build_sha` equals the current merged source SHA.
- The R01B external frozen-bundle verification and camera harness synthetic positive path requiring `ffprobe` remain documented limitations.

## 9. Frontend change rules

1. Read the frozen timing, state-machine and visual addendum before changing rendering.
2. Consume `RecognitionSceneFrame`; do not add a second clock, timer or stage machine.
3. Preserve Session/Attempt/Photo/Mask/Path/Geometry identity end-to-end.
4. Keep real model gates and failure/ORB_ONLY behavior; never use synthetic Mask/BBox geometry to make an animation appear complete.
5. Keep classifier-derived Glyph geometry dynamic.
6. Treat visual candidates and diagnostic modes as candidates until current-head device evidence and product-owner review are complete.
