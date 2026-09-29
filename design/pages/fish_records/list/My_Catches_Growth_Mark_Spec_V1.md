# 我的鱼获 · Growth Mark V1

Status: **FROZEN**
Page: `my_catches_v2`
Role: **低权重个人记录印记**
Recovered visual source: **与自然相遇：Growth Mark V1 设计稿.png**

## 1. 定义

Growth Mark 自动标记一条鱼获在个人钓鱼记录中的特殊意义。

它不是 Achievement Badge，不建立等级、积分、奖杯或稀有度系统。

视觉优先级必须低于：
**真实鱼获照片 / 鱼种 / 核心记录信息**。

## 2. V1 只支持四类

### A. 首条某鱼种
语义：该用户第一次正式记录这个鱼种。

动态 Label：
- `首条草鱼`
- `首条鲤鱼`
- `首条鳜鱼`

数据属性：基于该鱼种历史，可随记录删除后重新计算。

### B. 数量里程碑
基于全部正式 FishRecord 的历史顺序。

V1 thresholds:
- 第10条
- 第50条
- 第100条
- 第500条
- 第1000条

Label：
`第100条`

一旦形成历史里程碑，正常情况下保持永久语义。

### C. 同鱼种最长
在**同一鱼种**的有效长度记录中计算。

Label：
`最长`

### D. 同鱼种最重
在**同一鱼种**的有效重量记录中计算。

Label：
`最重`

## 3. 不属于 Growth Mark V1

以下不进入鱼获卡 Growth Mark：
- 新地点 / 首次地点
- 单日最多
- 月度 / 年度复杂统计
- 天气
- 装备
- 饵料
- “新鱼种”之外的成就化扩展

## 4. 列表显示数量

FishRecordRowCard：
**最多显示 1 个 Growth Mark。**

Priority：

`数量里程碑 > 首条某鱼种 > 尺寸纪录`

尺寸纪录内部：
- 同时最长 + 最重 → `最长 · 最重`
- 只命中一个 → `最长` 或 `最重`

FishRecordDetail 可以展示该记录全部有效 Growth Mark。

## 5. 位置

列表：
- 卡片右上信息区；
- Chevron 左侧；
- 不压照片；
- 不独占整行；
- 不改变卡片高度。

Target:
- 22–24dp height
- 11–12sp
- compact horizontal padding

## 6. 视觉

- 数量里程碑：restrained Morning Gold
- 首条某鱼种：muted gray-green
- 尺寸纪录：muted blue-gray

默认：
- 无强 icon
- 无阴影
- 无 glow
- 无动画
- 不做游戏化徽章

## 7. 搜索 / 筛选

自由搜索可匹配可读 Growth Mark 文案，例如：
- 首条草鱼
- 最长
- 最重
- 第100条

Filter V1 的“特殊记录”维度固定支持：
- 首条某鱼种
- 数量里程碑
- 同鱼种最长
- 同鱼种最重

筛选依据真实记录属性，不依赖当前列表只展示的那一个 Mark。

## 8. Runtime handoff

当前 Runtime 的：
- `FirstSpecies`：语义方向正确，保留并对齐文案；
- `FirstLocation`：不属于 V1，应从列表 Growth Mark 移除；
- global `Longest / Heaviest`：应改为**同鱼种**计算；
- 尚缺 `CountMilestone`。

这些属于开发交接差异，不改变本设计 Authority。
