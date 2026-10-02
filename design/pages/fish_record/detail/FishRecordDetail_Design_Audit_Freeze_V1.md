# FishRecordDetail Design Audit & Freeze V1

Audit date: **2026-10-02**  
Scope: **FishRecordDetail Design Manager 00–05**  
Base reviewed: **main @ cc780f6bc783cc197be475f9fcc08bf87f349cda**

## 1. Freeze result

The FishRecordDetail product model and scenario design are frozen. The exact approved B-side PNG is present at its canonical repository path, and the repository README and manifest record the same approved identity.

Approved binary identity:

- 941 × 1672
- PNG / RGB
- 2,095,527 bytes
- SHA-256 `c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b`

Current result:

- **Design decisions: FROZEN**
- **00 Overview: FROZEN**
- **01 A-side: FROZEN**
- **02 B-side spec and canonical binary: FROZEN / SOURCE BINARY GATE PASS**
- **03 Asset Generation: FROZEN**
- **04 Editing: FROZEN**
- **05 Page States: FROZEN**
- **FishRecordDetail design package: DESIGN FROZEN**

## 2. Authority matrix

| Entry | Authority | Result |
|---|---|---|
| 00 · Overview | FishRecordDetail_Overview_Authority_V1.md | FROZEN |
| 01 · A 面 · 鱼获记录 | fish_record_detail_v2.png + README | FROZEN |
| 02 · B 面 · 鱼获记忆 | FishRecordDetail_B_Side_Visual_Authority_V1.md + canonical PNG | FROZEN |
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

## 4. Existing asset and historical-design mapping

- **Current B-side visual authority:** `frozen/FishRecordDetail_B_Side_V1_Frozen.png`. This exact original PNG is the sole visual authority for menu 02; do not replace, crop, resize, re-encode, or reconstruct it.
- **Generation behavior authority:** `FishRecordDetail_Asset_Generation_Spec_V1.md`, owned by menu 03. It covers NOT_GENERATED / GENERATING / FAILED and the READY handoff; it does not create another B-side visual authority.
- **Historical unlock storyboard:** `渔见数字鱼体解锁流程 storyboard.png` (Library source, 2026-09-16) remains a historical exploration. It shows a fabricated 10–30 second ETA and manual-only flip, both superseded by the frozen 03 no-ETA rule and the 00 one-time first READY reveal.
- **Historical six-state board:** `渔见鱼获记忆六状态展示.png` remains historical. Its queue/processing/failure screens are job-state breakdowns, not the current four user-facing lifecycle states.
- The historical boards should not be promoted as current Hi-Fi or runtime requirements. Keep the active 02 and 03 menu structure compact.

## 5. Authority conflict cleanup

The current runtime `FishMemorySection.kt` still renders READY as a lower-page memory card with the generated image embedded in that section.

That runtime is **not** the frozen design authority.

Frozen design authority requires READY to expose the same FishRecordDetail B-side Hero surface and READY-only Flip behavior.

Development must migrate runtime toward the frozen design; design must not be rolled back to match the current implementation.

## 6. Background and shared systems

FishRecordDetail continues to use:

- Morning_Lake_Master_V1
- BG_CONTENT
- Top Navigation V1 / BACK_TITLE_ACTIONS
- Back + title + 鱼鉴 + 分享
- Text Action V1 for 编辑 >
- Icon Action V1 for Back / 鱼鉴 / 分享 / READY-only Flip
- Mist Glass / Color Typography / Spacing Radius shared systems

No new local component authority is introduced unless a shared-system gap is proven.

## 7. Batch closure rule

Future work on FishRecordDetail must not reopen 00–05 merely because runtime implementation is incomplete.

Allowed follow-up categories:

1. implement frozen runtime behavior;
2. add motion timing details without changing state semantics;
3. collect emulator / physical-device evidence;
4. fix a documented contradiction against frozen authority.

## 8. Final design status

**DESIGN FROZEN — SOURCE BINARY GATE PASS**

The B-side binary identity gate is closed. Historical asset/storyboard material remains clearly separated from current 02/03 authority.
