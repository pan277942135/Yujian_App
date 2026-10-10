# 记录日期 V2 · 导航 / 数据 / 状态合同（拟定版）

**Status: FROZEN / DESIGN TARGET（不代表 Kotlin 已落地）**

## 1. 首页入口与状态冲突
User-approved design target：从有数据首页统计条中的 **「记录天数」** 点击进入 `record_date_v2`，默认月视图。首页原工程合同 `design/pages/home/normal_home/engineering/State_Interaction_Contract_V1.md` 描述当时运行时「记录天数不点击」，属于 **existing runtime contract**；此处是**新版产品导航要求**，需在独立 Android 任务中修改和验收。**不许把设计已更新等同 Android 已实现。**

回退到 Normal Home 保留首页选中 FishRecord、Pager 位置；本模块返回首页或后退不变更鱼获数据。

## 2. 日期状态
Use a single state owner:
- `calendarMode: MONTH|YEAR`，默认为 MONTH。
- `focusedYearMonth: YYYY-MM`，首次定位当前年月；若当前月无鱼获，可选择最近有鱼获日期，但必须明显表明日期不在今天。
- `selectedDate: YYYY-MM-DD|null` 与 `selectedMonth: YYYY-MM` 保持一致；年模式选择月份后可直接显示该月按天记录。
- 点击「今天」：返回当前真实日期及月份，进入 MONTH，并选中今天；若今天没有鱼获，下方为空态。
- 月模式左右箭头按自然月前后移动、选择有效日期；年模式按自然年前后移动，改变年份后不能保留无效上一年的 selectedDate。
- 从年视图某日期进入月视图，自动聚焦对应年月日；从月视图切年视图，突出其所属月份。
- 网络加载/失败区分 Unknown / Empty / Error，禁止加载中先显示零记录或伪造鱼标。

## 3. 真值口径（非静态图数字）
- `recordDayCount`：有效 FishRecord 按产品规定本地时区 `capturedAt` 的 **去重自然日数**；不能用捕获次数替代。
- `catchCount`：该区间合法 FishRecord 条目数；允许同一天多条。
- `speciesCount`：有效鱼种 ID 去重数；未知鱼种不猜测新 species。
- `consecutiveRecordDays`：截至当前统计基准日，连续有鱼获自然日的长度；算法、空档和时区边界应测试，不能用7天活跃率替代。
- 月份卡的“X天·Y次”是 `countDistinct(date)` / `count(FishRecord)`，12个月鱼获数总和必须等于年度鱼获总次数；记录日数亦须一致。
- 已修订记录时间时必须重新归档所属日期；服务端缺时区或日期无效时使用有证据的日期回退，不得凭本机今天伪造。

## 4. 当天列表交互
- 月视图：点击有记录日期 → 展示该日 FishRecord 列表；点击鱼获卡 → FishRecordDetail(recordId)，使用 ID 而非日期数组索引。
- 点击没有记录的日期 → 保持选中态，下方显示“这一天暂无鱼获”及可回到相邻有记录日的轻引导（不新增拍照 CTA）。
- 年视图：选中月份 → 下方显示该月按日期降序的摘要条；点击日期摘要 → 切月视图并选中该日期；点击单条鱼获缩略图的行为需与卡片点击一致，避免热区冲突。
- 年月变更后统计条和鱼获列表由同一过滤快照派生，不能出现“年总68次，月总相加59次”的示例图式数据不一致。
- 当天超过可视空间时自然滚动，不使用截断无提示或点击死区。

## 5. 验收向量
1. 2026-09-01 周二、09-03 周四（工作日准确）。
2. 2026-02-28 / 2028-02-29，闰年与非闰年月份网格正确。
3. 同日三条鱼获 → 日标3、日统计3、记录天数1。
4. 9月7个有鱼获日 → 月份日数7；当月18条 → 捕获18，年份汇总等于12个月求和。
5. 年内空月/未来月展示0或未有记录，不显示错误的“资料缺失”。
6. 跨月/跨年切换、回退与「今天」，选择态无漂移。
7. loading / offline / partial cache 保持可见数据不闪零。
8. 点击记录天数：**仅设计目标**，Android 未实现前不要在验收清单中标 PASS。

## 6. 权威边界
该合同优先说明 V2 导航**目标行为**；若与当前 Android 代码或旧 Home V1 合同冲突，提交单独实现/版本修订，不得隐式覆盖既有 Kotlin。

## 7. 设计冻结

用户已批准此导航/数据设计目标。年图静态示例的68次与各月合计59次互相矛盾；永远以真实FishRecord聚合计算为准，不能当做测试通过或固化数字。Android的当前入口仍需单独开发与验收。详见 [视觉冻结决定](../review/Record_Date_V2_Freeze_Decision_20261010.md)。
