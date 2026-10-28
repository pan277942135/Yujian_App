# 我的 · Page Spec V2

Status: **FROZEN — IA / Behavior / Content Structure**
Visual status: **FROZEN — COMPOSITE AUTHORITY**

Visual authority:
- base Hi-Fi: `design/pages/account_privacy/My/00_My.png`
- adjustments: `design/pages/account_privacy/Active_Path_Visual_Adjustment_Authority_V1.md`
Page ID: `account_privacy_v1.02a`

## 1. Purpose

“我的” is the authenticated user's personal account hub.

It is not a second Home dashboard and must not compete visually with the fishing-memory Home experience.

Primary jobs:

1. establish user identity;
2. expose lightweight fishing-history summary;
3. enter profile editing;
4. enter Account & Security;
5. enter About / Legal.

## 2. Entry / exit

Entry:

`Home avatar → 我的`

TopNav:

- Shared `BACK_TITLE`
- title: `我的`

Back:

- returns to previous Home state;
- no intermediate account dashboard.

## 3. Background

Current authority:

`Morning_Lake_Master_V1 / BG_CONTENT`

The historical `My/00_My.png` visibly uses a stronger sunrise / golden-reflection treatment and is **not** current Visual Authority.

Do not use:

- sunrise hero background;
- BG_ENV_HERO prominence;
- heavy gold atmosphere;
- game/profile dashboard styling.

## 4. Identity card

Order:

1. avatar;
2. display name;
3. account identity when useful.

Avatar semantics are owned by Shared Default Profile Avatar V1:

- real avatar available → real avatar;
- avatar missing / load failed → Default Profile Avatar V1;
- Guest is a different state and is not rendered here because this is an authenticated page.

Display name:

- nickname when non-empty;
- otherwise username.

Secondary identity:

- show account / username only when it adds information;
- do not use lifestyle marketing copy as identity metadata.

Tap behavior:

- identity card itself is not a second Edit Profile shortcut;
- explicit `编辑个人资料` row owns edit navigation.

## 5. Fishing summary

Three values:

- 鱼种
- 鱼获
- 记录天数

Data is runtime-derived.

Interactions:

- 鱼种 → Fish Guide
- 鱼获 → My Catches
- 记录天数 → non-clickable informational metric

Forbidden:

- rarity;
- level;
- streak;
- trophies;
- achievement progress;
- artificial count-up animation.

## 6. Settings rows

Canonical order:

1. `编辑个人资料`
2. `账号与安全`
3. `关于渔见`

Do not show `账号与登录` in new design copy.

Each row:

- single clear label;
- optional restrained supporting text only when necessary;
- chevron uses shared navigation affordance;
- minimum 44dp target.

## 7. Content hierarchy

The identity card is primary.

Statistics are supporting context.

Settings/navigation rows are functional.

Do not:

- enlarge statistics into a performance dashboard;
- add badges/red dots without a real product state;
- add “完善资料” nagging UI for default-avatar users;
- automatically navigate users to Edit Profile.

## 8. States

Required design semantics:

- normal authenticated profile;
- default-avatar fallback;
- avatar loading failure → same default-avatar fallback;
- statistics zero values are valid and render as `0`;
- long nickname truncates safely;
- missing nickname falls back to username.

Loading/error of statistics must not remove account navigation.

## 9. Visual freeze

Visual is **FROZEN via composite authority**.

The existing `My/00_My.png` remains the byte-preserved base Hi-Fi. Do not regenerate it.

Current implementation must apply the frozen adjustments in `Active_Path_Visual_Adjustment_Authority_V1.md`, including BG_CONTENT, Shared Default Profile Avatar V1 and `账号与安全` naming. Shared authorities override obsolete screenshot pixels.
