# YuJian AI Orb Core V1

**Component ID:** `intelligence.ai_orb_core`  
**Version:** V1 · **Status:** REVIEW

## Identity
AI Orb is YuJian's brand-level visual representation of its intelligence: a transparent lake-water glass core, inner refraction, restrained sunlight-gold highlights, lake-blue water light and muted aquatic-green life ribbons. It is one reusable state-driven component family, not a page-specific animation or a collection of unrelated PNGs.

## Ownership
AI Orb owns its visual renderer, state model, time-derived motion frame, transparent core raster, internal refraction and front/rear ribbon rendering. Page hosts own navigation, CameraX/Gallery, the single PhotoSession and original photo, the 700ms photo transform, recognition/model events, trusted Mask/Contour gates and result routing. AI Orb never owns inference or business state.

## Cross-page behavior
- Home/Empty/Normal: frozen Primary Capture Button V1.1; click navigates to Capture; no Orb Morph on Home navigation.
- Camera Capture Ready: same frozen capture artwork and existing touch/haptic behavior.
- Camera shutter → Processing: camera/pearl visual dissolves into transparent lake-water Orb over 900ms.
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

## Frozen inputs
- Capture master: `design/system/components/primary_capture_button/assets/capture_button_main_v1.png`, 208×208 RGBA, SHA-256 `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327`.
- Processing core: `app/src/main/res/drawable-nodpi/processing_water_core_v1.webp`, 384×384 RGBA, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`.
- The existing same-basename Android capture WebP is a different legacy candidate (SHA-256 `f8114d5d6d1b2211ce3527e849b07ed9434d8d102f88b247bac28aa2eaea870a`); it is not the canonical Primary Capture Button.

## Geometry and accessibility
- Orb core 80dp standard / 68dp compact; ribbons travel no more than 14dp outside the core.
- Keep visual bounds separate from semantic touch bounds; capture retains the registered minimum target.
- Orb is decorative and does not duplicate button semantics or TalkBack focus.
- Respect responsive geometry, lifecycle, Reduce Motion and low-performance rendering.

## Prohibitions
No whole-sphere raster spin, mechanical closed orbital rings, dark rectangular background, detached particles in formal Processing, HUD/scan line, fake progress, fake fish geometry or inference changes.

Final visual freeze remains REVIEW until product-owner acceptance.
