# NH01/NH02 · Gemini Native V3 / Adaptive V2 — 统一现行规范 V1

Contract ID: NH01_GEMINI_ADAPTIVE_V2_UNIFIED_V1
Status: SPEC_FROZEN; Android Runtime: NOT_VERIFIED
Scope: only populated NH01/NH02 routed through GeminiNormalHomeContent; not Empty Home, shared Profile, or legacy NormalHomeContent.

Responsive geometry, typography sizing/wrapping, spacing, insets, Hero/Peek/Pager and camera/touch bounds are owned exclusively by the frozen `NH01_RESPONSIVE_LAYOUT_V2.json`. This document retains non-responsive product semantics. Its earlier Adaptive V2.2 responsive measurements below are historical and do not override the frozen machine contract.

## 1. 规范权威和版本替代

| Field | Current NH01 Gemini route | Retained legacy authority |
| --- | --- | --- |
| Page IA, state, navigation | NH01/NH02 feature and NH03 state; current Gemini Top/Middle/Footer | Original NH01 1080x1920 and NH02 immutable reference images |
| Layout, geometry, fonts, page gaps | Frozen machine contract `design/pages/home/normal_home/responsive/NH01_RESPONSIVE_LAYOUT_V2.json` | NH05 historical A1/A2 frozen coordinates, valid on legacy NormalHomeContent only |
| Real catch metadata | NH04 identity/omission plus V2.2 anti-orphan override below | NH04 single-line specimen remains for legacy route |
| Lake background | NH06 immutable Morning_Lake_Master source, Gemini viewport TopCenter Crop | NH06 legacy center-Crop viewport rule, not current Gemini crop |
| Header avatar | NH01_Avatar_Visual_Contract_V1_1_FROZEN.md, mist silhouette | NH07 fisherman bitmap and V2 default asset retained byte-identically; not current Gemini fallback |
| Auth and interaction | Guest -> Login; signed-in -> Profile; preserve avatar URL/token isolation | Guest identity != signed-in, even if same line illustration |
| Hero, Pager | True FishRecord photo, safe crop/Fit same-source backdrop, finite stable-ID Pager | No fake fish, no cloned pages, no 1-record neighbors |
| Motion/feedback | Original subtle 6000ms Hero idle, manual pager, shared capture motion, Reduce Motion; no page audio | Legacy frozen motion, haptic and sound tokens remain applicable |
| Acceptance | New 20 current route gates + 24 native profile runs + original 46 G/V/S/M/F domains | Old fixed pixel-geometry rows are historical comparison, not automatically PASS |

Only the named field is superseded on the named route. All Frozen pixels and asset hashes stay intact. Gemini is an intentional new Compose layout and does not claim 1080x1920 Frozen Pixel Parity; original product data, navigation, accessibility and safety rules remain binding. Static or design SPEC_FROZEN does not imply runtime PASS.


## 1.1 Responsive authority boundary

The static V1.1 archive preserves historical frozen image identities and presentation metadata. It is not a current Gemini runtime calculator. The V2.0 machine contract is the sole source for the current Gemini route's logical allocation, safe dimensions, responsive component parameters and acceptance bounds. Values below in this historical authority are not active responsive rules.

## 2. Section spacing — delegated

The previous V2.2 section-spacing table is historical implementation evidence. Current spacing targets, formulas, measurement anchors, font-scale behavior and continuity bounds are defined only by `NH01_RESPONSIVE_LAYOUT_V2.json`. No value in the historical table overrides that machine contract.

## 3. Adaptive Hero/Peek and short viewport — delegated

The prior Hero/Peek sizing and compact-window rules are retained as historical evidence only. The frozen V2 machine contract owns the current ratio, target width, size caps, Peek geometry, stable-record pager behavior, safe-area handling and exact short-screen acceptance. If measured native constraints cannot satisfy that contract, report the required failure state with bounds; do not weaken the contract.

## 4. NH04 metadata priority and one-character orphan rule

Show the actual sanitized species first, positive measurements only, and time ahead of optional location. Omit missing rows and separators; never fabricate values or leave an isolated final CJK glyph or dangling punctuation. Shorten or ellipsize optional location before required time is lost. Responsive line wrapping, line limits and font-scale behavior come from the V2 machine contract. Validate actual `TextLayoutResult` line ranges and native screenshots; `hasVisualOverflow=false` alone is insufficient.

## 5. NH06 / NH07 / photo / motion

The immutable Morning Lake source and its Gemini crop behavior, the approved avatar appearance, Guest versus signed-in identity semantics, same-source photo safety and existing motion/lifecycle behavior remain in this non-responsive authority. Responsive component and touch bounds are resolved from the V2 machine contract. Preserve current reduced-motion behavior and do not introduce new page audio or unrelated effects.

## 6. Independent admission

The existing V2.2 acceptance matrix and its 24 Android API28 executions remain unchanged as a baseline. The current responsive task adds the specified native viewport/font-scale and continuity-boundary probes without weakening that matrix; the 480dp width-boundary probe records an explicit test height and does not redefine M05. Require the same exact source SHA, measured bounds/text/insets/touch evidence, screenshots/logs and APK provenance. Missing evidence remains NOT_RUN/REVIEW_REQUIRED, never PASS. A build or browser preview is not Android Runtime PASS.
