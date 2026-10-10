# Normal Home V2 — Responsive Calculations & Device Examples

> **STATUS: FONT A + LONG-SCREEN NH05 A APPROVED (2026-10-10); BASELINE COORDINATES / INSET ORIGIN PENDING.** See `Normal_Home_V2_Decision_Record_20261010.md`. Any B distributed-gap figures below are rejected historical comparisons, NOT implementation targets.
>
> All measurements in this file are **physical px** unless clearly marked dp/sp. The values below are deterministic calculations from the existing documented reference anchors; they are **not screenshots or actual device telemetry**. The two CTA/Camera anchor versions are unresolved and must be approved after inspecting the canonical Frozen PNG.

## 1. Variables and one coordinate origin

- Design canvas: 1080×1920 physical px.
- Proposed V2 full-window inputs: window `W,H` physical px; safe inset `L,T,R,B` physical px; physical density `d`; Compose device fontScale.
- `W_safe=W−L−R`, `S=W_safe/1080`, `H_ref=1920×S`, **approved NH05** `E=max(0,usableHeight−H_ref)` where `usableHeight` excludes actual safe top/bottom insets once. **Approved** `offsetY=min(E×0.36,180×S)` is shared across the whole composition. Proposed geometric reference `x_window=L+x_frozen×S`, and full-window vs content-origin placement of `y_frozen×S + offsetY` is still **COORDINATE_REVIEW_PENDING** (do not apply T twice).
- Insets `T,B` are **constraints** for interactive bounds, not automatically another translation to the layout origin. The edge-to-edge scene background fills W×H.
- Height/width layout in Compose dp divides physical px by d once. Frozen typography is a separately specified font px/sp mapping and must be verified with the real nonlinear Compose text layout.

**User-approved rule (NH05 A, 2026-10-10):** `offsetY=min(max(0,usableHeight−1920S)×0.36,180S)` uniformly applies to the full page. Do NOT replace the usable-height budget with whole-window H, stretch intercomponent gaps or select B. The mapping between this local Y and the physical-window origin still needs proof.

## 2. Frozen 1080×1920 reference, zero safe insets — exact target

`W=1080,H=1920,L=T=R=B=0,S=1,E=0`.

| Component | Frozen x | top y | w | h | bottom |
|---|---:|---:|---:|---:|---:|
| Header | 88 | 104 | 904 | 104 | 208 |
| Stats | measured horizontally | 304 | reference group widths | 116 | 420 |
| Recent header | page wide | 494 | page wide | 70 | 564 |
| Hero | 170 | 596 | 740 | 880 | 1476 |
| CTA **current code anchor only** | centered | 1495 | 420 | 58 | 1553 |
| Camera **current code anchor only** | centered | 1564 | 200 | 200 | 1764 |

Top environment: 104 before Header. Bottom environment: 156 after the camera visual. Reference gaps: Header→Stats 96; Stats→Recent 74; Recent→Hero 32; Hero→CTA 19; CTA→Camera 11 (**last two depend on unresolved anchor version**).

**Alternate NH05 anchor version:** CTA top1504 => gap Hero→CTA28; Camera top1582 => gap CTA→Camera20 (assuming 58px CTA container). No part of this draft claims that the canonical PNG was already measured sufficiently to choose the version.

## 3. Tall screen 1080×2340, insets zero — side-by-side formula comparison

`W=1080,H=2340,L=R=T=B=0,S=1,H_ref=1920,E=420`.

### A: USER-APPROVED NH05 (zero-inset calculated example, NOT runtime evidence)

`δ=min(420×0.36,180)=151.2px`. Everything gets the same y shift; all internal gaps unchanged.

| Component | baseline top | old NH05 effective top (using current code CTA/Camera anchors) | effective bottom |
|---|---:|---:|---:|
| Header | 104 | 255.2 | 359.2 |
| Stats | 304 | 455.2 | 571.2 |
| Recent | 494 | 645.2 | 715.2 |
| Hero | 596 | 747.2 | 1627.2 |
| CTA | 1495 | 1646.2 | 1704.2 |
| Camera | 1564 | 1715.2 | 1915.2 |

Remaining bottom environment = `2340−1915.2=424.8px`; top environment =255.2px. Note R1/NH05's declared CTA/Camera anchor would result in y+9/+18px respectively; this example uses **current-code anchor variant** purely to isolate the responsive rule.

### B: REJECTED — historical V2 distributed-gaps candidate (DO NOT IMPLEMENT)

The following **E=420px** split was considered during review and **rejected** on 2026-10-10. It is retained strictly as a historical comparison, not as a design authority or test target:

| Region carrying additional height | Proposed Δpx | Resulting gap or whitespace | Source status |
|---|---:|---:|---|
| Window top environment, before Header | +80 | 104+80=184 | V2 candidate |
| Header bottom→Stats top | +60 | 96+60=156 | V2 candidate |
| Stats bottom→Recent top | +60 | 74+60=134 | V2 candidate |
| Recent bottom→Hero top | +35 | 32+35=67 | V2 candidate |
| Hero bottom→CTA top | +35 | 19+35=54 | V2 candidate, anchor unresolved |
| CTA bottom→Camera top | +25 | 11+25=36 | V2 candidate, anchor unresolved |
| Window bottom environment, after Camera | +125 | 156+125=281 | V2 candidate |
| **Total** | **420** | **No component resized** | **USER_APPROVAL_REQUIRED** |

The exact candidate top coordinates (for the current-code reference anchor variant only) are:

| Component | Candidate V2 top | size kept | Candidate bottom |
|---|---:|---|---:|
| Header | 184 | 904×104 | 288 |
| Stats | 444 | h116 | 560 |
| Recent | 694 | h70 | 764 |
| Hero | 831 | 740×880 | 1711 |
| CTA | 1765 | 420×58 | 1823 |
| Camera | 1859 | 200×200 | 2059 |

Bottom environment =281px, top environment=184px. The extra 420px is accounted for once (80+60+60+35+35+25+125); no Hero/Camera/text stretching occurs. **There are no frozen min/max bounds or normalized weights for these seven buckets in NH05. This option was explicitly rejected. Never encode these values as a target.**

### C: Same tall window, hypothetical real-world insets

Example **for math only**: `W=1080,H=2340,L=R=0,T=80,B=90,d=3,fontScale=1`. These inset numbers have **not** been read from the user's phone.

**Rejected B illustration only:** it previously computed `S=1,E=420` from full-window height. That conflicts with the now-approved NH05 **usableHeight** budget when insets are nonzero; its Header top184/Camera bottom2059 values have no standing as an implementation target. The scene still fills 1080×2340.

**Existing implementation trace for comparison:** R4's safeDrawing padding creates content height `H−T−B=2170`, whose NH05 excess above 1920 is 250, so offset `δ=min(250×.36,180)=90`. The child also starts at y=T=80. Therefore prior composition predicts `y_window=T+y_frozen+δ`: Header top274, Hero top766, Camera top1734, Camera bottom1934. This prediction follows inspected code under the hypothetical insets and has **not** been confirmed from a real device. Its output is not the same as either zero-inset NH05 example or proposed V2.

## 4. Reference-size window with hypothetical insets — identify possible clipping

Example `W=1080,H=1920,L=R=0,T=80,B=90,d=3`, purely illustrative.

- V2 full-window origin: Header top104≥safe top80; camera visual bottom1764≤safe bottom1830, 66px safe clearance. No extra shifting needed for the visual.
- R4 padded-window + current R3 anchors (E=0): Header top184, camera visual bottom1844; this is **14px below** `safeBottom=1830`. It indicates a testable risk that a safe-area padding and window-reference anchors can contradict each other, not proof of a specific user's phone screenshot. The separate 208px camera touch rectangle needs its own actual measured bounds and safe clearance.

## 5. Short/narrow 720×1280 — geometric scaling without invented UI changes

`W=720,H=1280,L=R=0,S=2/3,H_ref=1280,E=0`. Coordinates are scaled exactly once.

| Component | x | top | w×h or height | bottom |
|---|---:|---:|---|---:|
| Header | 58.67 | 69.33 | 602.67×69.33 | 138.67 |
| Stats | page dependent | 202.67 | h77.33 | 280.00 |
| Recent | page wide | 329.33 | h46.67 | 376.00 |
| Hero | 113.33 | 397.33 | 493.33×586.67 | 984.00 |
| CTA | centered | 996.67 | 280×38.67 | 1035.33 |
| Camera visual | centered | 1042.67 | 133.33×133.33 | 1176.00 |

Hypothetical top inset50, bottom inset60 → safe vertical interval `[50,1220]`; header and camera visual remain inside it. **Touch targets and actual text are not automatically safe merely because visual frames fit.**

If `density=2`, camera nominal 208px reference touch side ×2/3 => 138.67physical px =69.33dp, above 48dp. Avatar nominal visual side92px ×2/3 =>61.33px=30.67dp, **below 48dp if hit target equals visual**; target expansion must be designed without enlarging Frozen artwork. FontScale1.3 text may overflow 69.33px Header; require real layout measurement and a reviewed reachable/flexible fallback (not an auto-shrink).

## 6. Critical: width/density/font implications at 1080px physical width

For `W=1080,density=3,S=1,fontScale=1`, V1.1 font role conversion gives:

| Role | reference font px | intended Compose fontSize sp |
|---|---:|---:|
| Brand | 72 | 24.00 |
| Stats number | 36 | 12.00 |
| Stats label | 28 | **9.33** |
| Recent header | 48 | 16.00 |
| Hero species | 56 | 18.67 |
| Hero measurement | 46 | 15.33 |
| Hero metadata | 36 | **12.00** |
| Capture CTA | 40 | 13.33 |

At `W=720,d=2,S=2/3`, these sp sizes are numerically the same for the same 360dp logical width. That is a mathematical consequence of the current Frozen physical pixel contract, not evidence that the perceived type size is acceptable. It requires an **explicit product typography authority decision** if the user's requested optical/readability size exceeds the Frozen roster. Do not silently boost all fonts.

For fontScale1.15/1.3 the model must not assume Compose's nonlinear scaling of every TextUnit can be represented by a single multiplier. Capture `TextLayoutResult` and actual glyph/nodes on each tested device profile.

## 7. Device acceptance profile matrix — not executed in Phase 1

| Profile | Physical viewport / density | Font scales | Required checks | Current state |
|---|---|---|---|---|
| Frozen reference | 1080×1920, density1/3 probes | 1.0 | all targets exactly map; glyph bounds / full PNG overlay | CALCULATED, runtime NOT_RUN |
| Long portrait | 1080×2340, device exact physical size | 1.0/1.15/1.3 | safe insets, gap distribution, bottom button | CALCULATED, actual device NOT_RUN |
| Narrow short | 720×1280, e.g. density2 | 1.0/1.15/1.3 | min tap48dp, text no clip, camera safe | CALCULATED, runtime NOT_RUN |
| Logical width 360dp | measured device density and physical H | 1.0/1.15/1.3 | full row widths, ellipsis, accessibility | NOT_RUN |
| Logical width 393dp | measured device density and physical H | 1.0/1.15/1.3 | same | NOT_RUN |
| Logical width 480dp | measured device density and physical H | 1.0/1.15/1.3 | same | NOT_RUN |
| Data variations | 1,2,3+ records; no/loaded avatar | as above | pager centering/peek, gestures, hierarchy | NOT_RUN |

For each runtime profile log exact measured `W,H,L,T,R,B,d,fontScale`, component `bbox`, adjacent gaps, `TextLayoutResult`, overflow, minimum 48dp tap regions, screenshot dimensions/sha256 and source APK SHA. If 1080×2340 is not genuinely available, mark it `BLOCKED_INFRA` rather than PASS.

## 8. Blocking approval questions

1. Which pixel anchors are canonical for CTA and Camera: code/V1.1 vs NH05 vs directly measured Frozen visual/interaction rectangles?
2. **DECIDED:** user approved A — NH05 unchanged one-piece shift and frozen intercomponent gaps. The candidate seven-bucket B scheme was rejected.
3. **DECIDED:** user approved Font A — keep all V1.1 reference physical-pixel font roles despite the mathematically equivalent ≈9.33sp stats labels for 1080px/density3. Runtime optical parity remains to be verified.
4. On insufficient height / accessibility font scale, is a bounded vertical scroll layout permissible, and what region remains fixed?
5. What measurable tolerance should apply to font outlines and raster anti-aliasing? Pending authority.

**STOP HERE: A/A POLICY APPROVED; COORDINATE_REVIEW_PENDING.** No app implementation that guesses CTA/Camera baselines or duplicates safe insets, and no screenshot/CI visual PASS claim until reference measurements and runtime evidence exist.
