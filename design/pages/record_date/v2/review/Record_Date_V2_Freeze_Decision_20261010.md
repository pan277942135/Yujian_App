# 记录日期 V2 · 冻结决定（2026-10-10）

**用户批准：冻结现有两张高保 PNG，不重新生成或覆盖原图。**
**冻结范围：RD01 月视图、RD02 年视图的结构、版式、玻璃层级、控制区与选中态；RD03 页面设计目标。**
**不包括：静态图的生成示例数字、AI 鱼图、未经同源验证的湖景像素、Android 运行验收和未完成的独立视频/动效。**

## 1. 源文件（字节锁定）

| 编号 | 文件 | 尺寸 | SHA-256 |
|---|---|---|---|
| RD01 | `design/pages/record_date/v2/assets/Record_Date_Month_V2.png` | 853×1844 | `20977c8128c997221c044df8cc67d19daad4fcca92fd0466c9a0a200a819c4d5` |
| RD02 | `design/pages/record_date/v2/assets/Record_Date_Year_V2.png` | 935×1683 | `4a785c36d6de008cef05bff821e593767ad2ea3b7849bde8cf0ead5fc93cfaeb` |

**RD01：** 记录页顶栏、7列月历、鱼获日图标、选中日期淡金边界、当日鱼获时间线及卡片信息层级冻结。
**RD02：** 相同页面框架、年度概览、4列×3行月份索引、选中9月、选中月按日期排列鱼获信息的结构冻结。
**RD03：** 首页「记录天数」进入记录日期、月年切换、日期选择、今天与月年移动、动态统计口径冻结为设计目标。

## 2. 明确例外，不可冒充通过

1. **年图数据冲突**：顶部「68次鱼获」，12个月份卡的鱼获次数相加为 **59次**（差额9）。这是生成示例错误，不属于可冻结的数据真值。实际 `annualCatchCount = sum(monthlyCatchCount)`，所有数字都根据真实 FishRecord 动态计算，严禁写死 68 或 59。修正图中字面数字须创建新图版本并重新评审；本次不能改动冻结 PNG。
2. **背景同源未通过像素验证**：当前生成图的湖景不具有 Background System V1 所要求的像素同源证据。冻结的是 UI 设计，不是生成图湖景作为生产底图。实际页面背景唯一权威：`Morning_Lake_Master_V1` + `BG_DATA`（30% MistWhite、79% 饱和度、78% 对比度、106% 亮度、无全局模糊）。共享母版覆盖生成图中任何湖景差异。
3. **鱼照片仅是高保示意**：静态图中的 AI 鱼照片非真实来源。生产必须读取用户 FishRecord 真实照片，遵守 Fish Media Display V1。
4. **运行时导航未验收**：旧版 Normal Home 合同规定「记录天数」不可点击；这次批准的是新版目标，仍需后续单独更改 Kotlin、提供运行时截图和交互验证。不因设计菜单冻结而声称 Android 已经实现。
5. **其他尚未验收**：日期与星期、闰年、空记录、加载/异常、长列表、响应式与大字号、动效减弱、音视频资产，都需独立验收。不存在已冻结的背景视频或动态母版。

## 3. Authority 优先级

- **视觉结构：** RD01/RD02 原始 PNG，本次锁定，不得覆盖或重采样。
- **数据和交互：** `Record_Date_V2_Design_Spec.md` + `Record_Date_V2_Interaction_Contract.md`；图中的数字不是数据合同。
- **背景与鱼照片：** 公共 Background System V1 / BG_DATA + Fish Media Display V1，覆盖静态生成稿里的非权威内容。
- **工程运行：** 独立 Android 实现、自动化与截图门禁，不包含在这次设计冻结内。

## 4. Freeze Gate

- PNG 原始尺寸/哈希与 Git blob 一致：**PASS**
- RD01、RD02 页面结构与用户批准：**FROZEN**
- RD03 设计目标/数据语义：**FROZEN**
- 生成图 68 vs 59 示例数值：**KNOWN EXCEPTION / NOT DATA AUTHORITY**
- 背景与 canonical BG_DATA 同源像素：**UNVERIFIED / CANONICAL BACKGROUND WINS**
- Android 与视频/动效端到端：**NOT TESTED**

**结论：记录日期 V2 的视觉版式和行为设计已冻结。整项产品运行能力尚未闭环；未来任何视觉版式/源 PNG 更新须创建 V2.x 或 V3，并保留既有指纹。**
