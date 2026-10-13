# Asset Registry V3 — Phase 1 inventory

**Historical status: REVIEW.** This file summarizes the original 658-row Phase 1 inventory. Its SHA gaps were closed in Phase 2.

- Current normative registry: [Asset_Registry_V3_FINAL.json](Asset_Registry_V3_FINAL.json).
- Current byte audit: [Asset_SHA_Audit_FINAL.md](Asset_SHA_Audit_FINAL.md).
- Current missing-file report: [Missing_Asset_Report_FINAL.md](Missing_Asset_Report_FINAL.md).
- All 658 baseline file SHA-256 values were recomputed from actual bytes; Git blob SHA remains a separate object identity.
- Per-entry FROZEN / APPROVED / REVIEW states remain unchanged. Unmapped usage/provenance stays visible as review metadata.

The Phase 1 Asset_Integrity_Register_V3_1.json remains an audit snapshot and is not the Phase 3 asset input.

## A.3 Brand wordmark extension

The original 658-row baseline remains byte-identical. The current branch registry adds 8 explicitly tracked Normal Home brand-title derivation and review-evidence files (666 rows total). The production artwork `brand_title_primary` is an approved 155×80 RGBA derivative of the frozen Normal Home raster; its source and extraction evidence are recorded in the adjacent manifest and audit.
