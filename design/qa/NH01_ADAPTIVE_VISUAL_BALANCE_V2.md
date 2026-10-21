# NH01 Adaptive Visual Balance V2 — Candidate Contract V2.1

**Task:** `YUJIAN_NH01_CONTENT_FIRST_LAYOUT_V2`  
**Implementation:** `GeminiHomeAdaptiveLayoutPolicy.kt` + `GeminiNormalHomeContent.kt`  
**Status:** Candidate only. Runtime acceptance requires the exact-SHA 24-execution Android API 28 matrix.

## Frozen decisions for this candidate

1. **Main card:** maximize within the reasonable design cap; do not shrink it simply because a screen is larger or taller.
2. **Side Peek:** preserve left and right neighbor previews whenever geometry permits, without overlapping the active card.
3. **320×480:** compact mode compresses nonessential whitespace first and preserves readable content, the camera touch target, and Footer CTA.

## Geometry and sizing algorithm

- Use `dp` for layout geometry, `sp` for type, and actual Android window bounds for evidence. Apply each system inset exactly once.
- Preserve the Hero aspect ratio **247:293**.
- Main-card target width is `min(min(trackWidth, 560dp) × 0.70, 337.2dp)`; target height follows the ratio and is capped at 400dp.
- Measure the real Top and Footer sections first. Compress nonessential gaps before reducing the card. Resolve final card height as `min(desiredHeight, max(minReadableHeight, availableCardHeight))`; keep the Footer outside any Middle scroll container.
- Standard readable-height floor is **148–220dp**. The existing **124dp** exception is allowed only for 320×480dp at 1.0× font scale and only if the measured card width is at least 145dp, required text is not visually truncated, and Middle does not scroll.
- Viewport type multiplier remains bounded to **1.0–1.2×** and must be monotonic across 320×480, 360×640, 390×844, and 430×932 at the same system font scale. The runtime report records computed sp sizes for statistics, section title/action, card title/measurement/metadata, and CTA; no recorded font size or main-card width may decrease as viewport size grows at a fixed system font scale. Respect Android system font scaling.
- Adaptive V2 allows species title, measurement, and metadata to wrap to two lines when the target card width is below **240dp**, including at 1.0× system font scale. V3 Baseline retains its prior line policy as the control.
- The neighbor width cap is **82%** of the active card. For Adaptive V2, target edge-peek width is `clamp(trackWidth × 5%, 12dp, 28dp)`. Increase neighbor width only as needed to expose that edge width. If the 82% cap makes an edge sliver impossible, require the neighbor to remain visibly present inside the track by at least `min(12dp, actualNeighborWidth)`. Never overlap the active card. Preserve the 15dp top offset and 66.7% neighbor height.
- Keep the content-width cap at 560dp. Camera artwork follows `min(min(safeWidth, 560dp) × 0.18, safeHeight × 0.10, 80dp)`, with a 56dp visual floor and a separate touch target of at least 72dp.

## Exact-SHA runtime matrix

Run both **Adaptive V2** and **V3 Baseline** on the same source SHA:

- 320×480dp, 360×640dp, 390×844dp, 430×932dp.
- System font scale 1.0×, 1.3×, 1.5×.
- **12 cases per variant, 24 executions total.** Each case records the exact SHA, applied device settings, full-window PNG, component bounds, card dimensions, Peek geometry, text-overflow status, CTA/camera geometry, and two-catch pager interactions.
- Capture initial right-Peek bounds and after-swipe left-Peek bounds. The aggregator must reject missing evidence instead of treating a missing report as PASS.
- Device output and host aggregation must use the same versioned directory name. Browser simulation is not Android runtime evidence.

## Acceptance gates

- Card ratio within 1% of 247:293; active card centered within 2dp; measured width never exceeds its variant's target.
- No active/Peek overlap beyond 1dp; neighbor width no greater than 82%; real visible intersection is measured. Adaptive V2 must expose the target edge Peek when geometrically feasible.
- CTA and camera do not overlap; camera touch target at least 72dp; camera artwork matches its sizing policy within 2dp; Footer and camera remain above the actual bottom safe inset.
- Adaptive V2 species, measurement, and metadata text must not visually overflow. Required content must remain accessible.
- 320×480dp at 1.0× must have no Middle scroll viewport, active-card width at least 145dp, and no excessive blank area below the CTA.
- Compare all 12 same-profile V3/V2 screenshot pairs and both variants' bounds/metrics before selecting the default. A successful build or browser preview is not visual acceptance.

**No merge, release PASS, or Frozen status is implied by this candidate contract.**
