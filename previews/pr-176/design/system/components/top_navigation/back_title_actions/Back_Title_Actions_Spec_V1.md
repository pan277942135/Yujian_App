# Top Navigation · 03 BACK_TITLE_ACTIONS · Frozen Spec V1

Status: **FROZEN**
Version: **V1**
Date: **2026-09-29**

## 1. Purpose

`BACK_TITLE_ACTIONS` is used when a secondary page needs:

- one Back navigation action;
- one page title;
- one or two page-level Utility actions.

V1 frozen consumer: **FishRecordDetail / 鱼获详情**.

## 2. Composition

```text
safe drawing inset                              ← host page owns
┌──────────────────────────────────────────────┐
│ [Back 44] 8dp Title      ≥8dp [U1 44] 8dp [U2 44] │
└──────────────────────────────────────────────┘
page content
```

The title remains left-aligned after Back. Utility actions are right-aligned.

## 3. Geometry

- content min height: **56dp**;
- implementation: `heightIn(min = 56dp)`;
- outer horizontal padding: **8dp**;
- Back touch target: **44×44dp**;
- Back glyph: **22dp**;
- Back → title gap: **8dp**;
- title → utility group minimum gap: **8dp**;
- Utility touch target: **44×44dp** each;
- Utility glyph: **20–22dp** from Icon Action V1;
- Utility-to-Utility gap: **8dp**;
- title takes remaining width and ellipsizes;
- background: transparent;
- divider: none;
- no fake trailing spacer.

Host page owns status-bar / safe-drawing inset.

## 4. Typography

Same secondary-page title tier as BACK_TITLE:

- **20sp / Medium 500 / 26sp line-height**;
- DeepLakeBlue `#0B2D4B`;
- left aligned;
- normal font scale: maxLines = 1;
- overflow = Ellipsis;
- no page-local Bold override;
- no gold.

## 5. Back

Back uses:

- `Icon Action V1 / NAVIGATION / Back`;
- 44×44dp minimum target;
- 22dp glyph;
- content description `返回`;
- Icon Action states provide Normal / Pressed / Disabled / Focus.

`BACK_TITLE_ACTIONS` does not redefine Back visuals.

## 6. Utility actions

Top-bar utilities use:

- `Icon Action V1 / UTILITY`;
- current V1 tone: **ON_LIGHT**;
- 44×44dp target;
- 20–22dp glyph;
- transparent container by default;
- no gold;
- no scale / bounce / spin;
- Loading = N/A.

### Direct action count

- **0 actions** → use `BACK_TITLE`, not this variant;
- **1 action** → show 1 direct Utility;
- **2 actions** → show both direct Utilities;
- **3+ actions** → show 1 highest-priority direct Utility + `More` as the second slot;
- `More` is always rightmost when present.

Do not add a third direct icon and squeeze the title.

### Ordering

- action order is page-semantic and must be deterministic;
- rightmost direct action is the final action in that page's frozen order;
- do not reorder based on usage telemetry or transient state.

## 7. FishRecordDetail frozen mapping

```text
←  鱼获详情                         [鱼鉴] [分享]
```

Frozen order:

1. Fish Guide / 鱼鉴 — `Icon Action V1 / UTILITY / ON_LIGHT`;
2. Share / 分享 — `Icon Action V1 / UTILITY / ON_LIGHT`.

Explicit exclusions:

- Hero `编辑` remains `Text Action V1 / NORMAL / ON_MEDIA`;
- B-side `card_flip` remains a local `Icon Action V1 / UTILITY / ON_MEDIA` on the B-side surface;
- B-side flip must **not** be promoted into Top Navigation;
- Add Media remains a content/context action, not a top-bar Utility.

## 8. Small screen

For **320–359dp**:

- retain 8dp outer padding;
- retain all 44dp touch targets;
- retain 8dp Back-title and Utility gaps;
- retain 20sp title;
- title truncates before any touch target shrinks;
- do not reduce glyph size to gain width;
- do not center the title.

At the supported V1 minimum width, FishRecordDetail's four-character title fits with two Utility actions.

## 9. Long title

- preserve Back and Utility hit targets;
- title takes the remaining width;
- one line + Ellipsis;
- actions do not overlap title;
- title does not move to a second row.

## 10. Accessibility font scale

- do not manually downscale title;
- top bar may grow above 56dp vertically;
- Back and Utility targets remain vertically centered;
- horizontal touch targets stay 44dp;
- title may ellipsize sooner at large font scale;
- no glyph clipping.

## 11. Surface / tone rule

Current V1 top-navigation use is on light BG_CONTENT and therefore uses **ON_LIGHT** icons.

If a future page puts the top bar directly over complex photo/media:

- it must explicitly declare a media-backed top-nav revision;
- Icon Action may then use ON_MEDIA according to its frozen contract;
- do not silently add support discs to the current FishRecordDetail top bar.

## 12. State ownership

`BACK_TITLE_ACTIONS` itself has no separate Loading state.

- icon Pressed / Disabled / Focus → Icon Action;
- page async states → page behavior contract;
- navigation/action availability → host page;
- no automatic haptic or sound at Top Navigation level.

## 13. Prohibited

- 3 direct Utility icons;
- shrinking 44dp touch targets;
- shrinking title below 20sp for width;
- centered title with fake balancing;
- moving title to two lines;
- adding B-side Flip to the top bar;
- placing Hero Edit in the top bar;
- default glass/pill background around the top bar;
- gold utility icons;
- per-page icon redraws that bypass Icon Action V1.
