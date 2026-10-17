# Recognition QA User Fixture Changelog

## 1.0.0 — 2026-10-17
- Define stable local guest \`QA_RECOGNITION_001\`.
- Four reusable resettable states: FIRST_USE, GUIDE_PENDING, RETURNING, PERMISSION_DENIED.
- Pin real app-private guest archive and first-use guide preference keys.
- Add debug-only opt-in seed bridge and cryptographic manifest receipt.
- Require secrets-free, Git-versioned manifest validation and regression tests.
- Candidate mappings to recognition-only test cases; no Runtime or release PASS claimed.

Versioning policy: PATCH for metadata/docs only; MINOR for additive compatible state/test mappings; MAJOR for state/reset semantics changes.
