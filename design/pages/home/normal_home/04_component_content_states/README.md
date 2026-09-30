# NH04 · Component & Content States / 组件状态与内容边界 · Design Spec V1

Status: **SPEC FROZEN — COMBINED BOARD PENDING**  
Scope: **Normal Home component states / content boundary behavior**  
Output policy: **COMBINED_BOARD**

---

## 1. Purpose

NH04 freezes component-level variants that should not create additional full-page Home designs.

It covers:

- Header identity states;
- default-avatar fallback;
- HOME Hero field availability;
- long-text behavior;
- content omission rules.

Core principle:

> **Preserve geometry. Remove unavailable content cleanly. Never fill missing data with fake values.**

---

## 2. Header identity states

The board must show these three Header states side by side.

### A · Logged-in / Real Avatar

- use the user's valid avatar media;
- circular crop;
- same Home Header geometry as NH01/NH02;
- tap → My / Profile.

### B · Logged-in / No Avatar

Use **YuJian Default Profile Avatar V1**.

Authority:

`design/pages/home/normal_home/02_first_catch/default_avatar_contract.json`

Rules:

- same visual diameter and hit target as real avatar;
- Mist White translucent circle;
- Deep Blue-Gray person line icon;
- tap → My / Profile;
- no initials;
- no random color;
- no generated face;
- no fish logo;
- no “未设置头像” text.

Avatar load failure uses the same fallback.

### C · Guest

Guest is a separate account-entry state.

- Guest ≠ default avatar;
- tap → login / registration entry;
- do not visually imply that Guest is a logged-in profile.

NH04 illustrates this rule but does not redefine the default-avatar contract frozen in NH02.

---

## 3. HOME Hero field hierarchy

Canonical hierarchy:

1. species identity
2. measurement line
3. time / location context line

Card size, image size and overall overlay geometry remain stable across data combinations.

---

## 4. Measurement rules

Valid measurement values are positive sanitized values only.

### Both present

`42.6 cm · 1.28 kg`

### Length only

`42.6 cm`

### Weight only

`1.28 kg`

### Neither present

Hide the measurement line entirely.

Forbidden:

- `-- cm`
- `0 kg`
- `未知`
- empty separators
- keeping an empty line solely to preserve text count

Text group may reflow inside the existing overlay zone, but the Hero card itself must not resize.

---

## 5. Time / location rules

### Both present

`昨天 18:20 · 浙江 · 千岛湖`

### Time only

Show time only.

### Location only

Show location only.

### Neither present

Hide the context line entirely.

Separators appear only between existing values.

No placeholder such as `未知地点` or `时间未知` is authorized on Home.

---

## 6. Species fallback

A blank / invalid species label uses the existing presentation fallback:

> **鱼获**

Do not invent another species and do not display a model-confidence state on Home.

If the product later adopts a dedicated “鱼种待确认” record state, that requires a versioned revision; it is not introduced by NH04 V1.

---

## 7. Long text behavior

### Species

- maximum: one line;
- preserve approved species typography;
- ellipsize at end when required;
- do not shrink below the shared typography token merely to fit;
- do not wrap to two lines inside HOME Hero.

### Time + location context

- maximum: one line;
- time has higher retention priority than location;
- location consumes remaining width and ellipsizes at end;
- separators must not remain after truncated/missing content.

Example:

`昨天 18:20 · 浙江省杭州市淳安县千岛…`

### Header

“渔见” never compresses to make room for avatar/account state.

---

## 8. Image/content combinations

The combined board must cover at least:

1. complete Hero;
2. length only;
3. weight only;
4. no measurements;
5. no location;
6. no time;
7. long species;
8. long location.

These are **component specimens**, not eight page screenshots.

Media-unavailable belongs to NH03, not NH04.

---

## 9. Accessibility / hit targets

Changing content must not reduce interactive hit targets.

- account entry target remains at least 48 dp;
- Hero tap target remains the full Hero card;
- `全部` retains its existing target;
- omitted metadata must not create invisible tap regions.

---

## 10. Motion / Haptic / Sound

Content changes do not trigger:

- count-up;
- text morphing;
- card resize animation;
- automatic haptic;
- sound.

If runtime data updates while visible, text may update without page-level choreography.

---

## 11. Board production contract

One combined board, organized in two sections:

### Header strip
- Real Avatar
- Default Avatar
- Guest

### Hero content matrix
- Complete
- Length only
- Weight only
- No measurements
- No location
- No time
- Long species
- Long location

Board annotations are not runtime UI.

---

## 12. Frozen decisions

1. Header has three semantic states: real avatar / default avatar / Guest.
2. Default avatar semantics remain owned by NH02.
3. Missing fields are omitted, not replaced with fake values.
4. Card geometry remains stable.
5. Species fallback is `鱼获` in V1.
6. Species and context remain single-line with end ellipsis.
7. Time outranks location when horizontal space is constrained.
8. NH04 is one component board, not multiple page Hi-Fis.
