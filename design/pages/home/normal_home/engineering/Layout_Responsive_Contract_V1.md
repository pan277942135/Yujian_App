# Normal Home — Layout, Geometry, Typography and Responsive Contract V1 (P0)
Status: ENGINEERING_ACTIVE / REFERENCE_VISUAL_ANCHORS_PARTIALLY_PENDING
This is an implementation/acceptance binding of the approved A1 font contract and NH05 A policy; not a redesign of NH01/NH02.

## Inputs, coordinate system and units

Window is the edge-to-edge app *window*, not the physical display when multiwindow is active.
Read physical W,H (px) and actual safeDrawing insets L,T,R,B (px) in that same window. Read density d and Android fontScale. Record all six window/inset values from the device. Do not mix system display metrics and Compose-root bounds without proving equality.

    Wsafe = max(0,W-L-R)
    Hsafe = max(0,H-T-B)
    S = Wsafe/1080
    E = max(0,Hsafe-1920*S)
    delta = min(0.36*E,180*S)
    xWindow = L+xFrozen*S
    yWindow = yFrozen*S+delta         // NO +T
    widthWindow = widthFrozen*S
    heightWindow = heightFrozen*S
    ComposeDp = physicalPx/d

All core elements use one typed page mapping; do not apply a parent safePadding and then a second frozen-window offset. For RTL use PHYSICAL left safe inset L for physical x placement, not start/end that change meaning; mirror only intentional semantic arrangements, never mirror the frozen lake/photo or arbitrary absolute axes. The backdrop always fills W×H (edge to edge) using the registered bitmap and centered ContentScale.Crop. Insets constrain interactive targets inside [L,W-R]×[T,H-B]. On ordinary screens, no independent section shifting is permitted.

Reference proof: W=1080,H=1920,L=T=R=B=0 yields S=1 and delta=0. For no-inset 1080x2340, E=420, delta=151.2px for EVERY region. Insets change the measured delta only via Hsafe; do not add T to windowY. Measure actual boundsInWindow on runtime.

## Normative reference geometry (physical px at S=1, delta=0)

| Object | x | top | width | height | Anchor type |
|---|---:|---:|---:|---:|---|
| Header | 88 | 104 | 904 | 104 | region/container |
| Statistics | row | 304 | semantic groups | 116 | region/container |
| Recent header | page width, inner horizontal inset 108 | 494 | page width | 70 | region/container |
| HOME Hero selected | 170 | 596 | 740 | 880 | rounded card outer bounds |
| Hero radius | n/a | n/a | 32 | n/a | radius px |
| Hero footer | 56 inside Hero left | 32 above bottom | depends on text | depends on rows | inner insets px |
| CTA current implementation | centered | 1495 | 420 | 58 | CONTAINER; visual glyph not this Y |
| Camera current implementation | centered | 1564 | 208 | 208 | OUTER TOUCH box; visible child 200x200, centered |
| CTA legacy NH05 | centered | 1504 | unknown | unknown | ambiguous shorthand VISUAL Y; NOT a container coordinate |
| Camera legacy NH05 | centered | 1582 | approx 200 | approx 200 | ambiguous visible rim Y; NOT outer touch Y |

For camera when S=1, touch top 1564 and 200px child centered in 208px touch box produces child frame top 1568; rendered gold rim may start lower due to source alpha. Do not equate child frame, visible gold rim, and hit area. For CTA, text visible glyph y is not inferred from 58px parent. **Source bitmap measurement REQUIRED** to close optical deltas: freeze-verified 1080x1920 PNG, CTA glyph/visual block ROI, camera circle/rim ROI, documented segmentation method and uncertainty. Runtime touch rect must be measured from semantics. The verified original PNG has now been scanned: CTA near-white visible ink y1527..1558 and Camera high-contrast near-white y1590..1773, gold y1593..1789, with exact mask definitions in Visual_Anchor_Asset_Resolution_V1.md. Preserve implementation container anchors only as implementation facts; compare runtime visual ink to verified source ink. Android parity remains pending. This visual-only gate must not block independent Kotlin layout/inset/typography fixes.

## Frozen A1 typography and spacing

Reference role font/line-height **physical px**: Brand 72/80; Statistics value 36/42; statistics label 28/32; Recent title 48/52; 全部 action 40/44; Hero species 56/64; Hero measurement 46/52; Hero time/location 36/42; CTA 40/44.

    referenceFontPx -> fontSp = max(referenceFontPx*S, roleMinimumPhysicalPx)/d
    referenceLinePx -> lineSp = max(referenceLinePx*S, roleMinimum*1.2, fontPx*1.08)/d

Role minimum values are inherited without change from ../05_responsive_interaction/Normal_Home_Typography_Scaling_Contract_V1.md (brand/recent/species 18px, other roles 16px). Preserve system fontScale; do not treat reference px as sp or multiply density twice. Nonlinear accessibility font scaling must be VERIFIED using actual TextLayoutResult rather than assuming one numeric multiplier. Preserve role font family, weight, optical baseline, one-line ellipsis constraints; do not arbitrarily reduce size to avoid overflow.

Reference ancillary geometry: recent header inset 108px, statistics vertical padding 17px, stats value→label spacing 8px, footer text row spacing 20px, pager gap 16px, Hero footer left 56px/bottom 32px. Do not stretch these gaps on long screens.

## Fit and fallback: deterministic two modes

NORMAL_FIXED mode when all interactive rects fit within safe bounds, glyph/layout boxes do not clip, and no two mandatory regions overlap. This mode covers target 9:16, 19.5:9, 20:9, 21:9 where constraints permit. All six regions (Header, Stats, Recent, Hero, CTA, Camera) use their common frozen Y mapping. No vertical independent expansion.

SAFE_OVERFLOW mode activates ONLY when NORMAL_FIXED cannot meet the above actual measured constraints (short window, oversized system text, extreme safe insets, landscape/multiwindow). It MUST:
1. Keep the lake background edge-to-edge and retain the same original component appearances and width-scaled Hero proportions; do not globally shrink text, Hero or camera to fake a fit.
2. Keep primary Capture + CTA as a single action group above the measured bottom inset, centered within the horizontal safe span, with full ≥48dp **touch** bounds and preserved group internal spacing.
3. Make Header + Stats + Recent + Hero a vertically reachable, bounded scrollable content area between the top safe inset and reserved action group, with enough bottom padding to prevent hidden card content; the same card order, no new UI chrome and no new navigation. In this conditional mode the common frozen Y offsets are no longer claimed as exact reference parity (label mode SAFE_OVERFLOW in evidence).
4. Never let Pager's horizontal drag hijack a vertical scroll. Touch slop follows platform nested scroll/gesture direction.
5. Do not reduce system fontScale or hide required fields to fit; optional missing metadata may be omitted only according to NH04 rules.
6. If an app window is physically too small even for the ≥48dp capture target plus a usable content viewport, return mode UNSUPPORTED_WINDOW and acceptance status FAIL with reason CONSTRAINT_UNMET (or BLOCKED_INFRA only if the target window could not be created/tested), rather than pretending PASS. Work must report concrete W,H,insets,collision rectangles; no custom third design invented.

SAFE_OVERFLOW is a defined accessibility fallback for feasibility, not permission to substitute an alternative NH01 Frozen image or to reposition Normal Home on a normally fitting tall phone. If a product-specific visual decision is later desired, version the contract explicitly; no new A/B candidate workflow.

## Touch and clipping

- Interactive area ≥48dp in BOTH dimensions for avatar, stats links, 全部, Hero and Capture, measured on the unmerged semantics target. Invisible touch extension may not modify frozen visible size or steal an adjacent action.
- Do not allow the camera hit rect into navigation safe inset even when the visible 200px circle fits.
- On overflow, component groups must not obscure one another, clip important text or attach clickable semantics to 记录天数.
- On one record the Hero is centered, retains identical 740×880 ref geometry, has no fake adjacent card or pager hint.
- Profile avatar is a visual 92px reference diameter, distinct from its ≥48dp touch target. Guest and signed-in profile fallback must remain semantically and visually distinct.

## Geometry acceptance and report schema

Per profile record W,H,L,T,R,B,d,fontScale, mode (NORMAL_FIXED/SAFE_OVERFLOW/UNSUPPORTED_WINDOW), S, E, delta, window/root coordinate transforms, EACH node rect, visible glyph bounding box/TextLayoutResult, interactive rect, and clipping/intersections. Use source PNG SHA and APK commit SHA. Against Frozen compare like kind only: visible-vs-visible, box-vs-box, touch-vs-touch if a touch contract exists. Never compare a legacy visual Y to a Kotlin touch-box Y as if equivalent.

Modes and non-running profiles cannot be marked Visual PASS. Any new tolerance requires an explicit acceptance-contract change supported by repeat measurements; do not invent error margins or weaken existing assertions.

## Finalized exception and specialized visual anchors

Use [Short_Window_Adaptive_Freeze_V1.md](Short_Window_Adaptive_Freeze_V1.md) for the certified 320x480dp test envelope and measured overflow branch; use [Visual_Anchor_Asset_Resolution_V1.md](Visual_Anchor_Asset_Resolution_V1.md) for SHA-verified CTA/Camera ink and background/avatar identities; use [Quantitative_Visual_Acceptance_Contract_V1.md](Quantitative_Visual_Acceptance_Contract_V1.md) for versioned numeric tolerances. Earlier statements that optical thresholds are UNSET have been superseded, not silently reinterpreted. No normal-mode responsive transform is changed.
