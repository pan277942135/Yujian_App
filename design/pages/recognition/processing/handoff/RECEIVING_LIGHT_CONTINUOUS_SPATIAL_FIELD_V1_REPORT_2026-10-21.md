# Receiving Light Continuous Spatial Field V1 — Review Result

**Task:** `YUJIAN_RECEIVING_LIGHT_CONTINUOUS_SPATIAL_FIELD_V1`  
**Review result:** `BLOCKED` — the real photo composite still does not show a clearly perceptible, wide blue/gold field.  
**Android gate:** `NOT_RUN` — held because the browser visual gate did not pass.

## Provenance

| Item | Value |
| --- | --- |
| PR | [#166](https://github.com/pan277942135/Yujian_App/pull/166), Draft / Open / Unmerged |
| Continuous-field source SHA | `9731c15150e0acc873dc6e7b88e4283428ccefdc` |
| Browser run | [#39570298249](https://github.com/pan277942135/Yujian_App/actions/runs/39570298249) — success; deploy skipped |
| Evidence artifact | `EdgeGlow-Receiving-Light-Continuous-Spatial-Field-V1-39570298249-9731c15150e0acc873dc6e7b88e4283428ccefdc` (ID `12331432258`) |
| Artifact SHA-256 | `12ec3690a9a4afa5dd3721bcb4c6c8aa32afa1c0adaf325fbd32d45c3b04ffc1` |
| Acceptance Center SHA | `914d0c4cc6afeaf901998db45af438a41cfb1a12` |
| Renderer SHA-256 | `b243b22fa2a6b4dd62c34a3e392b3c121dfacd221b150e8b79bdac664565f518` |
| Source photo SHA-256 | `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef` (`1152×1536`) |

The first continuous-field capture was run [#39569488383](https://github.com/pan277942135/Yujian_App/actions/runs/39569488383), source SHA `70802bfade86e948b6fb3c97b610081f6a4c7443`, artifact ID `12331267363`. It showed that the linear field response produced a broad but weak overlay. The second run applies one Review-only response remap (`energyResponseExponent=0.52`) and preserves the first artifact as the comparison baseline.

The capture used the composed acceptance page and real Chromium. It verified the loaded page build SHA, Acceptance Center SHA, photo SHA, loaded script bytes and renderer status before saving the PNGs. The formal GitHub Pages site was not deployed.

## Fixed constraints

- P1 base Alpha: `0.07`; final transparent-layer cap: `17/255`.
- Receiving depth: `0.18` of the smaller photo dimension.
- Existing primary paths: `B1`, `G1`, `G3`, `B3`; path geometry SHA is identical between baseline and candidate.
- Existing colors, photo mapping, source-over composition and four Quiet Gap intervals are unchanged.
- No Frozen asset or P1–P5 contract was modified.
- Continuous Review-only texture is generated once per renderer cache key; it is not Android production code.

## Browser measurements at 390×844

Transparent layer and photo composite are both actual Chromium exports at `1152×1536`.

| Measurement | P1 baseline | Continuous B, second run |
| --- | ---: | ---: |
| Nonzero overlay pixels | 439,076 | 651,804 |
| Nonzero area | 24.81% | 36.84% |
| Mean Alpha among nonzero pixels | 6.95/255 | 4.59/255 |
| Median / P90 / P99 Alpha | 7 / 12 / 14 | 4 / 10 / 15 |
| Peak Alpha | 15/255 | 17/255 |
| Photo mean absolute RGB delta vs source | 0.534 | 0.358 |
| Bright-sky ROI mean RGB delta | 2.379 | 0.888 |
| Dark-water ROI mean RGB delta | 0.851 | 0.436 |
| Center-subject RGB delta | 0 | 0 |

The response remap improved the first continuous candidate (mean nonzero Alpha `3.60/255` → `4.59/255`, median `2` → `4`, photo RGB delta `0.242` → `0.358`). It still remains weaker than the existing P1 baseline in both tested photo ROIs, and manual inspection of the real composite does not show the required clear, wide blue/gold field. This is an engineering capture PASS and a visual gate failure; they are not interchangeable.

The field build took `110.6 ms` for the 390×844 run's 1152×1536 source photo and used an estimated `7,077,888` cached bytes. The same source texture was reused on the repeated render (`1` cache hit). These are browser measurements only.

## Quiet Gap A/B/C

- **A — hard:** all four checked gap centers had `0` nonzero pixels.
- **B — feathered boundaries:** all four checked gap centers had `0` nonzero pixels.
- **C — low field:** all four gaps were reached at low intensity; each measured peak was `2/255`.

The path geometry hash remained unchanged across A/B/C. Browser errors: `0`.

## Evidence files and SHA-256

All files and their hashes are in the artifact's `sha256-manifest.json`.

| Evidence | SHA-256 |
| --- | --- |
| Baseline transparent layer, 390×844 | `5699d4781dff965aea1eb9a7e97b060a540b712eb1d947f428a1f85a9b850cf6` |
| Baseline photo composite, 390×844 | `b073818aba759a166cd6bc9c883b69663f3333ceb111feb28446a8df54f6c429` |
| Continuous B transparent layer, 390×844 | `a3ee98ea8ddc25d9fa0985c961890b1ebffe63d2fcd317692bcab296440b8fef` |
| Continuous B photo composite, 390×844 | `2976af02fc23aa062886cd5959ecdd2e418643117fbf17674fe6bb90d8e3fd75` |
| Continuous B browser page, 320×640 | `3245c9b0805367fe66b7227a41a3d85b8f66e58839e777c8aa6ae1490f7560fd` |
| Continuous B browser page, 390×844 | `cbcb0ccd5e2e4651b01ddf31ee0e6de2990aca7f7b33f663e87611d8a34fb3aa` |
| Continuous B browser page, 1080×1920 | `6317d694d5d7e8df2af09c76d934b61b211b7a2c494f300ac68691d19c0e3816` |
| Continuous A photo composite, 390×844 | `32a37c51a75a1aff9fe7467d807cda79cadca85722f05e32bd5b5e879dba024d` |
| Continuous C photo composite, 390×844 | `c4ac47eae9fdde74dceb1e5f1257713a544244c3250780532dcb94b2b6af4154` |
| Baseline/B side-by-side | `d17aac2331eab4439129d11f7c4285fa95d0bfc08e82b99113df5c390aface22` |
| Baseline/B 4× difference image | `9fd40e13cde91b698733e31a7a6baa65632cb0c887570076eedb33cfb65382` |

## Decision

`BLOCKED`: the current profile uses too little of the P1 output Alpha budget across the broad field to make the receiving light clearly perceptible on the real photo. The Review-only response remap made a measurable improvement but did not meet the visual target. This execution stops after that single evidence-based remap; no Android implementation or API 28 runtime was started.

The next review decision is whether to authorize a new, bounded Review-only field-profile proposal within the existing P1 cap or to request a separately approved P1 output-Alpha contract change. No production token or Frozen contract is changed here.
