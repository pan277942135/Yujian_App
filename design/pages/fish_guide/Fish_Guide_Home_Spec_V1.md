# Fish Guide Home Spec V1

Status: **FROZEN**  
Date: **2026-09-29**  
Scope: **Fish Guide Home only / 鱼鉴首页**  
Product role: **Natural Collection / Personal Field Guide**

## 0. Authority

This specification defines the product, layout, state, data, and interaction contract for **01 · 鱼鉴首页**.

Authority order:

1. **Frozen Visual Authority**  
   `design/system/core_visual_v1/reference/fish_guide_v2.png`
2. **Home Product / Behavior Authority**  
   `design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md`
3. **UNLIT State Visual Authority / 01A · 未点亮状态**  
   `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`
4. **UNLIT State Product / Behavior Authority**  
   `design/pages/fish_guide/Fish_Guide_Unlit_State_Spec_V1.md`
5. **Shared visual system contracts**  
   Background / glass / color / typography / spacing / radius registries.

The UNLIT image is promoted to a directly inspectable **01A · 未点亮状态** Design Manager workspace, but it remains a state delta of the same FishGuideCard system and does not replace the main frozen page shell.  
Runtime evidence never replaces design authority.

---

## 1. Page role

Fish Guide Home is a **personal natural collection browser** built from the user's real saved catches.

Primary jobs:

- browse species already present in the Fish Guide catalog;
- understand personal discovery progress;
- move quickly between species;
- enter a species detail page;
- see lightweight personal catch-count metadata.

The page must feel like **observation / discovery / archive**, not achievement, rarity, leveling, or game collection.

---

## 2. V1 information architecture

Top-to-bottom hierarchy:

1. Page title: **鱼鉴**
2. Discovery Progress: **已点亮 N / T 种**
3. Lightweight progress indicator
4. Species Carousel
   - previous adjacent preview
   - centered main species card
   - next adjacent preview
5. Species archive metadata
   - species name
   - saved catch count where applicable

The **center species card is the visual focus**.

### V1 does not include

- Search
- Filter
- Grid species browser
- Masonry list
- traditional species list
- fish-shaped pager
- ranking
- rarity
- star level
- achievement HUD
- large lock treatment
- page-level primary CTA

Search is a **future scale feature** and must not reappear from older Fish Guide drafts.

---

## 3. Background contract

Fish Guide Home uses the shared background system:

- Master: `Morning_Lake_Master_V1`
- Variant: `BG_DATA`
- Sun: **none**
- Treatment authority:  
  `design/system/backgrounds/morning_lake_v1/treatment_contract.json`

Frozen BG_DATA treatment:

- saturation: **0.79**
- contrast: **0.78**
- brightness: **1.06**
- mist alpha: **0.30**

Rule:

> Fish Guide stays in the same Morning Lake world as the rest of YuJian, but the background is deliberately lower-presence than Hero pages so the species card remains dominant.

Do not introduce a page-specific lake background.

---

## 4. Discovery Progress

Canonical copy pattern:

`已点亮 N / T 种`

Semantics:

- `N` = count of distinct catalog species for which the user has at least one successfully saved FishRecord.
- `T` = total active species count in the Fish Guide catalog.
- Recognition attempts, photos, model predictions, or unsaved results do **not** increment `N`.

Visual hierarchy:

- lower than page title;
- substantially lower than the main species card;
- progress bar is restrained and informational;
- do not turn the number into a large gold KPI.

“点亮” describes a personal encounter / collection state, **not a permission or game unlock**.

---

## 5. Species Carousel

### 5.1 Structure

The homepage uses a horizontal, centered **Species Carousel**:

- exactly one centered main species card;
- left adjacent card is partially visible when available;
- right adjacent card is partially visible when available;
- adjacent previews exist to communicate horizontal browsing.

### 5.2 Required behavior

- horizontal swipe changes the active species;
- movement snaps to a stable centered species card;
- tapping the centered species card enters that species' detail;
- returning from Species Detail restores the previously selected species and carousel position;
- unlit species remain browsable and tappable;
- **FishGuideCard is a single-face browsing card and does not support Auto Flip**;
- **Fish Guide Home must not automatically switch or autoplay Species**;
- on the user's first eligible entry, the carousel may play **one low-amplitude Carousel Discover Hint** to communicate horizontal browsing;
- after that one hint, species changes are entirely user-driven;
- **Reduce Motion disables the Carousel Discover Hint**.

### 5.3 Forbidden layout regressions

Do not replace the carousel with:

- a full-width single card with no adjacent affordance;
- Grid;
- vertical list;
- masonry;
- fish-shaped pagination;
- strong page dots competing with the species card.

Exact pixel geometry follows the Frozen Visual Authority unless superseded by an explicit layout spec.

---

## 6. FishGuideCard contract

Shared semantic source:

`design/system/core_visual_v1/components/Fish_Card.md`

Every FishGuideCard should communicate:

- species identity;
- realistic biological subject;
- natural / field-guide tone;
- restrained archive metadata;
- saved catch count when available.

Forbidden card semantics:

- rarity frame;
- legendary / epic treatment;
- star level;
- collectible-game border tiers;
- dramatic unlock glow;
- reward badge hierarchy.

The fish remains the primary card subject.

---

## 7. Lit / Unlit states

### 7.1 LIT / 已点亮

Condition:

- user has at least one saved FishRecord mapped to this catalog species.

Presentation:

- normal natural field-guide card;
- complete species name;
- complete fish subject;
- normal content contrast;
- Species Catch Count may be shown as secondary archive metadata;
- card is tappable.

### 7.2 UNLIT / 未点亮

Dedicated second-level Design Manager authority:

- Menu: **01A · 未点亮状态**
- Visual: `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`
- State spec: `design/pages/fish_guide/Fish_Guide_Unlit_State_Spec_V1.md`

The dedicated 01A workspace freezes the UNLIT state delta only. The base Fish Guide Home shell, carousel geometry, and hierarchy remain owned by **01 · 鱼鉴首页**.

Condition:

- species exists in the active catalog;
- user has no saved FishRecord for this species.

Presentation:

- still visible;
- still browsable;
- still tappable;
- species information remains understandable;
- use restrained light desaturation / soft mist / white-glass reduction;
- lower fish-subject and metadata emphasis than LIT.

Forbidden:

- black-and-white disabled card;
- large lock icon;
- “disabled” affordance;
- blocking detail access;
- hiding species identity;
- `???` mystery treatment;
- dark locked-card game semantics.

Key rule:

> Unlit is a personal encounter state, not an access-control state.

---

## 8. Species Catch Count

Canonical semantic name:

**Species Catch Count**

Definition:

> Number of successfully saved FishRecord entries whose final species maps to the current Fish Guide species.

It is **not**:

- AI recognition count;
- photo count;
- upload count;
- model prediction count.

Presentation:

- secondary archive metadata;
- lower weight than species name and fish subject;
- copy may use the pattern `12 次记录`;
- do not present it as score, streak, or achievement points.

---

## 9. Navigation contract

Primary flow:

`Fish Guide Home → Species Detail`

On return:

- restore active species;
- restore carousel position;
- do not reset to the first catalog item.

Cross-module linkage to My Catches belongs to **02 · 鱼种详情** and **04 · 鱼种导航**; Fish Guide Home does not need a second large CTA for it.

---

## 10. Visual hierarchy

Priority order:

1. Main species card / fish subject
2. Species identity
3. Adjacent-card browsing affordance
4. Discovery progress
5. Catch-count archive metadata
6. Background atmosphere

Gold is restrained and must not turn discovery progress into an achievement system.

Glass is used for legibility and layering only; it must not become a special-effect surface.

---

## 11. Responsive guardrails

Detailed responsive behavior is owned by **08 · 响应式**.

Fish Guide Home must preserve these invariants:

- centered active card remains primary;
- horizontal browse affordance remains perceptible;
- species name remains readable;
- progress information remains secondary;
- content must not overlap system insets.

On smaller screens, reduce peripheral spacing before removing the adjacent-card affordance.

---

## 12. Accessibility / motion guardrails

Detailed motion is owned by **07 · 动效与交互**.

Home-level invariants:

- card state must not be communicated by color alone;
- Unlit must remain legible;
- swipe is not the only route into the active card: the card is tappable;
- **no Auto Flip**;
- **no automatic Species rotation / autoplay**;
- the only allowed automatic discovery motion is a **single low-amplitude Carousel Discover Hint on first eligible entry**;
- the hint must not change the active species or leave the carousel on another item;
- after the hint, carousel movement is fully user-driven;
- **Reduce Motion disables the hint entirely**;
- Reduce Motion must not change information hierarchy or state semantics;
- no automatic haptic or sound is required by this homepage spec.

---

## 13. Asset contract

Primary page visual:

`design/system/core_visual_v1/reference/fish_guide_v2.png`

Unlit supplemental reference:

`design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`

Shared background:

`design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`

Rules:

- do not crop a frozen full-page reference to create runtime card assets;
- species media must come from approved species-specific runtime/design assets;
- do not substitute B-side fish assets, generated fish-memory cards, or My Catches thumbnails for Fish Guide species media unless separately approved.

---

## 14. Edge-state ownership

This Home V1 spec freezes the normal LIT / UNLIT browsing semantics.

Detailed contracts for:

- content missing;
- catalog failure;
- offline content;
- loading;
- runtime fallback;

belong to **03 · 鱼种状态** and **09 · 验收证据**.

Until those specs are frozen, runtime fallback must not invent new visual hierarchy.

---

## 15. Acceptance gate

Fish Guide Home passes design acceptance only when all are true:

- [x] Page role is Natural Collection / Personal Field Guide.
- [x] Frozen Visual Authority remains `fish_guide_v2.png`.
- [x] Uses no-sun `Morning_Lake_Master_V1 / BG_DATA`.
- [x] V1 Search is absent.
- [x] Main browsing model is centered Species Carousel.
- [x] Adjacent species preview remains visible.
- [x] Discovery progress uses `已点亮 N / T 种` and stays low-weight.
- [x] Species Catch Count is based on saved FishRecord entries.
- [x] LIT / UNLIT are encounter states, not permission states.
- [x] UNLIT is exposed as a direct **01A · 未点亮状态** Design Manager second-level workspace.
- [x] 01A directly previews the existing frozen `fish_guide_unlit_state.png`; no replacement visual is generated.
- [x] UNLIT remains visible, readable, browsable, and tappable.
- [x] No rarity / level / achievement / large-lock game semantics.
- [x] Return from Species Detail preserves carousel position.
- [x] FishGuideCard is single-face; Auto Flip is forbidden.
- [x] Fish Guide Home does not autoplay or automatically switch Species.
- [x] A single first-entry low-amplitude Carousel Discover Hint is allowed; Reduce Motion disables it.
- [ ] Runtime parity evidence is attached under **09 · 验收证据**.

The final unchecked item is an implementation/evidence gate and does not reopen the frozen homepage design contract.
