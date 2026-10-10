# Normal Home V2 — Coordinate Origin and CTA/Camera Anchor Audit

**Date:** 2026-10-10  
**Status:** `ORIGIN_MODEL_RESOLVED_AT_DESIGN_LEVEL / CTA_CAMERA_RASTER_EVIDENCE_PENDING / PRODUCT_CODE_UNCHANGED`  
**Scope:** Normal Home only. Font A and NH05 one-piece tall-screen A were approved by the user. No permission to improvise typography, independent flexible gaps, or other-page changes.

## 1. Source register and current remote

- Work A PR #122, verified input HEAD `338d4ae9a7bbe14b1146694d711030ef8a09428e`.
- Frozen authority `design/system/core_visual_v1/reference/normal_home_v1.png`, 1080×1920 physical px, registered SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`.
- Frozen NH05 responsive rules: `design/pages/home/normal_home/05_responsive_interaction/README.md`, §§2–4, 9–12.
- Typography V1.1 `Normal_Home_Typography_Scaling_Contract_V1.md`.
- Source behavior: `app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt`, `app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt`, `app/src/main/java/com/yujian/ai/ui/home/HomeCameraButton.kt`, `app/src/main/java/com/yujian/ai/ui/designsystem/components/YuJianCaptureButton.kt`.
- Runtime reference for title glyph bounds: Run `37915120879`, artifact `11608748556`: 11 instrumentation tests, 1 failed; **brand glyph outside Header by approximately 3px**. This issue is separate from coordinate-system ambiguity.

## 2. Issue #2 — a single coordinate/inset contract (design-level resolution)

### Observed old call chain

`HomeScreen.kt` puts the lake backdrop in an edge-to-edge fullscreen Box, but passes `Modifier.fillMaxSize().padding(safePadding)` to `NormalHomeContent`. The latter uses frozen absolute `y` anchors inside that padded content and calculates `normalHomeVerticalOffset()` from the already shrunken `maxHeight`.

For a uniform top inset `T`, this **source-proven mapping** yields (with a common size scale and offset):
```
oldWindowY = T + frozenY*S + delta
delta = min(max(0, (H-T-B) - 1920*S)*0.36, 180*S)
```
The extra `+T` is a **coordinate-origin translation**, not literally a second subtraction of the safe height. The height budget is reduced once; the problem is using full-window Frozen Y values with safe-content coordinates. This predicts a common downward translation of all Normal Home nodes relative to the Frozen window frame (subject to actual runtime coordinate probes). Do not mislabel the mere existence of a top inset as a proven production bug without comparing source and measured root semantics.

### Single mapping to approve for implementation

**Reference origin:** the top-left pixel of the *edge-to-edge physical app window* (not the top-left safe content). Its reference canvas is exactly the 1080×1920 Frozen raster. `safeDrawing` supplies bounds for interaction and usable-height calculation; it is **not** a second Y origin.

Inputs, all measured for the same window (avoid mixing display resolution with app-window size):
```
W,H = physical app-window width,height (px)
L,T,R,B = safeDrawing insets (px) within that window
d = local density (px per dp)
W_safe = W-L-R
S = W_safe/1080
H_safe = H-T-B
E = max(0, H_safe - 1920*S)
delta = min(E*0.36, 180*S)   // user-approved NH05 A
```
For any frozen visual bbox `x,y,w,h`:
```
windowX = L + x*S
windowY = y*S + delta       // crucial: NO extra +T
windowW = w*S
windowH = h*S
ComposeGeometryDp = windowPhysicalPx/d
```
Vertical `T,B` enter `H_safe` **exactly once** and independently enter the safety check `top>=T, bottom<=H-B`. The translation `windowY` does not add `T`. If the accessible visual/touch region exceeds the safe interval, mark `SAFE_CONSTRAINT_CONFLICT`; do not guess new gap weights, shrink text or invent scrolling behavior. Report the smallest corrective rule requiring user approval. Horizontal `L,R` enter width budget and horizontal origin once; centered Hero/CTA/Camera use the center of the **safe horizontal span**, not a conflicting full-window-centered modifier if L!=R.

For the reference 1080×1920 with zero insets, `S=1,delta=0`; all Frozen rectangles return exact nominal pixel coordinates.

**Implementation implication — not yet executed:** use a fullscreen, unpadded Normal Home composition root; pass one typed NormalHome layout mapping that already incorporates safe insets and records `windowOrigin=(0,0)`. All child bbox/CTA/camera positions consume mapped rectangles; no independent parent safe padding, duplicate extra Y offset or arbitrary typography scaling. Empty Home remains untouched.

### Reproducible numerical examples

`T=80,B=90` below is a **hypothetical** sample, NOT read from the user's phone. Horizontal insets are zero, W=1080.

| Profile | H | H_safe | E | NH05 delta | New window Header Y | Hero Y | Camera outer Y from code anchor 1564 | Old source-predicted Header Y |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Frozen no insets | 1920 | 1920 | 0 | 0 | 104 | 596 | 1564 | 104 |
| Reference with sample insets | 1920 | 1750 | 0 | 0 | 104 | 596 | 1564 | 184 |
| Long no insets | 2340 | 2340 | 420 | 151.2 | 255.2 | 747.2 | 1715.2 | 255.2 |
| Long with sample insets | 2340 | 2170 | 250 | 90 | 194 | 686 | 1654 | 274 |

The explicit old-vs-new discrepancy is **T=80px** for the two sample-inset profiles; the exact user's device insets are unknown. In actual runtime, collect `W,H,L,T,R,B,d,fontScale`, `boundsInWindow`, and `boundsInRoot`, then prove there is **one** consistent mapping and no extra top-origin shift.

The Camera column uses only the **current code outer touch-target anchor** so this calculation does not silently decide the disputed visual anchor.

## 3. Issue #1 — separate visual, text, container, and touch bounds

Two normative documents disagree:
- NH05 design README: **CTA Y=1504**, **Camera Y=1582** (`visual size≈200`).
- V1.1 typography doc & current Kotlin constants: **CTA Y=1495**, **Camera Y=1564**.

**Important new source finding:** these are **not demonstrated to refer to the same rectangle**, so blindly changing either pair is prohibited.

### CTA anchor types

Current Kotlin sets `NormalHomeCtaY=1495` on a centered **420×58px container** (at S=1). The text itself is a 40px font / 44px line, vertically centered within the 58px container.

- Current **container top**: 1495, bottom 1553.
- Estimated **Text line box** before glyph-leading effects: `1495 + (58-44)/2 = 1502`.
- Actual **visible glyph** top/bottom must come from frozen raster or Compose TextLayoutResult; cannot be inferred from the 40px font size.
- NH05 `CTA Y=1504` could be a text/visual-content anchor rather than the 58px container, but that interpretation is **HYPOTHESIS**, not pixel proof.

### Camera anchor types

`NormalHomeCameraY=1564` offsets the **outer touch-target Box** of `YuJianCaptureButton`, explicitly sized **208×208px** at S=1. Its **200×200px** visual child is centered inside it:
```
touch top = 1564
visual frame top = 1564 + (208-200)/2 = 1568
visual frame bottom = 1768
```
The compiled APK's `normal_home_runtime_v1/camera/camera_button_base.png` source is **208×208 RGBA** and is scaled into the 200×200 visual child; alpha/edge treatments mean its perceived gold ring bounds may differ from the frame. NH05 `Camera Y=1582` names a **visual** reference, not necessarily the same 208px touch rectangle. The existing 14px residual between current calculated visual frame top 1568 and NH05 1582 is **unresolved**.

Current CTA→camera gap = 1564−1553=**11px from CTA container bottom to camera touch outer top**; the alternate NH05 Y values would imply a nominal 20px gap **if** both were equivalent component frames. These are not safe to compare as one kind of gap until source images and measured bbox types are consistent.

### Decision precedence and measurement STOP

- Primary source: checksum-verified `normal_home_v1.png`; inspect pixel coordinates separately for CTA visible glyph, CTA visual/container block, camera gold rim/opaque visual and nominal circular extent. A raster does **not** encode the invisible touch bbox.
- Secondary source: NH05 design spec for design intent and responsive anchors.
- Implementation description only: current Kotlin/V1.1 coordinate constants. Kotlin is not permitted to supersede an actually measured Frozen visual bbox.
- **Do not assert 1495, 1504, 1564 or 1582 to be a measured Frozen PNG coordinate without the file bytes and an inspectable ROI.**

**Retrieval obstacle in this review session:** GitHub connector provided text source but could not return the canonical binary PNG for direct pixel analysis. The APK includes derivatives of the capture assets but **not the immutable full Normal Home Frozen image**. Therefore source-semantic distinctions are resolved, but visual pixel adjudication remains **RASTER_EVIDENCE_PENDING**, not falsely marked PASS.

### Exact next evidence action (read-only, no product code change)

On an authorized GitHub-checkout Work environment with access to binary files:

1. `git show HEAD:design/system/core_visual_v1/reference/normal_home_v1.png > /tmp/normal_home_v1.png`.
2. Verify exact `1080×1920` and registered `sha256sum`. **If mismatch: STOP / AUTHORITY_MISMATCH**.
3. Create an original-resolution, nonresampled ROI `(x=250..830, y=1450..1830)` with overlaid horizontal pixel rulers every 5px; include a larger full-width screenshot for context.
4. Record and visually confirm CTA glyph top, CTA container/transparent area **if visible**, circular rim first/last visible pixels and circle centroid. Measure visible mask with clearly documented contrast/alpha rules; do not infer touch bounds from a flattened PNG.
5. Report a compact table `{anchor_kind, bbox(x0,y0,x1,y1), method, pixels_of_uncertainty}` and source checksum, plus the annotated PNG. Map those visible targets back through the known Kotlin linebox and 208px touch/200px child offset before changing any constants.
6. If NH05 and V1.1 values describe different anchor kinds, retain their numbers but **rename them by anchor kind** instead of pretending the exact design changed. If same kind and true disagreement, use measured Frozen bitmap as authority and explicitly deprecate the incorrect older doc entry after user review.

**Stop condition:** no Kotlin or Frozen changes, no regenerated APK and no claim that anchor collision has been fully corrected without checksum-verified visual measurement and approved numeric bbox reconciliation.

## 4. Summary and next gate

| Issue | Current outcome | Missing proof |
|---|---|---|
| #2 Safe-area/window origin | **DESIGN_MAPPING_RESOLVED**: one edge-to-edge window origin, safe height for NH05 E, no extra vertical +T, all placements use one page-owned mapping | real-device runtime bbox, safe-inset values and screenshot verifying parity |
| #1 CTA/Camera document Y conflict | **ANCHOR_SEMANTICS_IDENTIFIED**: code CTA container, camera touch box vs NH05 visual top values | actual checksum-verified Frozen visual ROI and measured rectangles; final coordinate values still pending |
| Brand glyph overflow | **KNOWN FAIL_TEST**, separate bug | new implementation TextLayoutResult + glyph outline bounds |
| Font/long-screen scheme | **APPROVED** Font A + NH05 single-shift A | device validation |

This is a **design-level audit**; final implementation and visual PASS require the missing raster/runtime evidence. No other page, frozen image, app code or CI/runner changes are authorized by this audit.