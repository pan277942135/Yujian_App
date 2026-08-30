# Production TFLite asset gate

This branch is ready for the exact production model asset:

- destination: `app/src/main/assets/fish_classifier.tflite`
- expected bytes: `2077712`
- expected SHA-256: `5bb77f0bea96be2c6d2ace8a0fea36e8907bc9e4076beac05e0c82f44c345459`
- TFLite identifier: `TFL3`

`python3 scripts/verify_production_model.py` and Android CI reject any missing or different file.

The model is the Android-validated v1.1.2 production asset with input `[1,224,224,3]` Float32 RGB and output `[1,10]`.
