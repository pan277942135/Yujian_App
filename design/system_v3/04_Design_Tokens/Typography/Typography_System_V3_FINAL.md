# Typography System V3.1 Final

**System status: REVIEW.** The platform text policy is frozen for ordinary UI text. The calligraphic brand role remains an explicit owner blocker, so Typography is not READY.

## Font family, source and fallback

| Script/content | Frozen mapping | Source | License and fallback |
|---|---|---|---|
| Chinese UI | Android platform SansSerif; Android resolves CJK by script and locale | `app/src/main/java/com/yujian/ai/ui/designsystem/typography/YuJianTypography.kt` | No bundled typeface or repository license artifact. The exact vendor face is intentionally not pinned; OS fallback supplies missing glyphs. |
| English UI | Android platform SansSerif | Same runtime mapping | Supplied by Android platform; missing glyphs use OS fallback. |
| Digits | Android platform SansSerif | Same runtime mapping | No tabular-digit face is declared. Length, weight, date and statistics use `type.numeric`, subject to an explicit page override. |
| Calligraphic brand | Frozen original approved brand artwork where available | Core Visual brand role and page Frozen authorities | No approved dynamic font source/license or replacement asset is registered. Normal Home's dynamic `渔见` is mapped to SansSerif and conflicts with the calligraphic role. D-007 remains open for this role. |

The family policy above records the repository's existing platform mapping; it does not claim an exact device face, font binary, license grant, or cross-platform face. Never infer a font from an image or fetch a candidate font during implementation.

## Weights and shared roles

Shared weights: Regular 400, Medium 500, SemiBold 600, Bold 700. The nine token rows (Display, Headline, Title, Subtitle, Body, Caption, Button, Numeric and Label) are in `Typography_Tokens_V3_FINAL.json`. Label is a naming alias of Caption, not a separate size scale.

## Page override policy

Allowed and registered:

- Brand Hero: frozen brand artwork may supply the lettering. Dynamic brand text requires an approved face/asset decision; no new font may be inferred.
- Fish Species Name: page authority may set size and line-height; shared family/weight remain in force.
- Normal Home Large Statistic: use the page-owned Font A reference-pixel conversion contract; keep `fontScale` enabled.
- Recognition Result title: use its registered centered-title exception.

Pages may not add private font families, fallback chains, unregistered size scales, unregistered tracking, or shrink/cancel font scaling. Container growth, reflow and scrolling handle larger text. Existing page authority wins when a frozen visual differs; log the conflict rather than rewriting the visual.

## Cross-page audit

| Page | Evidence and finding | Disposition |
|---|---|---|
| Empty Home | Frozen visual/copy remains authority; no typeface is inferred from PNG. | Frozen artwork preferred; no new family. |
| Normal Home | Font A uses the page's width/density formula and OS `fontScale`. `渔见` is dynamic text using SansSerif despite calligraphic brand intent. | Numeric/statistic override mapped; brand conflict stays open. |
| Recognition Result | Centered title and state typography have page authorities. | Preserve registered page exception. |
| FishRecordDetail | Shared family; media text and hierarchy remain page-scoped; A/B Frozen visuals remain authority. | No private family added. |
| Fish Guide | Species name has a page-owned size hierarchy; larger text grows/reflows. | Preserve the page size override. |

## Android mapping and acceptance

`Design Token → sp → FontFamily → FontWeight` is documented in `Typography_Android_Mapping_V3.md`. Convert token size and line height to Compose `sp`; map the family ID to `FontFamily.SansSerif`; map weights 400/500/600/700 to Normal/Medium/SemiBold/Bold. OS resolves CJK fallback by script/locale. `fontScale` remains enabled. Normal Home Font A uses its registered page formula, not the shared Numeric size.

Acceptance: every consumer references a token or an explicitly registered page override, resolves family/weight/color, retains system font scaling, and preserves the page authority. A private font/scale, silent shrink, or canceled scaling fails. This contract does not claim device rendering was tested.

## D-007 decision record

**Platform mapping policy: RESOLVED. Typography system gate: REVIEW.** Ordinary Chinese, English and numeric text uses platform SansSerif with OS script/locale fallback; no vendor typeface is pinned; frozen calligraphic text prefers original approved artwork. The dynamic Normal Home wordmark remains unresolved because the current SansSerif rendering conflicts with the declared brand role and no approved source/asset decision exists. No Frozen visual or runtime code is changed.
