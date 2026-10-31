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
  let route;
  let workspace;
  let workspaceCandidates = [];
  let workspaceScopeToken;
  try {
    const { chromium } = require(playwrightPath);
    browser = await chromium.launch({ headless: true });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", error => errors.push(error.message));
    page.on("requestfailed", request => failedRequests.push(`${request.url()} :: ${request.failure()?.errorText || "request failed"}`));
    page.on("response", response => { if (response.status() >= 400 && response.url().startsWith(new URL(args.candidateUrl).origin)) badResponses.push(`${response.status()} ${response.url()}`); });

    route = managerRoute(args.candidateUrl, args.pageId);
    await page.goto(route, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForFunction(({ pageId, sourceSha }) => {
      const isVisible = node => {
        for (let current = node; current; current = current.parentElement) {
          const style = getComputedStyle(current);
          if (current.hidden || current.getAttribute("aria-hidden") === "true" || style.display === "none"
            || style.visibility === "hidden" || style.opacity === "0") return false;
        }
        const rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      };
      return [...document.querySelectorAll("[data-page-workspace-root]")].some(node =>
        (node.dataset.pageId || null) === pageId
        && (node.dataset.sourceSha || node.dataset.buildSha || null) === sourceSha
        && isVisible(node));
    }, { pageId: args.pageId, sourceSha: args.sourceSha }, { timeout: args.readinessTimeoutMs });
    workspaceCandidates = await page.locator("[data-page-workspace-root]").evaluateAll((elements, { pageId, sourceSha }) => {
      const isVisible = node => {
        for (let current = node; current; current = current.parentElement) {
          const style = getComputedStyle(current);
          if (current.hidden || current.getAttribute("aria-hidden") === "true" || style.display === "none"
            || style.visibility === "hidden" || style.opacity === "0") return false;
        }
        const rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
      };
      return elements.map((node, index) => {
        const rect = node.getBoundingClientRect();
        const nodeSourceSha = node.dataset.sourceSha || node.dataset.buildSha || null;
        const identityMatches = (node.dataset.pageId || null) === pageId && nodeSourceSha === sourceSha;
        return {
          index,
          pageId: node.dataset.pageId || null,
          sourceSha: nodeSourceSha,
          workspaceKind: node.dataset.workspaceKind || null,
          viewState: node.dataset.viewState || null,
          visible: isVisible(node),
          identityMatches,
          rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
          treeNodes: [...node.querySelectorAll("[data-workspace-tree-node]")].map(treeNode => {
            const disabled = Boolean(treeNode.disabled) || treeNode.getAttribute("aria-disabled") === "true";
            const explicitSelectability = treeNode.getAttribute("data-node-selectable");
            const nativeInteractive = treeNode.matches("button, input, select, textarea, a[href], summary")
              || (treeNode.getAttribute("role") === "button" && treeNode.tabIndex >= 0);
            return {
              nodeId: treeNode.dataset.nodeId || treeNode.dataset.workspaceNodeId || null,
              disabled,
              selectable: explicitSelectability === null ? nativeInteractive && !disabled : explicitSelectability === "true",
              selectabilitySource: explicitSelectability === null ? "native-interactive-element" : "explicit-attribute"
            };
          })
        };
      });
    }, { pageId: args.pageId, sourceSha: args.sourceSha });
    const matchingVisibleWorkspaces = workspaceCandidates.filter(candidate => candidate.visible && candidate.identityMatches);
    assert(matchingVisibleWorkspaces.length === 1,
      `expected exactly one visible workspace matching page ${args.pageId} and source SHA ${args.sourceSha}; found ${matchingVisibleWorkspaces.length}`);
    workspaceScopeToken = `w4-${process.pid}-${Date.now()}`;
    const scoped = await page.locator("[data-page-workspace-root]").evaluateAll((elements, { index, token }) => {
      const node = elements[index];
      if (!node) return false;
      node.setAttribute("data-w4-verifier-scope", token);
      return true;
    }, { index: matchingVisibleWorkspaces[0].index, token: workspaceScopeToken });
    assert(scoped, "could not scope the unique visible workspace for W4 verification");
    workspace = page.locator(`[data-w4-verifier-scope="${workspaceScopeToken}"]`);
    await page.waitForFunction(token => {
      const root = document.querySelector(`[data-w4-verifier-scope="${token}"]`);
      if (!root) return false;
      if (root.dataset.workspaceKind === "reference-image-only") return ["REFERENCE_IMAGE_ONLY", "ERROR"].includes(root.dataset.viewState);
      const treeNodes = [...root.querySelectorAll("[data-workspace-tree-node]")];
      if (treeNodes.length === 0) return false;
      const renderedNodes = [...root.querySelectorAll("[data-workspace-rendered-node]")];
      return treeNodes.every(treeNode => {
        const nodeId = treeNode.dataset.nodeId || treeNode.dataset.workspaceNodeId;
        return Boolean(nodeId) && renderedNodes.filter(rendered => rendered.dataset.workspaceRenderedNode === nodeId).length === 1;
      });
    }, workspaceScopeToken, { timeout: args.readinessTimeoutMs });
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
        workspace: rootInfo, workspace_candidates: workspaceCandidates,
        console_errors: errors, failed_requests: failedRequests, bad_responses: badResponses,
        screenshot_path: null, screenshot_sha256: null, dom_path: null, dom_sha256: null,
        run_id: process.env.GITHUB_RUN_ID || null
      };
    } else {
    assert(rootInfo.workspaceKind !== "reference-image-only", "workspace exposes only a reference raster; page component DOM mapping is unavailable");
    assert(rootInfo.viewState !== "ERROR", "structured workspace reports an ERROR state");
    assert(!rootInfo.errorText, `structured workspace reports a visible error: ${rootInfo.errorText}`);

    const nodes = await workspace.locator("[data-workspace-tree-node]").evaluateAll(elements => elements.map((node, nodeIndex) => {
      const disabled = Boolean(node.disabled) || node.getAttribute("aria-disabled") === "true";
      const explicitSelectability = node.getAttribute("data-node-selectable");
      const nativeInteractive = node.matches("button, input, select, textarea, a[href], summary")
        || (node.getAttribute("role") === "button" && node.tabIndex >= 0);
      return {
        nodeIndex,
        nodeId: node.dataset.nodeId || node.dataset.workspaceNodeId || null,
        label: node.textContent?.trim() || null,
        state: node.dataset.nodeState || null,
        authorityStatus: node.dataset.nodeAuthorityStatus || null,
        visible: node.dataset.nodeVisible === "true",
        measurable: node.dataset.nodeMeasurable === "true",
        selectable: explicitSelectability === null ? nativeInteractive && !disabled : explicitSelectability === "true",
        selectabilitySource: explicitSelectability === null ? "native-interactive-element" : "explicit-attribute",
        disabled
      };
    }));
    assert(nodes.length > 0, "workspace component tree is empty");
    const measured = [];
    const blockedAuthorityNodes = [];
    const renderedNodes = workspace.locator("[data-workspace-rendered-node]");
    for (const row of nodes) {
      assert(row.nodeId, "component tree contains a node without a stable node ID");
      const targetIndices = await renderedNodes.evaluateAll((elements, nodeId) => elements.reduce((indexes, element, index) => {
        if (element.dataset.workspaceRenderedNode === nodeId) indexes.push(index);
        return indexes;
      }, []), row.nodeId);
      assert(targetIndices.length === 1, `tree node ${row.nodeId} does not map to exactly one rendered DOM node in the selected workspace`);
      const target = renderedNodes.nth(targetIndices[0]);
      if (row.state === "BLOCKED_AUTHORITY_GAP") {
        assert(row.disabled && !row.selectable && !row.visible && !row.measurable
          && row.authorityStatus === "BLOCKED_AUTHORITY_GAP",
        `blocked node ${row.nodeId} must remain explicitly hidden, unmeasurable, unselectable, and authority-blocked`);
        const availability = await target.first().evaluate(node => {
          const style = getComputedStyle(node);
          const rect = node.getBoundingClientRect();
          return { hidden: node.hidden, ariaHidden: node.getAttribute("aria-hidden"), display: style.display,
            visibility: style.visibility, width: rect.width, height: rect.height };
        });
        assert(availability.hidden || availability.ariaHidden === "true" || availability.display === "none"
          || availability.visibility === "hidden" || (availability.width === 0 && availability.height === 0),
        `blocked node ${row.nodeId} is rendered visibly despite its authority status`);
        blockedAuthorityNodes.push({ ...row, renderedDomId: await target.first().getAttribute("id"), availability });
        continue;
      }
      assert(!row.disabled, `component tree node ${row.nodeId} is disabled and cannot be selected in the actual workspace`);
      assert(row.selectable,
        `component tree node ${row.nodeId} is explicitly marked non-selectable or lacks a native interactive element`);
      const treeNode = workspace.locator("[data-workspace-tree-node]").nth(row.nodeIndex);
      await treeNode.click();
      const inspectorNodes = workspace.locator("[data-workspace-inspector]");
      assert(await inspectorNodes.count() === 1, "selected workspace must expose exactly one Inspector");
      const inspector = inspectorNodes.first();
      await inspector.waitFor({ state: "visible", timeout: 10000 });
      const selectedId = await inspector.getAttribute("data-selected-node-id");
      assert(selectedId === row.nodeId, `Inspector selected ${selectedId} for tree node ${row.nodeId}`);
      const bounds = await target.first().evaluate(node => {
        const r = node.getBoundingClientRect();
        return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
      });
      assert(bounds.width >= 0 && bounds.height >= 0, `invalid DOM rectangle for ${row.nodeId}`);
      measured.push({ ...row, inspectorSelectedNodeId: selectedId, bounds });
    }
    const overflow = await page.evaluate(() => ({
      viewportWidth: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth
    }));
    await page.screenshot({ path: screenshotPath, fullPage: true });
    const dom = { page_id: args.pageId, source_sha: args.sourceSha, route, viewport: { width: 1440, height: 1000, unit: "css_px" },
      workspace: rootInfo, workspace_candidates: workspaceCandidates, nodes: measured, blocked_authority_nodes: blockedAuthorityNodes, overflow };
    await writeFile(domPath, `${JSON.stringify(dom, null, 2)}\n`, "utf8");
    if (overflow.horizontalOverflow) errors.push(`horizontal overflow: document ${overflow.documentWidth}px > viewport ${overflow.viewportWidth}px`);
    if (failedRequests.length) errors.push(`failed browser requests: ${failedRequests.length}`);
    if (badResponses.length) errors.push(`same-origin HTTP failures: ${badResponses.length}`);
    report = {
      schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha, candidate_url: route,
      result: errors.length ? "FAIL" : "PASS", visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
      page_acceptance: "NOT_VERIFIED", workspace: rootInfo, workspace_candidates: workspaceCandidates, tree_node_count: nodes.length,
      measured_node_count: measured.length, blocked_authority_nodes: blockedAuthorityNodes, overflow, console_errors: errors,
      failed_requests: failedRequests, bad_responses: badResponses,
      screenshot_path: screenshotPath, screenshot_sha256: hash(await readFile(screenshotPath)),
      dom_path: domPath, dom_sha256: hash(await readFile(domPath)),
      run_id: process.env.GITHUB_RUN_ID || null
    };
    }
  } catch (error) {
    const status = browser ? "FAIL" : "BLOCKED";
    const originalFailure = error instanceof Error ? error.message : String(error);
    const diagnosticErrors = [];
    let screenshotSha = null;
    let domSha = null;
    if (page) {
      try {
        workspaceCandidates = await page.locator("[data-page-workspace-root]").evaluateAll((elements, { pageId, sourceSha }) => {
          const visible = node => {
            for (let current = node; current; current = current.parentElement) {
              const style = getComputedStyle(current);
              if (current.hidden || current.getAttribute("aria-hidden") === "true" || style.display === "none"
                || style.visibility === "hidden" || style.opacity === "0") return false;
            }
            const rect = node.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
          };
          return elements.map((node, index) => {
            const rect = node.getBoundingClientRect();
            const treeNodes = [...node.querySelectorAll("[data-workspace-tree-node]")].map(treeNode => {
              const treeRect = treeNode.getBoundingClientRect();
              const disabled = Boolean(treeNode.disabled) || treeNode.getAttribute("aria-disabled") === "true";
              const explicitSelectability = treeNode.getAttribute("data-node-selectable");
              const nativeInteractive = treeNode.matches("button, input, select, textarea, a[href], summary")
                || (treeNode.getAttribute("role") === "button" && treeNode.tabIndex >= 0);
              return { nodeId: treeNode.dataset.nodeId || treeNode.dataset.workspaceNodeId || null,
                disabled,
                selectable: explicitSelectability === null ? nativeInteractive && !disabled : explicitSelectability === "true",
                selectabilitySource: explicitSelectability === null ? "native-interactive-element" : "explicit-attribute",
                rect: { x: treeRect.x, y: treeRect.y, width: treeRect.width, height: treeRect.height } };
            });
            const inspectorNodes = [...node.querySelectorAll("[data-workspace-inspector]")].map(inspector => ({
              selectedNodeId: inspector.dataset.selectedNodeId || null,
              visible: visible(inspector),
              rect: (() => { const r = inspector.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; })()
            }));
            return { index, pageId: node.dataset.pageId || null, sourceSha: node.dataset.sourceSha || node.dataset.buildSha || null,
              workspaceKind: node.dataset.workspaceKind || null, viewState: node.dataset.viewState || null, visible: visible(node),
              identityMatches: (node.dataset.pageId || null) === pageId
                && (node.dataset.sourceSha || node.dataset.buildSha || null) === sourceSha,
              rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }, treeNodes, inspectorNodes,
              errorText: (() => { const n = node.querySelector("[data-workspace-error]"); return n && !n.hidden ? n.textContent?.trim() || null : null; })() };
          });
        }, { pageId: args.pageId, sourceSha: args.sourceSha });
        const dom = { page_id: args.pageId, source_sha: args.sourceSha, candidate_url: route || args.candidateUrl,
          viewport: await page.evaluate(() => ({ width: innerWidth, height: innerHeight, unit: "css_px" })),
          original_failure: originalFailure, selected_scope_token: workspaceScopeToken, workspace_candidates: workspaceCandidates,
          console_errors: errors, failed_requests: failedRequests, bad_responses: badResponses };
        await writeFile(domPath, `${JSON.stringify(dom, null, 2)}\n`, "utf8");
        domSha = hash(await readFile(domPath));
      } catch (diagnosticError) {
        diagnosticErrors.push(`DOM_DIAGNOSTICS_FAILED: ${diagnosticError instanceof Error ? diagnosticError.message : String(diagnosticError)}`);
      }
      try {
        await page.screenshot({ path: screenshotPath, fullPage: true });
        screenshotSha = hash(await readFile(screenshotPath));
      } catch (diagnosticError) {
        diagnosticErrors.push(`SCREENSHOT_CAPTURE_FAILED: ${diagnosticError instanceof Error ? diagnosticError.message : String(diagnosticError)}`);
      }
    }
    report = {
      schema_version: "1.0", page_id: args.pageId, source_sha: args.sourceSha,
      candidate_url: route || args.candidateUrl, result: status, original_failure: originalFailure,
      visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", page_acceptance: "NOT_VERIFIED",
      console_errors: [`${status === "BLOCKED" ? "BLOCKED_INFRA" : "WORKSPACE_CHECK_FAILED"}: ${originalFailure}`, ...errors],
      failed_requests: failedRequests, bad_responses: badResponses,
      workspace_candidates: workspaceCandidates, diagnostic_capture_errors: diagnosticErrors,
      screenshot_path: screenshotSha ? screenshotPath : null, screenshot_sha256: screenshotSha,
      dom_path: domSha ? domPath : null, dom_sha256: domSha,
      run_id: process.env.GITHUB_RUN_ID || null
    };
  } finally {
    if (browser) {
      try { await browser.close(); } catch (error) {
        if (report) report.diagnostic_capture_errors = [...(report.diagnostic_capture_errors || []),
          `BROWSER_CLOSE_FAILED: ${error instanceof Error ? error.message : String(error)}`];
      }
    }
  }
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(JSON.stringify({ result: report.result, page_id: report.page_id, source_sha: report.source_sha, report_path: reportPath }, null, 2));
  if (report.result !== "PASS" && !(report.result === "NOT_RUN" && args.unconnectedPolicy === "not-run")) process.exitCode = 1;
}

main().catch(error => { console.error(error instanceof Error ? error.stack : String(error)); process.exitCode = 2; });
