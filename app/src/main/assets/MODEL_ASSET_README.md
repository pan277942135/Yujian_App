# MODEL_M1_v0.6 16-class mobile asset gate

The Android app uses the verified 16-class mobile export published by the Model Factory repository.

- canonical release: `pan277942135/Yujian` → `mobile-model-v0.2`
- release asset: `fish_classifier_v0_2.tflite`
- Android destination: `app/src/main/assets/fish_classifier.tflite`
- expected bytes: `6249008`
- expected SHA-256: `b77ea78e7f8554078ea3a79051039af1ace04f0ac4e2604da57d1dd8f0b010e7`
- tensor contract: `app/src/main/assets/model_tensor_contract.json`
- TFLite input: `[1,3,224,224]` Float32 NCHW
- output: `[1,16]` Float32 logits
- preprocessing: whole-image aspect-preserving letterbox, padding RGB `[124,116,104]`, ImageNet mean/std normalization, no crop

CI downloads the exact release asset and pinned tensor contract before building. It rejects any
size/hash, tensor shape/dtype, or Android label-order mismatch.

## Class order

| Index | Key | Display name |
| ---: | --- | --- |
| 0 | `bighead_carp` | 鳙鱼 |
| 1 | `black_carp` | 青鱼 |
| 2 | `blunt_snout_bream` | 鳊鱼 / 武昌鱼 |
| 3 | `chinese_catfish` | 鲶鱼 |
| 4 | `common_carp` | 鲤鱼 |
| 5 | `crucian_carp` | 鲫鱼 |
| 6 | `grass_carp` | 草鱼 |
| 7 | `largemouth_bass` | 加州鲈 |
| 8 | `mandarin_fish` | 鳜鱼 |
| 9 | `other_freshwater_fish` | 其他淡水鱼 |
| 10 | `sharpbelly` | 白条 |
| 11 | `silver_carp` | 白鲢 |
| 12 | `snakehead` | 黑鱼 |
| 13 | `tilapia` | 罗非鱼 |
| 14 | `topmouth_culter` | 翘嘴鲌 |
| 15 | `yellow_catfish` | 黄骨鱼 |

## DET_FISH_v0.1 mobile detector gate

The production recognition path loads the real YOLOX-Nano detector before the classifier.

- canonical artifacts: `gs://yujian-model-factory-571785698442/models/DET_FISH_v0.1/`
- Android release mirror: `pan277942135/Yujian` → `detector-model-v0.1`
- release bundle: `det_fish_v0_1_android_bundle.zip`
- ONNX runtime: `onnxruntime-android`
- input/output: `[1,3,416,416]` → `[1,N,6]` decoded `cx, cy, w, h, objectness, fish_probability`
- detector contract: `RECOGNITION_PIPELINE_v1`, including NMS, thresholds and floor/ceil crop rounding

Android UX applies `QUALITY_GATE_v1.1` after detector decode. `GOOD` and `WARNING`
continue to `MODEL_M1_v0.6`; only `INVALID` blocks classification. A strong single-fish
box that touches the frame edge is retained as `WARNING`, so ordinary fishing photos with
an out-of-frame tail or light occlusion are not rejected before classification.

The Backend generates this bundle directly from the official GCS model prefix only after
the GCS-backed detector runtime gate and audited five-case golden manifest pass. Android
CI verifies the ONNX byte size and SHA-256 from immutable `detector_metadata.json` before
assembling the APK or running detector parity.
