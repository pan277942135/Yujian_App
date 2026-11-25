# RFX.3 — Real mask contour

## Scope

Level A now traces ordered closed boundaries from the generated subject bitmap's
alpha mask, discards small disconnected components, and rounds only the mask
corners into a continuous curve. Its warm-gold contour bloom is drawn from that
path. Level B/C continue to show only their real-box focus treatment; no box is
used as a substitute contour.

## Verification

- Added `RecognitionFishContourRuntimeTest.contourFollowsConnectedMaskAndBuildsOneClosedSmoothPath` using a synthetic oval mask and explicit no-bounding-box-corner assertions.
- The API28 recognition-frozen gate runs the contour test.
- `git diff --check` and the gate script syntax check: PASS.
- Android API28 instrumentation: pending CI for the completed RFX branch.

## Frozen behavior

The detector box, subject bitmap source, classifier, model thresholds, and
Recognition result flow are unchanged.
