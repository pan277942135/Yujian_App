# Recognition Runtime V1.2 execution checkpoint

**Task:** `YUJIAN_RECOGNITION_RUNTIME_13_STAGE_INTEGRATION_V1_2`
**Repository / PR:** `pan277942135/Yujian_App` / #317 (`Draft`, `Open`, `Unmerged`)
**Branch:** `feature/orb-photo-stable-visual-closure-v1`
**Main baseline:** `74cad95b4f673bd9c8c60d16839f036d5886d933`
**Current validated runtime source:** `beed77ad31bb54010148c7bf459f3b445ccbcd23`
**Current checkpoint:** CP01–CP07 implementation and exact-source build complete; device visual acceptance remains `NOT_RUN`.

## CP01–CP04 status

| CP | Status | Source / evidence |
|---|---|---|
| CP01 PHOTO_STABLE | Implemented | `81a0c257417c0be050e8dd57ace95e97619babf4`; existing exact-source workflow passed. |
| CP02 ORB_WAKE | Implemented; device visual `NOT_RUN` | `ab2407ab42459f12ccb8178df7ebe3badc396c13`; Orb workflow `42010365270` passed. Core is independently drawn below optical effects. Debug A/B/C comparison states remain available. |
| CP03 ORB_RELEASE | Implemented; resource freeze identity `NOT_CONFIRMED`; device visual `NOT_RUN` | `4dc78515bb006f2952386d046db079eb2dce3162`; Orb workflow `42011287356` passed. The existing five R06 paths and one shared SceneClock remain in use. |
| CP04 PATH_TO_FISH | Implemented; mask-tail heuristic intentionally returns no anchor when ambiguous; device visual `NOT_RUN` | Code commit `9e8d99471c2db09c9e244d804a4cc8da3c5d3c2c`, assertion correction `84c2bf30d51d38c3720f4124315ec799de601bd1`; exact-head Orb workflow `42019562279` passed. |
| CP05 CONTOUR_A/B/C | Implemented from trusted current Mask; device visual `NOT_RUN` | Source `ead45796f8332ef673fd615402c72b995fb5f09d`; exact-head Orb workflow `42023472919` passed unit tests, Debug and AndroidTest APK builds, packaged asset checks, and artifact upload. |

## CP02 root cause and targeted correction

The Core WebP existed, had non-empty alpha, and was packaged with the source-matching hash. The failure path was in composition: the only Core `Image` had its `RenderEffect` set on the same graphics layer, so the visible base image was coupled to shader input/output alpha and the effect layer bounds. A transparent, clipped, unavailable, or otherwise invalid effect could therefore remove the only visible Core. This is the code-level root cause; actual AGSL frame-by-frame visual confirmation remains `NOT_RUN`.

The corrected renderer draws the original Core image as the always-visible base and places the filtered copy in a separate optical overlay. `CORE_ONLY`, `CORE_WITH_OPTICS`, and `FULL_ORB_WAKE` gate optional optics/detail over the same source Core and shared SceneFrame. Shader creation failure is logged; the original Core and clipped Canvas fallback remain available, while failure is not reported as full optical success. Host bindings reject mismatched session/attempt/image/photo hash/content version/geometry host or geometry version. No second Orb or clock is created.

CP02 automated coverage executed in the exact-source Orb workflow includes `OrbFluidOpticalIntegrationV1Test.agslAndComposeUseOneFrameAndKeepOriginalAlpha`, `AIOrbPhotoStableRenderingPolicyTest.threeWakeModesKeepTheOriginalCoreAndGateOnlyTheOptionalLayers`, `OrbWakeBreathReleaseV11Test`, `OrbRuntimeProfileV1Test`, `WaterLightHostBindingTest.everyHostRequiresCurrentSessionAndValidOwnGeometry`, and `OrbGrassFrozenAssetsV11Test.sourceAndAndroidRuntimeCoreArePinnedAndTheOrbDrawsOneBakedTexture`. Shader failure fallback is implemented with an explicit warning log; failure injection/AGSL visual behavior itself was not run on a device.

## CP04 root cause and correction

`WaterLightMaskAdapter.toPhotoSpace` already converts the current SlimSAM ROI contour into canonical, EXIF-normalized whole-photo coordinates. The prior seek anchor then applied the detector BBox a second time and averaged contour points. That produced neither the fish-tail contact nor a stable source-photo coordinate. It could also move when the detector crop changed.

The corrected anchor resolver selects the largest closed outline, estimates its principal axis, and samples cross-section widths to identify a caudal-peduncle narrowing between a widened tail profile and the body. It returns no anchor when that shape is ambiguous. The resulting source-photo point uses the current `RecognitionImageTransform`; points outside the visible Crop viewport are rejected rather than clamped to a fabricated edge point.

The outward path ledger now binds its fish segment to session, attempt, image, photo SHA, geometry version, and a current Mask fingerprint. A return path can reuse the fish segment only when that Mask fingerprint still matches. This prevents stale attempt or Mask geometry from entering the Orb return route.

While Detector is ready and either Classifier or SlimSAM is still pending at `PATH_TO_FISH`, the existing perimeter shader remains active at low opacity (`0.12`, or `0.06` under reduced motion) and continues to sample the shared optical SceneClock. The controller keeps its existing Classifier/SlimSAM terminal join and FULL / ORB_ONLY decision; no completed release stage or second clock is replayed.

Changed CP04 files:

- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchor.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightPathLedger.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchorTest.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMathTest.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightPathLedgerTest.kt`

## CP04 verification

- `git diff --check`: **PASS**.
- Control-plane admission: **PASS** on the CP04 source.
- First exact-source workflow `42018883250`: **FAIL** on a newly added assertion that assumed a fixed Crop/Fit pixel delta. The resolver and compilation passed; the assertion was changed to compare each result directly with the corresponding `RecognitionImageTransform` mapping.
- Rerun `42019562279` on `84c2bf30d51d38c3720f4124315ec799de601bd1`: **PASS**. Unit tests, Debug APK build, AndroidTest APK build, and packaged resource verification all passed.
- Workflow artifact ID `13411636116`, artifact ZIP size `143,349,184` bytes, artifact ZIP SHA-256 `88f63f470f1fb65693da1d15494250e934a6cf5b6081bdae334013457f84d452`. This is an intermediate CP04 build artifact, not the final V1.2 APK.
- Real-device visual checks (AGSL, Orb Wake, fish gold, Glyph, Result handoff): **NOT_RUN**.
- Full PR CI remains separate from this feature workflow; the known branch-state promotion gate and unrelated Empty Home V2 contract failure are not marked PASS here.

## CP05 root cause and implementation

The accepted SlimSAM Mask was already converted to canonical whole-photo coordinates, but `RecognitionContourPathCache.reveal()` started at the path extractor's first vertex. That vertex is not the resolved fish-tail contact, so the contour could begin at an arbitrary body point. The previous Core, Soft, and Halo layers also shared one intensity envelope and did not have independent reveal timing.

CP05 reuses the current Mask-derived caudal-peduncle anchor from CP04, maps it through the same current `RecognitionImageTransform`, and declines contour drawing if the visible tail contact is unavailable. The primary closed contour now grows in two wrapped directions from that point and meets at the opposite end. Secondary Mask islands wait until C. The existing A/B/C stage progress drives the path on the one SceneClock. Core, Soft, and Halo retain separate alpha envelopes and separate width/blur draws. The real Mask extractor remains the source of the path and continues its geometry-safe smoothing/simplification; no synthetic contour or BBox outline is introduced.

Changed CP05 files:

- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishTailAnchor.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishFocusTest.kt`

## CP05 verification

- Added unit coverage for tail-origin bidirectional contour progression, wraparound and convergence; independent Core/Soft/Halo envelopes; and the ORB_ONLY no-contour path. Existing tests cover EXIF-normalized Mask tail resolution, Crop/Fit mapping, ambiguous-mask rejection, identity scoping, and contour extraction.
- First exact-source Orb workflow `42022698826` on `479da6bec14a4e053a71c33bacf6eb95503de475` failed Kotlin compilation at `RecognitionFishTailAnchor.kt:132:5` (`Missing return statement`). The failure and source SHA were recorded using `failure_gate.py`. The isolated repair added the required `return` and was committed as `ead45796f8332ef673fd615402c72b995fb5f09d`.
- Exact-source Orb workflow `42023472919` on `ead45796f8332ef673fd615402c72b995fb5f09d`: **PASS**. Full `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, packaged Core/capture asset verification, and artifact upload passed.
- Artifact ID `13412648411`; ZIP size `143,353,050` bytes; ZIP SHA-256 `95ddc762487169e3a6ce6b059f37a65304e54b20485233e2f087fdb0a4a0348a`. This is an intermediate CP05 artifact, not the final V1.2 APK and not an APK checksum.
- `git diff --check`: **PASS**. Local Gradle was unavailable; the exact-source GitHub workflow ran the Android tasks above.
- Full PR CI is **FAIL/BLOCKED**, separate from the passing feature workflow: Android CI `42023480004` stopped at the existing `EMPTY_HOME_RUNTIME_V2` contract; control-plane admission `42023480030` rejected branch-local state files during canonical-promotion validation. The unrelated contracts were left unchanged and neither check is marked PASS.
- Device visual checks (AGSL, Orb Wake, fish gold, Glyph, Result handoff): **NOT_RUN**.

## CP06 Orb Return / real Glyph (complete)

### Root cause and correction

The shared SceneClock already owned ORB_RETURN, ORB_TO_NAME, and SPECIES_NAME_REVEAL, but the result renderer had no single projection for the six frozen child states. The title could switch to static Compose text independently of the Orb, and the Glyph Path/cache did not have a complete current-attempt identity boundary. The stage minima also still reflected the pre-freeze timing contract, and one manual-preview test retained the old reveal durations.

The new `RecognitionOrbGlyphTransition` projects all six child states from the existing `RecognitionSceneFrame`: 11.1 ORB_CORE_HOLD, 11.2 ORB_HORIZONTAL_STRETCH, 11.3 LIQUID_BRUSH_TRANSITION, 12.1 STROKE_AGGREGATION, 12.2 GLYPH_EMERGENCE, and 12.3 SPECIES_NAME_FINAL. It creates no clock. The same keyed `AIOrbCore` remains on screen through Orb return and the Glyph transition. Glyph geometry comes from the laid-out real classifier name (`TextLayoutResult.getPathForRange`); cache identity includes Session, Attempt, photo, photo SHA, species key, and returned display name. FULL and ORB_ONLY use that same transition projection.

The 13 stage minima and reveal envelopes now match the frozen 6000ms contract, including 1000ms fish gold with the 500ms peak hold, the 850ms Stage 11 subphases, and the 800ms Stage 12 with a 450ms final-name hold. Manual preview remains 3000ms per main stage.

### Changed files

- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionOrbGlyphTransition.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealController.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/reveal/SpeciesNameReveal.kt`
- `app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneAnimationController.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/RecognitionSceneFrame.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionFishFocusTest.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionVisualStateControllerTest.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionOrbGlyphTransitionTest.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPolicyTest.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/RecognitionSceneFrameTest.kt`

### Verification and failure ledger

- Initial CP06 workflow `42029128760` on `465b23a08d422c53b41f2a478f56e2d07834eac3`: **FAIL**, Kotlin parser rejected an escaped quote in the added diagnostic interpolation. Corrected by `414a611b665590c93b36fcc3f3502dbd1ff96434`.
- Workflow `42030011021` on `414a611b665590c93b36fcc3f3502dbd1ff96434`: Kotlin compilation passed; **395/396 tests passed**. The remaining assertion expected old 170/380ms reveal durations. Updated the expectations to the frozen 350/850/800/450ms stage minima.
- Exact-source Orb workflow `42030743624` on `f53580b5554d62e8ef26e485c2f902c885ec7121`: **PASS**. All unit tests, `assembleDebug`, `assembleDebugAndroidTest`, packaged Core/asset verification, checksum staging, and workflow artifact upload passed.
- CP06 intermediate workflow artifact ID `13415968163`, ZIP size `143,360,209` bytes, ZIP SHA-256 `a205d780428b56643e0553af3aa8b6099e400b33f4a169c9815141e11e3133d0`. This is not the final V1.2 APK.
- `git diff --check`: **PASS**. Real-device Orb/Glyph visual checks: **NOT_RUN**.
- Full Android CI `42030743619`: **FAIL** before Gradle at `EMPTY_HOME_RUNTIME_V2`; it reports measured-anchor contract divergences in `EmptyHomeMotion`. No Empty Home code or contract was changed for this task.

## Current recovery point

- Current CP: **CP07 complete; final Debug APK built and uploaded**.
- Latest runtime implementation source: `beed77ad31bb54010148c7bf459f3b445ccbcd23`; its exact-source workflow `42037876704` passed unit tests, `assembleDebug`, `assembleDebugAndroidTest`, packaged Core/capture asset checks, SHA staging, and artifact upload.
- CP07 changed files: `RecognitionResultHandoffTransition.kt`, `RecognitionResultScreen.kt`, and `RecognitionResultHandoffTransitionTest.kt`.
- CP07 contract correction: an earlier Stage 13 draft partitioned the shared 450ms parent progress with fixed normalized thresholds. The timing contract explicitly disallows invented child timing windows. The final projection uses the single SceneClock frame; photo geometry progress drives repositioning, the current photo identity and result geometry gate the handoff, actual classifier data arrival gates information display, and the rendered stable event gates one-time commit. No child timer was added.
- CP07 tests cover 13.1–13.5 progression, monotonic same-photo geometry, missing identity/geometry/data guards, stable-event gating, and idempotent commit.
- Final APK: `Yujian_Recognition_Runtime_V1_2_Debug.apk`; source `beed77ad31bb54010148c7bf459f3b445ccbcd23`; 144,083,254 bytes; SHA-256 `1de29d32ec185b32b5c1cdc5f156e22a25afed79fe1bbe54cb10829391d0dd66`. The exact-source CI artifact ZIP is ID `13419188038`, 143,372,481 bytes, SHA-256 `38ba6c85d2bd6b5da2e3c84f25324d2ed9d2081b40a7ee83009d67f8b242f8fc`. The APK replaced the existing Library file `libfile_beb7dfacadc881918b79857f1be3c2cf`, now version 1.
- Full Android CI run `42037876742` remains **FAIL** before Gradle because the pre-existing `EMPTY_HOME_RUNTIME_V2` verifier reports canonical measured-anchor mismatches; this task changed no Empty Home contract/code. Control-plane admission run `42037885114` is **BLOCKED/FAIL** because Main-promotion validation rejects required branch-local state files. Neither is counted as a feature build pass.
- Real-device visual checks remain **NOT_RUN**: AGSL/Core comparison, Orb Wake, fish gold, Glyph, and Result handoff. No API28 or device visual run was performed.
- Final tests and APK delivery: **COMPLETE**. Remaining action: product-owner device installation/visual acceptance, resolve the independent Empty Home/control-plane gate, and confirm CP03 R06 frozen source identity; do not merge PR #317.


## Frozen product timing decision — 6000ms (2026-11-05)

**Authority:** [13-stage Runtime timing contract V1.0](design/pages/recognition/runtime/RECOGNITION_13_STAGE_TIMING_CONTRACT_V1_0.md) and [machine-readable JSON](design/pages/recognition/runtime/recognition_13_stage_timing_contract_v1_0.json).

**Decision:** FULL recognition path nominal minimum visual duration is frozen at **6000ms**. T0 is the first actually presented PHOTO_STABLE photo frame. Stage 01 is a zero-duration clock anchor; stages 02–13 sum to 6000ms. The exact stage boundaries, high-light subphase allocations, AUTO/MANUAL rules, model wait behavior and FULL/ORB_ONLY distinction are defined only by the linked contract.

| Stage | Frozen minimum | Cumulative end |
|---|---:|---:|
| 01 PHOTO_STABLE | 0ms (T0 anchor) | 0ms |
| 02 ORB_WAKE | 350ms | 350ms |
| 03 ORB_RELEASE | 600ms | 950ms |
| 04 PERIMETER_FORM | 125ms | 1075ms |
| 05 FISH_SEEK | 125ms | 1200ms |
| 06 CONTOUR_A | 450ms | 1650ms |
| 07 CONTOUR_B | 450ms | 2100ms |
| 08 CONTOUR_C | 450ms | 2550ms |
| 09 FISH_GOLD_PEAK | 1000ms | 3550ms |
| 10 ORB_RETURN | 350ms | 3900ms |
| 11 ORB_TO_NAME | 850ms | 4750ms |
| 12 SPECIES_NAME_REVEAL | 800ms | 5550ms |
| 13 RESIDUE_SETTLE | 450ms | 6000ms |

High-light holds are frozen: Stage 09 includes 300ms gold formation + **500ms continuous peak hold** + 200ms handoff; Stage 11 is 100ms + 350ms + 400ms; Stage 12 is 150ms + 200ms + **450ms readable species-name hold**. Stage 13's five child frames share 450ms; no independent child milliseconds are invented.

Model waits do not compress the sequence. The existing 60s model watchdog remains separate from the visual schedule. Debug MANUAL remains 3000ms per main stage with auto-pause. The result scene remains visible after reaching 6000ms.

**Implementation status:** Runtime timing and Stage 13 handoff are implemented and verified at exact source `beed77ad31bb54010148c7bf459f3b445ccbcd23`; exact-source Orb workflow `42037876704` passed unit tests, Debug and AndroidTest APK assembly, and packaged resource checks. Final Debug APK was delivered to Library. Device visual acceptance remains `NOT_RUN`.
