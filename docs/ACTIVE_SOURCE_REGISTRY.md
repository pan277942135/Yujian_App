# YuJian Active Source Registry V1

Status: **ACTIVE**

Purpose: give Work, Codex, reviewers, and CI one unambiguous map of the current implementation and design authorities. Historical branches and compatibility assets must not be treated as active product authority unless listed here.

## Repository baseline

- Canonical branch: `main`
- Closure baseline after PR #47: `94a99f9a7c848e4bb124c5c454dcf42b3236f74b`
- Runtime platform: Android API 28 GCP self-hosted runner
- Unified runtime harness: `scripts/run_android_runtime_gate.sh`

## Empty Home

### Active authority

- Frozen visual:
  `design/system/core_visual_v1/reference/empty_home_v2.png`
- Frozen source:
  `design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2.png`
- Feature/visual/acceptance:
  `design/pages/home/empty_home/spec/`
- Motion:
  `design/pages/home/empty_home/motion/Empty_Home_Motion_Spec_V2.md`
- Haptic:
  `design/pages/home/empty_home/haptic/Empty_Home_Haptic_Spec_V1.md`
- Android runtime assets:
  `app/src/main/assets/empty_home_runtime_v2/`
- Runtime test:
  `EmptyHomeV2RuntimeContractTest`
- Runtime gate:
  `empty-home-v2`

### Legacy / compatibility

- `app/src/main/assets/empty_home_runtime_v1/`
  - status: **LEGACY / NOT PRODUCT AUTHORITY**
  - no production runtime reference found in current Home Empty V2 path;
  - legacy verifier is removed from active Android CI.
- `app/src/main/assets/home_empty_v1_3/`
  - status: **COMPATIBILITY — NORMAL HOME ONLY**
  - historical compatibility path; not Empty Home V2 authority.

## Normal Home

### Active design authority

- Design package closure:
  `design/pages/home/normal_home/DESIGN_PACKAGE_CLOSURE_V1.md`
- Design Manager secondary navigation:
  `design/pages/home/normal_home/navigation.json`
- Frozen visual / NH01:
  `design/system/core_visual_v1/reference/normal_home_v1.png`
- Frozen SHA-256:
  `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`
- Feature/behavior/visual/acceptance:
  `design/pages/home/normal_home/spec/`
- Motion:
  `design/pages/home/normal_home/motion/Normal_Home_Motion_Spec_V1.md`
- Haptic:
  `design/pages/home/normal_home/haptic/Normal_Home_Haptic_Spec_V1.md`
- Sound:
  `design/pages/home/normal_home/sound/Normal_Home_Sound_Spec_V1.md`
- Assets:
  `design/pages/home/normal_home/assets/asset_manifest.json`
- Authority order:
  `design/pages/home/normal_home/authority/authority_map.json`
- Status:
  `design/pages/home/normal_home/status.json`

### Design Manager secondary menu

- NH01 — 主页面｜多鱼获状态 — independent Frozen Hi-Fi
- NH02 — 第一条鱼首页 — independent Hi-Fi to add
- NH03 — 页面状态与异常 — combined board
- NH04 — 组件状态与内容边界 — combined board
- NH05 — 响应式与交互 — combined board
- NH06 — 背景与环境权威 — authority board

### Background authority

Repository source now present:
`design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`

The prior Closure V1 statement that this source did not exist is superseded. Until NH06 / Background Authority V1.1 is formally closed, the Frozen NH01 reference remains the complete page-composition authority. The Morning Lake source must not be reconstructed from a screenshot.

### Runtime traceability

- Android runtime assets:
  `app/src/main/assets/normal_home_runtime_v1/`
- Runtime contract test:
  `NormalHomeRuntimeContractTest`
- Runtime gate:
  `normal-home-v1`

Design freeze and runtime evidence status are tracked separately.

## Recognition Processing

### Active authority

- Work entry point:
  `design/pages/recognition/Recognition_Design_Closure_V1_1.md`
- Nine Frozen Hi-Fi references:
  `design/pages/recognition/design/`
- Reference hashes:
  `design/pages/recognition/design/reference_manifest.json`
- Visual:
  `design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md`
- Motion/timing:
  `design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_1.md`
- Runtime semantics:
  `design/pages/recognition/runtime/Recognition_Runtime_Contract_V1_1.md`
- Acceptance:
  `design/pages/recognition/spec/Recognition_Acceptance_Criteria_V1_1.md`
- Evidence:
  `design/pages/recognition/evidence/Recognition_Evidence_Contract_V1_1.md`
- Machine-readable contract:
  `design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json`
- Production presentation:
  `RecognitionVisualStateController`
- Runtime gate:
  `recognition-frozen`

### Active timing

- CAPTURED 350ms
- DETECTING 600ms
- OUTLINE 600ms
- CLASSIFYING 1250ms
- nominal total 2800ms
- final resolve fade 200ms
- API28 runtime acceptance 2500–3500ms
- final fish-focus stable >=1000ms

### Compatibility contract

`RecognitionRuntimeContract` and packaged `identify/animation/identify_timeline.json` are compatibility/test representations only. They must mirror V1.1 cumulative boundaries and must not define an independent timeline.

Historical `RECOGNITION_RUNTIME_v1` 800/1500/2300/3000 boundaries are retired.

## Recognition Result 3+2

### Active design authority

- Design package closure:
  `design/pages/recognition/result/DESIGN_PACKAGE_CLOSURE_V1.md`
- Design Manager secondary navigation:
  `design/pages/recognition/result/navigation.json`
- State model:
  `design/pages/recognition/result/spec/Recognition_Result_State_Matrix_V1.md`
- Feature / behavior / visual:
  `design/pages/recognition/result/spec/`
- Motion:
  `design/pages/recognition/result/motion/Recognition_Result_Motion_Spec_V1.md`
- Authority order:
  `design/pages/recognition/result/authority/authority_map.json`
- Runtime alignment review:
  `design/pages/recognition/result/review/Recognition_Result_Runtime_Alignment_Review_V1.md`
- Status:
  `design/pages/recognition/result/status.json`

### Design Manager secondary menu

- RR00 — 结果总览 — 3+2 aggregate / overview board
- RR01 — 高置信结果 — independent Frozen Hi-Fi
- RR02 — 中置信结果 — independent Frozen Hi-Fi
- RR03 — 低置信结果 — independent Frozen Hi-Fi
- RR04 — 未检测到鱼 — independent Frozen Hi-Fi
- RR05 — 图片质量不足 — independent Frozen Hi-Fi

### State-level visual authority

- `design/pages/recognition/design/05_Result_High_Frozen.png`
- `design/pages/recognition/design/06_Result_Medium_Frozen.png`
- `design/pages/recognition/design/07_Result_Low_Frozen.png`
- `design/pages/recognition/design/08_Error_No_Fish_Frozen.png`
- `design/pages/recognition/design/09_Error_Image_Quality_Frozen.png`

The generic Core Visual reference `design/system/core_visual_v1/reference/recognition_result_v1.png` remains the shared Result visual-language authority only. State-level 05–09 references win for state-specific differences.

Design freeze and runtime alignment are tracked separately. Current Result design is frozen; Android Runtime alignment remains `NEEDS_CLOSURE` until the Result-specific review is resolved and revalidated.

## Recognition model

- Detector: `DET_FISH_v0.1`
- Classifier production channel: `pan277942135/Yujian@mobile-model-v0.2`
- Model verifier:
  `scripts/verify_production_model.py`
- App asset note:
  `app/src/main/assets/MODEL_ASSET_README.md`

The mutable production Release is resolved and verified at Android build time; do not restore or permanently pin an older classifier SHA.

## Runtime harness

Active entry point:

`scripts/run_android_runtime_gate.sh`

Required classifications:

- PASS
- BLOCKED_INFRA
- FAIL_ARTIFACT
- FAIL_TEST
- FAIL_EVIDENCE

Runtime jobs install only the frozen APK pair produced by the Build job.

Historical PR #46 is closed. Its unified-harness work was superseded and finalized by PR #47.

## Pull-request authority

As of Repository Closure V1:

- PR #47 — merged; final Empty Home + Recognition runtime closure.
- PR #46 — closed; superseded by #47.
- PR #2 — closed; obsolete 9-class mobile-model line.

Old branches remain Git history/checkpoints only. Their existence is not authorization to resume them.

## Work recovery rule

When a Work session recovers after interruption:

1. read `main` HEAD;
2. read this registry;
3. identify the one active PR for the requested scope;
4. do not resume a historical branch merely because its name appears relevant;
5. prefer current `design/` authority over old screenshots, task attachments, or branch-local specs;
6. when a current contract conflicts with a legacy source, current authority wins;
7. once a task is merged and main validation passes, mark predecessor PR/spec/branch state as superseded or compatibility-only.

## Retirement Gate for future Work

A task is not closed until:

- product/runtime gate is PASS where applicable;
- evidence exists;
- PR is merged or explicitly closed;
- predecessor PRs are closed;
- old spec authority is marked superseded or removed;
- status/registry is updated;
- duplicate root files are removed;
- final main SHA and validation run are recorded.
