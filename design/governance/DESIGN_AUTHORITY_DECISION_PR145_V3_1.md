# Design Authority Decision — PR #145 V3.1 Reconciliation

| Field | Value |
|---|---|
| Decision ID | `AUTH-PR145-20261016-01` |
| Status | `PROPOSED — CONTROL PLANE REVIEW REQUIRED` |
| Canonical base | `main@e4414282acad41b9554b522de46f59325b089300` |
| Source Work checkpoint | `5701512dad61fa2b634293a542804c60b70ce7b3` |
| Product PR | `#145` |
| Product branch | `audit/design-system-v3-1-phase1-master-baseline` |
| Reconciliation branch | `control/design-authority-reconcile-pr145` |

## Scope and classification

The product checkpoint contains 178 changes under the canonical design authority roots. They are classified as follows:

| Authority root | Added | Modified | Disposition |
|---|---:|---:|---|
| `design/system_v3/` | 153 | 0 | Proposed V3.1 authority package; preserve exact bytes and manifests. |
| `design/pages/` | 20 | 4 | Add evidence/guide authority and review the four contract/navigation changes. |
| `design/registry/` | 0 | 1 | Review the experience registry update against all referenced contracts and SHA records. |
| `design/governance/` | 0 | 0 | This decision record is the formal reconciliation record. |

The 20 new page files include Normal Home evidence, Recognition Capture evidence, and the first-use camera guide package. The guide package distinguishes active frozen candidates (`P01`–`P03`) from archived/superseded references; archive files are not runtime authority.

The four modified page files are:

- `design/pages/home/empty_home/shared/contracts/responsive_mapping_contract.json`
- `design/pages/home/empty_home/shared/contracts/runtime_manifest.json`
- `design/pages/recognition/result/MENU_STRUCTURE_V1.md`
- `design/pages/recognition/result/navigation.json`

## Freeze and integrity decisions

1. No existing Frozen PNG, SVG, token, or asset SHA file on `main` is overwritten by this reconciliation branch.
2. New Frozen candidates are proposed only with their existing manifests and byte-level SHA evidence; no image re-encoding or normalization is allowed.
3. Superseded guide references remain auditable under `archive/` and must not be promoted to runtime authority.
4. Any registry or contract status change remains subject to Control Plane review and downstream Feature Matrix, Task State Machine, Unified Contract, Evidence Gate, and Recovery binding validation.
5. Product implementation files are out of scope. This branch contains no `app/` changes and no Android runtime implementation changes.

## Required disposition

Control Plane reviewers must either approve this authority package as the canonical main design set or record a path-level rejection/deferral. A product branch must not delete these files, add a permanent validator exemption, or claim Admission PASS while this decision is unresolved.

If approved, merge this Control Plane PR into `main`, record the resulting main SHA, synchronize PR #145 from that exact main revision, and rerun the full Control Plane Admission. If rejected or deferred, keep PR #145 blocked and record the path-level disposition in a follow-up decision.

## Evidence

- Source diff is limited to the canonical design roots listed above.
- `git diff --check` must pass on this branch.
- Frozen asset SHA verification must be rerun after review and before any product-branch synchronization.
