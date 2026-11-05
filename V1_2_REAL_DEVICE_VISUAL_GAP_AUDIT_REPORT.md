# V1.2 Real Device Visual Gap Audit and Targeted Fix Report

**Task:** `YUJIAN_V1_2_REAL_DEVICE_VISUAL_GAP_AUDIT_AND_FIX_V1`
**Repository:** `pan277942135/Yujian_App`
**PR:** #317 (`feature/orb-photo-stable-visual-closure-v1`, Open / Draft / Unmerged)
**User-specified source baseline:** `beed77ad31bb54010148c7bf459f3b445ccbcd23`
**PR source before this audit:** `bf9c3f634fcba010f3d6ac2beaf9933ea7cfd3be`
**Main baseline:** `74cad95b4f673bd9c8c60d16839f036d5886d933`

## Status

- Audit source evidence: complete.
- Targeted source changes: published to PR #317. Final audited code commit: `f7eddced9398383763422d30be121d3226aa196e`.
- Exact-source Orb build workflow [42063850368](https://github.com/pan277942135/Yujian_App/actions/runs/42063850368): `PASS` — 402 unit tests, Debug APK, AndroidTest APK, packaged Core/resource checks and artifact upload.
- Debug APK: [`Yujian_Recognition_Runtime_V1_2_Debug.apk`](https://chatgpt.com/library?file_id=libfile_beb7dfacadc881918b79857f1be3c2cf&account_id=personal), 144,085,838 bytes; SHA-256 `3893ee4e8bb3c79f1207cb663446d16b3aa6d1f7bfb3d8ca1de12d2c27671069`. Existing Library item replaced successfully; current version 2.
- AndroidTest APK: 2,883,477 bytes; SHA-256 `5e998614e58946988dfbd7bd1333e6ff5a26d107c4138865e10cfa1472c73729`.
- Artifact: ID `13434786213`, 143,373,877 bytes; SHA-256 `69463c283d64ebe50401055d5faeae4f5c67338901b3598c055545b292f0cc39`.
- Post-fix real-device visual acceptance: `NOT_RUN`; product-owner retest remains required.
- Frozen R06 geometry identity: `NOT_CONFIRMED`; the actual runtime resource hashes differ from the V1.1 frozen hashes. No R06 asset was replaced.
- Control-plane admission: `BLOCKED`; static registry/feature/state-machine/unified-contract validators passed, but canonical branch sync and branch-local state validators returned errors (details below). No status was changed to PASS.

## Video evidence

Used only the newly uploaded ~40-second video, not `243459.mp4`.

- Duration: 40.063 s; 720×1594; about 59 fps; 2,262 decoded frames.
- SHA-256: `6c185d8473782a014bb0fa34ba02872c6d445cff8822cd3f4695f022e2e27f45`.
- All 2,262 frames were decoded and reviewed in indexed contact sheets; keyframe crops are retained under the audit evidence directory in the workspace.
- Around 10 s / Stage 02: the Orb remains present; no clearly perceptible internal wake transition is visible in the sampled recording.
- Around 18–25 s / A/B/C diagnostic switching: the visible Orb changes little between the selected modes.
- Around 30 s / Stage 03: one thin light trace is visible near the Orb; the recording does not establish that all five R06 paths are visible.
- Around 36–39 s / Stage 04: the photo shows a strong, nearly complete perimeter effect appearing quickly. The static rounded card border is separate from the animated WaterLight.
- The video ends while still on Stage 04. Stage 05 and an actual fish-tail contact are `NOT_OBSERVED` in this recording.

## Design sources inspected

- Full `ORB_WAKE_BREATH_RELEASE_VISUAL_CONTRACT_FROZEN_V1_1.json` (`FROZEN_FOR_WORK`), including Core identity/alpha, S1/S2, R01B, R03, R06 path IDs/order, alpha caps, timing, clipping and fallback.
- PR #318 handoff: G01–G06, six-group overview, 21-frame CSV/JSON, runtime component mapping, frozen parameter registry MD/JSON, assets manifest and QA report.
- Existing runtime timing and geometry code, including `RecognitionSceneFrame.kt`, the 13-stage timing contract, perimeter material, mask adapter, tail-anchor implementation, path ledger and fish-contour renderer.
- PR #318 remains a product-review handoff, not a blanket visual freeze. Its six-group overview PNG does not match the manifest SHA and cannot serve as a pixel-accurate source; G01–G06 themselves passed their recorded SHA checks. The PR #318 QA status and pending items were retained.

## Root-cause matrix

| Defect | Frozen requirement and actual code path | Finding and certainty | Targeted change |
|---|---|---|---|
| P0-1 Orb wake weak | Asset01 remains the original Core. `OrbWakeBreathReleaseV11.wake()` uses the 80 ms optics and 220 ms water/grass thresholds over the 350 ms ORB_WAKE stage. `AIOrbCore.OrbVisual` draws the original Core independently and overlays the optional RenderEffect image; shader failure logs and leaves the raw Core visible. The frozen `.22` optical group alpha was retained. | `PROVEN`: Core alpha was previously reduced by up to 0.18 during RELEASE/RETURN, contrary to frozen Asset01 alpha invariance. `PROVEN`: source keyframe thresholds match 80/220/350 ms. An exact 220ms unit-test sample exposed Float rounding residue; a 0.001 ms boundary tolerance now preserves the frozen zero-at-keyframe start. `UNVERIFIED`: the video’s weak Wake cannot be attributed to a specific runtime cause without device API/logcat; AGSL creation/runtime success, actual mode and per-source alpha cannot be recovered from pixels alone. The `.22` cap is frozen and was not reinterpreted as a defect. | Keep `coreImageAlpha = 1f`; keep the raw image independently drawn; add `wake_band`, optical/water/grass envelope, renderer mode, path count and life alpha to the existing stage log. Add 80/220ms boundary and Core-alpha invariant coverage. No Core pixels, optical alpha budget, scale, aspect ratio or position changed. Wake visual closure remains pending product retest. |
| P0-2 Orb R06 release | `AIOrbCore` and `ProcessingWaterOrb` default to `ORB_V1_CANDIDATE`; `OrbWakeRenderMode` defaults to FULL on the recognition screen. `OrbFilamentRenderer` consumes five Kotlin-generated Bezier profiles: G01/G02/A01/A02/C01, with back/front split and frozen 1.28 alpha gain, .82 cap, 1.18 release-head gain and stage-local release/return. | `PROVEN`: candidate mode and five path IDs are in the actual code; the user's video does not demonstrate five clearly visible paths. `PROVEN`: the frozen V1.1 geometry/profile/SVG hashes do not match current repository resources. Frozen expected geometry/profile/SVG: `a37d947d…a601a5`, `075e8390…cad864`, `66966e0b…4db52b`. Runtime geometry/profile/SVG: `1dfc85e6…f4cdc`, `8c7f998b…f21c10`, `d321c2bd…87389`. Runtime reads generated Kotlin from the current JSON/profile; it does not draw the SVG directly. `UNVERIFIED`: the lone video trace cannot be attributed to a particular R06 path, nor can device-side path alpha/clip be measured without runtime logs. | No geometry/profile/SVG replacement or guessed path was made. Added actual renderer mode/path count/life-alpha to the existing stage log to make the next capture diagnosable. R06 asset identity remains `NOT_CONFIRMED`, so exact frozen-shape compliance and post-fix visibility are not claimed. |
| P0-3 seek path to fish tail | SlimSAM contour is adapted with mask geometry metadata into canonical EXIF-normalized whole-photo coordinates. `RecognitionFishTailAnchor.resolve()` selects a closed contour, projects it on the principal axis, scores taper/adjacent-width ratios near either end, and returns null when the silhouette is ambiguous. `mapToViewport()` applies the current `RecognitionImageTransform` and rejects points outside the current photo rect. The ledger binds session, photo SHA, attempt, image, content version, geometry version and Mask identity. | `PROVEN`: `WaterLightEnergy.at()` previously capped PATH_TO_FISH absorption to `stageProgress * .25`, so the measured perimeter→tail path could stop at 25% before the next stage. `PROVEN`: the old seeking color/width treatment used a wide green/ice path (7/2.5/1 dp), inconsistent with frozen .9 dp gold core / 2.2 dp glow. The Stage 05 geometry is generated from the current Mask-derived tail anchor to the nearest photo perimeter point; the Orb→perimeter path remains ledger-backed for the later RETURN route. `UNVERIFIED`: this video never reaches Stage 05, so no actual model Mask, detector target, EXIF transform or tail contact can be visually validated from it. A slender silhouette whose two ends both taper can be ambiguous; resolver returns null rather than invent a point. | Let PATH_TO_FISH progress drive the full photo-perimeter→tail route; remove duplicate ABSORB from contour stages; render the measured route with broken gold strokes (2.2 dp glow, .9 dp core, .65 dp fine layer) and gold head accents. No fixed coordinates/BBox center/Mask reuse added. Existing identity tests cover stale attempt/Mask rejection. |
| P0-4 perimeter too fast/strong | The GLES perimeter shader uses `frame.progress` for its bottom-up reveal mask and sparkles. Previously progress included `.72 * ORB_RELEASE progress`, while perimeter opacity was zero in ORB_RELEASE; Stage 04 then began already at 72% and its 125 ms minimum drove the rest almost immediately. The material's existing 1,200 ms spread parameter was not consumed. Generic path rendering also treated `ARRIVE` as a wide photo-edge→detector-box-center ray. | `PROVEN`: release progress was accumulated invisibly and the 1,200 ms material duration had no call site. `PROVEN`: the ARRIVE event was eligible for the generic broad path renderer, separate from the perimeter shader. The uploaded video shows a fast bright perimeter; shader execution/runtime logs are not embedded in the video. | Start coverage at the current Stage 04 optical reference and advance it with the already-frozen 1,200 ms perimeter material window, preserving progress across Stage 05 and using the same SceneClock. Keep later perimeter decay separate. ARRIVE remains a stage/energy milestone for the perimeter shader but no longer draws a second broad ray through the photo. Static card border is unchanged. |

## Files changed in the source patch

- `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightEnergy.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbWakeBreathReleaseV11.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/WaterLightEnergyPathRenderer.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMathTest.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/OrbGrassFrozenAssetsV11Test.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/OrbWakeBreathReleaseV11Test.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightEnergyTest.kt`

## Tests and resource checks so far

| Check | Result | Notes |
|---|---|---|
| `git diff --check` | PASS | Final source and documentation diff. |
| Orb generated profile parity | PASS | Generated R02–R06 profile was left unchanged. |
| Core runtime WebP alpha/SHA | PASS | 384×384 RGBA; min alpha 0/max 255; SHA matches `daa85a1f…a7b09`. |
| Frozen Orb/R01B/R06 asset validator | PASS | R01B masks, parameters and R06 timing/caps pass; this validator does not certify the conflicting R06 source-file hashes as V1.1 frozen. |
| Recognition runtime contract validator | PASS | v3.1. |
| Capture/Processing raster validator | PASS | Required PNG/WEBP assets decode. |
| Targeted Android JVM tests | PASS | 402 tests in exact-source workflow `42063850368`; the first run exposed the 220ms Float-boundary issue, fixed in `OrbWakeBreathReleaseV11.kt`, then rerun successfully. |
| Debug APK / AndroidTest APK | PASS | Both assembled from exact code SHA `f7eddced9398383763422d30be121d3226aa196e`; packaged Core SHA and alpha checks passed. |
| Workflow artifact checksum | PASS | Artifact ID `13434786213`; downloaded ZIP digest matched GitHub metadata and embedded `SHA256SUMS` matched both APKs. |
| AGSL and all real-device visual checks | NOT_RUN | Product-owner retest after installation. |

## CI classification

- **PASS:** Exact-source Orb workflow `42063850368` — tests, Debug/AndroidTest APKs, packaged-resource validation and Artifact.
- **FAIL (unrelated pre-Gradle gate):** Full Android CI `42063849797` stopped at `Verify Android runtime asset contracts` because the existing Empty Home V2.2 measured-anchor contract reports `EmptyHomeMotion` divergences. No Empty Home code or contract was changed.
- **BLOCKED:** Local control-plane admission remains blocked on canonical-file synchronization and branch-local status/task bindings listed below. It was not bypassed or marked PASS.

## Control-plane result

`python3 scripts/validate_control_plane_admission.py` returned `CONTROL_PLANE_ADMISSION=FAIL` after the static validators passed. Reported blockers were four canonical-file sync/divergence errors; `P6.status` unsupported and no `READY` next action; branch-local state status `IMPLEMENTED_AND_DEBUG_APK_DELIVERED` unsupported; this task ID absent from the canonical machine; missing unified-contract binding; and state/report SHA revalidation required. These are recorded as blockers. No canonical design/control-plane files were edited and no validator was bypassed.

## Remaining exact actions

1. Product owner installs the delivered APK and performs the requested real-device visual retest. Record Wake/AGSL/renderer logs to resolve P0-1/P0-2.
2. Confirm R06 V1.1 geometry/profile/SVG resource identity before claiming frozen-shape compliance. No conflicting resource was silently replaced.
3. Keep all video-only visual claims bounded to the uploaded 40.063s recording; Stage 05 fish-tail contact was not present in it.
