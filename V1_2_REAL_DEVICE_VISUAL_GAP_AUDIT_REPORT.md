# V1.2 Real Device Visual Gap Audit and Targeted Fix Report

**Task:** `YUJIAN_V1_2_REAL_DEVICE_VISUAL_GAP_AUDIT_AND_FIX_V1`
**Repository:** `pan277942135/Yujian_App`
**PR:** #317 (`feature/orb-photo-stable-visual-closure-v1`, Open / Draft / Unmerged)
**User-specified source baseline:** `beed77ad31bb54010148c7bf459f3b445ccbcd23`
**PR source before this audit:** `bf9c3f634fcba010f3d6ac2beaf9933ea7cfd3be`
**Main baseline:** `74cad95b4f673bd9c8c60d16839f036d5886d933`

## Status

- Audit source evidence: complete.
- Targeted source changes: published to PR #317; code patch commit `334103baddcb0999e724d8eecda82acea0191bea` contains the complete source/test tree. Execution-state documentation is being updated separately.
- Android unit tests / Debug APK / AndroidTest APK: pending exact-SHA GitHub Actions build. This workspace has Java 17 but no Gradle executable/wrapper or Android SDK.
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
| P0-1 Orb wake weak | Asset01 remains the original Core. `OrbWakeBreathReleaseV11.wake()` already uses the 80 ms optics and 220 ms water/grass thresholds over the 350 ms ORB_WAKE stage. `AIOrbCore.OrbVisual` renders the raw Core and a separate RenderEffect image; shader failure logs and leaves the raw Core visible. The frozen `.22` optical group alpha was retained. | `PROVEN`: the Core image alpha was additionally reduced by up to 0.18 for RELEASE/RETURN, contrary to frozen Asset01 alpha invariance. `PROVEN`: wake timing code matches 80/220/350 ms thresholds. `UNVERIFIED`: the weak Wake shown in the video cannot be traced to a specific code failure without device API/logcat; AGSL creation/runtime success, actual mode at each frame and per-source alpha cannot be recovered from pixels alone. The `.22` group cap is frozen and was not reinterpreted as an implementation defect. | Keep `coreImageAlpha = 1f`; keep the raw image independently drawn; extend the existing Core stage log with `wake_band`, `wake_optics`, `wake_water`, `wake_grass`, renderer mode, path count and life alpha. Add exact 80/220 ms boundary and source-alpha invariant tests. No Core pixels, optical alpha budget, scale, aspect ratio or position changed. Wake visual closure remains pending product retest. |
| P0-2 Orb R06 release | `AIOrbCore` and `ProcessingWaterOrb` default to `ORB_V1_CANDIDATE`; `OrbWakeRenderMode` defaults to FULL on the recognition screen. `OrbFilamentRenderer` consumes five Kotlin-generated Bezier profiles: G01/G02/A01/A02/C01, with back/front split and frozen 1.28 alpha gain, .82 cap, 1.18 release-head gain and stage-local release/return. | `PROVEN`: candidate mode and five path IDs are in the actual code; the user's video does not demonstrate five clearly visible paths. `PROVEN`: the frozen V1.1 geometry/profile/SVG hashes do not match current repository resources. Frozen expected geometry/profile/SVG: `a37d947d…a601a5`, `075e8390…cad864`, `66966e0b…4db52b`. Runtime geometry/profile/SVG: `1dfc85e6…f4cdc`, `8c7f998b…f21c10`, `d321c2bd…87389`. Runtime reads generated Kotlin from the current JSON/profile; it does not draw the SVG directly. `UNVERIFIED`: the lone video trace cannot be attributed to a particular R06 path, nor can device-side path alpha/clip be measured without runtime logs. | No geometry/profile/SVG replacement or guessed path was made. Added actual renderer mode/path count/life-alpha to the existing stage log to make the next capture diagnosable. R06 asset identity remains `NOT_CONFIRMED`, so exact frozen-shape compliance and post-fix visibility are not claimed. |
| P0-3 seek path to fish tail | SlimSAM contour is adapted with mask geometry metadata into canonical EXIF-normalized whole-photo coordinates. `RecognitionFishTailAnchor.resolve()` selects a closed contour, projects it on the principal axis, scores taper/adjacent-width ratios near either end, and returns null when the silhouette is ambiguous. `mapToViewport()` applies the current `RecognitionImageTransform` and rejects points outside the current photo rect. The ledger binds session, photo SHA, attempt, image, content version, geometry version and Mask identity. | `PROVEN`: `WaterLightEnergy.at()` previously capped PATH_TO_FISH absorption to `stageProgress * .25`, so the measured perimeter→tail path could stop at 25% before the next stage. `PROVEN`: the old seeking color/width treatment used a wide green/ice path (7/2.5/1 dp), inconsistent with frozen .9 dp gold core / 2.2 dp glow. The Stage 05 geometry is generated from the current Mask-derived tail anchor to the nearest photo perimeter point; the Orb→perimeter path remains ledger-backed for the later RETURN route. `UNVERIFIED`: this video never reaches Stage 05, so no actual model Mask, detector target, EXIF transform or tail contact can be visually validated from it. A slender silhouette whose two ends both taper can be ambiguous; resolver returns null rather than invent a point. | Let PATH_TO_FISH progress drive the full photo-perimeter→tail route; remove duplicate ABSORB from contour stages; render the measured route with broken gold strokes (2.2 dp glow, .9 dp core, .65 dp fine layer) and gold head accents. No fixed coordinates/BBox center/Mask reuse added. Existing identity tests cover stale attempt/Mask rejection. |
| P0-4 perimeter too fast/strong | The GLES perimeter shader uses `frame.progress` for its bottom-up reveal mask and sparkles. Previously progress included `.72 * ORB_RELEASE progress`, while perimeter opacity was zero in ORB_RELEASE; Stage 04 then began already at 72% and its 125 ms minimum drove the rest almost immediately. The material's existing 1,200 ms spread parameter was not consumed. Generic path rendering also treated `ARRIVE` as a wide photo-edge→detector-box-center ray. | `PROVEN`: release progress was accumulated invisibly and the 1,200 ms material duration had no call site. `PROVEN`: the ARRIVE event was eligible for the generic broad path renderer, separate from the perimeter shader. The uploaded video shows a fast bright perimeter; shader execution/runtime logs are not embedded in the video. | Start coverage at the current Stage 04 optical reference and advance it with the already-frozen 1,200 ms perimeter material window, preserving progress across Stage 05 and using the same SceneClock. Keep later perimeter decay separate. ARRIVE remains a stage/energy milestone for the perimeter shader but no longer draws a second broad ray through the photo. Static card border is unchanged. |

## Files changed in the source patch

- `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/AIOrbCore.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt`
- `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/core/WaterLightEnergy.kt`
- `app/src/main/java/com/yujian/ai/ui/waterlight/renderer/WaterLightEnergyPathRenderer.kt`
- `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMathTest.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/OrbGrassFrozenAssetsV11Test.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/OrbWakeBreathReleaseV11Test.kt`
- `app/src/test/java/com/yujian/ai/ui/waterlight/WaterLightEnergyTest.kt`

## Tests and resource checks so far

| Check | Result | Notes |
|---|---|---|
| `git diff --check` | PASS | Working source diff. |
| Orb generated profile parity | PASS | Generated R02–R06 profile was left unchanged. |
| Core runtime WebP alpha/SHA | PASS | 384×384 RGBA; min alpha 0/max 255; SHA matches `daa85a1f…a7b09`. |
| Frozen Orb/R01B/R06 asset validator | PASS | R01B masks, parameters and R06 timing/caps pass; this validator does not certify the conflicting R06 source-file hashes as V1.1 frozen. |
| Recognition runtime contract validator | PASS | v3.1. |
| Capture/Processing raster validator | PASS | Required PNG/WEBP assets decode. |
| Targeted Android JVM tests | NOT_RUN locally | No Gradle executable/wrapper or Android SDK in this workspace. Exact-SHA GitHub Actions run is required. |
| Debug APK / AndroidTest APK | PENDING | Must be produced by Actions from the final pushed source SHA; no previous APK is reused. |
| AGSL and all real-device visual checks | NOT_RUN | Product-owner retest after installation. |

## Control-plane result

`python3 scripts/validate_control_plane_admission.py` returned `CONTROL_PLANE_ADMISSION=FAIL` after the static validators passed. Reported blockers were four canonical-file sync/divergence errors; `P6.status` unsupported and no `READY` next action; branch-local state status `IMPLEMENTED_AND_DEBUG_APK_DELIVERED` unsupported; this task ID absent from the canonical machine; missing unified-contract binding; and state/report SHA revalidation required. These are recorded as blockers. No canonical design/control-plane files were edited and no validator was bypassed.

## Next exact actions

1. Wait for the branch push Actions run for code patch commit `334103baddcb0999e724d8eecda82acea0191bea` to execute Android JVM tests, `assembleDebug`, `assembleDebugAndroidTest`, and package/resource verification.
2. If changed-code tests or compilation fail, fix them on the existing PR branch and rerun on the new exact source SHA. Keep unrelated control-plane/Empty Home CI failures classified separately.
3. Download only the exact-commit Debug APK artifact, verify APK SHA-256/size and packaged Core, then upload that APK for product-owner installation.
4. Keep the R06 source identity mismatch and all real-device visual observations explicitly unresolved until approved source identity/logcat and the next device recording are available.
