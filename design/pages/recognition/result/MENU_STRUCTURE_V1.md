# Recognition Result — Design Manager Menu V1

Status: **DESIGN CLOSED**

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

## Asset rule

The Design Manager pages reference the canonical Frozen PNGs in `design/pages/recognition/design/`.

Do not duplicate, crop, recompress or rename the five canonical Result-state PNGs merely for menu display. RR06 links to the separate canonical Species Selector and Metadata Edit PNGs, specs, contracts and manifests.

`00 Overview` is the common 3+2 contract and is closed after 01–05 individual review. RR06 is a separate content-edit authority index; it does not add a sixth Result state or change the 3+2 model.

## RR07 · Behavior / Engineering Decision Workspace

RR07 在识别结果下直接显示检测器质量门、分类阈值、first-match 五态路由、公式与边界样例。权威见 `07_decision_logic/README.md`、`spec/Recognition_Result_3plus2_Decision_Spec_V1_1.md`、`spec/recognition_result_3plus2_decision_contract_v1_1.json`。

**RR07 不增加产品状态，不更改 RR01–RR05 五张 Frozen PNG，不代表已调整 Kotlin 阈值。**
