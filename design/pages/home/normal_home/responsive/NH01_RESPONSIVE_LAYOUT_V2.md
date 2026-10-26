# NH01 Responsive Layout V2

**Status:** `SPEC_FROZEN` (PR #191 candidate; canonical on Main after merge)  
**Contract version:** `2.0`  
**Machine authority:** [NH01_RESPONSIVE_LAYOUT_V2.json](NH01_RESPONSIVE_LAYOUT_V2.json)  
**Authority crosswalk:** [NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md](NH01_RESPONSIVE_AUTHORITY_CROSSWALK_V1.md)  
**Android Runtime:** `NOT_VERIFIED`

## Purpose

NH01 uses one constraint-driven responsive policy. The five frozen M01–M05 images validate identity, hierarchy, relationships, proportions and balance; they are visual anchors, not separate layouts to copy.

The JSON file is the only machine-readable source for responsive values and rules. This guide explains how to apply that contract and intentionally does not repeat its numeric parameters. The structured-preview rules file contains presentation metadata only. The shared responsive standard remains a generic proposed reference and does not override NH01's page-owned contract.

## Scope and current Android route

The contract applies to the existing multi-record NH01 Home and single-record NH02 Home states on the Gemini route. The current Android call chain is `YujianApp.kt → HomeScreen.kt → GeminiNormalHomeContent`; the policy is resolved by `GeminiHomeAdaptiveLayoutPolicy.kt`. The existing Design Manager route remains the browser preview entry. Empty Home, the legacy `NormalHomeContent` route, other pages, and separately owned motion/avatar/background/data rules stay outside this contract.

## Fixed structure and adapting behavior

Keep the existing physical Top / Middle / Footer hierarchy. Logical bands A, B and C describe space allocation and do not add Compose containers. The Recent Catch title belongs to the catch-content band even where the current tree renders it in Top. Footer remains outside any Middle/Pager scroll container.

The contract resolves positive free space by adding equal portions to the two existing inter-band base gaps. It does not cap those additions or reserve extra outside space. The title-to-Hero gap remains internal to the catch-content band. The JSON also owns all Hero, Peek, Pager, width-cap, text-wrap, camera-art, touch-target and safe-area parameters.

Use actual safe dimensions, parent constraints, system font scale, content state and measured text. Consume each system inset once. Keep real records and stable IDs in the finite pager; a single-record state has one centered Hero without invented neighbors. Image failure preserves geometry and controls and uses the declared safe fallback.

## Short screens, text and constraint conflicts

Apply the contract's ordered short-screen response: compress nonessential whitespace, reduce declared gaps only within their allowed bounds, use the declared wrapping/reflow, then allow only the documented local Middle/Pager scroll. Preserve Footer placement. For the exact M01 default-font condition, the contract requires the declared Hero geometry, readable copy and no Middle scroll; if those constraints cannot coexist, record `FAIL_CONSTRAINT` with actual measurements. Do not hide content, globally shrink text, or weaken a minimum to manufacture a pass.

The record-next-catch CTA is static text under the existing interaction contract. Measure the camera artwork separately from its touch target using the values and bounds in the machine contract. Keep required species, measurements and time readable, and apply system font scaling once.

## Frozen anchors and M05

Keep the original whole-page visual and all five source PNGs unchanged. Their raster pixels are not Android dp values. M05 remains a static width-only anchor. If the Android task probes that width boundary, it must record a separate explicit runtime height; that result applies only to that test pair and does not infer or redefine an M05 anchor height.

## Freeze and runtime evidence

The candidate passed the registered exact-source browser matrix, isolated deployment and live-route verification on source `e6df12c74d971ce6ed9e465d33689b95a931d9cc` in [Design Manager Pages run 40302990272](https://github.com/pan277942135/Yujian_App/actions/runs/40302990272). This freeze-status commit must pass fresh required checks on its own exact SHA before merge.

A frozen responsive specification does not establish Android runtime acceptance. Android remains `NOT_VERIFIED` until the registered exact-SHA Android build, unit/static checks, measured device matrix, screenshots/logs and APK provenance all pass. Illustrative fish media may fail to load without blocking geometry acceptance, provided the layout-safe fallback remains in place.
