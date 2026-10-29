#!/usr/bin/env node
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, "../../..");
const args = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  const key = process.argv[index];
  if (key.startsWith("--")) args.set(key.slice(2), process.argv[index + 1]);
}
const outputDir = path.resolve(args.get("output-dir") || "/tmp/w6a-login-manager-route");
const sourceSha = args.get("source-sha") || process.env.SOURCE_SHA || "";
if (!/^[0-9a-f]{40}$/.test(sourceSha)) throw new Error("W6_LOGIN_MANAGER_SOURCE_SHA_REQUIRED");
const runId = process.env.GITHUB_RUN_ID || null;
const now = () => new Date().toISOString();
const mimeTypes = new Map([
  [".html", "text/html; charset=utf-8"], [".css", "text/css; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"], [".json", "application/json; charset=utf-8"],
  [".md", "text/markdown; charset=utf-8"], [".png", "image/png"], [".svg", "image/svg+xml"]
]);

function createManagerServer() {
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://127.0.0.1");
      let relative = decodeURIComponent(url.pathname).replace(/^\/+/, "");
      if (relative === "design/manager" || relative === "design/manager/") relative = "design/manager/index.html";
      if (relative === "design/manager/build.json") {
        response.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
        response.end(JSON.stringify({ sha: sourceSha, short_sha: sourceSha.slice(0, 12) }));
        return;
      }
      const resolved = path.resolve(REPO_ROOT, relative || "index.html");
      if (!resolved.startsWith(`${REPO_ROOT}${path.sep}`)) {
        response.writeHead(403, { "content-type": "text/plain; charset=utf-8" }).end("FORBIDDEN");
        return;
      }
      let bytes = await fs.readFile(resolved);
      if (relative === "design/manager/index.html") {
        const html = bytes.toString("utf8")
          .replaceAll("__DESIGN_MANAGER_BUILD__", sourceSha)
          .replaceAll("__DESIGN_MANAGER_BUILD_SHORT__", sourceSha.slice(0, 12));
        bytes = Buffer.from(html);
      }
      response.writeHead(200, {
        "content-type": mimeTypes.get(path.extname(resolved).toLowerCase()) || "application/octet-stream",
        "content-length": bytes.byteLength,
        "cache-control": "no-store"
      }).end(bytes);
    } catch (error) {
      const status = error?.code === "ENOENT" ? 404 : 500;
      response.writeHead(status, { "content-type": "text/plain; charset=utf-8" })
        .end(status === 404 ? "NOT_FOUND" : String(error?.stack || error));
    }
  });
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  const server = createManagerServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const origin = `http://127.0.0.1:${server.address().port}`;
  const route = `${origin}/design/manager/#page/auth_login_v2/hifi/structured_workspace`;
  const playwrightModuleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
  const require = createRequire(import.meta.url);
  const playwrightPath = playwrightModuleRoot ? path.join(playwrightModuleRoot, "playwright") : "playwright";
  const { chromium } = require(playwrightPath);
  const errors = { console: [], page: [], network: [], http: [] };
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    page.on("console", message => { if (message.type() === "error") errors.console.push(message.text()); });
    page.on("pageerror", error => errors.page.push(String(error?.stack || error)));
    page.on("requestfailed", request => errors.network.push({ url: request.url(), error: request.failure()?.errorText || "unknown" }));
    page.on("response", response => {
      if (response.status() >= 400) errors.http.push({ status: response.status(), url: response.url() });
    });

    await page.goto(route, { waitUntil: "networkidle" });
    await page.locator("#login-LoginPageRoot").waitFor({ state: "visible", timeout: 30000 });
    const initial = await page.evaluate(async () => {
      const adapter = window.LoginV21Workspace;
      if (!adapter) throw new Error("LOGIN_SHARED_ADAPTER_NOT_EXPOSED");
      return {
        page_id: adapter.pageId,
        source_sha: adapter.sourceSha,
        workspace_kind: document.querySelector("[data-page-workspace-root]")?.dataset.workspaceKind,
        root_id: (await adapter.loadComponentTree()).root.nodeId,
        component_count: (await adapter.loadComponentTree()).nodes.length,
        diagnostics: adapter.getResponsiveDiagnostics(),
        background_status: document.querySelector("#login-MorningLakeBGContent")?.dataset.resourceStatus,
        icon_status: document.querySelector("#login-AccountLeadingIcon")?.dataset.resourceStatus,
        footer_status: document.querySelector("#login-WatersideGrassDecoration")?.dataset.resourceStatus
      };
    });
    if (initial.page_id !== "auth_login_v2" || initial.source_sha !== sourceSha ||
        initial.workspace_kind !== "structured-dom-candidate" || initial.root_id !== "LoginPageRoot" ||
        initial.component_count < 15 || initial.background_status !== "W2_RESOLVED" ||
        initial.icon_status !== "NOT_CONFIRMED_IN_LOGIN_W2_BINDING" ||
        initial.footer_status !== "CANDIDATE_UNAPPROVED_ASSET") {
      throw new Error(`LOGIN_SHARED_MANAGER_INITIAL_STATE_INVALID ${JSON.stringify(initial)}`);
    }

    const inputNode = page.locator('[data-workspace-tree-node][data-node-id="AccountInput"]');
    await inputNode.waitFor({ state: "visible" });
    await inputNode.click();
    const inspector = await page.evaluate(() => ({
      selected: document.querySelector("[data-workspace-inspector]")?.dataset.selectedNodeId,
      actualInput: document.querySelector("#login-AccountInput")?.getBoundingClientRect().toJSON(),
      inspectorText: document.querySelector("[data-workspace-inspector]")?.innerText || ""
    }));
    if (inspector.selected !== "AccountInput" || inspector.actualInput.width <= 0 || inspector.actualInput.height <= 0 ||
        !inspector.inspectorText.includes("实际 DOM 边界")) {
      throw new Error(`LOGIN_SHARED_MANAGER_TREE_INSPECTOR_FAILED ${JSON.stringify(inspector)}`);
    }

    await page.locator("[data-workspace-width]").fill("320");
    await page.locator("[data-workspace-height]").fill("480");
    await page.locator("[data-workspace-apply-viewport]").click();
    await page.locator("[data-workspace-font-scale]").selectOption("1.5");
    await page.locator("[data-workspace-state]").selectOption("PASSWORD_SHOWN");
    const changed = await page.evaluate(() => ({
      diagnostics: window.LoginV21Workspace.getResponsiveDiagnostics(),
      artboard: document.querySelector("[data-workspace-artboard]")?.getBoundingClientRect().toJSON(),
      passwordType: document.querySelector("#login-PasswordInput")?.type,
      titleFontSize: getComputedStyle(document.querySelector("#login-WelcomeTitle")).fontSize,
      selected: document.querySelector("[data-workspace-inspector]")?.dataset.selectedNodeId
    }));
    if (changed.diagnostics.viewport.width !== 320 || changed.diagnostics.viewport.height !== 480 ||
        changed.diagnostics.font_scale !== 1.5 || changed.diagnostics.state !== "PASSWORD_SHOWN" ||
        changed.passwordType !== "text" || changed.selected !== "AccountInput") {
      throw new Error(`LOGIN_SHARED_MANAGER_CONTROLS_FAILED ${JSON.stringify(changed)}`);
    }
    await page.locator("#login-VisibilityAction").click();
    const toggled = await page.locator("#login-PasswordInput").getAttribute("type");
    if (toggled !== "password") throw new Error("LOGIN_SHARED_MANAGER_PASSWORD_ACTION_FAILED");

    const dimensions = await page.evaluate(() => window.LoginV21Workspace.measureElements());
    const result = {
      schema_version: "1.0",
      gate_id: "W6_SHARED_DESIGN_MANAGER_LOGIN_ROUTE",
      status: "PASS",
      page_id: "auth_login_v2",
      source_sha: sourceSha,
      run_id: runId,
      route,
      browser_engine: "Chromium",
      implementation: "existing Design Manager + shared PageWorkspaceAdapters.createStructuredDomAdapter",
      initial,
      tree_to_dom_and_inspector: "PASS",
      candidate_viewport_font_and_state_controls: "PASS",
      password_visibility_action: "PASS",
      current_dom_measurements: dimensions,
      changed_viewport: changed,
      resource_gaps_preserved: {
        shared_icon_publication: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING",
        footer_grass: "CANDIDATE_UNAPPROVED_ASSET",
        approved_upload_source: "MISSING",
        authentication_backend: "NOT_CONNECTED"
      },
      errors,
      user_visual_acceptance: "PENDING",
      native_runtime: "NOT_VERIFIED",
      generated_at: now()
    };
    if (errors.console.length || errors.page.length || errors.network.length || errors.http.length) {
      result.status = "FAIL";
      result.failure_reason = "Unexpected browser console, page, network, or HTTP error";
    }
    await page.screenshot({ path: path.join(outputDir, "login-shared-manager-route.png"), fullPage: true });
    await fs.writeFile(path.join(outputDir, "login-shared-manager-route-report.json"), `${JSON.stringify(result, null, 2)}\n`);

    await page.reload({ waitUntil: "networkidle" });
    await page.locator("#login-LoginPageRoot").waitFor({ state: "visible", timeout: 30000 });
    const refreshedSha = await page.locator("[data-page-workspace-root]").getAttribute("data-source-sha");
    if (refreshedSha !== sourceSha) throw new Error(`LOGIN_SHARED_MANAGER_REFRESH_SHA_MISMATCH ${refreshedSha}`);
    result.direct_route_refresh = "PASS";
    result.generated_at = now();
    await fs.writeFile(path.join(outputDir, "login-shared-manager-route-report.json"), `${JSON.stringify(result, null, 2)}\n`);
    if (result.status !== "PASS") throw new Error(`LOGIN_SHARED_MANAGER_BROWSER_ERRORS ${JSON.stringify(errors)}`);
    console.log(JSON.stringify({ gate_id: result.gate_id, status: result.status, source_sha: sourceSha, run_id: runId,
      tree_to_dom: result.tree_to_dom_and_inspector, controls: result.candidate_viewport_font_and_state_controls,
      direct_route_refresh: result.direct_route_refresh, screenshot: "login-shared-manager-route.png" }));
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
}

main().catch(error => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
