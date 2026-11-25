# Sunlit Water Optical System (SWOS) — Optical Contract V1

**Contract ID:** `YUJIAN-SWOS-OPTICAL-V1`
**Version:** 1.0
**Status:** `MAIN_ADOPTED_DESIGN_ONLY`
**Scope:** Recognition Processing optical language and rendering invariants.
**Runtime:** `NOT_RUN` — this document is a design contract, not implementation evidence.

## Authority and adoption boundary

This contract was adopted on `main` through PR #190 as design authority. It supersedes the optical-language and optical-layer portions of the line-led AI Edge Field V1 contracts for Recognition Processing. Android implementation and Runtime remain unchanged/not run until a separately authorized Stage 1; the design adoption does not claim a rendered SWOS implementation.

On adoption, SWOS V1 supersedes the **optical-language and optical-layer** portions of the line-led AI Edge Field V1 contracts for Recognition Processing. It does not delete or rewrite the frozen Edge Field assets, geometry, alpha values, Quiet Gaps, or history. Those authorities continue to describe the legacy Edge Field implementation only. SWOS does not inherit its seven-path geometry as its visual foundation.

Related main-adopted design authorities:

- Processing timing: [`Recognition_Processing_Motion_Spec_V1_3.md`](../motion/Recognition_Processing_Motion_Spec_V1_3.md)
- Cross-page handoff: [`Processing_Result_Optical_Continuity_Contract_V1.md`](../../continuity/Processing_Result_Optical_Continuity_Contract_V1.md)
- Machine-readable optical contract: [`SWOS_Optical_Contract_V1.json`](SWOS_Optical_Contract_V1.json)

## Optical narrative

**晨光入水 → 水纹扩散 → 水光寻鱼 → 鱼体浮光 → 外围水光消退 → 鱼体金光接续 → 金光凝结为中文鱼种名 → 余辉消散。**

The real photo remains intact and visually dominant. Light behaves like sunlight refracted by fishing water: irregular, layered, locally reflected and naturally attenuated. It must not communicate system wake-up, scanning, radar, targeting or a completed result before real pipeline data permits it.

## Layer responsibilities

| Layer | Responsibility | Required behavior |
|---|---|---|
| Photo Base | Original captured image | Preserve bytes, orientation-normalized content and approved page/Hero mapping. No synthetic replacement, global tint or model-input change. |
| Ambient Water | Low-frequency environmental light | Begin near the lower visible photo region and diffuse upward; remain local and transparent enough to preserve image detail. |
| Caustic Field | Sunlight-like refracted texture | Irregular, non-uniform, multi-scale patches with different phase and strength; avoid a tiled or repeated texture. |
| Ripple Field | Overlapping water-wave structure | Unequal scales, spacing and propagation; overlap and decay naturally. No simple concentric rings or mechanical scan sweep. |
| Peripheral Refraction | Local response at visible photo edges | Follow the actual visible photo region. Uneven local reflections are allowed; a uniform closed rectangle or full-screen border is forbidden. |
| Fish Focus | Semantic emphasis on the located fish | Use trusted engine-generated subject geometry when available. Use warm gold, with a soft halo, a contour-following mid glow and a restrained core. |
| Residual Gold Trace | Processing-to-Result light source | At Processing handoff, retain only a narrow, soft trace attached to trusted fish geometry; it seeds RR09 V3 and is not a separate business state. |

SWOS uses no legacy B1–B4/G1–G3 path network as its optical basis. A prototype may use vector geometry, textures or sampled fields, but each must implement the layer responsibilities above and stay clipped to the actual photo content rectangle.

## Spatial and geometry contract

1. Normalize EXIF orientation before constructing display geometry.
2. Resolve the existing production FIT/CROP transform once and use its `photoContentRect` as the only photo coordinate frame.
3. Photo, water layers, peripheral refraction, detector BBox, subject geometry and residual gold trace share that transform. Insets and surrounding screen bounds do not remap them.
4. Light travels through the photo's displayed space. It may be attracted toward a verified fish region, but the whole image must not warp and all light must not mechanically converge on the center.
5. Clip optical layers to `photoContentRect`; letterbox space remains untouched.
6. Preserve source-photo identity and recognition session identity across Processing and Result. Missing identity or invalid geometry disables spatial transfer rather than inventing a source location.

## Fish geometry and provenance

- The detector's real BBox, confidence and classification result come from `FishRecognitionPipeline.kt` and retain their existing business semantics.
- `FishSubjectPreviewEngine.kt` is a post-recognition visual preview engine. Its segmentation/contour is not a classifier input and must not be silently called a production classifier Mask.
- A contour may drive a fish-following rim only when the existing trust checks confirm matching image identity, normalized dimensions, detector box/confidence, valid mask dimensions, quality and finite non-empty contour.
- If a trusted contour is absent, a valid real BBox may support a restrained **regional glow only**. Never trace the rectangle or fabricate an ellipse/fish silhouette.
- If neither trusted geometry nor valid BBox exists, Fish Focus is `BLOCKED`; omit spatial gold travel and use the already-authorized non-spatial fallback. Do not present a false successful fish highlight.

## Optical calibration policy

The contract freezes layer meaning, spatial relationships, source identity and prohibited forms. It does **not** freeze a single alpha, color, blur radius, caustic contrast, light width or velocity from one demonstration image. Those are calibration parameters and must be tuned against a registered photo set that includes bright water, dark water, varied fish positions, crop/edge clipping, multiple fish and low-quality imagery. Store each calibration set with source identities, renderer version and visual evidence.

Initial water blue-green and warm gold may be calibrated within the existing brand palette. Avoid pure neon, clipped white highlights, uniform cyan cast, equal-strength edges or corner ornaments. Fish Focus remains warm gold; surrounding water may use restrained blue-green and sunlight gold. Do not merge the two into one undifferentiated color wash.

## Timing ownership

`Recognition_Processing_Motion_Spec_V1_3.md` owns the 2800 ms minimum visual pacing and its four internal phases. Real pipeline events remain mandatory gates. The optical clock never invents percentage, ETA or result completion. If inference exceeds the minimum visual rhythm, SWOS settles into a low-intensity hold until the actual result and existing business gates allow navigation.

The last 200 ms of the nominal 2800 ms Processing timeline is a water-field release subphase inside CLASSIFYING. No RESOLVE state or extra delay is introduced.

## API 28, performance and reduced motion

- API 28 is the supported baseline and may not depend on newer runtime shader APIs. Stage 1 must select and measure an API 28-compatible Canvas / cached-texture implementation; this contract does not claim such implementation exists yet.
- Cache static photo/geometry resources. Avoid per-frame full-resolution CPU image transforms, bitmap allocation, unbounded blur, or repeated full-screen overdraw.
- Degrade secondary caustics and ripple density before the photo or trusted fish geometry; retain the same semantic flow with a restrained fallback.
- With Reduce Motion, present a stable still composition: no ripple travel, traveling highlights, particle travel or continuous breathing. Preserve recognition state, result, CTA and failure semantics.
- Suspend or settle optical work when the page is not visible; release resources with the owning page lifecycle.

## Freeze and verification matrix

| Category | Phase 0 disposition |
|---|---|
| Frozen | Layer responsibilities, real-photo preservation, shared photo coordinate frame, non-closed irregular field, fish-geometry trust, no model/business changes, Reduce Motion semantics, no false success. |
| Calibrate in Stage 1 | Color values within palette, opacity, blur, field width, caustic contrast, ripple speed, texture scale, performance thresholds. |
| Engineering requirement | API 28-compatible renderer, clipping, resource lifecycle, real-photo fixture matrix, exact-source evidence and real-device performance verification. |
| Not established here | Browser visual PASS, Android implementation/runtime PASS, APK provenance or production Fish Focus availability. |
