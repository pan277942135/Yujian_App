# First-use Camera Guide V1 · Task Scope Resolution

**Status:** ACTIVE FOR `RECOGNITION-FIRST-USE-GUIDE-V1`\
**Date:** 2026-10-16

## Resolution

The explicitly requested V1 work task at `docs/qa/recognition_main_journey/WORK_TASK_FIRST_USE_CAMERA_GUIDE_V1.md` defines the active scope for PR #145 as three pages. This resolves `C-FIRST-USE-GUIDE-PAGE-COUNT` for this task.

| Page | Phone | Fish |
|---|---|---|
| P01 | Portrait | Horizontal |
| P02 | Portrait | Vertical |
| P03 | Landscape | Horizontal |

The task requires the exact original P01/P02/P03 Frozen PNG bytes already registered in `asset_import_report.json`. Their paths, bytes, dimensions and SHA-256 values remain unchanged.

## Authority boundary

`Freeze_Decision_20261016_V2.md` records the earlier two-page proposal. It is retained for provenance, but is superseded by the current three-page V1 scope. The three-page scope is not a visual redesign and does not alter Frozen artwork.

Android Runtime must continue to rebuild the guide with Compose. Independent page-specific runtime photos remain a separate asset-source acceptance item; full-page Frozen screenshots are not runtime resources.
