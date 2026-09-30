# Recognition Result — Design Manager Menu V1

Status: **DESIGN CLOSED**

一级菜单：**识别结果 / Recognition Result**

二级菜单：

| Order | Menu | Frozen authority | Review |
| --- | --- | --- | --- |
| 00 | Overview / 结果总览 | 3+2 aggregate | CLOSED V1 |
| 01 | High / 高置信结果 | 05_Result_High_Frozen.png | CLOSED V1 |
| 02 | Medium / 中置信结果 | 06_Result_Medium_Frozen.png | CLOSED V1 |
| 03 | Low / 低置信结果 | 07_Result_Low_Frozen.png | CLOSED V1 |
| 04 | No Fish / 未检测到鱼 | 08_Error_No_Fish_Frozen.png | CLOSED V1 |
| 05 | Image Quality / 图片质量不足 | 09_Error_Image_Quality_Frozen.png | CLOSED V1 |

## Asset rule

The Design Manager pages reference the canonical Frozen PNGs in `design/pages/recognition/design/`.

Do not duplicate, crop, recompress or rename the five canonical PNGs merely for menu display.

Each second-level page owns:
- one Frozen reference
- page purpose
- visible UI anatomy
- behavior/CTA contract
- review decisions
- explicit rejected alternatives
- acceptance notes

`00 Overview` is the common 3+2 contract and is now closed after 01–05 individual review.
