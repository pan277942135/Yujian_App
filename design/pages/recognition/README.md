# YuJian Recognition Processing Design Package — V1.2 Frozen Baseline / SWOS V1 Main-Adopted Design

Status: **V1.2 FROZEN VISUAL BASELINE · SWOS V1 / Page V3.2 MAIN-ADOPTED DESIGN ONLY**

Runtime: **ACTIVE_CLOSURE**  
Evidence: **ACTIVE_CLOSURE**

## 2026-10-29 · 完整拍照/选图→识别→结果 SPEC_FROZEN 补充

用户已冻结的新版跨页产品行为由 [Capture_To_Result_Visual_Flow_Contract_V1.md](journey/Capture_To_Result_Visual_Flow_Contract_V1.md) 定义；[机器 JSON](journey/Capture_To_Result_Visual_Flow_Contract_V1.json) 和 [视频验收矩阵](journey/Capture_To_Result_Acceptance_Matrix_V1.md) 提供可校验的细则。

- 起点：现有首页 CTA，真实 CameraX 相机页及原生照片选择器；**竖屏 UI，横图输入合法**。
- 初始承接：相机真实预览定格矩形 / 相册同源照片居中承接，连续归位到顶部 Fit/Contain 原图；照片背后不再生成放大模糊副本。
- Processing：下方仅小型动态标记和「识别中」，照片四周水光与真实可信 Mask 金色轮廓独立；无 Mask 就不画鱼体或中央放大圈。
- 时间：旧 2800ms 仅保留设计历史参照；当前全链路采用照片首帧启动、事件门禁和最短可感知视觉表现，RR09 仍以 850ms 为首轮目标。
- 结果：同源照片唯一所有权进入 Hero；无 Mask 仍自然揭晓中文名称，不伪造金光转译。
- 状态：**SPEC_FROZEN，不代表新 PNG 母版已逐像素验收，Android Runtime 与真实视频验收尚未完成**。本更新不覆盖旧图字节，不修改独立 Sites Web 产品与其响应式合同。

## Current design entry point

`Recognition_Design_Closure_V1_2.md`

The V1.2 package remains the frozen Processing visual baseline. SWOS V1, Processing Motion V1.3 and Page Contract V3.2 are the main-adopted design authority; Android runtime remains on its existing implementation pending Stage 1.

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

## Frozen V1.2 baseline and main-adopted design authorities

Frozen V1.2 photo-state references remain unchanged. PR #190 adopted the SWOS optical/timing design contracts from #186/#187. These contracts govern design only; Android implementation and runtime acceptance remain separate Stage 1 work.

- Main-adopted design-only SWOS optics: `processing/optics/SWOS_Optical_Contract_V1.md`
- Main-adopted design-only Processing timing: `processing/motion/Recognition_Processing_Motion_Spec_V1_3.md`
- Main-adopted design-only Processing Page Contract V3.2: `design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_2.md`
- Main-adopted design-only Processing → Result continuity: `continuity/Processing_Result_Optical_Continuity_Contract_V1.md`
- Main-adopted design-only RR09 reveal: `result/motion/Recognition_Result_Motion_Spec_V3.md`
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
