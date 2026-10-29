# Recognition Processing · Page Contract V3.1

PAGE_ID: recognition.processing.v3_1  
PAGE_NAME: Recognition Processing  
VERSION: 3.1  
STATUS: FROZEN  
AUTHORITY_SOURCE: design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json

## Page Purpose
- **Goal:** communicate real Recognition progress while preserving the original user photo.
- **User task:** wait for the real result, or follow a distinct issue/recovery route.
- **Entry condition:** Capture hands off one readable normalized image.
- **Exit condition:** the real result reaches RESULT_READY and is handed to Recognition Result; real No Fish, Invalid Quality or Technical Failure exits remain distinct; Back cancels.

## User Flow
CAPTURED (minimum 220ms) → DETECTING (minimum 320ms) → OUTLINE (minimum 350ms) → CLASSIFYING (minimum 250ms) → RESULT_READY → Recognition Result. Each advance requires both the minimum beat and the corresponding real pipeline event. Legacy Stage 1 / Stage 2 / Stage 3 labels are deprecated.

## Layout Contract
- State_Machine_V3_1.json is the sole effective Recognition Processing timeline/state authority.
- Existing Frozen Processing PNGs remain read-only visual references: CAPTURED/DETECTING use Early/Late references; OUTLINE and CLASSIFYING use their existing references. No artwork or new screen is generated.
- Photo, Edge Field, focus, status UI and Back remain within their existing page authorities.
- processing.subtitle maps to type.body_small (14sp, secondary); no font or page-private typography scale is introduced.
- Layout_Contract_V3_1.json resolves every component, token, asset, background and page authority.

## State Machine Contract
State_Machine_V3_1.json is named Recognition Processing State Machine V3.1. Each state row contains only State ID, Duration, Visual Behavior and Transition Rule. The minimum durations are 220/320/350/250ms; RESULT_READY is an immediate handoff, not a timed visual state.

## Data Contract
Data_Contract_V3_1.json maps the normalized image, real detector result, real focus region and classifier output. No candidate, focus, result, progress or error is fabricated.

## Engineering Contract
Preserve the original photo and existing Frozen layer stack; use one continuous Edge Field clock; apply the minimum-beat plus real-event gates; honor the registered 14sp subtitle token; keep Back/error behavior distinct. Runtime implementation and evidence are outside this freeze.

## Acceptance Contract
Acceptance_Contract_V3_1.json checks the state sequence, visual references, subtitle token and error/focus rules. Contract is READY to test; runtime/evidence execution is NOT_RUN.

## Conflicts and Freeze Gate
PROC-001 is closed: the V3.1 five-state sequence is the unique effective model and supersedes legacy three-step labels and competing timeline semantics while retaining Frozen visual references. PROC-002 is closed: the Frozen 14sp secondary subtitle maps to type.body_small through the Recognition Processing override. PROC-003 Runtime/Evidence remains a separate non-blocking follow-up and is not claimed as executed. Page Contract status is FROZEN.
