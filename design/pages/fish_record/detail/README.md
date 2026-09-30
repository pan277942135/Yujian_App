# FishRecordDetail V2

Role: **Memory Archive Baseline**

Status: **FROZEN**

## Frozen visual reference

- Canonical reference: design/system/core_visual_v1/reference/fish_record_detail_v2.png
- SHA-256: 3bb0fd5fd38d0721f5ac89489c224deae29c09401e2c9239fcf9435b320eeb58
- System authority: YuJian Core Visual System V1
- Verification: reference_manifest.json and verify_core_ui_v1_references.py

This is the single long-term detail destination for one FishRecord.

Entry points:
- Normal Home recent-catch card
- My Catches record card
- Recognition Result “保存并记录记忆” after successful FishRecord creation
- later search/history surfaces

## Structure
1. Top navigation
2. YuJianCatchHeroCard
3. 关于这次鱼获
4. 鱼获记忆
5. media continues vertically

## Detail Hero
- species
- length · weight · location
- no time in the current V2 visual
- low-weight 编辑 >

## Memory actions
- 添加照片/视频
- 继续拍照
- 录制视频

When entered from “保存并记录记忆”, scroll/focus to Memory without creating a separate enrichment page.

## Frozen Overview behavior authority

- Authority: `design/pages/fish_record/detail/FishRecordDetail_Overview_Authority_V1.md`
- Status: **FROZEN**
- Freeze date: **2026-09-30**

The Overview authority freezes the following product behavior:

- A-side and B-side are two surfaces of the same FishRecordDetail.
- B-side lifecycle is `NOT_GENERATED → GENERATING → READY / FAILED`.
- The first READY B-side is automatically revealed **once per FishRecord**.
- Generation success alone does not consume that reveal; the B-side must actually be presented.
- After the first reveal, every later detail entry defaults to A-side.
- Subsequent A ↔ B navigation is manual through the page-internal Flip Icon.
- Flip Icon is visible only while B-side = `READY`.
- Reduce Motion may change the transition treatment but not these state semantics.

## B-side visual authority

- Spec: `design/pages/fish_record/detail/FishRecordDetail_B_Side_Visual_Authority_V1.md`
- Canonical target: `design/pages/fish_record/detail/frozen/FishRecordDetail_B_Side_V1_Frozen.png`
- Approved source: 941 × 1672 PNG / 2,095,527 bytes
- Approved SHA-256: `c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b`
- Status: **FROZEN**
- Visual Authority: `design/pages/fish_record/detail/frozen/FishRecordDetail_B_Side_V1_Frozen.png`
- Manifest: `design/pages/fish_record/detail/frozen/manifest.json`
- SHA-256: `c07e684f6068e71ac2188819f9f69297343116a0c5db5b1089ef97be2f15582b`

B-side visual rules:

- preserve the A-side page shell, navigation, lower cards and FishRecord content;
- Hero replaces the real person + fish capture scene with the same fish isolated in a calm water environment;
- keep `草鱼`, `42.6 cm · 1.28 kg · 浙江 · 千岛湖`, and `编辑 >`;
- add the READY-only page-internal Flip Icon at the Hero top-right;
- do not add B面 / AI生成 / 数字鱼体 / 鱼体资产 labels;
- first reveal and subsequent manual flip behavior remain governed by the frozen Overview authority.



## Frozen batch scenario authorities

The 2026-09-30 batch review freezes the remaining compact Design Manager entries:

### 03 · 鱼体资产生成
- Authority: `FishRecordDetail_Asset_Generation_Spec_V1.md`
- Status: **FROZEN**
- Board scope: NOT_GENERATED / GENERATING / FAILED
- READY is handed off to 02 and is not duplicated as a generation page.
- A-side remains usable during generation.

### 04 · 信息编辑
- Authority: `FishRecordDetail_Editing_Spec_V1.md`
- Status: **FROZEN**
- One consolidated editing workspace.
- Basic facts + environment/record fields.
- Save / cancel / validation / failure preservation / destructive confirmation are frozen.

### 05 · 页面状态
- Authority: `FishRecordDetail_Page_States_Spec_V1.md`
- Status: **FROZEN**
- One merged State Board: Loading / Media Missing / No Uploaded Memory / Partial Data / Deleted or Invalid / Offline or Network Error / Retry and Fallback.
- Errors must fail the smallest responsible region.

#### 无上传记忆
- Visual Authority: `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_No_Uploaded_Memory_V1_Frozen.png`
- Source: `千岛湖晨雾中的草鱼记忆.png`
- Dimensions: **941 × 1672**
- Bytes: **1,699,366**
- SHA-256: `ca585b85d1c6fec224e402d368e60d122913ca6c0cb6e5cc6750c266d7fb8a93`
- Status: **FROZEN**

## Design freeze audit

- Authority: `FishRecordDetail_Design_Audit_Freeze_V1.md`
- Result: **DESIGN FROZEN — SOURCE BINARY GATE PASS**
- The 02 B-side PNG is archived at the canonical path and its byte identity matches the approved source.
- Current runtime `FishMemorySection.kt` is implementation state, not design authority; READY must eventually migrate to the frozen B-side Hero + Flip model.
