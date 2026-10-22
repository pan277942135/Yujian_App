# NH01 Responsive Layout V2

**Status:** `PROPOSED_FOR_CANONICAL_MAIN_REVIEW`  
**Machine authority:** [NH01_RESPONSIVE_LAYOUT_V2.json](NH01_RESPONSIVE_LAYOUT_V2.json)  
**Global standard:** [Responsive Layout Standard V1](../../../../system_v3/07_Responsive_System/Responsive_Layout_Standard_V1.md)  
**Authority crosswalk:** [NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md](NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md)  
**Android Runtime:** `NOT_VERIFIED`

## Purpose

NH01 uses one constraint-driven responsive policy, not a collection of layouts copied from individual viewport screenshots. The six frozen visual anchors validate brand identity, content hierarchy, component relationships and visual proportions. They are evidence anchors, not six independent layout algorithms.

The JSON is the sole machine authority for responsive values and rules. This guide is explanatory; do not maintain a second set of numeric values here.

## What is fixed

- Brand title and approved avatar visual semantics.
- Top / Middle / Footer hierarchy and the meaning/order of page content.
- The selected Hero's declared aspect ratio and role, real-record-only neighboring Peeks, finite pager behavior, and single-record behavior.
- Morning Lake background source/treatment and real FishRecord photo safety contract.
- Required text/data priority, system font-scale behavior, action semantics, touch bounds and safe-area protection.

## What may adapt

Component width/height within declared bounds, approved spacing formulas, text wrapping, card space allocation, edge-Peek exposure, and the documented local Middle-scroll fallback. The Hero keeps its readable frame and aspect ratio when short-screen space runs out; only Middle scrolls, and Footer stays pinned. Every numeric bound remains in the JSON contract.

## Authority boundary

- The original 1080x1920px PNG remains the frozen whole-page composition reference; its logical reference is 360x640dp at 3x.
- M01-M05 remain byte-identical frozen visual anchors. Their canvas pixels are not logical dp dimensions.
- The static archive V1 owns frozen-image identity and archive presentation. Its M05 480dp-width-only spacing target is not an Android runtime target.
- The existing Gemini unified authority continues to own non-responsive fields such as avatar, motion, background and photo behavior. The legacy fixed-coordinate layout contract is not the current Gemini route's responsive layout authority.
- ADAPTIVE_V2 is the target policy. V3_BASELINE is a comparison control; ADAPTIVE_V1 is non-authoritative for this route.

## Required review

The contract and control-plane references must validate before canonical adoption. Runtime conformance gaps are explicitly recorded in the JSON and are not hidden by changing the design rules. The Android matrix remains `NOT_VERIFIED` until exact-SHA evidence passes.
