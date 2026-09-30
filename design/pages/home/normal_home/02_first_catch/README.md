# NH02 · First Catch Home / 第一条鱼首页 · Design Spec V1

Status: **SPEC FROZEN — HIFI PENDING**  
Scope: **Normal Home / one valid FishRecord**  
Output policy: **INDEPENDENT_HIFI**  
Parent visual authority: `design/system/core_visual_v1/reference/normal_home_v1.png`

---

## 1. Purpose

NH02 defines the first stable Normal Home state after the user has saved the first valid FishRecord.

It is not a new Home style. It is the **single-record state of the existing Normal Home system**.

The design goal is:

> The user's first real catch becomes the only Hero on Home. The page changes from “no records” to “my fishing memory has started” without adding celebration UI, onboarding UI, or game-like reward language.

NH02 must feel like the same page as NH01, with only the minimum structural change required by `recordCount = 1`.

---

## 2. Entry condition

NH02 is eligible when the resolved valid FishRecord collection contains exactly one record:

```
validRecordCount == 1
```

Typical journey:

```
Empty Home
  → Capture / Recognition
  → Save first valid FishRecord
  → NH02 First Catch Home
```

NH02 is already **Normal Home**. It is not:

- Empty Home success state
- onboarding
- tutorial
- achievement overlay
- modal confirmation
- temporary save-success page

Loading / unresolved record state is owned by **NH03 · 页面状态与异常**, not NH02.

---

## 3. Authority model

### 3.1 Parent page authority

Until the dedicated NH02 Hi-Fi is frozen, NH01 remains the visual reference for all shared composition:

`design/system/core_visual_v1/reference/normal_home_v1.png`

SHA-256:

`6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`

Reference canvas:

**1080 × 1920**

### 3.2 Shared system authority

NH02 inherits:

- `BG_ENV_HERO`
- `Morning_Lake_Master_V1`
- `YuJianCatchHeroCard / HOME`
- `YuJianPrimaryCaptureButton`
- shared typography
- shared color
- shared spacing / radius
- shared text-action rules

### 3.3 NH02-specific authority

This document is the behavioral / composition authority for the single-record state.

The future NH02 Frozen Hi-Fi may clarify pixel-level visual details, but it **must not contradict this spec or redesign the parent Normal Home system**.

---

## 4. NH01 ↔ NH02 delta

The state change is intentionally narrow.

| Region | NH01 · Multiple Records | NH02 · First Catch |
| --- | --- | --- |
| Background | Morning Lake | **same** |
| Header | same | **same** |
| Stats structure | 3 values | **same** |
| Recent header | 最近鱼获 / 全部 | **same** |
| Hero area | center card + left/right neighbor peeks | **one centered card only** |
| Pager | user-driven horizontal pager | **no paging affordance** |
| CTA | 记录下一条鱼 | **same** |
| Capture button | shared primary capture | **same** |
| Celebration | none | **none** |

The **only major composition delta** is:

> multi-card pager → single centered Hero card.

---

## 5. Page hierarchy

NH02 keeps the exact Normal Home information hierarchy:

1. Morning Lake environment
2. first real catch Hero
3. contextual catch information
4. summary statistics
5. section/navigation affordances
6. primary capture action

The first catch must become the page's main subject naturally. Do not increase statistical weight or add a “first catch” reward layer.

---

## 6. Reference geometry

Use the existing Normal Home reference anchors.

Reference canvas: **1080 × 1920**

| Region | NH02 rule |
| --- | --- |
| Header | x 88, y 104, w 904, h 104 — unchanged |
| Statistics | y 304, h 116 — unchanged |
| Recent header | y 494, h 70 — unchanged |
| Hero | **x 170, y 596, w 740, h 880** |
| CTA | y 1504 — unchanged |
| Capture button | y 1582, visual size ≈200 — unchanged |

### Hero centering rule

```
heroCenterX = pageCenterX = 540
heroWidth   = 740
heroX       = 170
```

The single Hero uses the same central-card geometry as NH01.

It must **not** become wider or taller merely because adjacent cards are absent.

---

## 7. Header

Header inherits NH01 without modification.

Authenticated state:

```
渔见                                      Avatar
```

Guest state follows the approved shared account affordance.

The first catch does not add:

- “欢迎回来”
- nickname hero text
- “第一次记录”
- save-success status
- new account prompts

### 7.1 Logged-in avatar state — FROZEN

NH02 freezes the identity semantics for the Home header:

| Account state | Header avatar |
| --- | --- |
| Logged in + valid avatar media | user's real avatar |
| **Logged in + no avatar set** | **YuJian Default Profile Avatar V1** |
| **Logged in + avatar load failure** | **YuJian Default Profile Avatar V1** |
| Guest / not logged in | Guest account entry; **not** the default profile avatar |

Default avatar and Guest avatar are different semantic states and must not be conflated.

### 7.2 Default Profile Avatar V1 — FROZEN

Design contract:

`design/pages/home/normal_home/02_first_catch/default_avatar_contract.json`

Current Android implementation reference:

`app/src/main/res/drawable/profile_fallback_v13.xml`

The design contract is authoritative; the Android drawable is an implementation reference.

Visual language:

- circular profile placeholder;
- same display diameter and click target as the real Home avatar;
- translucent Mist White circular surface;
- subtle white border;
- Deep Blue-Gray head-and-shoulders line icon;
- no text label;
- no initials;
- no random color;
- no generated face;
- no fish / brand logo used as a person substitute.

At the 1080 × 1920 Normal Home reference canvas, the Header avatar visual diameter remains approximately **92 reference px**, matching NH01.

The vector source uses a 96 × 96 viewport. Its frozen optical structure is:

- outer disc center: (48, 48), radius 43;
- outer fill: `#6BFFFFFF`;
- outer stroke: `#9EFFFFFF`, width 2;
- person line color: `#30485A`;
- person line width: 4;
- round line cap on shoulder contour.

### 7.3 Interaction

For a logged-in user, the default avatar is not a disabled placeholder.

It has exactly the same account-entry behavior as a real avatar:

> tap → My / Profile

Avatar fallback itself must not trigger:

- toast;
- “请设置头像” prompt;
- red dot;
- onboarding badge;
- automatic navigation to Edit Profile.

### 7.4 NH02 Hi-Fi rule

The canonical NH02 Hi-Fi may show a real user avatar as the standard logged-in sample.

It does **not** need a second full-page NH02 image for the no-avatar case.

The no-avatar / real-avatar / Guest visual comparison belongs to **NH04 · 组件状态与内容边界**, but NH04 may only illustrate these already-frozen semantics; it may not redefine the fallback.


---

## 8. Statistics

The canonical NH02 Hi-Fi uses:

```
1                 1                 1
鱼种              鱼获             记录天数
```

This is the expected visual sample for a genuine first-record state.

Runtime values remain domain-derived and must not be hardcoded.

Interactions stay identical to Normal Home:

- 鱼种 → Fish Guide
- 鱼获 → My Catches
- 记录天数 → informational only; **not clickable**

No number count-up animation is authorized.

No “+1” treatment is authorized.

---

## 9. Recent Catch section

Section header remains:

```
最近鱼获                                  全部 >
```

Do **not** rename it to:

- 第一条鱼获
- 我的第一条鱼
- 首次鱼获
- 新鱼获

Reason:

The IA must remain stable when the user later adds record #2, #3, and beyond.

### “全部” behavior

Keep `全部 >` visible even when only one record exists.

It continues to navigate to My Catches.

The purpose is structural stability, not record-count optimization.

---

## 10. First Catch Hero

### 10.1 Structure

NH02 shows exactly one `YuJianCatchHeroCard / HOME`.

Required:

- one real FishRecord
- one centered Hero
- no left neighbor
- no right neighbor
- no duplicated record
- no translucent fake card
- no empty card shell
- no pager dots
- no left/right arrows
- no “swipe” hint

The lake environment remains visible in the side space where NH01 normally shows adjacent-card peeks.

That increased calmness is intentional.

### 10.2 Content hierarchy

Hero content follows NH01:

**Level 1 — species**
- fish species name

**Level 2 — measurements**
- length / weight when present

**Level 3 — context**
- time / location

Canonical Hi-Fi should use a complete record so the key state is not visually conflated with missing-data behavior.

Recommended canonical sample:

```
草鱼
42.6 cm · 1.28 kg
昨天 18:20 · 浙江 · 千岛湖
```

Missing measurement, location, time, long text and other content-boundary cases belong to **NH04**.

### 10.3 Image treatment

Use the same HOME Hero media rules as NH01:

- real catch photo
- fish remains the primary subject
- preserve the actual catch identity
- restrained lower readability treatment
- no synthetic fish replacement
- no confidence overlays
- no rarity treatment
- no screenshot-derived card bitmap

If the media fails to load, that state belongs to **NH03**.

---

## 11. Single-record interaction

When only one valid record exists:

```
pageCount = 1
```

The Hero must behave as a stable single card.

### Allowed

- tap Hero → FishRecordDetail(recordId)
- Hero idle micro-breath when motion is enabled

### Not allowed

- horizontal page change
- fake drag into an empty page
- edge behavior implying more records
- automatic carousel movement
- pagination indicator
- onboarding swipe cue
- “more catches coming” placeholder

Implementation may retain a shared pager container internally only if the runtime result is behaviorally indistinguishable from a single stable card.

---

## 12. Motion

NH02 inherits the existing selected-Hero idle motion:

- cycle: 6000 ms
- scale: 1.000 → 1.008 → 1.000
- Y: 0 → -2 dp → 0
- no rotation
- no opacity pulse

Because there is only one record:

- adjacent-page transforms are inactive
- pager settling motion is absent
- no horizontal autonomous movement exists

### Reduce Motion

When Reduce Motion / system animation disables decoration:

- Hero remains scale 1.000
- Hero Y remains 0
- capture decorative breathing / sweep stops
- layout remains unchanged

---

## 13. Haptic and sound

NH02 adds no first-catch-specific feedback.

### Haptic

- page entry: NONE
- first-catch appearance: NONE
- Hero idle: NONE
- Hero tap: no custom haptic
- statistics refresh: NONE
- Primary Capture Button: inherit shared light-impact convention

### Sound

NH02 introduces no:

- save-success sound
- first-catch sound
- lake ambience
- achievement sound
- page-entry sound

---

## 14. Transition into NH02

After the first valid FishRecord is saved, Home may resolve into NH02 through the product's normal navigation/state update.

There is **no NH02-specific celebration transition**.

Forbidden:

- card pop/bounce entrance
- gold burst
- confetti
- trophy/achievement animation
- statistics count-up
- Camera Button bounce
- full-screen success overlay
- automatic flip / reveal

The emotional change comes from the user's real catch now occupying the Home Hero.

---

## 15. CTA and capture action

CTA remains:

> **记录下一条鱼**

Do not change it to:

- 再钓一条
- 添加下一条
- 再记录一条
- 完成第一条
- 继续收集

The Primary Capture Button remains exactly the shared `YuJianPrimaryCaptureButton`.

NH02 does not add a gallery shortcut.

---

## 16. Background

NH02 and NH01 share the same Normal Home environment.

```
NH02 Environment = NH01 Environment
```

Background source relationship:

`Morning_Lake_Master_V1 → BG_ENV_HERO → Normal Home`

The first catch does not change:

- time of day
- weather
- mountain silhouette
- lake color
- mist character
- warmth level

Background authority detail is owned by **NH06**.

---

## 17. Content ownership boundaries

NH02 deliberately does not absorb every Normal Home edge state.

| Concern | Owner |
| --- | --- |
| First / single record main state | **NH02** |
| Loading / unresolved Home | NH03 |
| Catch image unavailable | NH03 |
| Refresh / error preservation | NH03 |
| Header visual comparison: real avatar / default avatar / Guest | NH04 — presentation board only; fallback semantics frozen in NH02 |
| Missing measurement fields | NH04 |
| Long species / location text | NH04 |
| Taller aspect ratios | NH05 |
| Reduce Motion board | NH05 |
| Background master / crop authority | NH06 |

This prevents NH02 from becoming a catch-all state sheet.

---

## 18. Hi-Fi production contract

NH02 requires one independent full-page Hi-Fi because it is a key state.

The future Frozen image must:

1. use 1080 × 1920 reference canvas;
2. preserve NH01 page structure and vertical anchors;
3. preserve NH01 central Hero dimensions;
4. remove both adjacent-card peeks;
5. use one centered real-catch Hero;
6. use canonical stats sample `1 / 1 / 1`;
7. retain `最近鱼获 / 全部`;
8. retain `记录下一条鱼`;
9. retain shared Capture Button;
10. not add first-catch badge, reward, onboarding, or celebration;
11. when representing a logged-in user without avatar media, use **YuJian Default Profile Avatar V1**;
12. never substitute the Guest avatar for the logged-in default-avatar state.

### Comparison discipline

When producing NH02 Hi-Fi, avoid unrelated aesthetic changes.

The review question is:

> “Does the existing Normal Home become a correct, calm single-record state?”

—not—

> “Can we redesign Normal Home to make the first catch more dramatic?”

---

## 19. Acceptance criteria

NH02 spec is satisfied when all statements below are true.

### State

- [ ] exactly one valid record is represented
- [ ] Home is Normal Home, not Empty Home
- [ ] no unresolved/loading semantics leak into the state

### Composition

- [ ] Hero is centered
- [ ] Hero uses NH01 central-card size
- [ ] no neighbor-card peeks exist
- [ ] no fake/duplicated cards exist
- [ ] header / stats / section / CTA / capture positions remain aligned with NH01

### Content

- [ ] canonical Hi-Fi uses a complete real catch
- [ ] stats sample is 1 / 1 / 1
- [ ] 最近鱼获 / 全部 remains
- [ ] CTA is 记录下一条鱼

### Header identity

- [ ] logged-in + avatar uses real avatar
- [ ] logged-in + no avatar uses YuJian Default Profile Avatar V1
- [ ] logged-in avatar load failure falls back to the same default avatar
- [ ] Guest account entry is visually/semantically distinct from the logged-in default avatar
- [ ] default avatar taps into My / Profile exactly like the real avatar

### Interaction

- [ ] Hero opens the only FishRecord
- [ ] no usable/implied pager interaction exists
- [ ] 记录天数 is non-clickable
- [ ] no gallery affordance is added

### Experience

- [ ] no reward badge
- [ ] no congratulatory copy
- [ ] no celebration animation
- [ ] no first-catch sound/haptic
- [ ] page still reads as quiet fishing memory, not collection/game progression

---

## 20. Frozen decisions

The following NH02 decisions are frozen in V1:

1. NH02 is the one-record Normal Home state.
2. The parent page style is inherited from NH01.
3. Single Hero stays the same size as NH01 central Hero.
4. Single Hero is horizontally centered.
5. No fake adjacent cards.
6. No pager affordance with one record.
7. Recent Catch title and “全部” remain unchanged.
8. Canonical Hi-Fi stats sample is 1 / 1 / 1.
9. CTA remains “记录下一条鱼”.
10. No first-catch badge / celebration / achievement layer.
11. No new Haptic or Sound.
12. NH03–NH06 own edge cases rather than multiplying NH02 variants.
13. Logged-in users without avatar media use YuJian Default Profile Avatar V1.
14. Avatar load failure uses the same default avatar.
15. Guest avatar/account entry is not the logged-in default avatar.
16. The default avatar keeps the same Home header geometry and account-entry interaction as a real avatar.
17. NH04 may illustrate avatar states but may not redefine the fallback semantics frozen here.

Any change to these decisions requires a versioned NH02 spec revision.
