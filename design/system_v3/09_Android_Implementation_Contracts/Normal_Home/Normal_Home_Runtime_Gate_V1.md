# Normal Home Android Runtime Gate V1

**Contract status:** READY_FOR_IMPLEMENTATION  
**Runtime execution:** NOT_RUN

This file defines future Android runtime evidence and verdicts. It does not run the Android build, emulator, device, or screenshot capture.

## Required Inputs

- APK SHA-256 and source/build commit SHA.
- Device/API profile, app-window width/height, screen size, density, orientation/window mode.
- Actual status/navigation/cutout/IME insets, fontScale, and selected NORMAL_FIXED or SHORT_SCREEN_MODE.
- Full native screenshot SHA-256, dimensions and capture timestamp; screenshots are uncropped and unresampled.
- Contract version and Visual Contract version.
- Top-level state, valid catch count, selected stable FishRecord ID, avatar state and media state.
- Resolved Asset/Component/Token IDs and source SHA-256 values.
- Fixture ID/source-photo SHA/provenance when Hero EvidenceFit is exercised.
- Lifecycle and Reduce Motion preference for motion cases.

Missing identity, source-hash mismatch, unsupported profile, cropped evidence, or unknown state cannot produce PASS.

## Required Profiles and States

- Baseline: 1080×1920px, 360×640dp, density 3.0 / 480dpi, portrait, fontScale 1.0.
- Widths: 320, 360, 393, 411, 412 and 480dp where supported.
- Long profile: 2340px and taller; retain NORMAL_FIXED whenever registered constraints fit.
- Short profile: 320×480dp and constrained/multiwindow window; verify overflow mode and reachability.
- Font scales: 1.0, 1.3 and 1.5.
- Catch states: NO_CATCH→Empty Home, HAS_CATCH / single record, MULTIPLE_CATCH, resolving, refresh pending/error with retained snapshot, media unavailable/recovered.
- Avatar: GUEST, DEFAULT, LOADING, ERROR and REMOTE.
- Hero evidence cases: only registered fixtures with verified provenance; cover, fit/evidence-fit and fallback behavior.
- Accessibility: TalkBack on/off, registered focus order, content descriptions, semantic bounds, contrast, and all targets ≥48×48dp.
- Keyboard: capture only when an IME is actually visible; Home has no page-owned text input.
- Motion: lifecycle resumed/paused and Reduce Motion on/off, with a neutral comparison frame.

## Assertions

1. Resolve exactly one state authority and verify its identifiers against State Binding.
2. Verify every static resource's raw-byte SHA-256 against Resource Binding and registry. Preserve inherited REVIEW/unregistered status; do not self-promote.
3. Compare geometry and glyph bounds only to same-kind anchors and the existing Pixel Target tolerances.
4. Verify the one Normal Home transform, single inset consumption, safe controls, and correct Empty Home sibling transform.
5. Verify registered typography/color/geometry tokens, component variants and Hero EvidenceFit rules.
6. Verify state transitions, retained stable record selection, media-only fallback, avatar fallback, navigation and recovery.
7. Verify TalkBack/focus order, 48dp target minima, fontScale behavior and contrast.
8. Verify motion timing/bounds, lifecycle behavior and Reduce Motion.
9. Save raw evidence, measured bounds, screenshots/logs, fixture provenance and result per profile/state.

## Verdicts

- PASS: all required evidence exists and all assertions pass for the exact APK/profile/state.
- FAIL: valid runtime evidence demonstrates a contract violation.
- BLOCKED_INFRA: a requested run cannot acquire the required device/evidence; include runner logs.
- REVIEW_REQUIRED: an authority, inherited asset status, fixture provenance or measured contradiction needs review.
- NOT_RUN: no runtime evidence has been collected.

A static validator PASS is not runtime PASS. The present work reports only static contract readiness.
