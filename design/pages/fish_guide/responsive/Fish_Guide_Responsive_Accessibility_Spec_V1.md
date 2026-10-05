# Fish Guide Responsive & Accessibility Spec V1

Status: **FROZEN — DESIGN RESPONSIVE CONTRACT**  
Date: **2026-09-30**  
Scope: **Fish Guide · 06 · 响应式与无障碍**  
Applies to: **01 · 鱼鉴首页 / 02 · 鱼种详情 / 03 · 页面状态 / 04 · 知识卡内容与资产**

## 0. Purpose

This contract closes the previous empty Responsive placeholder and defines cross-page adaptation rules without reopening frozen page composition.

Primary visual authorities:

- Fish Guide Home: `design/system/core_visual_v1/reference/fish_guide_v2.png`
- Species Detail: `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png`

The PNG dimensions are visual-composition references, not Android pixel-density contracts.

---

## 1. Supported design envelope

Fish Guide V1 is portrait-first.

Primary portrait envelope:

- 9:16
- 19.5:9
- 20:9
- 21:9

The design must adapt to system bars / cutouts without stretching frozen imagery.

Landscape is not a V1 visual-parity target. If runtime allows landscape, content must remain usable, but landscape does not create a second visual authority in this contract.

---

## 2. Shared adaptation priority

When space becomes constrained, preserve in this order:

1. safe-area / navigation reachability
2. species identity and factual text
3. active carousel card
4. current page / card position
5. primary navigation affordance
6. adjacent-card browse affordance
7. decorative atmosphere

Reduce decorative spacing before reducing factual legibility.

Never solve a small screen by:

- hiding the species name;
- shrinking text below the shared typography minimum;
- cropping the fish subject arbitrarily;
- removing the current card position;
- converting an active state into an icon-only mystery state.

---

## 3. Fish Guide Home

Invariant structure:

- Top Navigation / TITLE_ONLY
- discovery progress
- centered active FishGuideCard
- adjacent-card browse affordance where physically possible
- species identity / record metadata

Rules:

- active card stays visually dominant;
- preserve a visible adjacent preview on standard-width phones;
- on very compact widths, reduce outer gutters and inter-card spacing before reducing the active card;
- first/last carousel edges may naturally expose only the available neighbor;
- no responsive Grid fallback in V1;
- no vertical species list fallback in V1.

The frozen Home image owns standard composition proportions.

---

## 4. Species Detail

Invariant structure:

- Back + Species Header
- centered fixed five-card Knowledge Carousel
- `NN / 05`
- My Species region
- vertical page scroll when needed

Rules:

- knowledge card must remain fully readable;
- use **Fit / no stretch** for card artwork;
- preserve adjacent-card cue on standard widths where possible;
- short screens use one outer vertical scroll surface;
- do not create a nested vertical scroll inside the knowledge card;
- My Species preview tiles may wrap/reflow only if the page contract's semantic order is preserved.

---

## 5. Text scaling

### Normal design parity

At common font scales, preserve the frozen hierarchy and line limits.

### Larger text

When text scale increases:

- allow row / card / header height to grow;
- preserve full control touch targets;
- title may ellipsize only where the shared Top Navigation contract already permits it;
- body knowledge text should reflow instead of being clipped;
- factual values must not be replaced by icons to save space.

At extreme accessibility text sizes, usability and complete meaning take priority over pixel parity.

---

## 6. Touch targets

Interactive targets follow the shared Android design system.

Minimum intended touch target:

- **44 dp** for navigation and utility actions;
- visible card area remains fully tappable for its documented action.

Rules:

- adjacent-card tap target centers that card only;
- centered Home card tap enters Species Detail;
- zero-catch Text Action remains independently reachable;
- no critical action may depend on a tiny chevron glyph alone.

---

## 7. Screen reader semantics

### Home

Announce the settled active card with:

- species name;
- encounter state when useful;
- saved catch count;
- position in the species set when available.

Do not announce transient drag frames as new selected species.

### Species Detail

Announce:

- species heading;
- active knowledge-card title;
- `第 N 张，共 5 张`;
- My Species record count;
- unavailable content state when present.

Do not announce decorative gold labels or background art as separate content.

---

## 8. Color / contrast / state communication

LIT / UNLIT and content availability must not rely on color or opacity alone.

UNLIT keeps:

- readable species identity;
- semantic state text/metadata where needed;
- normal navigation access.

Missing media keeps textual identity and state meaning.

Gold accents are never the sole indicator of selection or success.

---

## 9. Reduce Motion linkage

Detailed timing remains owned by **05 · 动效与交互**.

Responsive/accessibility rules:

- direct user scrolling remains available;
- Discover Hint is disabled;
- required programmatic settle / navigation uses reduced-motion behavior;
- removing motion must not remove positional or state feedback.

---

## 10. Insets and device cutouts

- respect status/navigation gesture insets;
- Back must remain reachable;
- page title must not collide with camera cutouts;
- bottom My Species / Text Action content must not sit under the gesture area;
- no fixed screenshot-based top/bottom spacer may substitute for real insets.

---

## 11. Content overflow

Priority for overflow handling:

1. reflow body text;
2. grow local container height where allowed;
3. reduce non-semantic spacing;
4. ellipsize only fields explicitly allowed by their parent contract.

Do not:

- silently drop facts;
- reduce font size ad hoc per species;
- horizontally marquee scientific names;
- crop knowledge text to keep all cards exactly screenshot-height under accessibility text scaling.

---

## 12. Acceptance Gate

- [x] Portrait 9:16–21:9 envelope is defined.
- [x] Frozen PNG dimensions are not treated as device pixels.
- [x] Home remains Carousel; no responsive Grid/List fallback.
- [x] Species Detail keeps one outer vertical scroll surface.
- [x] Knowledge artwork is Fit / no stretch.
- [x] Large text prioritizes meaning over pixel parity.
- [x] Critical targets remain at least 44 dp.
- [x] Screen-reader selection updates only after carousel settle.
- [x] State meaning does not rely on color alone.
- [x] Reduce Motion cross-links to the motion authority.
- [x] Insets / cutouts are handled dynamically.

This contract replaces the former empty **08 · 响应式** Design Manager placeholder.
