# AI 边缘光场 V1 · 视觉参考集 · 生成规范索引 V1

状态：**FROZEN**

本索引冻结 R01–R06 的生成规范集合，并记录当前已确认视觉参考。

## 生成规范

- R01 · 主视觉细节 · **FROZEN**
- R02 · 能量岛拆解 · **FROZEN**
- R03 · 四岛构图 · **FROZEN**
- R04 · 光场与鱼体聚焦 · **FROZEN**
- R05 · 强度校准 · **FROZEN**
- R06 · 失败边界 · **FROZEN**

## 已确认视觉 Authority / 参考

### R01 · 主视觉细节

- 主视觉：`design/pages/recognition/design/04_Fish_Identifying_Frozen.png`
- SHA-256：`d6ad5b06ddd4eb6236dd5ee03b5e5f5e16bf1b23661689c0fc9d88c790dd15ec`
- 状态：**FROZEN**
- 范围：光学质感、蓝金气质、环境受光、Bloom、细丝、粒子克制、照片优先级。

### R05 · 强度校准

- 强表现参考：`design/pages/recognition/design/01_Capture_Transition_Frozen.png`
- SHA-256：`e3101806e7e850cbcb46eb46b184ae049acbf379e2cf36ba04549421e8d16b58`
- 状态：**FROZEN · STRONG REFERENCE**
- 范围：强度上限参考；目标档与最低可接受档仍待补齐。

## Authority 优先级

已有高保真图负责视觉质感，但不得反向覆盖结构合同。

发生冲突时按以下顺序解释：

1. AI Edge Field V1 · Static Shape；
2. AI Edge Field V1 · Rendering Contract；
3. AI Edge Field V1 · Motion Contract；
4. R01–R06 视觉参考规范；
5. 已确认高保真参考图中的光学语言。

特别说明：

- 高保真图中的返回按钮、状态卡和旧状态文案不属于 AI Edge Field 本体；
- 不需要为了移除这些 UI 再重新生成已经认可的视觉；
- R01 可直接用于实现视觉质感对照；
- R05 强表现图可直接用于强度上限对照。

## 尚待补齐

- R02 · 能量岛拆解视觉图；
- R03 · 四岛构图视觉图；
- R04 · 光场与鱼体聚焦视觉图；
- R05 · 目标档与最低可接受档；
- R06 · 失败边界视觉图。
