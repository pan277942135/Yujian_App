# Recognition Runtime Evidence V1.2

Status: **FROZEN**

Scope: **runtime proof required to accept the frozen Recognition Processing design**.

This document freezes the evidence contract. It does **not** claim that the current Android runtime already passes it.

## 1. Core rule

> Every frozen design contract must have a runtime proof.

Build PASS is necessary but is not visual acceptance.

A Recognition implementation is not considered runtime-closed until all required evidence exists and the final gate classifies the package as PASS.

## 2. Current product mapping

Recognition Processing has three product states:

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

Historical Frozen PNGs remain valid as visual references, but the first two no longer represent separate product states.

Runtime mapping:

| Runtime evidence | Product state | Historical visual reference | Role |
| --- | --- | --- | --- |
| 01_image_recognizing_early.png | 图片识别中 | 01_Capture_Transition_Frozen.png | Early |
| 02_image_recognizing_late.png | 图片识别中 | 02_AI_Understanding_Frozen.png | Late |
| 03_fish_located.png | 已定位到鱼体 | 03_Fish_Highlight_Frozen.png | Primary |
| 04_species_recognizing.png | 鱼种识别中 | 04_Fish_Identifying_Frozen.png | Primary |

The old CAPTURED / DETECTING visual split must not be reconstructed as two user-visible states merely to satisfy old evidence names.

## 3. Evidence Group 01 — Visual Keyframes & Parity

Required runtime artifacts:

1. `01_image_recognizing_early.png`
2. `02_image_recognizing_late.png`
3. `03_fish_located.png`
4. `04_species_recognizing.png`
5. `05_result_high.png`
6. `06_result_medium.png`
7. `07_result_low.png`
8. `08_issue_no_fish.png`
9. `09_issue_image_quality.png`
10. `10_issue_technical_failure.png`
11. `recognition_visual_parity_v1_2.json`
12. `recognition_visual_parity_contact_sheet_v1_2.png`

Rules:

- screenshots must come from actual runtime;
- do not substitute design source PNGs;
- debug overlays must be OFF in final visual screenshots;
- photo must remain dominant;
- no global blue/gold tint;
- no scanner rectangle / HUD / detector box;
- AI Edge Field must preserve four Energy Islands and mandatory Quiet Gaps;
- the field must not read as a closed frame/orbit;
- B_UR remains the dominant island;
- Fish Focus appears only after real fish location.

Pixel identity is not required.

Parity is evaluated on topology, hierarchy, placement, optical character, state meaning and component presence.

## 4. Evidence Group 02 — Motion & Timing

Required:

- `recognition_processing_v1_2.mp4`
- `recognition_processing_timing_v1_2.txt`
- `recognition_motion_trace_v1_2.json`

The normal-speed MP4 must include:

- real photograph;
- entry into 图片识别中;
- transition to 已定位到鱼体;
- transition to 鱼种识别中;
- final 200ms Resolve;
- first Result frame.

Timing evidence must record at least:

- 图片识别中 visible duration;
- 已定位到鱼体 visible duration;
- 鱼种识别中 visible duration;
- Resolve duration;
- total Processing duration;
- current SegmentOffset immediately before and after each state transition;
- StateStrength target and measured presentation value;
- whether Motion Clock restarted.

Frozen minimum pacing:

| State | Minimum |
| --- | ---: |
| 图片识别中 | 900ms |
| 已定位到鱼体 | 600ms |
| 鱼种识别中 | 1250ms |
| Resolve | 200ms |

Motion PASS requires:

- no SegmentOffset jump at state boundaries;
- no Motion Clock reset;
- StateStrength transitions according to V1 Motion Contract;
- Resolve freezes position before fade;
- slow inference may hold the current real state;
- no fabricated later state.

## 5. Evidence Group 03 — Accessibility & Degradation

Required fixed-input screenshots:

- `quality_full.png`
- `quality_balanced.png`
- `quality_lite.png`
- `reduce_motion_static.png`
- `degradation_d0_d4_contact_sheet.png`
- `recognition_accessibility_trace_v1_2.json`

Use the same:

- photograph;
- crop;
- detector bbox;
- visual clock / frozen offset where applicable.

### Quality Levels

FULL / BALANCED / LITE must preserve:

- four Primary Energy Islands;
- Quiet Gaps;
- island priority;
- Primary geometry;
- photo dominance.

LITE must not collapse into thin Core-only lines.

### Reduce Motion

Evidence must show:

- SegmentOffset frozen;
- Hairline static if enabled by Quality;
- Particle OFF;
- Fish Focus breathing OFF;
- Result handoff still uses the 200ms alpha Resolve;
- Recognition semantics unchanged.

### D0→D4

Contact sheet must cover:

```text
D0 = FULL + Fish Focus A
D1 = BALANCED + Fish Focus A
D2 = LITE + Fish Focus A
D3 = LITE + Fish Focus B
D4 = LITE + Fish Focus C
```

D4 is the lowest accepted design level.

## 6. Evidence Group 04 — Production Semantics

Required:

- `recognition_production_flow_trace_v1_2.json`
- `level_a_real_contour.png`
- `fish_focus_bbox_mapping.json`

Final Production evidence must run the real Recognition path.

The evidence trace must make it possible to audit:

```text
real selected/captured photo
→ detector
→ real primary bbox
→ crop
→ classifier
→ raw pipeline progress
→ three-state presentation mapping
→ Result
```

Final production evidence must not use `phaseOverride` as proof of real state progression.

Deterministic overrides remain valid for isolated visual tests only.

Level A contour evidence must show:

- real catch fixture;
- real detector bbox;
- real subject alpha/contour source accepted by the visual pipeline;
- contour attached to the same fish location.

Fish Focus A/B/C may change visual fidelity but never the bbox truth.

## 7. Evidence Group 05 — Final Gate

Required:

- `runtime_evidence_manifest_v1_2.json`
- `runtime_gate_result_v1_2.json`

The manifest records every required artifact with:

- path;
- exists;
- non-empty;
- source type;
- capture environment;
- relevant state/profile;
- validation result.

Final runtime closure requires:

```text
runtime_gate_result_v1_2.json.classification == PASS
```

and no required manifest item may be missing or invalid.

## 8. Failure taxonomy

### FAIL_TEST

Evidence exists, but runtime behavior violates the frozen contract.

Examples:

- closed Edge Field frame;
- Fish Focus before real location;
- wrong state meaning;
- Segment jump;
- wrong degradation order.

### FAIL_EVIDENCE

Runtime may be correct, but required proof is missing or invalid.

Examples:

- missing screenshot;
- zero-byte MP4;
- absent timing file;
- incomplete manifest.

### BLOCKED_INFRA

Runner / emulator / physical-device / adb / transport prevents evidence generation.

### FAIL_ARTIFACT

APK / package / assets / install pair is invalid.

A missing MP4 is not automatically a product FAIL_TEST.

## 9. What does not count as proof

The following are insufficient by themselves:

- Build PASS;
- Unit PASS;
- assembleDebug PASS;
- static source-code inspection;
- design PNG;
- screenshot generated from a mock screen;
- phaseOverride-only state flow;
- a video with no traceable state/timing evidence.

## 10. Final design-to-runtime matrix

| Frozen design area | Required runtime proof |
| --- | --- |
| 01 State Timeline | production flow trace + state/timing trace |
| 02 Visual States | 01–10 runtime screenshots + parity sheet |
| 03 Layer & Component Ownership | keyframes + bbox mapping + Level A contour |
| 04 Motion & Transition | MP4 + timing + motion trace |
| 05 Degradation & Accessibility | Quality triptych + Reduce Motion + D0–D4 sheet |
| 06 Runtime Evidence | manifest + final gate |

## 11. Acceptance shorthand

> No proof, no PASS. Wrong proof, no PASS. Build PASS is not Runtime PASS.
