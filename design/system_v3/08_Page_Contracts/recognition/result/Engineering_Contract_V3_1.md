# Recognition Result · Engineering Contract V3.1

**Status:** REVIEW · existing Result design package is Frozen; V3.1 data/responsive closure is not.

## Page structure
- One Result route with five distinct product states: HIGH_CONFIDENCE, MEDIUM_CONFIDENCE, LOW_CONFIDENCE, NO_FISH and IMAGE_QUALITY_WARNING.
- TECHNICAL_FAILURE is a separate issue route, outside the five Result/recovery states.
- Use one vertical, state-specific composition from the existing Frozen Result authority map. Shared components are reused only within their declared contract; page-private visual elements remain page composition, not new shared components.
- Result Hero geometry is state-specific. Do not normalize to one universal ratio.

## Scroll, fixed and flexible
- At the canonical 360dp viewport, content is approximately 322dp wide.
- The Result content column scrolls vertically when content, keyboard or font scale requires it. Keep active edits and the primary actions reachable.
- A compact height may reduce the state-specific Hero by at most 12%, preserving state identity and reachable actions, per the current geometry contract.
- Dual CTA width and 12dp gap follow Result V1.1 geometry. This is page geometry, not a global Button variant.

## Insets and system bars
- The host owns system insets. Content is laid out beneath resolved window insets per Shared Geometry.
- Centered Result title is centered on the viewport independently of Back width.
- Keep the Back action outside cutout/status-bar overlap.
- Do not modify Frozen page dimensions to compensate for an implementation inset error.

## Keyboard
- Metadata input uses the existing same-page Bottom Sheet contract.
- Numeric sheets use the declared decimal keyboard. Location search follows the registered Search contract.
- Back hides the IME before leaving/dismissing the active sheet; sheet focus is restored to its invoker on dismissal.
- Preserve the draft on validation error, selector cancellation and save failure.

## Media Fit
- HIGH/MEDIUM/LOW use the original oriented photo and the existing Hero Media contract: subject crop fill only when the full FishSafeRect fits; otherwise Subject Safe Fit.
- NO_FISH/IMAGE_QUALITY_WARNING use full-source Evidence Fit and show the complete evidence.
- Never stretch, generate image fill, use detector/classifier crop, blur-duplicate, or crop the Frozen screenshot into a runtime image.

## Shared component use
- Component IDs, tokens and icon assets are listed in Layout_Contract_V3_1.json.
- BACK_CENTER_TITLE, RESULT_SAVE and other Result profiles are page composition exceptions; they do not create new global variants.
- Species Selector is Shared Selector Core + Species Picker Variant with its existing Frozen authority.
- Metadata sheets use the registered Bottom Sheet; no new shared component is introduced.

## Animation and interaction
- Medium suggestions require an explicit candidate/species selection; a Top-1 suggestion is not user-confirmed.
- Low unresolved Save/Continue remembers destination, opens LOW_MANUAL, and resumes only after explicit species selection. Unconfirmed return preserves drafts and clears the pending destination.
- Save Catch → persist → Home. It does not open FishRecordDetail.
- Continue Memory → persist → FishRecordDetail(recordId, initialSection=MEMORY).
- During save, preserve layout and disable conflicting edits/duplicate submit. On failure, remain on Result with draft fields and a retryable error.
- No fake result/progress, no unknown-species persistence, and no no-op voice control.

## Error and recovery
- NO_FISH and IMAGE_QUALITY_WARNING show the Frozen Recovery panel with Retake and Gallery and never persist a record.
- IMAGE_QUALITY_WARNING keeps its existing Frozen generic copy while C-009 is open; no reason-specific diagnosis is invented.
- Save/network error remains retryable on Result. A save failure must not route Home or Detail.
- Product-level data fields do not yet have full audited repository/API/database lineage under C-011; do not treat this page contract as a backend schema.
