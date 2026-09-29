# 我的鱼获 · Empty States V1

Status: **FROZEN**
Page: `my_catches_v2`

三类空状态必须严格区分：

1. Archive Empty
2. Search Empty
3. Filter Empty

它们共享同一页面结构和 BG_DATA，不生成新的装饰背景。

---

## 1. Archive Empty

Condition:
- 用户确实没有任何正式 FishRecord

保留：
- 页面标题「我的鱼获」
- 搜索 / 筛选入口可保持低权重可见
- BG_DATA / Morning Lake 世界
- 页面底部主拍摄入口

主文案：

**还没有鱼获记录**

辅助文案：

**拍下第一条鱼，开始你的鱼获时间线**

主行动：

**记录第一条鱼**

视觉：
- 时间轴位置保留一个轻量 empty node / 起点暗示；
- 不使用大面积插画；
- 主拍摄按钮位于底部中心；
- “记录第一条鱼”与相机 CTA 只形成一个主要行动，不重复出现两个同级主按钮。

禁止：
- “暂无数据”
- 数据报表式 0/0/0
- 红色警告
- 复杂新手教程

---

## 2. Search Empty

Condition:
- raw records > 0
- query 非空
- search result = 0

必须保留：
- 搜索框
- 当前 query
- 清除 `×`
- Filter 状态
- Timeline 页面框架

示例：
`🔍 鳄鱼 ×`

主文案：

**没有找到相关鱼获**

辅助文案：

**你可以搜索：鱼种、地点、日期**

行动：

**清除搜索**

清除搜索：
- 仅清 query；
- 不清除筛选；
- 不触发拍照入口作为主行动。

禁止：
- “重新拍摄”
- “去记录鱼获”
- 自动改写用户输入

---

## 3. Filter Empty

Condition:
- raw records > 0
- filterApplied = true
- visible records = 0
- query 为空

必须保留：
- Timeline 页面框架
- 搜索
- 当前 Filter summary / chips

主文案：

**没有找到符合条件的鱼获**

辅助文案：

**试试调整筛选条件**

行动：
- 主操作：**修改筛选**
- 次操作：**清除筛选**

清除筛选：
- 清除全部 5 维筛选；
- query 不受影响（本状态下 query 为空）；
- 恢复完整 Timeline。

---

## 4. Priority

Empty state resolution priority:

```
loading / error
→ Archive Empty
→ Search Empty
→ Filter Empty
→ Timeline
```

Search 与 Filter 同时存在且结果为空：
先显示 Search Empty。

---

## 5. Shared visual rules

三类 Empty：
- 不改变 Header；
- 不改变 BG_DATA；
- 不切换到其它页面；
- 不使用大型插画占据页面主体；
- 空状态卡使用轻玻璃 / 雾白 Surface；
- 操作保持克制；
- 用户始终知道自己仍在「我的鱼获」。
