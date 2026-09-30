# FishRecordDetail Page States Spec V1

Status: **FROZEN**  
Freeze date: **2026-09-30**  
Design Manager owner: **05 · 页面状态**

## 1. Scope

05 owns structural page states and fallbacks. It does not duplicate B-side lifecycle states from 03.

Frozen state board:

1. Loading
2. Media Missing
3. Partial Data
4. Deleted / Invalid Record
5. Offline / Network Error
6. Retry / Disabled / Fallback behavior

## 2. Loading

Goal: preserve page identity without flashing incorrect content.

Required:
- keep Top Navigation / page frame stable when practical;
- use restrained skeletons for Hero and major content blocks;
- do not show stale species / measurements as if confirmed;
- do not auto-navigate away during transient load.

## 3. Media Missing

If the original catch photo is unavailable but the FishRecord exists:

- keep FishRecordDetail accessible;
- Hero uses a neutral media fallback inside the same geometry;
- species and factual metadata remain visible;
- do not substitute a B-side asset, Fish Guide artwork or unrelated catch photo;
- media failure must not mutate B-side lifecycle.

If a supplemental photo/video is missing:
- isolate the failure to that media tile.

## 4. Partial Data

FishRecord fields are independently optional.

Rules:
- never display fake placeholders such as `0.0 kg`, `-- cm` or invented location;
- collapse absent secondary facts cleanly;
- keep species if known;
- if species is unresolved, use the product’s explicit unknown/manual state;
- preserve the Hero hierarchy even when measurement/location lines shorten.

Examples:
- no weight → show length + location if present;
- no length/weight → show location only;
- no location → show available measurements only;
- no note → omit note body, do not fabricate copy.

## 5. Deleted / Invalid Record

When the target FishRecord no longer exists or is invalid:

- retain page identity with Back;
- message: **这条鱼获记录已不可用**
- supporting copy may explain that it was removed or cannot be found;
- do not show stale cached editable content as authoritative;
- primary action: **返回我的鱼获** when routing context supports it;
- no generation / edit / share actions.

## 6. Offline / Network Error

### Cached record available
- render cached factual content;
- mark network-dependent actions unavailable only where necessary;
- do not replace the whole page with an error screen.

### No cached record
- keep FishRecordDetail page shell;
- message: **暂时无法打开这条鱼获**
- action: **重新加载**
- Back remains available.

## 7. Local action failure

Share / add media / generation / save failures are local failures unless the record itself cannot load.

Principle:
**fail the smallest responsible region.**

Do not turn a media upload failure into a full FishRecordDetail error page.

## 8. Disabled and fallback rules

- disabled controls must have a factual reason;
- unavailable optional features may be hidden rather than disabled if no user action can resolve them;
- no spinner may run indefinitely without a state transition policy;
- no placeholder can masquerade as real user data;
- fallback visuals must preserve layout but remain visibly neutral.

## 9. Accessibility / responsive

- loading announcements are non-repetitive;
- error actions have explicit labels;
- Dynamic Type / font scaling may increase vertical height;
- page remains vertically scrollable on short screens;
- no nested vertical scrolling for the primary page.

## 10. Acceptance gate

1. six state families exist in one State Board;
2. media failure is localized;
3. partial data never fabricates values;
4. cached offline content is preferred over whole-page failure;
5. invalid record removes unsafe actions;
6. retry targets the smallest failed region;
7. page identity and Back behavior remain stable.
