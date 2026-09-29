# AI Edge Field V1 — Static Shape Spec

Status: **FROZEN**

Scope: **static visual topology only**.

This document defines what the AI effect around the Recognition photograph must look like when frozen at any representative instant. It intentionally does **not** define motion timing, speed, easing, state intensity curves, runtime implementation, or performance behavior.

## 1. Component identity

**AI Edge Field V1** is an independent visual component shared across all three Recognition Processing states:

- 图片识别中
- 已定位到鱼体
- 鱼种识别中

It owns:

- Edge Ambient Bloom
- Primary Filaments
- Secondary Hairlines
- Energy Nodes
- Sparse Particles

It does **not** own:

- Fish Halo
- Fish Contour
- Status Overlay
- Result UI

## 2. One-line visual definition

> AI Edge Field 不是围绕照片的一圈边框，而是从照片边缘局部生长出来的 **4 个断开的蓝 / 金能量岛**。

At a glance, the frame must read as:

```text
Gold fragment                     Blue fragment
     ╲                               ╱
      ╲                             ╱

          [ large quiet photo area ]

      ╱                             ╲
Gold fragment                     Blue fragment
```

The empty space between islands is part of the design and is mandatory.

## 3. Primary island topology

### G_UL · 左上金色岛

- family: GOLD
- anchor: top-left
- source geometry: G1
- active zone:
  - top: x = 0.00–0.30
  - left: y = 0.00–0.24
- role: primary

It should feel like a warm energy trace entering from the upper-left edge, not a corner ornament.

### B_UR · 右上蓝色岛

- family: BLUE
- anchor: top-right
- source geometry: B1
- active zone:
  - top: x = 0.72–1.00
  - right: y = 0.00–0.42
- role: primary

This is normally one of the clearest AI signatures in the opening Recognition state.

### B_LR · 右下蓝色岛

- family: BLUE
- anchor: lower-right
- source geometry: B3
- active zone:
  - right: y = 0.62–1.00
  - bottom: x = 0.64–1.00
- role: primary

It must remain visually disconnected from B_UR.

### G_LL · 左下金色岛

- family: GOLD
- anchor: lower-left
- source geometry: G3
- active zone:
  - left: y = 0.70–1.00
  - bottom: x = 0.00–0.38
- role: primary

It must remain visually disconnected from G_UL.

## 4. Mandatory Quiet Gaps

The gaps are not accidental empty space. They prevent the component from becoming a border or orbit.

### Q_TOP

- top x = 0.32–0.68
- no persistent primary filament

### Q_LEFT

- left y = 0.38–0.62
- no continuous connection between upper-left and lower-left islands

### Q_RIGHT

- right y = 0.46–0.58
- no continuous connection between upper-right and lower-right islands

### Q_BOTTOM

- bottom x = 0.40–0.62
- must preserve a clear center-bottom break

If a still frame reads as one continuous perimeter, it fails Static Shape even if every individual path coordinate is technically correct.

## 5. Secondary fragments

Legacy paths B2 / G2 / B4 remain available as **secondary fragment zones only**.

They are no longer allowed to behave like persistent perimeter strokes.

### B2 · right-mid

Allowed:

- short broken fragment;
- hairline echo;
- very low-weight local response.

Forbidden:

- visually connecting B_UR to B_LR.

### G2 · left-mid

Allowed:

- weak short response;
- occasional hairline texture.

Forbidden:

- visually connecting G_UL to G_LL.

### B4 · bottom

Allowed:

- short fragment near the bottom edge.

Forbidden:

- filling Q_BOTTOM;
- creating a continuous bottom border.

## 6. Edge-depth contract

Primary filament centerlines belong to the outer edge band.

- target centerline depth: nearest-edge distance ≈ 0–12% of frame dimension;
- low-alpha bloom may diffuse to ≈ 18%;
- the central photograph remains visually clean;
- AI Edge Field must not cross the fish body or central subject area.

The component should feel attached to the **edge atmosphere**, not laid on top of the subject.

## 7. Internal visual hierarchy

Normalize Primary Filament = 1.00.

| Layer | Maximum relative visual weight |
| --- | ---: |
| Primary Filament | 1.00 |
| Secondary Hairline | <= 0.35 |
| Energy Node | <= 0.30 |
| Edge Bloom | <= 0.25 |
| Sparse Particle | <= 0.20 |

This table freezes hierarchy, not opacity implementation.

The important rule is:

> no secondary layer may become visually stronger than the primary filament structure.

## 8. Color ownership

Primary color distribution is asymmetric but stable:

- GOLD owns the left-side energy identity;
- BLUE owns the right-side energy identity.

Secondary fragments may mix subtly, but the frame must not become a repeated blue/gold alternating perimeter.

The purpose is to create a calm directional field, not decorative RGB edging.

## 9. Asymmetry contract

Static Shape must remain intentionally irregular.

Required:

- different island lengths;
- different brightness peaks;
- different local curvature;
- non-mirrored left/right composition;
- non-equal four-corner emphasis.

Forbidden:

- four equally bright corners;
- bilateral symmetry;
- evenly spaced energy nodes;
- repeated decorative rhythm.

## 10. Hard failures

Any of the following is an automatic Static Shape failure:

1. closed rectangle / rounded-rectangle feel;
2. closed ellipse / orbital-ring feel;
3. visually continuous top, left, right or bottom border;
4. primary filament through the center of the photo;
5. four equally strong corners;
6. full-frame blue/gold wash;
7. dense secondary hairlines reconstructing a second border;
8. evenly distributed “light bulb” energy nodes;
9. particles reading as confetti;
10. AI effect visually stronger than the captured photograph.

## 11. Existing visual references

Perceptual reference:

- `design/pages/recognition/design/02_AI_Understanding_Frozen.png`
- `design/pages/recognition/design/01_Capture_Transition_Frozen.png`

Engineering infographic:

- `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png`

These images help communicate visual feeling, but **this Static Shape Spec is the topology authority** whenever older reference images can be interpreted as a continuous perimeter.

## 12. Existing path identity

The existing normalized Bézier families remain useful geometry seeds:

Primary islands:

- G1 → G_UL
- B1 → B_UR
- B3 → B_LR
- G3 → G_LL

Secondary-only:

- B2
- G2
- B4

This is the key V1 clarification:

> all seven paths are not equal persistent primary strokes.

## 13. Acceptance question

Freeze a representative frame and ask only one question:

> Does this look like four local energy islands growing from the photograph edges, with obvious quiet gaps between them?

If the answer is “it looks like a frame, ring, orbit, scanner, or decorated border,” the Static Shape fails.

## 14. What is not frozen here

Deferred to **04 · Motion & Transition**:

- travel direction;
- speed;
- visible segment progression;
- phase offset;
- per-state intensity;
- fade / resolve;
- Reduce Motion behavior.

Deferred to a later visual asset step:

- dedicated AI Edge Field Static Master image, if a new standalone visual master is produced.

The topology defined in this document is already frozen and must govern that future visual master.
