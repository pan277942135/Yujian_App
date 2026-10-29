#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import process from "node:process";
import { spawnSync } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "../..");
const args = new Map();
for (let i = 2; i < process.argv.length; i += 1) if (process.argv[i].startsWith("--")) args.set(process.argv[i].slice(2), process.argv[i + 1]);
const sourceSha = args.get("expected-sha") || process.env.BUILD_SHA || "";
const runId = String(process.env.GITHUB_RUN_ID || "");
if (!/^[0-9a-f]{40}$/.test(sourceSha)) throw new Error("LOGIN_W5_EXACT_SOURCE_SHA_REQUIRED");
if (!/^\d+$/.test(runId)) throw new Error("LOGIN_W5_GITHUB_RUN_ID_REQUIRED");
const outputDir = path.resolve(args.get("output-dir") || path.join(process.cwd(), "login-structured-hifi"));
const assetRoot = path.resolve(args.get("root") || repoRoot);
const matrixPath = "design/pages/account_privacy/Login/Login_Responsive_Acceptance_Matrix_V1.json";
const supplementalPath = "design/pages/account_privacy/Login/structured_candidate/responsive_candidate_v1.json";
const mappingPath = "design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json";
const bindingPath = "design/pages/account_privacy/Login/Page_Resource_Binding_V1.json";
const timeoutMs = 3000;
const now = () => new Date().toISOString();
const sha256 = bytes => crypto.createHash("sha256").update(bytes).digest("hex");
const mime = new Map([
  [".html", "text/html; charset=utf-8"], [".css", "text/css; charset=utf-8"], [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"], [".md", "text/markdown; charset=utf-8"], [".png", "image/png"], [".svg", "image/svg+xml"]
]);

function flatten(node, parentId, out) {
  out.push({ node_id: node.nodeId, parent_id: parentId });
  for (const child of node.children || []) flatten(child, node.nodeId, out);
  return out;
}

function createServer() {
  return http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url || "/", "http://127.0.0.1");
      let relative = decodeURIComponent(url.pathname).replace(/^\/+/, "");
      if (relative === "design/manager" || relative === "design/manager/") relative = "design/manager/index.html";
      if (relative === "design/manager/build.json") {
        res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
        res.end(JSON.stringify({ sha: sourceSha, short_sha: sourceSha.slice(0, 12) }));
        return;
      }
      const file = path.resolve(assetRoot, relative || "index.html");
      if (!file.startsWith(assetRoot + path.sep)) {
        res.writeHead(403).end("FORBIDDEN");
        return;
      }
      let bytes = await fs.readFile(file);
      if (relative === "design/manager/index.html") {
        bytes = Buffer.from(bytes.toString("utf8")
          .replaceAll("__DESIGN_MANAGER_BUILD__", sourceSha)
          .replaceAll("__DESIGN_MANAGER_BUILD_SHORT__", sourceSha.slice(0, 12)));
      }
      res.writeHead(200, { "content-type": mime.get(path.extname(file).toLowerCase()) || "application/octet-stream",
        "content-length": bytes.length, "cache-control": "no-store" }).end(bytes);
    } catch (error) {
      const status = error && error.code === "ENOENT" ? 404 : 500;
      res.writeHead(status, { "content-type": "text/plain; charset=utf-8" }).end(status === 404 ? "NOT_FOUND" : String(error));
    }
  });
}

async function readAuthority(relative) {
  const bytes = await fs.readFile(path.join(assetRoot, relative));
  return { doc: JSON.parse(bytes.toString("utf8")), bytes, sha: sha256(bytes) };
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  for (const sub of ["screenshots", "states", "negative", "dom", "visual"]) await fs.mkdir(path.join(outputDir, sub), { recursive: true });
  const [matrixFile, supplementalFile, mappingFile, bindingFile] = await Promise.all([
    readAuthority(matrixPath), readAuthority(supplementalPath), readAuthority(mappingPath), readAuthority(bindingPath)
  ]);
  const matrix = matrixFile.doc;
  const supplemental = supplementalFile.doc;
  const mapping = mappingFile.doc;
  if (matrix.page_id !== "auth_login_v2" || matrix.formal_matrix?.case_count !== 1 ||
      matrix.formal_matrix?.cases?.length !== 1 || matrix.supplemental_matrix?.expected_case_count !== 12) {
    throw new Error("LOGIN_RESPONSIVE_MATRIX_AUTHORITY_INVALID");
  }
  if (matrix.supplemental_matrix.source_path !== supplementalPath ||
      matrix.supplemental_matrix.source_sha256 !== supplementalFile.sha ||
      supplemental.page_id !== "auth_login_v2" || supplemental.viewport_matrix?.case_count !== 12) {
    throw new Error("LOGIN_SUPPLEMENTAL_MATRIX_AUTHORITY_BYTES_INVALID");
  }
  if (mapping.page_id !== "auth_login_v2" || mapping.component_tree?.nodeId !== "LoginPageRoot") throw new Error("LOGIN_COMPONENT_TREE_AUTHORITY_INVALID");
  const expectedNodes = flatten(mapping.component_tree, null, []);
  const formal = matrix.formal_matrix.cases[0];
  const cases = [{
    case_id: formal.case_id, matrix_classification: "FORMAL_AUTHORITY_CASE",
    width: formal.browser_environment.viewport_width_css_px, height: formal.browser_environment.viewport_height_css_px,
    font_scale: formal.font_scale, state_id: formal.state_id
  }];
  for (const viewport of supplemental.viewport_matrix.viewports_css_px) for (const scale of supplemental.viewport_matrix.font_scales) {
    cases.push({
      case_id: "LOGIN_SUPP_" + viewport.width + "x" + viewport.height + "_FONT_" + String(scale),
      matrix_classification: "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
      width: viewport.width, height: viewport.height, font_scale: scale, state_id: "DEFAULT"
    });
  }
  if (cases.length !== 13) throw new Error("LOGIN_FORMAL_AND_SUPPLEMENTAL_MATRIX_CASE_COUNT_INVALID");

  const server = createServer();
  await new Promise((resolve, reject) => { server.once("error", reject); server.listen(0, "127.0.0.1", resolve); });
  const origin = "http://127.0.0.1:" + server.address().port;
  const managerUrl = origin + "/design/manager/";
  const loginUrl = managerUrl + "#page/auth_login_v2/hifi/structured_workspace";
  const resourceUrl = relative => new URL(relative, origin + "/").href;
  const playwrightModuleDir = process.env.PLAYWRIGHT_MODULE_DIR;
  const require = createRequire(import.meta.url);
  const playwrightPath = playwrightModuleDir ? path.join(playwrightModuleDir, "playwright") : "playwright";
  const { chromium } = require(playwrightPath);
  const errors = { console: [], page: [], network: [], http: [] };
  let collectErrors = true;
  let browser, page;
  const rows = [], states = [], negatives = [], recoveries = [];
  let routeChecks, inspectorEvidence, interactions, domReport, visualComparison;
  try {
    browser = await chromium.launch({ headless: true });
    // Keep the Design Manager host viewport tall enough to capture the complete embedded page viewport.
    // The case dimensions are applied inside the workspace and remain independent of this host size.
    const context = await browser.newContext({ viewport: { width: 1440, height: 2400 }, deviceScaleFactor: 1 });
    page = await context.newPage();
    page.on("console", msg => { if (collectErrors && msg.type() === "error") errors.console.push(msg.text()); });
    page.on("pageerror", error => { if (collectErrors) errors.page.push(String(error.stack || error)); });
    page.on("requestfailed", request => { if (collectErrors) errors.network.push({ url: request.url(), error: request.failure()?.errorText || "unknown" }); });
    page.on("response", response => { if (collectErrors && response.status() >= 400) errors.http.push({ status: response.status(), url: response.url() }); });

    const waitLogin = async () => {
      await page.locator("#login-LoginPageRoot").waitFor({ state: "visible", timeout: 30000 });
      await page.waitForFunction(() => window.LoginV21Workspace?.getResponsiveDiagnostics?.()?.state !== "LOADING", null, { timeout: 30000 });
    };
    const openLogin = async () => { await page.goto(loginUrl, { waitUntil: "networkidle" }); await waitLogin(); };
    const apply = async (width, height, fontScale, stateId) => {
      await page.locator("[data-workspace-width]").fill(String(width));
      await page.locator("[data-workspace-height]").fill(String(height));
      await page.locator("[data-workspace-apply-viewport]").click();
      await page.locator("[data-workspace-font-scale]").selectOption(String(fontScale));
      await page.locator("[data-workspace-state]").selectOption(stateId);
      await page.waitForFunction(input => {
        const root = document.querySelector("#login-LoginPageRoot");
        const d = window.LoginV21Workspace?.getResponsiveDiagnostics?.();
        return Boolean(root && d && d.viewport?.width === input.width && d.viewport?.height === input.height &&
          d.font_scale === input.fontScale && d.state === input.stateId && root.dataset.state === input.stateId);
      }, { width, height, fontScale, stateId }, { timeout: 20000 });
    };

    await openLogin();
    const initial = await page.evaluate(async () => {
      const adapter = window.LoginV21Workspace;
      if (!adapter) throw new Error("LOGIN_SHARED_ADAPTER_NOT_EXPOSED");
      const tree = await adapter.loadComponentTree();
      const root = document.querySelector("[data-page-workspace-root]");
      return {
        page_id: adapter.pageId, source_sha: adapter.sourceSha, kind: root?.dataset.workspaceKind,
        root_id: tree.root?.nodeId, count: tree.nodes?.length || 0,
        diagnostics: adapter.getResponsiveDiagnostics(),
        background: document.querySelector("#login-MorningLakeBGContent")?.dataset.resourceStatus,
        icon: document.querySelector("#login-AccountLeadingIcon")?.dataset.resourceStatus,
        footer: document.querySelector("#login-WatersideGrassDecoration")?.dataset.resourceStatus
      };
    });
    if (initial.page_id !== "auth_login_v2" || initial.source_sha !== sourceSha ||
        initial.kind !== "structured-dom-candidate" || initial.root_id !== "LoginPageRoot" ||
        initial.count < 20 || initial.background !== "W2_RESOLVED" ||
        initial.icon !== "NOT_CONFIRMED_IN_LOGIN_W2_BINDING" || initial.footer !== "CANDIDATE_UNAPPROVED_ASSET") {
      throw new Error("LOGIN_SHARED_MANAGER_INITIAL_STATE_INVALID " + JSON.stringify(initial));
    }

    await page.locator('[data-workspace-tree-node][data-node-id="AccountInput"]').click();
    const treeSelect = await page.evaluate(() => ({
      id: document.querySelector("[data-workspace-inspector]")?.dataset.selectedNodeId || "",
      text: document.querySelector("[data-workspace-inspector]")?.innerText || ""
    }));
    if (treeSelect.id !== "AccountInput" || !treeSelect.text.includes("实际 DOM 边界")) throw new Error("LOGIN_TREE_INSPECTOR_GEOMETRY_FAILED");
    await page.locator("#login-PasswordInput").click();
    const domSelect = await page.evaluate(() => ({
      id: document.querySelector("[data-workspace-inspector]")?.dataset.selectedNodeId || "",
      rect: document.querySelector("#login-PasswordInput")?.getBoundingClientRect().toJSON() || null
    }));
    if (domSelect.id !== "PasswordInput" || !domSelect.rect?.width || !domSelect.rect?.height) throw new Error("LOGIN_DOM_INSPECTOR_GEOMETRY_FAILED");
    inspectorEvidence = {
      tree_to_inspector: treeSelect.id === "AccountInput", dom_to_inspector: domSelect.id === "PasswordInput",
      geometry_reported: Boolean(domSelect.rect.width && domSelect.rect.height), inspector_text: treeSelect.text
    };

    await page.reload({ waitUntil: "networkidle" });
    await waitLogin();
    if (await page.locator("[data-page-workspace-root]").getAttribute("data-source-sha") !== sourceSha) throw new Error("LOGIN_RELOAD_SHA_MISMATCH");
    routeChecks = { direct: "PASS", reload: "PASS", invalid: "NOT_RUN", back: "NOT_RUN", forward: "NOT_RUN" };
    await page.evaluate(() => { location.hash = "#page/home_empty_v2/hifi/structured_workspace"; });
    await page.waitForFunction(() => document.querySelector('[data-page-workspace-root][data-page-id="home_empty_v2"]') !== null, null, { timeout: 30000 });
    await page.goBack({ waitUntil: "commit" });
    await waitLogin();
    routeChecks.back = "PASS";
    await page.goForward({ waitUntil: "commit" });
    await page.waitForFunction(() => document.querySelector('[data-page-workspace-root][data-page-id="home_empty_v2"]') !== null, null, { timeout: 30000 });
    routeChecks.forward = "PASS";
    await page.evaluate(() => { location.hash = "#page/auth_login_v2/hifi/structured_workspace"; });
    await waitLogin();
    await page.evaluate(() => { location.hash = "#page/no_such_pilot_page/hifi/structured_workspace"; });
    await page.waitForFunction(() => location.hash.includes("no_such_pilot_page") && !document.querySelector("#login-LoginPageRoot"), null, { timeout: 15000 });
    routeChecks.invalid = "PASS";
    await page.evaluate(() => { location.hash = "#page/auth_login_v2/hifi/structured_workspace"; });
    await waitLogin();

    const caseDetails = [];
    for (const item of cases) {
      await apply(item.width, item.height, item.font_scale, item.state_id);
      const measured = await page.evaluate(async expected => {
        const adapter = window.LoginV21Workspace;
        const root = document.querySelector("#login-LoginPageRoot");
        if (!adapter || !root) throw new Error("LOGIN_MATRIX_WORKSPACE_NOT_MOUNTED");
        const rr = root.getBoundingClientRect();
        const dom = [root, ...root.querySelectorAll("[data-workspace-rendered-node]")];
        const byId = new Map(dom.map(element => [element.dataset.workspaceRenderedNode, element]));
        const nodeBounds = dom.map(element => {
          const r = element.getBoundingClientRect();
          return { node_id: element.dataset.workspaceRenderedNode, x: r.left - rr.left, y: r.top - rr.top,
            width: r.width, height: r.height, right: r.right - rr.left, bottom: r.bottom - rr.top,
            visible: getComputedStyle(element).display !== "none" && getComputedStyle(element).visibility !== "hidden" &&
              Number(getComputedStyle(element).opacity || 1) > 0 };
        });
        const parentChecks = expected.map(n => {
          const element = byId.get(n.node_id);
          const parent = element?.parentElement?.closest("[data-workspace-rendered-node]") || null;
          const actual = parent?.dataset.workspaceRenderedNode || null;
          return { node_id: n.node_id, expected_parent_id: n.parent_id, actual_parent_id: actual, matches: actual === n.parent_id };
        });
        const tree = await adapter.loadComponentTree();
        const title = document.querySelector("#login-WelcomeTitle");
        const action = document.querySelector("#login-LoginPrimaryButton");
        return {
          diagnostics: adapter.getResponsiveDiagnostics(), tree_count: tree.nodes?.length || 0,
          root: { width: rr.width, height: rr.height, scrollWidth: root.scrollWidth, scrollHeight: root.scrollHeight },
          node_bounds: nodeBounds, parent_checks: parentChecks,
          content: {
            title: title?.textContent?.trim() || "",
            subtitle: document.querySelector("#login-WelcomeSubtitle")?.textContent?.trim() || "",
            account_label: document.querySelector("#login-AccountLabel")?.textContent?.trim() || "",
            password_label: document.querySelector("#login-PasswordLabel")?.textContent?.trim() || "",
            forgot: document.querySelector("#login-ForgotPasswordAction")?.textContent?.trim() || "",
            primary: action?.textContent?.trim() || "",
            register: document.querySelector("#login-RegisterAction")?.textContent?.trim() || "",
            account_aria: document.querySelector("#login-AccountInput")?.getAttribute("aria-label") || "",
            password_aria: document.querySelector("#login-PasswordInput")?.getAttribute("aria-label") || "",
            title_font_px: title ? parseFloat(getComputedStyle(title).fontSize) : 0
          }
        };
      }, expectedNodes);
      const missing = expectedNodes.filter(n => !measured.node_bounds.some(x => x.node_id === n.node_id));
      const zero = measured.node_bounds.filter(x => x.visible && (x.width <= 0 || x.height <= 0));
      const overflow = measured.node_bounds.filter(x => x.visible && (x.x < -1 || x.right > item.width + 1));
      const wrongParents = measured.parent_checks.filter(x => !x.matches);
      const c = measured.content;
      const contentOk = c.title === "欢迎回来" && c.subtitle.length > 0 && c.account_label === "账号" &&
        c.password_label === "密码" && c.forgot === "忘记密码？" && c.primary.includes("登录") &&
        c.register === "创建账号" && c.account_aria === "账号" && c.password_aria === "密码";
      if (missing.length || zero.length || overflow.length || wrongParents.length || !contentOk ||
          measured.node_bounds.length < 20 || measured.root.width !== item.width || measured.root.height !== item.height ||
          measured.root.scrollWidth > item.width + 1) {
        throw new Error("LOGIN_MATRIX_ASSERTION_FAILED " + JSON.stringify({
          case_id: item.case_id, missing, zero, overflow, wrongParents, root: measured.root, content: c
        }));
      }
      const name = item.case_id.replace(/[^A-Za-z0-9_-]/g, "_") + ".png";
      const screenshotPath = path.join(outputDir, "screenshots", name);
      const loginRoot = page.locator("#login-LoginPageRoot");
      await loginRoot.scrollIntoViewIfNeeded();
      const rootBox = await loginRoot.boundingBox();
      if (!rootBox) throw new Error("LOGIN_MATRIX_SCREENSHOT_ROOT_NOT_VISIBLE " + item.case_id);
      await page.screenshot({
        path: screenshotPath,
        scale: "css",
        clip: { x: Math.round(rootBox.x), y: Math.round(rootBox.y), width: item.width, height: item.height }
      });
      const bytes = await fs.readFile(screenshotPath);
      const width = bytes.readUInt32BE(16), height = bytes.readUInt32BE(20);
      if (bytes.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a" || width !== item.width || height !== item.height) {
        throw new Error("LOGIN_MATRIX_SCREENSHOT_DIMENSIONS_INVALID " + item.case_id + " " + width + "x" + height);
      }
      const screenshotSha = sha256(bytes);
      caseDetails.push({ case_id: item.case_id, viewport: { width: item.width, height: item.height, unit: "css_px" },
        font_scale: item.font_scale, state_id: item.state_id, node_bounds: measured.node_bounds,
        parent_checks: measured.parent_checks, content: measured.content, screenshot_sha256: screenshotSha });
      rows.push({
        case_id: item.case_id, matrix_classification: item.matrix_classification,
        viewport: { width: item.width, height: item.height, unit: "css_px" },
        viewport_width: item.width, viewport_height: item.height, font_scale: item.font_scale, state_id: item.state_id,
        matrix_source_path: matrixPath, matrix_source_sha256: matrixFile.sha,
        result: "PASS", source_sha: sourceSha, run_id: runId,
        screenshot_rendered: { path: "screenshots/" + name, sha256: screenshotSha, pixel_width: width, pixel_height: height,
          coordinate_space: "browser_css_px" },
        node_bounds: measured.node_bounds,
        assertions: { component_tree_to_dom_relationships: "PASS", nonzero_visible_geometry: "PASS",
          no_horizontal_clipping: "PASS", complete_content: "PASS", viewport_font_and_state_applied: "PASS",
          browser_css_px_to_android_dp: "NOT_ASSERTED" }
      });
    }
    if (rows.filter(x => x.matrix_classification === "FORMAL_AUTHORITY_CASE").length !== 1 ||
        rows.filter(x => x.matrix_classification === "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY").length !== 12) {
      throw new Error("LOGIN_FORMAL_SUPPLEMENTAL_MATRIX_COUNTS_INVALID");
    }
    const scaleRows = caseDetails.filter(x => x.case_id.startsWith("LOGIN_SUPP_320x480_"));
    const size1 = scaleRows.find(x => x.font_scale === 1)?.content.title_font_px;
    const size15 = scaleRows.find(x => x.font_scale === 1.5)?.content.title_font_px;
    if (!(size15 > size1)) throw new Error("LOGIN_FONT_SCALE_GEOMETRY_DID_NOT_INCREASE");
    domReport = {
      schema: "YUJIAN_LOGIN_DOM_GEOMETRY_REPORT_V1", page_id: "auth_login_v2", source_sha: sourceSha, run_id: runId,
      browser_engine: "Chromium", implementation: "existing shared Design Manager structured workspace",
      component_tree_source: mappingPath, component_tree_sha256: mappingFile.sha, actual_dom_component_count: expectedNodes.length,
      exact_parent_relationships: "PASS", selection_inspector: inspectorEvidence, matrix_cases: caseDetails,
      font_scale_probe: { font_1_title_css_px: size1, font_1_5_title_css_px: size15, result: "PASS" },
      safe_area_owner: "NOT_VERIFIED", native_ime_and_scroll: "NOT_VERIFIED", generated_at: now()
    };
    await fs.writeFile(path.join(outputDir, "dom/login-dom-structure-geometry.json"), JSON.stringify(domReport, null, 2) + "\n");

    for (const stateId of ["DEFAULT", "ACCOUNT_FOCUS", "PASSWORD_FOCUS", "PASSWORD_SHOWN", "PASSWORD_HIDDEN",
      "INPUT_ERROR", "LOADING", "KEYBOARD_SIMULATION", "ACTION_DISABLED"]) {
      await apply(360, 640, 1, stateId);
      const seen = await page.evaluate(() => {
        const q = selector => document.querySelector(selector);
        return { state: q("#login-LoginPageRoot")?.dataset.state || "",
          account_focus: q("#login-AccountField")?.dataset.focus === "true",
          password_focus: q("#login-PasswordField")?.dataset.focus === "true",
          password_type: q("#login-PasswordInput")?.type || "", error: Boolean(q("[data-login-error]")),
          disabled: Boolean(q("#login-LoginPrimaryButton")?.disabled),
          submit_text: q("#login-LoginPrimaryButton")?.textContent?.trim() || "",
          keyboard: Boolean(q("#login-keyboard-overlay") && !q("#login-keyboard-overlay").hidden) };
      });
      const pass = {
        DEFAULT: seen.state === "DEFAULT" && seen.password_type === "password" && !seen.error && !seen.disabled,
        ACCOUNT_FOCUS: seen.state === "ACCOUNT_FOCUS" && seen.account_focus,
        PASSWORD_FOCUS: seen.state === "PASSWORD_FOCUS" && seen.password_focus,
        PASSWORD_SHOWN: seen.state === "PASSWORD_SHOWN" && seen.password_type === "text",
        PASSWORD_HIDDEN: seen.state === "PASSWORD_HIDDEN" && seen.password_type === "password",
        INPUT_ERROR: seen.state === "INPUT_ERROR" && seen.error,
        LOADING: seen.state === "LOADING" && seen.disabled && seen.submit_text.includes("登录中"),
        KEYBOARD_SIMULATION: seen.state === "KEYBOARD_SIMULATION" && seen.keyboard && seen.password_focus,
        ACTION_DISABLED: seen.state === "ACTION_DISABLED" && seen.disabled
      }[stateId];
      if (!pass) throw new Error("LOGIN_STATE_PROBE_FAILED " + stateId + " " + JSON.stringify(seen));
      const stateImage = path.join(outputDir, "states", stateId + ".png");
      await page.locator("#login-LoginPageRoot").screenshot({ path: stateImage, scale: "css" });
      states.push({ state: stateId, result: "PASS", observed: seen, screenshot_path: "states/" + stateId + ".png",
        screenshot_sha256: sha256(await fs.readFile(stateImage)) });
    }

    await apply(360, 640, 1, "DEFAULT");
    await page.locator("#login-AccountInput").fill("candidate_user");
    await page.locator("#login-PasswordInput").fill("candidate-password");
    await page.locator("#login-VisibilityAction").click();
    const shown = await page.locator("#login-PasswordInput").getAttribute("type");
    await page.locator("#login-VisibilityAction").click();
    const hidden = await page.locator("#login-PasswordInput").getAttribute("type");
    await page.evaluate(() => {
      window.__loginSubmitProbe = { observed: false, prevented: false };
      document.querySelector("#login-LoginForm").addEventListener("submit", event => {
        window.__loginSubmitProbe.observed = true;
        queueMicrotask(() => { window.__loginSubmitProbe.prevented = event.defaultPrevented; });
      }, { once: true });
    });
    const beforeSubmit = page.url();
    await page.locator("#login-LoginPrimaryButton").click();
    await page.waitForFunction(() => window.__loginSubmitProbe?.observed === true);
    const submitPrevented = await page.evaluate(() => window.__loginSubmitProbe?.prevented === true);
    interactions = { accountInput: true, passwordInput: true, visibilityShown: shown === "text",
      visibilityHidden: hidden === "password", localSubmitPrevented: submitPrevented && page.url() === beforeSubmit };
    if (!Object.values(interactions).every(Boolean)) throw new Error("LOGIN_FORM_INTERACTION_FAILED " + JSON.stringify(interactions));
    await page.locator("#login-AccountInput").fill("");
    await page.locator("#login-PasswordInput").fill("");

    await page.setViewportSize({ width: 1440, height: 1700 });
    await apply(864, 1536, 1, "DEFAULT");
    const renderPath = path.join(outputDir, "visual/login-v2_1-structured-render-864x1536.png");
    await page.locator("#login-LoginPageRoot").screenshot({ path: renderPath, scale: "css" });
    const renderBytes = await fs.readFile(renderPath);
    if (renderBytes.readUInt32BE(16) !== 864 || renderBytes.readUInt32BE(20) !== 1536) throw new Error("LOGIN_VISUAL_RENDER_NATIVE_SIZE_INVALID");
    const comparison = spawnSync("python3", [
      path.join(repoRoot, "scripts/design/compare_login_candidate_visual.py"),
      "--reference", path.join(assetRoot, "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png"),
      "--render", renderPath, "--output-dir", path.join(outputDir, "visual"), "--source-sha", sourceSha, "--run-id", runId
    ], { encoding: "utf8" });
    if (comparison.status !== 0) throw new Error("LOGIN_VISUAL_COMPARISON_FAILED " + (comparison.stderr || comparison.stdout));
    const compareReportBytes = await fs.readFile(path.join(outputDir, "visual/login-v2_1-visual-comparison-report.json"));
    const compareReport = JSON.parse(compareReportBytes.toString("utf8"));
    if (compareReport.source_sha !== sourceSha || String(compareReport.run_id) !== runId ||
        compareReport.comparison?.interpretation !== "MEASUREMENT_ONLY_NOT_VISUAL_PASS") throw new Error("LOGIN_VISUAL_COMPARISON_REPORT_INVALID");
    visualComparison = {
      report_path: "visual/login-v2_1-visual-comparison-report.json", report_sha256: sha256(compareReportBytes),
      render_sha256: sha256(renderBytes),
      comparison: { overlay_path: compareReport.comparison.overlay_path, difference_path: compareReport.comparison.difference_path,
        frozen_reference_copy_path: compareReport.comparison.frozen_reference_copy_path,
        changed_pixel_count: compareReport.comparison.changed_pixel_count,
        changed_pixel_fraction: compareReport.comparison.changed_pixel_fraction,
        mean_absolute_channel_error: compareReport.comparison.mean_absolute_channel_error,
        interpretation: compareReport.comparison.interpretation }
    };

    collectErrors = false;
    const builtInNegatives = await page.evaluate(async input =>
      window.LoginV21Candidate.validateNegativeCases(input.binding, relative => new URL(relative, input.origin + "/").href),
      { binding: { binding: bindingFile.doc }, origin });
    const byId = new Map(builtInNegatives.map(row => [row.id, row]));
    const negativeBindings = [
      ["bound-background-404", "missing_file"], ["bound-background-wrong-sha", "wrong_sha"],
      ["bound-background-wrong-dimensions", "wrong_dimensions"], ["bound-background-wrong-mime", "wrong_mime"],
      ["bound-background-publication-denied", "private_permission"], ["missing-publication-authority", "missing_permission"],
      ["frozen-page-image-as-background", "reference_image_as_rendered_background"]
    ];
    for (const [caseId, internalId] of negativeBindings) {
      const result = byId.get(internalId);
      if (!result || result.result !== "EXPECTED_FAIL") throw new Error("LOGIN_BOUND_ASSET_NEGATIVE_FAILED " + caseId);
      negatives.push({ case_id: caseId, expected: "FAIL_CLOSED", result: "EXPECTED_FAIL_CLOSED", observed_error: result.observed });
    }
    await page.goto(managerUrl + "#page/invalid-route-probe/hifi/structured_workspace", { waitUntil: "networkidle" });
    if (!await page.evaluate(() => !document.querySelector("#login-LoginPageRoot"))) throw new Error("LOGIN_INVALID_ROUTE_NEGATIVE_FAILED");
    negatives.push({ case_id: "invalid-manager-route", expected: "FAIL_CLOSED", result: "EXPECTED_FAIL_CLOSED",
      observed_error: "no Login DOM mounted for an unregistered route" });

    const mappingUrl = resourceUrl(mappingPath);
    const badJsonHandler = async route => route.fulfill({ status: 200, contentType: "application/json", body: "{" });
    await page.route(url => url.href === mappingUrl, badJsonHandler);
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(150);
    const badJsonState = await page.evaluate(() => ({
      rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
      text: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ") + " " + document.body.innerText
    }));
    if (badJsonState.rootCount !== 0 || !/JSON_INVALID|JSON|错误|工作区/i.test(badJsonState.text)) {
      throw new Error("LOGIN_INVALID_JSON_NEGATIVE_FAILED " + JSON.stringify(badJsonState));
    }
    await page.unroute(url => url.href === mappingUrl);
    await page.goto(loginUrl, { waitUntil: "networkidle" });
    await waitLogin();
    recoveries.push({ case_id: "invalid-mapping-json-and-recovery", result: "EXPECTED_FAIL_CLOSED_AND_RECOVERED",
      recovery: "PASS", failure_observed: true, recovery_source_sha: sourceSha });
    negatives.push({ case_id: "invalid-resource-json", expected: "FAIL_CLOSED", result: "EXPECTED_FAIL_CLOSED",
      observed_error: badJsonState.text.slice(0, 240) });

    const timeoutHandler = async route => {
      await new Promise(resolve => setTimeout(resolve, timeoutMs + 800));
      try { await route.continue(); } catch { /* Browser-side timeout closed this request. */ }
    };
    await page.route(url => url.href === mappingUrl, timeoutHandler);
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(timeoutMs + 300);
    const timeoutState = await page.evaluate(() => ({
      rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
      text: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ") + " " + document.body.innerText
    }));
    if (timeoutState.rootCount !== 0 || !/TIMEOUT|超时/i.test(timeoutState.text)) throw new Error("LOGIN_TIMEOUT_NEGATIVE_FAILED " + JSON.stringify(timeoutState));
    await page.unroute(url => url.href === mappingUrl);
    await page.goto(loginUrl, { waitUntil: "networkidle" });
    await waitLogin();
    recoveries.push({ case_id: "mapping-timeout-and-recovery", result: "EXPECTED_FAIL_CLOSED_AND_RECOVERED",
      recovery: "PASS", failure_observed: true, timeout_ms: timeoutMs, recovery_source_sha: sourceSha });
    negatives.push({ case_id: "resource-timeout-and-recovery", expected: "FAIL_CLOSED", result: "EXPECTED_FAIL_CLOSED",
      observed_error: timeoutState.text.slice(0, 240) });

    const backgroundPath = "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png";
    const backgroundUrl = resourceUrl(backgroundPath);
    await page.route(url => url.href === backgroundUrl, route => route.fulfill({ status: 404, body: "FAIL_CLOSED_TEST_404" }));
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(250);
    const backgroundState = await page.evaluate(() => ({
      rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
      error: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ")
    }));
    if (backgroundState.rootCount > 0 && !/HTTP_ERROR|404|资源|加载/i.test(backgroundState.error)) {
      throw new Error("LOGIN_REQUIRED_BACKGROUND_404_NEGATIVE_FAILED " + JSON.stringify(backgroundState));
    }
    await page.unroute(url => url.href === backgroundUrl);
    await page.goto(loginUrl, { waitUntil: "networkidle" });
    await waitLogin();
    recoveries.push({ case_id: "bound-background-404", result: "EXPECTED_FAIL_CLOSED_AND_RECOVERED",
      recovery: "PASS", failure_observed: true, recovery_source_sha: sourceSha });
    negatives.push({ case_id: "bound-background-404", expected: "FAIL_CLOSED", result: "EXPECTED_FAIL_CLOSED",
      observed_error: backgroundState.error || "required background did not mount under the 404 probe" });

    const requiredNegativeIds = new Set([
      "bound-background-404", "bound-background-wrong-sha", "bound-background-wrong-dimensions",
      "bound-background-wrong-mime", "bound-background-publication-denied", "missing-publication-authority",
      "frozen-page-image-as-background", "invalid-manager-route", "invalid-resource-json", "resource-timeout-and-recovery"
    ]);
    if (negatives.length !== 10 || new Set(negatives.map(x => x.case_id)).size !== 10 ||
        negatives.some(x => x.result !== "EXPECTED_FAIL_CLOSED" || !requiredNegativeIds.has(x.case_id)) || recoveries.length !== 3) {
      throw new Error("LOGIN_NEGATIVE_OR_RECOVERY_SET_INCOMPLETE");
    }
    const unresolvedResources = (bindingFile.doc.asset_bindings || [])
      .filter(row => ["MISSING", "NOT_VERIFIED"].includes(row.lifecycle_status))
      .map(row => ({ asset_id: row.asset_id, lifecycle_status: row.lifecycle_status, requiredness: row.requiredness }));
    const report = {
      schema: "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", page_id: "auth_login_v2", authority_version: "V2.1",
      status: "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING", source_sha: sourceSha, candidate_source_sha: sourceSha,
      github_run_id: runId, generated_at: now(), browser_engine: "Chromium", browser_version: await browser.version(), route: loginUrl,
      matrix_authority: { id: "login-responsive-matrix", path: matrixPath, sha256: matrixFile.sha, status: matrix.status },
      matrix_summary: {
        source_path: matrixPath, source_sha256: matrixFile.sha,
        expected: 1, executed: 1, pass: 1, fail: 0, not_run: 0,
        formal_expected: 1, formal_executed: 1, formal_pass: 1, formal_fail: 0, formal_not_run: 0,
        supplemental_expected: 12, supplemental_executed: 12, supplemental_pass: 12, supplemental_fail: 0, supplemental_not_run: 0,
        total_expected: 13, total_executed: 13, total_pass: 13, total_fail: 0, total_not_run: 0
      },
      supplemental_matrix_definition: { status: "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
        source_path: supplementalPath, source_sha256: supplementalFile.sha, expected_case_count: 12 },
      matrix: rows, dom_report: "dom/login-dom-structure-geometry.json", state_probes: states,
      negative_cases: negatives, resource_load_failures_and_recovery: recoveries, route_checks: routeChecks,
      selection_inspector: inspectorEvidence, form_interactions: interactions,
      gate_results: {
        W2: { status: "REVIEW_REQUIRED", source_sha: sourceSha, run_id: runId, binding_path: bindingPath,
          binding_sha256: bindingFile.sha, unresolved_resources: unresolvedResources,
          shared_icon_publication: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING", no_status_promotion: true },
        W3: { status: "PASS", source_sha: sourceSha, run_id: runId, component_tree_measured_nodes: expectedNodes.length,
          actual_dom_geometry: "PASS", parent_relationships: "PASS", selection_inspector: "PASS" },
        W4: { status: "PASS", source_sha: sourceSha, run_id: runId, chromium: "PASS", failure_recovery: "PASS" },
        W5: { status: "PASS", source_sha: sourceSha, run_id: runId, formal_expected: 1, formal_pass: 1,
          supplemental_expected: 12, supplemental_pass: 12, negative_expected: 10, negative_pass: 10,
          frozen_comparison: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", visual_acceptance: "USER_VISUAL_ACCEPTANCE_PENDING" }
      },
      visual_comparison: visualComparison, browser_errors: errors.console.concat(errors.page, errors.http),
      network_failures: errors.network,
      resource_status: { background: "FROZEN_AND_RESOLVED", shared_icons: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING",
        footer_grass: "NOT_VERIFIED", approved_upload_source: "MISSING_OPTIONAL", safe_area_owner: "NOT_VERIFIED" },
      visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING", user_acceptance_status: "USER_VISUAL_ACCEPTANCE_PENDING",
      native_runtime_status: "NOT_VERIFIED", implementation_readiness: "BLOCKED_PENDING_RESOURCE_AND_SAFE_AREA_REVIEW"
    };
    await page.locator("#login-LoginPageRoot").screenshot({ path: path.join(outputDir, "screenshots/login-shared-manager-final.png"), scale: "css" }).catch(() => {});
    await fs.writeFile(path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json"), JSON.stringify(report, null, 2) + "\n");
    await fs.writeFile(path.join(outputDir, "Login_W4_Run_Summary.json"), JSON.stringify({
      source_sha: sourceSha, run_id: runId, status: report.status, formal: "1/1 PASS",
      supplemental: "12/12 PASS", negative: "10/10 EXPECTED_FAIL_CLOSED",
      W2: report.gate_results.W2.status, W3: "PASS", W4: "PASS", W5: "PASS",
      visual_review: report.visual_review_status, native_runtime: report.native_runtime_status
    }, null, 2) + "\n");
    if (errors.console.length || errors.page.length || errors.network.length || errors.http.length) {
      throw new Error("LOGIN_PRIMARY_CHROMIUM_ERRORS " + JSON.stringify(errors));
    }
    console.log(JSON.stringify({ page_id: report.page_id, source_sha: sourceSha, run_id: runId, status: report.status,
      formal: "1/1", supplemental: "12/12", negative: "10/10", W2: "REVIEW_REQUIRED", W3: "PASS", W4: "PASS", W5: "PASS" }));
  } catch (error) {
    const failure = { schema: "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", page_id: "auth_login_v2", status: "FAIL",
      source_sha: sourceSha, candidate_source_sha: sourceSha, github_run_id: runId, generated_at: now(),
      failure_reason: error?.stack || String(error),
      partial_counts: { matrix_cases: rows.length, state_probes: states.length, negative_cases: negatives.length, recovery_cases: recoveries.length },
      browser_errors: errors.console.concat(errors.page, errors.http), network_failures: errors.network,
      visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING", native_runtime_status: "NOT_VERIFIED" };
    await fs.writeFile(path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json"), JSON.stringify(failure, null, 2) + "\n");
    if (page && !page.isClosed()) {
      try {
        failure.failure_url = page.url();
        failure.dom_snapshot = await page.evaluate(() => ({
          body_text: document.body.innerText.slice(0, 3000),
          login_root_count: document.querySelectorAll("#login-LoginPageRoot").length,
          workspace_error: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ")
        }));
        await page.screenshot({ path: path.join(outputDir, "negative/login-w5-failure.png"), fullPage: true });
        await fs.writeFile(path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json"), JSON.stringify(failure, null, 2) + "\n");
      } catch {}
    }
    throw error;
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
}

main().catch(error => { console.error(error?.stack || error); process.exitCode = 2; });
