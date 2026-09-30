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

Initial state:
- model candidates are suggestions;
- no candidate is silently treated as user-confirmed merely because it is Top-1.

Candidate selection:
- visually confirms one candidate
- updates the species identity used for save
- establishes user-confirmed state

Other-species action:
- opens the shared species selector
- returns to the same Result screen with the selected species

After confirmation:
- reuse High's metadata and dual CTA contract.

## 4. Low-confidence flow

Initial state:
- no species is claimed as confirmed;
- show `无法确认是什么鱼`;
- show `手动选择鱼种`;
- show `重新拍摄`;
- hide normal catch metadata/save controls.

Manual selection:
- resolves the species;
- does not rerun Recognition;
- moves the flow into the normal resolved-species recording contract.

Retake:
- abandons this recognition attempt and returns to the capture flow.

V1 does not authorize:
- saving a FishRecord with an invented unknown species;
- a new `鱼种待确认` persistent state;
- a pending-species data contract.

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
