#!/usr/bin/env node
"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const REPO = path.resolve(process.env.PR_SOURCE_ROOT || process.cwd());
const OUTPUT = path.resolve(process.env.OPTICAL_EVIDENCE_DIR || path.join(REPO, "receiving-light-continuous-spatial-field-v1"));
const SITE_BASE = process.env.EDGEGLOW_SITE_BASE || "http://127.0.0.1:8765/";
const SOURCE_SHA = process.env.PR_SOURCE_SHA || "LOCAL_UNBOUND";
const CENTER_SHA = process.env.CENTER_SOURCE_SHA || "";
const ACCEPTANCE_PATH = "design/manager/nh01_acceptance/edgeglow-fish-static/";
const PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const VIEWPORTS = [[320, 640], [390, 844], [430, 932], [1080, 1920]];
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
  await page.addInitScript(() => {
    window.__EDGEGLOW_ALPHA_EXPERIMENT__ = false;
    window.__EDGEGLOW_RADIAL_FIELD_EXPERIMENT__ = false;
    window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ = false;
    window.__EDGEGLOW_CONTINUOUS_FIELD_EXPERIMENT__ = false;
    window.__EDGEGLOW_QUIET_GAP_VARIANT__ = "B";
    window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__ = [];
    const nativeDrawImage = CanvasRenderingContext2D.prototype.drawImage;
    CanvasRenderingContext2D.prototype.drawImage = function(source, ...args) {
      const receivingSource = source?.dataset?.edgeglowReceivingLightSource;
      const sourceWidth = source?.naturalWidth || source?.width || 0;
      const sourceHeight = source?.naturalHeight || source?.height || 0;
      if (receivingSource && sourceWidth === 1152 && sourceHeight === 1536) {
        const captured = document.createElement("canvas");
        captured.width = sourceWidth;
        captured.height = sourceHeight;
        const capturedContext = captured.getContext("2d", { willReadFrequently: true });
        nativeDrawImage.call(capturedContext, source, 0, 0, sourceWidth, sourceHeight);
        const alphaData = capturedContext.getImageData(0, 0, captured.width, captured.height).data;
        let alphaNonzeroPixels = 0;
        let alphaPeak = 0;
        for (let offset = 3; offset < alphaData.length; offset += 4) {
          const alpha = alphaData[offset];
          if (alpha > 0) alphaNonzeroPixels++;
          if (alpha > alphaPeak) alphaPeak = alpha;
        }
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.push({
          canvas: captured,
          source_kind: receivingSource,
          source_size: [sourceWidth, sourceHeight],
          source_rect: [0, 0, sourceWidth, sourceHeight],
          destination_rect: args.slice(-4),
          captured_size: [captured.width, captured.height],
          alpha_nonzero_pixels: alphaNonzeroPixels,
          alpha_peak_8bit: alphaPeak
        });
      }
      return nativeDrawImage.call(this, source, ...args);
    };
  });

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
      renderInvocation = await page.evaluate(selection => {
        window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ = false;
        window.__EDGEGLOW_CONTINUOUS_FIELD_EXPERIMENT__ = selection.continuous;
        window.__EDGEGLOW_QUIET_GAP_VARIANT__ = selection.quietGapVariant;
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.length = 0;
        try {
          window.__EDGEGLOW_LAB__.render();
          return { error: null, status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null };
        } catch (error) {
          return { error: String(error && (error.stack || error.message) || error), status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null };
        }
      }, {
        continuous: variant.mode === "CONTINUOUS",
        quietGapVariant: variant.quietGapVariant || "B"
      });
      await page.waitForFunction(() =>
        window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.some(call => call.alpha_nonzero_pixels > 0) ||
        ["failed", "disabled"].includes(window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status),
      null, { timeout: 12000 });
    } catch (error) {
      const runtime = await page.evaluate(() => ({
        status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null,
        captured_draw_calls: window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length || 0,
        captures: (window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__ || []).map(call => ({
          source_size: call.source_size,
          source_rect: call.source_rect,
          destination_rect: call.destination_rect,
          captured_size: call.captured_size,
          alpha_nonzero_pixels: call.alpha_nonzero_pixels,
          alpha_peak_8bit: call.alpha_peak_8bit
        }))
      })).catch(() => null);
      throw new Error("Continuous spatial field renderer did not become ready. " + JSON.stringify({
        render_invocation: renderInvocation || null,
        runtime,
        browser_errors: errors.slice(-20),
        wait_error: String(error)
      }));
    }
    const runtime = await page.evaluate(() => ({
      status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ || null,
      captured_draw_calls: window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length || 0,
      captures: (window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__ || []).map(call => ({
        source_size: call.source_size,
        source_rect: call.source_rect,
        destination_rect: call.destination_rect,
        captured_size: call.captured_size,
        alpha_nonzero_pixels: call.alpha_nonzero_pixels,
        alpha_peak_8bit: call.alpha_peak_8bit
      }))
    }));
    if (renderInvocation?.error || runtime.status?.status !== "ready" || runtime.captured_draw_calls < 1) {
      throw new Error("Continuous spatial field renderer failed. " + JSON.stringify({
        render_invocation: renderInvocation || null,
        runtime,
        browser_errors: errors.slice(-20)
      }));
    }

    const overlay = await page.evaluate(() => {
      const call = window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.find(item => item.alpha_nonzero_pixels > 0);
      if (!call) throw new Error("No captured Receiving Light draw contains nonzero alpha.");
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
      const quietDepthPx = window.__EDGEGLOW_CONTINUOUS_FIELD_EXPERIMENT__ === true
        ? depthPx
        : window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ === true
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
      const pixel = (x, y) => {
        const px = Math.max(0, Math.min(canvas.width - 1, Math.round(x)));
        const py = Math.max(0, Math.min(canvas.height - 1, Math.round(y)));
        const offset = (py * canvas.width + px) * 4;
        return { alpha: rgba[offset + 3], rgb: [rgba[offset], rgba[offset + 1], rgba[offset + 2]] };
      };
      const edgePoints = {
        top: (t, d) => [t * (canvas.width - 1), d],
        right: (t, d) => [canvas.width - 1 - d, t * (canvas.height - 1)],
        bottom: (t, d) => [t * (canvas.width - 1), canvas.height - 1 - d],
        left: (t, d) => [d, t * (canvas.height - 1)]
      };
      const edgeProfiles = {};
      for (const side of Object.keys(edgePoints)) {
        const values = [];
        for (let index = 0; index <= 100; index++) {
          const t = 0.02 + 0.96 * index / 100;
          values.push(pixel(...edgePoints[side](t, 1)).alpha);
        }
        edgeProfiles[side] = {
          samples: values.length,
          min_alpha: Math.min(...values),
          max_alpha: Math.max(...values),
          mean_alpha: values.reduce((sum, value) => sum + value, 0) / values.length,
          range_alpha: Math.max(...values) - Math.min(...values),
          nonzero_samples: values.filter(value => value > 0).length
        };
      }
      const inwardPositions = {
        top: [0.22, 0.84], right: [0.16, 0.84],
        bottom: [0.22, 0.84], left: [0.16, 0.84]
      };
      const inwardDistances = [0, 0.03, 0.06, 0.10, 0.18].map(value => value * minDimension);
      const inwardProfiles = {};
      for (const [side, positions] of Object.entries(inwardPositions)) {
        inwardProfiles[side] = positions.map(t => {
          const samples = inwardDistances.map(distance => pixel(...edgePoints[side](t, distance)).alpha);
          return {
            edge_position: t,
            distances_px: inwardDistances,
            alpha: samples,
            monotonic_nonincreasing: samples.every((value, index) => index === 0 || value <= samples[index - 1])
          };
        });
      }
      const cornerCoordinates = {
        top_right: (offset, depth) => [[canvas.width - offset, depth], [canvas.width - depth, offset]],
        bottom_right: (offset, depth) => [[canvas.width - depth, canvas.height - offset], [canvas.width - offset, canvas.height - depth]],
        bottom_left: (offset, depth) => [[offset, canvas.height - depth], [depth, canvas.height - offset]],
        top_left: (offset, depth) => [[depth, offset], [offset, depth]]
      };
      const cornerOffsets = Array.from({ length: 32 }, (_, index) => index + 1);
      const cornerDepths = [2, 12, 36];
      const cornerContinuity = Object.fromEntries(Object.entries(cornerCoordinates).map(([id, coordinates]) => {
        const samples = [];
        for (const depth of cornerDepths) for (const offset of cornerOffsets) {
          const [pointA, pointB] = coordinates(offset, depth);
          const a = pixel(pointA[0], pointA[1]);
          const b = pixel(pointB[0], pointB[1]);
          samples.push({
            offset_px: offset, inward_depth_px: depth,
            adjacent_alpha: [a.alpha, b.alpha],
            alpha_delta: Math.abs(a.alpha - b.alpha),
            rgb_delta_max_channel: Math.max(...a.rgb.map((value, index) => Math.abs(value - b.rgb[index])))
          });
        }
        return [id, {
          sample_count: samples.length,
          offsets_px: cornerOffsets,
          inward_depths_px: cornerDepths,
          max_alpha_delta: Math.max(...samples.map(item => item.alpha_delta)),
          mean_alpha_delta: samples.reduce((sum, item) => sum + item.alpha_delta, 0) / samples.length,
          max_rgb_delta_channel: Math.max(...samples.map(item => item.rgb_delta_max_channel)),
          profiles: samples
        }];
      }));
      const quietBoundaryProfiles = {};
      const gapDefs = [
        ["Q_TOP", "top"], ["Q_RIGHT", "right"], ["Q_BOTTOM", "bottom"], ["Q_LEFT", "left"]
      ];
      for (const [id, side] of gapDefs) {
        const [start, end] = {
          Q_TOP: [0.32, 0.68], Q_RIGHT: [0.46, 0.58],
          Q_BOTTOM: [0.40, 0.62], Q_LEFT: [0.38, 0.62]
        }[id];
        const points = [start - 0.04, start - 0.02, start, (start + end) / 2, end, end + 0.02, end + 0.04];
        quietBoundaryProfiles[id] = points.map(t => ({
          edge_position: t,
          alpha_at_2px: pixel(...edgePoints[side](t, 2)).alpha,
          alpha_at_6pct_depth: pixel(...edgePoints[side](t, 0.06 * minDimension)).alpha
        }));
      }
      let nonzero = 0, peak = 0, alphaSum = 0;
      const alphaHistogram = new Uint32Array(256);
      let minX = canvas.width, minY = canvas.height, maxX = -1, maxY = -1;
      for (let y = 0; y < canvas.height; y++) for (let x = 0; x < canvas.width; x++) {
        const alpha = rgba[(y * canvas.width + x) * 4 + 3];
        alphaHistogram[alpha]++;
        alphaSum += alpha;
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
      const alphaPercentile = percentile => {
        const target = Math.ceil(nonzero * percentile);
        let cumulative = 0;
        for (let value = 0; value < alphaHistogram.length; value++) {
          cumulative += alphaHistogram[value];
          if (cumulative >= alphaHistogram[0] + target) return value;
        }
        return 0;
      };
      return {
        png: canvas.toDataURL("image/png"),
        size: [canvas.width, canvas.height],
        source_size: call.source_size, source_rect: call.source_rect, destination_rect: call.destination_rect,
        alpha: {
          nonzero_pixels: nonzero, peak_alpha_8bit: peak,
          mean_alpha_all_pixels: alphaSum / (canvas.width * canvas.height),
          mean_alpha_nonzero_pixels: nonzero ? alphaSum / nonzero : 0,
          p50_alpha_nonzero_pixels: alphaPercentile(0.50),
          p90_alpha_nonzero_pixels: alphaPercentile(0.90),
          p99_alpha_nonzero_pixels: alphaPercentile(0.99),
          cap_alpha_pixels: alphaHistogram[17],
          cap_alpha_fraction_of_nonzero: nonzero ? alphaHistogram[17] / nonzero : 0,
          bbox: [minX, minY, maxX + 1, maxY + 1],
          bbox_area_fraction: nonzero ? ((maxX - minX + 1) * (maxY - minY + 1)) / (canvas.width * canvas.height) : 0,
          canvas_area_fraction: nonzero / (canvas.width * canvas.height)
        },
        per_island: perIsland,
        quiet_gap_checks: quietGapChecks,
        edge_profiles: edgeProfiles,
        inward_profiles: inwardProfiles,
        corner_continuity: cornerContinuity,
        quiet_boundary_profiles: quietBoundaryProfiles,
        renderer_status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__
      };
    });

    const isContinuous = variant.mode === "CONTINUOUS";
    const expectedMode = isContinuous ? "REVIEW_ONLY_PERIMETER_CONTINUOUS_FIELD" : "P1";
    const expectedBase = 0.07;
    const expectedCap = 17;
    assert.equal(overlay.renderer_status.alpha_mode, expectedMode, "renderer must report the requested alpha mode");
    const expectedGeometry = isContinuous ? "PERIMETER_ANCHORED_CONTINUOUS_FIELD" : "PATH_STROKE";
    assert.equal(overlay.renderer_status.geometry_mode, expectedGeometry, "renderer must report the requested geometry mode");
    assert.equal(overlay.renderer_status.base_alpha, expectedBase, "renderer must report the requested base alpha");
    assert.equal(Math.round(overlay.renderer_status.alpha_cap * 255), expectedCap, "renderer must report the requested output alpha cap");
    assert.ok(overlay.alpha.nonzero_pixels > 0, "Receiving Light layer must render");
    if (isContinuous) {
      assert.ok(overlay.alpha.canvas_area_fraction > 0.17, "continuous spatial field must cover a broad perimeter area");
    }
    assert.ok(overlay.alpha.peak_alpha_8bit <= expectedCap, "actual PNG alpha must respect its recorded cap");
    for (const [id, stats] of Object.entries(overlay.per_island)) assert.ok(stats.nonzero_pixels > 0, "P1 island missing: " + id);
    if (variant.quietGapVariant !== "C") {
      for (const [id, stats] of Object.entries(overlay.quiet_gap_checks)) assert.equal(stats.peak_alpha_8bit, 0, "Quiet Gap center must remain clear: " + id);
    } else {
      for (const [id, stats] of Object.entries(overlay.quiet_gap_checks)) assert.ok(stats.nonzero_pixels > 0, "Low-level continuous variant must reach Quiet Gap: " + id);
    }

    const overlayName = variant.id.toLowerCase() + "-receiving-light-transparent-1152x1536-" + width + "x" + height + ".png";
    const overlayFile = savePng(overlayName, overlay.png, viewport);
    const overPhotoData = await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    const overPhotoName = variant.id.toLowerCase() + "-receiving-light-over-photo-1152x1536-" + width + "x" + height + ".png";
    const overPhotoFile = savePng(overPhotoName, overPhotoData, viewport);
    let sourcePhotoFile = null;
    if (!fs.existsSync(path.join(OUTPUT, "source-photo-1152x1536.png"))) {
      const sourcePhotoData = await page.locator("#originalCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
      sourcePhotoFile = savePng("source-photo-1152x1536.png", sourcePhotoData);
    }

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
        receiving_geometry: isContinuous
          ? "perimeter-anchored deterministic 2D field derived from existing primary curves, weights, seeds and path coverage"
          : "original P1 path-stroke masks and blur layers",
        continuous_field: isContinuous ? {
          version: overlay.renderer_status.field_version,
          anchors: overlay.renderer_status.field_anchors,
          profile: "E(s)=0.035+sum(weight*coverage*wrapped-Gaussian(s,mu,sigma)); renderEnergy=0.035+0.965*((E-0.035)/0.965)^0.52; local inward attenuation is a 3-Gaussian profile with energy-dependent width",
          spatial_parameters: overlay.renderer_status.field_profile || null,
          photo_depth_px: overlay.renderer_status.field_depth_px
        } : null,
        quiet_gap_variant: variant.quietGapVariant,
        colors: "unchanged",
        quiet_gaps: "frozen interval centers and bounds; A hard, B edge-feathered, C 12% low-level floor",
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
        quiet_gap_checks: overlay.quiet_gap_checks,
        edge_profiles: overlay.edge_profiles,
        inward_profiles: overlay.inward_profiles,
        corner_continuity: overlay.corner_continuity,
        quiet_boundary_profiles: overlay.quiet_boundary_profiles
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
      const difference = context.createImageData(canvas.width, canvas.height);
      const regions = {
        bright_sky: { left: 0.76, top: 0.04, right: 0.97, bottom: 0.20 },
        dark_water: { left: 0.78, top: 0.70, right: 0.97, bottom: 0.92 }
      };
      let sum = 0, changed = 0;
      const roi = Object.fromEntries(Object.keys(regions).map(id => [id, { sampled_pixels: 0, absolute_rgb_delta_sum: 0, changed_sample_pixels: 0 }]));
      const width = canvas.width, height = canvas.height;
      for (let offset = 0; offset < aPixels.length; offset += 4) {
        difference.data[offset] = Math.min(255, Math.abs(aPixels[offset] - bPixels[offset]) * 4);
        difference.data[offset + 1] = Math.min(255, Math.abs(aPixels[offset + 1] - bPixels[offset + 1]) * 4);
        difference.data[offset + 2] = Math.min(255, Math.abs(aPixels[offset + 2] - bPixels[offset + 2]) * 4);
        difference.data[offset + 3] = 255;
      }
      const differenceCanvas = document.createElement("canvas");
      differenceCanvas.width = width;
      differenceCanvas.height = height;
      differenceCanvas.getContext("2d").putImageData(difference, 0, 0);
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
        diff_png_data_url: differenceCanvas.toDataURL("image/png"),
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
    <section><h1>REVIEW ONLY · continuous spatial field B · Alpha 0.07 / cap 17</h1><img id="candidate" src="${candidateUrl}"></section></main>`);
    await page.evaluate(() => Promise.all([...document.images].map(image => image.decode())));
    const file = "baseline-vs-continuous-field-B-390x844-side-by-side.png";
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
    const baseline = await captureVariant(browser, { id: "BASELINE", mode: "P1", quietGapVariant: "A" }, [390, 844], errors);
    const candidate = [];
    for (const viewport of VIEWPORTS) candidate.push(await captureVariant(browser, { id: "CONTINUOUS-B", mode: "CONTINUOUS", quietGapVariant: "B" }, viewport, errors));
    const quietGapA = await captureVariant(browser, { id: "CONTINUOUS-A", mode: "CONTINUOUS", quietGapVariant: "A" }, [390, 844], errors);
    const quietGapC = await captureVariant(browser, { id: "CONTINUOUS-C", mode: "CONTINUOUS", quietGapVariant: "C" }, [390, 844], errors);
    const candidate390 = candidate.find(item => item.viewport[0] === 390 && item.viewport[1] === 844);
    assert.ok(candidate390, "390x844 continuous-field capture is required");
    for (const item of candidate) {
      assert.ok(item.overlay.alpha.peak_alpha_8bit <= 17, "continuous-field output must remain within the P1 cap");
      for (const [side, stats] of Object.entries(item.overlay.edge_profiles)) {
        assert.ok(stats.range_alpha > 0, "perimeter energy must vary along " + side);
      }
      for (const [side, samples] of Object.entries(item.overlay.inward_profiles)) {
        for (const sample of samples) assert.ok(sample.monotonic_nonincreasing, "inward falloff must not rise along " + side);
      }
    }
    for (const item of [quietGapA, candidate390, quietGapC]) {
      assert.equal(item.parameters.path_geometry_sha256, baseline.parameters.path_geometry_sha256, "Quiet Gap variants must preserve frozen path geometry");
    }
    assert.equal(
      baseline.parameters.path_geometry_sha256,
      candidate390.parameters.path_geometry_sha256,
      "baseline and candidate must use identical primary path geometry"
    );
    const baselineData = "data:image/png;base64," + fs.readFileSync(path.join(OUTPUT, baseline.over_photo.file)).toString("base64");
    const candidateData = "data:image/png;base64," + fs.readFileSync(path.join(OUTPUT, candidate390.over_photo.file)).toString("base64");
    const comparison = await comparePngs(browser, baselineData, candidateData);
    const difference = savePng("baseline-vs-continuous-field-B-390x844-difference-4x.png", comparison.diff_png_data_url, [390, 844]);
    delete comparison.diff_png_data_url;
    const sideBySide = await saveSideBySide(browser, baselineData, candidateData);
    assert.equal(errors.length, 0, errors.join("\n"));

    const rendererBytes = fs.readFileSync(path.join(REPO, "design/manager/edgeglow-receiving-light.js"));
    const report = {
      schema_version: "1.0",
      task_id: "YUJIAN_RECEIVING_LIGHT_CONTINUOUS_SPATIAL_FIELD_V1",
      status: "REVIEW_REQUIRED",
      experiment_result: "AWAITING_HUMAN_VISUAL_ASSESSMENT",
      source_sha: SOURCE_SHA,
      acceptance_center_sha: CENTER_SHA,
      page_url: new URL(ACCEPTANCE_PATH, SITE_BASE).href,
      renderer: { version: "edgeglow-receiving-light-svg-v1+perimeter-anchored-continuous-field-v1", sha256: sha256(rendererBytes) },
      photo: { sha256: PHOTO_SHA, dimensions: [1152, 1536] },
      controlled_variables: {
        base_alpha: 0.07,
        output_alpha_cap_8bit: 17,
        depth_ratio: 0.18,
        primary_paths: ["B1", "G1", "G3", "B3"],
        geometry_change: "Review-only field derives four perimeter anchor centers and spread from existing P1 cubic paths, seeds, coverage and weights; a deterministic 2D field attenuates inward with a shared perimeter coordinate",
        colors: "unchanged",
        quiet_gaps: "same frozen intervals; compare hard mask A, feathered boundaries B, and 12% low-level continuous field C",
        coordinate_mapping: "unchanged",
        global_intensity: 100,
        composite: "source-over",
        output_rendering: "deterministic Canvas ImageData, generated once per cache key; animated frames reuse cached texture"
      },
      baseline,
      candidate,
      quiet_gap_variants: { A: quietGapA, B: candidate390, C: quietGapC },
      candidate_vs_baseline_photo_rgb: comparison,
      side_by_side: sideBySide,
      difference_image: difference,
      artifacts: files,
      browser_errors: errors,
      visual_review: "REVIEW_REQUIRED; automated metrics are necessary evidence but do not substitute for visual judgment.",
      visual_pass_claimed: false,
      production_alpha_changed: false,
      pages_deployed: false,
      android_runtime: "NOT_RUN_UNTIL_BROWSER_VISUAL_GATE",
      native_implementation: "NOT_RUN",
      apk: "NOT_RUN"
    };
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-continuous-spatial-field-report.json"), JSON.stringify(report, null, 2) + "\n");
    const markdown = [
      "# Receiving Light Continuous Spatial Field Review V1",
      "",
      "- Status: REVIEW_REQUIRED; pixel checks are evidence, not a visual PASS.",
      "- Source SHA: " + SOURCE_SHA,
      "- Acceptance Center SHA: " + CENTER_SHA,
      "- Photo SHA-256: " + PHOTO_SHA,
      "- Baseline: base Alpha 0.07, output cap 17/255, depth 18%.",
      "- Review-only candidate: a cached deterministic 2D perimeter field derived from existing frozen primary curves, seeds, weights and path coverage; base Alpha 0.07, output cap 17/255, depth 18%.",
      "- Quiet Gap comparisons: A hard mask, B feathered transitions with a 2% normalized side fade, C low-level continuous floor at 12%; the frozen gap intervals are unchanged.",
      "- The field uses a wrapped perimeter coordinate, path-derived Gaussian energy nodes, family-weighted blue/gold color, and an energy-dependent three-scale inward falloff. Photo coordinates, colors, alpha, depth and source-over remain fixed.",
      "- Baseline RGB signal vs source photo (390x844): mean absolute delta " + baseline.over_photo.pixel_signal_vs_photo.mean_abs_rgb_delta_sampled.toFixed(6) + ".",
      "- Candidate RGB signal vs source photo (390x844): mean absolute delta " + candidate390.over_photo.pixel_signal_vs_photo.mean_abs_rgb_delta_sampled.toFixed(6) + ".",
      "- Candidate-vs-baseline RGB delta (390x844): " + comparison.mean_abs_rgb_delta_candidate_vs_baseline.toFixed(6) + ".",
      "- Visual review and root-cause decision must be completed from the included real PNGs.",
      "- No Pages deployment. Android Native/API 28/APK are held behind the real Chromium visual gate and are not run in this capture.",
      "",
      "## PNG SHA-256",
      "",
      "| File | SHA-256 |",
      "| --- | --- |",
      ...files.map(file => `| ${file.file} | ${file.sha256} |`),
      ""
    ].join("\n");
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-continuous-spatial-field-report.md"), markdown);
    fs.writeFileSync(path.join(OUTPUT, "sha256-manifest.json"), JSON.stringify(files, null, 2) + "\n");
    console.log(JSON.stringify({
      result: "CONTINUOUS_SPATIAL_FIELD_CAPTURE_COMPLETE_REVIEW_REQUIRED",
      source_sha: SOURCE_SHA,
      photo_sha256: PHOTO_SHA,
      baseline_peak_alpha_8bit: baseline.overlay.alpha.peak_alpha_8bit,
      candidate_peaks_alpha_8bit: candidate.map(item => ({ viewport: item.viewport, peak: item.overlay.alpha.peak_alpha_8bit })),
      quiet_gap_variant_peaks: { A: quietGapA.overlay.alpha.peak_alpha_8bit, B: candidate390.overlay.alpha.peak_alpha_8bit, C: quietGapC.overlay.alpha.peak_alpha_8bit },
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
