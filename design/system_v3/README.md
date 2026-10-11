# YuJian Design System V3.1 — Phase 1

**Status: AUDIT_DRAFT.** This is a versioned audit index; it does not replace or rewrite existing frozen authorities.

## Phase 1 purpose

Build an evidence-backed product flow, page/state/visual authority map, asset integrity register, contract audit, readiness baseline, and a Design Manager PRD overview draft.

## Authority and boundaries

- Baseline: `pan277942135/Yujian_App` main at `7737f293ea3831aeea2c6f58308961cdb2ba7d01` (2026-10-11 audit).
- Existing registries and feature-local authority files remain authoritative for their declared scope.
- V3.1 findings carry audit status separately from product freeze status.
- Git blob SHA is recorded separately from SHA-256.
- Phase 1 is design-only: no Android, Backend, database, API, Frozen asset or main changes.

## Checkpoint status

- A — repository tree and asset inventory: `11_Freeze_Governance/audits/Repository_Inventory_Checkpoint_A.json`.
- B — product master flow, registry map, subflows and Design Manager PRD draft: complete.
- C — asset integrity register, full design-system audit, and contract completeness matrix: complete; 658 design files inventoried, 579 raw SHA-256 values computed, 79 raw binaries blocked by the GitHub Contents API.
- D — conflict and decision registers, 150-node readiness baseline, and preliminary API/data dependency map: complete.
- E — baseline asset verifier and Design Governance integration: added; the PR check recomputes all baseline hashes and validates the single recorded manager-index delta.

## Entry points

- [Product Master Flow](00_Product_Master_Flow/Product_Master_Flow_V3_1.md)
- [Page / State / Hi-Fi Map](01_Page_Registry/Page_State_Hifi_Map_V3_1.md)
- [Asset Integrity Register](06_Asset_System/Asset_Integrity_Register_V3_1.json)
- [Full Design System Audit](11_Freeze_Governance/audits/Full_Design_System_Audit_V3_1.md)
- [Contract Completeness Matrix](11_Freeze_Governance/audits/Contract_Completeness_Matrix_V3_1.json)
- [Conflicts and Gaps](11_Freeze_Governance/audits/Conflicts_And_Gaps_V3_1.md)
- [Decisions Required](11_Freeze_Governance/audits/Phase1_Decisions_Required_V3_1.md)
- [Design Readiness Baseline](11_Freeze_Governance/audits/Design_Readiness_Baseline_V3_1.json)
- [Preliminary Dependency Map](11_Freeze_Governance/audits/Preliminary_Dependency_Map_V3_1.json)
- [Phase 1 Asset Verifier](../../scripts/design/verify_system_v3_phase1.py)
