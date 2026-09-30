# 我的鱼获 · Filter V1 Current Contract V2

Status: **FROZEN — CURRENT AUTHORITY**  
Page: `my_catches_v2`  
Supersedes: `My_Catches_Filter_Spec_V1.md` (historical / deprecated)

## 1. V1 filter model

The current Filter V1 has four dimensions in this fixed order:

1. 鱼种
2. 时间
3. 尺寸
4. 特殊记录

**地点 is not a V1 filter dimension.** Do not expose a location row or retain a location placeholder in F1.

Combination rules:

- Multiple selections inside one dimension use **OR**.
- Different dimensions use **AND**.
- Search and Filter combine as `SearchMatch AND FilterMatch`.
- “不限” clears the condition for that dimension.

## 2. F1 · top inline filter panel

F1 expands inside the existing My Catches page between the header/summary and timeline. It is not a Bottom Sheet or a separate route. The timeline moves down while F1 is open.

- F1 order: species → time → size → special records.
- High-frequency choices apply immediately and refresh the current timeline.
- No Apply / Done / “View N records” CTA.
- Reset clears all conditions immediately; it is disabled or visually lowered when there are no conditions.
- F1 layout and visual details remain governed by `F1_Filter_Panel_Spec_V1.md` and its frozen PNG.

## 3. Extended selection states

F2 species selection reuses `species_picker_v1 / MULTI_SELECT`; selections persist while browsing, multiple species can be selected, and return restores F1. Species selected within F2 combine with OR.

F3 custom time selection is a single date-range condition and returns to F1.

F4 custom size selection keeps length (cm) and weight (kg) ranges independently. Empty ranges impose no condition; when both are set, both must match.

F5 special records use the four Growth Mark semantics: first record for a species, quantity milestone, longest for the same species, and heaviest for the same species. Multiple selections combine with OR.

## 4. Results and empty states

Filtering keeps the existing archive structure:

`Month → Day → FishRecordRowCard`

Date and catch summaries are recomputed from visible records. Selected conditions appear in a compact removable summary; clear-all remains available.

When records exist, a filter is active, and no records match, use the Filter Empty state in `My_Catches_Empty_States_Spec_V1.md`. This is not the first-use archive empty state.

## 5. Visual status by child

- F1: FROZEN high-fidelity visual authority.
- F2: PARTIAL; shared picker board is a single-select visual baseline, while My Catches multi-select remains to be closed.
- F3 / F4: MISSING standalone high-fidelity states.
- F5 / F6 / F7: PARTIAL where existing behavior or shared visuals are reusable; no missing screen is inferred or recreated.

The recovered five-dimension product board and earlier F1 board remain historical references only. They do not override this contract or the frozen F1 PNG.
