# Android asset compatibility

The single source of runtime layer status is [`layer_manifest_v1.json`](layer_manifest_v1.json). Its SHA-256 is pinned by `runtime_manifest.json`, `asset_manifest_v3.json`, and `lake_layer_pack_v3.json`.

- API 24–32: draw only the 1440×3120 `LAYER_STATIC_COMPOSITE`; the unchanged Master is the decode/error fallback. No RuntimeShader, OpenGL, EGL, or `GLSurfaceView`.
- API 33+: the same static composition remains the baseline. AGSL may apply low-amplitude in-place luminance modulation and fixed-anchor droplet refraction through the combined Master-derived mask. It draws no independent grass, water-drop, sunlight, or caustic overlay.
- Any capability, compile, draw, or quality failure returns to `LAYER_STATIC_COMPOSITE`. Motion defaults off until API 33 visual and device validation.
- All pixels and masks use one CenterCrop transform from the 1440×3120 Master coordinate system. Diagnostic maps are not loaded in the production scene.
- The 360×780 RGBA optical atlas stores caustic weight in R, baked sunlight weight in G, and the union of the three original droplet region masks in B. Its alpha is opaque.
