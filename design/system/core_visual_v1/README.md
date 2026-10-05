# YuJian Core Visual System V1

Status: **DESIGN SOURCE OF TRUTH — FROZEN**

Core screen set:
1. Empty Home
2. Normal Home
3. Recognition Result
4. FishRecordDetail
5. My Catches
6. Fish Guide

## Baseline hierarchy
Empty Home + Normal Home define the primary environment/brand baseline.
Recognition Result + FishRecordDetail inherit that environment at reduced background salience.
My Catches + Fish Guide inherit the same world with further reduced background competition.

## Freeze gate
A screen is FROZEN only when:
- its canonical reference binary is present under `reference/`;
- its source SHA-256 is registered in `reference/reference_manifest.json`;
- its page contract exists under `design/pages/`;
- cross-page rules are satisfied.

Do not create replacement/generated reference images to close a missing-source gate.

## Current frozen source

All six Core UI V1 references are present under reference/ with canonical filenames and
verified SHA-256 records. The eight-file registry, supplemental provenance, and
machine-checkable gate are maintained in reference/reference_manifest.json.

Shared component, page-asset, extraction, cross-page, and recompose decisions are
maintained in validation/. These decisions guide implementation; frozen PNGs remain
read-only visual authority.
