# Fish Guide Motion & Interaction Spec V1

Status: **FROZEN — DESIGN / INTERACTION CONTRACT**  
Date: **2026-09-29**  
Scope: **Fish Guide · 07 · 动效与交互**  
Applies to: **01 · 鱼鉴首页 / 01A · 未点亮状态 / 02 · 鱼种详情 / 03 · 鱼种状态**

---

## 0. Authority

This specification freezes the motion and interaction behavior of Fish Guide V1.

Authority order:

1. Parent page/state authorities:
   - `design/pages/fish_guide/Fish_Guide_Home_Spec_V1.md`
   - `design/pages/fish_guide/Fish_Guide_Unlit_State_Spec_V1.md`
   - `design/pages/fish_guide/species_detail/Species_Detail_Page_Contract_V1.md`
   - `design/pages/fish_guide/species_states/Fish_Species_States_Spec_V1.md`
2. Motion / interaction authority:
   - `design/pages/fish_guide/motion/Fish_Guide_Motion_Interaction_Spec_V1.md`
   - `design/pages/fish_guide/motion/motion_contract.json`
3. Shared motion principle:
   - `design/system/core_visual_v1/tokens/motion_tokens.json`

If motion would alter a frozen page hierarchy, card geometry, state meaning, or data truth, the parent authority wins.

Core principle:

> **Alive, not animated. User-driven by default. Motion explains position or real data change; it never performs for attention.**

---

# 1. Motion model

Fish Guide V1 contains only five motion families:

1. **Home Species Carousel**
2. **First-entry Carousel Discover Hint**
3. **Species Detail Knowledge Carousel**
4. **Navigation / Press Feedback**
5. **Committed Data State Transition**

Everything else is static unless a shared platform behavior requires motion.

Not allowed:

- page-wide synchronized looping;
- decorative continuous glow;
- automatic species rotation;
- knowledge-card autoplay;
- Auto Flip;
- timed card rotation;
- celebratory unlock screens;
- confetti;
- repeated shimmer loops;
- motion that changes factual state before data is committed.

---

# 2. Home Species Carousel

## 2.1 Direct manipulation

The Species Carousel is directly controlled by the user's horizontal gesture.

Rules:

- finger drag maps directly to horizontal carousel displacement;
- movement is continuous while dragging;
- release settles to one stable centered species;
- no circular loop;
- first and last species use platform edge resistance and never wrap;
- no 3D tilt, Y rotation, perspective, or parallax;
- background remains static during carousel movement.

## 2.2 Active / adjacent emphasis

Normal-motion presentation:

| Role | Scale | Alpha |
|---|---:|---:|
| centered active card | 1.00 | 1.00 |
| adjacent preview | 0.94 | 0.58 |

During drag, scale and alpha interpolate continuously by distance to center.

Rules:

- transform is applied to the whole FishGuideCard container;
- species artwork itself is not independently stretched or warped;
- when settled, exactly one card is the active semantic selection;
- active species / accessible announcement updates **after settle**, not continuously while the card crosses center.

## 2.3 Settle

Target behavior:

- typical settle duration: **220–320 ms**;
- no visible bounce;
- overshoot: **≤ 2 dp**;
- use platform pager fling/snap physics tuned to this range rather than a custom theatrical easing;
- a fast fling may move to the platform-selected snap target, but must always end on one valid species.

## 2.4 Tap behavior

This rule is frozen to prevent accidental detail entry:

- **tap centered active card → open Species Detail**
- **tap adjacent preview → center that species only**
- tapping an adjacent preview must **not** immediately open Species Detail.

After an adjacent card becomes centered, a second tap may enter detail.

## 2.5 Selection semantics

- `selectedSpeciesId` changes only when the pager is settled;
- archive metadata and accessibility copy follow the settled species;
- do not flicker name/count state during partial drag;
- return from Species Detail restores the prior active species and carousel position without replaying Discover Hint.

---

# 3. First-entry Carousel Discover Hint

Purpose: communicate that the Species Carousel is horizontally browseable without changing the active species.

## 3.1 Eligibility

Run only when all are true:

- Fish Guide Home is foreground and fully laid out;
- catalog contains **at least 2 species**;
- user has not previously completed or interrupted the hint;
- Reduce Motion is OFF;
- no touch interaction is active.

## 3.2 Choreography

After the page is stable:

- delay: **600 ms**
- nudge toward the logical next species: **14 dp**
- outward phase: **180 ms**
- return-to-center phase: **260 ms**
- hold: **0 ms**
- total motion after delay: **440 ms**
- active species: **unchanged**
- settled page index: **unchanged**
- selection callback: **must not fire**
- haptic: **none**
- sound: **none**

The motion is a small elastic-looking browse cue, not a partial automatic page change.

At the final catalog item, nudge toward the previous species instead.

## 3.3 Frequency

- show **once** for the local user/profile experience, not once per session;
- persist a lightweight `fishGuideCarouselHintSeen` flag locally;
- if the user touches/swipes the carousel before the hint starts, treat discovery as complete and do not show it later;
- if the user touches during the hint, cancel immediately, restore direct control, and mark the hint complete.

The hint must never replay merely because the user returns from Species Detail.

---

# 4. Species Detail Knowledge Carousel

## 4.1 Direct manipulation

The fixed five-card knowledge carousel is entirely user-driven.

Rules:

- horizontal drag = knowledge-card browsing;
- one centered active card at settle;
- finite positions 01…05;
- no wrap from 05→01 or 01→05;
- no Auto Flip;
- no autoplay;
- no timed switching;
- no decorative card parallax;
- no Y rotation / 3D tilt;
- card artwork remains **Fit / no crop / no stretch**.

## 4.2 Settle

Target behavior:

- typical settle duration: **220–300 ms**;
- no visible bounce;
- edge overshoot: platform resistance only;
- `NN / 05` updates only after the new card is settled;
- accessibility announces `第 N 张，共 5 张` only after settle.

The knowledge cards do not need scale animation. Their hierarchy is already carried by center placement, edge exposure, and the frozen page composition.

## 4.3 Adjacent-card tap

- tapping a visible adjacent knowledge card centers that position;
- it does not trigger a second destination or hidden card action;
- knowledge-card internals remain read-only at the page-container level unless a future card-specific contract explicitly adds an action.

---

# 5. Horizontal / vertical gesture arbitration

Species Detail can vertically scroll while the knowledge carousel scrolls horizontally.

Use platform touch slop. After touch slop is exceeded:

- if `abs(dx) >= 1.2 × abs(dy)` → lock the gesture to the horizontal carousel;
- otherwise → allow the vertical page scroll;
- once an axis is locked for that gesture, do not switch axes mid-gesture.

Fish Guide Home has no nested vertical card scroll; its Species Carousel follows the same horizontal intent principle.

Do not introduce a custom gesture area smaller than the visible card.

---

# 6. Navigation transitions

## 6.1 Fish Guide Home → Species Detail

Trigger: tap the centered active FishGuideCard.

Press feedback:

- scale: **1.00 → 0.985**
- press-in: **70 ms**
- release / navigation handoff: **100 ms**
- no ripple that obscures fish artwork;
- no explicit haptic.

Page transition:

- duration: **220 ms**
- incoming Species Detail: `alpha 0.94 → 1.00`
- incoming translation: **+12 dp X → 0 dp** in logical forward direction
- no page zoom;
- no shared-element morph requirement;
- no lake-background parallax.

## 6.2 Species Detail → Back

- duration: **200 ms**
- reverse the low-amplitude X translation / fade;
- restore Home with the same active species and carousel position;
- do not replay Discover Hint.

Where the platform provides an interactive/predictive Back transition, platform progress may drive this motion while preserving the same destination and restore semantics.

## 6.3 My Species / FishRecord preview navigation

Use the app's standard navigation transition.

Tap feedback:

- opacity: **1.00 → 0.92**
- press-in: **70 ms**
- release: **100 ms**
- no bounce;
- no explicit haptic.

---

# 7. Committed data state transitions

State animation is allowed only after the underlying data change is committed.

## 7.1 First qualifying saved catch: UNLIT → LIT

Trigger:

`savedCount: 0 → 1`

If the affected FishGuideCard is currently the visible active card:

1. UNLIT mist/reduced-emphasis treatment lifts over **420 ms**.
2. Species color/contrast returns continuously during the same 420 ms.
3. Saved-count metadata fades in over **180 ms**, starting after **180 ms**.
4. Home discovery progress bar updates over **300 ms** ease-out.
5. No card scale burst.
6. No gold explosion / rim sweep.
7. No confetti.
8. No auto-navigation.
9. No automatic haptic or sound.

This should read as **“the archive has quietly become personal”**, not **“achievement unlocked.”**

If the affected species is offscreen or the Fish Guide surface is not visible, show the final LIT state next time it appears. Do **not** replay the transition later.

## 7.2 LIT → UNLIT

Possible causes: delete the final qualifying FishRecord or persisted species correction.

If the affected card is currently active and visible:

- crossfade to the UNLIT treatment: **260 ms**;
- count resolves to 0 without bounce;
- discovery progress updates over **240 ms**;
- no “locking” animation;
- no reverse celebration.

If offscreen, update silently to the final state.

## 7.3 Additional saved catch while already LIT

`N → N+1`, where N ≥ 1:

- count text crossfade: **160 ms**;
- affected recent-preview content crossfade: **180 ms** when visible;
- no scale pulse;
- no progress-bar animation because lit-species count `N / T` is unchanged.

## 7.4 Species correction affecting two species

After persistence:

- recompute both species;
- animate only the currently visible active surface when its encounter state changes;
- offscreen species update silently;
- never auto-scroll the Home carousel to the other affected species.

---

# 8. Loading / error transitions

Fish Guide uses static structural loading/fallback surfaces.

Rules:

- skeleton geometry is static; no required looping shimmer;
- loaded content may crossfade in over **180 ms**;
- region retry result may crossfade over **160 ms**;
- an error must not shake the page or knowledge card;
- repeated background loading must not restart attention-seeking motion.

---

# 9. Haptic and sound contract

Fish Guide V1 adds **no custom haptic or sound choreography**.

Specifically:

- no haptic on every carousel snap;
- no haptic on Discover Hint;
- no automatic haptic on UNLIT→LIT;
- no unlock sound;
- no page-entry sound;
- no automatic ambient audio.

Normal platform accessibility/touch behavior remains available.

---

# 10. Reduce Motion

Reduce Motion changes choreography, not information or navigation.

When enabled:

### Fish Guide Home
- Discover Hint: **disabled**
- direct user drag: **retained**
- programmatic settle: **immediate or ≤120 ms**
- scale/alpha interpolation during programmatic motion: **removed**
- settled active/adjacent hierarchy: retained

### Species Detail
- direct user drag: **retained**
- settle: **immediate or ≤120 ms**
- no decorative transition beyond required displacement
- `NN / 05` semantics unchanged

### Navigation
- remove the 12 dp translation;
- use crossfade only, **≤120 ms**, or platform reduced-motion behavior.

### State changes
- UNLIT↔LIT: crossfade only, **≤120 ms**;
- no delayed metadata sequence;
- data semantics remain identical.

User-driven scrolling itself is not removed because it is required navigation.

---

# 11. Interruptibility / concurrency

User input always has priority.

Rules:

- a user drag cancels Discover Hint immediately;
- a navigation tap cancels pending programmatic carousel movement;
- state visual transitions do not block scrolling or Back;
- if data changes during an active drag, commit data immediately but defer the visual state delta until the carousel settles;
- never queue multiple state animations for the same species;
- entering background cancels non-essential motion;
- returning foreground shows the correct final state rather than replaying missed motion.

---

# 12. Accessibility announcements

Announcements follow **settled factual state**, not animation frames.

Home:

- announce active species after settle;
- do not announce every partially crossed card;
- when UNLIT/LIT changes while visible, announce the factual state once if accessibility services require it.

Species Detail:

- announce `第 N 张，共 5 张` after settle;
- do not repeatedly announce while dragging.

Discover Hint:

- purely visual affordance;
- no separate accessibility announcement;
- users not seeing motion still have adjacent-card affordance and standard swipe actions.

---

# 13. Performance guardrails

Motion must remain inexpensive:

- animate only translation / scale / alpha where possible;
- do not animate runtime blur radius;
- do not animate full-screen color grading;
- do not animate the Morning Lake background for Fish Guide;
- no synchronized looping effects;
- target smooth frame pacing on the supported Android baseline;
- card images remain stable while moving; no per-frame image regeneration.

---

# 14. Implementation invariants

Development may use platform/Compose primitives, but the observable result must preserve:

- user-driven paging;
- centered settled selection;
- no autoplay / auto-flip / loop;
- adjacent tap centers before detail navigation;
- one-time Discover Hint only;
- state animations only after committed data changes;
- settled-only semantic updates;
- Reduce Motion behavior;
- no custom haptic/sound.

The spec intentionally does not require a custom physics engine.

---

# 15. Acceptance Gate

## Home Carousel
- [x] Direct horizontal manipulation.
- [x] Centered snap with no visible bounce.
- [x] Active = 1.00 / 1.00; adjacent = 0.94 / 0.58 in normal motion.
- [x] Center-card tap opens detail.
- [x] Adjacent-card tap centers only.
- [x] Selection semantics update after settle.
- [x] No autoplay / no circular loop.

## Discover Hint
- [x] One-time only.
- [x] 600 ms delay.
- [x] 14 dp logical-next nudge.
- [x] 180 ms out + 260 ms return.
- [x] Does not change selected species.
- [x] Cancels on user input.
- [x] Disabled by Reduce Motion.

## Species Detail Carousel
- [x] Five finite positions.
- [x] User-driven horizontal swipe.
- [x] 220–300 ms typical settle.
- [x] No Auto Flip / autoplay / timed rotation / loop.
- [x] NN / 05 updates after settle.
- [x] Horizontal/vertical gesture arbitration is deterministic.

## Navigation / State
- [x] Low-amplitude press feedback.
- [x] 220 ms forward / 200 ms back page transition.
- [x] UNLIT→LIT is a restrained 420 ms mist-lift only when currently visible.
- [x] Offscreen state changes do not replay later.
- [x] No celebration / lock animation / custom haptic / sound.
- [x] Reduce Motion contract is explicit.
- [ ] Runtime evidence is attached under **09 · 验收证据**.

The unchecked Runtime Evidence item does not reopen this frozen motion/interaction contract.
