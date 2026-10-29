# Recognition Result Android Runtime Gate V1

**Gate:** Recognition Result Android runtime acceptance  
**Contract status:** READY_TO_TEST  
**Execution status:** NOT_RUN  
**Scope:** acceptance criteria only; no Android build, APK, emulator/device test, screenshot or accessibility run is performed here.

## Authority

- Page acceptance: `design/system_v3/08_Page_Contracts/recognition/result/Acceptance_Contract_V3_1.json`
- Responsive/accessibility matrix: `design/system_v3/08_Page_Contracts/recognition/result/Result_Responsive_Accessibility_Matrix_V3_1.md`
- Layout and Frozen asset identities: `design/system_v3/08_Page_Contracts/recognition/result/Layout_Contract_V3_1.json`
- Shared geometry: `design/system_v3/07_Responsive_System/Geometry_Tokens_V3_FINAL.json`
- State binding: `design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_State_Binding_V1.json`

A runtime PASS is possible only after all rows below have test evidence for the same Android build SHA. Contract coverage is not runtime evidence.

## Layout Matrix

| Width | Required setup | Pass condition | Contract status | Runtime |
|---|---|---|---|---|
| 320dp | Render each applicable result/recovery state at 320dp usable width. | Candidate content is single column; text wraps; CTA pair fits available width and remains reachable; metadata does not clip; sheet scrolls above IME; Hero preserves the state-specific frame and media rule. | READY_TO_TEST | NOT_RUN |
| 360dp | Canonical Frozen reference width. | Preserve the registered composition and approximately 322dp content width, including each state-specific Hero size and CTA geometry. | READY_TO_TEST | NOT_RUN |
| 411dp | Wider phone layout. | Keep the content column capped at the 360dp Frozen authority width; do not scale type or crop media to fill extra width. | READY_TO_TEST | NOT_RUN |

At every width, the host consumes system/cutout insets once; content starts below the resolved top inset; primary actions remain above the navigation/gesture inset. IME changes the usable viewport and keeps the focused field and actions reachable. Do not compensate for inset errors by changing Frozen dimensions.

## Typography Matrix

| fontScale | Pass condition | Contract status | Runtime |
|---|---|---|---|
| 1.0 | Use registered Typography tokens and Frozen page hierarchy; no local font/size override. | READY_TO_TEST | NOT_RUN |
| 1.3 | Let labels, species names and values reflow; grow cards and scroll content without clipping. | READY_TO_TEST | NOT_RUN |
| 2.0 | Honor OS fontScale; wrap long content, grow surfaces and scroll; keep active actions reachable. | READY_TO_TEST | NOT_RUN |

## Accessibility Matrix

| Area | Acceptance rule | Contract status | Runtime |
|---|---|---|---|
| TalkBack | Read page heading once; preserve logical order; announce candidate species plus suggestion/selected state; expose distinct Save Catch and Continue Memory names and current enabled/loading state; expose metadata label with value. | READY_TO_TEST | NOT_RUN |
| Touch target | Every actionable target meets Shared Foundation `touch.minimum` (44×44dp). | READY_TO_TEST | NOT_RUN |
| Content Description | Every icon-only action has a localized accessible name; meaningful photo media has a page-approved description; decorative visuals are excluded from traversal; descriptions do not duplicate adjacent text. | READY_TO_TEST | NOT_RUN |
| Modal focus | Bottom Sheet exposes a modal boundary; focus enters on open, remains usable, IME/back semantics follow the Page Contract, and focus returns to the invoking control on dismissal. | READY_TO_TEST | NOT_RUN |

## State and Recovery Matrix

| Scenario | Expected binding and behavior | Contract status | Runtime |
|---|---|---|---|
| Loading | `INITIAL` keeps Processing active until a real payload exists. On persistence, `SAVE_LOADING` maps to `SAVE_IN_PROGRESS`, prevents repeat submit, and preserves layout/draft. No invented Result loading state. | READY_TO_TEST | NOT_RUN |
| Error | `SAVE_ERROR` remains on Result, preserves all draft values and destination, offers safe retry and creates at most one record. `TECHNICAL_FAILURE` stays separate from quality/recovery copy. | READY_TO_TEST | NOT_RUN |
| Empty | Empty optional metadata/story uses the exact Frozen placeholders/omission behavior; no inferred values or fake result state. | READY_TO_TEST | NOT_RUN |
| Long species name | Use registered type tokens, wrap/grow and scroll at large font scale; retain the full accessible species name and keep actions reachable. | READY_TO_TEST | NOT_RUN |
| Missing metadata | Location/length/weight/time follow the Data Contract fallback per field; no current-time or GPS substitution; absence alone does not block saving where optional. | READY_TO_TEST | NOT_RUN |
| Missing original photo | Do not substitute/generated-fill media or persist; route through Capture recovery. | READY_TO_TEST | NOT_RUN |
| Medium / Low species | Medium card never silently confirms; Low persistence requires explicit LOW_MANUAL selection; unconfirmed return preserves draft and clears the pending destination. | READY_TO_TEST | NOT_RUN |
| Save Catch success | Persist once and route Home; never open FishRecordDetail from Save Catch. | READY_TO_TEST | NOT_RUN |
| Continue Memory success | Persist first, then route with the persisted record ID to FishRecordDetail(MEMORY). | READY_TO_TEST | NOT_RUN |
| No Fish / Image Quality | Use the registered recovery copy and retake/gallery action; never create a FishRecord. | READY_TO_TEST | NOT_RUN |

## Runtime Evidence Required for PASS

For each row, record the Android build SHA, viewport width and fontScale, result state and scenario, assertion outcome, and corresponding screenshot or accessibility/test evidence. Keep evidence separate from the Frozen PNG masters. The test runner must not modify design assets or promote runtime output into a new visual authority.
