# YuJian Recognition Flow V1 Design Package V1.1

Status: **FROZEN FOR ANDROID IMPLEMENTATION AND ACCEPTANCE**

Purpose:
Freeze Recognition Flow V1.1 design, runtime and evidence source of truth for Work and Android implementation.

## Work entry point

Start here:

`design/pages/recognition/Recognition_Design_Closure_V1_1.md`

It defines authority order and conflict resolution for:
- nine Frozen Hi-Fi references;
- Processing visual spec;
- AI ambient-field motion;
- fish-local halo/contour;
- V1.1 2.8s timing;
- real-pipeline state mapping;
- API28 evidence and acceptance.

## Flow

Capture Completed
→ AI Understanding
→ Fish Highlight
→ Fish Identifying
→ Recognition Result

Result states:
- RESULT_HIGH
- RESULT_MEDIUM
- RESULT_LOW

Error states:
- ERROR_NO_FISH
- ERROR_IMAGE_QUALITY
- TECHNICAL_FAILURE (runtime-safe generic presentation)

## Frozen references

The nine Hi-Fi PNGs under `design/` are final visual authority.
Their dimensions and SHA-256 values are frozen in `design/reference_manifest.json`.

## Processing source of truth

- `processing/spec/Recognition_Processing_Visual_Spec_V1_1.md`
- `processing/motion/Recognition_Processing_Motion_Spec_V1_1.md`
- `processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png`
- `processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md` (visual geometry/token source; legacy timing superseded)

## Recognition Result 3+2 source of truth

The Result sub-package closes the existing five Frozen Result / recovery states without redesigning them:

- `result/DESIGN_PACKAGE_CLOSURE_V1.md`
- `result/spec/Recognition_Result_Feature_Spec_V1.md`
- `result/spec/Recognition_Result_State_Matrix_V1.md`
- `result/spec/Recognition_Result_Behavior_Spec_V1.md`
- `result/spec/Recognition_Result_Visual_Spec_V1.md`
- `result/motion/Recognition_Result_Motion_Spec_V1.md`
- `result/spec/Recognition_Result_Acceptance_Criteria_V1.md`
- `result/authority/authority_map.json`
- `result/review/Recognition_Result_Runtime_Alignment_Review_V1.md`

State-level Frozen references 05–09 are the final authority for state-specific composition. The Core Visual System `recognition_result_v1.png` remains the shared Result visual-language authority and does not override state-specific Frozen differences.

## Acceptance

- `runtime/Recognition_Runtime_Contract_V1_1.md`
- `evidence/Recognition_Evidence_Contract_V1_1.md`
- `spec/Recognition_Acceptance_Criteria_V1_1.md`

Final full-flow runtime acceptance requires API28 `runtime_gate_result.json.classification == PASS`.

Result Design Package closure and Result Runtime alignment are tracked separately in `result/status.json`.
