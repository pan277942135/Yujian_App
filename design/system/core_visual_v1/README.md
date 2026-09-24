# YuJian Core Visual System V1

Status: **SOURCE OF TRUTH — SPECIFICATION ESTABLISHED**

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
