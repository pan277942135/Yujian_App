# Recognition Optical Review — Frozen Visual & Evidence Rules V1

**Decision ID:** `YUJIAN_RECOGNITION_OPTICAL_REVIEW_FREEZE_V1`  
**Decision date:** 2026-10-20  
**Decision status:** `FROZEN` for visual hierarchy, review routing, evidence classification, and acceptance gates  
**Implementation status:** `REVIEW_REQUIRED` — this freeze does not imply production or runtime acceptance  
**Repository:** `pan277942135/Yujian_App`  
**Related PR:** [#166](https://github.com/pan277942135/Yujian_App/pull/166)

## 1. Authority and scope

This decision records the frozen rules for reviewing the Recognition Processing optical effect. It supplements, but does not supersede, the existing V1.2 three-state authority, the frozen `AI_Edge_Field_V1_Rendering_Contract.md`, the P1 PhotoViewport contract, the P2 Fish Focus contract, or the P4 Motion / Quality / Responsive contract.

Do not silently change Frozen PNGs, product-state semantics, detector/classifier/result gates, existing token values, or the seven-path topology while implementing this decision. If a numerical conflict exists, record it as an explicit gap against the owning contract; do not create a competing value in this decision record.

## 2. Canonical review entry — frozen

**The existing GitHub Pages Design Manager acceptance center is the canonical review entry.** Do not replace it with an isolated HTTPS Site or describe an isolated Site as the project's primary acceptance surface.

- Canonical acceptance page: [EdgeGlow Fish · Static + Dynamic Review](https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/)
- Review route opened from that page: [Optical Review](https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/)
- The acceptance page's **“打开静态 / 动态预览”** entry must continue to lead to the nested Optical Review route.
- Other hosted previews may be supplementary evidence only. They must be labelled as supplementary and must not displace the Design Manager page as the canonical entry.

The currently reported Design Manager browser build was verified by workflow [#172](https://github.com/pan277942135/Yujian_App/actions/runs/39394414852), at page-code source SHA `5ce46cc28b0af1436cc915bc60ea5520d60aede7`. That workflow result is browser/build evidence only, not an optical fidelity, Android Runtime, or overall M1 PASS.

## 3. Frozen visual hierarchy

### 3.1 Photo viewport and coordinate mapping

- Apply EXIF orientation before deriving display coordinates.
- FIT/crop resolution must be completed before mapping overlays. Edge Field and Fish Focus must use the same authoritative visible-photo transform and `photoContentRect`; do not derive a second coordinate system for either effect.
- The review fixture is the registered 1152 × 1536 photo, EXIF orientation 1, FIT / NO CROP, SHA-256 `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`.
- This fixture identity does **not** prove that the fixture is identical to the photo embedded in the Frozen Processing screenshots. Do not claim a Frozen-source pixel comparison until that identity is verified.
- System safe insets may position system controls; they must not remap the photo, Edge Field, bbox, mask, contour, or halo.

### 3.2 Photo-edge Edge Field — blue/gold optical structure

- The Edge Field belongs around the **four edges of the photograph**, as four disconnected luminous islands. The photograph remains the dominant visual object.
- Preserve the existing four-island topology, seven registered paths, and Quiet Gaps. Use the frozen Rendering Contract's shared geometry, color/alpha tokens, layer order, and path-specific weight hierarchy.
- Render local receiving illumination, soft bloom, mid glow, core, restrained hairline echoes, sparse nodes, and particles as defined by the existing contract. A luminous segment travels on the path; the whole path is not continuously painted.
- Do not draw a closed perimeter, reconstruct a full frame, use four independent corner widgets, add a global blue/gold tint, equalize all island brightness, or solve weak visibility by adding more lines.
- The result must read as soft light interacting with the photograph—not thin wires, a rectangular neon border, or fantasy lightning.

### 3.3 Fish Focus — independent warm-gold subject highlight

- Fish Focus is a separate renderer and visual layer from the photo-edge Edge Field. It highlights the **fish body**, not the photo frame.
- Keep the fish contour, warm-gold halo, and any luminous tail aligned to the same source-photo transform and to the actual subject boundary. Reuse the existing P2 tokens and formulas; do not invent parallel colors or geometry.
- Production contour requires a trusted detector bbox plus the production `FishSubjectResult` mask/alpha and generated contour. A bbox rectangle must never be presented as a production fish contour.
- The current manual reference contour is `MANUAL_REFERENCE_CONTOUR` and is **review-only, not a production mask**.
- If trusted bbox/mask provenance is missing, Fish Focus remains disabled or uses only the explicitly permitted bbox-only fallback; it must not fabricate a production contour. The current production Fish Focus gate remains `BLOCKED`.

### 3.4 Product states and status-card material

- Exactly three product visuals remain authoritative: Frozen 02 / 03 / 04. Do not add an Early visual, replacement fourth state, or new product state.
- `CAPTURED` and `DETECTING` remain internal pipeline phases mapped to the single product visual `IMAGE_RECOGNIZING`; preserve the real event gates and minimum phase beats.
- The status card must not regress to a dark rectangular box plus a progress ring as its defining material. Preserve the frozen state hierarchy, typography, semantic tokens, and restrained material treatment from the existing design authority.
- Never invent progress, ETA, detector outcomes, classification results, confidence, bbox, or fish focus to make the preview appear complete.

### 3.5 Motion, clock, and quality degradation

- Use the single continuous 10,000 ms Edge Field clock across product-state changes. Keep SegmentOffset, StateStrength, and ResolveStrength as separate inputs as defined by P4.
- RESOLVE remains 200 ms with the frozen `(1-p)^2` ResolveStrength. Freeze the current path offset at Resolve start and fade Edge Field, Fish Focus, and Status using the same Resolve strength; do not add an extra wait before handing off to Result.
- FULL / BALANCED / LITE, D0–D4, and Reduce Motion may reduce detail and cost, but must preserve photo dominance, four-island identity, Quiet Gaps, primary geometry, and product-state semantics.
- These are implementation requirements, not a claim that the current Android runtime already satisfies them. The documented Resolve synchronization gap remains open until code and runtime evidence close it.

## 4. Six-step closure sequence

Execute and record evidence in this order:

1. **Coordinate closure:** prove the displayed photo, Edge Field, and fish subject use one correct transform; no vertical offset or crop mismatch.
2. **Edge Field optical closure:** validate soft receiving light, brightness hierarchy, glow falloff, and gradient luminous segments against the Frozen contract.
3. **Fish Focus closure:** validate a smooth, source-aligned production contour and restrained warm-gold halo using a trusted bbox and production mask.
4. **Status-card closure:** remove the dark-box-plus-progress-ring visual failure while preserving the three frozen product states and real status semantics.
5. **Clock/cache/performance closure:** validate the shared clock, Resolve freeze/fade, cache behavior, FULL/BALANCED/LITE degradation, Reduce Motion, and compact viewports.
6. **End-to-end acceptance:** provide recording, frame table, source SHA, evidence images, viewport results, and an API 28 Android Runtime result. Browser smoke alone cannot close this step.

## 5. Evidence classes and current gates

| Gate | Current status | Rule |
|---|---|---|
| Canonical Design Manager entry | `CONNECTED` | Use the existing GitHub Pages acceptance center and nested review route. |
| Browser build / interaction smoke | `PASS` for reported workflow #172 | Proves only the checks executed by that workflow. |
| Human static optical selection | `REVIEW_REQUIRED` | Do not select a winner or report optical fidelity PASS without review. |
| Frozen source-photo identity | `BLOCKED_UNBOUND` | The registered fixture is not yet proven identical to the Frozen screenshot photo. |
| Production Fish Focus | `BLOCKED` | No trusted production bbox + mask evidence has been established for this review case. |
| Case B contour | `REVIEW_PROXY_NOT_PRODUCTION` | Proxy evidence must never be promoted to production-mask evidence. |
| Figma native nodes / Frozen export / independent visual QA | `NOT_RUN` unless separately evidenced | Do not infer these from browser preview success. |
| Android API 28 Runtime / recording / performance | `NOT_RUN` unless separately evidenced | Require exact source SHA and runtime evidence. |
| Overall `M1_PROCESSING_FIGMA_ENGINEERING_HANDOFF` | `NOT_PASS` | No overall PASS until all applicable gates are actually closed. |

### Evidence naming rules

- Every report must distinguish `PASS`, `REVIEW_REQUIRED`, `BLOCKED`, `NOT_RUN`, and `REVIEW_PROXY_NOT_PRODUCTION`; do not collapse them into one success label.
- A source SHA, workflow run, successful browser smoke, or deployed URL proves only the scope that was tested.
- Any new screenshot, recording, or pixel metric must identify the actual source SHA, fixture identity, viewport, state, and renderer. Do not relabel historical evidence as current-build evidence.
- No fabricated screenshots, Figma node IDs, production masks, API 28 results, APKs, or Pixel Diff PASS claims.

## 6. Change control

This decision is frozen for the review route, optical visual hierarchy, evidence classification, and acceptance gates. Changes require a new versioned decision record and an explicit explanation of which frozen authority is superseded. This document does not authorize merging PR #166 or changing `main`.

## 7. Traceability

- Design Manager acceptance center: https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/
- Optical Review: https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/
- PR #166: https://github.com/pan277942135/Yujian_App/pull/166
- Browser workflow #172: https://github.com/pan277942135/Yujian_App/actions/runs/39394414852
- P1 source contract: `design/pages/recognition/processing/handoff/M1_P1_PHOTOVIEWPORT_EDGE_FIELD.md`
- P2 source contract: `design/pages/recognition/processing/handoff/M1_P2_FISH_FOCUS.md`
- P4 source contract: `design/pages/recognition/processing/handoff/M1_P4_MOTION_QUALITY_RESPONSIVE.md`
- Rendering authority: `design/pages/recognition/processing/components/AI_Edge_Field_V1_Rendering_Contract.md`
