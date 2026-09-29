# TopNavigation V1

Status: **PARTIAL**  
Current structure: **3 direct submenus**

```text
顶部导航
├── 01 · 标题
├── 02 · 返回 + 标题
└── 03 · 返回 + 标题 + 工具动作
```

## Scope

Top Navigation only governs:

- top-bar composition;
- title placement and hierarchy;
- back/navigation slot;
- page-level utility-action slot;
- spacing, alignment, truncation and action-count rules.

It does **not** redefine Icon Action visuals.

## 01 · TITLE_ONLY — FROZEN

Frozen authority:

- `design/system/components/top_navigation/title_only/Title_Only_Spec_V1.md`
- `design/system/components/top_navigation/title_only/visual_contract.json`
- `design/system/components/top_navigation/title_only/visual/authority/01_Base_Geometry.svg`
- `design/system/components/top_navigation/title_only/visual/authority/02_Real_Usage.svg`
- `design/system/components/top_navigation/title_only/visual/authority/03_Long_Title_Small_Screen.svg`

Frozen consumers:

- 我的鱼获
- 鱼鉴

Core rules:

- no Back;
- no Utility action;
- transparent background;
- host owns safe-area inset;
- min content height **64dp**, using `heightIn(min=64dp)`;
- horizontal padding **16dp**;
- title = **28sp / Medium 500 / 34sp line-height**;
- color = **DeepLakeBlue #0B2D4B**;
- left aligned;
- one line + Ellipsis at normal font scale;
- small screen keeps 28sp and truncates rather than shrinking;
- accessibility font scaling may increase component height;
- subtitle / Search / Filter / progress belong below Top Navigation.

Runtime note:

- My Catches legacy `29sp / Bold + subtitle` is not shared-component authority and should be aligned during runtime closure.

## 02 · BACK_TITLE — FROZEN

Frozen authority:

- `design/system/components/top_navigation/back_title/Back_Title_Spec_V1.md`
- `design/system/components/top_navigation/back_title/visual_contract.json`
- `design/system/components/top_navigation/back_title/visual/authority/01_Base_Geometry.svg`
- `design/system/components/top_navigation/back_title/visual/authority/02_Real_Usage.svg`
- `design/system/components/top_navigation/back_title/visual/authority/03_Long_Title_Small_Screen.svg`

Frozen consumers include:

- 识别结果
- 账号与隐私 family
- 编辑资料
- 独立设置 / 法律文档页

Core rules:

- secondary-page composition is **left aligned after Back**;
- min content height **56dp**, using `heightIn(min=56dp)`;
- outer horizontal padding **8dp**;
- Back target **44×44dp** / glyph **22dp**;
- Back → title gap **8dp**;
- title = **20sp / Medium 500 / 26sp line-height**;
- title color = **DeepLakeBlue #0B2D4B**;
- one line + Ellipsis;
- no trailing fake-balance spacer;
- transparent background;
- no divider;
- host page owns safe-area inset;
- accessibility text may grow the bar vertically;
- Back states come from `Icon Action V1 / NAVIGATION`.

Runtime notes:

- Recognition Result legacy `25sp / Bold + centered title + text-glyph Back` is not authority.
- Account PageScaffold legacy `20sp / Bold + 58dp / 10dp` is not authority.
- Both should converge to the frozen BACK_TITLE contract during runtime closure.

## 03 · BACK_TITLE_ACTIONS

Use when a second-level page also needs page-level utilities.

Current primary example:

- 鱼获详情 → Back + title + Fish Guide / Share

Rules:

- Back → `Icon Action V1 / NAVIGATION`;
- right-side actions → `Icon Action V1 / UTILITY`;
- utility actions remain visually below title;
- target design supports **1–2 direct utility actions**;
- if future requirements exceed the direct-action limit, collapse excess actions into `More`;
- B-side card flip is contextual to the B-side surface and does not automatically become a Top Navigation action.

## Removed legacy variant

`search_filter` is removed.

Reason:

- Fish Guide V1 explicitly does not keep Search in the top bar;
- My Catches Search / Filter belong to page content/tooling;
- Search Field leading magnifier may be decoration rather than an action.

## Icon Action V1 binding

- Back / Close semantics are governed by Icon Action V1 / NAVIGATION.
- Fish Guide / Share / More and other independently tappable top-bar tools use Icon Action V1 / UTILITY.
- Text Action chevron and Search Field decorative icons are excluded.

## Current completion

`TITLE_ONLY` and `BACK_TITLE` are now FROZEN. `BACK_TITLE_ACTIONS` still requires detailed visual closure before the parent Top Navigation V1 can become FROZEN.
