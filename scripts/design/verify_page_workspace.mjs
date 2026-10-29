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
    try {
      await page.waitForFunction(({ selector }) => {
        const isVisible = node => {
          const style = getComputedStyle(node);
          const rect = node.getBoundingClientRect();
          return style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0 && rect.width > 0 && rect.height > 0;
        };
        const roots = [...document.querySelectorAll(selector)].filter(isVisible);
        if (roots.length !== 1) return true; // The existing uniqueness assertion reports duplicates.
        const root = roots[0];
        const state = root.dataset.viewState || "";
        if (state === "ERROR" || root.querySelector("[data-workspace-error]:not([hidden])")) return true;
        if (root.dataset.workspaceKind === "reference-image-only") return state === "REFERENCE_IMAGE_ONLY";
        if (state === "LOADING") return false;
        const rows = [...root.querySelectorAll("[data-workspace-tree-node]")];
        if (rows.length === 0) return false;
        const rendered = [...root.querySelectorAll("[data-workspace-rendered-node]")];
        return rows.every(row => {
          const id = row.dataset.nodeId || row.dataset.workspaceNodeId;
          return !id || rendered.some(node => node.dataset.workspaceRenderedNode === id);
        });
      }, { selector: workspaceSelector }, { timeout: args.readinessTimeoutMs });
    } catch (error) {
      // Continue after a readiness timeout so the unchanged fail-closed assertions
      // below report the missing node, LOADING state, or empty tree with evidence.
      if (!(error instanceof Error && /Timeout/.test(error.message))) throw error;
    }
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
      couóMm¢G§²ÚîÆ­yÚ[\™T™X\ÛÛ‹ˆÛÜšÜÜXÙWÜÙ[Xİ[ÛˆÈÙ[XİÜˆÛÜšÜÜXÙTÙ[XİÜ‹Ø[™Y]\ÎˆÛÜšÜÜXÙPØ[™Y]\ÈKˆ˜Z[YÜ™\]Y\İÎˆ˜Z[Y™\]Y\İË˜YÜ™\ÜÛœÙ\Îˆ˜Y™\ÜÛœÙ\Ë‹‹™˜Z[\™Q]šY[˜ÙKˆ[—ÚYˆ›ØÙ\ÜË™[‹‘ÒUP—Ô•S—ÒQ[ˆNÂˆHš[˜[HÂˆYˆ
œ›İÜÙ\ŠH]ØZ]œ›İÜÙ\‹˜ÛÜÙJ
NÂˆBˆ]ØZ]Üš]Qš[J™\Ü]	Ò”ÓÓ‹œİš[™ÚYJ™\Ü[Š_W˜]ŠNÂˆÛÛœÛÛK›ÙÊ”ÓÓ‹œİš[™ÚYJÈ™\İ[ˆ™\Üœ™\İ[YÙWÚYˆ™\ÜœYÙWÚYÛİ\˜ÙWÜÚNˆ™\ÜœÛİ\˜ÙWÜÚK™\ÜÜ]ˆ™\Ü]K[ŠJNÂˆYˆ
™\Üœ™\İ[OOH”TÔÈˆ	‰ˆJ™\Üœ™\İ[OOH““ÕÔ•Sˆˆ	‰ˆ\™ÜË[˜ÛÛ›™XİYÛXŞHOOH››İ\[ˆŠJH›ØÙ\ÜË™^]ÛÙHHNÂŸB‚›XZ[Š
K˜Ø]Ú
\œ›ÜˆOˆÈÛÛœÛÛK™\œ›ÜŠ\œ›Üˆ[œİ[˜Ù[Ùˆ\œ›ÜˆÈ\œ›Ü‹œİXÚÈˆİš[™Ê\œ›ÜŠJNÈ›ØÙ\ÜË™^]ÛÙHHÈJNÂ