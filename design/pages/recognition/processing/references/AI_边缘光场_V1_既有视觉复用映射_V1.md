# AI 边缘光场 V1 · 既有视觉复用映射 V1

状态：**ACTIVE**

目的：

> 优先复用已经批准或已经存在的 Recognition 高保视觉，不为了 R01–R06 菜单结构重复生成相似图片。

## 复用原则

1. 有已批准高保图能回答问题 → 直接复用。
2. 图基本正确但局部有歧义 → 用文字规范纠偏。
3. 结构问题可由 Contract 精确定义 → Contract 优先，不额外生图。
4. 强度/降级问题最终必须在真实 Runtime 验收 → 不用设计图替代真实 Evidence。
5. 只有现有图、规范、Runtime 都无法回答的问题，才新增视觉图。

## R01–R06 映射

| 项目 | 处理方式 | 现有视觉 / Authority | 是否需要新图 |
| --- | --- | --- | --- |
| R01 主视觉细节 | 直接复用 | `04_Fish_Identifying_Frozen.png` + R01 scope | 否 |
| R02 能量岛拆解 | 保留现有拆解视觉，文字纠偏 | R02 Spec + Rendering Contract | 否 |
| R03 四岛构图 | Contract + 现有完整状态图联合解释 | `02_AI_Understanding_Frozen.png` + Engineering Spec + Static Shape | 否 |
| R04 光场与鱼体聚焦 | 直接复用双状态高保 | `03_Fish_Highlight_Frozen.png` + `04_Fish_Identifying_Frozen.png` | 否 |
| R05 强度校准 | 设计参考 + Runtime 闭环 | `01_Capture_Transition_Frozen.png` + `02_AI_Understanding_Frozen.png` + Runtime LITE | 否 |
| R06 失败边界 | 文本 taxonomy + 真实失败归档 | F01–F06 QA 规则 | 默认否 |

## 现有四张 Processing 高保的新解释

### 01_Capture_Transition_Frozen.png

现在用于：

- 图片识别中 early reference；
- R05 强表现上限。

不再代表独立 CAPTURED 产品状态。

### 02_AI_Understanding_Frozen.png

现在用于：

- 图片识别中 late reference；
- R03 完整照片中的 Edge Field 构图辅助；
- R05 目标强度方向辅助。

不单独决定当前四岛 Static Shape。

### 03_Fish_Highlight_Frozen.png

现在用于：

- 已定位到鱼体；
- R04 状态 A。

### 04_Fish_Identifying_Frozen.png

现在用于：

- 鱼种识别中；
- R04 状态 B；
- R01 光学语言主视觉 Authority。

## 最终原则

R01–R06 是**开发与验收问题的分类方式**，不是“必须生产六张新图片”的生产清单。

> **Visual Reference Set 的完成标准是“开发和验收没有歧义”，不是“六个菜单各有一张新图”。**
