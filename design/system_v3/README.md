# YuJian Design System V3.1 — Phase 1

**Status: AUDIT_DRAFT.** This directory is a versioned audit index; it does not replace or rewrite current frozen authorities.

## Phase 1 purpose

Build an evidence-backed product flow, page/state/visual authority map, asset integrity register, design-contract audit, readiness baseline, and a Design Manager PRD overview draft.

## Authority and safety rules

- Source baseline: `pan277942135/Yujian_App` main at `7737f293ea3831aeea2c6f58308961cdb2ba7d01` (2026-10-11, Asia/Shanghai).
- Existing registries and feature-local authority files remain authoritative for their declared scope.
- V3.1 findings carry audit status separately from the existing product freeze status.
- Binary identity is only `VERIFIED` after raw bytes are read and SHA-256 is recomputed. Git object SHA is recorded separately and is not treated as SHA-256.
- Phase 1 is design-only: no Android, Backend, database, API, Frozen asset, or main changes.

## Checkpoint status

- A — repository tree and asset inventory: recorded in `11_Freeze_Governance/audits/Repository_Inventory_Checkpoint_A.json`.
- B–E — pending as the audit is built. Each checkpoint will add evidence and an updated status.

## Entry points

- [Product Master Flow](00_Product_Master_Flow/Product_Master_Flow_V3_1.md)
- [Page / State / Hi-Fi Map](01_Page_Registry/Page_State_Hifi_Map_V3_1.md)
- [Full Design Audit](11_Freeze_Governance/audits/Full_Design_System_Audit_V3_1.md)
- [Conflicts and Gaps](11_Freeze_Governance/audits/Conflicts_And_Gaps_V3_1.md)
