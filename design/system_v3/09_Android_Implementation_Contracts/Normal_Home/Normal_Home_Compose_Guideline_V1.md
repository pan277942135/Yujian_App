# Normal Home Compose Guideline V1

**Status:** FROZEN_IMPLEMENTATION_GUIDANCE  
**Authority:** Normal_Home_Android_Contract_V1.md  
**Runtime:** NOT_RUN

This file describes ownership and layout constraints for a later Compose implementation. It contains no Kotlin and defines no new design element.

## Ownership Order

Window → Insets owner → Safe content / source coordinate mapping → Page layout → Registered components and semantics.

- One route owner selects Empty Home or Normal Home from resolved record count.
- Normal Home data content is composed from the registered Header, Statistics, Recent Catch, Hero and CTA owners in source order.
- Empty Home uses its own V2.2 scene and control-group contract. Do not reuse Normal Home's vertical-offset formula.
- Each shared element resolves through Normal_Home_Android_Component_Mapping_V1.json; page-owned decorative layers remain local and are not promoted to components.

## Geometry Binding

- Resolve all anchors from Normal_Home_Pixel_Targets_V1.json and Normal_Home_Component_Anchor_Map_V1.json.
- Measure app-window width/height, safe insets, density, and fontScale from one window profile.
- Use the registered Normal Home transform and one shared delta for the Header, Statistics, Recent Catch, Hero, CTA and Camera.
- Convert source pixels using measured density/registered scale; do not interpret raster coordinates as dp.
- Keep Visual Bounds, Layout Bounds and Touch Bounds separate for NH05.
- Do not add a second safe-area offset or independently center child regions to compensate for drift.
- Do not use arbitrary padding, fillMaxHeight to stretch page content, or weight to place critical frozen visual anchors.

## Layout and Scroll

- Normal-height supported windows use NORMAL_FIXED while registered bounds fit.
- Enter SHORT_SCREEN_MODE only for the Visual Contract's overlap, required text clipping, unreachable content, unsafe control, or inaccessible primary action conditions.
- In the existing SAFE_OVERFLOW exception, Header → Statistics → Recent Catch → selected Hero remain reachable in registered order. The safe-bottom action group stays separate and reachable.
- Do not hide statistics, remove Hero, globally shrink the page, or introduce an extra overlay.
- Horizontal Pager movement remains user-driven and does not seize vertical scroll.
- Preserve source geometry and card bounds; only Hero media contents follow the existing Fill/Fit/Cover/EvidenceFit decision.

## State and Data

- Bind only the IDs in Normal_Home_State_Binding_V1.json and the existing page-state matrix.
- Empty data is shown only after successful resolution; unresolved data never becomes a zero count.
- During refresh/error with a previous snapshot, retain that snapshot and stable selected record.
- Optional absent metadata follows the existing omission rules; do not add null, fake zero, invented species, fake fish photo, or recognition confidence.
- Media failure/recovery affects the media viewport only.

## Components, Assets and Semantics

- Use exact shared component IDs/variants and token references in the Mapping JSON.
- Resolve icons through Icon Registry identity; Android system icons or hand-drawn substitutions are not permitted.
- Resolve asset paths and SHA-256 through Resource Binding and registries. A REVIEW item is not silently promoted.
- Interactive targets are at least 48×48dp and are measured independently from visible glyph/art bounds.
- Decorative background, rod, fishing line, bobber, ripple, cloud, and particle layers are excluded from TalkBack and keyboard focus.
- Avatar and Hero content have registered descriptions/fallbacks; selected record is announced once; adjacent Pager peeks are not duplicate focus targets.
- Keep reading and focus order consistent with the Accessibility Contract. Respect system fontScale.

## Motion

Use only values and conditions in Normal_Home_Motion_Runtime_Binding_V1.md. Start decorative loops only while lifecycle is resumed. Re-check the platform Reduce Motion/animator setting on resume and settings changes. Reduce Motion leaves a neutral static state; user paging/navigation remain available.
