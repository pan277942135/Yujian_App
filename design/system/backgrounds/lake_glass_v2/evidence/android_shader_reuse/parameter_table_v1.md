# Lake Glass V2 AGSL parameter table

Values are bounded in Android code. The formal host defaults to static output (`enabled=false`, motion disabled). Debug lab controls can isolate each effect.

| Effect | Uniforms / UI control | Master-space restriction | Range | Initial lab value | Runtime status |
|---|---|---|---:|---:|---|
| Caustics | `causticsEnabled`, `causticsAmount`; AGSL `causticField()` | Red channel of the verified 360×780 optical-mask atlas, mapped through the shared 1440×3120 CenterCrop transform | 0.000–0.035 multiplier | 0.014 | In-place modulation of existing baked luminance; no second caustic sheet |
| Sunlight | `sunlightEnabled`, `sunlightAmount`; `elapsedSeconds` | Green channel mask; fixed Master anchor `(1361,109)` | 0.000–0.018 multiplier | 0.007 | Fixed upper-right field; slow noise may modulate the baked light only |
| Droplet refraction / baked highlight | `dropletsEnabled`, `dropletAmount`; independent `drop01/02/03Enabled`; `elapsedSeconds` | Canonical `pixelBounds` and `anchorPx` for the three manifest droplets; blue channel region mask | 0–12 Master px; refraction shimmer ±8%; baked-highlight modulation ≤0.8% | 6 Master px | SDF-gated screen-space sample from the static composite input; tiny time variation is confined to fixed anchors and existing bright pixels; no sprite overlay, added glint, or position movement |
| Ambient time | `motionEnabled`, `elapsedSeconds` from `AmbientController` | Independent monotonic timeline, not recognition or WaterLight clock | disabled or elapsed seconds | disabled | Not advanced by page content or recognition lifecycle |

Shader params are provisional inputs until exact-head API 33+ emulator output, visual comparison and target-device review are recorded. No estimated normal map is represented as measured optical truth.
