# YuJian AI Orb Core V1

**Component ID:** `intelligence.ai_orb_core`  
**Version:** V1 · **Status:** REVIEW  
**Registry boundary:** Candidate only. The frozen `Component_Registry_V3_FINAL.json` remains unchanged until product-owner visual acceptance.

## Identity and four supplied visual assets
AI Orb is YuJian's brand-level visual representation of intelligence: a transparent lake-water glass core, internal refraction, restrained sunlight-gold highlights, lake-blue water light and muted aquatic-green life ribbons.

- **Asset 01 — Transparent lake-water glass sphere:** visual master reference for the Orb core (1254×1254 RGBA; SHA-256 `2773e4628ff6132605314693742488542c3e70fd04b581d8825dd13cea0067ee`).
- **Asset 02 — Transparent glass-water camera ring:** activated capture visual and the first stage of Button Morph (1254×1254 RGBA; SHA-256 `9c24d789078a83e9cf3eb16aa7e558eac8f23e4fd8de14ea0ab3116e427dbe13`). Existing Android WebP derivative is 384×384 RGBA at `app/src/main/res/drawable-nodpi/capture_button_main_v1.webp`, SHA-256 `f8114d5d6d1b2211ce3527e849b07ed9434d8d102f88b247bac28aa2eaea870a`.
- **Asset 03 — Emerald star-lit flowing crystal sphere:** motion/space reference only (1254×1254 RGB; SHA-256 `910d15313b39ee2afa277356c46290e2cb7739b80a60e9ab4efaa14a29d74122`). Do not use its baked dark background or detached bokeh as runtime layers.
- **Asset 04 — Home lake-fishing scene:** page composition and environment reference only (941×1672 RGB; SHA-256 `f6ddf1bf27319b08c43c4becdb8179dbf8f643fae7d58077b5ebf69a9cf7defd`).

The Home/Capture idle control remains the separately frozen Primary Capture Button V1.1. Asset 02 is not a second navigation button; it is the activated visual used at the beginning of the 900ms camera-shutter-to-Orb transition.

## Ownership
AI Orb owns its shared renderer, visual state model, time-derived motion frame, transparent core raster, internal refraction and front/rear ribbon rendering. Page hosts own navigation, CameraX/Gallery, the single PhotoSession and original photo, the 700ms photo transform, recognition/model events, trusted Mask/Contour gates and result routing. AI Orb never owns inference or business state.

## Cross-page behavior
- Home/Empty/Normal: frozen Primary Capture Button V1.1; click navigates to Capture; no Orb Morph on Home navigation.
- Camera Capture Ready: same frozen idle artwork and existing touch/haptic behavior.
- Camera shutter → Processing: use Asset 02 as the activated source for 0–150ms, dissolve it into the transparent lake-water Orb from 150–350ms, open independent ribbons from 350–650ms and settle by 900ms.
- Gallery → Processing: Orb core is visible immediately; no camera-button source is fabricated.
- Processing: 700ms Photo Morph and 900ms Orb entrance run in parallel from the same stable Processing photo-frame clock and same PhotoSession.
- Processing → Result: consume existing real-result completion/contraction and hand off to RR09; do not add a delay.
- Result: Orb is hidden and never morphs back into a camera button during successful flow.

## Seven material responsibilities
1. Environment halo/reflection — page-owned environment layer.
2. Detached droplets/particles — disabled in the current Processing baseline.
3. Rear life ribbons — native Canvas paths.
4. Outer glass shell — retained in transparent core raster.
5. Inner water/refraction — core raster plus native Canvas refraction.
6. Front life ribbons — native Canvas paths.
7. Specular glints — baked base plus moving Canvas glint.

Camera Core is a separate state slot, not an eighth material bitmap.

## Frozen implementation inputs
- Idle button: `design/system/components/primary_capture_button/assets/capture_button_main_v1.png`, 208×208 RGBA, SHA-256 `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327`.
- Activated camera-water source: Asset 02 above; reuse its existing Android WebP derivative for the morph.
- Existing Processing core: `app/src/main/res/drawable-nodpi/processing_water_core_v1.webp`, 384×384 RGBA, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`. Its current extraction provenance is Asset 03; compare the runtime appearance against Asset 01 before final visual freeze.
- The Android idle button resource must be a byte-identical copy of the frozen 208×208 V1.1 source. Do not resize, recolor or overwrite the design-system source.

## Geometry and accessibility
- Orb core 80dp standard / 68dp compact; ribbons travel no more than 14dp outside the core.
- Keep visual bounds separate from semantic touch bounds; capture retains the registered minimum target.
- Orb is decorative and does not duplicate button semantics or TalkBack focus.
- Respect responsive geometry, lifecycle, Reduce Motion and low-performance rendering.

## Prohibitions
No whole-sphere raster spin, mechanical closed orbital rings, dark rectangular background, detached particles in formal Processing, HUD/scan line, fake progress, fake fish geometry or inference changes.

Final visual freeze remains REVIEW until product-owner acceptance.
