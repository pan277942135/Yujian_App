# Normal Home Visual Spec V1

## Frozen authority

Canonical reference:

`design/system/core_visual_v1/reference/normal_home_v1.png`

SHA-256:

`6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`

Reference canvas: **1080 × 1920**.

Runtime layouts may adapt to taller screens but must preserve the frozen hierarchy and primary horizontal geometry.

## Visual hierarchy

1. BG_ENV_HERO morning-lake environment
2. real recent catch hero
3. header and contextual memory
4. summary statistics
5. section/navigation affordances
6. primary capture action

The screen must read as a natural fishing-memory home before it reads as data.

## Reference geometry

Reference-space anchors currently aligned to the approved implementation:

| Region | Reference geometry |
| --- | --- |
| Header | x 88, y 104, w 904, h 104 |
| Statistics | y 304, h 116 |
| Recent header | y 494, h 70 |
| Hero card | y 596, w 740, h 880 |
| CTA | y 1504 |
| Capture button | y 1582, visual size ≈200 |

These values are implementation-aligned traceability anchors, not permission to stretch content non-proportionally.

## Environment

Normal Home uses the shared YuJian **BG_ENV_HERO** visual language:

- quiet clear-morning lake
- distant mountain and restrained mist
- low-saturation blue-gray water/sky
- scarce warm gold
- no night HUD
- no strong HDR
- no tourism-poster sunlight

### Background authority resolution (V1.1)

- Page composition and hierarchy: `design/system/core_visual_v1/reference/normal_home_v1.png`.
- Background bitmap: `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` (941 × 1672; SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`).
- Runtime source is copied byte-for-byte; Android uses centered `ContentScale.Crop` for the viewport. The bitmap is not recolored or reconstructed.
- A crop or reconstruction from a page screenshot is forbidden.

## Hero catch card

Use the shared `YuJianCatchHeroCard / HOME` family.

Required:

- actual catch media
- fish remains first visual subject
- restrained bottom readability treatment
- large soft radius consistent with shared system
- contextual metadata remains subordinate to fish

Forbidden:

- solid black information strip
- confidence badge pile
- gamified rarity treatment
- metallic/CG fish replacement
- screenshot-derived card bitmap

## Statistics

Statistics are secondary and visually quiet:

- three equal semantic groups
- subtle separators
- data-number typography
- labels lower contrast than values
- no oversized dashboard cards

## Capture action

Use shared `YuJianPrimaryCaptureButton`:

- white solid core
- thin MorningGold rim
- deep blue-gray camera glyph
- restrained highlight
- no glass-ball treatment

## Adaptive behavior

On taller aspect ratios:

- preserve the reference hierarchy and card proportions
- use additional breathing room rather than vertically stretching the card
- keep safe system insets
- never compress the page into a dense dashboard

## Gold restraint

Gold is reserved for brand/meaningful emphasis and capture detail. Do not apply gold to every statistic, label, border or icon.
