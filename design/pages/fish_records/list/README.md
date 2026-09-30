# My Catches V2.2

Role: **Chronological Archive / 时间记忆档案**

Status: **DESIGN FROZEN**
Freeze date: **2026-09-30**

## Current visual authority

- Populated Main: `design/pages/fish_records/list/frozen/main_v2_2/My_Catches_Populated_Main_V2_2_Frozen.webp`
- Timeline Scroll: `design/pages/fish_records/list/frozen/timeline_v1/My_Catches_Timeline_Scroll_V1_Frozen.webp`
- Filter F1–F5: `design/pages/fish_records/list/frozen/filter_v1/`
- Search B1–B4: `design/pages/fish_records/list/frozen/search_v1/`

The former Core UI seed `design/system/core_visual_v1/reference/my_catches_v2.png` is retained as historical input only. It is not current My Catches page authority because its old back/centered-header/search-filter composition and sunrise treatment conflict with the current shared systems.

## Page identity

My Catches is a memory timeline, not an analytics dashboard.

Current hierarchy:

1. `TopNavigation / TITLE_ONLY` — only “我的鱼获”
2. page-owned Search Field + independent Filter Action
3. archive summary
4. optional F1 inline filter panel
5. Month Sticky → Day → FishRecordRowCard
6. low-weight Growth Mark
7. fixed shared Camera Button

Search/Filter do not live inside `TITLE_ONLY`.

## Shared systems

- `Morning_Lake_Master_V1 / BG_DATA`
- `Top Navigation / TITLE_ONLY`
- `Primary Capture Button / archive_primary_capture`
- `FishRecordRowCard`
- `Growth Mark V1`
- shared typography / spacing / radius

## Frozen behavior

- newest first
- month header sticky
- day header scrolls normally
- 1–5 records: all visible
- 6–10: first 5 → inline expand/collapse
- >10: first 5 → day-detail view
- FishRecordRowCard → `FishRecordDetail(recordId)`
- return restores timeline/search/filter state
- list image always represents the original real catch photo

## Filter V1

Current V1 dimensions are exactly:

`鱼种 → 时间 → 尺寸 → 特殊记录`

Location is not a Filter V1 dimension. Location remains visible in records/day summaries and remains searchable.

## Design / runtime boundary

This package is **DESIGN FROZEN**. Android runtime parity remains a separate development handoff and does not change this visual/behavior authority.
