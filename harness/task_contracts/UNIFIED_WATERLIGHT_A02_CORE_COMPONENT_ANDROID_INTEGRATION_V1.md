# Unified WaterLight A02 — Core Components and Android Recognition Integration V1

**Task ID:** `YUJIAN_UNIFIED_WATERLIGHT_A02_CORE_COMPONENT_ANDROID_INTEGRATION_V1`  
**Phase:** P6 / P0  
**Repository:** `pan277942135/Yujian_App`  
**Registered baseline:** `3ac4e5c5aa9295169dd1a08ed7a717cef125f640`  
**Shared contract:** `ANDROID-UNIFIED-WATERLIGHT-A02-V1`  
**Evidence Gate:** `G-ANDROID-UNIFIED-WATERLIGHT-A02-V1`  
**Product branch:** `work/unified-waterlight-a02-core-component-v1`

## 1. Authoritative inputs

- **A01 audit:** `harness/reports/UNIFIED_WATERLIGHT_A01_RESOURCE_AUDIT_REPORT_V1.md`. This is the preserved audit report; reuse its identified Android sources and upstream provenance rather than rebuilding the Orb, perimeter or contour algorithms.
- **B01 material master:** **B01《统一水光生命系统｜材质母版 V1》**, product-owner-confirmed and frozen on 2026-10-31. Its material roles are champagne-gold fine filaments, transparent aquatic-green flexible filaments, crystal-clear refractive droplets and blue-green lake-water refraction. B01 is a material reference, not a page background or final animation texture. Its exact binary path/hash and numeric parameter sheet are not present in the current canonical source; do not invent them or promote inferred values to frozen authority.
- **Existing Orb values:** `harness/task_contracts/ANDROID_PROCESSING_LAKE_ORB_V1.md` remains authoritative for the current Processing Orb values, cycles and geometry. Preserve its current values and the AI Orb Core V1 state/motion contracts.
- **Recognition authority:** Recognition State Timeline V1.3, Processing V3.2, SWOS optical/motion contracts and RR09 850ms handoff remain authoritative.
- **Caustics upstream:** `ethanfox/water-caustics` at immutable source SHA `de856eec62819b7c4176366db73b8fd37a1b1455`; retain the original MIT license and source attribution.

## 2. Objective and ownership

Establish two independent components over one shared rendering foundation:

- **AI Orb** owns its core shape, core mask, depth, state and Orb-specific geometry. It may call WaterLight renderers.
- **WaterLight** owns material, frame input, geometry adapters and renderers. It must not depend on AI Orb.

Use the existing `ProcessingWaterOrb.kt`, `RecognitionPerimeterShaderOverlay.kt`, `RecognitionPerimeterShaderMath.kt`, `RecognitionAmbientField.kt`, SlimSAM and contour code as the starting point. Refactor by extraction and thin adapters; do not rebuild pages or duplicate recognition state machines.

## 3. Shared runtime contract

- One `WaterLightFrame` per rendered frame, bound to `sessionId`, `photoSha256`, `elapsedMs`, `opticalReferenceElapsedMs`, `phase`, `completion`, `reducedMotion` and `geometryVersion`.
- All hosts consume the existing Recognition Processing scene clock. No second Choreographer, independent recognition clock or renderer-owned phase progression.
- `WaterLightMaterialProfile` is host-specific. Orb, perimeter, path, contour, residue and handoff may vary in intensity, width, count, blend and clipping.
- All geometry uses one canonical PhotoSpace: pixels of the EXIF-normalized canonical bitmap actually passed through the current camera/gallery pipeline. Do not label a center-cropped/downsampled bitmap as the sensor full-resolution original.

## 4. Mask and coordinate correctness

Carry the actual ROI rectangle and preprocessing transform from SlimSAM inference through `MaskGeometryMetadata`; include session/attempt/image/photo identity, canonical dimensions, ROI, model input, resize dimensions, four padding values, mask grid, inverse transform, pixel-center convention, threshold, contour version and model SHA.

Perform the inverse mapping exactly once in the adapter. The page must not recompute or guess ROI. Apply all eight EXIF orientations, including mirrored orientations, before Detector, SlimSAM, Crop, Mask and display consume the same canonical bitmap. Preserve multiple closed contour subpaths. The 256×256 model grid must never be treated as screen coordinates.

## 5. Host set

Integrate one common host interface for Orb, Perimeter, Path, Contour, Residue and Handoff. Keep host-owned geometry and clipping. The Perimeter host must preserve the existing GLES backend, source alpha behavior, photo corner clipping and shared micro-shake transform while reconciling the existing shader/path margin difference.

## 6. Renderer and performance requirements

- Extract existing Orb gold/green filament paths without changing their current default appearance.
- Add a deterministic, reusable transparent droplet renderer with an actual refractive droplet body, edge highlight and internal light/shade; a glow dot is not a droplet.
- Port upstream `rotate`, `octave`, `field` and `sampleCaustic` to Kotlin with MIT attribution. Use background calculation, bounded pixel budget, reusable ARGB_8888 buffers and double buffering. Never perform full per-pixel work on the UI draw thread.
- Reject asynchronous results whose `sessionId` or `geometryVersion` no longer matches.
- Target 60fps; if measured performance is insufficient, degrade deliberately to 30fps while prioritizing photo and fish-contour stability. Release buffers and renderer resources on lifecycle exit.

## 7. Frozen invariants

Do not alter the frozen P0/P2/P3 visual contracts, AI Orb state machine, PhotoSession semantics, Recognition Timeline, Processing timing, RR09 timing/geometry, Detector/Classifier outputs, model inputs, BBox semantics, fish-name semantics or page layout/photo size. Keep current Orb and Perimeter visual defaults byte-/parameter-equivalent before enabling new additive material layers.

## 8. Evidence and completion

Require deterministic renderer tests, host geometry and PhotoSpace round trips, ROI inverse mapping, EXIF 8-way parity, multi-path contours, session/geometry stale-result isolation, caustics numerical comparison against the pinned upstream source, MIT license provenance, real-photo positive/negative Mask evidence, six-host integration, Recognition/RR09 invariance, frame/CPU/GPU/memory metrics, resource-release evidence, Android build, unit tests, AndroidTest compilation, lint, API28 runtime evidence and exact-head Debug APK SHA/artifact provenance.

If the model runtime or real fish-photo data is genuinely unavailable, record the real-mask subcheck as **BLOCKED_BY_MASK_DATA**. Continue all independently verifiable work; no synthetic or hand-drawn mask may be reported as real evidence. Emulator evidence must not be presented as physical-device acceptance.
