# Android AI Orb Core Component V1

**Task ID:** `YUJIAN_ANDROID_AI_ORB_CORE_COMPONENT_V1` · **Phase:** P6/P0  
**Repository:** `pan277942135/Yujian_App`  
**Shared contract:** `AI-ORB-CORE-V1` · **Gate:** `G-ANDROID-AI-ORB-CORE-V1`

## Objective
Upgrade existing `YuJianCaptureButton` and `ProcessingWaterOrb` into one shared, state-driven AI Orb Core family. Reuse Compose/Canvas and current Processing contracts. Do not create a second Orb engine or page-specific Demo.

## Behavior
- Home button click navigates to Capture; no Orb Morph on Home navigation.
- Camera shutter capture leads to a 900ms camera/pearl-to-Orb morph in Processing.
- Photo Morph is 700ms and runs in parallel with the 900ms Orb entrance from the same stable Processing photo-frame clock.
- Gallery enters Processing with a 700ms contained photo and Orb core visible immediately; no fabricated camera-button source.
- Orb remains alive while real recognition is active.
- Existing completion/contraction and RR09 remain authoritative. Result page hides Orb; successful flow never morphs back to camera button.

## Implementation
1. Read `AGENTS.md`, Control Plane Registry and this contract from current Main. Start from exact current Main after contract merge.
2. Initialize branch-local `PROJECT_MASTER_STATE.yaml` and `WORK_EXECUTION_STATE.json` untracked in the private Work workspace. Do not commit/publish private dynamic state. Run `python scripts/validate_control_plane_admission.py`; code only after PASS.
3. Create shared component under `app/src/main/java/com/yujian/ai/ui/designsystem/components/aiorb/`. Expose states `CAPTURE_BUTTON_IDLE`, `CAPTURE_BUTTON_PRESSED`, `ORB_MORPH_IN`, `ORB_ACTIVE`, `ORB_CONTRACTING`, `HIDDEN`. The component consumes visual state, one scene clock, source kind, existing completion progress, Reduce Motion and performance tier. It does not own navigation, CameraX, PhotoSession, inference or result routing.
4. Extract/reuse the existing `ProcessingWaterOrb.kt` Canvas renderer: transparent `processing_water_core_v1.webp`, inner refraction/glint, separate front/rear ribbons. Keep one actual Orb renderer; retain a thin compatibility adapter if needed.
5. Bind frozen Primary Capture Button V1.1 source `design/system/components/primary_capture_button/assets/capture_button_main_v1.png`, 208×208 RGBA, SHA-256 `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327`, byte-identically to `app/src/main/res/drawable-nodpi/primary_capture_button_v1_1.png`. Do not resize/recolor/overwrite source.
6. Existing same-basename Android `capture_button_main_v1.webp` is a different 384×384 candidate, SHA-256 `f8114d5d6d1b2211ce3527e849b07ed9434d8d102f88b247bac28aa2eaea870a`. Rename to `ai_orb_camera_seed_v1.webp` if retained; remove it from formal call sites, preserve provenance.
7. Update `YuJianCaptureButton.kt`, `IdentifyScreen.kt` frozen shutter frame, `CaptureEntryButtonHandoff.kt`, Processing and every `R.drawable.capture_button_main_v1` use. Update `scripts/verify_capture_presentation_assets.py` and `CapturePresentationAssetRuntimeTest.kt` to validate new canonical resource and actual 208×208 dimensions.
8. Home Empty/Normal use canonical button and preserve geometry/haptic/navigation. Capture preserves Capture V3.1 layout, camera preview, Gallery, zoom, flash, permission and lifecycle. Processing calls shared Core. Result and RR09 remain unchanged.

## Timing
- Photo Morph: 700ms.
- Orb entrance: 900ms.
- 0–150ms press; 150–350ms dissolve; 350–650ms ribbons open; 650–900ms settle.
- Gallery: core visible immediately; ribbons open and settle on same 900ms clock.
- Optical cycles: core 4.2s, blue 4.5s, gold 5.5s, green 6.5s.
- Core 80dp standard / 68dp compact; external ribbon travel max 14dp.
- Preserve normal/low-performance path counts and Reduce Motion behavior.
- Consume existing real-result completion and RR09 handoff; no extra delay.

## Protected invariants
Same PhotoSession, original normalized photo, photo SHA, EXIF, detector/classifier input, thresholds, BBox, SlimSAM/Mask, trusted geometry and fish-name semantics. Preserve Processing V3.2 real-event gates, RR09 geometry and first-frame ownership. No fake contour or result.

## Evidence
- Motion tests at 0, 149, 150, 349, 350, 649, 650, 899, 900ms.
- Camera vs Gallery source tests; completion progress and no-extra-delay regression.
- Canonical source/runtime copy SHA equality.
- Packaged APK asset decode/alpha/dimension checks.
- Existing Capture, PhotoSession, EXIF, photo transform, recognition, Mask and RR09 tests.
- Debug APK, AndroidTest compile, unit tests and lint.
- Exact-head API 28 runtime; responsive sizes 320×480, 360×640, 390×844, 412×915, 430×932, 800×360 where supported.
- APK SHA-256, workflow run, artifact ID, source SHA and evidence manifest.
- Physical-device visual acceptance remains PENDING until product owner tests the delivered APK.

## Completion
Engineering PASS requires exact-current-PR-head admission, build, unit tests, AndroidTest compilation, lint, asset verification and applicable API 28 runtime/evidence. Product-owner visual acceptance remains separate. Merge only exact current PR HEAD after required checks pass; never merge branch-local dynamic state.
