# Recognition Degradation & Accessibility V1

Status: **FROZEN**

This is the parent authority for `05 · Degradation & Accessibility`.

The system is composed of four frozen contracts:

1. **Quality Levels**
   - FULL / BALANCED / LITE
   - controls AI Edge Field complexity.
2. **Reduce Motion**
   - removes continuous motion while preserving static semantics.
3. **Layer Degradation Order**
   - defines global D0→D4 ordering across Edge Field and Fish Focus.
4. **Semantic Invariants**
   - defines what none of the above are allowed to change.

## Frozen composition

```text
Quality Level
      ↓
Edge Field layer budget

D0…D4
      ↓
Edge Field quality + Fish Focus A/B/C

Reduce Motion
      ↓
motion override only

Semantic Invariants
      ↓
hard boundary around all of the above
```

## Global implementation order

```text
1. Determine D0…D4
2. Apply FULL / BALANCED / LITE Edge Field profile
3. Apply Fish Focus A / B / C
4. Apply Reduce Motion override
5. Verify Semantic Invariants
```

## Parent rule

> Every degradation path must end inside the same Recognition product model.

No platform may trade away Recognition meaning for visual performance.

## Design floor

The lowest permitted visual state is:

```text
D4
=
Original Photo
+ LITE AI Edge Field
+ Fish Focus C from real bbox
+ Status / Back
```

With Reduce Motion enabled, the same D4 layers remain but continuous motion is removed.

## Completion status

- 01 · Quality Levels — FROZEN
- 02 · Reduce Motion — FROZEN
- 03 · Layer Degradation Order — FROZEN
- 04 · Semantic Invariants — FROZEN

Therefore:

> **05 · Degradation & Accessibility = FROZEN**
