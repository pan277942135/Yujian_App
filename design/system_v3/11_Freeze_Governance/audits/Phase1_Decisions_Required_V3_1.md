# Phase 1 Decisions Required V3.1

**Status:** OPEN — for product/design owners before implementation handoff.  
**Baseline:** `7737f293ea3831aeea2c6f58308961cdb2ba7d01`

This is a decision log, not an approval request. No frozen authority is changed by listing these items.

| ID | Owner | Decision required | Choices to resolve | Gate |
|---|---|---|---|---|
| D-001 | Product owner | Choose the canonical Result CTA outcome. | Keep frozen Save Catch → Home and Continue Memory → Detail B; or explicitly revise the frozen contract and adopt Save Catch → Detail A → B. Record handling of back navigation and duplicate saves. | Before any Result/Detail implementation change |
| D-002 | Product + Android owner | Decide whether Record Date V2 remains design-only or receives an Android destination. | Keep hidden/design-only; or approve route entry, date selection, aggregation timezone, empty states and behavior when a catch is edited/deleted. | Before implementation planning |
| D-003 | Product / data owner | Resolve treatment of the 68-vs-59 Record Date example. | Keep labeled illustrative and exclude from acceptance tests; or approve corrected annual/monthly sample data. | Before sample values become product truth |
| D-004 | Product owner | Decide the My Habitat scope. | Keep design-only; or approve a product entry point, route, states, metric definitions and data source. | Before Habitat implementation |
| D-005 | Product + design owner | Finalize T01/T02 share outputs. | Keep current text-only share and mark templates future; or approve visual output dimensions, content fields, privacy rules, export/share path and fallbacks. | Before template implementation |
| D-006 | Product + backend owner | Define sync behavior for detail edits and memory media. | Keep app-private local overlay; or approve remote update/supplemental-media APIs, upload failure handling, and cross-device semantics. | Before promising synced records |
| D-007 | Design system owner | Resolve the global typography token contract. | Specify family, weights, line-height, tracking, license, fallback and system scaling; identify explicitly page-local overrides. | Before shared type implementation |
| D-008 | Design + accessibility owner | Approve a common responsive and accessibility acceptance matrix. | Name supported viewports/orientations, large-text behavior, contrast, focus order, minimum touch targets and failure/permission-state checks. | Before declaring the full page set engineering-ready |
| D-009 | Product + content owner | Confirm the user-facing meaning of Image Quality recovery. | Use broad “retake” guidance that covers framing/eligibility; or distinguish blur, distance, occlusion and other machine-detected causes where reliable. | Before changing frozen copy or error mapping |
| D-010 | Product + legal/privacy owner | Set scope and copy for deferred account lifecycle actions. | Keep password reset/export/delete deferred; or approve their user flows, legal copy, API, retention and error states. | Before enabling those controls |
| D-011 | Android + backend + product owners | Close dynamic field lineage. | Map each displayed field to repository/model, request/response, database column or local store, sort/filter, ownership, empty/error state and evidence. | Before implementation acceptance |
| D-012 | Design system + CI owner | Decide how Phase 1 records unavailable raw checksums. | Recompute all baseline assets from a repository checkout in Checkpoint E; document the 19 frozen items without direct declared SHA-256 as source-bound by baseline Git blob SHA. | Checkpoint E |
| D-013 | Android UI owner | Define SVG icon conversion and hit-target verification. | Keep design masters with no Android change in Phase 1; plan an implementation mapping from SVG identifiers to Android resources and page-level 44dp checks. | Later implementation gate |
| D-014 | Design system owner | Close the partial Species Picker contract. | Name complete visual, behavior, accessibility, runtime and acceptance states or keep it out of shared integration scope. | Before shared selector implementation |

## Ready-to-decide order

1. D-001 Save Catch destination.
2. D-002 / D-003 Record Date route and sample-data authority.
3. D-004 / D-005 Habitat and share-template product scope.
4. D-006 / D-011 persistence and dynamic-field lineage.
5. D-007 / D-008 system type and accessibility gates.
6. D-009 / D-010 error wording and account lifecycle scope.
