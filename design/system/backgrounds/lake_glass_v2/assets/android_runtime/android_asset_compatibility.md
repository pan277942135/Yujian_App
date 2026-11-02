# Android asset compatibility

- Minimum API: 24. API 24–32 use the supplied static reconstruction PNG or unchanged Master fallback.
- API 33+ may bind droplet optical maps and caustic detail to optional AGSL RuntimeShader. No device shader test was run.
- Draw all layers through one 1440x3120 scene transform. Use SRC_OVER for RGBA assets and PLUS/PorterDuff ADD for caustic detail.
- Sunlight remains baked in the base. Do not draw the procedural sunlight preview over the base.
- No Android page integration or GLSurfaceView is included.
