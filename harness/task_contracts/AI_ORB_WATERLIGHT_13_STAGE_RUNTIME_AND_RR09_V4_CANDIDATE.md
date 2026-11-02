# AI Orb × WaterLight 13-Stage Runtime Contract and RR09 V4 Candidate

**Contract ID:** `YUJIAN-AI-ORB-WATERLIGHT-13-STAGE-RUNTIME-RR09-V4`
**Status:** `IMPLEMENTATION_CANDIDATE_PENDING_DEVICE_VISUAL_REVIEW`
**Scope:** Android visual runtime projection only; recognition inputs, thresholds, routing, Result layout and CTA behavior remain owned by their existing contracts.
**Runtime verification:** `NOT_RUN` until an exact-source Debug APK is run on the API 28 target and the visual evidence matrix is saved.

## 1. Clock and identity authority

`RecognitionProcessingScene` owns the sole monotonic clock. Its origin is the first frame that actually draws the current normalized PhotoSession bitmap. The frame is projected through `RecognitionSceneFrame`; renderers do not create clocks. The 700 ms photo settle, 900 ms Orb entrance, perimeter, path, contour, Orb return and RR09 projections consume that shared sample. Photo settle and Orb entrance run in parallel.

Each event and render host must match the active `sessionId`, `attemptId`, `imageId`, `photoSha256`, `photoContentVersion` and `geometryVersion`. Runtime paths use current viewport-space anchors derived from the current `RecognitionImageTransform`, trusted SlimSAM contour and measured Orb/title geometry. A stale identity or invalid curve produces no spatial effect. No trusted Mask means no fish-directed path, contour, or celebratory RR09.

The 13 scene stages and original 2800 ms Processing minimum are unchanged:

| Stage | Time | Runtime role |
|---|---:|---|
| `PHOTO_STABLE` | 0 ms | First real photo frame; no added delay |
| `ORB_WAKE` | 0–350 ms | Camera art dissolves into the water core; gallery uses the core directly |
| `ORB_RELEASE` | 350–950 ms | Open filaments expand from the Orb surface toward the photo perimeter |
| `PERIMETER_ARRIVAL` | 950–1075 ms | Perimeter light receives the arrival event |
| `PATH_TO_FISH` | 1075–1200 ms | Trusted-mask path begins absorbing energy |
| `CONTOUR_A` | 1200–1450 ms | Real contour begins to appear |
| `CONTOUR_B` | 1450–1700 ms | Contour expands; gold is already at its frozen 1550 ms peak |
| `CONTOUR_C` | 1700–1950 ms | Real contour closes and stabilizes |
| `FISH_GOLD_PEAK` | 1950–2800 ms | Gold stays stable; environmental water exits to zero at 2800 ms |
| `ORB_RETURN` | 2800–3180 ms | Trusted fish energy returns to the original Orb anchor |
| `ORB_TO_NAME` | 3180–3350 ms | The Orb stretches and resolves into actual Chinese glyph strokes |
| `SPECIES_NAME_REVEAL` | 3350–3480 ms | The real classifier Chinese name becomes legible |
| `RESIDUE_SETTLE` | 3480–3650 ms | Localized water-gold residue settles to zero |

The frozen P0–P7 curve remains authoritative: environmental intensity `[0,10,45,75,60,35,10,0]` and fish gold `[0,0,15,45,100,100,100,100]` at `[0,350,950,1200,1550,2050,2600,2800]` ms. Numeric Alpha, coverage and new colors stay calibration candidates unless visually signed off.

## 2. RR09 V4 candidate — first presented Result frame T0, 850 ms total

V3 remains immutable historical authority. V4 changes only the light-source route and glyph-formation method. It preserves HIGH-only eligibility, one-shot behavior, same photo/session/transform, first presented Result frame T0, 850 ms duration and immediate CTA availability.

| RR09 | Duration | V4 projection |
|---|---:|---|
| 0–200 ms | 200 ms | Same-photo continuity; return path launches from the real fish contour toward the recorded Processing Orb anchor |
| 200–380 ms | 180 ms | Orb receives and stabilizes the returning energy; its inner light and filaments respond |
| 380–550 ms | 170 ms | Orb elongates along a measured curve and resolves into deterministic samples of the real Chinese glyph path |
| 550–680 ms | 130 ms | Actual classifier-provided Chinese name becomes readable; no English scientific row is revealed |
| 680–850 ms | 170 ms | Localized title-adjacent residue fades to zero; Result is fully interactive throughout |

The return curve starts at the transformed trusted-mask anchor and ends at the saved original Orb center. The translation curve starts at that Orb center and ends inside the measured Chinese title bounds. Glyph ink samples are derived from the rendered font path for the current classifier name, not a fixed bitmap or fixed species string. Missing/untrusted spatial data disables travel and uses the existing restrained fallback. Non-HIGH, missing identity, failure and negative results never run the successful reveal.

## 3. Renderer ownership

- `AIOrbCore` owns core material, breath, shape and release/return response.
- WaterLight `ORB`, `PERIMETER`, `PATH`, `CONTOUR`, `HANDOFF` and `RESIDUE` hosts render their own role profiles and consume the same frame/event identity.
- `RecognitionPerimeterTextureView` retains opaque photo-plus-light composition. Its photo base is not darkened by the perimeter delta.
- `RecognitionFishFocus` uses only the existing trusted SlimSAM contour for Core/Soft/Halo; BBOX-only never draws a rectangle or fabricated Mask.
- `RecognitionRevealOpticalRenderer` retains the same photo and draws transient Orb return/morph/settle layers; `SpeciesNameReveal` samples the current Chinese glyph path.

## 4. Candidate vs frozen values and verification

The existing frozen Processing and RR09 timings, real result gates, photo continuity, true geometry and CTA semantics are unchanged. The perimeter cool-water profile follows the existing B01-compatible candidate family (`#2D7C89`, `#67BDCA`, `#C1E9EE`) and is not visually frozen. Other non-ORB material widths/Alpha/coverage remain candidates.

The API 28 production runtime gate uses the registered night-catch photo at its recorded SHA-256, production Detector/Classifier and SlimSAM Mask. It captures each of the 13 stages from the shared scene projection, P0–P7, RR09 R0/R0.5/R1/R2/R3/R4/R5, the screen recording, identity/model/mask logs, and a SHA-indexed contact sheet. The gate fails unless the same HIGH result and trusted Mask qualify the production handoff; no fixture species or synthetic contour is accepted.

Unit tests verify stage projection, identity rejection, deterministic curves and RR09 boundaries; they do not certify visual fidelity. API 28 playback, the 13-stage screenshot comparison, continuous recording, FPS/memory and exact-head APK provenance remain `NOT_RUN` until the target workflow finishes. The contact sheet reports B01 material values as runtime candidates and does not freeze visual calibration.
