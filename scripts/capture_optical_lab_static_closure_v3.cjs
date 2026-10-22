const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "playwright"));

const SITE_BASE = new URL(process.env.EDGEGLOW_SITE_BASE || "http://127.0.0.1:8765/");
const PUBLIC_SITE_BASE = new URL(process.env.OPTICAL_PUBLIC_BASE_URL || "https://pan277942135.github.io/Yujian_App/");
const SOURCE_ROOT = path.resolve(process.env.PR_SOURCE_ROOT || process.cwd());
const OUTPUT_DIR = path.resolve(process.env.OPTICAL_EVIDENCE_DIR || "optical-static-v3-evidence");
const PUBLIC_DIR = process.env.OPTICAL_PUBLIC_EVIDENCE_DIR ? path.resolve(process.env.OPTICAL_PUBLIC_EVIDENCE_DIR) : null;
const SOURCE_SHA = process.env.PR_SOURCE_SHA || process.env.GITHUB_SHA || "LOCAL_UNPINNED";
const RECOVERY_BASELINE_SHA = process.env.OPTICAL_RECOVERY_BASELINE_SHA || "76512ea4c889e4963cc8ed42662f2b0d344b835b";
const ACCEPTANCE_PATH = "design/manager/nh01_acceptance/edgeglow-fish-static/";
const REVIEW_PATH = ACCEPTANCE_PATH + "optical-review/";
const VIEWPORTS = [[390, 844], [320, 640], [412, 915], [800, 360], [1152, 1536]];
const REQUIRED_PATHS = ["B1", "B2", "B3", "B4", "G1", "G2", "G3"];

const configPath = "design/pages/recognition/processing/handoff/Processing_Engineering_Import_Config_V1.json";
const manifestPath = "design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json";
const fixturePath = "design/manager/nh01_acceptance/edgeglow-fish-static/fixture.json";
const photoPath = "design/system/fish_media_display_v1/assets/real_catch_source.jpg";
const reviewBboxPath = "design/manager/optical-lab/review_v1/fish_focus_case_b_bbox.json";
const reviewAlphaPath = "design/manager/optical-lab/review_v1/fish_focus_review_only_alpha.png";
const baselineEvidencePath = "design/manager/optical-lab/review_v1/evidence/baseline-head-34de";

function sha256(bytes) { return crypto.createHash("sha256").update(bytes).digest("hex"); }
function readJson(relative) { return JSON.parse(fs.readFileSync(path.join(SOURCE_ROOT, relative), "utf8")); }
function writeDataUrl(filename, dataUrl) {
  const match = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(dataUrl || "");
  assert.ok(match, "browser did not return a PNG data URL for " + filename);
  fs.writeFileSync(path.join(OUTPUT_DIR, filename), Buffer.from(match[1], "base64"));
}
async function canvasPng(page, filename) {
  const dataUrl = await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
  writeDataUrl(filename, dataUrl);
}
async function canvasAlphaSummary(page) {
  return page.locator("#previewCanvas").evaluate(canvas => {
    const ctx=canvas.getContext("2d",{willReadFrequently:true});
    const rgba=ctx.getImageData(0,0,canvas.width,canvas.height).data;
    let transparent=0,translucent=0,opaque=0,visible=0;
    for(let i=3;i<rgba.length;i+=4){
      const a=rgba[i];
      if(a===0)transparent++;else if(a<255)translucent++;else opaque++;
      if(a>0)visible++;
    }
    return {width:canvas.width,height:canvas.height,transparent_pixels:transparent,translucent_pixels:translucent,opaque_pixels:opaque,visible_pixels:visible};
  });
}
async function setTargetViewport(page, width, height) {
  await page.locator("#viewportSelect").evaluate((select, value) => {
    if (![...select.options].some(option => option.value === value)) select.add(new Option(value, value));
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }, `${width}x${height}`);
  await page.waitForFunction(([w, h]) => {
    const canvas = document.querySelector("#previewCanvas");
    return canvas?.width === w && canvas?.height === h;
  }, [width, height]);
}
async function setLayer(page, name, checked) {
  await page.locator(`#layerControls input[data-layer="${name}"]`).evaluate((input, value) => {
    input.checked = value;
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, checked);
}
async function setReviewLayer(page, id, checked) {
  await page.locator(`#${id}`).evaluate((input, value) => {
    input.checked = value;
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, checked);
}
async function waitForPaint(page) { await page.waitForTimeout(100); }
async function captureBoard(page, filename) {
  await page.locator("#boards").screenshot({ path: path.join(OUTPUT_DIR, filename) });
}
async function measureReceiving(page, candidate) {
  await page.selectOption("#staticCandidate", candidate);
  await page.selectOption("#fieldLayerView", "receiving");
  await page.locator("#fieldToggle").uncheck();
  await waitForPaint(page);
  await page.locator("#previewCanvas").evaluate(canvas => {
    window.__staticP1ReceivingOff = new Uint8ClampedArray(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data);
  });
  await page.locator("#fieldToggle").check();
  await waitForPaint(page);
  return page.locator("#previewCanvas").evaluate(canvas => {
    const after = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    const before = window.__staticP1ReceivingOff;
    const rect = JSON.parse(canvas.dataset.photoContentRect || "null");
    if (!rect) throw new Error("missing renderer photoContentRect");
    const photo = { x: rect.left, y: rect.top, w: rect.right - rect.left, h: rect.bottom - rect.top };
    const areas = [
      { id: "G_UL", x0: 0, x1: .36, y0: 0, y1: .30, gold: true },
      { id: "B_UR", x0: .64, x1: 1, y0: 0, y1: .34, gold: false },
      { id: "G_LL", x0: 0, x1: .42, y0: .68, y1: 1, gold: true },
      { id: "B_LR", x0: .58, x1: 1, y0: .66, y1: 1, gold: false },
    ];
    let totalAbsDelta = 0, totalSamples = 0;
    const corners = areas.map(area => {
      let absDelta = 0, signedRole = 0, samples = 0, changed = 0;
      const x0 = Math.max(0, Math.floor(photo.x + area.x0 * photo.w)), x1 = Math.min(canvas.width, Math.ceil(photo.x + area.x1 * photo.w));
      const y0 = Math.max(0, Math.floor(photo.y + area.y0 * photo.h)), y1 = Math.min(canvas.height, Math.ceil(photo.y + area.y1 * photo.h));
      for (let y = y0; y < y1; y += 2) for (let x = x0; x < x1; x += 2) {
        const i = (y * canvas.width + x) * 4, dr = after[i] - before[i], dg = after[i + 1] - before[i + 1], db = after[i + 2] - before[i + 2];
        absDelta += (Math.abs(dr) + Math.abs(dg) + Math.abs(db)) / 3;
        signedRole += area.gold ? dr - db : db - dr;
        if (Math.max(Math.abs(dr), Math.abs(dg), Math.abs(db)) >= 8) changed++;
        samples++;
      }
      totalAbsDelta += absDelta; totalSamples += samples;
      return { island: area.id, changed_ratio_at_8: changed / Math.max(1, samples), mean_abs_rgb_delta: absDelta / Math.max(1, samples), expected_color_role_margin: signedRole / Math.max(1, samples) };
    });
    return { photoContentRect: rect, corners, mean_abs_rgb_delta: totalAbsDelta / Math.max(1, totalSamples), interpretation: "RENDER_SIGNAL_ONLY_NOT_VISUAL_PASS" };
  });
}

async function captureLivePredeployPage(browser, url, targetSourceSha, outputDir) {
  const result = { status: "BLOCKED", url, target_source_sha: targetSourceSha };
  let page;
  try {
    page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() => window.OPTICAL_LAB_BUILD_INFO__?.head_sha && document.querySelector("#previewCanvas"), null, { timeout: 15000 });
    const build = await page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__);
    result.page_source_sha = build.head_sha;
    result.page_acceptance_center_source_sha = build.acceptance_center_source_sha;
    result.status = build.head_sha === targetSourceSha ? "CAPTURED_TARGET_SHA_ALREADY_ONLINE" : "CAPTURED_PREDEPLOY_PAGE";
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    await page.locator("#stateButtons button").nth(0).click();
    await page.locator("#staticCandidate").selectOption("B");
    await page.screenshot({ path: path.join(outputDir, "baseline-cloud-default.png"), fullPage: true });
    result.default_page_png = "baseline-cloud-default.png";
    await page.locator("#advancedDiagnostics").evaluate(node => { node.open = true; });
    for (const [name, enabled] of Object.entries({ photo: true, receiving: true, outer: false, mid: false, core: false, hairlines: false, fishHalo: false, fishContour: false, status: false })) {
      await setLayer(page, name, enabled);
    }
    await page.locator("#fieldToggle").setChecked(true);
    await page.locator("#pathToggle").setChecked(false);
    await page.locator("#filamentToggle").setChecked(false);
    await page.locator("#fishToggle").setChecked(false);
    await page.locator("#fieldLayerView").selectOption("receiving");
    await waitForPaint(page);
    await page.locator("#previewCanvas").screenshot({ path: path.join(outputDir, "baseline-cloud-receiving-only.png") });
    result.receiving_only_png = "baseline-cloud-receiving-only.png";
  } catch (error) {
    result.error = error.message;
  } finally {
    await page?.close().catch(() => {});
  }
  return result;
}

async function main() {
  fs.rmSync(OUTPUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });

  const config = readJson(configPath);
  const fixture = readJson(fixturePath);
  const manifest = readJson(manifestPath);
  const bboxEvidence = readJson(reviewBboxPath);
  const photoBytes = fs.readFileSync(path.join(SOURCE_ROOT, photoPath));
  const alphaBytes = fs.readFileSync(path.join(SOURCE_ROOT, reviewAlphaPath));
  const photoSha = sha256(photoBytes);
  const alphaSha = sha256(alphaBytes);
  const registeredPhoto = config.p5_compose_import_bindings.image_bindings.test_photo;
  assert.equal(photoSha, registeredPhoto.sha256, "registered P5 photo bytes changed");
  assert.equal(fixture.photo.sha256, photoSha, "fixture photo SHA does not match the registered photo");
  assert.equal(fixture.photo.exif_orientation, 1, "expected the registered photo to have EXIF orientation 1");
  assert.deepEqual([fixture.photo.width, fixture.photo.height], [1152, 1536]);
  assert.deepEqual(config.p1_edge_field.paths.map(path => path.id).sort(), REQUIRED_PATHS);

  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const errors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    page.on("pageerror", error => errors.push(error.message));
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("requestfailed", request => errors.push("requestfailed: " + request.url() + " · " + request.failure()?.errorText));

    const acceptanceCaptureUrl = new URL(ACCEPTANCE_PATH, SITE_BASE).href;
    await page.goto(acceptanceCaptureUrl, { waitUntil: "networkidle", timeout: 30000 });
    const reviewEntry = page.locator("#opticalReviewEntry");
    assert.equal(await reviewEntry.evaluate(anchor => anchor.tagName.toLowerCase()), "a", "canonical Optical Review entry must remain a direct link");
    assert.ok(await reviewEntry.evaluate(anchor => new URL(anchor.getAttribute("href"), location.href).pathname.endsWith("/edgeglow-fish-static/optical-review/")), "canonical Acceptance Center must link to its in-project Optical Review route");
    const acceptanceScreenshot = path.join(OUTPUT_DIR, "acceptance-center-entry.png");
    await page.screenshot({ path: acceptanceScreenshot, fullPage: true });

    const reviewCaptureUrl = new URL(REVIEW_PATH, SITE_BASE).href;
    const acceptanceUrl = new URL(ACCEPTANCE_PATH, PUBLIC_SITE_BASE).href;
    const reviewUrl = new URL(REVIEW_PATH, PUBLIC_SITE_BASE).href;
    const livePredeployCapture = await captureLivePredeployPage(browser, reviewUrl, SOURCE_SHA, OUTPUT_DIR);
    await page.goto(reviewCaptureUrl, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() => {
      const canvas = document.querySelector("#previewCanvas");
      return document.querySelector("#overallStatus")?.textContent === "REVIEW_REQUIRED" &&
        document.querySelector("#photoName")?.textContent.includes("SHA-256 verified") &&
        canvas?.dataset.photoContentRect && window.OPTICAL_LAB_BUILD_INFO__?.head_sha;
    }, null, { timeout: 30000 });
    const buildInfo = await page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__);
    assert.equal(buildInfo.head_sha, SOURCE_SHA, "page build SHA must match the PR source SHA");
    const receivingOptics = await page.evaluate(() => window.OPTICAL_LAB_STATIC_RECEIVING_PARAMETERS__);
    const pageCandidates = await page.evaluate(() => window.OPTICAL_LAB_STATIC_CANDIDATES__);
    assert.ok(receivingOptics, "static Receiving Light parameters must be exposed by the rendered page");
    assert.ok(pageCandidates, "static candidate parameters must be exposed by the rendered page");
    const declaredOptics = readJson("design/manager/optical-lab/review_v1/candidates.json");
    const receivingContract = config.p1_edge_field.optical_stack.find(layer => layer.layer === "local_receiving_light");
    const receivingDepthRange = declaredOptics.receiving_light_depth_contract;
    assert.deepEqual(receivingDepthRange, receivingContract.contract_edge_depth_ratio, "declared candidate depth interval must match frozen P1");
    assert.equal(receivingOptics.opacityCap, declaredOptics.receiving_light_web_review_alpha_cap, "reported Receiving Light cap must match page code");
    assert.ok(receivingOptics.opacityCap <= receivingContract.runtime_alpha_cap, "post-multiplier Receiving Light cap must not exceed P1's runtime ceiling");
    assert.deepEqual(receivingContract.contract_alpha_before_multipliers, declaredOptics.contract_alpha_before_multipliers, "candidate base-alpha interval must match P1");
    assert.equal(receivingOptics.ovalGain, declaredOptics.receiving_light_oval_gain, "oval gain must match the candidate declaration");
    assert.equal(receivingOptics.composite, declaredOptics.receiving_light_composite, "Receiving Light must use the declared source-over composition");
    for (const candidate of ["A", "B", "C"]) {
      for (const key of ["id", "name", "receiving_field_scale", "primary_path_scale", "secondary_filament_scale", "receiving_depth_ratio"]) {
        assert.equal(pageCandidates[candidate][key], declaredOptics.static_candidates[candidate][key], `${candidate} ${key} must match the live page candidate`);
      }
      assert.ok(pageCandidates[candidate].receiving_field_scale <= 1 && pageCandidates[candidate].primary_path_scale <= 1 && pageCandidates[candidate].secondary_filament_scale <= 1, `${candidate} scales must not amplify frozen P1 values`);
      assert.ok(pageCandidates[candidate].receiving_depth_ratio >= receivingDepthRange[0] && pageCandidates[candidate].receiving_depth_ratio <= receivingDepthRange[1], `${candidate} Receiving Light depth must remain in the P1 range`);
      const nominalBaseAlpha = receivingContract.contract_alpha_before_multipliers[1] * pageCandidates[candidate].receiving_field_scale;
      assert.ok(nominalBaseAlpha >= receivingContract.contract_alpha_before_multipliers[0] && nominalBaseAlpha <= receivingContract.contract_alpha_before_multipliers[1], `${candidate} nominal base alpha must remain in P1's pre-multiplier range`);
    }
    assert.equal(await page.locator("#stateButtons button").count(), 3, "keep exactly the three frozen product visuals");
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    await page.locator("#stateButtons button").nth(0).click();
    await page.selectOption("#focusSource", "blocked");
    await page.selectOption("#staticCandidate", "B");
    await setTargetViewport(page, 390, 844);
    await waitForPaint(page);

    const geometry = await page.evaluate(() => window.OPTICAL_LAB_STATIC_GEOMETRY_DIAGNOSTIC());
    assert.equal(geometry.photo.sha256, photoSha);
    assert.deepEqual(geometry.paths.map(item => item.id).sort(), REQUIRED_PATHS);
    const pickGeometry = item => ({ id: item.id, family: item.family, role: item.role, island: item.island, start: item.start, control1: item.control1, control2: item.control2, end: item.end, core_width_dp: item.core_width_dp, visible_ratio: item.visible_ratio, island_weight: item.island_weight });
    assert.deepEqual(geometry.paths.map(pickGeometry), config.p1_edge_field.paths.map(pickGeometry), "mapped Web geometry must use the exact P1 path records");
    writeDataUrl("geometry-diagnostic-390x844.png", geometry.png_data_url);

    const viewportMatrix = [];
    for (const [width, height] of VIEWPORTS) {
      await setTargetViewport(page, width, height);
      const actual = await page.locator("#previewCanvas").evaluate(canvas => ({
        width: canvas.width, height: canvas.height,
        rect: JSON.parse(canvas.dataset.photoContentRect || "null"),
        transform: JSON.parse(canvas.dataset.photoTransform || "null"),
      }));
      const scale = Math.min(width / fixture.photo.width, height / fixture.photo.height);
      const drawnWidth = fixture.photo.width * scale, drawnHeight = fixture.photo.height * scale;
      const expected = { left: Math.max(0, (width - drawnWidth) / 2), top: Math.max(0, (height - drawnHeight) / 2), right: Math.min(width, (width + drawnWidth) / 2), bottom: Math.min(height, (height + drawnHeight) / 2) };
      for (const key of Object.keys(expected)) assert.ok(Math.abs(actual.rect[key] - expected[key]) < 0.01, `${width}x${height} ${key}: shared FIT rect mismatch`);
      assert.equal(actual.transform.sourceWidth, fixture.photo.width);
      assert.equal(actual.transform.sourceHeight, fixture.photo.height);
      assert.ok(Math.abs(actual.transform.translationX - Math.max(0, (width - drawnWidth) / 2)) < 0.01, `${width}x${height} FIT x translation mismatch`);
      assert.ok(Math.abs(actual.transform.translationY - Math.max(0, (height - drawnHeight) / 2)) < 0.01, `${width}x${height} FIT y translation mismatch`);
      assert.ok(actual.transform.translationX >= -0.01 && actual.transform.translationY >= -0.01, `${width}x${height} FIT must not crop or clamp photo coordinates`);
      const canvasPath = `viewport-B-${width}x${height}.png`;
      await canvasPng(page, canvasPath);
      viewportMatrix.push({ viewport: [width, height], photoContentRect: actual.rect, transform: actual.transform, rendered_png: canvasPath, result: "PASS" });
    }

    const candidateDefinitions = declaredOptics.static_candidates;
    const candidateSignal = {}, candidateAlphaSummary = {};
    const allLayerNames = ["photo", "receiving", "outer", "mid", "core", "hairlines", "fishHalo", "fishContour", "status"];
    const setOnlyLayers = async enabled => {
      for (const name of allLayerNames) await setLayer(page, name, enabled.includes(name));
    };
    for (const candidate of ["A", "B", "C"]) {
      await page.selectOption("#staticCandidate", candidate);
      await page.selectOption("#fieldLayerView", "composite");
      await page.setViewportSize({ width: 1440, height: 1100 });
      await setTargetViewport(page, 390, 844);
      await waitForPaint(page);
      await captureBoard(page, `static-${candidate}-desktop-board.png`);
      await canvasPng(page, `static-${candidate}-390x844.png`);
      await page.setViewportSize({ width: 390, height: 844 });
      await captureBoard(page, `static-${candidate}-narrow-board.png`);
      await page.screenshot({ path: path.join(OUTPUT_DIR, `static-${candidate}-narrow-page.png`), fullPage: true });
      candidateSignal[candidate] = await measureReceiving(page, candidate);

      await setOnlyLayers(["receiving"]);
      await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", false); await setReviewLayer(page, "filamentToggle", false); await setReviewLayer(page, "fishToggle", false);
      await page.selectOption("#fieldLayerView", "receiving"); await waitForPaint(page);
      await canvasPng(page, `receiving-light-only-${candidate}.png`);
      candidateAlphaSummary[candidate]=await canvasAlphaSummary(page);
      assert.ok(candidateAlphaSummary[candidate].visible_pixels>0 && candidateAlphaSummary[candidate].translucent_pixels>0 && candidateAlphaSummary[candidate].transparent_pixels>0,
        `${candidate} isolated Receiving Light PNG must preserve translucent pixels and a transparent canvas`);
      await setOnlyLayers(["photo", "receiving"]); await waitForPaint(page);
      await canvasPng(page, `receiving-light-over-photo-${candidate}.png`);
      await setOnlyLayers(allLayerNames);
      await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", true); await setReviewLayer(page, "filamentToggle", true); await setReviewLayer(page, "fishToggle", true);
      await page.selectOption("#fieldLayerView", "composite");
    }
    assert.ok(candidateSignal.A.mean_abs_rgb_delta < candidateSignal.B.mean_abs_rgb_delta && candidateSignal.B.mean_abs_rgb_delta < candidateSignal.C.mean_abs_rgb_delta,
      "static A/B/C Receiving Light scales must produce increasing render signal");

    // Source-over Receiving Light is composited over the same P1 FIT photo in
    // both renderer modes. Compare the measured field signal for candidate B
    // before collecting the separate layer exports.
    await page.selectOption("#rendererSelect", "software-cache");
    const softwareCacheReceivingSignal = await measureReceiving(page, "B");
    const rendererSignalRatio = softwareCacheReceivingSignal.mean_abs_rgb_delta / Math.max(.001, candidateSignal.B.mean_abs_rgb_delta);
    assert.ok(rendererSignalRatio >= 0.80 && rendererSignalRatio <= 1.40,
      `software-cache static Receiving Light should stay close to hardware-gradient (${rendererSignalRatio.toFixed(3)}×)`);
    await canvasPng(page, "receiving-light-over-photo-software-cache.png");
    await page.selectOption("#rendererSelect", "hardware-gradient");

    await page.selectOption("#staticCandidate", "B");
    await page.setViewportSize({ width: 1440, height: 1100 });
    await setTargetViewport(page, 390, 844);
    await page.locator("#advancedDiagnostics").evaluate(node => { node.open = true; });
    await page.locator("#fieldToggle").check();
    await page.locator("#pathToggle").check();
    await page.locator("#filamentToggle").check();
    await page.locator("#fishToggle").check();
    await setLayer(page, "photo", false);
    await setLayer(page, "status", false);
    await setLayer(page, "receiving", true);
    await setLayer(page, "outer", false); await setLayer(page, "mid", false); await setLayer(page, "core", false); await setLayer(page, "hairlines", false);
    await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", false); await setReviewLayer(page, "filamentToggle", false); await setReviewLayer(page, "fishToggle", false);
    await page.selectOption("#fieldLayerView", "receiving"); await waitForPaint(page); await canvasPng(page, "receiving-light-only.png");
    // Keep the same P5 source photo visible while every other overlay stays off.
    // This checks whether the Receiving Light reads as light on the real photo,
    // not only as colored blobs against the scene background.
    await setLayer(page, "photo", true); await setLayer(page, "receiving", false); await waitForPaint(page); await canvasPng(page, "receiving-light-off-photo.png");
    await setLayer(page, "receiving", true); await waitForPaint(page); await canvasPng(page, "receiving-light-over-photo.png");
    await setLayer(page, "photo", false);
    const layerCases = [
      ["outer-bloom-only.png", "outer"], ["mid-glow-only.png", "mid"], ["core-only.png", "core"],
    ];
    for (const [filename, enabled] of layerCases) {
      await setOnlyLayers([enabled]);
      await page.selectOption("#fieldLayerView", "primary");
      await setReviewLayer(page, "fieldToggle", false); await setReviewLayer(page, "pathToggle", true); await setReviewLayer(page, "filamentToggle", false);
      await waitForPaint(page); await canvasPng(page, filename);
    }
    await setOnlyLayers(["hairlines"]);
    await setReviewLayer(page, "pathToggle", false); await setReviewLayer(page, "filamentToggle", true);
    await page.selectOption("#fieldLayerView", "hairline"); await waitForPaint(page); await canvasPng(page, "hairline-echoes-only.png");
    await setOnlyLayers(allLayerNames);
    await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", true); await setReviewLayer(page, "filamentToggle", true); await setReviewLayer(page, "fishToggle", true);
    await page.selectOption("#fieldLayerView", "composite");
    await page.locator("#advancedDiagnostics").evaluate(node => { node.open = false; });
    await waitForPaint(page); await canvasPng(page, "full-composite-B-390x844.png");

    await page.locator("#stateButtons button").nth(1).click();
    await page.selectOption("#focusSource", "blocked");
    await waitForPaint(page);
    await captureBoard(page, "fish-focus-blocked-fish-located.png");
    await canvasPng(page, "fish-focus-blocked-fish-located-canvas.png");
    await page.selectOption("#focusSource", "review");
    await page.waitForFunction(() => document.querySelector("#photoName")?.textContent.includes("detector_case_b_night_flash") && document.querySelector("#maskName")?.textContent.includes("REVIEW ONLY"), null, { timeout: 30000 });
    await page.waitForFunction(() => document.querySelector("#focusReadiness")?.textContent.includes("REVIEW_PROXY_NOT_PRODUCTION"), null, { timeout: 30000 });
    await waitForPaint(page);
    await captureBoard(page, "fish-focus-review-proxy-fish-located.png");
    await canvasPng(page, "fish-focus-review-proxy-fish-located-canvas.png");
    await setOnlyLayers(["fishHalo", "fishContour"]);
    await setReviewLayer(page, "fieldToggle", false); await setReviewLayer(page, "pathToggle", false); await setReviewLayer(page, "filamentToggle", false); await setReviewLayer(page, "fishToggle", true);
    await page.selectOption("#fieldLayerView", "composite"); await waitForPaint(page);
    await canvasPng(page, "fish-focus-review-proxy-only.png");
    await setOnlyLayers(allLayerNames);
    await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", true); await setReviewLayer(page, "filamentToggle", true); await setReviewLayer(page, "fishToggle", true);
    await page.locator("#stateButtons button").nth(2).click();
    await waitForPaint(page);
    await captureBoard(page, "fish-focus-review-proxy-species-recognizing.png");
    await canvasPng(page, "fish-focus-review-proxy-species-recognizing-canvas.png");

    assert.deepEqual(errors, [], "browser page must have no uncaught errors or failed assets");
    const frozenState = manifest.product_states.find(state => state.id === "IMAGE_RECOGNIZING");
    const frozenPhotoSha = config.p5_compose_import_bindings.image_bindings.frozen_references.find(item => item.state === "IMAGE_RECOGNIZING").sha256;
    const candidateParameters = {
      task_id: "YUJIAN_EDGEGLOW_WEB_STATIC_VISUAL_CLOSURE_V3",
      authority: "REVIEW_ONLY_NOT_FROZEN",
      source_sha: SOURCE_SHA,
      data_source_photo_sha256: photoSha,
      receiving_field_geometry_authority: "P1 freezes radial-oval class, alpha/depth bounds and matching path island weights; exact oval layout/stops are from the non-frozen runtime source snapshot",
      frozen_path_authority: "P1 exact seven paths and the shared photoContentRect",
      receiving_field_opacity_cap: receivingOptics.opacityCap,
      receiving_field_composite: receivingOptics.composite,
      receiving_field_depth_contract: receivingDepthRange,
      contract_alpha_before_multipliers: receivingContract.contract_alpha_before_multipliers,
      candidate_differences: "P1-bounded base alpha/depth and separate static path-strength scales; all candidates share the same non-frozen review oval layout/stops and shared photoContentRect; Receiving Light adds no path geometry",
      receiving_oval_review_gain: receivingOptics.ovalGain,
      isolated_receiving_layer_alpha_integrity: candidateAlphaSummary,
      renderer_signal_parity: {
        hardware_gradient_mean_abs_rgb_delta: candidateSignal.B.mean_abs_rgb_delta,
        software_cache_mean_abs_rgb_delta: softwareCacheReceivingSignal.mean_abs_rgb_delta,
        software_to_hardware_ratio: rendererSignalRatio,
        interpretation: "RENDERER_COMPOSITING_PARITY_ONLY_NOT_VISUAL_PASS",
      },
      candidates: Object.fromEntries(["A", "B", "C"].map(candidate => [candidate, {
        id: candidateDefinitions[candidate].id,
        name: candidateDefinitions[candidate].name,
        receiving_field_scale: candidateDefinitions[candidate].receiving_field_scale,
        receiving_nominal_peak_alpha_after_review_gain_before_island_state_quality: Math.min(receivingOptics.opacityCap,
          config.p1_edge_field.optical_stack.find(layer => layer.layer === "local_receiving_light").contract_alpha_before_multipliers[1] * candidateDefinitions[candidate].receiving_field_scale),
        primary_path_scale: candidateDefinitions[candidate].primary_path_scale,
        secondary_filament_scale: candidateDefinitions[candidate].secondary_filament_scale,
      }])),
      visual_status: "REVIEW_REQUIRED",
    };
    const baselineSource = readJson(`${baselineEvidencePath}/source.json`);
    const baselineFiles = ["receiving-field-signal.json", "source.json"];
    for (const file of baselineFiles) fs.copyFileSync(path.join(SOURCE_ROOT, baselineEvidencePath, file), path.join(OUTPUT_DIR, `baseline-34de-${file}`));
    fs.copyFileSync(path.join(SOURCE_ROOT, "design/manager/optical-lab/review_v1/evidence/pre-change-cloud-default.jpg"), path.join(OUTPUT_DIR, "baseline-pre-change-cloud-default.jpg"));
    fs.copyFileSync(path.join(SOURCE_ROOT, reviewBboxPath), path.join(OUTPUT_DIR, "fish-focus-case-b-bbox.json"));
    fs.copyFileSync(path.join(SOURCE_ROOT, reviewAlphaPath), path.join(OUTPUT_DIR, "fish-focus-review-only-alpha.png"));
    fs.copyFileSync(path.join(SOURCE_ROOT, bboxEvidence.fixture_path), path.join(OUTPUT_DIR, "fish-focus-review-case-b-photo.jpg"));
    fs.writeFileSync(path.join(OUTPUT_DIR, "static-candidate-parameters.json"), JSON.stringify(candidateParameters, null, 2) + "\n");
    const renderedArtifacts = fs.readdirSync(OUTPUT_DIR).filter(file => /\.png$/i.test(file)).sort().map(file => {
      const bytes = fs.readFileSync(path.join(OUTPUT_DIR, file));
      return { file, bytes: bytes.length, sha256: sha256(bytes) };
    });
    fs.writeFileSync(path.join(OUTPUT_DIR, "static-evidence-artifact-manifest.json"), JSON.stringify({ source_sha: SOURCE_SHA, photo_sha256: photoSha, artifacts: renderedArtifacts }, null, 2) + "\n");
    const report = {
      task_id: "YUJIAN_EDGEGLOW_WEB_STATIC_VISUAL_CLOSURE_V3",
      task_scope: "WEB_STATIC_ONLY",
      page_url: reviewUrl,
      acceptance_url: acceptanceUrl,
      pr_number: 166,
      source_sha: SOURCE_SHA,
      build_sha: buildInfo.head_sha,
      deployment_source_sha: SOURCE_SHA,
      deployment_source_sha_basis: "expected from this branch push; public route identity is verified by the post-deploy live smoke",
      data_source: { path: photoPath, sha256: photoSha, bytes: photoBytes.length, dimensions: [fixture.photo.width, fixture.photo.height], exif_orientation: fixture.photo.exif_orientation, display_mode: "FIT / NO CROP", identity: "REGISTERED_P5_FIXTURE; Frozen photo identity is SOURCE_PHOTO_IDENTITY_UNVERIFIED" },
      frozen_reference: { path: frozenState.keyframes[0].path, sha256: frozenPhotoSha, dimensions: [frozenState.keyframes[0].width, frozenState.keyframes[0].height], source_photo_identity: "SOURCE_PHOTO_IDENTITY_UNVERIFIED" },
      baseline: {
        source_sha: baselineSource.source_commit_sha,
        workflow_run_id: baselineSource.workflow_run_id,
        artifact_id: baselineSource.artifact_id,
        workflow_run_url: `https://github.com/pan277942135/Yujian_App/actions/runs/${baselineSource.workflow_run_id}`,
        capture: baselineSource.capture,
        original_screenshot_sha256: baselineSource.files["optical-review-page.png"].sha256,
        original_receiving_layer_sha256: baselineSource.files["receiving-field-only.png"].sha256,
        pixel_signal: "baseline-34de-receiving-field-signal.json",
        live_predeploy_capture: livePredeployCapture,
        screenshot_from_reviewed_local_baseline: "baseline-pre-change-cloud-default.jpg",
        assessment: "Baseline screenshots document the weak/default field; historical pixel signal proves only render changes, not optical quality.",
        status: "BASELINE_EVIDENCE_REVIEW_REQUIRED",
      },
      first_v3_failed_render: {
        source_sha: "4d02ad9dcac410174376a5d771837d1d796bc6fa",
        workflow_run_id: 39422517210,
        artifact_id: 12263717925,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39422517210/artifacts/12263717925",
        receiving_candidate_b_corner_changed_ratio: { top_left_gold: 0.0005467468562055768, top_right_blue: 0.008039982616253803, bottom_left_gold: 0, bottom_right_blue: 0.04056737588652482 },
        status: "REAL_BROWSER_CAPTURE_FAILED_VISIBILITY_GATE",
      },
      second_v3_failed_render: {
        source_sha: "f0386d8bf4da06ccbfbf210c250670ae98868d6c",
        workflow_run_id: 39425637563,
        artifact_id: 12265831623,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39425637563/artifacts/12265831623",
        candidate_b_mean_abs_rgb_delta_in_sampled_photo_corners: 2.893727041175498,
        assessment: "REAL_IMAGE_REVIEW_REJECTED_AS_STILL_TOO_WEAK_AND_LINE_LIKE",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK",
      },
      third_v3_failed_render: {
        source_sha: "a5c73c1ae5813879a68c62310556622cf0f13b36",
        workflow_run_id: 39427017498,
        artifact_id: 12265918165,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39427017498/artifacts/12265918165",
        candidate_b_mean_abs_rgb_delta_in_sampled_photo_corners: 3.3447503487821515,
        assessment: "P1_OVAL_LAYOUT_RESTORED_BUT_FIELD_STILL_TOO_FAINT_AT_0_34_OPACITY_CAP",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK",
      },
      fourth_v3_failed_render: {
        source_sha: "6138f994709c19cdcd121cb6bf9928eec2717ac5",
        workflow_run_id: 39427791943,
        artifact_id: 12266233847,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39427791943/artifacts/12266233847",
        assessment: "The 0.48 Web-only cap still reads primarily as faint path traces on the real P5 photo; direct live-page inspection and Chromium output did not show a perceptible broad receiving field.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK",
      },
      fifth_v3_failed_render: {
        source_sha: "64880b89c8a424e98bc8ce9eea4e08ece7e09bce",
        workflow_run_id: 39431484510,
        artifact_id: 12267708759,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39431484510/artifacts/12267708759",
        candidate_b_corner_mean_abs_rgb_delta: 9.384569110061593,
        candidate_c_corner_mean_abs_rgb_delta: 11.872467329672686,
        assessment: "Screen compositing increased measured pixel changes, but the isolated field still read as four corner spots and remained too weak beside Frozen in the real page screenshot.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK",
      },
      sixth_v3_failed_render: {
        source_sha: "f646cf17aab05740299d133abcfcc99060e871b8",
        workflow_run_id: 39433014752,
        artifact_id: 12268278853,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39433014752/artifacts/12268278853",
        candidate_b_mean_abs_rgb_delta: 5.056944284452641,
        candidate_c_mean_abs_rgb_delta: 6.269336609965596,
        assessment: "Actual Chromium single-layer PNG still read as four corner patches, and A/B/C composites remained far weaker than Frozen. The broad pass used the full primary curves, so the next pass constrains it to the frozen runtime-seed/visible-ratio segments and shifts weight from corner ovals to the edge field.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK_AND_CORNER_PATCHED",
      },
      seventh_v3_failed_render: {
        source_sha: "93f5e9a0f6f2686bb921685a99d8aaaef033ada1",
        workflow_run_id: 39434352243,
        artifact_id: 12270140451,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39434352243/artifacts/12270140451",
        candidate_b_mean_abs_rgb_delta: 2.843796797603958,
        candidate_c_mean_abs_rgb_delta: 3.547850350062066,
        assessment: "Real Chromium screenshots confirmed the Receiving Light still appeared as four faint corner dabs; constraining the diffuse field to short visible path segments reduced covered edge area. Next pass keeps exact P1 cubic control curves as the diffuse field's shape, raises the width/depth within the P1 depth interval, and attenuates only the separate corner ovals.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK_AND_PATCHED",
      },
      eighth_v3_failed_render: {
        source_sha: "58ca8b4d6c29266dd7d87a755442e22aa96cfa0e",
        workflow_run_id: 39435256712,
        artifact_id: 12269443136,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39435256712/artifacts/12269443136",
        candidate_b_mean_abs_rgb_delta: 6.858093665604347,
        candidate_c_mean_abs_rgb_delta: 8.461301181379524,
        assessment: "Actual Receiving-Light-over-photo and A/B/C Chromium PNGs still showed little edge illumination beside Frozen; B was predominantly faint path traces. Source inspection identified the 0.24 oval multiplier as reducing B's .07 × 3.2 peak alpha to .05376 before P1 island weighting and radial falloff. The next pass uses 1.0 oval review gain and fixes screen compositing in the software-cache renderer by caching the P1 FIT photo with its optical layers.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK_AND_PATCHED",
      },
      ninth_v3_failed_render: {
        source_sha: "dc00e51516837e0cd3d19203f29a7220d7240022",
        workflow_run_id: 39436867001,
        artifact_id: 12271421944,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39436867001/artifacts/12271421944",
        candidate_b_mean_abs_rgb_delta: 8.843208027749004,
        candidate_c_mean_abs_rgb_delta: 10.842529662481263,
        assessment: "The actual Receiving-Light-over-photo and A/B/C Chromium PNGs still read as faint edge arcs. The report also listed stale bloom pass ratios that did not match the page constants. Source geometry shows the frozen primary curves overshoot photoContentRect; clipping a centered bloom leaves too little illumination on the photograph side. This pass offsets only derived Receiving Light samples inward, keeps the frozen line records unchanged, and makes the captured parameter report assert against page constants.",
        status: "REAL_BROWSER_CAPTURE_VISUALLY_TOO_WEAK_AND_PARAMETER_PROVENANCE_MISMATCHED",
      },
      tenth_v3_failed_render: {
        source_sha: "5553fb7ad169ce5b05c38fc13fe60cbd6ce80dc4",
        workflow_run_id: 39439145572,
        artifact_id: 12272574099,
        artifact_url: "https://github.com/pan277942135/Yujian_App/actions/runs/39439145572/artifacts/12272574099",
        candidate_b_mean_abs_rgb_delta: 6.672579963905881,
        candidate_c_mean_abs_rgb_delta: 8.252825455336724,
        assessment: "Live GitHub Pages inspection and actual Chromium PNGs still read as faint path traces beside Frozen. The .32 review cap limited B/C nominal receiving alpha to .224/.280; measured corner deltas are renderer signal only and did not meet the optical goal.",
        status: "REAL_DEPLOYED_PAGE_VISUALLY_TOO_WEAK",
      },
      root_causes: [
        "The live default field was nearly invisible; the old pixel-delta smoke checked only for changed pixels and was incorrectly treated as visual evidence.",
        "Earlier Web rendering inferred a separate 3:4 photo rectangle, so portions of the receiving field landed in FIT letterbox or drifted from the displayed photo.",
        "A bright photo and P1-bounded low-alpha local ovals left little perceptual contrast; earlier attempts amplified alpha beyond P1 or added path-derived Receiving Light geometry, so those variants were rejected.",
        "Four similar corner patches and thin path-only traces read as regular decoration; this pass removes the path bloom and preserves the frozen seven path topology while recording the remaining human-review gate.",
        "Frozen 02 and the registered P5 source photo are different images, so same-source Pixel Diff is unavailable."
      ],

      geometry: { transform_authority: "RecognitionImageTransform FIT oracle / renderer photoContentRect", photoContentRect: geometry.photoContentRect, viewports: viewportMatrix, paths: geometry.paths.map(pickGeometry), mapped_path_points: geometry.mapped_path_points, quiet_gaps: geometry.quiet_gaps, path_points_clamped: false, diagnostic_paths_clipped_to_photoContentRect: false, status: "PASS_FOR_GEOMETRY_MAPPING" },
      receiving_field_geometry: { authority: "P1 freezes radial-oval class, alpha/depth bounds and matching path island weights; exact oval placements/stops are from the non-frozen runtime snapshot", layout_source_status: config.optical_lab_runtime_snapshot.status, layout: config.optical_lab_runtime_snapshot.receiving_light_layout, alpha_stops: config.optical_lab_runtime_snapshot.receiving_light_alpha_stops, island_weight_source: "matching P1 primary path island_weight", opacity_cap: receivingOptics.opacityCap, oval_gain: receivingOptics.ovalGain, depth_contract: receivingDepthRange, composite: receivingOptics.composite, field_path_ids: [], photoContentRect: geometry.photoContentRect, geometry_added: false, renderer_signal_parity: { software_cache_to_hardware_gradient_ratio: rendererSignalRatio, result: "RENDERER_COMPOSITING_PARITY_ONLY_NOT_VISUAL_PASS" }, status: "REVIEW_CANDIDATE_LAYOUT_NOT_FROZEN_DESIGN" },
      static_candidates: Object.fromEntries(["A", "B", "C"].map(candidate => [candidate, {
        id: candidateDefinitions[candidate].id,
        name: candidateDefinitions[candidate].name,
        parameters: candidateDefinitions[candidate],
        receiving_base_alpha_before_global_multipliers: receivingContract.contract_alpha_before_multipliers[1],
        receiving_depth_ratio: candidateDefinitions[candidate].receiving_depth_ratio,
        receiving_composite: receivingOptics.composite,
        receiving_nominal_peak_alpha_before_island_state_quality: Math.min(receivingOptics.opacityCap,
          receivingContract.contract_alpha_before_multipliers[1] * candidateDefinitions[candidate].receiving_field_scale),
        receiving_render_signal: candidateSignal[candidate],
        receiving_layer_alpha_integrity: candidateAlphaSummary[candidate],
        same_review_fixture_geometry_and_paths: true,
        screenshots: [`static-${candidate}-desktop-board.png`, `static-${candidate}-narrow-board.png`, `static-${candidate}-390x844.png`],
      }])) ,
      candidate_parameter_json: { path: "static-candidate-parameters.json", sha256: sha256(fs.readFileSync(path.join(OUTPUT_DIR, "static-candidate-parameters.json"))) },
      rendered_png_artifacts: renderedArtifacts,
      artifact_manifest: "static-evidence-artifact-manifest.json",
      layer_images: ["receiving-light-only-A.png", "receiving-light-only-B.png", "receiving-light-only-C.png", "receiving-light-over-photo-A.png", "receiving-light-over-photo-B.png", "receiving-light-over-photo-C.png", "receiving-light-off-photo.png", "receiving-light-over-photo-software-cache.png", "outer-bloom-only.png", "mid-glow-only.png", "core-only.png", "hairline-echoes-only.png", "full-composite-B-390x844.png", "fish-focus-review-proxy-only.png"],
      fish_focus: {
        production: "BLOCKED · no production FishSubjectResult mask is bound to this Web review input",
        review_only: "REVIEW_PROXY_NOT_PRODUCTION · Case B historical BBox + manually annotated alpha",
        fixture_path: bboxEvidence.fixture_path,
        fixture_sha256: bboxEvidence.fixture_sha256,
        bbox_path: reviewBboxPath,
        bbox_normalized: bboxEvidence.bbox_normalized,
        alpha_path: reviewAlphaPath,
        alpha_sha256: alphaSha,
        alpha_provenance: bboxEvidence.alpha_provenance,
        case_b_photo_copy: "fish-focus-review-case-b-photo.jpg",
        current_runtime_log: "NOT_ATTACHED; BBox record is historical and is not a current-head runtime run",
        screenshots: ["fish-focus-blocked-fish-located.png", "fish-focus-review-proxy-fish-located.png", "fish-focus-review-proxy-species-recognizing.png", "fish-focus-review-proxy-only.png"],
      },
      gates: {
        composed_page_build_binding: "PASS",
        public_https_route_verification: "POST_DEPLOY_LIVE_SMOKE_REQUIRED",
        source_fixture_sha_and_exif: "PASS",
        source_photo_identity_to_frozen: "SOURCE_PHOTO_IDENTITY_UNVERIFIED",
        authoritative_transform_and_viewport_matrix: "PASS",
        seven_frozen_paths_and_quiet_gaps: "PASS",
        static_png_rendering_and_layer_exports: "PASS · browser render evidence only",
        automated_visual_quality: "NOT_AUTOMATED",
        human_static_optical_review: "REVIEW_REQUIRED",
        fish_focus_production: "BLOCKED",
        fish_focus_review_proxy: "REVIEW_PROXY_NOT_PRODUCTION",
        dynamic_implementation_or_recording: "NOT_RUN · static-only task",
        android_runtime: "NOT_RUN",
        native_implementation: "NOT_RUN",
        final_user_approval: "REVIEW_REQUIRED",
      },
      browser_errors: errors,
    };
    fs.writeFileSync(path.join(OUTPUT_DIR, "static-closure-v3-report.json"), JSON.stringify(report, null, 2) + "\n");
    fs.writeFileSync(path.join(OUTPUT_DIR, "index.html"), galleryHtml(report));
    if (PUBLIC_DIR) {
      fs.mkdirSync(PUBLIC_DIR, { recursive: true });
      fs.cpSync(OUTPUT_DIR, PUBLIC_DIR, { recursive: true });
    }
    console.log(JSON.stringify({ result: "STATIC_BROWSER_RENDER_EVIDENCE_CREATED_REVIEW_REQUIRED", source_sha: SOURCE_SHA, page_url: reviewUrl, capture_url: reviewCaptureUrl, output_dir: OUTPUT_DIR, public_dir: PUBLIC_DIR, static_candidates: ["A", "B", "C"], viewports: viewportMatrix.map(row => row.viewport), browser_errors: errors }, null, 2));
  } finally {
    await browser.close();
  }
}

async function captureFormalReceivingRenderer(browser, fixture) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on("pageerror", error => errors.push("formal pageerror: " + error.message));
  page.on("console", message => { if (message.type() === "error") errors.push("formal console: " + message.text()); });
  page.on("requestfailed", request => errors.push("formal requestfailed: " + request.url() + " · " + request.failure()?.errorText));
  await page.addInitScript(() => {
    window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__ = [];
    const nativeDrawImage = CanvasRenderingContext2D.prototype.drawImage;
    CanvasRenderingContext2D.prototype.drawImage = function(source, ...args) {
      if (source instanceof HTMLCanvasElement && args.length === 8 && this.canvas.width === 1152 && this.canvas.height === 1536) {
        const [sx, sy, sw, sh, dx, dy, dw, dh] = args;
        const captured = document.createElement("canvas");
        captured.width = dw; captured.height = dh;
        nativeDrawImage.call(captured.getContext("2d"), source, sx, sy, sw, sh, dx, dy, dw, dh);
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.push({
          canvas: captured,
          source_size: [source.width, source.height],
          source_rect: [sx, sy, sw, sh],
          destination_rect: [dx, dy, dw, dh]
        });
      }
      return nativeDrawImage.call(this, source, ...args);
    };
  });
  try {
    await page.goto(new URL(ACCEPTANCE_PATH, SITE_BASE).href, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() =>
      document.querySelector("#sourceStatus")?.dataset.status === "PASS" &&
      window.__EDGEGLOW_LAB__?.photoDimensions?.[0] === 1152 &&
      document.querySelector("#buildSha")?.textContent,
      null,
      { timeout: 30000 }
    );
    const build = await page.evaluate(async () => ({
      build: await fetch("./build-info.json", { cache: "no-store" }).then(response => response.json()),
      photo_sha: document.querySelector("#photoSha")?.textContent.trim(),
      photo_dimensions: window.__EDGEGLOW_LAB__.photoDimensions,
      page_build_stamp: document.querySelector("#buildSha")?.textContent.trim(),
      script_url: [...document.scripts].find(script => script.src.includes("edgeglow.js"))?.src || null,
      script_attribute: [...document.scripts].find(script => script.src.includes("edgeglow.js"))?.getAttribute("src") || null,
      browser_viewport: [innerWidth, innerHeight]
    }));
    assert.equal(build.build.pr_source_sha, SOURCE_SHA, "formal renderer must use the exact PR source SHA");
    assert.equal(build.photo_sha, fixture.photo.sha256, "formal renderer must load the registered source photo");
    assert.deepEqual(build.photo_dimensions, [fixture.photo.width, fixture.photo.height]);
    assert.deepEqual(build.browser_viewport, [390, 844]);
    const loadedScriptText = await page.evaluate(url => fetch(url, { cache: "no-store" }).then(response => response.text()), build.script_url);
    const loadedScriptSha = sha256(Buffer.from(loadedScriptText, "utf8"));
    const sourceScriptPath = path.join(SOURCE_ROOT, "design/manager/nh01_acceptance/edgeglow-fish-static/edgeglow.js");
    assert.equal(loadedScriptSha, sha256(fs.readFileSync(sourceScriptPath)), "formal page must load the committed EdgeGlow renderer byte-for-byte");

    const setControl = async (id, value) => page.locator(`#${id}`).evaluate((node, next) => {
      if (node.type === "checkbox") node.checked = Boolean(next);
      else node.value = String(next);
      node.dispatchEvent(new Event("input", { bubbles: true }));
      node.dispatchEvent(new Event("change", { bubbles: true }));
    }, value);
    for (const [id, value] of Object.entries({
      edgeFieldEnabled: true, receivingLightEnabled: true,
      primaryIslandsEnabled: false, secondaryFragmentsEnabled: false,
      companionHairlinesEnabled: false, outerEnabled: false,
      coreEnabled: false, cometEnabled: false
    })) await setControl(id, value);
    await page.evaluate(() => {
      window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.length = 0;
      window.__EDGEGLOW_LAB__.render();
    });
    await page.waitForFunction(() => window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length > 0, null, { timeout: 15000 });
    const isolated = await page.evaluate(() => {
      const call = window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__[0];
      const data = call.canvas.toDataURL("image/png");
      const rgba = call.canvas.getContext("2d", { willReadFrequently: true }).getImageData(0, 0, 1152, 1536).data;
      let nonzero = 0, translucent = 0, peak = 0;
      for (let i = 3; i < rgba.length; i += 4) {
        const alpha = rgba[i];
        if (alpha > 0) nonzero++;
        if (alpha > 0 && alpha < 255) translucent++;
        if (alpha > peak) peak = alpha;
      }
      return {
        data,
        source_size: call.source_size,
        source_rect: call.source_rect,
        destination_rect: call.destination_rect,
        alpha: { width: 1152, height: 1536, nonzero_pixels: nonzero, translucent_pixels: translucent, peak_alpha_8bit: peak }
      };
    });
    writeDataUrl("formal-receiving-light-only-1152x1536.png", isolated.data);
    const receivingOnlyState = await page.evaluate(() => ({
      renderer_state: window.__EDGEGLOW_LAB__.state,
      path_ids: window.__EDGEGLOW_LAB__.edgeFieldPaths.map(path => path.id),
      render_version: document.documentElement.dataset.renderVersion
    }));
    await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"))
      .then(data => writeDataUrl("formal-receiving-over-photo-1152x1536.png", data));

    await page.reload({ waitUntil: "networkidle" });
    await page.waitForFunction(() =>
      document.querySelector("#sourceStatus")?.dataset.status === "PASS" &&
      window.__EDGEGLOW_LAB__?.photoDimensions?.[0] === 1152,
      null,
      { timeout: 30000 }
    );
    await page.evaluate(() => window.__EDGEGLOW_LAB__.render());
    await page.waitForTimeout(100);
    const fullData = await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    writeDataUrl("formal-static-composite-1152x1536.png", fullData);
    await page.screenshot({ path: path.join(OUTPUT_DIR, "formal-page-static-390x844.png") });

    return {
      page_url: new URL(ACCEPTANCE_PATH, SITE_BASE).href,
      renderer_function: "drawReceivingLight(ctx) -> compositePhotoEdge(bufferContext, inset, source-over)",
      capture_method: "actual detached alpha edgeLayer cloned at its 9-argument drawImage into the photo-sized browser compositor while only Receiving Light was enabled",
      browser_viewport: build.browser_viewport,
      photo_sha256: build.photo_sha,
      photo_dimensions: build.photo_dimensions,
      build: build.build,
      page_build_stamp: build.page_build_stamp,
      loaded_script_url: build.script_url,
      loaded_script_attribute: build.script_attribute,
      loaded_script_source_sha256: loadedScriptSha,
      isolated_layer: isolated.alpha,
      isolated_capture_geometry: { source_size: isolated.source_size, source_rect: isolated.source_rect, destination_rect: isolated.destination_rect },
      receiving_only_state: receivingOnlyState,
      default_static_state: "reloaded page defaults, preset Enhanced, all configured layers enabled",
      rendered_files: [
        "formal-receiving-light-only-1152x1536.png",
        "formal-receiving-over-photo-1152x1536.png",
        "formal-static-composite-1152x1536.png",
        "formal-page-static-390x844.png"
      ],
      browser_errors: errors
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function mainReceivingRecoveryR01() {
  fs.rmSync(OUTPUT_DIR, { recursive: true, force: true });
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  if (PUBLIC_DIR) {
    fs.rmSync(PUBLIC_DIR, { recursive: true, force: true });
    fs.mkdirSync(PUBLIC_DIR, { recursive: true });
  }
  const config = readJson(configPath);
  const fixture = readJson(fixturePath);
  const candidateManifest = readJson("design/manager/optical-lab/review_v1/candidates.json");
  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const errors = [];
  try {
    const formalEvidence = await captureFormalReceivingRenderer(browser, fixture);
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    page.on("pageerror", error => errors.push(error.message));
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("requestfailed", request => errors.push("requestfailed: " + request.url() + " · " + request.failure()?.errorText));
    await page.goto(new URL(REVIEW_PATH, SITE_BASE).href, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() =>
      document.querySelector("#overallStatus")?.textContent === "REVIEW_REQUIRED" &&
      window.OPTICAL_LAB_BUILD_INFO__?.head_sha &&
      document.querySelector("#previewCanvas")?.dataset.photoContentRect,
      null,
      { timeout: 30000 }
    );
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    await page.locator("#stateButtons button").nth(0).click();
    await page.selectOption("#focusSource", "blocked");
    await page.selectOption("#staticCandidate", "B");
    await setTargetViewport(page, 390, 844);
    await page.waitForTimeout(120);

    const buildInfo = await page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__);
    assert.equal(buildInfo.head_sha, SOURCE_SHA, "R01 browser page must be built from this exact source SHA");
    assert.ok(buildInfo.acceptance_center_source_sha, "Acceptance Center source SHA must be present");

    const setOnlyLayers = async enabled => {
      for (const name of ["photo","receiving","outer","mid","core","hairlines","fishHalo","fishContour","status"]) {
        await setLayer(page, name, enabled.includes(name));
      }
    };
    const setFieldFlags = async () => {
      await setReviewLayer(page, "fieldToggle", true);
      await setReviewLayer(page, "pathToggle", false);
      await setReviewLayer(page, "filamentToggle", false);
      await setReviewLayer(page, "fishToggle", false);
    };
    await setFieldFlags();
    await page.selectOption("#fieldLayerView", "receiving");
    await setOnlyLayers(["receiving"]);
    await waitForPaint(page);
    await canvasPng(page, "receiving-light-only-B.png");
    const isolatedAlpha = await canvasAlphaSummary(page);
    assert.equal(isolatedAlpha.opaque_pixels, 0, "isolated Receiving Light export must retain transparency");
    assert.ok(isolatedAlpha.translucent_pixels > 0, "isolated Receiving Light export must contain rendered field pixels");

    await setOnlyLayers(["photo","receiving"]);
    await waitForPaint(page);
    await canvasPng(page, "receiving-light-over-photo-B.png");

    await setFieldFlags();
    await setOnlyLayers(["photo","receiving","outer","mid","core","hairlines","fishHalo","fishContour","status"]);
    await page.selectOption("#fieldLayerView", "composite");
    await waitForPaint(page);
    await canvasPng(page, "static-B-390x844.png");

    const geometry = await page.evaluate(() => window.OPTICAL_LAB_STATIC_GEOMETRY_DIAGNOSTIC());
    assert.deepEqual(geometry.viewport, [390, 844]);
    assert.equal(geometry.receiving_fields.length, 4, "R01 must keep the four existing primary islands");
    const minDim = Math.min(geometry.photoContentRect.right-geometry.photoContentRect.left, geometry.photoContentRect.bottom-geometry.photoContentRect.top);
    assert.ok(geometry.receiving_fields.every(field => field.minor_radius_px <= 0.18*minDim+0.01), "the field depth axis must stay within P1's 8–18% range");
    writeDataUrl("geometry-diagnostic-390x844.png", geometry.png_data_url);

    const optics = await page.evaluate(() => window.OPTICAL_LAB_STATIC_RECEIVING_PARAMETERS__);
    const pageCandidates = await page.evaluate(() => window.OPTICAL_LAB_STATIC_CANDIDATES__);
    assert.equal(optics.composite, "source-over");
    assert.equal(optics.opacityCap, 0.14);
    assert.equal(pageCandidates.B.receiving_field_scale, candidateManifest.static_candidates.B.receiving_field_scale);
    assert.equal(pageCandidates.B.receiving_depth_ratio, candidateManifest.static_candidates.B.receiving_depth_ratio);
    assert.deepEqual(optics.alphaStops, [[0,1],[0.48,0.94],[0.84,0.46],[1,0]]);
    assert.deepEqual(optics.layout.map(item => item.id), ["B_UR","G_UL","G_LL","B_LR"]);

    const names = ["receiving-light-only-B.png","receiving-light-over-photo-B.png","static-B-390x844.png","geometry-diagnostic-390x844.png"];
    const rendered = names.map(file => {
      const bytes = fs.readFileSync(path.join(OUTPUT_DIR,file));
      assert.ok(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])), file + " must be a real browser PNG");
      return {file,bytes:bytes.length,sha256:sha256(bytes)};
    });
    const formalRendered = formalEvidence.rendered_files.map(file => {
      const bytes = fs.readFileSync(path.join(OUTPUT_DIR, file));
      assert.ok(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])), file + " must be a real browser PNG");
      return {file, bytes:bytes.length, sha256:sha256(bytes)};
    });
    const fieldParams = geometry.receiving_fields.map(field => ({
      id:field.id,family:field.family,
      path_id:config.p1_edge_field.paths.find(path=>path.island===field.id)?.id||null,
      center_normalized:field.center_normalized,center_px:field.center_px,
      tangent_radius_min_dimension:field.tangent_radius_min_dimension,
      major_radius_px:field.major_radius_px,minor_radius_px:field.minor_radius_px,
      angle_rad:field.angle_rad,depth_ratio:field.depth_ratio
    }));
    const parameterJson = {
      task_id:"YUJIAN_EDGEFIELD_STATIC_RECOVERY_R01",
      authority:"REVIEW_CANDIDATE_ONLY_NOT_FROZEN",
      source_sha:SOURCE_SHA,
      inherited_baseline_sha:RECOVERY_BASELINE_SHA,
      source_photo_sha256:fixture.photo.sha256,
      source_photo_dimensions:[fixture.photo.width,fixture.photo.height],
      exif_orientation:fixture.photo.exif_orientation,
      viewport:[390,844],
      photoContentRect:geometry.photoContentRect,
      candidate:{
        id:"REVIEW_CANDIDATE_B",name:"Balanced Field",
        receiving_field_scale:pageCandidates.B.receiving_field_scale,
        base_alpha_range:config.p1_edge_field.optical_stack.find(layer=>layer.layer==="local_receiving_light").contract_alpha_before_multipliers,
        nominal_peak_alpha_before_path_weight:0.07*pageCandidates.B.receiving_field_scale,
        depth_ratio:pageCandidates.B.receiving_depth_ratio,
        opacity_cap:optics.opacityCap,composite:optics.composite
      },
      receiving_fields:fieldParams,
      alpha_stops:optics.alphaStops,
      formal_renderer_capture:formalEvidence,
      palette_authority:"Existing P1 blue/gold tokens; unchanged",
      paths_authority:"Existing P1 seven paths and Quiet Gaps; unchanged",
      inherited_geometry_evidence:{
        workflow_run:39453185868,source_sha:RECOVERY_BASELINE_SHA,
        source_photo_sha256:"6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef",
        photo_exif:"PASS_ORIENTATION_1",shared_photoContentRect:"PASS",
        five_viewport_geometry:"PASS_GEOMETRY_ONLY",
        status:"REUSED_PARENT_EVIDENCE_NO_REPEAT"
      }
    };
    const parameterBytes = Buffer.from(JSON.stringify(parameterJson,null,2)+"\n");
    fs.writeFileSync(path.join(OUTPUT_DIR,"receiving-light-recovery-r01-parameters.json"),parameterBytes);
    const report = {
      task_id:"YUJIAN_EDGEFIELD_STATIC_RECOVERY_R01",
      scope:"WEB_STATIC_RECEIVING_LIGHT_ONLY",
      source_sha:SOURCE_SHA,build_sha:buildInfo.head_sha,deployment_source_sha:SOURCE_SHA,
      acceptance_center_source_sha:buildInfo.acceptance_center_source_sha,
      page_url:new URL(REVIEW_PATH,PUBLIC_SITE_BASE).href,
      evidence_url:new URL(REVIEW_PATH+"evidence/static-recovery-r01/",PUBLIC_SITE_BASE).href,
      viewport:[390,844],state:"IMAGE_RECOGNIZING",candidate:"REVIEW_CANDIDATE_B",
      photoContentRect:geometry.photoContentRect,
      browser_render_status:"PASS_TARGETED_BROWSER_RENDER_ONLY",
      visual_status:"REVIEW_REQUIRED · no automated visual PASS",
      inherited_geometry_evidence:parameterJson.inherited_geometry_evidence,
      receiving_layer_alpha_integrity:isolatedAlpha,
      rendered_pngs:rendered,
      formal_renderer:formalEvidence,
      formal_rendered_pngs:formalRendered,
      parameters_file:"receiving-light-recovery-r01-parameters.json",
      parameters_sha256:sha256(parameterBytes),
      constraints:{
        p1_base_alpha:"UNCHANGED",palette:"UNCHANGED",seven_paths:"UNCHANGED",
        composite:"source-over",extra_layers_or_paths:"NONE",android:"NOT_RUN",dynamic:"NOT_RUN"
      },
      browser_errors:errors
    };
    fs.writeFileSync(path.join(OUTPUT_DIR,"receiving-light-recovery-r01-test-report.json"),JSON.stringify(report,null,2)+"\n");
    const figures=names.map(file=>"<figure><a href=\"./"+file+"\"><img src=\"./"+file+"\" alt=\""+file+"\"></a><figcaption>"+file+"</figcaption></figure>").join("");
    const gallery='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Receiving Light 静态恢复 R01</title><style>body{font:15px/1.45 system-ui,sans-serif;background:#101b20;color:#eaf3f5;margin:0 auto;padding:22px;max-width:1180px}a{color:#9edbe8}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}figure{margin:0}img{width:100%;height:auto;background:#162329;border:1px solid #38535c;border-radius:8px}figcaption{font-size:13px;padding:5px 0;color:#b5c9ce}code{overflow-wrap:anywhere}</style><h1>Receiving Light 定向修复 R01</h1><p>REVIEW_REQUIRED · Web 静态候选 · source SHA <code>'+SOURCE_SHA+'</code> · IMAGE_RECOGNIZING · 390×844</p><p><a href="./receiving-light-recovery-r01-parameters.json">参数 JSON</a> · <a href="./receiving-light-recovery-r01-test-report.json">浏览器测试报告</a></p><div class="grid">'+figures+'</div></html>';
    fs.writeFileSync(path.join(OUTPUT_DIR,"index.html"),gallery);
    if(PUBLIC_DIR)fs.cpSync(OUTPUT_DIR,PUBLIC_DIR,{recursive:true});
    assert.equal(fs.readdirSync(OUTPUT_DIR).filter(file=>file.endsWith(".png")).length,8,"R01 mode must output four R01 and four formal-renderer browser PNGs");
    console.log(JSON.stringify({result:"PASS_TARGETED_BROWSER_RENDER_ONLY",visual_status:"REVIEW_REQUIRED",source_sha:SOURCE_SHA,output_dir:OUTPUT_DIR,public_dir:PUBLIC_DIR,r01_pngs:rendered,formal_pngs:formalRendered},null,2));
  } finally {
    await browser.close();
  }
}

function galleryHtml(report) {
  const cand = ["A", "B", "C"].map(candidate => {
    const entry = report.static_candidates[candidate];
    return `<section><h2>${entry.id} · ${entry.name}</h2><p>底场 alpha scale ${entry.parameters.receiving_field_scale}× · 名义峰值 ${entry.receiving_nominal_peak_alpha_before_island_state_quality.toFixed(4)} · 深度 ${entry.receiving_depth_ratio} · 主路径 ${entry.parameters.primary_path_scale}× · 次级光丝 ${entry.parameters.secondary_filament_scale}×</p><div class="grid"><figure><img src="static-${candidate}-desktop-board.png"><figcaption>桌面截图（Frozen 与 P5 fixture 不同源）</figcaption></figure><figure><img src="static-${candidate}-narrow-board.png"><figcaption>窄屏截图（Frozen 与 P5 fixture 不同源）</figcaption></figure><figure><img src="static-${candidate}-390x844.png"><figcaption>实际 Canvas Composite · IMAGE_RECOGNIZING · 390×844</figcaption></figure><figure><img src="receiving-light-only-${candidate}.png"><figcaption>Receiving Light 单层 · ${candidate}</figcaption></figure><figure><img src="receiving-light-over-photo-${candidate}.png"><figcaption>Receiving Light over same photo · ${candidate}</figcaption></figure></div></section>`;
  }).join("\n");
  const predeploy = report.baseline.live_predeploy_capture;
  const liveBaselineHtml = predeploy.status.startsWith("CAPTURED")
    ? `<div class="grid"><figure><img src="baseline-cloud-default.png"><figcaption>部署前正式 HTTPS 页面 · source ${predeploy.page_source_sha}</figcaption></figure><figure><img src="baseline-cloud-receiving-only.png"><figcaption>部署前页面的 Receiving Light 单层</figcaption></figure></div>`
    : `<p>部署前正式 HTTPS 页面捕获状态：${predeploy.status} · ${predeploy.error || "未返回错误详情"}。</p>`;
  const viewports = report.geometry.viewports.map(row => `<figure><img src="viewport-B-${row.viewport[0]}x${row.viewport[1]}.png"><figcaption>${row.viewport.join("×")} · photoContentRect ${JSON.stringify(row.photoContentRect)}</figcaption></figure>`).join("\n");
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>EdgeGlow 静态光场证据 V3</title><style>body{font:15px/1.5 system-ui,sans-serif;background:#101b20;color:#eaf3f5;margin:0 auto;padding:24px;max-width:1440px}a{color:#9edbe8}section{border-top:1px solid #38535c;padding:20px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}figure{margin:0}img{display:block;width:100%;height:auto;border:1px solid #38535c;border-radius:8px;background:#152329}figcaption{font-size:12px;color:#aac0c6;padding:6px 0}.badge{display:inline-block;border:1px solid #8c7550;border-radius:999px;padding:4px 10px;color:#ffdda3}pre{white-space:pre-wrap;background:#17272d;padding:12px;border-radius:8px}</style><h1>EdgeGlow 静态光场 · 浏览器真实渲染证据 V3</h1><p class="badge">REVIEW_REQUIRED · 未替用户批准候选</p><p>页面：<a href="${report.page_url}">Optical Review</a> · <a href="${report.acceptance_url}">正式验收中心</a></p><p>source SHA <code>${report.source_sha}</code> · build SHA <code>${report.build_sha}</code> · deploy source <code>${report.deployment_source_sha}</code>（部署仅由 post-deploy live smoke 验证）· 目标状态 IMAGE_RECOGNIZING · P5 FIT 真实 fixture</p><p>源照片 <code>${report.data_source.sha256}</code> · ${report.data_source.dimensions.join("×")} · EXIF ${report.data_source.exif_orientation} · FIT / NO CROP。Frozen source photo identity: <strong>SOURCE_PHOTO_IDENTITY_UNVERIFIED</strong>。</p><section><h2>修复前真实页面与 G0 基线</h2><p>正式页部署前 source SHA：<code>${report.baseline.live_predeploy_capture.page_source_sha || "NOT_CAPTURED"}</code> · 截图状态：<code>${report.baseline.live_predeploy_capture.status}</code>。</p>${liveBaselineHtml}<p><img src="baseline-pre-change-cloud-default.jpg" style="max-width:720px" alt="修复前浏览器截图"><br>修复前存档截图；历史 34de run <code>${report.baseline.workflow_run_id}</code>，<a href="${report.baseline.workflow_run_url}">原始 Actions artifact</a>。历史图 SHA <code>${report.baseline.original_screenshot_sha256}</code>、Receiving Light PNG SHA <code>${report.baseline.original_receiving_layer_sha256}</code> 记录在来源清单。</p><p>旧像素差只证明渲染信号，不是视觉通过。Frozen 02 与当前 P5 photo 不同源，因此标记 SOURCE_PHOTO_IDENTITY_UNVERIFIED。</p></section><h2>静态候选 A/B/C</h2>${cand}<section><h2>B · 正式视口矩阵 PNG</h2><div class="grid">${viewports}</div></section><section><h2>图层与几何</h2><p><a href="geometry-diagnostic-390x844.png">纯几何诊断（photoContentRect、网格、七路径、控制点、Quiet Gaps）</a> · <a href="static-closure-v3-report.json">静态闭环 JSON</a> · <a href="static-candidate-parameters.json">A/B/C 参数 JSON</a> · <a href="static-evidence-artifact-manifest.json">PNG SHA-256 清单</a> · <a href="fish-focus-case-b-bbox.json">Case B BBox JSON</a> · <a href="fish-focus-review-only-alpha.png">Review-only alpha PNG</a></p><div class="grid">${report.layer_images.map(file => `<figure><img src="${file}"><figcaption>${file}</figcaption></figure>`).join("")}</div></section><section><h2>Fish Focus 输入来源</h2><p>Production: <strong>BLOCKED</strong>。Case B 使用历史 BBox 与人工标注 alpha，必须标记 <code>REVIEW_PROXY_NOT_PRODUCTION</code>。<a href="fish-focus-case-b-bbox.json">BBox JSON</a> · <a href="fish-focus-review-only-alpha.png">review alpha PNG</a> · <a href="fish-focus-review-case-b-photo.jpg">Case B fixture photo</a> · <a href="fish-focus-review-proxy-only.png">暖金 Fish Focus 单层 PNG</a>。</p><div class="grid">${report.fish_focus.screenshots.map(file => `<figure><img src="${file}"><figcaption>${file}</figcaption></figure>`).join("")}</div></section><section><h2>候选参数</h2><pre>${JSON.stringify(report.static_candidates,null,2)}</pre></section><p>这些 PNG 是 Chromium Canvas 页面构建的实际输出；自动化验证资源、坐标、图层开关和导出完整性，不替代视觉判断。视觉结论为 REVIEW_REQUIRED；动态实现/录制和 Android Runtime 本轮 NOT_RUN。</p></html>`;
}

(process.argv.includes("--receiving-recovery-r01") ? mainReceivingRecoveryR01 : main)().catch(error => { console.error(error); process.exitCode = 1; });
