# Responsive Layout Standard V1

**Status:** PROPOSED_FOR_CANONICAL_MAIN_REVIEW  
**Scope:** Shared responsive-layout rules for registered YuJian product pages  
**Machine contract schema:** `design/system_v3/07_Responsive_System/Responsive_Layout_Contract_Schema_V1.json`  
**Viewport profiles:** `design/system_v3/07_Responsive_System/Viewport_Profiles_V1.json`

## 1. Purpose and authority

This standard defines how one approved page visual and one page-owned machine contract produce responsive layouts. It does not replace page-specific visual, state, component, asset, interaction, motion, or accessibility authorities. Each page contract must identify which authority owns each domain.

A page's machine-readable responsive contract is the only source of its responsive numeric parameters and layout rules. Its Markdown guide explains the contract but must not independently maintain a second set of values. Preview screenshots and runtime screenshots are evidence produced by the contract; they are not new layout authorities.

## 2. One rule set, not one layout per screenshot

- Use the real app-window constraints available to the page, not the physical device model or the size of a reference screenshot.
- Express logical layout dimensions in dp and text sizes in sp. Keep source-raster pixels, browser CSS pixels, device pixels, and logical dp explicitly distinguished.
- Apply system insets once. Calculate the safe content width and height after the agreed inset owner has consumed them.
- Feed safe width, safe height, system font scale, content/state, component intrinsic measurements, and asset constraints into one deterministic responsive policy.
- A viewport profile is a test input, not a key that selects a separate page layout. Do not create a `320px layout`, `360px layout`, or six copied implementations.
- A named piecewise threshold is allowed only when it represents an explicit constraint or behavior change, has a documented reason, and has boundary tests. It must not hide a collection of per-screenshot overrides.
- Scaling functions must declare their input, minimum, preferred target, maximum, interpolation/clamp behavior, and monotonicity expectations. Any exception must be explicit and bounded.

## 3. Page visual invariants

Each page contract must separate invariants from adaptive variables.

**Invariants** normally include brand identity, content hierarchy, semantic order, component relationships, approved assets, action meaning, data priority, and required touch/safety behavior.

**Adaptive variables** may include component width/height within declared bounds, spacing, text wrapping, visible neighboring content, alignment, and the permitted scroll/reflow mode. Every variable must be controlled by a named rule.

A responsive result may differ geometrically from the master visual only when the page contract explains the difference. Work may not independently redesign, hide, duplicate, reorder, or invent product content to make a screenshot look balanced.

## 4. Constraint priority and resolution

When constraints conflict, apply this order:

1. **P0 — Safety and access:** safe drawing bounds, non-overlapping touch targets, readable required content, reachable primary actions, and platform accessibility requirements.
2. **P1 — Product meaning:** page state, data truth, content hierarchy, component identity, and interaction semantics.
3. **P2 — Primary visual structure:** brand relationships, primary component proportions, approved media treatment, and primary content visibility.
4. **P3 — Layout geometry:** alignment, card exposure, spacing, and section distribution within their declared bounds.
5. **P4 — Decorative space:** nonessential whitespace and non-semantic visual flourishes.

Resolve space pressure in this order: compress nonessential spacing; allow only contract-approved local wrapping/reflow; use the declared local scroll fallback; otherwise report a constraint failure. Do not globally shrink text/cards, double-apply insets, hide required content, overlap actions, or silently invent a new layout mode.

## 5. Typography and content

- Declare semantic text roles, base-size formula, minimum/maximum sizes, line-height, allowed line count, wrapping/ellipsis behavior, and orphan-character rules.
- System font scaling must be applied exactly once by the platform text system. It may also inform measured layout pressure, but must not be multiplied into the same text style a second time.
- Prefer measured text layout and actual line ranges to a boolean overflow flag alone.
- When metadata is constrained, preserve higher-priority values and units. A page contract must state what may be omitted, shortened, wrapped, or ellipsized and in what order.
- A content value must never be fabricated to fill space.

## 6. Media, assets, and safe areas

- Every visual asset must resolve through its registered manifest or explicit page-owned authority and retain source provenance.
- A reference image's canvas dimensions do not define a logical viewport. A width-only reference does not imply an Android window height.
- Crop/Fit behavior, aspect ratios, backdrop composition, crop-safety signals, and fallback behavior must be explicit.
- The background may be edge-to-edge while interactive content remains within safe bounds. The contract must identify the inset owner and prohibit double application.

## 7. Required failure states

Page contracts must define machine-readable outcomes for at least:
- `PASS`: all required assertions ran and passed on the claimed source SHA.
- `FAIL_CONSTRAINT`: a measured layout constraint is violated.
- `SAFE_OVERFLOW`: the declared safe fallback is active; it is not an automatic visual pass.
- `UNSUPPORTED_WINDOW`: the real window cannot support required controls/content under the contract.
- `NOT_RUN` / `NOT_VERIFIED`: evidence is absent.
- `BLOCKED_INFRA`: an actual execution attempt was blocked by infrastructure and evidence exists.

A frozen specification is not runtime acceptance. Browser preview, static-image integrity, Android build, unit tests, and device runtime remain distinct evidence domains.

## 8. Preview and acceptance model

The shared viewport profile list defines standard preview/test inputs. A page may add a page-specific boundary case only with an explicit purpose and without redefining the global profiles. Tests must include the named anchor viewports, at least one non-anchor/interpolated viewport, and probes around every responsive threshold.

For each viewport, record the exact contract version, source SHA, logical dimensions, safe dimensions/insets, font scale, selected declared mode, component bounds, text-layout results, overlap/touch checks, and screenshot evidence. Compare like with like: visible pixels to visible pixels, containers to containers, and touch bounds to touch bounds.

The same page contract must be usable by Design Manager preview and native implementation. Platform adapters may differ in measurement mechanics, but may not invent independent responsive values or contradict the page contract.

## 9. Implementation guidance

For Jetpack Compose, use actual parent constraints and measured content to drive layout; centralize responsive policy and pass resolved values into page components. Do not scatter device-width checks across components. Use a declared mode switch only when the contract explicitly defines one.

Reference: [Compose layout basics](https://developer.android.com/develop/ui/compose/layouts/basics), [Support different display sizes](https://developer.android.com/develop/adaptive-apps/guides/support-different-display-sizes).

## 10. Freeze criteria

A responsive contract is ready to freeze only when:
- the schema validates the machine contract;
- every referenced authority and asset exists and has verified provenance;
- all authority conflicts have an explicit scoped decision;
- there is no per-viewport full-layout override;
- generated previews meet invariant and constraint assertions at the anchor and non-anchor profiles;
- responsive thresholds have boundary tests;
- the Feature Matrix, Phase/Task State Machine, Unified Contract Registry, page authority map, and acceptance gate point to the same contract;
- runtime status remains separate and truthful.

This standard is a candidate until the control-plane change is admitted and merged to canonical `main`.
