# Recognition Result Candidate Card V1

Status: **FROZEN**

Scope: Medium-confidence candidate confirmation only.

Machine-readable authority: `candidate_card_contract.json`.

## 1. Candidate count

- Medium displays **2 or 3** distinct species candidates.
- Maximum visible candidates: **3**.
- If prediction exposes >3, use top 3 by model order.
- Never fabricate a candidate to fill a slot.
- A Medium state with <2 valid candidates is an upstream state-contract issue; do not visually fake Medium.

## 2. Row

Canonical 360dp:
- content width: 320dp
- gap: **8dp**
- 3-card width: **101.3dp**
- row height: **112dp**

Adaptive width is defined by Layout Geometry V1:
`cardWidth = (contentWidth - 16dp) / 3`, clamped **88–116dp**.

For exactly 2 candidates:
- card width max **136dp**
- centered row
- 8dp gap

Normal font scale: no carousel, no snap, all candidates visible.

At accessibility font scale where text no longer fits without clipping:
- use horizontally scrollable fixed-width **104dp** cards;
- no automatic scroll;
- no snapping;
- selected card may be brought into view only after user action.

## 3. Card visual

Geometry:
- height: **112dp** canonical; **108dp** on 320dp width
- radius: **16dp**
- internal padding: **10dp**
- media area: **48 × 48dp** (44dp on 320dp)
- media → label gap: **8dp**
- species label: 14sp / 20sp Medium, max 1 line, ellipsis

Candidate media:
1. approved species `cover_image` / Fish Guide species media keyed by species id;
2. approved neutral system fish silhouette/placeholder when media is unavailable;
3. never substitute another species image;
4. never use the generic text character `鱼` as the final production thumbnail;
5. ContentScale = Fit.

## 4. State model

### Default
- surface: GLASS_A-light / LakeWhite family
- border: 1dp neutral light edge
- species label: TextPrimary

### Suggested
Initial Top-1 model suggestion only.
- **not selected**
- border: 1dp teal at ~28% emphasis
- surface remains neutral
- no check icon
- no confidence percentage
- accessibility state includes `模型建议`

Suggested state is visually weaker than Selected.

### Selected
Established only by explicit user tap or shared species selector return.
- border: **2dp #0F7A78**
- fill: teal tint approximately **8%**
- species label remains DeepLakeBlue
- no gold
- no rarity language
- accessibility state: selected=true

### Pressed
- preserve current semantic state
- scale: **0.985**
- duration: shared quick press transition
- no glow

### Disabled
Only when interaction must be temporarily blocked, e.g. saving.
- whole card opacity: **0.42**
- selected identity remains semantically recoverable
- no tap

### Focus
- external focus outline: **2dp #0F7A78**
- external gap: **2dp**

## 5. Interaction

Initial:
- Top-1 = Suggested
- Selected = none

Tap a candidate:
1. set Selected to tapped candidate;
2. clear prior Selected;
3. model Suggested identity may remain known internally, but Selected styling takes priority on the same card;
4. enable resolved-species flow.

Tap another candidate:
- selection moves immediately;
- no confirmation dialog.

`都不是？选择其他鱼种 ›`:
- opens shared species selector;
- returned species becomes Selected/resolved;
- if returned species is not among the three visible cards, no visible card is falsely marked Selected; resolved identity is shown in the subsequent recording section.

## 6. Candidate ordering

- preserve model candidate order;
- never re-sort alphabetically;
- do not reorder after a tap;
- do not auto-cycle/highlight.

## 7. Accessibility

Each card exposes:
- species name;
- Button/Radio-like selectable semantics;
- Suggested when applicable;
- Selected when applicable.

Touch target equals full card.

## 8. Prohibited

- Fish Guide carousel component reuse;
- rarity/star/confidence bars;
- percentage;
- auto-carousel;
- auto-selection of Top-1;
- gold Selected styling;
- text-only `鱼` placeholder as final UI;
- another species' artwork as fallback;
- selected animation that changes row geometry.
