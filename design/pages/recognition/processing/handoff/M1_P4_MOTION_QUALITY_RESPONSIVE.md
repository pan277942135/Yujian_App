# M1 P4 · Motion / Quality / Responsive Contract

Status: **ENGINEERING TRACE COMMITTED · RUNTIME VISUAL VALIDATION PENDING**

PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166)  
P4 config commit: `9011a095e97617414a955d99a9602e0585e0445c`  
Machine input: [Processing_Engineering_Import_Config_V1.json](Processing_Engineering_Import_Config_V1.json)

## Motion contract

The three independent inputs remain separate:

- SegmentOffset: continuous 10,000ms path clock across product-state changes.
- StateStrength: 1.00 / 0.58 / 0.36 for IMAGE_RECOGNIZING / FISH_LOCATED / SPECIES_RECOGNIZING.
- ResolveStrength: 1 outside Resolve; `(1-p)^2` over the final 200ms.

Primary speed is 1.00 / 0.62 / 0.42. State interpolation uses `cubic-bezier(0.22,1,0.36,1)`, with 260ms to FISH_LOCATED and 280ms to SPECIES_RECOGNIZING. Entry staging is Receiving 0–180ms, Primary 80–320ms, Detail 180–420ms. Four primary seeds and the child-path phase deltas are in the import JSON.

The Android accumulator integrates the mean speed into its 10s cycle and CAPTURED/DETECTING share one product stage. It preserves their field clock. At RESULT, however, the implementation eases speed and StateStrength toward zero for 200ms. It does not freeze SegmentOffset at Resolve start, use a separate quadratic ResolveStrength, or stop node/particle clocks. Status and Fish Focus also do not consume a common explicit Resolve value. This is a concrete implementation gap, not a Figma result.

**RP-A11:** save current offset at Resolve start; freeze primary-derived hairline offsets, nodes and particles; fade all optical layers, Fish Focus and Status with the same `(1-p)^2` strength; hand off immediately, with no added processing wait. Keep real result gates unchanged.

## FULL / BALANCED / LITE and D0–D4

| Degradation | Edge Field | Fish Focus |
| --- | --- | --- |
| D0 | FULL | A |
| D1 | BALANCED | A |
| D2 | LITE | A |
| D3 | LITE | B |
| D4 | LITE | C |

FULL draws four companion hairlines, B_UR micro hairline, two nodes and eight anchored particles. BALANCED uses B1C/G1C, one node and four particles. LITE removes hairlines, nodes and particles. All three retain their own Receiving / Bloom / Mid / Core multipliers from the current Android profile; the exact values are in JSON. The compatibility `lowPerformance=true` input maps to D2.

Reduce Motion reads Android animator duration scale ≤0. It stops SegmentOffset progression, holds nodes static, suppresses particles and keeps semantic state changes/event gates intact. The screenshot override is deterministic evidence mode; it is not proof that production motion stays continuous.

## Responsive evidence and gaps

The existing transform fixtures cover six viewport classes from 1080×1920 through compact 320×640, five source aspect cases, center/edge fish boxes, full-source FIT, decorative CROP, and no transform change when detector bbox arrives. Back uses safe drawing insets; photo geometry does not. These checks exist in source but were **not run** in this handoff.

Existing coverage does not establish Status text wrapping, the Back touch target, safe-area screenshots, or full optical ROI at the compact sizes. **RP-A13** adds those UI checks. **RP-A14** keeps R05 FULL/LITE strength calibration pending real-device captures against the three retained Frozen images only; deleted Early art is not a calibration source.

## Test/source status

- `RecognitionVisualStateControllerTest`: blob `958ad857d44a31fa2d93094d28e43320efda7ec4`; source reviewed, not run.
- `RecognitionProcessingVisualTokensTest`: blob `edf1a2f23857c83d309b006307322f3f566bd3f6`; source reviewed, not run.
- `RecognitionImageTransformTest` and `RecognitionProcessingMediaPlanTest`: source reviewed in P1, not run.
- No runtime screenshot, API28 gate, R05 calibration, or Pixel Diff was executed here.

## Gate

P4's structured parameters and repair tasks are committed. The state machine's 220/320/350/250ms gates are unchanged. Engineering traces are ready for downstream implementation review; device and visual acceptance remain pending.
