# Contract Resolution Protocol V1 — YuJian

## Purpose

This protocol tells Work how to resolve the **one authoritative contract set** before
touching product code.

The machine-readable resolver is:

`harness/UNIFIED_CONTRACT_REGISTRY.yaml`

The registry binds, but does not replace, the domain authorities.

## Mandatory lookup

For every implementation task:

1. Read `PROJECT_MASTER_STATE.yaml`.
2. Read `harness/FEATURE_MATRIX.yaml`.
3. Read `harness/UNIFIED_CONTRACT_REGISTRY.yaml`.
4. Read `harness/PHASE_TASK_STATE_MACHINE.yaml`.
5. Resolve the current task's Feature IDs.
6. Resolve the affected Child Surface(s).
7. Resolve the Route(s).
8. Resolve each required Contract Domain.
9. Read the named authority files before editing code.
10. Read the Task Gate and Evidence requirements.
11. Only then implement.

## Resolution order

```text
Task
  ↓
Feature
  ↓
Child Surface
  ↓
Journey / Route
  ↓
Contract Domain
  ↓
Authoritative Source
  ↓
Acceptance Gate
  ↓
Evidence
```

## Domain contract set

At minimum, Work must resolve these domains when applicable:

- behavior
- visual
- state
- data
- implementation
- runtime
- accessibility
- evidence
- asset

Additional domains:

- motion
- haptic
- sound
- model
- release

## Missing / conflict behavior

### MISSING

No authoritative contract exists.

Work must:

- not invent one;
- not copy a neighboring feature's contract;
- not mark the feature READY;
- record the gap;
- continue only if the Phase/Task State Machine explicitly permits independent work.

### PARTIAL

Some dimensions exist.

Work may implement only the explicitly contracted portion. Do not silently fill
the missing dimensions.

### NOT_CONFIRMED

Two authoritative sources conflict or provenance is incomplete.

Work must stop the affected promotion and resolve the conflict with evidence.

### HISTORICAL

Useful for diagnosis only.

It cannot make the current execution SHA PASS.

## Shared vs page-owned authority

Shared Foundation provides defaults for:

- typography
- color
- geometry
- components
- registered assets

A page-owned contract may override a shared rule only inside its explicitly declared
scope.

Example:

```text
Shared Action Button
      ↓
Recognition Result page-scoped variant
      ↓
Android implementation binding
```

The page-scoped variant does not create a new global component.

## Exact-SHA rule

The Contract Registry is resolved against the claimed execution baseline.

Runtime and evidence are bound to the exact source SHA they exercised.

Never use:

- an old APK to accept a new SHA;
- a historical runtime result to close a current blocker;
- an artifact ZIP digest as APK SHA-256;
- a build PASS as runtime PASS.

## Frozen authority rule

Do not modify without explicit authorization:

- Frozen PNG bytes;
- registered asset SHA-256;
- frozen component IDs/variants;
- effective Typography/Color/Geometry tokens;
- model thresholds/package;
- protected Backend/API/database contracts.

## Completion rule

A task is not contract-complete merely because its code compiles.

Contract completion requires:

```text
Authority resolved
+
Implementation bound
+
Required tests run
+
Required runtime/evidence produced
+
Exact SHA provenance
+
State updated
+
Git checkpoint
```

## Recovery

If Work is interrupted:

1. read the Master State;
2. read the Feature Matrix;
3. read the Unified Contract Registry;
4. read the Phase/Task Machine;
5. locate the current task;
6. resolve its contract refs;
7. resume from the latest committed checkpoint.

Do not reconstruct contract intent from conversation history.
