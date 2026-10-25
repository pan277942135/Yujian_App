# First-use Camera Guide V1 — pre-development freeze decision

**Task ID:** YUJIAN_FIRST_USE_GUIDE_PREDEV_CONTRACT_ASSETS_RESPONSIVE_FREEZE_V1  
**Decision status:** Partial freeze. The exact-head Chromium matrix passed on evidence SHA b8fdb47985d123a08cae947065634ade2a5d451d; original independent photos, candidate visual choices, and native Android acceptance remain unresolved.  
**Machine matrix:** PREDEV_READINESS_MATRIX.json

## Frozen

- The three existing P01/P02/P03 Frozen PNG files retain their original byte lengths, dimensions, SHA-256 values, and V1 teaching sequence.
- The structured page contracts retain the V1 copy, one correct Hero, three fixed error roles, navigation visibility, stable layer IDs, explicit parent/child links, and existing shared component/token references.
- Pixel coordinates, normalized coordinates, source checksums, and measurement methods are frozen as a measurement record of the supplied flattened PNGs. They are not runtime layout coordinates and do not claim recoverable original Figma/PSD layers.
- The responsive implementation invariants are frozen after the exact-head Chromium matrix: inset-aware available viewport; P03 capture direction separate from outer-page orientation; complete image containment; wrapping rather than truncating required text; short-screen vertical scroll; reachable actions; and 48dp minimum target recommendation. Spacing, typography, hero sizing and crop/focal tuning remain candidate values.
- Candidate asset records are frozen as accurate provenance/status records only. Candidate photographs themselves are not frozen or production-approved.

## Candidate pending one product-owner review

- Eight generated independent PNG candidates are available for preview: four horizontal candidates shared by P01/P03 and four vertical candidates for P02. They map across all 12 page/role slots without duplicate binaries.
- Candidate images are not pixel matches to the Frozen PNGs, are not approved originals, and remain CANDIDATE_NEEDS_APPROVAL; source/license/privacy review is unresolved. Design Manager direct links are for review and development discussion only.
- Proposed spacing, minimum typography, hero sizing and focal tuning remain candidate visual parameters. The responsive algorithm is executable and its invariants passed; final visual polish awaits one combined review.

## Blocked / not run

- No approved original independent hero/error photographs were located in the canonical repository tree, recorded historical branches, or archive. Nine archived error crops remain REFERENCE_ONLY; they are composite-derived and are not promoted.
- The Figma account connection is verified, but no task-linked Figma file key/URL or selected file was available to inspect. The account does not expose a file-list/search entry point without a file key; this is not evidence that every file in the account was searched.
- Original source layers, uncomposited alpha/effects, declared fonts/line heights, original DOM z-index, and invisible touch bounds cannot be reconstructed from the flattened PNGs.
- Android runtime/device acceptance is outside this design-preparation task and remains NOT_RUN.
- ALL_ASSETS_FROZEN and PREDEV_FULL_READY are **not** PASS. Required next action: one product-owner review of the eight candidate images and the responsive visual tuning; replace candidates with approved independent originals where available, then update source/license metadata and rerun exact-SHA checks.

## Exact browser evidence

- Evidence source SHA: b8fdb47985d123a08cae947065634ade2a5d451d
- Design Manager Pages run: 40157848305; Design Governance run: 40157848242; Control Plane Admission run: 40157848222.
- The browser suite passed 54 page × viewport × font-scale combinations. It reported zero console errors, HTTP errors, and failed requests.
- Screenshot artifact: Design-Manager-Browser-Build-40157848305-b8fdb47985d123a08cae947065634ade2a5d451d, including structure, assets, responsive desktop/mobile and P03 landscape-capture previews. The final PR head is rechecked separately before merge.

The page-level manifest.json remains the sole authority for Frozen PNG bytes. frontend_assets_manifest.json owns independent photo-slot provenance. geometry_measurements.json records native raster measurements. responsive_contract.json records responsive implementation rules. No candidate photo or inferred source style is promoted by this decision.
