# Normal Home — Final Visual Anchors, Background and Avatar Authority V1
Status: FROZEN_ENGINEERING_INTERPRETATION / OPTICAL_THRESHOLDS_REPRODUCIBLE / ANDROID_PARITY_UNVERIFIED
Date: 2026-10-10. Scope Normal Home only. No Frozen raster is modified.

## Original-source audit

Original NH01 Frozen asset: `design/system/core_visual_v1/reference/normal_home_v1.png`
Physical size: 1080x1920. Exact SHA-256:
`6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`.

Its **actual binary** was decoded inside GitHub Actions at [Run 38020083123](https://github.com/pan277942135/Yujian_App/actions/runs/38020083123) using `scripts/design/measure_normal_home_frozen_roi_v1.py`. This is source evidence, not a screenshot guessed from a JPEG.

The decoded reference pixel observations are:
- CTA white/near-white ink in ROI x400..680, y1492..1571: y **1527..1558** for thresholds 165–210 (RGB min and near-neutral chroma, >=3 row pixels); at stricter 225 threshold y1528..1557. Existing V1.1 Frozen glyph optical X range x433..647.
- Camera high-contrast near-white core in ROI x430..650,y1560..1810: significant rows **1590..1773** (RGB >=210, near-neutral, >=12 row pixels).
- Camera warm-gold pixels in same near-center ROI: significant rows **1593..1789** (R−G>=18,G−B>=5,R>=125,G>=105,>=6 row pixels); 12/18/25 channel deltas all yielded same row bounds at this sample.
- Combined salient camera evidence: y **1590..1789**, explicitly a thresholded **high-contrast/visible-pixel candidate**, not a claim to include every faint shadow or subpixel reflection.
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

## Avatar — source identity and routing

Authoritative semantic/visual fallbacks are OWNED by `design/system/components/profile_avatar_v1/default_profile_avatar_contract.json` (Shared Default Profile Avatar V1):
1. Signed-in + valid remote photo: sanitized user avatar media; circular crop, same visual/touch geometry.
2. Signed-in + no avatar, loading with no valid image, or load failure: canonical shared **Default Profile Avatar V1** person-outline fallback, implementation reference `app/src/main/res/drawable/profile_fallback_v13.xml`. User may approve a shared asset revision, but a page-local PNG is not an implicit override.
3. Guest: **Guest Account Entry** independent identity, source asset `app/src/main/assets/normal_home_runtime_v1/avatar/guest_avatar.png` (Git blob `f58549babb41aaedb5ee2590e6d48bceda19d05a`); tap Login/Register, not Profile. Guest appearance must be optically different from logged-in fallback.
4. Source `app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png` (SHA-256 `fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528`) is preserved as a **page-specific legacy/alternative raster**, not promoted above canonical Shared Default Profile Avatar V1 merely because code currently references it. Do not delete bitmap or claim it is product-authoritative without a versioned shared identity revision.

**Observed implementation inconsistency:** some current NormalHomeContent.kt branches render `normal_home_default_avatar_v2` for both Guest and signed-in fallback. This violates the canonical identity distinction and is a Work A code issue, not a reason to recreate the shared avatar design. Build/CI alone cannot establish the corrected runtime pixels; acceptance compares logged-in vs guest actual captures.

## Test and status

Optical source measurements: SOURCE_VERIFIED via GitHub Actions. Android visual parity: NOT_RUN/REVIEW_REQUIRED at exact target APK SHA until full capture. Assert both normal and reduced-motion neutral frames and classify ring/glow shadow separately. Never claim touch geometry from a flat PNG. No new sound, haptic or motion authorized.
