# T01 · 战绩卡 · Spec V1

Status: **FROZEN — CONTENT / BEHAVIOR**
Visual status: **PARTIAL — recovered iterations, no unique final binary**

## 1. Role

T01 summarizes fishing results for one selected time range.

It should feel like a strong but credible fishing record, not a game ranking screen.

## 2. Time range

Supported by the shared time-range authority:
- 今天
- 本周
- 本月
- 自定义

Template identity does not change with time range.

## 3. Primary visual

Primary visual source:
- Fish Sticker / Fish Knowledge fish asset authority.

The fish must correspond to real FishRecord data.

Do not:
- invent a fish;
- change species;
- fabricate length/weight;
- substitute unrelated decorative fish.

## 4. Required content hierarchy

1. period title / time range;
2. optional location when meaningful for the selected records;
3. total catch count;
4. total species count;
5. Top 1–3 fish highlights when enough valid records exist;
6. remaining species/count summary;
7. YuJian brand/signature treatment.

## 5. Top 1–3

T01 may highlight Top 1–3 real catches.

Rules:
- ranking criterion must be defined by real data/context;
- do not invent records to fill Top 2/3;
- fewer valid records → show fewer highlights;
- no fake crown/rarity/power/level semantics;
- ranking is informational, not competitive game scoring.

## 6. Other catches

All non-highlighted species must remain represented.

Preferred semantic form:
`鱼种 × 数量`

Do not silently omit a species just because it is outside Top 3.

## 7. Missing data

Never fabricate:
- count;
- species;
- weight;
- length;
- location;
- date;
- record rank.

When a metric is unavailable:
- omit/reflow according to the fixed template;
- do not display fake zero unless zero is semantically true.

## 8. Copy

Do not auto-generate generic motivational/chicken-soup copy as factual user memory.

Template headings/brand copy may be fixed.

Personal narrative belongs to user-provided content.

## 9. Visual boundary

Prior direction favors:
- clear statistics;
- strong Fish Sticker hierarchy;
- restrained natural palette;
- premium but not game-like composition.

Recovered images are iteration references, not final visual authority.
