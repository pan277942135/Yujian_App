# Typography Android Mapping V3.1

This table maps each Design Token to Compose sp, FontFamily and FontWeight. It documents the current source mapping; it does not modify Kotlin.

| Design Token | Size / line height | FontFamily | FontWeight | Color |
|---|---|---|---|---|
| type.display | 30sp / 36sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.deep_lake_blue |
| type.headline | 28sp / 34sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.deep_lake_blue |
| type.title | 20sp / 26sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.deep_lake_blue |
| type.subtitle | 16sp / 24sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.deep_lake_blue |
| type.body | 16sp / 24sp | FontFamily.SansSerif; OS CJK fallback | Regular (400) | color.deep_lake_blue |
| type.caption | 12sp / 18sp | FontFamily.SansSerif; OS CJK fallback | Regular (400) | color.mist_blue_gray |
| type.button | 16sp / 20sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.lake_white |
| type.numeric | 22sp / 28sp | FontFamily.SansSerif; OS CJK fallback | Medium (500) | color.deep_lake_blue |
| type.label | 12sp / 18sp | FontFamily.SansSerif; OS CJK fallback | Regular (400) | color.mist_blue_gray |

## Mapping rules

- Convert each token size and lineHeight to Compose TextUnit.sp; never treat source raster px as device sp.
- Map Regular 400 → FontWeight.Normal, Medium 500 → FontWeight.Medium, SemiBold 600 → FontWeight.SemiBold, Bold 700 → FontWeight.Bold.
- Map the family ID to FontFamily.SansSerif. Android resolves CJK through its locale/script fallback chain. No named bundled face is pinned.
- Respect fontScale. Containers may grow and content may scroll; do not cancel scaling or silently shrink.
- Normal Home’s Font A path is a page override: use its width/density formula in NormalHomeTypography.kt. Do not substitute the shared Numeric size.
- The current brand calligraphic intent is unresolved. Do not claim a new font or license; keep D-007 open.

## Verification

For each role, compare the token’s sp size, lineHeight, weight and color reference with the table. Page exceptions must cite their authority. No Android build or runtime rendering is part of this mapping.