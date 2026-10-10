# Normal Home — Quantitative Visual Parity and Independent Verdict V1
Status: ACTIVE_MEASUREMENT_POLICY / DEVICE_EVIDENCE_PENDING
Primary reference is checksum verified `normal_home_v1.png` 1080x1920; NH02 independent 941x1672 reference for one-record state. Reuse page-native source images and state fixture IDs. Treat source original as immutable.

## Coordinate transformation and measurement types

Use physical app-window W,H and safeDrawing insets L,T,R,B with density d and fontScale and apply Layout_Responsive_Contract_V1.md once. A single transform must be produced as serialized JSON: `S=(W-L-R)/1080`, `delta=min(.36*max(0,H-T-B-1920*S),180*S)`. For NH02 generated raster at 941x1672, normalization into its 1080x1920 reference coordinates is **two-axis diagnostic normalization only**, not an instruction to nonuniformly stretch any runtime asset. Compare each state's OWN frozen original in its intrinsic pixels after accurately documenting normalization and crop assumptions.

Every measured node must declare its type: `region_rect`, `container_rect`, `text_linebox`, `glyph_ink_bbox`, `camera_raster_frame`, `camera_ink_core`, `camera_gold_mask`, `touch_hit_rect` or `hero_media_bbox`. Never compare different kinds. Screenshot aspect ratio, UI OS font rasterizer and motion frame time must be recorded; dynamic fish imagery is not included in a static reference's global pixel-error score.

### Acceptance tolerance classes (engineering QA, not permission to resize the frozen art)

- **Immutable assets**: SHA-256 EXACT. Wrong source hash -> SOURCE_FAIL (no partial pass).
- **Container/geometry at baseline**: `max abs x/y/w/h delta <= max(2px,0.002*Wsafe)` for same-kind target when NORMAL_FIXED, after the common physical-coordinate transform and **only one final raster rounding**. Expected ref Hero 740x880 and pager alignment follow same tolerance. No geometry parity judgement for SAFE_OVERFLOW except invariant card width/height and safe reachability; label mode.
- **Measured text visible ink**: bounding-box edge absolute delta `<=max(3px,0.003*Wsafe)` under matching fontScale, source font weight, density, stable neutral-motion frame and verified source ROI mask. No clipping even if bbox tolerance passes. Different font rasterizer/version must be recorded; repeated threshold sensitivity over this tolerance is `REVIEW_REQUIRED`, not hidden.
- **CTA/CAMERA optical masks**: compare source ROI with exact mask algorithm in `scripts/design/measure_normal_home_frozen_roi_v1.py` after transforming to reported baseline screenshot coordinates. Source CTA bright glyph y1527..1558 (threshold <=210), camera near-white high-contrast y1590..1773 and warm gold y1593..1789. Optical target is source visual ink, **not** NH05 shorthand container top. An observed edge error above geometry/ink tolerance -> FAIL for supported stable profile.
- **Static RGB colors**: source bitmap/asset SHA is primary. For screenshots, restrict color comparison to semantically same known static assets, same profile, gamma/color pipeline and unobscured regions. Do not manufacture a global pixel MAE threshold across photographs, antialiasing or animated frames. Record ARGB/ICC/transform and sample distribution; unexplained tint -> FAIL or REVIEW_REQUIRED. Background is exact asset + centered Crop without extra runtime treatment.
- **Hit targets and safe insets**: hard rule `widthDp>=48`, `heightDp>=48`, inside [L,W-R]x[T,H-B], no neighboring actions overlap. There is no ±px allowance for reducing a 48dp minimum.
- **Motion**: numerical time keyframes and amplitudes from Motion_Feedback_Contract_V1.md; video minimum 16s, neutral frame required for geometry and raster comparison. No unauthorized sound/haptic.
- **Responsive**: compare transformed geometry in NORMAL_FIXED, and invariant component size, full reachability/scrolling plus action docking in SAFE_OVERFLOW. A screen profile not actually run cannot be called PASS.

## Deterministic comparison workflow

1. Verify Frozen source SHA/dimensions and runtime source manifest SHA; abort optical compare on mismatch. Reuse `scripts/design/measure_normal_home_frozen_roi_v1.py` for CTA/Camera.
2. Run instrumented UI fixture, capture native screenshot without crop/resampling; collect device window metrics and SHA, app commit SHA, APK SHA, both layouts' `boundsInWindow` and semantic touch targets. Record d and fontScale.
3. Convert like-kind reference rectangles to expected window coordinates; compare by type and tolerance class above; check clip, geometry collisions and bottom safe margins.
4. Compare source vs rendered CTA/camera visible pixels using identical threshold and semantic ROI. Ensure animation neutral or document phase; never use a screenshot of Frozen as a runtime source.
5. If media mode is EVIDENCE_FIT, compare full source image visibility and on-card coverage rather than expecting it to match a different catch photo shown in the Frozen sample.
6. Produce per-case JSON evidence `{id,head_sha,apk_sha256,device:{W,H,L,T,R,B,d,fontScale},mode,frozen:{path,sha256},fixture_id,bboxes:[{type,expected,actual,delta,tolerance,pass}],screenshots:[{path,sha256,native_dimensions}],runtime_checks,verdict}`.
7. Validation Work independently verifies source file and APK hash and recomputes deltas. Work A CI logs are inputs, not automatically equivalent to independent PASS.

## Judgement

PASS means all mandatory case assertions measured and proven. FAIL is a tested violation. BLOCKED_INFRA is attempted test where target screen/profile unavailable (runner logs required). NOT_RUN means missing runtime evidence. REVIEW_REQUIRED means genuinely ambiguous pixel threshold/origin/visual authority and requires explicit evidence—not a substitute for FAIL. SOURCE_FAIL means hash mismatch and invalidates comparison. Preserve measured raw values and versioned tolerances so developers cannot simply loosen a failing assertion.

## Compliance modes and reporting

A fully conformant NORMAL_FIXED device meets exact source SHA, nominal geometry and optical ink tolerances with required touch/interaction/animations tests. SAFE_OVERFLOW does not need to visually reproduce NH01 1080x1920 on a short viewport; instead its acceptance target is its frozen conditional exception layout and invariant typography/card visuals. Any new exception rule is versioned, and existing NH01/NH02 Frozen source remains unchanged.
