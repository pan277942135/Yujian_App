# RFX.4 — V3.1 status card and energy ring

## Scope

Recognition now presents CAPTURED, DETECTING, OUTLINE, and CLASSIFYING as
separate pipeline-backed beats. The controller applies the frozen
220/320/350/250ms minimums, waits for detection start, a real fish box,
classifier start, and a real result, and exits immediately on a reported
failure. RESULT_READY hands off immediately when the classifier result and
minimum beat are both satisfied.

The status card uses the V3.1 frozen Chinese copy. Its progress ring is drawn
from the shared visual clock and holds a static segment when reduce-motion is
active.

## Verification

- Replaced the stale V1.3 runtime assets and contract projection with the V3.1 state sequence and exact frozen copy.
- Updated unit cases cover all four minimum beats, real-event gating, immediate result handoff, failure exit, V3.1 copy, and reduced-motion ring behavior.
- Updated API28 production-flow evidence assertions to capture CAPTURED and DETECTING separately, verify each V3.1 minimum, and record immediate RESULT_READY handoff.
- Local whitespace checks pass; Android unit/API28 execution is pending the completed RFX CI run.

## Frozen behavior

The original source photo, detector output, model thresholds, backend, and
reference images remain unchanged. No timer-only semantic progress is shown.
