# YuJian V3｜Orb Source and Parameter Audit V1

**Audit status:** SOURCE_MEASUREMENTS_CONFIRMED / NUMERIC_MOTION_AND_OPTICS_PARTLY_CANDIDATE  
**Scope:** source pixels, existing runtime core, Resident path topology, droplets, lighting, fluid/refraction candidate profiles. No production code or shader changes.

## 1. Core image master and runtime binary

| Asset | Dimensions / format | Size | SHA-256 | Disposition |
|---|---|---:|---|---|
| 透明湖水玻璃球.png | 1254×1254 RGBA | 1,674,284 B | 2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee | Frozen visual source master; preserve full transparent canvas |
| processing_water_core_v1.webp | 384×384 RGBA | 27,632 B | daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09 | Existing Runtime core; keep byte-identical |
| 翡翠星光中的流光水晶球.png | 1254×1254 RGB | 2,004,711 B | 910d15313b39ee2afa277356c46290e2cb7739b80a60e9ab4efaa14a29d74122 | Dark composite/material reference; not an alpha core |
| 236493.png | 1254×1254 RGB | 2,846,520 B | adefad98ec7cfe32df1316a605ac7f3ded8a5a7b33e107f2380ecbf49f2f7c35 | Decoded pixels identical to 翡翠星光…; byte hash differs |

### Pixel measurements

- Source alpha mask at threshold 128: bbox [129,124,1125,1118], size 996×994; center (626.64,621.13) on the 1254×1254 canvas.
- Source visible-silhouette ratio: 79.43% canvas width × 79.27% canvas height; alpha coverage 49.18%.
- Runtime WebP alpha mask at threshold 128: bbox [39,38,345,342], size 306×304; center (191.53,189.84) on the 384×384 canvas.
- Runtime alpha agreement with Lanczos-resampled source at alpha threshold 16: IoU 0.99825019; alpha MAE 0.170736 / 255. This is asset-level comparison, not Android/GPU visual acceptance.
- Do not crop the transparent canvas or scale the source sphere independently from its transparent margins.

## 2. Screen-space Orb anchor measured from three independent portrait references

Measurement method: Hough circle detection restricted to the lower Orb region of three independent composited reference screenshots. These are approximate raster measurements, not production shader uniforms.

| Reference | Center (px) | Radius (px) | Center normalized (x/W, y/H) | Diameter / viewport width |
|---|---:|---:|---:|---:|
| 01 PHOTO_STABLE | (470,1432) | 106 | (0.4995,0.8565) | 22.53% |
| 02 ORB_WAKE | (470,1438) | 113 | (0.4995,0.8600) | 24.02% |
| 03 ORB_RELEASE | (468,1442) | 114 | (0.4973,0.8622) | 24.23% |

Reference target: center approximately (0.499W,0.860H); visible diameter approximately 23.5% W, with the measured state range above. Use this as a composition target and verify on device; do not treat Hough values as exact GPU uniforms.

## 3. Frozen visual invariants

- The same immutable core image is used in idle and active states; the sphere silhouette does not morph during wake/active. Animate overlays and surrounding Resident layers, not the base sphere geometry.
- Glass appearance: transparent rounded spherical shell; dark teal inner water; cyan/blue and emerald-green liquid layers; warm gold upper-left specular; bright cyan lower-core caustic; visible rim separation.
- Keep the upper-left warm key-light direction consistent. Do not add a second full-ball flash over the baked highlight.
- The green band inside the core is baked source artwork, not a separately rigged plant. Preserve it. Deprecated R01_grass_back.svg and R01_grass_front.svg must not be integrated.
- Water droplets remain sparse and small, integrated with the core alpha; do not turn them into a dense bubble-particle field.
- Dynamic refraction/caustic overlays must be clipped to the real core alpha. Never draw rectangular masks or create a black matte.
- Bloom must preserve rim, fluid detail, and filament detail; no whole-ball flash or highlight washout.

## 4. Resident filament topology

The five IDs are frozen as Orb Resident filaments, not Task energy:

| Path | Material | Depth | Existing candidate period | Phase | Existing candidate width (dp) | Existing candidate alpha |
|---|---|---|---:|---:|---:|---:|
| G01 | Green water ribbon | BACK | 6100 ms | 0.07 | 1.5–3.2 | 0.18–0.35 |
| G02 | Green water ribbon | FRONT | 5300 ms | 0.34 | 1.4–2.7 | 0.18–0.38 |
| A01 | Fine gold filament | BACK | 3700 ms | 0.59 | 0.55–0.92 | 0.45–0.69 |
| A02 | Fine gold filament | FRONT | 2900 ms | 0.81 | 0.55–1.00 | 0.45–0.72 |
| C01 | Cyan water ribbon | FRONT | 4400 ms | 0.23 | 1.4–2.8 | 0.18–0.40 |

**Freeze:** identities, Resident role, material families, depth topology (2 BACK / 3 FRONT), open-Bezier topology, independent phase/period principle, one shared motion clock, deterministic motion, and no double draw.

**Candidate only:** exact centerline coordinates, numeric periods/phases, width/alpha ranges, deformation amplitudes, speed-distance LUT, head/trail profiles, 14dp expansion bound. The historical R06 profile is marked V2_VISUAL_CANDIDATE_PENDING_PRODUCT_REVIEW; its numbers must not be silently promoted to V3 frozen values.

Layer order: BACK paths draw behind the immutable core; FRONT paths draw above the core with a center-safety fade. The old candidate uses a 0.30–0.46D fade radius; retain as a candidate until device review.

## 5. Motion / release / return

- Resident paths use open Bezier centerlines and independent phase/period values under one shared MotionClock. The R06 board samples 0/900/1800/2700ms and demonstrates deterministic continuous motion.
- KF14 return reuses the same Task Path identity and geometry created for KF05; reverse direction on that path. Do not create a second visually similar return path.
- The historical R06 candidate uses arc-length position retention, reverse velocity, C1 speed ramp, and no clock reset. The 260ms ramp is candidate, not frozen.
- Internal liquid motion must be layered, organic, and continuous; not a single alpha pulse or a rotating flat texture. Exact flow speed/frequency remains tunable.

## 6. Existing candidate profiles (not frozen numeric values)

| Profile | Existing candidate values | Status |
|---|---|---|
| R02 droplets | 3 droplets; UV (0.32,0.36), (0.68,0.56), (0.49,0.73); periods 5200/6100/4800ms | TUNE_BASELINE |
| R03 sunlight | sourceUV (0.24,0.20); upper-left→lower-right; warm gold #D8BC87; extra additive alpha cap 0.22 | TUNE_BASELINE |
| R04 states | same immutable core; S1 idle / S2 active; S1→S2 450ms; entrance morph 900ms; S2→S1 620ms | VISUAL_CANDIDATE_PENDING_PRODUCT_REVIEW |
| R05 caustics | deep flow 6500ms/frequency 1.65/alpha 0.07; surface caustic 4100ms/frequency 3.05/alpha 0.12 | TUNE_BASELINE |
| R06 Resident | five path periods/phases/widths/alphas listed above | V2_VISUAL_CANDIDATE_PENDING_PRODUCT_REVIEW |

These values are preserved as traceable starting candidates only. Stage 3 may use them for an initial diagnostic render, but every change must be captured in the Stage 2 Evidence ZIP and compared against the frozen visual invariants.

## 7. Explicitly not inferable from composited PNG

Do not invent or freeze: physical IOR, numeric Fresnel curve, refraction displacement strength, Bloom threshold/radius/intensity, exact internal fluid velocity field, shader noise amplitude, GPU FBO scale, and device-specific performance tier. These require controlled Stage 3 rendering and device PixelCopy calibration.

## 8. Geometry / model boundary

- Orb local alpha geometry is derived from the source core texture.
- Fish contour references in KF09–KF13 are visual references only.
- Runtime fish contour must use the trusted Mask for the current Attempt.
- P* must be derived from the trusted Mask outer contour and legal contact rule; no fixed tail anchor or hard-coded image point.
- BBOX_ONLY is not a trusted pixel mask and must not fabricate P*.
