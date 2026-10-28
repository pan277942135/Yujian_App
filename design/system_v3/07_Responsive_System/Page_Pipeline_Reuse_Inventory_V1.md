# Page Pipeline Reuse Inventory V1

**Task:** YUJIAN_PAGE_FREEZE_PIPELINE_W0_GOVERNANCE_V1 (Issue #219)  
**Epic:** #218  
**Audit baseline:** Main 7aa7fb5e93d0b61b3d9598306dbb4c92f225400d; PR #191 is merged; PR #216 was open at HEAD b6e0e54f8260c7e5c855282338ae057f64810197 when reviewed.  
**Scope:** governance and reuse boundaries only. This inventory does not implement W1–W6 page-pipeline functions.

## 1. Classification rules

- **SHARED** means page-neutral behavior or a governance contract that can serve every registered page.
- **PAGE_SPECIFIC** means the values, composition, states, renderer, or assertions are owned by one page and must not become mandatory fields for other pages.
- **MIXED** means a reusable shell or validator is coupled to a page implementation and needs extraction or an adapter boundary.
- **DIRECT_REUSE** uses the existing common implementation as it stands.
- **EXTRACT** separates reusable behavior from existing page-specific behavior.
- **ADAPTER** connects a page-owned source or renderer to a shared interface without copying its implementation.

## 2. Contract field boundary

| Field or field group | Classification | Existing source and observed form | Owner and next responsibility |
|---|---|---|---|
| Page identity, route, implementation entrypoints | SHARED envelope fields | Experience Registry records page/feature identity; Feature Matrix owns canonical route mapping. | W1 defines envelope names; W3 resolves a registered page through its adapter. |
| Contract ID, version, lifecycle status | SHARED envelope fields | NH01 V2 has contract_id, contract_version, status, page_id; Main records SPEC_FROZEN. | W1 defines generic syntax and lifecycle semantics. A page's status remains page-owned evidence. |
| Authority references and provenance | SHARED envelope fields with page-owned values | NH01 authority references the global standard, schema, viewport profiles, master art, page static archive, and Kotlin entrypoints. | W1 defines reference shape; W2 defines asset provenance and SHA verification. |
| Safe-area and coordinate-space inputs | SHARED envelope fields | Responsive standard and NH01 preview inputs distinguish logical dp, CSS pixels, safe insets, and inset-consumption owner. | W1 defines the neutral fields; each page supplies its actual coordinate and inset rules. |
| Viewport and font-scale inputs | SHARED input shape; PAGE_SPECIFIC profile values | Viewport_Profiles_V1.json contains NH01 anchors and probes; NH01 acceptance runs the 4-phone × 3-font 24-case matrix plus scoped cases. | W1 defines profile shape; W4 executes each page's matrix through its adapter. Do not copy NH01 viewport values as universal defaults. |
| Validation status, acceptance, evidence binding | SHARED process fields | The page checklist has gate results, exact source SHA, run/command, report/artifact, owner decision, Main SHA, and native runtime state. | W0 owns G0–G8 process baseline; W4 owns generic evidence manifest and runner results. |
| Layout regions and logical allocation | PAGE_SPECIFIC profile fields | NH01 contract has layout_regions background/safe-content/top/middle/footer and logical_allocation bands A/B/C with S/2 gap allocation. | W1 keeps optional page_conformance_profile content outside the required envelope; W5/W6 supply their own profiles. |
| Hero, pager, neighbor-peek, fish content/state | PAGE_SPECIFIC profile fields | NH01 owns the 247:293 Hero ratio, Card-Main-Active/Card-Peek-Left-Right, FishCatchPager/Neighbor Peek behavior, record content, and its status states. | Never make these required generic fields. W1 defines the extension boundary; W3 page renderer/adapters consume page-owned data. |
| Frozen visual anchors | PAGE_SPECIFIC binding; SHARED verification shape | NH01 has six frozen anchor records and hashes; the registry and manifest identify asset paths and lifecycle. | W2 binds source/path/hash/state; W4 compares each page's own anchors. Do not copy or regenerate frozen images. |
| Component and geometry assertions | MIXED | Generic bounds, clipping, overlap, text, touch, and safe-area checks coexist with NH01-specific DOM selectors and assertions. | W4 extracts the reusable assertion interface; the NH01 assertions stay in the NH01 adapter. |

## 3. Capability inventory

| Capability ID | Existing Source | Current Owner | Classification | Reuse Strategy | Target Task | Modification Boundary | Dependencies | Validation Gate | Migration Risk |
|---|---|---|---|---|---|---|---|---|---|
| PAGE-REGISTRATION | design/registry/experience_registry_v1.json; harness/FEATURE_MATRIX.yaml | Experience Registry and Feature Matrix | MIXED | ADAPTER | W1, W3 | W1 names neutral page identity and route fields; W3 resolves existing records. Do not widen the 15-feature product matrix for tooling. | W0 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT, G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE | Route/feature ownership can drift if copied into a second registry. |
| CONTRACT-ENVELOPE | design/system_v3/07_Responsive_System/Responsive_Layout_Standard_V1.md; Responsive_Layout_Contract_Schema_V1.json; NH01_RESPONSIVE_LAYOUT_V2.json | Responsive System and NH01 page contract | MIXED | EXTRACT | W1 | Extract only identity, version/status, authority, coordinate, safe-area, inputs, validation, and evidence envelope. Keep NH01 regions, numeric layout, components, and states out of generic required fields. | W0 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT | A required-field leak would make other page contracts inherit NH01 semantics. |
| PAGE-CONFORMANCE-PROFILE | NH01_RESPONSIVE_LAYOUT_V2.json: layout_regions, logical_allocation, components, typography, spacing, overflow_policy, native_acceptance | NH01 / normal_home_v1 | PAGE_SPECIFIC | ADAPTER | W1, W5, W6 | Define the extension point only; W0 does not change the NH01 contract. Each pilot authors its own profile. | W1 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT | Copying A/B/C, S/2, Hero, or fish states would create false cross-page constraints. |
| VIEWPORT-AND-FONT-INPUTS | design/system_v3/07_Responsive_System/Viewport_Profiles_V1.json; NH01 contract preview_profiles and acceptance | Responsive System plus page-owned matrices | MIXED | ADAPTER | W1, W4 | Reuse profile and font-scale input shape; retain per-page anchor, interpolation, threshold, and state values. | W1 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT, G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Treating NH01's 24 cases as a universal matrix can miss other pages' constraints. |
| AUTHORITY-AND-PROVENANCE | design/system_v3/11_Freeze_Governance/Effective_Authority_Registry_V3_1.json; harness/UNIFIED_CONTRACT_REGISTRY.yaml; page contract authority | Governance registry plus page authorities | SHARED | DIRECT_REUSE | W1, W2 | Resolve canonical path/status and preserve page-owned references. W0 does not change the frozen authority registry or page data. | W0 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT, G-PAGE-FREEZE-PIPELINE-W2-RESOURCES | Stale aliases or unrecorded conflicts can bind work to the wrong authority. |
| RESOURCE-BINDING | design/pages/home/normal_home/assets/asset_manifest.json; design/pages/home/empty_home/shared/contracts/asset_manifest.json; design/pages/account_privacy/Login/frozen/manifest.json | Individual page resource manifests | MIXED | EXTRACT | W2 | Define page + resource identity, canonical path, role, type, provenance, expected SHA-256, lifecycle, and publication permission. Manifests remain with their pages. | W0 | G-PAGE-FREEZE-PIPELINE-W2-RESOURCES | Cross-page copying can expose private assets or silently change a frozen mapping. |
| ASSET-INTEGRITY-CHECKS | scripts/verify_design_manager_hifi_release.py; scripts/verify_private_visual_references.py; manifest SHA-256 fields | Design Manager asset validation | MIXED | EXTRACT | W2, W4 | Extract generic byte/hash/path checks; keep restricted-resource allowlists and source-specific checks behind policy adapters. | W2 | G-PAGE-FREEZE-PIPELINE-W2-RESOURCES, G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Changing byte or allowlist logic may affect private references and published originals. |
| PUBLICATION-PERMISSION | design registry asset lifecycle; scripts/verify_private_visual_references.py | Design registry and private-reference guard | MIXED | ADAPTER | W2 | Add a typed permission state to each binding and enforce it at publication. Never publish private fish photos or model assets. | W2 | G-PAGE-FREEZE-PIPELINE-W2-RESOURCES | A missing or default-allow permission could publish protected materials. |
| WORKSPACE-ROUTING-SHELL | design/manager/app.js; design/registry/experience_registry_v1.json | Shared Design Manager | MIXED | DIRECT_REUSE | W3 | Reuse the current route/page registry, selection, preview shell, and build identity. Do not create a second manager or parallel design site. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE | A route regression could affect pages unrelated to the pilot. |
| NH01-WORKSPACE-RENDERER | design/manager/nh01_structured_workspace.js; design/manager/nh01_structured_workspace.css | NH01 workspace | PAGE_SPECIFIC | ADAPTER | W3 | Keep its renderer and NH01 geometry scoped. W3 wraps it through Page Workspace Adapter; no W0 renderer refactor. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE | Genericizing the renderer prematurely could alter NH01 component selection or preview geometry. |
| RESOURCE-LOADER | createResourceLoader in design/manager/nh01_structured_workspace.js | NH01 workspace resource state machine | MIXED | EXTRACT | W2, W3 | W2 specifies loader inputs/results; W3 extracts reusable fetch, integrity, timeout, retry, and error-state behavior behind an adapter. | W2 | G-PAGE-FREEZE-PIPELINE-W2-RESOURCES, G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE | Retry or stale-generation changes can cause incorrect asset display or false integrity states. |
| RESPONSIVE-DIAGNOSTICS | DOM measurement, component selection, and contract/source SHA display in design/manager/nh01_structured_workspace.js and app.js | Design Manager plus NH01 workspace | MIXED | EXTRACT | W3 | Share diagnostic result shape and shell display; page renderer supplies its own measurements and selectors. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE | NH01 selectors cannot be assumed to exist on empty-home or login. |
| NH01-CONTRACT-VALIDATOR | scripts/design/validate_nh01_responsive_layout_contract_v2.py | NH01 contract | PAGE_SPECIFIC | ADAPTER | W4 | Retain as NH01 regression validator. Call through a page adapter; do not rename it as the generic validator or weaken its checks. | W1 | G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | A renamed or loosened validator could hide NH01 regressions. |
| NH01-BROWSER-MATRIX | scripts/verify_nh01_structured_responsive_layout.mjs; scripts/verify_design_manager_browser.mjs | NH01 browser workspace and route check | MIXED | ADAPTER | W4 | Keep NH01's 24-case geometry and page assertions scoped. W4 adds a generic runner contract and invokes page-specific adapters independently. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Reusing NH01 assertions as universal tests can falsely pass or fail other pages. |
| NH01-VISUAL-DIAGNOSTIC | scripts/design/compare_nh01_workspace_visual_v1.py | NH01 frozen visual anchors | PAGE_SPECIFIC | ADAPTER | W4 | Preserve comparison and one-pixel screenshot-edge normalization semantics; raw screenshot/hash remain unchanged. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Widening normalization tolerance would conceal a real visual regression. |
| GENERIC-EVIDENCE-PIPELINE | .github/workflows/design-manager-pages.yml; exact build SHA; GitHub Actions Run ID and uploaded artifacts | Design Manager Pages CI | MIXED | EXTRACT | W4 | Emit per-page matrix, geometry JSON, screenshot, visual-diagnostic, and manifest records with source SHA, run ID, and result. Preserve isolated PR previews. | W1, W2 | G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Workflow path or artifact changes may deploy the wrong build or lose traceability. |
| G0-G8-CHECKLIST | Responsive_Page_Freeze_And_Implementation_Playbook_V1.md; Responsive_Page_Freeze_Checklist_Template_V1.json; scripts/design/validate_responsive_page_freeze_checklist.py | Shared page-freeze process | SHARED | DIRECT_REUSE | W0, W4 | W0 repairs the one checklist and validator; later tasks consume this schema and may not create a parallel checklist. | W0 | G-PAGE-FREEZE-PIPELINE-W0-GOVERNANCE, G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE | Incomplete evidence could be recorded as PASS or browser PASS mistaken for runtime PASS. |
| BOUNDED-RECOVERY | harness/PHASE_TASK_STATE_MACHINE.yaml recovery; AGENTS.md admission and failure rules; tools/work_execution_v2/failure_gate.py | Canonical Control Plane | SHARED | DIRECT_REUSE | W0–W6 | Reuse exact-SHA checkpoint and retry policy. Preserve failed evidence; classify before retry; do not change thresholds or bypass a gate. | W0 | G-PAGE-FREEZE-PIPELINE-W0-GOVERNANCE | Re-running the same failed source without classification can duplicate work or hide failures. |

## 4. W1–W4 development interfaces

The entries below are frozen inputs for later issues. They define ownership and exchange shape, not implementation.

### W1 — Generic Contract Envelope and Page Conformance Profile (#220)

- **Input:** registered page identity/route, the shared responsive standard, page-owned authority and conformance sources, plus W0's status/evidence semantics.
- **Output:** Generic Contract Envelope; Page Conformance Profile extension; generic contract validator interface and page-profile validator hook.
- **Envelope-owned fields:** contract/page IDs, route and entrypoints, version/status, authority references, coordinate spaces, safe-area/inset ownership, viewport/font inputs, validator/profile references, acceptance decision, and evidence/run/source-SHA references.
- **Profile-owned fields:** regions and allocation, component/asset identities, visual anchors, page content and behavior states, numeric layout constraints, scroll/fallback behavior, and page-specific assertions. None are envelope-required fields.
- **File ownership:** W1 may add the generic schema, profile schema, validator modules, fixtures, and tests under the shared Responsive System and scripts/design. It may not modify NH01 V2 values, the six frozen images, page component structure, or Android code.
- **Dependencies:** W0 only. **Gate:** G-PAGE-FREEZE-PIPELINE-W1-CONTRACT plus Control Plane Admission, Schema, and Design Governance on the exact W1 HEAD.

### W2 — Page Resource Binding (#221)

- **Input:** page resource manifest, frozen asset registry/authority, W0 provenance and permission fields.
- **Output:** Page Resource Binding shape and Asset Provenance / Asset Validation / Publication Permission interfaces.
- **Required binding:** page ID, resource ID, canonical path, role/media type, source authority, expected SHA-256, lifecycle state, original-versus-illustrative designation, and explicit publication permission. A missing permission is not allow.
- **File ownership:** W2 may add shared binding schema, generic validator, and tests under the asset system and scripts/design. Page manifests remain page-owned and change only as an independently authorized page mapping. W2 may not alter or regenerate any frozen image.
- **Dependencies:** W0 only. **Gate:** G-PAGE-FREEZE-PIPELINE-W2-RESOURCES plus Control Plane Admission, Schema, Design Governance, and resource SHA checks on the exact W2 HEAD.

### W3 — Page Workspace Adapter (#222)

- **Input:** W1 envelope/profile and W2 resource binding.
- **Output:** Page Workspace Adapter interface separating the existing shared shell from a page-owned renderer.
- **Shared shell owns:** page registration/routing, component-tree container, central preview frame, selection/Inspector shell, viewport and font controls, contract/source-SHA display, measurement surface, and common resource/error recovery.
- **Adapter/page renderer owns:** page component tree, page geometry and styles, state/content projection, resource-to-component binding, and page-specific responsive diagnostics.
- **File ownership:** changes remain in the existing Design Manager and its adapter modules; W3 may not create a second workspace/site or refactor NH01 geometry in W0.
- **Dependencies:** W1 + W2. **Gate:** G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE plus Design Manager Pages checks.

### W4 — Page Test Matrix and Evidence (#223)

- **Input:** W1 envelope/profile, W2 bindings, W3 adapter surface.
- **Output:** Page Test Matrix; Generic Verification Runner; Visual Diagnostic interface; Evidence Manifest; per-gate result schema.
- **Runner inputs:** exact source SHA, page adapter, anchor/interpolation/boundary viewport sets, font scales, required states, assets and expected hashes.
- **Runner outputs:** per-case status, DOM/geometry measurements, original screenshots, comparison diagnostics, source/build SHA, workflow Run ID, report/artifact URLs, failure classification, and gate result.
- **File ownership:** generic runner and evidence modules belong under scripts/design; NH01 scripts remain separate adapters/regression checks. A 1px renderer-edge normalization remains limited to the existing NH01 comparator rule.
- **Dependencies:** W1 + W2. **Gate:** G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE plus exact-SHA browser/design governance checks.

## 5. Registered execution topology

| Work | Issue | Static dependencies | Gate ID |
|---|---:|---|---|
| W0 governance baseline | #219 | — | G-PAGE-FREEZE-PIPELINE-W0-GOVERNANCE |
| W1 generic contract | #220 | W0 | G-PAGE-FREEZE-PIPELINE-W1-CONTRACT |
| W2 resource binding | #221 | W0 | G-PAGE-FREEZE-PIPELINE-W2-RESOURCES |
| W3 workspace adapter | #222 | W1, W2 | G-PAGE-FREEZE-PIPELINE-W3-WORKSPACE |
| W4 test and evidence | #223 | W1, W2 | G-PAGE-FREEZE-PIPELINE-W4-TEST-EVIDENCE |
| W5 empty-home pilot | #224 | W3, W4 | G-PAGE-FREEZE-PIPELINE-W5-EMPTY-HOME-PILOT |
| W6-A Login structured module | #237 | W3, W4 | G-PAGE-FREEZE-PIPELINE-W6A-LOGIN-MODULE |
| W6 login integration and acceptance | #225 | W5, W6-A | G-PAGE-FREEZE-PIPELINE-W6-LOGIN-PILOT |

These tasks are registered static scope, not status assertions. They have empty product feature/journey refs, remain outside phase rollups and the N001–N010 release queue, and are explicitly non-blocking for the Android release chain. Work status and exact current SHA remain in branch-local execution state and Actions evidence.

W6-A is an explicitly bounded Login-owned module task that may proceed alongside W5 after W3/W4. It does not own shared Design Manager routing or cross-page integration. W6 #225 still requires both W5 and W6-A, including the W5 user acceptance dependency, before final integration or acceptance can be recorded.

## 6. Out of scope for W0

No page migration, W1–W6 implementation, NH01 contract or frozen-image change, Android change, Design Manager refactor, public private-asset release, or candidate/production preview replacement is part of this baseline.
