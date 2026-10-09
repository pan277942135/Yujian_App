# Normal Home Typography Scaling Contract V1

This is a page-scoped implementation contract. It does not change shared
`YuJianTypography` values or the Frozen visual authority.

## Reference space

- Frozen reference canvas: 1080 × 1920 physical pixels.
- Reference width unit: one source pixel at 1080 px width.
- Compose geometry uses Android `Dp`; the responsive width scale is
  `usableContentWidthDp / 1080`.
- A reference geometry value `g` is rendered as `g × widthScale` dp.
- A reference text size `s` is rendered as
  `max(s × widthScale, minimumPhysicalFontPx / density)` sp.
- Android converts the resulting `sp` with both density and the user's system
  `fontScale`. The implementation never reads, resets, or cancels `fontScale`.
- With standard font scale and a 1080 px usable width, the physical text size
  remains the reference size at 160, 320, and 440 dpi. At other widths it follows
  the same width-first rule as card geometry. Minimum pixel floors prevent
  supporting text from becoming too small on narrow content widths.

The physical-size equation is:

```text
physicalFontPx = max(referenceSp × usableWidthPx / 1080,
                     minimumPhysicalFontPx) × fontScale
```

This equation follows from `usableContentWidthDp = usableWidthPx / density`;
density cancels when the width-scaled `sp` is converted to pixels. Font scale
remains an independent accessibility multiplier.

## Page text roles

| UI text | Existing reference size | Contract reference size | Minimum physical size | Layout rule |
|---|---:|---:|---:|---|
| 渔见 | 32 sp / 40 sp line | 32 sp / 40 sp line | 18 px | One line; ellipsis |
| Statistics value | 22 sp / 28 sp line | 22 sp / 28 sp line | 16 px | One line; ellipsis |
| Statistics label | 12 sp / 18 sp line | 12 sp / 18 sp line | 12 px | One line; ellipsis |
| 最近鱼获 | 24 sp / 30 sp line | 24 sp / 30 sp line | 16 px | One line; ellipsis |
| 全部 | 18 sp / 24 sp line | 18 sp / 24 sp line | 14 px | One line; ellipsis; 48 dp touch target |
| Hero species | 32 sp / 38 sp line | 32 sp / 38 sp line | 18 px | One line; ellipsis |
| Hero length and weight | 22 sp / 28 sp line | 22 sp / 28 sp line | 16 px | One line; ellipsis |
| Hero time and location | 16 sp / 22 sp line | 16 sp / 22 sp line | 14 px | One line; location may ellipsize |
| 记录下一条鱼 | 20 sp / 28 sp line | 20 sp / 28 sp line | 16 px | One line; ellipsis |

The reference text sizes at 1080 px are intentionally unchanged. The correction
is that text now follows the same reference-width conversion as page geometry
instead of remaining fixed in `sp` while the surrounding geometry shrinks on
higher-density devices.

## Measurement evidence

`NormalHomeDataParityTest` writes a `NORMAL_HOME_TEXT_LAYOUT` record for each
listed text role. Each record contains the actual `TextLayoutResult` font size,
line height, Compose density and font scale, effective physical font size,
text bounds, parent bounds, width ratio, measured text size, line count, and
overflow state. The test also asserts the text node remains within its measured
parent. Long location is allowed to set `visualOverflow=true` only after the
single-line ellipsis has retained its visible prefix.

Those runtime numbers must be taken from the AndroidTest log for the exact APK
and device profile. This contract contains no substituted or estimated runtime
measurements.
