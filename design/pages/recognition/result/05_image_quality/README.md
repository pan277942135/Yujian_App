# 05 — Image Quality / 图片质量不足

Status: **UI REVIEWED V1 — FROZEN REFERENCE RETAINED**

## Frozen Hi-Fi

![Image Quality Frozen](../../../design/09_Error_Image_Quality_Frozen.png)

Canonical:
`design/pages/recognition/design/09_Error_Image_Quality_Frozen.png`

SHA-256:
`965be5b37ce4c41e8a5b758f2f94bac16fa0b99dc135e25b114c2943d35b1a0d`

## Page role

Image Quality is a capture-quality recovery state.

A fish may be present, but the source image is not reliable enough for recognition.

It must remain distinct from No Fish.

## Frozen information hierarchy

1. Source photo
2. Quality-specific title
3. Capture-quality guidance
4. Primary retake
5. Secondary gallery choice
6. Tertiary back

## UI review V1

### A. Title — FREEZE

`照片不够清晰，无法识别`

Do not collapse this into the No Fish message.

### B. Guidance — FREEZE

`请拍摄更清晰的照片，确保鱼的整体轮廓清晰、没有遮挡。`

The copy explains what the user can improve without exposing model internals.

### C. Primary action — FREEZE

`重新拍摄`

### D. Secondary action — FREEZE

`从相册选择`

### E. Back — TERTIARY

Back remains available and low weight.

### F. Content exclusions — FREEZE

Do not show:
- species candidates
- catch metadata
- save
- memory CTA
- Fish Guide
- confidence
- quality score
- blur percentage
- technical quality-gate code

### G. Visual tone — FREEZE

- calm and instructional;
- no aggressive error-red treatment;
- keep the source photo visible;
- panel and actions share the No Fish recovery family;
- copy difference, not a new visual system, distinguishes the two states.

## Relationship to No Fish

Shared:
- layout family
- action hierarchy
- recovery behavior
- background language

Different:
- reason/title
- guidance copy
- underlying runtime state

Do not merge them into one generic error page in design semantics.

## Review status

UI structure: **CLOSED V1**

Copy: **CLOSED V1**

Recovery actions: **CLOSED V1**

State distinction from No Fish: **CLOSED V1**

Runtime parity: **NOT PART OF THIS DESIGN REVIEW**

## Hero Media Contract — FROZEN

Image Quality uses **Evidence First / EVIDENCE_FIT**.

Rules:
- show the entire oriented source photo;
- preserve blur, occlusion and framing evidence;
- do not Smart Crop to hide the quality problem;
- do not apply sharpening, AI enhancement or color grading;
- uncovered Hero area uses shared GLASS_A over BG_CONTENT.

Authority:
- `../media/Recognition_Result_Hero_Media_Contract_V1.md`
