# SWOS Phase 0 — Contract Unification and Cross-Page Optical Continuity Closure V1

**Task ID:** `YUJIAN_SWOS_PHASE0_CONTRACT_UNIFICATION_V1`
**PR:** [#186](https://github.com/pan277942135/Yujian_App/pull/186)
**Status:** `PARTIAL` — versioned proposal and validators are prepared on the Draft control-plane PR; main adoption and Android implementation remain pending.
**Scope:** Phase 0 audit / contract / control-plane work only. No demo, Android source, API 28, APK or Pages deployment.

## 1. Live baseline and control-plane boundary

At execution start, `git fetch origin main --prune` returned `origin/main = 888706fda16d6f343f9bb4e02463f3b7a3526c31`. PR #186 was Open, Draft, Unmerged and based on that main SHA. Its branch was `design/swos-phase0-audit-optical-contract-v1`; the live PR head at audit was `5e43fa295bc7d3e8de578f9fa3569152591a58b9`. The prior audit SHA `888706…` still matched main; the PR head was a separate one-file audit proposal. A clean isolated worktree was used; unrelated dirty worktrees were left untouched.

`python scripts/validate_control_plane_admission.py` was run locally from exact audit head `5e43fa295bc7d3e8de578f9fa3569152591a58b9` and logged `mode=CONTROL_PLANE_CHANGE branch=design/swos-phase0-audit-optical-contract-v1` plus `CONTROL_PLANE_ADMISSION=PASS`. The PR's `control-plane-admission` workflow run `39714784710` also succeeded for the pre-change PR state. After the Phase 0 change set is committed, Admission and relevant validators must be rerun on the new exact SHA; their run IDs and results belong in the final PR report.

Repository policy keeps `main` as the adopted Control Plane authority and requires a control-plane change to land there before dependent product branches synchronize. Therefore this Draft PR's new files and references are a reviewable proposal set, not yet the canonical main authority. This is why the Phase 0 outcome remains `PARTIAL` until the PR is reviewed/adopted. No main merge was performed.

## 2. Authority inventory and conflicts resolved by versioning

| Authority | Current audited version / status | Domain and dependencies | Conflict / Phase 0 resolution |
|---|---|---|---|
| Recognition Processing Visual Fidelity | `design/pages/recognition/spec/Recognition_Visual_Fidelity_V1_2.md`; existing implementation authority | Three visible product states; old Edge Field visual behavior; depends on real pipeline semantics and frozen Processing references. | Conflicts optically with natural SWOS. Preserved unchanged; SWOS V1 proposes the new optical authority and explicitly supersedes only the optical layer scope after main adoption. |
| Recognition Processing Motion | `design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md`; FROZEN | Old 900/600/1250/200ms table and continuous seven-path field. | Conflicts with the confirmed 350/600/600/1250ms pacing and no extra Resolve. Preserved unchanged; V1.3 is the proposed timing authority. |
| AI Edge Field V1 | `YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md`, static-shape, rendering and motion contracts; FROZEN | Seven B/G paths, four islands, alpha/depth and Quiet Gaps; depends on legacy renderer. | Explicitly retained for historical Edge Field only; not renamed or reused as SWOS geometry. Frozen bytes/values are untouched. |
| Fish Focus / image geometry | `RecognitionRevealPolicy.kt`, `RecognitionRevealHandoff.kt`, `RecognitionImageTransform`, `FishRecognitionPipeline.kt`, `FishSubjectPreviewEngine.kt`; implementation status only | Detector output and image/session identity are production; subject preview geometry is post-recognition visual output and trust-gated. | Clarified in SWOS/continuity contracts. A BBox permits regional glow only; it is never a contour. Missing trusted geometry blocks spatial fish tracing. |
| Processing Page / state machine | Page Contract V3.1 and State Machine V3.1; FROZEN | State/data/layout contracts; state timing 220/320/350/250ms. | Preserved as history. Proposed Page Contract/State Machine V3.2 updates timing/optics only; unchanged V3.1 Layout/Data are explicitly reused. |
| Recognition Result Motion | `Recognition_Result_Motion_Spec_V2.md`, machine V2 and system V2; FROZEN | RR09 850ms, one-shot HIGH reveal, photo continuity, gold transfer and title behavior. | V2 T0 wording refers to Processing RESOLVE and its title includes English scientific name. V3 resolves scoped conflicts: first presented Result frame, Chinese-only title and no English row. V2 is not edited; it is marked as historical in navigation. |
| RR09 Android compatibility | `docs/execution/rr09_gold_transfer/RR09_Motion_V2_Android_Timeline_Compatibility_Report.md`; historical adapter report | Documents Android first-presented-Result-frame clock origin without changing V2 browser contract. | V3 makes the same origin normative, removing the adapter/spec disagreement. Android source still needs Stage 1 alignment. |
| Result Hero Media | `design/pages/recognition/result/media/Recognition_Result_Hero_Media_Contract_V1.md`; current static photo geometry authority | Owns original photo mapping inside existing Result Hero. | Preserved. Continuity V1 references it and requires one source photo/crop transform across navigation. |
| Design/Control Plane | Feature Matrix, Phase/Task State Machine, Unified Contract Registry, Experience Registry, result navigation and validation scripts | Canonical static feature/page/gate/task routing; Design Manager is the actual entry. | Updated on this PR to point to proposed SWOS V1, Processing V3.2, continuity V1 and RR09 V3 while retaining V2 history. Adoption remains pending main-first policy. |

## 3. New versioned authorities

| Contract | Canonical proposal path | Scope |
|---|---|---|
| SWOS Optical Contract V1 | `design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.md` + `.json` | Photo Base, Ambient Water, Caustic Field, Ripple Field, Peripheral Refraction, Fish Focus and Residual Gold Trace; geometry/provenance; calibration split; API 28 and Reduce Motion requirements. |
| Processing Motion V1.3 / Page Contract V3.2 | `design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_3.md`, `recognition_processing_motion_contract_v1_3.json`, and `design/system_v3/08_Page_Contracts/recognition/processing/*V3_2*` | Boundaries 0/350/950/1550/2800ms; four phase durations 350/600/600/1250ms; true-event gates; 2600–2800ms water release inside CLASSIFYING. |
| Processing → Result Optical Continuity V1 | `design/pages/recognition/continuity/Processing_Result_Optical_Continuity_Contract_V1.md` + `.json` | Shared photo/source identity, transform, session, geometry trust, 2800+850ms timing, no pre-navigation wait and residual-gold transfer. |
| RR09 V3 | `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V3.md`, `recognition_result_motion_contract_v3.json`, `design/system_v3/10_Page_Visual_Acceptance_Contracts/Recognition_Result/Recognition_Result_Motion_Contract_V3.md` | 850ms from first presented Result frame, five approved boundaries, Chinese-only title, HIGH-only one-shot, no CTA blocking. |
| Stage 1 input contract | `design/pages/recognition/phase1/Phase1_SWOS_Playable_Demo_Input_Contract_V1.md` | Required photo, geometry, provenance, controls and browser/API 28 evidence. No demo was started. |

### Confirmed timing relationship

| Timing | Contract meaning |
|---|---|
| Processing 2800ms | Minimum visual pacing: 350 + 600 + 600 + 1250. Real events still gate progress. |
| RR09 850ms | Begins at first actually presented Result frame; 0–200ms is post-navigation photo continuity. |
| Nominal 3650ms | Design duration sum only; not an inference deadline or forced success timer. |
| Actual inference | May exceed 2800ms; remain in quiet low-intensity CLASSIFYING until real result and business gates permit immediate route handoff. |

## 4. Control plane and Design Manager synchronization

This change set adds the P3-05 task and `G-SWOS-PHASE0-CONTRACT` gate, binds both recognition features to `RECOGNITION-OPTICAL-CONTINUITY`, updates Feature Matrix and Unified Contract Registry references, and updates the Experience Registry / RR09 `navigation.json`. Design Manager adds two Processing contract entries and separates current RR09 V3 from deprecated historical RR09 V2. The new `validate_swos_phase0_contracts.py` checks timing arithmetic, boundaries, no-RESOLVE policy, first-frame T0, Chinese-only reveal, layer set, `photoContentRect` and runtime status; the Design Governance workflow runs it.

The root `CONTROL_PLANE_REGISTRY.yaml` remains unchanged because it is the index-only single entrypoint and already indexes the Feature Matrix, state machine, registry and design authority trees. No live work state was written to main or to static registries.

## 5. Validation and status

This section is updated after final exact-SHA validation. Required commands and results:

| Check | Source SHA / Run ID | Result | Evidence |
|---|---|---|---|
| Initial Control Plane Admission (audit baseline) | `5e43fa295bc7d3e8de578f9fa3569152591a58b9` / workflow `39714784710` plus local run | PASS | [PR #186 admission workflow](https://github.com/pan277942135/Yujian_App/actions/runs/39714784710) |
| Final Control Plane Admission | fill with exact committed SHA and run ID | PENDING | Final PR comment |
| Feature Matrix / State Machine / Unified Registry | fill with exact committed SHA and run IDs | PENDING | Final PR comment |
| Design Manager registry/navigation/hifi release | fill with exact committed SHA and run IDs | PENDING | Final PR comment |
| SWOS cross-contract validator + regression tests | fill with exact committed SHA and run IDs | PENDING | Final PR comment |
| Browser visual / demo / Android / API 28 / APK | intentionally not run in Phase 0 | NOT_RUN | Out of Phase 0 scope |

Contract consistency PASS is not a visual PASS, an Android PASS, or an adopted-main PASS. The PR remains Draft / Open / Unmerged, and Pages were not deployed.

## 6. Stage 1 gaps

The exact Android files/symbols and required Stage 1 checks are listed in [`ANDROID_STAGE1_IMPLEMENTATION_GAP_REPORT_V1.md`](ANDROID_STAGE1_IMPLEMENTATION_GAP_REPORT_V1.md). The largest confirmed gaps are the V3.1 220/320/350/250ms projection/assets/controller/tests, the seven-path Edge Field renderer, V2 RR09 title rendering and the absence of exact-SHA SWOS/API 28 evidence. These are not addressed in Phase 0.

## 7. Phase 0 conclusion

**Outcome: `PARTIAL`.** The branch contains a versioned, cross-validated contract proposal and synchronized Design Manager/control-plane references. It is not yet canonical on main because PR #186 remains Draft and unmerged. Android timing/renderer/RR09 changes, browser demo, Android API 28 runtime and APK are explicitly not implemented or run. No final visual scheme is claimed approved.

## 8. Phase 1 execution plan (waiting for next-stage authorization)

The executable package order, entry gates, evidence requirements and stop rules are in [`PHASE1_PLAYABLE_DEMO_EXECUTION_PLAN_V1.md`](PHASE1_PLAYABLE_DEMO_EXECUTION_PLAN_V1.md). It starts with a playable Web prototype and separate visual review; Android work remains a later gated package. Main adoption and explicit Phase 1 entry are required before implementation begins. Phase 0 does not start that work.
