#!/usr/bin/env node
import { createRequire } from "node:module";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { createHash } from "node:crypto";

const require = createRequire(import.meta.url);
const playwrightRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (playwrightRoot ? resolve(playwrightRoot, "playwright") : "playwright");

function parseArgs(argv) {
  const args = { pageId: null, candidateUrl: null, sourceSha: null, outputDir: "/tmp/page-workspace-evidence", readinessTimeoutMs: 30000 };
  for (let i = 2; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === "--page-id") args.pageId = argv[++i];
    else if (key === "--candidate-url") args.candidateUrl = argv[++i];
    else if (key === "--source-sha") args.sourceSha = argv[++i];
    else if (key === "--output-dir") args.outputDir = resolve(argv[++i]);
    else if (key === "--readiness-timeout-ms") args.readinessTimeoutMs = Number(argv[++i]);
    else throw new Error(`Unknown argument: ${key}`);
  }
  if (!args.pageId || !args.candidateUrl || !/^[0-9a-f]{40}$/.test(args.sourceSha || "")) {
    throw new Error("--page-id, --candidate-url, and a full --source-sha are required");
  }
  if (!Number.isInteger(args.readinessTimeoutMs) || args.readinessTimeoutMs < 100 || args.readinessTimeoutMs > 60000) {
    throw new Error("--readiness-timeout-ms must be an integer from 100 to 60000");
  }
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
  let report;
  try {
    const { chromium } = require(playwrightPath);
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", error => errors.push(error.message));
    page.on("requestfailed", request => failedRequests.push(`${request.url()} :: ${request.failure()?.errorText || "request failed"}`));
    page.on("response", response => { if (response.status() >= 400 && response.url().startsWith(new URL(args.candidateUrl).origin)) badResponses.push(`${response.status()} ${response.url()}`); });

    const route = managerRoute(args.candidateUrl, args.pageId);
    await page.goto(route, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.locator("[data-page-workspace-root]").waitFor({ state: "visible", timeout: args.readinessTimeoutMs });
    const rootInfo = await page.locator("[data-page-workspace-root]").evaluate(node => ({
      pageId: node.dataset.pageId || null,
      sourceSha: node.dataset.sourceSha || node.dataset.buildSha || null,
      workspaceKind: node.dataset.workspaceKind || null,
      viewState: node.dataset.viewState || null,
      rect: (() => { const r = node.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; })()
    }));
    assert(rootInfo.pageId === args.pageId, `workspace page_id mismatch: expected ${args.pageId}, got ${rootInfo.pageId}`);
    assert(rootInfo.sourceSha === args.sourceSha, `workspace source/build SHA mismatch: expected ${args.sourceSha}, got ${rootInfo.sourceSha}`);
    assert(rootInfo.workspaceKind, "workspace is missing its adapter/rendering kind marker");
    assert(rootInfo.workspaceKind !== "reference-image-only", "workspace exposes only a reference raster; page component DOM mapping is unavailable");

    const nodes = await page.locator("[data-workspace-tree-node]").evaluateAll(elements => elements.map(node => ({
      nodeId: node.dataset.nodeId || node.dataset.workspaceNodeId || null,
      label: node.textContent?.trim() || null,
      disabled: Boolean(node.disabled)
    })));
    assert(nodes.length > 0, "workspace component tree is empty");
    const measured = [];
    for (const row of nodes) {
      assert(row.nodeId, "component tree contains a node without a stable node ID");
      assert(!row.disabled, `component tree node ${row.nodeId} is disabled and cannot be selected in the actual workspace`);
      const target = page.locator(`[data-workspace-rendered-node="${row.nodeId.replaceAll('"', '\\"')}"]`);
      assert(await target.count() === 1, `tree node ${row.nodeId} does not map to exactly one rendered DOM node`);
      await page.locator(`[data-workspace-tree-node][data-node-id="${row.nodeId.replaceAll('"', '\\"')}"]`).first().click();
      const inspector = page.locator("[data-workspace-inspector]");
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
      page_acceptance: "NOT_VERIFIED", workspace: rootInfo, tree_node_count: nodes.length,
      measured_node_count: measured.length, overflow, console_errors: errors,
      failed_requests: failedRequests, bad_responses: badResponses,
      screenshot_path: screenshotPath, screenshot_sha256: hash(await readFile(screenshotPath)),
      dom_path: domPath, dom_sha256: hash(await readFile(domPath)),
      run_id: process.env.GITHUB_RUN_ID || null
    };
  } catch (error) {
    const status = browser ? "FAIL" : "BLOCKED";
    report = {
      schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha,
      candidate_url: args.candidateUrl, result: status,
      visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", page_acceptance: "NOT_VERIFIED",
      console_errors: [...errors, `${status === "BLOCKED" ? "BLOCKED_INFRA" : "WORKSPACE_CHECK_FAILED"}: ${error instanceof Error ? error.message : String(error)}`],
      failed_requests: failedRequests, bad_responses: badResponses,
      screenshot_path: null, screenshot_sha256: null, dom_path: null, dom_sha256: null,
      run_id: process.env.GITHUB_RUN_ID || null
    };
  } finally {
    if (browser) await browser.close();
  }
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ result: report.result, page_id: report.page_id, source_sha: report.source_sha, report_path: reportPath }, null, 2));
  if (report.result !== "PASS") process.exitCode = 1;
}

main().catch(error => { console.error(error instanceof Error ? error.stack : String(error)); process.exitCode = 2; });
