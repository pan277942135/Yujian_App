# First-use Camera Guide V1.2 — two-step portrait-phone active decision

**Task ID:** YUJIAN_FIRST_USE_GUIDE_PREDEV_CONTRACT_ASSETS_RESPONSIVE_FREEZE_V1  
**Decision status:** Active V1.2 two-step sequence. Both active steps keep the phone portrait. Eight generated candidate images are owner-approved for Android Runtime but remain GENERATED_CANDIDATE / NOT_FROZEN. Original independent photo sources remain blocked; Android device acceptance remains NOT_RUN.  
**Machine matrix:** PREDEV_READINESS_MATRIX.json

## Frozen

- The active guide contains only P01/P02: P01 portrait phone + horizontal fish; P02 portrait phone + vertical fish. The former P03 landscape-phone Frozen PNG retains its original bytes and SHA-256 only as historical archive; it is not an active page, route, asset slot or Runtime step.
- The active P01/P02 structured page contracts retain one correct Hero, three fixed error roles, navigation visibility, stable layer IDs, explicit parent/child links, and existing shared component/token references. P02 is the final step and its primary action is 开始拍摄.
- Pixel coordinates, normalized coordinates, source checksums, and measurement methods are frozen as a measurement record of the supplied flattened PNGs. They are not runtime layout coordinates and do not claim recoverable original Figma/PSD layers.
- The responsive implementation invariants remain: inset-aware available viewport; portrait-phone instruction on both active steps; complete image containment; wrapping rather than truncating required text; short-screen vertical scroll; reachable actions; and 48dp minimum target recommendation. Spacing, typography, hero sizing and crop/focal tuning remain candidate values.
- Candidate asset records preserve provenance. The eight independent generated candidates are approved for Android Runtime, but are still not Frozen originals.

## Runtime-approved generated candidates (not Frozen)

- Eight generated independent PNG candidates are approved for Runtime: four horizontal candidates for P01 and four vertical candidates for P02. They map to exactly eight active page/role slots without duplicate binaries.
- Candidate images are not pixel matches to the Frozen PNGs and remain GENERATED_CANDIDATE / NOT_FROZEN. The owner approval permits Runtime use only; it does not promote them to Frozen design masters.
- Proposed spacing, minimum typography, hero sizing and focal tuning remain candidate visual parameters. The responsive algorithm is executable and its invariants passed; final visual polish awaits one combined review.

## Blocked / not run

- No approved original independent hero/error photographs were located in the canonical repository tree, recorded historical branches, or archive. Nine archived error crops remain REFERENCE_ONLY; they are composite-derived and are not promoted.
- The Figma account connection is verified, but no task-linked Figma file key/URL or selected file was available to inspect. The account does not expose a file-list/search entry point without a file key; this is not evidence that every file in the account was searched.
- Original source layers, uncomposited alpha/effects, declared fonts/line heights, original DOM z-index, and invisible touch bounds cannot be reconstructed from the flattened PNGs.
- Android runtime/device acceptance is outside this design-preparation task and remains NOT_RUN.
- ALL_ASSETS_FROZEN and PREDEV_FULL_READY are **not** PASS. The remaining device acceptance is deliberately deferred to the product owner using the delivered APK; this task does not run API 28 device verification.

## Exact browser evidence

- Evidence source SHA: b8fdb47985d123a08cae947065634ade2a5d451d
- Design Manager Pages run: 40157848305; Design Governance run: 40157848242; Control Plane Admission run: 40157848222.
- Historical V1 browser evidence passed 54 page × viewport × font-scale combinations for the former three-page guide. It is retained as historical evidence only. The active V1.2 Design Manager browser matrix is 36 page × viewport × font-scale combinations (2 pages × 9 viewports × 2 font scales) and must be rerun on the current Design Manager build.

The page-level manifest.json remains the sole authority for Frozen PNG bytes. frontend_assets_manifest.json owns independent photo-slot provenance. geometry_measurements.json records native raster measurements. responsive_contract.json records responsive implementation rules. No candidate photo or inferred source style is promoted by this decision.
