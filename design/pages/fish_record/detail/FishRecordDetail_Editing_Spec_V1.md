# FishRecordDetail Editing Spec V1

Status: **FROZEN**  
Freeze date: **2026-09-30**  
Design Manager owner: **04 · 信息编辑**
Visual Authority: `design/pages/fish_record/detail/frozen/editing/FishRecordDetail_Editing_V1_Frozen.png`
Source: `湖畔鱼获编辑界面.png`
Image: PNG / RGB, 941 × 1672, 1,542,547 bytes
SHA-256: `d81495f760840c0411aa693bb8b3b39fc1d65e012387350732047f1272250114`

## 1. Product role

Editing modifies the same FishRecord. It must not create a second record or fork A/B identity.

Entry is the low-weight **编辑 >** action in the Hero.

## 2. Frozen editing model

Use one consolidated editing workspace instead of separate menu pages.

Editable groups:

### 基础信息
- 鱼种
- 长度
- 重量

### 环境与记录
- 时间
- 地点
- 天气（when supported by the data model）
- 感言 / note

The editing surface may use a full-height sheet or full-screen form at runtime, but the information architecture is frozen: one editing session, grouped fields, one Save action.

## 3. Entry / exit

Entry:
- Tap **编辑 >** from FishRecordDetail.
- Current FishRecord values populate the form.
- A/B surface identity does not change.

Exit:
- Save → validate → persist → return to the same FishRecordDetail.
- Cancel / Back with no changes → return immediately.
- Cancel / Back with unsaved changes → show discard confirmation.

## 4. Validation

Species:
- must resolve to a supported catalog species or the product’s explicit unknown/manual value;
- changing species updates the FishRecord identity metadata, not the original media bytes.

Length / Weight:
- optional unless the product flow explicitly made them required;
- numeric;
- positive;
- unit labels are fixed and not typed into the value field.

Time:
- valid date/time only;
- future timestamps beyond the allowed capture policy are rejected.

Location:
- user-visible place text may be edited;
- editing display location must not silently invent precise coordinates.

Note:
- plain user content;
- preserve line breaks;
- apply the product-wide max length if one exists; otherwise do not invent a hidden truncation rule in UI.

## 5. Save state

Normal:
- top action or bottom primary action: **保存**
- disabled only when validation fails or save is actively in progress.

Saving:
- prevent duplicate submits;
- preserve typed values;
- use restrained progress;
- do not leave the page until persistence succeeds.

Save failure:
- remain in editing surface;
- keep all user-entered values;
- show local error plus **重试**;
- do not roll fields back silently.

## 6. Destructive action

Record deletion is not a peer field.

If deletion is supported:
- place it at the bottom of the editing workspace;
- use explicit destructive styling;
- require confirmation;
- confirmation names the consequence: this FishRecord and its bound memory/media references may become unavailable according to storage policy.

No swipe-to-delete and no one-tap destructive icon in the Hero.

## 7. Relationship to B-side

Editing the FishRecord does not create a new B-side.

After edits:
- metadata text on A and B surfaces must remain consistent;
- if an edit invalidates a generated asset semantically, runtime may mark the B-side unavailable and return lifecycle to a non-READY state;
- until such invalidation is explicitly defined by backend/product logic, do not silently delete a READY asset.

## 8. Shared component authority

- Top Navigation follows the product-wide edit surface authority.
- Text inputs, selectors, dialogs and buttons use shared component specs.
- **编辑 >** remains Text Action / NORMAL / ON_MEDIA in the Hero.
- destructive confirmation uses the shared dialog pattern.

## 9. Acceptance gate

1. one consolidated editing workspace;
2. fields grouped into 基础信息 / 环境与记录;
3. Save / Cancel / unsaved-change behavior defined;
4. validation does not fabricate unavailable data;
5. save failure preserves user input;
6. deletion, if enabled, requires confirmation;
7. edits preserve one-FishRecord A/B identity.
