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
const expectedPhotoSha = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const centerPath = "design/manager/nh01_acceptance/";
const routePath = "design/manager/nh01_acceptance/edgeglow-fish-static/";
let launchedBrowser;

async function canvasStats(page, id = "glowCanvas") {
  return page.locator(`#${id}`).evaluate(canvas => {
    const roi = { x: 0, y: 180, width: Math.min(560, canvas.width), height: Math.min(1356, canvas.height - 180) };
    const data = canvas.getContext("2d").getImageData(roi.x, roi.y, roi.width, roi.height).data;
    let hash = 2166136261;
    let luminance = 0;
    let nonBackgroundSamples = 0;
    let samples = 0;
    for (let y = 0; y < roi.height; y += 4) for (let x = 0; x < roi.width; x += 4) {
      const i = (y * roi.width + x) * 4;
      hash ^= data[i]; hash = Math.imul(hash, 16777619);
      hash ^= data[i + 1]; hash = Math.imul(hash, 16777619);
      hash ^= data[i + 2]; hash = Math.imul(hash, 16777619);
      luminance += data[i] * 0.2126 + data[i + 1] * 0.7152 + data[i + 2] * 0.0722;
      if (data[i] > 245 && data[i + 1] > 245 && data[i + 2] > 245) nonBackgroundSamples++;
      samples++;
    }
    return { width: canvas.width, height: canvas.height, roi, hash: (hash >>> 0).toString(16), meanLuminance: luminance / samples, nearWhitePixels: nonBackgroundSamples, sampleStep: 4, samples };
  });
}

async function wholeCanvasHash(page, id = "glowCanvas") {
  return page.locator(`#${id}`).evaluate(canvas => {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    let hash = 2166136261;
    for (let y = 0; y < canvas.height; y += 4) for (let x = 0; x < canvas.width; x += 4) {
      const i = (y * canvas.width + x) * 4;
      hash ^= data[i]; hash = Math.imul(hash, 16777619);
      hash ^= data[i + 1]; hash = Math.imul(hash, 16777619);
      hash ^= data[i + 2]; hash = Math.imul(hash, 16777619);
    }
    return (hash >>> 0).toString(16);
  });
}

async function perimeterDeltaByCorner(page) {
  return page.locator("#glowCanvas").evaluate(canvas => {
    const ctx = canvas.getContext("2d");
    const current = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
    const reference = window.__edgeFieldDefaultPixels;
    const corners = [
      { id: "top-left-gold", x0: 0, x1: 0.30, y0: 0, y1: 0.24, warmer: true },
      { id: "top-right-blue", x0: 0.70, x1: 1, y0: 0, y1: 0.30, warmer: false },
      { id: "bottom-left-gold", x0: 0, x1: 0.38, y0: 0.70, y1: 1, warmer: true },
      { id: "bottom-right-blue", x0: 0.62, x1: 1, y0: 0.64, y1: 1, warmer: false }
    ];
    return corners.map(corner => {
      let changedPixels = 0, sampleCount = 0, deltaR = 0, deltaG = 0, deltaB = 0;
      for (let y = Math.floor(canvas.height * corner.y0); y < Math.floor(canvas.height * corner.y1); y += 4) {
        for (let x = Math.floor(canvas.width * corner.x0); x < Math.floor(canvas.width * corner.x1); x += 4) {
          const i = (y * canvas.width + x) * 4;
          sampleCount++;
          // Compare the light-on frame against the light-off reference.
          const dr = reference[i] - current[i];
          const dg = reference[i + 1] - current[i + 1];
          const db = reference[i + 2] - current[i + 2];
          if (dr !== 0 || dg !== 0 || db !== 0) changedPixels++;
          deltaR += dr; deltaG += dg; deltaB += db;
        }
      }
      const colorRoleMargin = (corner.warmer ? deltaR - deltaB : deltaB - deltaR) / sampleCount;
      return { id: corner.id, changedPixels, changedRatio: changedPixels / sampleCount, colorRoleMargin, signedDeltaRGB: [deltaR, deltaG, deltaB], colorRoleMatches: colorRoleMargin > 0 };
    });
  });
}

async function fishWarmthSamples(page) {
  return page.locator("#glowCanvas").evaluate(canvas => {
    const contour = document.getElementById("fishContourPath");
    const total = contour.getTotalLength();
    const pixels = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    const positions = [0.04, 0.11, 0.18, 0.25, 0.32, 0.39, 0.46, 0.53, 0.60, 0.67, 0.74, 0.81, 0.88, 0.95];
    return positions.map(t => {
      const point = contour.getPointAtLength(total * t);
      const x = Math.max(1, Math.min(canvas.width - 2, Math.round(point.x)));
      const y = Math.max(1, Math.min(canvas.height - 2, Math.round(point.y)));
      let r = 0, g = 0, b = 0, count = 0;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const i = ((y + dy) * canvas.width + x + dx) * 4;
        r += pixels[i]; g += pixels[i + 1]; b += pixels[i + 2]; count++;
      }
      r /= count; g /= count; b /= count;
      return { position: t, redMinusBlue: r - b, greenMinusBlue: g - b };
    });
  });
}

async function afterRender(page, action) {
  const previous = await page.evaluate(() => Number(document.documentElement.dataset.renderVersion || 0));
  await action();
  await page.waitForFunction(version => Number(document.documentElement.dataset.renderVersion || 0) > version, previous, { timeout: 10000 });
}

async function setControl(page, selector, value) {
  await afterRender(page, () => page.locator(selector).evaluate((node, next) => {
      node.value = String(next);
      node.dispatchEvent(new Event("input", { bubbles: true }));
    }, value));
}

async function saveDownload(page, selector, targetPath) {
  const downloadReady = page.waitForEvent("download");
  await page.locator(selector).click();
  const download = await downloadReady;
  await download.saveAs(targetPath);
}

async function saveCornerCrop(page, id, x0, y0, x1, y1, targetPath) {
  const dataUrl = await page.locator("#glowCanvas").evaluate((source, crop) => {
    const canvas = document.createElement("canvas");
    const x = Math.floor(source.width * crop.x0), y = Math.floor(source.height * crop.y0);
    canvas.width = Math.max(1, Math.floor(source.width * (crop.x1 - crop.x0)));
    canvas.height = Math.max(1, Math.floor(source.height * (crop.y1 - crop.y0)));
    canvas.getContext("2d").drawImage(source, x, y, canvas.width, canvas.height, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/png");
  }, { x0, y0, x1, y1 });
  await fs.writeFile(targetPath, Buffer.from(dataUrl.split(",")[1], "base64"));
  return { id, file: path.basename(targetPath) };
}

async function main() {
  await fs.mkdir(outputDir, { recursive: true });
  const browser = launchedBrowser = await chromium.launch({ headless: true });
  const errors = [];
  const page = await browser.newPage({ viewport: { width: 1440, height: 1180 }, deviceScaleFactor: 1 });
  page.on("pageerror", error => errors.push(`pageerror: ${error.message}`));
  page.on("console", message => { if (message.type() === "error") errors.push(`console: ${message.text()}`); });

  const centerUrl = new URL(centerPath, rootUrl).href;
  await page.goto(centerUrl, { waitUntil: "networkidle" });
  const entry = page.locator('[data-edgeglow-entry="recognition-processing"]');
  assert.equal(await entry.count(), 1, "Acceptance Center has one Recognition Processing → EdgeGlow entry.");
  assert.match(await entry.innerText(), /Recognition Processing.*EdgeGlow Fish Static Prototype.*Static \+ Dynamic Review/);
  await entry.click();
  await page.waitForURL(url => url.pathname.includes("/edgeglow-fish-static/"), { timeout: 15000 });
  const staticRouteUrl = page.url();
  await page.waitForFunction(() => window.__EDGEGLOW_LAB__?.pathLength > 1000 && window.__EDGEGLOW_LAB__?.edgeFieldInset > 0, { timeout: 15000 });
  await page.waitForFunction(() => window.__EDGEGLOW_LAB__?.config?.schema_version === "2.0.0", { timeout: 10000 });

  const identity = await page.evaluate(async () => ({
    title: document.title,
    status: document.getElementById("sourceStatus").textContent,
    statusCode: document.getElementById("sourceStatus").dataset.status,
    width: window.__EDGEGLOW_LAB__.photoDimensions[0],
    height: window.__EDGEGLOW_LAB__.photoDimensions[1],
    pathLength: window.__EDGEGLOW_LAB__.pathLength,
    edgeFieldInset: window.__EDGEGLOW_LAB__.edgeFieldInset,
    contourBounds: (() => { const b = document.getElementById("fishContourPath").getBBox(); return [b.x, b.y, b.width, b.height]; })(),
    photoSha: document.getElementById("photoSha").textContent,
    buildLabel: document.getElementById("buildSha").textContent,
    edgeFieldPaths: window.__EDGEGLOW_LAB__.edgeFieldPaths,
    buildInfo: await fetch("./build-info.json", { cache: "no-store" }).then(response => response.json())
  }));
  assert.equal(identity.statusCode, "PASS", identity.status);
  assert.equal(identity.width, 1152);
  assert.equal(identity.height, 1536);
  assert.ok(identity.edgeFieldInset >= 100, `Photo-edge blur bleed is rendered on an expanded offscreen layer: ${identity.edgeFieldInset}px inset.`);
  assert.equal(identity.photoSha, expectedPhotoSha);
  assert.equal((await page.locator("#fishGoldColor").getAttribute("value")), "#ffd887", "Fish Focus starts in warm gold, separate from the perimeter field palette.");
  assert.equal((await page.locator("#fishCoreColor").getAttribute("value")), "#ffe7ae", "Fish core uses the warm-gold highlight family.");
  assert.equal(await page.locator("#blueColor").count(), 0, "There is no blue control for the fish-body highlight.");
  assert.equal(identity.buildInfo.photo_sha256, expectedPhotoSha);
  assert.equal(identity.buildInfo.optical_config_version, "2.0.0");
  if (process.env.PR_SOURCE_SHA) assert.equal(identity.buildInfo.pr_source_sha, process.env.PR_SOURCE_SHA);
  if (process.env.CENTER_SOURCE_SHA) assert.equal(identity.buildInfo.acceptance_center_source_sha, process.env.CENTER_SOURCE_SHA);
  assert.ok(identity.pathLength > 2500, `PathMeasure arc length is plausible: ${identity.pathLength}`);
  assert.ok(identity.contourBounds[2] < 600 && identity.contourBounds[3] > 900, "Contour is irregular fish-sized geometry, not an image-sized frame.");
  assert.deepEqual(identity.edgeFieldPaths.map(path => path.id), ["B1", "B3", "G1", "G3", "B2", "G2", "B4", "B1C", "G1C", "G3C", "B3C", "B1M"], "Four frozen primary paths, three source-backed secondary fragments, four companion hairlines and one micro path are present.");
  assert.deepEqual(identity.edgeFieldPaths.slice(0, 7).map(path => [path.seed, path.visibleRatio]), [[0, 0.3], [0.47, 0.2], [0.12, 0.26], [0.64, 0.22], [0.07, 0.18], [0.09, 0.16], [0.11, 0.14]], "Primary seed values are frozen; secondary seeds are recorded Android runtime values.");
  assert.ok(identity.edgeFieldPaths.slice(4, 7).every(path => path.seedAuthority === "ANDROID_RUNTIME_RP_A03_NOT_FROZEN"), "Secondary path seeds remain explicitly non-Frozen.");
  console.log("SMOKE: source photo, contour and all edge-field paths verified");

  const presetButtons = await page.locator("[data-optical-preset]").evaluateAll(nodes => nodes.map(node => node.dataset.opticalPreset));
  assert.deepEqual(presetButtons, ["current", "enhanced", "frozen_match"], "The Current, Enhanced and Frozen Match presets are available.");
  const evidenceDir = path.join(outputDir, "visual-evidence");
  await fs.mkdir(evidenceDir, { recursive: true });
  const sourcePhotoUrl = new URL("../../../system/fish_media_display_v1/assets/real_catch_source.jpg", page.url());
  const photoResponse = await page.request.get(sourcePhotoUrl.href);
  assert.equal(photoResponse.status(), 200, "The original fixed test photograph is reachable.");
  await fs.writeFile(path.join(evidenceDir, "Original_Real_Catch_Photo.jpg"), await photoResponse.body());
  for (const [name, file] of [
    ["Frozen_02_AI_Understanding.png", "02_AI_Understanding_Frozen.png"],
    ["Frozen_03_Fish_Highlight.png", "03_Fish_Highlight_Frozen.png"],
    ["Frozen_04_Fish_Identifying.png", "04_Fish_Identifying_Frozen.png"]
  ]) {
    const response = await page.request.get(new URL(`../../../pages/recognition/design/${file}`, page.url()).href);
    assert.equal(response.status(), 200, `Frozen reference ${file} is reachable.`);
    await fs.writeFile(path.join(evidenceDir, name), await response.body());
  }
  const presetEvidence = [];
  for (const [preset, fileBase] of [["current", "Current"], ["enhanced", "Enhanced"], ["frozen_match", "Frozen_Match"]]) {
    await afterRender(page, () => page.locator(`[data-optical-preset="${preset}"]`).click());
    assert.equal(await page.locator(`[data-optical-preset="${preset}"]`).getAttribute("aria-pressed"), "true", `${preset} preset is selected by the live control.`);
    await saveDownload(page, "#exportPngButton", path.join(evidenceDir, `EdgeGlow_${fileBase}.png`));
    await saveDownload(page, "#exportJsonButton", path.join(evidenceDir, `EdgeGlow_${fileBase}_Parameters.json`));
    presetEvidence.push({ preset, png: `EdgeGlow_${fileBase}.png`, parameters: `EdgeGlow_${fileBase}_Parameters.json`, pixel_hash: await wholeCanvasHash(page) });
  }
  await afterRender(page, () => page.locator('[data-optical-preset="enhanced"]').click());
  const blendTrialHashes = {};
  for (const blend of ["source-over", "screen", "additive", "soft-light"]) {
    await afterRender(page, () => page.locator("#opticalCoreBlend").selectOption(blend));
    blendTrialHashes[blend] = await wholeCanvasHash(page);
  }
  assert.ok(new Set(Object.values(blendTrialHashes)).size > 1, `Optical Core blend trials must change visible pixels: ${JSON.stringify(blendTrialHashes)}`);
  await afterRender(page, () => page.locator("#opticalCoreBlend").selectOption("source-over"));
  const cornerEvidence = [];
  for (const corner of [
    { id: "G1", x0: 0, y0: 0, x1: 0.32, y1: 0.25 },
    { id: "B1", x0: 0.68, y0: 0, x1: 1, y1: 0.32 },
    { id: "G3", x0: 0, y0: 0.70, x1: 0.40, y1: 1 },
    { id: "B3", x0: 0.60, y0: 0.64, x1: 1, y1: 1 }
  ]) cornerEvidence.push(await saveCornerCrop(page, corner.id, corner.x0, corner.y0, corner.x1, corner.y1, path.join(evidenceDir, `Enhanced_Corner_${corner.id}.png`)));
  await fs.copyFile(path.join(evidenceDir, "EdgeGlow_Enhanced.png"), path.join(evidenceDir, "Final_Enhanced_Render.png"));
  console.log("SMOKE: exported exact photo, three presets, full parameter JSON, Frozen references and four enlarged corner crops");

  const canvasDimensions = await page.locator(".photo-stage canvas").evaluateAll(nodes => nodes.map(node => [node.width, node.height]));
  assert.equal(canvasDimensions.length, 3);
  assert.deepEqual(canvasDimensions, [[1152, 1536], [1152, 1536], [1152, 1536]], "All three views share the photo pixel coordinate system.");

  const defaultPreviewGlow = await canvasStats(page);
  const defaultGlow = defaultPreviewGlow;
  const defaultWholeGlow = await wholeCanvasHash(page);
  const fishWarmSamples = await fishWarmthSamples(page);
  const goldSamples = fishWarmSamples.filter(sample => sample.redMinusBlue >= 24 && sample.greenMinusBlue >= 10);
  assert.ok(goldSamples.length >= 9, `Frozen 03 fish contour should stay chromatically warm gold, not wash to white: ${JSON.stringify(fishWarmSamples)}`);
  console.log("SMOKE: fish highlight retains warm-gold color at the contour");
  const defaultCore = await canvasStats(page, "contourCanvas");
  await page.locator("#glowCanvas").evaluate(canvas => {
    window.__edgeFieldDefaultPixels = new Uint8ClampedArray(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data);
  });

  // Capture the default Frozen-role composition before stress interactions.
  // The canvas backing store is already 1152x1536, even at responsive CSS scale.
  const exportControls = {
    png: await page.locator("#exportPngButton").count(),
    json: await page.locator("#exportJsonButton").count()
  };
  assert.deepEqual(exportControls, { png: 1, json: 1 }, "PNG and parameter JSON export controls are available.");
  console.log("SMOKE: PNG and JSON export controls are present; runtime downloads are checked in the live browser");

  await afterRender(page, () => page.locator("#edgeFieldEnabled").uncheck());
  const noEdgeField = await wholeCanvasHash(page);
  assert.notEqual(noEdgeField, defaultWholeGlow, "Edge Field master toggle removes all photo-edge lighting.");
  const perimeterCorners = await perimeterDeltaByCorner(page);
  await fs.writeFile(path.join(evidenceDir, "Perimeter_Corner_Signals.json"), `${JSON.stringify(perimeterCorners, null, 2)}\n`);
  assert.ok(perimeterCorners.every(corner => corner.changedRatio > 0.55 && corner.colorRoleMargin > 12 && corner.colorRoleMatches), `Enhanced blue/gold perimeter light must produce a substantial color signal in every photo corner: ${JSON.stringify(perimeterCorners)}`);
  await afterRender(page, () => page.locator("#edgeFieldEnabled").check());
  await afterRender(page, () => page.locator("#resetButton").click());
  assert.equal(await wholeCanvasHash(page), defaultWholeGlow, "Reset restores the default edge-field render.");
  console.log("SMOKE: photo-edge field, four corner color roles and reset verified");

  await afterRender(page, () => page.locator("#outerEnabled").uncheck());
  const noOuter = await canvasStats(page);
  assert.notEqual(noOuter.hash, defaultGlow.hash, "Outer Bloom toggle changes rendered pixels.");
  await afterRender(page, () => page.locator("#coreEnabled").uncheck());
  const noCore = await canvasStats(page);
  assert.notEqual(noCore.hash, noOuter.hash, "Core Highlight toggle changes rendered pixels independently of the preceding state.");
  await afterRender(page, () => page.locator("#cometEnabled").uncheck());
  const noComet = await canvasStats(page);
  assert.notEqual(noComet.hash, noCore.hash, "Static Comet toggle changes rendered pixels independently of the preceding state.");
  await afterRender(page, () => page.locator("#resetButton").click());
  const afterLayerReset = await canvasStats(page);
  assert.equal(afterLayerReset.hash, defaultGlow.hash, "Reset restores all independently toggled layers.");
  console.log("SMOKE: gold Bloom, Core and Comet layer toggles verified");

  await setControl(page, "#outerWidth", 52);
  assert.equal(await page.locator("#outerWidthValue").innerText(), "52 px", "Outer Bloom readout tracks the live range value.");
  const wideBloom = await canvasStats(page);
  assert.notEqual(wideBloom.hash, afterLayerReset.hash, "Outer Bloom width changes rendered pixels.");
  await setControl(page, "#cometPosition", 64);
  const movedComet = await canvasStats(page);
  assert.notEqual(movedComet.hash, wideBloom.hash, "Static Comet path position changes rendered pixels.");
  console.log("SMOKE: live optical parameter changes verified");

  await afterRender(page, () => page.locator("#contourVisible").uncheck());
  const contourHidden = await canvasStats(page, "contourCanvas");
  assert.notEqual(contourHidden.hash, defaultCore.hash, "Contour visibility control changes only contour view output.");
  await afterRender(page, () => page.locator("#contourVisible").check());
  assert.equal((await canvasStats(page, "contourCanvas")).hash, defaultCore.hash);

  await afterRender(page, () => page.locator("#compareMode").selectOption("overlay"));
  const overlay = await canvasStats(page);
  assert.notEqual(overlay.hash, defaultGlow.hash, "50% overlay uses a separate blended comparison output.");
  assert.match(await page.locator("#comparisonCaption").innerText(), /50% Overlay/);
  await afterRender(page, () => page.locator("#compareMode").selectOption("wipe"));
  await setControl(page, "#wipePosition", 32);
  const wipeA = await canvasStats(page);
  await setControl(page, "#wipePosition", 68);
  const wipeB = await canvasStats(page);
  assert.notEqual(wipeA.hash, wipeB.hash, "Wipe handle changes the image comparison boundary.");
  assert.equal(await page.locator("#wipePosition").isVisible(), true);
  console.log("SMOKE: Overlay and Wipe comparison modes verified");

  await afterRender(page, () => page.locator("#compareMode").selectOption("side"));


  await afterRender(page, () => page.evaluate(() => {
    for (const id of ["edgeFieldEnabled", "outerEnabled", "coreEnabled", "cometEnabled"]) {
      const control = document.getElementById(id);
      control.checked = false;
      control.dispatchEvent(new Event("input", { bubbles: true }));
    }
  }));
  const viewportResults = [];
  for (const viewport of [{ width: 390, height: 844 }, { width: 800, height: 360 }, { width: 1440, height: 1180 }]) {
    await page.setViewportSize(viewport);
    await page.waitForTimeout(100);
    const layout = await page.evaluate(() => ({
      innerWidth,
      scrollWidth: document.documentElement.scrollWidth,
      cards: [...document.querySelectorAll(".edgeglow-view-card")].map(x => { const r = x.getBoundingClientRect(); return { x: r.x, width: r.width, y: r.y, height: r.height }; }),
      canvas: (() => { const r = document.getElementById("glowCanvas").getBoundingClientRect(); return { width: r.width, height: r.height }; })(),
      edgeFieldInset: window.__EDGEGLOW_LAB__.edgeFieldInset
    }));
    assert.ok(layout.scrollWidth <= viewport.width + 1, `No horizontal overflow at ${viewport.width}x${viewport.height}: ${layout.scrollWidth}`);
    assert.equal(layout.cards.length, 3);
    assert.ok(Math.abs(layout.canvas.width / layout.canvas.height - 0.75) < 0.005, "Photo FIT preserves the original 3:4 ratio.");
    assert.ok(layout.edgeFieldInset >= 100, `Responsive resize retains padded perimeter blur at ${viewport.width}x${viewport.height}.`);
    viewportResults.push({ viewport, scrollWidth: layout.scrollWidth, cardWidths: layout.cards.map(card => card.width) });
    console.log(`SMOKE: responsive layout verified at ${viewport.width}x${viewport.height}`);
  }

  const opticalEntry = page.locator("#opticalReviewEntry");
  assert.equal(await opticalEntry.count(), 1, "the existing EdgeGlow page links to the static/dynamic Optical Review");
  await opticalEntry.click();
  await page.waitForURL(url => url.pathname.endsWith("/edgeglow-fish-static/optical-review/"), { timeout: 15000 });
  await page.waitForFunction(() => window.OPTICAL_LAB_BUILD_INFO__?.head_sha && document.querySelector("#overallStatus")?.textContent === "REVIEW_REQUIRED", null, { timeout: 30000 });
  const opticalReview = await page.evaluate(() => ({
    title: document.title,
    sourceSha: window.OPTICAL_LAB_BUILD_INFO__?.head_sha,
    status: document.querySelector("#overallStatus")?.textContent,
    assetCount: document.querySelectorAll("#assetRows tr").length,
    assetStatuses: [...document.querySelectorAll("#assetRows tr .asset-status")].map(node => node.textContent),
    stateCount: document.querySelectorAll("#stateButtons button").length,
    modeCount: document.querySelectorAll("#previewModeButtons button").length,
    productionDisabled: document.querySelector('#focusSource option[value="production"]')?.disabled,
    productionBlocked: document.querySelector("#focusProvenance")?.textContent.includes("Production: BLOCKED"),
    canvas: Boolean(document.querySelector("#previewCanvas")),
  }));
  assert.equal(opticalReview.sourceSha, process.env.PR_SOURCE_SHA, "the nested review page exposes the current PR SHA");
  assert.equal(opticalReview.status, "REVIEW_REQUIRED");
  assert.equal(opticalReview.assetCount, 7, "the nested page verifies config, state manifest, registry, three Frozen images and photo fixture");
  assert.ok(opticalReview.assetStatuses.every(status => status === "VERIFIED"), "all optical review source assets verify in the composed GitHub Pages site");
  assert.equal(opticalReview.stateCount, 3, "three product states remain available");
  assert.equal(opticalReview.modeCount, 2, "static and dynamic preview modes are available");
  assert.equal(opticalReview.productionDisabled, true, "Production Fish Focus remains unavailable without current production evidence");
  assert.equal(opticalReview.productionBlocked, true, "the provenance UI explicitly reports Production BLOCKED");
  assert.equal(opticalReview.canvas, true, "the optical review canvas is mounted");
  await page.locator("#previewModeButtons [data-preview-mode='dynamic']").click();
  await page.selectOption("#dynamicCandidate", "B");
  await page.locator("#stateButtons button").nth(1).click();
  await page.selectOption("#focusSource", "review");
  await page.waitForFunction(() => document.querySelector("#photoName")?.textContent.includes("detector_case_b_night_flash") && document.querySelector("#maskName")?.textContent.includes("REVIEW ONLY"), null, { timeout: 15000 });
  await page.locator("#playPause").click();
  await page.waitForTimeout(450);
  const dynamicReview = await page.evaluate(() => ({
    candidate: document.querySelector("#dynamicCandidate")?.value,
    state: document.querySelector("#stateButtons button.active")?.dataset.state,
    provenance: document.querySelector("#focusReadiness")?.textContent,
    previewTime: Number(document.querySelector("#previewCanvas")?.dataset.previewTime || 0),
  }));
  assert.equal(dynamicReview.candidate, "B");
  assert.equal(dynamicReview.state, "1");
  assert.match(dynamicReview.provenance, /REVIEW_PROXY_NOT_PRODUCTION/);
  assert.ok(dynamicReview.previewTime > 0, "the integrated dynamic B preview advances in the Acceptance Center route");
  await page.locator("#playPause").click();
  await page.screenshot({ path: path.join(evidenceDir, "Acceptance_Center_Optical_Review_Dynamic_B_Fish_Located.png"), fullPage: true });
  console.log("SMOKE: integrated optical review route, exact source assets, dynamic playback and Review-only provenance verified");

  assert.deepEqual(errors, [], "No new browser page or console errors.");
  const report = {
    task_id: "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2",
    run_id: runId,
    status: "PASS_ENGINEERING_INTERACTION",
    visual_quality: "REVIEW_REQUIRED",
    android_runtime: "NOT_RUN",
    cloud_route_url: staticRouteUrl,
    optical_review_route_url: page.url(),
    optical_review_source_sha: opticalReview.sourceSha,
    dynamic_review: { candidate: dynamicReview.candidate, state: dynamicReview.state, preview_time: dynamicReview.previewTime, fish_focus_source: "REVIEW_PROXY_NOT_PRODUCTION" },
    pr_source_sha: identity.buildInfo.pr_source_sha,
    acceptance_center_source_sha: identity.buildInfo.acceptance_center_source_sha,
    source_photo_sha256: expectedPhotoSha,
    source_photo_dimensions: [1152, 1536],
    optical_config_version: "2.0.0",
    optical_presets: presetEvidence,
    optical_core_blend_trials: blendTrialHashes,
    corner_zoom_evidence: cornerEvidence,
    contour_source: "MANUAL_REFERENCE_CONTOUR",
    path_length_photo_px: identity.pathLength,
    contour_bounds_photo_px: identity.contourBounds,
    edge_effect_padding_px: identity.edgeFieldInset,
    edge_field_paths: identity.edgeFieldPaths.map(({ id, family, widthDp, seed, visibleRatio, weight, length }) => ({ id, family, widthDp, seed, visibleRatio, weight, length })),
    perimeter_corner_delta: perimeterCorners,
    effect_bounds_photo_px: await page.evaluate(() => window.__EDGEGLOW_LAB__.effectBounds),
    default_pixel_hash: defaultGlow.hash,
    toggles_and_parameters_change_pixels: true,
    browser_verified_layer_toggles: ["edgeFieldEnabled", "outerEnabled", "coreEnabled", "cometEnabled"],
    edge_layer_control_ids: ["receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled"],
    compare_modes: ["side", "overlay", "wipe"],
    export_controls: { png_button_present: exportControls.png === 1, json_button_present: exportControls.json === 1, runtime_downloads: "PENDING_LIVE_BROWSER_CHECK" },
    responsive: viewportResults,
    browser_errors: errors,
    build_label: identity.buildLabel,
    artifacts: ["EdgeGlow_Fish_Static_Test_Report.json", "visual-evidence/Original_Real_Catch_Photo.jpg", "visual-evidence/EdgeGlow_Current.png", "visual-evidence/EdgeGlow_Enhanced.png", "visual-evidence/EdgeGlow_Frozen_Match.png", "visual-evidence/EdgeGlow_Enhanced_Parameters.json", "visual-evidence/Final_Enhanced_Render.png", ...cornerEvidence.map(crop => `visual-evidence/${crop.file}`), "visual-evidence/Frozen_02_AI_Understanding.png", "visual-evidence/Frozen_03_Fish_Highlight.png", "visual-evidence/Frozen_04_Fish_Identifying.png"]
  };
  await fs.writeFile(path.join(outputDir, "EdgeGlow_Fish_Static_Test_Report.json"), `${JSON.stringify(report, null, 2)}\n`);
  console.log(JSON.stringify(report, null, 2));
  await browser.close();
}

main().catch(async error => {
  console.error(error);
  await launchedBrowser?.close().catch(() => {});
  process.exitCode = 1;
});
