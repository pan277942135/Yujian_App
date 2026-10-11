# Recognition Processing · Engineering Contract V3.1

**Status:** FROZEN page contract; runtime/evidence execution is NOT_RUN.

## Page structure
- Enter only with a normalized user image.
- The original oriented photo remains the dominant flexible media surface. Existing Frozen layers and keyframes remain read-only.
- The sole effective state timeline is Recognition Processing State Machine V3.1.
- Back exits to the invoking surface; it is not an error or persistence state.

## Scroll, fixed and flexible
- The image viewport flexes within the resolved visible photo viewport.
- Status UI and Back follow their existing Frozen authorities.
- Processing has no content list and no page scrolling contract.

## Insets and system bars
- Resolve system window insets before establishing the content/media viewport.
- The AI Edge Field and actual detector geometry use the same displayed-photo transform.
- Existing Runtime/Evidence behavior remains outside this page freeze.

## Keyboard
- No page-owned text input or keyboard is defined.

## Media Fit
- Use the original user photo with the existing opaque photo and registered cover behavior.
- Do not use synthetic replacements, processing tint, duplicated blur, generated fill, detector boxes or classifier crops.
- Keep the photo readable beneath the authorized edge/focus layers.

## Shared component use
- Use registered media.image and navigation.back.
- RecognitionStatusCard and AI Edge Field remain page-scoped Frozen authorities, not new shared components.
- Status subtitle maps to type.body_small, 14sp / secondary, through the registered Processing override.

## Animation and interaction
- Minimum beats: CAPTURED ≥220ms; DETECTING ≥320ms; OUTLINE ≥350ms; CLASSIFYING ≥250ms.
- Advance only when both the state minimum and real pipeline event are satisfied. No timer-only auto-advance, fake percentage, ETA or promised total duration.
- Preserve one continuous Edge Field clock across state transitions.
- Reduced Motion / FULL-BALANCED-LITE / D0-D4 preserve semantic state order.
- Haptic = NONE; Sound = NONE. Back exits without persistence.

## Error and recovery
- NO_FISH and INVALID quality recovery remain distinct exits; neither creates a record.
- TECHNICAL_FAILURE is only a real pipeline/model/crop/dependency error.
- Network failure is never No Fish or Image Quality. Timeout is technical failure only when reported by the pipeline.
- Legacy Stage 1 / Stage 2 / Stage 3 labels are deprecated; this V3.1 State Machine is canonical.
