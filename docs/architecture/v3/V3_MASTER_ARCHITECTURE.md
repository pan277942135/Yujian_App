# YuJian V3 — Stage0 Master Architecture
Main audited: 1f930e4678264522d4d160c28c8d52901eb14c0d. Stage 0 is architecture-only; no production code or visual parameters modified.

## Product invariants
- Orb is the continuous energy source. Its resident internal motion and ambient filaments do not diminish when a recognition task emits extra energy.
- Existing R06 five resident paths G01/G02/A01/A02/C01 are not five task filaments. Do not lock task filament count. Existing GL Orb has five orbital ribbon specs, another distinct implementation detail.
- Energy journey: Orb -> PhotoContact (Release) -> rounded Photo Perimeter -> trustworthy Mask exterior nearest contact P* (Fish) -> stored exact Return segments -> same Orb -> real Chinese Glyph -> Result Stable.
- One Controller owns the stages and Scene Clock, beginning at actually presented photo first frame. Thirteen stage minimum durations sum to 6000ms, but real model gates can extend elapsed wall time. 60s watchdog is failure, never success.
- No fake Mask/P*/fish-tail anchor, no arrivalPath masquerading as fishPath, no separate clock, no hardcoded Chinese result.

## Logical architecture (reuse, not rewrite)
L1 Visual references and measurable 26-frame parameter contract: Stage1 freeze MD+JSON+SHA.
L2 Scene+model: RecognitionSceneAnimationController / RecognitionSceneFrame.
L3 Energy+identity: WaterLightPathLedger / RecognitionTaskFilamentState / RecognitionNearestContourContactPoint.
L4 Render: existing EGL14/GLES20 OrbCrystalGlRenderer, Stage045GlRenderer, Perimeter shader; unique Layer Ownership.
L5 Handoff: RecognitionResultRuntime / RecognitionResultHandoffConsumer / Result screen.
L6 Evidence: existing Recorder, EventSink, Store, PixelCopy and ZipExporter.

## Cross-layer contract
SceneKey: sessionId,attemptId,imageId,photoSha256,photoContentVersion,geometryVersion. Events add stage, eventSequence, monotonicTimestamp; path-bound events add pathId, maskSha256, maskVersion and P*. Renderers only consume immutable frame; they cannot authorize stage transition or invent Path.
A valid first GL frame requires valid Shader,FBO,Scene,Bloom,Composite and EGL Swap; EGL Swap alone is insufficient. Keep legacy fallback until a valid new frame. Multiple EGL hosts can stay if logical state and layer owners are unified.

## Mandatory execution order
Stage0 architecture; Stage1 visual source measurement+freeze Commit; Stage2 diagnostic base; Stage3 Orb GPU; Stage4 Task Release+Photo Perimeter; Stage5 real Mask+Fish; Stage6 Return+Glyph+Result; Stage7 26-frame API36 final verification.
Only one Stage per Work. Earlier gate PASS is required before proceeding. Stage0 is not device or visual PASS.
