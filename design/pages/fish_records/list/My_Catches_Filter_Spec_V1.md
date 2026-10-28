# 我的鱼获 · 五维筛选规范 V1 · Historical

Status: **HISTORICAL / DEPRECATED AS CURRENT AUTHORITY**
This earlier five-dimension contract is retained for history only. Current filter behavior is defined by `My_Catches_Filter_Spec_V2.md`; its five dimensions, location row, Bottom Sheet flow, and Apply/Done action are not current V1 behavior.
Page: `my_catches_v2`

## 1. 五个冻结维度

1. **鱼种**
2. **时间**
3. **地点**
4. **特殊记录**
5. **尺寸**

Boolean rule:

- 同一维度内部：**OR**
- 不同维度之间：**AND**

示例：

`(草鱼 OR 鲤鱼) AND 千岛湖 AND 今年 AND 特殊记录=最重`

## 2. 鱼种

- multi-select
- 50+ 鱼种时不得堆成一屏标签
- 一级只显示摘要入口
- 二级选择器包含：
  - 搜索
  - 最近使用 / 最近记录
  - 全部鱼种
  - 已选择状态
- 已选鱼种可单独移除

## 3. 时间

单选时间范围：

Quick presets:
- 全部时间
- 近 7 天
- 近 30 天
- 今年

并保留：
- 自定义日期范围

时间维度不做多选。

## 4. 地点

- multi-select
- 大量地点时采用二级选择器
- 支持搜索
- 支持最近地点
- 支持全部地点
- 地点显示经过数据清洗后的用户可读名称
- 不把 `null` / 空字符串当地点标签

## 5. 特殊记录

- multi-select
- 使用 Growth Mark V1 的四类语义：
  - 首次记录
  - 数量里程碑
  - 同鱼种最重
  - 同鱼种最长
- 可按类别筛选，不依赖列表当下只显示的那一个 Growth Mark

## 6. 尺寸

两个独立范围：

- 长度范围（cm）
- 重量范围（kg）

规则：
- 两者都为空 = 不限制尺寸
- 只填一个维度时，只按该维度筛
- 同时填写长度和重量时，两者为 AND
- 没有对应长度 / 重量数据的记录，不应错误命中该尺寸条件

## 7. 已选条件展示

一级页面保留紧凑摘要，不把 50+ 选项全部展开。

已选条件：
- 以可移除标签 / 摘要显示
- 支持单项移除
- 支持“清除全部”
- 返回页面后仍保留筛选状态

## 8. 筛选后的 Timeline

筛选不会把页面改成普通搜索结果列表。

必须继续保留：

`月份 → 日期 → 当天鱼获集合`

日期 / 地点摘要根据**筛选后的可见记录**重新计算。

## 9. Filter Empty

当：
- totalFishRecords > 0
- filterApplied = true
- visibleFishRecords = 0

显示：

**没有找到符合条件的鱼获**

辅助文案：
**试试调整筛选条件**

保留页面背景和时间档案语义，不切换到首次使用空状态。

## 10. Runtime gap

当前 Android Runtime 仅实现：
- 鱼种
- 地点
- 时间

特殊记录与尺寸仍是开发交接缺口。

这不改变本文件作为 V1 设计 Authority 的冻结状态。


## 11. Filter Sheet 层级冻结

页面一级不把 50+ 鱼种、地点等选项直接铺成大量 chips。

点击筛选入口后：

### Level 1 · 五维摘要
Bottom Sheet 首层固定展示：
1. 鱼种
2. 时间
3. 地点
4. 特殊记录
5. 尺寸

每行展示：
- 维度名称；
- 当前选择摘要；
- Chevron；
- 已选时使用低权重 active state。

底部：
- 主操作：`完成`
- 次操作：`清除全部`（仅有筛选时出现）

### Level 2 · 二级选择器

鱼种：
- 搜索框
- 最近使用 / 最近记录
- 全部鱼种
- 多选 Checkbox

地点：
- 搜索框
- 最近地点
- 全部地点
- 多选 Checkbox

时间：
- 全部时间 / 近7天 / 近30天 / 今年
- 自定义日期范围

特殊记录：
- 第一次记录
- 数量里程碑
- 同鱼种最重
- 同鱼种最长
- 多选

尺寸：
- 长度 Min / Max
- 重量 Min / Max

二级完成后返回 Level 1，不关闭整个 Sheet。

### Sheet 行为
- 默认从底部弹出；
- 不做全屏新页面；
- 支持系统返回回到 Level 1 / 关闭 Sheet；
- Sheet 内滚动不影响后方 Timeline；
- 点击“完成”后关闭 Sheet，并在原页面即时更新 Timeline。
