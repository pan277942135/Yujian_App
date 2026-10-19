# Recognition Processing · Optical Lab

An isolated browser engineering preview for the existing Processing P1–P5 contract. It reuses `Processing_Engineering_Import_Config_V1.json`, the State Set Manifest, all three Frozen PNGs, the registered P5 test-photo fixture and the existing runtime source bindings. It does not modify the Frozen references, create another Figma importer, or claim a Figma/Android visual pass.

## Run the preview

Use the `Recognition-Optical-Lab-Static` artifact attached to the latest PR #166 workflow run. Extract `Recognition-Optical-Lab-Browser-Package.zip`, then from its root run:

```sh
python3 -m http.server 8765
```

Open `http://localhost:8765/design/manager/optical-lab/`. The package preserves the repository paths and includes both the P5 Android fixture and its byte-identical Design Manager publication alias. It supports the repository-root route above and a design-root deployment at `/manager/optical-lab/`. Serve over localhost or HTTPS; `file://` prevents the page from fetching and SHA-verifying the existing data.

The repository route is linked from the existing Design Manager. The Actions package is a reproducible local preview, not a published Figma page.

## Stage A · static controls

- Switch the three frozen product visuals: `IMAGE_RECOGNIZING`, `FISH_LOCATED`, `SPECIES_RECOGNIZING`.
- Compare the Frozen image and Web candidate side by side, at 50% overlay, or with a wipe. Advanced controls include exploratory pixel heatmap, ROI enlargement, and the three same-source renderer candidates.
- Toggle PhotoViewport, Receiving Light, Primary Outer Bloom, Primary Mid Glow, Primary Core, Companion/Micro Hairlines, Fish Focus Halo, Fish Focus Contour, and Status Card independently.
- Use the quick layer controls or advanced sliders for alpha, width, blur, falloff, tail length/position, focus intensity, State Strength, and safe-area inputs. Restore P1–P5-derived defaults with **恢复默认参数**.
- Set FULL/BALANCED/LITE, D0–D4 and Reduce Motion; inspect the P4 responsive fixtures.
- Export the current preview, comparison and ROI PNGs, verified asset manifest and experiment parameter JSON. Importing an experiment JSON restores its renderer, state, viewport, quality and parameter values.

The default photo is the existing P5 Android test fixture. Its P5 SHA-256 is checked at load and its published Design Manager alias is verified byte-identical in CI. The registry scopes this asset to real-photo container examples; it does not bind it to the Processing Frozen screenshots. Frozen/Web pixel metrics therefore stay exploratory and the Frozen visual gate remains blocked.

The fixture has no trusted detector BBox or production FishSubjectResult Mask. Fish Focus is disabled without a valid, explicitly attested BBox; no synthetic contour or mask is substituted. A true mask comparison still needs authorized production detector output.

## Renderer comparison

The candidates consume the same source fixture, viewport, state and P1 geometry. Candidate values already stored in the P1–P5 runtime extension remain explicitly experimental.

| Candidate | Browser drawing | Android relevance / limit |
| --- | --- | --- |
| `android-current` | Existing geometry with browser `shadowBlur` approximation | Does not reproduce Android `BlurMaskFilter` exactly. |
| `hardware-gradient` | Path, radial/linear gradients and layered low-alpha strokes without a blur filter | Preferred API 28 research candidate for a device check; not selected or validated by this browser preview. |
| `software-cache` | Offscreen RGBA Canvas cache and browser Canvas filter blur | Browser kernel and compositing are not an Android software Canvas result. |

The browser smoke confirms that the candidates render different pixels on the same input; that is an interaction/implementation check, not evidence of optical superiority. Antialiasing, color composition, font rasterization and blur differ from Android.

## Stage B gate

Stage B Play/Pause, timeline, continuous clock, state transitions, RESOLVE decay, keyframe export and recording are kept locked until the static optical route is reviewed and selected. No particles or animation are rendered in this Stage A build.

## Reproduce checks

```sh
node scripts/verify_processing_optical_lab.mjs
node scripts/smoke_processing_optical_lab.cjs
```

The PR workflow runs the source verifier and Playwright Chromium against both supported deployment roots. It records screenshot evidence for all three states, renderer comparisons, overlay/diff/ROI, no-BBox Fish Focus downgrade, responsive fixtures, the verified asset manifest and a runnable ZIP. These checks do not establish a Frozen visual pass, source-photo identity, Android Runtime pass, Figma native structure, or overall M1 PASS.
