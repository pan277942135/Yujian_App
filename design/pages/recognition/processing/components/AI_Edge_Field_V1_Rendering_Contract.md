# AI Edge Field V1 — Rendering Contract

Status: **FROZEN**

Scope: **cross-platform rendering architecture and draw contract**.

This document exists because visual references alone are insufficient. Its purpose is to make Android / iOS / HarmonyOS render the same visual system instead of independently interpreting the design.

It does **not** freeze motion timing. State timing, segment travel and transition curves belong to `04 · Motion & Transition`.

## 1. Implementation model

AI Edge Field V1 is a **single full-photo overlay renderer**.

```text
Original Photo
    ↓
AI Edge Field Overlay
    ↓
Fish Focus
    ↓
Status Overlay / Back
    ↓
System UI
```

Do not implement the four corners as four independent UI widgets.

All AI Edge Field geometry is rendered in one coordinate space attached to the visible photo viewport.

## 2. Coordinate contract

Geometry source uses normalized coordinates:

```text
x = 0..1
y = 0..1
```

The normalized space maps to the **actual visible photo viewport after production crop/scale has been resolved**.

Rules:

- geometry follows the displayed photograph, not the raw image pixel size;
- system insets do not remap the AI field;
- the renderer must not use independent per-corner layout constraints;
- all islands, gaps, nodes and particles share one transform.

This prevents the effect from drifting when the image crop or device aspect ratio changes.

## 3. Data is shared; renderer is platform-specific

Cross-platform shared source:

- Primary paths: G1 / B1 / B3 / G3;
- secondary fragment seeds: B2 / G2 / B4;
- Quiet Gap ranges;
- per-path Core widths;
- Companion / Micro Hairline geometry;
- color and alpha tokens;
- island balance multipliers;
- particle and node anchors.

Platform-specific code only converts those inputs into native drawing calls.

Target renderer families:

- Android: Compose Canvas / DrawScope / Path;
- iOS: SwiftUI Canvas / CGPath;
- HarmonyOS: ArkUI Canvas / Path / drawing effects.

The platform renderer may differ. The geometry, optical stack and visual hierarchy may not.

## 4. Mandatory draw order

### 4.1 Local Receiving Light

Render first.

Each island receives one broad, very soft local illumination zone on the photo side of its Primary path.

- depth: approximately 8–18% from nearest edge;
- alpha: approximately 0.03–0.07 before global multipliers;
- soft falloff;
- local only.

Do not implement Receiving Light as a global photo tint.

Do not use four visible circular corner glows.

The receiving light should look like the photograph is **being illuminated**, not covered by a colored translucent layer.

### 4.2 Primary Outer Bloom

Draw only the currently visible Primary segment.

- width: 10dp;
- alpha: 0.025–0.055 before multipliers;
- same centerline as Mid / Core.

Never draw a full perimeter skeleton.

### 4.3 Primary Mid Glow

On the exact same centerline:

- width: 4dp;
- alpha: 0.05–0.10 before multipliers.

Do not offset it into a neighboring line.

### 4.4 Primary Core

On the exact same centerline:

| Path | Core width |
| --- | ---: |
| B1 | 1.2dp |
| B3 | 1.2dp |
| G3 | 1.2dp |
| G1 | 1.1dp |
| B2 | 1.0dp |
| G2 | 0.9dp |
| B4 | 0.8dp |

Core alpha:

- normal: 0.20–0.46;
- local hot peak: <= 0.50.

Do not normalize all paths to one width.

### 4.5 Hairline Echo

Every island may show one Companion Hairline.

Only B_UR may add one Micro Hairline.

Companion:

- Core: 0.50–0.65dp;
- Local Glow: 1.8–2.4dp;
- no 10dp Outer Bloom;
- visual weight: 0.22–0.35 × local Primary;
- visible length: 25–55% of local Primary island span;
- variable separation: 4–10dp.

Micro, B_UR only:

- Core: 0.35–0.50dp;
- Local Glow: 1.2–1.8dp;
- no Outer Bloom;
- visual weight: 0.12–0.22 × local Primary;
- visible length: 15–32%;
- variable separation: 7–14dp.

Hairline rules:

- never same start and end as Primary;
- never constant parallel spacing;
- never cross a Quiet Gap to connect islands;
- never become another Primary line.

### 4.6 Hot Node

A Hot Node is attached to a local brightness peak of the active Primary segment.

- B_UR: maximum 1 main node;
- full screen: maximum 1 additional weak node.

Do not distribute nodes evenly.

### 4.7 Sparse Particles

Particles are rendered last inside AI Edge Field.

The renderer owns the fixed anchor set. Motion/alpha decides which anchors are perceptible at a given moment.

The design contains 8 normal anchors, but the final still must not read as eight equally visible points.

Particles are atmospheric detail only.

## 5. Critical segment rule

A Primary path is **trajectory data**, not a line that must be fully painted.

At any frame, only one local luminous segment of a Primary island is rendered.

Target visible segment:

```text
18–35% of that path's own trajectory length
```

The rest of the path must not become a visible border.

This rule is one of the most important implementation requirements in the component.

### Optional faint skeleton

Production visual should not depend on a full-path skeleton.

If a platform uses a faint internal carrier for technical reasons:

- it must remain inside the island;
- relative visual weight < 0.10 of Primary;
- it must not reconnect Quiet Gaps;
- it must disappear when it begins to read as a border.

## 6. One alpha formula

Do not tune every layer independently until they “look roughly right”.

Use one multiplicative strength chain:

```text
FinalLayerAlpha
=
BaseLayerAlpha
× IslandWeight
× StateStrength
× ResolveStrength
× QualityStrength
```

Island weights:

| Island | Static relative peak |
| --- | ---: |
| B_UR | 1.00 |
| G_UL | 0.72–0.82 |
| G_LL | 0.58–0.68 |
| B_LR | 0.48–0.60 |

This prevents all four islands from drifting toward equal brightness during implementation.

## 7. Inputs owned by other specs

Rendering Contract consumes these values but does not define their timing.

### StateStrength

Owner: `04 · Motion & Transition`

Direction:

```text
图片识别中      strongest edge field
已定位到鱼体    reduced
鱼种识别中      further reduced
```

### Clock / SegmentOffset

Owner: `04 · Motion & Transition`

It changes where the luminous segment sits on the path.

It does not change island topology.

### ResolveStrength

Owner: `04 · Motion & Transition`

One common multiplier fades the whole Edge Field during Result handoff.

### QualityLevel

Owner: `05 · Degradation & Accessibility`

Quality degradation may remove detail, but must preserve:

- four-island identity;
- Quiet Gaps;
- Primary geometry;
- photo dominance.

### ReduceMotion

Owner: `05 · Degradation & Accessibility`

Reduce Motion stops continuous travel.

It does **not** remove the static AI Edge Field completely.

## 8. Minimum viable renderer

Before Motion is implemented, the team must be able to render a valid static frame using only:

```text
Photo
+ Receiving Light
+ Primary Outer
+ Primary Mid
+ Primary Core
```

If that base frame already looks like a neon border, Motion will not fix it.

Hairlines, nodes and particles are enhancements, not the foundation.

Implementation order:

```text
1. Correct photo viewport transform
2. Four Primary island paths
3. Quiet Gaps
4. Receiving Light
5. Composite Primary stroke
6. Hairline
7. Node
8. Particles
9. Motion
```

Do not start by implementing particles or moving dashes.

## 9. Debug mode

A development-only debug overlay may expose:

- island ID;
- path ID;
- full trajectory;
- luminous segment bounds;
- Quiet Gap regions;
- photo viewport bounds.

This overlay must never ship in production.

Debug is used to verify geometry; visual review must always use Debug OFF.

## 10. Visual diagnosis

### Symptom: looks like a frame / orbit

Check in this order:

1. Is a full path being painted?
2. Are B2 / G2 / B4 too visible?
3. Are Quiet Gaps being crossed?
4. Are all islands equally bright?
5. Is Outer Bloom too strong?
6. Are Hairlines reconstructing the missing border?

### Symptom: looks like thin wires

Check:

1. Is Receiving Light missing?
2. Is Mid Glow missing?
3. Is Outer Bloom missing?
4. Is Core too bright relative to Glow?
5. Are Hairlines too numerous?

### Symptom: looks like fantasy lightning

Check:

1. Too many Hairlines;
2. too much curvature noise;
3. nodes too bright;
4. particles too visible;
5. local glow too sharp;
6. multiple lines competing as Primary.

### Symptom: effect is barely visible

Increase in this order:

1. Local Receiving Light;
2. Primary Mid Glow;
3. Primary Core;
4. only then Node / Particle.

Do not solve weak visibility by adding more lines.

## 11. Cross-platform acceptance

Use the same test photograph and fixed visual clock.

Android / iOS / HarmonyOS should agree on:

- island positions;
- Quiet Gaps;
- Primary geometry;
- Core width hierarchy;
- dominant B_UR balance;
- receiving-light footprint;
- Hairline count;
- overall photo dominance.

Pixel-perfect blur is not required across GPU stacks.

Perceptual hierarchy and topology are required.

## 12. Automatic failure conditions

FAIL if any implementation does one of the following:

1. uses one full-frame special-effect PNG/GIF as the production Edge Field;
2. builds four corners as independent UI widgets;
3. keeps all seven paths continuously visible;
4. draws a persistent perimeter skeleton;
5. makes Primary / Hairline constant-parallel tracks;
6. offsets Glow from Core centerline;
7. makes four islands equal brightness;
8. applies a full-frame blue/gold tint or blur;
9. makes particles/nodes the dominant visual;
10. draws Fish Halo / Fish Contour inside the AI Edge Field renderer.

## 13. Handoff rule

The implementation team should never be asked:

> “Can you make it more like the reference?”

They should be able to answer each mismatch by naming one contract layer:

```text
Viewport
Island Geometry
Receiving Light
Outer Bloom
Mid Glow
Core
Hairline
Node
Particle
State Strength
```

That is the purpose of this Rendering Contract.
