# YuJian Recognition Flow — Design Closure V1.1

Status: **FROZEN FOR ANDROID IMPLEMENTATION AND ACCEPTANCE**

This document is the Work-facing source-of-truth index for Recognition Processing and result/error states.

## Authority order

1. **Frozen Hi-Fi PNGs** — final visual appearance and composition.
   - `design/pages/recognition/design/01_Capture_Transition_Frozen.png`
   - `02_AI_Understanding_Frozen.png`
   - `03_Fish_Highlight_Frozen.png`
   - `04_Fish_Identifying_Frozen.png`
   - `05_Result_High_Frozen.png`
   - `06_Result_Medium_Frozen.png`
   - `07_Result_Low_Frozen.png`
   - `08_Error_No_Fish_Frozen.png`
   - `09_Error_Image_Quality_Frozen.png`
   - dimensions/SHA: `design/pages/recognition/design/reference_manifest.json`

2. **Recognition Processing Visual Spec V1.1** — layer behavior, AI field, fish highlight, status overlay.
   - `processing/spec/Recognition_Processing_Visual_Spec_V1_1.md`

3. **Recognition Processing Motion Spec V1.1** — phase timing, filament motion, particles, fish-focus reveal/breathing and result fade.
   - `processing/motion/Recognition_Processing_Motion_Spec_V1_1.md`

4. **Runtime Contract V1.1** — relationship between the real detector/classifier pipeline and presented visual phases.
   - `runtime/Recognition_Runtime_Contract_V1_1.md`

5. **Evidence Contract V1.1** — files that prove the implementation.
   - `evidence/Recognition_Evidence_Contract_V1_1.md`

6. **Acceptance Criteria V1.1** — PASS/FAIL rules used by Work.
   - `spec/Recognition_Acceptance_Criteria_V1_1.md`

## Conflict rules

- Frozen PNG vs prose visual description: **Frozen PNG wins for final appearance**.
- Engineering infographic vs Markdown numeric contract: **Markdown wins for numeric values**.
- Legacy V1 timing vs V1.1 timing: **V1.1 wins**.
- Runtime implementation vs frozen contract: **the contract wins; runtime must be corrected**.
- Evidence capture failures must not be disguised as product PASS and must not be misclassified as product FAIL_TEST.

## Product invariants

- The real captured/selected photo remains the primary frame throughout processing.
- AI presentation is restrained, edge-biased and documentary; never a cyber HUD.
- DETECTING does not visually claim a fish has been found.
- Fish-local focus starts only after the real pipeline has reached OUTLINE.
- Visual presentation never invents a later phase before the real pipeline reaches it.
- Detector, crop, classifier, confidence semantics and result routing are not altered by visual code.
- Result screens and error screens remain the nine frozen states represented by the reference set.

## V1.1 timing authority

`CAPTURED 350ms → DETECTING 600ms → OUTLINE 600ms → CLASSIFYING 1250ms → RESULT`

Nominal fast-result visual story: **2800ms**.
Runtime acceptance window: **2500–3500ms**.
Final fish-focus stable duration: **>=1000ms**.
Resolve fade: **200ms** at the end of CLASSIFYING once a real result exists.

The older 220/320/350/250ms + 900ms compression contract is superseded for V1.1.
