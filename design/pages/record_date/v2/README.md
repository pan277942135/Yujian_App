# 记录日期 V2 · Record Date / Calendar Archive

**Design Manager 一级菜单：记录日期**  |  **设计阶段：ACTIVE_CLOSURE / 原始高保候选**  |  **更新时间：2026-10-10**

此模块由 Normal Home「记录天数」进入，使用独立的「月 / 年」切换。现有 Android 的 Record Days CTA 可能仍未跳转，本仓库更新属于**设计系统交付，不代表 Android 已实现或验收**。

## 子菜单

- [01 · 月视图 V2](assets/Record_Date_Month_V2.png) — 2026年9月的月历、鱼获日标记、9月3日当天3条鱼获。**原图 853×1844 / SHA-256 `20977c8128c997221c044df8cc67d19daad4fcca92fd0466c9a0a200a819c4d5`**。
- [02 · 年视图 V2](assets/Record_Date_Year_V2.png) — 2026 年、4×3 月份索引、选中9月、按天归档列表。**原图 935×1683 / SHA-256 `4a785c36d6de008cef05bff821e593767ad2ea3b7849bde8cf0ead5fc93cfaeb`**。
- [03 · 交互与数据规范](spec/Record_Date_V2_Interaction_Contract.md) — 导航、日期分组、统计口径、边界及 Reduce Motion。

## 工作依据

- [V2 页面设计规范](spec/Record_Date_V2_Design_Spec.md)
- [V2 交互与数据合同](spec/Record_Date_V2_Interaction_Contract.md)
- [V2 动效 / 视频边界](spec/Record_Date_V2_Motion_Video_Contract.md)
- [高保审议与冻结 Gate](review/Record_Date_V2_Visual_Audit.md)
- [资产指纹 Manifest](assets/asset_manifest.json)

## 背景权威

复用 [Background System V1](../../../system/backgrounds/morning_lake_v1/Background_System_Spec_V1.md) 的 **Morning_Lake_Master_V1 → BG_DATA**：30% MistWhite、79% saturation、78% contrast、106% brightness、Global Blur OFF。无太阳公共母版唯一权威；PNG 中湖景只是待校验的合成视觉，不是需要另生成一套湖景背景。

## 冻结边界

两张原始 PNG 已入 GitHub。**资产字节校验通过 ≠ 页面视觉设计已获批准，也不等于 Android Runtime PASS**。当前图中所有鱼获数量、鱼种照片、日期文字仅作表现样例，见审议清单。对照新背景与真实 FishRecord 数据进行校正、通过用户验收后方可将 `ACTIVE_CLOSURE` 改为 `FROZEN`。
