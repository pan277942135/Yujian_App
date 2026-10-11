# Phase 1 Decisions Required V3.1

**Status:** PHASE 1 CLOSED · DEC-001 RESOLVED · Other decisions remain open.  
**Baseline:** `7737f293ea3831aeea2c6f58308961cdb2ba7d01`

This log records owner decisions and remaining owner decisions. Frozen page authority changes only through an explicit decision record.

| ID | Owner | Decision required | Choices to resolve | Gate |
|---|---|---|---|---|
| D-001 | Product owner | **RESOLVED:** Keep frozen Save Catch → Home; Continue Memory is the separate direct memory entry. | `DECISION_SAVE_CATCH_NAVIGATION_V1`; ordinary Save Catch does not navigate to detail. | Closed 2026-10-11; no Frozen authority changed |
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
| D-012 | Design system + CI owner | Decide the final SHA-256 policy for assets with no recorded/direct value. | Materialize the missing SHA-256 values in the V3 registry; or explicitly accept Git blob identity plus checkout recomputation as sufficient. The new V3 registry keeps 50 values REVIEW until this decision is closed. | Before Asset Ready / Phase 3 |
| D-013 | Android UI owner | Define SVG icon conversion and hit-target verification. | Keep design masters with no Android change in Phase 1; plan an implementation mapping from SVG identifiers to Android resources and page-level 44dp checks. | Later implementation gate |
| D-014 | Design system owner | Close the partial Species Picker contract. | Name complete visual, behavior, accessibility, runtime and acceptance states or keep it out of shared integration scope. | Before shared selector implementation |

## Decision Record — Save Catch navigation

**ID:** `DECISION_SAVE_CATCH_NAVIGATION_V1`  
**状态:** **RESOLVED**  
**最终规则:** `Recognition Result → Save Catch → Home`

Save Catch 完成鱼获记录，不代表进入详情。FishRecordDetail 定位为鱼获沉淀与回顾体验，用户之后可从 Home 最近鱼获、My Catches、Fish Guide 鱼获关联或 Share 进入。独立 CTA「继续记忆」仍作为直接记忆入口，不改变普通 Save Catch 语义。

该决定由用户于 2026-10-11 选择当前冻结规则，关闭唯一 P0 C-001；未修改 Frozen 视觉或运行时代码。

## Remaining decisions (P1 / P2)

## Ready-to-decide order

2. D-002 / D-003 Record Date route and sample-data authority.
3. D-004 / D-005 Habitat and share-template product scope.
4. D-006 / D-011 persistence and dynamic-field lineage.
5. D-007 / D-008 system type and accessibility gates.
6. D-009 / D-010 error wording and account lifecycle scope.

Checkpoint E verified the baseline Git objects at checkout, but the V3 registry still lacks 50 SHA-256 values; D-012 / C-012 therefore remain open. Other decisions remain open unless a later owner decision explicitly resolves them.
