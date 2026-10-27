#!/usr/bin/env node
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { createServer } from "node:http";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { extname, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { createHash } from "node:crypto";

const require = createRequire(import.meta.url);
const repoRoot = resolve(fileURLToPath(new URL("../../..", import.meta.url)));
const SHA1 = /^[0-9a-f]{40}$/;
const args = { sourceSha: process.env.BUILD_SHA || null, outputDir: resolve("/tmp/non-nh01-workspace-fixture") };
for (let index = 2; index < process.argv.length; index += 1) {
  const key = process.argv[index];
  if (key === "--source-sha") args.sourceSha = process.argv[++index];
  else if (key === "--output-dir") args.outputDir = resolve(process.argv[++index]);
  else throw new Error(`Unknown argument: ${key}`);
}
assert(SHA1.test(args.sourceSha || ""), "a full exact --source-sha is required");
await mkdir(args.outputDir, { recursive: true });

const mime = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8",
  ".png": "image/png", ".svg": "image/svg+xml"
};
const server = createServer(async (request, response) => {
  const url = new URL(request.url || "/", "http://127.0.0.1");
  const relative = decodeURIComponent(url.pathname).replace(/^\/+/, "");
  if (url.pathname === "/__missing_fixture_resource__.png") {
    response.writeHead(404, { "content-type": "text/plain" }).end("intentional fixture resource failure");
    return;
  }
  const path = resolve(repoRoot, relative);
  if (path !== repoRoot && !path.startsWith(repoRoot + sep)) {
    response.writeHead(403).end("forbidden");
    return;
  }
  try {
    const bytes = await readFile(path);
    response.writeHead(200, { "content-type": mime[extname(path).toLowerCase()] || "application/octet-stream" }).end(bytes);
  } catch {
    response.writeHead(404, { "content-type": "text/plain" }).end("not found");
  }
});

let browser;
const consoleErrors = [];
const unexpectedHttpErrors = [];
const screenshotPaths = [];
try {
  await new Promise((resolvePromise, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolvePromise);
  });
  const address = server.address();
  const origin = `http://127.0.0.1:${address.port}`;
  const { chromium } = require(process.env.PLAYWRIGHT_PATH || (process.env.PLAYWRIGHT_MODULE_DIR
    ? resolve(process.env.PLAYWRIGHT_MODULE_DIR, "playwright") : "playwright"));
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
  page.on("console", message => {
    if (message.type() === "error" && !message.text().includes("__missing_fixture_resource__.png")) consoleErrors.push(message.text());
  });
  page.on("pageerror", error => consoleErrors.push(error.message));
  page.on("response", response => {
    if (response.status() >= 400 && !new URL(response.url()).pathname.includes("__missing_fixture_resource__.png")) {
      unexpectedHttpErrors.push(`${response.status()} ${response.url()}`);
    }
  });

  const fixturePath = "/scripts/design/tests/fixtures/non_nh01_workspace_fixture.html";
  const fixtureUrl = `${origin}${fixturePath}?source=${args.sourceSha}`;
  await page.goto(fixtureUrl, { waitUntil: "domcontentloaded" });
  await page.locator("#fixture-workspace[data-fixture-status='READY']").waitFor({ state: "attached", timeout: 10000 });
  await page.locator("#fixture-resource-dom[data-resource-status='ERROR']").waitFor({ state: "visible", timeout: 10000 });

  const rootInfo = await page.locator("[data-page-workspace-root]").evaluate(element => ({
    pageId: element.dataset.pageId,
    sourceSha: element.dataset.sourceSha,
    workspaceKind: element.dataset.workspaceKind,
    fixtureScope: document.querySelector("#fixture-workspace").dataset.fixtureScope,
    viewport: element.dataset.logicalViewportUnit
  }));
  assert.equal(rootInfo.pageId, "test_non_nh01_workspace_v1");
  assert.equal(rootInfo.sourceSha, args.sourceSha);
  assert.equal(rootInfo.workspaceKind, "structured-dom-candidate");
  assert.equal(rootInfo.fixtureScope, "TEST_ONLY_NOT_EMPTY_HOME_OR_LOGIN");
  assert(!["home_normal_v1", "home_empty_v2", "auth_login_v2"].includes(rootInfo.pageId));

  const initial = await page.evaluate(async () => {
    const api = window.PageWorkspaceAdapters;
    const authority = await api.loadAuthority(document.querySelector("[data-page-workspace-root]").dataset.sourceSha);
    const tree = await api.loadComponentTree();
    return {
      authorityPage: authority.page_id,
      treePage: tree.page_id,
      treeStatus: tree.status,
      nodes: tree.nodes.map(node => ({ nodeId: node.nodeId, renderedDomId: node.renderedDomId, parentId: node.parentId, measurable: node.measurable, visible: node.visible })),
      measurements: api.measureElements(),
      diagnostics: api.getResponsiveDiagnostics(),
      inspector: api.getInspector()
    };
  });
  assert.equal(initial.authorityPage, rootInfo.pageId);
  assert.equal(initial.treePage, rootInfo.pageId);
  assert.equal(initial.treeStatus, "STRUCTURED_DOM");
  assert.equal(initial.nodes.length, 6, "fixture must include root, header, two regions, action, and failed resource");
  assert.equal(initial.measurements.length, initial.nodes.length, "every visible measurable node must have a real DOM rectangle");
  assert.equal(initial.diagnostics.native_runtime, "NOT_VERIFIED");
  assert.equal(initial.diagnostics.dom_measurement_unit, "css_px");
  assert.equal(initial.diagnostics.resources[0].status, "ERROR");
  assert.equal(initial.inspector.source_sha, args.sourceSha);

  const geometryRows = [];
  for (const node of initial.nodes) {
    if (!node.visible || !node.measurable) continue;
    const target = page.locator(`[data-workspace-rendered-node="${node.nodeId}"]`);
    assert.equal(await target.count(), 1, `${node.nodeId} must map to exactly one rendered DOM node`);
    assert.equal(await target.getAttribute("id"), node.renderedDomId);
    const treeNode = page.locator(`[data-workspace-tree-node][data-node-id="${node.nodeId}"]`);
    await treeNode.click();
    assert.equal(await page.locator("[data-workspace-inspector]").getAttribute("data-selected-node-id"), node.nodeId);
    const actual = await target.evaluate(element => {
      const rect = element.getBoundingClientRect();
      return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
    });
    assert(actual.width > 0 && actual.height > 0, `${node.nodeId} must have a non-zero actual DOM rectangle`);
    await target.evaluate(element => element.dispatchEvent(new MouseEvent("click", { bubbles: true, view: window })));
    assert.equal(await page.locator("[data-workspace-inspector]").getAttribute("data-selected-node-id"), node.nodeId,
      `clicking the actual DOM for ${node.nodeId} must reverse-select its tree layer`);
    geometryRows.push({ node_id: node.nodeId, rendered_dom_id: node.renderedDomId, parent_id: node.parentId, bounds: actual });
  }

  const rootToggle = page.locator("[data-workspace-tree-item='fixture-root'] > .page-ws-tree-row > [data-workspace-tree-toggle]");
  const rootChildren = page.locator("[data-workspace-tree-item='fixture-root'] > [data-workspace-tree-children]");
  await rootToggle.click();
  assert.equal(await rootToggle.getAttribute("aria-expanded"), "false");
  assert.equal(await rootChildren.isVisible(), false, "tree disclosure must collapse its real child layers");
  await rootToggle.click();
  assert.equal(await rootChildren.isVisible(), true, "tree disclosure must restore child layers");

  const largeLayout = await page.locator(".fixture-grid").evaluate(element => ({
    columns: getComputedStyle(element).gridTemplateColumns,
    primary: element.querySelector("#fixture-primary-dom").getBoundingClientRect().toJSON(),
    secondary: element.querySelector("#fixture-secondary-dom").getBoundingClientRect().toJSON()
  }));
  assert(largeLayout.primary.x < largeLayout.secondary.x, "wide viewport must show two independent side-by-side content regions");
  await page.screenshot({ path: resolve(args.outputDir, "non-nh01-fixture-wide.png"), fullPage: true });
  screenshotPaths.push("non-nh01-fixture-wide.png");

  const shortCopyHeight = await page.locator("#fixture-primary-dom").evaluate(element => element.getBoundingClientRect().height);
  await page.locator("#fixture-action-dom").click();
  await page.waitForFunction(() => document.querySelector("[data-page-workspace-root]")?.dataset.viewState === "EXPANDED");
  assert.equal(await page.locator("[data-workspace-tree-node][data-node-id='fixture-action']").getAttribute("data-node-state"), "EXPANDED");

  await page.locator("[data-workspace-width]").fill("390");
  await page.locator("[data-workspace-height]").fill("844");
  await page.locator("[data-workspace-apply-viewport]").click();
  await page.waitForFunction(() => document.querySelector("[data-workspace-artboard]")?.dataset.viewport === "390x844");
  const compactLayout = await page.locator(".fixture-grid").evaluate(element => ({
    columns: getComputedStyle(element).gridTemplateColumns,
    primary: element.querySelector("#fixture-primary-dom").getBoundingClientRect().toJSON(),
    secondary: element.querySelector("#fixture-secondary-dom").getBoundingClientRect().toJSON()
  }));
  assert(compactLayout.secondary.y > compactLayout.primary.y, "compact viewport must reflow content regions into a vertical layout");
  assert(compactLayout.primary.width < largeLayout.primary.width, "viewport change must update actual component width");
  const beforeFontScale = await page.locator("#fixture-copy").evaluate(element => ({
    fontSize: getComputedStyle(element).fontSize,
    height: element.getBoundingClientRect().height
  }));
  await page.locator("[data-workspace-font-scale]").selectOption("1.3");
  await page.waitForFunction(() => getComputedStyle(document.querySelector("#fixture-copy")).fontSize !== "16px");
  const afterFontScale = await page.locator("#fixture-copy").evaluate(element => ({
    fontSize: getComputedStyle(element).fontSize,
    height: element.getBoundingClientRect().height
  }));
  assert(parseFloat(afterFontScale.fontSize) > parseFloat(beforeFontScale.fontSize), "font scale must update real rendered typography");
  assert(afterFontScale.height > beforeFontScale.height, "font scale must update measured text geometry");
  assert(shortCopyHeight > 0);
  await page.screenshot({ path: resolve(args.outputDir, "non-nh01-fixture-compact-font-1_3.png"), fullPage: true });
  screenshotPaths.push("non-nh01-fixture-compact-font-1_3.png");

  const missingSelection = await page.evaluate(() => {
    try { window.PageWorkspaceAdapters.selectLayer("missing-component"); return null; }
    catch (error) { return String(error.message || error); }
  });
  assert.match(missingSelection || "", /COMPONENT_NODE_NOT_SELECTABLE/);

  await page.evaluate(() => window.PageWorkspaceAdapters.dispose());
  assert.equal(await page.locator("[data-page-workspace-root]").count(), 0, "dispose must destroy the workspace DOM");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.locator("#fixture-workspace[data-fixture-status='READY']").waitFor({ state: "attached", timeout: 10000 });
  await page.goto(`${fixtureUrl}&history=second`, { waitUntil: "domcontentloaded" });
  await page.goBack({ waitUntil: "domcontentloaded" });
  assert(new URL(page.url()).searchParams.has("source"), "browser back must restore the previous workspace route");
  await page.goForward({ waitUntil: "domcontentloaded" });
  await page.locator("#fixture-workspace[data-fixture-status='READY']").waitFor({ state: "attached", timeout: 10000 });
  assert.equal(await page.locator("[data-page-workspace-root]").getAttribute("data-source-sha"), args.sourceSha,
    "browser forward/reload must retain exact source identity");

  const missingPage = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await missingPage.goto(`${origin}${fixturePath}?source=${args.sourceSha}&missing-node=1`, { waitUntil: "domcontentloaded" });
  await missingPage.locator("#fixture-workspace[data-fixture-status='FAIL']").waitFor({ state: "attached", timeout: 10000 });
  const missingReason = await missingPage.locator("#fixture-workspace").getAttribute("data-fixture-error");
  assert.match(missingReason || "", /COMPONENT_DOM_MAPPING_MISSING: fixture-secondary-region/);
  await missingPage.close();

  assert.deepEqual(consoleErrors, [], `unexpected browser console errors: ${consoleErrors.join(" | ")}`);
  assert.deepEqual(unexpectedHttpErrors, [], `unexpected same-origin HTTP failures: ${unexpectedHttpErrors.join(" | ")}`);
  const dom = {
    schema_version: "1.0",
    gate_id: "GATE_A_W3_NON_NH01_DOM_FIXTURE",
    page_id: rootInfo.pageId,
    scope: "TEST_ONLY_NOT_EMPTY_HOME_OR_LOGIN",
    source_sha: args.sourceSha,
    browser_measurement_unit: "css_px",
    native_runtime: "NOT_VERIFIED",
    initial_geometry: geometryRows,
    wide_layout: largeLayout,
    compact_layout: compactLayout,
    font_scale: { before: beforeFontScale, after: afterFontScale },
    resource_state: "ERROR",
    missing_component_negative: { result: "EXPECTED_FAIL", reason: missingReason },
    browser_history: "PASS",
    console_errors: consoleErrors,
    unexpected_http_errors: unexpectedHttpErrors
  };
  const domPath = resolve(args.outputDir, "non-nh01-fixture-dom-report.json");
  const domBytes = `${JSON.stringify(dom, null, 2)}\n`;
  await writeFile(domPath, domBytes, "utf8");
  const report = {
    schema_version: "1.0",
    result: "PASS",
    page_id: rootInfo.pageId,
    source_sha: args.sourceSha,
    gate_id: "GATE_A_W3_NON_NH01_DOM_FIXTURE",
    fixture_scope: "TEST_ONLY_NOT_EMPTY_HOME_OR_LOGIN",
    exact_source_bound: true,
    browser_run_id: process.env.GITHUB_RUN_ID || null,
    checks: {
      real_component_tree: "PASS", tree_to_dom_selection: "PASS", dom_to_tree_selection: "PASS",
      inspector_bounds: "PASS", viewport_reflow: "PASS", font_scale_measurement: "PASS",
      page_state_switch: "PASS", explicit_resource_error_state: "PASS", missing_component_negative: "EXPECTED_FAIL",
      disposal_and_reload: "PASS", back_forward: "PASS", console_and_http: "PASS",
      visual_acceptance: "NOT_VERIFIED", native_runtime: "NOT_VERIFIED"
    },
    dom_report: { path: "non-nh01-fixture-dom-report.json", sha256: createHash("sha256").update(domBytes).digest("hex") },
    screenshots: screenshotPaths,
    console_errors: consoleErrors,
    unexpected_http_errors: unexpectedHttpErrors
  };
  await writeFile(resolve(args.outputDir, "non-nh01-fixture-report.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8");
  assert((await stat(resolve(args.outputDir, "non-nh01-fixture-wide.png"))).size > 0);
  console.log(`W3_NON_NH01_WORKSPACE_FIXTURE=PASS page_id=${rootInfo.pageId} source_sha=${args.sourceSha} nodes=${geometryRows.length}`);
} catch (error) {
  const report = {
    schema_version: "1.0", result: "FAIL", page_id: "test_non_nh01_workspace_v1", source_sha: args.sourceSha,
    gate_id: "GATE_A_W3_NON_NH01_DOM_FIXTURE", browser_run_id: process.env.GITHUB_RUN_ID || null,
    error: error instanceof Error ? error.stack : String(error), console_errors: consoleErrors,
    unexpected_http_errors: unexpectedHttpErrors
  };
  await writeFile(resolve(args.outputDir, "non-nh01-fixture-report.json"), `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.error(report.error);
  process.exitCode = 1;
} finally {
  if (browser) await browser.close();
  if (server.listening) await new Promise(resolvePromise => server.close(resolvePromise));
}
