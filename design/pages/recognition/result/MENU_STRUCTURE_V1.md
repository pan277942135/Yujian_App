# Recognition Result — Design Manager Menu V1

Status: **STATIC RESULT DESIGN FROZEN · RR09 V3 PROPOSED / V2 HISTORICAL (RUNTIME NOT_RUN)**

Canonical machine-readable navigation:

`design/pages/recognition/result/navigation.json`

一级菜单：**识别结果 / Recognition Result**

二级菜单：

| Order | ID | Menu | Frozen authority | Review |
| --- | --- | --- | --- | --- |
| 00 | RR00 | Overview / 结果总览 | 3+2 aggregate | CLOSED V1 |
| 01 | RR01 | High / 高置信结果 | 05_Result_High_Frozen.png | CLOSED V1 |
| 02 | RR02 | Medium / 中置信结果 | 06_Result_Medium_Frozen.png | CLOSED V1 |
| 03 | RR03 | Low / 低置信结果 | 07_Result_Low_Frozen.png | CLOSED V1 |
| 04 | RR04 | No Fish / 未检测到鱼 | 08_Error_No_Fish_Frozen.png | CLOSED V1 |
| 05 | RR05 | Image Quality / 图片质量不足 | 09_Error_Image_Quality_Frozen.png | CLOSED V1 |
| 06 | RR06 | Content Edit / 内容修改 | Species Selector V1 + Metadata Edit Flow V1 (existing FROZEN authorities) | FROZEN AUTHORITY INDEX |
| 07 | RR07 | **识别过程 · 07 · 判定逻辑** | Current Kotlin routing audit + 3+2 machine contract | AS-IS DOCUMENTED / PROPOSALS UNAPPROVED |
| 08 | RR08 | Visual Acceptance / 视觉验收 | Recognition Result Visual Contract V1 + Pixel Targets + State Matrix + Anchor Map | READY_FOR_VISUAL_IMPLEMENTATION; runtime NOT_RUN |
| 09 | RR09 | **动效接续 V3** | Recognition Result Motion Spec V3 + machine contract + system Motion Contract V3 + Optical Continuity V1 | PROPOSED_FOR_MAIN_ADOPTION; runtime NOT_RUN |
| 10 | RR09 V2 | **V2 历史基线** | Recognition Result Motion Spec V2 + machine contract + system Motion Contract V2 | DEPRECATED historical authority; bytes preserved |

## Asset rule

The Design Manager pages reference the canonical Frozen PNGs in `design/pages/recognition/design/`.

Do not duplicate, crop, recompress or rename the five canonical Result-state PNGs merely for menu display. RR06 links to the separate canonical Species Selector and Metadata Edit PNGs, specs, contracts and manifests.

`00 Overview` is the common 3+2 contract and is closed after 01–05 individual review. RR06 is a separate content-edit authority index; it does not add a sixth Result state or change the 3+2 model.

## RR07 · Behavior / Engineering Decision Workspace

RR07 在识别结果下直接显示检测器质量门、分类阈值、first-match 五态路由、公式与边界样例。权威见 `07_decision_logic/README.md`、`spec/Recognition_Result_3plus2_Decision_Spec_V1_1.md`、`spec/recognition_result_3plus2_decision_contract_v1_1.json`。

**RR07 不增加产品状态，不更改 RR01–RR05 五张 Frozen PNG，不代表已调整 Kotlin 阈值。**

## RR08 · Visual Acceptance Contract V1

RR08 links the canonical visual, pixel, state, anchor, motion, accessibility and runtime acceptance contracts. It is derived from the frozen Page Contract, adds no Result product state and leaves source artwork unchanged.


## RR09 · 动效接续 / Motion Continuation V3

RR09 V3 提议一次性 `RESULT_HIGH` 揭晓：T0 为首个实际呈现的 Result 帧；导航不等待 200ms；同一照片接续 → 金光从可信鱼体区域转向照片下方的中文鱼种名 → 余辉在 850ms 内消散。英文科学名不显示，也不预留空行。它不是新产品状态，不改变 3+2 判定，也不代表 Android Runtime 已通过。

- Spec: `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md`
- Machine contract: `design/pages/recognition/result/motion/recognition_result_motion_contract_v3.json`
- System contract: `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md`
- Continuity contract: `design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md`
- V2 spec/machine/system files remain unchanged historical references under the Design Manager V2 history submenu.
