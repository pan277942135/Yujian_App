# Normal Home Accessibility Contract V1

**Status:** `READY_FOR_VISUAL_IMPLEMENTATION / RUNTIME_NOT_RUN`  
**Authority:** Normal Home NH03/NH04/NH07, Normal Home engineering contracts, Empty Home V2.2 responsive anchors and Shared Foundation V3.1.

## TalkBack order

Decorative backgrounds, sun/ripple/rod/line and noninteractive neighboring-card peeks are not focus stops.

### Empty Home

1. Brand / page heading.
2. Login or account entry, when shown.
3. Profile/avatar control, when shown.
4. Frozen Hero copy as one meaningful reading block.
5. Album/gallery action, if present in the current approved state.
6. Primary Capture button.

### Data Home

1. Brand/greeting heading.
2. Avatar control: “Profile” for signed-in states, “Login or register” for Guest.
3. Fish species statistic and value.
4. Catch-count statistic and value.
5. Record-days statistic and value; read-only, never a click target.
6. Recent Catch heading and “All” action.
7. Selected Hero content: fish photo description, species, valid measurement and available time/location, in that order.
8. Camera capture action.

When multiple records exist, expose the Pager as a labelled adjustable selection only if the existing implementation offers that semantic; otherwise each record is reached by the declared horizontal gesture and selected content is announced once. Never add focus stops for decorative neighbor peeks.

## Text scaling and clipping

Test fontScale **1.0, 1.3 and 1.5**. Use the registered Platform Typography roles and the existing Normal Home A1 page scaling contract. Preserve the system fontScale; do not shrink required text. Verify actual TextLayoutResult/glyph bounds, one-line ellipsis rules, and no overlap or CTA occlusion. FontScale 1.5 is an extended profile; its runtime result remains unverified until tested.

Missing optional Hero fields disappear according to NH04; do not announce placeholder zeroes, nulls or invented species. Initial unresolved statistics announce “not loaded”/dashes without claiming zero. Avatar loading announces loading once; failure resolves to the registered default without a persistent error badge.

## Touch, focus and contrast

- Normal Home page-specific hard minimum: every interactive target is at least **48×48 dp** and fully inside the measured safe window. The shared `touch.minimum` token is a 44×44 dp foundation floor; Normal Home's existing 48 dp page requirement is stricter and does not alter that token.
- Keep visible glyph/image bounds distinct from the larger touch bounds. Targets must not overlap adjacent actions; the record-days statistic is noninteractive.
- All icon-only controls have localized accessible names, role and state. Focus indication must remain visible against the actual background/media and must not change Frozen image pixels or geometry.
- Evaluate contrast against the actual composited frame: WCAG AA 4.5:1 for normal text and 3:1 for large text and meaningful non-text controls. If a Frozen/composited source does not meet the threshold, record `REVIEW_REQUIRED`; do not recolor the Frozen art or add an unregistered overlay to force a pass.

## State and recovery accessibility

TalkBack must distinguish Empty Home from unresolved data and from retained data after refresh failure. On media error, announce an unavailable photo while preserving species, record identity and navigation. Avatar states preserve identity semantics: Guest is distinct from signed-in default; remote avatar has concise profile semantics; loading and error do not create extra focus stops. All focus order, scaling, touch and contrast results remain `NOT_RUN` until measured with the exact APK/device profile.
