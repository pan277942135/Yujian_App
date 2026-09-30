# Recognition Reduce Motion V1

Status: **FROZEN**

Scope: **Recognition Processing motion accessibility**.

This contract defines how Recognition behaves when the operating system's Reduce Motion preference is enabled.

Reduce Motion is not a performance mode.

It does not change Recognition quality, confidence, pipeline semantics or result semantics.

## 1. Core principle

> Preserve meaning and static visual identity. Remove continuous peripheral motion.

The Recognition experience must still clearly express:

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ Result
```

but the user should not be exposed to continuous edge travel, particle drift or periodic breathing.

## 2. Reduce Motion is orthogonal to Quality Level

Valid combinations include:

```text
FULL + Reduce Motion
BALANCED + Reduce Motion
LITE + Reduce Motion
```

Implementation order:

```text
1. Apply QualityLevel layer enable/disable
2. Apply ReduceMotion behavior overrides
```

Reduce Motion must **not** be implemented as:

`QualityLevel = LITE`

Those are different concerns.

## 3. SegmentOffset

### Normal

Primary luminous segments move continuously along their frozen paths using the 10s base clock and state speed multipliers.

### Reduce Motion

`SegmentOffset = FROZEN`

No continuous path travel occurs.

### If Reduce Motion is already enabled before Recognition opens

Use the frozen Primary seeds as the static locations:

| Island | Static seed |
| --- | ---: |
| B_UR | 0.00 |
| G_UL | 0.12 |
| B_LR | 0.47 |
| G_LL | 0.64 |

Do not start the continuous motion clock.

### If Reduce Motion becomes enabled during Processing

Freeze the **current frame's** SegmentOffset.

Do not:

- jump back to a seed;
- restart the field;
- snap to a state-specific pose.

The frozen offset remains unchanged through later Processing states.

State changes may still change StateStrength, but not position.

### If Reduce Motion becomes disabled during Processing

Resume from the current frozen offset.

Ramp motion speed from 0 to the current state's target speed over **220ms** using:

`cubic-bezier(0.22, 1, 0.36, 1)`

Do not jump immediately to full speed.

## 4. Hairline

### Normal

Companion / Micro Hairlines inherit Primary t and use frozen lead / lag offsets.

### Reduce Motion

Hairlines may remain visible according to the active Quality Level, but become **fully static**.

They do not:

- travel;
- drift;
- periodically fade in/out;
- oscillate around the Primary;
- cross from one side of the Primary to the other.

They preserve the frozen local geometry relative to the static Primary segment.

Examples:

### FULL + Reduce Motion

- Companion Hairline may remain on all four islands;
- B_UR Micro Hairline may remain;
- all are static.

### BALANCED + Reduce Motion

- B_UR / G_UL Companion Hairlines remain;
- static.

### LITE + Reduce Motion

- no Hairlines, because LITE already removes them.

## 5. Particles

### Normal

Particles may use low-amplitude local drift around deterministic anchors.

### Reduce Motion

**Particles are OFF.**

This applies regardless of FULL / BALANCED / LITE.

Do not replace particle movement with:

- blinking;
- flashing;
- opacity twinkle loops;
- repeated appearing/disappearing dots.

Particles are nonessential ambient detail and are the first motion layer to disappear for accessibility.

## 6. Hot Node

Hot Node behavior follows Quality Level visibility.

When Reduce Motion is active:

- no positional travel;
- no periodic alpha pulse;
- no breathing;
- static brightness only.

The node remains a local optical accent, not an animated event.

## 7. Fish Focus reveal

Fish Focus remains semantically important because it communicates:

> AI has located the fish.

Therefore it is **not removed** under Reduce Motion.

At entry to `已定位到鱼体`:

```text
Halo / Contour
alpha 0 → frozen static target
180ms
cubic-bezier(0.22, 1, 0.36, 1)
```

No:

- scale;
- scan;
- stroke travel;
- positional expansion.

Frozen static target:

- Halo alpha: **0.15**
- Contour Core alpha: **0.39**

These are midpoint values of the normal-mode frozen breathing ranges and avoid choosing either breathing extreme.

## 8. Fish Focus breathing

### Normal

During 鱼种识别中, Fish Focus may breathe slowly.

### Reduce Motion

**Breathing is OFF.**

After the one-time reveal:

```text
Halo alpha = 0.15
Contour Core alpha = 0.39
position = fixed
scale = fixed
```

There is no 1900ms periodic cycle.

The fish remains visually emphasized without repetitive motion.

## 9. AI Edge Field entry

Normal mode uses the frozen 420ms staged entry:

```text
Receiving Light
→ Primary
→ secondary detail
```

Reduce Motion simplifies this to one short static fade:

```text
Static Edge Field
opacity 0 → target
180ms
```

Easing:

`cubic-bezier(0.22, 1, 0.36, 1)`

Do not stage multiple layer entrances under Reduce Motion.

## 10. StateStrength transitions

StateStrength semantics do not change:

| State | Target |
| --- | ---: |
| 图片识别中 | 1.00 |
| 已定位到鱼体 | 0.58 |
| 鱼种识别中 | 0.36 |

Under Reduce Motion, transition between targets using a simple **180ms alpha interpolation**.

The field remains spatially static while its visual weight changes.

No positional or scale animation accompanies this transition.

## 11. Resolve

Resolve remains necessary to avoid a hard visual cut into Result.

Frozen Reduce Motion Resolve:

- duration: **200ms**;
- SegmentOffset: already frozen;
- Hairline: static;
- Particle: OFF;
- Node: static;
- Fish Focus: static;
- all visual layers fade through the existing ResolveStrength.

```text
p = elapsed / 200ms

ResolveStrength = (1 - p)^2
```

No:

- slide;
- zoom;
- scale;
- path travel;
- delayed glow tail.

At 200ms, all Processing-only visual layers are gone.

## 12. Runtime activation behavior

The user preference may change while Recognition is visible.

### OFF → ON

Immediate motion stop is allowed, but geometry must not jump.

```text
current SegmentOffset
→ freeze here
```

Particles may disappear through a short <=120ms alpha fade if needed to avoid a one-frame pop.

### ON → OFF

Resume from frozen geometry.

```text
speed 0
→ current state target speed
over 220ms
```

No clock restart.

## 13. Semantic invariants

Reduce Motion never changes:

- Recognition pipeline;
- product state;
- state minimum duration;
- Result routing;
- confidence;
- quality semantics;
- four-island topology;
- Quiet Gaps;
- Fish Focus availability;
- user copy.

It changes only motion expression.

## 14. Hard failures

FAIL if Reduce Motion:

1. hides the entire AI Edge Field;
2. forces QualityLevel=LITE;
3. jumps SegmentOffset on state transition;
4. substitutes movement with blinking/flashing;
5. leaves Particles drifting;
6. keeps Fish Focus breathing;
7. uses scale pulse as a breathing replacement;
8. uses slide/zoom during Resolve;
9. snaps to static seed when enabled mid-session;
10. restarts the motion clock when disabled;
11. changes Recognition timing or semantic states;
12. removes Fish Focus instead of making it static.

## 15. Acceptance shorthand

Normal:

> quiet, continuous AI motion.

Reduce Motion:

> the same Recognition visual language, held almost still.

The screen must still feel alive through state changes and hierarchy, not through continuous movement.
