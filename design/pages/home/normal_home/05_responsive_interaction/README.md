# NH05 · Responsive & Interaction / 响应式与交互 · Design Spec V1

Status: **SPEC FROZEN — COMBINED BOARD PENDING**  
Scope: **Normal Home phone adaptation / Pager / Reduce Motion**  
Output policy: **COMBINED_BOARD**

---

## 1. Purpose

NH05 freezes how the Normal Home composition adapts across supported tall-phone aspect ratios without stretching or redesigning NH01/NH02.

Required review ratios:

- 9:16
- 19.5:9
- 20:9
- 21:9

Core principle:

> **Scale from width, preserve the page's internal composition, and let extra height become environment breathing room rather than stretched UI.**

---

## 2. Reference frame

Canonical design frame:

**1080 × 1920**

Reference anchors:

- Header: x 88, y 104, w 904, h 104
- Statistics: y 304, h 116
- Recent header: y 494, h 70
- Hero: x 170, y 596, w 740, h 880
- CTA: y 1504
- Capture Button: y 1582, visual size ≈200

---

## 3. Width-first adaptation

Let:

`S = usableContentWidth / 1080`

All core horizontal dimensions and Hero dimensions scale by `S`.

Reference content-frame height:

`H_ref = 1920 × S`

For a taller usable viewport:

`E = max(0, usableHeight - H_ref)`

Apply one shared vertical offset to the complete core composition:

`offsetY = min(E × 0.36, 180 × S)`

Then:

`runtimeY = referenceY × S + offsetY`

This means:

- internal vertical spacing remains stable;
- Hero aspect ratio remains stable;
- CTA / Capture relationship remains stable;
- remaining excess height stays as Morning Lake breathing room.

Do not independently stretch individual regions.

---

## 4. Aspect-ratio expectations

### 9:16

- closest to canonical 1080 × 1920 composition;
- reference anchors apply directly after safe-inset scaling.

### 19.5:9 / 20:9 / 21:9

- no taller Hero;
- no enlarged statistics;
- no duplicated decorative content;
- no bottom filler card;
- page composition shifts down modestly as one unit;
- remaining lower-area space is environmental lake/air, not UI.

Safe insets are resolved before applying the content-frame math.

---

## 5. Hero size invariants

HOME Hero:

- preserves the 740 × 880 reference proportion;
- does not become taller on taller phones;
- does not expand horizontally just because only one record exists;
- NH02 single-record Hero and NH01 selected Hero share the same core geometry.

---

## 6. Multiple-record Pager

### Selected page idle

Inherit Normal Home Motion V1:

- 6000 ms cycle;
- scale 1.000 → 1.008 → 1.000;
- Y 0 → -2 dp → 0.

### Adjacent-page transform

For normalized page distance `d ∈ [0,1]`:

- scale = `1.0 - d × 0.055`
- translationY = `4 dp × d`
- alpha = `1.0 - d × 0.12`

Adjacent pages may partially peek.

### Gesture

- horizontal movement is directly user-driven;
- no auto-advance;
- no circular looping;
- no snap haptic authored by this page;
- vertical Home navigation must not be hijacked by a minor horizontal touch unless horizontal drag intent is established.

---

## 7. Single-record state

When `pageCount = 1`:

- one centered Hero;
- no fake neighbors;
- no pager dots;
- no swipe hint;
- no horizontal affordance implying additional catches;
- no empty page reachable through drag.

Shared pager infrastructure may remain internally only if runtime behavior is indistinguishable from a stable single card.

---

## 8. Reduce Motion

When system animation / Reduce Motion disables decorative motion:

- Hero idle micro-breath stops;
- Capture Button decorative breathing stops;
- Gold rim sweep stops;
- user-driven Pager drag remains functional;
- Pager settles without decorative overshoot;
- layout and information hierarchy remain unchanged.

Reduce Motion never changes available navigation.

---

## 9. Touch / safe-area requirements

- respect status/navigation safe insets;
- Primary Capture Button must remain fully visible above bottom system inset;
- interactive targets remain at least 48 dp;
- adjacent-card peeks are visual context, not separate miniature buttons outside their real card hit area;
- no interactive content may be clipped by rounded display corners.

---

## 10. Combined board production contract

One board must contain:

### Responsive row
- 9:16
- 19.5:9
- 20:9
- 21:9

Use the same content and same selected FishRecord so only adaptation differences are visible.

### Interaction strip
- selected Pager card;
- adjacent card;
- active swipe;
- single-record state;
- Reduce Motion state.

The board must show layout relationships, not simulate a video timeline.

---

## 11. Acceptance criteria

- [ ] no Hero vertical stretching across 9:16–21:9
- [ ] no content width drift
- [ ] extra height becomes environment breathing room
- [ ] NH01 Pager still shows neighbor peeks
- [ ] NH02 never implies Pager
- [ ] no auto-carousel
- [ ] Reduce Motion stops decorative loops but preserves gesture navigation
- [ ] safe insets do not cover Capture Button

---

## 12. Frozen decisions

1. 1080 × 1920 is the reference coordinate frame.
2. Adaptation is width-first.
3. Core composition shifts as one unit using the frozen offset rule.
4. Hero dimensions scale with width only.
5. Tall screens gain environmental breathing room, not stretched UI.
6. Pager is manual only.
7. Single-record state has no Pager affordance.
8. Reduce Motion removes decoration, not interaction.
