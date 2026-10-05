# F1 · 筛选面板 · Frozen Spec V1

Status: **FROZEN**  
Freeze date: **2026-09-29**  
Owner: **My Catches / 我的鱼获**  
Visual Authority: `design/pages/fish_records/list/frozen/filter_v1/F1_Filter_Panel_Frozen_V1.png`

High-Fidelity Source: `晨曦湖畔鱼获筛选界面.png`

Image: PNG / RGB, 941 × 1672, 1,531,888 bytes

SHA-256: `c384ee67ad10997807cd9b3313e0e79ef8de9f39e192c34428022906bc58e246`

## 1. 产品定位

F1 是「我的鱼获」页面内的**顶部内联筛选面板（Top Inline Filter Panel）**。

它不是独立页面，也不是 Bottom Sheet。用户点击右上角 Filter Action 后，筛选面板从页面顶部内容区向下展开，原 Timeline 向下让位；筛选完成后无需再点击“应用/查看结果”，结果即时刷新。

目标：高频条件一次点击完成，减少“进入二级页 → 选择 → 返回”的操作链路。

## 2. V1 正式筛选维度与顺序

固定顺序：

1. **鱼种**
2. **时间**
3. **尺寸**
4. **特殊记录**

**地点暂不进入 V1。** 不显示地点行，不提供地点入口，不为了维持“五维”概念保留占位。

## 3. F1 一级快捷项

### 3.1 鱼种

快捷项：不限 / 草鱼 / 鲫鱼 / 鲤鱼 / 更多 ›。

鱼种支持多选；同维度之间 OR。「不限」清空鱼种条件；「更多 ›」进入 F2，承载 50+ 鱼种、搜索、最近选择和完整列表。

### 3.2 时间

快捷项：不限 / 本月 / 近3个月 / 今年 / 自定义 ›。

时间单选；「自定义 ›」进入 F3 自定义日期范围。

### 3.3 尺寸

顶部提供轻量 **长度 / 重量** 切换。

长度快捷范围：不限 / <20 cm / 20–40 cm / 40–60 cm / ≥60 cm / 自定义 ›。

重量快捷范围：不限 / <0.5 kg / 0.5–1 kg / 1–3 kg / ≥3 kg / 自定义 ›。

「自定义 ›」进入 F4，可手动输入最小值 / 最大值；长度单位 cm，重量单位 kg。切换长度 / 重量只切换当前编辑视图，不丢失另一指标已设置条件；两者均设置时为 AND。

### 3.4 特殊记录

快捷项：不限 / 首条 / 最长 / 最重 / 里程碑。

允许多选，同维度 OR。「首条」指首条某鱼种记录；「里程碑」指数量里程碑。F1 不复制 Growth Mark 徽章视觉。

## 4. 条件组合

- 同一维度多选：**OR**
- 不同维度：**AND**
- Search + Filter：**SearchMatch AND FilterMatch**
- 「不限」= 该维度无约束

## 5. 交互

- Filter Action：展开 / 收起 F1。
- 面板展开时 Filter Action 保持 Active。
- 任一非默认筛选生效时，Filter Action 显示低权重状态提示点。
- Chip 点击即生效，Timeline 即时刷新。
- **不提供“查看 N 条鱼获 / 应用 / 完成”按钮。**
- 「重置」一次清空全部筛选并即时恢复 Timeline。
- 无筛选条件时「重置」降权或 disabled，位置不跳动。

## 6. 视觉布局

- 直接基于已冻结「我的鱼获」主页面。
- 面板位于 Header / Summary 下方、Timeline 上方。
- 展开时推动 Timeline 向下，不使用全屏遮罩。
- Mist / Lake White 轻 Surface；禁止重玻璃、强阴影。
- 圆角约 24dp；横向安全边距约 20–24dp。
- 每个维度固定为「标题 + 快捷 Chips」。
- 标题到 Chips：约 10–12dp。
- 相邻维度：约 **22–28dp**，禁止过挤。
- Chip 高度约 40dp，最小触控区域 44dp。
- 默认 Chip：低对比 Mist Gray。
- Selected Chip：轻 Lake Teal Surface + Deep Teal Text。
- 不使用彩色图标、Badge、卡片嵌套或重 Divider。
- **特殊记录固定第 4 项。**

## 7. 内容边界

F1 不承载：50+ 鱼种完整列表、自定义日期日历、精确尺寸输入表单、筛选结果空状态、地点筛选。

## 8. 子菜单映射

- F1 · 筛选面板 — **FROZEN**
- F2 · 鱼种选择 — Pending
- F3 · 时间选择 — Pending
- F4 · 尺寸筛选 — Pending
- F5 · 特殊记录 — Pending
- F6 · 筛选有结果 — Pending
- F7 · 筛选无结果 — Pending

## 9. 冻结结论

> **顶部内联展开 + 高频快捷筛选 + 即时更新 Timeline + 复杂条件进入二级选择器。**

地点不进入当前 V1；后续产品能力成熟后可新增，不影响当前 F1 信息架构。
