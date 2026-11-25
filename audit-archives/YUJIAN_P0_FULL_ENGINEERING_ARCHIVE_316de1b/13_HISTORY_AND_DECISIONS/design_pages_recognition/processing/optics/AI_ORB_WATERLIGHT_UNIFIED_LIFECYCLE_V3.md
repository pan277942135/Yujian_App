# AI Orb × WaterLight Unified Lifecycle V3

**Status:** `ACTIVE_IMPLEMENTATION_CONTRACT`  
**Task:** `YUJIAN_ORB_WATERLIGHT_WEB_ANDROID_FULL_LIFECYCLE_V3`  
**Android Main audited:** `ddd0db3a7f1bdb38cbd70081322c20d330ec7fad`  
**Android implementation candidate audited:** PR #294 HEAD `b820baa41d62ab7d056e3b084daa5b9f655d8aeb`  
**Web reference audited:** `pan277942135/Fish-Mask-Gold-Contour-Lab` Main `54ac0b6307454549d004d1890d9ee12cc6b54040`

## 1. Purpose and authority

This contract defines the continuous production lifecycle for the existing Android AI Orb, WaterLight, Recognition and Result components. It supersedes only the fixed *global* Processing/RR09 elapsed-time cutoff semantics in the older SWOS/RR09 documents. Those documents remain historical records. Their useful local optical curves, visual ordering, photo geometry, and the 700 ms photo / 900 ms Orb parallel entry are retained where they do not conflict with this contract.

There is no production rule that Processing ends at 2,800 ms, RR09 ends at 850 ms, or the full scene ends at 3,650 ms. Those values must not reject a late but valid Mask, stop a renderer, trigger Result navigation, or fabricate model success. Local visual transitions may have durations. Production stages advance only when both their local presentation minimum and their real data/event gates are satisfied. A 60,000 ms model watchdog is a failure/cancellation safety bound only; it never converts a pending model into success or an Orb-only reveal.

The visual stages are semantic states, not timestamp bins. Their order is shared by production, Debug MANUAL and Debug AUTO. MANUAL may visit a stage without satisfying its production data gate, but must display the real missing-data status and must not draw fabricated geometry.

## 2. Component responsibilities

### AI Orb

AI Orb remains an independent reusable component. It owns the water core, internal aquatic structure, refraction, sunlight response, breathing, shape changes, energy release/receipt and the eventual morph of the real classifier-provided Chinese glyph. Its entrance is projected from the shared scene frame and remains 900 ms when entered from Camera; gallery entry displays the Orb directly. It never starts its own recognition clock.

### WaterLight

WaterLight remains independent of Orb internals. It renders Orb filaments, the photo perimeter, photo-to-fish paths, real Mask contour layers, path-bound droplets, fish energy and residue. It uses the shared scene frame, identity, geometry and event protocol. It may be invoked by Orb but does not depend on Orb implementation details.

Both systems preserve water droplets, internal aquatic life and warm sunlight as core visual elements. Lake-water cyan/green is the primary family; warm gold is the accent. Filaments are curved, open water threads rather than laser columns. The source photo remains fully visible and unmodified outside localized optical compositing.

## 3. Single scene authority

`RecognitionSceneAnimationController` is the logical owner of stage entry, monotonic scene time, local stage elapsed time, pause/resume/seek, model-event intake, stage gates, geometry version, cancellation and handoff. Implementation must extend the existing `RecognitionVisualStateController`, `SceneMotionCoordinator` and `RecognitionSceneFrame` responsibilities or replace them with one controller; it must not add a second ticker or timer.

The scene clock starts on the first actually presented stable original-photo frame. Photo settling (700 ms) and Orb entrance (900 ms) run in parallel as projections of that same clock. Compose, Canvas, PathMeasure and GLES consume the same immutable frame sample. No Renderer advances time or decides the next stage.

Each frame and event is accepted only for the current identity tuple:

`sessionId + attemptId + imageId + photoSha256 + photoContentVersion + geometryVersion`.

Each event also carries monotonically increasing `eventSequence`, monotonic uptime timestamp, source/target host anchors and, where applicable, a stable `pathId` and sampled path geometry. Duplicate, out-of-order, stale-photo, stale-mask and stale-geometry events are ignored.

## 4. Model join and reveal decision

After the real Detector provides a BBox, Classifier and SlimSAM run independently and concurrently. Classifier completion stores the real species result but never reveals it or navigates by itself. SlimSAM must reach an explicit terminal state and have its Mask trust/contour validation completed before visual branching.

The production join is the existing `RecognitionRevealDecisionGate`, extended only as needed:

- `Classifier READY` + current `SlimSAM READY / TRUSTED` + valid contour → `FISH_FOCUS_READY`.
- `Classifier READY` + current `SlimSAM READY / UNTRUSTED` or `FAILED` → `ORB_ONLY_READY`.
- `Classifier FAILED`, current attempt cancelled, or identity mismatch → existing failure/cancel handling.
- `IDLE`, `QUEUED` or `PROCESSING` on either required branch remains pending.

Trusted-mask validation includes source photo SHA/identity, ROI and source dimensions, EXIF-normalized PhotoSpace mapping, mask quality and dimensions, non-empty contour, current geometry version and current attempt. Classifier confidence and Mask quality remain independent. Detector BBox is not a fish contour and cannot create fish gold.

While the join is pending, the scene holds the current eligible visual state with restrained living motion. It does not restart completed stages, publish a name, draw a contour, or infer a terminal model state from elapsed time. After 60 seconds without a required model terminal event, the watchdog reports a technical timeout and enters explicit failure recovery; it does not select `ORB_ONLY_READY`.

When the current Mask is untrusted or failed but classification is valid, production skips fish-only contour/gold states and returns energy from the measured photo perimeter to the original Orb. It then morphs the same Orb toward the real Chinese species glyph. Debug MANUAL may inspect all 13 labels; skipped fish states show `MASK_UNTRUSTED` / `WAITING_FOR_MASK` and render no fake fish.

## 5. The 13 semantic stages

These states preserve the established order and visual intent. Their meanings do not encode absolute global elapsed milliseconds.

| # | Stage | Entry evidence and visual responsibility |
|---:|---|---|
| 01 | `PHOTO_STABLE` | The current original photo has completed a real presented frame. This is the scene-clock origin. |
| 02 | `ORB_WAKE` | The same Orb wakes: water core, refraction, internal aquatic structure and soft breathing respond. |
| 03 | `ORB_RELEASE` | Energy gathers in the Orb and 1–2 curved filaments leave its measured outer edge with a few refractive droplets. |
| 04 | `PERIMETER_ARRIVAL` | Those filaments reach measured photo-edge anchors; contact regions light first and the perimeter spreads organically. |
| 05 | `PATH_TO_FISH` | With a real Detector target and valid photo transform, WaterLight follows a curved path toward the real fish. No contour is drawn here. If required model data is pending, hold at the path endpoint. |
| 06 | `CONTOUR_A` | On `FISH_FOCUS_READY` only, the trusted Mask contour begins to appear. |
| 07 | `CONTOUR_B` | Blue/gold alternating contour, attached droplets and soft light build on the same trusted contour. |
| 08 | `CONTOUR_C` | The real contour closes and stabilizes; the local warm-sun glint moves along its PathMeasure. |
| 09 | `FISH_GOLD_PEAK` | Core/Soft/Halo concentrate on the same trusted Mask. Perimeter light yields attention only after the focus is ready. |
| 10 | `ORB_RETURN` | Fish-focus branch returns energy along the stored release/path geometry in reverse; Orb-only branch returns from the measured perimeter. |
| 11 | `ORB_TO_NAME` | The original Orb receives energy, breathes, expands horizontally and begins transferring water material toward the actual glyph geometry. |
| 12 | `SPECIES_NAME_REVEAL` | Actual Classifier Chinese glyph fills/forms and becomes readable. No fixed string, raster substitute or English placeholder. |
| 13 | `RESIDUE_SETTLE` | Localized residue condenses and settles. Temporary optical layers exit; normal Result UI and CTA remain available. |

The 13 labels are never merged or renamed. AUTO uses local minimum presentation time plus the required event gate. MANUAL advances only on user control. Display-mode changes do not change stage, local progress, identity, energy events, model state or any renderer instance.

## 6. Energy event chain

The single event protocol supports:

1. `RELEASE`: Orb ribbon → measured perimeter anchor.
2. `ARRIVE`: perimeter contact → path host.
3. `ABSORB`: path → trusted Mask contour anchor.
4. `RETURN`: trusted fish contour → same Orb along the release/path geometry in reverse; untrusted branch uses photo perimeter → same Orb.
5. `TRANSLATE`: Orb core → actual glyph path.
6. `REVEAL`: progressive actual glyph formation.
7. `SETTLE`: glyph → localized residue.

An event is emitted from an actual controller or geometry milestone, not because `elapsedMs` crossed a hard-coded window. Existing event types can be extended, but empty events or timestamp-only events do not satisfy this contract.

## 7. Web rendering migration boundary

The audited Web reference is an algorithm and appearance source, not an Android runtime to embed. Port only active production behavior and preserve the frozen Android photo/edge geometry.

`FishCatchWaterOverlay.ts` provides rounded-perimeter distance and wave functions, the `revealedMask`, multi-octave caustics, sunlit spots, tint mixing, and 16 perimeter motion nodes. The shader's `causticsDensity` config value is not declared as a shader uniform and has no effect in that Web build. Its second Canvas also uses `screen` composition internally and CSS `overlay` externally. Android must implement and test the intended local blend explicitly; changing alpha alone is not parity. The Web bottom-up field and decorative flourish are separate from the frozen Android four-sided perimeter and must not be drawn over fish/photo content without the corresponding Android contract.

`FishContourGlowRenderer.tsx` consumes a real `ContourData.svgPath`. Its optical stack is the dark-teal contrast under-stroke, blue-to-gold two-cycle core gradient, narrow ice highlight, moving sunlit-gold dash with feathered ends, Soft Glow, Halo and internal glint. Web widths are in CSS/image units; Android converts by the measured canonical-photo-to-display matrix. It does not copy BBox into a contour.

`WaterResonanceRenderer.tsx` has a `BBOX_ONLY` branch and also synthesizes a fallback box when no detection exists. That renderer is not the source of Android production fish geometry. Its static ambient blobs, pulse and residue may inform non-fish environmental material only after separate identity/geometry validation.

## 8. Rendering, identity and fallback

The perimeter host remains TextureView + EGL14/GLES2 with opaque photo composite. The original Compose photo remains visible until the current identity's photo texture upload, draw and EGL swap succeed. Shader compile/link, texture upload, draw and swap are independently recorded. Any error, Surface loss, session replacement, cancellation or geometry mismatch immediately returns photo ownership to Compose.

The single canonical `RecognitionImageTransform` maps the EXIF-normalized source into `photoContentRect`. Perimeter, path, trusted Mask contour, droplets and Result handoff use the same transform and geometry version. Source photo pixels, PhotoSession, Detector/Classifier inputs and coordinate semantics remain unchanged.

Renderers use deterministic, identity-seeded path and droplet samples; cache paths, paints, textures and buffers; reject stale generations; and release on lifecycle termination. No persistent layer may darken or conceal the source photo. API 28 capability is maintained, while this task's final physical-device/API 28 acceptance remains the product owner's test.

## 9. Acceptance boundary

Engineering acceptance requires exact-SHA unit tests, AndroidTest compile/runtime tests where available, lint, build and APK provenance. Product-owner visual and device acceptance is reported separately; a successful build is not a visual PASS. The current product-owner instruction for this Work is to deliver an installable Debug APK after basic tests and let the owner run device acceptance.
