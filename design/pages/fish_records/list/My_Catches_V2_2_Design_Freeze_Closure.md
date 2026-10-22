# My Catches V2.2 · Design Freeze Closure

Status: **DESIGN FROZEN**
Date: **2026-09-30**
Frozen base: `ac2fe3a7bbf6feb172910a980be731cd586483c1`

## Final visual authorities

- Main: `frozen/main_v2_2/My_Catches_Populated_Main_V2_2_Frozen.webp` — 941×1672
- Timeline: `frozen/timeline_v1/My_Catches_Timeline_Scroll_V1_Frozen.webp` — 1800×1250
- F1: `frozen/filter_v1/F1_Filter_Panel_Frozen_V2.webp` — 941×1672
- F2: `frozen/filter_v1/F2_Species_Selector_Frozen_V1.webp` — 941×1672
- F3: `frozen/filter_v1/F3_Time_Selector_Frozen_V1.webp` — 941×1672
- F4: `frozen/filter_v1/F4_Size_Filter_Frozen_V1.webp` — 941×1672
- F5: `frozen/filter_v1/F5_Special_Record_Filter_Frozen_V1.webp` — 941×1672

All are raster images; no SVG reconstruction is used for these frozen views.

## Authority decisions

### Top Navigation

`TITLE_ONLY` is the only base-page shared Top Navigation authority.

### Filter

Current V1:

`鱼种 → 时间 → 尺寸 → 特殊记录`

No Location filter in V1.

F1 = inline / immediate update / no Apply.  
F2–F5 = focused secondary selectors.

### Timeline

Newest-first, Month Sticky, Day non-sticky, 1–5 expanded, 6–10 inline expansion, >10 day-detail, scroll restoration.

### BG_DATA populated QA

**PASS**

Acceptance:
- lake world remains visible but subordinate;
- row scanning wins over background inspection;
- real catch thumbnails remain strongest repeated objects;
- glass/white surfaces remain legible;
- page does not collapse into a flat white list;
- no Empty-Home sunrise hero treatment is used.

## Freeze boundary

Design is frozen. Android runtime, CI, motion, haptic and sound closure are separate workstreams and must not reopen this design package without an explicit design version bump.
