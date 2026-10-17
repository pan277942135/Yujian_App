# RFX.1 — Shared photo and light-field transform

## Scope

The ambient recognition field now uses the same processing-photo transform as
the displayed source image and fish focus. FIT letterbox bands are excluded;
CROP uses the visible viewport. Authored path control points outside the photo
remain unclamped and are clipped at the visible photo boundary.

## Verification

- Added `RecognitionImageTransformTest.ambientPhotoCoordinatesExcludeFitBandsAndPreserveAuthoredOvershoot` for FIT, CROP, and out-of-range optical coordinates.
- `git diff --check`: PASS.
- Android unit and API28 runtime execution: pending CI; this checkout has no Gradle wrapper or installed Gradle executable.

## Frozen behavior

Recognition source pixels and focus continue to use the existing source-to-
viewport transform. No frozen reference image, detector, model, or backend
behavior changed.
