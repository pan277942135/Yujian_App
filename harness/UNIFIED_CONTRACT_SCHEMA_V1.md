# Unified Contract Schema V1 — 渔见

## 1. Purpose

`harness/UNIFIED_CONTRACT_REGISTRY.yaml` is the machine-readable **contract-of-contracts** for YuJian.

It does not replace the authoritative Product, Design, Page, Android, Data/API, Model,
Runtime, Accessibility, Evidence or Release documents. It binds them into one resolver
so an AI agent can answer, before implementation:

```text
What am I changing?
  -> Feature
  -> Child Surface
  -> Journey
  -> Route
  -> Contract Domain
  -> Authoritative Source
  -> Acceptance Gate
  -> Evidence
```

## 2. Contract resolution key

Every implementation scope resolves through:

`feature_id + child_surface_id + contract_domain`

If no child surface is applicable, use the feature-level scope.

## 3. Domain model

| Domain | Meaning | Typical authority |
|---|---|---|
| product | Product decisions and master flow | PROJECT_MASTER_STATE.yaml |
| feature | Feature scope/traceability | harness/FEATURE_MATRIX.yaml |
| journey | J01-J20 journey semantics | PROJECT_MASTER_STATE.yaml + FEATURE_MATRIX |
| child_surface | Sub-surface ownership | FEATURE_MATRIX |
| route | Android route ownership | FEATURE_MATRIX + YujianApp.kt |
| page | Page structure/state/data/acceptance | design/system_v3/08_Page_Contracts |
| visual | Visual/pixel authority | design/system_v3/10_Page_Visual_Acceptance_Contracts |
| component | Shared component contract | Component Registry |
| token | Typography/color/geometry | Shared token authorities |
| asset | Asset identity/frozen bytes | Asset Registry |
| state | State machine/binding | Page State Machine / Android State Binding |
| data | Data/API/database semantics | Page Data Contracts + protected backend |
| model | Production model package/trace | production_model_contract.py + backend release |
| implementation | Android implementation mapping | Android Implementation Contracts |
| runtime | Device execution | runtime harness + runtime gate |
| accessibility | Focus/semantics/responsive behavior | accessibility matrices/contracts |
| evidence | Proof/provenance | runtime/evidence manifests + state protocol |
| release | APK/release authorization | Phase Task State Machine + Master State |

## 4. Status semantics

- **FROZEN** — authoritative and immutable unless a new approved version supersedes it.
- **READY** — contract exists and is structurally usable; runtime may still be NOT_RUN.
- **ACTIVE_CLOSURE** — authoritative contract with current closure work still active.
- **PARTIAL** — some dimensions exist; missing dimensions remain explicit.
- **MISSING** — no authoritative contract exists.
- **DEFERRED** — explicitly deferred and not required for the claimed scope.
- **NOT_CONFIRMED** — conflicting authority or incomplete provenance.
- **HISTORICAL** — provenance only; never current-head PASS.

## 5. Resolver rules

1. Start from the exact execution SHA.
2. Resolve the Feature Matrix feature.
3. Resolve its child surface, journey and route bindings.
4. Resolve the requested contract domain.
5. Use the named authoritative source.
6. Apply shared foundation contracts unless a page-owned contract explicitly overrides them.
7. If the source is missing, return **MISSING**; do not invent a fallback.
8. If two authorities conflict, return **NOT_CONFIRMED/BLOCKED** and stop promotion.
9. Runtime/evidence can only promote the exact SHA they actually exercised.
10. A structural Contract Registry PASS never promotes product/runtime/release status.

## 6. Required implementation bundle

Before Work changes product code, the resolved scope must have:

- feature scope;
- applicable child surfaces;
- route(s);
- behavior contract;
- visual contract or explicit visual status;
- state contract;
- data/API boundary;
- Android implementation contract;
- runtime gate;
- accessibility requirements;
- evidence requirements;
- applicable release dependency.

Missing secondary contracts may remain OPEN only when the Phase/Task State Machine
explicitly marks the scope as deferred/blocked. Work must not silently treat missing
contracts as approved.

## 7. Evidence contract

Runtime evidence must carry:

- source SHA;
- workflow run ID;
- device/API;
- test suite;
- APK artifact;
- APK SHA-256 when verified;
- screenshots/logs/reports;
- gate classification.

Evidence from another SHA is historical.

## 8. Frozen authority rules

Never modify without explicit authorization:

- Frozen PNG bytes;
- registered asset SHA-256;
- frozen component IDs/variants;
- effective typography/color/geometry tokens;
- model thresholds/package contract;
- protected Backend/API/database contracts.

## 9. Task integration

Every Phase/Task Machine task is bound to one or more Contract IDs.

For executable N001-N010:

| Task | Contract gate |
|---|---|
| N001 | G-API28-MATRIX |
| N002 | G-RECOGNITION-FROZEN |
| N003 | G-J01-J20 |
| N004 | G-NORMAL-HOME-A4.1 |
| N005 | G-EVIDENCE-PACK |
| N006 | G-APK-PROVENANCE |
| N007 | G-PHASE3B-PROVENANCE |
| N008 | G-FOCUSED-REPORT |
| N009 | G-HUMAN-RELEASE |
| N010 | G-FIRST-USE-GUIDE-ASSET |

## 10. Validation

Run:

```bash
python scripts/validate_unified_contract.py
```

A successful structural result is:

```text
UNIFIED_CONTRACT_VALIDATION=PASS
feature_count=15
journey_count=20
child_surface_count=75
route_count=22
task_count=45
```

The validator may also report OPEN_GAPS. Open gaps are not structural failures; they
are the actual current product contract incompleteness and must remain visible.

## 11. Relationship to other control files

```text
PROJECT_MASTER_STATE.yaml
        │ project truth
        ▼
FEATURE_MATRIX.yaml
        │ scope/index
        ▼
UNIFIED_CONTRACT_REGISTRY.yaml
        │ contract resolution
        ▼
PHASE_TASK_STATE_MACHINE.yaml
        │ execution authorization
        ▼
Evidence / Runtime / APK
        │
        ▼
P9 Release Gate
```

This ordering prevents an AI agent from treating an implementation file, a historical
report, or an artifact as a substitute for the authoritative contract.
