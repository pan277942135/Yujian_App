# Empty Home — Frozen Visual Revision V2.3

Status: **APPROVED / FROZEN**

V2.3 is a narrow physical-device correction over V2.2. It overrides only the Empty Home bobber placement and the bobber's underwater rendering. Every other visual, motion, layout, and interaction rule inherits V2/V2.2 unchanged.

Reference coordinates use the Android runtime canvas **1080 × 1920**.

## Bobber open-water placement override

- Water-contact anchor: **(560, 1120)**.
- Bobber layer origin: **(548, 1036)**; retain its 24 × 122 source dimensions.
- Keep the contact point within the approved open-water band, y = 1100–1140.
- Ripple origin: **(441, 1079)**; ripple center: **(560, 1120)**.
- Fishing-line start remains the existing rod tip **(335, 1180)**.
- Fishing-line end: **(560, 1128)**, tucked just below the water-contact seam.
- Fishing-line cubic controls: **(390, 1218)** and **(470, 1090)**. Preserve one smooth, restrained curve.
- Preserve bobber Y-only motion at ±3 reference px over 4600 ms and the existing ripple timing/scale/alpha contract.

The bobber and ripple must remain aligned to the same water-contact point throughout the bobber motion cycle.

## No-ghost underwater treatment override

- Render the above-water crop of the bobber once.
- Do not render a second submerged bobber bitmap.
- Underwater visible height: **0 px**.
- Underwater alpha: **0**.
- Retain the narrow water-contact seam and one ripple beneath the bobber.

This override removes the long duplicate-looking submerged silhouette. The float must read as one visually continuous bobber at the waterline.

## Inherited unchanged

Hero artwork and geometry, header, background, grading, Camera CTA, prompt, album, rod origin/asset, all other motion timings, safe-area mapping, and all non-bobber behavior remain frozen to V2/V2.2.
