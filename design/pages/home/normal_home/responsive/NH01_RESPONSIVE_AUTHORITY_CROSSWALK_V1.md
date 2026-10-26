# NH01 Responsive Authority Crosswalk V1

**Status:** SPEC_FROZEN (PR #191 candidate; canonical on Main after merge)  
**Purpose:** One explicit, field-scoped resolution for existing NH01 visual, static archive, responsive, legacy layout and Gemini runtime authorities.

## Resolved authority table

| Authority | Current scope after the frozen PR is merged | Must not be used for |
|---|---|---|
| `design/system/core_visual_v1/reference/normal_home_v1.png` | Frozen whole-page composition and visible hierarchy; SHA remains unchanged | Absolute-coordinate scaling for every viewport; invisible touch bounds |
| `08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json` plus M01-M05 PNGs | Exact identity, canvas dimensions, SHA-256 and provenance of the five recovered static anchors | A set of five separate responsive algorithms |
| `08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md/.json` | Immutable historical archive contract and static image presentation/metadata; M05 remains a width-only static reference | Current Gemini runtime layout formulas or a fabricated M05 native height |
| `responsive/NH01_RESPONSIVE_LAYOUT_V2.json` | Sole frozen machine authority for current Gemini responsive geometry, constraints, typography roles, spacing, overflow and the existing structured browser preview | Motion, avatar appearance, background source, photo semantics, state or backend behavior outside its declared scope |
| `engineering/NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md` | Existing current authority for non-responsive Gemini fields and acceptance semantics; current responsive geometry is owned exclusively by frozen V2 | A competing copy of responsive numeric values once V2 is canonical |
| `engineering/Layout_Responsive_Contract_V1.md` | Legacy `NormalHomeContent` route and verified raster/touch-anchor evidence, where explicitly applicable | The rebuilt Gemini V3 Top/Middle/Footer responsive algorithm |
| `design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md` | Frozen decision record and traceability for current Adaptive V2.2 targets | A second independently maintained machine parameter set after V2 is canonical |
| `GeminiHomeAdaptiveLayoutPolicy.kt` and `GeminiNormalHomeContent.kt` | Evidence of the current implementation to compare against the contract | Design authority; current code discrepancies must be fixed to meet the contract, not used to silently redefine it |

## Explicit conflict decisions

1. **Static images are anchors, not layout branches.** Keep the original image and all five M01-M05 PNGs unchanged. Compare invariant hierarchy, component relationships, permitted proportions and declared bounds rather than demanding cross-size pixel identity.
2. **One active responsive rule set.** The machine contract uses actual safe width/height, system font scale, content state, measured text and component constraints. It does not choose a complete layout by matching a viewport ID.
3. **Logical units are explicit.** The original 1080x1920px master corresponds to 360x640dp at 3x. The source raster canvas is not a 1080x1920dp viewport. The 360x640dp M02 image is a distinct PNG with its own SHA.
4. **M05 is width-only.** The 480dp static target and its 24±2dp title-to-card note are archive/static-preview scope only. No Android window height is inferred from M05 and it remains excluded from the existing 24-case native baseline. A supplemental runtime width-boundary test may use an explicitly stated height and can claim only that test pair.
5. **Legacy and Gemini routes are separate.** The legacy fixed-coordinate mapping remains readable and useful for raster/touch evidence. The current Gemini route uses the Top/Middle/Footer content-first hierarchy and the active Adaptive V2 policy.
6. **Variants have clear roles.** Adaptive V2 is the target. V3 Baseline is comparison-only. Adaptive V1 is not authoritative for the current Gemini route.
7. **Specification and runtime states are independent.** A schema or browser-preview pass cannot change Android Runtime from `NOT_VERIFIED` to `PASS`.
8. **Code is not the authority.** Three implementation-conformance follow-ups are recorded in the machine contract. Work must resolve them against the contract in a separate implementation task; it must not weaken the contract to fit current helper behavior.

## Current Main baseline alignment

The frozen V2 page contract is the only numeric source for the current Gemini responsive route. Its machine rules use equal additions to the existing inter-band gaps and do not add independent gap caps or outer-breathing allocation. The shared Responsive Layout Standard remains a generic proposed reference; it does not override the page-owned V2 values. The V1 static archive and all frozen assets remain unchanged.

The short-screen minimum floor, minimum Hero width, aspect ratio, readable-copy rule and no-scroll condition are owned by the machine contract. The floor is a minimum-readable allowance, not a forced final card height. If those constraints cannot fit after compressing nonessential whitespace, report `FAIL_CONSTRAINT` with measurements.

The record-next-catch CTA is a static label under the current interaction contract/runtime, so it has no separate touch target. Camera artwork and its touch target remain separate measurements inside one Footer group; their numeric bounds come only from V2. Generated illustrative Hero/neighbor images do not require real-catch authenticity or provenance review for this geometry freeze; independent files, manifest status, and stable fallback geometry remain checked.

## Freeze and implementation sequence

1. The existing V2 machine contract, six frozen anchor hashes/canvas dimensions, metadata-only preview rules and control-plane bindings have passed their registered candidate gates.
2. PR #191 promotes the unchanged V2.0 numeric contract to `SPEC_FROZEN`. Fresh Admission, Normal Home Spec Governance, Design Governance and Design Manager Pages checks must pass on the updated exact head before merge.
3. Merge through the normal GitHub PR path. The V2 contract becomes the canonical Main responsive authority; no frozen PNG or V1 static archive value is rewritten.
4. Start Android implementation from that exact Main. Reuse the existing Gemini page and current call chain; record actual Compose constraints and safe insets. M05 remains a width-only static anchor; any supplemental native boundary probe states its height as a separate test input.
5. Android remains `NOT_VERIFIED` until the registered exact-SHA native build, unit/static checks, full measured matrix, screenshots/logs and APK provenance pass. A browser preview or frozen specification cannot substitute for device evidence.

The generic shared Responsive Layout Standard and Viewport Profiles remain shared references; this NH01 freeze does not freeze or redefine responsive rules for other pages.

No frozen PNG or existing V1 archive file is rewritten by this freeze.

