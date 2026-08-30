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
