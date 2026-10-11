# Typography System V3.1 Final

**System status: REVIEW.** The shared role catalog and Android mapping are documented; C-007 / D-007 remains an owner decision, so this file does not claim Typography READY.

## Font family and provenance

| Script | Frozen mapping | Source | License / fallback |
|---|---|---|---|
| Chinese | Android platform SansSerif with OS script/locale CJK fallback | app/src/main/java/com/yujian/ai/ui/designsystem/typography/YuJianTypography.kt | No bundled font file or project license is present. Exact CJK face and vendor license are not pinned. |
| English | Android platform SansSerif | Same runtime mapping | Android platform supplies the face; fallback is OS-managed. |
| Digits | Android platform SansSerif | Same runtime mapping | No tabular-digit face is declared. Length, weight, date and statistics use Numeric unless a page authority overrides size. |

The platform mapping is evidence from the existing runtime source, not a typeface guessed from an image. Android OS resolves the concrete font face and CJK fallback. Other renderers need their own approved mapping.

## Weights and tokens

Regular 400, Medium 500, SemiBold 600 and Bold 700 are the only shared weight names. Token values, line height, tracking and semantic color references are in Typography_Tokens_V3_FINAL.json. Label is an alias of Caption and adds no new size scale.

## Page override boundary

Allowed overrides are limited to Brand Hero, Fish Species Name and Large Statistic. They may reference approved page authority for size/line height or frozen brand artwork. Family and weight remain shared. Brand Hero remains REVIEW until an owner supplies or approves the brand type source. No page may add a private family, fallback chain or private scale.

## Cross-page audit

| Page | Finding | Disposition |
|---|---|---|
| Empty Home | BRAND role says calligraphic; Android source maps SansSerif. | Conflict recorded; frozen image unchanged; D-007 remains open. |
| Normal Home | Font A values are reference px with an explicit conversion formula. | Preserve page override and use sp mapping; no global scale merge. |
| Recognition Result | Centered title and state-specific result typography. | Preserve registered page exception. |
| FishRecordDetail | A/B frozen design uses on-media roles and page hierarchy. | Shared family; page image remains visual authority. |
| Fish Guide | Species name has page hierarchy; large text requires content growth. | Shared family with page size override. |

## Acceptance

A consumer passes only when each text style names a token, resolves to the family catalog, maps its sp/weight/color, honors fontScale and references an explicit page override where applicable. A private page font or unregistered scale fails. This is a design contract; it does not claim device rendering was tested.

**Freeze decision:** Typography remains REVIEW until C-007 / D-007 resolves brand intent and platform fallback/license expectations.