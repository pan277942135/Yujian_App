# Recognition Optical Lab · Stage A.1 Repair and Evidence

**Task:** `RECOGNITION_OPTICAL_LAB_STAGE_A1_VISUAL_CLOSURE`  
**Owner:** Work B · PR #166  
**Validated code head:** `f9184974a05aa6a66da8acbde450fbf2a5214bac`  
**CI:** [run 39198463901](https://github.com/pan277942135/Yujian_App/actions/runs/39198463901) · PASS  
**Artifact:** `Recognition-Optical-Lab-Static`, ID `12162907327`  
**Overall M1:** `NOT_PASS`

## Verified input baseline

| Input | SHA / status |
| --- | --- |
| PR #165 three-state visual authority | `31d1483b83038db78821625cd9339ad0e8c19ff3` |
| PR #166 head before Stage A.1 work | `48bb7d58c3fed8841b1ba770703a6ba3684264c4` |
| P1–P5 contract | Existing `Processing_Engineering_Import_Config_V1.json`; consumed in place, no parallel spec created |
| Frozen references | Existing State Set Manifest and unchanged 02/03/04 PNG bytes |
| Test photo | Existing P5 Android fixture plus registered, byte-identical Design Manager alias |

PR #166 remains Draft/Open/Unmerged and based on PR #165. Work B did not access Figma or Work Chrome, create RP01, create an importer, edit Android product code, edit Frozen images, restore Early, or change event gates. NH01 Work A remains owner of the sole unified importer.

## Repair and implementation

The page supports both repository-root `/design/manager/optical-lab/` and design-root `/manager/optical-lab/` deployment. The loader now resolves each root against existing repository paths, verifies config/Manifest/image SHA and dimensions, checks the photo registry and P5 fixture equality, uses the registered alias first with the existing fixture as fallback, and clears stale object URLs on retry.

The browser initialization fixes made during the Chromium loop were:

- Connect the diagnostics subtree before looking up its mount nodes.
- Insert the review toolbar relative to the live `layout` child.
- Drive a hidden viewport control with a DOM `change` event in responsive tests, without opening the advanced panel.
- Restore side-by-side mode before asserting the main comparison boards are visible.
- Read collapsed diagnostic row values through `textContent`.
- Persist the Playwright result into the exported asset manifest, changing browser and responsive gates to `PASS` only after the smoke file reports success and zero browser errors.

The verifier reuses the existing contract and checks all 27 declared source Git Blob SHAs, the FIT oracle, all seven P1 paths, exactly three state bindings, no Early asset, all three Frozen SHA/dimensions, and the P5 photo/registered alias. No P1–P5 source file or design contract was rewritten for this lab.

## Browser evidence

The passing Chromium smoke covered both deployment roots, seven verified runtime assets, all three Frozen product states, three same-input renderer candidates, live optical-layer parameter redraw, compare exports, Overlay/Wipe/Pixel Diff/ROI, experiment JSON export/import, no-BBox Fish Focus downgrade, and five responsive fixtures:

`390×844`, `320×640`, `360×800`, `412×915`, `800×360`.

Result file: `browser-smoke.json` in artifact `12162907327`. It reports `browserErrors: []` and exact source revision `f9184974a05aa6a66da8acbde450fbf2a5214bac`.

| Measure | Result | Interpretation |
| --- | ---: | --- |
| `android-current` vs `hardware-gradient` changed pixels | 48,847 | Same P5 fixture/state/viewport; renderer interaction only |
| `software-cache` vs `hardware-gradient` changed pixels | 48,368 | Same-input implementation difference only |
| Receiving Light tuning changed pixels | 41,169 | Live redraw works |
| Frozen vs Web mean RGB Δ | 68.18 | Exploratory; source-photo identity is unbound |
| Frozen vs Web pixels with RGB Δ ≥16 | 86.1% | Exploratory; not a quality score or fidelity pass |

The artifact contains the original Frozen reference screenshots, three Web state previews, same-source renderer grid, source-unbound side-by-side comparison, Overlay, Wipe, exploratory heatmap, ROI images, no-BBox Fish Focus image, responsive screenshots, the asset manifest, experiment JSON, and runnable `Recognition-Optical-Lab-Browser-Package.zip`.

To run the package locally, extract the browser package and serve its root:

```sh
python3 -m http.server 8765
```

Then open `http://localhost:8765/design/manager/optical-lab/`. It is a browser engineering preview route; it is not a Figma page or a deployed production Android view.

## Comparison conditions and limits

- `android-current`, `hardware-gradient`, and `software-cache` use the same P5 test photo, viewport, product state, paths and P1–P5 values. Changed-pixel counts only establish that the implementations differ.
- The P5 test photo is not attested as the photo embedded in the Processing Frozen screens. Frozen Overlay, Wipe, Diff and ROI remain exploratory. Do not optimize to the unrelated photo difference or call the reported Δ a visual score.
- P5 has no trusted detector BBox or production `FishSubjectResult` alpha Mask binding. Fish Focus is disabled; no synthetic contour, BBox, or mask comparison was added.
- Browser `shadowBlur` and offscreen filtering do not establish parity with Android `BlurMaskFilter` or software Canvas. Antialiasing, color composition, text and API 28 performance remain Android runtime questions.
- Stage B motion, continuous clock, RESOLVE, particles and recording remain locked pending human static route selection. This stage does not add animation or independent clocks.
- CAPTURED and DETECTING remain internal technical phases mapped to `IMAGE_RECOGNIZING`; the legacy user-copy difference remains RP-A07. Event gates and the three product visuals are unchanged.

## Gate record

| Gate | Result |
| --- | --- |
| P1–P5 contracts, all 27 declared source dependencies | `PASS` |
| Frozen byte integrity / exact three states / seven paths / no Early | `PASS` |
| P5 photo and registered alias identity | `PASS` (fixture-to-alias only) |
| Both browser deployment roots and runtime asset checks | `PASS` |
| Five responsive viewport fixtures | `PASS` |
| Same-source renderer and control interaction smoke | `PASS` |
| Frozen source-photo identity | `BLOCKED_UNBOUND` |
| Trusted Fish Focus BBox / production Mask comparison | `BLOCKED_NO_TRUSTED_BBOX_OR_MASK` |
| Static optical renderer selection | `PENDING_HUMAN_REVIEW` |
| Stage B motion / recording | `LOCKED_PENDING_STATIC_SELECTION` |
| Android Runtime / API 28 | `NOT_RUN` |
| Figma nodes / export / native visual QA | `NOT_RUN` |
| `M1_PROCESSING_FIGMA_ENGINEERING_HANDOFF` | `NOT_PASS` |

The browser package is ready for optical review and parameter exploration. It does not establish Frozen high fidelity, native Figma structure, Android Runtime, or an overall M1 PASS.
