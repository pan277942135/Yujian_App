#!/usr/bin/env node
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import process from "node:process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, "../../..");
const PAGE_REL = "scripts/design/page_candidates/auth_login_v2/preview.html";
const CANDIDATE_REL = "scripts/design/page_candidates/auth_login_v2";
const requiredSet = (values, required, label) => {
  for (const item of required) if (!values.includes(item)) throw new Error(`${label} missing: ${item}`);
};
const sha256Bytes = bytes => createHash("sha256").update(bytes).digest("hex");
const args = new Map();
for (let index = 2; index < process.argv.length; index += 1) {
  const key = process.argv[index];
  if (key.startsWith("--")) args.set(key.slice(2), process.argv[index + 1]);
}
const outputDir = path.resolve(args.get("output-dir") || path.join(process.env.RUNNER_TEMP || "/tmp", "w6a-login-candidate-evidence"));
const sourceSha = execFileSync("git", ["rev-parse", "HEAD"], { cwd: REPO_ROOT, encoding: "utf8" }).trim();
const runId = process.env.GITHUB_RUN_ID || null;
const now = () => new Date().toISOString();

function assertSourceTreeClean() {
  const status = execFileSync("git", ["status", "--porcelain=v1", "--untracked-files=all"], { cwd: REPO_ROOT, encoding: "utf8" });
  const allow = new Set(["PROJECT_MASTER_STATE.yaml", "WORK_EXECUTION_STATE.json"]);
  const dirty = status.split(/\r?\n/).filter(Boolean).map(row => row.slice(3))
    .filter(file => !allow.has(file) && !file.includes("/__pycache__/") && !file.endsWith(".pyc") && !file.endsWith(".pyo"));
  if (dirty.length) throw new Error(`EXACT_SOURCE_TREE_DIRTY: ${dirty.join(", ")}`);
}

const mimeTypes = new Map([
  [".html", "text/html; charset=utf-8"], [".css", "text/css; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"], [".mjs", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"], [".md", "text/markdown; charset=utf-8"],
  [".png", "image/png"], [".svg", "image/svg+xml"], [".jpg", "image/jpeg"]
]);

async function readJson(rel) {
  return JSON.parse(await fs.readFile(path.join(REPO_ROOT, rel), "utf8"));
}

async function readEvidenceJson(filePath, label) {
  try { return JSON.parse(await fs.readFile(filePath, "utf8")); }
  catch (error) { throw new Error(`${label}_REPORT_MISSING_OR_INVALID: ${error?.message || error}`); }
}

async function validateUpstreamEvidence() {
  if (!runId) throw new Error("GITHUB_RUN_ID_REQUIRED_FOR_EXACT_EVIDENCE");
  const admissionPath = path.join(outputDir, "admission-report.json");
  const admissionLogPath = path.join(outputDir, "admission.log");
  const w1Path = path.join(outputDir, "w1-contract-report.json");
  const w2BundlePath = path.join(outputDir, "resources/pages-bundle-w2.json");
  const frozenPath = path.join(outputDir, "resources/login-frozen-visual-w2.json");
  const backgroundPath = path.join(outputDir, "resources/login-bg-content-w2.json");
  const [admission, admissionLog, w1, w2Bundle, frozen, background, binding] = await Promise.all([
    readEvidenceJson(admissionPath, "CONTROL_PLANE_ADMISSION"),
    fs.readFile(admissionLogPath),
    readEvidenceJson(w1Path, "W1_CONTRACT"),
    readEvidenceJson(w2BundlePath, "W2_PAGES_BUNDLE"),
    readEvidenceJson(frozenPath, "W2_LOGIN_FROZEN"),
    readEvidenceJson(backgroundPath, "W2_LOGIN_BACKGROUND"),
    readJson("design/pages/account_privacy/Login/Page_Resource_Binding_V1.json")
  ]);
  const exactRun = value => value === runId;
  if (admission.status !== "PASS" || admission.gate_id !== "CONTROL_PLANE_ADMISSION" || admission.source_sha !== sourceSha ||
      !exactRun(admission.workflow_run_id) || admission.report_path !== "admission.log" ||
      admission.report_sha256 !== sha256Bytes(admissionLog) || !admissionLog.toString("utf8").includes("CONTROL_PLANE_ADMISSION=PASS")) {
    throw new Error(`ADMISSION_EXACT_SHA_OR_RUN_MISMATCH ${JSON.stringify({ status: admission.status, source_sha: admission.source_sha, workflow_run_id: admission.workflow_run_id })}`);
  }
  if (w1.status !== "PASS" || w1.source_sha !== sourceSha || w1.expected_source_sha !== sourceSha || !exactRun(w1.workflow_run_id)) {
    throw new Error(`W1_EXACT_SHA_OR_RUN_MISMATCH ${JSON.stringify({ status: w1.status, source_sha: w1.source_sha, expected_source_sha: w1.expected_source_sha, workflow_run_id: w1.workflow_run_id })}`);
  }
  if (w2Bundle.status !== "PASS" || w2Bundle.source_sha !== sourceSha || !exactRun(w2Bundle.workflow_run_id)) {
    throw new Error(`W2_BUNDLE_EXACT_SHA_OR_RUN_MISMATCH ${JSON.stringify({ status: w2Bundle.status, source_sha: w2Bundle.source_sha, workflow_run_id: w2Bundle.workflow_run_id })}`);
  }
  const loginPage = (w2Bundle.pages || []).find(row => row.page_id === "auth_login_v2");
  if (!loginPage || loginPage.status !== "PASS" || loginPage.binding_sha256 !== await sha256Bytes(await fs.readFile(path.join(REPO_ROOT, loginPage.binding_path)))) {
    throw new Error("W2_LOGIN_PAGE_BINDING_NOT_EXACT_OR_PASS");
  }
  const expectedBindingSha = await sha256Bytes(await fs.readFile(path.join(REPO_ROOT, "design/pages/account_privacy/Login/Page_Resource_Binding_V1.json")));
  if (loginPage.binding_sha256 !== expectedBindingSha || binding.page_id !== "auth_login_v2") throw new Error("W2_LOGIN_BINDING_SHA_MISMATCH");

  const bindingAssets = new Map((binding.asset_bindings || []).map(asset => [asset.asset_role, asset]));
  const audit = await readJson("scripts/design/page_candidates/auth_login_v2/authority_audit_v1.json");
  const expectedRoles = new Map([
    ["page_visual_authority_v2_1", audit.w2_resolution.frozen_visual],
    ["background_bg_content", audit.w2_resolution.background]
  ]);
  const roleReports = [frozen, background];
  const roleEvidence = {};
  for (const report of roleReports) {
    const expected = expectedRoles.get(report.asset_role);
    const bindingAsset = bindingAssets.get(report.asset_role);
    const row = (report.resources || []).find(item => item.asset_role === report.asset_role);
    if (!expected || !bindingAsset || report.page_id !== "auth_login_v2" || report.authority_version !== "V2.1" ||
        report.status !== "RESOLVED" || report.binding_sha256 !== expectedBindingSha || !row ||
        row.sha256 !== expected.sha256 || row.sha256 !== bindingAsset.sha256 ||
        row.verification?.validation_status !== "PASS" || row.verification?.resource_status !== "FROZEN") {
      throw new Error(`W2_ROLE_NOT_RESOLVED_OR_SHA_MISMATCH ${report.asset_role}`);
    }
    roleEvidence[report.asset_role] = {
      status: report.status,
      binding_sha256: report.binding_sha256,
      asset_id: row.asset_id,
      source_path: row.source_path,
      asset_sha256: row.sha256,
      validation_status: row.verification.validation_status,
      resource_status: row.verification.resource_status,
      report_path: path.relative(outputDir, report === frozen ? frozenPath : backgroundPath).replaceAll(path.sep, "/"),
      report_sha256: sha256Bytes(await fs.readFile(report === frozen ? frozenPath : backgroundPath))
    };
  }
  return {
    admission: { status: admission.status, source_sha: admission.source_sha, run_id: admission.workflow_run_id, report_path: admission.report_path, report_sha256: admission.report_sha256, manifest_path: path.relative(outputDir, admissionPath), manifest_sha256: sha256Bytes(await fs.readFile(admissionPath)) },
    w1: { status: w1.status, source_sha: w1.source_sha, run_id: w1.workflow_run_id, report_path: path.relative(outputDir, w1Path), report_sha256: sha256Bytes(await fs.readFile(w1Path)) },
    w2: { status: w2Bundle.status, source_sha: w2Bundle.source_sha, run_id: w2Bundle.workflow_run_id, report_path: path.relative(outputDir, w2BundlePath), report_sha256: sha256Bytes(await fs.readFile(w2BundlePath)), page_id: loginPage.page_id, page_status: loginPage.status, binding_sha256: loginPage.binding_sha256, roles: roleEvidence }
  };
}

function makeStaticServer() {
  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://127.0.0.1");
      const relative = decodeURIComponent(url.pathname).replace(/^\/+/, "");
      const resolved = path.resolve(REPO_ROOT, relative || "index.html");
      if (!resolved.startsWith(`${REPO_ROOT}${path.sep}`) || resolved === REPO_ROOT) {
        response.writeHead(403, { "content-type": "text/plain" }).end("FORBIDDEN");
        return;
      }
      const bytes = await fs.readFile(resolved);
      response.writeHead(200, {
        "content-type": mimeTypes.get(path.extname(resolved).toLowerCase()) || "application/octet-stream",
        "content-length": bytes.byteLength,
        "cache-control": "no-store"
      });
      response.end(bytes);
    } catch (error) {
      const status = error?.code === "ENOENT" ? 404 : 500;
      response.writeHead(status, { "content-type": "text/plain; charset=utf-8" }).end(status === 404 ? "NOT_FOUND" : String(error?.message || error));
    }
  });
}

async function main() {
  assertSourceTreeClean();
  const evidenceDir = path.join(outputDir, "evidence");
  const screenshotDir = path.join(evidenceDir, "screenshots");
  await fs.mkdir(screenshotDir, { recursive: true });
  const upstreamEvidence = await validateUpstreamEvidence();
  const mapping = await readJson(`${CANDIDATE_REL}/component_mapping_v1.json`);
  const profile = await readJson(`${CANDIDATE_REL}/responsive_candidate_v1.json`);
  const authorities = await readJson("design/pages/account_privacy/Login/Page_Resource_Binding_V1.json");
  if (mapping.page_id !== "auth_login_v2" || profile.page_id !== "auth_login_v2" || authorities.page_id !== "auth_login_v2") {
    throw new Error("PAGE_ID_MISMATCH");
  }
  const server = makeStaticServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  const origin = `http://127.0.0.1:${address.port}`;
  const candidateUrl = `${origin}/${PAGE_REL}`;
  const playwrightModuleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
  const require = createRequire(import.meta.url);
  const playwrightPath = playwrightModuleRoot ? path.join(playwrightModuleRoot, "playwright") : "playwright";
  const { chromium } = require(playwrightPath);
  const browserErrors = { console: [], page: [], network: [], http: [] };
  const unexpectedHttp = [];
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1800, height: 2500 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    page.on("console", message => { if (message.type() === "error") browserErrors.console.push(message.text()); });
    page.on("pageerror", error => browserErrors.page.push(String(error?.stack || error)));
    page.on("requestfailed", request => browserErrors.network.push({ url: request.url(), error: request.failure()?.errorText || "unknown" }));
    page.on("response", response => {
      if (response.status() >= 400) {
        const row = { status: response.status(), url: response.url() };
        browserErrors.http.push(row);
        if (!response.url().includes("__w6a_missing.png")) unexpectedHttp.push(row);
      }
    });
    await page.addInitScript(({ exactSourceSha }) => { window.__W6A_EXACT_SOURCE_SHA__ = exactSourceSha; }, { exactSourceSha: sourceSha });
    await page.goto(candidateUrl, { waitUntil: "networkidle" });
    try {
      await page.waitForFunction(() => document.documentElement.dataset.loginCandidateReady === "true", { timeout: 30000 });
    } catch (error) {
      const bootFailure = await page.evaluate(() => ({
        url: location.href,
        ready: document.documentElement.dataset.loginCandidateReady || null,
        status: document.querySelector("#login-boot-status")?.textContent || null,
        mount_text: document.querySelector("#workspace-mount")?.innerText || null,
        page_title: document.title
      }));
      const diagnostic = { page_id: "auth_login_v2", source_sha: sourceSha, run_id: runId,
        error: String(error?.stack || error), boot_failure: bootFailure, browser_errors: browserErrors };
      await fs.writeFile(path.join(evidenceDir, "login-candidate-boot-failure.json"), `${JSON.stringify(diagnostic, null, 2)}\n`);
      await page.screenshot({ path: path.join(screenshotDir, "login-candidate-boot-failure.png"), fullPage: true }).catch(() => {});
      throw new Error(`LOGIN_CANDIDATE_BOOT_FAILED ${JSON.stringify(diagnostic)}`);
    }
    await page.evaluate(() => document.fonts.ready);
    await page.locator("#login-LoginPageRoot").waitFor({ state: "visible" });

    const pageIdentity = await page.evaluate(() => ({
      page_id: window.LoginV21Workspace?.pageId,
      source_sha: window.LoginV21Workspace?.sourceSha,
      structured: document.querySelector("[data-workspace-kind='structured-dom-candidate']") !== null,
      workspace_kind: document.querySelector("[data-workspace-kind]")?.dataset.workspaceKind,
      authority_mode: document.querySelector("[data-page-workspace-root]")?.dataset.authorityMode,
      profile_status: document.querySelector("[data-workspace-inspector]")?.innerText.includes("DRAFT") ? "DRAFT" : "UNKNOWN",
      reference_outside_renderer: !document.querySelector("#login-LoginPageRoot img[src*='Login_V2_1_Frozen_Final.png']"),
      frozen_reference_loaded: document.querySelector("#login-frozen-reference")?.naturalWidth === 864
    }));
    if (pageIdentity.page_id !== "auth_login_v2" || pageIdentity.source_sha !== sourceSha || !pageIdentity.structured ||
        pageIdentity.workspace_kind !== "structured-dom-candidate" || pageIdentity.authority_mode !== "W1_W2_RESOLVED" ||
        !pageIdentity.reference_outside_renderer || !pageIdentity.frozen_reference_loaded) {
      throw new Error(`LOGIN_WORKSPACE_IDENTITY_INVALID ${JSON.stringify(pageIdentity)}`);
    }

    const treeResult = await page.evaluate(async () => ({
      tree: await window.LoginV21Workspace.loadComponentTree(),
      measured: window.LoginV21Workspace.measureElements()
    }));
    const tree = treeResult.tree;
    const measured = treeResult.measured;
    const expectedNodeIds = [];
    const visit = node => { expectedNodeIds.push(node.nodeId); for (const child of node.children || []) visit(child); };
    visit(mapping.component_tree);
    requiredSet(measured.map(row => row.node_id), expectedNodeIds, "DOM geometry mapping");
    if (new Set(measured.map(row => row.rendered_dom_id)).size !== measured.length || measured.length !== expectedNodeIds.length) {
      throw new Error("LOGIN_DOM_MAPPING_NOT_ONE_TO_ONE");
    }
    const domControls = await page.evaluate(() => ({
      form: document.querySelector("#login-LoginForm") instanceof HTMLFormElement,
      account: document.querySelector("#login-AccountInput") instanceof HTMLInputElement,
      password: document.querySelector("#login-PasswordInput") instanceof HTMLInputElement,
      submit: document.querySelector("#login-LoginPrimaryButton") instanceof HTMLButtonElement,
      forgot: document.querySelector("#login-ForgotPasswordAction") instanceof HTMLButtonElement,
      register: document.querySelector("#login-RegisterAction") instanceof HTMLButtonElement,
      resourceStatuses: [...document.querySelectorAll("#login-LoginPageRoot [data-resource-status]")].map(node => node.dataset.resourceStatus),
      candidateFooterStatus: document.querySelector("#login-Footer")?.dataset.resourceStatus
    }));
    if (!Object.values(domControls).filter(value => typeof value === "boolean").every(Boolean)) throw new Error("LOGIN_REAL_HTML_CONTROL_MISSING");
    if (domControls.candidateFooterStatus !== "CANDIDATE_UNAPPROVED_ASSET" || !domControls.resourceStatuses.includes("W2_RESOLVED")) {
      throw new Error("LOGIN_RESOURCE_STATE_NOT_EXPLICIT");
    }

    await page.locator('[data-workspace-tree-node][data-node-id="AccountInput"]').click();
    const treeSelection = await page.evaluate(() => window.LoginV21Workspace.getInspector("AccountInput"));
    if (treeSelection.bounds.width <= 0 || treeSelection.bounds.height <= 0 || treeSelection.component_type !== "HTML_INPUT_TEXT" ||
        !treeSelection.expected_bounds || !treeSelection.deviation) {
      throw new Error("LOGIN_TREE_TO_DOM_SELECTION_FAILED");
    }
    await page.locator("#login-WelcomeTitle").click();
    const reverseSelection = await page.evaluate(() => ({
      selected: document.querySelector('[data-workspace-tree-node][data-node-id="WelcomeTitle"]')?.getAttribute("aria-pressed"),
      hasSelectionOutline: document.querySelector("#login-WelcomeTitle")?.hasAttribute("data-workspace-selected"),
      inspectorId: document.querySelector("[data-workspace-inspector]")?.dataset.selectedNodeId
    }));
    if (reverseSelection.selected !== "true" || !reverseSelection.hasSelectionOutline || reverseSelection.inspectorId !== "WelcomeTitle") {
      throw new Error(`LOGIN_DOM_TO_TREE_SELECTION_FAILED ${JSON.stringify(reverseSelection)}`);
    }
    let missingLayerFailedClosed = false;
    try { await page.evaluate(() => window.LoginV21Workspace.selectLayer("NoSuchLoginNode")); }
    catch (error) { missingLayerFailedClosed = /COMPONENT_NODE_NOT_SELECTABLE|COMPONENT_NODE_NOT_FOUND/.test(String(error)); }
    if (!missingLayerFailedClosed) throw new Error("LOGIN_MISSING_COMPONENT_DID_NOT_FAIL_CLOSED");

    const negativeResults = await page.evaluate(async () => {
      const authority = await window.LoginV21Workspace.loadAuthority();
      return window.LoginV21Candidate.validateNegativeCases(authority, window.LoginRepoHref);
    });
    if (!negativeResults.every(row => row.result === "EXPECTED_FAIL")) throw new Error("LOGIN_RESOURCE_NEGATIVE_CASE_FAILED");

    const matrixRows = [];
    for (const viewport of profile.viewport_matrix.viewports_css_px) {
      for (const fontScale of profile.viewport_matrix.font_scales) {
        const state = "DEFAULT";
        await page.evaluate(async input => window.LoginV21Workspace.renderPreview(
          { width: input.width, height: input.height, unit: "css_px" }, input.fontScale, input.state
        ), { width: viewport.width, height: viewport.height, fontScale, state });
        const run = await page.evaluate(() => {
          const pageRoot = document.querySelector("#login-LoginPageRoot");
          const all = window.LoginV21Workspace.measureElements();
          const button = document.querySelector("#login-LoginPrimaryButton").getBoundingClientRect();
          const footer = document.querySelector("#login-Footer").getBoundingClientRect();
          const title = document.querySelector("#login-WelcomeTitle").getBoundingClientRect();
          return {
            measured_count: all.length,
            node_ids: all.map(row => row.node_id),
            page_width: pageRoot.getBoundingClientRect().width,
            page_height: pageRoot.getBoundingClientRect().height,
            page_scroll_width: pageRoot.scrollWidth,
            button: { x: button.x, y: button.y, width: button.width, height: button.height, right: button.right, bottom: button.bottom },
            footer: { x: footer.x, y: footer.y, width: footer.width, height: footer.height },
            title: { width: title.width, height: title.height, font: getComputedStyle(document.querySelector("#login-WelcomeTitle")).fontSize },
            state: pageRoot.dataset.loginVisualState,
            font_scale: pageRoot.dataset.loginFontScale,
            viewport: pageRoot.dataset.loginViewport
          };
        });
        requiredSet(run.node_ids, expectedNodeIds, `matrix DOM map ${viewport.id} ${fontScale}`);
        if (run.page_width !== viewport.width || run.page_scroll_width > viewport.width + 1 || run.button.width <= 0 || run.button.height <= 0) {
          throw new Error(`LOGIN_MATRIX_GEOMETRY_INVALID ${viewport.id} font=${fontScale} ${JSON.stringify(run)}`);
        }
        const caseId = `${viewport.id}-font-${fontScale.toFixed(1)}`;
        const screenshotPath = path.join(screenshotDir, `${caseId}.png`);
        await page.locator("#login-LoginPageRoot").screenshot({ path: screenshotPath, animations: "disabled" });
        const domPath = path.join(evidenceDir, `${caseId}-dom.json`);
        await fs.writeFile(domPath, `${JSON.stringify({ page_id: "auth_login_v2", source_sha: sourceSha, run_id: runId, viewport, font_scale: fontScale, measured: await page.evaluate(() => window.LoginV21Workspace.measureElements()) }, null, 2)}\n`);
        const evidenceBytes = await fs.readFile(screenshotPath);
        matrixRows.push({ case_id: caseId, viewport: { width: viewport.width, height: viewport.height, unit: "css_px" }, font_scale: fontScale,
          state_id: state, result: "PASS_MEASUREMENT_ONLY", screenshot_path: path.relative(evidenceDir, screenshotPath).replaceAll(path.sep, "/"),
          screenshot_sha256: sha256Bytes(evidenceBytes), dom_path: path.basename(domPath), dom_sha256: sha256Bytes(await fs.readFile(domPath)), geometry: run });
      }
    }

    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "ACCOUNT_FOCUS"));
    const accountFocus = await page.evaluate(() => ({ focused: document.activeElement?.id, state: document.querySelector("#login-LoginPageRoot")?.dataset.loginVisualState }));
    if (accountFocus.focused !== "login-AccountInput" || accountFocus.state !== "ACCOUNT_FOCUS") throw new Error("LOGIN_ACCOUNT_FOCUS_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "PASSWORD_FOCUS"));
    const passwordFocus = await page.evaluate(() => ({ focused: document.activeElement?.id, state: document.querySelector("#login-LoginPageRoot")?.dataset.loginVisualState }));
    if (passwordFocus.focused !== "login-PasswordInput" || passwordFocus.state !== "PASSWORD_FOCUS") throw new Error("LOGIN_PASSWORD_FOCUS_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "PASSWORD_SHOWN"));
    if (await page.locator("#login-PasswordInput").getAttribute("type") !== "text") throw new Error("LOGIN_PASSWORD_SHOWN_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "PASSWORD_HIDDEN"));
    if (await page.locator("#login-PasswordInput").getAttribute("type") !== "password") throw new Error("LOGIN_PASSWORD_HIDDEN_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "INPUT_ERROR"));
    if (!(await page.locator("[data-login-error]").isVisible())) throw new Error("LOGIN_ERROR_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "LOADING"));
    if (!(await page.locator("#login-LoginPrimaryButton").isDisabled())) throw new Error("LOGIN_LOADING_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "ACTION_DISABLED"));
    if (!(await page.locator("#login-LoginPrimaryButton").isDisabled())) throw new Error("LOGIN_DISABLED_ACTION_STATE_FAILED");
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "KEYBOARD_SIMULATION"));
    const keyboardResult = await page.evaluate(() => ({
      visible: !document.querySelector("#login-keyboard-overlay").hidden,
      focused: document.activeElement?.id,
      label: document.querySelector("#login-keyboard-overlay")?.getAttribute("aria-label")
    }));
    if (!keyboardResult.visible || keyboardResult.focused !== "login-PasswordInput" || !keyboardResult.label.includes("非原生键盘")) {
      throw new Error("LOGIN_KEYBOARD_SIMULATION_FAILED");
    }

    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "DEFAULT"));
    await page.locator("#login-AccountInput").fill("design-test@example.invalid");
    await page.locator("#login-PasswordInput").fill("W6A-CANDIDATE-ONLY-NOT-A-REAL-SECRET");
    await page.locator("#login-VisibilityAction").click();
    if (await page.locator("#login-PasswordInput").getAttribute("type") !== "text" ||
        await page.locator("#login-VisibilityAction").getAttribute("aria-pressed") !== "true") {
      throw new Error("LOGIN_REAL_PASSWORD_VISIBILITY_ACTION_FAILED");
    }
    const filledPath = path.join(screenshotDir, "login-v2_1-filled-test-values.png");
    await page.locator("#login-LoginPageRoot").screenshot({ path: filledPath, animations: "disabled" });
    await page.locator("#login-LoginPrimaryButton").click();
    await page.waitForFunction(() => document.querySelector("#login-LoginPrimaryButton")?.textContent.includes("登录中"), { timeout: 5000 });
    const loadingPath = path.join(screenshotDir, "login-v2_1-loading-test-values.png");
    await page.locator("#login-LoginPageRoot").screenshot({ path: loadingPath, animations: "disabled" });
    await page.waitForSelector("[data-login-error]", { timeout: 5000 });
    const errorPath = path.join(screenshotDir, "login-v2_1-error-test-values.png");
    await page.locator("#login-LoginPageRoot").screenshot({ path: errorPath, animations: "disabled" });
    await page.locator("#login-ForgotPasswordAction").click();
    if (await page.url() !== candidateUrl) throw new Error("FORGOT_PASSWORD_ACTION_NAVIGATED");
    await page.locator("#login-RegisterAction").click();
    if (await page.url() !== candidateUrl) throw new Error("REGISTER_ACTION_NAVIGATED");
    await page.locator("#login-AccountInput").fill("");
    await page.locator("#login-PasswordInput").fill("");

    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 864, height: 1536, unit: "css_px" }, 1, "DEFAULT"));
    await page.evaluate(() => document.fonts.ready);
    const referenceCanvasPath = path.join(screenshotDir, "login-v2_1-structured-864x1536.png");
    const comparisonCanvas = page.locator("#login-LoginPageRoot");
    await comparisonCanvas.scrollIntoViewIfNeeded();
    const comparisonBounds = await comparisonCanvas.boundingBox();
    if (!comparisonBounds || comparisonBounds.width !== 864 || comparisonBounds.height !== 1536) {
      throw new Error(`LOGIN_COMPARISON_CANVAS_BOUNDS_INVALID ${JSON.stringify(comparisonBounds)}`);
    }
    const captureViewport = page.viewportSize();
    if (comparisonBounds.x < 0 || comparisonBounds.y < 0 ||
        comparisonBounds.x + comparisonBounds.width > captureViewport.width ||
        comparisonBounds.y + comparisonBounds.height > captureViewport.height) {
      throw new Error(`LOGIN_COMPARISON_CANVAS_CLIPPED_BY_CAPTURE_VIEWPORT ${JSON.stringify({ comparisonBounds, captureViewport })}`);
    }
    // Capture the exact measured artboard rectangle. Locator screenshots round
    // fractional document coordinates outward, which can add a spurious pixel
    // even when the rendered canvas is exactly 864x1536 CSS px. This clip keeps
    // the canvas at native size; the comparator still refuses all resizing.
    await page.screenshot({ path: referenceCanvasPath, clip: comparisonBounds, animations: "disabled" });
    const comparisonCapture = {
      method: "browser screenshot clipped to measured LoginPageRoot CSS bounds",
      css_bounds: comparisonBounds,
      capture_viewport_css_px: captureViewport,
      output_canvas_css_px: [864, 1536],
      resizing: "NONE",
      visual_review_status: "MEASUREMENT_ONLY_NOT_VISUAL_PASS"
    };
    await page.evaluate(() => window.LoginV21Workspace.selectLayer("LoginPageRoot"));
    const finalDomPath = path.join(evidenceDir, "login-v2_1-structured-dom-864x1536.json");
    const finalDom = await page.evaluate(() => ({
      page_id: window.LoginV21Workspace.pageId,
      source_sha: window.LoginV21Workspace.sourceSha,
      profile: window.LoginV21Workspace.getResponsiveDiagnostics(),
      bounds: window.LoginV21Workspace.measureElements(),
      inspector: window.LoginV21Workspace.getInspector("LoginPageRoot"),
      privacy: { real_auth_service_connected: false, test_credentials_real: false, native_runtime: "NOT_VERIFIED" }
    }));
    const finalBoundsByNode = new Map(finalDom.bounds.map(row => [row.node_id, row]));
    const boxOf = nodeId => finalBoundsByNode.get(nodeId)?.bounds;
    const backgroundBox = boxOf("Background");
    const backgroundImageBox = boxOf("MorningLakeBGContent");
    const visibilityBox = boxOf("VisibilityIcon");
    if (!backgroundBox || !backgroundImageBox ||
        Math.abs(backgroundImageBox.width - backgroundBox.width) > 0.1 ||
        Math.abs(backgroundImageBox.height - backgroundBox.height) > 0.1) {
      throw new Error(`LOGIN_BACKGROUND_ASSET_DOM_BOUNDS_INVALID ${JSON.stringify({ backgroundBox, backgroundImageBox })}`);
    }
    if (!visibilityBox || visibilityBox.width !== 27 || visibilityBox.height !== 27) {
      throw new Error(`LOGIN_VISIBILITY_ICON_DOM_BOUNDS_INVALID ${JSON.stringify(visibilityBox)}`);
    }
    const debugOverlayPresent = await page.locator("#login-LoginPageRoot .login-state-chip").count();
    if (debugOverlayPresent !== 0) throw new Error("LOGIN_DEBUG_STATE_OVERLAY_LEAKED_INTO_PAGE_ARTBOARD");
    const rendererIntegrity = {
      background_asset_matches_background_dom_box: true,
      visibility_icon_css_px: [visibilityBox.width, visibilityBox.height],
      page_debug_state_overlay: "ABSENT_FROM_ARTBOARD",
      candidate_resource_status_remains_in_dom: finalDom.profile.resources.some(row => row.status === "CANDIDATE_UNAPPROVED_ASSET"),
      status: "PASS"
    };
    await fs.writeFile(finalDomPath, `${JSON.stringify(finalDom, null, 2)}\n`);

    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1, "DEFAULT"));
    const fontOne = await page.locator("#login-WelcomeTitle").evaluate(node => getComputedStyle(node).fontSize);
    await page.evaluate(async () => window.LoginV21Workspace.renderPreview({ width: 390, height: 844, unit: "css_px" }, 1.5, "DEFAULT"));
    const fontOneFive = await page.locator("#login-WelcomeTitle").evaluate(node => getComputedStyle(node).fontSize);
    if (fontOne === fontOneFive) throw new Error("LOGIN_FONT_SCALE_DID_NOT_CHANGE_TEXT_MEASUREMENT");

    const sourceImage = path.join(REPO_ROOT, "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png");
    const referenceSha256 = sha256Bytes(await fs.readFile(sourceImage));
    const differenceNodeIds = {
      CROP_MISMATCH: ["Background", "MorningLakeBGContent", "LakeToWhiteTransition", "WhiteContentSurface"],
      GEOMETRY_MISMATCH: ["MainContent", "WelcomeTitle", "WelcomeSubtitle", "AccountGroup", "AccountField", "PasswordGroup", "PasswordField", "ForgotPasswordAction", "LoginPrimaryButton", "RegisterActionGroup", "Footer"],
      TYPOGRAPHY_MISMATCH: ["WelcomeTitle", "WelcomeSubtitle", "AccountLabel", "PasswordLabel"],
      ASSET_MISMATCH: ["WatersideGrassDecoration"]
    };
    const differenceRootCauses = {
      CROP_MISMATCH: "The candidate uses the W2-verified BG_CONTENT master with provisional scene clipping and a white transition; no approved source-to-frozen crop mapping is registered.",
      GEOMETRY_MISMATCH: "The measured candidate geometry is compared with raster-derived approximate bounds; no numeric responsive profile is frozen for Login.",
      TYPOGRAPHY_MISMATCH: "The browser renders live text with the candidate font stack; the reference typography is raster-authored and no exact web font profile is registered.",
      ASSET_MISMATCH: "No independent approved footer grass asset is present; the isolated candidate retains its explicitly unapproved CSS expression."
    };
    const visualDifferenceReview = {
      schema_version: "1.0",
      page_id: "auth_login_v2",
      authority_version: "V2.1",
      candidate_source_sha: sourceSha,
      github_run_id: runId,
      reference: { path: "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png", sha256: referenceSha256, dimensions_px: [864, 1536] },
      candidate_canvas: { dimensions_css_px: [864, 1536], coordinate_unit: "css_px", native_runtime: "NOT_VERIFIED" },
      difference_policy: "Measurements and pixels are diagnostic. No numeric visual acceptance threshold is inferred; product owner visual acceptance remains required.",
      items: Object.entries(differenceNodeIds).flatMap(([category, nodeIds]) => nodeIds.map(nodeId => {
        const row = finalBoundsByNode.get(nodeId);
        return {
          category,
          page_id: "auth_login_v2",
          component_id: nodeId,
          authority_ref: row?.authority_ref?.path || "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png",
          actual: row ? { bounds_css_px: row.bounds, typography: row.typography, resource_ref: row.resource_ref, resource_status: row.resource_status } : null,
          expected: row?.expected_bounds || "See frozen visual authority and V2.1 page composition rule.",
          measured_deviation_css_px: row?.deviation || null,
          root_cause: differenceRootCauses[category],
          repair_status: category === "ASSET_MISMATCH" ? "UNVERIFIED_CANDIDATE_ASSET" : "CANDIDATE_REVIEW_REQUIRED",
          user_approval_required: true
        };
      })),
      authority_gaps: [
        { id: "LOGIN_FROZEN_CANVAS_TO_DP_MAPPING_UNBOUND", status: "AUTHORITY_GAP", source: "scripts/design/page_candidates/auth_login_v2/authority_audit_v1.json" },
        { id: "LOGIN_RESPONSIVE_NUMERIC_AUTHORITY_UNBOUND", status: "REVIEW_REQUIRED", source: "scripts/design/page_candidates/auth_login_v2/responsive_candidate_v1.json" },
        { id: "LOGIN_FOOTER_ASSET_MISSING", status: "UNVERIFIED", source: "scripts/design/page_candidates/auth_login_v2/authority_audit_v1.json" }
      ],
      status: "USER_VISUAL_ACCEPTANCE_PENDING"
    };
    const visualDifferencePath = path.join(evidenceDir, "login-candidate-visual-differences.json");
    await fs.writeFile(visualDifferencePath, `${JSON.stringify(visualDifferenceReview, null, 2)}\n`);
    const summary = {
      schema_version: "1.0",
      page_id: "auth_login_v2",
      authority_version: "V2.1",
      candidate_source_sha: sourceSha,
      candidate_url: candidateUrl,
      online_preview: "BLOCKED_BY_W5",
      ci_run_id: runId,
      generated_at: now(),
      module_test_status: "PASS",
      browser_engine: "Chromium",
      source_sha_verified: true,
      workbench: { implementation: "W3 createStructuredDomAdapter", kind: "structured-dom-candidate", page_route_changed: false },
      w1: { ...upstreamEvidence.w1, profile_status: "DRAFT", numeric_authority: "UNBOUND_DRAFT", generic_numeric_matrix: "NOT_RUN" },
      w2: { ...upstreamEvidence.w2, binding_path: "design/pages/account_privacy/Login/Page_Resource_Binding_V1.json", required_assets: ["page_visual_authority_v2_1", "background_bg_content"], source_attachment: "MISSING", footer_asset: "MISSING", shared_icon_publication: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING" },
      admission: upstreamEvidence.admission,
      matrix: { status: "PASS_MEASUREMENT_ONLY", expected_case_count: 12, recorded_case_count: matrixRows.length, profile_path: `${CANDIDATE_REL}/responsive_candidate_v1.json`, cases: matrixRows },
      states: { accountFocus: accountFocus.focused, passwordFocus: passwordFocus.focused, passwordShown: "PASS", passwordHidden: "PASS", error: "PASS", loading: "PASS", disabledAction: "PASS", keyboardSimulation: "PASS", localSubmitSimulation: "LOADING_THEN_CANDIDATE_ERROR", noBackend: true },
      negative_resource_tests: negativeResults,
      interaction: { component_tree_to_dom: "PASS", dom_to_tree: "PASS", inspector_actual_bounds: "PASS", missing_node_fail_closed: "PASS", visibility_button: "PASS", forgot_action_no_navigation: "PASS", register_action_no_navigation: "PASS" },
      renderer_integrity: rendererIntegrity,
      visual_difference_review: { path: path.basename(visualDifferencePath), sha256: sha256Bytes(await fs.readFile(visualDifferencePath)), status: "USER_VISUAL_ACCEPTANCE_PENDING", item_count: visualDifferenceReview.items.length },
      visuals: { frozen_reference_path: "design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png", frozen_sha256: referenceSha256, rendered_path: path.relative(evidenceDir, referenceCanvasPath).replaceAll(path.sep, "/"), rendered_sha256: sha256Bytes(await fs.readFile(referenceCanvasPath)), overlay: "generated by compare_login_candidate_visual.py; no resizing", diff: "generated by compare_login_candidate_visual.py; measurement only", visual_result: "MEASUREMENT_ONLY_NOT_VISUAL_PASS" },
      errors: (() => {
        const expectedMissing404 = browserErrors.http.filter(row => row.status === 404 && row.url.includes("__w6a_missing.png"));
        const remainingConsole = browserErrors.console.slice();
        const expectedNegativeConsole = [];
        for (const _row of expectedMissing404) {
          const index = remainingConsole.findIndex(message => /Failed to load resource:.*404/i.test(message));
          if (index >= 0) expectedNegativeConsole.push(remainingConsole.splice(index, 1)[0]);
        }
        return { console: remainingConsole, page: browserErrors.page, network: browserErrors.network, http: unexpectedHttp,
          expected_negative: { missing_resource_http_404: expectedMissing404, console_messages: expectedNegativeConsole } };
      })(),
      acceptance: { visual: "USER_VISUAL_ACCEPTANCE_PENDING", native_runtime: "NOT_VERIFIED", public_deployment: "BLOCKED_BY_W5_AND_UNCONFIRMED_ICON_AND_FOOTER_PUBLICATION" }
    };
    if (summary.errors.console.length || summary.errors.page.length || summary.errors.network.length || summary.errors.http.length) {
      summary.module_test_status = "FAIL";
      summary.failure_reason = "Unexpected browser console, page, network, or HTTP error";
    }
    await fs.writeFile(path.join(evidenceDir, "login-candidate-browser-report.json"), `${JSON.stringify(summary, null, 2)}\n`);
    await fs.writeFile(path.join(evidenceDir, "login-candidate-component-tree.json"), `${JSON.stringify(tree, null, 2)}\n`);
    await fs.writeFile(path.join(evidenceDir, "login-candidate-negative-resource-report.json"), `${JSON.stringify(negativeResults, null, 2)}\n`);
    await fs.writeFile(path.join(evidenceDir, "login-candidate-dom-measurements.json"), `${JSON.stringify(finalDom, null, 2)}\n`);
    await fs.writeFile(path.join(evidenceDir, "login-candidate-evidence-manifest.json"), `${JSON.stringify({
      schema_version: "1.0", page_id: "auth_login_v2", contract_id: "auth-login-v2-draft", contract_version: "V2.1",
      contract_path: "design/system_v3/07_Responsive_System/page_contract_examples/auth_login_v2.envelope.json",
      contract_sha256: "91195ed22924085fcd285de9b1e01ccccd4a6ddb7d7e0b179a955294a5811bee",
      profile_id: "auth-login-responsive-profile-draft-v1", profile_path: "design/system_v3/07_Responsive_System/page_contract_examples/auth_login_v2.profile.json",
      profile_sha256: "1afcf7b14239aa87496ecb465759989e689d258bdf12ea20fd82c6708028c2d8",
      resource_binding_path: "design/pages/account_privacy/Login/Page_Resource_Binding_V1.json",
      resource_binding_sha256: "df91cfb156dc5fdf71a7fdf6af3e066abf8d01d46f2b81229edd4a2c1dba5e6a",
      candidate_source_sha: sourceSha, candidate_build_sha: sourceSha, deploy_sha: null, github_run_id: runId,
      upstream_validation_evidence: upstreamEvidence,
      browser_matrix: { result: "PASS_MEASUREMENT_ONLY", expected_case_count: 12, recorded_case_count: matrixRows.length },
      browser_report: { path: "login-candidate-browser-report.json", sha256: sha256Bytes(await fs.readFile(path.join(evidenceDir, "login-candidate-browser-report.json"))) },
      visual_difference_review: { path: path.basename(visualDifferencePath), sha256: sha256Bytes(await fs.readFile(visualDifferencePath)) },
      screenshot: { path: path.relative(evidenceDir, referenceCanvasPath).replaceAll(path.sep, "/"), sha256: sha256Bytes(await fs.readFile(referenceCanvasPath)), capture: comparisonCapture },
      dom: { path: path.basename(finalDomPath), sha256: sha256Bytes(await fs.readFile(finalDomPath)) },
      visual_comparison: "MEASUREMENT_ONLY_NOT_VISUAL_PASS", public_preview: "BLOCKED_BY_W5",
      native_runtime: "NOT_VERIFIED", user_acceptance: "USER_VISUAL_ACCEPTANCE_PENDING", page_acceptance: "NOT_VERIFIED", generated_at: now()
    }, null, 2)}\n`);
    if (summary.module_test_status !== "PASS") throw new Error(summary.failure_reason);
    console.log(JSON.stringify({ status: summary.module_test_status, source_sha: sourceSha, run_id: runId, evidence_dir: evidenceDir,
      candidate_matrix_cases: matrixRows.length, measured_component_count: measured.length, negative_cases: negativeResults.length,
      online_preview: summary.online_preview }, null, 2));
    await context.close();
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
}

main().catch(error => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
