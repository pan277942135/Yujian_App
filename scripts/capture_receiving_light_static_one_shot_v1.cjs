#!/usr/bin/env node
"use strict";

const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const REPO = path.resolve(process.env.PR_SOURCE_ROOT || process.cwd());
const OUTPUT = path.resolve(process.env.OPTICAL_EVIDENCE_DIR || path.join(REPO, "receiving-light-static-one-shot-v1"));
const SITE_BASE = process.env.EDGEGLOW_SITE_BASE || "http://127.0.0.1:8765/";
const SOURCE_SHA = process.env.PR_SOURCE_SHA || "LOCAL_UNBOUND";
const CENTER_SHA = process.env.CENTER_SOURCE_SHA || "";
const ACCEPTANCE_PATH = "design/manager/nh01_acceptance/edgeglow-fish-static/";
const OPTICAL_PATH = ACCEPTANCE_PATH + "optical-review/";
const PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const VIEWPORTS = [[390, 844], [320, 640], [1080, 1920]];
const PLAYWRIGHT_DIR = process.env.PLAYWRIGHT_MODULE_DIR;
if (!PLAYWRIGHT_DIR) throw new Error("PLAYWRIGHT_MODULE_DIR is required.");
const { chromium } = require(path.join(PLAYWRIGHT_DIR, "playwright"));

const files = [];
const sha256 = data => crypto.createHash("sha256").update(data).digest("hex");
const dataUrlBuffer = dataUrl => Buffer.from(dataUrl.slice(dataUrl.indexOf(",") + 1), "base64");
function primaryPathGeometry(paths) {
  return paths.filter(path => ["B1", "G1", "G3", "B3"].includes(path.id)).map(path => ({
    id: path.id,
    family: path.family,
    weight: Number(path.weight ?? path.island_weight),
    seed: Number(path.seed ?? path.runtime_seed),
    visible_ratio: Number(path.visibleRatio ?? path.visible_ratio),
    start: path.start,
    control1: path.control1,
    control2: path.control2,
    end: path.end
  })).sort((a, b) => a.id.localeCompare(b.id));
}

function writePng(name, dataUrl) {
  const buffer = dataUrlBuffer(dataUrl);
  assert.equal(buffer.subarray(0, 8).toString("hex"), "89504e470d0a1a0a", name + " must be a PNG");
  fs.writeFileSync(path.join(OUTPUT, name), buffer);
  files.push({ file: name, bytes: buffer.length, sha256: sha256(buffer) });
  return buffer;
}

function attachErrors(page, label, errors) {
  page.on("pageerror", error => errors.push(label + " pageerror: " + error.message));
  page.on("console", message => {
    if (message.type() === "error") errors.push(label + " console: " + message.text());
  });
  page.on("requestfailed", request => {
    errors.push(label + " requestfailed: " + request.url() + " · " + request.failure()?.errorText);
  });
}

async function pageScriptRecord(page, needle, sourcePath) {
  const record = await page.evaluate(async text => {
    const script = [...document.scripts].find(item => item.src.includes(text));
    if (!script) throw new Error("Required loaded script not found: " + text);
    const response = await fetch(script.src, { cache: "no-store" });
    if (!response.ok) throw new Error("Script fetch failed: " + script.src + " · " + response.status);
    return { url: script.src, source: await response.text() };
  }, needle);
  const sourceBuffer = Buffer.from(record.source, "utf8");
  const localBuffer = fs.readFileSync(sourcePath);
  assert.equal(sha256(sourceBuffer), sha256(localBuffer), needle + " must match exact PR source bytes");
  return { url: record.url, sha256: sha256(sourceBuffer) };
}

async function waitForFormal(page) {
  await page.waitForFunction(() =>
    document.querySelector("#sourceStatus")?.dataset.status === "PASS" &&
    window.__EDGEGLOW_LAB__?.photoDimensions?.[0] === 1152 &&
    window.EdgeGlowReceivingLight?.version === "edgeglow-receiving-light-svg-v1",
  null, { timeout: 30000 });
  await page.waitForFunction(() =>
    window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status === "ready" &&
    document.querySelector("#buildSha")?.textContent?.includes("center"),
  null, { timeout: 30000 });
}

async function setFormalControl(page, id, value) {
  await page.locator("#" + id).evaluate((node, next) => {
    if (node.type === "checkbox") node.checked = Boolean(next);
    else node.value = String(next);
    node.dispatchEvent(new Event("input", { bubbles: true }));
    node.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
}

async function renderFormal(page) {
  await page.evaluate(() => window.__EDGEGLOW_LAB__.render());
  await page.waitForFunction(() => window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status === "ready", null, { timeout: 30000 });
}

async function formalIdentity(page) {
  return page.evaluate(async () => {
    const build = await fetch("./build-info.json", { cache: "no-store" }).then(response => response.json());
    return {
      build,
      photo_sha256: document.querySelector("#photoSha")?.textContent.trim(),
      photo_dimensions: window.__EDGEGLOW_LAB__.photoDimensions,
      photo_source: document.querySelector('meta[name="edgeglow-photo-sha256"]')?.content,
      contour_source: document.querySelector('meta[name="edgeglow-contour-source"]')?.content,
      viewport: [innerWidth, innerHeight],
      preset: window.__EDGEGLOW_LAB__.state.preset,
      primary_paths: window.__EDGEGLOW_LAB__.edgeFieldPaths.filter(path => ["B1", "G1", "G3", "B3"].includes(path.id))
    };
  });
}

async function captureReceivingOnly(browser, errors) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  attachErrors(page, "formal-isolated", errors);
  await page.addInitScript(() => {
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
  });
  try {
    const url = new URL(ACCEPTANCE_PATH, SITE_BASE).href;
    await page.goto(url, { waitUntil: "networkidle", timeout: 30000 });
    await waitForFormal(page);
    const identity = await formalIdentity(page);
    assert.equal(identity.build.pr_source_sha, SOURCE_SHA, "formal page must use exact PR source SHA");
    assert.equal(identity.build.acceptance_center_source_sha, CENTER_SHA, "formal page must use exact Acceptance Center SHA");
    assert.equal(identity.photo_sha256, PHOTO_SHA);
    assert.equal(identity.photo_source, PHOTO_SHA);
    assert.deepEqual(identity.photo_dimensions, [1152, 1536]);

    const formalScript = await pageScriptRecord(
      page, "edgeglow.js",
      path.join(REPO, "design/manager/nh01_acceptance/edgeglow-fish-static/edgeglow.js")
    );
    const sharedScript = await pageScriptRecord(
      page, "edgeglow-receiving-light.js",
      path.join(REPO, "design/manager/edgeglow-receiving-light.js")
    );

    await page.locator('[data-optical-preset="current"]').click();
    for (const [id, value] of Object.entries({
      edgeFieldEnabled: true, receivingLightEnabled: true,
      primaryIslandsEnabled: false, secondaryFragmentsEnabled: false,
      companionHairlinesEnabled: false, outerEnabled: false,
      coreEnabled: false, cometEnabled: false
    })) await setFormalControl(page, id, value);
    await page.evaluate(() => {
      window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__.length = 0;
      window.__EDGEGLOW_LAB__.render();
    });
    await page.waitForFunction(() =>
      window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__?.length > 0 &&
      window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status === "ready",
    null, { timeout: 30000 });

    const isolated = await page.evaluate(() => {
      const call = window.__EDGEGLOW_RECEIVING_CAPTURE_CALLS__[0];
      const canvas = call.canvas;
      const rgba = canvas.getContext("2d", { willReadFrequently: true }).getImageData(0, 0, canvas.width, canvas.height).data;
      let nonzero = 0, translucent = 0, peak = 0;
      const islands = {
        B1: { left: 0.73, top: 0.00, right: 1.00, bottom: 0.43 },
        G1: { left: 0.00, top: 0.00, right: 0.33, bottom: 0.30 },
        G3: { left: 0.00, top: 0.68, right: 0.41, bottom: 1.00 },
        B3: { left: 0.59, top: 0.57, right: 1.00, bottom: 1.00 }
      };
      const perIsland = {};
      for (const [id] of Object.entries(islands)) perIsland[id] = { nonzero_pixels: 0, peak_alpha_8bit: 0 };
      const minDimension = Math.min(canvas.width, canvas.height);
      const depthPx = 0.14 * minDimension;
      const outerSigma = depthPx * 0.0857;
      const quietDepthPx = Math.min(minDimension * 0.18, depthPx / 2 + outerSigma * 3);
      const quietRegions = {
        Q_TOP: [Math.ceil(0.32 * canvas.width) + 2, 2, Math.floor(0.68 * canvas.width) - 2, Math.floor(quietDepthPx) - 2],
        Q_LEFT: [2, Math.ceil(0.38 * canvas.height) + 2, Math.floor(quietDepthPx) - 2, Math.floor(0.62 * canvas.height) - 2],
        Q_RIGHT: [Math.ceil(canvas.width - quietDepthPx) + 2, Math.ceil(0.46 * canvas.height) + 2, canvas.width - 2, Math.floor(0.58 * canvas.height) - 2],
        Q_BOTTOM: [Math.ceil(0.40 * canvas.width) + 2, Math.ceil(canvas.height - quietDepthPx) + 2, Math.floor(0.62 * canvas.width) - 2, canvas.height - 2]
      };
      const quietGapChecks = {};
      for (const [id, [left, top, right, bottom]] of Object.entries(quietRegions)) {
        let nonzeroPixels = 0, peakAlpha = 0;
        for (let y = top; y < bottom; y++) {
          for (let x = left; x < right; x++) {
            const alpha = rgba[(y * canvas.width + x) * 4 + 3];
            if (alpha) nonzeroPixels++;
            if (alpha > peakAlpha) peakAlpha = alpha;
          }
        }
        quietGapChecks[id] = { checked_rect_px: [left, top, right, bottom], nonzero_pixels: nonzeroPixels, peak_alpha_8bit: peakAlpha };
      }
      for (let y = 0; y < canvas.height; y++) {
        for (let x = 0; x < canvas.width; x++) {
          const alpha = rgba[(y * canvas.width + x) * 4 + 3];
          if (!alpha) continue;
          nonzero++;
          if (alpha < 255) translucent++;
          if (alpha > peak) peak = alpha;
          for (const [id, roi] of Object.entries(islands)) {
            if (x >= roi.left * canvas.width && x < roi.right * canvas.width &&
                y >= roi.top * canvas.height && y < roi.bottom * canvas.height) {
              perIsland[id].nonzero_pixels++;
              if (alpha > perIsland[id].peak_alpha_8bit) perIsland[id].peak_alpha_8bit = alpha;
            }
          }
        }
      }
      return {
        data: canvas.toDataURL("image/png"),
        size: [canvas.width, canvas.height],
        source_size: call.source_size,
        source_rect: call.source_rect,
        destination_rect: call.destination_rect,
        alpha: { nonzero_pixels: nonzero, translucent_pixels: translucent, peak_alpha_8bit: peak },
        per_island: perIsland,
        quiet_gap_checks: quietGapChecks,
        renderer_status: window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__
      };
    });
    writePng("receiving-light-transparent-1152x1536.png", isolated.data);
    assert.ok(isolated.alpha.nonzero_pixels > 0, "isolated Receiving Light must render");
    assert.ok(isolated.alpha.translucent_pixels > 0, "isolated Receiving Light must remain translucent");
    assert.ok(isolated.alpha.peak_alpha_8bit <= 17, "actual PNG alpha must remain at or below 0.07");
    for (const [id, stats] of Object.entries(isolated.per_island)) {
      assert.ok(stats.nonzero_pixels > 0, "expected visible P1 island " + id);
    }
    for (const [id, stats] of Object.entries(isolated.quiet_gap_checks)) {
      assert.equal(stats.peak_alpha_8bit, 0, "P1 Quiet Gap must remain clear inside its inset check region: " + id);
    }

    const overPhotoData = await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    writePng("receiving-light-over-photo-1152x1536.png", overPhotoData);
    const pixelStats = await page.evaluate(() => {
      const original = document.querySelector("#originalCanvas").getContext("2d", { willReadFrequently: true });
      const output = document.querySelector("#glowCanvas").getContext("2d", { willReadFrequently: true });
      const a = original.getImageData(0, 0, 1152, 1536).data;
      const b = output.getImageData(0, 0, 1152, 1536).data;
      const regions = {
        bright_sky: { left: 0.76, top: 0.04, right: 0.97, bottom: 0.20 },
        dark_water: { left: 0.78, top: 0.70, right: 0.97, bottom: 0.92 }
      };
      const regionStats = Object.fromEntries(Object.entries(regions).map(([id]) => [id, { sampled_pixels: 0, changed_sample_pixels: 0, absolute_rgb_delta_sum: 0 }]));
      let totalDelta = 0, totalChanged = 0, interiorDelta = 0, interiorSamples = 0;
      for (let y = 0; y < 1536; y += 4) {
        for (let x = 0; x < 1152; x += 4) {
          const offset = (y * 1152 + x) * 4;
          const delta = Math.abs(a[offset] - b[offset]) + Math.abs(a[offset + 1] - b[offset + 1]) + Math.abs(a[offset + 2] - b[offset + 2]);
          totalDelta += delta;
          if (delta) totalChanged++;
          for (const [id, region] of Object.entries(regions)) {
            if (x >= region.left * 1152 && x < region.right * 1152 && y >= region.top * 1536 && y < region.bottom * 1536) {
              const stats = regionStats[id];
              stats.sampled_pixels++;
              stats.absolute_rgb_delta_sum += delta;
              if (delta) stats.changed_sample_pixels++;
            }
          }
          if (x > 0.34 * 1152 && x < 0.66 * 1152 && y > 0.34 * 1536 && y < 0.66 * 1536) {
            interiorDelta += delta;
            interiorSamples++;
          }
        }
      }
      return {
        sampled_pixels: Math.ceil(1152 / 4) * Math.ceil(1536 / 4),
        changed_sample_pixels: totalChanged,
        mean_abs_rgb_delta_sampled: totalDelta / (Math.ceil(1152 / 4) * Math.ceil(1536 / 4) * 3),
        center_subject_mean_abs_rgb_delta_sampled: interiorDelta / Math.max(1, interiorSamples * 3),
        background_regions: Object.fromEntries(Object.entries(regionStats).map(([id, stats]) => [id, {
          sampled_pixels: stats.sampled_pixels,
          changed_sample_pixels: stats.changed_sample_pixels,
          mean_abs_rgb_delta_sampled: stats.absolute_rgb_delta_sum / Math.max(1, stats.sampled_pixels * 3),
          source_region: regions[id]
        }]))
      };
    });
    assert.equal(pixelStats.center_subject_mean_abs_rgb_delta_sampled, 0, "Receiving Light must leave center photo detail untouched");

    return {
      page_url: url,
      build: identity.build,
      photo: { sha256: identity.photo_sha256, dimensions: identity.photo_dimensions, contour_source: identity.contour_source },
      primary_paths: primaryPathGeometry(identity.primary_paths),
      browser_viewport: identity.viewport,
      loaded_scripts: { formal_edgeglow: formalScript, shared_receiving_light: sharedScript },
      isolated_png: {
        file: "receiving-light-transparent-1152x1536.png",
        sha256: files.find(item => item.file === "receiving-light-transparent-1152x1536.png").sha256,
        size: isolated.size, alpha: isolated.alpha, per_island: isolated.per_island, quiet_gap_checks: isolated.quiet_gap_checks,
        source_size: isolated.source_size, source_rect: isolated.source_rect, destination_rect: isolated.destination_rect
      },
      receiving_over_photo_png: {
        file: "receiving-light-over-photo-1152x1536.png",
        sha256: files.find(item => item.file === "receiving-light-over-photo-1152x1536.png").sha256,
        pixel_signal: pixelStats
      }
    };
  } finally {
    await page.close();
  }
}

async function captureDefaultFormal(browser, errors) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  attachErrors(page, "formal-default", errors);
  try {
    await page.goto(new URL(ACCEPTANCE_PATH, SITE_BASE).href, { waitUntil: "networkidle", timeout: 30000 });
    await waitForFormal(page);
    const identity = await formalIdentity(page);
    assert.equal(identity.build.pr_source_sha, SOURCE_SHA);
    assert.equal(identity.build.acceptance_center_source_sha, CENTER_SHA);
    const sharedScript = await pageScriptRecord(
      page, "edgeglow-receiving-light.js",
      path.join(REPO, "design/manager/edgeglow-receiving-light.js")
    );

    await renderFormal(page);
    writePng("full-static-composite-1152x1536.png",
      await page.locator("#glowCanvas").evaluate(canvas => canvas.toDataURL("image/png")));

    const viewports = [];
    for (const [width, height] of VIEWPORTS) {
      await page.setViewportSize({ width, height });
      await page.evaluate(() => window.__EDGEGLOW_LAB__.render());
      await page.waitForFunction(() => window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__?.status === "ready", null, { timeout: 30000 });
      await page.locator("#glowCanvas").evaluate(canvas => canvas.scrollIntoView({ block: "center", inline: "center" }));
      const pageFile = "static-page-" + width + "x" + height + ".png";
      const canvasFile = "static-canvas-" + width + "x" + height + ".png";
      await page.screenshot({ path: path.join(OUTPUT, pageFile), fullPage: false });
      await page.locator("#glowCanvas").screenshot({ path: path.join(OUTPUT, canvasFile), animations: "disabled" });
      const pageBuffer = fs.readFileSync(path.join(OUTPUT, pageFile));
      const canvasBuffer = fs.readFileSync(path.join(OUTPUT, canvasFile));
      files.push({ file: pageFile, bytes: pageBuffer.length, sha256: sha256(pageBuffer), viewport: [width, height] });
      files.push({ file: canvasFile, bytes: canvasBuffer.length, sha256: sha256(canvasBuffer), viewport: [width, height] });
      assert.deepEqual(await page.evaluate(() => [innerWidth, innerHeight]), [width, height]);
      viewports.push({
        viewport: [width, height],
        page_screenshot: pageFile,
        canvas_screenshot: canvasFile,
        shared_renderer: sharedScript,
        photo_sha256: identity.photo_sha256,
        build_sha: identity.build.pr_source_sha,
        status: "CAPTURED_FROM_FORMAL_EDGEGLOW_PAGE"
      });
    }
    return {
      page_url: new URL(ACCEPTANCE_PATH, SITE_BASE).href,
      build: identity.build,
      photo_sha256: identity.photo_sha256,
      loaded_shared_renderer: sharedScript,
      full_composite_png: {
        file: "full-static-composite-1152x1536.png",
        sha256: files.find(item => item.file === "full-static-composite-1152x1536.png").sha256
      },
      viewport_screenshots: viewports,
      note: "Fish Focus in this page is the registered manual reference contour; not a production Mask or FishSubjectResult."
    };
  } finally {
    await page.close();
  }
}

async function verifyOpticalLab(browser, errors) {
  const page = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  attachErrors(page, "optical-lab", errors);
  try {
    await page.goto(new URL(OPTICAL_PATH, SITE_BASE).href, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() =>
      document.querySelector("#overallStatus")?.textContent === "REVIEW_REQUIRED" &&
      document.querySelector("#previewCanvas")?.dataset.photoContentRect &&
      window.EdgeGlowReceivingLight?.version === "edgeglow-receiving-light-svg-v1" &&
      window.OPTICAL_LAB_BUILD_INFO__?.head_sha,
    null, { timeout: 30000 });
    const identity = await page.evaluate(() => {
      const geometry = window.OPTICAL_LAB_STATIC_GEOMETRY_DIAGNOSTIC();
      return {
        build: window.OPTICAL_LAB_BUILD_INFO__,
        photo: geometry.photo,
        rect: geometry.photoContentRect,
        viewport: [innerWidth, innerHeight],
        params: window.OPTICAL_LAB_STATIC_RECEIVING_PARAMETERS__,
        staticCandidates: window.OPTICAL_LAB_STATIC_CANDIDATES__,
        primary_paths: geometry.paths.filter(path => path.role === "primary")
      };
    });
    assert.equal(identity.build.head_sha, SOURCE_SHA, "Optical Lab must use the exact PR source SHA");
    assert.equal(identity.build.acceptance_center_source_sha, CENTER_SHA);
    assert.equal(identity.photo.sha256, PHOTO_SHA);
    assert.deepEqual(Object.keys(identity.staticCandidates), ["SINGLE"], "only one static candidate may be exposed");
    const script = await pageScriptRecord(
      page, "edgeglow-receiving-light.js",
      path.join(REPO, "design/manager/edgeglow-receiving-light.js")
    );
    await page.locator("#previewModeButtons [data-preview-mode='static']").click();
    await page.locator("#stateButtons button").nth(0).click();
    await page.selectOption("#staticCandidate", "SINGLE");
    await page.selectOption("#focusSource", "blocked");
    await page.selectOption("#fieldLayerView", "composite");
    await page.waitForFunction(() =>
      document.querySelector("#previewCanvas")?.dataset.receivingLightRenderer === "edgeglow-receiving-light-svg-v1" &&
      document.querySelector("#previewCanvas")?.dataset.receivingLightStatus === "ready",
    null, { timeout: 30000 });
    writePng("geometry-diagnostic-390x844.png",
      await page.evaluate(() => window.OPTICAL_LAB_STATIC_GEOMETRY_DIAGNOSTIC().png_data_url));
    writePng("optical-lab-shared-render-390x844.png",
      await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png")));
    await page.locator("#previewCanvas").evaluate(canvas => canvas.scrollIntoView({ block: "center", inline: "center" }));
    const pageFile = "optical-lab-page-390x844.png";
    await page.screenshot({ path: path.join(OUTPUT, pageFile), fullPage: false });
    const pageBuffer = fs.readFileSync(path.join(OUTPUT, pageFile));
    files.push({ file: pageFile, bytes: pageBuffer.length, sha256: sha256(pageBuffer), viewport: [390, 844] });
    return {
      page_url: new URL(OPTICAL_PATH, SITE_BASE).href,
      build: identity.build,
      photo: identity.photo,
      photo_content_rect: identity.rect,
      primary_paths: identity.primary_paths,
      loaded_shared_renderer: script,
      static_candidate: identity.staticCandidates.SINGLE,
      receiving_parameters: identity.params,
      screenshots: ["geometry-diagnostic-390x844.png", "optical-lab-shared-render-390x844.png", pageFile],
      status: "SHARED_RENDERER_USED_IN_OPTICAL_LAB"
    };
  } finally {
    await page.close();
  }
}

function buildMarkdown(report) {
  const lines = [
    "# Receiving Light Static One-Shot V1",
    "",
    "- Status: REVIEW_REQUIRED (browser captures and constraint checks are recorded; human visual approval is pending).",
    "- Source SHA: " + report.source_sha,
    "- Acceptance Center SHA: " + report.acceptance_center_sha,
    "- Photo SHA-256: " + report.photo.sha256,
    "- Shared renderer: " + report.renderer.version + " · SHA-256 " + report.renderer.sha256,
    "- P1 base alpha: 0.07; final 8-bit output cap: " + report.renderer.output_alpha_cap_8bit + "/255.",
    "- P1 edge depth: 14%; composite: source-over; Quiet Gaps: masked.",
    "- Android Runtime / Native / APK: NOT_RUN.",
    "",
    "## Browser PNG evidence",
    ""
  ];
  for (const item of report.artifacts) lines.push("- " + item.file + " · SHA-256 " + item.sha256);
  lines.push(
    "",
    "## Renderer provenance",
    "",
    "- Formal EdgeGlow and Optical Lab loaded the same shared renderer bytes.",
    "- Primary path data: B1, G1, G3, B3; island weights and asymmetric path coverage are in the JSON report.",
    "- The formal page uses a registered manual reference contour for Fish Focus; it is not a production Mask.",
    "- These are actual Chromium captures and pixel constraints; this report does not claim visual PASS."
  );
  return lines.join("\n") + "\n";
}

async function main() {
  if (fs.existsSync(OUTPUT) && fs.readdirSync(OUTPUT).length > 0) {
    throw new Error("Evidence output directory already exists and is not empty: " + OUTPUT);
  }
  fs.mkdirSync(OUTPUT, { recursive: true });
  const errors = [];
  if (!/^[0-9a-f]{40}$/.test(SOURCE_SHA)) throw new Error("PR_SOURCE_SHA must be an exact 40-character SHA.");
  if (!/^[0-9a-f]{40}$/.test(CENTER_SHA)) throw new Error("CENTER_SOURCE_SHA must be an exact 40-character SHA.");

  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  try {
    const formalIsolated = await captureReceivingOnly(browser, errors);
    const formalDefault = await captureDefaultFormal(browser, errors);
    const opticalLab = await verifyOpticalLab(browser, errors);
    const formalPaths = primaryPathGeometry(formalIsolated.primary_paths);
    const opticalPaths = primaryPathGeometry(opticalLab.primary_paths);
    assert.deepEqual(opticalPaths, formalPaths, "Optical Lab and formal EdgeGlow must use identical P1 primary path geometry");
    assert.equal(errors.length, 0, errors.join("\n"));

    const rendererSource = fs.readFileSync(path.join(REPO, "design/manager/edgeglow-receiving-light.js"));
    const report = {
      schema_version: "1.0",
      task_id: "YUJIAN_EDGEGLOW_STATIC_VISUAL_ONE_SHOT_V1",
      status: "REVIEW_REQUIRED",
      source_sha: SOURCE_SHA,
      acceptance_center_sha: CENTER_SHA,
      capture_site_base: SITE_BASE,
      photo: {
        sha256: PHOTO_SHA,
        dimensions: [1152, 1536],
        exif_orientation: 1,
        source: "registered real catch photo; same in Optical Lab and formal EdgeGlow"
      },
      renderer: {
        version: "edgeglow-receiving-light-svg-v1",
        sha256: sha256(rendererSource),
        output_alpha_cap_8bit: 17,
        p1_base_alpha_range: [0.03, 0.07],
        selected_base_alpha: 0.07,
        p1_depth_range: [0.08, 0.18],
        selected_depth_ratio: 0.14,
        composite: "source-over",
        drawing: "SVG path masks + feGaussianBlur outer/mid + core ridge + photoContentRect clip + P1 Quiet Gap mask",
        paths: ["B1", "G1", "G3", "B3"],
        path_weights: { B1: 1.0, G1: 0.78, G3: 0.63, B3: 0.54 },
        path_coverage: { B1: 1.0, G1: 0.84, G3: 0.60, B3: 0.38 },
        quiet_gaps: { Q_TOP: [0.32, 0.68], Q_LEFT: [0.38, 0.62], Q_RIGHT: [0.46, 0.58], Q_BOTTOM: [0.40, 0.62] },
        layers: [{ id: "outer", alpha_share: 0.68 }, { id: "mid", alpha_share: 0.24 }, { id: "core", alpha_share: 0.08 }]
      },
      path_geometry_match: { source_paths: formalPaths, optical_lab_paths: opticalPaths, equal: true },
      formal_isolated_render: formalIsolated,
      formal_static_render: formalDefault,
      optical_lab_render: opticalLab,
      artifacts: files,
      visual_review: "REVIEW_REQUIRED",
      visual_pass_claimed: false,
      browser_errors: errors,
      android_runtime: "NOT_RUN",
      native_implementation: "NOT_RUN",
      apk: "NOT_RUN",
      production_fish_mask: "BLOCKED_OR_NOT_PROVIDED; manual reference contour is REVIEW_ONLY"
    };
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-static-one-shot-report.json"), JSON.stringify(report, null, 2) + "\n");
    fs.writeFileSync(path.join(OUTPUT, "receiving-light-static-one-shot-report.md"), buildMarkdown(report));
    fs.writeFileSync(path.join(OUTPUT, "sha256-manifest.json"), JSON.stringify(files, null, 2) + "\n");
    console.log(JSON.stringify({
      result: "BROWSER_CAPTURE_COMPLETE_REVIEW_REQUIRED",
      source_sha: SOURCE_SHA,
      acceptance_center_sha: CENTER_SHA,
      renderer_sha256: report.renderer.sha256,
      photo_sha256: PHOTO_SHA,
      artifact_count: files.length,
      output_dir: OUTPUT,
      alpha_peak_8bit: formalIsolated.isolated_png.alpha.peak_alpha_8bit,
      viewports: VIEWPORTS,
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
