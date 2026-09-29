# Recognition Result V1

Role: **Capture → Record Bridge**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/recognition_result_v1.png
- SHA-256: c4c9d77084dd3547cacba31a45c5bf4766a77dd8844d504a980471e0b93d8482
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

## Responsibility
Fast fish-species confirmation + lightweight base information entry.

Contains:
- primary image
- species + 修改鱼种
- length
- weight
- location
- short story / voice input

Do not add media-gallery complexity to this screen.

## CTA contract

Shared authority: `design/system/components/action_button/Action_Button_Spec_V1_1.md`

### Normal / confirmed result
- `保存本次鱼获` → **PRIMARY** → create FishRecord → Normal Home
- `继续记录记忆` → **SECONDARY_STRONG** → create FishRecord first → FishRecordDetail(recordId, initialSection=MEMORY)

Current paired order:
`[继续记录记忆] [保存本次鱼获]`

### Low-confidence correction
- `手动选择` → **SECONDARY_STRONG**
- `重新拍摄` → **SECONDARY_MUTED**

Current paired order:
`[手动选择] [重新拍摄]`

`重新拍摄` is intentionally lower priority and must not become PRIMARY.

The FishRecord must exist before entering memory-media operations.


## Text Action

- `修改鱼种` uses shared Text Action V1 / NORMAL / LIGHT.
- Chevron is a separate 14dp icon; it is not part of the localized text string.


## Top Navigation

- Uses **Top Navigation V1 / BACK_TITLE / FROZEN**.
- Title: `识别结果`.
- Layout: 56dp min height / 8dp outer padding / 44dp Back target / 8dp Back-title gap.
- Title: 20sp / Medium / 26sp / DeepLakeBlue.
- Back uses Icon Action V1 / NAVIGATION.
- Legacy centered `25sp / Bold` title and text glyph `‹` are not authority and must be removed during runtime closure.
