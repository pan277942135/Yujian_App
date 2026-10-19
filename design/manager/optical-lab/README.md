# Recognition Optical Lab

This is a static browser engineering experiment linked from Design Manager. It reads the existing Processing engineering import config and Frozen State Set directly; it does not create a second design contract or modify reference assets.

## Run locally

From the repository root on the PR #166 branch:

    python3 -m http.server 8765

Open:

    http://localhost:8765/design/manager/optical-lab/

The PR Actions artifact also includes `Recognition-Optical-Lab-Browser-Package.zip` with this page, the exact three Frozen references, the Manifest, the Processing config and the verified test photo. Unzip it, run the same HTTP server from the extracted package root, then open the path above.

Use HTTP localhost rather than file:// so the page can fetch the existing JSON and images and use browser SHA-256 verification.

## Stage A controls

- Switch exactly the three Frozen product states.
- Compare Frozen and Web side by side, 50% overlay, wipe, exploratory pixel heatmap, or four-up Frozen/current/hardware-gradient/software-cache. Frozen images are uniformly FIT into the selected viewport; aspect ratios are not stretched.
- Toggle PhotoViewport, Receiving Light, primary outer bloom, primary mid glow, primary core, companion/micro hairlines, Fish Focus halo/contour, and Status Card.
- Adjust opacity, glow width, blur radius, gradient falloff, tail length/position, focus intensity, State Strength, quality profile, D0–D4, and browser viewport. Restore the P1–P5-derived defaults at any time.
- Import a source photo and an alpha-mask PNG. BBox coordinates must be normalized, valid, and explicitly attested as real detector output before Fish Focus draws. A-level focus maps the real mask contour through the P2-expanded BBox; no synthetic contour is used.
- Export Web frame, comparison, and ROI PNGs.

The default photo is the repository Android test fixture. Its SHA is checked against P5, but no source identity binding proves that it is the same original photo used in the three Frozen screenshots. Pixel metrics therefore remain exploratory. The repository fixture has no trusted BBox or production Mask, so the Fish Focus visual comparison stays empty until a real detector BBox and FishSubjectResult alpha mask are bound. Imported BBox/Mask provenance is not machine-verified; A-level focus requires an actual FishSubjectResult alpha mask and the corresponding real detector box. No bbox means no focus, no mask falls back to B, D4 uses C.

Stage A is static. Play/Pause, timeline, RESOLVE animation, and continuous recording remain disabled until a human chooses a static rendering route after reviewing the generated browser screenshots. No particles or motion clocks are drawn.

## Renderer notes

- Android current: geometry and alpha from the runtime implementation; browser shadowBlur is only an approximation of Paint BlurMaskFilter.
- Hardware-gradient: Path, radial gradients, linear gradients, and wider low-alpha strokes without a blur filter. Candidate stroke multipliers are explicitly local engineering values in the `optical_lab_runtime_snapshot.renderer_candidates` extension, pending static visual review. This is the candidate to carry into an API 28 device check.
- Software-cache: cache the static optical layers in an offscreen RGBA canvas and composite the cache with Canvas filter blur; the browser kernel is only an approximation and is not an Android software Canvas result.

Candidate geometry that is available only in RecognitionAmbientField.kt (the four Receiving Light ovals and companion/micro control points) is sourced from that Android runtime file, not represented as a Frozen design value in P1 config. The existing P1–P5 config remains the single source for authored paths, tokens, layer order, state mapping, motion contract, quality and degradation. Runtime-only geometry is stored in its explicitly non-normative `optical_lab_runtime_snapshot` extension with source Git blob SHAs; browser candidate multipliers and tessellation are marked unvalidated. The screenshot/status copy for IMAGE_RECOGNIZING is not fully transcribed in text authority, so the Web card only uses the canonical display name in that state and marks copy verification pending.

## Browser tests and artifacts

Run:

    node scripts/verify_processing_optical_lab.mjs

The PR workflow also launches Chromium, verifies the route, exact three-state Manifest/P5 bindings, source hashes, controls and renderer behavior, exports all three state previews, the three-renderer comparison, ROI, and exploratory pixel heatmap, and packages a runnable local-browser bundle in a 30-day Actions artifact. Static visual review is still required before Stage B motion is unlocked. These browser results do not prove Android runtime, native Figma structure, source-photo identity, or a Frozen visual PASS.
