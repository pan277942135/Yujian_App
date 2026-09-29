# Species Detail Page Contract V1

Status: **FROZEN — PAGE LEVEL**  
Date: **2026-09-29**  
Scope: **Fish Guide · 02 · 鱼种详情 / Species Detail**  
Exclusion: **the internal content and internal visual design of the five black-gold knowledge cards remain PARTIAL and are not frozen by this document**

---

## 0. Authority

Page-level authority order:

1. **Frozen Visual Authority**  
   `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png`
2. **Page Product / Interaction Authority**  
   `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`
3. **Visual Authority Registration**  
   `design/pages/fish_guide/species_detail/Species_Detail_Visual_Authority_V1.md`
4. Shared Design System contracts.

If the five knowledge cards are refined later, those changes must remain inside the frozen page-level container unless this full-page authority is explicitly re-frozen.

---

## 1. Page role

Species Detail is the focused reading surface for one Fish Guide species.

It has three jobs only:

1. identify the current species;
2. let the user actively browse the fixed five knowledge-card positions;
3. connect the species knowledge back to the user's own real catches.

It is **not**:

- a second Fish Guide Home;
- a species search page;
- a ranking page;
- a feed;
- a game collection detail;
- a full My Catches archive.

---

## 2. Frozen information architecture

Top to bottom:

1. Back navigation
2. Species Header
   - species name
   - one lightweight descriptor line
   - restrained gold accent
3. Knowledge Card Carousel
   - previous adjacent preview when available
   - centered active card
   - next adjacent preview when available
4. Page Indicator
   - `NN / 05`
5. My Species section
   - `我的{speciesName}`
   - `N 次记录`
   - chevron
   - up to two recent real-catch previews

No Search, Filter, Ranking, “最新”, secondary tabs, or page-level primary CTA is added in V1.

---

## 3. Species Header

### 3.1 Required content

Canonical structure:

```
{speciesName}
{descriptorA} · {descriptorB}
— restrained gold accent —
```

Example in the visual authority:

```
白条
灵动迅捷 · 上层小型鱼
```

### 3.2 Rules

- species name is the strongest text outside the knowledge card;
- descriptor is a single lightweight line;
- descriptor describes the species, not the user's catch;
- scientific name is not required in Species Detail V1;
- do not add catch statistics, taxonomy chips, rarity, badges, bookmark, share, or Search into the header;
- long descriptors may ellipsize on small screens; do not create a multi-row metadata block.

### 3.3 Scroll behavior

V1 does not introduce a collapsing or sticky Species Header.

On vertically constrained screens, the page may scroll as one continuous surface. The header preserves its relative hierarchy and spacing rather than transforming into a second compact toolbar.

---

## 4. Top navigation

- one Back action at the upper-left safe area;
- no Search action;
- no Share action;
- no overflow menu in V1;
- Android system Back and the visible Back action have the same navigation result.

Minimum interactive target follows the shared Icon Action / Navigation contract.

---

## 5. Knowledge Card Carousel

### 5.1 Fixed page-level contract

Species Detail contains exactly **5 knowledge-card positions** in V1.

This document freezes the five-slot carousel container and interaction only. It does **not** freeze the internal visual/content treatment of each black-gold card.

### 5.2 Active-card layout

- exactly one active card is visually dominant;
- active card is horizontally centered;
- previous and next cards partially enter the viewport when those positions exist;
- adjacent previews are subordinate and may be edge-faded/occluded as shown by the visual authority;
- the active card must be shown complete: **no crop, no stretch**;
- approved card artwork uses fit behavior inside the stable page-level carousel container.

Target visual relationship from the frozen page authority:

- active-card width: approximately **82–86% of viewport width**;
- adjacent-card visible cue: approximately **5–8% of viewport width per available side**;
- exact runtime pixels may adapt to device width, but the center-card dominance and visible adjacent affordance must remain.

### 5.3 User interaction

- horizontal swipe changes card position;
- carousel snaps to one stable centered card;
- a centered card may also be moved with accessibility / directional actions where supported;
- card browsing is user-driven.

### 5.4 Explicitly forbidden

- **Auto Flip: NO**
- **Auto Play / automatic card switching: NO**
- **automatic timed rotation: NO**
- **infinite / circular loop: NO**
- **wrap from 05 → 01 or 01 → 05: NO**
- vertical swipe must not change knowledge-card position.

At 01, there is no previous card.  
At 05, there is no next card.

Adjacent-card affordance disappears naturally at the corresponding boundary.

### 5.5 Initial and restored position

- first cold entry for a species: **01 / 05**;
- direct entry with no retained detail state: **01 / 05**;
- while Species Detail remains in the navigation back stack, returning to it restores the previously centered card position;
- process recreation may restore the last centered index when state restoration is available; otherwise fallback is 01 / 05;
- re-entering the same species from Fish Guide after its previous detail screen has been fully dismissed starts at 01 / 05.

This avoids silently persisting a historical card position across unrelated sessions.

---

## 6. Page Indicator

Canonical format:

`NN / 05`

Examples:

- `01 / 05`
- `04 / 05`
- `05 / 05`

Rules:

- total is fixed at **05** in V1;
- current value updates only when the new card becomes the settled / centered card;
- use two-digit current numbering as shown by the authority;
- indicator is lower priority than the card;
- **do not add a second dot pager**;
- indicator is informational, not a separate five-button navigation control.

---

## 7. My Species / “我的白条”

Canonical title:

`我的{speciesName}`

Example:

`我的白条`

This section links species knowledge to the user's own saved catches.

### 7.1 Catch count

Canonical copy:

`N 次记录`

Definition:

> Count of successfully saved FishRecord entries whose final species maps to the current Fish Guide species.

It does not count:

- recognition attempts;
- predicted species before save;
- photos;
- uploads;
- deleted FishRecords.

### 7.2 Preview media

Display up to **2 most recent available FishRecord photos** for the current species.

Ordering:

1. catch / capture time descending when available;
2. fallback to FishRecord creation time descending.

Media semantics:

- use the FishRecord's real original field/catch photo, or a runtime thumbnail derived from that original photo;
- do not use B-side generated media;
- do not use Fish Guide species artwork;
- do not use black-gold knowledge-card imagery;
- do not use AI-generated replacement imagery;
- never fabricate a second preview when only one qualifying record exists.

Detailed zero/one-record visual treatment belongs to **03 · 鱼种状态**, but this page contract forbids fake catch imagery.

### 7.3 Navigation behavior

Header row:

`我的{speciesName}    N 次记录  >`

Tapping the section header / count / chevron opens:

`My Catches → species filter = current species`

Each visible real-catch preview is independently tappable:

`preview → FishRecordDetail(selected FishRecord)`

Returning from either destination restores the Species Detail state:

- same species;
- same centered knowledge-card index;
- same vertical scroll position when feasible.

---

## 8. Cross-page navigation

### 8.1 Fish Guide → Species Detail

When opened from Fish Guide Home:

- current species identity is passed by stable species ID;
- Species Detail opens at 01 / 05 on a new detail entry;
- Fish Guide Home retains its active Species Carousel position in the previous back-stack entry.

### 8.2 Species Detail → Back

Back returns to the originating Fish Guide Home entry and restores:

- the same active species;
- the same Fish Guide Home carousel position.

Do not reset Fish Guide Home to its first species.

### 8.3 Species Detail → My Catches

Open My Catches with the current species filter already applied.

Back returns to the same Species Detail state.

### 8.4 Species Detail → FishRecordDetail

Open the selected record from a preview.

Back returns to the same Species Detail state.

Detailed deep-link policy remains owned by **04 · 鱼种导航**; deep links must not alter this page's visual hierarchy.

---

## 9. Vertical layout / scrolling

The frozen 941 × 1672 authority is the reference composition for a tall phone viewport.

Rules:

- preserve the order Header → Carousel → Indicator → My Species;
- do not make the black-gold card independently vertically scroll inside the page-level vertical scroll;
- the page may vertically scroll on shorter displays;
- avoid nested vertical scrolling;
- horizontal carousel gestures and page vertical scrolling must coexist without accidental axis switching;
- My Species must remain reachable without overlaying or covering the knowledge card.

---

## 10. Responsive guardrails

Detailed global responsive standards are owned by **08 · 响应式**, but Species Detail freezes these invariants:

- active knowledge card remains the primary page object;
- left/right card affordance remains perceptible on standard phone widths;
- card artwork is never cropped or stretched to force parity;
- reduce outer horizontal margins before removing adjacent-card affordance;
- Species Header remains readable without becoming a dense metadata stack;
- `NN / 05` remains visible below the carousel;
- My Species remains a distinct lower section;
- system status/navigation insets must never obscure Back, species title, carousel, or My Species.

For very narrow screens, adjacent-card exposure may reduce below the target range, but must remain non-zero when an adjacent card exists and space permits.

---

## 11. Accessibility

- Back has an accessible label and shared Navigation Icon Action target size;
- carousel exposes current position as `第 N 张，共 5 张` to accessibility services;
- knowledge-card browsing is not dependent on decorative gold color;
- real-catch previews have meaningful content descriptions based on record context;
- My Species navigation is reachable independently of its chevron icon;
- text scaling must not overlap the active card or make Back unreachable.

### Reduce Motion

Reduce Motion does not change page semantics.

When enabled:

- no automatic motion exists to disable because autoplay/auto-flip are already forbidden;
- carousel settles with reduced/minimal animation while retaining clear position change;
- no decorative parallax is required by this page contract.

---

## 12. Loading / missing-content ownership

This document freezes the normal Species Detail page and its page-level behavior.

Detailed visual states for:

- loading;
- species content missing;
- one or more knowledge-card assets missing;
- zero FishRecord;
- one FishRecord;
- offline content;
- data error;

belong to **03 · 鱼种状态**.

Until those states are frozen:

- do not invent extra tabs;
- do not add a page-level primary CTA;
- do not substitute fake fish/catch images;
- preserve the frozen page hierarchy as far as available data permits.

---

## 13. Five-card system boundary

The following are intentionally **outside this Page Contract**:

- black-gold card internal visual style refinements;
- exact card copy;
- exact card title names;
- star/rating semantics;
- diagrams;
- gear recommendations;
- ecology content;
- fishing technique content;
- internal card image generation rules.

Only these card-system properties are frozen here:

- there are **5 page positions**;
- cards are browsed horizontally;
- one card is centered at a time;
- cards are not cropped/stretched;
- no auto-flip/autoplay/loop;
- page indicator is `NN / 05`.

The separate five-card review may refine the cards without reopening the page layout.

---

## 14. Runtime state contract

Minimum page-level state to retain while the screen remains in the active navigation stack:

- `speciesId`
- `knowledgeCardIndex` in range 0…4
- page vertical scroll position

My Catches and FishRecordDetail round trips must not lose those values.

Do not persist card index as a global per-species user preference in V1.

---

## 15. Acceptance gate

Species Detail page-level design is accepted only when all are true:

- [x] Frozen full-page Visual Authority is the repository PNG.
- [x] Back + Species Header preserve the frozen hierarchy.
- [x] Search / Share / Ranking / “最新” / detail tabs are absent in V1.
- [x] Exactly five page-level card positions exist.
- [x] One active card is centered and adjacent-card affordance is preserved.
- [x] Knowledge-card artwork uses Fit; no crop or stretch.
- [x] Auto Flip is forbidden.
- [x] Autoplay / automatic card switching is forbidden.
- [x] Carousel is finite and does not wrap.
- [x] First cold entry opens at 01 / 05.
- [x] `NN / 05` is the only page indicator.
- [x] My Species shows saved FishRecord count semantics.
- [x] My Species previews use up to two recent real FishRecord photos only.
- [x] Preview taps can open the selected FishRecord; header/count/chevron opens species-filtered My Catches.
- [x] Back to Fish Guide restores the originating species carousel position.
- [x] Round trips through My Catches / FishRecordDetail restore Species Detail card state.
- [x] Five black-gold card internal content/visual design remains explicitly outside this freeze.
- [ ] Runtime visual/interaction evidence is attached under **09 · 验收证据**.

The final unchecked Runtime Evidence item does not reopen the frozen page-level design contract.
