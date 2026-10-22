# YuJian Work Instruction Protocol V1.1

## Authority and startup

`CONTROL_PLANE_REGISTRY.yaml` remains the only Work entrypoint. It indexes this protocol, the API28 batch policy, the user-feedback schema, and the instruction template; none creates a second task authority.

Before implementation, design consumption, tests, runtime, or release actions:

1. Fetch `origin/main` and record branch, `HEAD`, clean/dirty state, and exact `origin/main` SHA. Preserve unrelated local changes.
2. Read `AGENTS.md` and `CONTROL_PLANE_REGISTRY.yaml`, then resolve all six layers and the current `CONTROL_PLANE_CHANGE_SYNC.md` and `harness/STATE_UPDATE_PROTOCOL.md` rules.
3. Run `python scripts/validate_control_plane_admission.py` and retain its real output. `FAIL`, unavailable, stale, or contradictory authority means stop implementation and name the exact unblock condition.
4. On recovery, repeat the same live SHA and Admission checks before reading the branch-local `WORK_EXECUTION_STATE.json` checkpoint and resuming its first unfinished action.

Control-plane proposals use `control/*` or `design/*` and static admission. Product Work uses a product branch synchronized to current `main` and requires branch-local state. Do not merge product PRs, change frozen UI contracts, push dynamic Project/Work state or build logs to `main`, or treat chat history as repository truth.

## Work lifecycle and artifact classes

Keep these states distinct in task state and delivery evidence:

| Class / state | Meaning |
| --- | --- |
| `IMPLEMENTATION` | Source changes in progress; no delivery claim. |
| `QUICK_CHECK` | Static checks, unit tests, lint, AndroidTest compilation and APK build on one exact source SHA. |
| `USER_PREVIEW` | Installable APK after quick checks; `api28_status=NOT_RUN`; never formal acceptance or release PASS. |
| `API28_QUEUED` | Candidate is durably recorded by PR number and head SHA; no runtime result. |
| `API28_BATCH` | A controlled batch tests one exact Integration SHA and one APK. Record each component PR/head SHA separately. |
| `FORMAL_ACCEPTANCE` | Only the exact Integration/source SHA whose required batch gates and evidence passed. It does not authorize release. |
| `RELEASE_CANDIDATE` | Exact APK and required release evidence are ready for N009 human authorization. |

The same PR SHA may receive independent quick-check results. A batch PASS applies to its Integration SHA only. Never copy it to the component PR SHAs. The Integration APK must be built once, hashed, and reused by every gate in that batch.

## Candidate and batch lifecycle

Only a maintainer-issued `api28-ready` label plus exact-SHA quick checks and the required PR metadata may enqueue a task. Candidates must identify their Work Task ID, required API28 gates, and either named dependency PRs or `none`. Fork code, missing task metadata, missing checks, stale heads, and unstated dependencies are rejected or blocked.

The scheduler writes candidate and batch records to the dedicated `api28-batch-ledger` branch; GitHub concurrency is only a mutex and never the queue database. Each PR number/head SHA pair is idempotent. If the same PR gets a new head, preserve the old record as `CANCELLED_SUPERSEDED`, including its old SHA, replacement SHA, timestamp, and reason. Do not delete old records.

Before integration, sort candidates deterministically and check explicit dependencies. A merge conflict becomes `BLOCKED_CONFLICT`; a missing or blocked dependency becomes `BLOCKED_DEPENDENCY`; neither may be silently omitted from a PASS claim. Compatible candidates produce one deterministic Integration SHA. Store its base SHA, ordered component PR/head SHAs, tree/commit SHA, and required gate set.

The midday batch is `CORE`; the evening batch is `FULL`. A candidate whose declared gates are outside the current scope remains queued for the next compatible batch. `CORE_PASS` is not `FORMAL_ACCEPTANCE`; only all declared required gates in an authorized full batch can produce batch-level formal acceptance. A schedule may be delayed by GitHub; record the actual dispatch, queue, start, and finish times rather than claiming the cron fired on time.

Runner unavailable, emulator preflight failure, API outage, artifact loss, or timeout is `BLOCKED_INFRA`, with candidate retained for retry. Test assertion failure is `FAIL`, not infrastructure. Preserve logs and failed evidence. Cancellation is allowed only with an explicit reason such as superseded SHA, withdrawn PR, incompatible dependency, user cancellation, or stale run; retain the record.

Emergency runs require an authorized actor, a recorded authorization source, and a reason. The operator can choose the emergency scope; candidates remain in deterministic PR-number order and emergency mode does not bypass Admission, quick checks, exact-SHA provenance, frozen contracts, required gates, or the global API28 mutex. Main-only legacy workflow-dispatch diagnostics remain non-formal and cannot create a batch PASS.

## Feedback and completion

Every user report uses `harness/USER_FEEDBACK_SCHEMA_V1.json` and binds feedback to Task ID, PR, preview source SHA, preview APK SHA-256, device profile, observed and expected behavior, reproduction steps, evidence links, severity, fix SHA, replacement APK SHA-256, and user retest status. Never close feedback before the user retests the replacement APK. A user retest cannot substitute for API28 automation; API28 PASS cannot close unresolved user feedback.

At Work completion, report exact branch and source SHA, current control-plane SHA, PR state, real Admission and quick-check results, workflow Run IDs, APK artifact class and SHA-256, API28 status, batch ID/Integration SHA when applicable, test gate results, evidence links, feedback disposition, blockers, and next action. Use `NOT_RUN`, `NOT_CONFIRMED`, or `BLOCKED_INFRA` when evidence does not support PASS. Do not infer success from an uploaded APK or a queued workflow.

## Permission and public-data boundary

Ordinary push/PR jobs run on GitHub-hosted runners with read-only repository permissions. Only the main-branch central scheduler may request formal API28 batches; main-only manual diagnostics are explicitly non-formal. Candidate source is built on a hosted runner. The self-hosted runner checks out the trusted harness from the scheduler's exact `main` SHA, then installs the exact candidate APK pair; candidate app and instrumentation code executes in the API28 emulator. The API28 job token is read only and no repository secrets are exposed to that job. The host must be isolated from sensitive runner credentials and production networks. All API28 entrypoints use the same global concurrency group.

The ledger branch may store public provenance (batch IDs, public PR/head SHAs, gate states, artifact hashes, timestamps, and evidence links). Do not place credentials, personal data, raw user feedback, restricted screenshots, or `PROJECT_MASTER_STATE.yaml`, `WORK_EXECUTION_STATE.json`, or runtime build logs on `main`. Keep sensitive feedback evidence at its approved private location and link it only when access is appropriate.
