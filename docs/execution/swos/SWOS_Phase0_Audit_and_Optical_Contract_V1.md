# SWOS Phase 0 — Current-State Audit & Optical Contract V1

**Task ID:** YUJIAN_SWOS_PHASE0_AUDIT_AND_OPTICAL_CONTRACT_V1
**Status:** AUDIT SOURCE · VERSIONED CONTRACTS AND CONTROL-PLANE CLOSURE ARE IN `PHASE0_CLOSURE_REPORT_V1.md` · NOT YET MAIN-AUTHORITATIVE
**Repository:** pan277942135/Yujian_App
**Audit baseline:** main @ 888706fda16d6f343f9bb4e02463f3b7a3526c31
**Runtime status:** NOT_RUN for SWOS; existing RR09 runtime evidence does not prove SWOS acceptance
**Scope:** Current-state audit, optical design contract proposal, and cross-page continuity definition. No Android source changes in Phase 0.

## 1. Product decision captured

Sunlit Water Optical System (SWOS) is the proposed original optical language for Recognition Processing. The uploaded HarmonyOS Xiaoyi launch video is only a motion-quality reference. It is not the recognition journey, not a visual blueprint to copy, and not authority for the Processing state machine.

SWOS draws its identity from sunlight interacting with fishing water:

**晨光入水 → 水纹扩散 → 水光寻鱼 → 鱼体浮光 → 外围水光消退 → 鱼体金光自然接续至识别结果 → 金光凝结为中文鱼种名 → 余辉消散。**

The result handoff is part of this single optical story. It must not look like one animation ending and an unrelated Result animation starting.

User-confirmed constraints captured here:

- Processing visual timeline target: 2800 ms — CAPTURED 350 ms, DETECTING 600 ms, OUTLINE 600 ms, CLASSIFYING 1250 ms. The final 200 ms resolve is a visual subphase inside CLASSIFYING, not an extra state or added delay.
- The Chinese fish species name is the only species title revealed in the Result motion. Do not show or animate the English scientific name in the Result title block. Do not reserve an empty English-name row. Fish Guide taxonomy data is outside this presentation change.
- Preserve the real captured photo, real recognition semantics, existing route, Result layout hierarchy, CTA availability, HIGH-only one-shot reveal, Reduce Motion, and failure-state behavior.
- No HUD, radar, scanner frame, closed neon border, full-frame color wash, confetti, fireworks, or assistant-wakeup imitation.

This document is a review proposal, not yet the canonical frozen authority. Existing frozen contracts are not silently overwritten. After review, the approved contract must be versioned and propagated through the Control Plane before implementation begins.

## 2. Live baseline and evidence

### 2.1 Canonical baseline

- Current main SHA at audit: 888706fda16d6f343f9bb4e02463f3b7a3526c31.
- Exact-SHA Control Plane Admission: PASS — Run 39710733994.
- Design Governance on that SHA: PASS — Run 39710734225.
- Design Manager Pages on that SHA: PASS — Run 39710733999.
- Android CI and Camera Runtime Harness runs for the same SHA were pending when this audit queried Actions. They are not SWOS runtime evidence.
- Main has no PROJECT_MASTER_STATE.yaml. This matches the repository rule that dynamic project/work state is branch-local; do not invent or copy another branch's state.

### 2.2 Current Processing implementation

Current source: app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt (blob 1969409c2f903a16ea38ed2f73b705daa424c050).

- The current renderer is AI Edge Field V1: four disconnected photo-edge islands, seven registered paths, Quiet Gaps, receiving illumination, layered strokes, hairlines, nodes, and particles.
- The current frozen rendering contract is design/pages/recognition/processing/components/AI_Edge_Field_V1_Rendering_Contract.md. It explicitly requires a line-led, seven-path structure and prohibits a full perimeter.
- This is a different optical language from SWOS. Reusing its viewport transform, quality policy, or timing infrastructure may be appropriate; reusing its seven-path geometry as the SWOS visual foundation is not.
- RecognitionFishFocus.kt already provides a separate fish-focus layer. Real subject geometry must remain trusted and photo-aligned; a detector rectangle is never a production contour.
- RecognitionProcessingScene in RecognizingScreen.kt owns the processing scene, current visual clock, photo transform, fish focus, and processing-photo snapshot.

### 2.3 Current Result handoff

Current authorities:

- design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V2.md
- design/pages/recognition/result/motion/recognition_result_motion_contract_v2.json
- design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V2.md
- docs/execution/rr09_gold_transfer/RR09_Motion_V2_Android_Timeline_Compatibility_Report.md

RR09 V2 already defines an 850 ms one-shot reveal: photo continuity 0–200 ms, gold transfer 200–380 ms, name formation 380–550 ms, name reveal 550–680 ms, afterglow fade 680–850 ms.

Android's compatibility report says T0 is the first presented Result frame. The real pipeline hands off immediately after its effective state machine gates; Android must not invent a 200 ms wait. The frozen wording that describes T0 as a Processing RESOLVE event is not a valid Android event timestamp. A new version must make this distinction explicit.

The existing Android path is reusable in part:

- RecognitionRevealHandoffFactory carries trusted detector/subject geometry, photo viewport bounds and transform, result identity, eligibility, and handoff timestamp.
- RecognitionRevealClock already defines the 850 ms RR09 phases.
- RecognitionRevealOpticalRenderer draws the shared photo, fish halo, gold transfer path, particles, and title reveal overlay.
- RecognitionRevealSessionStore and its playback ledger protect session identity and one-shot playback.

The current handoff is not yet a SWOS contract. Phase 0 must define what visual state survives Processing-to-Result: the peripheral water field is fully released; a restrained gold trace on the real fish contour remains the transfer source; the same photo identity and actual photo transform are retained.

### 2.4 Timing conflict found

The effective State Machine V3.1 JSON and Android RecognitionRuntimeContract.kt currently use minimums of 220 / 320 / 350 / 250 ms, totalling 1140 ms before considering actual pipeline event completion. That does not match the user-confirmed 2800 ms optical pacing target.

Recognition_Visual_Fidelity_V1_2.md records 350 / 600 / 600 / 1250 ms. Recognition_Processing_Motion_Spec_V1_2.md separately records a legacy 900 / 600 / 1250 ms three-product-state pacing table plus 200 ms resolve (2950 ms), and the project has a separate note that this older table is not the effective V3.1 timing authority.

**Required closure:** before Stage 1 can claim timing parity, update the effective State Machine contract, Android runtime timing projection, tests, trace expectations, and evidence gate together to the user-confirmed 2800 ms timeline. Do not average the conflicting numbers, and do not claim this is already implemented.

### 2.5 Existing optical proposal is not SWOS authority

PR #166 is Draft / Open / Unmerged and contains a previous Edge Field engineering handoff. Its current work uses the existing seven-path line-led topology. The report RECEIVING_LIGHT_CONTINUOUS_SPATIAL_FIELD_V1_REPORT_2026-10-21.md classifies the real-photo composite visual result as BLOCKED and Android gate as NOT_RUN. This branch is useful historical evidence of the current approach's limitations, but it is not the SWOS baseline and must not be treated as a merged production result.

## 3. Proposed SWOS visual contract

### 3.1 Optical identity

The visual metaphor is natural sunlight on fishing water, not an AI assistant being awakened. The photo remains an actual photo and stays visually dominant. The light changes around and over the photo; the photo itself is not transformed into a synthetic underwater image.

SWOS's signature is the causal sequence:

1. Light appears at the lower portion of the displayed photo.
2. Irregular caustic light and overlapping ripple fronts propagate upward.
3. Water light reaches the actual photo edges as uneven local reflections, not as a closed rectangle.
4. The visual focus moves to the real fish geometry.
5. Warm gold remains on the fish while the peripheral water field fades completely.
6. The same gold trace becomes the source of RR09's downward title transfer.
7. The Chinese species name is revealed below the photo; all excess glow disappears.

### 3.2 Layer ownership

Render in this order, using one photo viewport coordinate system:

- L0 Photo Base: original oriented photo and the existing approved photo transform. No model input is changed.
- L1 Ambient Water: low-intensity blue-green environmental light, local rather than a global tint.
- L2 Sunlight Caustics: irregular, multi-scale bright/dark patterns resembling sunlight refracted by moving water.
- L3 Ripple Field: overlapping, non-uniform wave fronts that propagate from the lower photo region upward. No single mechanical scan line and no simple expanding concentric circles.
- L4 Peripheral Refraction: uneven local light near the actual visible photo edges. The full field must not read as a border.
- L5 Fish Focus: trusted fish mask/contour, restrained warm-gold rim, mid glow and thin hot core. Keep fish interior and background detail legible.
- L6 Residual Gold Trace: the narrow, low-intensity fish contour trace retained at the end of Processing to seed the Result transfer.

The SWOS layers and Fish Focus remain separate render responsibilities. The fish contour must not be generated from the photo rectangle or a detector BBox outline.

### 3.3 Color and material direction

Initial visual-study tokens, not final frozen numeric colors:

- Water Blue-Green: #527C82
- Ripple Mist: #91B9B2
- Sun Gold: #D9BE7D
- Fish Glow: #F0DCA5

Tune them against real light, dark, night, handheld, cropped and edge-clipped fish photos. Do not freeze alpha/blur numbers from a single clean demo photo.

Material rules:

- Soft multi-scale falloff; no hard neon edge.
- Caustic texture slowly drifts while ripple fronts move at a different phase.
- Brightness is non-uniform and does not peak everywhere simultaneously.
- Any photo refraction is subtle. Do not visibly bend the whole photo or alter the fish's perceived shape.
- Avoid large white flashes, uniform blue filters, excessive blur, equal-strength corners, and persistent glow loops.

### 3.4 Coordinates and model geometry

- Apply EXIF orientation before creating any display-space geometry.
- Map all layers to the actual visible photo viewport after the approved FIT/CROP transform has resolved.
- BBox, subject alpha/mask, contour, fish halo, water field, and transition trace share that transform. Safe-area insets do not remap the photo.
- Reuse the trusted subject geometry produced by the real recognition pipeline. If only a valid BBox exists and no trusted subject mask/contour is available, use a restrained regional halo; do not fabricate a precise fish outline.
- Invalid mapping or missing photo identity disables spatial light travel and uses the approved restrained fallback.
- The original photo bytes and detector/classifier inputs remain unchanged.

### 3.5 API 28 rendering constraints

Phase 0 freezes observable visual behavior and coordinate rules, not an unbenchmarked rendering backend.

Stage 1 should compare an API 28-compatible cached-texture / Canvas implementation against a GPU-shader option only if the visual prototype requires it. Android 13 RuntimeShader must not be a required path for the API 28 baseline. Avoid full-resolution CPU pixel transforms per frame, unbounded blur layers, per-frame Bitmap allocations, or full-screen overdraw. The chosen renderer must preserve accessibility, touch handling, lifecycle behavior, and the existing page hierarchy.

## 4. Proposed unified timeline

### 4.1 Recognition Processing — 2800 ms target

| Absolute time | Internal phase | Optical behavior |
|---|---|---|
| 0–350 ms | CAPTURED | Photo is stable; a subtle warm reflection awakens at the lower photo region. |
| 350–950 ms | DETECTING | Caustic light and irregular ripple fronts propagate upward; no fish contour before real detector geometry exists. |
| 950–1550 ms | OUTLINE | Water light reaches the photo edges unevenly; light contrast begins to converge around the real fish; gold contour reveals progressively. |
| 1550–2600 ms | CLASSIFYING | Fish contour remains the main focus; water light slows and becomes subordinate. |
| 2600–2800 ms | CLASSIFYING / visual resolve subphase | Peripheral water/blue-green field fades to zero. Keep only a narrow, restrained gold trace on the real fish contour. This is not a new product state and adds no time. |

All phase changes still require the real pipeline event as well as the required minimum visual beat. If inference takes longer than 2800 ms, stay in a calm, low-intensity CLASSIFYING hold until the real result arrives; do not fake completion, loop attention bursts, or allow the animation clock to advance to success without a result. If a result arrives early, the 2800 ms minimum optical story still cannot be shortened.

### 4.2 Processing → Result — RR09 V3 proposal, 850 ms from first Result frame

The Result route begins as soon as the real result and effective Processing gates permit it. Android T0 is the first presented Result frame, consistent with the existing compatibility report. No extra 200 ms Processing wait is appended.

| Result elapsed time | Phase | Optical behavior |
|---|---|---|
| 0–200 ms | Photo continuity | The same original photo resolves into the frozen Result Hero geometry. Peripheral water light is already gone; the retained gold fish trace remains visible. |
| 200–380 ms | Gold transfer | Warm gold moves from the real fish contour downward toward the existing species-title area below the photo. |
| 380–550 ms | Name formation | Only incomplete, softly formed Chinese glyph strokes are visible. The complete name is not readable early. |
| 550–680 ms | Chinese name reveal | The Chinese species name becomes fully legible in the existing title area. No English scientific-name line is shown or reserved. |
| 680–850 ms | Afterglow fade | Gold particles and glow fade completely. The Result page returns to its normal static visual state. |

Nominal combined duration is 3650 ms from the start of the 2800 ms Processing story to the end of the 850 ms Result reveal. The RR09 200 ms photo-continuity phase is inside its 850 ms clock; it is not an extra pre-navigation wait. Actual total duration may be longer if real inference takes longer than the minimum Processing beats.

### 4.3 Cross-page invariants

- Same photo source identity throughout; no generated replacement image.
- Photo crop and fish position remain continuous through the handoff. Any geometry adjustment follows the existing Hero Media Contract, not animation convenience.
- The peripheral water field must be fully gone by the end of Processing.
- The fish's narrow gold trace must survive long enough to be the explicit source of the Result transfer.
- RR09 begins with the photo, fish geometry, and session identity supplied by the real handoff; no arbitrary particle origin or guessed fish position.
- The Chinese species name remains below the photo, never overlaid on the fish.
- Do not add a fourth Processing state, alter recognition thresholds, change confidence semantics, change result routing, or delay CTA availability.
- Trigger the full reveal only for a new session entering RESULT_HIGH with a valid species identity. No replay on recomposition, rotation, historical result reopening, or editor return.
- RESULT_MEDIUM, RESULT_LOW, NO_FISH, IMAGE_QUALITY, and TECHNICAL_FAILURE do not play the successful reveal.

## 5. Accessibility, performance and fallback

- Reduce Motion removes ripple travel, particle travel, transfer travel, scale interpolation and title sweep. Keep the same result semantics and a restrained, immediately readable title resolution.
- Low-performance mode reduces caustic texture resolution and secondary ripple detail first; it may reduce RR09 particles to 0–6. Preserve the fish contour and gold source-to-title meaning.
- If geometry is untrusted, do not invent a light origin. Use a restrained opacity-only result title and preserve all controls.
- No custom sound or haptic.
- No continuous motion after the 850 ms Result reveal.
- No CTA blocking, layout shift, duplicate navigation, or changes to saving/editing semantics.

## 6. Stage 1 acceptance gates

This proposal is not implementation evidence. Stage 1 must build a playable prototype using registered real catch photos and export at least these Processing keyframes:

0, 350, 650, 950, 1250, 1550, 1900, 2200, 2600, 2800 ms.

It must also export Result frames at:

0, 120, 200, 290, 380, 465, 550, 610, 680, 850 ms relative to the first Result frame.

Acceptance must prove:

- upward irregular ripple propagation, not moving wires;
- visible but restrained caustic texture and local photo-edge refraction;
- no full-frame tint or closed border;
- real fish contour/halo alignment through crop/scale/EXIF mapping;
- peripheral water field reaches zero while fish gold trace remains;
- same-photo continuity into Result and unmistakable fish-to-Chinese-title transfer;
- no English scientific-name line in the Result title block;
- correct HIGH-only one-shot behavior and no celebration on error/medium/low;
- exact 2800 ms minimum optical pacing and 850 ms Result reveal boundaries;
- Reduce Motion, low-performance, lifecycle and CTA behavior;
- browser evidence and Android API 28 evidence are classified separately.

## 7. Phase 0 exit criteria and authority boundary

Versioned Phase 0 authorities are now split into dedicated files: SWOS Optical Contract V1, Processing Motion Spec V1.3 / Page Contract V3.2, Processing→Result Optical Continuity Contract V1 and RR09 V3. This document remains the source audit and design rationale; use `docs/execution/swos/PHASE0_CLOSURE_REPORT_V1.md` for the exact current contract set, control-plane links, validation evidence and Stage 1 gap list.

Phase 0 is complete only when the audit is reviewed, the SWOS and Processing-to-Result continuity contracts are approved, and the Control Plane authority pointers are synchronized coherently. The active RR09 V2 frozen spec is retained as historical evidence; do not edit it in place. A new RR09 V3 authority is required for the Chinese-only title and first-Result-frame clock origin.

The authority sync must update, as applicable, the Feature Matrix, Phase/Task State Machine, Unified Contract Registry, design source registries, Result RR09 navigation/status, validators, and evidence-gate bindings. The effective State Machine timing and Android runtime timing projection must be changed together before Stage 1 can claim parity.

Until that sync and review are complete:

- This document is PROPOSED_FOR_REVIEW, not FROZEN.
- No Android source implementation is claimed.
- SWOS browser playback is NOT_RUN.
- Android API 28 runtime, frame timing, visual parity and performance are NOT_RUN.
- No APK or production release is claimed.
