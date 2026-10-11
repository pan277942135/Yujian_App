# Conflicts and Gaps V3.1 — Checkpoint D

**Status:** AUDIT_DRAFT  
**Baseline:** `7737f293ea3831aeea2c6f58308961cdb2ba7d01`  
**Review boundary:** design-only source review; no Android/backend/database mutation or runtime testing.

## Severity key

- **P0** — product owner must resolve the behavior before implementation; multiple valid routes remain.
- **P1** — blocks or materially changes implementation, content, data, legal, accessibility or runtime acceptance.
- **P2** — important verification or integration gap that can be handled in a later implementation gate.
- **CONFIRMED** means directly supported by a registry, design contract or source path cited below. It does not imply production behavior was executed.

## Conflict and gap register

| ID | Priority | Topic | Finding | Required disposition |
|---|---|---|---|---|
| C-001 | P0 | Save Catch destination is inconsistent | The user-requested master-flow shorthand says Save Catch continues into Detail A/B. Current frozen Recognition Result behavior and Android source send ordinary Save Catch to Home; Continue Memory creates the record and opens Detail B. Either flow may be intentional, but implementation cannot choose without an owner decision. | Before implementation or changing frozen authority |
| C-002 | P1 | Record Date has no Android destination | RD01/RD02 visual and RD03 behavior contracts are frozen, while registry runtime is MISSING and no destination was found in the audited Android navigation graph. | Before implementation planning |
| C-003 | P1 | Record Date annual example does not reconcile | The annual sample states 68 catches while its twelve monthly entries sum to 59. The authority labels values illustrative, so this is not a production-data discrepancy; however the visual cannot be used as a canonical acceptance fixture. | Before using the sample as data truth or test fixture |
| C-004 | P1 | My Habitat is design-only | Pond / Lake levels are registered design assets under My Catches, but the product flow explicitly says no Android route/callback was located. | Before implementation planning |
| C-005 | P1 | T01/T02 share output lacks runtime implementation | T01/T02 behavior and product model exist, but current Android share action is a text/plain chooser. No T01/T02 route or rendered image output is present. | Before template implementation |
| C-006 | P1 | Detail edits and supplemental memory remain device-local | CatchLocalOverlayStore explicitly saves edits and original memory media to app-private SharedPreferences/files because the current catches API has no update or supplemental-media endpoint. Cross-device persistence and sync behavior are not specified. | Before promising synced edits or supplemental memory |
| C-007 | P1 | Global typography contract is incomplete | Typography tokens lack one resolved cross-platform contract for family, weights, line-height, tracking, license/fallback, and system font scaling. | Before shared typography implementation |
| C-008 | P1 | Accessibility acceptance is uneven across modules | Local specs cover some features, but a shared matrix does not close viewport classes, landscape/tablet, large text, contrast, focus order, touch targets, and error/permission states for every registered page. | Before declaring page set engineering-ready |
| C-009 | P1 | Image Quality copy may overdiagnose the failure | The generic “photo is not clear enough” presentation can be reached through multiple image eligibility conditions, including too-far framing; it is not proven to mean optical blur in every case. | Before changing error copy or recovery guidance |
| C-010 | P1 | Account lifecycle operations are deferred | Password reset, export, and account deletion are shown as ComingSoon/deferred. External legal copy remains a gated content dependency. | Before enabling the corresponding entry points |
| C-011 | P1 | Dynamic-field mapping is only route-level | Current source confirms route families and backend table families, but does not provide one audited mapping from every displayed field through Android repository/DTO to API response, database column, sorting, empty/error state, and ownership. | Before final implementation acceptance |
| C-012 | P2 | Raw SHA-256 unavailable for 79 large binaries | Baseline path, file size and Git blob SHA are available for all 79; GitHub Contents API did not return raw bytes. Nineteen of 37 blocked frozen references have no direct current SHA-256 declaration. Repository identity is preserved by the baseline Git object; raw SHA-256 and some visual inspections remain outstanding. | Checkpoint E repository-side verification |
| C-013 | P2 | Frozen SVG icon assets are not Android resource parity evidence | 43 icons have frozen SVG masters and common 24×24 / 1.75 / currentColor rules, but resource conversion and per-page 44dp target verification are not part of this design audit. | During a later implementation phase |
| C-014 | P2 | Species Picker is the only non-frozen shared system | 15/16 shared systems are marked FROZEN; species_picker_v1 remains PARTIAL and needs a defined integration acceptance boundary. | Before building shared selector behavior |

## P0 implementation gate

### C-001 — Save Catch destination

The existing frozen result contract says the ordinary **Save Catch** action creates the record and returns to Home. **Continue Memory** saves and opens the Fish Record Detail B-side. The requested Phase 1 product shorthand says save then Detail A/B. These are different product outcomes. The audit records the current source behavior and leaves the requested flow unresolved; neither path was rewritten.

## Evidence notes

- Record Date V2 sample values are explicitly illustrative. The annual/month totals conflict is a design-data issue, not evidence that live catch records are inconsistent.
- My Habitat exists as a manager-design route under My Catches but the master flow marks it DESIGN_ONLY and notes that an Android destination is missing.
- A route found in source only proves a route declaration exists. It does not prove live service availability or correct values for all UI fields.
- Asset inventory scope is the repository baseline. It does not include private Library-only uploads or originals outside the repository.
