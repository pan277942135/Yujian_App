# FishRecordDetail Design Audit & Freeze V1

Audit date: **2026-09-30**  
Scope: **FishRecordDetail Design Manager 00–05**  
Base reviewed: **main @ 7d6d255354e162c77b15a2f2599b46c19bc49845**

## 1. Freeze result

The FishRecordDetail product model and scenario design are frozen.

One repository-level visual-source blocker remains:

`02 · B 面 · 鱼获记忆` has an approved exact PNG identity, but the exact binary is not currently available in the repository or recoverable from the available Library candidates.

Approved binary identity:

- 941 × 1672
- PNG / RGB
- 2,095,527 bytes
- SHA-256 `c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b`

Therefore:

- **Design decisions: FROZEN**
- **00 Overview: FROZEN**
- **01 A-side: FROZEN**
- **02 B-side spec: FROZEN**
- **02 canonical repository binary: BLOCKED_SOURCE**
- **03 Asset Generation: FROZEN**
- **04 Editing: FROZEN**
- **05 Page States: FROZEN**
- **Full repository visual package: not allowed to claim byte-complete FROZEN until 02 exact PNG gate passes**

## 2. Authority matrix

| Entry | Authority | Result |
|---|---|---|
| 00 · Overview | FishRecordDetail_Overview_Authority_V1.md | FROZEN |
| 01 · A 面 · 鱼获记录 | fish_record_detail_v2.png + README | FROZEN |
| 02 · B 面 · 鱼获记忆 | FishRecordDetail_B_Side_Visual_Authority_V1.md | SPEC FROZEN / BINARY BLOCKED |
| 03 · 鱼体资产生成 | FishRecordDetail_Asset_Generation_Spec_V1.md | FROZEN |
| 04 · 信息编辑 | FishRecordDetail_Editing_Spec_V1.md | FROZEN |
| 05 · 页面状态 | FishRecordDetail_Page_States_Spec_V1.md | FROZEN |

## 3. Key frozen decisions

- A / B are two surfaces of one FishRecordDetail.
- default entry is A-side.
- B-side lifecycle is NOT_GENERATED / GENERATING / READY / FAILED.
- first READY B-side may auto reveal A → B at most once per FishRecord.
- reveal is consumed only after B-side is actually visible.
- later entries always default to A-side.
- manual Flip Icon exists only while READY.
- Flip is an on-media utility, not a Top Navigation action.
- generation states remain inside the A-side page and do not block browsing.
- editing is one consolidated workspace.
- page-state failures are localized to the smallest responsible region.
- no internal queue / worker / GPU language appears in product UI.

## 4. Authority conflict cleanup

The current runtime `FishMemorySection.kt` still renders READY as a lower-page memory card with the generated image embedded in that section.

That runtime is **not** the frozen design authority.

Frozen design authority requires READY to expose the same FishRecordDetail B-side Hero surface and READY-only Flip behavior.

Development must migrate runtime toward the frozen design; design must not be rolled back to match the current implementation.

## 5. Background and shared systems

FishRecordDetail continues to use:

- Morning_Lake_Master_V1
- BG_CONTENT
- Top Navigation V1 / BACK_TITLE_ACTIONS
- Back + title + 鱼鉴 + 分享
- Text Action V1 for 编辑 >
- Icon Action V1 for Back / 鱼鉴 / 分享 / READY-only Flip
- Mist Glass / Color Typography / Spacing Radius shared systems

No new local component authority is introduced unless a shared-system gap is proven.

## 6. Batch closure rule

Future work on FishRecordDetail must not reopen 00–05 merely because runtime implementation is incomplete.

Allowed follow-up categories:

1. upload the exact approved B-side PNG and pass the SHA gate;
2. implement frozen runtime behavior;
3. add motion timing details without changing state semantics;
4. collect emulator / physical-device evidence;
5. fix a documented contradiction against frozen authority.

## 7. Final design status

**DESIGN FROZEN — SOURCE BINARY GATE OPEN**

The only open design-package gate is the exact 02 B-side canonical PNG byte identity.
