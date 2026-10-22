# Recognition Result V1.1

Role: **Capture → Record Bridge**

Status: **STATIC 3+2 DESIGN FROZEN · RR09 V3 PROPOSED / V2 HISTORICAL · RUNTIME NOT_RUN**

## Design Manager menu

Second-level pages:

- `00_overview/` — Overview / 结果总览
- `01_high/` — High / 高置信结果
- `02_medium/` — Medium / 中置信结果
- `03_low/` — Low / 低置信结果
- `04_no_fish/` — No Fish / 未检测到鱼
- `05_image_quality/` — Image Quality / 图片质量不足
- `06_content_edit/` — RR06 · 内容修改（Species Selector V1 + Metadata Edit Flow V1 Authority 索引）
- `07_decision_logic/` — **RR07 · 识别过程 · 07 · 判定逻辑**（现行 Detector / Classifier 判定链及门槛说明，不增加产品状态）
- RR08 — `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/` — 视觉验收合同索引（Runtime NOT_RUN）
- `08_motion_continuation/` — **RR09 · 动效接续 V3**（PROPOSED_FOR_MAIN_ADOPTION; V2 历史基线保留；Runtime NOT_RUN）

Machine-readable menu:

`navigation.json`

Menu definition:

`MENU_STRUCTURE_V1.md`

## Scope

Recognition Result contains five frozen product states:

Result:
- RESULT_HIGH
- RESULT_MEDIUM
- RESULT_LOW

Recovery:
- ERROR_NO_FISH
- ERROR_IMAGE_QUALITY

`TECHNICAL_FAILURE` remains a runtime-safe generic fallback under the global Recognition runtime contract. It is not one of the 3+2 frozen Result states.

## Frozen visuals

The five Design Manager state pages reference the canonical Frozen PNGs directly:

- `design/pages/recognition/design/05_Result_High_Frozen.png`
- `design/pages/recognition/design/06_Result_Medium_Frozen.png`
- `design/pages/recognition/design/07_Result_Low_Frozen.png`
- `design/pages/recognition/design/08_Error_No_Fish_Frozen.png`
- `design/pages/recognition/design/09_Error_Image_Quality_Frozen.png`

No duplicate image copies are maintained inside the menu folders.

Dimensions/SHA are frozen in:

`design/pages/recognition/design/reference_manifest.json`

## Authority

### State-specific authority

The five state Frozen PNGs are the final state-level composition authority.

### System-level authority

`design/system/core_visual_v1/reference/recognition_result_v1.png`

defines the shared Result visual language and base capture-to-record composition.

Conflict rule:

**state-specific Frozen PNG > Result package spec > Core Visual System shared reference > runtime implementation**

The Core Visual reference must not override a state-specific difference shown in 05–09.

Dynamic user-photo placement inside the frozen Hero container is governed by:

`design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`

Numeric authority:

`design/pages/recognition/result/media/hero_media_contract.json`

The Frozen PNG owns Hero **container composition**. The Hero Media Contract owns how arbitrary real user photos are mapped **inside** that container.

## Package entry points

- `DESIGN_PACKAGE_CLOSURE_V1.md`
- `spec/Recognition_Result_Feature_Spec_V1.md`
- `spec/Recognition_Result_State_Matrix_V1.md`
- `spec/Recognition_Result_Behavior_Spec_V1.md`
- `spec/Recognition_Result_Visual_Spec_V1.md`
- `motion/Recognition_Result_Motion_Spec_V3.md` — proposed current Result-entry reveal authority; T0 = first presented Result frame; Chinese title only
- `motion/recognition_result_motion_contract_v3.json` — machine-readable V3 timing and trigger contract
- `motion/Recognition_Result_Motion_Spec_V2.md` and `motion/recognition_result_motion_contract_v2.json` — unchanged historical baseline
- `motion/Recognition_Result_Motion_Spec_V1.md` — historical baseline for non-conflicting rules
- `media/Recognition_Result_Hero_Media_Contract_V1.md`
- `media/hero_media_contract.json`
- `media/hero_media_test_vectors.json`
- `spec/Recognition_Result_Acceptance_Criteria_V1.md`
- `08_motion_continuation/README.md` — RR09 Design Manager entry
- `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md` — proposed current system-level motion contract
- `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V2.md` — unchanged historical system contract
- `authority/authority_map.json`
- `review/Recognition_Result_Runtime_Alignment_Review_V1.md`
- `status.json`

## Content-edit authority index · RR06

`design/pages/recognition/result/06_content_edit/README.md` is the single Design Manager entry for the two already-frozen authorities below. RR06 does not add a product state or alter the 3+2 Result model.

- Species Selector V1: `design/pages/recognition/result/species_selector/Species_Selector_Spec_V1.md` · `design/pages/recognition/result/species_selector/species_selector_contract.json` · `design/pages/recognition/result/species_selector/frozen/Recognition_Result_Species_Selector_V1_Frozen.png` · `design/pages/recognition/result/species_selector/frozen/manifest.json`
- Metadata Edit Flow V1: `design/pages/recognition/result/engineering/Recognition_Result_Metadata_Edit_Flow_V1.md` · `design/pages/recognition/result/engineering/metadata_edit_flow_contract.json` · `design/pages/recognition/result/metadata_edit/frozen/Recognition_Result_Metadata_Edit_Flow_V1_Frozen.png` · `design/pages/recognition/result/metadata_edit/frozen/manifest.json`

RR06 links to the canonical specs, contracts, PNGs and manifests; it does not duplicate or merge their distinct decision boundaries. Species Selector owns the selector only. The Metadata Edit board remains limited to metadata overlay interactions and does not authorize its legacy Result-page composition.

## Implementation-ready engineering authority

Work / frontend implementation must read:

- `engineering/Recognition_Result_Layout_Geometry_V1.md`
- `engineering/Recognition_Result_Component_Map_V1.md`
- `engineering/Recognition_Result_Metadata_Input_Contract_V1.md`
- `engineering/Recognition_Result_Candidate_Card_V1.md`
- `engineering/Recognition_Result_Visual_Acceptance_Map_V1.md`

Machine-readable companions live beside each Markdown contract.

Combined with Hero Media V1, these contracts remove page-level visual discretion from implementation.

**Recognition Result Design Package status: IMPLEMENTATION READY.**

Runtime implementation and validation remain a separate closure step.

## Product responsibility

Recognition Result is a lightweight confirmation and record bridge.

It may contain:
- real captured fish photo
- species confirmation / correction
- length
- weight
- location
- short catch note
- voice-note affordance when implemented
- save / continue-memory actions for resolved species

It must not become:
- a Fish Guide detail page
- a media gallery
- a statistics page
- a detector/debug UI
- an achievement/game screen

## CTA contract

Resolved species:

- `保存本次鱼获` → create FishRecord → Normal Home
- `继续记忆` → create FishRecord first → FishRecordDetail(recordId, initialSection=MEMORY)

Low initial state:

- `手动选择鱼种`
- `重新拍摄`
- vertical Metadata and Story remain visible before species resolution
- unresolved Dual CTA remembers destination and opens Species Selector with LOW_MANUAL

A Low result does not create an unknown/pending-species FishRecord. Returning
unconfirmed preserves draft fields and clears the pending destination.

A FishRecord must exist before memory-media operations begin.


## Design-system registry audit · 2026-10-10

Result V1.1 page-level Frozen authorities have priority over the conflicting historical Top Navigation / Action Button usage examples. The current Result centered title and light Result Save/Continue composition are registered as **scoped exceptions**, leaving globally frozen component variants untouched. RR03 Low keeps editable metadata, Story and visible dual CTAs before species confirmation; saving is gated by LOW_MANUAL. The 3+2 model, seven Frozen visual boards and metadata/selector boundaries are unchanged.

See `review/Recognition_Result_Design_Audit_20261010.md`. Runtime parity remains separate and pending.

## Recognition Decision Logic · RR07

`07_decision_logic/README.md` 是单独的 Design Manager 行为/工程子菜单。交互展示当前代码阈值、质量门分支优先级、五态映射、有效 Softmax 测试向量及待校准风险；机器合同是 `spec/recognition_result_3plus2_decision_contract_v1_1.json`。**当前代码与未经批准的优化建议严格分离。**


## Motion Continuation · RR09

`08_motion_continuation/README.md` is the Design Manager entry for proposed RR09 V3 and its V2 historical baseline. The reveal is presentation-only: it does not add a Processing or Result state, modify recognition thresholds, or change CTA semantics. Runtime and visual playback remain NOT_RUN. V3 is not canonical on main until the Draft control-plane PR is adopted.

The RR09 V3 Design Manager view links back to `识别过程 → 12 · SWOS · 静态视觉`, the dependent static V1.1 review workspace. It adds no product state and does not replace the historical RR09 V2 contract.
