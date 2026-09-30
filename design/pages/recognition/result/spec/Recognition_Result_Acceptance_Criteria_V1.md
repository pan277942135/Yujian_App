# Recognition Result Acceptance Criteria V1

A design or implementation is conformant only when all applicable criteria pass.

Numeric visual/ROI authority:

`../engineering/Recognition_Result_Visual_Acceptance_Map_V1.md`
`../engineering/visual_acceptance_map.json`

Layout geometry authority:

`../engineering/Recognition_Result_Layout_Geometry_V1.md`

Component/input/candidate authorities:

`../engineering/Recognition_Result_Component_Map_V1.md`
`../engineering/Recognition_Result_Metadata_Input_Contract_V1.md`
`../engineering/Recognition_Result_Candidate_Card_V1.md`

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
- prompt is one combined line
- candidate cards are additional horizontal confirmation UI
- model suggestion is distinguishable from user-confirmed choice
- “都不是 / 选择其他鱼种” remains available
- resolved species reuses the normal save/memory flow
- candidate UI does not become a second hero carousel

## E. Low

PASS only when:
- main message is `无法确认是什么鱼`
- `手动选择鱼种` is available
- `重新拍摄` is available
- normal metadata/save UI is hidden before species resolution
- manual selection can recover into the normal record flow
- no pending/unknown species persistence is invented in V1

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

## I. Hero Media Contract

For High / Medium / Low with a trustworthy bbox:

- display source is the original oriented user photo;
- fish bbox rectangular coverage = 100%;
- FishSafeRect rectangular coverage = 100%;
- FishSafeRect visible coverage after rounded-mask clipping >=98%;
- FishSafeRect expansion = 14% each horizontal side / 18% each vertical side;
- minimum visual safety inset = 12dp;
- a bbox side within 2% of the source edge is treated as SOURCE_CLIPPED and must not be cropped further;
- unsafe Fill falls back to Subject Safe Fit;
- no detector/classifier crop is displayed as Hero;
- no blurred duplicate-photo support fill;
- no post-entry crop jump.

For No Fish / Image Quality:

- full oriented source image visible = 100%;
- ContentScale = Fit/Contain;
- bbox-based crop = prohibited;
- no blurred duplicate-photo support fill;
- no AI enhancement/sharpening/outpaint.

For all 3+2 states:

- orientation is correct;
- no stretch;
- no generative modification;
- outer Hero geometry is stable across source ratios.

Frozen machine-readable test matrix:

`../media/hero_media_test_vectors.json`

## J. Save contract

For resolved-species states:
- duplicate submission is blocked
- user inputs survive retryable save errors
- save creates FishRecord before navigation
- continue-memory creates FishRecord before memory operations
- correction feedback is preserved when species changes

## K. Evidence target

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
- low manual recovery
- dual CTA navigation proof

Existing Recognition visual parity may continue to validate 05–09, but Result-specific behavioral evidence must not be omitted merely because the full Recognition gate is green.

## L. Prohibited shortcuts

- replacing Frozen PNGs with runtime captures
- lowering parity thresholds to obtain PASS
- hiding a required CTA because runtime has no implementation
- changing confidence semantics to make screens easier to test
- inventing pending-species persistence
- retaining no-op controls
- universal CenterCrop for arbitrary user photos
- blurred source-photo copy as Hero support fill
