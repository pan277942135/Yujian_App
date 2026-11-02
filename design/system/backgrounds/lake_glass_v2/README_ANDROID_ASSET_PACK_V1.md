# Lake Glass V2 Android asset pack — P1

Asset-only controlled reconstruction from the supplied Candidate 02 Master. No Android page replacement is included.

## Status

ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS. Broad upper-right sunlight and lower grass/root beds remain baked in lake_base_clean_v2.png. Do not draw the procedural sunlight field over that base.

## Entry points

- assets/base/lake_base_clean_v2.png
- assets/caustics/caustics_texture_v2.png
- assets/grass/grass_left_rgba_v2.png and grass_right_rgba_v2.png
- assets/droplets/ (three optical map sets and shaped RGBA compatibility layers)
- assets/android_runtime/runtime_manifest.json
- assets/metadata/asset_manifest_v3.json

Run scripts/build_lake_glass_true_layers_p1.py then scripts/verify_lake_glass_true_layers_p1.py from repository root.
