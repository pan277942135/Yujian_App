#!/usr/bin/env node
import { createRequire } from "node:module";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { createHash } from "node:crypto";

const require = createRequire(import.meta.url);
const playwrightRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (playwrightRoot ? resolve(playwrightRoot, "playwright") : "playwright");

function parseArgs(argv) {
  const args = { pageId: null, candidateUrl: null, sourceSha: null, outputDir: "/tmp/page-workspace-evidence", readinessTimeoutMs: 30000, unconnectedPolicy: "fail" };
  for (let i = 2; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === "--page-id") args.pageId = argv[++i];
    else if (key === "--candidate-url") args.candidateUrl = argv[++i];
    else if (key === "--source-sha") args.sourceSha = argv[++i];
    else if (key === "--output-dir") args.outputDir = resolve(argv[++i]);
    else if (key === "--readiness-timeout-ms") args.readinessTimeoutMs = Number(argv[++i]);
    else if (key === "--unconnected-policy") args.unconnectedPolicy = argv[++i];
    else throw new Error(`Unknown argument: ${key}`);
  }
  if (!args.pageId || !args.candidateUrl || !/^[0-9a-f]{40}$/.test(args.sourceSha || "")) {
    throw new Error("--page-id, --candidate-url, and a full --source-sha are required");
  }
  if (!Number.isInteger(args.readinessTimeoutMs) || args.readinessTimeoutMs < 100 || args.readinessTimeoutMs > 60000) {
    throw new Error("--readiness-timeout-ms must be an integer from 100 to 60000");
  }
  if (!["fail", "not-run"].includes(args.unconnectedPolicy)) throw new Error("--unconnected-policy must be fail or not-run");
  return args;
}

function hash(bytes) { return createHash("sha256").update(bytes).digest("hex"); }
function assert(condition, message) { if (!condition) throw new Error(message); }

function managerRoute(candidateUrl, pageId) {
  const url = new URL(candidateUrl);
  assert(["http:", "https:"].includes(url.protocol), "candidate URL must use HTTP or HTTPS");
  if (!url.pathname.endsWith("/design/manager/") && !url.pathname.endsWith("/design/manager")) {
    const prefix = url.pathname.endsWith("/") ? url.pathname : `${url.pathname}/`;
    url.pathname = `${prefix}design/manager/`.replace(/\/+/g, "/");
  }
  if (!url.hash.startsWith("#page/")) url.hash = `#page/${encodeURIComponent(pageId)}/hifi/structured_workspace`;
  return url.href;
}

async function main() {
  const args = parseArgs(process.argv);
  await mkdir(args.outputDir, { recursive: true });
  const reportPath = resolve(args.outputDir, "workspace-browser-report.json");
  const screenshotPath = resolve(args.outputDir, "workspace.png");
  const domPath = resolve(args.outputDir, "workspace-dom.json");
  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  let browser;
  let page;
  let report;
  let workspaceSelector = null;
  let workspaceCandidates = [];
  try {
    const { chromium } = require(playwrightPath);
    browser = await chromium.launch({ headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", error => errors.push(error.message));
    page.on("requestfailed", request => failedRequests.push(`${request.url()} :: ${request.failure()?.errorText || "request failed"}`));
    page.on("response", response => { if (response.status() >= 400 && response.url().startsWith(new URL(args.candidateUrl).origin)) badResponses.push(`${response.status()} ${response.url()}`); });

    const route = managerRoute(args.candidateUrl, args.pageId);
    await page.goto(route, { waitUntil: "domcontentloaded", timeout: 45000 });
    workspaceSelector = `[data-page-workspace-root][data-page-id="${args.pageId}"][data-source-sha="${args.sourceSha}"], [data-page-workspace-root][data-page-id="${args.pageId}"][data-build-sha="${args.sourceSha}"]`;
    await page.waitForFunction(({ selector }) => [...document.querySelectorAll(selector)].some(node => {
      const style = getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0;
    }), { selector: workspaceSelector }, { timeout: args.readinessTimeoutMs });
    const matchingWorkspaces = page.locator(workspaceSelector);
    const matchingWorkspaceInfo = await matchingWorkspaces.evaluateAll(nodes => nodes.map(node => {
      const style = getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return {
        pageId: node.dataset.pageId || null,
        sourceSha: node.dataset.sourceSha || node.dataset.buildSha || null,
        workspaceKind: node.dataset.workspaceKind || null,
        viewState: node.dataset.viewState || null,
        visible: style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0,
        rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
      };
    }));
    workspaceCandidates = await page.locator("[data-page-workspace-root]").evaluateAll(nodes => nodes.map(node => {
      const style = getComputedStyle(node);
      const rect = node.getBoundingClientRect();
      return {
        pageId: node.dataset.pageId || null,
        sourceSha: node.dataset.sourceSha || node.dataset.buildSha || null,
        workspaceKind: node.dataset.workspaceKind || null,
        viewState: node.dataset.viewState || null,
        visible: style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0,
        rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
      };
    }));
    const visibleWorkspaceIndexes = matchingWorkspaceInfo.map((node, index) => node.visible ? index : -1).filter(index => index >= 0);
    assert(visibleWorkspaceIndexes.length === 1,
      `expected exactly one visible workspace for page_id=${args.pageId} source_sha=${args.sourceSha}; found ${visibleWorkspaceIndexes.length}; candidates=${JSON.stringify(workspaceCandidates)}`);
    const workspace = matchingWorkspaces.nth(visibleWorkspaceIndexes[0]);
    const rootInfo = await workspace.evaluate(node => ({
      pageId: node.dataset.pageId || null,
      sourceSha: node.dataset.sourceSha || node.dataset.buildSha || null,
      workspaceKind: node.dataset.workspaceKind || null,
      viewState: node.dataset.viewState || null,
      errorText: (() => {
        const error = node.querySelector("[data-workspace-error]");
        return error && !error.hidden ? error.textContent?.trim() || "WORKSPACE_ERROR" : null;
      })(),
      rect: (() => { const r = node.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; })()
    }));
    assert(rootInfo.pageId === args.pageId, `workspace page_id mismatch: expected ${args.pageId}, got ${rootInfo.pageId}`);
    assert(rootInfo.sourceSha === args.sourceSha, `workspace source/build SHA mismatch: expected ${args.sourceSha}, got ${rootInfo.sourceSha}`);
    assert(rootInfo.workspaceKind, "workspace is missing its adapter/rendering kind marker");
    if (rootInfo.workspaceKind === "reference-image-only" && args.unconnectedPolicy === "not-run") {
      assert(rootInfo.viewState === "REFERENCE_IMAGE_ONLY", `reference-only workspace is not ready: ${rootInfo.viewState || "missing state"}`);
      assert(!rootInfo.errorText, `reference-only workspace reports an authority/render error: ${rootInfo.errorText}`);
      assert(errors.length === 0, `reference-only workspace emitted browser console errors: ${errors.join(" | ")}`);
      assert(failedRequests.length === 0, `reference-only workspace has failed requests: ${failedRequests.join(" | ")}`);
      assert(badResponses.length === 0, `reference-only workspace has HTTP failures: ${badResponses.join(" | ")}`);
      report = {
        schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha, candidate_url: route,
        result: "NOT_RUN", reason: "REFERENCE_IMAGE_ONLY_NOT_CONNECTED",
        visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", page_acceptance: "NOT_VERIFIED",
        workspace: rootInfo, console_errors: errors, failed_requests: failedRequests, bad_responses: badResponses,
        screenshot_path: null, screenshot_sha256: null, dom_path: null, dom_sha256: null,
        run_id: process.env.GITHUB_RUN_ID || null
      };
    } else {
    assert(rootInfo.workspaceKind !== "reference-image-only", "workspace exposes only a reference raster; page component DOM mapping is unavailable");
    assert(rootInfo.viewState !== "ERROR", "structured workspace reports an ERROR state");
    assert(!rootInfo.errorText, `structured workspace reports a visible error: ${rootInfo.errorText}`);

    const nodes = await workspace.locator("[data-workspace-tree-node]").evaluateAll(elements => elements.map(node => ({
      nodeId: node.dataset.nodeId || node.dataset.workspaceNodeId || null,
      label: node.textContent?.trim() || null,
      disabled: Boolean(node.disabled)
    })));
    assert(nodes.length > 0, "workspace component tree is empty");
    const measured = [];
    for (const row of nodes) {
      assert(row.nodeId, "component tree contains a node without a stable node ID");
      assert(!row.disabled, `component tree node ${row.nodeId} is disabled and cannot be selected in the actual workspace`);
      const target = workspace.locator(`[data-workspace-rendered-node="${row.nodeId.replaceAll('"', '\\"')}"]`);
      assert(await target.count() === 1, `tree node ${row.nodeId} does not map to exactly one rendered DOM node inside the selected workspace`);
      await workspace.locator(`[data-workspace-tree-node][data-node-id="${row.nodeId.replaceAll('"', '\\"')}"]`).first().click();
      const inspector = workspace.locator("[data-workspace-inspector]");
      await inspector.waitFor({ state: "visible", timeout: 10000 });
      const selectedId = await inspector.getAttribute("data-selected-node-id");
      assert(selectedId === row.nodeId, `Inspector selected ${selectedId} for tree node ${row.nodeId}`);
      const bounds = await target.first().evaluate(node => {
        const r = node.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
      });
      assert(bounds.width >= 0 && bounds.height >= 0, `invalid DOM rectangle for ${row.nodeId}`);
      measured.push({ ...row, bounds });
    }
    const overflow = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth
    }));
    await page.screenshot({ path: screenshotPath, fullPage: true });
    const dom = { page_id: args.pageId, source_sha: args.sourceSha, route, viewport: { width: 1440, height: 1000, unit: "css_px" }, workspace: rootInfo, nodes: measured, overflow };
    await writeFile(domPath, `${JSON.stringify(dom, null, 2)}\n`, "utf8");
    if (overflow.horizontalOverflow) errors.push(`horizontal overflow: document ${overflow.documentWidth}px > viewport ${overflow.viewportWidth}px`);
    if (failedRequests.length) errors.push(`failed browser requests: ${failedRequests.length}`);
    if (badResponses.length) errors.push(`same-origin HTTP failures: ${badResponses.length}`);
    report = {
      schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha, candidate_url: route,
      result: errors.length ? "FAIL" : "PASS", visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
      page_acceptance: "NOT_VERIFIED", workspace: rootInfo,
        workspace_selection: { selector: workspaceSelector, matching_visible_count: 1, all_workspace_candidates: workspaceCandidates },
        tree_node_count: nodes.length,
      measured_node_count: measured.length, overflow, console_errors: errors,
      failed_requests: failedRequests, bad_responses: badResponses,
      screenshot_path: screenshotPath, screenshot_sha256: hash(await readFile(screenshotPath)),
      dom_path: domPath, dom_sha256: hash(await readFile(domPath)),
      run_id: process.env.GITHUB_RUN_ID || null
    };
    }
  } catch (error) {
    const status = browser ? "FAIL" : "BLOCKED";
    const failureReason = `${status === "BLOCKED" ? "BLOCKED_INFRA" : "WORKSPACE_CHECK_FAILED"}: ${error instanceof Error ? error.message : String(error)}`;
    let failureEvidence = { screenshot_path: null, screenshot_sha256: null, dom_path: null, dom_sha256: null };
    if (browser && page && !page.isClosed()) {
      try {
        const snapshot = await page.evaluate(({ pageId, sourceSha }) => {
          const roots = [...document.querySelectorAll("[data-page-workspace-root]")].map(node => {
            const style = getComputedStyle(node);
            const rect = node.getBoundingClientRect();
            const visible = style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0;
            return {
              page_id: node.dataset.pageId || null,
              source_sha: node.dataset.sourceSha || node.dataset.buildSha || null,
              workspace_kind: node.dataset.workspaceKind || null,
              view_state: node.dataset.viewState || null,
              visible,
              rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
              tree_node_ids: [...node.querySelectorAll("[data-workspace-tree-node]")].map(item => item.dataset.nodeId || null),
              rendered_node_ids: [...node.querySelectorAll("[data-workspace-rendered-node]")].map(item => item.dataset.workspaceRenderedNode || null),
              error_text: node.querySelector("[data-workspace-error]")?.textContent?.trim() || null,
              html_excerpt: node.outerHTML.slice(0, 30000)
            };
          });
          return { page_id: pageId, source_sha: sourceSha, url: location.href, title: document.title, roots,
            body_text_excerpt: document.body.innerText.slice(0, 5000) };
        }, { pageId: args.pageId, sourceSha: args.sourceSha });
        workspaceCandidates = snapshot.roots.map(({ page_id, source_sha, workspace_kind, view_state, visible, rect }) =>
          ({ pageId: page_id, sourceSha: source_sha, workspaceKind: workspace_kind, viewState: view_state, visible, rect }));
        const domPath = resolve(args.outputDir, "workspace-failure-dom.json");
        await writeFile(domPath, `${JSON.stringify(snapshot, null, 2)}\n`, "utf8");
        const screenshotPath = resolve(args.outputDir, "workspace-failure.png");
        await page.screenshot({ path: screenshotPath, fullPage: true });
        failureEvidence = {
          screenshot_path: screenshotPath, screenshot_sha256: hash(await readFile(screenshotPath)),
          dom_path: domPath, dom_sha256: hash(await readFile(domPath))
        };
      } catch (captureError) {
        failureEvidence.capture_error = captureError instanceof Error ? captureError.message : String(captureError);
      }
    }
    report = {
      schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha,
      candidate_url: args.candidateUrl, result: status,
      visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", page_acceptance: "NOT_VERIFIED",
      console_errors: [...errors, failureReason], failure_reason: failureReason,
      workspace_selection: { selector: workspaceSelector, candidates: workspaceCandidates },
      failed_requests: failedRequests, bad_responses: badResponses, ...failureEvidence,
      run_id: process.env.GITHUB_RUN_ID || null
    };
  } finally {
    if (browser) await browser.close();
  }
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ result: report.result, page_id: report.page_id, source_sha: report.source_sha, report_path: reportPath }, null, 2));
  if (report.result !== "PASS" && !(report.result === "NOT_RUN" && args.unconnectedPolicy === "not-run")) process.exitCode = 1;
}

main().catch(error => { console.error(error instanceof Error ? error.stack : String(error)); process.exitCode = 2; });
