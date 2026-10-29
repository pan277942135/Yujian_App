# Normal Home Runtime Visual Gate V1

**Contract status:** `READY_FOR_VISUAL_IMPLEMENTATION`  
**Runtime execution:** `NOT_RUN`

This file defines future runtime acceptance only. This work does not build Android, create an APK, run an emulator/device, or capture runtime screenshots.

## Required input identity

```text
APK SHA-256
Build/source commit SHA
Device/API and app-window profile
Full native screenshot SHA-256 and dimensions
Window W,H,L,T,R,B; density; fontScale; orientation/window mode
Top-level Home state and selected FishRecord ID
Normal_Home_Visual_Contract_V1 version
Exact Frozen source asset IDs and SHA-256 values
Fixture IDs / source photo SHA and provenance, when Hero evidence fit is tested
```

Missing identity, unknown page state, cropped/resized screenshot, unregistered source, or absent runtime bounds is not a PASS.

## Required profiles and states

- Reference: 1080×1920 px / 360×640 dp, density 3, fontScale 1.0, portrait, full uncropped output.
- Widths: 320, 360, 393, 411, 412 and 480 dp where the device profile supports them.
- Long window: 2340 px and taller; prove normal shared-offset mode whenever its constraints fit.
- Short window: 320×480 dp and constrained/multiwindow cases; capture mode and all content/action reachability.
- FontScale: 1.0, 1.3, 1.5. TalkBack on/off, focus, remote avatar loading/error, media loading/error, keyboard only when an IME is actually open, and supported orientation.
- Data counts: 0→Empty Home, 1→NH02 first catch, 2+→NH01 multiple catch. Also resolving, retained snapshot during refresh/error, media fallback/recovery, optional/missing/long metadata, Guest/default/loading/error/remote avatar.
- Hero inputs F01–F07 as registered in the Hero EvidenceFit contract, with valid provenance before runtime visual PASS.

## Procedure and output

1. Verify all source SHA-256 values against the contract registry; mismatch is `SOURCE_FAIL` and invalidates visual comparison.
2. Record exact APK/device/window/contract/fixture identity and capture full original-resolution screenshot; do not crop/resample it to pass.
3. Resolve page state and the one authority source for that state. Check geometry and glyph using existing source-specific tolerances; check image hash, crop/evidence-fit, visible body, assets, tokens and component refs.
4. Measure real semantics touch bounds and safe insets separately from visual bounds. Check state interactions, stable record ID, TalkBack/focus, fontScale, contrast, error messaging/recovery and motion/Reduce Motion.
5. Save per-case report with reference/actual rectangles of the same kind, raw deltas, test logs, screenshot SHA and verdict.

Allowed result values:

- `PASS`: required evidence exists and all assertions pass for this exact APK/profile/state.
- `FAIL`: valid evidence demonstrates a contract violation.
- `BLOCKED_INFRA`: an attempted run could not obtain the required target/device/evidence; attach the runner log.
- `REVIEW_REQUIRED`: an authority, source profile, fixture provenance or measured contradiction still requires a decision.
- `NOT_RUN`: no runtime execution/evidence.

Static validator PASS is not runtime PASS. No runtime verdict is claimed by this contract.
