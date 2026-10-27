#!/usr/bin/env node
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve, join } from "node:path";

const sourceSha = "42ad0d3fef23ea6576fd43dee781d253dcc11251";
const pageId = "home_empty_v2";
const verifier = resolve("scripts/design/verify_page_workspace.mjs");

function shellHtml({ page = pageId, source = sourceSha, node = true, throwing = false, kind = "structured-page-adapter", disabled = false } = {}) {
  const tree = `<button data-workspace-tree-node data-node-id="empty-scene" ${disabled ? "disabled" : ""}>Empty scene</button>`;
  const rendered = node ? `<div data-workspace-rendered-node="empty-scene" style="width:120px;height:80px">scene</div>` : "";
  return `<!doctype html><html><head><meta charset="utf-8"></head><body>
    <main data-page-workspace-root data-page-id="${page}" data-source-sha="${source}" data-workspace-kind="${kind}">
      ${tree}${rendered}<aside data-workspace-inspector data-selected-node-id="">Inspector</aside>
    </main>
    <script>
      document.querySelector('[data-workspace-tree-node]').addEventListener('click', () => {
        document.querySelector('[data-workspace-inspector]').dataset.selectedNodeId = 'empty-scene';
      });
      ${throwing ? "throw new Error('fixture page error');" : ""}
    </script>
  </body></html>`;
}

async function runVerifier(serverUrl, fixtureName, outputDir) {
  const candidateUrl = `${serverUrl}/design/manager/?fixture=${fixtureName}`;
  const args = [verifier, "--page-id", pageId, "--candidate-url", candidateUrl, "--source-sha", sourceSha,
    "--output-dir", outputDir, "--readiness-timeout-ms", "300"];
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
      : fixture === "missing-layer" ? shellHtml({ node: false })
      : fixture === "reference-only" ? shellHtml({ kind: "reference-image-only" })
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
    for (const fixture of ["ok", "missing", "wrong-page", "missing-layer", "reference-only", "disabled-node", "page-error"]) {
      const outputDir = join(root, fixture);
      const code = await runVerifier(serverUrl, fixture, outputDir);
      const report = JSON.parse(await readFile(join(outputDir, "workspace-browser-report.json"), "utf8"));
      if (report.result === "BLOCKED") {
        infrastructureBlocked = true;
        continue;
      }
      const expected = fixture === "ok" ? "PASS" : "FAIL";
      if (report.result !== expected || (expected === "PASS" && code !== 0) || (expected === "FAIL" && code === 0)) {
        throw new Error(`${fixture}: expected ${expected}, got ${report.result}, exit ${code}; report=${JSON.stringify(report)}`);
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
    if (infrastructureBlocked) {
      console.error("PAGE_WORKSPACE_VERIFIER_FIXTURES=BLOCKED_INFRA browser launch unavailable");
      process.exitCode = 2;
      return;
    }
    console.log("PAGE_WORKSPACE_VERIFIER_FIXTURES=PASS cases=7");
  } finally {
    await new Promise(resolvePromise => browser.close(resolvePromise));
    await rm(root, { recursive: true, force: true });
  }
}

main().catch(error => { console.error(error instanceof Error ? error.stack : String(error)); process.exitCode = 1; });
