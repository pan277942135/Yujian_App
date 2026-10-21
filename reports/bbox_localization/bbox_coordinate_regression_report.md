# Android BBox Localization Fix V1 — Offline Audit and Runtime Evidence

**Task:** `YUJIAN_BBOX_LOCALIZATION_ANDROID_FIX_V1`
**Repository:** `pan277942135/Yujian_App`
**Status:** `IMPLEMENTED_PENDING_ANDROID_CI`
**Updated:** 2026-10-19

## Summary

The detector decode, top-left letterbox inverse scale, CW90/CCW90 inverse box formulas, crop rounding and display mapping inspected in the current Android source are internally consistent. I found a separate, concrete source-photo orientation defect: the normalizer previously read only `ExifInterface.rotationDegrees`, which is zero for EXIF mirror orientations 2 and 4 and does not encode the reflection for orientations 5 and 7. Those inputs were passed to detection and display without their required mirror transform. The code now reads the full orientation tag and applies all eight EXIF transforms before saving and returning the normalized bitmap.

This is a confirmed general `PREPROCESSING_MISMATCH`; it is **not established as the cause of the two user image results**. PR #164 records EXIF orientation 1 for both supplied files, so this defect cannot explain their historical offline outputs. Their exact source bytes are not mounted in this Android Work session, and neither has reviewed Ground Truth. No user-image Android replay or true IoU is claimed here.

The Android instrumentation suite now contains geometry, all-eight-EXIF, real Case B model-output, and synthetic EXIF-2 before/after tests. The tests emit the actual detector tensors, raw output rows, mapped boxes, crops, statuses and a prediction-only comparison PNG on the device. They have **not yet run**: this Work container has no Gradle executable, Android SDK, `adb`, or emulator. The exact-head hosted build and existing API 28 runner remain the next validation path.

## Live baseline and PR isolation

| Item | Live/current observation |
|---|---|
| Android repository | `pan277942135/Yujian_App` |
| Android `main` | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` |
| Work branch | `fix/bbox-localization-android-v1`, based on current `main` |
| Investigation PR #164 | `pan277942135/Yujian`, Open / Draft / Unmerged, head `689e9e38391bcce70610c9c6ce8ca7efd35cc20c` |
| PR #145 | Closed / merged; Design System V3.1, not current Recognition implementation authority |
| PR #166 / #167 | Existing Open / Draft branches left untouched |
| New Android PR | Not yet created at report preparation; will remain Draft / Open / Unmerged |

The Android branch is isolated from the dirty pre-existing PR #167 worktree. The existing Recognition Processing V1.3 implementation is already an ancestor of current Android `main`. No shared PR branch or backend PR #164 content was changed.

## Image identity and evidence availability

The GitHub V3 retest record in PR #164 is retained as **historical/offline CPU evidence**, not re-run Android evidence. It documents these two user-supplied inputs:

| Input | Recorded source identity | Historical offline directions | Ground Truth / current Android bytes |
|---|---|---|---|
| `IMG_1415.jpg` | 1152×1536 JPEG, EXIF 1, SHA-256 `0fc341627e4345131a15fbf3514454f053dcb349db17bf1171c8f2f8756226cf`; user-designated Case A candidate, not hash-matched to historical original | ORIGINAL `.036015 / NO_FISH`; CW90 `.430140 / READY`; CCW90 `.359594 / FISH_TOO_SMALL` | No reviewed GT; source bytes not available in current Android Work filesystem |
| `screenshot_20261007_012240_com.xingin.xhs_hos.jpg` | 911×1536 JPEG, EXIF 1, SHA-256 `10409025dd5fe590b9487ef4dc43ea019a9e2083143fb458bf160a6c37eec5a6`; separate screenshot input | ORIGINAL `.770657 / READY`; CW90 `.708222 / READY`; CCW90 `.279121 / UNCERTAIN` | No reviewed GT; source bytes not available in current Android Work filesystem |
| Historical Case A original | Expected 1440×1920, SHA-256 `2dd703a46b378093604e10f0add454606393138f7501774bd3bbcca75352c9fe` | Historical low-objectness values remain in PR #164 V1/V3 | Exact bytes and GT unavailable |
| Repository Case B fixture | `app/src/androidTest/assets/detector_case_b_night_flash.jpg`, 1152×1536, SHA-256 `a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b`, EXIF 1 | V3 offline CCW90 positive control is historical | Available to Android instrumentation; no authoritative GT in current evidence |

The per-direction values above are copied only as historical comparison from `CASE_A_OFFLINE_REPRODUCTION_V3.md` and `user_provided_two_image_retest_v3_20261019.json` at PR #164 head `689e9e3…`. They used archived ONNX + Python CPU; they are not this branch's Android outputs. The V3 record confirms repeated tensor/output hashes for that prior Python run. It also confirms the candidate photo SHA differs from the historical Case A SHA. No screenshot, image, guessed annotation or generated substitute was added to this public repository.

Historical CPU BBoxes mapped back to each source image (normalized xyxy) were: `IMG_1415.jpg` — ORIGINAL `[0.203844,0.209540,0.406149,0.827160]`, CW90 `[0.186673,0.266021,0.414967,0.791586]`, CCW90 `[0.612116,0.445006,0.993586,0.566836]`; screenshot — ORIGINAL `[0.208835,0.120963,0.624793,0.982316]`, CW90 `[0.075701,0.123843,0.628598,0.989796]`, CCW90 `[0.136769,0.131971,0.721948,0.995111]`. These are **HISTORICAL** model predictions with no GT; they are not labeled accurate and no IoU is inferred. Full raw output provenance is in the [PR #164 V3 evidence JSON](https://github.com/pan277942135/Yujian/blob/689e9e38391bcce70610c9c6ce8ca7efd35cc20c/reports/case_a/user_provided_two_image_retest_v3_20261019.json).

## Model identity

Local GitHub Release assets were bootstrapped without GCP. The detector and classifier files are ignored build assets and are not committed.

| Artifact | Verified SHA-256 | Size / scope |
|---|---|---|
| Android detector Release ZIP | `246ddacaf89ca7ecc9c64a47d3e12c5ee9088d92c763a38626778505ae8c15ee` | Pinned Release ZIP |
| Detector ONNX | `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f` | 3,753,998 bytes; `DET_FISH_v0.1` |
| Detector metadata | `53994a745c192e3c2afc31ad256800bb87f841fafd07977f0fd38fa7e1f8327e` | Declares same ONNX hash, `[1,3,416,416]` input and `[1,3549,6]` decoded output |
| Classifier TFLite | `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18` | 6,244,908 bytes; `MODEL_CROP_M1_v0.2`, input `[1,3,224,224]`, output `[1,15]` |
| Generated release contract | `build_sha=null` in this local bootstrap | Not an APK identity or exact-head packaged-model claim |

`verify_production_model.py` passed for the fetched classifier release. The archived detector ONNX's output-graph semantics were statically checked in PR #164 V3: the exact `12b97f…` model applies Sigmoid to objectness/class probability head outputs, has no Softmax, and emits decoded xywh/probability rows. That evidence is scoped to the archived model; no current GCS model or installed production device identity is asserted. The API 28 instrumentation test will verify the model SHA from the app's loaded model on the exact tested APK.

## Coordinate-chain audit

| Stage | Current Android source | Audit result |
|---|---|---|
| File decode + EXIF | `RecognitionImageStore.kt:61-168` | Fixed to read the full EXIF orientation tag and apply orientations 1–8. The previous rotation-only implementation skipped mirror transforms. The normalized JPEG stores upright pixels and the returned bitmap uses those same pixels. |
| Resize / letterbox | `FishDetectorEngine.kt:403-425` | 416 square input, Pillow-compatible bilinear resize, image anchored top-left, fill 114; float32 NCHW BGR in 0..255. Resize scale/drawn dimensions are retained. |
| Decode / confidence / NMS | `FishDetectorEngine.kt:569-598` and `nms` | Reads decoded `[cx,cy,w,h,obj,fish_probability]`, computes `obj × fish_probability` once, maps box edges by scale and source dimensions, then NMS. No extra activation is applied in the consumer. |
| Orientation inverse | `FishDetectorEngine.kt:600-620` | CW90 maps `(x1,y1,x2,y2)` to `(y1,1-x2,y2,1-x1)`; CCW90 maps to `(1-y2,x1,1-y1,x2)`. This agrees with the pixel rotation convention; round-trip tests cover all corners and a non-square box. |
| Quality gate / crop | `FishDetectionQualityGate.kt:136-200`; `FishRecognitionPipeline.kt:76-88` | Crop expansion is unchanged at the frozen 0.15 ratio; pixel crop uses floor left/top and ceil right/bottom, clamps to image bounds and guarantees at least one pixel. Pipeline crops the same upright bitmap coordinates as the detector result. |
| Display overlay | `RecognitionImageTransform.kt:29-105`, `RecognizingScreen.kt:295-339` | Photo, normalized fish box and focus overlays use one source-to-viewport transform. Static audit found no independent scale/translation path for BBox overlays. |

The finding is limited to full EXIF orientation normalization. It does not change detector weights, model output semantics, thresholds, NMS, orientation retry selection, crop expansion, classifier preprocessing or UI scaling.

## Implemented regression coverage and emitted evidence

- `RecognitionImageStoreExifOrientationRuntimeTest`: makes asymmetric JPEG test pixels, writes EXIF 1–8, calls the production camera normalizer, verifies dimensions, corner mapping and that the normalized file has no remaining transform.
- `FishDetectorDecodeTest`: tests non-square top-left letterbox decode, CW90/CCW90 inverse mapping at all four corners and a non-square round trip.
- `FishDetectionQualityGateTest`: tests crop pixel bounds and one-pixel targets.
- `DetectorOrientationRetryRuntimeTest`: keeps the exact Case B detector/classifier regression and additionally records ORIGINAL/CW90/CCW90 model tensor bytes, channel statistics, complete raw output rows and hashes, decoded/mapped boxes, gates and crop coordinates.
- `BboxExifBeforeAfterRuntimeTest`: derives a metadata-only EXIF-2 variant from the repository Case B fixture and compares the old rotation-only behavior with the production full-EXIF normalizer. This is a synthetic orientation regression fixture, not a user image and has no GT. The generated PNG displays predictions/crops only and states that GT/IoU are missing.

The `recognition-frozen` runtime gate now runs 59 instrumentation test methods, including these regression tests, and collects the generated JSON, little-endian raw output binary, prediction comparison PNG and coordinate report from the target app cache. Runtime evidence is written only after the device tests actually run; no placeholder image or fabricated prediction has been committed. A future successful gate artifact should contain `bbox_case_b_direction_replay.json`, `bbox_case_b_direction_replay_raw_outputs.bin`, `bbox_before_after_metrics.json`, `bbox_before_after_comparison.png`, `bbox_metrics_v1.json`, `bbox_test_manifest.json` and `bbox_coordinate_regression_report.md`.

## Checks and gate status

| Check | Result |
|---|---|
| `bash -n scripts/android_runtime/gates/recognition_frozen.sh` | PASS |
| `bash scripts/android_runtime/tests/test_contract.sh` | PASS, 9 fake-ADB scenarios including PASS/failure/blocked-infra classifications |
| `python3 -m unittest discover -s scripts -p test_runtime_evidence_source_closure.py -v` | PASS, 6 tests |
| `python3 scripts/verify_recognition_runtime_contract.py` | PASS |
| `python3 scripts/verify_production_model.py` | PASS; classifier release contract verified |
| Android `:app:test`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | NOT RUN locally; Gradle is absent |
| API 28 install/runtime | NOT RUN yet; requires the existing self-hosted `yujian-android,api28` runner |
| APK SHA / CI run provenance | NOT AVAILABLE until exact-head build artifact exists |
| User-image Android replay | BLOCKED; image bytes are absent from this Work filesystem |
| Real user-image IoU / `BBOX_METRICS_PASS` | BLOCKED; reviewed Ground Truth is absent |

The CI workflow's pull-request jobs build on hosted Linux, while the full API 28 matrix runs for pushes on the existing self-hosted runner. A branch push will test whether that runner is already available; no GCP service, billing, device, or runner will be provisioned by this Work.

## Root-cause decision

| Classification | Decision | Evidence / limit |
|---|---|---|
| `PREPROCESSING_MISMATCH` | **Confirmed general defect and scoped fix** | Rotation-only handling omitted EXIF mirror/transpose transforms. Candidate and screenshot EXIF are both 1, so this is not their demonstrated cause. API 28 transform regression is pending. |
| `ARTIFACT_MISMATCH` | Historical lineage issue only | PR #164 records archived ONNX SHA `12b97f…` vs last observed production GCS SHA `2338f4…`; current production identity unavailable. Not sufficient to explain candidate localization or score. |
| `MODEL_DETECTION_FAILURE` | Not established | No reviewed GT for new images and no exact Android image replay. |
| `DIFFICULT_DOMAIN_COVERAGE_GAP` / `TRAINING_LABEL_NOISE` | Unresolved hypotheses | DET_DS_v0.1 per-sample manifest/images/labels were not found in this Android repo; aggregate release summary is not a sample audit. |
| User Case A low objectness / BBox quality | `BLOCKED_INSUFFICIENT_EVIDENCE` | Historical original bytes and GT are absent. V3 candidate CPU results are available only as historical evidence. |

The confirmed EXIF bug explains how affected mirrored files could enter inference in the wrong orientation. It does not establish why the user's EXIF-1 images receive their observed boxes or low objectness.

## Remaining inputs and next executable steps

1. Make the original Case A bytes and the two user test files available to the current Work filesystem (the prior V3 hashes can then establish whether the supplied files match). Keep the images private; do not commit them.
2. Supply reviewed fish-body boxes in normalized original-image coordinates for each image, or independently review provisional annotations. Do not compute IoU before that.
3. Push this exact implementation branch and use the existing GitHub build + API 28 runner. Record the final source SHA, run ID, APK SHA and evidence artifact. If the runner is unavailable, preserve the actual runner status as `BLOCKED_INFRA` and leave API 28 as NOT_RUN/BLOCKED.
4. Compare Android tensor/output hashes against the already recorded V3 CPU values only after the exact source bytes and model identity are available. Differences must be localized by tensor, raw output, decode, mapping or crop stage.

No cloud billing/resources were accessed, no model training occurred, and no threshold/NMS adjustment or main merge was made.
