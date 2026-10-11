# Typography Android Mapping V3.1

This is a design-to-Android mapping record; no Kotlin or Compose source is changed by this contract.

| Design Token | Size / line height | FontFamily | FontWeight | Color |
|---|---|---|---|---|
| `type.display` | 30sp / 36sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.deep_lake_blue` |
| `type.headline` | 28sp / 34sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.deep_lake_blue` |
| `type.title` | 20sp / 26sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.deep_lake_blue` |
| `type.subtitle` | 16sp / 24sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.deep_lake_blue` |
| `type.body` | 16sp / 24sp | `FontFamily.SansSerif`; OS CJK fallback | Regular (400) | `color.deep_lake_blue` |
| `type.caption` | 12sp / 18sp | `FontFamily.SansSerif`; OS CJK fallback | Regular (400) | `color.mist_blue_gray` |
| `type.button` | 16sp / 20sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.lake_white` |
| `type.numeric` | 22sp / 28sp | `FontFamily.SansSerif`; OS CJK fallback | Medium (500) | `color.deep_lake_blue` |
| `type.label` | 12sp / 18sp | `FontFamily.SansSerif`; OS CJK fallback | Regular (400) | `color.mist_blue_gray` |

## Mapping rules

- `Design Token → sp → FontFamily → FontWeight`: use the registered `size` and `lineHeight` as Compose `sp`; map `android_platform_sans_serif` to `FontFamily.SansSerif`; map weights 400/500/600/700 to `FontWeight.Normal/Medium/SemiBold/Bold`.
- Android resolves CJK fallback by OS script and locale. Do not pin a vendor-specific face or add a bundled font without a separate approved source/license decision.
- Respect OS `fontScale`. Expand containers, reflow or scroll; never cancel scaling or silently shrink text.
- Normal Home Font A is page-owned. Apply its width/density conversion formula from `Normal_Home_Typography_Scaling_Contract_V1.md`; do not substitute the shared Numeric size or treat reference px as sp.
- Frozen calligraphic brand text prefers the original approved artwork. Normal Home's dynamic `渔见` remains a D-007 conflict until its source/asset is approved; do not guess from a screenshot.

## Verification

Consumers cite one token or page override and preserve weight, line-height, color reference and system scaling. Page exceptions cite their authority. This mapping documents policy and source; it is not runtime/device verification.
