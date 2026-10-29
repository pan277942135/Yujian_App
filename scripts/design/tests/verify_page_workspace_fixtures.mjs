#!/usr/bin/env node
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";

const sourceSha = "42ad0d3fef23ea6576fd43dee781d253dcc11251";
const pageId = "home_empty_v2";
const verifier = resolve("scripts/design/verify_page_workspace.mjs");

function shellHtml({ page = pageId, source = sourceSha, node = true, throwing = false, kind = "structured-page-adapter", disabled = false, viewState = "READY", errorText = null, badResource = false, staleWorkspace = false, duplicateWorkspace = false } = {}) {
  const tree = `<button data-workspace-tree-node data-node-id="empty-scene" ${disabled ? "disabled" : ""}>Empty scene</button>`;
  const rendered = node ? `<div data-workspace-rendered-node="empty-scene" style="width:120px;height:80px">scene</div>` : "";
  const stale = staleWorkspace ? `<main data-page-workspace-root data-page-id="${page}" data-source-sha="1111111111111111111111111111111111111111" data-workspace-kind="structured-page-adapter" data-view-state="READY" style="display:none"><button data-workspace-tree-node data-node-id="old-workspace-layer" disabled>Old hidden layer</button><aside data-workspace-inspector data-selected-node-id="">Old Inspector</aside></main>` : "";
  const duplicate = duplicateWorkspace ? `<main data-page-workspace-root data-page-id="${page}" data-source-sha="${source}" data-workspace-kind="${kind}" data-view-state="${viewState}">${tree}${rendered}<aside data-workspace-inspector data-selected-node-id="">Duplicate Inspector</aside></main>` : "";
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>
    ${stale}
    <main data-page-workspace-root data-page-id="${page}" data-source-sha="${source}" data-workspace-kind="${kind}" data-view-state="${viewState}">
      ${tree}${rendered}<aside data-workspace-inspector data-selected-node-id="">Inspector</aside>
      ${errorText ? `<p data-workspace-error role="alert">${errorText}</p>` : ""}
    </main>
    ${duplicate}
    <script>
      document.querySelectorAll('[data-page-workspace-root]').forEach(workspace => {
        workspace.querySelectorAll('[data-workspace-tree-node]').forEach(button => button.addEventListener('click', () => {
          workspace.querySelector('[data-workspace-inspector]').dataset.selectedNodeId = button.dataset.nodeId;
        }));
      });
      ${throwing ? "throw new Error('fixture page error');" : ""}
      ${badResource ? `fetch('/broken-workspace-resource.png').then(response => {
        const workspace = document.querySelector('[data-page-workspace-root]');
        workspace.dataset.viewState = 'ERROR';
        const error = document.createElement('p');
        error.dataset.workspaceError = '';
        error.setAttribute('role', 'alert');
        error.textContent = 'authority resource HTTP ' + response.status;
        workspace.append(error);
      });` : ""}
    </script>
  </body></html>`;
}

async function runVerifier(serverUrl, fixtureName, outputDir, unconnectedPolicy = "fail") {
  const candidateUrl = `${serverUrl}/design/manager/?fixture=${fixtureName}`;
  const args = [verifier, "--page-id", pageId, "--candidate-url", candidateUrl, "--source-sha", sourceSha,
    "--output-dir", outputDir, "--readiness-timeout-ms", "300", "--unconnected-policy", unconnectedPolicy];
  return await new Promise((resolvePromise, reject) => {
    const child = spawn("node", args, { stdio: "ignore", env: process.env });
    child.once("error", reject);
    child.once("close", code => resolvePromise(code));
  });
}

async function main() {
  const browser = createServer((request, response) => {
    const url = new URL(request.url, "http://localhost");
    if (!url.pathname.startsWith("/design/manager/")) {
      response.writeHead(404).end("not found");
      return;
    }
    const fixture = url.searchParams.get("fixture");
    if (fixture === "missing") {
      response.writeHead(404).end("missing route");
      return;
    }
    const html = fixture === "wrong-page" ? shellHtml({ page: "auth_login_v2" })
      : fixture === "hidden-old-workspace" ? shellHtml({ staleWorkspace: true })
      : fixture === "duplicate-visible-workspaces" ? shellHtml({ duplicateWorkspace: true })
      : fixture === "missing-layer" ? shellHtml({ node: false })
      : fixture === "reference-only" ? shellHtml({ kind: "reference-image-only", viewState: "REFERENCE_IMAGE_ONLY" })
      : fixture === "reference-only-error" ? shellHtml({ kind: "reference-image-only", viewState: "ERROR", errorText: "authority load failed", throwing: true })
      : fixture === "reference-only-bad-response" ? shellHtml({ kind: "reference-image-only", viewState: "LOADING", badResource: true })
      : fixture === "structured-error" ? shellHtml({ viewState: "ERROR", errorText: "renderer failed" })
      : fixture === "disabled-node" ? shellHtml({ disabled: true })
      : fixture === "page-error" ? shellHtml({ throwing: true })
      : shellHtml();
    response.writeHead(200, { "content-type": "text/html; charset=utf-8" }).end(html);
  });
  await new Promise(resolvePromise => browser.listen(0, "127.0.0.1", resolvePromise));
  const address = browser.address();
  const serverUrl = `http://127.0.0.1:${address.port}`;
  const root = await mkdtemp(join(tmpdir(), "page-workspace-w4-test-"));
  let infrastructureBlocked = false;
  try {
    for (const fixture of ["ok", "hidden-old-workspace", "duplicate-visible-workspaces", "missing", "wrong-page", "missing-layer", "reference-only", "structured-error", "disabled-node", "page-error"]) {
      const outputDir = join(root, fixture);
      const code = await runVerifier(serverUrl, fixture, outputDir);
      const report = JSON.parse(await readFile(join(outputDir, "workspace-browser-report.json"), "utf8"));
      if (report.result === "BLOCKED") {
        infrastructureBlocked = true;
        continue;
      }
      const expected = ["ok", "hidden-old-workspace"].includes(fixture) ? "PASS" : "FAIL";
      if (report.result !== expected || (expected === "PASS" && code !== 0) || (expected === "FAIL" && code === 0)) {
        throw new Error(`${fixture}: expected ${expected}, got ${report.result}, exit ${code}; report=${JSON.stringify(report)}`);
      }
      if (expected === "FAIL" && report.result === "FAIL" &&
          (!report.screenshot_sha256 || !report.dom_sha256 || !report.failure_reason)) {
        throw new Error(`${fixture}: failure report omitted original reason or browser evidence: ${JSON.stringify(report)}`);
      }
      if (fixture === "hidden-old-workspace") {
        if (report.workspace_selection?.matching_visible_count !== 1 ||
            report.workspace_selection?.all_workspace_candidates?.length !== 2 ||
            report.tree_node_count !== 1) {
          throw new Error(`hidden old workspace polluted active evidence: ${JSON.stringify(report.workspace_selection)}`);
        }
      }
      if (fixture === "ok") {
        if (report.visual_result !== "MEASUREMENT_ONLY_NOT_VISUAL_PASS" || report.page_acceptance !== "NOT_VERIFIED") {
          throw new Error("workspace verifier promoted visual or page acceptance");
        }
        if (!(await stat(join(outputDir, "workspace.png"))).size || !(await stat(join(outputDir, "workspace-dom.json"))).size) {
          throw new Error("successful workspace inspection omitted screenshot or DOM evidence");
        }
      }
    }
    const referenceOnlyOutput = join(root, "reference-only-core-not-run");
    const referenceOnlyCode = await runVerifier(serverUrl, "reference-only", referenceOnlyOutput, "not-run");
    const referenceOnlyReport = JSON.parse(await readFile(join(referenceOnlyOutput, "workspace-browser-report.json"), "utf8"));
    if (referenceOnlyCode !== 0 || referenceOnlyReport.result !== "NOT_RUN"
      || referenceOnlyReport.reason !== "REFERENCE_IMAGE_ONLY_NOT_CONNECTED"
      || referenceOnlyReport.page_acceptance !== "NOT_VERIFIED") {
      throw new Error(`core tools must preserve reference-only page status as NOT_RUN; report=${JSON.stringify(referenceOnlyReport)}`);
    }
    for (const fixture of ["reference-only-error", "reference-only-bad-response"]) {
      const outputDir = join(root, fixture);
      const code = await runVerifier(serverUrl, fixture, outputDir, "not-run");
      const report = JSON.parse(await readFile(join(outputDir, "workspace-browser-report.json"), "utf8"));
      if (report.result !== "FAIL" || code === 0) {
        throw new Error(`${fixture}: broken reference-only authority must not be masked as NOT_RUN; report=${JSON.stringify(report)}`);
      }
    }
    if (infrastructureBlocked) {
      console.error("PAGE_WORKSPACE_VERIFIER_FIXTURES=BLOCKED_INFRA browser launch unavailable");
      process.exitCode = 2;
      return;
    }
    console.log("PAGE_WORKSPACE_VERIFIER_FIXTURES=PASS cases=12 hidden_old_workspace=IGNORED duplicate_visible_workspace=FAIL reference_only=NOT_RUN reference_only_errors=FAIL");
  } finally {
    await new Promise(resolvePromise => browser.close(resolvePromise));
    await rm(root, { recursive: true, force: true });
  }
}

main().catch(error => { console.error(error instanceof Error ? error.stack : String(error)); process.exitCode = 1; });
