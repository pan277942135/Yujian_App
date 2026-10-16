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
python scripts/validate_control_plane_sync.py
```

Then follow the current task, contract, evidence and recovery rules resolved from `CONTROL_PLANE_REGISTRY.yaml`.

## State
`PROJECT_MASTER_STATE.yaml` and Work execution state are allowed to remain branch-local. They must never become an alternative control-plane definition.

## Recovery
On interruption, return to `CONTROL_PLANE_REGISTRY.yaml`, then resume from the latest durable branch-local state/checkpoint. Do not reconstruct the project from chat history.
