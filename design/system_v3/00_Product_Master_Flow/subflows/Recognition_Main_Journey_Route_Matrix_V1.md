# Recognition Main Journey · Route & Decision Matrix V1.0

| ID | Source | Trigger / guard | Destination | Result/data effect |
|---|---|---|---|---|
| R01 | Empty Home | User taps camera CTA | Capture / Identify | New Recognition Session; 0 records. |
| R02 | Capture | Camera path, no permission | Platform Camera permission | Request only after user action. |
| R03 | Permission | Granted + CameraX Provider and Preview ready | Capture Ready | Camera enabled. |
| R04 | Permission | Denied or permanently denied | Capture recovery / system settings | Camera stopped; gallery and Back remain available. |
| R05 | Home / Capture | User chooses Gallery | System Photo Picker | Independent of Camera permission and first guide. |
| R06 | Capture Ready | First camera entry, guide incomplete | First Capture Guide overlay | Mark complete only after Skip / Start dismissal. |
| R07 | First Capture Guide | Skip or Start Capture | Same Capture Preview | Close overlay; no automatic shutter. |
| R08 | Capture / Picker | Shutter success or valid new selection | Processing | One normalized photo, new imageId + attemptId. |
| R09 | Photo Picker | User cancels | Original Capture context | No inference, no old-image flash, 0 records. |
| R10 | Processing | Real pipeline events plus V3.1 minimum stage beats | Processing next stage / real outcome | No timer-only advance. |
| R11 | Processing | Back before terminal outcome | Capture | Cancel attempt; invalidate late callback; 0 records. |
| R12 | Pipeline | Real eligible output, top band High | Result High | Top-1 editable; saving still requires user intent. |
| R13 | Pipeline | Medium | Result Medium | Candidate remains suggestion until explicit selection. |
| R14 | Pipeline | Low | Result Low | Metadata/story may be draft; manual species required to save. |
| R15 | Pipeline | NO_FISH | No Fish recovery | RR04 copy; Retake/Gallery; 0 records. |
| R16 | Quality Gate | INVALID and reason != NO_FISH | Image Quality recovery | RR05 Frozen copy; Retake/Gallery; 0 records. |
| R17 | Pipeline | WARNING only | Existing eligible classification route | Never route to Image Quality only because of WARNING. |
| R18 | Pipeline | Real model/crop/runtime/dependency exception | Technical Failure recovery | Separate from 3+2; no photo-quality claim; 0 records. |
| R19 | Medium / Low | Candidate/manual species committed | Result with species confirmed | Preserve same photo and editable draft. |
| R20 | Species Selector | Back/dismiss without commit | Same Result state | No implicit confirmation and no data change. |
| R21 | Result | Return with no material edits | Capture | No save; do not reuse old image for new inference. |
| R22 | Result | Retake, no dirty edits | Capture with active result cleared | New image and attempt required. |
| R23 | Result | Back/Retake with material edits or unresolved save | Shared discard confirmation | Continue editing stays on Result; Discard goes to Capture. |
| R24 | Editor / Selector / IME | Back while editor state is active | Close editor, selector, or IME first | Result route remains; no dirty exit prompt until overlay is dismissed. |
| R25 | Result | Save requested while Saving | No navigation / no second request | Duplicate taps and unsafe Back blocked. |
| R26 | Result | Network/upload/validation failure known | Same Result | Keep image, selected species, fields, story, destination, clientRecordId. |
| R27 | Save | Server outcome unknown / response lost | Idempotency lookup by clientRecordId | Verify existing record before retry. |
| R28 | Save | Guest local save | Local Catch repository | Stable-key idempotent write; durable commit before success. |
| R29 | Result | Save Catch persistence verified | Home / NH02 when first record | Exactly +1 record; remove recognition stack. |
| R30 | Result | Continue Memory persistence verified | FishRecordDetail(memory), parent Home | Exactly +1 record; remove recognition stack. |
| R31 | FishRecordDetail | System Back | Home / declared parent | Never show old Result/Processing/Capture. |
| R32 | Any unsaved / recovery state | Exit, cancel, failure, or process termination | Capture/Home/recoverable state | No automatic record creation; count unchanged. |

**Status:** Frozen business routing. Runtime implementation and test disposition are tracked separately in the Stage 0/implementation ledger and J01–J20 acceptance matrix.
