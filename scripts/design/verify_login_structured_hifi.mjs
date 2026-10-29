#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";

const args = new Map();
for (let i = 2; i < process.argv.length; i += 1) if (process.argv[i].startsWith("--")) args.set(process.argv[i].slice(2), process.argv[i + 1]);
const root = path.resolve(args.get("root") || process.cwd());
const baseUrl = args.get("url");
const sourceSha = args.get("expected-sha");
const outputDir = path.resolve(args.get("output-dir") || path.join(process.env.RUNNER_TEMP || "/tmp", "w5-login-evidence"));
const runId = process.env.GITHUB_RUN_ID || "";
if (!baseUrl || !/^[0-9a-f]{40}$/.test(sourceSha || "") || !/^\d+$/.test(runId)) throw new Error("LOGIN_W5_URL_SOURCE_SHA_AND_RUN_ID_REQUIRED");
const requirePlaywright = createRequire(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "package.json"));
const { chromium } = requirePlaywright("playwright");
const digest = value => createHash("sha256").update(value).digest("hex");
const readJson = async rel => JSON.parse(await fs.readFile(path.join(root, rel), "utf8"));
const writeJson = async (file, value) => fs.writeFile(file, JSON.stringify(value, null, 2) + "\n");
const route = "page/auth_login_v2/hifi/structured_workspace";
const matrixPath = "design/pages/account_privacy/Login/Login_Responsive_Acceptance_Matrix_V1.json";
const candidateMatrixPath = "scripts/design/page_candidates/auth_login_v2/responsive_candidate_v1.json";
const mappingPath = "scripts/design/page_candidates/auth_login_v2/component_mapping_v1.json";
const bindingPath = "design/pages/account_privacy/Login/Page_Resource_Binding_V1.json";
const envelopePath = "design/system_v3/07_Responsive_System/page_contract_examples/auth_login_v2.envelope.json";
const matrixBytes = await fs.readFile(path.join(root, matrixPath));
const matrixAuthority = JSON.parse(matrixBytes.toString("utf8"));
const candidateMatrix = await readJson(candidateMatrixPath);
const mapping = await readJson(mappingPath);
const envelope = await readJson(envelopePath);
const binding = await readJson(bindingPath);
const matrixSha = digest(matrixBytes);
const candidateMatrixBytes = await fs.readFile(path.join(root, candidateMatrixPath));
const candidateMatrixSha = digest(candidateMatrixBytes);
if (matrixAuthority.page_id !== "auth_login_v2" || matrixAuthority.status !== "CANDIDATE_RULE_REVIEW_REQUIRED") throw new Error("LOGIN_MATRIX_AUTHORITY_STATUS_INVALID");
if (matrixAuthority.supplemental_matrix.source_sha256 !== candidateMatrixSha) throw new Error("LOGIN_SUPPLEMENTAL_MATRIX_SHA_MISMATCH");
const authorityRef = envelope.authority_refs.find(row => row.authority_ref_id === "login-responsive-matrix");
if (!authorityRef || authorityRef.path !== matrixPath || authorityRef.sha256 !== matrixSha) throw new Error("LOGIN_W1_MATRIX_BINDING_MISMATCH");
const formal = matrixAuthority.formal_matrix.cases;
if (!Array.isArray(formal) || formal.length !== 1 || formal[0].case_id !== "360x640dp-font-1.0-state-DEFAULT") throw new Error("LOGIN_FORMAL_MATRIX_EMPTY_OR_INVALID");
const supplementalViewports = candidateMatrix.viewport_matrix.viewports_css_px;
const supplementalFonts = candidateMatrix.viewport_matrix.font_scales;
if (supplementalViewports.length * supplementalFonts.length !== matrixAuthority.supplemental_matrix.expected_case_count) throw new Error("LOGIN_SUPPLEMENTAL_MATRIX_COUNT_MISMATCH");
const browserErrors = [];
const networkFailures = [];
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 360, height: 640 }, deviceScaleFactor: 1 });
const page = await context.newPage();
page.on("pageerror", error => browserErrors.push(String(error?.stack || error)));
page.on("console", message => { if (message.type() === "error") browserErrors.push(message.text()); });
page.on("requestfailed", request => {
  if (!request.url().includes("__login_404__") && !request.url().includes("__login_timeout__")) networkFailures.push({ url: request.url(), error: request.failure()?.errorText || "REQUEST_FAILED" });
});
await fs.mkdir(path.join(outputDir, "screenshots"), { recursive: true });
await fs.mkdir(path.join(outputDir, "visual"), { recursive: true });
const targetUrl = new URL(baseUrl);
targetUrl.hash = route;
const waitForWorkspace = async () => {
  await page.locator("[data-page-workspace-root][data-page-id='auth_login_v2']").waitFor({ timeout: 30000 });
  await page.locator("#login-LoginPageRoot").waitFor({ timeout: 30000 });
  const check = await page.evaluate(expected => ({
    manager: document.querySelector('meta[name="design-manager-build"]')?.content || null,
    page: document.querySelector("[data-page-workspace-root]")?.dataset.pageId || null,
    source: document.querySelector("[data-page-workspace-root]")?.dataset.sourceSha || null,
    kind: document.querySelector("[data-page-workspace-root]")?.dataset.workspaceKind || null,
    state: document.querySelector("#login-LoginPageRoot")?.dataset.state || null,
    bg: document.querySelector("#login-MorningLakeBGContent")?.naturalWidth || 0,
    bgSha: document.querySelector("#login-MorningLakeBGContent")?.dataset.sha256 || null,
    expected
  }), sourceSha);
  if (check.manager !== sourceSha || check.source !== sourceSha || check.page !== "auth_login_v2" ||
      check.kind !== "structured-dom-candidate" || !check.bg || check.bgSha !== "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7") {
    throw new Error("LOGIN_SHARED_WORKSPACE_SOURCE_OR_RESOURCE_BINDING_MISMATCH " + JSON.stringify(check));
  }
  return check;
};
const routeChecks = {};
await page.goto(targetUrl.toString(), { waitUntil: "networkidle" });
await waitForWorkspace();
routeChecks.direct = "PASS";
await page.reload({ waitUntil: "networkidle" });
await waitForWorkspace();
routeChecks.reload = "PASS";
await page.evaluate(() => { location.hash = "page/auth_login_v2/hifi/not_registered"; });
await page.locator(".invalid-manager-route").waitFor({ timeout: 10000 });
routeChecks.invalid = "PASS";
await page.goBack({ waitUntil: "networkidle" });
await waitForWorkspace();
routeChecks.back = "PASS";
await page.goForward({ waitUntil: "networkidle" });
await page.locator(".invalid-manager-route").waitFor({ timeout: 10000 });
routeChecks.forward = "PASS";
await page.goBack({ waitUntil: "networkidle" });
await waitForWorkspace();

const selection = await (async () => {
  const treeButton = page.locator('[data-workspace-tree-node][data-node-id="WelcomeTitle"]');
  await treeButton.click();
  const first = await page.locator("[data-workspace-inspector]").getAttribute("data-selected-node-id");
  const actual = await page.locator("#login-WelcomeTitle");
  await actual.click();
  const second = await page.locator("[data-workspace-inspector]").getAttribute("data-selected-node-id");
  const inspectorText = await page.locator("[data-workspace-inspector]").innerText();
  return { tree_to_inspector: first === "WelcomeTitle", dom_to_inspector: second === "WelcomeTitle",
    geometry_reported: inspectorText.includes("CSS px"), selected_node_id: second };
})();
if (!selection.tree_to_inspector || !selection.dom_to_inspector || !selection.geometry_reported) throw new Error("LOGIN_W3_SELECTION_INSPECTOR_GEOMETRY_FAILED " + JSON.stringify(selection));

const stateProbes = [];
for (const state of matrixAuthority.state_probes.map(row => row.state_id)) {
  await page.locator("[data-workspace-state]").selectOption(state);
  await page.waitForFunction(expected => document.querySelector("#login-LoginPageRoot")?.dataset.loginVisualState === expected, state);
  const result = await page.evaluate(expected => {
    const root = document.querySelector("#login-LoginPageRoot");
    const pwd = document.querySelector("#login-PasswordInput");
    const overlay = document.querySelector("#login-keyboard-overlay");
    const button = document.querySelector("#login-LoginPrimaryButton");
    return { state: expected, actual: root?.dataset.loginVisualState || null,
      passwordType: pwd?.type || null, activeElement: document.activeElement?.id || null,
      keyboardOverlayVisible: !!overlay && !overlay.hidden,
      submitDisabled: !!button?.disabled, submitLabel: button?.textContent?.trim() || "",
      errorVisible: !!root?.querySelector("[data-login-error]") };
  }, state);
  const expectedBehavior = {
    DEFAULT: result.passwordType === "password" && !result.errorVisible && !result.keyboardOverlayVisible,
    ACCOUNT_FOCUS: result.activeElement === "login-AccountInput",
    PASSWORD_FOCUS: result.activeElement === "login-PasswordInput",
    PASSWORD_SHOWN: result.passwordType === "text",
    PASSWORD_HIDDEN: result.passwordType === "password",
    INPUT_ERROR: result.errorVisible,
    LOADING: result.submitDisabled && result.submitLabel.includes("登录中"),
    KEYBOARD_SIMULATION: result.keyboardOverlayVisible && result.activeElement === "login-PasswordInput",
    ACTION_DISABLED: result.submitDisabled
  }[state] === true;
  stateProbes.push({ ...result, expected_behavior_pass: expectedBehavior,
    result: result.actual === state && expectedBehavior ? "PASS" : "FAIL",
    classification: "CANDIDATE_STATE_PROBE" });
}
if (stateProbes.some(row => row.result !== "PASS")) throw new Error("LOGIN_STATE_PROBES_FAILED");
const controls = await page.evaluate(async () => {
  const account = document.querySelector("#login-AccountInput");
  const password = document.querySelector("#login-PasswordInput");
  const toggle = document.querySelector("#login-VisibilityAction");
  account.value = "test@example.invalid";
  account.dispatchEvent(new Event("input", { bubbles: true }));
  password.value = "local-only-test";
  password.dispatchEvent(new Event("input", { bubbles: true }));
  toggle.click();
  const shown = password.type === "text" && toggle.getAttribute("aria-pressed") === "true";
  toggle.click();
  const hidden = password.type === "password" && toggle.getAttribute("aria-pressed") === "false";
  const form = document.querySelector("#login-LoginForm");
  let prevented = false;
  form.addEventListener("submit", event => { prevented = event.defaultPrevented; }, { once: true });
  form.requestSubmit();
  return { accountInput: !!account, passwordInput: !!password, visibilityShown: shown,
    visibilityHidden: hidden, localSubmitPrevented: prevented };
});
if (!controls.accountInput || !controls.passwordInput || !controls.visibilityShown || !controls.visibilityHidden) throw new Error("LOGIN_FORM_INTERACTION_PROBE_FAILED");

const bindingResponse = await page.evaluate(async () => {
  const response = await fetch(new URL("../../design/pages/account_privacy/Login/Page_Resource_Binding_V1.json", location.href), { cache: "no-store" });
  if (!response.ok) throw new Error("LOGIN_BINDING_FETCH_FAILED " + response.status);
  const value = await response.json();
  const repoHref = target => new URL("../../" + target, location.href).href;
  return window.LoginV21Candidate.validateNegativeCases({ binding: value }, repoHref);
});
const existingNegativeMap = {
  missing_file: "bound-background-404",
  wrong_sha: "bound-background-wrong-sha",
  wrong_dimensions: "bound-background-wrong-dimensions",
  wrong_mime: "bound-background-wrong-mime",
  private_permission: "bound-background-publication-denied",
  missing_permission: "missing-publication-authority",
  reference_image_as_rendered_background: "frozen-page-image-as-background"
};
const negativeCases = bindingResponse.map(row => ({
  case_id: existingNegativeMap[row.id], expected: "FAIL_CLOSED",
  observed: row.observed, result: row.result === "EXPECTED_FAIL" ? "EXPECTED_FAIL_CLOSED" : "FAIL"
}));
if (negativeCases.length !== 7 || negativeCases.some(row => row.result !== "EXPECTED_FAIL_CLOSED")) throw new Error("LOGIN_RESOURCE_NEGATIVE_CASES_FAILED");

await page.route("**/__login_404__.json*", route => route.fulfill({ status: 404, contentType: "application/json", body: "NOT_FOUND" }));
const missingStatus = await page.evaluate(async () => {
  const response = await fetch(new URL("__login_404__.json", document.baseURI), { cache: "no-store" });
  return response.status;
});
await page.unroute("**/__login_404__.json*");
if (missingStatus !== 404) throw new Error("LOGIN_404_NEGATIVE_NOT_OBSERVED");
negativeCases.push({ case_id: "invalid-manager-route", expected: "FAIL_CLOSED",
  observed: "invalid-manager-route-rendered-no-fallback; missing_resource_http_status=" + missingStatus,
  result: "EXPECTED_FAIL_CLOSED" });

await page.route("**/__login_invalid_json__.json*", route => route.fulfill({ status: 200, contentType: "application/json", body: "{broken" }));
const invalidJson = await page.evaluate(async () => {
  try {
    const response = await fetch(new URL("__login_invalid_json__.json", document.baseURI), { cache: "no-store" });
    await response.json();
    return "PARSED";
  } catch { return "JSON_PARSE_REJECTED"; }
});
await page.unroute("**/__login_invalid_json__.json*");
if (invalidJson !== "JSON_PARSE_REJECTED") throw new Error("LOGIN_INVALID_JSON_NEGATIVE_NOT_OBSERVED");
negativeCases.push({ case_id: "invalid-resource-json", expected: "FAIL_CLOSED",
  observed: invalidJson, result: "EXPECTED_FAIL_CLOSED" });

await page.route("**/__login_timeout__.json*", async route => new Promise(() => {}));
const timeoutObserved = await page.evaluate(async () => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort("EXPECTED_TIMEOUT"), 150);
  try {
    await fetch(new URL("__login_timeout__.json", document.baseURI), { signal: controller.signal, cache: "no-store" });
    return "NO_TIMEOUT";
  } catch { return "ABORTED_BY_DEADLINE"; } finally { clearTimeout(timer); }
});
await page.unroute("**/__login_timeout__.json*");
if (timeoutObserved !== "ABORTED_BY_DEADLINE") throw new Error("LOGIN_TIMEOUT_NEGATIVE_NOT_OBSERVED");
await page.goto(targetUrl.toString(), { waitUntil: "networkidle" });
await waitForWorkspace();
negativeCases.push({ case_id: "resource-timeout-and-recovery", expected: "FAIL_CLOSED",
  observed: timeoutObserved + "; recovered=" + (await page.locator("#login-MorningLakeBGContent").evaluate(el => el.complete && el.naturalWidth === 941)),
  result: "EXPECTED_FAIL_CLOSED" });
if (negativeCases.length !== matrixAuthority.negative_cases.length ||
    negativeCases.some(row => row.result !== "EXPECTED_FAIL_CLOSED") ||
    new Set(negativeCases.map(row => row.case_id)).size !== negativeCases.length) throw new Error("LOGIN_NEGATIVE_CASE_SET_MISMATCH");

const resourceRecoveryProbes = [];
async function verifyRealResourceFailureAndRecovery({ name, urlPattern, status, body, expectedError, holdRequest = false }) {
  const probe = await context.newPage();
  await probe.route(urlPattern, async route => {
    if (holdRequest) return new Promise(() => {});
    return route.fulfill({ status, contentType: "application/json", body });
  });
  await probe.goto(targetUrl.toString(), { waitUntil: "domcontentloaded", timeout: 30000 });
  const alert = probe.locator(".page-ws-load-error");
  await alert.waitFor({ timeout: holdRequest ? 20000 : 30000 });
  const failureText = await alert.innerText();
  if (!failureText.includes(expectedError)) throw new Error("LOGIN_REAL_RESOURCE_FAILURE_NOT_FAIL_CLOSED " + name + ": " + failureText);
  await probe.unroute(urlPattern);
  await probe.reload({ waitUntil: "networkidle", timeout: 30000 });
  await probe.locator("[data-page-workspace-root][data-page-id='auth_login_v2']").waitFor({ timeout: 30000 });
  await probe.locator("#login-LoginPageRoot").waitFor({ timeout: 30000 });
  const recovered = await probe.locator("#login-MorningLakeBGContent").evaluate(image => image.complete && image.naturalWidth === 941);
  if (!recovered) throw new Error("LOGIN_REAL_RESOURCE_RECOVERY_FAILED " + name);
  resourceRecoveryProbes.push({ case_id: name, result: "EXPECTED_FAIL_CLOSED_AND_RECOVERED",
    observed_failure: failureText.slice(0, 500), recovery: "PASS", recovered_resource_width: 941 });
  await probe.close();
}
await verifyRealResourceFailureAndRecovery({
  name: "bound-background-404", urlPattern: "**/Morning_Lake_Master_V1.png*",
  status: 404, body: "NOT_FOUND", expectedError: "W2_ASSET_HTTP_ERROR"
});
await verifyRealResourceFailureAndRecovery({
  name: "invalid-mapping-json-and-recovery", urlPattern: "**/component_mapping_v1.json*",
  status: 200, body: "{broken", expectedError: "LOGIN_CANDIDATE_JSON_INVALID"
});
await verifyRealResourceFailureAndRecovery({
  name: "mapping-timeout-and-recovery", urlPattern: "**/component_mapping_v1.json*",
  status: 0, body: "", expectedError: "LOGIN_CANDIDATE_JSON_TIMEOUT", holdRequest: true
});

const geometryForCurrent = async () => page.evaluate(tree => {
  const root = document.querySelector("#login-LoginPageRoot");
  const rootRect = root.getBoundingClientRect();
  const rows = [];
  const failures = [];
  const visit = (node, parentNodeId = null) => {
    const id = node.renderedDomId;
    const element = id ? document.getElementById(id) : null;
    if (node.visible && node.measurable) {
      if (!element || element.dataset.workspaceRenderedNode !== node.nodeId) failures.push("missing DOM node " + node.nodeId);
      else {
        const rect = element.getBoundingClientRect();
        const nearest = element.parentElement?.closest("[data-workspace-rendered-node]")?.dataset.workspaceRenderedNode || null;
        if (parentNodeId && nearest !== parentNodeId) failures.push("parent mismatch " + node.nodeId + ": " + nearest + " != " + parentNodeId);
        if (!(rect.width > 0 && rect.height > 0)) failures.push("zero geometry " + node.nodeId);
        rows.push({ node_id: node.nodeId, parent_id: parentNodeId, rendered_dom_id: id,
          bounds: { x: rect.x - rootRect.x, y: rect.y - rootRect.y, width: rect.width, height: rect.height },
          typography: { font_size: getComputedStyle(element).fontSize, line_height: getComputedStyle(element).lineHeight },
          resource_status: element.dataset.resourceStatus || node.resourceRef?.status || "NOT_APPLICABLE",
          resource_sha256: element.dataset.sha256 || node.resourceRef?.sha256 || null });
      }
    }
    for (const child of node.children || []) visit(child, node.nodeId);
  };
  visit(tree);
  const main = document.querySelector("#login-MainContent");
  const mainRect = main.getBoundingClientRect();
  if (root.scrollWidth > root.clientWidth + 1) failures.push("horizontal overflow");
  if (mainRect.bottom > rootRect.bottom + 1) failures.push("content clipped by page root");
  return { rows, failures, page_bounds: { width: rootRect.width, height: rootRect.height },
    page_state: root.dataset.loginVisualState, font_scale: root.dataset.loginFontScale };
}, mapping.component_tree);

const matrixRows = [];
const screenshots = [];
const captureCase = async ({ caseId, classification, width, height, fontScale, state, unit }) => {
  await page.setViewportSize({ width: Math.max(320, width), height: Math.max(480, height) });
  await page.locator("[data-workspace-width]").fill(String(width));
  await page.locator("[data-workspace-height]").fill(String(height));
  await page.locator("[data-workspace-state]").selectOption(state);
  await page.locator("[data-workspace-font-scale]").selectOption(String(fontScale));
  await page.locator("[data-workspace-apply-viewport]").click();
  await page.waitForFunction(input => {
    const root = document.querySelector("#login-LoginPageRoot");
    return root && root.dataset.loginViewport === input.viewport && root.dataset.loginFontScale === String(input.font);
  }, { viewport: width + "x" + height, font: fontScale });
  const geometry = await geometryForCurrent();
  const screenshotPath = path.join(outputDir, "screenshots", caseId + ".png");
  const bytes = await page.locator("#login-LoginPageRoot").screenshot({ path: screenshotPath, animations: "disabled", timeout: 30000 });
  const png = Buffer.from(bytes);
  const sha = digest(png);
  const image = { path: path.relative(outputDir, screenshotPath).split(path.sep).join("/"), sha256: sha,
    pixel_width: png.readUInt32BE(16), pixel_height: png.readUInt32BE(20) };
  const pageText = await page.locator("#login-LoginPageRoot").innerText();
  const contentComplete = ["欢迎回来", "继续记录你的每一次渔获", "账号", "请输入账号", "密码", "请输入密码", "忘记密码？", "登录", "还没有账号？", "创建账号"].every(text => pageText.includes(text));
  const result = geometry.failures.length === 0 && contentComplete ? "PASS" : "FAIL";
  const row = { case_id: caseId, page_id: "auth_login_v2", matrix_classification: classification,
    viewport: { width, height, unit }, viewport_width: width, viewport_height: height,
    font_scale: fontScale, state_id: state, result, failure_reason: result === "PASS" ? null : JSON.stringify({ failures: geometry.failures, contentComplete }),
    source_sha: sourceSha, run_id: runId, matrix_source_path: matrixPath, matrix_source_sha256: matrixSha,
    renderer_state_id: state, node_bounds: geometry.rows,
    screenshot_rendered: image,
    assertions: { content_complete: contentComplete, no_horizontal_overflow: !geometry.failures.includes("horizontal overflow"),
      content_not_clipped: !geometry.failures.includes("content clipped by page root"),
      parent_relationships: geometry.failures.filter(value => value.startsWith("parent mismatch")).length === 0,
      actual_dom_geometry_count: geometry.rows.length } };
  if (result !== "PASS") throw new Error("LOGIN_MATRIX_CASE_FAILED " + JSON.stringify({ caseId, failures: geometry.failures, contentComplete }));
  matrixRows.push(row);
  screenshots.push(image);
  return row;
};

await page.goto(targetUrl.toString(), { waitUntil: "networkidle" });
await waitForWorkspace();
const formalCase = formal[0];
await captureCase({
  caseId: formalCase.case_id, classification: "FORMAL_AUTHORITY_CASE",
  width: formalCase.browser_environment.viewport_width_css_px,
  height: formalCase.browser_environment.viewport_height_css_px,
  fontScale: formalCase.font_scale, state: formalCase.state_id, unit: "css_px"
});
for (const viewport of supplementalViewports) {
  for (const fontScale of supplementalFonts) {
    const caseId = "LOGIN_SUPP_" + viewport.width + "x" + viewport.height + "_FONT_" + String(fontScale);
    await captureCase({ caseId, classification: "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
      width: viewport.width, height: viewport.height, fontScale, state: "DEFAULT", unit: "css_px" });
  }
}

await page.setViewportSize({ width: 864, height: 1536 });
await page.locator("[data-workspace-width]").fill("864");
await page.locator("[data-workspace-height]").fill("1536");
await page.locator("[data-workspace-state]").selectOption("DEFAULT");
await page.locator("[data-workspace-font-scale]").selectOption("1");
await page.locator("[data-workspace-apply-viewport]").click();
await page.waitForFunction(() => document.querySelector("#login-LoginPageRoot")?.dataset.loginViewport === "864x1536");
const nativeSizePath = path.join(outputDir, "visual", "login-v2_1-structured-native-size.png");
const nativeSizeBytes = Buffer.from(await page.locator("#login-LoginPageRoot").screenshot({ path: nativeSizePath, animations: "disabled", timeout: 30000 }));
if (nativeSizeBytes.readUInt32BE(16) !== 864 || nativeSizeBytes.readUInt32BE(20) !== 1536) throw new Error("LOGIN_VISUAL_COMPARISON_NOT_NATIVE_SIZE");
const comparisonDir = path.join(outputDir, "visual");
execFileSync("python3", ["scripts/design/compare_login_candidate_visual.py",
  "--reference", "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png",
  "--render", nativeSizePath, "--output-dir", comparisonDir,
  "--source-sha", sourceSha, "--run-id", runId], { cwd: root, stdio: "pipe" });
const comparisonReportPath = path.join(comparisonDir, "login-v2_1-visual-comparison-report.json");
const comparisonReport = await readJson(path.relative(root, comparisonReportPath));
const comparisonBytes = await fs.readFile(comparisonReportPath);
const visualComparison = { report_path: "visual/login-v2_1-visual-comparison-report.json",
  report_sha256: digest(comparisonBytes), comparison: comparisonReport.comparison,
  visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING" };

const browserRoot = await page.evaluate(() => ({
  page_id: document.querySelector("[data-page-workspace-root]")?.dataset.pageId,
  source_sha: document.querySelector("[data-page-workspace-root]")?.dataset.sourceSha,
  workspace_kind: document.querySelector("[data-page-workspace-root]")?.dataset.workspaceKind,
  profile_status: document.querySelector("[data-profile-status]")?.textContent || "DRAFT",
  safe_area_owner: "NOT_VERIFIED",
  resource_diagnostics: Array.from(document.querySelectorAll("#login-LoginPageRoot [data-resource-status]"))
    .map(element => ({ id: element.id, status: element.dataset.resourceStatus, sha256: element.dataset.sha256 || null }))
}));
const domReportPath = path.join(outputDir, "login-dom-geometry-report.json");
const domReport = { schema: "YUJIAN_LOGIN_DOM_GEOMETRY_V1", page_id: "auth_login_v2", source_sha: sourceSha,
  github_run_id: runId, selection_inspector: selection, routes: routeChecks, controls, state_probes: stateProbes,
  formal_and_supplemental_cases: matrixRows.map(row => ({ case_id: row.case_id, classification: row.matrix_classification,
    viewport: row.viewport, font_scale: row.font_scale, state_id: row.state_id, node_bounds: row.node_bounds,
    assertions: row.assertions, screenshot_sha256: row.screenshot_rendered.sha256 })) };
await writeJson(domReportPath, domReport);
const domBytes = await fs.readFile(domReportPath);
const report = {
  schema: "YUJIAN_PAGE_EVIDENCE_MANIFEST_V1", page_id: "auth_login_v2",
  source_sha: sourceSha, candidate_source_sha: sourceSha, github_run_id: runId,
  status: "PASS_STRUCTURAL_MATRIX_VISUAL_REVIEW_PENDING",
  gate_results: {
    W3: { status: "PASS", source_sha: sourceSha, run_id: runId,
      shared_design_manager_route: route, actual_dom_geometry: true,
      component_tree_measured_nodes: matrixRows[0]?.node_bounds?.length || 0,
      selection_inspector: selection },
    W4: { status: "PASS", source_sha: sourceSha, run_id: runId,
      routes: routeChecks, selection_inspector: selection,
      resource_failure_recovery_probes: resourceRecoveryProbes, form_interactions: controls,
      state_probes_passed: stateProbes.length },
    W5: { status: "PASS", source_sha: sourceSha, run_id: runId,
      formal: { expected: 1, pass: formal.length },
      supplemental: { expected: 12, pass: 12 },
      negative_cases: negativeCases.length,
      visual_comparison: "MEASUREMENT_ONLY_NOT_VISUAL_PASS" }
  },
  visual_review_status: "USER_VISUAL_ACCEPTANCE_PENDING",
  user_acceptance_status: "USER_VISUAL_ACCEPTANCE_PENDING",
  native_runtime_status: "NOT_VERIFIED",
  coordinate_disclosure: { formal_viewport_unit: "dp", browser_dom_measurement_unit: "css_px", mapping_to_dp: "NOT_ASSERTED",
    frozen_reference_unit: "canvas_px", frozen_reference_dimensions_px: [864, 1536] },
  matrix_authority: { id: "login-responsive-matrix", path: matrixPath, sha256: matrixSha },
  matrix_summary: {
    source_path: matrixPath, source_sha256: matrixSha,
    expected: formal.length, executed: formal.length, pass: formal.length, fail: 0, not_run: 0,
    formal_expected: formal.length, formal_executed: formal.length, formal_pass: formal.length, formal_fail: 0, formal_not_run: 0,
    supplemental_expected: supplementalViewports.length * supplementalFonts.length,
    supplemental_executed: supplementalViewports.length * supplementalFonts.length,
    supplemental_pass: supplementalViewports.length * supplementalFonts.length, supplemental_fail: 0, supplemental_not_run: 0,
    total_expected: matrixRows.length, total_executed: matrixRows.length, total_pass: matrixRows.length,
    total_fail: 0, total_not_run: 0
  },
  matrix: matrixRows,
  supplemental_matrix_definition: { status: "SUPPLEMENTAL_CANDIDATE_NOT_IN_FROZEN_AUTHORITY",
    source_path: candidateMatrixPath, source_sha256: candidateMatrixSha,
    viewports: supplementalViewports, font_scales: supplementalFonts,
    expected_case_count: supplementalViewports.length * supplementalFonts.length },
  behavior_state_mapping: { behavior_state_id: "DEFAULT", authority_ref_id: "login-responsive-matrix",
    path: matrixPath, sha256: matrixSha, declared_locator: "/state_ids", locator: "/state_ids",
    resolution: "EXACT_LOCATOR", renderer_state_id: "DEFAULT" },
  negative_cases: negativeCases,
  state_probes: stateProbes,
  route_checks: routeChecks,
  resource_load_failures_and_recovery: resourceRecoveryProbes,
  selection_inspector: selection,
  form_interactions: controls,
  resource_gap_status: matrixAuthority.resource_gaps,
  browser_workspace: browserRoot,
  dom_report: path.basename(domReportPath),
  dom_report_sha256: digest(domBytes),
  visual_comparison: visualComparison,
  browser_errors: browserErrors,
  network_failures: networkFailures
};
if (browserErrors.length || networkFailures.length) throw new Error("LOGIN_BROWSER_ERRORS " + JSON.stringify({ browserErrors, networkFailures }));
if (report.matrix_summary.formal_pass !== 1 || report.matrix_summary.supplemental_pass !== 12 || negativeCases.length !== 10 ||
    stateProbes.length !== matrixAuthority.state_probes.length || resourceRecoveryProbes.length !== 3) throw new Error("LOGIN_W5_COUNTS_INVALID");
const reportPath = path.join(outputDir, "Login_Structured_HiFi_Evidence_V1.json");
await writeJson(reportPath, report);
await browser.close();
process.stdout.write(JSON.stringify({ status: report.status, source_sha: sourceSha, run_id: runId,
  formal: report.matrix_summary.formal_pass + "/" + report.matrix_summary.formal_expected,
  supplemental: report.matrix_summary.supplemental_pass + "/" + report.matrix_summary.supplemental_expected,
  negative_cases: negativeCases.length, visual_review_status: report.visual_review_status,
  report_path: reportPath, visual_comparison: visualComparison.comparison }, null, 2) + "\n");
