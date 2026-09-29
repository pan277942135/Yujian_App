# Fish Guide Unlit State Spec V1

Status: **FROZEN**  
Date: **2026-09-29**  
Scope: **Fish Guide Home / UNLIT species state**  
Parent authority: **01 · 鱼鉴首页**

## 0. Authority

This specification promotes the existing UNLIT visual into a directly inspectable Design Manager second-level workspace:

- Design Manager menu: **01A · 未点亮状态**
- Visual Authority: `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png`
- Visual SHA-256: `68a3b83884f9fe0fd9a0b3a7325e75f9b22d8b734860063a9e16fc60d725bc62`
- Parent page authority: `design/system/core_visual_v1/reference/fish_guide_v2.png`
- Parent behavior authority: `design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md`

The image remains a **supplemental Core UI reference** and does not change the Core UI 6/6 freeze count. In Fish Guide, however, it is a first-class **state Visual Authority**.

If this state image conflicts with the base page shell, the base **01 · 鱼鉴首页** layout remains authoritative and this spec controls the **UNLIT state delta**.

---

## 1. State meaning

UNLIT means:

- the species exists in the active Fish Guide catalog;
- the user has **0 successfully saved FishRecord** entries mapped to that species.

UNLIT is a **personal encounter state**.

It is not:

- permission gating;
- disabled content;
- a locked game card;
- missing catalog data;
- an AI confidence state;
- a loading state.

The state must remain browseable and understandable before the user has personally caught the species.

---

## 2. Entry / exit conditions

### Enter UNLIT

A species is UNLIT when:

`savedFishRecordCount(species) = 0`

Recognition attempts, unsaved results, photos, uploads, or model predictions do not light the species.

### Exit UNLIT

The state becomes LIT only after at least one FishRecord is successfully saved with its final species mapped to the catalog species.

`savedFishRecordCount(species) >= 1 → LIT`

Do not persist a separate `isLit` flag when the state can be derived from saved records.

---

## 3. Frozen visual delta

The UNLIT state follows the provided Frozen Visual Authority.

Required treatment:

- preserve the Morning Lake / BG_DATA world;
- preserve the same FishGuideCard geometry and carousel structure as LIT;
- keep species identity readable;
- keep the fish subject recognizable;
- lower emphasis using restrained desaturation, soft mist, and lighter glass treatment;
- lower metadata emphasis relative to LIT;
- keep adjacent-card browsing affordance;
- keep the state visually quiet and natural.

The treatment must feel like **“尚未在我的真实鱼获中遇见”**, not **“功能未解锁”**.

---

## 4. Interaction contract

UNLIT species:

- remain visible in the Species Carousel;
- remain horizontally browseable;
- remain tappable;
- can enter Species Detail normally;
- participate in return-position restore exactly like LIT species;
- do not require a catch before knowledge can be viewed.

The card is still a single-face FishGuideCard:

- Auto Flip: **NO**
- Species autoplay: **NO**
- timed unlock animation: **NO**
- forced CTA overlay: **NO**

The only homepage automatic discovery motion remains the parent page's one-time low-amplitude Carousel Discover Hint; Reduce Motion disables it.

---

## 5. Data semantics

UNLIT is derived from saved real records only.

Source of truth:

> Count successfully saved FishRecord entries whose final species maps to the current catalog species.

For UNLIT:

- Species Catch Count = **0**
- the species still contributes to catalog total `T`;
- the species does not contribute to lit count `N`.

Do not use recognition count, photo count, upload count, or prediction count as a substitute.

---

## 6. Copy / labeling

The card should not introduce game-style lock copy.

Allowed semantics:

- species name;
- natural field-guide description;
- restrained “未点亮” / “尚未记录” semantics when needed;
- zero saved-record metadata when the product surface requires it.

Avoid:

- “解锁”
- “稀有”
- “等级”
- “传说”
- “收集完成度奖励”
- mystery `???`
- permission-style “不可查看”

---

## 7. Forbidden visual regressions

Do not introduce:

- black-and-white disabled rendering;
- large lock icon;
- dark locked-card overlay;
- opacity so low that species identity is difficult to read;
- hidden species name;
- replacement mystery silhouette;
- neon / legendary glow;
- separate UNLIT card family;
- altered carousel geometry;
- separate page background;
- new primary CTA covering the card.

UNLIT and LIT are variants of the **same FishGuideCard component**.

---

## 8. Relationship to Species Detail

Tapping an UNLIT species opens Species Detail.

If no saved record exists for that species:

- Species Detail uses its existing zero-catch contract;
- knowledge access is not blocked;
- the bottom “我的{鱼种}” region follows **02A · 无我的鱼获记录**.

Authority:

`design/pages/fish_guide/species_detail/Species_Detail_Zero_Catch_State_V1.md`

The homepage UNLIT visual must not be reused as the Species Detail page shell.

---

## 9. Accessibility

- UNLIT must remain legible and tappable.
- State must not rely on desaturation alone; semantic structure and metadata must remain understandable.
- Touch targets are unchanged from LIT.
- Reduce Motion does not alter UNLIT semantics.
- No automatic sound or haptic is required.

---

## 10. Design Manager contract

The Design Manager must expose this state as a direct second-level menu under **鱼鉴**:

`鱼鉴 → 01A · 未点亮状态`

The workspace must:

- directly preview `fish_guide_unlit_state.png`;
- label the state **FROZEN**;
- link to this specification;
- identify `01 · 鱼鉴首页` as parent authority;
- make clear that this is a state variant, not a new page family.

---

## 11. Acceptance gate

- [x] UNLIT has a dedicated second-level Design Manager entry.
- [x] Visual Authority is the existing frozen PNG, not a regenerated image.
- [x] UNLIT is derived from 0 successfully saved FishRecord entries.
- [x] Species remains visible, readable, browseable, and tappable.
- [x] Species Detail remains accessible.
- [x] LIT / UNLIT share one FishGuideCard system.
- [x] No large lock / disabled / rarity / level / game semantics.
- [x] Parent Fish Guide Home geometry and carousel rules remain unchanged.
- [x] Reduce Motion does not change state semantics.
- [ ] Runtime parity evidence is attached under **09 · 验收证据**.

The final unchecked item is implementation evidence only and does not reopen the frozen UNLIT design contract.
