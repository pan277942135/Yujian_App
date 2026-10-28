# Normal Home — Final Visual Anchors, Background and Avatar Authority V1
Status: FROZEN_ENGINEERING_INTERPRETATION / OPTICAL_THRESHOLDS_REPRODUCIBLE / ANDROID_PARITY_UNVERIFIED
Date: 2026-10-10. Scope Normal Home only. No Frozen raster is modified.

## Original-source audit

Original NH01 Frozen asset: `design/system/core_visual_v1/reference/normal_home_v1.png`
Physical size: 1080x1920. Exact SHA-256:
`6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`.

Its **actual binary** was decoded inside GitHub Actions at [Run 38020083123](https://github.com/pan277942135/Yujian_App/actions/runs/38020083123) using `scripts/design/measure_normal_home_frozen_roi_v1.py`. This is source evidence, not a screenshot guessed from a JPEG.

The decoded reference pixel observations are:
- CTA white/near-white ink in ROI x400..680, y1492..1571: y **1527..1558** for thresholds 165–210 (RGB min and near-neutral chroma, >=3 row pixels); at stricter 225 threshold y1528..1557. Verified full-mask bbox x433..646 (inclusive) under the exact 210 RGB threshold (934 pixels).
- Camera high-contrast near-white core in ROI x430..650,y1560..1810: significant rows **1590..1773** (RGB >=210, near-neutral, >=12 row pixels).
- Camera warm-gold pixels in same near-center ROI: significant rows **1593..1789** (R−G>=18,G−B>=5,R>=125,G>=105,>=6 row pixels); 12/18/25 channel deltas all yielded same row bounds at this sample.
- Combined salient camera evidence: y **1590..1789**, explicitly a thresholded **high-contrast/visible-pixel candidate**, not a claim to include every faint shadow or subpixel reflection.
- Verified **full two-dimensional original-pixel mask rectangles** (inclusive): CTA near-white **(433,1527)–(646,1558)** (934 pixels); Camera core near-white **(450,1590)–(628,1773)** (19670 pixels); Camera warm gold **(439,1593)–(638,1789)** (2123 pixels). These are saved as structured `normal_home_visual_pixel_targets_v1.json` and reproduced by [Run 38020447518](https://github.com/pan277942135/Yujian_App/actions/runs/38020447518).
- Full per-row tabulation and threshold method are reproducible in the immutable-source ROI script; results are sensitive to mask threshold and source antialiasing, so use the specified mask and report any threshold sensitivity rather than elevating a single sampled pixel to a universal boundary.

## Anchor type reconciliation (no invented equivalence)

| Source/value | Actual semantic type | Rule |
|---|---|---|
| NH05 CTA y1504 | historic shorthand graphic/text reference | NOT a measured CTA layout container top; retire as runtime box assertion |
| Android CTA y1495, w420 h58 | current container implementation | runtime implementation reference ONLY; visual ink must match source y1527..1558 at reference profile |
| NH01 CTA ink y1527..1558; x approx433..647 | source visible glyph mask | normative optical target under stated ROI and threshold method |
| NH05 Camera y1582 | historic nominal decorative/visual placement shorthand | NOT known to be the 208px hit box top; cannot use as touch or white-core bbox assertion |
| Android Camera y1564, 208x208 | current outer touch box implementation | runtime reference ONLY; require semantics hit rect >=48dp and cover visible interactive art without obstructing neighboring CTA |
| Camera visual child y1568 from current code | current raster child frame position | current implementation math, not a source optical measurement |
| Camera source near-white y1590..1773 | high-contrast core mask | normative measured candidate ROI target at reference profile |
| Camera source salient gold y1593..1789 | gold mask | normative measured candidate ROI target at reference profile |

**Resolution:** previous y1495 vs y1504 and y1564 vs y1582 did not describe the same kind of rectangle; stop selecting an arbitrary winner. Distinguish `ctaContainer`, `ctaInk`, `cameraTouch`, `cameraVisualFrame`, `cameraHighContrastCore`, `cameraGoldRim`. Work must compare Frozen source ink to Android screen ink using the same mask and transformed reference position, and touch box to Android semantics—not to source pixels. Exact optical parity of the current APK is NOT established. If runtime visible art extends outside the clickable camera bounds, enlarge/reposition the interaction target without changing the source graphic.

## Background — one execution stage

- **Final runtime bitmap:** `app/src/main/assets/normal_home_runtime_v1/static/scene_base.png`, exact byte-equivalent to `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` (SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`, 941x1672).
- Rendering operation: **single centered ContentScale.Crop** filling edge-to-edge *window*. No re-encoding, per-device saturation/contrast/brightness processing, overlay tint, or global blur is authorized in Normal Home runtime.
- Background System `BG_ENV_HERO` style numbers (veil0.02, saturation0.98, contrast0.98, brightness1.00) are **treatment-family provenance/design-target metadata** in this current usage, not additional Android postprocess instructions applied over this already-approved immutable pixel master. Do not assume without evidence that a given numeric operation was historically 'baked in'; visual source SHA is the runtime authority regardless.
- This page-level runtime clarification does not revise the shared Background System's other pages or numeric metadata. Any future new mastered background/treatment pipeline requires a versioned shared change.
- Source bitmap cropping shall not be copied from an NH01 page screenshot; Empty Home sunrise source prohibited.

## Avatar — later approved Home V2 asset, shared semantics and Guest separation

There is a time-ordered authority conflict: the 2026-09-30 Shared Default Profile Avatar V1 contract specified a reusable person-outline fallback, while later Normal Home-specific code + raster were committed in `fefb95953d17` on 2026-10-09 (`fix(home): restore Frozen typography scale and Normal Home avatar`). A later page-specific **approved visual identity for Normal Home** supersedes the older shared fallback's *Home optical artwork* without changing the shared fallback's account-state semantics elsewhere.

1. **Signed-in + loadable remote**: real account avatar; circular crop; tap Profile.
2. **Signed-in + no media/loading/failure**: Normal Home-specific frozen V2 optical bitmap `app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png` (SHA-256 `fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528`, 1254×1254); tap Profile. This is the later Home visual authority. Other pages still follow Shared Default Profile Avatar V1 unless separately versioned.
3. **Guest**: independent `app/src/main/assets/normal_home_runtime_v1/avatar/guest_avatar.png` (Git blob `f58549babb41aaedb5ee2590e6d48bceda19d05a`); tap Login/Register. Guest must NOT reuse the signed-in V2 fallback image if doing so visually conflates identity.
4. The older shared `app/src/main/res/drawable/profile_fallback_v13.xml` remains a cross-page V1 reference and historical fallback, not a reason to replace the later signed-in Normal Home V2 PNG. The original immutable V2 raster is retained and never regenerated.

**Implementation mismatch to fix:** current NormalHomeContent.kt renders `normal_home_default_avatar_v2` in both Guest and signed-in fallback branches, and the older V2 manifest also mapped Guest to it. The Home-specific source mapping is now resolved as above; actual Android runtime still requires code changes and screenshot validation. Semantic distinction (Guest vs signed-in) derives from the shared contract, while the 2026-10-09 V2 bitmap determines the later page-specific optical fallback. This is a documented chronological exception; it does not authorize uncontrolled page-level forks elsewhere.

## Test and status

Optical source measurements: SOURCE_VERIFIED via GitHub Actions. Android visual parity: NOT_RUN/REVIEW_REQUIRED at exact target APK SHA until full capture. Assert both normal and reduced-motion neutral frames and classify ring/glow shadow separately. Never claim touch geometry from a flat PNG. No new sound, haptic or motion authorized.
