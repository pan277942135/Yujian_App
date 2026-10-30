# Recognition QA User Fixture Changelog

## Recognition test catalog 1.1.0 — 2026-10-17 (test user data remains 1.1.0)
- Approved PER-04/PER-05/PROC-07/NAV-03/NAV-04/NAV-05/NAV-07 behavior rules.
- Machine-readable seven-decisions policy, approved assertion variants, and CI checks.
- Base case design state: seven FROZEN, 59 DRAFT; execution still entirely NOT_RUN.
- E2E-01/E2E-09 behavior ambiguity closed; E2E-06/E2E-07 lack verified real photo data.
- PROC-07 configured timeout still pending; NAV-07 requires real Android lifecycle evidence.

## 1.1.0 — 2026-10-17
- Bind all 66 planned main-flow base cases to one of four deterministic user states, with a bidirectional reverse index.
- Add 10 composed E2E scenarios (marked REAL_DEVICE or controlled injection) and test input registry.
- Add versioned catalog validator, 14 negative/regression tests and static review matrix.
- Preserve NOT_RUN until native/API28 evidence is produced; pending return/cancellation rules stay REVIEW.

## 1.0.0 — 2026-10-17
- Define stable local guest \`QA_RECOGNITION_001\`.
- Four reusable resettable states: FIRST_USE, GUIDE_PENDING, RETURNING, PERMISSION_DENIED.
- Pin real app-private guest archive and first-use guide preference keys.
- Add debug-only opt-in seed bridge and cryptographic manifest receipt.
- Require secrets-free, Git-versioned manifest validation and regression tests.
- Candidate mappings to recognition-only test cases; no Runtime or release PASS claimed.

Versioning policy: PATCH for metadata/docs only; MINOR for additive compatible state/test mappings; MAJOR for state/reset semantics changes.
