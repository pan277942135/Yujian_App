# M1 P1 · PhotoViewport / Edge Field Engineering Trace

Status: **ENGINEERING TRACE COMMITTED · NOT A VISUAL PASS**

PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166)  
Base authority: PR #165 head `31d1483b83038db78821625cd9339ad0e8c19ff3`  
P1 config commit: `07b1b8961009773b1e2e889caa418f9e9cea9671`  
Machine input: [Processing_Engineering_Import_Config_V1.json](Processing_Engineering_Import_Config_V1.json)

This is a derived trace for Android implementation and the NH01 unified importer. The Frozen contracts and Kotlin runtime remain authoritative. It does not create or claim Figma nodes, an RP01 page, a native Figma structure pass, or a pixel comparison.

## 1. PhotoViewport coordinate contract

**Authority:** `Recognition_Processing_Visual_State_Set_V1_2.json`, V3.1 `Layout_Contract`, AI Edge Field `Rendering_Contract`, and Android `RecognitionMediaPlanner` / `RecognitionImageTransform`.

- Normalize the EXIF-oriented source photo to `[0,1] × [0,1]`.
- Processing uses a primary `FIT` transform and full source rect `[0,0,1,1]`. A detector bbox arriving later does not change it.
- For viewport `W×H` and source `w×h`: `s=min(W/w,H/h)`; drawn size is `(s·w,s·h)`; centered translation is `((W-s·w)/2,(H-s·h)/2)`.
- Source point mapping is `(tx + x·drawnW, ty + y·drawnH)`. A FIT photo's visible rect excludes letterbox bands.
- A same-source `CROP` image may fill FIT bands as a decorative background at alpha `.16`. It is not used for bbox, mask, contour, halo, or Edge Field geometry.
- Edge Field normalized points map to the actual `photoContentRect`; authored control points outside `[0,1]` stay outside and are clipped at that rect. Do not clamp path overshoot.
- Safe drawing insets affect Back and Status controls. They do not remap the photograph, fish focus, or Edge Field.

Concrete test oracle already in source: source `2000×1000`, viewport `1000×1000`, FIT gives `scale=.5`, `translation=(0,250)`, `photoContentRect=[0,250,1000,750]`. The existing transform tests also assert CROP/FIT, unclamped overshoot, shared bbox/source-pixel mapping, and responsive viewports down to `320×640`.

## 2. Seven frozen path records

All coordinates below are normalized to the visible photo content; order is `start → control1 → control2 → end`. Geometry, widths, primary seeds, ratios, and weights are copied into the machine input.

| Path | Island / role | Cubic points | Core | Seed | Segment / weight |
| --- | --- | --- | ---: | ---: | --- |
| B1 | B_UR primary | `(.78,-.02),(.94,.06),(1.02,.22),(.96,.42)` | 1.2dp | .00 | .30 / 1.00 |
| B2 | B_UR secondary fragment (must not connect B_UR/B_LR) | `(1.01,.18),(.92,.31),(.95,.52),(1.02,.68)` | 1.0dp | runtime .07 | .18 / .30 |
| B3 | B_LR primary | `(1.02,.61),(.92,.75),(.84,.89),(.66,1.02)` | 1.2dp | .47 | .20 / .54 |
| B4 | bottom secondary fragment | `(.22,1.02),(.38,.95),(.52,.95),(.66,1.01)` | .8dp | runtime .11 | .14 / .22 |
| G1 | G_UL primary | `(-.02,.18),(.03,.08),(.12,.02),(.26,-.02)` | 1.1dp | .12 | .26 / .78 |
| G2 | G_UL secondary fragment (must not connect G_UL/G_LL) | `(-.02,.33),(.04,.47),(.02,.62),(-.01,.78)` | .9dp | runtime .09 | .16 / .26 |
| G3 | G_LL primary | `(-.01,.74),(.08,.86),(.20,.95),(.38,1.02)` | 1.2dp | .64 | .22 / .63 |

Frozen topology keeps four disconnected islands, three secondary fragments and four Quiet Gaps. B2/G2 sit near their respective upper islands but never bridge adjacent primary islands:  top `.32–.68`, left `.38–.62`, right `.46–.58`, bottom `.40–.62`. B1/G1/B3/G3 are the only primary movers. No closed frame, border reconstruction or central crossing is permitted.

B2/B4/G2 runtime seeds are not silently treated as frozen: the Android values are `.07/.11/.09`; superseded V1.1 values are `.23/.71/.39`; V1.2 Motion freezes only the four primary seeds. These remain `REVIEW_REQUIRED_CONTRACT_GAP` before animated Figma prototyping.

## 3. Optical drawing contract

Back-to-front in the clipped photo space:

1. Local Receiving Light: four broad local radial ovals, with soft falloff; no global tint.
2. Primary Outer Bloom: visible primary segment only, 10dp, 5dp blur.
3. Primary Mid Glow: same centerline, 4dp, 2dp blur.
4. Core: same centerline, path-specific width; round caps/joins.
5. Hairline echoes: four companion geometries; B_UR alone may have a micro hairline; no outer bloom.
6. Hot Nodes: B_UR main plus at most one weak secondary node.
7. Sparse Particles: fixed eight-anchor set, never eight equal visible dots.

The Kotlin renderer uses one alpha chain for primary paths: `base × islandWeight × StateStrength × entry × quality`; the frozen Rendering Contract additionally requires `× ResolveStrength`. Exact current values, path controls, anchors, and token references live in the JSON rather than a parallel prose spec.

## 4. Concrete Android repairs / unresolved evidence

- **RP-A01 · Receiving Light alpha:** Kotlin base is `.13 × common × islandWeight` (cap `.14`); the frozen Rendering Contract says pre-multiplier `.03–.07`. Reconcile only with the three-state Frozen target and real FULL runtime capture. Do not infer the removed Early upper bound.
- **RP-A02 · B1M micro hairline:** Kotlin visible ratio is `.12`, below the frozen `.15–.32` range; verify the ratio against the contract's local-primary-span basis before changing it.
- **RP-A03 · Secondary seeds:** resolve B2/B4/G2 source authority before motion is generated in Figma.
- Companion hairline ratio basis also needs a visual check: Android extracts a segment by ratio of each child path's measured length, while the contract describes length relative to the local Primary span.

## 5. Existing tests and result

| Existing test | Contract covered | This handoff run |
| --- | --- | --- |
| `RecognitionImageTransformTest` | FIT/CROP, FIT rect, photo-coordinate overshoot, common source transform and six viewport sizes | Source reviewed; tests not run |
| `RecognitionProcessingMediaPlanTest` | FIT full-source policy, decorative CROP, transform invariant on bbox arrival, five aspect cases and compact viewport | Source reviewed; tests not run |
| `RecognitionOpticalSegmentRuntimeTest` | measured Bézier segment length and correct wraparound split | Source reviewed; tests not run |

The source tests are existing evidence fixtures, not a new second test suite. No Gradle/API28 run or screenshot comparison was performed here. Frozen image hashes in the V1.2 manifest are recorded as declared hashes; this pass did not download or recompute binary assets.

## 6. Gate

P1's structured engineering trace is ready for review. `ENGINEERING_HANDOFF_READY` for the complete P1–P5 set remains pending. `RP_FIGMA_STRUCTURE`, native Figma generation, export and Frozen visual QA remain **NOT_PASS / NOT_RUN** until Work A's unified plugin passes NH01 minimum import validation and a real RP01 page is inspected. PR #166 remains Draft and unmerged.
