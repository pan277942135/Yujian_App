# Recognition Result · Engineering Contract V3.1

**Status:** FROZEN page contract; runtime execution is NOT_RUN.

## Page structure
- One Result route with High, Medium, Low, No Fish and Image Quality product states; Technical Failure remains separate.
- State-specific Frozen PNGs and authority_map.json own composition and visible copy.
- Candidate Card, CTA, Metadata Row and Bottom Sheet use declared shared contracts; Result-only compositions stay page-scoped.
- Hero geometry remains state-specific; no universal ratio is introduced.

## Scroll, fixed and flexible
- At 360dp preserve the existing approximately 322dp content width and Frozen composition.
- At 320dp adapt to safe available width while preserving composition and source aspect; at 411dp retain the Frozen content-width cap.
- The content column scrolls vertically when content, keyboard or font scale requires it; keep active edit and primary actions reachable.
- Compact height may reduce the existing state-specific Hero by no more than 12%; preserve state identity.

## Insets and system bars
- Host owns system insets. Content is placed below resolved window insets.
- Centered title remains centered independently of Back width; Back stays clear of cutout/status overlap.
- Do not change Frozen page dimensions to compensate for host inset error.

## Keyboard
- Metadata uses the existing same-page Bottom Sheet and registered Search contracts.
- Back hides IME before leaving/dismissing the active sheet; focus returns to its invoker.
- The sheet scrolls above IME and preserves drafts on validation error, selector cancellation and save failure.

## Media Fit
- HIGH/MEDIUM/LOW use the original oriented photo and existing subject-safe Hero contract.
- NO_FISH/IMAGE_QUALITY_WARNING use complete Evidence Fit.
- Never stretch, generate fill, use detector/classifier crop, blur-duplicate or crop a Frozen screenshot into runtime media.

## Shared component use
- Resolve IDs/tokens/assets through Layout_Contract_V3_1.json and effective registries.
- Candidate Card, CTA, Metadata Row and Bottom Sheet use existing public contracts; page-specific profiles remain page-owned.
- Species Selector remains Shared Selector Core + Species Picker Variant; no shared component is added.

## Animation and interaction
- Medium suggestions require explicit selection; unresolved Low persistence is gated by LOW_MANUAL.
- Save Catch → persist → Home. Continue Memory → persist → FishRecordDetail(memory).
- Save prevents duplicates; failure stays on Result with fields/destination preserved.
- Quality and copy resolve only through ImageQualityCopy_V1.json; UI cannot invent/paraphrase a reason diagnosis.

## Error and recovery
- No Fish and Image Quality use their respective Frozen Recovery copy/actions and never persist.
- QUALITY_GATE WARNING alone does not force Image Quality; NO_FISH stays separate.
- Technical Failure uses neither recovery copy.
- Source ownership resolves at page-contract boundary only; this is not a Backend/API/DB field contract.
