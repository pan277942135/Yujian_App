# Recognition Processing Motion Spec V1.2

Status: **FROZEN**

This document supersedes `Recognition_Processing_Motion_Spec_V1_1.md` for current product motion.

V1.2 aligns Recognition Processing motion with the three-state State Timeline:

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

The old CAPTURED / DETECTING split is no longer a user-visible motion state.

## 1. Minimum product pacing

| Product state | Minimum visual beat | Edge Field strength | Segment speed |
| --- | ---: | ---: | ---: |
| 图片识别中 | 900ms | 1.00 | 1.00× |
| 已定位到鱼体 | 600ms | 0.58 | 0.62× |
| 鱼种识别中 | 1250ms | 0.36 | 0.42× |
| RESOLVE | 200ms | 1→0 via ResolveStrength | frozen |

Nominal fast-result story:

```text
900 + 600 + 1250 + 200 = 2950ms
```

These are minimum visual beats, not fake progress timers.

If a product state needs to remain longer, it may remain longer without adding extra loading states or rotating fake progress copy.

## 2. Motion continuity

The Recognition Processing surface uses one continuous visual clock.

Frozen rules:

- state transitions never restart the Edge Field;
- state transitions never reset path phase;
- state transitions never jump the luminous segment to a new position;
- state transitions change **speed** and **visual strength**, not geometry identity;
- RESOLVE freezes current segment position before fade-out.

This creates one continuous AI field rather than three separate animations.

## 3. Entry behavior

The first state, 图片识别中, contains the former “photo accepted” visual settling inside itself.

There is no separate CAPTURED state.

During the first **420ms**:

```text
0–180ms    Local Receiving Light establishes
80–320ms   Primary Bloom / Mid / Core becomes readable
180–420ms  Hairline / Node / Particle detail joins quietly
```

All layers use a soft ease-out:

`cubic-bezier(0.22, 1, 0.36, 1)`

The AI field must not pop in as a complete frame.

## 4. State transitions

### 图片识别中 → 已定位到鱼体

Duration for Edge Field strength/speed interpolation: **260ms**.

```text
StateStrength  1.00 → 0.58
SegmentSpeed   1.00 → 0.62
```

The fish-focus reveal belongs to the Fish Focus component and becomes the visual center.

The Edge Field remains continuous but clearly yields visual priority.

### 已定位到鱼体 → 鱼种识别中

Interpolation duration: **280ms**.

```text
StateStrength  0.58 → 0.36
SegmentSpeed   0.62 → 0.42
```

The field does not disappear. It becomes slower and quieter so the user reads “analysis continues” without peripheral distraction.

## 5. Result handoff

RESOLVE duration: **200ms**.

At the instant Resolve starts:

1. freeze the current SegmentOffset;
2. stop additional Node / Particle travel;
3. retain current geometry;
4. fade the entire Edge Field with one ResolveStrength.

```text
p = elapsed / 200ms
ResolveStrength = (1 - p)^2
```

At p=0:

`ResolveStrength = 1`

At p=1:

`ResolveStrength = 0`

No Edge Field layer may remain after Resolve completes.

## 6. Ownership

This spec freezes:

- product state pacing;
- continuous motion relationship;
- Edge Field state-strength targets;
- Edge Field segment-speed targets;
- Resolve handoff.

Detailed AI Edge Field travel is frozen in:

`AI_Edge_Field_V1_Motion_Contract.md`

Static geometry is frozen in:

`../components/AI_Edge_Field_V1_Static_Shape_Spec.md`

Rendering architecture is frozen in:

`../components/AI_Edge_Field_V1_Rendering_Contract.md`

Accessibility and quality degradation belong to:

`05 · Degradation & Accessibility`
