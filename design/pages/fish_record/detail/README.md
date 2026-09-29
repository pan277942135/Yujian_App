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
- low-weight 编辑 + trailing chevron

## Memory actions
- 添加照片/视频
- 继续拍照
- 录制视频

When entered from “保存并记录记忆”, scroll/focus to Memory without creating a separate enrichment page.


## Text Action

- Hero `编辑` uses shared Text Action V1 / NORMAL / ON_MEDIA.
- Trailing chevron is a separate 14dp icon; `>` / `›` is not part of the localized copy.


## Icon Action

- Top navigation Back → Icon Action V1 / NAVIGATION / ON_LIGHT.
- Fish Guide and Share → Icon Action V1 / UTILITY / ON_LIGHT.
- B-side manual flip → Icon Action V1 / UTILITY / ON_MEDIA; only visible when B-side exists.
- Card flip motion belongs to the card/content; the flip icon itself does not rotate.


## Top Navigation

FishRecordDetail uses **Top Navigation V1 / BACK_TITLE_ACTIONS / FROZEN**.

Frozen layout:

```text
←  鱼获详情                         鱼鉴   分享
```

- min content height: 56dp;
- outer padding: 8dp;
- Back: Icon Action V1 / NAVIGATION / 44dp target / 22dp glyph;
- title: 20sp / Medium / 26sp / DeepLakeBlue;
- Fish Guide: Icon Action V1 / UTILITY / ON_LIGHT;
- Share: Icon Action V1 / UTILITY / ON_LIGHT;
- Utility target: 44dp;
- Utility gap: 8dp;
- maximum visible Utility slots: 2.

Boundary:

- Hero `编辑` stays Text Action V1 / NORMAL / ON_MEDIA;
- B-side manual Flip stays local Icon Action V1 / UTILITY / ON_MEDIA;
- Add Media stays in the content/context layer;
- none of these are Top Navigation actions.

Current Runtime `YuJianTopBar` is not yet pixel-aligned with the frozen secondary-page typography/height; runtime parity is a separate closure task.
