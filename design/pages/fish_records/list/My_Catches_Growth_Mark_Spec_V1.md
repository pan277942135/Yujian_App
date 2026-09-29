# 我的鱼获 · Growth Mark V1

Status: **FROZEN**
Page: `my_catches_v2`
Role: **低权重个人记录印记**

## 1. 设计原则

Growth Mark 是“这条记录为什么值得记住”的轻量提示。

它不是：
- Achievement Badge
- 游戏成就
- 奖杯
- 皇冠
- 星级
- 积分
- 稀有度
- 等级系统

视觉优先级必须低于：
**真实鱼获照片 / 鱼种 / 核心记录信息**

## 2. V1 只支持四类

### A. 首次记录

语义：
用户的第一条正式 FishRecord。

Label:
`第一次记录`

不是“首次某地点”。

### B. 数量里程碑

按全部正式 FishRecord 的时间顺序永久计算。

Thresholds:
- 第 10 条
- 第 50 条
- 第 100 条
- 第 500 条
- 第 1000 条

Labels:
- `第10条`
- `第50条`
- `第100条`
- `第500条`
- `第1000条`

禁止：
`达成100条！`
等庆功式文案。

### C. 同鱼种最重

在同一鱼种的有效重量记录中计算。

Label:
`最重记录`

“最大记录”不再作为 V1 规范文案。

### D. 同鱼种最长

在同一鱼种的有效长度记录中计算。

Label:
`最长记录`

## 3. 最长 + 最重合并

同一条记录同时为该鱼种最长和最重时：

`最长 · 最重`

不同时占两枚标签。

## 4. Timeline 单卡显示数量

列表单张 FishRecordRowCard：
**最多显示 1 个 Growth Mark。**

Priority:

`数量里程碑 > 首次记录 > 尺寸纪录`

尺寸纪录内部：
- 若同时最长 + 最重 → 合并
- 若只命中一个 → 显示对应记录

FishRecordDetail 可展示该条记录全部有效 Growth Mark。

## 5. 位置

列表位置：

**卡片右上信息区，Chevron 左侧**

规则：
- 不压照片
- 不独占整行
- 不放在照片左上角
- 不放大到标题同等级
- 不改变卡片高度来强调标签

## 6. 尺寸与字体

Target:
- mark height: **22–24dp**
- type: **11–12sp**
- compact horizontal padding
- no icon
- no shadow
- no glow
- no animation

可使用极短、极细的淡金竖线作为“记录印记”提示。

## 7. 颜色语义

- 数量里程碑： restrained Morning Gold
- 首次记录： muted gray-green
- 尺寸纪录： muted blue-gray

颜色只用于弱区分，不形成 Badge 等级。

## 8. 搜索 / 筛选

Growth Mark V1 四类均进入：
**特殊记录**筛选维度。

筛选语义基于记录真实属性，而不是基于列表当下显示的唯一标签。

## 9. 稳定性

记录一旦获得“第一次记录 / 第N条”类历史语义，应保持稳定。

尺寸纪录属于基于当前档案计算的相对记录：
后续出现更长 / 更重的同鱼种记录时，纪录标签可迁移到新记录。

## 10. Current runtime divergence

当前 Android 代码仍使用：
- FirstSpecies
- FirstLocation
- global Longest
- global Heaviest

这与冻结 Growth Mark V1 不一致。

未来开发应调整为：
- FirstRecord
- CountMilestone
- SpeciesHeaviest
- SpeciesLongest

这是 Runtime handoff gap，不修改本设计 Authority。
