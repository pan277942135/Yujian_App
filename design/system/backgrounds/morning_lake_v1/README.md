# 渔见 Morning Lake Background System V1

Status: **FROZEN**
Version: **V1**
Scope: **Shared Background Authority**
Frozen date: **2026-09-29**

## 1. 设计结论

渔见背景系统采用：

**同一个 Morning Lake 视觉家族 + 两张 Canonical Master + 五种背景类型。**

两张母版承担不同职责：

1. **Morning_Lake_Sunrise_Hero_V1**
   - 有太阳 / 暖金晨光
   - **仅空首页使用**
   - 负责品牌首次体验与“开始一次出钓”的 Hero 时刻

2. **Morning_Lake_Master_V1**
   - 无太阳 / 冷灰蓝晨雾山湖
   - 除空首页之外的湖景页面统一使用
   - BG_ENV_HERO / BG_CONTENT / BG_DATA 只通过雾化、饱和度、对比度、亮度区分

不得再为单个页面另造第三套湖景世界。

## 2. Canonical Masters

### Morning_Lake_Master_V1

Path:

`assets/Morning_Lake_Master_V1.png`

- Source filename: `晨雾山湖与远山.png`
- Dimensions: 941 × 1672
- Mode: RGB
- SHA-256: `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`
- Role: default shared lake master
- Used by: Normal Home, Recognition Result, FishRecordDetail, Login / Account, My Catches, Fish Guide and other lake-content pages

### Morning_Lake_Sunrise_Hero_V1

Path:

`assets/Morning_Lake_Sunrise_Hero_V1.png`

- Source filename: `晨曦映照的静谧山湖.png`
- Dimensions: 941 × 1672
- Mode: RGB
- SHA-256: `28313d84c8cf3fb9db52196874e9c4d55483c99ff61cf579cac3ecc69af6cc50`
- Role: Empty Home hero-only master
- Used by: **Empty Home only**

## 3. 五种背景类型

- `BG_ENV_HERO`
  - Empty Home → Sunrise Hero Master
  - Normal Home → Morning Lake Master
- `BG_CONTENT`
  - Morning Lake Master
  - Recognition Result / FishRecordDetail / Login / Account / Form
- `BG_DATA`
  - Morning Lake Master
  - My Catches / Fish Guide / dense archive pages
- `BG_CAPTURE`
  - current user photo
  - Recognition Processing
- `BG_SOLID_FALLBACK`
  - solid color only
  - missing background / load failure / transient error

## 4. 数值权威

唯一参数权威：

`treatment_contract.json`

BG_ENV_HERO / BG_CONTENT / BG_DATA 只允许改变：

- Mist / 雾白覆盖
- Saturation / 饱和度
- Contrast / 对比度
- Brightness / 亮度

Global Blur is frozen to **OFF**.

不得通过重新生成山、湖、天空、太阳、岸线、树木或前景来制造背景层级。

## 5. 已批准跨页面视觉验证

Approved review board:

`validation/Morning_Lake_Content_Adaptation_Approved_V1.png`

- Dimensions: 992 × 1586
- SHA-256: `911ab26ad1c53fb2d10c7e225ad8bfffeb959246be2fef29abf80cf979801cce`
- Review result: **APPROVED**
- Covers:
  - Normal Home / BG_ENV_HERO
  - Recognition Result / BG_CONTENT
  - FishRecordDetail / BG_CONTENT
  - Login / BG_CONTENT
  - My Catches / BG_DATA
  - Fish Guide / BG_DATA

Important:

**The board is visual-review evidence. Its displayed percentages are explanatory labels, not engineering parameter authority.**
Exact frozen values come only from `treatment_contract.json`.

## 6. 页面前景不属于背景系统

Background System 不包含：

- Empty Home rod / line / bobber / ripple
- user catch photos
- fish Hero
- glass card
- navigation
- button
- copy
- account avatar
- Recognition AI field / contour / halo

## 7. Authority Rule

1. This package is the shared background authority.
2. Page-level UI cannot create a new lake-world asset.
3. Empty Home may use only the Sunrise Hero master exception.
4. All other lake pages use Morning_Lake_Master_V1.
5. Page frozen references remain authoritative for page layout and page-specific foreground.
6. BG_CAPTURE and BG_SOLID_FALLBACK are non-lake background types.
7. Any future change to either master or a frozen treatment creates a new Background System version/revision.
