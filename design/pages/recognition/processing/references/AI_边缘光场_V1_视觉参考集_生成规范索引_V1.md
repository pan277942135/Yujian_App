# AI 边缘光场 V1 · 视觉参考集 · 索引 V1

状态：**FROZEN SPECS / EXISTING VISUALS FIRST**

## 核心原则

> R01–R06 是开发与验收问题分类，不要求每一项重新生成一张图片。

优先级：

1. 已有 Frozen 高保；
2. 已冻结文字 / Machine Contract；
3. Runtime Evidence；
4. 只有前三者仍不能消除歧义时，才新增视觉。

## 当前闭环状态

- **R01 · 主视觉细节**：既有主视觉 FROZEN，无需新图。
- **R02 · 能量岛拆解**：现有拆解视觉保留，文字纠偏强制执行，无需重生成。
- **R03 · 四岛构图**：Static Shape + 02 高保 + Engineering Spec 足够指导开发，无需新图。
- **R04 · 光场与鱼体聚焦**：03 / 04 两张 Frozen 高保直接闭环，无需新图。
- **R05 · 强度校准**：01 强表现 + 02 目标辅助；最低档由真实 Runtime LITE 闭环，无需新设计图。
- **R06 · 失败边界**：F01–F06 文字 taxonomy 立即用于 QA，真实失败案例按需归档，不主动生图。

详细映射：

`design/pages/recognition/processing/references/AI_边缘光场_V1_既有视觉复用映射_V1.md`

## Authority 边界

高保图负责视觉质感和状态主次；精确结构仍由：

- Static Shape
- Rendering Contract
- Motion Contract
- Accessibility / Degradation Contract

负责。

任何历史高保中的旧状态文案、旧路径表达或 UI，不得覆盖当前冻结合同。
