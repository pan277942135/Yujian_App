# 我的鱼获 · Empty States V1

Status: **FROZEN**
Page: `my_catches_v2`
Recovered visual source: **我的鱼获空状态规范图.png**

Frozen Visual Authority: `design/pages/fish_records/list/frozen/empty_states_v1/My_Catches_Empty_States_V1_Frozen.png`

SHA-256: `5258ca2f2683ec56c33c872fe80c06cc878b83ab473577dc47edc22052746ac8`

The frozen PNG is the visual authority for this Design Manager view. Do not substitute an SVG, HTML/CSS recreation, screenshot, or re-encoded image.

三类状态共享同一 My Catches 页面框架与全局 Camera Button：

1. Archive Empty
2. Filter Empty
3. Search Empty

---

## 1. Archive Empty

Condition：
- 用户没有任何正式 FishRecord。

文案：

**还没有鱼获记录**

辅助：

**拍下第一条鱼，开始你的鱼获时间线**

底部提示：

**记录第一条鱼**

主行动：
- 底部共享 Camera Button。

不要额外叠一个同级主按钮。

---

## 2. Filter Empty

Condition：
- raw records > 0
- filterApplied = true
- visible records = 0
- query 为空

文案：

**没有找到符合条件的鱼获**

辅助：

**试试调整筛选条件**

主恢复动作：

**清除筛选**

次恢复动作：

**修改筛选**

同时保留：
- Filter active indicator / 已选摘要；
- 底部全局 Camera Button。

Camera Button 是记录入口，不替代筛选恢复 CTA。

---

## 3. Search Empty

Condition：
- raw records > 0
- query 非空
- search result = 0

顶部保持：
- Focused Search
- 当前 query
- `×`
- 右侧 `取消`

文案：

**没有找到相关鱼获**

辅助：

**你可以搜索：鱼种、地点、日期**

恢复动作：

**清除搜索**

同时保留底部全局 Camera Button。

---

## 4. Priority

```
loading / error
→ Archive Empty
→ Search Empty
→ Filter Empty
→ Timeline
```

Search + Filter 同时存在且结果为空：
先显示 Search Empty。

## 5. Shared visual rules

- 页面身份始终是「我的鱼获」；
- 使用 My Catches 当前背景 Authority；
- 不切到新的装饰页；
- 不使用大型插画；
- 空状态内容保持轻、居中、留白；
- 全局 Camera Button 始终固定在底部中心，不参与列表滚动；
- Search / Filter 的恢复 CTA 与 Camera Button 是不同语义层级。
