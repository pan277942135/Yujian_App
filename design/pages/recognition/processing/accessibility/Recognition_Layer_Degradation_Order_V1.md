# Recognition Layer Degradation Order V1

Status: **FROZEN**

Scope: **global visual degradation priority across AI Edge Field and Fish Focus**.

## 1. Principle

> Preserve semantic focus before ambient decoration.

AI Edge Field is atmospheric and brand-defining. Fish Focus, after a real fish location exists, carries the stronger semantic message: **AI has found this fish**.

Therefore performance degradation must reduce peripheral Edge Field complexity before reducing Fish Focus fidelity.

## 2. Frozen global ladder

| Level | AI Edge Field | Fish Focus | Meaning |
| --- | --- | --- | --- |
| D0 | FULL | A | full fidelity |
| D1 | BALANCED | A | ambient detail reduced, real contour preserved |
| D2 | LITE | A | Edge Field reaches identity floor, contour still preserved |
| D3 | LITE | B | contour removed; real-bbox halo/perimeter retained |
| D4 | LITE | C | minimum accepted visual fidelity; real-bbox center halo retained |

Do not jump directly from D0 to Fish Focus B/C as the first performance optimization.

## 3. D0 — Full fidelity

- Edge Field = FULL.
- Fish Focus = A.
- Fish Focus A = real detector bbox + subject contour + halo.
- This is the preferred target.

## 4. D1 — Ambient detail reduction

- Edge Field = BALANCED.
- Fish Focus = A.
- Remove Micro Hairline, Secondary Fragments, some Companion Hairlines, particles and secondary node according to Quality Levels.
- Keep the real fish contour.

## 5. D2 — Edge Field identity floor

- Edge Field = LITE.
- Fish Focus = A.
- Edge Field keeps four Primary Energy Islands, Receiving Light, Outer Bloom, Mid Glow, Core and Quiet Gaps.
- Hairline / Node / Particle are no longer required.
- Fish Focus A is still preserved.

> Real fish contour is more semantically valuable than ambient AI ornament.

## 6. D3 — Fish Focus A → B

Only after Edge Field reaches LITE may Fish Focus degrade from A to B.

Fish Focus B:
- source remains real detector bbox;
- subject contour OFF;
- bbox-derived halo ON;
- restrained perimeter may remain;
- detector rectangle forbidden.

## 7. D4 — Fish Focus B → C

D4 is the lowest accepted design level.

Fish Focus C:
- source remains real detector bbox;
- contour OFF;
- perimeter requirement OFF;
- center-biased halo ON.

The halo must remain anchored to the actual fish location.

## 8. Never degrade

Performance degradation may never remove or change:
- Original Photo;
- Back action;
- Status copy semantics;
- three Recognition product states;
- Result routing and confidence semantics;
- detector/classifier behavior;
- four Primary Energy Islands;
- mandatory Quiet Gaps;
- Primary Core;
- Primary Mid Glow;
- basic Local Receiving Light;
- the rule that Fish Focus starts only after real fish location;
- real detector bbox as Fish Focus source.

## 9. Forbidden shortcuts

Never optimize by changing four islands into three or two islands.

Never optimize Fish Focus as A → OFF. The only valid sequence is A → B → C.

Do not retain expensive ambient decoration while sacrificing semantic focus first. A performance fallback such as Edge Field FULL + Fish Focus C is invalid.

## 10. Degrade / recover behavior

Quality degradation is not a user-facing event:
- no badge;
- no copy;
- no state reset;
- no Motion Clock reset;
- no Result change.

During degradation:
- preserve current SegmentOffset;
- remove subordinate layers quietly;
- Fish Focus A/B/C must remain anchored to the same real bbox.

During recovery, restore in reverse order: **D4 → D3 → D2 → D1 → D0**.

Do not restore all Hairline / Node / Particle detail in one visible frame.

Exact hysteresis belongs to platform implementation.

## 11. Reduce Motion relationship

Reduce Motion is independent.

Application order:
1. select global degradation level D0…D4;
2. apply Edge Field Quality Level;
3. apply Fish Focus A/B/C;
4. apply Reduce Motion behavior overrides.

Examples:
- D0 + Reduce Motion = FULL static Edge Field + Fish Focus A static;
- D2 + Reduce Motion = LITE static Edge Field + Fish Focus A static;
- D4 + Reduce Motion = LITE static Edge Field + Fish Focus C static.

## 12. Trigger ownership

This spec freezes **order**, not thresholds.

RAM threshold, GPU class, frame-time budget, thermal condition, device allow/deny list and OS-version policy are platform-owned.

Platforms must select among D0…D4 instead of inventing a different visual degradation path.

## 13. Design floor

D4 remains:

    Original Photo
    + LITE AI Edge Field
    + Fish Focus C
    + Status / Back

Forbidden lower state:

    Original Photo
    + text spinner only

or a state with no Edge Field and no Fish Focus.

If D4 cannot render reliably, that is a runtime/platform problem, not permission to silently redesign Recognition.

## 14. Hard failures

FAIL if implementation:
1. degrades Fish Focus before Edge Field reaches LITE;
2. removes a Primary Energy Island;
3. reconnects a Quiet Gap;
4. removes Fish Focus entirely after fish location;
5. uses a detector rectangle as B/C fallback;
6. detaches B/C from the real detector bbox;
7. changes Recognition states or result/confidence semantics;
8. restarts SegmentOffset during degradation;
9. treats Reduce Motion as a D-level;
10. restores all expensive detail in one flash;
11. degrades below D4 without a new design authority.

## 15. Acceptance shorthand

> First sacrifice decoration. Then simplify ambient identity. Only then simplify fish emphasis. Never sacrifice Recognition meaning.
