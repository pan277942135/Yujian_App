# Recognition Result Acceptance Criteria V1

A design or implementation is conformant only when all applicable criteria pass.

## A. Authority

- 05–09 Frozen PNGs remain unchanged
- SHA-256 matches `design/pages/recognition/design/reference_manifest.json`
- state-specific Frozen PNG wins over generic/system reference for state differences
- real catch image is runtime media, not a screenshot crop
- shared Design System components are not forked

## B. State routing

- High / Medium / Low routing continues to use existing recognition semantics
- No Fish / Image Quality continue to use real pipeline quality/detection outcomes
- visual implementation does not modify thresholds
- Technical Failure remains outside the frozen 3+2 set

## C. High

PASS only when:
- no “已识别” label is added
- species + 修改鱼种 share the identity row
- length / weight / location remain lightweight
- catch-note role matches `留下本次鱼获感言`
- voice affordance is real if visible
- `继续记录记忆` and `保存本次鱼获` preserve their frozen hierarchy
- no extra top-level `查看鱼鉴` action displaces the frozen CTA model

## D. Medium

PASS only when:
- hero photo is preserved
- candidate cards are additional confirmation UI
- user-confirmed selection is distinguishable from model suggestion
- “都不是 / 选择其他鱼种” remains available
- selected species can proceed to save/memory flow
- candidate UI does not become a second hero carousel

## E. Low

PASS only when:
- main message is `无法确认是什么鱼`
- record state communicates `鱼种待确认`
- manual species recovery is available
- retake is lower priority than preserving the catch
- low state is not forced to expose the normal metadata block
- design retains a pending-save path; if runtime cannot persist it, the gap is reported rather than hidden

## F. No Fish

PASS only when:
- title is `没有找到可识别的鱼`
- supporting guidance remains user-safe
- `重新拍摄` is primary
- `从相册选择` is secondary
- no save/metadata controls appear

## G. Image Quality

PASS only when:
- title is `照片不够清晰，无法识别`
- quality guidance matches Frozen copy
- `重新拍摄` is primary
- `从相册选择` is secondary
- no save/metadata controls appear

## H. Visual

- photo remains dominant
- no Processing AI effects survive into Result
- no detector/debug overlays
- no gamification
- color/radius/typography follow Core Visual System
- compact-height state remains usable
- system bars/IME do not hide required actions

## I. Save contract

- duplicate submission is blocked
- user inputs survive retryable save errors
- save creates FishRecord before navigation
- continue-memory creates FishRecord before memory operations
- correction feedback is preserved when species changes

## J. Evidence target

Required implementation evidence should include at minimum:
- High runtime screenshot
- Medium runtime screenshot
- Low runtime screenshot
- No Fish runtime screenshot
- Image Quality runtime screenshot
- compact-height result screenshot
- saving/loading state
- species correction interaction
- medium candidate confirmation
- low pending/manual recovery
- dual CTA navigation proof

Existing Recognition visual parity may continue to validate 05–09, but Result-specific behavioral evidence must not be omitted merely because the full Recognition gate is green.

## K. Prohibited shortcuts

- replacing Frozen PNGs with runtime captures
- lowering parity thresholds to obtain PASS
- hiding a required CTA because runtime has no implementation
- changing confidence semantics to make screens easier to test
- requiring manual species selection in Low solely because pending persistence is missing
- retaining no-op controls
