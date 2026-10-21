#!/usr/bin/env node
"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const REPO = path.resolve(process.env.PR_SOURCE_ROOT || process.cwd());
const OUTPUT = path.resolve(process.env.OPTICAL_EVIDENCE_DIR || path.join(REPO, "receiving-light-edge-ribbon-review-v1"));
const SITE_BASE = process.env.EDGEGLOW_SITE_BASE || "http://127.0.0.1:8765/";
const SOURCE_SHA = process.env.PR_SOURCE_SHA || "LOCAL_UNBOUND";
const CENTER_SHA = process.env.CENTER_SOURCE_SHA || "";
const ACCEPTANCE_PATH = "design/manager/nh01_acceptance/edgeglow-fish-static/";
const PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const VIEWPORTS = [[390, 844], [320, 640], [1080, 1920]];
const PLAYWRIGHT_DIR = process.env.PLAYWRIGHT_MODULE_DIR;
if (!PLAYWRIGHT_DIR) throw new Error("PLAYWRIGHT_MODULE_DIR is required.");
const { chromium } = require(path.join(PLAYWRIGHT_DIR, "playwright"));

const files = [];
const sha256 = data => crypto.createHash("sha256").update(data).digest("hex");
const pngBuffer = dataUrl => Buffer.from(dataUrl.slice(dataUrl.indexOf(",") + 1), "base64");

function savePng(file, dataUrl, viewport) {
  const buffer = pngBuffer(dataUrl);
  assert.equal(buffer.subarray(0, 8).toString("hex"), "89504e470d0a1a0a", file + " must be a PNG");
  fs.writeFileSync(path.join(OUTPUT, file), buffer);
  const item = { file, bytes: buffer.length, sha256: sha256(buffer) };
  if (viewport) item.viewport = viewport;
  files.push(item);
  return item;
}

function attachErrors(page, label, errors) {
  page.on("pageerror", error => errors.push(label + " pageerror: " + error.message));
  page.on("console", message => {
    if (message.type() === "error") errors.push(label + " console: " + message.text());
  });
  page.on("requestfailed", request => errors.push(label + " requestfailed: " + request.url()));
}

async function scriptRecord(page, needle, localPath) {
  const record = await page.evaluate(async text => {
    const script = [...document.scripts].find(item => item.src.includes(text));
    if (!script) throw new Error("Required script not loaded: " + text);
    const response = await fetch(script.src, { cache: "no-store" });
    if (!response.ok) throw new Error("Script fetch failed: " + script.src);
    return { url: script.src, source: await response.text() };
  }, needle);
  const fetched = Buffer.from(record.source, "utf8");
  const local = fs.readFileSync(localPath);
  assert.equal(sha256(fetched), sha256(local), needle + " bytes must match the captured source tree");
  return { url: record.url, sha256: sha256(fetched) };
}

async function waitForFormal(page) {
  await page.waitForFunction(() =>
    document.querySelector("#sourceStatus")?.dataset.status === "PASS" &&
    window.__EDGEGLOW_LAB__?.photoDimensions?.[0] === 1152 &&
    window.EdgeGlowReceivingLight?.version === "edgeglow-receiving-light-svg-v1",
  null, { timeout: 30000 });
  // build-info.json is verified immediately after this wait; do not gate on display-label text.
}

async function setControl(page, id, value) {
  await page.locator("#" + id).evaluate((node, next) => {
    if (node.type === "checkbox") node.checked = Boolean(next);
    else node.value = String(next);
    node.dispatchEvent(new Event("input", { bubbles: true }));
    node.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
}

async function pageIdentity(page) {
  return page.evaluate(async () => {
    const build = await fetch("./build-info.json", { cache: "no-store" }).then(response => response.json());
    return {
      build,
      photo_sha256: document.querySelector("#photoSha")?.textContent.trim(),
      photo_source: document.querySelector('meta[name="edgeglow-photo-sha256"]')?.content,
      photo_dimensions: window.__EDGEGLOW_LAB__.photoDimensions,
      viewport: [innerWidth, innerHeight],
      paths: window.__EDGEGLOW_LAB__.edgeFieldPaths.filter(item => ["B1", "G1", "G3", "B3"].includes(item.id))
    };
  });
}

async function captureVariant(browser, variant, viewport, errors) {
  const [width, height] = viewport;
  const prefix = variant.id.toLowerCase() + "-" + width + "x" + height;
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 });
  attachErrors(page, prefix, errors);
  await page.addInitScript(isCandidate => {
    window.__EDGEGLOW_ALPHA_EXPERIMENT__ = false;
    window.__EDGEGLOW_RADIAL_FIELD_EXPERIMENT__ = false;
    window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ = isCandidate;
    window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__ = [];
    const nativeDrawImage = CanvasRenderingContext2D.prototype.drawImage;
    CanvasRenderingContext2D.prototype.drawImage = function(source, ...args) {
      if (source instanceof HTMLCanvasElement && args.length === 8 &&
          this.canvas.width === 1152 && this.canvas.height === 1536) {
        const [sx, sy, sw, sh, dx, dy, dw, dh] = args;
        const captured = document.createElement("canvas");
        captured.width = dw;
        captured.height = dh;
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
  }, variant.id === "CANDIDATE");

  try {
    const url = new URL(ACCEPTANCE_PATH, SITE_BASE).href;
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    await waitForFormal(page);
    const identity = await pageIdentity(page);
    assert.equal(identity.build.pr_source_sha, SOURCE_SHA, "formal page must use exact tested source SHA");
    assert.equal(identity.build.acceptance_center_source_sha, CENTER_SHA, "formal page must use exact Acceptance Center SHA");
    assert.equal(identity.photo_sha256, PHOTO_SHA);
    assert.equal(identity.photo_source, PHOTO_SHA);
    assert.deepEqual(identity.photo_dimensions, [1152, 1536]);

    const formalScript = await scriptRecord(page, "edgeglow.js",
      path.join(REPO, "design/manager/nh01_acceptance/edgeglow-fish-static/edgeglow.js"));
    const rendererScript = await scriptRecord(page, "edgeglow-receiving-light.js",
      path.join(REPO, "design/manager/edgeglow-receiving-light.js"));

    await page.locator('[data-optical-preset="current"]').click();
    for (const [id, value] of Object.entries({
      edgeFieldEnabled: true, receivingLightEnabled: true, edgeFieldIntensity: 100,
      primaryIslandsEnabled: false, secondaryFragmentsEnabled: false,
      companionHairlinesEnabled: false, outerEnabled: false, coreEnabled: false,
      cometEnabled: false, contourVisible: false
    })) await setControl(page, id, value);
    let renderInvocation;
    try {
      renderInvocation = await page.evaluate(() => {
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.length = 0;
        try {
          window.__EDGEGLOW_LAB__.render();
          return { error: null, status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null };
        } catch (error) {
          return { error: String(error && (error.stack || error.message) || error), status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null };
        }
      });
      await page.waitForFunction(() =>
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length > 0 ||
        ["failed", "disabled"].includes(window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status),
      null, { timeout: 12000 });
    } catch (error) {
      const runtime = await page.evaluate(() => ({
        status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null,
        captured_draw_calls: window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length || 0
      })).catch(() => null);
      throw new Error("Edge-ribbon renderer did not become ready. " + JSON.stringify({
        render_invocation: renderInvocation || null,
        runtime,
        browser_errors: errors.slice(-20),
        wait_error: String(error)
      }));
    }
    const runtime = await page.evaluate(() => ({
      status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null,
      captured_draw_calls: window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length || 0
    }));
    if (renderInvocation?.error || runtime.status?.status !== "ready" || runtime.captured_draw_calls < 1) {
      throw new Error("Edge-ribbon renderer failed. " + JSON.stringify({
        render_invocation: renderInvocation || null,
        runtime,
        browser_errors: errors.slice(-20)
      }));
    }

    const overlay = await page.evaluate(() => {
      const call = window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__[0];
      const canvas = call.canvas;
      const rgba = canvas.getContext("2d", { willReadFrequently: true }).getImageData(0, 0, canvas.width, canvas.height).data;
      const islands = {
        B1: { left: 0.73, top: 0.00, right: 1.00, bottom: 0.43 },
        G1: { left: 0.00, top: 0.00, right: 0.33, bottom: 0.30 },
        G3: { left: 0.00, top: 0.68, right: 0.41, bottom: 1.00 },
        B3: { left: 0.59, top: 0.57, right: 1.00, bottom: 1.00 }
      };
      const perIsland = Object.fromEntries(Object.keys(islands).map(id => [id, { nonzero_pixels: 0, peak_alpha_8bit: 0 }]));
      const minDimension = Math.min(canvas.width, canvas.height);
      const depthPx = 0.18 * minDimension;
      const outerSigma = depthPx * 0.0857;
      const quietDepthPx = window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ === true
        ? depthPx + 3 * Math.max(2, minDimension * 0.0065) * 1.55
        : Math.min(minDimension * 0.18, depthPx / 2 + outerSigma * 3);
      const quietRegions = {
        Q_TOP: [Math.ceil(0.32 * canvas.width) + 2, 2, Math.floor(0.68 * canvas.width) - 2, Math.floor(quietDepthPx) - 2],
        Q_LEFT: [2, Math.ceil(0.38 * canvas.height) + 2, Math.floor(quietDepthPx) - 2, Math.floor(0.62 * canvas.height) - 2],
        Q_RIGHT: [Math.ceil(canvas.width - quietDepthPx) + 2, Math.ceil(0.46 * canvas.height) + 2, canvas.width - 2, Math.floor(0.58 * canvas.height) - 2],
        Q_BOTTOM: [Math.ceil(0.40 * canvas.width) + 2, Math.ceil(canvas.height - quietDepthPx) + 2, Math.floor(0.62 * canvas.width) - 2, canvas.height - 2]
      };
      const quietGapChecks = {};
      for (const [id, [left, top, right, bottom]] of Object.entries(quietRegions)) {
        let nonzeroPixels = 0, peakAlpha = 0;
        for (let y = top; y < bottom; y++) for (let x = left; x < right; x++) {
          const alpha = rgba[(y * canvas.width + x) * 4 + 3];
          if (alpha) nonzeroPixels++;
          if (alpha > peakAlpha) peakAlpha = alpha;
        }
        quietGapChecks[id] = { checked_rect_px: [left, top, right, bottom], nonzero_pixels: nonzeroPixels, peak_alpha_8bit: peakAlpha };
      }
      let nonzero = 0, peak = 0, minX = canvas.width, minY = canvas.height, maxX = -1, maxY = -1;
      for (let y = 0; y < canvas.height; y++) for (let x = 0; x < canvas.width; x++) {
        const alpha = rgba[(y * canvas.width + x) * 4 + 3];
        if (!alpha) continue;
        nonzero++;
        if (alpha > peak) peak = alpha;
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        for (const [id, roi] of Object.entries(islands)) {
          if (x >= roi.left * canvas.width && x < roi.right * canvas.width &&
              y >= roi.top * canvas.height && y < roi.bottom * canvas.height) {
            perIsland[id].nonzero_pixels++;
            if (alpha > perIsland[id].peak_alpha_8bit) perIsland[id].peak_alpha_8bit = alpha;
          }
        }
      }
      return {
        png: canvas.toDataURL("image/png"),
        size: [canvas.width, canvas.height],
        source_size: call.source_size, source_rect: call.source_rect, destination_rect: call.destination_rect,
        alpha: {
          nonzero_pixels: nonzero, peak_alpha_8bit: peak,
          bbox: [minX, minY, maxX + 1, maxY + 1],
          bbox_area_fraction: nonzero ? ((maxX - minX + 1) * (maxY - minY + 1)) / (canvas.width * canvas.height) : 0,
          canvas_area_fraction: nonzero / (canvas.width * canvas.height)
        },
        per_island: perIsland,
        quiet_gap_checks: quietGapChecks,
        renderer_status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__
      };
    });

    const expectedMode = variant.id === "CANDIDATE" ? "REVIEW_ONLY_EDGE_ANCHORED_RIBBON" : "P1";
    const expectedBase = 0.07;
    const expectedCap = 17;
    assert.equal(overlay.renderer_status.alpha_mode, expectedMode, "renderer must report the requested alpha mode");
    const expectedGeometry = variant.id === "CANDIDATE" ? "EDGE_ANCHORED_RIBBON_FIELD" : "PATH_STROKE";
    assert.equal(overlay.renderer_status.geometry_mode, expectedGeometry, "renderer must report the requested geometry mode");
    assert.equal(overlay.renderer_status.base_alpha, expectedBase, "renderer must report the requested base alpha");
    assert.equal(Math.round(overlay.renderer_status.alpha_cap * 255), expectedCap, "renderer must report the requested output alpha cap");
    assert.ok(overlay.alpha.nonzero_pixels > 0, "Receiving Light layer must render");
    if (variant.id === "CANDIDATE") {
      assert.ok(overlay.alpha.canvas_area_fraction > 0.17, "continuous edge ribbon must cover a broad perimeter band");
    }
    assert.ok(overlay.alpha.peak_alpha_8bit <= expectedCap, "actual PNG alpha must respect its recorded cap");
    for (const [id, stats] of Object.entries(overlay.per_island)) assert.ok(stats.nonzero_pixels > 0, "P1 island missing: " + id);
    for (const [id, stats] of Object.entries(overlay.quiet_gap_checks)) assert.equal(stats.peak_alpha_8bit, 0, "Quiet Gap must remain clear: " + id);

    const overlayName = variant.id.toLowerCase() + "-receiving-light-transparent-1152x1536-" + width + "x" + height + ".png";
    const overlayFile = savePng(overlayName, overlay.png, viewport);
    const overPhotoData = await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    const overPhotoName = variant.id.toLowerCase() + "-receiving-light-over-photo-1152x1536-" + width + "x" + height + ".png";
    const overPhotoFile = savePng(overPhotoName, overPhotoData, viewport);

    const pixelSignal = await page.evaluate(() => {
      const width = 1152, height = 1536;
      const source = document.querySelector("#originalCanvas").getContext("2d", { willReadFrequently: true }).getImageData(0, 0, width, height).data;
      const output = document.querySelector("#glowCanvas").getContext("2d", { willReadFrequently: true }).getImageData(0, 0, width, height).data;
      const regions = {
        bright_sky: { left: 0.76, top: 0.04, right: 0.97, bottom: 0.20 },
        dark_water: { left: 0.78, top: 0.70, right: 0.97, bottom: 0.92 }
      };
      const stats = Object.fromEntries(Object.keys(regions).map(id => [id, { sampled_pixels: 0, changed_sample_pixels: 0, absolute_rgb_delta_sum: 0 }]));
      let sum = 0, changed = 0, centerSum = 0, centerCount = 0;
      for (let y = 0; y < height; y += 4) for (let x = 0; x < width; x += 4) {
        const offset = (y * width + x) * 4;
        const delta = Math.abs(source[offset] - output[offset]) + Math.abs(source[offset + 1] - output[offset + 1]) + Math.abs(source[offset + 2] - output[offset + 2]);
        sum += delta;
        if (delta) changed++;
        for (const [id, region] of Object.entries(regions)) {
          if (x >= region.left * width && x < region.right * width && y >= region.top * height && y < region.bottom * height) {
            stats[id].sampled_pixels++;
            stats[id].absolute_rgb_delta_sum += delta;
            if (delta) stats[id].changed_sample_pixels++;
          }
        }
        if (x > 0.34 * width && x < 0.66 * width && y > 0.34 * height && y < 0.66 * height) {
          centerSum += delta;
          centerCount++;
        }
      }
      const samples = Math.ceil(width / 4) * Math.ceil(height / 4);
      return {
        sampled_pixels: samples,
        changed_sample_pixels: changed,
        mean_abs_rgb_delta_sampled: sum / (samples * 3),
        center_subject_mean_abs_rgb_delta_sampled: centerSum / Math.max(1, centerCount * 3),
        background_regions: Object.fromEntries(Object.entries(stats).map(([id, value]) => [id, {
          sampled_pixels: value.sampled_pixels,
          changed_sample_pixels: value.changed_sample_pixels,
          mean_abs_rgb_delta_sampled: value.absolute_rgb_delta_sum / Math.max(1, value.sampled_pixels * 3),
          source_region: regions[id]
        }]))
      };
    });
    assert.equal(pixelSignal.center_subject_mean_abs_rgb_delta_sampled, 0, "Receiving Light must leave the center photo unchanged");

    await page.locator("#glowCanvas").evaluate(canvas => canvas.scrollIntoView({ block: "center", inline: "center" }));
    const viewportFile = variant.id.toLowerCase() + "-static-receiving-" + width + "x" + height + ".png";
    const screenshot = await page.screenshot({ path: path.join(OUTPUT, viewportFile), fullPage: false });
    files.push({ file: viewportFile, bytes: screenshot.length, sha256: sha256(screenshot), viewport });
    const canvasFile = variant.id.toLowerCase() + "-canvas-receiving-" + width + "x" + height + ".png";
    const canvasScreenshot = await page.locator("#glowCanvas").screenshot({ path: path.join(OUTPUT, canvasFile), animations: "disabled" });
    files.push({ file: canvasFile, bytes: canvasScreenshot.length, sha256: sha256(canvasScreenshot), viewport });

    return {
      variant: variant.id,
      parameters: {
        base_alpha: expectedBase,
        output_alpha_cap_8bit: expectedCap,
        depth_ratio: 0.18,
        paths: ["B1", "G1", "G3", "B3"],
        path_geometry_sha256: sha256(Buffer.from(JSON.stringify(identity.paths))),
        receiving_geometry: variant.id === "CANDIDATE" ? "eight perimeter segments split around frozen Quiet Gaps; path-stroke masks bypassed in review-only mode" : "original P1 path-stroke masks and blur layers",
        edge_ribbon_geometry: variant.id === "CANDIDATE" ? {
          anchor: "photoContentRect perimeter",
          inward_depth: "side-normal offset up to frozen 18% depth ratio; tapered at segment ends",
          geometry: "eight edge segments split around the frozen Quiet Gaps; independent of corner-path coverage",
          color_gradient: "blue/gold perimeter color with broad plateau and smooth inward fade",
          feather: "local Gaussian blur sigma max(2 px, 0.0065 x minDimension), followed by post-cap blur at 0.55x sigma; Quiet Gap mask is applied after the blur",
          segment_taper: "three-sample geometric taper at visible interval endpoints"
        } : "not applicable to P1 path-stroke baseline",
        colors: "unchanged",
        quiet_gaps: "unchanged",
        photo_mapping: "unchanged photoContentRect",
        composite: "source-over",
        global_intensity_percent: 100
      },
      viewport,
      source_rect: { source_size: overlay.source_size, source_rect: overlay.source_rect, destination_rect: overlay.destination_rect },
      renderer_status: overlay.renderer_status,
      loaded_scripts: { formal_edgeglow: formalScript, shared_renderer: rendererScript },
      overlay: {
        file: overlayName,
        sha256: overlayFile.sha256,
        size: overlay.size,
        alpha: overlay.alpha,
        per_island: overlay.per_island,
        quiet_gap_checks: overlay.quiet_gap_checks
      },
      over_photo: {
        file: overPhotoName,
        sha256: overPhotoFile.sha256,
        pixel_signal_vs_photo: pixelSignal
      },
      screenshots: { page: viewportFile, canvas: canvasFile },
      build: identity.build,
      photo_sha256: identity.photo_sha256
    };
  } finally {
    await page.close();
  }
}

async function comparePngs(browser, baselineUrl, candidateUrl) {
  const page = await browser.newPage({ viewport: { width: 900, height: 700 }, deviceScaleFactor: 1 });
  try {
    return await page.evaluate(async ([baselineSrc, candidateSrc]) => {
      const load = src => new Promise((resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = reject;
        image.src = src;
      });
      const [a, b] = await Promise.all([load(baselineSrc), load(candidateSrc)]);
      const canvas = document.createElement("canvas");
      canvas.width = 1152;
      canvas.height = 1536;
      const context = canvas.getContext("2d", { willReadFrequently: true });
      context.drawImage(a, 0, 0);
      const aPixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(b, 0, 0);
      const bPixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
      const regions = {
        bright_sky: { left: 0.76, top: 0.04, right: 0.97, bottom: 0.20 },
        dark_water: { left: 0.78, top: 0.70, right: 0.97, bottom: 0.92 }
      };
      let sum = 0, changed = 0;
      const roi = Object.fromEntries(Object.keys(regions).map(id => [id, { sampled_pixels: 0, absolute_rgb_delta_sum: 0, changed_sample_pixels: 0 }]));
      const width = canvas.width, height = canvas.height;
      for (let y = 0; y < height; y += 4) for (let x = 0; x < width; x += 4) {
        const i = (y * width + x) * 4;
        const delta = Math.abs(aPixels[i] - bPixels[i]) + Math.abs(aPixels[i + 1] - bPixels[i + 1]) + Math.abs(aPixels[i + 2] - bPixels[i + 2]);
        sum += delta;
        if (delta) changed++;
        for (const [id, region] of Object.entries(regions)) if (x >= region.left * width && x < region.right * width && y >= region.top * height && y < region.bottom * height) {
          roi[id].sampled_pixels++;
          roi[id].absolute_rgb_delta_sum += delta;
          if (delta) roi[id].changed_sample_pixels++;
        }
      }
      const samples = Math.ceil(width / 4) * Math.ceil(height / 4);
      return {
        sampled_pixels: samples,
        changed_sample_pixels: changed,
        mean_abs_rgb_delta_candidate_vs_baseline: sum / (samples * 3),
        regions: Object.fromEntries(Object.entries(roi).map(([id, item]) => [id, {
          sampled_pixels: item.sampled_pixels,
          changed_sample_pixels: item.changed_sample_pixels,
          mean_abs_rgb_delta: item.absolute_rgb_delta_sum / Math.max(1, item.sampled_pixels * 3)
        }]))
      };
    }, [baselineUrl, candidateUrl]);
  } finally {
    await page.close();
  }
}

async function saveSideBySide(browser, baselineUrl, candidateUrl) {
  const page = await browser.newPage({ viewport: { width: 820, height: 590 }, deviceScaleFactor: 1 });
  try {
    await page.setContent(`<!doctype html><meta charset="utf-8"><style>
      *{box-sizing:border-box}body{margin:0;padding:10px;background:#e8edf2;color:#173047;font:600 14px system-ui}
      main{display:flex;gap:10px}section{width:400px;padding:5px;background:white;border-radius:8px}
      h1{font-size:14px;margin:4px 0 8px}img{display:block;width:390px;height:520px;object-fit:contain;background:#fff}
    </style><main><section><h1>BASELINE · P1 Alpha 0.07 / cap 17</h1><img id="baseline" src="${baselineUrl}"></section>
    <section><h1>REVIEW ONLY · edge-anchored ribbon field · Alpha 0.07 / cap 17</h1><img id="candidate" src="${candidateUrl}"></section></main>`);
    await page.evaluate(() => Promise.all([...document.images].map(image => image.decode())));
    const file = "baseline-vs-edge-ribbon-candidate-390x844-side-by-side.png";
    const buffer = await page.screenshot({ path: path.join(OUTPUT, file), fullPage: false });
    const item = { file, bytes: buffer.length, sha256: sha256(buffer), viewport: [820, 590], source: "side-by-side browser composition of the two real 1152x1536 Chromium PNGs" };
    files.push(item);
    return item;
  } finally {
    await page.close();
  }
}

async function main() {
  fs.mkdirSync(OUTPUT, { recursive: true });
  const errors = [];
  const browser = await chromium.launch({ headless: true });
  try {
    const baseline = await captureVariant(browser, { id: "BASELINE" }, VIEWPORTS[0], errors);
    const candidate = [];
    for (const viewport of VIEWPORTS) candidate.push(await captureVariant(browser, { id: "CANDIDATE" }, viewport, errors));
    const candidate390 = candidate[0];
    assert.equal(
      baseline.parameters.path_geometry_sha256,
      candidate390.parameters.path_geometry_sha256,
      "baseline and candidate must use identical primary path geometry"
    );
    const baselineData = "data:image/png;base64," + fs.readFileSync(path.join(OUTPUT, baseline.over_photo.file)).toString("base64");
    const candidateData = "data:image/png;base64," + fs.readFileSync(path.join(OUTPUT, candidate390.over_photo.file)).toString("base64");
    const comparison = await comparePngs(browser, baselineData, candidateData);
    const sideBySide = await saveSideBySide(browser, baselineData, candidateData);
    assert.equal(errors.length, 0, errors.join("\n"));

    const rendererBytes = fs.readFileSync(path.join(REPO, "design/manager/edgeglow-receiving-light.js"));
    const report = {
      schema_version: "1.0",
      task_id: "YUJIAN_RECEIVING_LIGHT_RADIAL_FIELD_GEOMETRY_REVIEW_V1",
      status: "REVIEW_REQUIRED",
      experiment_result: "AWAITING_HUMAN_VISUAL_ASSESSMENT",
      source_sha: SOURCE_SHA,
      acceptance_center_sha: CENTER_SHA,
      page_url: new URL(ACCEPTANCE_PATH, SITE_BASE).href,
      renderer: { version: "edgeglow-receiving-light-svg-v1", sha256: sha256(rendererBytes) },
      photo: { sha256: PHOTO_SHA, dimensions: [1152, 1536] },
      controlled_variables: {
        base_alpha: 0.07,
        output_alpha_cap_8bit: 17,
        depth_ratio: 0.18,
        primary_paths: ["B1", "G1", "G3", "B3"],
        geometry_change: "candidate uses eight perimeter segments split around Quiet Gaps, with side-normal gradients and tapered endpoints; Alpha remains fixed",
        colors: "unchanged",
        quiet_gaps: "unchanged",
        coordinate_mapping: "unchanged",
        global_intensity: 100,
        composite: "source-over"
      },
      baseline,
      candidate,
      candidate_vs_baseline_photo_rgb: comparison,
      side_by_side: sideBySide,
      artifacts: files,
      browser_errors: errors,
      visual_review: "REVIEW_REQUIRED; browser captures and pixel measurements do not substitute for visual judgment.",
      visual_pass_claimed: false,
      production_alpha_changed: false,
      pages_deployed: false,
      android_runtime: "NOT_RUN",
      native_implementation: "NOT_RUN",
      apk: "NOT_RUN"
    };
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-edge-ribbon-review-report.json"), JSON.stringify(report, null, 2) + "\n");
    const markdown = [
      "# Receiving Light Edge-Anchored Ribbon Review V1",
      "",
      "- Status: REVIEW_REQUIRED; pixel checks are evidence, not a visual PASS.",
      "- Source SHA: " + SOURCE_SHA,
      "- Acceptance Center SHA: " + CENTER_SHA,
      "- Photo SHA-256: " + PHOTO_SHA,
      "- Baseline: base Alpha 0.07, output cap 17/255, depth 18%.",
      "- Review-only candidate: eight edge-anchored perimeter segments split around Quiet Gaps, with side-normal gradients, tapered endpoints, and a small Gaussian feather; base Alpha 0.07, output cap 17/255, core depth 18%.",
      "- Only geometry changes: each visible path interval becomes one continuous perimeter-anchored polygon with edge-normal gradients and a geometric endpoint taper. Alpha, color, depth ratio, global intensity, photo mapping, Quiet Gaps and source-over remain fixed.",
      "- Baseline RGB signal vs source photo (390x844): mean absolute delta " + baseline.over_photo.pixel_signal_vs_photo.mean_abs_rgb_delta_sampled.toFixed(6) + ".",
      "- Candidate RGB signal vs source photo (390x844): mean absolute delta " + candidate390.over_photo.pixel_signal_vs_photo.mean_abs_rgb_delta_sampled.toFixed(6) + ".",
      "- Candidate-vs-baseline RGB delta (390x844): " + comparison.mean_abs_rgb_delta_candidate_vs_baseline.toFixed(6) + ".",
      "- Visual review and root-cause decision must be completed from the included real PNGs.",
      "- No Pages deployment; Android Native/API 28/APK not run.",
      "",
      "## PNG SHA-256",
      "",
      "| File | SHA-256 |",
      "| --- | --- |",
      ...files.map(file => `| ${file.file} | ${file.sha256} |`),
      ""
    ].join("\n");
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-edge-ribbon-review-report.md"), markdown);
    fs.writeFileSync(path.join(OUTPUT, "sha256-manifest.json"), JSON.stringify(files, null, 2) + "\n");
    console.log(JSON.stringify({
      result: "EDGE_RIBBON_CAPTURE_COMPLETE_REVIEW_REQUIRED",
      source_sha: SOURCE_SHA,
      photo_sha256: PHOTO_SHA,
      baseline_peak_alpha_8bit: baseline.overlay.alpha.peak_alpha_8bit,
      candidate_peaks_alpha_8bit: candidate.map(item => ({ viewport: item.viewport, peak: item.overlay.alpha.peak_alpha_8bit })),
      candidate_vs_baseline_mean_rgb_delta: comparison.mean_abs_rgb_delta_candidate_vs_baseline,
      artifact_count: files.length,
      output_dir: OUTPUT,
      visual_pass_claimed: false
    }, null, 2));
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error.stack || String(error));
  process.exitCode = 1;
});
