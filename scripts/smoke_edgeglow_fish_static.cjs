#!/usr/bin/env node
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_DIR
  ? path.join(process.env.PLAYWRIGHT_MODULE_DIR, "playwright")
  : "playwright");

function arg(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const rootUrl = (arg("--base-url", process.env.EDGEGLOW_BASE_URL || "http://127.0.0.1:8765/")).replace(/\/?$/, "/");
const outputDir = path.resolve(arg("--output-dir", process.env.EDGEGLOW_OUTPUT_DIR || "edgeglow-artifacts"));
const runId = process.env.GITHUB_RUN_ID || "LOCAL";
const expectedSourceSha = process.env.PR_SOURCE_SHA || "LOCAL_UNPINNED";
const centerPath = "design/manager/nh01_acceptance/edgeglow-fish-static/";
const reviewPath = `${centerPath}optical-review/`;
const expectedPhoto = {
  sha256: "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef",
  width: 1152,
  height: 1536,
};

async function nextPaint(page) {
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

async function selectViewport(page, width, height) {
  await page.locator("#viewportSelect").evaluate((select, value) => {
    if (![...select.options].some(option => option.value === value)) select.add(new Option(value, value));
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  }, `${width}x${height}`);
  await page.waitForFunction(([w, h]) => {
    const canvas = document.querySelector("#previewCanvas");
    return canvas?.width === w && canvas?.height === h;
  }, [width, height]);
  await nextPaint(page);
}

async function canvasPixels(page) {
  return page.locator("#previewCanvas").evaluate(canvas => {
    const image = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    let hash = 2166136261;
    for (let i = 0; i < image.length; i += 16) {
      hash ^= image[i]; hash = Math.imul(hash, 16777619);
      hash ^= image[i + 1]; hash = Math.imul(hash, 16777619);
      hash ^= image[i + 2]; hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(16);
  });
}

async function saveCanvas(page, filename) {
  const dataUrl = await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
  const match = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(dataUrl);
  assert.ok(match, `browser canvas did not export a PNG for ${filename}`);
  const bytes = Buffer.from(match[1], "base64");
  assert.equal(bytes.subarray(0, 8).toString("hex"), "89504e470d0a1a0a", `${filename} has a valid PNG signature`);
  await fs.writeFile(path.join(outputDir, filename), bytes);
  return { file: filename, bytes: bytes.length };
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const errors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });
    page.on("pageerror", error => errors.push(`pageerror: ${error.message}`));
    page.on("console", message => { if (message.type() === "error") errors.push(`console: ${message.text()}`); });
    page.on("requestfailed", request => errors.push(`requestfailed: ${request.url()} · ${request.failure()?.errorText}`));

    await page.goto(new URL(centerPath, rootUrl).href, { waitUntil: "networkidle", timeout: 30000 });
    const entry = page.locator("#opticalReviewEntry");
    assert.equal(await entry.count(), 1, "the Design Manager acceptance center exposes its Optical Review link");
    assert.equal(await entry.evaluate(anchor => anchor.tagName.toLowerCase()), "a", "the Optical Review entry remains a direct link");
    assert.ok(await entry.evaluate(anchor => new URL(anchor.getAttribute("href"), location.href).pathname.endsWith("/edgeglow-fish-static/optical-review/")), "the canonical entry points into this project’s Optical Review route");
    await page.screenshot({ path: path.join(outputDir, "acceptance-center-entry.png"), fullPage: true });
    await entry.click();
    await page.waitForURL(url => url.pathname.endsWith("/edgeglow-fish-static/optical-review/"), { timeout: 15000 });
    await page.waitForFunction(() => window.OPTICAL_LAB_BUILD_INFO__?.head_sha && document.querySelector("#previewCanvas")?.dataset.photoContentRect, null, { timeout: 30000 });

    const build = await page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__);
    if (expectedSourceSha !== "LOCAL_UNPINNED") assert.equal(build.head_sha, expectedSourceSha, "the static review page reports the source SHA being tested");
    assert.equal(await page.locator("#overallStatus").innerText(), "REVIEW_REQUIRED", "the UI does not turn browser smoke into visual approval");
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    assert.equal(await page.locator("#previewCanvas").getAttribute("data-preview-mode"), "static", "all captured frames use static mode");

    const assetStatuses = await page.locator("#assetRows tr .asset-status").allTextContents();
    assert.equal(assetStatuses.length, 7, "the Optical Review verifies its seven declared source assets");
    assert.ok(assetStatuses.every(status => status === "VERIFIED"), `all declared review assets load: ${assetStatuses.join(", ")}`);
    assert.match(await page.locator("#photoName").textContent(), /SHA-256 verified/, "the registered photo is hash-verified by the page");

    const stateButtons = page.locator("#stateButtons button");
    assert.equal(await stateButtons.count(), 3, "exactly three Frozen product states remain selectable");
    const expectedStates = ["IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING"];
    const observedStates = [];
    for (let index = 0; index < expectedStates.length; index++) {
      await stateButtons.nth(index).click();
      observedStates.push(await page.locator("#stateLabel").innerText());
    }
    assert.deepEqual(observedStates, expectedStates, "the three state controls select the exact Frozen state IDs");
    await stateButtons.first().click();
    assert.equal(await page.locator('#focusSource option[value="production"]').isDisabled(), true, "Production Focus remains unavailable without production evidence");
    assert.match(await page.locator("#focusProvenance").innerText(), /Production: BLOCKED/, "the page labels missing Production Fish Focus as BLOCKED");

    const candidateOptions = await page.locator("#staticCandidate option").evaluateAll(options => options.map(option => option.value));
    assert.deepEqual(candidateOptions, ["A", "B", "C"], "the three static review candidates are available");
    const viewportMatrix = [];
    const captures = [];
    const setReviewLayer = async (selector, checked) => {
      await page.locator(selector).setChecked(checked);
      await nextPaint(page);
    };

    for (const [width, height] of [[390, 844], [320, 640], [412, 915], [800, 360], [1152, 1536]]) {
      await selectViewport(page, width, height);
      const geometry = await page.locator("#previewCanvas").evaluate(canvas => ({
        width: canvas.width,
        height: canvas.height,
        rect: JSON.parse(canvas.dataset.photoContentRect || "null"),
        transform: JSON.parse(canvas.dataset.photoTransform || "null"),
      }));
      assert.deepEqual([geometry.width, geometry.height], [width, height]);
      assert.ok(geometry.rect && geometry.transform, `shared photoContentRect and photo transform exist at ${width}×${height}`);
      assert.equal(geometry.transform.sourceWidth, expectedPhoto.width);
      assert.equal(geometry.transform.sourceHeight, expectedPhoto.height);
      const scale = Math.min(width / expectedPhoto.width, height / expectedPhoto.height);
      const drawnWidth = expectedPhoto.width * scale;
      const drawnHeight = expectedPhoto.height * scale;
      const expectedRect = {
        left: Math.max(0, (width - drawnWidth) / 2),
        top: Math.max(0, (height - drawnHeight) / 2),
        right: Math.min(width, (width + drawnWidth) / 2),
        bottom: Math.min(height, (height + drawnHeight) / 2),
      };
      for (const key of Object.keys(expectedRect)) assert.ok(Math.abs(geometry.rect[key] - expectedRect[key]) < 0.01, `${width}×${height} photoContentRect ${key} matches FIT`);
      viewportMatrix.push({ viewport: [width, height], photoContentRect: geometry.rect, transform: geometry.transform, result: "PASS_GEOMETRY_ONLY" });
      if (width === 390 && height === 844) {
        await page.locator("#staticCandidate").selectOption("B");
        await page.locator("#fieldLayerView").selectOption("composite");
        captures.push(await saveCanvas(page, "static-B-390x844.png"));
      }
    }

    await selectViewport(page, 390, 844);
    await page.locator("#stateButtons button").nth(0).click();
    await page.locator("#fieldLayerView").selectOption("receiving");
    for (const selector of ["#pathToggle", "#filamentToggle", "#fishToggle"]) await setReviewLayer(selector, false);
    await setReviewLayer("#fieldToggle", false);
    const fieldOff = await canvasPixels(page);
    await saveCanvas(page, "receiving-light-off.png");
    await setReviewLayer("#fieldToggle", true);
    const fieldOn = await canvasPixels(page);
    assert.notEqual(fieldOn, fieldOff, "the Receiving Light layer control changes the rendered static canvas; this is render-signal evidence only");
    captures.push(await saveCanvas(page, "receiving-light-only-390x844.png"));
    await page.screenshot({ path: path.join(outputDir, "receiving-light-only-page.png"), fullPage: true });

    const candidateRenderSignals = {};
    for (const candidate of candidateOptions) {
      await page.locator("#staticCandidate").selectOption(candidate);
      await nextPaint(page);
      candidateRenderSignals[candidate] = await canvasPixels(page);
      if (candidate !== "B") captures.push(await saveCanvas(page, `static-${candidate}-390x844.png`));
    }
    assert.equal(new Set(Object.values(candidateRenderSignals)).size, 3, "A/B/C controls produce distinct static render signals; this does not rank visual quality");

    await page.locator("#fieldLayerView").selectOption("composite");
    await page.locator("#fieldToggle").check();
    await page.locator("#pathToggle").check();
    await page.locator("#filamentToggle").check();
    await page.locator("#stateButtons button").nth(1).click();
    await page.locator("#focusSource").selectOption("review");
    await page.waitForFunction(() => document.querySelector("#focusReadiness")?.textContent.includes("REVIEW_PROXY_NOT_PRODUCTION"), null, { timeout: 30000 });
    assert.match(await page.locator("#focusReadiness").innerText(), /REVIEW_PROXY_NOT_PRODUCTION/);
    await nextPaint(page);
    captures.push(await saveCanvas(page, "fish-focus-review-proxy-fish-located.png"));
    await page.locator("#stateButtons button").nth(2).click();
    await nextPaint(page);
    captures.push(await saveCanvas(page, "fish-focus-review-proxy-species-recognizing.png"));

    assert.deepEqual(errors, [], "the static Acceptance Center and Optical Review load without browser errors");
    const report = {
      task_id: "YUJIAN_EDGEGLOW_WEB_STATIC_VISUAL_CLOSURE_V3",
      run_id: runId,
      source_sha: build.head_sha,
      acceptance_center_source_sha: build.acceptance_center_source_sha,
      routes: { acceptance_center: new URL(centerPath, rootUrl).href, optical_review: page.url() },
      result: "PASS_STATIC_ENGINEERING_SMOKE_ONLY",
      visual_quality: "REVIEW_REQUIRED · no automated visual PASS",
      rendered_candidate_signals: candidateRenderSignals,
      receiving_field_toggle_changes_pixels: true,
      geometry_viewports: viewportMatrix,
      captures,
      source_photo: expectedPhoto,
      source_photo_identity_to_frozen: "SOURCE_PHOTO_IDENTITY_UNVERIFIED",
      fish_focus_production: "BLOCKED",
      fish_focus_review: "REVIEW_PROXY_NOT_PRODUCTION",
      dynamic_implementation_or_recording: "NOT_RUN · V3 static-only scope",
      android_runtime: "NOT_RUN",
      native_implementation: "NOT_RUN",
      browser_errors: errors,
    };
    await fs.writeFile(path.join(outputDir, "EdgeGlow_Fish_Static_Test_Report.json"), `${JSON.stringify(report, null, 2)}\n`);
    console.log(JSON.stringify(report, null, 2));
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
