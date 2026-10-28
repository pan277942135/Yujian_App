# Yujian Work Agent Bootstrap

## STOP — single control-plane entry

Before any implementation, design change, test, runtime action or release action:

1. Read `CONTROL_PLANE_REGISTRY.yaml`.
2. Resolve the canonical registry from `main`.
3. Follow its six layers and synchronization gate.
4. Do not execute if the control plane is missing, stale, contradictory or not admitted.

`AGENTS.md` is only the bootstrap instruction. It is not a second task/design authority.

## Product branch rule
Product branches remain independent. Product code is never required to be merged into `main` merely to synchronize the control plane.

Task/design/control-plane changes originate on `main`, then synchronize into the active product branch.

## Required admission
Run:

```bash
python scripts/validate_control_plane_admission.py
```

This is the only Work admission command. It resolves the canonical Control Plane, refreshes `origin/main`, validates the six static layers, and for product branches validates branch synchronization plus live Project/Work state.

## State
`PROJECT_MASTER_STATE.yaml` and Work execution state are allowed to remain branch-local. They must never become an alternative control-plane definition.

## Recovery
On interruption, return to `CONTROL_PLANE_REGISTRY.yaml`, then resume from the latest durable branch-local state/checkpoint. Do not reconstruct the project from chat history.


## Control Plane Entry Rule V1.1
- The only project-process entrypoint is `CONTROL_PLANE_REGISTRY.yaml`.
- Before any Work implementation, test, runtime, design-consumption or release action, run `python scripts/validate_control_plane_admission.py`.
- Product branches must exactly synchronize the canonical Control Plane from `origin/main`.
- `control/*` and `design/*` branches are Control Plane change proposals; they run static validation and are not required to equal the current main Control Plane until merged.
- Work progress MUST NOT modify the static Control Plane. Update branch-local `PROJECT_MASTER_STATE.yaml`, generic `WORK_EXECUTION_STATE.json`, logs and evidence instead.
- A task/design/contract/gate/recovery rule change must land on main first; affected Work branches then resynchronize and re-enter admission.

## Work V2 bounded recovery (after admission)

For a repeated failing gate, invoke the approved `tools/work_execution_v2/failure_gate.py check`
against the current branch's `WORK_EXECUTION_STATE.json` before another repair.
Record each actual failed source run with `failure_gate.py record` and checkpoint
per `harness/STATE_UPDATE_PROTOCOL.md`. Nonzero guard exit is a hard stop:
do not bypass it by changing source SHA or opening a different branch for the
same failure. Reopen only with a separately evidenced, distinct root cause and
the applicable authorization. This mechanism never promotes a gate to PASS.
Never push sensitive dynamic state to a public destination.


## Green-check merge policy V1.2

- For code, design and control-plane PRs within an already authorized task scope, merge once all repository-required checks pass on the exact current PR HEAD. A separate per-PR merge authorization is not required.
- `N009` is the human release-authorization gate only; it must not block ordinary code/design PR merges.
- Never merge a failing, stale-head, out-of-scope or unresolved-conflict PR. Keep force-push, rebase and history rewrite disabled. Do not weaken required checks to make a PR mergeable.

## Remote-first execution fallback V1.2

- A missing repository in the current scratch directory is an execution-environment routing issue, not itself an Admission FAIL.
- Do not run `scripts/validate_control_plane_admission.py` outside a repository checkout and treat the resulting “file not found” as a failed project gate. Obtain a clean checkout/worktree or use a supported remote runner / GitHub API workflow.
- For remote execution, use successful admission evidence bound to the exact baseline SHA before work, then require the relevant exact-HEAD workflow checks and browser/runtime evidence for the proposed SHA. Product tasks must still satisfy their branch-local state requirements where applicable.
- If the local checkout is unavailable, continue through the supported remote path; stop only for a real control-plane validator failure, failed required check, missing authority, or unresolved product decision—not merely because `/workspace/scratch` is not a Git repository.
