# AI Edge Field V1 — Motion Contract

Status: **FROZEN**

Scope: **SegmentOffset / StateStrength / ResolveStrength**.

This contract turns the frozen Static Shape and Rendering Contract into one continuous motion system.

It does not redefine geometry.

## 1. Three independent inputs

The renderer receives three conceptually separate motion inputs:

### SegmentOffset

Answers:

> Where is the visible luminous segment on its path right now?

It changes position only.

It does not decide brightness.

### StateStrength

Answers:

> How visually present should the entire AI Edge Field be in this product state?

It changes visual weight only.

It does not move the segment.

### ResolveStrength

Answers:

> How much of the AI Edge Field remains during Result handoff?

It is normally 1.0.

It only changes during the final 200ms Resolve.

These inputs must not be collapsed into one “animation progress” value.

## 2. SegmentOffset

### Base clock

Base cycle:

**10,000ms**

The clock is continuous across all three Processing states.

There is no:

- state-local restart;
- state-local 0ms;
- jump to a new seed;
- synchronized ring pulse.

### Formula

Conceptually:

```text
offset_i(t)
=
fract(
  seed_i
  +
  ∫ speed(state(t)) / 10000 dt
)
```

Exact numerical integration method is platform-specific.

The perceptual requirement is continuity.

### Primary seeds

| Island | Path | Seed | Direction |
| --- | --- | ---: | --- |
| B_UR | B1 | 0.00 | path-forward |
| G_UL | G1 | 0.12 | path-forward |
| B_LR | B3 | 0.47 | path-forward |
| G_LL | G3 | 0.64 | path-forward |

Each path's own Bézier orientation defines its movement.

Do not reinterpret the four islands as one clockwise or counter-clockwise perimeter flow.

### Visible segment length

| Island | Visible ratio of its own Primary path |
| --- | ---: |
| B_UR | 0.30 |
| G_UL | 0.26 |
| G_LL | 0.22 |
| B_LR | 0.20 |

The dominant B_UR has the longest readable segment.

The weakest B_LR remains shortest.

### Segment speed by state

| State | Speed multiplier |
| --- | ---: |
| 图片识别中 | 1.00 |
| 已定位到鱼体 | 0.62 |
| 鱼种识别中 | 0.42 |
| RESOLVE | 0.00 / freeze current offset |

Speed changes are interpolated, never stepped.

Easing:

`cubic-bezier(0.22, 1, 0.36, 1)`

Transition durations:

- 图片识别中 → 已定位到鱼体: 260ms;
- 已定位到鱼体 → 鱼种识别中: 280ms.

## 3. Hairline movement

Hairlines do not own independent clocks.

They inherit the local Primary normalized t and apply a fixed path-local lead / lag.

### Companion phase delta

| Island | Delta from Primary |
| --- | ---: |
| B_UR | -0.055 |
| G_UL | +0.065 |
| B_LR | -0.060 |
| G_LL | +0.050 |

This creates a local echo rather than a second animation.

### B_UR Micro Hairline

```text
Micro t = Primary t + 0.085
```

The Micro Hairline remains shorter and lower weight.

It never becomes a second moving focal point.

## 4. StateStrength

Frozen targets:

| Product state | StateStrength |
| --- | ---: |
| 图片识别中 | 1.00 |
| 已定位到鱼体 | 0.58 |
| 鱼种识别中 | 0.36 |

Interpretation:

### 图片识别中 · 1.00

The photo is still dominant, but AI Edge Field is the main AI visual language because Fish Focus does not yet exist.

### 已定位到鱼体 · 0.58

The field yields to the fish.

This is a deliberate visual priority transfer:

```text
Edge Field ↓
Fish Focus ↑
```

### 鱼种识别中 · 0.36

Peripheral activity becomes quiet.

The user should still feel that AI is working, but the Edge Field must not compete with the fish contour / halo.

## 5. Initial StateStrength ramp

On first appearance:

```text
StateStrength 0.00 → 1.00 over 420ms
```

Easing:

`cubic-bezier(0.22, 1, 0.36, 1)`

Layer staging:

| Time | Layer group |
| --- | --- |
| 0–180ms | Receiving Light |
| 80–320ms | Primary Bloom / Mid / Core |
| 180–420ms | Hairline / Hot Node / Particles |

The groups overlap intentionally.

The field should feel as though the photograph begins to receive AI light, then reveals energy detail.

It must not appear as a completed graphic in one frame.

## 6. StateStrength interpolation

### To 已定位到鱼体

```text
1.00 → 0.58 over 260ms
```

### To 鱼种识别中

```text
0.58 → 0.36 over 280ms
```

The interpolation affects all Edge Field layers through the shared alpha formula.

It does not:

- reset the clock;
- change island geometry;
- change Quiet Gaps;
- create or destroy Primary paths.

## 7. ResolveStrength

Outside Resolve:

`ResolveStrength = 1.0`

During final 200ms:

```text
p = elapsed / 200ms
ResolveStrength = (1 - p)^2
```

At Resolve start:

- freeze SegmentOffset;
- freeze Hairline derived offsets;
- stop Node travel;
- stop Particle travel.

Then fade all Edge Field layers using the same ResolveStrength:

- Receiving Light;
- Outer Bloom;
- Mid Glow;
- Primary Core;
- Hairline;
- Hot Node;
- Particles.

This prevents “lingering glow” after the Result UI takes over.

## 8. Secondary motion hierarchy

Only Primary segments own continuous directional motion.

Everything else is subordinate:

### Companion / Micro Hairline

Derived from Primary offset.

### Hot Node

Attached to the currently active Primary segment.

It may breathe slightly in alpha but does not roam independently.

### Particles

May use slow local drift around fixed anchors.

They never define the main direction of motion.

This implements the product rule:

> one readable motion language, not several competing animation systems.

## 9. Long-hold behavior

When a Processing state remains longer than its minimum:

- SegmentOffset continues at that state's speed;
- StateStrength remains at that state's target;
- no new loading copy is introduced;
- no extra pulse event is triggered;
- no periodic “attention burst” occurs.

The effect should remain calm even during a long inference.

## 10. Hard failures

FAIL if:

1. state transition restarts the path clock;
2. luminous segments jump position during a state transition;
3. all four islands pulse together;
4. path travel creates an apparent clockwise/counter-clockwise frame;
5. Hairline runs on its own unrelated clock;
6. StateStrength changes instantly with no interpolation;
7. 鱼体定位后 Edge Field remains as visually strong as 图片识别中;
8. RESOLVE continues moving while fading;
9. Glow / particles remain visible after Resolve;
10. long-hold mode adds repeated pulse bursts to look “busy”.

## 11. Frozen handoff table

| Input | 图片识别中 | 已定位到鱼体 | 鱼种识别中 | RESOLVE |
| --- | ---: | ---: | ---: | ---: |
| StateStrength | 1.00 | 0.58 | 0.36 | × Resolve |
| SegmentSpeed | 1.00 | 0.62 | 0.42 | 0 / frozen |
| ResolveStrength | 1.00 | 1.00 | 1.00 | (1-p)^2 |
| Clock reset | never | never | never | no |
| Hairline clock | derived | derived | derived | frozen |

This table is the primary implementation handoff for AI Edge Field motion.
