# Recognition Processing — V1.2 Frozen Baseline / SWOS V1.3 Main-Adopted Design Contract

V1.2 remains the frozen historical baseline. The SWOS contract set below was adopted on main through PR #190 as the current Processing optical/timing design authority. Android implementation and runtime acceptance remain separate and are not claimed by this adoption.

## Authority order

1. Main-adopted design-only optical authority: `optics/SWOS_Optical_Contract_V1.md` + `.json`; runtime NOT_RUN.
2. Main-adopted timing: `motion/Recognition_Processing_Motion_Spec_V1_3.md` + `motion/recognition_processing_motion_contract_v1_3.json`.
3. Main-adopted design-only Page Contract: `design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_2.md` and its V3.2 state/engineering/acceptance companions.
4. Cross-page handoff: `../continuity/Processing_Result_Optical_Continuity_Contract_V1.md` + `.json`.
5. Existing frozen photo-state references: `../Recognition_Design_Closure_V1_2.md`, `../spec/Recognition_State_Timeline_Spec_V1_3.md`, `../design/Recognition_Processing_Visual_State_Set_V1_2.json`.
6. Legacy Edge Field V1 static shape/render/motion contracts remain unchanged historical implementation references; they are not the SWOS geometry basis.
7. Existing accessibility, haptic, sound, data and evidence authorities remain in force where non-conflicting; haptic/sound remain NONE.

## Three-state model and timing status

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

The three user-visible product states remain unchanged. CAPTURED/DETECTING are internal phases of IMAGE_RECOGNIZING. The main-adopted V3.2 design boundaries are 0/350/950/1550/2800ms with real-event gates; they are not an inference deadline. V3.1's 220/320/350/250ms values and V1.2's 900/600/1250/200ms wording remain documented history and are superseded in design authority by V3.2, while the Android runtime remains on its existing implementation until Stage 1.

## Main-adopted motion and legacy boundary

SWOS is the main-adopted design basis replacing the line-led seven-path Edge Field optical basis. The 2600–2800ms peripheral-water release is within CLASSIFYING; no RESOLVE business state or pre-navigation wait is added. Actual Android timing and renderer remain NOT_RUN against this design contract.

## SWOS Static Visual V1.1

The Design Manager entry `识别过程 → 12 · SWOS · 静态视觉` is a static review workspace only. It registers 8 Processing frames + 7 RR09 frames, including R0.5 at 100ms inside the existing 0–200ms photo handoff. Total nominal time remains 3650ms. The frame and shared-photo contracts are main-adopted design authorities through PR #190; runtime and production visual acceptance remain NOT_RUN.

- Static visual contract: `optics/SWOS_Static_Visual_Contract_V1_1.md`
- Fifteen-frame machine contract: `optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json`
- Shared photo geometry: `../continuity/SWOS_Shared_Photo_Transition_Contract_V1.md` + `.json`
- Static source manifest: `optics/SWOS_Static_Asset_Manifest_V1_1.json`
- Dynamic demo handoff: `optics/SWOS_Dynamic_Demo_Technical_Handoff_V1.md`

Photo preview uses a user-selected local file in browser memory. The nominated 208758.jpg fixture and derivative exports are withheld from this public repository because publication permission is not confirmed. Its manual fish contour is review-only and never a production mask. R5 reuses the existing Frozen HIGH Result source and Hero Media Contract; the species name is a visual placeholder rather than model output.

The Phase/Task State Machine remains unchanged: R0.5 is a static sample within the existing transition interval and adds no product or execution state.

## Feedback

- Haptic: NONE
- Sound: NONE

## SWOS Web 高保真动效技术合同 V1.0

Web 真实播放、Renderer 降级、SAM 实验链路复用、15 帧逐帧控制、九视口与性能证据的执行权威为 [SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md](../web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md)；时序、照片几何和光学参数直接引用既有 SWOS 15 帧、共享照片转场及光学机器合同，不维护重复的 Web 专属 JSON。

该合同复用产品仓库现有 SAM ViT-B 实验链路，不另建分割服务；现有 debug endpoint 不是公开生产 API。生产 Mask 资格仍为 NOT_QUALIFIED，浏览器逐帧视觉验收仍须独立完成。

## Runtime boundary

This package freezes design authority only. Detector, crop, classifier, confidence semantics, production routing, Runtime closure, and Evidence PASS remain separate concerns.

Historical V1.1/V1.2 files and Page Contract V3.1 remain unchanged. The exact supersession scope and Android Stage 1 gaps are listed in `docs/execution/swos/PHASE0_CLOSURE_REPORT_V1.md`.
