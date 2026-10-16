# Control Plane Change Synchronization Protocol V1

## Purpose
Keep task design, product design authority, execution contracts, evidence gates and recovery bindings synchronized through the canonical main control plane.

## Single entry
Every Work starts with `CONTROL_PLANE_REGISTRY.yaml`. No alternate task list, design task file, chat instruction, PR-local plan or branch-local registry may become an execution authority.

## Main-first rule
Task/design/control-plane changes originate on `main`.
Product branches remain independent implementation workspaces. A product branch may change product code and branch-local execution evidence, but it must not establish a competing task/design/control-plane authority.

## Required propagation
For every material task or design change, update the affected authoritative source(s) and then synchronize:
1. Feature Matrix
2. Phase / Task State Machine
3. Unified Contract Registry
4. Evidence Gate bindings
5. Recovery/checkpoint bindings when execution semantics change
6. branch-local Project Master State after synchronization

The change is incomplete until all affected references validate.

## Branch admission
Before Work executes:
1. Resolve the canonical `CONTROL_PLANE_REGISTRY.yaml` from `main`.
2. Verify the branch registry matches the canonical registry.
3. Run `scripts/validate_control_plane_sync.py`.
4. If stale/missing control or design authority is detected: BLOCK.
5. Synchronize control-plane-only changes from `main`; do not merge unrelated product code merely to satisfy control-plane synchronization.
6. Re-run admission until PASS.

## What may remain branch-local
- `PROJECT_MASTER_STATE.yaml`
- `WORK_EXECUTION_STATE.json`
- product implementation code
- Work-generated runtime/evidence artifacts

Branch-local state must still reference the canonical control-plane revision.

## Forbidden
- independent branch-local task plans becoming authoritative;
- modifying Feature Matrix/Phase Task/Unified Contract to create a branch-specific interpretation;
- design authority changes that are not reflected in downstream task/contract/gate bindings;
- declaring PASS while control-plane sync is stale;
- using chat history as a substitute for durable state.

## Completion
A task/design change is complete only when:
- authoritative source is updated;
- affected control-plane references are synchronized;
- validators pass;
- branch admission passes;
- the exact resulting main control-plane commit is recorded;
- product Work then executes against that synchronized control-plane revision.


## Static / Dynamic Boundary V1.1
The Control Plane defines rules; it does not store live Work progress.

Static on main: registry, Feature Matrix, Phase/Task Machine, Unified Contract, Evidence/Recovery rules and canonical design authorities.

Dynamic on a Work branch: PROJECT_MASTER_STATE.yaml, generic WORK_EXECUTION_STATE.json, checkpoints, CI/runtime observations, evidence manifests, APK provenance and next action.

A Work status change never requires a main Control Plane commit. A task/design/contract/gate/recovery rule change requires a Control Plane change on main first, followed by branch resynchronization and re-admission.
