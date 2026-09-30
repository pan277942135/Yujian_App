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

## 4. Fish Memory media collection

### No Uploaded Memory / 无上传记忆

Condition:
- the FishRecord exists;
- primary record data is available;
- the Fish Memory media collection count is 0.

Visual:
- use the canonical frozen PNG at `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_No_Uploaded_Memory_V1_Frozen.png`;
- preserve A-side page identity, Hero, and factual catch details;
- show an empty-media state in the Fish Memory section;
- do not fabricate or imply that any photo/video exists.

Actions:
- **添加照片/视频**
- **继续拍照**
- **录制视频**

Boundary:
- this is a valid FishRecord with an empty Fish Memory media collection;
- it is distinct from Media Missing, where media was expected or referenced but is unavailable;
- it is unrelated to B-side asset-generation lifecycle;
- B-side may independently be NOT_GENERATED / GENERATING / READY / FAILED.

## 5. Partial Data

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

## 6. Deleted / Invalid Record

When the target FishRecord no longer exists or is invalid:

- retain page identity with Back;
- message: **这条鱼获记录已不可用**
- supporting copy may explain that it was removed or cannot be found;
- do not show stale cached editable content as authoritative;
- primary action: **返回我的鱼获** when routing context supports it;
- no generation / edit / share actions.

## 7. Offline / Network Error

### Cached record available
- render cached factual content;
- mark network-dependent actions unavailable only where necessary;
- do not replace the whole page with an error screen.

### No cached record
- keep FishRecordDetail page shell;
- message: **暂时无法打开这条鱼获**
- action: **重新加载**
- Back remains available.

## 8. Local action failure

Share / add media / generation / save failures are local failures unless the record itself cannot load.

Principle:
**fail the smallest responsible region.**

Do not turn a media upload failure into a full FishRecordDetail error page.

## 9. Disabled and fallback rules

- disabled controls must have a factual reason;
- unavailable optional features may be hidden rather than disabled if no user action can resolve them;
- no spinner may run indefinitely without a state transition policy;
- no placeholder can masquerade as real user data;
- fallback visuals must preserve layout but remain visibly neutral.

## 10. Accessibility / responsive

- loading announcements are non-repetitive;
- error actions have explicit labels;
- Dynamic Type / font scaling may increase vertical height;
- page remains vertically scrollable on short screens;
- no nested vertical scrolling for the primary page.

## 11. Acceptance gate

1. all six existing state families remain under 05, with No Uploaded Memory separately represented;
2. media failure is localized;
3. partial data never fabricates values;
4. cached offline content is preferred over whole-page failure;
5. invalid record removes unsafe actions;
6. retry targets the smallest failed region;
7. No Uploaded Memory means a valid record with a zero-item memory media collection and is distinct from Media Missing;
8. B-side lifecycle remains independent;
9. page identity and Back behavior remain stable.
