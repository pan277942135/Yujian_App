# YuJian Recognition Processing Design Package — V1.2 Baseline / SWOS V1 Proposal

Status: **V1.2 FROZEN BASELINE · SWOS V1 / Page V3.2 PROPOSED FOR MAIN ADOPTION**

Runtime: **ACTIVE_CLOSURE**  
Evidence: **ACTIVE_CLOSURE**

## Current design entry point

`Recognition_Design_Closure_V1_2.md`

This V1.2 closure is the current Recognition Processing design authority.

## Frozen product flow

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Recognition Result
```

Exactly three user-visible Processing states are allowed. RESOLVE is a transition.

## Visual authority

Three Frozen Processing PNGs now remain byte-identical, one per user-visible product state, after the user-approved Early reference retirement:

- `design/02_AI_Understanding_Frozen.png` — 图片识别中 / 唯一视觉参考
- `design/03_Fish_Highlight_Frozen.png` — 已定位到鱼体
- `design/04_Fish_Identifying_Frozen.png` — 鱼种识别中

State-set mapping:

- `design/Recognition_Processing_Visual_State_Set_V1_2.json`

Binary hashes remain frozen in:

- `design/reference_manifest.json`

## Frozen baseline authorities and current proposal

Frozen V1.2 photo-state references remain unchanged. The Phase 0 Draft PR proposes a versioned optical/timing successor; until main adoption, Android and main remain on their prior authorities.

- Proposed SWOS optics: `processing/optics/SWOS_Optical_Contract_V1.md`
- Proposed Processing timing: `processing/motion/Recognition_Processing_Motion_Spec_V1_3.md`
- Proposed Processing Page Contract V3.2: `design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_2.md`
- Proposed Processing → Result continuity: `continuity/Processing_Result_Optical_Continuity_Contract_V1.md`
- Proposed RR09 reveal: `result/motion/Recognition_Result_Motion_Spec_V3.md`
- Phase 0 closure and Android gap list: `docs/execution/swos/PHASE0_CLOSURE_REPORT_V1.md`

## Frozen V1.2 baseline authorities

- State Timeline: `spec/Recognition_State_Timeline_Spec_V1_3.md`
- Visual: `processing/spec/Recognition_Processing_Visual_Spec_V1_1.md`
- Motion: `processing/motion/Recognition_Processing_Motion_Spec_V1_2.md`
- Edge Field motion: `processing/motion/AI_Edge_Field_V1_Motion_Contract.md`
- Degradation / Accessibility: `processing/accessibility/Recognition_Degradation_Accessibility_V1.md`
- Haptic: `processing/haptic/Recognition_Processing_Haptic_Spec_V1.md` — NONE
- Sound: `processing/sound/Recognition_Processing_Sound_Spec_V1.md` — NONE
- Machine contract: `processing/contracts/Recognition_Processing_Contract_V1_2.json`
- Runtime Evidence requirements: `evidence/Recognition_Runtime_Evidence_V1_2.md`

## Recognition Result

Recognition Result remains a separate design package under `result/`. Processing does not redefine Result High / Medium / Low or Issue surfaces.

## Compatibility

V1.1 Recognition Processing contracts remain in the repository for historical/runtime compatibility. They are not the current product-state or motion authority where V1.2 explicitly supersedes them.

## Runtime boundary

Design is frozen. Android Runtime and Evidence continue to close independently. Runtime gaps, runner issues, evidence gaps, or parity failures must not silently reopen the V1.2 design authority.

## Recognition Full Flow & Model Responsibilities V1

The Design Manager submenu `识别过程 → 09 · 识别全流程-和模型` documents the end-to-end path from the original complete scene image through preprocessing, quality gate, DET_FISH detection, BBox coordinate validation, size/classifiability rules, Crop, MODEL_M1 classification, RR07 result fusion and user confirmation.

- Detailed authority: `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.md`
- Machine-readable flow: `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.json`
- Flowchart: `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.svg`

This is a DESIGN_ONLY business/technical flow view, not a new product state or single-page Hi-Fi authority. DET_FISH owns fish presence/location; geometry/quality rules own size and eligibility; MODEL_M1 classifies a valid crop; final user-facing states remain governed by the existing Processing and Result contracts. Runtime model versions and thresholds are not changed by this documentation.
