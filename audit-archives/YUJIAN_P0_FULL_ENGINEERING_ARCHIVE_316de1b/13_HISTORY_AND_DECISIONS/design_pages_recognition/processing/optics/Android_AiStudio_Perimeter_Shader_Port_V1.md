# Android AI Studio Perimeter Shader Port V1

**Task:** `YUJIAN_ANDROID_AISTUDIO_PERIMETER_SHADER_PORT_V1`
**Status:** Source and parameter contract frozen; Android runtime and visual acceptance not verified.
**Scope:** Directly port the source repository's four-sided image perimeter light into the existing Android recognition Processing photo.

## Source authority

- Repository: [`pan277942135/demo--Image-light-effect`](https://github.com/pan277942135/demo--Image-light-effect)
- Source commit: `794e7f2698525761bbb2f3e5d775b5dae8d7a25a`
- Shader: [`src/water-caustics/shaders.ts`](https://github.com/pan277942135/demo--Image-light-effect/blob/794e7f2698525761bbb2f3e5d775b5dae8d7a25a/src/water-caustics/shaders.ts#L90-L269)
- Perimeter nodes and configuration: [`src/water-caustics/fish-catch-caustics.ts`](https://github.com/pan277942135/demo--Image-light-effect/blob/794e7f2698525761bbb2f3e5d775b5dae8d7a25a/src/water-caustics/fish-catch-caustics.ts#L17-L53) and [perimeter path / accents](https://github.com/pan277942135/demo--Image-light-effect/blob/794e7f2698525761bbb2f3e5d775b5dae8d7a25a/src/water-caustics/fish-catch-caustics.ts#L105-L216).

This is an algorithm port, not a visual redesign. The source commit is the only algorithm authority. Preserve its source expressions and frozen values except for the one explicitly allowed `smoothstep` compatibility rewrite below.

## Included effect

1. The continuous four-sided ribbon based on `sdRoundedBox`, `perimAngle`, `perimWave`, `wideHalo`, `mediumBloom`, `sharpCore`, and `perimeterAura`.
2. The bottom-up `revealedMask`, with linear Android progress `clamp(elapsedMs / 1200, 0, 1)` and the shader's own smoothing.
3. The original cyan, gold, and green composition from `filamentGoldBias` and `perimeterColor`.
4. The original `perimContrib`, `totalLight`, `finalAlpha`, and `coreSpark` edge-branch semantics.
5. Sixteen perimeter nodes using the source `generatePerimeterPath()` geometry (280 subdivisions in the renderer) and parameterized two-ended reveal, movement, pulse, color, and halo rules:
   - `baseParam = index / 16`; `pPos = (baseParam + timeSec * speed) % 1`.
   - `rightBranchEnd = 0.5 * progress`; `leftBranchStart = 1 - 0.5 * progress`; a node is visible only in either active branch.
   - Movement speed is `0.012 + (index % 3) * 0.007`; size is `2.2 + (index % 4) * 0.7` source CSS coordinate units.
   - Pulse is `0.65 + 0.35 * sin(timeSec * 3.4 + phase)`; color switches at `pPos > 0.45` between `#FEF08A` and `#E0F2FE`.
   - Halo radius is `size * 3.2 * pulse`, core radius is `size * 1.15 * pulse`, halo alpha is `0.42 * pulse * intensity`, and core alpha is `0.92 * pulse * intensity`.
   Their randomized initial phase may use a fixed seed for repeatable captures. Node count, placement, size, movement, pulse, color, and halo formulas remain unchanged.
6. Source-equivalent Overlay composition over the same PhotoSession photo.

## Frozen values

| Parameter | Value |
|---|---:|
| `spreadDurationMs` | `1200` |
| `intensity` | `1.50` |
| `speed` | `1.00` |
| `marginRatio` | `0.015` |
| `glowSize` | `48` source Shader coordinate units |
| `goldColor` | `#FEF08A` |
| `cyanColor` | `#38BDF8` |
| `greenColor` | `#2DD4BF` |
| `blendMode` | `overlay` |

`bottomMaskStrength` and `blendRatio` are not inputs to this perimeter-only render path. They cannot be used to tune the perimeter effect.

## Coordinate scale

The source canvas rounds its CSS width and height, caps its backing-store scale at `min(devicePixelRatio, 2)`, then sets the WebGL resolution to CSS size times that scale. Its shader uses physical backing-store coordinates, while the 16 node paths and radii are specified in CSS coordinates and scaled by the same capped DPR during drawing.

Before choosing Android fixed-unit values, record the reference browser's actual `devicePixelRatio`, perimeter canvas CSS dimensions, and backing-store/render dimensions. Record the Android photo surface's physical dimensions and `photoContentRect`, then define and retain one uniform scale mapping from the reference render space to Android render space. Apply that mapping to every fixed shader-space length, including the `glowSize`, wave displacement, core thresholds, and node radii. Keep `marginRatio` and rounded-corner radius proportional to the photo bounds. `glowSize=48` is never interpreted as 48 dp.

## Shader and composition rules

- Port the perimeter calculations from `FISH_CATCH_FRAG_SRC`; omit the bottom-light branch from executable rendering. The output must be produced by the perimeter branch only.
- Correct the source's undefined reverse-edge call `smoothstep(2.8, 0.3, dBox)` to `1.0 - smoothstep(0.3, 2.8, dBox)`. This preserves its descending transition intent and is the only allowed mathematical compatibility correction.
- Preserve the source's two-stage composition: the light shader's `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` drawing-buffer blend, followed by CSS `mix-blend-mode: overlay` over the photo. Determine the effective alpha and final RGB from that chain; do not treat `finalAlpha` as the whole photo-composite alpha by assumption.
- Preserve the node layer's `screen` drawing-buffer blend before its CSS Overlay composition. Validate the nodes and Shader against the source browser output as separate contributions and as the combined image.
- The Android output must be compared to the source browser output on the same photo, photo-content bounds, mapped reference coordinates, and all required timestamps before visual acceptance.
- The Android implementation must use GLES2 / GLSL ES 1.00 on API 28. It must compose the photo and effect in one verifiable GPU channel or use an API-28-verified Overlay surface. Independent transparent `GLSurfaceView` blending is not assumed to work.
- Original photo pixels, orientation, crop, `RecognitionImageTransform`, `photoContentRect`, model input, and PhotoSession identity remain unchanged.

## Excluded effects

Do not draw or execute `causticOctave()`, `causticField()`, `lightSpotsField()`, `bottomGrad`, `causticsMask`, `waterSpectrum`, `bottomCausticColor`, or `bottomContrib`; bottom caustics; rising scans; photo motion; ambient radial glow outside the photo; fish masks, contours, or golden fish reveal; the bottom-center flourish; or the existing native path filaments, extra particles, and duplicate edge nodes. Disable visible `RecognitionFishFocus` output during this task's perimeter acceptance captures. Recognition data, model output, and detector/classifier behavior are untouched.

## Time and lifecycle

- Use the existing `processingElapsedMs` / `visualClockMs` and reset the start point when the PhotoSession changes or a retry creates a new session.
- Pass `timeSec = elapsedMs / 1000 * speed` once. The shader then computes `t = u_time * 0.42`; the source `u_speed` uniform is unused by the edge equations and must not multiply time again.
- At 0 ms the perimeter is unrevealed; 300, 600, and 900 ms use the source reveal; at 1200 ms progress reaches 1; at 1800 and 2600 ms the reveal remains full while the light continues to move.
- Pause or stop rendering when the page is not active. Photo changes, cancel, retry, success, failure, and exit must follow the existing session and page lifecycle. Release EGL/GL resources after the renderer is detached; do not block Detector, Classifier, or the result state machine.

## Exact-head evidence required

Record evidence against the exact Android source SHA and exact installed APK SHA-256:

- Source SHA, source-file provenance, GLES version, renderer backend, shader compile/link logs, reference DPR/CSS/backing-store sizes, Android photo bounds, coordinate mapping, and Overlay/alpha comparison.
- Build, unit, AndroidTest compile, lint, and targeted API 28 runtime results.
- Same-photo AI Studio and Android captures at `0`, `300`, `600`, `900`, `1200`, `1800`, and `2600` ms, plus the complete runtime recording.
- Lifecycle evidence for PhotoSession change, cancel/exit, retry, success, and failure.
- Evidence that displayed photo geometry and recognition input/model outputs are unchanged.
- API 28 device installation and APK SHA-256. An emulator result alone does not satisfy a real-device requirement.

Visual PASS, formal freeze, and completion remain unavailable until the real-device and source-browser comparisons pass. Build or static checks cannot promote those gates.
