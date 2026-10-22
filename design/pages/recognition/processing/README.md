# Recognition Processing — V1.2 Frozen Baseline / SWOS V1.3 Proposal

V1.2 remains the frozen historical baseline on main. The proposed SWOS contract set below is the current Processing optical/timing proposal on the Phase 0 control-plane branch; it becomes canonical only after main adoption.

## Authority order

1. Proposed optical: `optics/SWOS_Optical_Contract_V1.md` + `.json` — pending main adoption; runtime NOT_RUN.
2. Proposed timing: `motion/Recognition_Processing_Motion_Spec_V1_3.md` + `motion/recognition_processing_motion_contract_v1_3.json`.
3. Proposed Page Contract: `design/system_v3/08_Page_Contracts/recognition/processing/Recognition_Processing_Page_Contract_V3_2.md` and its V3.2 state/engineering/acceptance companions.
4. Cross-page handoff: `../continuity/Processing_Result_Optical_Continuity_Contract_V1.md` + `.json`.
5. Existing frozen photo-state references: `../Recognition_Design_Closure_V1_2.md`, `../spec/Recognition_State_Timeline_Spec_V1_3.md`, `../design/Recognition_Processing_Visual_State_Set_V1_2.json`.
6. Legacy Edge Field V1 static shape/render/motion contracts remain unchanged historical implementation references; they are not the SWOS geometry basis.
7. Existing accessibility, haptic, sound, data and evidence authorities remain in force where non-conflicting; haptic/sound remain NONE.

## Three-state model and timing status

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

The three user-visible product states remain unchanged. CAPTURED/DETECTING are internal phases of IMAGE_RECOGNIZING. Proposed boundaries are 0/350/950/1550/2800ms with real-event gates; they are not an inference deadline. V3.1's 220/320/350/250ms values and V1.2's 900/600/1250/200ms wording remain documented history and are superseded only when the proposed V3.2 contract is adopted.

## Proposed motion and legacy boundary

SWOS replaces the line-led seven-path Edge Field optical basis after control-plane adoption. The 2600–2800ms peripheral-water release is within CLASSIFYING; no RESOLVE business state or pre-navigation wait is added. Actual Android timing and renderer remain NOT_RUN against this proposal.

## Feedback

- Haptic: NONE
- Sound: NONE

## Runtime boundary

This package freezes design authority only. Detector, crop, classifier, confidence semantics, production routing, Runtime closure, and Evidence PASS remain separate concerns.

Historical V1.1/V1.2 files and Page Contract V3.1 remain unchanged. The exact supersession scope and Android Stage 1 gaps are listed in `docs/execution/swos/PHASE0_CLOSURE_REPORT_V1.md`.
