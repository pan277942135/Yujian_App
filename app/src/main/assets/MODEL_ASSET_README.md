# Android production classifier release assets

The next Android package always resolves the current official classifier from
`pan277942135/Yujian` → Release tag `mobile-model-v0.2`. Bootstrap downloads
the TFLite model, its metadata, `class_map.json`, and `tensor_contract.json` by
their GitHub Release asset IDs, validates that the Release did not change while
downloading, and verifies all files against each other before Android packaging.

The generated `model_release_contract.json` records the exact model, dataset,
Release, bytes, SHA-256, class order, tensor shapes, and source asset identities
used for that APK. It is included in the APK assets and runtime evidence.

Stable Android asset names:

- `fish_classifier.tflite`
- `fish_classifier_v0_2.metadata.json`
- `class_map.json`
- `tensor_contract.json`
- `model_release_contract.json`

The classifier class count and labels come from the production Release. The
application's manual correction selector is populated from the Fish Guide
catalogue and remains independent of the model's output classes.

The production recognition path remains Detector → `DETECTOR_CROP` → classifier.
Detector assets and crop behavior use their existing, separate contract.
