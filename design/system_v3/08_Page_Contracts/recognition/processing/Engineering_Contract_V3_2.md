# Recognition Processing Engineering Contract V3.2

**Status:** `PROPOSED_FOR_MAIN_ADOPTION`
**Page:** `recognition.processing.v3_2`

## Source and geometry

- Preserve the real original image bytes and EXIF-normalized source identity.
- Reuse `RecognitionImageTransform` and the resolved `photoContentRect`; photo, SWOS, BBox and trusted subject geometry share it.
- Keep the existing Result Hero mapping under `Recognition_Result_Hero_Media_Contract_V1.md` and pass the shared transform through the continuity handoff.
- Use actual detector output from `FishRecognitionPipeline.kt`. `FishSubjectPreviewEngine.kt` output is visual preview geometry, not classifier input; trust it only through the existing gate.
- Never trace a BBox as a fish contour or substitute a synthetic mask.

## Timing and orchestration

- Implement the 0/350/950/1550/2800 ms minimum boundaries from `State_Machine_V3_2.json` and `recognition_processing_motion_contract_v1_3.json`.
- Use one monotonic Processing clock for the four optical phases. Minimum beats and actual pipeline events are independent requirements.
- If the result is early, preserve the minimum story; if late, use a quiet low-intensity hold. Route immediately once both requirements and business gates pass.
- Do not add a 200 ms RESOLVE state. The 2600–2800 ms water release is a visual subphase inside CLASSIFYING.
- Pass photo ID, normalized dimensions, session/attempt/result identity, transform, geometry trust and residual-gold state to RR09 V3. RR09 starts at the first presented Result frame.

## Rendering and platform

- Implement the SWOS layer responsibilities in `SWOS_Optical_Contract_V1.md`; the legacy seven-path Edge Field is not the new renderer's geometry basis.
- Keep photo pixels, model input, business thresholds, confidence meaning, result routing and CTA behavior unchanged.
- Provide an API 28-compatible renderer. No newer runtime-shader API may be mandatory. Cache static sources; avoid per-frame full-resolution CPU work, bitmap allocation and unbounded blur.
- Reduce Motion uses a static optical composition and removes all travel/continuous motion. Degrade caustics/ripple detail before real photo or trusted fish geometry.
- Clip all effects to the visible photo content rectangle and release resources with the owning page lifecycle.

## Adoption and evidence boundary

V3.2 is a proposed contract and does not assert that Android code, API 28 runtime, demo or APK has been updated. Stage 1 must implement the mapped source files listed in `docs/execution/swos/PHASE0_CLOSURE_REPORT_V1.md` and attach exact-SHA evidence before claiming runtime parity.
