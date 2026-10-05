# Top Navigation · 01 TITLE_ONLY · Frozen Spec V1

Status: **FROZEN**
Version: **V1**
Date: **2026-09-29**

## 1. Purpose

`TITLE_ONLY` is used on root-level pages where no back navigation or page-level utility action belongs in the top bar.

Current frozen usage:

- 我的鱼获
- 鱼鉴

## 2. Composition

```text
safe drawing inset         ← host page owns
┌──────────────────────────┐
│  页面标题                 │  ← TITLE_ONLY
└──────────────────────────┘
page content               ← page owns
```

The top navigation contains **only one visible element: page title**.

Not part of TITLE_ONLY:

- subtitle;
- archive summary;
- Search Field;
- Filter trigger / Filter chips;
- progress summary;
- Back;
- Utility actions;
- separator / glass surface.

## 3. Geometry

- content min height: **64dp**;
- use `heightIn(min = 64dp)`, not hard fixed height for accessibility font scaling;
- horizontal padding: **16dp**;
- title vertical alignment: center within the component content area;
- title left edge aligns to the shared page content grid;
- no additional leading icon slot;
- no trailing action reservation;
- component background: transparent;
- no divider.

Host page owns status-bar / safe-drawing top inset.

## 4. Typography

- role: `DISPLAY / pageTitle`;
- Android token: `YuJianTypography.pageTitle`;
- font size: **28sp**;
- line height: **34sp**;
- weight: **Medium / 500**;
- color: **DeepLakeBlue #0B2D4B**;
- alignment: left;
- max lines: **1** under normal font scale;
- overflow: **Ellipsis**;
- no letter spacing override;
- no gold.

## 5. Small screen

For narrow screens such as 320–359dp:

- keep 16dp horizontal padding;
- keep 28sp title size;
- do not compress to a smaller typography tier;
- preserve one line and Ellipsis when required;
- content beneath the top navigation may adapt independently.

## 6. Accessibility font scale

- do not manually reduce font size;
- top navigation may grow vertically above the 64dp minimum when scaled text requires it;
- preserve at least 12dp effective vertical breathing around the scaled title where possible;
- avoid clipping descenders or CJK glyph bounds.

## 7. Page-content relationship

`TITLE_ONLY` ends after its own content area.

The consuming page owns the next vertical spacing. Recommended first-section separation is **8–16dp**, depending on the page composition.

Subtitle / explanatory copy must not be embedded into Top Navigation to create page-specific header variants.

## 8. Frozen page semantics

### 我的鱼获

- title: `我的鱼获`;
- variant: `TITLE_ONLY`;
- background: `BG_DATA`;
- Search / Filter start below Top Navigation;
- legacy runtime `29sp / Bold` and the subtitle `按时间留存每一次真实鱼获` are not shared-component authority and must be aligned during runtime closure.

### 鱼鉴

- title: `鱼鉴`;
- variant: `TITLE_ONLY`;
- background: `BG_DATA`;
- no Search in Fish Guide Home V1;
- progress belongs to page content below Top Navigation.

## 9. Prohibited

- center-aligned root title;
- 29sp/Bold page-local override;
- subtitle inside Top Navigation;
- Search / Filter inside TITLE_ONLY;
- top-bar glass plate;
- divider line by default;
- gold title;
- shrinking title below 28sp solely to fit a narrow device.
