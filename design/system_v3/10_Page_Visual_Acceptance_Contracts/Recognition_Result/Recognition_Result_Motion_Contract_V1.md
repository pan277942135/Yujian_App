# Recognition Result Motion Contract V1

**Status:** FROZEN contract; runtime NOT_RUN.

## Authority

Reuse design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V1.md and design/pages/recognition/spec/Recognition_Visual_Fidelity_V1_2.md. Recognition Result adds no AI animation phase.

## Page entry

| Property | Frozen rule |
|---|---|
| Delay | 0 ms |
| Duration | 200 ms, existing final resolve fade inside CLASSIFYING |
| Easing | squared fade / fast release from Recognition Visual Fidelity V1.2 |
| Behavior | One restrained opacity resolve into normal Result; no translation, zoom, parallax or celebration |

## Reveal order

1. Background remains stable.
2. Fish identity and state content resolve with the inherited 200ms fade; no new stagger.
3. Confidence selects HIGH/MEDIUM/LOW. No separate numeric confidence badge is defined by the Frozen references.
4. CTA becomes actionable at canonical RESULT_READY when required state data is available; no extra delay.

NO_FISH and IMAGE_QUALITY_WARNING show their static recovery surface with no persistence action.

## Interaction and Reduce Motion

Candidate selection uses shared pressed/selected feedback only. No automatic highlight travel or candidate cycling. Save uses shared Loading with stable layout and duplicate-submit prevention. Recovery remains static except shared control feedback.

With Reduce Motion enabled, remove nonessential interpolation and use opacity-only resolution for the existing fade. Preserve semantic state, selected candidate, loading state and navigation. No translation, scale, pulse, breathing or continuous animation.

## Acceptance

Check delay, duration and easing against the referenced upstream transition. Under Reduce Motion, check opacity-only behavior and unchanged state/action semantics. Runtime evidence is NOT_RUN.
