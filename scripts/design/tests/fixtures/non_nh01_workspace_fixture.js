(() => {
  "use strict";
  const pageId = "test_non_nh01_workspace_v1";
  const sourceSha = new URLSearchParams(location.search).get("source") || "0000000000000000000000000000000000000000";
  const missingNode = new URLSearchParams(location.search).has("missing-node");
  const root = document.querySelector("#fixture-workspace");
  const fixtureAuthority = {
    page_id: pageId,
    source_sha: sourceSha,
    profile: { profile_id: "TEST_ONLY_NEUTRAL_FIXTURE", status: "TEST_FIXTURE_ONLY" },
    envelope: { contract_id: "TEST_ONLY_NON_NH01_FIXTURE", contract_version: "1.0" },
    binding: { binding_id: "TEST_ONLY_NON_NH01_FIXTURE_RESOURCES", asset_bindings: [] }
  };
  const fixtureRef = { path: "scripts/design/tests/fixtures/non_nh01_workspace_fixture.js", status: "TEST_ONLY" };
  const resourceRef = {
    asset_id: "test-only-missing-resource",
    source_path: "scripts/design/tests/fixtures/__missing_resource__.png",
    sha256: "0".repeat(64),
    status: "NOT_VERIFIED",
    designation: "TEST_FIXTURE_ONLY"
  };
  const componentTree = {
    nodeId: "fixture-root",
    pageId,
    parentId: null,
    componentType: "page_root",
    renderedDomId: "fixture-root-dom",
    authorityRef: fixtureRef,
    layoutRuleRef: { rule_id: "NEUTRAL_FIXTURE_CONTAINER_LAYOUT" },
    state: "DEFAULT",
    visible: true,
    selectable: true,
    measurable: true,
    label: "Fixture Page Root",
    children: [
      {
        nodeId: "fixture-header",
        pageId,
        parentId: "fixture-root",
        componentType: "header",
        renderedDomId: "fixture-header-dom",
        authorityRef: fixtureRef,
        layoutRuleRef: { rule_id: "HEADER_FULL_WIDTH" },
        state: "DEFAULT",
        visible: true,
        selectable: true,
        measurable: true,
        label: "Header",
        children: []
      },
      {
        nodeId: "fixture-primary-region",
        pageId,
        parentId: "fixture-root",
        componentType: "content_region",
        renderedDomId: "fixture-primary-dom",
        authorityRef: fixtureRef,
        layoutRuleRef: { rule_id: "TWO_COLUMN_CONTENT_REGION" },
        state: "DEFAULT",
        visible: true,
        selectable: true,
        measurable: true,
        label: "Primary content region",
        children: []
      },
      {
        nodeId: "fixture-secondary-region",
        pageId,
        parentId: "fixture-root",
        componentType: "content_region",
        renderedDomId: "fixture-secondary-dom",
        authorityRef: fixtureRef,
        layoutRuleRef: { rule_id: "TWO_COLUMN_CONTENT_REGION" },
        state: "DEFAULT",
        visible: true,
        selectable: true,
        measurable: true,
        label: "Secondary content region",
        children: []
      },
      {
        nodeId: "fixture-action",
        pageId,
        parentId: "fixture-root",
        componentType: "button",
        renderedDomId: "fixture-action-dom",
        authorityRef: fixtureRef,
        layoutRuleRef: { rule_id: "INTERACTIVE_BUTTON" },
        state: "IDLE",
        visible: true,
        selectable: true,
        measurable: true,
        label: "Interactive action",
        stateProfiles: { IDLE: "IDLE", EXPANDED: "EXPANDED" },
        children: []
      },
      {
        nodeId: "fixture-error-resource",
        pageId,
        parentId: "fixture-root",
        componentType: "image_resource_preview",
        renderedDomId: "fixture-resource-dom",
        authorityRef: fixtureRef,
        resourceRef,
        layoutRuleRef: { rule_id: "RESOURCE_ERROR_DISCLOSURE" },
        state: "RESOURCE_ERROR",
        visible: true,
        selectable: true,
        measurable: true,
        label: "Missing resource · explicit error state",
        children: []
      }
    ]
  };

  let adapter;
  const renderer = ({ canvas, viewport, fontScale, state }) => {
    const longCopy = state === "EXPANDED"
      ? "A deliberately longer paragraph exercises text wrapping and measured height after font scaling. This neutral fixture validates shared workspace behavior only."
      : "Short copy.";
    const secondary = missingNode ? "" : `<section id="fixture-secondary-dom" data-workspace-rendered-node="fixture-secondary-region" class="fixture-section"><h3>Independent secondary region</h3><p>Short note.</p></section>`;
    canvas.innerHTML = `<main id="fixture-root-dom" data-workspace-rendered-node="fixture-root" class="fixture-page" data-fixture-state="${state}" style="width:${viewport.width}px;min-height:${viewport.height}px;font-size:${fontScale}em">
      <header id="fixture-header-dom" data-workspace-rendered-node="fixture-header" class="fixture-header"><h2>Neutral page header</h2><p>Different structure from the NH01 Home renderer.</p></header>
      <div class="fixture-grid"><section id="fixture-primary-dom" data-workspace-rendered-node="fixture-primary-region" class="fixture-section"><h3>Primary content region</h3><p class="fixture-copy" id="fixture-copy">${longCopy}</p></section>${secondary}</div>
      <button id="fixture-action-dom" data-workspace-rendered-node="fixture-action" class="fixture-action" type="button">${state === "EXPANDED" ? "Expanded action" : "Activate fixture"}</button>
      <figure id="fixture-resource-dom" data-workspace-rendered-node="fixture-error-resource" data-resource-status="LOADING" class="fixture-resource"><img src="/__missing_fixture_resource__.png" alt="Expected missing test fixture resource"><figcaption><span class="fixture-status">Resource status: <b data-resource-status-label>LOADING</b></span><div class="fixture-error">This error state is intentionally contained in the test fixture.</div></figcaption></figure>
    </main>`;
    const image = canvas.querySelector("img");
    image.addEventListener("error", () => {
      const resource = canvas.querySelector("#fixture-resource-dom");
      resource.dataset.resourceStatus = "ERROR";
      resource.querySelector("[data-resource-status-label]").textContent = "ERROR";
    }, { once: true });
    canvas.querySelector("#fixture-action-dom").addEventListener("click", () => {
      adapter.renderPreview({ width: viewport.width, height: viewport.height, unit: viewport.unit }, fontScale, "EXPANDED");
    });
  };

  const mountPromise = window.PageWorkspaceAdapters.mount(root, {
    pageId,
    sourceSha,
    displayName: "Neutral non-NH01 DOM fixture · test only",
    coordinateUnit: "css_px",
    authorityLoader: async sha => {
      if (sha !== sourceSha) throw new Error("FIXTURE_SOURCE_SHA_MISMATCH");
      return fixtureAuthority;
    },
    componentTree,
    renderer,
    viewportProfiles: [
      { width: 1000, height: 760, unit: "css_px" },
      { width: 390, height: 844, unit: "css_px" }
    ],
    fontScaleProfiles: [1, 1.3],
    stateProfiles: ["IDLE", "EXPANDED"]
  });
  adapter = window.PageWorkspaceAdapters.active;
  window.NonNh01WorkspaceFixture = { pageId, adapter, sourceSha };
  mountPromise.then(() => {
    root.dataset.fixtureStatus = "READY";
    root.dataset.fixtureScope = "TEST_ONLY_NOT_EMPTY_HOME_OR_LOGIN";
  }).catch(error => {
    root.dataset.fixtureStatus = "FAIL";
    root.dataset.fixtureError = error instanceof Error ? error.message : String(error);
  });
})();
