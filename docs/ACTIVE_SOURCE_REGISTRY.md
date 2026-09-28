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
  - still referenced by `HomeScreen` when `showEmptyState == false`;
  - do not delete until Normal Home migrates to its own canonical runtime asset root.
  - this path is not Empty Home V2 authority.

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

## Recognition model

- Detector: `DET_FISH_v0.1`
- Classifier: `MODEL_M1_v0.6`
- Published Android classes: 16
- Model verifier:
  `scripts/verify_production_model.py`
- App asset note:
  `app/src/main/assets/MODEL_ASSET_README.md`

Historical MODEL_M1_v0.2 / 9-class PR #2 is closed and must not be revived.

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
