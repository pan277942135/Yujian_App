# AGENTS.md — 渔见 / YuJian repository operating protocol

## 1. Mission

This repository is an AI-first Android product repository for 渔见 (YuJian).

The current product priority is the primary photo-recognition journey:

Empty Home -> Camera -> Permission -> First Capture Guide -> Capture -> Recognition Processing -> Recognition Result -> Save Catch / Continue -> Home / Recent Catch -> FishRecordDetail

The agent is expected to execute implementation, tests, debugging, documentation, evidence collection, and GitHub delivery. Human authority remains required for product direction, design freezes, architecture freezes, production changes, and final release approval.

## 2. Mandatory startup sequence

At the beginning of every work session, before editing code:

1. Read AGENTS.md.
2. Read PROJECT_MASTER_STATE.yaml.
3. Verify the live Git branch and HEAD.
4. Read docs/qa/recognition_main_journey/WORK_EXECUTION_STATE.json when the task touches the recognition journey.
5. Read the applicable design/page/runtime contract before changing UI or behavior.
6. Read the latest relevant entry in harness/build-log.md.
7. Check all open blockers in PROJECT_MASTER_STATE.yaml.
8. Select work only from next_actions or from an explicitly authorized user task.
9. Before claiming completion, run the applicable validator/tests and collect evidence.
10. Update the master state and build log before ending the work session.

Do not rely on conversation history as the only project checkpoint.

## 3. Source-of-truth hierarchy

When sources disagree, use this precedence:

1. Live repository contents and current Git state
2. Current branch / exact HEAD
3. Current Pull Request metadata
4. Observed CI / runtime / APK / evidence
5. Durable repository contracts and ledgers
6. Historical reports
7. Conversation memory

If a conflict cannot be resolved:

- mark the affected state CONFLICT or NOT_CONFIRMED;
- do not guess;
- do not promote the item to PASS;
- record the conflict in harness/build-log.md;
- create or update a blocker.

## 4. Current control plane

The current Android control plane is:

- Repository: pan277942135/Yujian_App
- Branch: audit/design-system-v3-1-phase1-master-baseline
- Current acceptance baseline: a280699da7575aa332f9d00104969161bbe918bd
- PR: #145
- PR state: Draft / Open / Unmerged
- Base: main@7737f293ea3831aeea2c6f58308961cdb2ba7d01

The backend/data plane is:

- Repository: pan277942135/Yujian
- Current main SHA is recorded in PROJECT_MASTER_STATE.yaml.

Do not silently switch to an older branch, older PR, or historical SHA.

## 5. State semantics

Use these meanings consistently:

- PASS: observed and evidenced; the required gate actually ran and passed.
- FAIL: required gate ran and failed.
- BLOCKED: required work cannot proceed because a named dependency is unresolved.
- NOT_RUN: the required check has not run.
- NOT_CONFIRMED: evidence/source conflict exists.
- IN_PROGRESS: work is actively being executed.
- READY: prerequisites are satisfied and work may start.
- HISTORICAL: retained for provenance; cannot establish current status.

Never convert:

- build PASS -> runtime PASS;
- unit PASS -> end-to-end PASS;
- historical PASS -> current PASS;
- artifact exists -> release ready;
- AI assertion -> evidence.

## 6. Exact-SHA rule

Every meaningful implementation and runtime claim must be tied to an exact source SHA.

For runtime acceptance, record:

- source SHA;
- workflow run ID;
- device/API;
- test suite;
- APK artifact;
- APK SHA-256 when available;
- screenshots/logs/reports;
- gate classification.

Evidence from another SHA is historical evidence only.

After any implementation commit:

1. Read the remote HEAD again.
2. Use that exact SHA as the new execution baseline.
3. Rerun affected validation.
4. Update PROJECT_MASTER_STATE.yaml.
5. Append an observation to harness/build-log.md.

State-only documentation commits may move the branch HEAD. The tracked execution baseline must remain explicitly identified; do not confuse a state-document commit with a product implementation baseline.

## 7. Design-system rules

design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json is the effective authority lookup.

Frozen rules include:

- Frozen PNG bytes are immutable.
- Frozen asset SHA-256 values are immutable unless explicitly re-authorized.
- brand_title_primary is the brand-title artwork authority.
- Do not introduce an unregistered crop/fill mode.
- Preserve original user media.
- Do not use non-uniform image stretching.
- Use EVIDENCE_FIT where the declared Hero contract requires it.
- Do not compensate for incorrect geometry with arbitrary overlays.
- Do not apply safe-area/inset transformations twice.
- Dynamic UI typography follows the effective typography contract; do not invent a font dependency.

## 8. Recognition main journey invariants

The primary journey is governed by J01-J20.

Required route:

Empty Home -> Camera -> Permission -> First Capture Guide -> Capture -> Preview Ready -> Processing -> Result -> Save Catch -> Home -> Recent Catch -> FishRecordDetail

Recognition timing contract:

- CAPTURED >= 220ms
- DETECTING >= 320ms
- OUTLINE >= 350ms
- CLASSIFYING >= 250ms
- Result hold <= 900ms
- Target total approximately 1.1-1.3s

Result contract:

- First Result entry is not loading.
- Loading begins only after an explicit user action.
- GOOD / WARNING / INVALID quality semantics remain intact.
- High / Medium / Low / No Fish / Invalid states remain distinct.

FishRecordDetail:

- A-side is default.
- B-side first reveal happens once after READY.
- Subsequent entry defaults to A-side.
- Hero uses the declared Evidence Fit contract when required.

## 9. Runtime environment

Authoritative Android runtime:

- API level: 28
- Runner: self-hosted
- Reference device: Pixel_2
- Reference resolution: 1080x1920

The repository's heavy Android runtime matrix is not necessarily executed by PR synchronize. If the workflow requires branch push or workflow dispatch, use the repository-defined trigger rather than treating a skipped runtime job as PASS.

If the local Android toolchain is unavailable, do not fabricate local runtime results. Use the configured authoritative runner.

## 10. Work execution rules

Before implementation:

- identify the current exact SHA;
- identify the authoritative contract;
- identify the acceptance gate;
- identify dependencies/blockers;
- define the smallest coherent change.

During implementation:

- make the smallest change that satisfies the contract;
- preserve unrelated work;
- do not reset/rebase/force-push unless explicitly authorized;
- do not modify main;
- do not change frozen product/design/model/backend contracts silently.

After implementation, run as applicable:

- static validation;
- unit tests;
- AndroidTest compilation;
- lint;
- APK assembly;
- packaged-asset verification;
- model provenance checks;
- API28 runtime tests;
- visual/geometry evidence;
- motion/accessibility checks.

Then update state and evidence.

## 11. Git policy

Current project policy:

- Do not merge main unless explicitly authorized.
- Do not force-push.
- Do not rewrite existing history.
- Do not delete failed evidence.
- Do not overwrite historical evidence with a new result.
- Prefer additive commits.
- Commit each meaningful stage.
- Push completed stages when the task authorizes GitHub delivery.
- Every commit message must describe the actual change.
- Before pushing, run scripts/validate_project_master_state.py.

For this project, the existing Draft PR #145 remains the current control PR.

## 12. State update protocol

The detailed protocol is:

harness/STATE_UPDATE_PROTOCOL.md

The observed execution log is:

harness/build-log.md

Whenever any of these changes, update state:

- current execution baseline SHA;
- Work stage;
- phase status;
- PR state;
- CI result;
- runtime result;
- blocker;
- APK;
- evidence;
- material decision;
- next action.

State updates must describe observed facts, not intentions.

## 13. Context and evidence

Do not put the full project history into AGENTS.md.

Use progressive disclosure:

- project state -> PROJECT_MASTER_STATE.yaml
- task execution state -> docs/qa/.../WORK_EXECUTION_STATE.json
- detailed plans -> harness/build/
- phase-specific discoveries -> harness/context/
- observed execution -> harness/build-log.md
- detailed runtime evidence -> existing QA evidence paths
- design authority -> design/system_v3/

Read only the sources relevant to the current task.

## 14. Blocker protocol

When blocked:

1. classify the blocker (P0/P1/P2, infra/data/product/design/engineering);
2. state the exact observed failure;
3. identify the unblock condition;
4. do not invent a workaround that violates the contract;
5. keep the affected gate BLOCKED, NOT_RUN, or NOT_CONFIRMED;
6. continue independent work when safe;
7. update the state and build log.

A blocker is not a reason to stop the entire project if independent work remains available.

## 15. Agent restart / recovery

If a session is interrupted:

1. Read AGENTS.md.
2. Read PROJECT_MASTER_STATE.yaml.
3. Verify live HEAD.
4. Read current Work state.
5. Read latest build-log entry.
6. Inspect blockers.
7. Continue from the first unfinished next_action.

Do not restart a completed stage merely because the previous agent session is unavailable.

## 16. Completion standard

A task is complete only when:

- implementation exists;
- applicable contracts are satisfied;
- required automated checks pass;
- required runtime checks actually run;
- evidence is generated where required;
- exact SHA provenance is recorded;
- blockers are closed or explicitly accepted as non-blocking;
- master state is updated;
- build log records the observed result;
- the GitHub commit is pushed when authorized.

The phrase "implemented" must never be used as a substitute for "accepted".

## 17. Final response from an agent

At the end of a work stage, report:

1. What changed
2. Exact commit SHA
3. Tests/checks run
4. Runtime/evidence result
5. Remaining blockers
6. Next action
7. Whether the stage is PASS, FAIL, BLOCKED, or NOT_RUN

Keep the report factual and traceable.
