# AGENTS.md — 渔见 / YuJian

## Purpose

This file is the repository entry point for AI agents. It is intentionally concise:
use it as a map, then read the authoritative state, contract, plan, and evidence files.

## 1. Mandatory startup

Before changing anything:

1. Read AGENTS.md.
2. Read PROJECT_MASTER_STATE.yaml.
3. Verify live branch and Git HEAD.
4. Read harness/FEATURE_MATRIX.yaml and identify the Feature/Child Surface/Route/Journey scope.
5. If touching recognition, read docs/qa/recognition_main_journey/WORK_EXECUTION_STATE.json.
6. Read the applicable design/page/runtime contract.
7. Read the latest relevant harness/build-log.md entry.
8. Check open blockers in PROJECT_MASTER_STATE.yaml.
9. Execute only an authorized next_action or explicit user task.
10. Before completion, run the applicable validator/tests and collect evidence.
11. Update state + build log before ending the stage.

Conversation history is not the project source of truth.

## 2. Source-of-truth order

When sources disagree:

1. live repository contents/Git state
2. current branch and exact execution baseline
3. current PR
4. observed CI/runtime/APK/evidence
5. durable contracts/ledgers
6. historical reports
7. conversation memory

If unresolved, use NOT_CONFIRMED or BLOCKED. Never guess or promote to PASS.

## 3. Current control plane

- Android repo: pan277942135/Yujian_App
- Control branch: audit/design-system-v3-1-phase1-master-baseline
- Current tracked application-code baseline: e881c2a6772b418a49b6208ce44c8527fbeb97db
- Control PR: #145, Draft / Open / Unmerged
- Base: main@7737f293ea3831aeea2c6f58308961cdb2ba7d01
- Backend repo: pan277942135/Yujian
- Backend current main SHA: see PROJECT_MASTER_STATE.yaml

The current product priority is:

Empty Home -> Camera -> Permission -> First Capture Guide -> Capture ->
Recognition Processing -> Recognition Result -> Save Catch / Continue ->
Home / Recent Catch -> FishRecordDetail

## 4. Status semantics

- PASS = required check actually ran, passed, and has evidence.
- FAIL = required check actually ran and failed.
- BLOCKED = a named dependency prevents execution/acceptance.
- NOT_RUN = the check has not run.
- NOT_CONFIRMED = authoritative sources conflict or provenance is incomplete.
- IN_PROGRESS = active work.
- READY = prerequisites satisfied.
- HISTORICAL = provenance only; cannot establish current status.

Never infer:
build PASS -> runtime PASS
unit PASS -> end-to-end PASS
historical PASS -> current PASS
artifact exists -> release ready
AI statement -> evidence

## 5. Exact-SHA rule

Every implementation/runtime claim must identify its exact source SHA.

Runtime evidence must record, when applicable:

- source SHA
- workflow run ID
- device/API
- suite
- APK artifact
- APK SHA-256
- screenshots/logs/reports
- gate classification

Evidence from another SHA is historical only.

A state-documentation commit may move the Git HEAD without changing the tracked
product/runtime baseline. Keep those two concepts separate.

## 6. Design authority

Effective design lookup:

design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json

Frozen rules:

- do not modify Frozen PNG bytes or their SHA without explicit authorization;
- brand_title_primary is the brand-title authority;
- preserve original user media;
- use only registered crop/fill modes;
- use EVIDENCE_FIT when required by the Hero contract;
- never use non-uniform image stretching;
- do not double-apply insets/safePadding;
- do not invent typography/font dependencies.

## 7. Recognition invariants

Recognition timing:

- CAPTURED >= 220ms
- DETECTING >= 320ms
- OUTLINE >= 350ms
- CLASSIFYING >= 250ms
- Result hold <= 900ms
- target total approximately 1.1-1.3s

Result:

- first entry is not loading;
- loading starts only after explicit user action;
- GOOD/WARNING/INVALID remains intact;
- High/Medium/Low/No Fish/Invalid remain distinct.

FishRecordDetail:

- A-side default;
- B-side first reveal once after READY;
- subsequent entry defaults to A-side;
- Hero follows Evidence Fit when required.

## 8. Runtime authority

Primary runtime:

- API 28
- self-hosted runner
- Pixel_2
- reference 1080x1920

PR synchronize may skip the heavy Android runtime matrix. A skipped runtime job is
NOT_RUN, not PASS. Use the repository-defined branch-push or workflow-dispatch
runtime path.

If local Android tooling is unavailable, do not fabricate runtime evidence.

## 9. Work loop

For each stage:

1. inspect exact baseline and Feature Matrix scope;
2. identify applicable contract, blockers and acceptance gate;
3. implement the smallest coherent change;
4. validate statically and dynamically as applicable;
5. capture evidence;
6. update PROJECT_MASTER_STATE.yaml;
7. append harness/build-log.md;
8. run scripts/validate_project_master_state.py;
9. commit the coherent stage;
10. re-read remote HEAD;
11. update the tracked execution baseline when the product/runtime baseline changed.

## 10. Git and scope policy

Do not:

- merge main unless explicitly authorized;
- force-push or rewrite history;
- delete failed evidence;
- silently change frozen product/design/model/backend contracts;
- claim acceptance without evidence.

Prefer additive, traceable commits. Push completed stages when the task authorizes
GitHub delivery.

## 11. Blocker policy

For every blocker record:

- ID
- severity
- area
- exact observed condition
- affected gate
- unblock condition

Keep failed evidence. Continue independent work when safe.

## 12. Recovery

On interruption:

1. AGENTS.md
2. PROJECT_MASTER_STATE.yaml
3. live branch/HEAD
4. current Work state
5. latest build-log entry
6. blockers
7. first unfinished next_action

Resume from the latest committed state; do not reconstruct the project from chat.

## 13. State protocol

Detailed rules:

harness/STATE_UPDATE_PROTOCOL.md

Observed execution history:

harness/build-log.md

Validator:

python scripts/validate_project_master_state.py

## 14. Completion

A stage is complete only when its required implementation, verification, evidence,
provenance, state update, and Git checkpoint are complete.

The word "implemented" is not a substitute for "accepted".

At stage close, report:

- change summary
- exact commit SHA
- checks actually run
- runtime/evidence result
- remaining blockers
- next action
- PASS / FAIL / BLOCKED / NOT_RUN
