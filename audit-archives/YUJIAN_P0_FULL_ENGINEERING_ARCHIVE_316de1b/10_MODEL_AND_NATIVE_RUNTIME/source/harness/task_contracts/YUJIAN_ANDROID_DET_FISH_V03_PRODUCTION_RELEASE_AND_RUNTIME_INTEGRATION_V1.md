# DET_FISH v0.3 Production Release and Android Runtime Integration V1

**Task ID:** `YUJIAN_ANDROID_DET_FISH_V03_PRODUCTION_RELEASE_AND_RUNTIME_INTEGRATION_V1`  
**Phase:** `P6`  
**Priority:** `P0`  
**Gate:** `G-DET-FISH-V03-ANDROID-PRODUCTION-RELEASE-V1`  
**Status:** `ACTIVE_CLOSURE`

## 1. Scope and repository boundaries

- Android repository: `pan277942135/Yujian_App`
- Model repository: `pan277942135/Yujian_Model_Assets`
- Existing model-assets Release ID: `422778098`
- Existing Release tag: `model-assets-v1`
- Existing Release URL: https://github.com/pan277942135/Yujian_Model_Assets/releases/tag/model-assets-v1

This is an independent P0 task and must use its own implementation branch and PR. It does not replace or absorb WP2 PR #327.

## 2. Authorized immutable Release assets

Publish the existing Draft Release in place. Do not create a replacement Release, re-upload, rename, repackage, retrain, or replace either ZIP.

| Asset | Asset ID | Exact size | Required ZIP SHA-256 |
|---|---:|---:|---|
| `det-fish-v03.zip` | `672467369` | 10,525,017 bytes | `4a3dd5a55f254e6a813d7f029e23b8fe00caf7c616422be51510b70a3ba495ca` |
| `classifier-crop-m1-v02.zip` | `672468391` | 19,082,024 bytes | `aef10982824426052c07907093b11cb1d6448ff5f6c52027f266ed95f87c640a` |

The product owner authorized publication of exactly these two existing assets. Preserve the original ZIP bytes and digests. Do not add an unconfirmed SPDX, OSI, or other open-source license declaration. Do not publish or bundle SAM/SlimSAM checkpoint weights.

## 3. DET_FISH v0.3 frozen model contract

| Item | Required value |
|---|---|
| Model version | `DET_FISH_v0.3` |
| ONNX SHA-256 | `98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1` |
| Checkpoint SHA-256 | `3e2b4d835de65bf408f565f5104c9993d3c558527690782e75a4415e3c67ca89` |
| Input | `[1,3,416,416]` |
| Layout | BGR planar |
| Letterbox pad | 114 |
| Output | `[1,3549,6]` |
| Output contract | `decoded_xywh_objectness_class_probability` |
| NMS IoU | 0.45 |

The ZIP metadata identifies `DET_FISH_v0.3`, YOLOX_NANO, ONNX opset 17 and the output contract above. The ONNX graph itself must be inspected and its actual input/output names, tensor types and dimensions verified. The ONNX filename contains a historical `v0_1` suffix; this filename is not the model version authority. Keep the ZIP unchanged and use metadata/graph validation to identify the model.

Do not apply YOLOX grid/stride/anchor decoding to this already-decoded output. Decode the six values according to the verified v0.3 contract, combine objectness and class probability, restore the original photo coordinates through the existing letterbox transform, and apply the frozen 0.45 NMS. Preserve EXIF normalization, original-coordinate mapping, bounded Orientation Retry, Detection Quality Gate, existing candidate semantics and quality statuses.

## 4. Classifier v0.2 reuse; no unnecessary replacement

The current production classifier Release is:

- Repository: `pan277942135/Yujian`
- Release ID: `379310749`
- Tag: `mobile-model-v0.2`
- TFLite Asset ID: `598521217`
- TFLite SHA-256: `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`

The supplied classifier ZIP must be checked against the existing production Release. Its TFLite must match the exact SHA above; verify the metadata, 15-class map and tensor contract. Keep the existing production classifier integration if the exact parity checks pass.

Frozen classifier contract: input `[1,3,224,224]`, RGB, ImageNet mean/std, BBox expansion 15%, RGB pad `[124,116,104]`, output `[1,15]`.

## 5. Bootstrap and APK provenance

Update the production asset path in:

- `FishDetectorEngine.kt`
- `scripts/bootstrap_android_recognition_assets.py`
- `scripts/verify_production_detector.py`
- `scripts/test_bootstrap_android_recognition_assets.py`
- `DetectorOrientationRetryRuntimeTest.kt`
- `.github/workflows/android.yml`

Pin the existing model-assets Release ID and Detector Asset ID. Download the ZIP, verify its exact ZIP SHA, safely extract it, verify ONNX and checkpoint SHA, validate metadata and ONNX graph/tensor contracts, and create an immutable model snapshot. Update the CI cache key to include the v0.3 ZIP SHA.

The final APK must contain `DET_FISH_v0.3` and `MODEL_CROP_M1_v0.2`. Extract the model files from the actual APK and recompute their SHA-256 values. A successful download-cache check is not APK provenance. Remove v0.1 from the production model path; historical v0.1 artifacts may remain only in historical tests or records.

## 6. Required automated tests

At minimum:

- ZIP, ONNX and checkpoint SHA validation.
- ONNX graph input/output names, types and shapes.
- Metadata model version, family, dataset and output contract.
- BGR planar ordering and pad=114.
- Decoded XYWH/Objectness/Class probability handling.
- Original BBox mapping and letterbox inversion.
- NMS IoU=0.45.
- EXIF orientation and all existing Orientation Retry paths.
- Empty detection, multi-fish, incomplete fish and too-small fish.
- Incorrect ZIP SHA, ONNX SHA or tensor shape must fail closed.
- Classifier ZIP/TFLite parity with the existing production Release, class-map order and tensor contract.
- FULL and ORB_ONLY integration must preserve real SlimSAM Mask trust; no synthetic Mask or result.

## 7. Real Android inference smoke test

Run a real Android ONNX Runtime Detector v0.3 + TFLite Classifier v0.2 inference smoke test using an approved non-private fixture. Do not substitute a mocked engine or a browser/backend prediction.

Record model versions, ONNX/TFLite SHA, detection count, every selected BBox and confidence, Classifier Top1, inference latency, and SlimSAM Mask status. If a real Android device/emulator run is unavailable, mark it `NOT_RUN`; do not claim inference PASS from compile, CI asset inspection or static tests.

## 8. Final delivery and exclusions

Deliver the published Release URL, exact asset digests, ONNX/TFLite digests, model snapshot, test results, exact Main SHA, independent PR and merge commit, APK SHA-256/size, APK-internal model SHA report, and unresolved issue ledger.

Do not retrain, change model parameters, alter the 6000ms timing contract, create a second SceneClock, change unrelated pages, enable GCP billing, upload user-original photos, expose SAM weights, or perform an API28 matrix. Basic Android build/tests and APK delivery are required; physical-device visual acceptance remains the product owner's responsibility.
