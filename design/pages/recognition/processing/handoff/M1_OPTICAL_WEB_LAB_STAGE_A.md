# Recognition Optical Lab — Stage A Engineering Record

**Task:** `RECOGNITION_OPTICAL_WEB_LAB_V1`  
**Stage:** A · static optical validation  
**Result:** `BROWSER_AUTOMATION_PASS`; `STATIC_VISUAL_REVIEW_PENDING`  
**Overall M1:** `NOT_PASS`

## Baseline and implementation commits

| Input or change | SHA |
| --- | --- |
| PR #165 three-state visual authority | `31d1483b83038db78821625cd9339ad0e8c19ff3` |
| PR #166 P1–P5 handoff before Optical Lab | `f6ee9ddebb6036e9f3ce5629e5ff9f9b3c10a209` |
| Pre-lab Processing config blob | `b1646d1379682ad3c0df9e628792e180d1f5437e` |
| Optical Lab implementation | `4bf6a97fe5a45897aef35594132062c15c879651` |
| Frozen Manifest assertion correction | `e4f8748837e600e35b49ff2c8fd7ad2c8bd6d80e` |
| Renderer/control browser checks | `0ed237cc087f94816c16cf7b3ac639f8c41b847c` |
| Runnable-preview packaging | `432d3a3179e5a421341e1697d88a662e4f77962d` |

The Lab reads the existing P1–P5 config and three-state Manifest. P1–P5 reports and all three Frozen PNG files are unchanged. The existing Import Config now contains an explicitly non-normative `optical_lab_runtime_snapshot` extension for Android-only runtime geometry and browser candidate parameters; each Android source binding carries its Git blob SHA. Candidate values are labeled as engineering inputs, not Frozen design.

## Optical and component implementation

| Area | Browser implementation | Authority / limit |
| --- | --- | --- |
| PhotoViewport | One EXIF-oriented FIT transform maps the source image, BBox, mask and field. FIT bands are excluded from overlays; same-source CROP is only a decorative underlay. Cubic overshoot is preserved and clipped to `photoContentRect`. | P1 `p1_photoviewport`; the `2000×1000 → 1000×1000` oracle is asserted. |
| AI Edge Field | Reads exact P1 B1/B2/B3/B4/G1/G2/G3 cubic control points, family, weights, seeds, visible ratios, widths and stack order. Receiving Light and companion/micro hairline geometry absent from the P1 JSON are sourced from `RecognitionAmbientField.kt` blob `1969409c2f903a16ea38ed2f73b705daa424c050`. | All seven paths are contract data. B2/B4 secondary seed candidates retain `REVIEW_REQUIRED`. No nodes or particles are drawn in Stage A. |
| Render candidates | `android-current` uses Canvas shadow blur as a browser approximation of Android MaskFilter. `hardware-gradient` uses Path, radial/linear gradients and config-driven layered strokes without a blur filter. `software-cache` draws to offscreen RGBA and uses Canvas filter blur before compositing. | Width/alpha multipliers and 220-point cubic subdivision are tagged browser-only candidates in the config extension; they are not Frozen parameters. |
| Fish Focus | Requires a valid, user-attested real detector BBox. A-level contour uses an imported alpha mask mapped through P2’s expanded subjectBox; missing mask falls back to B; D3 forces B; D4 uses C. No BBox means no Fish Focus. | The repository test photo SHA is `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`, but its P5 binding is `none_trusted` for BBox/Mask. Production Mask comparison therefore remains pending. |
| Status Card / state mapping | Three product states bind one-to-one to the Manifest’s three Frozen images. CAPTURED/DETECTING both map to IMAGE_RECOGNIZING; OUTLINE to FISH_LOCATED; CLASSIFYING to SPECIES_RECOGNIZING. The first status card shows the canonical “图片识别中” label because its exact Frozen subtitle is not transcribed. | P3/P5 remain authoritative. RP-A07 copy mismatch is still open; no phase gate or event behavior changed. |
| Quality / responsive | FULL/BALANCED/LITE, D0–D4, Reduce Motion and P4 viewport fixtures are exposed. Stage A checks FIT closure at 320×640, 720×1600, 1080×2340 and browser preview sizes including 390×844 and 800×360. | CSS logical pixels are not claimed to equal Android dp. Safe-area and font parity need device review. |

P5 already contains the unified importer’s component tree, Compose mapping, Token references, three Frozen image bindings, runtime photo binding and recognition data bindings. The page reads those bindings directly; it does not create a competing contract or Figma plugin.

## Browser verification and artifacts

PR workflow run [#39190440709](https://github.com/pan277942135/Yujian_App/actions/runs/39190440709) completed successfully. The first run, #39189938076, failed only because the verifier expected the wrong Manifest lookup string; commit `e4f8748837e600e35b49ff2c8fd7ad2c8bd6d80e` corrected that test assertion. Later runs #39190028903, #39190298288, and this PR run passed. It checked:

- Exactly three product states, three Frozen bindings and seven Edge Field paths; no Early asset.
- SHA-256 and dimensions of the Frozen images, SHA/byte count of the test photo, the FIT oracle and Android source Git blob SHAs.
- Chromium loading with no page errors; state switching, renderer pixel changes, live Alpha redraw, side-by-side/overlay/wipe/diff, ROI and export controls.
- The responsive preview sizes listed above and the no-BBox/no-focus downgrade.

The smoke run reported 48,847 changed pixels for hardware-gradient versus current Android approximation, 42,461 for software-cache, and 47,474 after the live Alpha adjustment. The exploratory Frozen comparison reported mean RGB difference 68.19 and 86.2% pixels at or above the test threshold. These are interaction/diagnostic readings only: the P5 test photo is visibly not bound as the Frozen screenshots’ original source. They are not a visual pass.

Actions artifact [Recognition-Optical-Lab-Static](https://github.com/pan277942135/Yujian_App/actions/runs/39190440709) (artifact ID `12159453683`, retained through 2026-11-18) contains the three Frozen reference captures, three Web state captures, four-up candidate comparison, exploratory pixel heatmap, ROI comparisons, `browser-smoke.json`, and `Recognition-Optical-Lab-Browser-Package.zip`. The package preserves the exact repo-relative paths needed to run locally:

```sh
unzip Recognition-Optical-Lab-Browser-Package.zip
cd <extracted-package-root>
python3 -m http.server 8765
```

Open `http://localhost:8765/design/manager/optical-lab/`. The existing Design Manager also links to this route on the PR branch. No shared GitHub Pages deployment was made.

## Decision and remaining work

Provisional Android candidate for the next static review: `hardware-gradient`, because its Path and Gradient operations can be mapped to Canvas/Shader without relying on browser blur. It is **not selected or validated** yet. Browser shadowBlur and Canvas filter are not equivalent to Android Paint blur behavior; all three candidate renderers still need human optical review and API 28 Android runtime measurement. A gradient candidate that looks correct in Chromium may not match Android antialiasing, colors or performance.

Fish Focus screenshots with a production mask remain blocked on trusted detector BBox and FishSubjectResult alpha-mask data. Frozen/source-photo identity and photo provenance/rights remain unverified. Status typography, safe-area rendering and Back touch target have not been validated on Android.

Stage B Play/Pause, shared clock, state transitions, RESOLVE preview and recording remain locked until static optical review selects a route. Figma structure/native nodes and Figma export/Geometry/Glyph/Optical ROI/Pixel Diff/independent QA remain pending Work A’s unified importer and real design access. No Figma nodes were claimed, no Android product code was changed, and PR #166 remains Draft/Open/Unmerged. Overall `M1_PROCESSING_FIGMA_ENGINEERING_HANDOFF` remains `NOT_PASS`.
