# Recognition Result Visual Acceptance Contract V1

**Page ID:** recognition_result_v1  
**Upstream Page Contract:** recognition.result.v3_1 — FROZEN  
**Status:** READY_FOR_VISUAL_IMPLEMENTATION  
**Runtime acceptance:** NOT_RUN

## Authority

This is a subordinate acceptance layer. The frozen Page Contract remains product authority; this document binds its five Result visuals to measurable future screenshot checks. It does not replace or reinterpret the Page Contract, Android Implementation Contract, or Shared Foundation.

The unique authority chain is:
- Page: design/system_v3/08_Page_Contracts/recognition/result/Recognition_Result_Page_Contract_V3_1.md
- State and layout: design/system_v3/08_Page_Contracts/recognition/result/State_Machine_V3_1.json; design/pages/recognition/result/engineering/layout_geometry_contract.json
- Frozen page authority: design/pages/recognition/result/authority/authority_map.json; design/pages/recognition/result/engineering/visual_acceptance_map.json
- Shared components, typography, color, geometry and asset identity: the canonical registries in the Effective Authority Registry V3.1
- Runtime binding: design/system_v3/09_Android_Implementation_Contracts/Recognition_Result_Android_Contract_V1.md
- This layer: Recognition_Result_Visual_Contract_Registry_V1.json

## Frozen image sources

Each Result state has one authority record in `frozen_visual_authority` in the Visual Contract Registry. The records carry the exact path, SHA-256, dimensions, color-space declaration, aspect ratio and safe-area rule. The five page sources are:

| State | Frozen PNG | SHA-256 | Size | Color space | Aspect |
|---|---|---|---|---|---|
| HIGH_CONFIDENCE | `design/pages/recognition/design/05_Result_High_Frozen.png` | `f540ddc4b3844a453170537eaa70f506dc0eb1c312fc3410a960ad6cd87ba156` | 941×1672 | UNVERIFIED_NOT_RECORDED | 941:1672 |
| MEDIUM_CONFIDENCE | `design/pages/recognition/design/06_Result_Medium_Frozen.png` | `5a334f8deef344910865d43057a2df3d6d5137ca8ebfad3eef7557120586fcd3` | 941×1672 | UNVERIFIED_NOT_RECORDED | 941:1672 |
| LOW_CONFIDENCE | `design/pages/recognition/design/07_Result_Low_Frozen.png` | `b767965028786374bcadf9dd1b219b7b61f698edade323d8557293ebcec1d6e1` | 941×1672 | UNVERIFIED_NOT_RECORDED | 941:1672 |
| NO_FISH | `design/pages/recognition/design/08_Error_No_Fish_Frozen.png` | `3952e2f8eb8e42124e8554f6c1481351ff7cb7bb200180613787c4448bf2d8d2` | 941×1672 | UNVERIFIED_NOT_RECORDED | 941:1672 |
| IMAGE_QUALITY_WARNING | `design/pages/recognition/design/09_Error_Image_Quality_Frozen.png` | `965be5b37ce4c41e8a5b758f2f94bac16fa0b99dc135e25b114c2943d35b1a0d` | 941×1672 | UNVERIFIED_NOT_RECORDED | 941:1672 |

The frozen files are page-composition authorities. No separate raster safe-area metadata is registered; runtime safe content is derived from measured host insets, and exact content anchors resolve from the uncropped source and page geometry contract. Runtime must record the detected embedded profile (or its absence). Never crop, stretch, recompress or re-render source files.

The source aspect is 941:1672 (0.562799); target canvas is 1080×1920 (9:16). Pixel Targets defines uniform contain. Never crop, stretch, recompress or re-render source files. Preserve full uncropped test screenshots.

Species Selector and Metadata Edit overlays retain their separate frozen references/manifests and do not add top-level Result states.

## Profile and registry observations

Asset Registry records identity, dimensions and SHA-256 but has no ICC/color-space field. Source color space is UNVERIFIED_NOT_RECORDED, not guessed. Runtime comparison decodes any embedded profile to sRGB for comparison only and leaves source bytes unchanged; the run records the detected profile.

The page authority map calls the five page PNGs Frozen references while Asset Registry status remains REVIEW. Both statuses are recorded; this contract does not promote asset approval. See OBS-001 and OBS-002 in the Visual Contract Registry.

## Page layout and state contract

The baseline content column is 322dp at 360dp, centered with 19dp side margins. The content column scrolls when content, compact height, keyboard or fontScale requires it. Buttons remain in the state content flow; no universal fixed overlay is introduced. Host owns system insets and content begins below actual resolved insets. Compact-height Hero reduction is capped at the existing 12%.

| State | Hero at 360dp | Frozen composition |
|---|---:|---|
| HIGH_CONFIDENCE | 322×210dp | Hero → identity → vertical Length/Weight/Location → Story → dual actions |
| MEDIUM_CONFIDENCE | 322×178dp | Hero → candidate panel → confirmed record flow |
| LOW_CONFIDENCE | 322×178dp | Hero → low message/manual or retake → metadata → Story → dual actions |
| NO_FISH | 322×245dp | complete Evidence Fit → one recovery panel |
| IMAGE_QUALITY_WARNING | 322×214dp | complete Evidence Fit → one recovery panel using approved quality copy |

Medium candidates enter as uncommitted suggestions. The Frozen Medium reference contains both idle and selected candidate visuals; the gold-selected style applies only after an explicit tap. Confidence drives state routing; no independent numeric badge is added because the Frozen references define none.

The Result title is centered independently of Back. HIGH/MEDIUM/LOW use the original oriented photo with the existing subject-safe Hero rule. Recovery uses full-source Evidence Fit. Result has no fish-outline overlay; the Processing contour must end during the inherited resolve transition. Save Catch routes Home only after persistence; Continue Memory remains distinct.

## Shared system bindings

The full State Matrix and Anchor Map list every binding. The visual layer uses only the registered typography roles, color tokens, geometry tokens, components and assets. It creates no page-private token or shared component. Result Save retains the frozen light/gold-edge treatment; Recovery actions are not teal-solid. Glass uses registered MistGlass and remains intrinsic rather than stretched to viewport height.

## Quantitative verification

Pixel Targets fixes the 1080×1920 / density 3.0 baseline, records host insets and full-canvas source transform, and freezes the geometry/glyph tolerance formulas. Runtime checks must use full screenshots, exact asset SHA, token/component identity and source-specific anchors. No manual-only PASS, crop-to-fit, or tolerance adjustment is allowed.

## Motion and accessibility

Recognition Result reuses the existing final 200ms squared-fade resolve from Recognition Visual Fidelity V1.2; it adds no celebration, pulse, candidate cycling or looping AI animation. Reduce Motion is opacity-only. Accessibility order, fontScale, touch targets, keyboard/focus, state labels and contrast criteria are frozen in the linked contracts.

## Gate

**RECOGNITION_RESULT_VISUAL_CONTRACT_READY = PASS**

Upstream Design Contract = PASS; Android Implementation Contract = PASS; Visual Contract = PASS. Aggregate planning gate: **READY_FOR_ANDROID_IMPLEMENTATION = PASS**. This gate authorizes a later Android implementation work item only; no implementation is performed here.

PASS certifies the contract and its references are complete for implementation planning. Runtime screenshot, TalkBack, device, emulator and Android acceptance remain NOT_RUN. A future evidence run may be REVIEW_REQUIRED if source profile or authority evidence is ambiguous.
