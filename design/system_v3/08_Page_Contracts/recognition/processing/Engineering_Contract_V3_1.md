# Recognition Processing · Engineering Contract V3.1

**Status:** REVIEW · source package design is FROZEN, compiled page contract is not.

## Page structure
- Full-viewport Recognition Processing surface entered only with a normalized user image.
- The original oriented photo remains the dominant flexible media surface. Existing Frozen page layers are applied in their documented order.
- Back is an exit to the invoking surface, not a failure state.
- Product-visible Processing states are IMAGE_RECOGNIZING, FISH_LOCATED and SPECIES_RECOGNIZING. CAPTURED and DETECTING are internal phases mapped to the same first visible state.

## Scroll, fixed and flexible
- The image viewport is flexible and fills the page's resolved visible photo viewport.
- Status UI and Back follow their existing Frozen page authorities; do not infer coordinates from a different page screenshot.
- Processing has no content list and no page scrolling contract.

## Insets and system bars
- Resolve system window insets before constructing the content/media viewport.
- The AI Edge Field uses that visible photo viewport after crop/scale and does not remap coordinates for system insets.
- Preserve the same displayed-photo transform for the actual detector bbox and focus geometry.
- Exact runtime edge-to-edge behavior remains part of the independent Runtime/Evidence closure.

## Keyboard
- No page-owned text input or keyboard is defined.

## Media Fit
- Use the original user photo; no synthetic replacement, processing tint, duplicated blur, generated fill, detector rectangle or classifier crop.
- The existing Frozen Processing Visual Spec declares an opaque photo with ContentScale.Crop. Geometry uses the registered cover/crop behavior only within that page authority.
- Keep the photo readable under edge field/focus layers.

## Shared component use
- Shared public references are media.image and navigation.back, with their listed Foundation dependencies.
- RecognitionStatusCard and AI Edge Field remain existing page-scoped frozen authorities. They are not new shared registry entries; their exact component geometry remains owned by their current source documents.
- Do not substitute the general Glass Surface for the dark Processing status card unless the current Processing authority explicitly maps it.

## Animation and interaction
- Preserve one continuous Edge Field clock across state transitions; do not restart its geometry or segment phase.
- Minimum visual beats: IMAGE_RECOGNIZING ≥900ms; FISH_LOCATED ≥600ms; SPECIES_RECOGNIZING ≥1250ms; RESOLVE = 200ms.
- These are semantic visual minimums, not progress timers or promised recognition duration.
- Reduce Motion / FULL-BALANCED-LITE / D0-D4 preserve the same product-state meaning.
- Haptic = NONE; Sound = NONE.
- While active, Back exits to the previous surface; no automatic persistence occurs.

## Error and recovery
- NO_FISH and IMAGE_QUALITY_WARNING may exit early from detection; they are issue routes, not extra Processing product states.
- TECHNICAL_FAILURE is separate and can result from real pipeline/model/crop failure.
- A pipeline timeout is represented as technical failure only when the pipeline reports it; no numeric timeout is invented here.
- Network/dependency failure, if emitted by the pipeline, is TECHNICAL_FAILURE and never No Fish/Image Quality.
- Page remains REVIEW until PROC-001 state/copy reconciliation and PROC-002 typography mapping are resolved by their owners.
