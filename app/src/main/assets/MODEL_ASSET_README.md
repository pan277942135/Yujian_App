# Production mobile model contract

The Android app uses the verified mobile export of `MODEL_M1_v0.2`.

## Asset

- destination: `app/src/main/assets/fish_classifier.tflite`
- model version: `MODEL_M1_v0.2_LITERT_WHOLE_FISH`
- expected bytes: `6220308`
- expected SHA-256: `9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e`
- TFLite identifier: `TFL3`
- source TorchScript SHA-256: `baa3b729b6928cc7bc5d9585e977dc1b30083a7e725497a14cbf67c5830a3945`

## Classes

Exact output-index order:

0. `grass_carp` — 草鱼
1. `bighead_carp` — 鳙鱼
2. `silver_carp` — 白鲢
3. `common_carp` — 鲤鱼
4. `crucian_carp` — 鲫鱼
5. `largemouth_bass` — 加州鲈
6. `snakehead` — 黑鱼
7. `yellow_catfish` — 黄骨鱼
8. `black_carp` — 青鱼

## Preprocessing

Approved mobile preprocessing is `whole_image_letterbox_imagenet`:

1. preserve the complete source image and its aspect ratio;
2. fit the complete image inside the model's `224 × 224` canvas;
3. do **not** center-crop any part of the fish;
4. center the scaled image and pad unused pixels with RGB `[124,116,104]`;
5. convert RGB to Float32 and apply ImageNet normalization:
   - mean `[0.485,0.456,0.406]`
   - std `[0.229,0.224,0.225]`;
6. feed the tensor in the layout declared by the exported LiteRT model (Android supports both NCHW and NHWC and validates it at runtime).

On the complete 35-image `DS_M1_v0.2` test split, whole-image letterbox improved Top-1 from 60.0% (center crop) to 62.9% and Top-3 from 85.7% to 88.6%.

`python3 scripts/verify_production_model.py` and Android CI reject any missing or different model binary.
