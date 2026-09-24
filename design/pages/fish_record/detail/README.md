# FishRecordDetail V2

Role: **Memory Archive Baseline**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/fish_record_detail_v2.png
- SHA-256: 3bb0fd5fd38d0721f5ac89489c224deae29c09401e2c9239fcf9435b320eeb58
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

This is the single long-term detail destination for one FishRecord.

Entry points:
- Normal Home recent-catch card
- My Catches record card
- Recognition Result “保存并记录记忆” after successful FishRecord creation
- later search/history surfaces

## Structure
1. Top navigation
2. YuJianCatchHeroCard
3. 关于这次鱼获
4. 鱼获记忆
5. media continues vertically

## Detail Hero
- species
- length · weight · location
- no time in the current V2 visual
- low-weight 编辑 >

## Memory actions
- 添加照片/视频
- 继续拍照
- 录制视频

When entered from “保存并记录记忆”, scroll/focus to Memory without creating a separate enrichment page.
