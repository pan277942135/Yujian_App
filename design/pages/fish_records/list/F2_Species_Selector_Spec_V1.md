# F2 · 鱼种选择 · Frozen Spec V1

Status: **FROZEN**
Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F2_Species_Selector_Frozen_V1.webp`
Parent: F1

## Scope

F2 carries species selection that is too large for F1 quick chips.

## Frozen structure

- title: `选择鱼种`
- species search field
- `最近选择`
- `全部鱼种`
- explicit multi-select checkboxes
- selected state always visible

## Rules

- multi-select; same dimension = OR
- 50+ species must remain searchable / scrollable
- selecting does not close the selector
- `完成` closes F2 and returns to F1; it does not act as a global “apply filter” command
- state survives return to F1
- no location selection here
