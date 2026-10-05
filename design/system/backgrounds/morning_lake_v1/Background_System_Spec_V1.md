# 渔见背景系统 V1 · Frozen Spec

Status: **FROZEN**
Version: **V1**
Visual family: **Morning Lake**
Frozen date: **2026-09-29**

## 1. 系统结构

```
Morning Lake Background System V1
│
├── Morning_Lake_Sunrise_Hero_V1
│   └── Empty Home only
│
├── Morning_Lake_Master_V1
│   ├── BG_ENV_HERO → Normal Home
│   ├── BG_CONTENT  → Recognition Result / FishRecordDetail / Login / Account
│   └── BG_DATA     → My Catches / Fish Guide
│
├── BG_CAPTURE
│   └── current user photo
│
└── BG_SOLID_FALLBACK
    └── solid fallback colors
```

## 2. 视觉家族约束

Morning Lake 必须保持：

- 清晨，而非黄昏
- 湖面淡灰蓝绿
- 远山与自然植被
- 薄雾与柔和空气透视
- 安静、自然、克制、纪录片感

### Sunrise Hero 例外

Empty Home 可以拥有：

- 明确太阳
- 更强晨光
- 更明显暖金反射

但它仍属于 Morning Lake family。

### 非空首页页面禁止

- 强太阳视觉中心
- 大面积暖橙 wash
- 旅游海报式晨曦
- 强 HDR
- 高饱和青蓝湖水
- 不同地貌 / 不同湖世界
- 页面自行重新生成背景

## 3. BG_ENV_HERO

### Empty Home

Master:

`Morning_Lake_Sunrise_Hero_V1`

Treatment:

- MistWhite veil: 2%
- Saturation: 98%
- Contrast: 98%
- Brightness: 100%
- Global Blur: OFF

The sun is part of the source master. Do not add another sun layer through background treatment.

### Normal Home

Master:

`Morning_Lake_Master_V1`

Treatment:

- MistWhite veil: 2%
- Saturation: 98%
- Contrast: 98%
- Brightness: 100%
- Global Blur: OFF

Goal:

Environment remains present, while catch content becomes the first information focus.

## 4. BG_CONTENT

Master:

`Morning_Lake_Master_V1`

Pages:

- Recognition Result
- FishRecordDetail
- Login
- Account / Privacy
- Register / Profile / Agreement when a lake background is used

Frozen treatment:

- MistWhite veil: **15%**
- Saturation: **91%**
- Contrast: **89%**
- Brightness: **103%**
- Global Blur: **OFF**
- Target environmental salience vs BG_ENV_HERO: **75%**

Goal:

Same lake world remains recognizable, but page content clearly outranks the background.

## 5. BG_DATA

Master:

`Morning_Lake_Master_V1`

Pages:

- My Catches
- Fish Guide
- other dense archive/data pages

Frozen treatment:

- MistWhite veil: **30%**
- Saturation: **79%**
- Contrast: **78%**
- Brightness: **106%**
- Global Blur: **OFF**
- Target environmental salience vs BG_ENV_HERO: **48%**

Goal:

Retain brand world and spatial atmosphere while allowing rapid scanning of dense content.

## 6. BG_CAPTURE

Source:

current user photo

Frozen behavior:

- opaque: true
- ContentScale: Crop
- original photo tint: none
- no Morning Lake image beneath or above the photo
- AI ambient field / contour / halo are overlay layers, not background treatment

Photo unavailable fallback:

`#102D35`

## 7. BG_SOLID_FALLBACK

Frozen colors:

- Light content fallback: `#F7FAFB`
- Dark capture fallback: `#102D35`

Rules:

- fallback is transient
- cannot become a permanent page background
- cannot create a third visual world

## 8. 允许修改的维度

For BG_ENV_HERO / BG_CONTENT / BG_DATA, V1 only permits:

1. MistWhite veil
2. saturation
3. contrast
4. brightness

Frozen:

- source composition
- mountains
- shoreline
- sky
- lake
- vegetation
- camera viewpoint
- crop policy
- Global Blur = OFF

Any other visual change requires V2 or an approved revision.

## 9. 已批准视觉验证

Authority evidence:

`validation/Morning_Lake_Content_Adaptation_Approved_V1.png`

Review coverage:

- Normal Home
- Recognition Result
- FishRecordDetail
- Login
- My Catches
- Fish Guide

Result: **PASS / APPROVED**

Empty Home Sunrise Hero remains separately governed by the approved Empty Home visual authority.

## 10. Freeze Gate

- [x] Two canonical masters archived
- [x] Native dimensions recorded
- [x] SHA-256 recorded
- [x] Five background types mapped
- [x] Three lake treatment presets fixed to exact values
- [x] BG_CAPTURE fixed
- [x] BG_SOLID_FALLBACK fixed
- [x] Cross-page content review approved
- [x] Design Manager mapping complete
- [x] No third lake-world authority permitted

**Background System V1 = FROZEN.**
