# Recognition Evidence Contract V1.1

Status: **FROZEN**

A Recognition implementation is not complete at Build PASS. Work must prove the runtime on API 28.

## Required frozen-state screenshots

Exact runtime outputs:

1. `01_capture_transition.png` ↔ `01_Capture_Transition_Frozen.png`
2. `02_ai_understanding.png` ↔ `02_AI_Understanding_Frozen.png`
3. `03_fish_highlight.png` ↔ `03_Fish_Highlight_Frozen.png`
4. `04_fish_identifying.png` ↔ `04_Fish_Identifying_Frozen.png`
5. `05_result_high.png` ↔ `05_Result_High_Frozen.png`
6. `06_result_medium.png` ↔ `06_Result_Medium_Frozen.png`
7. `07_result_low.png` ↔ `07_Result_Low_Frozen.png`
8. `08_error_no_fish.png` ↔ `08_Error_No_Fish_Frozen.png`
9. `09_error_image_quality.png` ↔ `09_Error_Image_Quality_Frozen.png`

Frozen reference dimensions and hashes are controlled by `design/reference_manifest.json`.

## Required timing evidence

`recognition_processing_timing.txt` must record:
- CAPTURED duration;
- DETECTING duration;
- OUTLINE duration;
- CLASSIFYING duration;
- TOTAL duration;
- FINAL FISH FOCUS STABLE duration.

Acceptance:
- fast-result total 2500–3500ms;
- fish-focus stable >=1000ms.

## Required video

`recognition_processing_v1_1.mp4`

Must:
- contain the real photo;
- show processing phases at normal speed;
- include the first result frame;
- be non-empty and finalized before evidence collection completes.

MP4 persistence belongs to the evidence phase. A missing/finalization-race MP4 is **FAIL_EVIDENCE**, not product **FAIL_TEST**, unless the instrumentation itself proves the processing flow failed.

## API28 Runtime Gate

Required sequence:
1. preflight PASS;
2. exact APK pair install PASS;
3. `RecognitionFrozenFlowEmulatorTest` PASS;
4. evidence extraction PASS;
5. final `runtime_gate_result.json.classification == PASS`.

## Failure taxonomy

- `FAIL_TEST`: UI/state/timing/assertion behavior is wrong.
- `FAIL_EVIDENCE`: required PNG/timing/MP4 is missing or invalid.
- `BLOCKED_INFRA`: emulator/adb/runner/transport problem.
- `FAIL_ARTIFACT`: APK/assets/package problem.

Work must repair the matching layer. It must not weaken a Frozen contract to turn the gate green.


## Final visual fidelity evidence

The final Recognition closure additionally requires:

- `10_level_a_contour.png` — real catch fixture + detector bbox + alpha subject fixture, rendered through the production contour extraction path;
- `11_reduce_motion_low_performance.png` — Reduce Motion + low-performance runtime degradation;
- `recognition_production_flow_trace.txt` — real `FishRecognitionPipeline` progress, real bbox, classifier result, visual-controller timestamps;
- `recognition_visual_parity.json` — nine-state Frozen-vs-runtime ROI/overlay structure metrics;
- `recognition_visual_parity_contact_sheet.png` — Frozen / runtime / blend review sheet.

### Frozen visual parity rule

Pixel identity is explicitly **not** required because runtime photography and Android system bars can differ.

The gate compares all nine states using UI-dominant normalized ROIs and includes:
- coarse edge/layout structure;
- luminance/tone projection;
- frozen palette occupancy;
- UI centroid/placement;
- OUTLINE / CLASSIFYING fish-focus presence.

The verifier is `scripts/verify_recognition_visual_parity_v1_1.py`.

A parity mismatch is `FAIL_TEST`, not `FAIL_EVIDENCE`: evidence exists, but the product rendering is outside the Frozen visual contract.

## Real production-flow gate

The final production-flow evidence runs without `phaseOverride` and executes:

`real catch photo → FishRecognitionPipeline detector → real bbox → classifier → RecognitionVisualStateController → result`.

A deterministic alpha subject fixture may be used to exercise Level A contour rendering, but it may not substitute detector/classifier state or prediction.
