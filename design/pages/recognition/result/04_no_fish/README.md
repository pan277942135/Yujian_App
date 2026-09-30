# 04 — No Fish / 未检测到鱼

Status: **UI REVIEWED V1 — FROZEN REFERENCE RETAINED**

## Frozen Hi-Fi

![No Fish Frozen](../../../design/08_Error_No_Fish_Frozen.png)

Canonical:
`design/pages/recognition/design/08_Error_No_Fish_Frozen.png`

SHA-256:
`3952e2f8eb8e42124e8554f6c1481351ff7cb7bb200180613787c4448bf2d8d2`

## Page role

No Fish is a capture-recovery state for a photo where the Recognition pipeline did not obtain a usable fish subject.

It is not a generic technical error.

## Frozen information hierarchy

1. Source photo
2. Recovery title
3. One concise guidance sentence
4. Primary retake
5. Secondary gallery choice
6. Tertiary back

## UI review V1

### A. Title — FREEZE

`没有找到可识别的鱼`

Do not replace with:
- `识别失败`
- `检测失败`
- technical detector terminology

### B. Guidance — FREEZE

`请让鱼完整出现在画面中，再试一次。`

The guidance is actionable and capture-oriented.

### C. Primary action — FREEZE

`重新拍摄`

This is the strongest action.

### D. Secondary action — FREEZE

`从相册选择`

This re-enters the existing image-selection flow.

### E. Back — TERTIARY

Back remains available but visually subordinate to recovery actions.

### F. Content exclusions — FREEZE

No Fish does not show:
- species candidates
- metadata
- save
- memory CTA
- Fish Guide
- model/debug data

### G. Visual tone — FREEZE

- calm recovery;
- source photo remains visible;
- no alarm-red panel;
- no warning icon required unless present in Frozen;
- shared Result background language;
- action hierarchy is obvious.

## Adaptive rules

- recovery panel remains readable on short screens;
- buttons remain reachable;
- source photo may reduce in height but remains contextually visible;
- no system bar overlap.

## Review status

UI structure: **CLOSED V1**

Copy: **CLOSED V1**

Recovery actions: **CLOSED V1**

Runtime parity: **NOT PART OF THIS DESIGN REVIEW**
