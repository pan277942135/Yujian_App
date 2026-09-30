# 我的鱼获 · 搜索 V1

Status: **FROZEN**
Page: `my_catches_v2`
Visual family: **Morning_Lake_Master_V1 / BG_DATA**
Role: **在现有鱼获时间档案中快速定位内容，不建立新的浏览结构**

## 1. 产品边界

Search 是 My Catches 的查看状态，不是独立业务页面。

```
My Catches Timeline
  → Search Focused
  → Search Results / Search Empty
  → 退出后回到原 Timeline
```

Search 与 Filter 可以组合，但职责不同：

- Search：关键词定位；
- Filter：结构化缩小范围。

最终可见集合：

`SearchMatch AND FilterMatch`

禁止：
- 创建独立 SearchResultScreen；
- 搜索后改成普通扁平列表；
- 搜索时切换背景；
- 搜索时建立新的导航结构。

## 2. 入口与 Header

A 主页面 Header：

`←   我的鱼获   Search   Filter`

进入 Search Mode 后：

`←  [ 搜索鱼种、地点或日期 ]  取消`

冻结规则：
- 默认 My Catches 根页面沿用共享 Top Navigation V1 / `TITLE_ONLY`。Focused Search 是同一页面的 Search 状态，不是另一套根级 Top Navigation。
- Back 保留；
- 页面标题暂时让位给 Search Bar；
- 档案摘要在 Focused 输入阶段隐藏 / 降权；
- Search Bar 成为 Header 主体；
- 右侧必须显示 `取消`；
- 键盘自动打开；
- Search Focused 时 Filter icon 不继续占据右上主空间，但已有 Filter 条件仍生效。

## 3. Search Bar 视觉合同

Target：
- height: **44dp**
- radius: **14dp**
- left/right alignment: 与 My Catches 页面内容边距一致
- background: Mist Glass / 浅雾白
- border: 1dp 低对比湖灰
- search icon: 18–20dp / Deep Lake Blue
- input: 15sp
- placeholder: 14–15sp
- clear ×: 18dp
- focus ring: 极轻 Lake Blue
- shadow: none / extremely subtle
- gold: prohibited

Placeholder：

**搜索鱼种、地点或日期**

## 4. 搜索对象

V1 支持四类关键词：

1. 鱼种
   - 草鱼
   - 鲤鱼
   - 鳜鱼

2. 地点
   - 千岛湖
   - 钱塘江

3. 日期
   - 9月23日
   - 2026年9月
   - 2026-09-23

4. 可读 Growth Mark 文案
   - 首条草鱼
   - 第100条
   - 最长
   - 最重

不属于自由搜索：
- 长度 / 重量条件；
- 天气；
- 饵料；
- 装备；
- AI 自然语言问句。

这些由 Filter 或后续版本处理。

## 5. 匹配逻辑

- query trim；
- 中文直接匹配；
- case-insensitive（拉丁字符）；
- 多关键词组合采用 AND；
- 日期输入标准化后匹配；
- Search 与 Filter 组合时为 AND。

示例：

`草鱼 千岛湖`

= 同时满足“草鱼”与“千岛湖”。

## 6. B1 · Focused / 最近搜索

Search Mode 刚进入且 query 为空时：

顶部：
- Focused Search Bar；
- 右侧 `取消`。

下方插入轻量 Recent Search：

```
最近搜索                         清空
草鱼   千岛湖   9月23日
首条草鱼   最长   第100条
```

规则：
- 最多保存 6 个；
- 最近使用优先；
- 允许鱼种 / 地点 / 日期 / Growth Mark；
- Recent Search 是轻量辅助，不盖住整个 Timeline；
- 下方 Timeline 仍属于当前页面。

## 7. Recent Search 写入规则

不按每个输入字符保存。

仅在以下条件之一成立时写入：
- 用户点击过某条搜索结果；
- query 有有效结果并退出搜索。

例如输入过程：

`草 → 草鱼 → 草鱼 千 → 草鱼 千岛湖`

最终只保存：

`草鱼 千岛湖`

## 8. B2 · 搜索有结果

例如 query：

`草鱼`

Header：

`← [ 草鱼 × ] 取消`

Search Bar 下方显示轻量结果摘要：

**2 次鱼获**

结果继续使用原 Timeline：

```
2026年9月

23 SEP
千岛湖 · 1条鱼获 · 1种鱼
[草鱼]

03 SEP
千岛湖 · 1条鱼获 · 1种鱼
[草鱼]
```

不建立 Search Results 标题页。

## 9. 结果 Timeline 重算

搜索后：
- 无匹配日期隐藏；
- 无匹配月份隐藏；
- Month Sticky 继续有效；
- Day grouping 继续有效；
- 日期摘要按当前可见结果重算。

Day Summary：
- 单一地点：`地点 · N条鱼获 · M种鱼`
- 多地点 / 无地点：`N条鱼获 · M种鱼`

## 10. B3 · 搜索无结果

必须保留：
- 当前 query；
- ×；
- 取消；
- 当前 Filter 条件；
- 底部全局 Camera Button；
- BG_DATA。

内容：

**没有找到相关鱼获**

**你可以搜索：鱼种、地点、日期**

恢复动作：

**清除搜索**

冻结边界：
- 不显示大面积插画；
- 不显示“推荐搜索”标签墙；
- 不出现“重新拍摄”；
- 不出现“记录第一条鱼”；
- Camera Button 是全局记录入口，不是 Search Empty 的恢复 CTA。

## 11. × 与「取消」

### ×
- 仅清 query；
- 保持 Search Mode；
- 键盘保持；
- Recent Search 重新出现；
- Filter 条件不变。

### 取消
- 退出 Search Mode；
- 清 query；
- 关闭键盘；
- Filter 条件保留；
- 返回 Filter 条件对应 Timeline；
- 恢复进入 Search 前的滚动位置。

## 12. B4 · Search + Filter

Search 和 Filter 可以同时生效。

例如 Filter：
- 今年
- 千岛湖
- 长度 ≥ 40cm

Search：
- 草鱼

最终：

`草鱼 AND 今年 AND 千岛湖 AND 长度≥40cm`

Search Bar 下方只显示一行低权重 Filter Summary：

`筛选中 · 3个条件`

或：

`今年 · 千岛湖 · ≥40cm`

禁止把全部 Filter chips 大面积铺满搜索页。

点击 Filter Summary：
- 返回原页面并展开 F1 内联筛选面板；
- Search query 保留。

## 13. B5 · 结果详情与返回

点击 FishRecordRowCard：
- 进入 FishRecordDetail；
- 返回后仍处于 Search Mode；
- query 保留；
- Filter 保留；
- 结果集合保持；
- 恢复离开前滚动位置。

禁止返回后自动退出搜索。

## 14. Keyboard / Back

Search Mode：
- 键盘打开时 Timeline 仍可滚动；
- 点击 Timeline 空白不自动退出搜索；
- Android Back 第一次：收键盘；
- 再次 Back：退出 Search Mode。

## 15. Loading

本地搜索不需要独立 Loading。

如果未来依赖远程分页：
- 结果区域允许极轻 `正在查找…`；
- 不允许整页 Loading；
- 不允许 Modal Spinner；
- 不允许背景变暗。

## 16. 性能建议

开发建议：

`debounce = 150–250ms`

用户感知保持即时。

## 17. Background / Shared System

所有 Search 状态继承：

- Morning_Lake_Master_V1
- BG_DATA
- FishRecordRowCard V2
- Growth Mark V1
- Primary Capture Button

Search Mode 不修改 Background 参数：
- 不再模糊；
- 不变暗；
- 不重新降饱和；
- 不换纯色；
- 不换另一张湖景。

## 18. 高保真状态集合

Design Manager Search V1 二级子菜单：

- B1 · 最近搜索 / 空输入
- B2 · 搜索有结果
- B3 · 搜索无结果
- B4 · Search + Filter
- B5 · 交互与返回规则

B1–B4 使用无手机边框的 9:16 App Canvas。
B5 是交互状态图 / 规则板，不制造新的产品页面。

## 19. Rejected exploration

2026-09-29 生成的三张带手机外框 Search 探索图：

**不进入 Authority。**

原因：
- 手机外框不属于产品高保真；
- B3 出现不需要的推荐搜索；
- Search Header / 页面层级与本规范不完全一致；
- 部分 Growth Mark / 卡片文案沿用了旧稿；
- 应由 Design Manager 无边框 App Canvas 重建。

## 20. Freeze statement

Search V1 于 **2026-09-29** 完成整批冻结。

最终状态：

- B1 · 最近搜索 / 空输入：**FROZEN**
- B2 · 搜索有结果：**FROZEN**
- B3 · 搜索无结果：**FROZEN**
- B4 · Search + Filter：**FROZEN**
- B5 · 交互与返回规则：**FROZEN**

Authority：

- 产品 / 交互：本文件；
- B1–B4 视觉：`design/pages/fish_records/list/frozen/search_v1/` 中对应的已确认高保真 PNG；
- B5：本文件中的状态 / 返回 / Recent Search 行为合同。

此前带手机边框、探索稿以及 Design Manager 重构阶段生成的 SVG 重建稿均不进入 Authority。Design Manager 只展示上述冻结 PNG，不重新绘制页面。
