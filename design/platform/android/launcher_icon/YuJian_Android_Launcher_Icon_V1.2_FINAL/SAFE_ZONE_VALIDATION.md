# Adaptive Icon Safe Zone Validation — V1.2 FINAL

## Frozen geometry
- Canvas: 1080×1080 px = 108×108 dp design canvas
- Guaranteed safe zone used for validation: centered 660 px diameter = 66 dp
- V1.2 foreground scale relative to V1.1: 0.85
- Final overall foreground alpha bbox: (243, 272, 842, 768)
- Primary fish component bbox: (243, 381, 583, 285)
- Primary fish maximum radius from icon center: 318.93 px
- Allowed guaranteed-safe radius: 330.00 px
- Fish safe-zone result: PASS

## Decorative elements
The two gold corner marks are intentionally treated as non-critical decoration. They may extend into the outer 18 dp effect/masking area. The core fish identity remains inside the guaranteed safe zone.

## Background gate
PASS: background is a true full-square scene rebuilt independently of the foreground. It contains no baked fish silhouette, corner-mark silhouette, final rounded mask, or outer icon shadow.

## Monochrome gate
- Non-transparent RGB unique values: 1
- Expected: one color (white) + alpha geometry
- Result: PASS

## Preview gate
Generated masks:
- circle
- rounded square
- squircle
- 66 dp safe-zone overlay
