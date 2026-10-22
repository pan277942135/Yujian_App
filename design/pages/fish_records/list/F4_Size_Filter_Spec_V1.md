# F4 · 尺寸筛选 · Frozen Spec V1

Status: **FROZEN**
Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F4_Size_Filter_Frozen_V1.webp`
Parent: F1

## Frozen model

Two independent numeric ranges:

- length: min / max, unit cm
- weight: min / max, unit kg

## Rules

- empty range = no constraint for that measure
- only length set → filter by length only
- only weight set → filter by weight only
- both set → length AND weight
- records missing the required measurement must not falsely match
- min > max is invalid and must be corrected before closing
- segmented `长度 / 重量` changes edit focus only; it must not discard the other measure
