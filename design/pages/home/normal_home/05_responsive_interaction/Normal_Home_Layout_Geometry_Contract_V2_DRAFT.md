# Normal Home Layout Geometry Contract V2 — DRAFT

> **STATUS: DESIGN_REVIEW_PENDING — NOT APPROVED FOR PRODUCT IMPLEMENTATION**
>
> This is a proposed single-coordinate-system design contract. It does not replace any Frozen image or existing NH05 rule until the product owner explicitly approves the differences documented here. No Kotlin, assets, tests or CI have been modified by this documentation submission.

## 0. Scope and authority

- Repository: \`pan277942135/Yujian_App\`; source Work A branch: \`fix/normal-home-hifi-typography-parity-v1\`; reviewed source base HEAD: \`565e99eacab4c1412e69301e224cb598db1c22bf\`.
- Integration snapshot: PR #128 at \`4f5cb27cd2de983f917f2d1a74a78686f43f5194\`. Neither PR nor \`main\` is to be modified by this draft.
- Visual authority: \`design/system/core_visual_v1/reference/normal_home_v1.png\` (1080 × 1920 **physical pixels**, recorded SHA-256 \`6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377\`). This SHA is from the existing design registry; this draft does NOT claim to have independently rehashed the PNG.
- Prior responsive authority: \`design/pages/home/normal_home/05_responsive_interaction/README.md\` (NH05, Frozen V1). Text authority: \`Normal_Home_Typography_Scaling_Contract_V1.md\` (V1.1).
- Product scope: **Normal Home with ≥1 valid FishRecord only**. Empty Home, Fish Guide, FishRecordDetail, Recognition, Auth, global tokens, backend and model remain untouched.
- Authority precedence *pending review*: immutable Frozen raster for the 1080×1920 reference composition; V1.1 text metrics for page text; NH05 for V1 behavior; any proposed V2 deviation must be explicitly listed, justified, and approved, not silently preferred.

## 1. Measurable defects and hypotheses (not yet product root-cause proof)

Source \`app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt\` has Normal Home \`Modifier.fillMaxSize().padding(safePadding)\` after rendering the fullscreen background; \`app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt\` positions absolute Frozen Y anchors inside this padded coordinate space, and computes an additional \`normalHomeVerticalOffset()\` from the padded maxHeight. This is a **suspected coordinate-origin and height-budget inconsistency**. Do not state it is proven to account for every visual defect without runtime measurements.

The previously collected Normal Home instrumentation evidence (integrated Run \`37915120879\`, artifact ID \`11608748556\`) recorded **11 tests / 1 failure**, \`FAIL_TEST\`, failing on the brand title text bounds. The observed \`glyphOutlineRootBounds.bottom ≈ 211 px\` exceeded \`Header.bottom = 208 px\` by ≈3 px at density=1, fontScale=1. This is a separate clipping concern; it does not on its own prove why the user's long-screen page looks clustered.

## 2. One coordinate reference — proposal requiring approval

### Input values and units

| Variable | Definition | Unit | Acquisition |
|---|---|---|---|
| \`W,H\` | physical app window width and height, **not the cropped content bounds** | physical px | window metrics / Compose root measured bounds |
| \`L,T,R,B\` | safeDrawing insets relative to that same window | physical px | Android WindowInsets |
| \`d\` | current logical density | physical px per dp | LocalDensity.density |
| \`fontScale\` | current system accessibility text setting | semantic scale | LocalDensity / Compose font measurements |
| \`W_safe\` | \`W − L − R\` | physical px | derived, nonnegative |
| \`S\` | \`W_safe / 1080\` | dimensionless | proposed width-first mapping |
| \`H_ref\` | \`1920 × S\` | physical px | derived |
| \`E\` | \`max(0, H − H_ref)\` | physical px | **proposal** for V2 whole-window budget |

A Frozen rectangle \`(x,y,w,h)\` is first mapped to the full-window coordinate system as \`(L + xS, yS, wS, hS)\`. Safe top/bottom insets **constrain** interactive objects but **do not automatically become another Y translation**. In other words, top \`T\` must never be both a layout origin and a second offset. Background remains edge-to-edge.

**Reference guarantee:** \`W=1080, H=1920, L=R=0, T=B=0\` gives \`S=1, E=0\`, every Frozen rectangle at exactly its reference coordinates.

For nonzero insets at reference dimensions, the Frozen rectangles remain geometrically unchanged **if** interactive visual/touch bounds satisfy \`y ≥ T\` and \`y + h ≤ H − B\`; otherwise mark \`CONSTRAINT_CONFLICT\` and supply a bounded adaptation proposal. Insets are constraints, not proof that a re-layout is always necessary.

For horizontal curved/cutout devices, \`L\`/\`R\` are used once in width computation and horizontal origin; record whether unequal insets break visual centering relative to the full physical window. The Frozen authority is for L=R=0; nonzero L/R requires a traceable adaptation.

Compose transformation proposal: \`runtimeDp = physicalPx/d\` for geometry; text continues to use the V1.1 measured physical reference px → sp contract, preserving Android accessibility scaling. Do not substitute \`sp = px\`, do not multiply fontScale twice, and do not presume nonlinear Android font scaling is a pure multiplier; use real \`TextLayoutResult\` at fontScale 1/1.15/1.3. Round only when reporting screen pixel bounds; avoid accumulated rounding at each layout edge.

### Coordinate invariants

1. Every Normal Home component receives its origin, outer bounds and selected shared gaps from **one** page-owned mapping result.
2. Child components must not secretly apply an independent window-density, safe-inset, whole-page shift or scaling factor.
3. Background crop is viewport-based and independent of safe controls.
4. Interaction targets are at least 48 dp, and stay within \`[L, W−R] × [T, H−B]\`. Keep 200px reference camera visual size separate from its touch rect; an invisible 48dp hit area must not change visual size.
5. No crop, parent clipping or typography auto-shrink may conceal a true overflow in the geometry report.

## 3. Frozen 1080 × 1920 component positions

These are **reference physical pixels**, not Android dp/sp. Values from existing NH05 and current V1.1/code are labeled. Where sources conflict, the target is **UNRESOLVED**, never silently chosen.

| Component | Reference x | y | width | height | Evidence |
|---|---:|---:|---:|---:|---|
| Header | 88 | 104 | 904 | 104 | NH05, NormalHomeContent |
| Statistics | dynamic horizontal | 304 | measured row width | **116** | NH05; not fully sized by typography-only contract |
| Recent header | full width | 494 | page width | 70 | NH05, code |
| Hero | 170 | 596 | 740 | 880 | NH05, code |
| CTA container | centered | **1495 / 1504** | 420 (code) | 58 (code) | conflicting code/V1.1 vs NH05 |
| Camera visual | centered | **1564 / 1582** | ≈200 | ≈200 | conflicting code/V1.1 vs NH05 |
| Camera touch | centered | require measurement | 208 (code) | 208 (code) | current HomeCameraButton contract |

- Hero radius 32; page left/right margin 170.
- Statistics row top 304, bottom 420; Header bottom 208; Recent header bottom 564; Hero bottom 1476.
- Hero footer horizontal inset 56 and bottom inset 32; Hero metadata spacing 20.
- Statistics vertical padding 17; value-label gap 8.
- Recent header horizontal inset 108; pager gap 16.

**Reference gaps (not all are visible glyph gaps):** Header bottom→Statistics start = 96; Statistics bottom→Recent top = **74**; Recent bottom→Hero top=32; Hero bottom→CTA top = **19 (code) / 28 (NH05)**; CTA bottom→Camera visual top = **11 (code) / 20 (NH05)**. CTA/Camera pairs are **DESIGN_AUTHORITY_CONFLICT** until the Frozen raster is directly measured to separate container, text and visual bounds.

### Text role values from V1.1 (fontSizePx / lineHeightPx)

| Brand | 72 / 80 |
| Statistics value | 36 / 42 |
| Statistics label | 28 / 32 |
| Recent header | 48 / 52 |
| All action | 40 / 44 |
| Hero species title | 56 / 64 |
| Hero length & weight | 46 / 52 |
| Hero time & location | 36 / 42 |
| Capture CTA copy | 40 / 44 |

These are frozen **font/line design roles**, *not* visible glyph-bitmap heights. Preserve font family, weight, content and accessibility behavior.

## 4. Responsive strategy — proposals, NOT authoritative until review

### 4.1 Reference mode (1080×1920, S=1)

Use the exact Frozen values above, after resolving conflicting CTA/Camera bounds from the PNG. Do not change one component to make another pass. Top and bottom insets must be checked separately against touch rectangles.

### 4.2 Tall viewport (e.g. 1080×2340)

NH05 V1 currently mandates \`offsetY = min(E×0.36,180×S)\` applied **uniformly** to the page; internal gaps remain unchanged. **V2 option B** proposes to use named elastic gaps and environment-only breathing room instead. This **conflicts with NH05 V1** and must not be implemented before approval.

At W=1080, H=2340, horizontal safe insets 0 and S=1: \`E=420\`. Option A (unchanged NH05) shifts everything by 151.2px; Option B is a separately listed candidate in \`Normal_Home_V2_Responsive_Examples.md\` allocating all 420px across top whitespace / bounded between-region gaps / bottom environment. The exact candidate values are not frozen authority. Do not bake them into code or tests before approval.

### 4.3 Narrow/short viewport

For W=720,H=1280 with L=R=0: S=2/3, scaled reference camera visual=133.33px, Hero=493.33×586.67px. Check whether interactive visual and touch bounds fall inside \`T..H−B\`. On insufficient height: retain text readability and 48dp interaction targets; candidate safe fallback may use vertical reachability/scrolling **only after its UX constraints have been approved**. No global downscaling of fonts or Hero as a collision workaround.

### 4.4 Font scale / long strings / data state

Run candidate mapping across 360/393/480dp widths, physical densities 2/2.75/3, fontScale 1.0/1.15/1.3, and one vs two or more records. Include long species names/locations, default avatar, 9:16 and wide fish photos, Reduced Motion. Enlarged fonts must be measured with actual Compose bounds, not predicted as exact linear values. Text ellipsis is allowed only where the existing Frozen typography contract says so.

## 5. Measurements and acceptance required after approval

For every candidate device/profile produce \`W,H,L,T,R,B,d,fontScale\`, root content bounds, mapped and measured \`(left,top,right,bottom)\` for every region, intercomponent gaps, text visible glyph bounds and \`TextLayoutResult\`, clip/overflow states, touch geometry, original PNG SHA, side-by-side diff and a PASS / FAIL / BLOCKED judgement.

This draft only calculates from documented source constants and explicitly named **hypothetical inset examples**. It is **not** a runtime or physical-device acceptance report. User-supplied JPEG is a visual incident, not a source of reliable native device insets/DPI.

## 6. Approval questions (blocking)

1. Is Frozen PNG measurement approved as the arbiter between CTA y1495 vs y1504 and Camera y1564 vs y1582, with visual bounds distinguished from CTA text and touch areas?
2. Does user approve **overriding NH05's uniform tall-screen shift** with V2 elastic-region allocation? If yes, which precisely named allocations in the examples document?
3. Are short-screen overflow/reachability fallbacks allowed, or must Work A propose a different product-specific layout?
4. What measured tolerance, if any, may be applied to raster-vs-font-outline discrepancy? None is presumed.

**STOP POINT: DESIGN_REVIEW_PENDING. No Kotlin, app assets, global design tokens, screenshot PASS claim, CI rerun, release candidate or main merge until explicit approval.**
