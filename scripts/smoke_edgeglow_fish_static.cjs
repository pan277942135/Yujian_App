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
      let changedPixels = 0, deltaR = 0, deltaG = 0, deltaB = 0;
      for (let y = Math.floor(canvas.height * corner.y0); y < Math.floor(canvas.height * corner.y1); y += 4) {
        for (let x = Math.floor(canvas.width * corner.x0); x < Math.floor(canvas.width * corner.x1); x += 4) {
          const i = (y * canvas.width + x) * 4;
          const dr = current[i] - reference[i];
          const dg = current[i + 1] - reference[i + 1];
          const db = current[i + 2] - reference[i + 2];
          if (dr !== 0 || dg !== 0 || db !== 0) changedPixels++;
          deltaR += dr; deltaG += dg; deltaB += db;
        }
      }
      return { id: corner.id, changedPixels, signedDeltaRGB: [deltaR, deltaG, deltaB], colorRoleMatches: corner.warmer ? deltaR > deltaB : deltaB > deltaR };
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
  assert.match(await entry.innerText(), /Recognition Processing.*EdgeGlowView Fish Static Prototype/);
  await entry.click();
  await page.waitForURL(url => url.pathname.includes("/edgeglow-fish-static/"), { timeout: 15000 });
  await page.waitForFunction(() => window.__EDGEGLOW_LAB__?.pathLength > 1000 && window.__EDGEGLOW_LAB__?.edgeFieldInset > 0, { timeout: 15000 });

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
  if (process.env.PR_SOURCE_SHA) assert.equal(identity.buildInfo.pr_source_sha, process.env.PR_SOURCE_SHA);
  if (process.env.CENTER_SOURCE_SHA) assert.equal(identity.buildInfo.acceptance_center_source_sha, process.env.CENTER_SOURCE_SHA);
  assert.ok(identity.pathLength > 2500, `PathMeasure arc length is plausible: ${identity.pathLength}`);
  assert.ok(identity.contourBounds[2] < 600 && identity.contourBounds[3] > 900, "Contour is irregular fish-sized geometry, not an image-sized frame.");
  assert.deepEqual(identity.edgeFieldPaths.map(path => path.id), ["B1", "B3", "G1", "G3", "B2", "G2", "B4", "B1C", "G1C", "G3C", "B3C", "B1M"], "Four frozen primary paths, three source-backed secondary fragments, four companion hairlines and one micro path are present.");
  assert.deepEqual(identity.edgeFieldPaths.slice(0, 7).map(path => [path.seed, path.visibleRatio]), [[0, 0.3], [0.47, 0.2], [0.12, 0.26], [0.64, 0.22], [0.07, 0.18], [0.09, 0.16], [0.11, 0.14]], "Primary seed values are frozen; secondary seeds are recorded Android runtime values.");
  assert.ok(identity.edgeFieldPaths.slice(4, 7).every(path => path.seedAuthority === "ANDROID_RUNTIME_RP_A03_NOT_FROZEN"), "Secondary path seeds remain explicitly non-Frozen.");

  const canvasDimensions = await page.locator(".photo-stage canvas").evaluateAll(nodes => nodes.map(node => [node.width, node.height]));
  assert.equal(canvasDimensions.length, 3);
  assert.deepEqual(canvasDimensions, [[1152, 1536], [1152, 1536], [1152, 1536]], "All three views share the photo pixel coordinate system.");

  const defaultGlow = await canvasStats(page);
  const defaultWholeGlow = await wholeCanvasHash(page);
  const defaultCore = await canvasStats(page, "contourCanvas");
  await page.locator("#glowCanvas").evaluate(canvas => {
    window.__edgeFieldDefaultPixels = new Uint8ClampedArray(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data);
  });
  await afterRender(page, () => page.locator("#receivingLightEnabled").uncheck());
  const noReceivingLight = await wholeCanvasHash(page);
  assert.notEqual(noReceivingLight, defaultWholeGlow, "Receiving Light toggle changes corner illumination pixels.");
  await afterRender(page, () => page.locator("#receivingLightEnabled").check());
  await afterRender(page, () => page.locator("#primaryIslandsEnabled").uncheck());
  const noPrimaryIslands = await wholeCanvasHash(page);
  assert.notEqual(noPrimaryIslands, defaultWholeGlow, "Primary Energy Islands toggle changes the four irregular edge paths.");
  await afterRender(page, () => page.locator("#primaryIslandsEnabled").check());
  await afterRender(page, () => page.locator("#secondaryFragmentsEnabled").uncheck());
  const noSecondaryFragments = await wholeCanvasHash(page);
  assert.notEqual(noSecondaryFragments, defaultWholeGlow, "Secondary B2/G2/B4 toggle independently changes perimeter pixels.");
  await afterRender(page, () => page.locator("#secondaryFragmentsEnabled").check());
  await afterRender(page, () => page.locator("#edgeFieldEnabled").uncheck());
  const noEdgeField = await wholeCanvasHash(page);
  assert.notEqual(noEdgeField, defaultWholeGlow, "Edge Field master toggle removes all photo-edge lighting.");
  const perimeterCorners = await perimeterDeltaByCorner(page);
  assert.ok(perimeterCorners.every(corner => corner.changedPixels > 20 && corner.colorRoleMatches), `Frozen blue/gold roles are perceptible in all four photo corners: ${JSON.stringify(perimeterCorners)}`);
  await afterRender(page, () => page.locator("#edgeFieldEnabled").check());
  await setControl(page, "#edgeFieldIntensity", 38);
  const lowerEdgeField = await wholeCanvasHash(page);
  assert.notEqual(lowerEdgeField, defaultWholeGlow, "Edge Field strength changes the rendered output.");
  await afterRender(page, () => page.locator("#resetButton").click());
  assert.equal(await wholeCanvasHash(page), defaultWholeGlow, "Reset restores the default edge-field render.");

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

  await setControl(page, "#outerWidth", 52);
  assert.equal(await page.locator("#outerWidthValue").innerText(), "52 px", "Outer Bloom readout tracks the live range value.");
  const wideBloom = await canvasStats(page);
  assert.notEqual(wideBloom.hash, afterLayerReset.hash, "Outer Bloom width changes rendered pixels.");
  await setControl(page, "#cometLength", 26);
  const longComet = await canvasStats(page);
  assert.notEqual(longComet.hash, wideBloom.hash, "Static Comet arc length changes rendered pixels.");
  await setControl(page, "#cometPosition", 64);
  const movedComet = await canvasStats(page);
  assert.notEqual(movedComet.hash, longComet.hash, "Static Comet path position changes rendered pixels.");

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

  await afterRender(page, () => page.locator("#resetButton").click());
  await page.waitForFunction(() => document.getElementById("compareMode").value === "side" && document.getElementById("outerWidth").value === "28");
  const resetGlow = await canvasStats(page);
  assert.equal(resetGlow.hash, defaultGlow.hash, "Reset restores the deterministic default render.");

  await page.screenshot({ path: path.join(outputDir, "EdgeGlow_Fish_Static_Full_Page.png"), fullPage: true });
  for (const [id, filename] of [["originalCanvas", "01_Original_Photo.png"], ["contourCanvas", "02_Manual_Contour_Overlay.png"], ["glowCanvas", "03_EdgeGlow_Adapted.png"]]) {
    await page.locator(`#${id}`).screenshot({ path: path.join(outputDir, filename) });
  }
  const screenshotBeforeExport = await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
  const expectedPng = Buffer.from(screenshotBeforeExport.split(",")[1], "base64");
  const pngDownload = page.waitForEvent("download", { timeout: 15000 });
  await page.locator("#exportPngButton").click();
  const downloadedPng = await pngDownload;
  const pngPath = path.join(outputDir, "EdgeGlowView_Fish_Static_Current_Render.png");
  await downloadedPng.saveAs(pngPath);
  const pngBytes = await fs.readFile(pngPath);
  assert.equal(pngBytes.subarray(0, 8).toString("hex"), "89504e470d0a1a0a", "PNG export has a valid signature.");
  assert.equal(pngBytes.readUInt32BE(16), 1152);
  assert.equal(pngBytes.readUInt32BE(20), 1536);
  assert.ok(pngBytes.length > 100000, "PNG export contains the rendered photograph and glow layers.");
  assert.deepEqual(pngBytes, expectedPng, "Exported PNG bytes are the current visible canvas encoding.");

  const jsonDownload = page.waitForEvent("download", { timeout: 15000 });
  await page.locator("#exportJsonButton").click();
  const downloadedJson = await jsonDownload;
  const jsonPath = path.join(outputDir, "EdgeGlowView_Fish_Static_Parameters.json");
  await downloadedJson.saveAs(jsonPath);
  const exportConfig = JSON.parse(await fs.readFile(jsonPath, "utf8"));
  assert.equal(exportConfig.photo.sha256, expectedPhotoSha);
  assert.equal(exportConfig.contour.source, "MANUAL_REFERENCE_CONTOUR");
  assert.equal(exportConfig.parameters.cometPosition, 21);
  assert.deepEqual(exportConfig.edge_field.paths.map(path => path.id), ["B1", "B3", "G1", "G3", "B2", "G2", "B4"]);
  assert.deepEqual(exportConfig.edge_field.secondary_path_ids, ["B2", "G2", "B4"]);
  assert.deepEqual(exportConfig.edge_field.receiving_alpha_per_pass, [0.66, 0.50], "The Web-only corner-light candidate is explicit and separately tracked under RP-A01.");
  assert.match(exportConfig.edge_field.edge_effect_compositor, /padded transparent offscreen layer/);
  assert.ok(exportConfig.edge_field.edge_effect_padding_px >= 100, "Export records the responsive perimeter bleed inset.");
  assert.equal(exportConfig.parameters.edgeFieldEnabled, true);

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
    if (viewport.width === 390) await page.screenshot({ path: path.join(outputDir, "04_Responsive_390x844.png"), fullPage: true });
  }

  assert.deepEqual(errors, [], "No new browser page or console errors.");
  const report = {
    task_id: "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
    run_id: runId,
    status: "PASS_ENGINEERING_INTERACTION",
    visual_quality: "REVIEW_REQUIRED",
    android_runtime: "NOT_RUN",
    cloud_route_url: page.url(),
    pr_source_sha: identity.buildInfo.pr_source_sha,
    acceptance_center_source_sha: identity.buildInfo.acceptance_center_source_sha,
    source_photo_sha256: expectedPhotoSha,
    source_photo_dimensions: [1152, 1536],
    contour_source: "MANUAL_REFERENCE_CONTOUR",
    path_length_photo_px: identity.pathLength,
    contour_bounds_photo_px: identity.contourBounds,
    edge_effect_padding_px: identity.edgeFieldInset,
    edge_field_paths: identity.edgeFieldPaths.map(({ id, family, widthDp, seed, visibleRatio, weight, length }) => ({ id, family, widthDp, seed, visibleRatio, weight, length })),
    perimeter_corner_delta: perimeterCorners,
    effect_bounds_photo_px: await page.evaluate(() => window.__EDGEGLOW_LAB__.effectBounds),
    default_pixel_hash: defaultGlow.hash,
    toggles_and_parameters_change_pixels: true,
    receiving_light_and_primary_edge_field_toggles: true,
    compare_modes: ["side", "overlay", "wipe"],
    png_export: { bytes: pngBytes.length, dimensions: [pngBytes.readUInt32BE(16), pngBytes.readUInt32BE(20)], downloaded: true },
    json_export: { downloaded: true, photo_sha256: exportConfig.photo.sha256 },
    responsive: viewportResults,
    browser_errors: errors,
    build_label: identity.buildLabel,
    artifacts: ["01_Original_Photo.png", "02_Manual_Contour_Overlay.png", "03_EdgeGlow_Adapted.png", "EdgeGlowView_Fish_Static_Current_Render.png", "EdgeGlowView_Fish_Static_Parameters.json", "04_Responsive_390x844.png"]
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
