# 我的鱼获 V2 — Design Audit V1

Status: **DESIGN FROZEN**
Scope: **Design governance only**
Page ID: `my_catches_v2`

## 1. Current authority

Frozen visual:
`design/system/core_visual_v1/reference/my_catches_v2.png`

Page role:
**Chronological Archive / 时间记忆档案**

Background target:
`Morning Lake Background System V1 / BG_DATA`

Shared systems currently referenced:
- Background System / BG_DATA
- Primary Capture Button / archive_primary_capture
- Top Navigation / search_filter
- Mist Glass / GLASS_A
- Color + Typography
- Spacing + Radius

## 2. Approved information hierarchy

The page should remain a memory timeline rather than an analytics dashboard.

Recommended hierarchy:

1. 页面标题「我的鱼获」
2. 搜索
3. 筛选
4. 档案摘要
5. 月份分组
6. 日期 / 地点 / 当日摘要
7. FishRecordRowCard
8. Growth Mark（低权重）
9. 主拍摄入口

Real catch media remains more important than decorative background.

## 3. Background review

### Decision

`BG_DATA` remains the correct background class for My Catches.

Reason:
- page has high information density;
- repeated white/glass row cards require a low-salience environmental base;
- the lake world should remain perceptible but must not compete with search, timeline and catch thumbnails.

### Acceptance rule

Do not approve BG_DATA from an empty background sample alone.

BG_DATA must be reviewed on a populated My Catches screen containing:
- title;
- search;
- active filters;
- month/day headers;
- at least 3 FishRecordRowCards;
- at least one Growth Mark;
- bottom capture/navigation region.

The background is acceptable only when:
- mountain/lake world is still perceptible;
- timeline scanning is faster than background inspection;
- catch thumbnails remain the strongest repeated visual objects;
- glass/card boundaries remain readable without excessive opacity;
- the page does not collapse into a flat white list.

## 4. Search / filter audit

Current repository implementation exposes:
- Fish species
- Location
- Time

Species and location support multi-select.
Time supports:
- all
- last 7 days
- last 30 days
- this year

### Open design gap: MC-DESIGN-01

A broader five-dimension filter system was discussed in product design, but the current GitHub design authority does not archive the complete five-dimension frozen specification.

Do not invent the missing dimensions from runtime code.

Required closure:
- archive the final five dimensions;
- define large-dataset behavior for 50+ species / many locations;
- define search-inside-filter behavior where needed;
- define selected-chip overflow / clear-all behavior;
- define Filter Empty state against the final filter model.

## 5. Timeline audit

Current semantics are coherent:
- newest first;
- group by month;
- then by day;
- day summary may include one location + catch count;
- chronological grouping remains scrollable.

### Open design gap: MC-DESIGN-02

The repository lacks a dedicated frozen Timeline Scroll contract covering:
- sticky/non-sticky month header behavior;
- sticky/non-sticky day header behavior;
- scroll restoration;
- long-day behavior;
- transition between months;
- interaction with search/filter updates.

The page README is not sufficient as the long-term authority for these details.

## 6. FishRecordRowCard audit

Current shared card semantics are appropriate:
- thumbnail;
- species;
- length / weight;
- location;
- date;
- disclosure affordance;
- optional low-weight meaningful-record annotation.

The card should remain visually stronger than the timeline furniture and weaker than a page-level hero.

## 7. Growth Mark audit

Current semantic types in the repository:
- Longest
- Heaviest
- FirstSpecies
- FirstLocation

The resolver deliberately returns all applicable marks.

### Open design gap: MC-DESIGN-03

The frozen repository authority does not define multi-tag display priority.

Required closure:
- priority when multiple marks apply to one record;
- maximum visible marks per card;
- overflow behavior;
- visual distinction, if any, among the four meanings;
- placement relative to measurement/location/date;
- behavior on narrow screens.

Growth Mark remains secondary to the fish record itself.

## 8. Capture entry audit

The page README and Core UI validation both expect a shared primary capture entry.

### Open handoff gap: MC-HANDOFF-01

Current `MyScreen.kt` uses the shared camera button in Archive Empty, but the non-empty timeline screen does not expose the persistent capture entry described by the page design contract.

This is a future development-handoff issue, not a reason to change the frozen design now.

## 9. Empty-state audit

The repository correctly separates:
- Archive Empty
- Search Empty
- Filter Empty

These states should continue to share the same page world and must not introduce a new decorative background.

Archive Empty may expose the primary capture action.
Search Empty / Filter Empty should prioritize recovery actions rather than camera capture.

## 10. Current design conclusion

Keep the current page identity and chronological archive model.

Do not redesign My Catches from scratch.

Before declaring the complete page experience FROZEN, close:
- MC-DESIGN-01 filter system;
- MC-DESIGN-02 timeline-scroll contract;
- MC-DESIGN-03 Growth Mark priority/overflow;
- BG_DATA populated-page visual acceptance.

Motion / Haptic / Sound remain separate design-system gaps tracked by Design Manager.


---

## 11. 2026-09-29 authority recovery update

The following previously discussed / frozen design decisions have now been restored as explicit repository authority:

- `My_Catches_List_Image_Spec_V1.md`
- `My_Catches_Filter_Spec_V1.md`
- `My_Catches_Timeline_Scroll_Spec_V1.md`
- `My_Catches_Growth_Mark_Spec_V1.md`

Therefore:
- MC-DESIGN-01 is resolved at design-authority level;
- MC-DESIGN-02 is resolved at design-authority level;
- MC-DESIGN-03 is resolved at design-authority level.

Remaining design review blocker:
- BG_DATA populated-page visual acceptance;
- final whole-page review after background approval.

Runtime differences remain development-handoff items and do not invalidate the recovered design authority.


---

## 12. High-fidelity source reconciliation

The prior high-fidelity design boards were recovered and reviewed against the text specs.

Corrections applied:
1. Growth Mark first-event semantics = first record **of a species** (`首条草鱼` etc.), not the user's first catch overall.
2. Day groups with 6–10 catches expand inline after the first 5; day groups with >10 catches route to a dedicated day-detail view after the first 5.
3. Search Focused includes a visible Cancel action.
4. The persistent shared Camera Button remains visible in Archive Empty, Search Empty and Filter Empty; it is not the recovery CTA for search/filter.
5. Historical exploratory comparison boards remain non-authoritative.

Current Design Manager navigation is organized by high-fidelity view:
Main / Timeline / Filter / Empty States / Growth Mark / Search / System states.


---

## 13. 2026-09-30 final closure

Status: **DESIGN FROZEN**

Resolved:
- Filter authority conflict: old five-dimensional Bottom Sheet is SUPERSEDED; F1–F5 current four-dimensional inline model is authoritative.
- Top Navigation conflict: My Catches base page uses shared TITLE_ONLY only; Search / Filter are page-owned content utilities.
- F1 visual source is now a full 941×1672 raster authority, not the prior 360×640 low-resolution JPEG.
- F2–F5 visual + behavior authority completed.
- Timeline high-fidelity closure completed with seven states.
- BG_DATA populated-page review completed against current dense archive content.
- current populated main visual moved to V2.2 page-owned authority.

Current design authority does not depend on Android runtime parity. Runtime gaps remain separate engineering handoff items.
