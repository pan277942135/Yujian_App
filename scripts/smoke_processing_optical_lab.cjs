const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "playwright"));

const ROOT_URL = process.env.LAB_REPO_URL || "http://127.0.0.1:8765/design/manager/optical-lab/";
const DESIGN_URL = process.env.LAB_DESIGN_ROOT_URL || "http://127.0.0.1:8766/manager/optical-lab/";
const REQUIRED_STATES = ["IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING"];
const RESPONSIVE = [[390, 844], [320, 640], [360, 800], [412, 915], [800, 360]];
let browser;

(async () => {
  const out = process.env.LAB_OUTPUT_DIR || "optical-lab-artifacts";
  fs.mkdirSync(out, { recursive: true });
  const staticEvidence = path.join(out, "static-candidates");
  const dynamicEvidence = path.join(out, "dynamic-candidates");
  fs.mkdirSync(staticEvidence, { recursive: true });
  fs.mkdirSync(dynamicEvidence, { recursive: true });
  browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const allErrors = [];

  async function openReady(url, label) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1200 }, deviceScaleFactor: 1 });
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("requestfailed", request => errors.push("requestfailed: " + request.url() + " · " + request.failure()?.errorText));
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    try {
      await page.waitForFunction(() => ["REVIEW_REQUIRED", "BLOCKED"].includes(document.querySelector("#overallStatus")?.textContent), null, { timeout: 30000 });
    } catch (error) {
      let state = {};
      try {
        state = await page.evaluate(() => ({
          status: document.querySelector("#overallStatus")?.textContent,
          inputStatus: document.querySelector("#inputStatus")?.textContent,
          runtime: document.querySelector("#runtimeStatus")?.textContent,
          assetErrors: document.querySelector("#assetErrors")?.textContent,
          assetsLoaded: document.querySelectorAll("#assetRows tr").length,
          scriptBuildInfo: window.OPTICAL_LAB_BUILD_INFO__?.head_sha || "MISSING",
        }));
      } catch (diagnosticError) {
        state = { diagnostic_error: diagnosticError.message };
      }
      throw new Error(label + ": loader failed to reach a terminal status · " + JSON.stringify({ state, errors }) + " · " + error.message);
    }
    assert.equal(await page.locator("#overallStatus").textContent(), "REVIEW_REQUIRED", label + ": asset loader reached a blocked state");
    await page.waitForFunction(() => {
      const image = document.querySelector("#frozenImage");
      return image && image.complete && image.naturalWidth === 941 && image.naturalHeight === 1672 &&
        document.querySelector("#photoName")?.textContent.includes("SHA-256 verified") &&
        document.querySelector("#inputStatus")?.textContent.includes("PASS");
    }, null, { timeout: 30000 });
    const audit = await page.evaluate(() => ({
      assets: Array.from(document.querySelectorAll("#assetRows tr")).map(row => ({
        text: row.innerText,
        status: row.querySelector(".asset-status")?.textContent,
      })),
      errors: document.querySelector("#assetErrors")?.textContent,
      revision: document.querySelector("#buildRevision")?.textContent,
      mode: document.querySelector("#sourceMode")?.textContent,
      sourceRevision: window.OPTICAL_LAB_BUILD_INFO__?.head_sha || "NOT_EMBEDDED",
    }));
    assert.equal(audit.assets.length, 7, label + ": expected config + manifest + photo registry + 3 Frozen + test fixture");
    assert.ok(audit.assets.every(asset => asset.status === "VERIFIED"), label + ": every loaded asset must pass its check");
    assert.equal(audit.errors, "", label + ": no unhandled resource errors");
    assert.match(audit.mode, /未绑定|unverified/i, label + ": Frozen source-photo relationship must stay explicitly unverified");
    assert.match(audit.revision, /PR #166 Draft/);
    assert.match(audit.sourceRevision, /^[a-f0-9]{40}$/i, label + ": browser package must expose the exact CI source commit");
    assert.equal(await page.locator("#advancedDiagnostics").evaluate(node => node.open), false, label + ": advanced diagnostics default collapsed");
    assert.equal(await page.locator("#stateButtons button").count(), 3, label + ": exactly three product states");
    assert.equal(await page.locator("#previewModeButtons button").count(), 2, label + ": static and dynamic review modes available");
    assert.equal(await page.locator("#playPause").isVisible(), true, label + ": playback control is present");
    assert.equal(await page.locator("#recordSequence").isVisible(), true, label + ": WebM recorder is present");
    const dynamicEnabled = await page.evaluate(() => window.OPTICAL_DYNAMIC_REVIEW_ENABLED === true);
    assert.equal(await page.locator("#playPause").isDisabled(), !dynamicEnabled, label + ": dynamic playback follows the static visual gate");
    assert.equal(await page.locator("#recordSequence").isDisabled(), !dynamicEnabled, label + ": recorder follows the static visual gate");
    assert.deepEqual(await page.locator("#motionSpeed option").evaluateAll(nodes => nodes.map(node => node.value)), ["0.5", "1", "1.5"], label + ": all required motion speeds available");
    assert.equal(await page.locator("#compareModes button").count(), 3, label + ": main compare toolbar has only side/overlay/wipe");
    assert.equal(await page.locator("#advancedModes button").count(), 2, label + ": candidate and diff modes stay in advanced diagnostics");
    assert.equal(await page.locator("#motionTimeline").isVisible(), false, label + ": legacy contract timeline remains separate from the new review preview");
    assert.equal(await page.locator("#paramMotionSpeed").isVisible(), false, label + ": motion controls remain hidden during Stage A");
    assert.equal(await page.locator("#stateMap tr").count(), 3, label + ": state map preserves three visuals");
    return { page, errors, audit };
  }

  const repo = await openReady(ROOT_URL, "repository-root HTTP preview");
  const page = repo.page;
  const audit = repo.audit;
  await page.setViewportSize({ width: 1440, height: 1100 });
  const referenceStates = [];
  const frozen = [];

  await page.locator("#stateButtons button").nth(1).click();
  assert.match(await page.locator("#focusReadiness").textContent(), /BLOCKED/);
  assert.equal(await page.locator("#quickFish").isDisabled(), true, "Fish Focus tuning is unavailable without a real BBox");
  for (let i = 0; i < 3; i++) {
    await page.locator("#stateButtons button").nth(i).click();
    await page.waitForTimeout(35);
    const record = await page.evaluate(() => ({
      state: document.querySelector("#stateLabel").textContent,
      frozenWidth: document.querySelector("#frozenImage").naturalWidth,
      frozenHeight: document.querySelector("#frozenImage").naturalHeight,
      previewWidth: document.querySelector("#previewCanvas").width,
      previewHeight: document.querySelector("#previewCanvas").height,
      photo: document.querySelector("#photoName").textContent,
    }));
    assert.equal(record.frozenWidth, 941);
    assert.equal(record.frozenHeight, 1672);
    assert.match(record.photo, /SHA-256 verified/);
    referenceStates.push(record.state);
    const png = await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    fs.writeFileSync(path.join(out, "web-" + record.state + ".png"), Buffer.from(png.split(",")[1], "base64"));
    await page.locator("#frozenImage").screenshot({ path: path.join(out, "frozen-" + record.state + ".png") });
    if (i === 0) await page.locator("#boards").screenshot({ path: path.join(out, "frozen-vs-web-source-identity-unverified.png") });
  }
  assert.deepEqual(referenceStates, REQUIRED_STATES);

  const staticCandidates = [];
  for (const candidate of ["A", "B", "C"]) {
    await page.locator("#stateButtons button").nth(0).click();
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    await page.selectOption("#staticCandidate", candidate);
    await page.selectOption("#fieldLayerView", "composite");
    await page.setViewportSize({ width: 1440, height: 1100 });
    await page.screenshot({ path: path.join(staticEvidence, `static-${candidate}-desktop.png`), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: path.join(staticEvidence, `static-${candidate}-narrow.png`), fullPage: true });
    for (const [view, slug] of [["receiving", "receiving-field"], ["primary", "primary-paths"], ["filaments", "secondary-filaments"], ["composite", "final-composite"]]) {
      await page.selectOption("#fieldLayerView", view);
      const png = await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
      fs.writeFileSync(path.join(staticEvidence, `static-${candidate}-${slug}.png`), Buffer.from(png.split(",")[1], "base64"));
    }
    staticCandidates.push(candidate);
  }
  await page.selectOption("#fieldLayerView", "composite");
  await page.selectOption("#staticCandidate", "B");
  await page.setViewportSize({ width: 1440, height: 1100 });

  await page.locator("#stateButtons button").nth(1).click();
  await page.screenshot({ path: path.join(out, "fish-focus-blocked.png"), fullPage: true });
  assert.match(await page.locator("#focusReadiness").textContent(), /BLOCKED/);
  await page.selectOption("#focusSource", "review");
  await page.waitForFunction(() => document.querySelector("#photoName")?.textContent.includes("detector_case_b_night_flash") && document.querySelector("#maskName")?.textContent.includes("REVIEW ONLY"), null, { timeout: 30000 });
  assert.match(await page.locator("#focusProvenance").textContent(), /Production: BLOCKED.*Review-only/);
  await page.screenshot({ path: path.join(out, "fish-focus-review-proxy-fish-located.png"), fullPage: true });
  await page.locator("#stateButtons button").nth(2).click();
  await page.screenshot({ path: path.join(out, "fish-focus-review-proxy-species-recognizing.png"), fullPage: true });

  const dynamicCandidates = [];
  const dynamicEnabled = await page.evaluate(() => window.OPTICAL_DYNAMIC_REVIEW_ENABLED === true);
  if (dynamicEnabled) for (const candidate of ["A", "B", "C"]) {
    await page.locator("#previewModeButtons [data-preview-mode='dynamic']").click();
    await page.selectOption("#dynamicCandidate", candidate);
    await page.selectOption("#motionSpeed", "1");
    await page.locator("#stateButtons button").nth(0).click();
    await page.locator("#playPause").click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(dynamicEvidence, `dynamic-${candidate}-image-recognizing.png`), fullPage: true });
    await page.locator("#stateButtons button").nth(1).click();
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(dynamicEvidence, `dynamic-${candidate}-fish-located.png`), fullPage: true });
    await page.locator("#stateButtons button").nth(2).click();
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(dynamicEvidence, `dynamic-${candidate}-species-recognizing.png`), fullPage: true });
    await page.locator("#playPause").click();
    await page.locator("#stateButtons button").nth(0).click();
    const downloadEvent = page.waitForEvent("download", { timeout: 30000 });
    await page.locator("#recordSequence").click();
    const clip = await downloadEvent;
    const clipPath = path.join(dynamicEvidence, `dynamic-${candidate}-three-state-cycle.webm`);
    await clip.saveAs(clipPath);
    assert.ok(fs.statSync(clipPath).size > 10000, `Dynamic ${candidate} clip must contain encoded frames`);
    dynamicCandidates.push(candidate);
  }
  if (dynamicEnabled) await page.locator("#playPause").click().catch(() => {});
  await page.locator("#focusSource").selectOption("blocked");
  await page.locator("#resetButton").click();

  await page.locator("#stateButtons button").nth(0).click();
  await page.locator("#previewCanvas").evaluate(canvas => { window.__opticalBaseline = new Uint8Array(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data); });
  await page.selectOption("#rendererSelect", "android-current");
  const currentVsHardware = await page.locator("#previewCanvas").evaluate(canvas => {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data; let changed = 0;
    for (let i = 0; i < data.length; i += 4) if (window.__opticalBaseline[i] !== data[i] || window.__opticalBaseline[i + 1] !== data[i + 1] || window.__opticalBaseline[i + 2] !== data[i + 2]) changed++;
    return changed;
  });
  assert.ok(currentVsHardware > 0, "android-current and hardware-gradient should produce different candidate pixels on the same input");
  await page.selectOption("#rendererSelect", "software-cache");
  const softwareVsHardware = await page.locator("#previewCanvas").evaluate(canvas => {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data; let changed = 0;
    for (let i = 0; i < data.length; i += 4) if (window.__opticalBaseline[i] !== data[i] || window.__opticalBaseline[i + 1] !== data[i + 1] || window.__opticalBaseline[i + 2] !== data[i + 2]) changed++;
    return changed;
  });
  assert.ok(softwareVsHardware > 0, "software-cache should differ from hardware-gradient on the same input");

  await page.selectOption("#rendererSelect", "hardware-gradient");
  await page.locator("#previewCanvas").evaluate(canvas => { window.__opticalBaseline = new Uint8Array(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data); });
  await page.locator("#quickReceiving").evaluate(input => { input.value = "0.3"; input.dispatchEvent(new Event("input", { bubbles: true })); });
  const quickChanged = await page.locator("#previewCanvas").evaluate(canvas => {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data; let changed = 0;
    for (let i = 0; i < data.length; i += 4) if (window.__opticalBaseline[i] !== data[i] || window.__opticalBaseline[i + 1] !== data[i + 1] || window.__opticalBaseline[i + 2] !== data[i + 2]) changed++;
    return changed;
  });
  assert.ok(quickChanged > 0, "quick optical layer tuning redraws immediately");
  await page.locator("#resetButton").click();

  await page.locator("#boards").screenshot({ path: path.join(out, "frozen-vs-web-reset.png") });
  await page.locator("#compareModes [data-mode='overlay']").click();
  await page.screenshot({ path: path.join(out, "frozen-web-overlay.png"), fullPage: true });
  await page.locator("#compareModes [data-mode='wipe']").click();
  await page.locator("#wipeInput").evaluate(input => { input.value = "62"; input.dispatchEvent(new Event("input", { bubbles: true })); });
  await page.locator("#compareScreen").screenshot({ path: path.join(out, "frozen-web-wipe.png") });
  await page.locator("#advancedDiagnostics").evaluate(node => { node.open = true; });
  await page.locator("#advancedModes [data-mode='diff']").click();
  await page.waitForFunction(() => document.querySelector("#metricMean")?.textContent !== "—");
  const metrics = await page.evaluate(() => ({
    mean: document.querySelector("#metricMean").textContent,
    threshold: document.querySelector("#metricThreshold").textContent,
    roi: document.querySelector("#metricRoi").textContent,
    gate: document.querySelector("#metricGate").textContent,
  }));
  assert.ok(Number.isFinite(Number(metrics.mean)));
  assert.match(metrics.gate, /探索性|identity unverified/i);
  await page.locator("#compareCanvas").evaluate(canvas => canvas.toDataURL("image/png")).then(data => {
    fs.writeFileSync(path.join(out, "pixel-diff-exploratory.png"), Buffer.from(data.split(",")[1], "base64"));
  });
  await page.locator("#roiCanvas").evaluate(canvas => canvas.toDataURL("image/png")).then(data => {
    fs.writeFileSync(path.join(out, "roi-side-by-side.png"), Buffer.from(data.split(",")[1], "base64"));
  });
  await page.selectOption("#roiSelect", "fish");
  await page.locator("#roiCanvas").screenshot({ path: path.join(out, "fish-focus-no-bbox.png") });
  await page.locator("#advancedModes [data-mode='candidates']").click();
  await page.locator("#candidateGrid").waitFor({ state: "visible" });
  await page.locator("#candidateGrid").screenshot({ path: path.join(out, "same-source-three-renderers.png") });

  async function saveDownload(selector, name) {
    const pending = page.waitForEvent("download", { timeout: 20000 });
    await page.locator(selector).click();
    const item = await pending;
    await item.saveAs(path.join(out, name || item.suggestedFilename()));
  }
  await saveDownload("#exportPreview", "web-current.png");
  await saveDownload("#exportCompare", "renderer-relative-composite.png");
  await saveDownload("#exportRoi", "roi-current.png");
  await saveDownload("#exportAssetManifest", "Recognition-Optical-Lab-Asset-Manifest.json");
  await saveDownload("#exportExperiment", "Recognition-Optical-Lab-Experiment.json");

  const experiment = JSON.parse(fs.readFileSync(path.join(out, "Recognition-Optical-Lab-Experiment.json"), "utf8"));
  experiment.params.layerStrength.receiving = 0.55;
  experiment.params.stateStrength = 0.73;
  experiment.state = "FISH_LOCATED";
  experiment.viewport = [412, 915];
  const importBuffer = Buffer.from(JSON.stringify(experiment));
  await page.locator("#importExperiment").setInputFiles({ name: "round-trip.json", mimeType: "application/json", buffer: importBuffer });
  await page.waitForFunction(() => document.querySelector("#quickReceiving")?.value === "0.55");
  assert.equal(await page.locator("#quickReceiving").inputValue(), "0.55", "experiment JSON import restores layer parameters");
  assert.equal(await page.locator("#stateButtons button.active").getAttribute("data-state"), "1", "experiment JSON import restores the product state");
  assert.equal(await page.locator("#viewportSelect").inputValue(), "412x915", "experiment JSON import restores the viewport");
  assert.equal(await page.locator("#paramStrength").inputValue(), "0.73", "experiment JSON import restores State Strength");
  await page.locator("#resetButton").click();

  await page.locator("#compareModes [data-mode='side']").click();
  await page.locator("#advancedDiagnostics").evaluate(node => { node.open = false; });
  const responsive = [];
  for (const [width, height] of RESPONSIVE) {
    await page.setViewportSize({ width, height });
    await page.locator("#viewportSelect").evaluate((select, value) => {
      select.value = value;
      select.dispatchEvent(new Event("change", { bubbles: true }));
    }, width + "x" + height);
    const check = await page.evaluate(() => ({
      width: innerWidth,
      scrollWidth: document.documentElement.scrollWidth,
      canvas: [document.querySelector("#previewCanvas").width, document.querySelector("#previewCanvas").height],
      boardsWidth: document.querySelector("#boards").getBoundingClientRect().width,
      previewRect: document.querySelector("#previewCanvas").getBoundingClientRect().toJSON(),
      quickRect: document.querySelector("#quickPanel").getBoundingClientRect().toJSON(),
      advancedOpen: document.querySelector("#advancedDiagnostics").open,
    }));
    assert.equal(check.scrollWidth, check.width, width + "x" + height + ": no horizontal overflow");
    assert.deepEqual(check.canvas, [width, height], width + "x" + height + ": viewport dimensions follow PhotoViewport contract");
    assert.ok(check.boardsWidth > 0 && check.previewRect.width > 0, width + "x" + height + ": main visual canvas remains visible");
    assert.equal(check.advancedOpen, false, width + "x" + height + ": advanced diagnostics remain collapsed");
    if (width <= 720) assert.ok(check.quickRect.top >= check.previewRect.bottom - 1, width + "x" + height + ": controls do not overlap the main canvas");
    await page.screenshot({ path: path.join(out, `responsive-${width}x${height}.png`), fullPage: true });
    responsive.push(width + "x" + height);
  }

  const repoErrors = repo.errors;
  await page.close();
  const design = await openReady(DESIGN_URL, "design-root / Pages preview");
  const designAlias = await design.page.evaluate(() => Array.from(document.querySelectorAll("#assetRows tr")).find(row => row.textContent.includes("processing-test-photo"))?.textContent);
  assert.match(designAlias, /real_catch_source\.jpg/);
  assert.match(designAlias, /VERIFIED/);
  await design.page.screenshot({ path: path.join(out, "design-root-pages-entry.png"), fullPage: true });
  allErrors.push(...repoErrors, ...design.errors);

  const summary = {
    result: "AUTOMATION_SMOKE_PASSED_VISUAL_REVIEW_REQUIRED",
    source_revision: audit.sourceRevision,
    asset_auto_loading: "PASS · both repository-root and design-root page layouts; config/manifest/3 Frozen/test photo SHA and dimensions checked",
    frozen_photo_identity: "BLOCKED · real source photo not linked to Processing Frozen screenshots",
    states: referenceStates,
    renderers: { currentVsHardwarePixels: currentVsHardware, softwareVsHardwarePixels: softwareVsHardware, quickLayerChangedPixels: quickChanged },
    diff: { ...metrics, status: "EXPLORATORY_ONLY_SOURCE_IDENTITY_UNVERIFIED" },
    responsive,
    staticCandidates,
    dynamicCandidates,
    fishFocus: "Production BLOCKED · Case B review-only annotated alpha available · no production Mask asserted",
    stageB: dynamicEnabled ? "WEB_REVIEW_ONLY · three motion candidates and 12s state-cycle WebM recordings exported" : "LOCKED_PENDING_STATIC_VISUAL_GATE",
    visualReview: "PENDING_USER_CHOICE · automation evidence does not equal visual approval",
    browserErrors: allErrors,
  };
  fs.writeFileSync(path.join(out, "browser-smoke.json"), JSON.stringify(summary, null, 2));
  assert.deepEqual(allErrors, [], "no browser runtime, console, or failed request errors");
  await design.page.close();
  await browser.close();
  console.log(JSON.stringify(summary, null, 2));
})().catch(async error => {
  console.error(error);
  try { await browser?.close(); } catch (closeError) { console.error("Chromium cleanup failed:", closeError); }
  process.exitCode = 1;
});
