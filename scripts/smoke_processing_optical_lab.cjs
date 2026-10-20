const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "playwright"));

const ROOT_URL = process.env.LAB_REPO_URL || "http://127.0.0.1:8765/design/manager/optical-lab/";
const DESIGN_URL = process.env.LAB_DESIGN_ROOT_URL || "http://127.0.0.1:8766/manager/optical-lab/";
const REQUIRED_STATES = ["IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING"];
const RESPONSIVE = [[390, 844], [320, 640], [360, 800], [412, 915], [800, 360]];
let browser;

async function captureReceivingFieldSignal(page, evidence, label) {
  await page.selectOption("#fieldLayerView", "receiving");
  await page.locator("#pathToggle").uncheck();
  await page.locator("#filamentToggle").uncheck();
  await page.locator("#fishToggle").uncheck();
  await page.locator("#fieldToggle").uncheck();
  await page.locator("#previewCanvas").evaluate(canvas => {
    window.__receivingFieldOff = new Uint8ClampedArray(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data);
  });
  await page.locator("#fieldToggle").check();
  const signal = await page.locator("#previewCanvas").evaluate(canvas => {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    const before = window.__receivingFieldOff;
    const corners = [
      { id: "top-left-gold", x0: 0, x1: .30, y0: 0, y1: .24, gold: true },
      { id: "top-right-blue", x0: .70, x1: 1, y0: 0, y1: .30, gold: false },
      { id: "bottom-left-gold", x0: 0, x1: .38, y0: .70, y1: 1, gold: true },
      { id: "bottom-right-blue", x0: .62, x1: 1, y0: .64, y1: 1, gold: false },
    ];
    const photoWidth = Math.min(canvas.width, canvas.height * .75), photoHeight = photoWidth / .75;
    const photoRect = { left: (canvas.width - photoWidth) / 2, top: (canvas.height - photoHeight) / 2, width: photoWidth, height: photoHeight };
    return { photo_rect_px: photoRect, corners: corners.map(corner => {
      let changed = 0, samples = 0, roleDelta = 0, deltaR = 0, deltaG = 0, deltaB = 0;
      const x0 = Math.floor(photoRect.left + photoRect.width * corner.x0), x1 = Math.floor(photoRect.left + photoRect.width * corner.x1);
      const y0 = Math.floor(photoRect.top + photoRect.height * corner.y0), y1 = Math.floor(photoRect.top + photoRect.height * corner.y1);
      for (let y = y0; y < y1; y += 2) {
        for (let x = x0; x < x1; x += 2) {
          const i = (y * canvas.width + x) * 4, dr = data[i] - before[i], dg = data[i + 1] - before[i + 1], db = data[i + 2] - before[i + 2];
          samples++;
          if (Math.max(Math.abs(dr), Math.abs(dg), Math.abs(db)) >= 8) {
            changed++;
            roleDelta += corner.gold ? dr - db : db - dr;
          }
          deltaR += dr; deltaG += dg; deltaB += db;
        }
      }
      return { id: corner.id, changed_ratio: changed / samples, color_role_margin_per_changed_pixel: changed ? roleDelta / changed : 0, color_role_delta_per_sample: roleDelta / samples, mean_delta_rgb: [deltaR / samples, deltaG / samples, deltaB / samples] };
    }) };
  });
  signal.automation_signal_status = "RENDER_SIGNAL_FLOOR_MET_VISUAL_REVIEW_REQUIRED";
  fs.writeFileSync(evidence.signalPath, JSON.stringify(signal, null, 2));
  const minimumRoleSignal = { "top-left-gold": 16, "top-right-blue": 24, "bottom-left-gold": 4, "bottom-right-blue": 1.4 };
  assert.ok(signal.corners.every(corner => corner.changed_ratio >= ({"top-left-gold":.70,"top-right-blue":.70,"bottom-left-gold":.20,"bottom-right-blue":.10}[corner.id]) && corner.color_role_delta_per_sample >= minimumRoleSignal[corner.id]), `${label} Receiving Field must clear the engineering color-signal floor in its asymmetric corner regions; this is not a visual PASS: ${JSON.stringify(signal)}`);
  await page.locator("#previewCanvas").screenshot({ path: evidence.canvasPath });
  if (evidence.pagePath) await page.screenshot({ path: evidence.pagePath, fullPage: true });
  await page.locator("#pathToggle").check();
  await page.locator("#filamentToggle").check();
  await page.locator("#fishToggle").check();
  return signal;
}

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
  let receivingFieldVisibility = null;
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
      if (candidate === "B" && view === "receiving") {
        receivingFieldVisibility = await captureReceivingFieldSignal(page, {
          signalPath: path.join(staticEvidence, "static-B-receiving-field-signal.json"),
          canvasPath: path.join(staticEvidence, "static-B-receiving-field-alone-visible.png"),
        }, "Default REVIEW_CANDIDATE_B");
        await page.locator("#fieldToggle").check();
        await page.selectOption("#fieldLayerView", "receiving");
      }
    }
    staticCandidates.push(candidate);
  }
  assert.ok(Array.isArray(receivingFieldVisibility?.corners) && receivingFieldVisibility.corners.length === 4, "default Balanced Receiving Light signal must be recorded for all four photo corners");
  await page.selectOption("#fieldLayerView", "composite");
  await page.selectOption("#staticCandidate", "B");
  await page.setViewportSize({ width: 1440, height: 1100 });

  await page.locator("#stateButtons button").nth(1).click();
  await page.screenshot({ path: path.join(out, "fish-focus-blocked.png"), fullPage: true });
  assert.match(await page.locator("#focusReadiness").textContent(), /BLOCKED/);
  await page.selectOption("#focusSource", "review");
  await page.waitForFunction(() => document.querySelector("#photoName")?.textContent.includes("detector_case_b_night_flash") && document.querySelector("#maskName")?.textContent.includes("REVIEW ONLY"), null, { timeout: 30000 });
  assert.match(await page.locator("#focusProvenance").textContent(), /Production: BLOCKED.*Review-only/);
  assert.match(await page.locator("#focusProvenance").textContent(), /0\.72×/);
  await page.screenshot({ path: path.join(out, "fish-focus-review-proxy-fish-located.png"), fullPage: true });
  await page.locator("#stateButtons button").nth(2).click();
  await page.screenshot({ path: path.join(out, "fish-focus-review-proxy-species-recognizing.png"), fullPage: true });

  const dynamicCandidates = [];
  const dynamicEnabled = await page.evaluate(() => window.OPTICAL_DYNAMIC_REVIEW_ENABLED === true);
  let speedPlaybackChecks = null;
  if (dynamicEnabled) for (const candidate of ["A", "B", "C"]) {
    await page.locator("#previewModeButtons [data-preview-mode='dynamic']").click();
    await page.selectOption("#dynamicCandidate", candidate);
    await page.selectOption("#motionSpeed", "1");
    await page.locator("#stateButtons button").nth(0).click();
    if (candidate === "A") {
      await page.selectOption("#motionSpeed", "0.5");
      await page.locator("#playPause").click();
      await page.waitForTimeout(650);
      const slowPreviewTime = await page.locator("#previewCanvas").evaluate(canvas => Number(canvas.dataset.previewTime));
      await page.selectOption("#motionSpeed", "1.5");
      await page.waitForTimeout(650);
      const speedState = await page.evaluate(() => ({
        previewTime: Number(document.querySelector("#previewCanvas").dataset.previewTime || 0),
        currentSpeed: document.querySelector("#motionSpeed").value,
      }));
      await page.locator("#playPause").click();
      assert.equal(speedState.currentSpeed, "1.5", "1.5× playback speed can be selected while motion is playing");
      assert.ok(speedState.previewTime > slowPreviewTime + 0.2, "preview time advances faster after switching from 0.5× to 1.5×");
      speedPlaybackChecks = { slowPreviewTime, fastPreviewTime: speedState.previewTime };
      await page.selectOption("#motionSpeed", "1");
    }
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
  if (dynamicEnabled) assert.deepEqual(dynamicCandidates, ["A", "B", "C"], "all three dynamic candidates must export a three-state WebM");
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
    for (let i = 0; i < data.length; i += 4) if (window.__opticalBaseline[i] !== data[i] || window.__opticalBaseline[i + 1] !== data[i + 1] || wind