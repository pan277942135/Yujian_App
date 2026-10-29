# NH01 Adaptive Visual Balance V1

**Task:** `YUJIAN_NH01_ADAPTIVE_VISUAL_BALANCE_V1`
**Validation branch:** `work/fish-image-smart-crop-v1` (existing Draft PR #170)
**Status:** Candidate rules implemented; final values remain pending the API 28 3×3 runtime matrix.

## Existing page contract

NH01 continues to use the Gemini native `Top / Middle / Footer` Compose structure in `GeminiNormalHomeContent.kt`, routed from `HomeScreen.kt`. The established card shape remains **247:293**. The previous V3 layout used a 68.6% track-width target capped at 337.2dp / 400dp, a 13dp visible title gap, and vertically centered the middle group when it fit. Its finite `HorizontalPager` has one page per stable `RemoteCatch.id`; `orderedHomeRecords()` removes duplicate IDs before the page count is created.

## V1 candidate rules under test

The policy is in `app/src/main/java/com/yujian/ai/ui/home/GeminiHomeAdaptiveLayoutPolicy.kt`. It consumes safe window dimensions in dp and the system `fontScale` supplied by Compose.

| Band | Predicate (safe window) | Width target | Maximum card width |
|---|---|---:|---:|
| Compact | width < 360dp **or** height < 700dp | 76% of available track | 360dp |
| Medium | width 360–399dp and height ≥ 700dp | 76% | 360dp |
| Expanded | width ≥ 400dp and height ≥ 700dp | 76% | 360dp |

All bands keep the existing **247:293** aspect ratio. If the measured Middle height limits the target card and at least 200dp of card width remains, the card shrinks proportionally. Below that readability floor, the Middle content scrolls and the Footer remains outside the scroll viewport. Content width remains capped at 560dp. These width, maximum-size, and band values are candidates until the captured runtime results are reviewed.

The carousel viewport is centered within the 560dp content cap. Neighbor width stays at least large enough to expose a 5% viewport sliver, bounded to 12–28dp and never wider than the main card; this compensates for the side-card scale when the main card reaches 360dp. The runtime gate runs the adaptive matrix independently after the existing Gemini instrumentation suite so that an unrelated legacy assertion cannot suppress the requested matrix evidence.

| Gap | Compact 1.0× / 1.3× / 1.5× | Medium 1.0× / 1.3× / 1.5× | Expanded 1.0× / 1.3× / 1.5× |
|---|---|---|---|
| Brand/header to stats | 16 / 14 / 12dp | 20 / 18 / 16dp | 24 / 20 / 18dp |
| Stats to recent-catch group | 20 / 18 / 16dp | 24 / 22 / 20dp | 26 / 22 / 20dp |
| Recent title to card | 12 / 11 / 10dp | 16 / 14 / 12dp | 18 / 16 / 14dp |
| Card to Footer region | 16 / 14 / 12dp | 20 / 18 / 16dp | 24 / 20 / 16dp |

System font scaling is left to Compose. Auxiliary measurement and metadata lines can wrap to two lines at 1.3× and above; card title, measurement values, CTA, and camera retain their normal `sp` sizing and interaction semantics.

## Two-record carousel contract

The data pipeline is `recentCatches → orderedHomeRecords() → HorizontalPager(pageCount = records.size, key = records[page].id)`. It does not clone records to fill preview positions. The runtime matrix injects exactly `nh01-catch-001` and `nh01-catch-002`, checks page count 2, checks one active card plus one distinct neighboring card, and swipes to verify the selected page maps to the second real ID.

## Runtime evidence

The API 28 Android instrumentation test invokes production `HomeScreen` on the emulator's real display and system font configuration. The runner sets each requested logical dp size by setting the emulator display override to the same pixel dimensions at 160 dpi, then sets system `font_scale` to 1.0, 1.3, or 1.5. The test asserts the resulting physical resolution, records the Compose-observed font scale and safe drawing Insets, obtains component `boundsInWindow`, and captures full-screen images with `UiAutomation.takeScreenshot()`.

Each of the nine profiles is captured once with `ADAPTIVE_V1` and once with `V3_BASELINE` under identical display and font settings. The runtime artifact provides the raw screenshots, JSON bounds and layout metrics, two-catch regression assertions, per-profile comparison images, and a visual acceptance report. A successful Android emulator result is labeled API 28 emulator runtime; it is not physical handset evidence.

## Finalization gate

Do not freeze the candidate numbers until all nine adaptive runtime cases pass: exact requested resolution and font scale, card ratio within 1%, centered card bounds, visible and separated CTA/camera, safe-bottom clearance, and no duplicated catch ID. Review the paired V3/adaptive captures for all nine conditions. Any failing case must be fixed and the affected runtime cases rerun before changing this document's status to frozen.
