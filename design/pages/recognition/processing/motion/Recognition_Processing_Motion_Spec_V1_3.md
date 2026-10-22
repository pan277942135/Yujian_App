# Recognition Processing Motion Spec V1.3 — SWOS Timing Authority

**Status:** `MAIN_ADOPTED_DESIGN_ONLY`
**Scope:** Recognition Processing's minimum optical pacing and state/event relationship.
**Runtime:** `NOT_RUN`; this version does not claim Android parity.

## Authority and supersession

Adopted on main through PR #190, V1.3 supersedes V1.2 only for Processing timing, optical direction and Processing-to-Result timing ownership. V1.2, Visual Fidelity V1.2, the V3.1 Page Contract, the V3.1 State Machine and AI Edge Field V1 remain unchanged as frozen history/implementation evidence. The new SWOS Optical Contract V1 owns the optical layer responsibilities; V1.3 owns the timeline below. This design contract does not change recognition behavior, confidence semantics, routing, copy, thresholds or CTA availability.

## 2800 ms minimum visual pacing

The four internal phases sum to a **2800 ms minimum optical story**. Boundaries are measured from Processing visual-clock start:

| Phase | Duration | Cumulative boundary | User-visible mapping | Optical intent |
|---|---:|---:|---|---|
| CAPTURED | 350 ms | 350 ms | IMAGE_RECOGNIZING | Preserve the real photo; a low water reflection wakes near the lower photo. |
| DETECTING | 600 ms | 950 ms | IMAGE_RECOGNIZING | Irregular water caustics and overlapping ripples diffuse upward; no fish focus before real detector evidence. |
| OUTLINE | 600 ms | 1550 ms | FISH_LOCATED | Real fish geometry may receive warm-gold focus; water light bends toward the verified fish region without warping the photo. |
| CLASSIFYING | 1250 ms | 2800 ms | SPECIES_RECOGNIZING | Fish focus remains the semantic lead while water light settles. The 2600–2800 ms peripheral-water release is inside this phase. |

Boundary vector: `[0, 350, 950, 1550, 2800] ms`. Duration vector: `[350, 600, 600, 1250] ms`.

CAPTURED and DETECTING are internal technical phases of the same `IMAGE_RECOGNIZING` product state. They are not separate user-facing screens or states. The other product states remain `FISH_LOCATED` and `SPECIES_RECOGNIZING`.

## Real-event gates and elapsed inference

The timing is a minimum visual rhythm, not a completion timer. Each internal phase advances only after its minimum beat and its real pipeline event. A real No Fish, invalid-quality result or technical failure uses its existing route. A timer alone never advances the experience to success.

- If the real successful result is available before 2800 ms, preserve the minimum optical story and hand off when the minimum and existing business gates are satisfied.
- If inference is still running after 2800 ms, hold in a quiet, low-intensity CLASSIFYING presentation. Do not add a progress percentage, ETA, fake state or repeated attention pulse.
- When the actual result and existing business gates are satisfied, navigate immediately. No extra 200 ms Processing wait is inserted.

## Processing end and optical continuity

During 2600–2800 ms, peripheral ambient water, caustics and ripples naturally decay to zero. Keep only a narrow, soft gold trace on trusted fish geometry when that geometry exists. The trace is the source for the Result motion; it is not a new state. If no trusted subject contour exists, do not invent one; use the continuity contract's bounded regional-glow or non-spatial fallback.

RR09's 850 ms starts at the first actual presented Result frame. Its opening 0–200 ms is photo continuity **after navigation**, not a pre-navigation wait. `Processing_Result_Optical_Continuity_Contract_V1.md` owns the shared photo, geometry, session and light-source transfer.

## Accessibility and implementation boundary

Reduce Motion replaces moving caustics, ripples and traveling light with a static composition while preserving real-event gates and business semantics. Performance fallback reduces secondary optical detail first. API 28 renderer choice, frame pacing and resource lifecycle require Stage 1 implementation and runtime evidence.

This spec does not certify the existing Android runtime against the new timing. Stage 1 must update the runtime projection, assets, controller and tests as listed in the Phase 0 closure report.
