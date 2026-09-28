# Recognition Processing Motion Spec V1.1

Status: **FROZEN**

This file supersedes the legacy Recognition V1 phase-duration section. Geometry/color tokens in `YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md` remain valid unless explicitly overridden here.

## Real pipeline → visual timeline

The visual controller never advances beyond the real pipeline state.

| Presented phase | Minimum visible | Meaning |
|---|---:|---|
| CAPTURED | 350ms | photo accepted / prepare recognition |
| DETECTING | 600ms | understand photo; no fish-local claim |
| OUTLINE | 600ms | a real fish bbox has been reached |
| CLASSIFYING | 1250ms | analyze fish features |
| RESULT | route immediately after visual contract + real result | terminal result |

Nominal fast-result visual story: **2800ms**.

Rules:
- If inference is faster, retain the completed real result until the 2.8s visual story completes.
- If inference is slower, do not fabricate OUTLINE / CLASSIFYING / RESULT. The current real phase may remain longer.
- FAILURE routes immediately to failure presentation.
- `MAX_POST_RESULT_HOLD_MS = 2800` is retained as V1.1 API compatibility; the behavioral authority is the complete 2.8s story, not V1’s 900ms compression.
- `RESOLVE_FADE_MS = 200`.

Runtime gate tolerance:
- total measured fast-result story: 2500–3500ms;
- final fish focus stable duration: >=1000ms.

## Ambient field motion

Base loop: **10,000ms**, linear.

Phase speed:
- CAPTURED 1.00×
- DETECTING 1.00×
- OUTLINE 0.82×
- CLASSIFYING 0.65×

Fixed path phase offsets:
- B1 0.00
- B2 0.23
- B3 0.47
- B4 0.71
- G1 0.12
- G2 0.39
- G3 0.64

Visibility must remain asynchronous: normally only 3–5 paths feel active at one moment. No synchronized ring pulse.

## Edge bloom

- Blue bloom anchored toward upper/right edge.
- Gold bloom anchored toward upper/left edge.
- Bloom is narrow and peripheral.
- Never tint the full photograph.
- Low-performance mode may reduce outer bloom by ~20%.

## Particle motion

- 8 particles normal, 4 low-performance.
- ~5s deterministic travel cycle with independent phase offsets.
- local travel about 20–64px equivalent depending on anchor.
- sinusoidal pulse; no sudden pop.
- screenshot mode freezes to deterministic anchors/clock.

## Fish-focus motion

### OUTLINE reveal
- halo/perimeter reveal: 360ms target;
- contour reveal: 360–380ms target;
- alpha rises from 0 to frozen target, no scale “scan”.

### CLASSIFYING breathing
- period: **1900ms**;
- halo/perimeter response breathes slowly;
- contour core: 0.36 ↔ 0.42;
- no positional wobble of the bbox/contour;
- fish focus remains visually attached to the fish.

### Resolve
When a real result already exists, the final **200ms** of CLASSIFYING fades:
- ambient field;
- status overlay;
- fish halo/contour;
together toward result transition.

## Motion accessibility / degradation

- Reduce Motion: preserve state changes and static fish focus; remove/flatten nonessential continuous travel.
- Low-performance: particles 8→4, outer bloom −20%, contour may degrade A→B.
- Neither mode may change recognition semantics or confidence result.
