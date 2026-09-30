# 03 — Low / 低置信结果

Status: **UI REVIEWED V1 — FROZEN REFERENCE RETAINED**

## Frozen Hi-Fi

![Low confidence Frozen](../../../design/07_Result_Low_Frozen.png)

Canonical:
`design/pages/recognition/design/07_Result_Low_Frozen.png`

SHA-256:
`b767965028786374bcadf9dd1b219b7b61f698edade323d8557293ebcec1d6e1`

## Page role

Low is the simplest recognition-result recovery state.

The product does not pretend to know the species when confidence is too low.

## Frozen information hierarchy

1. Result Top Navigation
2. Real catch hero photo
3. `无法确认是什么鱼`
4. Two recovery actions:
   - `手动选择鱼种`
   - `重新拍摄`

No normal catch-metadata block is shown before species recovery.

## UI review V1

### A. Main message — FREEZE

Canonical message:

`无法确认是什么鱼`

Keep it direct and short.

Do not add:
- long AI explanation
- confidence percentage
- model failure details
- technical reasoning

### B. Manual species action — FREEZE

`手动选择鱼种`

Role:
- user can recover the flow by selecting the correct species;
- selection does not re-run Recognition;
- after a species is selected, the Result can enter the normal resolved-species recording flow.

### C. Retake — FREEZE

`重新拍摄`

Role:
- abandons this recognition attempt;
- returns to the existing camera capture flow.

### D. Initial Low state — KEEP SIMPLE

Before the user selects a species:
- no length;
- no weight;
- no location;
- no catch-note form;
- no save CTA;
- no `鱼种待确认` pseudo-record state;
- no automatic unknown-species FishRecord creation.

This is intentionally simpler than High and Medium.

### E. After manual species selection

Once the user manually resolves a species:
- reuse the normal Result species identity/recording contract;
- do not create a separate Low-only editor;
- correction/feedback semantics remain preserved.

### F. Visual hierarchy — FREEZE

- real photo stays dominant;
- message panel stays calm;
- two actions are clear;
- no alarm-red treatment;
- no “recognition failed” technical tone;
- no extra help paragraphs unless separately versioned.

## Explicitly rejected

- automatic save with pending/unknown species
- `鱼种待确认` as a new persistent product state
- metadata shown before species selection
- long error explanation
- confidence score
- three or more competing recovery actions
- re-running classifier after manual selection

## Review status

UI structure: **CLOSED V1**

Copy: **CLOSED V1**

Recovery behavior: **CLOSED V1**

Runtime parity: **NOT PART OF THIS DESIGN REVIEW**

## Hero Media Contract — FROZEN

Low remains a recognition-result state with a detected fish subject, even when species identity is uncertain.

Use **Subject First**:
- valid fish bbox → safe Smart Crop;
- unsafe/missing bbox → Subject Safe Fit;
- preserve fish integrity before fill efficiency;
- no pending-species semantics are implied by the media contract.

Authority:
- `../media/Recognition_Result_Hero_Media_Contract_V1.md`
