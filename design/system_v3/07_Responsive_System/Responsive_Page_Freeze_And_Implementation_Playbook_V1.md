# Responsive Page Freeze and Implementation Playbook V1

**Status:** ACTIVE_PROCESS_GUIDE  
**Scope:** Repeatable responsive-specification freeze, preview validation, native implementation, and runtime acceptance for registered YuJian pages.  
**First case study:** NH01 / `normal_home_v1`.  
**Authority boundary:** This playbook defines the process and evidence discipline. Each page's approved machine-readable responsive contract owns page-specific numeric values and constraints. This document does not itself freeze any page contract.

## 1. Purpose

Use NH01 as a process reference, not as a page template to copy literally. Other pages reuse the stages, evidence requirements, failure semantics, and validation protocol; they define their own visual invariants, layout regions, component geometry, content priorities, states, and runtime sources.

The goal is to prevent these recurring failures:
- multiple competing sources for the same responsive values;
- screenshot coordinates copied into production layout code;
- a passing browser preview being misreported as native runtime acceptance;
- tests passing because assertions were weakened rather than because the layout was fixed;
- long-running Work sessions losing their progress or repeating completed steps.

## 2. The three-layer model

### Layer A — Shared responsive system

Owns coordinate-space definitions, schema conventions, safe-area policy, typography scaling principles, constraint priority, failure states, evidence format, and common test profiles.

### Layer B — Page-owned responsive contract

Owns exactly one page's route, visual anchors, semantic invariants, layout regions, component-specific ratios and bounds, spacing functions, text priorities, state variants, overflow policy, implementation sources, and page-specific acceptance matrix.

### Layer C — Platform adapters

Browser preview and native Android/iOS implementations consume the same page-owned contract. Platform-specific measurement mechanics may differ, but adapters must not invent a second set of responsive numeric values or contradict the contract.

## 3. End-to-end freeze and implementation lifecycle

### Stage 0 — Admission and scope lock

1. Read canonical `AGENTS.md`, `CONTROL_PLANE_REGISTRY.yaml`, the six registered control-plane layers, and the current branch-local execution state.
2. Run the repository's required Admission check before implementation or design-consumption changes.
3. Identify the exact page ID, route, current Main SHA, implementation entrypoint, current PR/branch, and explicit in-scope/out-of-scope states.
4. Record a durable checkpoint with Task ID, SHA, current state, and next action.

**Gate:** Admission PASS; scope and implementation route are unambiguous. Otherwise stop before source changes.

### Stage 1 — Authority and asset audit

Create a page authority crosswalk that identifies:
- original visual master and its SHA-256;
- static visual anchors and their source paths, canvas pixel sizes, logical viewport meanings, and SHA-256 values;
- existing page behavior/state/data/interaction/component/resource contracts;
- current responsive contract(s), legacy contracts, and any conflicting values;
- existing browser preview route and native implementation source files.

Do not infer logical dp from PNG dimensions or browser CSS pixels. A width-only reference does not imply a runtime height. Preserve frozen assets unchanged.

**Gate:** Every required reference resolves, provenance is verified, and each authority conflict has a scoped decision.

### Stage 2 — Separate invariants from adaptive variables

For the page, explicitly classify:

- **Invariants:** semantic order, page states, data truth, component identity, primary action meaning, required content, approved asset treatment, touch/safety requirements.
- **Adaptive variables:** bounded component dimensions, spacing, wrapping, alignment, visible neighbor exposure, and declared local scroll/reflow behavior.

Use frozen design images to judge hierarchy, proportions, relationships, and overall balance—not as absolute pixel-coordinate blueprints.

**Gate:** Every adaptive variable has a named rule; no required content or behavior is left to Work's interpretation.

### Stage 3 — Define one executable machine contract

Create one page-owned machine-readable contract validated by the shared schema. It must include, as applicable:

- page ID, version, lifecycle status, route, and implementation sources;
- authority references and immutable asset provenance;
- actual runtime inputs: safe width/height, insets and their owner, font scale, parent constraints, content/state, measured text, and asset ratios;
- named layout regions and logical allocation groups;
- component aspect ratios, minimum/target/maximum bounds, spacing curves, and thresholds with rationale;
- content priority, wrapping, truncation/omission rules, and text measurement expectations;
- safe-area, touch-target, overflow, scroll, and constraint-conflict behavior;
- required anchor, interpolation, threshold-boundary, state, and font-scale test profiles;
- explicit distinction between design-preview status and native runtime status.

The Markdown explanation may clarify the machine contract, but it must not maintain a second numeric source.

**Gate:** Schema validation passes; there is one numeric authority; no viewport-keyed full-layout overrides; all constraints have deterministic outcomes.

### Stage 4 — Resolve conflicts before freeze

Do not defer contradictions to implementation. Check combinations of minimum width, aspect ratio, readable height, text readability, no-scroll requirements, touch bounds, and safe-area constraints.

If requirements cannot be satisfied simultaneously, report a named `SPEC_CONFLICT` / `FAIL_CONSTRAINT`, propose a product decision, and keep the contract proposed until resolved. Never silently weaken a requirement to obtain PASS.

**Gate:** All hard-constraint combinations are executable and consistent, or explicitly blocked by a recorded decision.

### Stage 5 — Integrate the existing preview

The existing registered Design Manager route must consume the page's machine contract directly.

- Reuse the existing workspace, route, component hierarchy, and state handling.
- Do not create a second preview shell or independent responsive calculator.
- Treat generated/illustrative media as replaceable presentation assets; missing media must not alter geometry or block layout validation.
- Expose actual component bounds, resolved parameters, contract version, and source build marker for verification.

**Gate:** Direct route loads, contract binding is demonstrable, and the preview reports the exact contract/build identity.

### Stage 6 — Automated browser and geometry validation

For the exact candidate SHA, validate:
- all frozen anchor viewports;
- non-anchor/interpolation viewports;
- every declared responsive threshold and its adjacent probes;
- system-font scale variants required by the page;
- all required page states and content variants;
- measured component bounds, ratios, gaps, overlap, text lines, touch bounds, and safe-area containment;
- missing/failed media fallback and geometry stability;
- direct route, resource loading, browser errors, and refresh/history behavior where applicable.

Screenshots are evidence, not the only oracle. Use DOM/layout measurements and contract assertions. If screenshot tooling has a rounding issue, fix the test tool with narrowly scoped, auditable normalization; preserve originals and do not relax layout requirements.

**Gate:** Required matrix passes on one exact source SHA. Every skipped or unrun case remains explicitly NOT_RUN/NOT_VERIFIED.

### Stage 7 — Candidate deployment and human visual acceptance

Deploy an isolated candidate preview, never silently overwrite production. Verify:
- HTTP 200 and normal rendering;
- contract version and source/build SHA match;
- required resources load;
- the target route works when opened directly;
- no new console errors or failed requests;
- evidence artifacts are accessible and bound to the same SHA.

The product owner reviews structural hierarchy, ratios, spacing, text readability, and representative short/standard/long screens. Pixel-difference images may diagnose issues but do not automatically determine acceptance unless the contract explicitly defines pixel targets.

**Gate:** Exact-source preview evidence is complete and the product owner records ACCEPT or a concrete list of changes.

### Stage 8 — Freeze and canonical synchronization

Only after the previous gates pass:
1. update the page contract lifecycle status to `SPEC_FROZEN`;
2. synchronize the page authority map, shared registries, Feature Matrix, Phase/Task State Machine, Unified Contract Registry, and gate bindings as required;
3. run canonical Admission, schema/provenance checks, browser gates, and registered freeze gates on the exact PR HEAD;
4. merge through the repository's normal PR path after all required checks pass;
5. record the merged Main SHA and freeze evidence.

A candidate preview, a green unit test, or a successful build alone is not a freeze.

### Stage 9 — Native implementation and runtime acceptance

After the contract is frozen, implement it in the existing native page and shared layout policy. Reuse the existing route, state, data, components, and navigation.

Validate native runtime independently:
- exact source SHA and build identity;
- relevant unit and static checks;
- real device or reproducible emulator runtime;
- the page's anchor/interpolation/boundary/font-scale/state matrix;
- actual safe bounds, measured component rectangles, text line ranges, overlap/touch checks, and scroll behavior;
- screenshot/log evidence and artifact hash.

Build PASS is not Runtime PASS. Browser preview PASS is not Runtime PASS. If runtime evidence is missing, status remains `NOT_VERIFIED`.

## 4. Required evidence record

Every gate result must include:
- page ID and contract version;
- exact source SHA;
- command/workflow and Run ID;
- result: `PASS`, `FAIL`, `BLOCKED`, `NOT_RUN`, or `NOT_VERIFIED`;
- report/artifact link;
- failure root cause and next action when not PASS.

A status cannot be marked PASS unless the evidence is bound to the exact SHA being claimed.

## 5. Reusable page rollout

For each new page, copy the process and checklist, not NH01's numeric rules.

Replace at least:
- page ID, route, implementation source, and included/excluded states;
- visual anchors and verified asset hashes;
- logical regions and semantic hierarchy;
- page-specific component invariants and adaptive variables;
- layout formulas, thresholds, content priority, and failure behavior;
- required state/view/font/boundary matrix;
- existing preview integration point and native runtime entrypoint.

Keep shared:
- coordinate-space and inset principles;
- single-machine-authority rule;
- conflict-first freeze discipline;
- exact-SHA evidence provenance;
- candidate deployment and human acceptance sequence;
- distinction between preview/build/runtime status;
- checkpoint and interruption-recovery protocol.

**Important schema readiness note:** the current NH01 V2 validator and its current contract include page-specific assertions (for example, the 247:293 Hero ratio, M01 320×480 constraints, six registered visual anchors, and A/B/C allocation). Do not reuse the NH01-specific validator or copy those constraints to another page. Before claiming fully automated batch conformance for heterogeneous pages, the shared schema/validator must be separated into a generic contract envelope plus page-specific conformance profiles. Until that is done, reuse the process and checklist, and add a page-specific validator/profile for each page.

## 6. NH01 as the first case study

NH01 demonstrated these reusable practices:
- five frozen design images plus the original master were treated as visual anchors, not per-viewport layout blueprints;
- a single V2 machine contract became the intended source of responsive numeric values;
- the existing Design Manager route consumed the contract rather than creating a parallel workbench;
- generated illustrative imagery was decoupled from geometry;
- an eight-viewport × three-font-scale Chromium matrix produced 24 cases;
- a screenshot comparator's one-pixel boundary-rounding issue was fixed in the test tool, preserving raw evidence and strict width/other-size checks;
- the PR candidate was deployed with exact-source identity and browser/resource evidence.

At the time this guide was authored, NH01's V2 contract is still recorded as `PROPOSED_FOR_CANONICAL_MAIN_REVIEW` on the candidate branch, PR #191 is open/draft/unmerged, and Android Runtime is `NOT_VERIFIED`. These facts must not be rewritten as formal freeze or native acceptance until the required gates and actual runtime evidence are complete.

## 7. Definition of reusable success

The process is reusable when a new page can:
1. instantiate its own contract from the shared structure;
2. validate authority, schema, provenance, constraints, and matrix completeness;
3. integrate into its existing preview route;
4. produce exact-SHA browser evidence and human acceptance;
5. freeze through canonical gates;
6. implement and verify natively without inventing a second responsive policy.

Do not declare batch automation complete until the generic schema/validator gap described above is closed and exercised against at least two materially different page contracts.
