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

## 7. Composite stroke system

AI Edge Field is **not** made of single hairline strokes.

Each visible filament is a layered energy band composed from the same path geometry:

| Layer | Width | Role |
| --- | ---: | --- |
| Outer Bloom | 10dp | broadest, weakest atmospheric diffusion |
| Mid Glow | 4dp | soft luminous body |
| Core | per-path | thin hot center that preserves path identity |

The three layers belong to **one filament**, not three parallel filaments.

### Per-path Core widths

Primary islands:

| Path | Core width | Role |
| --- | ---: | --- |
| G1 | 1.1dp | upper-left gold primary |
| B1 | 1.2dp | upper-right blue primary |
| B3 | 1.2dp | lower-right blue primary |
| G3 | 1.2dp | lower-left gold primary |

Secondary fragments:

| Path | Core width | Role |
| --- | ---: | --- |
| B2 | 1.0dp | right-mid secondary fragment |
| G2 | 0.9dp | left-mid secondary fragment |
| B4 | 0.8dp | bottom secondary fragment |

Frozen rules:

- Core width is path-specific and must not be normalized to one universal width.
- Outer / Mid / Core share the same path centerline.
- Outer Bloom and Mid Glow are glow layers, not additional neighboring strands.
- Secondary fragments may retain the same composite-layer structure, but their overall visual weight remains substantially below Primary islands.
- Bloom stacking must not make every path appear equally thick.
- A representative still should read as **layered luminous energy bands with subtle thickness variation**, not seven identical glowing strings.

## 8. Energy-island internal structure

Each Primary Energy Island is **not** a single isolated filament.

The frozen structure is:

> **1 Primary Energy Band + 1 Companion Hairline**

Only the upper-right blue island `B_UR` may add one extra **Micro Hairline**.

This gives the AI field layered visual intelligence without turning every island into the same repeated template.

### Per-island composition

| Island | Frozen composition | Emphasis |
| --- | --- | --- |
| G_UL | 1 Primary + 1 Companion Hairline | medium |
| B_UR | 1 Primary + 1 Companion Hairline + 1 Micro Hairline | highest |
| B_LR | 1 Primary + 1 Companion Hairline | medium-low |
| G_LL | 1 Primary + 1 Companion Hairline | medium |

`B_UR` is the only island allowed to contain two companion traces. This asymmetry is intentional.

### Companion Hairline contract

A Companion Hairline is a **local echo** of the Primary, not a second Primary.

- Core width: **0.50–0.65dp**
- Local Glow: **1.8–2.4dp**
- Outer Bloom: **none**
- Visual weight: **0.22–0.35 × local Primary**
- Visible length: **25%–55%** of the local Primary island span
- Centerline separation: **4–10dp**, variable along the curve

Geometry rules:

- start and end points must be offset from the Primary;
- Companion and Primary must not begin and end together;
- the Hairline follows the same broad directional flow but has a slightly different local curvature;
- separation must vary — constant parallel spacing is forbidden;
- a Hairline should usually stay on one local side of the Primary rather than repeatedly crossing it.

### B_UR Micro Hairline

Only `B_UR` may contain one additional Micro Hairline:

- Core width: **0.35–0.50dp**
- Local Glow: **1.2–1.8dp**
- Outer Bloom: **none**
- Visual weight: **0.12–0.22 × local Primary**
- Visible length: **15%–32%** of the local Primary island span
- Centerline separation: **7–14dp**, variable

It must be noticeably shorter and quieter than the Companion Hairline.

### Hard composition rules

- Primary is always the only dominant line inside an island.
- Hairlines must be shorter, thinner and dimmer.
- Hairlines never use the Primary's 10dp Outer Bloom.
- Constant parallel spacing is forbidden; no railway / double-track appearance.
- The four islands must not have identical internal complexity.
- No island except B_UR may display two Hairlines.
- A Hairline may not cross a mandatory Quiet Gap to connect two islands.
- A Hairline may not become a new fifth Energy Island.

## 9. Internal visual hierarchy

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

## 10. Color ownership

Primary color distribution is asymmetric but stable:

- GOLD owns the left-side energy identity;
- BLUE owns the right-side energy identity.

Secondary fragments may mix subtly, but the frame must not become a repeated blue/gold alternating perimeter.

The purpose is to create a calm directional field, not decorative RGB edging.

## 11. Asymmetry contract

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

## 12. Hard failures

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

## 13. Existing visual references

Perceptual reference:

- `design/pages/recognition/design/02_AI_Understanding_Frozen.png`
- `design/pages/recognition/design/01_Capture_Transition_Frozen.png`

Engineering infographic:

- `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png`

These images help communicate visual feeling, but **this Static Shape Spec is the topology authority** whenever older reference images can be interpreted as a continuous perimeter.

## 14. Existing path identity

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

## 15. Acceptance question

Freeze a representative frame and ask only one question:

> Does this look like four local energy islands growing from the photograph edges, with obvious quiet gaps between them?

If the answer is “it looks like a frame, ring, orbit, scanner, or decorated border,” the Static Shape fails.

## 16. What is not frozen here

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


## HarmonyOS official-reference adoption

Official HarmonyOS UI Design Kit describes a UI light-field family that includes **edge flowing light, background flowing light, and luminous receiving-light effects**. HarmonyOS' current immersive-light design language also emphasizes simulated light propagation/reflection, transparency, layering, and restrained motion.

AI Edge Field V1 does **not** copy HarmonyOS component styling. It absorbs the following higher-level optical rules.

### Adopted rule A · Light field before line

The viewer should first perceive a **local light field**, and only then notice the bright Primary Core and Hairline detail.

Fail:

> “I see several glowing wires around the photo.”

Pass:

> “The edge of the photograph is locally illuminated; inside that light field are finer energy traces.”

### Adopted rule B · Local receiving light

Each Energy Island owns a subtle receiving-light zone on the underlying photo.

- depth: approximately 8–18% from the nearest edge;
- very soft falloff;
- low intensity;
- local only;
- must preserve skin tone, fish color, water/sky color and scene contrast.

No full-frame blue/gold wash is allowed.

### Adopted rule C · One dominant light moment

The Static Master does not make all four islands equally bright.

Frozen peak hierarchy:

| Island | Relative peak |
| --- | ---: |
| B_UR | 1.00 |
| G_UL | 0.72–0.82 |
| G_LL | 0.58–0.68 |
| B_LR | 0.48–0.60 |

B_UR is the only dominant light field.

### Adopted rule D · Soft physical layering

Each island should read as an optical stack:

1. Local Receiving Light
2. Outer Bloom
3. Mid Glow
4. Primary Core
5. Hairline detail

The stack should feel soft and spatial, not like a digital outline.

### Adopted rule E · Point-light as accent

A light node is a local peak inside the energy field, not a decorative bulb.

- B_UR: one main Hot Node permitted;
- whole screen: at most one additional weak secondary node;
- nodes must not be evenly spaced.

### Adopted rule F · Content remains dominant

The captured fishing photo remains the main visual surface.

The light field must enhance Recognition atmosphere without replacing the photo's natural color, contrast or subject readability.

### HarmonyOS concepts intentionally NOT adopted

- full-background flowing light across the entire photograph;
- closed component-border illumination;
- symmetric dual-edge treatment;
- press-response material effects.

Those behaviors make sense for system components but conflict with YuJian Recognition's documentary-photo priority.

## Static Master optical stack

From broadest / softest to finest / hottest:

| Layer | Frozen role |
| --- | --- |
| Local Receiving Light | 8–18% edge depth, very soft, very low intensity |
| Outer Bloom | 10dp atmospheric diffusion |
| Mid Glow | 4dp luminous body |
| Primary Core | path-specific 0.8–1.2dp hot ridge |
| Hairline | 0.35–0.65dp local high-frequency detail |

Perceptual acceptance:

> First read = local edge illumination and directional energy.  
> Second read = Primary Core / Hairline detail.

