# Normal Home Typography Scaling Contract V1 — V1.1 Unit Correction

Status: **PAGE-SCOPED IMPLEMENTATION CONTRACT**
Visual authority: `design/system/core_visual_v1/reference/normal_home_v1.png`
Frozen SHA-256: `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`

This correction does not change the Frozen PNG, shared `YuJianTypography`,
page anchors, Empty Home, or other pages.

## V1.1 correction history

The initial V1 contract incorrectly called existing numeric `sp` values
reference pixels. The implementation divided those numbers by the width scale
using `usableWidthDp / 1080`, even though 1080 is a physical-pixel reference.
At 1080 physical pixels and density 3, a former 32 sp value became 10.67 sp
and rendered at about 32 px. The prior fixed 32 sp code would instead render at
96 px on that density. Neither is a valid substitute for measuring the Frozen
raster: the brand's visible raster glyph height is about 63 px.

V1.1 redefines each role in physical reference pixels and derives its Android
`sp` size from the actual usable viewport. It does not apply a uniform scale to
all text or cancel the user's accessibility font scale.

## Coordinate and unit contract

- Frozen canvas: **1080 × 1920 physical pixels**.
- `usableWidthPx = usableWidthDp × density`.
- `widthScale = usableWidthPx / 1080`.
- Reference geometry `gPx` becomes `gDp = gPx × usableWidthDp / 1080`.
- Reference text `fPx` becomes `fontSizeSp = max(fPx × widthScale, minimumFontPx) / density`.
- Compose then renders `effectiveFontPx = fontSizeSp × density × fontScale`.

Equivalently:

```text
effectiveFontPx = max(referenceFontPx × usableWidthPx / 1080,
                      minimumPhysicalFontPx) × fontScale
```

Density appears once in the conversion from viewport dp to physical px and once
in Android's sp-to-px conversion; it cancels in the width-scaled font size.
At a 1080 px usable width and `fontScale = 1`, each role resolves to its
reference-pixel size at density 1, 2, 2.75, or 3. The system `fontScale` remains
an independent multiplier at 1.0, 1.15, and 1.3.

## Frozen raster measurements and page roles

Visible glyph boxes below were measured on the Frozen PNG with page-specific
regions and contrast masks. They are raster bounds, not runtime Compose
`TextLayoutResult` values. `referenceFontPx` is the page role size selected to
match those visible glyph bounds; Android runtime layout remains for Validation
Work to verify on the target profiles.

| UI role | Prior V1.1 input (treated as reference sp) | Frozen visible glyph bounds (x,y; px) | Visible height | Reference font / line (px) | Minimum font (px) | Layout |
|---|---:|---:|---:|---:|---:|---|
| 渔见 | 32 | (103,125)–(242,188) | 63 px | 72 / 80 | 18 | One line; ellipsis |
| Statistics values | 22 | Combined values: (262,326)–(809,356) | ≈30 px | 36 / 42 | 16 | Three equal groups; one line |
| Statistics labels | 12 | Combined labels: (257,378)–(850,401) | ≈23 px | 28 / 32 | 16 | One line |
| 最近鱼获 | 24 | (109,515)–(282,555) | 40 px | 48 / 52 | 18 | One line; ellipsis |
| 全部 | 18 | (867,521)–(971,554) | 33 px | 40 / 44 | 16 | One line; ellipsis; 48 dp target |
| Hero species | 32 | (228,1276)–(334,1325) | 49 px | 56 / 64 | 18 | One line; ellipsis |
| Hero measurement | 22 | (227,1348)–(564,1388) | 40 px | 46 / 52 | 16 | One line; ellipsis |
| Hero time/location | 16 | (232,1408)–(684,1440) | 32 px | 36 / 42 | 16 | One line; long location ellipsizes |
| 记录下一条鱼 | 20 | (433,1527)–(647,1559) | 32 px | 40 / 44 | 16 | One line; ellipsis |

The floors apply in physical pixels before conversion to sp. They preserve
legibility on narrow physical viewports; accessibility scaling is applied
afterwards by Android.

## Spacing and unchanged geometry

| Page element | Frozen/reference measurement | Implementation reference px | Notes |
|---|---:|---:|---|
| Recent header left/right inset | Title begins at x≈109; action ends at x≈971 | 108 | Corrects the previous 32 px inset |
| Statistics top/bottom breathing room | Glyph range y326–401 within y304 section anchor | 17 | Together with 42+8+32 px text rows, keeps the group within the 116 px statistics region |
| Statistics value-to-label gap | Raster gap ≈22 px with line-box leading included | 8 | Independent of font role sizes |
| Hero metadata row gap | Visible row starts y1276, 1348, 1408 | 20 | Corrects the previous 5 px inter-row padding |
| Pager page gap | Existing implementation value | 16 | Retained; exact runtime page-peek parity remains for Validation Work |
| Hero footer horizontal inset | Card x=170; title x=228 | 56 | Preserved |
| Hero footer bottom inset | Card bottom≈1476; final glyph ends≈1440 | 32 | Preserved |

The established NH05 anchors remain unchanged: header `(88,104,904,104)`;
statistics `y=304`; recent header `(y=494,h=70)`; Hero `(x=170,y=596,w=740,h=880)`;
CTA `y=1495` in the existing implementation; camera `y=1564`. Responsive width
scaling and the shared vertical-offset rule remain as specified in
`README.md`. The Hero photo still uses `ContentScale.Crop`, full card media
bounds, and the existing shared rounded clip.

## Verification boundary

JVM tests verify the unit conversion, widths 360/393/480 dp, densities
1/2/2.75/3, font scales 1/1.15/1.3, minimum floors, spacing conversion, and
avatar state mapping. They do not prove Compose glyph bounds or visual parity.
Instrumentation diagnostics retain the role reference px and log actual
`TextLayoutResult`, density, font scale, node/parent bounds, and visible glyph
bounds when Validation Work runs. No runtime values are asserted here.
