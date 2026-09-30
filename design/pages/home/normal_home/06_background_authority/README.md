# NH06 · Background & Environment Authority / 背景与环境权威 · Design Spec V1

Status: **FROZEN — SPEC + VISUAL**
Scope: **Normal Home / Morning Lake / BG_ENV_HERO**  
Output policy: **AUTHORITY_BOARD**


## Frozen visual authority

- Visual authority: `design/pages/home/normal_home/06_background_authority/frozen/NH06_Background_Authority_V1_Frozen.png`
- Manifest: `design/pages/home/normal_home/06_background_authority/frozen/manifest.json`
- Image: PNG, 1491 × 1055, 1,975,877 bytes, SHA-256 `6eb08594e3d84ff24fa6ba464aa98fd4669211b4102b7fd40dee12017f398aed`
- Frozen status: `FROZEN — SPEC + VISUAL`
- The frozen raster PNG cannot be replaced by SVG, HTML/CSS, screenshots, or reconstruction.

---

## 1. Purpose

NH06 freezes the exact relationship between the shared Morning Lake system and Normal Home.

It resolves earlier ambiguity where the page package predated the repository-level background master.

Core principle:

> **The shared Morning Lake master owns the reusable environment source. NH01/NH02 own complete page composition. A page screenshot never becomes a background asset.**

---

## 2. Canonical Normal Home background source

Master:

`design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`

Frozen source metadata:

- ID: `Morning_Lake_Master_V1`
- native size: **941 × 1672**
- mode: RGB
- bytes: **2,255,291**
- SHA-256: `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`
- role: `DEFAULT_SHARED_MASTER`
- status: **FROZEN**

Normal Home must not use `Morning_Lake_Sunrise_Hero_V1`; that source is reserved for Empty Home.

---

## 3. Variant

Normal Home mapping:

`home_normal_v1 → BG_ENV_HERO → Morning_Lake_Master_V1`

Frozen treatment:

- MistWhite veil alpha: **0.02**
- saturation: **0.98**
- contrast: **0.98**
- brightness: **1.00**
- global blur: **OFF**
- target environmental salience vs Home: **1.00**

Numeric authority:

`design/system/backgrounds/morning_lake_v1/treatment_contract.json`

Usage authority:

`design/system/backgrounds/morning_lake_v1/usage_map.json`

---

## 4. Visual character

Normal Home environment must remain:

- clear quiet morning;
- pale blue-gray-green lake;
- distant mountain layers;
- restrained mist;
- low saturation;
- scarce warm gold;
- documentary / natural feeling.

Forbidden:

- visible strong sun as focal point;
- orange sunrise wash;
- sunset;
- strong HDR;
- saturated cyan lake;
- night/HUD styling;
- different mountain/lake world;
- page-specific regenerated landscape.

---

## 5. Crop / scaling rule

The source keeps its native binary and native composition.

Runtime presentation may use crop-to-fill to cover the device viewport, but:

- preserve the master camera viewpoint;
- preserve the mountain/lake horizon relationship;
- prioritize central environmental continuity over edge details;
- never non-uniformly stretch;
- never add new scene objects;
- never blur the whole background;
- never bake page UI into the background.

For taller phones, use additional vertical environment coverage under NH05 rules; do not stretch the source.

---

## 6. Authority boundary

### Morning Lake Background System owns

- reusable background source;
- source SHA/dimensions;
- BG_ENV_HERO numeric treatment;
- cross-page background-family consistency;
- crop/source restrictions.

### NH01 / NH02 visual authority owns

- Header placement;
- statistics;
- Recent Catch section;
- Hero placement;
- Pager composition;
- CTA;
- Capture Button;
- complete page visual balance.

### NH06 board owns

- explanation of the relationship above;
- crop examples;
- allowed / forbidden environment examples;
- source-to-page traceability.

NH06 board does **not** replace NH01/NH02 page Visual Authority.

---

## 7. Screenshot extraction prohibition

Hard rule:

> **Never crop a Normal Home screenshot to create or replace Morning_Lake_Master_V1.**

Forbidden sources for reusable background production:

- NH01 page screenshot crop;
- NH02 page screenshot crop;
- emulator screenshot;
- runtime evidence frame;
- image with Hero/UI removed by inpainting.

The canonical master must be consumed directly.

---

## 8. Empty Home boundary

Empty Home is a sibling use of the same background family but uses a different approved source:

`Morning_Lake_Sunrise_Hero_V1`

Therefore:

- Empty Home may contain a visible sun / stronger sunrise character;
- Normal Home may not inherit that visible-sun source;
- do not merge the two master images.

---

## 9. Authority board production contract

One NH06 Authority Board must contain:

1. canonical `Morning_Lake_Master_V1` source preview;
2. source metadata / SHA;
3. `BG_ENV_HERO` treatment values;
4. Normal Home usage chain:
   `Master → BG_ENV_HERO → NH01/NH02`;
5. 9:16 and tall-screen crop examples;
6. clear boundary:
   `Background Source Authority ≠ Full Page Composition Authority`;
7. forbidden examples:
   screenshot crop / sunrise source / regenerated lake / global blur.

This is an Authority Board, not another page Hi-Fi.

---

## 10. Visual Spec supersession

The older Normal Home Visual Spec statement saying that no independent Morning Lake master exists is obsolete.

Current authority is:

1. `Morning_Lake_Master_V1.png` for reusable background source;
2. Background System V1 contract for treatment and mapping;
3. NH01/NH02 Frozen page images for complete page composition.

---

## 11. Acceptance criteria

- [ ] Normal Home points to Morning_Lake_Master_V1
- [ ] Empty Home Sunrise source is not used
- [ ] exact BG_ENV_HERO treatment is documented
- [ ] no global blur
- [ ] no screenshot extraction
- [ ] crop preserves source composition
- [ ] NH01/NH02 remain full-page authorities
- [ ] NH06 board is explanatory authority, not a replacement Hi-Fi

---

## 12. Frozen decisions

1. Morning_Lake_Master_V1 is the Normal Home reusable background master.
2. Normal Home uses BG_ENV_HERO.
3. BG_ENV_HERO numeric treatment is frozen.
4. Empty Home Sunrise Hero source is excluded.
5. Screenshot extraction is prohibited.
6. Background authority and full-page composition authority remain separate.
7. NH06 is one Authority Board.
