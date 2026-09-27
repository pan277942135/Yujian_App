# YuJian Recognition AI Ambient Field — Frozen Spec V1

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

| Real phase | Minimum | Edge / Filament / Particles | Fish focus |
| --- | ---: | --- | --- |
| CAPTURED | 220ms | .20 / .15 / .05 | off |
| DETECTING | 320ms | .30 / .35 / .20 | off |
| OUTLINE | 350ms | .22 / .24 / .12 | halo + contour |
| CLASSIFYING | 250ms | .18 / .20 / .08 | .90, breathing |

The controller preserves phase order and never delays inference. After a real result it compresses remaining presentation time, bounded by `MAX_POST_RESULT_HOLD_MS = 900`.

## Fixed paths and motion

Exactly seven cubic Bézier paths are used: blue `B1(.78,-.02;.94,.06;1.02,.22;.96,.42)`, `B2(1.01,.18;.92,.31;.95,.52;1.02,.68)`, `B3(1.02,.61;.92,.75;.84,.89;.66,1.02)`, `B4(.22,1.02;.38,.95;.52,.95;.66,1.01)`; gold `G1(-.02,.18;.03,.08;.12,.02;.26,-.02)`, `G2(-.02,.33;.04,.47;.02,.62;-.01,.78)`, `G3(-.01,.74;.08,.86;.20,.95;.38,1.02)`.

Loop: 10,000ms, linear travel, visible segment length `0.18–0.35`, offsets B1/B2/B3/B4 = `0/.23/.47/.71`, G1/G2/G3 = `.12/.39/.64`. Multi-stroke bloom: core `1.2dp × 1.0`; mid `4dp × .22`; outer `10dp × .08`.

## Fallback and performance

Fish focus levels: A=contour+halo; B=halo when segmentation is unavailable; C=bbox-center halo under visual degradation. Segmentation must never affect classification. Screenshot mode uses fixed visual clock and fixed particle anchors. Low-performance mode reduces particles `8→4`, reduces outer bloom 20%, and may use Level B.
