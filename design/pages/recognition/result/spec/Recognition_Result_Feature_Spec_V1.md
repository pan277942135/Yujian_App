# Recognition Result Feature Spec V1.1

## Scope

Recognition Result begins after the real Recognition pipeline has resolved into one of the supported UI states.

It is responsible for:
- presenting the result or recovery reason
- allowing species confirmation/correction where applicable
- capturing lightweight catch metadata after species resolution
- creating the FishRecord
- routing to Home or Memory

It is not responsible for:
- Recognition Processing animation
- Fish Guide knowledge content
- FishRecordDetail content
- media gallery authoring
- model/debug information

## State family

### RESULT_HIGH

Use when the recognition result is sufficiently confident under the existing runtime confidence semantics.

Product intent:
- treat Top-1 as the default species
- keep correction easy and visible
- move quickly into catch recording

### RESULT_MEDIUM

Use when the result is plausible but ambiguous.

Product intent:
- preserve the original hero photo
- present candidate species for explicit confirmation
- keep “other species” available
- after confirmation, reuse the normal catch-recording flow
- do not make the page look like a failure screen

### RESULT_LOW

Use when species cannot be responsibly confirmed.

Product intent:
- say clearly that the species cannot be confirmed
- offer manual species selection
- offer retake
- keep vertical metadata and Story editable as local draft before species resolution
- if a Dual CTA is tapped unresolved, remember the destination and enter LOW_MANUAL
- commit only after species selection; never invent an unknown/pending-species record

### ERROR_NO_FISH

Use when the real pipeline cannot locate a usable fish subject.

Product intent:
- explain what is missing
- prioritize retake
- offer gallery recovery
- do not expose metadata or save actions

### ERROR_IMAGE_QUALITY

Use when image quality is insufficient for reliable recognition.

Product intent:
- explain the capture-quality problem
- prioritize retake
- offer gallery recovery
- do not expose metadata or save actions

## Shared information hierarchy

For resolved result states:

1. real catch photo
2. species state
3. lightweight catch metadata
4. catch note
5. record action

For unresolved Low:

1. real catch photo
2. unable-to-confirm message
3. manual selection / retake
4. vertical metadata / Story / Dual CTA

For recovery states:

1. failed source photo
2. calm recovery explanation
3. recovery actions

## Product invariants

- No “已识别” badge is required above the species title.
- The fish photo remains the strongest content object.
- Confidence numbers are not exposed as a consumer-facing score.
- There is no rarity/achievement language.
- No detector bbox, crop box, confidence debug, model version or classifier data appears in normal UI.
- Result UI must remain calm and documentary rather than diagnostic.
