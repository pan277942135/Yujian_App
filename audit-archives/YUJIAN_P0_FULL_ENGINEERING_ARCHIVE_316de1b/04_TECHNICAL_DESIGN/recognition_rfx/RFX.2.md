# RFX.2 — Optical edge renderer

## Scope

The seven frozen primary path geometries and their island/fragment IDs remain
unchanged. Moving light is now cut from each actual curve with Android
`PathMeasure`, including a two-piece wrap at the real endpoint. The renderer
draws a per-path color tail, local path blur for bloom layers, and the existing
path-specific core widths. It does not create a full-frame effect bitmap.

## Verification

- Added `RecognitionOpticalSegmentRuntimeTest.luminousSegmentUsesMeasuredBezierLengthAndWrapsAtTheRealPathEnd`; the recognition-frozen API28 gate now runs it.
- Existing topology assertions still require islands `B1/G1/B3/G3` and fragments `B2/G2/B4`.
- `git diff --check` and `bash -n scripts/android_runtime/gates/recognition_frozen.sh`: PASS.
- API28 instrumentation: pending CI for the completed RFX branch.

## Frozen behavior

No frozen reference art, path endpoint/control-point geometry, detector, model,
or backend behavior changed.
