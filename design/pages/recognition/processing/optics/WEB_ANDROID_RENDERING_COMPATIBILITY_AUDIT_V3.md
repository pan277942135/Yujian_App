# Web → Android Rendering Compatibility Audit V3

Audit date: 2026-11-02  
Android Main: `ddd0db3a7f1bdb38cbd70081322c20d330ec7fad`  
Android PR #294 candidate: `b820baa41d62ab7d056e3b084daa5b9f655d8aeb`  
Web source: `pan277942135/Fish-Mask-Gold-Contour-Lab@54ac0b6307454549d004d1890d9ee12cc6b54040`

## Findings

| Area | Web reference | Android #294 | V3 action |
|---|---|---|---|
| Perimeter | `FishCatchWaterOverlay.ts` uses rounded perimeter distance/waves, revealed mask, multi-octave caustics, sunlit spots and tint mixing. | TextureView/EGL opaque photo composite preserves the photo but stops perimeter output at the legacy 2800ms reference and does not include the Web caustic/spot field. | Keep the opaque photo owner and frozen edge geometry; remove global-time cutoff and port only local, photo-safe caustic/spot modulation. |
| Config | EFFECT_01/EFFECT_02 define spread, intensity, colors, blend and `causticsDensity`. | Android values remain candidate constants. | Preserve values as candidates; `causticsDensity` is documented as unused in Web until wired to the shader. |
| Contour | `FishContourGlowRenderer.tsx` renders a real SVG contour with dark contrast under-stroke, blue/gold core, narrow ice highlight, moving gold dash, soft glow, halo and internal glint. | `RecognitionFishFocus.kt` draws the trusted Mask contour and Core/Soft/Halo, but lacks the alternating blue/gold core, moving dash and glint. | Port the visual layers onto the existing validated real Mask path only. |
| Resonance | `WaterResonanceRenderer.tsx` supports synthetic BBOX_ONLY blobs and an invented fallback box. | Not accepted as a production contour source. | Do not port its BBox/fallback geometry into Android fish focus. |
| Droplets | Web contour droplets are associated with contour motion. | `WaterDropletRenderer.kt` deterministically samples rectangular bounds. | Adapt stable droplet anchors to the trusted contour; suppress fish-attached droplets when Mask is absent. |
| Lifecycle | Web components are mounted as effects over React state. | Android shares frame identity/material structures but has legacy fixed optical projections. | Keep one Android scene authority and reject stale session/attempt/photo/geometry events. |

## Exact source notes

- Android PR #294 is a candidate, not Main. No claim in this audit promotes its changes to Main.
- Current PR perimeter uniforms (`1.50`, `.060`, `.75`) differ from Web EFFECT_02 candidates (`1.80`, `.015`, `.80`); calibration remains a candidate and is not asserted frozen.
- The Web `causticsDensity` setting is not declared as a shader uniform and has no effect in the audited Web build.
- Existing Android mask-to-canonical-photo mapping remains the sole source of fish geometry. BBox-only and synthetic geometry are excluded from contour/gold/droplets.
- No API28 physical-device runtime evidence was produced by this audit.
