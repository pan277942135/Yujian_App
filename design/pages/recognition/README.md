# YuJian Recognition Flow V1 Design Package V1.1

Status: **FROZEN FOR ANDROID IMPLEMENTATION AND ACCEPTANCE**

Purpose:
Freeze Recognition Flow V1.1 design, runtime and evidence source of truth for Work and Android implementation.

## Design Manager

Recognition Processing 在 Design Manager 中固定为 **6 个直接子菜单**：

```text
识别过程
├── 01 · State Timeline
├── 02 · Visual States
├── 03 · Layer & Component Ownership
├── 04 · Motion & Transition
├── 05 · Degradation & Accessibility
└── 06 · Runtime Evidence
```

迁移规则：

- `01_Capture_Transition_Frozen.png` → Visual States / CAPTURED
- `02_AI_Understanding_Frozen.png` → Visual States / DETECTING
- `03_Fish_Highlight_Frozen.png` → Visual States / OUTLINE
- `04_Fish_Identifying_Frozen.png` → Visual States / CLASSIFYING
- 原文件不移动、不复制；只在 Design Manager 中迁移展示与 Authority 引用。
- `05_Result_High_Frozen.png` ～ `09_Error_Image_Quality_Frozen.png` 属于 Result / Error，不并入 Processing。
- Engineering Spec 继续作为 Timeline / Layer / Motion 的辅助视觉 Authority。

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

## Acceptance

- `runtime/Recognition_Runtime_Contract_V1_1.md`
- `spec/Recognition_State_Timeline_Spec_V1_2.md` — product/experience State Timeline authority
- `evidence/Recognition_Evidence_Contract_V1_1.md`
- `spec/Recognition_Acceptance_Criteria_V1_1.md`

Final runtime acceptance requires API28 `runtime_gate_result.json.classification == PASS`.
