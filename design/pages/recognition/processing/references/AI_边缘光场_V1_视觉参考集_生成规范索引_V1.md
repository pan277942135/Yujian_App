# AI 边缘光场 V1 · 视觉参考集 · 生成规范索引 V1

状态：**FROZEN**

本索引冻结 R01–R06 的生成规范集合。

## 已冻结

- R01 · 主视觉细节
- R02 · 能量岛拆解
- R03 · 四岛构图
- R04 · 光场与鱼体聚焦
- R05 · 强度校准
- R06 · 失败边界

## 边界

本次冻结的是：

> **要生成什么、画面要表达什么、什么必须保持、什么明确禁止。**

本次没有冻结：

- R01–R06 最终视觉图片；
- 像素级视觉 Authority；
- 新的 Runtime 实现；
- 新的动效参数。

在最终参考图缺失期间，代码实现仍必须同时遵守：

1. AI Edge Field V1 · Static Shape；
2. AI Edge Field V1 · Rendering Contract；
3. AI Edge Field V1 · Motion Contract；
4. R01–R06 本组已冻结生成规范。

最终视觉图补齐后，可作为新的视觉验收 Authority，但不得反向破坏上述冻结结构。
