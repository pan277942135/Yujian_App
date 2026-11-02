# Lake Glass V2 asset pack audit

Status: **ASSET_PACK_READY_FOR_ANDROID** (static-first). The complete, verified static composition is the only visible runtime surface. It is safe for API 24–32 and shader fallback. Provisional optical shader inputs do not create separate water-drop, sunlight, grass, or caustic surfaces.

The single authority is `assets/android_runtime/layer_manifest_v1.json`; `asset_manifest_v3.json`, `lake_layer_pack_v3.json`, and `runtime_manifest.json` pin its SHA-256. Each layer carries source/asset SHA-256, Master bounds, anchor, z-index, blend operation, alpha, separation status, runtime mode, motion policy and fallback.

- Grass extraction alpha and root-bed overlap are not certified for runtime; use static composition only.
- The sunlight clean plate remains blocked by baked reflection; the light stays in Base.
- Droplet normal maps are estimated; shader refraction is constrained to the existing fixed droplet regions and samples the same composite.
- Caustic modulation is non-tiled and multiplies existing luminance only.
- Design status remains candidate, not frozen. No runtime device or dynamic visual acceptance is implied by the asset-pack status.
