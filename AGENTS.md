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
- Use `harness/WORK_INSTRUCTION_PROTOCOL_V1_1.md` for Work start/resume, preview, API28 batch, feedback, and completion semantics. Ordinary push/PR workflows must not launch API28. Formal API28 batches start only from `.github/workflows/api28-batch-acceptance.yml` on `main`; preserve legacy API28 as main-only diagnostic during migration.
- The API28 queue is durable state on the dedicated `api28-batch-ledger` branch. GitHub concurrency is a mutex only. Never copy an integration SHA PASS to component PR/head SHAs.
- `USER_PREVIEW` APKs require `api28_status=NOT_RUN` and cannot be promoted to formal or release PASS. User feedback stays open until a replacement APK is retested by the user.

## Work V2 bounded recovery (after admission)

For a repeated failing gate, invoke the approved `tools/work_execution_v2/failure_gate.py check`
against the current branch's `WORK_EXECUTION_STATE.json` before another repair.
Record each actual failed source run with `failure_gate.py record` and checkpoint
per `harness/STATE_UPDATE_PROTOCOL.md`. Nonzero guard exit is a hard stop:
do not bypass it by changing source SHA or opening a different branch for the
same failure. Reopen only with a separately evidenced, distinct root cause and
the applicable authorization. This mechanism never promotes a gate to PASS.
Never push sensitive dynamic state to a public destination.
