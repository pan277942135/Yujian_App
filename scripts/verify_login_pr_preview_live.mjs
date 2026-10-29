#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";

const args = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  if (process.argv[index].startsWith("--")) args.set(process.argv[index].slice(2), process.argv[index + 1]);
}
const baseUrl = args.get("url");
const expectedBuild = args.get("expected-build");
const outputDir = path.resolve(args.get("output-dir") || "/tmp/login-pr-preview-evidence");
if (!baseUrl || !/^[0-9a-f]{40}$/.test(expectedBuild || "")) throw new Error("LOGIN_PREVIEW_URL_AND_EXACT_BUILD_REQUIRED");
const playwrightModuleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const require = createRequire(import.meta.url);
const playwrightPath = playwrightModuleRoot ? path.join(playwrightModuleRoot, "playwright") : "playwright";
const { chromium } = require(playwrightPath);
const errors = { console: [], page: [], network: [], http: [] };
const unapprovedIconRequests = [];

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  const route = new URL("design/manager/#page/auth_login_v2/hifi/structured_workspace", baseUrl).href;
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("console", message => { if (message.type() === "error") errors.console.push(message.text()); });
    page.on("pageerror", error => errors.page.push(String(error?.stack || error)));
    page.on("requestfailed", request => errors.network.push({ url: request.url(), error: request.failure()?.errorText || "unknown" }));
    page.on("response", response => { if (response.status() >= 400) errors.http.push({ status: response.status(), url: response.url() }); });
    page.on("request", request => {
      if (/ic_(person|lock|visibility|visibility_off)_v1\.svg/i.test(request.url())) unapprovedIconRequests.push(request.url());
    });
    await page.goto(route, { waitUntil: "networkidle" });
    await page.locator("[data-page-workspace-root][data-page-id='auth_login_v2']").waitFor({ state: "visible", timeout: 30000 });
    const observation = await page.evaluate(() => {
      const root = document.querySelector("[data-page-workspace-root][data-page-id='auth_login_v2']");
      return {
        build_sha: document.querySelector('meta[name="design-manager-build"]')?.content,
        source_sha: root?.dataset.sourceSha,
        view_state: root?.dataset.viewState,
        workspace_kind: root?.dataset.workspaceKind,
        visible_status: root?.querySelector("[data-workspace-error]")?.textContent,
        candidate_renderer_nodes: document.querySelectorAll("#login-LoginPageRoot").length
      };
    });
    const result = {
      gate_id: "W6_LOGIN_PUBLIC_PREVIEW_PERMISSION_GATE",
      status: "PASS_FAIL_CLOSED",
      page_id: "auth_login_v2",
      exact_build_sha: expectedBuild,
      route,
      observation,
      resource_permission: "SHARED_ICON_PUBLICATION_NOT_CONFIRMED; no icon bytes requested",
      unapproved_icon_requests: unapprovedIconRequests,
      browser_errors: errors,
      user_visual_acceptance: "PENDING",
      native_runtime: "NOT_VERIFIED",
      generated_at: new Date().toISOString()
    };
    if (observation.build_sha !== expectedBuild || observation.source_sha !== expectedBuild ||
        observation.view_state !== "BLOCKED_RESOURCE_PERMISSION" ||
        observation.workspace_kind !== "blocked-login-candidate" ||
        !String(observation.visible_status || "").includes("PRIVATE_PREVIEW_REQUIRED") ||
        observation.candidate_renderer_nodes !== 0 || unapprovedIconRequests.length ||
        errors.console.length || errors.page.length || errors.network.length || errors.http.length) {
      result.status = "FAIL";
      result.failure_reason = "Live Login route did not preserve the exact build and fail closed before requesting unapproved resources";
    }
    await page.screenshot({ path: path.join(outputDir, "login-public-preview-permission-gate.png"), fullPage: true });
    await fs.writeFile(path.join(outputDir, "login-public-preview-permission-gate.json"), `${JSON.stringify(result, null, 2)}\n`);
    console.log(JSON.stringify({ gate_id: result.gate_id, status: result.status, source_sha: expectedBuild,
      unapproved_icon_requests: unapprovedIconRequests.length, route: result.route }));
    if (result.status !== "PASS_FAIL_CLOSED") throw new Error(JSON.stringify(result));
  } finally {
    await browser?.close();
  }
}

main().catch(error => { console.error(error?.stack || error); process.exitCode = 1; });
