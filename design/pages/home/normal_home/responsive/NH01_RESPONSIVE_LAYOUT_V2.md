# NH01 Responsive Layout V2

**Status:** `PROPOSED_FOR_CANONICAL_MAIN_REVIEW`  
**Machine authority:** [NH01_RESPONSIVE_LAYOUT_V2.json](NH01_RESPONSIVE_LAYOUT_V2.json)  
**Global standard:** [Responsive Layout Standard V1](../../../../system_v3/07_Responsive_System/Responsive_Layout_Standard_V1.md)  
**Authority crosswalk:** [NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md](NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md)  
**Android Runtime:** `NOT_VERIFIED`

## Purpose

NH01 uses one constraint-driven responsive policy, not a collection of layouts copied from individual viewport screenshots. The six frozen visual anchors validate brand identity, content hierarchy, component relationships and visual proportions. They are evidence anchors, not six independent layout algorithms.

The JSON is the sole machine authority for responsive values and rules, including the structured browser preview. This guide is explanatory; do not maintain a second set of numeric values here. The existing V1 responsive archive keeps static image identity and history; it is not a second browser layout calculator.

## What is fixed

- Brand title and approved avatar visual semantics.
- Top / Middle / Footer hierarchy and the meaning/order of page content.
- The selected Hero's declared aspect ratio and role, real-record-only neighboring Peeks, finite pager behavior, and single-record behavior.
- Morning Lake background source/treatment and real FishRecord photo safety contract.
- Required text/data priority, system font-scale behavior, action semantics, touch bounds and safe-area protection.

## What may adapt

Component width/height within declared bounds, approved spacing formulas, text wrapping, card space allocation, edge-Peek exposure, and the documented local Middle-scroll fallback. Footer stays pinned outside Middle scrolling. Exact M01 320×480 / fontScale 1.0 is `NO_SCROLL`; if its ratio, minimum width, readable copy, and safe bounds cannot fit together, report a constraint failure. Every numeric bound remains in the JSON contract.

## Logical band allocation

The JSON contract maps logical bands A (brand + statistics), B (Recent Catch heading + Hero/Pager) and C (Footer) onto the unchanged physical Top / Middle / Footer structure. After safe insets are consumed once, it computes `S = max(0, H_safe - (hA + hB + hC + gAB_base + gBC_base))`. Positive surplus adds `S/2` to each base gap; zero surplus preserves both base gaps. There are no extra gap caps, outer-breathing remainder, or separate footer reserve. The title-to-Hero gap remains internal to band B and is not modified by this allocation.

The `124dp` M01 exception is a minimum-readable floor, not a forced final Hero height. The final card also preserves its ratio, is at least `145dp` wide, keeps required copy readable, and does not scroll. If this exact profile cannot satisfy all four constraints after compressing nonessential whitespace, it fails rather than silently weakening one condition.

The “记录下一条鱼” copy is a static label under the current interaction contract. It receives no independent touch rectangle. The camera artwork sits centered inside its separately measured touch target; the complete Footer height counts the CTA line, camera target, one CTA-to-camera gap, and existing outer padding once.

## Exact M01 compact constraint

For exactly 320×480dp at system fontScale 1.0, use the contract's `preview_engine.parameters.hero.short_screen_exception.compact_layout` values to compress only nonessential top/footer whitespace and declared gap floors. The final Hero height is derived from the available Pager budget while preserving the `247:293` ratio and minimum width of 145dp. Do not scroll the Middle/Pager in this exact profile. If the readable copy, minimum width, ratio and `NO_SCROLL` cannot all fit, report `FAIL_CONSTRAINT`; do not weaken the contract.

## Authority boundary

- The original 1080x1920px PNG remains the frozen whole-page composition reference; its logical reference is 360x640dp at 3x.
- M01-M05 remain byte-identical frozen visual anchors. Their canvas pixels are not logical dp dimensions.
- The static archive V1 owns frozen-image identity and archive presentation. Its M05 480dp-width-only spacing target is not an Android runtime target.
- The existing Gemini unified authority continues to own non-responsive fields such as avatar, motion, background and photo behavior. The legacy fixed-coordinate layout contract is not the current Gemini route's responsive layout authority.
- ADAPTIVE_V2 is the target policy. V3_BASELINE is a comparison control; ADAPTIVE_V1 is non-authoritative for this route.

## Required review

The contract and control-plane references must validate before canonical adoption. Runtime conformance gaps are explicitly recorded in the JSON and are not hidden by changing the design rules. The Android matrix remains `NOT_VERIFIED` until exact-SHA evidence passes. Generated Hero/neighbor images remain `GENERATED_ILLUSTRATIVE_ONLY`; source authenticity and real-catch provenance do not gate this geometry freeze, while independent files, manifest status, and layout-stable failure fallback remain required.
