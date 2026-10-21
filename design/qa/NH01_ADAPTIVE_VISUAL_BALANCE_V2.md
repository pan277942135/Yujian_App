# NH01 Adaptive Visual Balance V2 — Content-First Layout Contract

**Task:** `YUJIAN_NH01_CONTENT_FIRST_LAYOUT_V2`  
**Implementation:** `GeminiHomeAdaptiveLayoutPolicy.kt` + `GeminiNormalHomeContent.kt`  
**Status:** Candidate implementation; exact-SHA Android API 28 matrix is required before acceptance.

## Decision

V2 replaces Adaptive V1 as the default policy. V1 remains in the enum only for explicit regression comparison; it is not the default or acceptance candidate. V3 Baseline remains the same-device comparison arm.

The root defect in V1 was not merely its spacing values. When the measured Middle height fell below the large fixed readability floor, the implementation restored the full desired card height. That created the opposite of responsive behavior: the tighter the viewport, the more likely the card was to force an oversized scroll region. V2 never restores an oversized target when space is constrained.

## Geometry contract

- Preserve the approved Hero card ratio **247:293**.
- Main-card target width is **70% of the carousel track**, capped at **337.2dp**. The final width may be smaller because height is constrained by the actual Middle region.
- Resolve final card height as `min(desiredHeight, max(minReadableHeight, availableCardHeight))`.
- Compute a conservative readable minimum from the font-scaled text budget: species title, up to two measurement lines, up to two metadata lines, footer padding, and font-metric safety allowance. Bound the minimum to **148–220dp**.
- If the available Middle space can contain the target card and its header/gaps, center the group vertically. If the readable minimum cannot fit, keep the card at that minimum (unless the viewport itself forces a smaller target) and enable scrolling **inside Middle only**. The Footer remains outside that scroll container.
- Never shrink text below the system-provided Compose font scale to make the layout appear to fit.
- Keep the existing safe-drawing inset contract, finite pager identity, and 560dp content-width cap. Camera artwork scales from both usable canvas width and height: `min(min(safeWidth, 560dp) × 0.18, safeHeight × 0.10, 80dp)`, with a 56dp visual floor. The camera touch target is `max(72dp, visualSize)`; visible artwork and touch bounds are measured independently.

## Continuous spacing policy

The V2 policy no longer uses a nine-cell band × font-scale lookup table. It calculates bounded spacing from continuous font pressure and short-height pressure:

- `fontPressure = clamp((fontScale - 1) / 0.5, 0, 1)`
- `shortHeightPressure = clamp((620dp - safeHeight) / 220dp, 0, 1)`

| Gap | Base rule | Font-pressure reduction | Short-height reduction |
|---|---|---:|---:|
| Brand → stats | clamp(safeHeight × 0.035, 14, 28) | 3dp × pressure | 2dp × pressure |
| Stats → section title | clamp(safeHeight × 0.015, 6, 14) | 3dp × pressure | 1.5dp × pressure |
| Section title → card | clamp(safeHeight × 0.014, 7, 12) | 2dp × pressure | 1.5dp × pressure |
| Card → footer region | clamp(safeHeight × 0.018, 8, 14) | 3dp × pressure | 2dp × pressure |

Each final gap has a non-negative lower bound in code. The screen band remains diagnostic metadata; it does not select a separate spacing table in V2.

## Required runtime matrix

The existing API 28 device matrix must run V2 and V3 Baseline for the same nine combinations:

- 320 × 640dp, 393 × 852dp, 411 × 915dp
- System font scale 1.0×, 1.3×, 1.5×

For each exact source SHA, preserve full-screen PNGs, `boundsInWindow`, actual density/resolution, observed font scale, card ratio/target-vs-measured size, scrollability, footer visibility, camera touch bounds and separate visible-art bounds, and two-catch pager identity evidence.

## Acceptance gates

- No CTA/camera overlap; camera target remains at least 72dp. Visible camera artwork matches the width/height-aware size policy within 2dp and increases across the compact, phone, tall-phone and wide-canvas profiles until the 80dp cap.
- Footer stays outside the Middle scroll viewport and above the actual bottom safe inset.
- Card ratio remains within 1% of 247:293; measured width never exceeds the V2 target.
- When the minimum readable card does not fit, V2 reports/uses Middle scrolling instead of reverting to the oversized desired height.
- Required fish metadata remains reachable and visible after scrolling; no text is silently clipped.
- Compare all nine paired V2/V3 screenshots before selecting a final default. Browser preview success alone is not Android runtime acceptance.

**No merge or release PASS is implied by adding this contract.**
