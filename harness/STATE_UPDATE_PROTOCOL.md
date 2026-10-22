# State Update Protocol — YuJian

## 1. Purpose

PROJECT_MASTER_STATE.yaml is the current project control snapshot.

This file is intentionally compact compared with the full project history. Detailed
execution evidence stays in the relevant QA, design, runtime, and artifact paths.

This protocol defines when and how the state is changed so that an AI agent can
resume work without depending on chat history.

## 2. State ownership

| Information | Owner |
|---|---|
| Current project state | PROJECT_MASTER_STATE.yaml |
| Agent startup rules | AGENTS.md |
| Current Work execution state | WORK_EXECUTION_STATE.json |
| Observed execution history | harness/build-log.md |
| Phase / Task State Machine | harness/PHASE_TASK_STATE_MACHINE.yaml |
| Unified Contract Registry | harness/UNIFIED_CONTRACT_REGISTRY.yaml |
| Contract Resolution Protocol | harness/CONTRACT_RESOLUTION_PROTOCOL.md |
| Detailed phase plan | harness/build/ |
| Phase discoveries/decisions | harness/context/ |
| Design authority | design/system_v3/11_Freeze_Governance/ |
| Runtime evidence | existing QA/evidence/artifact paths |

Do not duplicate large evidence payloads into PROJECT_MASTER_STATE.yaml.

## 3. Update triggers

Update the master state when any of the following changes:

- current execution baseline SHA;
- current branch or PR;
- phase status;
- Work stage/status;
- feature status;
- CI result;
- runtime result;
- APK artifact or APK SHA-256;
- evidence availability;
- blocker status;
- material product/design/architecture decision;
- next action;
- phase/task transition;
- retry/recovery state;
- release readiness.

A documentation-only commit does not automatically change the tracked execution
baseline. Keep the product/runtime baseline explicitly identified.

## 4. State transaction

Every material update follows this order:

1. Inspect the live repository and current HEAD.
2. Identify the exact evidence or decision that changed.
3. Update PROJECT_MASTER_STATE.yaml.
4. Append an observation to harness/build-log.md.
5. Resolve the Unified Contract Registry for the changed scope.
6. Add or update detailed evidence in its owning location.
7. Run scripts/validate_project_master_state.py.
8. Run scripts/validate_unified_contract.py.
9. Run affected tests/checks.
10. Commit the coherent state/evidence update.
11. Re-read the remote branch HEAD.
12. If the product/runtime baseline changed, update the tracked execution baseline
    to the new exact implementation SHA and repeat validation.
13. Push when the current task authorizes GitHub delivery.

## 5. Status transition rules

### PASS

Use only when:

- the required gate actually ran;
- the expected assertions passed;
- the evidence exists;
- the evidence is traceable to the claimed source SHA.

### FAIL

Use when:

- the required gate actually ran;
- at least one required assertion failed.

### BLOCKED

Use when:

- a required dependency prevents execution or acceptance;
- the dependency is named;
- the unblock condition is recorded.

### NOT_RUN

Use when:

- the check has not been executed;
- no inference is allowed.

### NOT_CONFIRMED

Use when:

- authoritative sources conflict;
- provenance is incomplete;
- current-branch truth cannot yet be established.

### HISTORICAL

Use for old results that remain useful for provenance but cannot establish
current status.

## 6. Exact-SHA provenance

For every runtime result, record:

- source SHA;
- workflow run ID;
- test suite;
- device/API;
- APK artifact ID/name;
- APK SHA-256 if verified;
- evidence location;
- gate classification.

Artifact ZIP digest is not the same thing as APK SHA-256. Never copy an artifact
ZIP digest into the APK SHA-256 field.

A result from SHA A cannot make SHA B PASS.

## 7. Runtime acceptance rule

The following are separate gates:

1. source/static validation;
2. build;
3. unit tests;
4. AndroidTest compilation;
5. APK packaging;
6. device/runtime execution;
7. visual/geometry evidence;
8. motion/accessibility evidence;
9. end-to-end journey acceptance;
10. release readiness.

A skipped runtime job is NOT_RUN, not PASS.

A build-generated APK is not a release candidate until the required runtime and
evidence gates pass.

## 8. Blocker management

When opening a blocker, record:

- blocker ID;
- severity;
- area;
- exact observed condition;
- affected gate;
- unblock condition;
- whether independent work may continue.

When closing a blocker:

- record the evidence that closed it;
- update the relevant gate;
- retain the historical failure record.

Never delete the failed evidence merely because a later run passes.

## 9. Next-action management

PROJECT_MASTER_STATE.yaml must always expose the next executable actions.

Rules:

- action IDs are unique;
- at least one action is READY unless the project is explicitly awaiting human
  approval or blocked;
- each action has an owner;
- each action has a concrete verb;
- dependencies are explicit;
- completed actions are removed from the active queue or marked completed in
  the detailed Work log.

Do not leave a vague action such as "continue development".

Prefer:

"Run API28 recognition-frozen against exact HEAD X and record runtime evidence."

## 10. Work stage checkpoint

For each stage:

### Start

Record:

- stage ID;
- source SHA;
- goal;
- applicable contract;
- acceptance gate;
- known blockers.

### Execute

Record:

- implementation changes;
- discoveries;
- deviations;
- test output;
- evidence paths.

### Close

Record:

- exact commit SHA;
- tests;
- runtime result;
- evidence;
- blocker status;
- next action.

A stage is not closed merely because the code compiles.

## 11. Agent interruption/recovery

If an agent stops unexpectedly:

1. Read AGENTS.md.
2. Read PROJECT_MASTER_STATE.yaml.
3. Verify current branch and HEAD.
4. Read harness/FEATURE_MATRIX.yaml.
5. Read harness/UNIFIED_CONTRACT_REGISTRY.yaml.
6. Read harness/PHASE_TASK_STATE_MACHINE.yaml.
7. Read current Work state.
8. Read the last build-log entry.
9. Check open blockers.
10. Resolve the current task's contract bindings.
11. Identify the first unfinished next_action.
12. Continue from the latest committed state.

Never reconstruct the project solely from conversation history.

## 12. Conflict resolution

If two durable sources disagree:

1. do not guess;
2. inspect current branch contents;
3. inspect exact commit ancestry;
4. inspect the PR;
5. inspect runtime/evidence provenance;
6. record the conflict;
7. set affected state to NOT_CONFIRMED or BLOCKED;
8. resolve only after evidence establishes the current truth.

Historical documentation may be superseded without being deleted.

## 13. Git transaction rules

State changes are ordinary version-controlled project changes.

Do not:

- force-push;
- rewrite history;
- merge main without explicit authorization;
- delete historical evidence;
- silently modify frozen assets;
- silently modify model thresholds;
- silently modify Backend/API/database contracts.

Prefer:

- additive commits;
- one coherent change per commit;
- state + evidence updates in the same commit when they describe the same event;
- exact SHA references in commit messages or logs.

## 14. Release gate

P9 may become PASS only when:

- required implementation gates pass;
- required runtime matrix passes;
- required journey acceptance passes;
- required visual/motion/accessibility evidence exists;
- exact release APK SHA-256 is verified;
- no unresolved P0/P1 blocker remains for the release scope;
- human release authorization is obtained.

## 15. Example

A runtime failure is observed on SHA A:

- runtime gate = FAIL;
- blocker = OPEN;
- build log records the failure;
- fix is committed as SHA B;
- state still records SHA A as historical failure;
- SHA B becomes the tracked execution baseline;
- runtime is rerun on SHA B;
- only the SHA B result can promote the current gate.

## 16. Validation command

Run from repository root:

python scripts/validate_project_master_state.py
python scripts/validate_unified_contract.py

Expected success markers:

PROJECT_MASTER_STATE_VALIDATION=PASS
UNIFIED_CONTRACT_VALIDATION=PASS


## 17. Phase / Task State Machine

The execution controller is harness/PHASE_TASK_STATE_MACHINE.yaml.

At task start, verify:

- current phase and task;
- exact queue status for N001-N010;
- dependency tasks;
- named blockers;
- retry_count and retry_budget;
- exact-SHA evidence requirements.

Task lifecycle:

READY -> RUNNING -> VALIDATING -> PASS -> CLOSED

Failure/recovery:

VALIDATING -> FAIL -> RECOVERING -> RUNNING

Dependency or infrastructure stop:

VALIDATING -> BLOCKED

Human release:

FINAL_GATE -> CLOSED only after explicit human authorization.

Automatic repair is bounded to the initial attempt plus two repairs. The third
failure is classified and stopped for that gate. Failed evidence is retained.

A task cannot be marked PASS from historical evidence or from a different source SHA.

## 18. Unified Contract Layer

`harness/UNIFIED_CONTRACT_REGISTRY.yaml` is the single machine-readable resolver across
Product, Feature, Journey, Child Surface, Route, Page, Design, Component, Token, Asset,
State, Data/API, Model, Android Implementation, Runtime, Accessibility, Evidence and Release.
It indexes domain authorities; it does not replace them.

Every executable task must resolve its contract references before implementation. A
structural registry PASS is not a runtime or release PASS. Missing/partial contracts
remain explicit and are governed by the Phase/Task State Machine.


## V1.1 — Static / Dynamic Boundary
The Control Plane defines rules; it does not store live Work progress.

- Static on main: registry, Feature Matrix, Phase/Task Machine, Unified Contract, Evidence/Recovery rules and canonical design authorities.
- Dynamic on a Work branch: PROJECT_MASTER_STATE.yaml, generic WORK_EXECUTION_STATE.json, checkpoints, logs, CI/runtime observations, evidence and APK provenance.
- A Work status change never requires a main Control Plane commit.
- A task/design/contract/gate/recovery rule change requires a Control Plane change on main first, followed by branch resynchronization and re-admission.

## 19. Work lifecycle, previews, batches, and user feedback V1.1

Follow `harness/WORK_INSTRUCTION_PROTOCOL_V1_1.md` and the policy indexed by
`CONTROL_PLANE_REGISTRY.yaml`.

- Record `USER_PREVIEW` provenance in branch-local Work state: exact source
  SHA/branch, Actions Run ID, artifact URL, APK SHA-256, real quick-check
  results, and `api28_status=NOT_RUN`. A preview is never formal acceptance or
  Release PASS.
- A central batch records Batch ID, base SHA, exact Integration SHA, ordered
  PR/head SHAs, exact APK SHA-256, Workflow Run IDs, queue/start/finish times,
  per-gate results, and evidence links. Store scheduler records on its
  dedicated ledger branch, never on `main`.
- A batch result belongs to its Integration SHA. Component PRs remain
  `TESTED_INTEGRATED` until each exact PR SHA is independently tested.
- Preserve replaced candidates as `CANCELLED_SUPERSEDED`. Runner or artifact
  infrastructure failure is `BLOCKED_INFRA` and retains the candidate for a
  later batch. Do not convert a skipped/queued job to PASS.
- Add user reports using `harness/USER_FEEDBACK_SCHEMA_V1.json`. A fixed item
  remains `FIXED_AWAITING_RETEST` until the user tests the replacement APK;
  automated API28 evidence cannot close that feedback.


## Work State Migration V1.1
Legacy feature-specific Work state paths such as `docs/qa/recognition_main_journey/WORK_EXECUTION_STATE.json` are retired. Every active Work branch uses root `WORK_EXECUTION_STATE.json`.

Required identity fields: `work_id`, `branch`, `control_plane_revision`, `base_sha`, `phase`, `task_id`, `stage`, `status`, `source_sha` and `next_action`.

The Work state records the live pointer; the Phase/Task Machine records the static definition. Never put the live pointer back into the machine.

## Work V2 failure convergence (optional field in existing state)

`execution_v2` is an optional structured field in the existing branch-local
`WORK_EXECUTION_STATE.json`, not an independent authority. The field stores
a stable failure ID from (task_id, gate_id, symptom_key), failed source SHAs,
hypotheses and evidence. Before each repair, the Work must run the
`tools/work_execution_v2/failure_gate.py check` command; after a failure,
run `record`. Exit status 3 means stop and escalate. A third distinct failed
source SHA makes the work `BLOCKED`; a fourth is rejected. The Work must
persist checkpoints at its already approved private or safe location and
apply the usual Project Master/log/evidence transaction. A locally verified
fix does not close a release or Runtime Evidence Gate. Never make a new
dynamic-state file into a parallel master or upload restricted metadata.
