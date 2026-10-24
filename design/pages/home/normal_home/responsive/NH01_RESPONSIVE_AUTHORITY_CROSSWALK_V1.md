# NH01 Responsive Authority Crosswalk V1

**Status:** PROPOSED_FOR_CANONICAL_MAIN_REVIEW  
**Purpose:** One explicit, field-scoped resolution for existing NH01 visual, static archive, responsive, legacy layout and Gemini runtime authorities.

## Resolved authority table

| Authority | Scope after this proposal is canonical | Must not be used for |
|---|---|---|
| `design/system/core_visual_v1/reference/normal_home_v1.png` | Frozen whole-page composition and visible hierarchy; SHA remains unchanged | Absolute-coordinate scaling for every viewport; invisible touch bounds |
| `08_multi_screen_static_hifi/NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json` plus M01-M05 PNGs | Exact identity, canvas dimensions, SHA-256 and provenance of the five recovered static anchors | A set of five separate responsive algorithms |
| `08_multi_screen_static_hifi/NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md/.json` | Immutable historical archive contract and static image presentation/metadata; M05 remains a width-only static reference | Current Gemini runtime layout formulas or a fabricated M05 native height |
| `responsive/NH01_RESPONSIVE_LAYOUT_V2.json` | Single proposed machine authority for responsive geometry, constraints, typography roles, spacing, overflow and generated preview rules for the current Gemini route | Motion, avatar, background, photo, state or backend behavior outside its declared scope |
| `engineering/NH01_GEMINI_ADAPTIVE_V2_UNIFIED_AUTHORITY_V1.md` | Existing current authority for non-responsive Gemini fields and acceptance semantics; responsive geometry is delegated to V2 only after its preview/freeze gate passes | A competing copy of responsive numeric values once V2 is canonical |
| `engineering/Layout_Responsive_Contract_V1.md` | Legacy `NormalHomeContent` route and verified raster/touch-anchor evidence, where explicitly applicable | The rebuilt Gemini V3 Top/Middle/Footer responsive algorithm |
| `design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md` | Frozen decision record and traceability for current Adaptive V2.2 targets | A second independently maintained machine parameter set after V2 is canonical |
| `GeminiHomeAdaptiveLayoutPolicy.kt` and `GeminiNormalHomeContent.kt` | Evidence of the current implementation to compare against the contract | Design authority; current code discrepancies must be fixed to meet the contract, not used to silently redefine it |

## Explicit conflict decisions

1. **Static images are anchors, not layout branches.** Keep the original image and all five M01-M05 PNGs unchanged. Compare invariant hierarchy, component relationships, permitted proportions and declared bounds rather than demanding cross-size pixel identity.
2. **One active responsive rule set.** The machine contract uses actual safe width/height, system font scale, content state, measured text and component constraints. It does not choose a complete layout by matching a viewport ID.
3. **Logical units are explicit.** The original 1080x1920px master corresponds to 360x640dp at 3x. The source raster canvas is not a 1080x1920dp viewport. The 360x640dp M02 image is a distinct PNG with its own SHA.
4. **M05 is width-only.** The 480dp static target and its 24±2dp title-to-card note are archive/static-preview scope only. No Android window height is inferred and M05 is excluded from the current 24-case native matrix.
5. **Legacy and Gemini routes are separate.** The legacy fixed-coordinate mapping remains readable and useful for raster/touch evidence. The current Gemini route uses the Top/Middle/Footer content-first hierarchy and the active Adaptive V2 policy.
6. **Variants have clear roles.** Adaptive V2 is the target. V3 Baseline is comparison-only. Adaptive V1 is not authoritative for the current Gemini route.
7. **Specification and runtime states are independent.** A schema or browser-preview pass cannot change Android Runtime from `NOT_VERIFIED` to `PASS`.
8. **Code is not the authority.** Three implementation-conformance follow-ups are recorded in the machine contract. Work must resolve them against the contract in a separate implementation task; it must not weaken the contract to fit current helper behavior.

## Current Main baseline delta — review required

The latest Main responsive foundation is V1.1 and freezes equal positive-surplus additions (`S/2`, `S/2`). This PR's NH01 Responsive Layout V2 candidate retains the task-specified `2S/3`, `S/3` split, so it is a deliberate proposal to change that frozen foundation. The candidate does not claim that the two policies are already aligned. Keep the contract `PROPOSED_FOR_CANONICAL_MAIN_REVIEW` and the preview `PREVIEW_ONLY` until canonical Main explicitly accepts or rejects the delta. The authority map now reflects V1.1 and records the candidate delta separately.

## Canonical adoption sequence

1. Validate the JSON against the shared schema and verify all six frozen image hashes/canvas dimensions.
2. Validate page-owned and control-plane references, task/gate binding and the single-authority rule.
3. Merge the validated candidate contract to canonical `main` with status still `PROPOSED_FOR_CANONICAL_MAIN_REVIEW`; do not begin Android implementation from a candidate.
4. `NH01-RESPONSIVE-PREVIEW-V2` updates the existing registered NH01 preview route to consume the machine contract, without creating a second workspace shell, and proves fixed, wide, non-anchor and threshold-boundary behavior.
5. Only after `G-NH01-RESPONSIVE-PREVIEW-V2` passes may a control-plane change promote the contract to `SPEC_FROZEN`; synchronize product branches and rerun admission. This candidate gate is distinct from canonical SWOS P3-05/P3-06/P3-07.
6. P3-02 then integrates the shared Design Manager workspace using the frozen responsive contract and preserves the V1 static archive renderer.
7. Run the exact-SHA Android matrix separately. Runtime remains `NOT_VERIFIED` until the native gate passes.

No frozen PNG or existing V1 archive file is rewritten by this proposal.
