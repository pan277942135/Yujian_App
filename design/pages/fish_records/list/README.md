# My Catches V2

Role: **Chronological Archive**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/my_catches_v2.png
- SHA-256: d88e1542103aaa6af98da036b62ed6b18fca8e1b870dcc817fecea27e05072bd
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

## Structure
- search / filter
- month grouping
- date timeline
- location/day summary
- FishRecordRowCard list
- shared Camera Button

## Visual rule
This is a memory timeline, not a sports analytics dashboard.

Meaningful-record annotations such as 最大记录 / 首条草鱼 / 最长记录 remain visually below the fish record itself.

Each record opens FishRecordDetail(recordId).


## Icon Action

- Standalone Filter trigger → Icon Action V1 / UTILITY / ON_LIGHT.
- A Search Field leading magnifier is decoration when it is not independently tappable and must not be implemented as Icon Action.
- Filter pills/chips remain Selection Controls, not Icon Action.


## Top Navigation

- Root header uses **Top Navigation V1 / TITLE_ONLY / FROZEN**.
- Title copy: `我的鱼获`.
- Typography: 28sp / Medium / 34sp, DeepLakeBlue.
- Search / Filter stay below Top Navigation.
- The legacy runtime subtitle `按时间留存每一次真实鱼获` is page content, not part of TITLE_ONLY.
- Legacy 29sp/Bold title styling must not override the shared component authority.
