# SWOS 3650ms Dynamic Hi-Fi Demo — Technical Handoff V1

**Status:** `IMPLEMENTATION_PLAN_READY` · dynamic demo not implemented in this change
**Design basis:** Main-adopted SWOS contracts from PR #186/#187, integrated via PR #190. Phase 1 still requires explicit entry, approved photo inputs and evidence.
**Scope:** Design Manager / browser demo first; no Android production code or APK

## Repository evidence and technology decision

The current Design Manager renders its experience registry with ordinary HTML and CSS (`design/manager/app.js`, `styles.css`). The existing RR09 V2 historical playground is also an HTML page with Canvas/SVG overlays under `design/manager/rr09_motion/`; it is historical V2 evidence and must not be presented as a V3 implementation. Recognition Android already has Jetpack Compose Canvas/native-canvas drawing owners in `RecognitionAmbientField.kt`, `RecognitionFishFocus.kt`, and `RecognitionRevealOpticalRenderer.kt`. A repository search found no existing WebGL 2 renderer or shared SWOS Canvas renderer in the Design Manager. This supports reusing the current DOM/CSS page surface and preserves a native drawing boundary, but it is not a performance benchmark.

The current Design Manager CI browser gate checks page routes, resource loads, interactions and responsive layout; it does not record Canvas 2D or WebGL 2 capability. Local Chromium was unavailable during this audit, so no browser capability result is claimed here. The PR CI run is expected to verify the page workspace, but it does not establish GPU performance, mobile compatibility, or Android API 28 support. The dynamic demo must record actual Canvas 2D/WebGL 2 context availability and frame-time evidence before the renderer choice is treated as verified.

| Work | Proposed implementation | Reason / fallback |
|---|---|---|
| Existing Processing and Result UI | Reuse existing DOM/CSS and Frozen Result HIGH structure | Preserves navigation, status card, field order, metadata, story, and dual CTA |
| Shared photo geometry | One oriented source image element in a single shared visual layer, with one normalized source-to-viewport matrix | Photo, crop window and fish overlay derive from one transform; avoid independent page snapshots and re-crops |
| Ambient water caustics | WebGL 2 shader after browser capability and performance measurement | Best fit for multiple soft, irregular, multiscale fields; Canvas 2D fallback paints cached low-resolution textures |
| Gold Core / Soft Glow / Halo | Canvas 2D or SVG paths generated from one trusted contour | One geometric input feeds all three layers; suppress all fish-attached gold when geometry trust is absent |
| Gold transfer | SVG path or Canvas 2D path in the same result coordinate space | Path origin is transformed from the trusted fish contour; target binds to the existing Chinese title region |
| Chinese glyph mask | Canvas 2D offscreen text mask using the installed Chinese typeface | Renders partial strokes without English row; if required glyph/font is unavailable, show the static title without decorative mask |
| Unified clock | One deterministic JavaScript timeline sampled by `elapsedMs` | All optical/UI layers consume one time; pause/step/seek/slow-motion do not start separate clocks |

### Renderer fallback policy

1. Probe WebGL 2 at demo startup. If missing, blacklisted, or context-lost, use the Canvas 2D caustic renderer without changing frame times or photo geometry.
2. If Canvas 2D cannot allocate an offscreen layer, fall back to static optical samples and keep the UI and photo transition usable.
3. When `prefers-reduced-motion: reduce` is active, omit decorative caustic propagation, fish glow and glyph travel; show the settled high-fidelity result without delaying the CTA.
4. Renderer quality may reduce blur resolution, secondary patch count and halo radius. It must not change fish coordinates, source identity, crop policy, page content, reveal order, or total 3650ms duration.
5. Collect frame-time, dropped-frame, context-loss and layer-resolution measurements on target Chromium devices before choosing shader constants. The current relative strengths are design values, not opacity, shader uniforms, or calibrated GPU costs.

## Unified rendering function

The demo renderer should be pure for a fixed input tuple:

```text
RenderFrame(elapsedMs, photo, geometry, opticalConfig, recognitionState)
```

The function returns a visual frame and does not mutate recognition state. Inputs include the SHA-256 and EXIF-normalized dimensions; Processing content rect; Result Hero rect; one shared photo transform; trusted fish geometry plus its trust state; and the optical-strength sample. Tests must render the same fixture/time twice and compare the resulting geometry and stable pixel regions.

The controller samples one timeline:

```text
0–2800ms: Processing samples P0–P7
2800–3650ms: RR09 samples R0–R5 (R0.5 at global 2900ms)
```

The first presented Result frame is RR09 T0. R0.5 is a seek/sample point inside 0–200ms, not a hold or transition delay. The existing five RR09 phases remain 0–200 / 200–380 / 380–550 / 550–680 / 680–850ms.

## Shared photo transform

Normalize orientation once, then define source coordinates in oriented pixels or a normalized `[0,1]` source plane. The geometry engine owns the source-photo identity, Processing content rect, Result Hero rect, fit/crop choice, and one transform `M(t)`. The photo foreground, its same-source blurred fallback, and the trusted contour all derive from `M(t)`; no view independently interpolates the fish path or starts a second crop animation.

At R0, use the Processing photo display geometry. At R0.5, interpolate center and a single uniform scale factor to the midpoint geometry while changing the outer container bounds. At R1, fit the same oriented source into the frozen 322×210dp HIGH Hero. Use the current Hero Media Contract: a verified BBox safe crop when it retains the valid subject; otherwise keep the complete source foreground over a low-contrast blurred version of the same source. Never stretch, invent pixels, swap images, or introduce a second crop jump near 200ms.

If fish geometry is absent or untrusted, preserve the photo motion and page transition but omit the fish outline, gold transfer origin, and any claim of fish lock. The manual 208758 contour is review artwork only.

## Optical and text layers

- Water caustics live only in the optical renderer and use low-to-medium-density irregular area patches, soft multiscale falloff, and upward propagation. No closed frame, mechanical scan path, Edge Field seven-path substitution, or photo-scene rewrite.
- Gold Core, Soft Glow, and Halo share the exact contour and shared transform. They have one strength input and hold without flashing or breathing from 1550ms to 2800ms.
- At 200ms after Result begins, transfer may start only after the photo has reached R1. Its source is the real trusted fish contour. Its target is the existing Chinese title area. The title component supplies the target bounds; a separate hard-coded glowing title is not permitted.
- Build the Chinese glyph mask from the actual title text and the final font. R3 may show incomplete unreadable mask fragments. R4 is fully readable at 680ms. No English scientific-name row is reserved in the main-adopted V3 design contract. R5 removes all temporary glow and remains interactive.

## Browser compatibility and evidence

The next demo branch must record Chromium version, browser/device scale, Canvas 2D and WebGL 2 context availability, selected fallback, viewport/DPR, frame-time distribution and screenshots/recording. Probe context loss and resize. Use Canvas 2D if WebGL 2 is unavailable; do not equate a successful context creation with acceptable rendering speed. Run the 3650ms capture at 1× and test deterministic seek at all 15 keys. No browser result proves Android API 28 parity.

Test targets include 320/360/390dp widths, tall and landscape source photos, EXIF rotations, a non-water grass/shore photo, indoor/night photos, a low-light source, and a photo with missing/untrusted BBox. Confirm source checksum consistency, no stretching, stable fish/photo registration, no frame jump at 200ms, text timing, CTA availability, Reduce Motion behavior, and no continuing glow at R5.

## Android API 28 migration boundary

Do not port WebGL to production Android. Keep the production surface in the existing Compose page owners and draw optics using the existing Android Canvas/native-canvas route. Map the deterministic `elapsedMs`, normalized photo geometry, and trust-state contract into Kotlin data classes. Reuse the image matrix for photo and fish path. Any API 28 shader/blur optimization must be measured on the real runtime matrix; pre-rasterize small caustic textures or reduce only decorative resolution if needed. Existing detector, crop, classifier, confidence and result routing contracts stay outside this design task.

## Dynamic demo implementation plan

| Task | Output | Acceptance |
|---|---|---|
| D1 · Timeline/controller | One 3650ms clock with pause, step, seek, 0.25×/0.5×/1× | All 15 keys selectable; R0.5 never changes duration; deterministic repeated render |
| D2 · Geometry engine | Oriented source identity, Processing/Result rectangles, shared transform and crop policy | P7/R0/R0.5/R1 trace the same source; no stretch, swap, or 200ms crop jump |
| D3 · Processing shell | Existing Processing DOM/CSS plus SWOS overlays | Status/navigation hierarchy remains; ambient and fish gold stay independent |
| D4 · Ambient renderer | WebGL 2 caustics with Canvas 2D fallback | Relative curve, no forbidden motifs, reduced quality does not change geometry or time |
| D5 · Fish/gold renderer | Trusted contour, three shared layers, transfer path | Untrusted geometry disables attached gold; stable hold; transfer starts after R1 |
| D6 · Glyph renderer | Chinese mask and progressive title reveal | R3 unreadable, R4 readable at 680ms, no English line, R5 static |
| D7 · Review controls | Layer switches, intensity controls, original/composite compare, geometry overlay, parameter export | Export captures fixture identity and all changed tunables; no production state mutation |
| D8 · Verification | Multi-photo cases, Chromium screenshots/recording and perf report | 3650ms completion; pause/step/seek/fallback/Reduce Motion; R5 CTA remains operable |

Each dynamic result must be reviewed against this 15-frame contract and the existing Processing, main-adopted RR09 V3 design contract, Result HIGH page and Hero Media Contract. That review is separate from Android Runtime acceptance.
