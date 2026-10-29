# 分享模板 · 时间范围与数据规则 V1

Status: **FROZEN**

## 1. Current ranges

- 今天
- 本周
- 本月
- 自定义

Time range is independent from template type.

## 2. Source of truth

Share output is derived from real FishRecord data within the selected range.

Do not use hardcoded demo statistics as production truth.

## 3. Core aggregates

Depending on template:
- catch count;
- species count;
- largest/selected highlight;
- species × count;
- valid length/weight data;
- record-supported location/time.

## 4. Species aggregation

Species count means distinct confirmed/committed species represented by the selected FishRecords.

Do not treat an unconfirmed model candidate as a confirmed species.

## 5. Largest catch

The metric must state/derive from an available comparable field.

If the product has not established whether “largest” means length or weight for a context, do not silently choose a different criterion per output.

## 6. Missing metrics

Missing ≠ zero.

A template must reflow rather than fabricate.

## 7. Location

Only show:
- explicit FishRecord location;
- an aggregate place when the selected records support that statement.

Mixed-location data must not be collapsed into a false single place.

## 8. Historical period model

`单条 / 今日 / 本周 / 本月 / 本年 / 累计` is retained only as historical P08 context.

Current V1 does not define six separate templates from those labels.
