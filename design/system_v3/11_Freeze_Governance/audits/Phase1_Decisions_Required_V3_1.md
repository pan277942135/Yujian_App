# Phase 1 Decisions Required V3.1

**Status:** PHASE 1 CLOSED · P0 = 0 · D-007, D-015 and D-014 RESOLVED · C-007, C-012 and C-014 RESOLVED; remaining P1/P2 items stay open.
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
| D-007 | Design system owner | **RESOLVED:** dynamic UI uses Platform Typography; the brand wordmark uses the registered Frozen Artwork Asset (`dynamic_text=false`, `font_dependency=none`). | No page font overrides beyond registered tokens/authorities; no new calligraphic font, simulated brand font, or logo redraw. | Closed 2026-10-11 |
| D-008 | Design + accessibility owner | Approve a common responsive and accessibility acceptance matrix. | Name supported viewports/orientations, large-text behavior, contrast, focus order, minimum touch targets and failure/permission-state checks. | Before declaring the full page set engineering-ready |
| D-009 | Product + content owner | Confirm the user-facing meaning of Image Quality recovery. | Use broad “retake” guidance that covers framing/eligibility; or distinguish blur, distance, occlusion and other machine-detected causes where reliable. | Before changing frozen copy or error mapping |
| D-010 | Product + legal/privacy owner | Set scope and copy for deferred account lifecycle actions. | Keep password reset/export/delete deferred; or approve their user flows, legal copy, API, retention and error states. | Before enabling those controls |
| D-011 | Android + backend + product owners | Close dynamic field lineage. | Map each displayed field to repository/model, request/response, database column or local store, sort/filter, ownership, empty/error state and evidence. | Before implementation acceptance |
| D-012 | Design system + CI owner | **RESOLVED:** Materialize the missing SHA-256 values in the V3 registry. | All 658 baseline files now have raw-byte SHA-256 values and size verification; Git blob identity remains a separate field. | Closed in Phase 2 Final Freeze |
| D-013 | Android UI owner | Define SVG icon conversion and hit-target verification. | Keep design masters with no Android change in Phase 1; plan an implementation mapping from SVG identifiers to Android resources and page-level 44dp checks. | Later implementation gate |
| D-014 | Design system owner | **RESOLVED:** freeze Selector Core + Species Picker Variant with one visual authority, declared states, commit/return, keyboard/focus, target-size, fontScale and executable acceptance. | My Catches F2 reuses existing single-select visual/geometry with multi-select persistence; no new frozen screen is created. | Closed 2026-10-11 |

## Decision Record — Save Catch navigation

**ID:** `DECISION_SAVE_CATCH_NAVIGATION_V1`
**状态:** **RESOLVED**
**最终规则:** `Recognition Result → Save Catch → Home`

Save Catch 完成鱼获记录，不代表进入详情。FishRecordDetail 定位为鱼获沉淀与回顾体验，用户之后可从 Home 最近鱼获、My Catches、Fish Guide 鱼获关联或 Share 进入。独立 CTA「继续记忆」仍作为直接记忆入口，不改变普通 Save Catch 语义。

该决定由用户于 2026-10-11 选择当前冻结规则，关闭唯一 P0 C-001；未修改 Frozen 视觉或运行时代码。

## Remaining decisions (P1 / P2)

## Ready-to-decide order

1. D-002 / D-003 Record Date route and sample-data authority.
2. D-004 / D-005 Habitat and share-template product scope.
3. D-006 / D-011 persistence and dynamic-field lineage.
4. D-008 common responsive/accessibility matrix; D-007 is resolved.
5. D-009 / D-010 error wording and account lifecycle scope.

Phase 2 Final Freeze recomputed all 658 baseline file SHA-256 values from raw bytes and closed D-012 / C-012. Other owner decisions remain open unless explicitly resolved.

## Phase 2 shared foundation decision

| ID | Owner | Decision required | Choices / evidence needed | Gate |
|---|---|---|---|---|
| D-015 | Design system + Product/Accessibility owner | **RESOLVED:** approved recommended Brand Primary/Secondary and Success/Warning/Error/Info pairs are tokenized in the FINAL Color registry. | Danger remains Page Scoped; Error is system/operation failure. Alternatives remain non-effective. | Closed 2026-10-11 |


## Decision Record — D-007 Typography

**Status:** `RESOLVED` — Typography `READY`.

**Approved rule:** Dynamic UI text uses the Platform Typography token registry, Android platform SansSerif, OS script/locale CJK fallback, registered weights, and system `fontScale`. Brand Typography uses a `FROZEN_ARTWORK_ASSET` with `dynamic_text=false` and `font_dependency=none`, resolved to the registered frozen Normal Home artwork identity.

No new calligraphic font, Android-simulated brand face, page-selected brand font, or logo redraw is allowed. The Frozen artwork bytes were not changed; placement remains page-owned for Phase 3.

## Decision Record — D-015 Color

**Status:** `RESOLVED` — Color `READY`.

**Approved mappings:** Brand Primary `color.action_primary` on `color.lake_white`; Brand Secondary `color.lake_blue` on `color.lake_white`; Success `color.status_success_foreground` / `color.status_success_surface`; Warning `color.status_warning_foreground` / `color.status_warning_surface`; Error `color.status_error_foreground` / `color.status_error_surface`; Info `color.status_info_foreground` / `color.status_info_surface`. Only the recommended candidate pairs were approved.

**Danger:** remains Page Scoped and is not a Global Semantic. Error is reserved for system/AI/network/upload failure; Danger is for user-initiated destructive actions.

## Decision Record — D-014 Species Picker

**Status:** `RESOLVED` — shared `input.selector` contract `FROZEN`.

Selector Core and Species Picker Variant reuse the existing frozen authority. The shared contract covers search, states, single-select Tap Commit + Return, persistent multi-select OR semantics, keyboard/back behavior, 44×44dp targets, fontScale, accessibility and acceptance. My Catches F2 retains its page-owned shell and adds no new bitmap or picker authority.

## Remaining decisions (P1 / P2)

D-007, D-014 and D-015 are closed. Remaining P1 findings: C-002–C-006 and C-008–C-011 (9 total). Remaining P2 finding: C-013 (Android SVG resource mapping and 44dp runtime verification). C-008 remains a Page Contract finding; C-013 remains downstream implementation/test scope. Neither blocks the seven-system Shared Foundation gate.
