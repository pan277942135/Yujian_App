# Typography System V3.1 Final

**System status: READY.** This document defines Platform Typography for dynamic UI and the separate Brand Typography artwork policy. The canonical token source is `Typography_Tokens_V3_FINAL.json`.

## Platform font family, source and fallback

| Script/content | Frozen mapping | Source | License and fallback |
|---|---|---|---|
| Chinese UI | Android platform SansSerif | `YuJianTypography.kt` (`FontFamily.SansSerif`) | No app-bundled font or vendor face is pinned. Android resolves CJK by script/locale and applies OS fallback. |
| English UI | Android platform SansSerif | `YuJianTypography.kt` (`FontFamily.SansSerif`) | Platform supplied; missing glyphs use OS fallback. |
| Digits / measurements / dates / statistics | Android platform SansSerif; `type.numeric` by default | Platform source and registered page override where applicable | No tabular-digit face is declared. Numeric formatting stays page/data-owned; font scale is honored. |
| Brand wordmark / special brand lettering | `FROZEN_ARTWORK_ASSET` | `system_core_visual_v1_reference_normal_home_v1_png`, `design/system/core_visual_v1/reference/normal_home_v1.png`, SHA-256 in Typography token registry | `dynamic_text=false`, `font_dependency=none`; no font license or font file is needed. |

Brand art remains the exact registered Frozen Artwork Asset authority. Do not extract/crop the frozen page raster, redraw the glyphs, introduce a calligraphic font, or simulate the brand in Android text. Page-specific placement remains for Phase 3.

## Font weights and token roles

The platform weight catalog is Regular 400, Medium 500, SemiBold 600 and Bold 700. Ten tokens cover Display, Headline, Title, Subtitle, Body, Body Small, Caption, Button, Numeric and Label. Label is a naming alias of Caption, not a second scale. Exact `sp`, line-height, tracking, color token and usage live in the canonical JSON.

## Page override policy

Allowed and registered:

- Brand Hero / 渔见 wordmark: reference only the frozen artwork contract; it is not dynamic text.
- Fish Species Name: the cited page authority may set size and line height while retaining the platform family and shared weight catalog.
- Normal Home Large Statistic: use its registered Font A physical-reference-pixel conversion and keep OS `fontScale`.
- Recognition Result title: use the registered centered-title exception.
- Recognition Processing subtitle: use type.body_small (14sp, Regular, secondary); no font family or page-private scale is added.

Pages may not add private font families, select brand fonts, add unregistered fallback chains or size scales, use unregistered tracking, silently shrink, or cancel `fontScale`. Existing Frozen page authority wins where it owns geometry.

## Cross-page audit

| Page | Finding | Disposition |
|---|---|---|
| Empty Home | Frozen brand composition remains authoritative; no typeface is inferred from pixels. | Use Brand Artwork Asset policy; page placement stays scoped. |
| Normal Home | Font A is a registered page override; wordmark no longer has a dynamic-font role in the design contract. | Platform text and brand artwork are separated; runtime/page geometry is not certified here. |
| Recognition Result | Centered title and state hierarchy have page authorities. | Preserve the registered page override. |
| Recognition Processing | Frozen subtitle is 14sp secondary. | Use shared type.body_small through the registered processing.subtitle page override. |
| FishRecordDetail | Shared family; media text hierarchy and A/B visuals remain page-scoped. | No private family. |
| Fish Guide | Species title sizing is page-owned; larger text grows/reflows. | Preserve page size override and OS scaling. |

## Android mapping and acceptance

Mapping is `Design Token → sp → FontFamily → FontWeight`: use registered size/line-height as `sp`; map `android_platform_sans_serif` to `FontFamily.SansSerif`; map 400/500/600/700 to Normal/Medium/SemiBold/Bold. OS resolves CJK fallback by script/locale. Honor OS `fontScale`; expand containers, reflow and scroll. The Normal Home Font A override uses its own registered reference-pixel formula. Brand lettering maps to the frozen artwork identity and has no dynamic font dependency.

Acceptance: every UI text consumer names one token or registered page override; every token resolves its family, weight, line-height, letter spacing and Color ID; no unregistered family or page scale; fontScale remains honored. This is design-contract acceptance, not device-render acceptance.

## D-007 decision record

**RESOLVED.** Dynamic text uses Platform Typography. Brand Typography uses the registered Frozen Artwork Asset (`dynamic_text=false`, `font_dependency=none`). No new calligraphic font, simulated brand face, page-selected font, or logo redraw is allowed. Frozen artwork bytes were not changed.
