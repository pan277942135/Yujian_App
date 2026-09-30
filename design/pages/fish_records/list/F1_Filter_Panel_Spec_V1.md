# F1 · 筛选面板 · Frozen Spec V2

Status: **FROZEN**
Freeze date: **2026-09-30**
Owner: **My Catches / 我的鱼获**
Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F1_Filter_Panel_Frozen_V2.webp`

## 1. Product role

F1 is a **Top Inline Filter Panel** inside My Catches page content.

It is not a new page and not a Bottom Sheet.

Shared `TopNavigation / TITLE_ONLY` remains authoritative and contains only the page title. The Search Field and Filter Action are page-owned utilities below Top Navigation. Tapping the Filter Action expands F1 beneath the archive summary and pushes Timeline content downward.

## 2. Frozen dimensions and order

1. **鱼种**
2. **时间**
3. **尺寸**
4. **特殊记录**

**地点 does not enter Filter V1.** Do not keep a placeholder merely to preserve an old “five-dimension” concept.

## 3. Quick controls

### Fish species
`不限 / 草鱼 / 鲫鱼 / 鲤鱼 / 更多 ›`

- multi-select
- same dimension = OR
- `更多 ›` → F2

### Time
`不限 / 本月 / 近3个月 / 今年 / 自定义 ›`

- single-select
- `自定义 ›` → F3

### Size
Segmented edit focus: `长度 / 重量`

Length presets:
`不限 / <20 cm / 20–40 cm / 40–60 cm / ≥60 cm / 自定义 ›`

Weight presets:
`不限 / <0.5 kg / 0.5–1 kg / 1–3 kg / ≥3 kg / 自定义 ›`

- `自定义 ›` → F4
- length and weight may coexist; if both are set, relation = AND

### Special record
`不限 / 首条 / 最长 / 最重 / 里程碑`

- multi-select
- same dimension = OR
- advanced selection → F5
- does not copy Achievement / trophy visual language

## 4. Combination rule

- same dimension multi-select = **OR**
- different dimensions = **AND**
- Search + Filter = **SearchMatch AND FilterMatch**
- `不限` = no constraint for that dimension

## 5. Interaction

- Filter Action expands/collapses F1.
- Any chip click takes effect immediately and refreshes Timeline.
- There is **no “应用 / 查看 N 条 / 完成” button in F1**.
- `重置` clears all filters immediately.
- Active filters add only a low-weight indicator to the page-owned Filter Action.
- Timeline position updates to the first/latest visible result after a filter change.

## 6. Visual

- same BG_DATA world as populated My Catches
- panel: Mist / Lake White, restrained opacity
- no full-screen mask
- no heavy glass / heavy shadow
- radius target ≈24dp
- chip touch target ≥44dp
- Selected: light Lake Teal surface + Deep Teal text
- no badge wall / colored icons / game styling

## 7. Child authority

- F1 · 筛选面板 — **FROZEN**
- F2 · 鱼种选择 — **FROZEN**
- F3 · 时间选择 — **FROZEN**
- F4 · 尺寸筛选 — **FROZEN**
- F5 · 特殊记录 — **FROZEN**

Filter-result and Filter-empty are not independent filter pages:
- results reuse the normal Timeline structure;
- empty reuses `My_Catches_Empty_States_Spec_V1.md / Filter Empty`.
