# Recognition Quality Levels V1

Status: **FROZEN**

Scope: **visual complexity degradation only**.

This contract defines three rendering-quality profiles for Recognition Processing:

- `FULL`
- `BALANCED`
- `LITE`

They are **not three visual styles**.

They are three complexity budgets for the same frozen Recognition visual system.

## 1. Core principle

> Degrade detail first. Preserve identity last.

A lower quality level may remove expensive or high-frequency visual detail, but it must never redesign the Recognition experience.

All three levels must still read as the same YuJian AI Edge Field.

## 2. Cross-level invariants

The following are frozen and remain identical in FULL / BALANCED / LITE:

1. four Primary Energy Islands;
2. island positions;
3. mandatory Quiet Gaps;
4. Primary Bézier geometry;
5. per-path Primary Core widths;
6. island hierarchy:
   `B_UR > G_UL > G_LL > B_LR`;
7. StateStrength semantics;
8. SegmentOffset semantics;
9. ResolveStrength semantics;
10. Recognition three-state product story;
11. captured photo remains visually dominant.

A quality level may not create a different topology.

## 3. FULL

Purpose:

> complete high-fidelity AI Edge Field.

### Optical layers

| Layer | FULL |
| --- | --- |
| Local Receiving Light | 100% |
| Primary Core | 100% |
| Primary Mid Glow | 100% |
| Primary Outer Bloom | 100% |
| Companion Hairline | all 4 islands |
| Micro Hairline | B_UR ON |
| Secondary Fragments | allowed |
| Hot Node | B_UR main + max 1 weak secondary |
| Particles | 8 anchors |

Particle rule:

- 8 anchors exist;
- at one frame, no more than approximately 3 should feel strongly visible;
- particles remain atmospheric detail.

FULL does not mean “maximum brightness”.

It means **complete layer vocabulary**.

The frozen natural / restrained hierarchy still applies.

## 4. BALANCED

Purpose:

> preserve the high-fidelity visual identity while removing expensive high-frequency detail.

### Optical layers

| Layer | BALANCED |
| --- | --- |
| Local Receiving Light | 85% |
| Primary Core | 100% |
| Primary Mid Glow | 90% |
| Primary Outer Bloom | 75% |
| Companion Hairline | B_UR + G_UL only |
| Micro Hairline | OFF |
| Secondary Fragments | OFF |
| Hot Node | B_UR only |
| Particles | 4 anchors |

Particle rule:

- maximum approximately 2 strongly visible particles at one frame.

BALANCED removes detail in this order:

```text
Micro Hairline
→ Secondary Fragments
→ lower-island Companion Hairlines
→ half of Particles
→ secondary Hot Node
```

The four Primary islands remain visually unchanged in geometry.

BALANCED must not look like a separate visual mode.

## 5. LITE

Purpose:

> the minimum renderer that still unmistakably preserves AI Edge Field V1 identity.

### Optical layers

| Layer | LITE |
| --- | --- |
| Local Receiving Light | 70% |
| Primary Core | 100% |
| Primary Mid Glow | 80% |
| Primary Outer Bloom | 50% |
| Companion Hairline | OFF |
| Micro Hairline | OFF |
| Secondary Fragments | OFF |
| Hot Node | OFF |
| Particles | OFF |

The minimum optical stack is therefore:

```text
Original Photo
+ Local Receiving Light
+ Primary Outer Bloom
+ Primary Mid Glow
+ Primary Core
```

for all four Primary Energy Islands.

Important:

> LITE must **not** degrade to “four thin glowing lines”.

Receiving Light and Mid Glow remain mandatory because they preserve the natural light-field character.

## 6. Quality profile multipliers

The quality profile may provide a global supporting multiplier to the Rendering Contract:

| Profile | Quality multiplier |
| --- | ---: |
| FULL | 1.00 |
| BALANCED | 0.92 |
| LITE | 0.82 |

However, the layer enable/disable rules above take precedence.

The multiplier is **not** sufficient by itself to implement degradation.

Do not implement quality levels by simply dimming the entire Edge Field.

## 7. Frozen degradation priority

When visual cost must be reduced, remove or reduce layers in this order:

```text
01 Micro Hairline
02 Secondary Fragments
03 lower-island Companion Hairlines
04 Particles 8 → 4
05 Secondary Hot Node
06 all remaining Companion Hairlines
07 Particles 4 → 0
08 Outer Bloom strength
09 Receiving Light strength
```

Never cross below the LITE minimum stack.

## 8. Never remove

The following survive every performance quality level:

- Original Photo;
- four Primary Energy Islands;
- Primary Core;
- Primary Mid Glow;
- basic Local Receiving Light;
- mandatory Quiet Gaps.

This is the visual identity floor.

## 9. Quality switching

Quality switching is an implementation condition, not a product event.

Therefore:

- no quality-level badge;
- no visible “performance mode” copy;
- no loading reset;
- no state reset;
- no Motion Clock reset;
- current SegmentOffset remains continuous.

When dropping quality:

> remove subordinate detail quietly.

When recovering quality:

> restore detail gradually enough to avoid a one-frame Hairline / Particle pop.

Exact device thresholds, frame-time thresholds, thermal policy and hardware classification belong to platform implementation and are intentionally **not frozen here**.

## 10. Relationship to motion

Quality level does not change:

- minimum state timing;
- Segment Speed;
- Primary path seed;
- StateStrength target;
- Resolve duration.

Quality controls visual cost, not Recognition pacing.

## 11. Relationship to Reduce Motion

Quality Level and Reduce Motion are orthogonal.

Example:

```text
FULL + Reduce Motion
BALANCED + Reduce Motion
LITE + Reduce Motion
```

are all valid combinations.

Reduce Motion will be frozen separately under:

`05 · Degradation & Accessibility → 02 · Reduce Motion`

## 12. Fish Focus boundary

This Quality Levels V1 contract freezes **AI Edge Field quality**.

Fish Focus Level A / B / C degradation remains a separate contract and must not be silently coupled to Edge Field FULL / BALANCED / LITE.

For example:

```text
Edge Field = LITE
Fish Focus = Level A
```

is valid if the platform can afford the real contour.

Performance policy may later coordinate them, but design semantics remain separate.

## 13. Hard failures

FAIL if a lower quality profile:

1. removes one of the four Primary islands;
2. changes island positions;
3. reconnects a Quiet Gap;
4. changes Primary Core widths;
5. equalizes island brightness;
6. restarts SegmentOffset during quality switching;
7. changes state timing;
8. removes Receiving Light entirely;
9. removes Mid Glow and leaves only thin Core lines;
10. changes Fish Focus semantics implicitly;
11. changes Recognition Result semantics;
12. exposes quality switching to the user as a visible UI state.

## 14. Acceptance shorthand

### FULL

> complete, restrained high-fidelity field.

### BALANCED

> looks almost the same at first glance; fewer fine details on inspection.

### LITE

> visibly simpler, but unmistakably the same four-island natural light field.

If LITE looks like a different visual system, the degradation strategy has failed.
