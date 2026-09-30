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

This document records implementation gaps only. It does not redefine the 3+2 UI.

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

### 2. Medium confirmation state

Current runtime:
- `selectedKey` is initialized to Top-1

Closed design:
- Medium asks the user to confirm which candidate is correct
- a model suggestion must not silently count as user confirmation

Action:
- separate suggestion from explicit confirmation

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
- approved voice affordance is preserved when functional

Action:
- refactor information hierarchy to match Frozen High rather than a generic settings card

### 5. High bottom actions

Current runtime lacks:
- `继续记录记忆`

Action:
- implement the frozen dual CTA behavior after design-to-runtime work begins

### 6. Medium candidate visuals

Current runtime:
- candidate card uses a generic circle containing the character `鱼`

Closed design:
- candidate cards are compact species confirmation elements

Action:
- match Frozen candidate structure; do not retain a generic placeholder when approved species representation is available

### 7. Low flow

Current runtime:
- initial Low exposes exactly `手动选择鱼种` + `重新拍摄`
- metadata is hidden until selection

This is directionally aligned with the finalized Low design.

Keep:
- no pending-species save path
- no `鱼种待确认` persistence
- no normal metadata before manual recovery

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

### 10. Result-specific acceptance

Current frozen-flow test proves basic five-state routing but does not prove:
- High dual CTA
- memory route
- Medium explicit confirmation
- shared-component parity

Action:
- extend Result-specific evidence after implementation closure

## Confirmed correct / keep

- real captured photo remains the primary Result media
- High / Medium / Low routing remains driven by recognition semantics
- Low starts with manual selection + retake and hides metadata
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
