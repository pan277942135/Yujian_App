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
  const rows = [], states = [], negatives = [], recoveries = [], matrixFailures = [], suiteFailures = [];
  let routeChecks, inspectorEvidence, interactions, domReport, visualComparison, evidenceWritten = false;
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
    await page.evaluate(() => { location.hash = "#page/no_such_pilot_page/hifi/structured_workspace"; });
    await page.waitForFunction(() => location.hash.includes("no_such_pilot_page") &&
      !document.querySelector("[data-page-workspace-root]"), null, { timeout: 15000 });
    routeChecks.invalid = "PASS";
    // Restore the manager through the already-verified direct route after the fail-closed invalid-route check.
    await openLogin();

    const caseDetails = [];
    for (const item of cases) {
      let measured = null;
      let caseResult = "PASS";
      let caseFailure = null;
      let screenshotRendered = null;
      let expandedArtboardScreenshot = null;
      try {
        await apply(item.width, item.height, item.font_scale, item.state_id);
        measured = await page.evaluate(async expected => {
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
          const canvas = document.querySelector("#hifiViewCanvas");
          const canvasRect = canvas?.getBoundingClientRect() || null;
          return {
            diagnostics: adapter.getResponsiveDiagnostics(), tree_count: tree.nodes?.length || 0,
            root: { width: rr.width, height: rr.height, scrollWidth: root.scrollWidth, scrollHeight: root.scrollHeight },
            canvas: { overflow_y: canvas ? getComputedStyle(canvas).overflowY : "", scroll_height: canvas?.scrollHeight || 0,
              client_height: canvas?.clientHeight || 0, artboard_extent: canvasRect ? rr.bottom - canvasRect.top : 0,
              covers_artboard: Boolean(canvas && canvasRect && canvas.scrollHeight + 1 >= rr.bottom - canvasRect.top) },
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
        const verticalOverflow = measured.node_bounds.filter(x => x.visible && (x.y < -1 || x.bottom > measured.root.height + 1));
        const wrongParents = measured.parent_checks.filter(x => !x.matches);
        const c = measured.content;
        const contentOk = c.title === "欢迎回来" && c.subtitle.length > 0 && c.account_label === "账号" &&
          c.password_label === "密码" && c.forgot === "忘记密码？" && c.primary.includes("登录") &&
          c.register === "创建账号" && c.account_aria === "账号" && c.password_aria === "密码";
        const heightRuleOk = measured.root.height + 1 >= item.height &&
          measured.root.scrollHeight <= measured.root.height + 1 &&
          verticalOverflow.length === 0 &&
          ["auto", "scroll"].includes(measured.canvas.overflow_y) &&
          measured.canvas.covers_artboard;
        if (missing.length || zero.length || overflow.length || wrongParents.length || !contentOk ||
            measured.node_bounds.length < 20 || measured.root.width !== item.width ||
            measured.root.scrollWidth > item.width + 1 || !heightRuleOk) {
          throw new Error("LOGIN_MATRIX_ASSERTION_FAILED " + JSON.stringify({
            case_id: item.case_id, missing, zero, overflow, verticalOverflow, wrongParents, root: measured.root,
            canvas: measured.canvas, content: c, height_rule: heightRuleOk
          }));
        }
      } catch (error) {
        caseResult = "FAIL";
        caseFailure = error?.message || String(error);
        matrixFailures.push({ case_id: item.case_id, source_sha: sourceSha, failure_reason: caseFailure });
      }

      const name = item.case_id.replace(/[^A-Za-z0-9_-]/g, "_") + ".png";
      const screenshotPath = path.join(outputDir, "screenshots", name);
      const loginRoot = page.locator("#login-LoginPageRoot");
      try {
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
        screenshotRendered = { path: "screenshots/" + name, sha256: sha256(bytes), pixel_width: width, pixel_height: height,
          coordinate_space: "browser_css_px" };
      } catch (error) {
        caseResult = "FAIL";
        caseFailure = [caseFailure, error?.message || String(error)].filter(Boolean).join(" | ");
        matrixFailures.push({ case_id: item.case_id, source_sha: sourceSha, failure_reason: error?.message || String(error) });
      }
      if (measured && measured.root.height > item.height + 1) {
        try {
          const expandedPath = path.join(outputDir, "screenshots", name.replace(".png", "_expanded_artboard.png"));
          await loginRoot.screenshot({ path: expandedPath, scale: "css" });
          const bytes = await fs.readFile(expandedPath);
          expandedArtboardScreenshot = { path: "screenshots/" + path.basename(expandedPath), sha256: sha256(bytes),
            pixel_width: bytes.readUInt32BE(16), pixel_height: bytes.readUInt32BE(20) };
        } catch (error) {
          caseResult = "FAIL";
          caseFailure = [caseFailure, error?.message || String(error)].filter(Boolean).join(" | ");
          matrixFailures.push({ case_id: item.case_id, source_sha: sourceSha, failure_reason: "EXPANDED_ARTBOARD_SCREENSHOT_FAILED " + (error?.message || String(error)) });
        }
      }

      const detail = { case_id: item.case_id, matrix_classification: item.matrix_classification, result: caseResult,
        viewport: { width: item.width, height: item.height, unit: "css_px" }, font_scale: item.font_scale, state_id: item.state_id,
        node_bounds: measured?.node_bounds || [], parent_checks: measured?.parent_checks || [], content: measured?.content || null,
        root_geometry: measured?.root || null, scroll_container: measured?.canvas || null,
        screenshot_sha256: screenshotRendered?.sha256 || null, screenshot: screenshotRendered,
        expanded_artboard_screenshot: expandedArtboardScreenshot, failure_reason: caseFailure };
      caseDetails.push(detail);
      rows.push({
        case_id: item.case_id, matrix_classification: item.matrix_classification,
        viewport: { width: item.width, height: item.height, unit: "css_px" },
        viewport_width: item.width, viewport_height: item.height, font_scale: item.font_scale, state_id: item.state_id,
        matrix_source_path: matrixPath, matrix_source_sha256: matrixFile.sha,
        result: caseResult, failure_reason: caseFailure, source_sha: sourceSha, run_id: runId,
        screenshot_rendered: screenshotRendered, expanded_artboard_screenshot: expandedArtboardScreenshot,
        node_bounds: measured?.node_bounds || [],
        assertions: { component_tree_to_dom_relationships: measured && !measured.parent_checks.some(x => !x.matches) ? "PASS" : "FAIL",
          nonzero_visible_geometry: measured && measured.node_bounds.length >= 20 && !measured.node_bounds.some(x => x.visible && (x.width <= 0 || x.height <= 0)) ? "PASS" : "FAIL",
          no_horizontal_clipping: measured && measured.root.width === item.width && measured.root.scrollWidth <= item.width + 1 ? "PASS" : "FAIL",
          complete_content: measured && measured.content?.primary?.includes("登录") && measured.content?.register === "创建账号" ? "PASS" : "FAIL",
          viewport_font_and_state_applied: measured && measured.diagnostics?.font_scale === item.font_scale && measured.diagnostics?.state === item.state_id ? "PASS" : "FAIL",
          expanded_artboard_and_vertical_overflow_owner: measured && measured.root.height + 1 >= item.height &&
            measured.root.scrollHeight <= measured.root.height + 1 && measured.canvas?.covers_artboard &&
            ["auto", "scroll"].includes(measured.canvas?.overflow_y) ? "PASS" : "FAIL",
          browser_css_px_to_android_dp: "NOT_ASSERTED" }
      });
    }
    if (rows.filter(x => x.matrix_classification === "FORMAL_AUTHORITY_CASE").length !== 1 ||
        rows.filter(x => x.matrix_classification === "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY").length !== 12) {
      suiteFailures.push({ scope: "matrix_case_counts", expected_formal: 1, expected_supplemental: 12,
        actual_formal: rows.filter(x => x.matrix_classification === "FORMAL_AUTHORITY_CASE").length,
        actual_supplemental: rows.filter(x => x.matrix_classification === "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY").length });
    }
    const scaleRows = caseDetails.filter(x => x.case_id.startsWith("LOGIN_SUPP_320x480_"));
    const size1 = scaleRows.find(x => x.font_scale === 1)?.content.title_font_px;
    const size15 = scaleRows.find(x => x.font_scale === 1.5)?.content.title_font_px;
    const fontScalePass = Number.isFinite(size1) && Number.isFinite(size15) && size15 > size1;
    if (!fontScalePass) suiteFailures.push({ scope: "font_scale_probe", size_1: size1, size_1_5: size15,
      failure_reason: "LOGIN_FONT_SCALE_GEOMETRY_DID_NOT_INCREASE" });
    domReport = {
      schema: "YUJIAN_LOGIN_DOM_GEOMETRY_REPORT_V1", page_id: "auth_login_v2", source_sha: sourceSha, run_id: runId,
      browser_engine: "Chromium", implementation: "existing shared Design Manager structured workspace",
      component_tree_source: mappingPath, component_tree_sha256: mappingFile.sha, actual_dom_component_count: expectedNodes.length,
      exact_parent_relationships: matrixFailures.some(x => /parent|tree/i.test(x.failure_reason || "")) ? "FAIL" : "PASS",
      selection_inspector: inspectorEvidence, matrix_cases: caseDetails,
      font_scale_probe: { font_1_title_css_px: size1, font_1_5_title_css_px: size15, result: fontScalePass ? "PASS" : "FAIL" },
      safe_area_owner: "NOT_VERIFIED", native_ime_and_scroll: "NOT_VERIFIED", generated_at: now()
    };
    await fs.writeFile(path.join(outputDir, "dom/login-dom-structure-geometry.json"), JSON.stringify(domReport, null, 2) + "\n");

    for (const stateId of ["DEFAULT", "ACCOUNT_FOCUS", "PASSWORD_FOCUS", "PASSWORD_SHOWN", "PASSWORD_HIDDEN",
      "INPUT_ERROR", "LOADING", "KEYBOARD_SIMULATION", "ACTION_DISABLED"]) {
      let seen = null;
      let passed = false;
      let failureReason = null;
      const stateImage = path.join(outputDir, "states", stateId + ".png");
      try {
        await apply(360, 640, 1, stateId);
        seen = await page.evaluate(() => {
          const q = selector => document.querySelector(selector);
          return { state: q("#login-LoginPageRoot")?.dataset.state || "",
            account_focus: q("#login-AccountField")?.dataset.focus === "true",
            password_focus: q("#login-PasswordField")?.dataset.focus === "true",
            password_type: q("#login-PasswordInput")?.type || "", error: Boolean(q("[data-login-error]")),
            disabled: Boolean(q("#login-LoginPrimaryButton")?.disabled),
            submit_text: q("#login-LoginPrimaryButton")?.textContent?.trim() || "",
            keyboard: Boolean(q("#login-keyboard-overlay") && !q("#login-keyboard-overlay").hidden) };
        });
        passed = {
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
        await page.locator("#login-LoginPageRoot").screenshot({ path: stateImage, scale: "css" });
      } catch (error) {
        failureReason = error?.message || String(error);
      }
      const stateRow = { state: stateId, result: passed && !failureReason ? "PASS" : "FAIL", observed: seen,
        screenshot_path: "states/" + stateId + ".png",
        screenshot_sha256: await fs.readFile(stateImage).then(sha256).catch(() => null), failure_reason: failureReason };
      states.push(stateRow);
      if (stateRow.result !== "PASS") suiteFailures.push({ scope: "state_probe", ...stateRow });
    }

    try {
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

    } catch (error) {
      interactions = { result: "FAIL", failure_reason: error?.message || String(error) };
      suiteFailures.push({ scope: "form_interactions", ...interactions });
    }
    try {
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

    } catch (error) {
      visualComparison = { status: "FAIL", failure_reason: error?.message || String(error) };
      suiteFailures.push({ scope: "visual_measurement", ...visualComparison });
    }

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
      const passed = Boolean(result && result.result === "EXPECTED_FAIL");
      negatives.push({ case_id: caseId, expected: "FAIL_CLOSED", result: passed ? "EXPECTED_FAIL_CLOSED" : "FAIL",
        observed_error: result?.observed || "expected fail-closed response was not recorded" });
      if (!passed) suiteFailures.push({ scope: "negative_case", case_id: caseId, observed: result || null });
    }
    let invalidRoutePass = false;
    let invalidRouteError = null;
    try {
      await page.goto(managerUrl + "#page/invalid-route-probe/hifi/structured_workspace", { waitUntil: "networkidle" });
      invalidRoutePass = await page.evaluate(() => !document.querySelector("#login-LoginPageRoot"));
    } catch (error) { invalidRouteError = error?.message || String(error); }
    negatives.push({ case_id: "invalid-manager-route", expected: "FAIL_CLOSED",
      result: invalidRoutePass ? "EXPECTED_FAIL_CLOSED" : "FAIL",
      observed_error: invalidRouteError || (invalidRoutePass ? "no Login DOM mounted for an unregistered route" : "Login DOM mounted for an unregistered route") });
    if (!invalidRoutePass) suiteFailures.push({ scope: "negative_case", case_id: "invalid-manager-route", failure_reason: invalidRouteError });

    const mappingUrl = resourceUrl(mappingPath);
    let badJsonState = { rootCount: -1, text: "" };
    let badJsonPass = false;
    try {
      const badJsonHandler = async route => route.fulfill({ status: 200, contentType: "application/json", body: "{" });
      await page.route(url => url.href === mappingUrl, badJsonHandler);
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(150);
      badJsonState = await page.evaluate(() => ({
        rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
        text: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ") + " " + document.body.innerText
      }));
      badJsonPass = badJsonState.rootCount === 0 && /JSON_INVALID|JSON|错误|工作区/i.test(badJsonState.text);
    } catch (error) { badJsonState.text += " " + (error?.message || String(error)); }
    await page.unroute(url => url.href === mappingUrl).catch(() => {});
    negatives.push({ case_id: "invalid-resource-json", expected: "FAIL_CLOSED",
      result: badJsonPass ? "EXPECTED_FAIL_CLOSED" : "FAIL", observed_error: badJsonState.text.slice(0, 240) });
    if (!badJsonPass) suiteFailures.push({ scope: "negative_case", case_id: "invalid-resource-json", observed: badJsonState });
    let badJsonRecovery = false;
    let badJsonRecoveryError = null;
    try { await page.goto(loginUrl, { waitUntil: "networkidle" }); await waitLogin(); badJsonRecovery = true; }
    catch (error) { badJsonRecoveryError = error?.message || String(error); }
    recoveries.push({ case_id: "invalid-mapping-json-and-recovery",
      result: badJsonPass && badJsonRecovery ? "EXPECTED_FAIL_CLOSED_AND_RECOVERED" : "FAIL",
      recovery: badJsonRecovery ? "PASS" : "FAIL", failure_observed: badJsonPass, recovery_source_sha: sourceSha,
      failure_reason: badJsonRecoveryError });
    if (!badJsonRecovery) suiteFailures.push({ scope: "recovery", case_id: "invalid-mapping-json-and-recovery", failure_reason: badJsonRecoveryError });

    let timeoutState = { rootCount: -1, text: "" };
    let timeoutPass = false;
    try {
      const timeoutHandler = async route => {
        await new Promise(resolve => setTimeout(resolve, timeoutMs + 800));
        try { await route.continue(); } catch { /* Browser-side timeout closed this request. */ }
      };
      await page.route(url => url.href === mappingUrl, timeoutHandler);
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(timeoutMs + 300);
      timeoutState = await page.evaluate(() => ({
        rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
        text: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ") + " " + document.body.innerText
      }));
      timeoutPass = timeoutState.rootCount === 0 && /TIMEOUT|超时/i.test(timeoutState.text);
    } catch (error) { timeoutState.text += " " + (error?.message || String(error)); }
    await page.unroute(url => url.href === mappingUrl).catch(() => {});
    negatives.push({ case_id: "resource-timeout-and-recovery", expected: "FAIL_CLOSED",
      result: timeoutPass ? "EXPECTED_FAIL_CLOSED" : "FAIL", observed_error: timeoutState.text.slice(0, 240) });
    if (!timeoutPass) suiteFailures.push({ scope: "negative_case", case_id: "resource-timeout-and-recovery", observed: timeoutState });
    let timeoutRecovery = false;
    let timeoutRecoveryError = null;
    try { await page.goto(loginUrl, { waitUntil: "networkidle" }); await waitLogin(); timeoutRecovery = true; }
    catch (error) { timeoutRecoveryError = error?.message || String(error); }
    recoveries.push({ case_id: "mapping-timeout-and-recovery",
      result: timeoutPass && timeoutRecovery ? "EXPECTED_FAIL_CLOSED_AND_RECOVERED" : "FAIL",
      recovery: timeoutRecovery ? "PASS" : "FAIL", failure_observed: timeoutPass, timeout_ms: timeoutMs,
      recovery_source_sha: sourceSha, failure_reason: timeoutRecoveryError });
    if (!timeoutRecovery) suiteFailures.push({ scope: "recovery", case_id: "mapping-timeout-and-recovery", failure_reason: timeoutRecoveryError });

    const backgroundPath = "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png";
    const backgroundUrl = resourceUrl(backgroundPath);
    let backgroundState = { rootCount: -1, error: "" };
    let backgroundPass = false;
    try {
      await page.route(url => url.href === backgroundUrl, route => route.fulfill({ status: 404, body: "FAIL_CLOSED_TEST_404" }));
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await page.waitForTimeout(250);
      backgroundState = await page.evaluate(() => ({
        rootCount: document.querySelectorAll("#login-LoginPageRoot").length,
        error: [...document.querySelectorAll("[data-workspace-error],#design-manager-bootstrap-error")].map(x => x.textContent || "").join(" ")
      }));
      backgroundPass = backgroundState.rootCount === 0 || /HTTP_ERROR|404|资源|加载/i.test(backgroundState.error);
    } catch (error) { backgroundState.error += " " + (error?.message || String(error)); }
    await page.unroute(url => url.href === backgroundUrl).catch(() => {});
    negatives.push({ case_id: "browser-background-404", expected: "FAIL_CLOSED",
      result: backgroundPass ? "EXPECTED_FAIL_CLOSED" : "FAIL",
      observed_error: backgroundState.error || (backgroundState.rootCount === 0 ? "required background failed closed" : "background 404 did not fail closed") });
    if (!backgroundPass) suiteFailures.push({ scope: "negative_case", case_id: "browser-background-404", observed: backgroundState });
    let backgroundRecovery = false;
    let backgroundRecoveryError = null;
    try { await page.goto(loginUrl, { waitUntil: "networkidle" }); await waitLogin(); backgroundRecovery = true; }
    catch (error) { backgroundRecoveryError = error?.message || String(error); }
    recoveries.push({ case_id: "bound-background-404",
      result: backgroundPass && backgroundRecovery ? "EXPECTED_FAIL_CLOSED_AND_RECOVERED" : "FAIL",
      recovery: backgroundRecovery ? "PASS" : "FAIL", failure_observed: backgroundPass,
      recovery_source_sha: sourceSha, failure_reason: backgroundRecoveryError });
    if (!backgroundRecovery) suiteFailures.push({ scope: "recovery", case_id: "bound-background-404", failure_reason: backgroundRecoveryError });

    const requiredNegativeIds = new Set([
      "bound-background-404", "bound-background-wrong-sha", "bound-background-wrong-dimensions",
      "bound-background-wrong-mime", "bound-background-publication-denied", "missing-publication-authority",
      "frozen-page-image-as-background", "invalid-manager-route", "invalid-resource-json", "resource-timeout-and-recovery",
      "browser-background-404"
    ]);
    if (negatives.length !== 11 || new Set(negatives.map(x => x.case_id)).size !== 11 ||
        negatives.some(x => x.result !== "EXPECTED_FAIL_CLOSED" || !requiredNegativeIds.has(x.case_id)) || recoveries.length !== 3) {
      suiteFailures.push({ scope: "negative_and_recovery_counts", expected_negatives: 11, actual_negatives: negatives.length,
        unique_negative_ids: new Set(negatives.map(x => x.case_id)).size, expected_recoveries: 3, actual_recoveries: recoveries.length });
    }

    const unresolvedResources = (bindingFile.doc.asset_bindings || [])
      .filter(row => ["MISSING", "NOT_VERIFIED"].includes(row.lifecycle_status))
      .map(row => ({ asset_id: row.asset_id, lifecycle_status: row.lifecycle_status, requiredness: row.requiredness }));
    const formalRows = rows.filter(x => x.matrix_classification === "FORMAL_AUTHORITY_CASE");
    const supplementalRows = rows.filter(x => x.matrix_classification === "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY");
    const failedRows = rows.filter(x => x.result === "FAIL");
    const formalPass = formalRows.length === 1 && formalRows.every(x => x.result === "PASS");
    const supplementalPass = supplementalRows.length === 12 && supplementalRows.every(x => x.result === "PASS");
    const negativePass = negatives.length === 11 && negatives.every(x => x.result === "EXPECTED_FAIL_CLOSED");
    const statePass = states.length === 9 && states.every(x => x.result === "PASS");
    const recoveryPass = recoveries.length === 3 && recoveries.every(x => x.recovery === "PASS");
    const routesPass = Boolean(routeChecks && Object.values(routeChecks).every(x => x === "PASS"));
    const browserPass = !errors.console.length && !errors.page.length && !errors.network.length && !errors.http.length;
    const suitePass = rows.length === 13 && formalPass && supplementalPass && negativePass && statePass && recoveryPass &&
      routesPass && browserPass && suiteFailures.length === 0;
    const report = {
      schema: "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", page_id: "auth_login_v2", authority_version: "V2.1",
      status: suitePass ? "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING" : "FAIL_STRUCTURAL_MATRIX_REVIEW_PENDING",
      source_sha: sourceSha, candidate_source_sha: sourceSha,
      github_run_id: runId, generated_at: now(), browser_engine: "Chromium", browser_version: await browser.version(), route: loginUrl,
      matrix_authority: { id: "login-responsive-matrix", path: matrixPath, sha256: matrixFile.sha, status: matrix.status },
      matrix_summary: {
        source_path: matrixPath, source_sha256: matrixFile.sha,
        expected: 1, executed: formalRows.length, pass: formalRows.filter(x => x.result === "PASS").length,
        fail: formalRows.filter(x => x.result === "FAIL").length, not_run: Math.max(0, 1 - formalRows.length),
        formal_expected: 1, formal_executed: formalRows.length, formal_pass: formalRows.filter(x => x.result === "PASS").length,
        formal_fail: formalRows.filter(x => x.result === "FAIL").length, formal_not_run: Math.max(0, 1 - formalRows.length),
        supplemental_expected: 12, supplemental_executed: supplementalRows.length,
        supplemental_pass: supplementalRows.filter(x => x.result === "PASS").length,
        supplemental_fail: supplementalRows.filter(x => x.result === "FAIL").length,
        supplemental_not_run: Math.max(0, 12 - supplementalRows.length),
        total_expected: 13, total_executed: rows.length, total_pass: rows.filter(x => x.result === "PASS").length,
        total_fail: failedRows.length, total_not_run: Math.max(0, 13 - rows.length)
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
        W3: { status: matrixFailures.length ? "FAIL" : "PASS", source_sha: sourceSha, run_id: runId,
          component_tree_measured_nodes: expectedNodes.length, actual_dom_geometry: matrixFailures.length ? "FAIL" : "PASS",
          parent_relationships: matrixFailures.some(x => /parent|tree/i.test(x.failure_reason || "")) ? "FAIL" : "PASS",
          selection_inspector: inspectorEvidence },
        W4: { status: routesPass && recoveryPass && browserPass ? "PASS" : "FAIL", source_sha: sourceSha, run_id: runId,
          chromium: browserPass ? "PASS" : "FAIL", failure_recovery: recoveryPass ? "PASS" : "FAIL", route_checks: routeChecks },
        W5: { status: formalPass && supplementalPass && negativePass && statePass ? "PASS" : "FAIL",
          source_sha: sourceSha, run_id: runId, formal_expected: 1, formal_pass: formalRows.filter(x => x.result === "PASS").length,
          supplemental_expected: 12, supplemental_pass: supplementalRows.filter(x => x.result === "PASS").length,
          negative_expected: 11, negative_pass: negatives.filter(x => x.result === "EXPECTED_FAIL_CLOSED").length,
          frozen_comparison: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", visual_acceptance: "USER_VISUAL_ACCEPTANCE_PENDING" }
      },
      suite_failures: suiteFailures,
      matrix_failures: matrixFailures,
      visual_comparison: visualComparison, browser_errors: errors.console.concat(errors.page, errors.http),
      network_failures: errors.network,
      resource_status: { background: "FROZEN_AND_RESOLVED", shared_icons: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING",
        footer_grass: "NOT_VERIFIED", approved_upload_source: "MISSING_OPTIONAL", safe_area_owner: "NOT_VERIFIED" },
      visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING", user_acceptance_status: "USER_VISUAL_ACCEPTANCE_PENDING",
      native_runtime_status: "NOT_VERIFIED", implementation_readiness: "BLOCKED_PENDING_RESOURCE_AND_SAFE_AREA_REVIEW"
    };
    await page.locator("#login-LoginPageRoot").screenshot({ path: path.join(outputDir, "screenshots/login-shared-manager-final.png"), scale: "css" }).catch(() => {});
    await fs.writeFile(path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json"), JSON.stringify(report, null, 2) + "\n");
    evidenceWritten = true;
    const runSummary = {
      source_sha: sourceSha, run_id: runId, status: report.status,
      formal: formalRows.filter(x => x.result === "PASS").length + "/1 PASS; fail=" + formalRows.filter(x => x.result === "FAIL").length,
      supplemental: supplementalRows.filter(x => x.result === "PASS").length + "/12 PASS; fail=" + supplementalRows.filter(x => x.result === "FAIL").length,
      negative: negatives.filter(x => x.result === "EXPECTED_FAIL_CLOSED").length + "/11 EXPECTED_FAIL_CLOSED",
      state_probes: states.filter(x => x.result === "PASS").length + "/9 PASS", recoveries: recoveries.filter(x => x.recovery === "PASS").length + "/3 PASS",
      W2: report.gate_results.W2.status, W3: report.gate_results.W3.status, W4: report.gate_results.W4.status,
      W5: report.gate_results.W5.status, visual_review: report.visual_review_status, native_runtime: report.native_runtime_status,
      suite_failures: suiteFailures.length, matrix_failures: matrixFailures.length
    };
    await fs.writeFile(path.join(outputDir, "Login_W4_Run_Summary.json"), JSON.stringify(runSummary, null, 2) + "\n");
    console.log(JSON.stringify({ page_id: report.page_id, source_sha: sourceSha, run_id: runId, status: report.status,
      formal_expected: 1, formal_executed: formalRows.length, formal_pass: formalRows.filter(x => x.result === "PASS").length,
      supplemental_expected: 12, supplemental_executed: supplementalRows.length,
      supplemental_pass: supplementalRows.filter(x => x.result === "PASS").length, negative_expected: 11,
      negative_pass: negatives.filter(x => x.result === "EXPECTED_FAIL_CLOSED").length,
      state_probes: runSummary.state_probes, recoveries: runSummary.recoveries, W2: runSummary.W2,
      W3: runSummary.W3, W4: runSummary.W4, W5: runSummary.W5 }));
    if (!suitePass) throw new Error("LOGIN_W5_SUITE_FAILED " + JSON.stringify({
      matrix_failures: matrixFailures.length, suite_failures: suiteFailures.length,
      negative_results: negatives.map(x => ({ case_id: x.case_id, result: x.result })),
      state_results: states.map(x => ({ state: x.state, result: x.result })),
      recovery_results: recoveries.map(x => ({ case_id: x.case_id, recovery: x.recovery }))
    }));
  } catch (error) {
    const failureReason = error?.stack || String(error);
    const evidencePath = path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json");
    if (evidenceWritten) {
      try {
        const report = JSON.parse((await fs.readFile(evidencePath)).toString("utf8"));
        report.status = "FAIL_STRUCTURAL_MATRIX_REVIEW_PENDING";
        report.runner_failure_reason = failureReason;
        report.matrix_failures = matrixFailures;
        report.suite_failures = suiteFailures;
        await fs.writeFile(evidencePath, JSON.stringify(report, null, 2) + "\n");
      } catch {}
    } else {
      const failure = { schema: "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", page_id: "auth_login_v2", status: "FAIL",
        source_sha: sourceSha, candidate_source_sha: sourceSha, github_run_id: runId, generated_at: now(),
        failure_reason: failureReason,
        partial_counts: { matrix_cases: rows.length, state_probes: states.length, negative_cases: negatives.length, recovery_cases: recoveries.length },
        matrix_rows: rows, matrix_failures: matrixFailures, suite_failures: suiteFailures,
        browser_errors: errors.console.concat(errors.page, errors.http), network_failures: errors.network,
        visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING", native_runtime_status: "NOT_VERIFIED" };
      await fs.writeFile(evidencePath, JSON.stringify(failure, null, 2) + "\n");
    }
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
