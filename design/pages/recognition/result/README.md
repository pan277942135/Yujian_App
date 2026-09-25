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
- 保存鱼获 → create FishRecord → Normal Home
- 保存并记录记忆 → create FishRecord first → FishRecordDetail(recordId, initialSection=MEMORY)

The FishRecord must exist before entering memory-media operations.
