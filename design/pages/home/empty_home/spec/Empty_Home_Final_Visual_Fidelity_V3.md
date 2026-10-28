# Empty Home Final Visual Fidelity V3

Status: ACTIVE CLOSURE CONTRACT

This document does not redesign Empty Home V2. It defines the final runtime-fidelity closure against the existing frozen authority.

## Frozen authority

- `design/system/core_visual_v1/reference/empty_home_v2.png`
- `design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2.png`
- `Visual_Spec_V2.md`
- `Acceptance_Criteria_V2.md`
- `Empty_Home_Motion_Spec_V2.md`

If this document conflicts with a frozen V2 visual/motion contract, V2 wins.

## Scope

Only:
- Empty Home native UI geometry;
- camera capture control material and motion;
- bobber / water-contact / ripple visual quality;
- Empty Home motion quality;
- Empty Home visual-evidence quality.

Out of scope:
- Recognition;
- Normal Home;
- model/runtime semantics;
- account/navigation;
- global CI architecture.

## Current baseline problem

The API28 runtime gate is functionally green, but the full-frame MAE threshold is too background-dominated to prove high fidelity.

Baseline main evidence at `8d8d1429249fc9d5f099c6ba843ee674bc9b2491`:
- full-frame MAE: 32.3536 / threshold 40;
- Hero region MAE: ~58.29;
- Camera region MAE: ~58.07;
- CTA region MAE: ~44.57;
- Bobber region MAE: ~9.92.

Visual review confirms:
- Hero is too wide, too far right, and too high relative to Frozen;
- camera control is materially too small and too low;
- CTA group scale/vertical rhythm does not match Frozen;
- full-frame PASS therefore cannot be treated as final high-fidelity PASS.

## Reference-space UI geometry

Use the 1080 × 1920 design reference, independently scaled into the runtime viewport for native UI geometry.

Frozen anchors:
- Hero bbox: x=50, y=224, w=620, h=310.
- Camera visual bbox: x=436, y=1500, w=208, h=208.
- Camera center: (540, 1604).

The runtime must preserve the frozen visible geometry rather than relying on density-dependent fixed dp sizes.

Header/login may continue to respect safe insets, provided final visual placement remains consistent with Frozen.

## Camera visual contract

Must read as:
- white solid body;
- fine visible gold rim;
- dark-blue camera icon;
- restrained physical depth;
- subtle breathing;
- intermittent gold-rim sweep.

The visible raster camera size must follow the frozen 208-reference-pixel bbox.

Motion values remain:
- breath 5000ms;
- max scale <=1.015;
- first gold sweep ~3000ms;
- sweep ~1400ms;
- repeat ~9000ms.

The sweep must be visible in normal-speed evidence but must never read as a loading ring.

## Bobber / water contact

Existing V2 anchors remain frozen:
- bobber bbox x=518 y=1084 w=24 h=122;
- water contact (530,1168);
- ripple center (530,1168).

Do not move the water plane with bobber motion.
Do not add a second ripple.
Do not introduce UI-like rings.
The bobber must visually read as seated in water, with restrained contact/refraction cues rather than pasted over the lake.

## Visual semantic evidence

Final evidence must include both full-frame and region-level comparison.

Required region checks:
- Hero region;
- Camera region;
- CTA region;
- Bobber/water-contact region.

Target:
- full-frame parity PASS;
- Hero region materially improved from baseline (~58.29);
- Camera region materially improved from baseline (~58.07);
- CTA region materially improved from baseline (~44.57);
- Bobber region must not regress from the already-close baseline (~9.92).

The closure may tighten region gates once the corrected runtime establishes non-regressive values. Do not lower a threshold to make a bad render pass.

## Motion review

The 15s video must show:
- bobber ±3 reference px / 4600ms;
- ripple 1.00→1.22 / 0.30→0 / 3200ms;
- subtle cloud drift;
- restrained sun particles;
- camera breathing;
- one clearly perceivable but restrained gold-rim sweep.

Phase offsets should prevent a synchronized mechanical loop.

## Completion / Stop Rule

Continue only while there is a concrete defect affecting:
- Frozen visual fidelity;
- motion quality;
- runtime stability;
- rendering performance;
- evidence truthfulness.

When all required gates pass and no concrete Frozen-fidelity defect remains:
- stop modifying implementation;
- do not refactor for cleanliness;
- do not add extra diagnostics;
- merge;
- run final main validation;
- record final main SHA / run / artifacts.
