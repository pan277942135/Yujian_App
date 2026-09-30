# Recognition Result — Runtime Alignment Review V1

Status: **DESIGN CLOSED / RUNTIME NEEDS CLOSURE**

Reviewed runtime:
- `RecognitionResultScreen.kt`
- `RecognitionIssueScreen.kt`

Reviewed against:
- Frozen 05–09
- Core Visual System V1
- Recognition Result Design Package V1
- existing P0 shared component system

## Summary

The current runtime implements all five broad routes, but it is not yet fully conformant with the closed Result design package.

## P0 — structural / product mismatches

### 1. High result CTA model

Current runtime:
- one primary `保存本次鱼获`
- separate `查看鱼鉴` text action

Frozen Result contract:
- light `继续记录记忆`
- primary `保存本次鱼获`

Action:
- remove `查看鱼鉴` from the Result primary action model
- implement the dual CTA save/memory contract

### 2. Low-confidence persistence model

Current runtime:
- `selectedKey` starts blank
- save actions are absent until user manually selects a species

Closed design:
- low result is `鱼种待确认`
- preserving the catch remains primary
- pending-species save path must exist where product persistence supports it

Action:
- audit catch data contract for a formal pending species representation
- do not force a design change to “manual selection required”
- if persistence lacks support, record and implement the minimum product-safe data-contract fix before claiming parity

### 3. Shared component adoption

Current runtime still uses page-private Material:
- Button
- OutlinedButton
- TextButton
- custom back/title row

Closed design:
- shared P0 Top Navigation
- shared primary/light button
- shared text/icon action

Action:
- migrate Result/Issue page action roles to the shared component system

## P1 — visual/interaction mismatches

### 4. High metadata presentation

Current runtime:
- generic `FrozenCatchDetails` settings-like panel
- labels show `请输入`
- one `编辑记录` dialog entry

Closed design:
- lightweight length / weight / location affordances
- catch note role is `留下本次鱼获感言`
- approved voice affordance is preserved

Action:
- refactor information hierarchy to match Frozen high result instead of a generic settings card

### 5. Medium confirmation semantics

Current runtime:
- `selectedKey` is initialized to Top-1
- model suggestion can behave like an already resolved selection before explicit user confirmation

Closed design:
- Medium must distinguish model suggestion from user-confirmed species

Action:
- separate suggested vs confirmed state
- candidate tap / other-species selection establishes confirmation

### 6. Medium candidate visuals

Current runtime:
- candidate card uses a generic circle containing the character `鱼`

Frozen design:
- candidate cards are state-specific visual elements

Action:
- match Frozen candidate structure; do not use the generic “鱼” placeholder when approved species representation is available

### 7. Low action hierarchy

Current runtime:
- `手动选择鱼种` and `重新拍摄` have equal outlined weight

Closed design:
- retake is downgraded
- preserve/save direction has higher product priority

Action:
- recompose low action hierarchy per Frozen

## P2 — cleanup / consistency

### 8. Result background / layout implementation

Current runtime:
- blurred full-screen source image at low opacity is used as background

Action:
- compare directly with Frozen/system Result background treatment
- keep only if parity confirms it; do not treat runtime blur as authority

### 9. Issue page shared controls

Current `RecognitionIssueScreen` uses page-private primary/outlined buttons.

Action:
- migrate to shared P0 components while preserving the five-state visual geometry

### 10. Runtime tests reflect existing UI more than final package

Current frozen-flow test asserts:
- High save button
- Medium helper/candidate
- Low manual selection + retake

It does not prove:
- dual High CTA
- memory route
- low pending-save path
- voice affordance behavior
- shared-component parity

Action:
- extend Result-specific acceptance evidence after implementation closure

## Confirmed correct / keep

- real captured photo remains the primary Result media
- High / Medium / Low routing remains driven by recognition semantics
- No Fish and Image Quality are separate runtime states
- error copy is user-safe
- location permission is user-triggered
- technical failure stays separate from the 3+2 frozen set
- debug/model data is not exposed in consumer UI

## Closure target

Runtime may be reported conformant only after:
1. P0 items are closed,
2. P1 Frozen hierarchy is matched,
3. Result-specific behavior evidence passes,
4. the existing full Recognition gate remains green.
