# Design Manager Shared Page-Layer Workspace V1

Status: **Proposed for canonical `main` review**  
Scope: the Design Manager's reusable inspection workspace. This contract does not create a new product page, change a page's frozen visual authority, or make browser preview equivalent to Android acceptance.

## 1. Registry ownership

- `design/registry/experience_registry_v1.json` remains the only global product-page registry and remains authoritative for page IDs, names, states, and page-owned design references.
- `design/manager/workspace_registry.json` remains the Design Manager route/render registry. It may point to a registered `page_id` and a page-owned layer mapping; it must not become a second product-page registry.
- A page's layer definition and mappings live with that page's existing design authority. A test fixture may be supplied only by a test and must not be registered as a product page.
- The shared shell resolves page names and status from the existing registries. It must not contain NH01-specific IDs, titles, labels, or selectors.

## 2. Shared shell and page-owned adapter

The shared shell owns only:

- the layer tree;
- the central preview surface;
- the selected-layer property view;
- preview controls required by the registered workspace, such as viewport selection and zoom.

Each page-owned adapter declares the actual layer IDs and parent relationships, labels, selectors or stable targets, preview source, available properties, component/asset bindings, authority references, supported viewport profiles, and page-specific acceptance checks. An adapter reuses the page's existing preview, assets, contracts, and interactions; it does not duplicate those implementations in the shell.

The shell renders only declared layers that resolve to real preview elements. It reports only properties measured from the selected element or explicitly bound to an existing page authority. Missing targets, values, bindings, or authority stay visibly missing/blocked and are never filled with invented data.

## 3. Resource, authority, and state references

- Page and workspace entries reference existing source files; they do not copy registered assets or create parallel authority files.
- Asset identity and integrity come from the page's existing asset manifest. Structure and implementation mappings cite their existing page-owned snapshots/contracts. Each reference keeps its repository path and exact digest where the owning manifest provides one.
- Workspace preview state, visual review state, asset provenance, and Android Runtime state remain separate. Existing status meanings such as `PREVIEW_ONLY`, `REQUIRED`, `NOT_RUN`, `BLOCKED`, and `PASS` are not promoted by a browser-only result.
- The generic shell displays the status supplied by the page/workspace configuration. Page-specific acceptance remains owned by that page's contract.

## 4. Navigation and history

- Entering a workspace is one parent Design Manager route transition. Its hash identifies the registered page and workspace view; direct opening and refresh must resolve that route.
- Switching views inside an already-open workspace replaces the current parent view state; it does not push another parent history entry.
- The preview keeps one stable same-origin iframe document while switching internal views through the existing page adapter, or uses `location.replace()` when a document replacement is unavoidable. It must not add competing iframe history entries.
- One browser Back after an internal view switch returns to the page or route that preceded workspace entry. Forward re-enters the workspace. Direct route, refresh, Back, and Forward preserve the selected registered view.

## 5. NH01 sample and shell-independence fixture

- NH01 is the first page-owned adapter and continues to reference the existing Gemini Native preview, structure snapshot, implementation contract, asset manifest, and evidence. Its existing frozen design and asset authorities remain unchanged.
- A minimal non-NH01 test fixture uses a different page ID, title, layer IDs, selectors, and preview source. It verifies that the same shell renders and selects its declared layers without relying on NH01-specific identifiers. The fixture is test-only and is not added to the product page registry.
- This V1 closes only the NH01 sample and the shell-independence fixture. Other product pages can opt in later through their own page-owned mappings.

## 6. Acceptance evidence

The NH01 sample is complete only when exact-source browser evidence shows:

1. the existing registered home page opens the workspace and renders its declared real layer tree, preview, and properties;
2. the non-NH01 fixture uses the same shell with its own page mapping;
3. structured high-fidelity and responsive views switch without adding parent/iframe history conflicts;
4. one Back, Forward, refresh, and direct workspace route behave as specified;
5. current asset, navigation, viewport, and Design Governance checks remain valid;
6. Android Runtime status is recorded separately and remains `NOT_RUN` unless an Android runtime gate actually ran.

Evidence is recorded against the exact source SHA and does not change page freeze state, asset authority, or Android release gates.
