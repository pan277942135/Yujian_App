# Recognition Processing — Frozen V1.1

Android implementation source of truth for the Recognition processing visual runtime.

## Authority

1. Frozen Hi-Fi:
   - `../design/01_Capture_Transition_Frozen.png`
   - `../design/02_AI_Understanding_Frozen.png`
   - `../design/03_Fish_Highlight_Frozen.png`
   - `../design/04_Fish_Identifying_Frozen.png`
2. `spec/Recognition_Processing_Visual_Spec_V1_1.md`
3. `motion/Recognition_Processing_Motion_Spec_V1_1.md`
4. `design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png`
5. `motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md` for legacy V1 geometry/tokens only where not superseded.

Markdown numeric values override the engineering infographic when a numeric discrepancy exists.

The V1.1 motion spec supersedes the old V1 220/320/350/250ms + 900ms timing contract.

This source controls only visual presentation. Detector, crop, classifier, confidence semantics and result routing remain in the production Recognition runtime contract.

Work must validate through:
`../evidence/Recognition_Evidence_Contract_V1_1.md`
and
`../spec/Recognition_Acceptance_Criteria_V1_1.md`.
