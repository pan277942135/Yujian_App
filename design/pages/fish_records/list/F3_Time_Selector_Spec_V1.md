# F3 · 时间选择 · Frozen Spec V1

Status: **FROZEN**
Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F3_Time_Selector_Frozen_V1.webp`
Parent: F1

## Scope

F3 only owns **custom continuous date range** selection. F1 continues to own the quick presets: 不限 / 本月 / 近3个月 / 今年.

## Rules

- one start date + one end date
- start <= end
- time dimension is single-range, not multi-select
- range is shown explicitly before closing
- `完成` returns to F1
- changing the range updates Filter state; F1 remains the source of the final combined filter summary
