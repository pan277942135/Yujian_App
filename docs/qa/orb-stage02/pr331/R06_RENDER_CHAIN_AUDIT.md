# PR #331 R06 Render Chain Audit — Iteration 001

**Baseline:** `762b656591be3129bf2ec02768a65109371fe4ab`
**Scope:** G01/G02/A01/A02/C01 Stage02 path rendering, glass transmission and pixel evidence.
**Frozen:** path geometry, five IDs, colors/material assets, motion periods/phases, and 14 dp outer bound.

## Shared render chain

`OrbRuntimeProfileV1` and the frozen runtime profile supply path specs/materials. `OrbWaterLightRibbons` creates the existing `OrbFilamentPathRenderer`; it does not add path geometry. Each frame fills the existing arc-length LUT, computes the current movement and effective trail, clears current sample-validity bits, and constructs only contiguous runs with at least two valid samples. Each run is clipped to the path’s allowed radius after accounting for width, MAX stroke gain, refraction, and blur margins. Material layers calculate original alpha from active state, pulse, opacity profiles, cross-section, tail taper, longitudinal/head response, event response, alpha limits and end fade. Only `translucent_body` / `fine_filament` receive MAX body alpha and stroke gain.

The rendering order in `AIOrbCore` is: Back exterior → Raw Core → Back Glass Transmission → R03 optical effects → Camera Hero → Front. The glass pass is restricted to the Back renderer and path IDs G01/A01. Front paths G02/A02/C01 remain in the Front pass. The `debugExcludedPathId` is passed through each of these passes, so a single path ablation removes the same ID from exterior, transmission, and front rendering.

## Per-path source audit

| Path | Frozen plane | Exterior pass | Glass pass | Front pass | What the current source can report | What remains unmeasured |
|---|---|---|---|---|---|---|
| G01 | Back | Candidate R06 | MAX glass transmission | No | current phase U, effective trail, valid samples/runs, body alpha/paint/stroke, transmission and edge factors | same-frame final pixel contribution and human visibility |
| G02 | Front | No | No | Candidate R06 | same per-frame body/path metrics | same-frame final pixel contribution and human visibility |
| A01 | Back | Candidate R06 | MAX glass transmission | No | same per-frame body/path metrics | same-frame final pixel contribution and human visibility |
| A02 | Front | No | No | Candidate R06 | same per-frame body/path metrics | same-frame final pixel contribution and human visibility |
| C01 | Front | No | No | Candidate R06 | same per-frame body/path metrics | same-frame final pixel contribution and human visibility |

These entries describe the production renderer route. `drawnPathIds`, Body Paint Alpha, or a positive stroke value are `CANVAS_DRAW_SUBMISSION`/calculated data. They are not final screen pixels.

## Back glass attenuation: source and math

The old `GLASS_TRANSMISSION` body path multiplied:

```text
original body alpha × 0.80 × raw Core alpha × radialMask²
```

Then the Paint applied a radial gradient that also faded from the center toward the boundary. The repeated radial factor is confirmed in the baseline source. The corrected candidate uses:

```text
bodyAlphaBeforeGlass = max(originalBodyAlpha, MAXBodyContribution)
alphaBeforePaintMask = bodyAlphaBeforeGlass × 0.80 × rawCoreAlpha
paintMask = one radial gradient with a smooth clear edge
```

The source Core alpha is a separate silhouette check, not a replacement for the interior radial mask. The actual final alpha also includes Paint quantization, blur, Canvas blending, and the Android window composite.

For scale, the old and new radial factors were evaluated at normalized radius `r`, holding body alpha and raw Core alpha constant. “Gradient” is the Paint radial-gradient alpha from the frozen start/end edge parameters; values exclude `bodyAlpha` and source alpha:

| Radius | `interiorWeight(r)` | Paint gradient alpha | Old extra factor `0.8 × interiorWeight² × gradient` | New factor `0.8 × gradient` | Evidence |
|---:|---:|---:|---:|---:|---|
| 0.200 (center region) | 1.0000 | 1.0000 | 0.8000 | 0.8000 | L1 math from source |
| 0.405 (middle) | 0.5000 | 0.4995 | 0.0999 | 0.3996 | L1 math from source |
| 0.455 (near edge) | 0.0369 | 0.0452 | 0.000049 | 0.0362 | L1 math from source |

At the middle sample, removing the duplicate factor raises the normalized glass-mask portion from about 0.10 to 0.40. Near the edge the single gradient still suppresses transmission. These are mathematical factors, not measured pixels and not proof that G01/A01 were visually absent because of this mechanism.

The glass path still uses the existing shared `OrbInteriorOpticalField` for local direction/phase; only the run's local geometry is refracted. The 0.006D displacement, 0.65 dp blur, and 14 dp clipping budget remain bounded. The filter is applied to the same G01/A01 IDs; it does not convert them to Front paths.

## Stage02 evidence and pixel attribution

The old 264191.mp4 recording shows aggregate diagnostic mode changes, but does not isolate all five paths or hold a validated capture frame for an ablation. No old per-path PixelCopy ZIP is accessible. Consequently:

| Path | Prior valid sample/run/paint data | Prior final pixel contribution | Current iteration final pixel data |
|---|---|---|---|
| G01 | NOT_AVAILABLE | NOT_MEASURED | NOT_MEASURED; exporter/test implementation added, not yet run on device |
| G02 | NOT_AVAILABLE | NOT_MEASURED | NOT_MEASURED; exporter/test implementation added, not yet run on device |
| A01 | NOT_AVAILABLE | NOT_MEASURED | NOT_MEASURED; exporter/test implementation added, not yet run on device |
| A02 | NOT_AVAILABLE | NOT_MEASURED | NOT_MEASURED; exporter/test implementation added, not yet run on device |
| C01 | NOT_AVAILABLE | NOT_MEASURED | NOT_MEASURED; exporter/test implementation added, not yet run on device |

The revised Debug export captures one `R06_FULL` and `R06_WITHOUT_<path>` for each of the five IDs under the same pinned StageProgress, geometry, source image, SceneFrame time, and optical sample. The returned report measures changed pixels, mean difference, bounds, largest connected component extent, and per-path Canvas metrics. It labels the result `PIXEL_CONTRIBUTION_MEASURED_NOT_HUMAN_VISIBILITY`; actual use remains pending device execution.

## Iteration 001 R06 classification

| Finding | Classification | Evidence grade | Status |
|---|---|---|---|
| G01/A01 body uses a squared radial mask and a later radial-gradient edge fade | R06-D | L0 source + L1 math | Source mechanism confirmed and corrected; final pixel causality unconfirmed |
| G01/A01 source Core alpha is sampled and applied separately | R06-H candidate | L0 source | Coordinate mapping matches `coreSize / 0.84` image scale; final alignment not device-measured |
| G02/A02/C01 body visibility is limited by path length, alpha, width or local contrast | R06-U | No per-path pixel bundle | No root cause assigned |
| Five path identities map to the expected depth passes | Source route confirmed | L0 source | Does not mean all five have visible pixels at any one sample |

**Current R06 result:** `GLASS_MASK_SOURCE_FIX_PIXEL_DELTA_NOT_MEASURED`. The three front paths remain evidence-unlocated; do not increase their global Alpha based on this source audit.
