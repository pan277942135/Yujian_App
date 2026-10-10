> **SUPERSESSION NOTICE (2026-10-10):** HISTORICAL CONFLICT REGISTER. Many statuses refer to old SHAs; current decision/proof status is maintained by engineering/README.md and its acceptance matrix. Current Work/Validation entry: [Normal Home Engineering Authority](../engineering/README.md). No historical evidence or original numbered rows below are deleted.

# Normal Home V2 — Authority Conflict Matrix

> **STATUS: PARTIALLY_APPROVED / COORDINATE_REVIEW_PENDING (2026-10-10).** User has approved Font A (V1.1 frozen values) and NH05 long-screen A (one-piece offset). See `Normal_Home_V2_Decision_Record_20261010.md`. Other baseline coordinates, safe-area origin and test evidence remain unresolved.

## Source register

| ID | Source | Scope |
|---|---|---|
| R0 | `design/system/core_visual_v1/reference/normal_home_v1.png`, 1080×1920, registry SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377` | immutable canonical visual raster |
| R1 | `design/pages/home/normal_home/05_responsive_interaction/README.md` | Frozen NH05 responsive coordinate anchors, one-piece shift, touch constraints |
| R2 | `design/pages/home/normal_home/05_responsive_interaction/Normal_Home_Typography_Scaling_Contract_V1.md` | V1.1 measured font/line roles, spacings and runtime anchor restatement |
| R3 | `app/src/main/java/com/yujian/ai/ui/home/NormalHomeContent.kt` at `565e99e...` | current page local positioning and vertical shift |
| R4 | `app/src/main/java/com/yujian/ai/ui/screens/HomeScreen.kt` at `565e99e...` | fullscreen background; NormalHomeContent inside safeDrawing padding |
| R5 | `app/src/main/java/com/yujian/ai/ui/home/NormalHomeTypography.kt` | reference-pixel to Compose sp/dp scaling formulas |
| R6 | GitHub Actions Run `37915120879`, Normal Home artifact `11608748556` | 11 device instrumentation tests; 1 brand glyph overflow failure |
| R7 | user-supplied Normal Home photograph of integrated APK `4f5cb27c` | observed undersized typography / crowded central composition; not intrinsic native DPI/Inset evidence |

## Conflict ledger

| ID | Question / issue | Evidence from authority | Current implementation / competing authority | Status | Decision needed |
|---|---|---|---|---|---|
| C-01 | Where is CTA top? | R1: y=1504 | R2/R3: y=1495 | **DESIGN_AUTHORITY_CONFLICT** | measure R0's actual CTA text & container separately and approve one common mapping |
| C-02 | Where is camera visual top? | R1: y=1582 | R2/R3: y=1564 | **DESIGN_AUTHORITY_CONFLICT** | measure R0 camera visual circle vs hit rect and approve |
| C-03 | Hero→CTA / CTA→Camera gaps | R1 implies 28 / 20 if CTA h58 | R3 implies 19 / 11; R0 needs image measurement | **DERIVED_CONFLICT** | derive from resolved component rectangles, don't separately free-tune |
| C-04 | How tall is Statistics? | R1: y304 h116; bottom420 | R2 abbreviated anchor often says y304 only; R3 height results from contents | **RESOLVED SOURCE FACT / RUNTIME_PENDING** | use R1's 116px as reference, verify dynamic Compose measured rect |
| C-05 | Long-screen vertical adaptation | R1: `offsetY=min(E×0.36,180×S)` moves entire page, no independent region stretch | earlier V2 option B proposed separate elastic gaps | **APPROVED A / B REJECTED (2026-10-10)** | keep unchanged NH05 one-piece offset and frozen gaps; E uses measured usable height after safe insets |
| C-06 | Safe inset origin | R1: resolve safe insets before content math | R4 passes safeDrawing padding and R3 applies absolute window design Y; coordinate origin unclear | **HYPOTHESIS_NEEDS_MEASUREMENT** | define one origin and no duplicate top inset; verify against runtime metrics |
| C-07 | Height budget | R1 requires `usableHeight − 1920S`; R3 receives height already reduced by R4 padding | previous draft full-window H for E is NOT user-approved | **NH05 E FORMULA APPROVED / ORIGIN AUDIT PENDING** | implement `E=max(0,usableHeight−1920S)` once; independently diagnose whether safe-top padding duplicates the coordinate origin |
| C-08 | Brand clipping | R0 text visual target; R2 brand 72/80; R6 brand text glyph outline y≈211 while parent ends 208 | previous instrumentation 11 tests, 1 fail | **CONFIRMED OBSERVED TEST FAILURE** | resolve font metrics/container without unapproved font-size reduction or assertion bypass |
| C-09 | Frozen reference vs user-perceived typography size | R0 visual glyph stats label height ≈23px; R2 font28px | R5 at 1080 physical px / density3 maps stats label to **9.33sp**, stats value **12sp**, Hero metadata **12sp** | **FONT A APPROVED (2026-10-10)** | retain V1.1 reference fonts and no visual B/C enlargement; still verify actual glyph rendering and mapping against Frozen |
| C-10 | Screen evidence completeness | R6 instrumented 11/1 but image evidence did not complete | prior Run report had fallback infra classification text on outer workflow logs | **EVIDENCE GAP** | classify from artifact JSON and instrumentation log first; do not claim Screenshot PASS |
| C-11 | Avatar tap target vs visual size on narrow devices | R3 visual avatar ref92px × S | 720px at density2 implies visual≈30.7dp, below recommended 48dp touch target if hit box equals visual | **RUNTIME / ACCESSIBILITY_RISK** | preserve frozen visual but design separate invisible ≥48dp hit rect, measure no overlap |
| C-12 | Text size conversion at different densities | R2 defines reference physical px → sp | At 1080px physical width, density1 and density3 can produce same glyph physical pixels but different sp values | **A FONT AUTHORITY APPROVED / RUNTIME VERIFICATION PENDING** | retain exact V1.1 formula, measure actual TextLayoutResult and physical glyph bounds |

## Typography implication (crucial; not an authorization to edit values)

With **physical window width 1080px**, no horizontal insets, `S=1`, `density=3`, `fontScale=1`, the published V1.1 contract calculates:

| Role | Frozen font physical px | Equivalent Compose sp at density3 |
|---|---:|---:|
| Brand | 72 | 24.00 |
| Stats value | 36 | 12.00 |
| Stats label | 28 | 9.33 |
| Recent header | 48 | 16.00 |
| All | 40 | 13.33 |
| Hero species | 56 | 18.67 |
| Measurement | 46 | 15.33 |
| Meta time/location | 36 | 12.00 |
| Capture CTA | 40 | 13.33 |

This arithmetic is **not a bug in sp conversion**: it follows the frozen physical-raster contract. But it may reproduce text the user regards as too small. If the frozen image itself is not the desired optical baseline at modern phone reading distance, no amount of correcting safe inset or camera Y can make text larger **and** preserve its published physical glyph bounds. This is a genuine product authority question: do not tell Work A to freehand enlarge text; ask the product owner to approve a new exact set of typography role pixels or an explicit device readability floor before any implementation.

## Proven / not proven

**Source-proven:** R1 vs R2/R3 CTA & camera numbers differ; R1 has Statistics h116; R4+R3 combine safeDrawing padding and absolute Frozen-coordinate formulas; V1.1 converts 28 reference physical px to 9.33sp at density3/1080px.

**Runtime evidence:** brand glyph extends ≈3px beyond current Header parent in the cited API28 run; instrumentation executed, so do not describe it as `NOT_RUN`.

**Not proven:** exactly how much of the user's visual complaint is caused by safe insets, font geometry, the Frozen baseline itself, or the long-screen offset; no native user device density or insets have been measured; the draft has not pixel-inspected R0 and therefore does not claim to have settled C-01/C-02.

## Decision record: two choices approved, remaining questions pending

| Decision ID | Reviewer / selection | State |
|---|---|---|
| D1: CTA / Camera true Frozen visual bounds | user / designer after R0 measurement | **PENDING** |
| D2: Retain NH05 one-piece vertical shift; reject B elastic gaps | user, 2026-10-10 | **APPROVED A** |
| D3: Preserve all V1.1 physical-pixel font/line values (Font A) | user, 2026-10-10 | **APPROVED A** |
| D4: Short-screen reachable fallback / minimum touch rect | user | **PENDING** |
| D5: Allowed raster measurement tolerance and rounding policy | user | **PENDING** |

**DO NOT MODIFY** existing NH05 README, frozen PNG, Kotlin, shared tokens, tests, main or integrated PR #128 merely to close this documentation ledger.