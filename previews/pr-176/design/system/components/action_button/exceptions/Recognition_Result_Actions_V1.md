# Recognition Result · Scoped Action Button Exceptions V1

Status: **PAGE-SCOPED FROZEN COMPOSITION**  
Reviewed: 2026-10-10  
Applies only to: Recognition Result V1.1

## Why a scoped exception is necessary

Shared Action Button V1.1 freezes the global semantic hierarchy PRIMARY / SECONDARY_STRONG / SECONDARY_MUTED, the default teal PRIMARY surface and the 44/56 confirmed-result pair example. Later **Result-specific** Frozen state authorities and V1.1 engineering contracts use an additive `RESULT_SAVE` light surface, a muted visual for `继续记忆`, and an equal-width confirmed dual-CTA arrangement.

Those are **conflicting visual recipes** if the generic usage examples are applied unconditionally. Neither source should be silently overwritten.

## RR01 and resolved RR02 / RR03

- `保存本次鱼获`: semantic commit/primary action; Result-owned `RESULT_SAVE` presentation (light surface, DeepLakeBlue text, restrained MorningGold edge/accent). This is **not** a fourth general-purpose Action Button hierarchy level.
- `继续记忆`: secondary destination, using the Result V1.1 **muted** treatment specified in `component_map.json`, while remaining a real actionable path.
- Layout: equal widths and 12dp gap as specified by `layout_geometry_contract.json`; do not apply the shared 44/56 example to this later Result V1.1 composition.
- On initial entry, both actions are idle and actionable when valid. A spinner/disabled submitting state is entered **only after** an actual activation; no artificial first-frame loading.
- Both destinations create one FishRecord before navigating; while saving, prevent duplicate submission and preserve user input on error.

## Unresolved RR03

- Keep manual selection and retake visible, **plus editable Metadata, Story and Dual CTA** as frozen for Low V1.1.
- Manual / retake controls retain their action hierarchy and the shared 58/42 example unless a state-specific Frozen authority overrules it.
- Tapping unresolved `继续记忆` / `保存本次鱼获` must launch `LOW_MANUAL` without creating an unknown-species record, then resume the chosen destination after confirmation.
- Returning from the selector without confirmation preserves local draft inputs and clears the pending destination.

## RR04 / RR05

- Recovery Retake / Gallery share the non-teal-solid Result treatment inside **one Recovery Glass Panel**, not separate stand-alone action cards.
- No Result Save, Story, or FishRecord action is shown.

## Precedence and scope

Result 05–09 state PNGs > Result V1.1 geometry/component contracts > this page exception > global Action Button V1.1 usage examples. The shared three visual levels, button accessibility states and global non-Result page mappings stay frozen.

Machine profile names `RESULT_SAVE` and `RESULT_CONTINUE_MUTED` are scoped Result composition keys, **not** globally selectable `action_button_v1.variants[]` IDs.

If a Frozen screenshot proves a different button proportion or copy, log an explicit versioned conflict and obtain visual signoff before changing the frozen numeric contract. Do not infer pixel-exact measurements from prose alone.
