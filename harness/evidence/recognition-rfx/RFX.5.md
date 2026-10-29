# RFX.5 — Shared frame clock and cached geometry

## Scope

The Processing scene now samples one uptime-based visual clock from Compose's
frame callback and supplies that timestamp to the Edge Field, Fish Focus, and
status ring. Pipeline events use the same uptime source. The Edge Field
accumulator is keyed to the image session, so visual state changes adjust
strength and speed without resetting the path offset.

Viewport-specific optical Beziers and their Android `PathMeasure` instances
are cached until the visible photo rectangle changes. Glow paints, the Level A
contour path, and status-card gradient brushes are reused across frames.
Deterministic clock rendering remains available to screenshot tests.

## Verification

- Added `RecognitionAmbientFieldTest.liveFieldClockKeepsSegmentPhaseAcrossPipelineStateChanges` for phase continuity and eased speed changes.
- Removed independent 16ms/33ms delay loops; production redraws are frame-clock driven.
- `git diff --check`: PASS. Android unit/API28 runtime execution: pending the completed RFX CI run.

## Frozen behavior

The viewport transform, authored optical paths, quality degradation policy,
detector geometry, classifier, and result payload are unchanged.
