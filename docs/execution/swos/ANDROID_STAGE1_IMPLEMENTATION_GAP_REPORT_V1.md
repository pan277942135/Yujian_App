# SWOS Phase 0 — Android Stage 1 Implementation Gap Report

**Status:** `OPEN_FOR_STAGE_1` · Android changes intentionally not made in Phase 0
**Audited source:** `main` / PR base `888706fda16d6f343f9bb4e02463f3b7a3526c31`; proposal branch at audit `5e43fa295bc7d3e8de578f9fa3569152591a58b9`
**Runtime / API 28 / APK:** `NOT_RUN`

This report identifies implementation work required after the proposed contract set is adopted. It does not treat the contract update as implementation evidence.

## RR09 shared-photo transition contract binding

The Android-specific implementation binding is now defined by [Android RR09 Shared-Photo Transition Final Contract V1](../../../design/pages/recognition/continuity/Android_RR09_Shared_Photo_Transition_Final_Contract_V1.md) and its [machine-readable JSON](../../../design/pages/recognition/continuity/android_rr09_shared_photo_transition_final_v1.json). It consolidates the two permitted photo placement modes, the exact R0.5 midpoint, first-frame geometry readiness, BBox-only degradation, Chinese-glyph formation, and one-shot lifecycle requirements.

The contract is design-only. It does not prove Android implementation, API 28 playback, screenshot parity, Mask qualification, APK provenance, or product-owner visual acceptance. The implementation gaps G01–G10 and exact-head evidence remain open until the dedicated Android RR09 runtime gate passes.

## Processing timing and state projection

| Source | Current code/evidence | Required Stage 1 change | Verification |
|---|---|---|---|
| `app/src/main/java/com/yujian/ai/ai/RecognitionRuntimeContract.kt` — `CONTRACT_VERSION`, `states` | Declares `recognition_processing_state_machine_v3_1` and minimums 220/320/350/250/0 ms. | Project V3.2 timing as 350/600/600/1250 ms while keeping real event gates and the same visible product states. | Unit test against State Machine V3.2; emitted runtime trace includes source SHA and event timestamps. |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionVisualStateController.kt` — `CAPTURED_MIN_MS`, `DETECTING_MIN_MS`, `OUTLINE_MIN_MS`, `CLASSIFYING_MIN_MS`, `DEFAULT_MIN_VISIBLE_MS` | Defaults remain 220/320/350/250 ms. | Update minimums and verify a late inference remains quiet after 2800 ms, with no timer-only result. | JVM tests for all boundaries, early-result hold, late-result hold and failure routing. |
| `app/src/main/assets/identify/state_machine/identify_state_machine.json` | Contract version V3.1 and 220/320/350/250 ms. | Move to the V3.2 projection and encode no RESOLVE business state / no extra pre-navigation delay. | Asset JSON validator and runtime-contract source parity test. |
| `app/src/main/assets/identify/animation/identify_timeline.json` | V3.1 presentation timeline with 220/320/350/250 ms. | Project 0/350/950/1550/2800 ms; 2600–2800 ms water release remains inside CLASSIFYING. | Timeline parity test; exact boundary trace. |
| `app/src/test/java/com/yujian/ai/ai/RecognitionRuntimeContractTest.kt` | Asserts V3.1 and 220/320/350/250/0 ms. | Assert V3.2 contract, 350/600/600/1250/0 values and real-event gate behavior. | Run target JVM unit suite on implementation SHA. |
| `app/src/androidTest/java/com/yujian/ai/RecognitionFrozenFlowEmulatorTest.kt` — timing trace/assertion block around `visualTimes`, `classifyingMs`, and `TIMING_*` evidence | Validates current controller minimums and records current stage timing. | Update expected phase boundaries and prove no timer-only success; record actual event gates independently from visual minimums. | Emulator test on supported API 28 runner with exact SHA and artifact. |

## SWOS renderer and fish focus

| Source | Current code/evidence | Required Stage 1 change | Verification |
|---|---|---|---|
| `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt` — `RecognitionProcessingScene`, `extractContour`, calls into `RecognitionAmbientField` | Owns the current photo snapshot, visual clock, and current contour extraction / Edge Field orchestration. | Replace the old line-led optical basis with SWOS layers; retain the production photo transform and handoff identity. Prove any contour is sourced from trusted subject geometry before treating it as fish silhouette. | Exact-SHA renderer trace, multi-photo screenshot matrix and no-letterbox pixel checks. |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt` — `RecognitionAmbientField`, path/geometry cache | Draws AI Edge Field V1 from seven registered paths, receiving light and layered strokes. | Implement Photo Base / Ambient Water / Caustic / Ripple / Peripheral Refraction layers under SWOS V1. Keep Edge Field source files untouched as history; do not reuse its seven paths as SWOS geometry. | API 28 and browser prototypes compared on same photo IDs, coordinate transform and visual review set. |
| `app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt` — `FishRecognitionPipeline` | Provides production detector BBox, confidence, classifier output; does not itself provide a segmentation mask. | Preserve detector/classifier outputs and route semantics. | Existing recognition pipeline tests plus unchanged model trace/threshold evidence. |
| `app/src/main/java/com/yujian/ai/ai/subject/FishSubjectPreviewEngine.kt` — `FishSubjectPreviewEngine` | Post-recognition visual subject preview/segmentation; not classifier input. | Label its output as engine-generated visual subject geometry and only use after the existing trust gate. If absent, use bounded BBox regional glow only, never a traced rectangle or invented contour. | Geometry provenance tests using image/session/source dimensions and trust-state checks. |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealPolicy.kt` — `geometryTrust`, `eligibility` | Existing trust state distinguishes `TRUSTED_MASK` from `TRUSTED_BBOX_ONLY`; validated against image identity, dimensions, BBox/confidence and mask quality. | Reuse trust semantics for SWOS Fish Focus and Residual Gold Trace; do not weaken the gate. | Unit tests for stale image, invalid box, mismatched mask and BBox-only fallback. |

## Processing → Result and RR09 V3

| Source | Current code/evidence | Required Stage 1 change | Verification |
|---|---|---|---|
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealHandoff.kt` — `RecognitionRevealHandoffFactory.create` | Carries result/session identity, detector/subject data, trust state, Processing photo viewport and transform; computes geometry trust. | Extend or map the handoff to SWOS source photo identity, residual-gold state and shared Processing→Hero geometry without an extra wait. | Handoff unit tests for same image/session/transform and valid/invalid geometry fallbacks. |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealController.kt` — `RecognitionRevealClock` | Already has 850 ms RR09 boundaries 200/380/550/680/850 ms. | Align its clock origin to the first actually presented Result frame and ensure Processing does not delay route for a second 200 ms. | Test-frame clock source assertion and Android frame trace at Result entry. |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealOpticalRenderer.kt` — `RecognitionRevealOpticalOverlay` | Draws photo transition, contour halo/core, transfer path and particles. | Start from the Processing residual gold trace, keep transfer warm-gold and photo-aligned, and ensure transient layers end by 850 ms. | API 28 frame captures, source/target mapping checks and post-850 static-frame comparison. |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognitionResultScreen.kt` — reveal `LaunchedEffect`, `SpeciesIdentityRow` | Samples a `withFrameNanos` clock; `SpeciesIdentityRow` can render a scientific-name line. | Make T0 the first presented Result frame. The V3 reveal title presents the Chinese species name only and allocates no English row; static Result layout/data remain under their existing authorities. Preserve CTA/action readiness and one-shot policy. | HIGH/Medium/Low/error integration cases, first-frame timestamp evidence, title layout and CTA interaction tests. |
| `app/src/main/java/com/yujian/ai/ui/recognition/reveal/RecognitionRevealGeometry.kt` — `RecognitionRevealGeometryResolver.resolveDetailed` | Resolves Processing/Result photo geometry and only creates contour transfer geometry for `TRUSTED_MASK`. | Use continuity contract data; retain no-spatial fallback when geometry is not trusted. | Same-source crop/transform tests across Result Hero geometry. |

## Stage 1 completion sequence

1. Main adopts the versioned Phase 0 contract/control-plane proposal; product branch synchronizes from main.
2. Update Android projection/assets/controller and tests together; no source-only timing edit.
3. Implement SWOS using the same original photo and approved `photoContentRect`; evaluate API 28-compatible rendering/performance.
4. Implement the Processing residual-gold → RR09 V3 handoff and Chinese-only reveal behavior.
5. Run exact-SHA JVM, emulator/API 28, accessibility, visual and runtime gates. Keep browser, Android and APK provenance results separate.
