# 我的鱼获 · 搜索交互 V1

Status: **FROZEN**
Page: `my_catches_v2`
Recovered visual source: **我的鱼获空状态规范图.png**（Search Empty）

## 1. 入口

搜索是同一 My Catches 页面中的**搜索模式**，不是独立业务路由。

点击右上搜索 / 搜索框后：
- 搜索输入框进入顶部 Focused 状态；
- 键盘打开；
- 右侧显示 `取消`；
- 有输入时显示清除 `×`；
- 页面身份仍是「我的鱼获」；
- Timeline 数据结构不变。

## 2. 搜索范围

V1 自由文本搜索：
- 鱼种
- 地点
- 日期
- 可读 Growth Mark 文案

Placeholder：
`搜索鱼种、地点、日期`

## 3. 输入与结果

- 输入即时过滤；
- 无需额外“搜索”按钮；
- query trim 后参与匹配；
- Search 与 Filter 同时存在时：
  `SearchMatch AND FilterMatch`
- 有结果时仍保持：
  `月份 → 日期 → FishRecordRowCard`
- 隐藏空日期组和空月份组；
- 日期摘要按可见结果重新计算。

## 4. Focused / Cancel

Focused：
- 顶部搜索框成为当前操作焦点；
- 右侧显示 `取消`；
- 输入非空时同时显示 `×`。

`×`：
- 仅清空 query；
- 保持搜索模式与键盘。

`取消`：
- 退出搜索模式；
- 清空 query；
- 保留 Filter 条件；
- 回到 Filter 条件下的 Timeline。

## 5. Search Empty

保留：
- 当前 query
- `×`
- `取消`
- 当前 Filter 状态
- 底部全局 Camera Button

文案：

**没有找到相关鱼获**

辅助：

**你可以搜索：鱼种、地点、日期**

恢复动作：

**清除搜索**

“清除搜索”只清 query，不清 Filter。

底部 Camera Button 仍是全局记录入口，但不是 Search Empty 的恢复 CTA。

## 6. Runtime gap

当前 Runtime 搜索鱼种 / 地点，日期与 Growth Mark 文案匹配尚需开发对齐。
