# SWOS Static Visual Contract V1.1

**Contract ID:** `YUJIAN-SWOS-STATIC-VISUAL-V1_1`
**Design status:** `FROZEN_DESIGN_DIRECTION`
**Adoption status:** `MAIN_ADOPTED_DESIGN_ONLY`
**Source PRs (provenance only):** #186 (Phase 0) and #187 (Static Visual V1.1); design content adopted together via PR #190
**Runtime / playback:** `NOT_RUN`

## Authority and scope

This static visual/keyframe contract is main-adopted through PR #190, with content sourced from PR #186 and #187. The predecessor PRs are provenance only and are not active dependencies. It does not create a parallel Processing or RR09 motion specification; static review remains design-only and is not Runtime or production evidence.

- Processing duration remains **2800ms** under the main-adopted Processing Motion V1.3.
- RR09 reveal remains **850ms** under the main-adopted RR09 Motion V3.
- Total nominal visual time remains **3650ms**; no extra wait, model behavior, or business state is added.
- The 14-frame V0.3 poster remains `HISTORICAL_DIRECTION_REFERENCE`, not a pixel-frozen source.
- This document does not claim browser animation, Android runtime, or production fish-mask acceptance.

## Visual narrative

**晨光入水 → 水纹扩散 → 水光寻鱼 → 鱼体浮光 → 光归于鱼 → 照片自然迁移 → Result Hero 落位 → 金光转移 → 中文笔画凝结 → 中文名称揭晓 → 余辉消散。**

SWOS is YuJian's own optical language. The Hongmeng Xiao Yi wake-up video was only a light-motion reference and is not a recognition-flow prototype. Do not copy assistant wake-up frames, system borders, scan paths, radar motifs, HUD framing, or UI semantics.

Use one restrained optical language for all source photos. The water-light metaphor does not require actual water in the image. Do not turn grass, clothing, people, or indoor scenes into underwater scenery.

## Frozen water caustics direction

- Prefer low-to-medium-density irregular area patches, supported by a few directional light textures.
- Use multiple scales, soft edges, uneven local overlaps, gradual attenuation, and a clear lower-to-upper propagation direction.
- Wake near the lower visible edge of the original photo, spread upward, peak through the middle, then yield to the fish highlight.
- Keep the photograph intact, sharp, and dominant. Ambient light may not overpower the fish highlight.
- Do not use dense spiderweb lines, concentric scan rings, seven-path Edge Field geometry, a closed blue/gold frame, or a uniform full-screen border.

Relative visual-strength samples (not shader alpha):

| Time | Ambient water |
|---:|---:|
| 0ms | 0 |
| 350ms | 10 |
| 950ms | 45 |
| 1200ms | 75 |
| 1550ms | 60 |
| 2050ms | 35 |
| 2600ms | 10 |
| 2800ms | 0 |

These values describe relative visual strength only. They are not final production opacity or shader values.

## Frozen fish-gold direction

Gold is independent of ambient water and uses one source geometry for all three layers:

1. **Gold Core** — narrow and crisp.
2. **Gold Soft Glow** — close to the same contour.
3. **Gold Halo** — wider and low-brightness.

The three layers must share one trusted fish geometry. They may not each guess or re-fit the fish. Preserve fish scales, eye, fins, and tail detail. Avoid blinking, breathing loops, broad fluorescent edges, or false recognition. A manual contour is labeled `MANUAL_REFERENCE_CONTOUR` and is not a production mask.

| Time | Fish-gold relative strength |
|---:|---:|
| 0ms | 0 |
| 350ms | 0 |
| 950ms | 15 |
| 1200ms | 45 |
| 1550ms | 100 |
| 2050ms | 100 |
| 2600ms | 100 |
| 2800ms | 100 |

Peak: 1550ms. Stable hold: 1250ms (1550–2800ms). The fixture's reference trace is shown only to review the intended optical relationship; production rendering still requires the real pipeline geometry/trust gate defined by the dependent Phase 0 contract.

## Fifteen static samples

The machine-readable frame list is [SWOS 15-Frame Keyframe Contract V1.1](SWOS_15_Frame_Keyframe_Contract_V1_1.json). It contains 8 Processing samples and 7 RR09 samples.

The added **R0.5 / 100ms / global 2900ms** is a real geometric mid-flight sample inside the existing 0–200ms handoff. It adds no duration or state. R1 remains at 200ms / global 3000ms. Processing remains 2800ms; RR09 remains 850ms.

R0, R0.5, and R1 retain the same source bytes, fish coordinate space, gold source, and shared transform. The picture must visibly move, scale, and change container bounds; a page crossfade alone is insufficient. The result background/navigation/form may enter behind the same photo layer. Full Hero geometry is reached at R1; gold may begin moving to the title only after that point.

R5 reuses the existing frozen HIGH Result page composition and media policy. It includes the top navigation, Result Hero, Chinese-only species label, length, weight, location, story field, and the existing dual CTA. The fixture uses `鱼种名称` as an explicit visual placeholder; it is not a model prediction.

## RR09 authority relationship

RR09 V3 is the main-adopted design authority for the successful reveal; V2 remains byte-for-byte historical. This static contract adds R0.5 sampling and shared-photo geometry evidence. It does not change model output or establish runtime/production visual acceptance.

## Photo and publication boundary

The nominated `208758.jpg` was verified locally as 1152×1536, EXIF orientation 1, SHA-256 `ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987`. It contains a person and a Xiaohongshu watermark. Public publication permission is not confirmed, so the photo, its derivatives, screenshots, and frame exports are excluded from this public repository change. The Design Manager inspector accepts a local file and processes it in-browser; it does not upload or persist the selected photo. Private review exports are delivered separately.

No detector BBox or production contour was present in the task input. A manually traced sample BBox/contour is marked `MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK`. It cannot satisfy any runtime trust gate.

## Freeze boundary

Frozen here: visual language, relative strengths/timing, three-layer geometry relationship, 15 frame identities, R0.5 geometry, Chinese-only reveal for the main-adopted RR09 V3 design contract, source-identity invariants, and prohibited visual forms.

Still open: photo publication permission, browser visual art-direction acceptance for production, calibrated shader/opacity parameters, dynamic playback, Android/API28 implementation, and runtime performance.
