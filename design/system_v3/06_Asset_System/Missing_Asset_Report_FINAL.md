# Missing Asset Report V3.1 Final

**Missing repository paths: 0. Missing SHA-256: 0. Missing dimension fields: 0. Duplicate asset IDs: 0 after correction.**

| Scope | Count | Result |
|---|---:|---|
| Baseline asset rows | 658 | All paths are present at baseline commit 7737f293ea3831aeea2c6f58308961cdb2ba7d01. |
| Raw SHA-256 gaps | 0 | 658/658 actual byte hashes recorded. |
| Visual dimensions gaps | 0 | 244 raster/SVG entries have intrinsic dimensions; non-visual rows are explicitly N/A. |
| Unmapped used_by arrays | 466 | Empty means no consumer was declared in the Phase 1 inventory; entries remain REVIEW and are not assigned by filename guess. |
| Per-entry REVIEW | 575 | Preserved from source audit; not treated as a missing file or hash. |
| Per-entry FROZEN | 82 | Preserved. |
| Per-entry APPROVED | 1 | Preserved. |

## Checked families

- Background roles: 5 registry entries; static master hashes cross-reference the Asset Registry. BG_CAPTURE is runtime media with no static file/hash.
- Icon masters: 43/43 SVG entries; each file, viewBox and SHA is cross-checked.
- Empty Home: source/frozen/intermediate rows are tracked by the exact baseline path.
- Normal Home: page references retain existing source authority.
- FishRecordDetail and Fish Guide: Frozen/reference PNG rows keep their content and existing per-entry status.
- My Habitat: reference assets remain associated with its design-only page scope; no route or product status is inferred.

The baseline contains no missing repository file. Provenance and consumer mapping remain review metadata for rows whose source audit left it empty; those values are not fabricated to reach the identity gate.