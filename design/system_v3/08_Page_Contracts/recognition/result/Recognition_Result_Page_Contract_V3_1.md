# Recognition Result · Page Contract V3.1

PAGE_ID: recognition.result.v3_1  
PAGE_NAME: Recognition Result  
VERSION: 3.1  
STATUS: REVIEW  
AUTHORITY_SOURCE: design/pages/recognition/result/authority/authority_map.json

## Page Purpose
- **Goal:** present the resolved Recognition outcome, let the user confirm/correct species and record optional catch information, then execute the selected destination.
- **User task:** review the result and either Save Catch to Home or Continue Memory to FishRecordDetail after persistence.
- **Entry condition:** real Recognition pipeline resolves to a Result/recovery outcome.
- **Exit condition:** successful Save Catch → Home; successful Continue Memory → FishRecordDetail memory; No Fish/Image Quality → Capture recovery; Back → prior context. No Save Catch route enters Detail.

## User Flow
Recognition Processing → one of five Result/recovery states → explicit species confirmation when required → optional metadata/story → Save Catch or Continue Memory → persistence → selected destination.  
No Fish/Image Quality → Retake/Gallery → Capture. Save failure stays on Result and preserves the draft.

## Layout Contract
- Machine-readable dependencies: Layout_Contract_V3_1.json.
- Frozen state visuals and state-specific Hero dimensions remain authoritative. The normal Result background is BG_CONTENT.
- Shared components, Foundation tokens, Frozen assets and page-owned geometry are enumerated in the Layout Contract.
- Result private compositions described by the existing authority map are not added as public components.
- No page-private shared token or component is introduced.

## State Machine Contract
See State_Machine_V3_1.json. High, Medium, Low, No Fish and Image Quality are separate; Technical Failure is an independent issue route. Medium/Low confirmation and Save Catch/Continue Memory persistence are explicit.

## Data Contract
See Data_Contract_V3_1.json. Optional metadata can remain empty. Medium/Low species confirmation, saving, partial data, network error and recovery paths are explicit. C-011 means repository/API/database/local-store field lineage is not certified.

## Engineering Contract
See Engineering_Contract_V3_1.md for vertical scroll, state-specific Hero, host-owned insets, IME/sheets, component use, interaction, persistence and media rules.

## Acceptance Contract
See Acceptance_Contract_V3_1.json. Frozen visual checks and Save Catch navigation are executable. Image Quality meaning (C-009), full field lineage (C-011) and the page accessibility matrix (C-008) remain blocked.

## Conflicts and Freeze Gate
- **RES-001 / C-009:** Image Quality can be reached for more than a measured optical-blur condition. Current Frozen copy is preserved; product/content must approve broad vs reliable reason-specific guidance.
- **RES-002 / C-011:** field names are product-level only; the implementation/storage mapping remains unaudited.
- **RES-003 / C-008:** Result-specific compact-height/font-scale acceptance remains pending approval. Preserve Frozen dimensions.
- **DECISION_SAVE_CATCH_NAVIGATION_V1 is RESOLVED:** ordinary Save Catch persists then routes Home; older AUDIT_DRAFT subflow text cannot override it. Continue Memory is separate.
- Result: REVIEW until RES-001/002/003 close. No page artwork, Foundation token or Android/Backend code was changed.
