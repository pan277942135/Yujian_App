# Recognition Result · Page Contract V3.1

PAGE_ID: recognition.result.v3_1  
PAGE_NAME: Recognition Result  
VERSION: 3.1  
STATUS: FROZEN  
AUTHORITY_SOURCE: design/pages/recognition/result/authority/authority_map.json

## Page Purpose
- **Goal:** present the resolved Recognition outcome and let the user confirm/correct species, enter optional catch data, and choose a destination.
- **User task:** review the result and either Save Catch to Home or Continue Memory to FishRecordDetail after persistence.
- **Entry condition:** the real Recognition pipeline resolves to a Result or recovery outcome.
- **Exit condition:** successful Save Catch → Home; Continue Memory → FishRecordDetail memory after persistence; No Fish/Image Quality → Capture recovery; Back → prior context. Save Catch never opens Detail.

## User Flow
Recognition Processing → High/Medium/Low/No Fish/Image Quality state → explicit species confirmation when needed → optional metadata/story → Save Catch or Continue Memory → persist → selected destination. Recovery never creates a record. Save failure remains on Result with the draft intact.

## Layout Contract
- Existing state-specific Frozen PNGs and authority_map.json remain the visual source; no Frozen PNG is modified.
- Candidate Card, CTA, Metadata Row and Bottom Sheet use registered Shared Components and existing Result page composition.
- Layout_Contract_V3_1.json references shared tokens, components, assets, backgrounds and Frozen page authorities.
- Responsive/accessibility behavior is frozen in Result_Responsive_Accessibility_Matrix_V3_1.md. No page-private token/component is introduced.

## State Machine Contract
State_Machine_V3_1.json preserves HIGH_CONFIDENCE, MEDIUM_CONFIDENCE, LOW_CONFIDENCE, NO_FISH and IMAGE_QUALITY_WARNING; TECHNICAL_FAILURE remains separate. Quality values and reason/copy routing are defined by ImageQualityCopy_V1.json; source fields resolve through Recognition_Result_Data_Lineage_V1.json.

## Data Contract
Data_Contract_V3_1.json maps Result fields through UI → Data Contract → Source Owner. Species, confidence, quality, timestamp, location, length, weight and photo resolve to the approved source owners. This is a page-level contract, not an API/DB schema change.

## Engineering Contract
The Result content column scrolls; host owns insets; original media uses state-specific Fit; fixed/adaptive behavior follows Frozen geometry and the responsive matrix. See Engineering_Contract_V3_1.md.

## Acceptance Contract
Acceptance_Contract_V3_1.json and the responsive/accessibility matrix provide runnable visual, interaction, accessibility and error criteria for Candidate Card, CTA, Metadata Row and Bottom Sheet. Contract status is READY; runtime execution is NOT_RUN.

## Conflicts and Freeze Gate
RES-001 is closed by the GOOD/WARNING/INVALID gate, six-code reason taxonomy and ImageQualityCopy_V1.json. Existing generic Frozen copy is retained; unmeasured diagnoses are never asserted. RES-002 is closed by explicit UI field → Data Contract → Source Owner mapping; implementation/storage schemas remain outside scope. RES-003 is closed by the responsive/accessibility matrix. Frozen page PNGs remain unchanged. Page Contract status is FROZEN.
