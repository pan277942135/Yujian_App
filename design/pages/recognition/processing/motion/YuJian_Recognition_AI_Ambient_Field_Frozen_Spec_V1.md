# YuJian Recognition AI Ambient Field — Frozen Spec V1

> **V1.1 supersession notice**  
> This file remains authoritative for AI ambient-field colors, alpha ranges, fixed paths, filament geometry and degradation principles.  
> Its legacy phase-duration / 900ms compression section is **not** the V1.1 runtime timing authority.  
> Use `Recognition_Processing_Motion_Spec_V1_1.md` for current timing: 350/600/600/1250ms, nominal 2800ms, resolve fade 200ms.

This checked-in source records the supplied Frozen V1 contract. Numeric values below take precedence over the accompanying engineering infographic.

## Visual tokens

| Token | Value |
| --- | --- |
| AI_BLUE_CORE | `#BFEAF2` |
| AI_BLUE_HOT | `#D8F5FA` |
| AI_GOLD_CORE | `#F6D99B` |
| AI_GOLD_HOT | `#FFE7AE` |
| STATUS_BG | `#1A2C35 @ 0.85` |
| STATUS_TEXT_SECONDARY | `#BFD0D7` |

Core alpha is `0.20–0.46`; hot points `<=0.50`; mid bloom `0.05–0.10`; outer bloom `0.025–0.055`; halo `0.10–0.18`; contour `0.30–0.42`.

## Runtime presentation

The historical V1 phase durations and 900ms fast-result compression are retired and intentionally
removed from the active contract to prevent automated tooling from treating them as valid values.

Current runtime timing authority:
`Recognition_Processing_Motion_Spec_V1_1.md`

Active V1.1 presentation:
- CAPTURED 350ms
- DETECTING 600ms
- OUTLINE 600ms
- CLASSIFYING 1250ms
- nominal total 2800ms
- resolve fade 200ms

The real pipeline still gates visual advancement; presentation never invents a later semantic phase.

## Fixed paths and motion

Exactly seven cubic Bézier paths are used: blue `B1(.78,-.02;.94,.06;1.02,.22;.96,.42)`, `B2(1.01,.18;.92,.31;.95,.52;1.02,.68)`, `B3(1.02,.61;.92,.75;.84,.89;.66,1.02)`, `B4(.22,1.02;.38,.95;.52,.95;.66,1.01)`; gold `G1(-.02,.18;.03,.08;.12,.02;.26,-.02)`, `G2(-.02,.33;.04,.47;.02,.62;-.01,.78)`, `G3(-.01,.74;.08,.86;.20,.95;.38,1.02)`.

Loop: 10,000ms, linear travel, visible segment length `0.18–0.35`, offsets B1/B2/B3/B4 = `0/.23/.47/.71`, G1/G2/G3 = `.12/.39/.64`. Multi-stroke bloom: core `1.2dp × 1.0`; mid `4dp × .22`; outer `10dp × .08`.

## Fallback and performance

Fish focus levels: A=contour+halo; B=halo when segmentation is unavailable; C=bbox-center halo under visual degradation. Segmentation must never affect classification. Screenshot mode uses fixed visual clock and fixed particle anchors. Low-performance mode reduces particles `8→4`, reduces outer bloom 20%, and may use Level B.
