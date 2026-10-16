# Recognition Result Accessibility Contract V1

**Status:** FROZEN contract; runtime NOT_RUN.

## Reading order

- High/Low: Back → title → evidence image → identity or low-confidence message → correction/manual/retake controls when present → Length → Weight → Location → Story heading/editor/counter → Continue Memory → Save Catch.
- Medium: Back → title → evidence image → candidate prompt → options in visual order → Other Species → confirmed metadata → Story → Continue Memory → Save Catch.
- Recovery: Back → title → complete evidence image → recovery heading → approved guidance → Retake → Gallery.

Do not expose background layers. Announce a value once; avoid redundant identity narration.

## Labels and state

Back is localized and labelled. Title is one heading. Evidence images receive meaningful state-specific descriptions without claiming an unconfirmed species. Candidate options announce fish name and suggestion/selected state. Metadata reads label with value. Story is a labelled multiline field with a 0–300 Unicode-code-point counter. Save Catch and Continue Memory have distinct labels and enabled/loading semantics. Errors are textual and associated with the relevant control; color alone never communicates state.

## Font scaling

Test fontScale 1.0, 1.3, 1.5 and 2.0. Do not shrink tokens. Text wraps, surfaces grow, and the outer column scrolls. No critical text is clipped or primary action hidden. At 320dp, use registered narrow candidate/selector variants; 360dp preserves Frozen composition; 411dp keeps the 322dp content cap. Compact-height Hero reduction stays within the frozen 12% limit.

## Touch, keyboard and focus

- Every interactive target: at least 44×44dp.
- Save Catch, Continue Memory, Retake, Gallery, species commit and sheet commit/close: at least 48×48dp.
- No hit target overlaps another control or gesture exclusion region.
- Sheet opens with focus inside and restores focus to its invoking field.
- Back first hides IME, then dismisses sheet; sheet content scrolls above IME with title and actions reachable.

## Contrast and color

Use canonical Color Tokens and measure the actual foreground against the composited MistGlass/background:
- normal text ≥4.5:1;
- large text ≥3:1;
- meaningful control/icon/boundary ≥3:1.

These thresholds follow the existing frozen Account & Privacy Accessibility & Localization V1 criterion. If a token fails in this Result context, record the state and composite; do not add a page-local color.

## Acceptance

Record state, viewport, insets, fontScale, TalkBack order, focus restoration, target size and measured contrast. Missing labels, clipping, obscured actions, focus loss or below-threshold contrast are FAIL. Runtime checks are NOT_RUN.
