# YuJian Android Launcher Icon V1.2 FINAL

Production-oriented Android launcher icon asset package.

## V1.2 fixes
- Rebuilt `icon_background_1080.png` as a true clean full-square lake layer.
- Removed V1.1 baked fish residue, corner-mark residue, rounded-square border and presentation glow from the adaptive background.
- Foreground geometry scaled to **0.85× of V1.1** about canvas center.
- Primary fish body is fully inside Android's centered **66×66 dp guaranteed safe zone**.
- Gold corner marks are retained as non-critical decorative elements and may enter the outer effect/mask area.
- Monochrome geometry is generated directly from the final foreground alpha and contains white + alpha only.
- Regenerated all legacy density assets and true circular round fallbacks.
- Regenerated Play Store 512 as full-square, fully opaque **RGBA (32-bit)** PNG with no pre-rounded mask.
- Manifest is now standard `sha256sum -c` compatible.

## Android integration
Copy `android/res/*` into the app module `src/main/res/`, preserving directories.

Manifest references:
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

## Adaptive layers
- 1080×1080 source corresponds to the Android 108×108 dp adaptive icon canvas.
- `source/icon_background_1080.png`: RGB, clean full-square scene, no baked final mask.
- `source/icon_foreground_1080.png`: RGBA brand foreground.
- `source/icon_monochrome_1080.png`: white RGBA silhouette for themed icons.

## Legacy sizes
- mdpi: 48×48
- hdpi: 72×72
- xhdpi: 96×96
- xxhdpi: 144×144
- xxxhdpi: 192×192

## Play Store
- `play_store/icon_512.png`: 512×512 RGBA PNG, full square, alpha fully opaque, no pre-rounded corners.

## Review previews
Files under `preview/` are review-only and must not be referenced by the app.
