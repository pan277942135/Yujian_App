# Recognition Result V1

Role: **Capture → Record Bridge**

Status: visual source registered; binary Git copy pending.

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
