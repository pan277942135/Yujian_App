# Lake Glass V2 Android asset pack — P1 / static-first runtime contract

Asset-only controlled reconstruction from the supplied Candidate 02 Master. No Android page replacement is included.

## Status

**ASSET_PACK_READY_FOR_ANDROID** means the verified static composite can be used safely on API 24+, with a static fallback on every device. It does not certify that every extracted feature is an independent visible layer. The upper-right sunlight, water drops, grass/root beds, and lower caustics remain baked in the composition; provisional API 33+ effects sample and modulate those pixels in place. No standalone visual overlay is enabled.

## Entry points

- assets/base/lake_base_clean_v2.png
- assets/caustics/caustics_texture_v2.png
- assets/grass/grass_left_rgba_v2.png and grass_right_rgba_v2.png
- assets/droplets/ (three optical map sets and shaped RGBA compatibility layers)
- assets/android_runtime/layer_manifest_v1.json (single source of runtime status)
- assets/android_runtime/runtime_manifest.json
- assets/android_runtime/api24_static/lake_glass_static_layered_v3.png
- assets/android_runtime/runtime_optical_masks_v1.png
- assets/metadata/asset_manifest_v3.json

The grass and droplet RGBA files, normal maps, and detailed caustic texture remain available for audit and future work but are not loaded as visible Android overlays. Grass alpha candidates contain isolated fragments and edge color contamination; reconstructed droplet clean plates contain obvious circular patches. Those defects are why the runtime keeps the full-scene image intact.

Run from repository root:

```bash
python3 scripts/build_lake_glass_true_layers_p1.py
python3 scripts/build_lake_glass_android_runtime_v1.py
python3 scripts/verify_lake_glass_android_runtime_v1.py
```

`evidence/android_runtime_asset_pack_acceptance_v1.json` records the current Master comparison. `ANDROID_DEVICE_VALIDATED` is a separate state and is not implied here.
