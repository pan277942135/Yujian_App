# MODEL_M1_v0.2 mobile asset gate

The Android app now uses the verified mobile export of `MODEL_M1_v0.2` published by the Model Factory repository.

- canonical release: `pan277942135/Yujian` → `mobile-model-v0.2`
- release asset: `fish_classifier_v0_2.tflite`
- Android destination: `app/src/main/assets/fish_classifier.tflite`
- expected bytes: `6220308`
- expected SHA-256: `9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e`
- TFLite input: `[1,3,224,224]` Float32 NCHW
- output: `[1,9]` Float32 logits
- preprocessing: whole-image aspect-preserving letterbox, padding RGB `[124,116,104]`, ImageNet mean/std normalization, no crop

CI downloads the exact release asset before building and rejects any size/hash mismatch.

## DET_FISH_v0.1 mobile detector gate

The production recognition path loads the real YOLOX-Nano detector before the classifier.

- canonical artifacts: `gs://yujian-model-factory-571785698442/models/DET_FISH_v0.1/`
- Android release mirror: `pan277942135/Yujian` → `detector-model-v0.1`
- release bundle: `det_fish_v0_1_android_bundle.zip`
- ONNX runtime: `onnxruntime-android`
- input/output: `[1,3,416,416]` → `[1,N,6]` decoded `cx, cy, w, h, objectness, fish_probability`
- detector contract: `RECOGNITION_PIPELINE_v1`, including NMS, thresholds and floor/ceil crop rounding

Android UX applies `QUALITY_GATE_v1.1` after detector decode. `GOOD` and `WARNING`
continue to `MODEL_M1_v0.2`; only `INVALID` blocks classification. A strong single-fish
box that touches the frame edge is retained as `WARNING`, so ordinary fishing photos with
an out-of-frame tail or light occlusion are not rejected before classification.

The Backend generates this bundle directly from the official GCS model prefix only after
the GCS-backed detector runtime gate and audited five-case golden manifest pass. Android
CI verifies the ONNX byte size and SHA-256 from immutable `detector_metadata.json` before
assembling the APK or running detector parity.
