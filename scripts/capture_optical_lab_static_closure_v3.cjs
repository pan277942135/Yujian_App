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
    await page.goto(reviewCaptureUrl, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() => {
      const canvas = document.querySelector("#previewCanvas");
      return document.querySelector("#overallStatus")?.textContent === "REVIEW_REQUIRED" &&
        document.querySelector("#photoName")?.textContent.includes("SHA-256 verified") &&
        canvas?.dataset.photoContentRect && window.OPTICAL_LAB_BUILD_INFO__?.head_sha;
    }, null, { timeout: 30000 });
    const buildInfo = await page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__);
    assert.equal(buildInfo.head_sha, SOURCE_SHA, "page build SHA must match the PR source SHA");
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

    const candidateDefinitions = readJson("design/manager/optical-lab/review_v1/candidates.json").static_candidates;
    const candidateSignal = {};
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
    }
    assert.ok(candidateSignal.A.mean_abs_rgb_delta < candidateSignal.B.mean_abs_rgb_delta && candidateSignal.B.mean_abs_rgb_delta < candidateSignal.C.mean_abs_rgb_delta,
      "static A/B/C Receiving Light scales must produce increasing render signal");

    await page.selectOption("#staticCandidate", "B");
    await page.setViewportSize({ width: 1440, height: 1100 });
    await setTargetViewport(page, 390, 844);
    await page.locator("#advancedDiagnostics").evaluate(node => { node.open = true; });
    await page.locator("#fieldToggle").check();
    await page.locator("#pathToggle").check();
    await page.locator("#filamentToggle").check();
    await page.locator("#fishToggle").check();
    const allLayerNames = ["photo", "receiving", "outer", "mid", "core", "hairlines", "fishHalo", "fishContour", "status"];
    const setOnlyLayers = async enabled => {
      for (const name of allLayerNames) await setLayer(page, name, enabled.includes(name));
    };
    await setLayer(page, "photo", false);
    await setLayer(page, "status", false);
    await setLayer(page, "receiving", true);
    await setLayer(page, "outer", false); await setLayer(page, "mid", false); await setLayer(page, "core", false); await setLayer(page, "hairlines", false);
    await setReviewLayer(page, "fieldToggle", true); await setReviewLayer(page, "pathToggle", false); await setReviewLayer(page, "filamentToggle", false); await setReviewLayer(page, "fishToggle", false);
    await page.selectOption("#fieldLayerView", "receiving"); await waitForPaint(page); await canvasPng(page, "receiving-light-only.png");
    // Keep the same P5 source photo visible while every other overlay stays off.
    // This checks whether the Receiving Light reads as light on the real photo,
    // not only as colored blobs against the scene background.
    await setLayer(page, "photo", true); await waitForPaint(page); await canvasPng(page, "receiving-light-over-photo.png");
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
      receiving_field_geometry_authority: "P1 receiving_light_layout and receiving_light_alpha_stops",
      frozen_path_authority: "P1 exact seven paths and one shared photoContentRect",
      receiving_field_opacity_cap: 0.32,
      receiving_field_composite: "screen · Web Review static only",
      receiving_field_depth_ratio: 0.13,
      contract_alpha_before_multipliers: config.p1_edge_field.optical_stack.find(layer => layer.layer === "local_receiving_light").contract_alpha_before_multipliers,
      candidate_differences: "Web-only Receiving Light gain plus primary path and secondary filament gains; the corner ovals use 0.36 review gain and broad field uses exact P1 primary runtime-seed/visible-ratio segments with a shared screen composite; contract and Native values unchanged",
      receiving_oval_review_gain: 0.36,
      path_bloom_passes: [{ width_ratio: 0.13, blur_ratio: 0.039, opacity: 0.78 }, { width_ratio: 0.0624, blur_ratio: 0.0169, opacity: 0.54 }],
      path_bloom_uses_frozen_visible_segments: true,
      candidates: Object.fromEntries(["A", "B", "C"].map(candidate => [candidate, {
        id: candidateDefinitions[candidate].id,
        name: candidateDefinitions[candidate].name,
        receiving_field_scale: candidateDefinitions[candidate].receiving_field_scale,
        primary_path_scale: candidateDefinitions[candidate].primary_path_scale,
        secondary_filament_scale: candidateDefinitions[candidate].secondary_filament_scale,
      }])),
      visual_status: "REVIEW_REQUIRED",
    };
    const baselineSource = readJson(`${baselineEvidencePath}/source.json`);
    const baselineFiles = ["receiving-field-signal.json", "source.json"];
    for (const file of baselineFiles) fs.copyFileSync(path.join(SOURCE_ROOT, baselineEvidencePath, file), path.join(OUTPUT_DIR, `baseline-34de-${file}`));
    fs.copyFileSync(path.join(SOURCE_ROOT, reviewBboxPath), path.join(OUTPUT_DIR, "fish-focus-case-b-bbox.json"));
    fs.copyFileSync(path.join(SOURCE_ROOT, reviewAlphaPath), path.join(OUTPUT_DIR, "fish-focus-review-only-alpha.png"));
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
      deployment_source_sha: buildInfo.head_sha,
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
        assessment: "The baseline field reads as broad patches and bleeds into FIT letterbox; prior pixel signal proves render changes only, not optical quality.",
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
      root_causes: [
        "The pre-V3 static helper used a second assumed 3:4 coordinate space, was not clipped to the renderer photoContentRect, and produced broad patches instead of edge-following light.",
        "The first V3 fix removed the helper but used four low-alpha radial ovals. The actual Browser Receiving Light-only capture stayed below visibility thresholds in three of four corner samples; its pastel P1 tokens at the 0.07 contract alpha were largely swallowed by the bright fixture.",
        "The second V3 fix turned the Receiving Light into blurred primary-path strokes. That preserved the path curves but made the bottom field too narrow and faint; the real browser PNG still read as a few fine traces.",
        "The third V3 fix restored the exact P1 receiving_light_layout ovals and alpha stops, but the 0.34 local opacity ceiling still left the corner field too faint on the real photograph.",
        "The fourth V3 pass kept the P1 ovals, alpha stops, colors, island weights and shared photoContentRect, but its 0.48 Web-only opacity cap still read as faint traces on the real photo. This pass raises only the Web-review gain/cap and adds a same-photo Receiving-Light-only capture; P1 and Native values remain unchanged.",
        "The 0.84-cap source-over capture adds only a small average luminance change across broad corner sample windows on the bright P5 fixture; most pixels in those windows receive no field at all. This pass tests screen blending for the static Web Receiving Light so local incident light can lift midtones without changing P1 colors, stops or geometry. The result remains REVIEW_REQUIRED until human inspection of the real PNGs.",
        "The actual screen-composite Chromium capture at 64880 still read as four corner spots rather than a photo-edge field; candidate B changed the broad corner samples by a mean absolute RGB delta of 9.38, but the real page comparison remained too weak. This pass retains the P1 ovals at a bounded cap and adds only broad, blurred energy sampled from the exact four P1 primary cubic paths, with a 0.13 depth ratio inside P1's 0.08–0.18 range. It adds no path or perimeter and leaves P1/Native values unchanged.",
        "The prior smoke measured pixel changes and blue/gold deltas but did not test whether the edge field was visually clear, localized, or natural.",
        "Frozen 02 and the registered P5 source photo are different images, so strict same-source Frozen pixel diff is unavailable.",
      ],
      geometry: { transform_authority: "RecognitionImageTransform FIT oracle / renderer photoContentRect", photoContentRect: geometry.photoContentRect, viewports: viewportMatrix, paths: geometry.paths.map(pickGeometry), mapped_path_points: geometry.mapped_path_points, quiet_gaps: geometry.quiet_gaps, path_points_clamped: false, diagnostic_paths_clipped_to_photoContentRect: false, status: "PASS_FOR_GEOMETRY_MAPPING" },
      receiving_field_geometry: { authority: "P1 receiving_light_layout, alpha stops, primary paths and contract_edge_depth_ratio", layout: config.optical_lab_runtime_snapshot.receiving_light_layout, alpha_stops: config.optical_lab_runtime_snapshot.receiving_light_alpha_stops, opacity_cap: 0.32, depth_ratio: 0.13, composite: "screen · static Web Review only", field_path_ids: ["B1", "G1", "B3", "G3"], photoContentRect: geometry.photoContentRect, geometry_added: false, status: "P1_GEOMETRY_MAPPED_TO_SHARED_RECT" },
      static_candidates: Object.fromEntries(["A", "B", "C"].map(candidate => [candidate, {
        id: candidateDefinitions[candidate].id,
        name: candidateDefinitions[candidate].name,
        parameters: candidateDefinitions[candidate],
        receiving_base_alpha_before_global_multipliers: config.p1_edge_field.optical_stack.find(layer => layer.layer === "local_receiving_light").contract_alpha_before_multipliers[1],
        receiving_render_signal: candidateSignal[candidate],
        same_source_geometry_and_paths: true,
        screenshots: [`static-${candidate}-desktop-board.png`, `static-${candidate}-narrow-board.png`, `static-${candidate}-390x844.png`],
      }])) ,
      candidate_parameter_json: { path: "static-candidate-parameters.json", sha256: sha256(fs.readFileSync(path.join(OUTPUT_DIR, "static-candidate-parameters.json"))) },
      rendered_png_artifacts: renderedArtifacts,
      artifact_manifest: "static-evidence-artifact-manifest.json",
      layer_images: ["receiving-light-only.png", "receiving-light-over-photo.png", "outer-bloom-only.png", "mid-glow-only.png", "core-only.png", "hairline-echoes-only.png", "full-composite-B-390x844.png"],
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
        current_runtime_log: "NOT_ATTACHED; BBox record is historical and is not a current-head runtime run",
        screenshots: ["fish-focus-blocked-fish-located.png", "fish-focus-review-proxy-fish-located.png", "fish-focus-review-proxy-species-recognizing.png"],
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

function galleryHtml(report) {
  const cand = ["A", "B", "C"].map(candidate => {
    const entry = report.static_candidates[candidate];
    return `<section><h2>${entry.id} · ${entry.name}</h2><p>底场 ${entry.parameters.receiving_field_scale}× · 主路径 ${entry.parameters.primary_path_scale}× · 次级光丝 ${entry.parameters.secondary_filament_scale}×</p><div class="grid"><figure><img src="static-${candidate}-desktop-board.png"><figcaption>桌面页面截图（左侧 Frozen 与右侧 P5 fixture 并非同源）</figcaption></figure><figure><img src="static-${candidate}-narrow-board.png"><figcaption>窄屏页面截图（左侧 Frozen 与右侧 P5 fixture 并非同源）</figcaption></figure><figure><img src="static-${candidate}-390x844.png"><figcaption>实际渲染 Canvas PNG · IMAGE_RECOGNIZING · 390×844</figcaption></figure></div></section>`;
  }).join("\n");
  const viewports = report.geometry.viewports.map(row => `<figure><img src="viewport-B-${row.viewport[0]}x${row.viewport[1]}.png"><figcaption>${row.viewport.join("×")} · photoContentRect ${JSON.stringify(row.photoContentRect)}</figcaption></figure>`).join("\n");
  return `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>EdgeGlow 静态光场证据 V3</title><style>body{font:15px/1.5 system-ui,sans-serif;background:#101b20;color:#eaf3f5;margin:0 auto;padding:24px;max-width:1440px}a{color:#9edbe8}section{border-top:1px solid #38535c;padding:20px 0}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}figure{margin:0}img{display:block;width:100%;height:auto;border:1px solid #38535c;border-radius:8px;background:#152329}figcaption{font-size:12px;color:#aac0c6;padding:6px 0}.badge{display:inline-block;border:1px solid #8c7550;border-radius:999px;padding:4px 10px;color:#ffdda3}pre{white-space:pre-wrap;background:#17272d;padding:12px;border-radius:8px}</style><h1>EdgeGlow 静态光场 · 浏览器真实渲染证据 V3</h1><p class="badge">REVIEW_REQUIRED · 未替用户批准候选</p><p>页面：<a href="${report.page_url}">Optical Review</a> · <a href="${report.acceptance_url}">正式验收中心</a></p><p>source SHA <code>${report.source_sha}</code> · build SHA <code>${report.build_sha}</code> · 目标状态 IMAGE_RECOGNIZING · P5 FIT 真实 fixture</p><p>源照片 <code>${report.data_source.sha256}</code> · ${report.data_source.dimensions.join("×")} · EXIF ${report.data_source.exif_orientation} · FIT / NO CROP。Frozen source photo identity: <strong>SOURCE_PHOTO_IDENTITY_UNVERIFIED</strong>。</p><section><h2>修复前真实页面 · HEAD 34de</h2><p>GitHub Actions run <code>${report.baseline.workflow_run_id}</code>，artifact <code>${report.baseline.artifact_id}</code>。<a href="${report.baseline.workflow_run_url}">打开原 browser artifact</a>，其中含完整页面截图与 Receiving Field-only PNG。两份图的原始 SHA-256 记录在 <a href="baseline-34de-source.json">来源清单</a>。原图与当前 P5 fixture 同一 SHA；Frozen 图身份仍未确认。</p><p><a href="${report.baseline.pixel_signal}">原像素差 JSON</a>（仅渲染信号，不是视觉通过）</p><p>首轮径向候选可见度门失败：<a href="${report.first_v3_failed_render.artifact_url}">打开首轮 Chromium 证据</a>。第二轮沿主路径绘制仍偏弱，B 的角区平均绝对 RGB 差为 ${report.second_v3_failed_render.candidate_b_mean_abs_rgb_delta_in_sampled_photo_corners.toFixed(2)}：<a href="${report.second_v3_failed_render.artifact_url}">打开第二轮真实截图</a>。第三轮恢复 P1 椭圆后 B 的角区平均绝对 RGB 差仍只有 ${report.third_v3_failed_render.candidate_b_mean_abs_rgb_delta_in_sampled_photo_corners.toFixed(2)}，继续判为偏弱：<a href="${report.third_v3_failed_render.artifact_url}">打开第三轮真实截图</a>。三轮都不冒充视觉通过。</p></section><h2>静态候选 A/B/C</h2>${cand}<section><h2>B · 正式视口矩阵 PNG</h2><div class="grid">${viewports}</div></section><section><h2>图层与几何</h2><p><a href="geometry-diagnostic-390x844.png">纯几何诊断（photoContentRect、网格、七路径、控制点、Quiet Gaps）</a> · <a href="static-closure-v3-report.json">静态闭环 JSON</a> · <a href="static-candidate-parameters.json">A/B/C 参数 JSON</a> · <a href="static-evidence-artifact-manifest.json">PNG SHA-256 清单</a> · <a href="fish-focus-case-b-bbox.json">Case B BBox JSON</a> · <a href="fish-focus-review-only-alpha.png">Review-only alpha PNG</a></p><div class="grid">${report.layer_images.map(file => `<figure><img src="${file}"><figcaption>${file}</figcaption></figure>`).join("")}</div></section><section><h2>Fish Focus 输入来源</h2><p>Production: <strong>BLOCKED</strong>。仅 Review-only Case B 手工 alpha 可见，状态必须保持 <code>REVIEW_PROXY_NOT_PRODUCTION</code>。报告包含历史 BBox 值、fixture SHA、alpha SHA 与缺失的当前 runtime log 说明。</p><div class="grid">${report.fish_focus.screenshots.map(file => `<figure><img src="${file}"><figcaption>${file}</figcaption></figure>`).join("")}</div></section><section><h2>候选参数</h2><pre>${JSON.stringify(report.static_candidates,null,2)}</pre></section><p>这些 PNG 是 Chromium Canvas 页面构建的实际输出；自动化只验证资源、坐标、候选差异和浏览器渲染信号。视觉审美结论仍为 REVIEW_REQUIRED。动态和 Android Runtime 本轮 NOT_RUN。</p></html>`;
}

main().catch(error => { console.error(error); process.exitCode = 1; });
