# YuJian V3｜Stage 1 核心视觉规格冻结

**Status:** CORE_VISUAL_SPEC_FROZEN_WITH_TUNABLES  
**Freeze scope:** 26KF source mapping + core visual invariants + Orb geometry and Resident topology.  
**Numeric shader tuning:** deferred to Stage 3 device calibration; no unsupported numeric value is declared frozen.

## 1. Authority and source mapping

- 26KF master contact sheet: YuJian_AI_26KF_HighRes_Overview_V2.png (8677×5626; SHA-256 173b1314bcfaebbf0510b71585a929eb67be962806b225b7660cedcaf115abac).
- Independent KF00 Home and KF01 Capture supplements are recorded in V3_26KF_SOURCE_MAPPING_V2.md.
- The 26KF mapping is 26 visual process points, not 26 runtime stages. 13 main runtime stages remain unchanged.
- KF14 uses the KF05 path in reverse with the same Path ID and geometry.
- KF25 is save-success → return to KF00 Home. No separate save-success artwork or animation is required.
- 12.2 GLYPH_EMERGENCE is Frame 5 of the six-frame Glyph board; it is an embedded subphase, not a 27th KF.

## 2. Inherited timing contract

The 13-stage minimum visibility budget remains [0,350,600,125,125,450,450,450,1000,350,850,800,450]ms, sum 6000ms, with a 60,000ms abnormal timeout. Model/Mask events remain reveal gates. This document does not change the controller timing contract.

## 3. Orb core — frozen

1. Use the existing transparent master 透明湖水玻璃球.png and keep the full 1254×1254 transparent canvas.
2. Keep the existing Runtime core WebP byte-identical: processing_water_core_v1.webp, 384×384 RGBA, SHA-256 daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09.
3. The core sphere silhouette does not morph between idle and active. Wake/active changes are carried by overlays and Resident filaments.
4. The visible sphere occupies about 79.4%×79.3% of the source texture canvas. Source alpha bbox at threshold 128 is [129,124,1125,1118].
5. Reference screen anchor from KF02/KF03/KF05 images is approximately (0.499W,0.860H) with visible diameter around 23.5% W; the observed diameter range is 22.53–24.23% W. These are raster-derived composition measurements, not shader uniforms.

## 4. Orb material and optical appearance — frozen visual invariants

- Transparent glass shell and visible rim separation.
- Dark teal inner water with layered cyan/blue and emerald-green liquid.
- Warm gold key highlight from upper-left; bright cyan caustic response in the lower core.
- Existing baked green band is preserved; it is not an animated plant rig.
- Droplets are sparse, small, and clipped to the actual core alpha.
- Refraction/caustic overlays must respect the core alpha and must not introduce rectangular masks, black matte, or double-exposed baked highlights.
- Bloom is restrained: preserve glass edge, inner liquid structure, and filament detail; no whole-ball flash or washout.

## 5. Resident filaments — frozen topology

- Resident IDs: G01, G02, A01, A02, C01.
- BACK: G01, A01; FRONT: G02, A02, C01.
- These five paths are always Orb Resident structure, not five Task filaments.
- Resident paths use open Bezier trajectories, independent phase/period values, deterministic motion, and one shared motion clock.
- BACK draws behind the immutable core; FRONT draws above it with a center-safety fade.
- Task energy count is null / dynamic and must not be inferred from Resident count.
- Exact R06 Bezier control points, periods, phases, width, alpha, and deformation are preserved as candidate values only until visual/device review.

## 6. Motion — frozen behavior, tunable numerics

- Internal fluid must look like continuous layered liquid motion, not a simple alpha pulse or flat rotating texture.
- Resident paths must continuously coil around the core with different phase/speed; no per-frame random jitter.
- Release and Return are direction changes on the same path identity. Return reverses the existing path; it does not create a new path.
- Exact angular velocity, fluid advection, phase offsets, speed curve, and transition ramp remain tunable.

## 7. Mask / P* boundary

Visual contours and Task path sketches are reference-only. Runtime contour, Geometry Version, Path ID, and P* must be derived from the trusted Mask of the current Attempt. BBOX_ONLY cannot be promoted to a pixel mask and cannot fabricate P*.

## 8. Explicit tunables

The following are intentionally not frozen from composite PNGs: physical IOR, numeric Fresnel curve, refraction displacement, Bloom threshold/radius/intensity, fluid velocity field, shader noise amplitude, FBO resolution, droplet timing/positions, Resident numeric periods/phases/width/alpha, and device performance tier. Stage 3 must record each candidate value and every calibration change in Stage 2 evidence.

## 9. Stage 3 entry gate

**Allowed:** Orb-only GPU implementation using the frozen core asset, visual invariants, Resident identity/depth topology, and the candidate profiles as explicitly labeled starting values.

**Forbidden in Stage 3:** Task energy to photo, perimeter, fish seeking, Mask/P*, fish contour, Gold Peak, Glyph, Result, or any new Runtime/Clock. These belong to later stages.

Stage 1 passes for the core visual specification. This is not a claim that Android GPU rendering or device visual acceptance has passed.
