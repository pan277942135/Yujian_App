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

## 01 · TITLE_ONLY

Use for root-level pages such as:

- 我的鱼获
- 鱼鉴

Rules:

- no Back;
- page title is the primary top-bar visual;
- Search / Filter do not become Top Navigation variants;
- page-specific Search / Filter live in page content or input/tool systems.

## 02 · BACK_TITLE

Use for second-level pages such as:

- 识别结果
- 账号与登录
- 修改密码
- 数据与隐私
- 隐私政策 / legal documents

Rules:

- Back uses `Icon Action V1 / NAVIGATION`;
- title remains dominant over navigation affordance;
- Back and title are one layout composition, not two page-specific implementations.

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

The information architecture and semantic split are now authoritative, but detailed visual geometry / truncation / small-screen / state reference boards still need closure before Top Navigation V1 can be marked FROZEN.
