# Recognition Result Runtime Visual Gate V1

**Contract status:** READY_FOR_VISUAL_IMPLEMENTATION  
**Runtime execution:** NOT_RUN

This document defines future acceptance only. No Android build, APK, emulator, device, screenshot or TalkBack run is performed here.

## Required inputs

APK SHA-256 and build SHA; device/API profile; output size, density and logical viewport; fontScale/orientation/locale; actual system/cutout/gesture insets and IME state; top-level Result state and overlay state; full uncropped screenshot SHA-256 (1080×1920 baseline); contract version; exact registered reference asset ID/path/SHA.

Missing identity, unknown state, cropped image, missing inset profile or unregistered source is not a pass.

## Profiles

Baseline: 1080×1920, density 3.0 / 480dpi, logical 360×640dp, fontScale 1.0, portrait, IME hidden. Host reports actual insets; do not hard-code bar sizes.

Exercise 320dp, 360dp and 411dp; fontScale 1.0/1.3/1.5/2.0; supported orientation; TalkBack on/off; keyboard visible/hidden. Exercise all five canonical states, candidate idle/selected, long species name, missing optional metadata, save loading/error, Species Selector, numeric sheet, location sheet and IME/back. These overlays are not new Result states.

## Gate procedure

1. Verify evidence identity, uncropped full-canvas dimensions, contract version and asset SHA.
2. Resolve page/state/asset authority and sRGB comparison normalization; do not rewrite source assets.
3. Resolve actual insets and compare content safely without cropping the screenshot.
4. Check state-specific Hero geometry, content anchors, typography glyph bounds, token colors and component variants using the fixed pixel tolerances.
5. Check original-photo subject-safe framing or full Evidence Fit; never stretch or generate fill.
6. Check state copy, candidate selection, CTA routes, TalkBack order, focus, fontScale, touch targets, contrast and IME behavior.
7. Confirm no extra product state, unapproved overlay or Save Catch → Detail route.

## Output classification

| Output | Meaning |
|---|---|
| PASS | Evidence is complete and all visual/state/accessibility/error assertions pass. |
| FAIL | Valid evidence exists and one or more measured contract assertions fail. |
| BLOCKED_INFRA | Evidence collection is blocked by test/device infrastructure. |
| REVIEW_REQUIRED | Asset profile/authority/evidence is ambiguous or an owner decision is required. |

Runtime PASS applies only to the exact APK SHA, device profile, screenshot SHA and contract version recorded. Missing evidence is never PASS.
