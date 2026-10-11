# Recognition Processing · Page Contract V3.1

PAGE_ID: recognition.processing.v3_1  
PAGE_NAME: Recognition Processing  
VERSION: 3.1  
STATUS: REVIEW  
AUTHORITY_SOURCE: design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_2.json

## Page Purpose
- **Goal:** communicate Recognition progress through three meaningful visible product states while preserving the actual user photo.
- **User task:** wait for a real recognition result, or leave/recover from an actual issue.
- **Entry condition:** Capture hands off a readable normalized image.
- **Exit condition:** RESOLVE to Recognition Result, a distinct No Fish/Image Quality/Technical Failure issue route, or user Back.

## User Flow
Capture normalized image → CAPTURED / DETECTING under one visible 图片识别中 state → OUTLINE / 已定位到鱼体 → CLASSIFYING / 鱼种识别中 → RESOLVE → Recognition Result.  
No Fish, Image Quality and Technical Failure are issue exits; Back is a user exit.

## Layout Contract
- Machine-readable dependencies: Layout_Contract_V3_1.json.
- Four current Processing Frozen PNGs remain byte-identical. V1.2 assigns PNG 01 + 02 as Early/Late frames of the single IMAGE_RECOGNIZING state; 03 is FISH_LOCATED; 04 is SPECIES_RECOGNIZING.
- The photo, Edge Field, Fish Focus, status UI and Back follow the named page visual/motion/accessibility authorities.
- Colors use existing identity-preservation tokens only within source scope. No color or geometry token is added.
- No page-private token is introduced.

## State Machine Contract
See State_Machine_V3_1.json. Internal phases CAPTURED, DETECTING, OUTLINE, CLASSIFYING and RESULT remain explicit. The visible product model is exactly three Processing states; RESOLVE is a transition.

## Data Contract
See Data_Contract_V3_1.json. Empty input, active work, partial focus/contour, issue exits and network/technical failure are mapped. Classifier/detector thresholds and output semantics are not changed.

## Engineering Contract
See Engineering_Contract_V3_1.md for media transform, coordinate/inset ownership, state pacing, continuous motion, degraded modes, Back, and error routing.

## Acceptance Contract
See Acceptance_Contract_V3_1.json. Existing visual/motion/interaction checks are measurable. Copy/state reconciliation and a registered mapping for the Frozen 14sp subtitle remain blocked; the source Runtime/Evidence closure also remains independent.

## Conflicts and Freeze Gate
- **PROC-001:** V1.3 three-state semantic timeline conflicts with the still-listed V1.1 per-phase copy table; V1.2 remaps keyframes but does not amend the old copy table.
- **PROC-002:** Frozen 14sp status subtitle has no exact effective Typography token or registered Recognition Processing page override. Do not replace it with 12sp/16sp or create a private token.
- **PROC-003:** Source design is FROZEN while Runtime/Evidence remain ACTIVE_CLOSURE; this is reported separately and is not a V3.1 page design decision.
- Result: REVIEW until PROC-001/PROC-002 are resolved by the responsible design owner. Runtime/Evidence is not claimed as PASS.
