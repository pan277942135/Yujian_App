# Top Navigation · 02 BACK_TITLE · Frozen Spec V1

Status: **FROZEN**
Version: **V1**
Date: **2026-09-29**

## 1. Purpose

`BACK_TITLE` is the canonical composition for second-level pages that need a Back action and a page title, but no page-level utility actions.

## 2. Composition

```text
safe drawing inset                    ← host page owns
┌─────────────────────────────────────┐
│ [ Back 44dp ]  8dp  页面标题         │
└─────────────────────────────────────┘
page content
```

The title is **left aligned after Back**. Do not center it with fake balancing spacers.

## 3. Geometry

- content min height: **56dp**;
- use `heightIn(min = 56dp)`;
- horizontal padding: **8dp**;
- Back touch target: **44×44dp**;
- Back glyph: **22dp**, inherited from Icon Action V1 / NAVIGATION;
- Back target → title gap: **8dp**;
- title occupies remaining width;
- no trailing reserved slot;
- background: transparent;
- divider: none.

Host page owns status-bar / safe-drawing top inset.

## 4. Typography

- semantic role: secondary page navigation title;
- target style: **20sp / Medium 500 / 26sp line-height**;
- color: **DeepLakeBlue #0B2D4B**;
- alignment: left;
- max lines: **1** at normal font scale;
- overflow: **Ellipsis**;
- no gold;
- no page-local Bold override.

## 5. Back action

Back is not visually redefined here.

Use:

- `Icon Action V1 / NAVIGATION / Back`;
- minimum target 44×44dp;
- rounded-outline 22dp glyph;
- content description: `返回`;
- Normal / Pressed / Disabled / Focus states come from Icon Action V1;
- no additional haptic, sound, scale, rotation or custom circular container.

Back behavior:

- pop the current navigation layer / return to the immediate parent;
- do not use Back to close a modal layer when semantic Close is required;
- do not replace Back with a text glyph such as `‹`.

## 6. Small screen

For 320–359dp widths:

- keep 8dp outer padding;
- keep 44dp Back target;
- keep 8dp Back-title gap;
- keep title at 20sp;
- truncate long title with Ellipsis;
- do not shrink title to force full copy.

## 7. Accessibility font scale

- do not manually reduce text size;
- component may grow above 56dp when scaled text metrics require it;
- keep Back vertically centered;
- preserve title legibility without clipping;
- title remains one line and may ellipsize if horizontal width is insufficient.

## 8. Frozen usage

### Recognition Result

- title: `识别结果`;
- variant: `BACK_TITLE`;
- legacy runtime `25sp / Bold + centered title + text glyph Back` is not authority;
- runtime closure should migrate to shared Icon Action Back + 20sp/Medium left title.

### Account / Privacy family

Representative titles:

- 我的
- 账号与登录
- 修改密码
- 数据与隐私
- 关于渔见
- 隐私政策
- 用户协议

These use the same BACK_TITLE composition.

Legacy Account `20sp / Bold + 58dp bar + 10dp padding` is close but not authority; target is this frozen contract.

### Edit Profile

- page-level edit screens use BACK_TITLE unless a later page-specific utility action is formally added;
- save/confirm business actions belong to page content or Action Button, not automatically to Top Navigation.

## 9. Relationship to BACK_TITLE_ACTIONS

`BACK_TITLE` contains **no right-side utility actions**.

If a page needs page-level Share / Fish Guide / More / Edit icon actions in the top bar, use `BACK_TITLE_ACTIONS` instead.

Do not dynamically add actions to BACK_TITLE and silently turn it into the third variant.

## 10. Prohibited

- centered title with fake symmetric spacer;
- text glyph `‹` as Back;
- 25sp/Bold Recognition Result exception;
- 20sp/Bold account-page exception;
- gold Back or title;
- visible glass / pill background around the whole top bar;
- divider by default;
- reducing title font size on narrow devices;
- placing page subtitle inside the top navigation.

## Appendix · Result page-scoped override (2026-10-10)

The global `BACK_TITLE` geometry, left alignment and the three-variant Top Navigation V1 family remain **FROZEN**. Section 8's original Recognition Result example is historical and **does not apply to the current RR01–RR05 Result V1.1 Frozen visual authorities**. For these five Result pages, use the registered viewport-centered `BACK_CENTER_TITLE` page composition, retaining the shared Icon Action Back; this is not a general Top Navigation variant. The separate Species Selector V1 **still uses BACK_TITLE**.

Authority: `design/system/components/top_navigation/exceptions/Recognition_Result_Centered_Title_V1.md`. All other shared `BACK_TITLE` consumers are unchanged.
