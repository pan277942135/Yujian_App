# Recognition Result Behavior Spec V1

## 1. Entry

The Result screen is entered only after the real Recognition pipeline has resolved.

Result presentation must not change:
- confidence thresholds
- candidate ordering semantics
- detector/crop/classifier behavior

## 2. High-confidence flow

Default species:
- real Top-1 species

User may:
- modify species
- add/edit length
- add/edit weight
- add/edit location
- add/edit catch note
- use voice-note input when that capability is implemented

Save:
1. create FishRecord
2. persist correction feedback when species changed
3. route to Normal Home

Continue memory:
1. create FishRecord
2. persist correction feedback when applicable
3. route to FishRecordDetail(recordId, initialSection=MEMORY)

Do not enter memory operations before FishRecord creation succeeds.

## 3. Medium-confidence flow

The page preserves the catch hero and shows candidate choices.

Candidate selection:
- visually confirms one candidate
- updates the species identity used for save
- is user-confirmed state, not merely model suggestion

Other-species action:
- opens the shared species selector
- returns to the same Result screen with the selected species

The candidate region must not replace the catch hero.

## 4. Low-confidence flow

Initial state:
- no species is claimed as confirmed
- show `鱼种待确认`
- metadata is not required to explain the state
- manual selection is available
- retake is available but lower priority than preserving the catch

Pending save:
- design requires a path to preserve the catch even when species is still pending
- persistence may use the product's formal pending/unknown-species representation
- implementation must not invent a public species label that looks like a real species

If no persistence representation exists, report a runtime/data-contract gap.

Manual selection:
- resolves the species and moves the record into normal save behavior
- does not require re-running recognition

## 5. Metadata

Length and weight:
- optional
- numeric validation belongs to runtime
- absence must not block save unless a later product rule explicitly changes this

Location:
- optional
- location permission is requested only after explicit user action
- permission denial must not block save

Catch note:
- optional
- short-form memory text
- voice input, if shown, must perform a real action and must not be a no-op control

## 6. Save state

While saving:
- prevent duplicate submission
- preserve layout
- disable conflicting species/metadata edits
- show a clear saving state on the primary action

On save error:
- remain on Result
- keep user-entered metadata
- show a user-safe retryable error
- do not create duplicate records

## 7. Back

Back returns to the prior capture/selection context according to the existing navigation contract.

Back must not silently save a record.

## 8. Recovery states

No Fish / Image Quality:
- never create FishRecord
- primary retake uses the existing camera flow
- gallery action uses the existing image-selection flow
- back remains available but visually subordinate

## 9. Shared components

Use the shared Design System for:
- Top Navigation
- primary/light actions
- text actions
- icon actions

Do not maintain a private Result button/navigation system.
