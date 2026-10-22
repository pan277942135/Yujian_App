#!/usr/bin/env node
"use strict";

const { createServer } = require("node:http");
const { createReadStream } = require("node:fs");
const { existsSync, statSync } = require("node:fs");
const { mkdir, readFile, writeFile, copyFile } = require("node:fs/promises");
const { createHash } = require("node:crypto");
const { extname, resolve, sep } = require("node:path");
const { createRequire } = require("node:module");

const requireFromHere = createRequire(__filename);
const modulePath = process.env.PLAYWRIGHT_MODULE_DIR
  ? resolve(process.env.PLAYWRIGHT_MODULE_DIR, "playwright")
  : "playwright";
const { chromium } = requireFromHere(modulePath);
const ROOT = resolve(process.env.PR_SOURCE_ROOT || process.cwd());
const OUTPUT = resolve(process.env.MOTION_STUDIO_EVIDENCE_DIR || "/tmp/receiving-light-motion-studio");
const SOURCE_SHA = process.env.MOTION_STUDIO_SOURCE_SHA || process.env.GITHUB_SHA || "local-uncommitted";
const BASE = process.env.MOTION_STUDIO_BASE || "";
const PHOTO_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
const DURATION = 2800;
const PHASE_BOUNDARIES = [0, 350, 950, 1550, 2800];
const KEYFRAMES = [
  [0, "CAPTURED"], [350, "DETECTING"], [950, "OUTLINE"], [1200, "OUTLINE"],
  [1550, "CLASSIFYING"], [2050, "CLASSIFYING"], [2800, "CLASSIFYING"]
];
const MOTION_PATH = "design/manager/optical-lab/recognition-motion/";
const VIEWPORTS = [[320, 640], [390, 844], [430, 932], [1080, 1920]];
let server = null;
let browser = null;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function sha256(data) {
  return createHash("sha256").update(data).digest("hex");
}

function mime(path) {
  return ({
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".svg": "image/svg+xml"
  })[extname(path).toLowerCase()] || "application/octet-stream";
}

function startServer(root) {
  return new Promise((resolvePromise, reject) => {
    const instance = createServer((request, response) => {
      const pathname = decodeURIComponent(new URL(request.url, "http://127.0.0.1").pathname);
      let file = resolve(root, "." + pathname);
      if (file !== root && !file.startsWith(root + sep)) {
        response.writeHead(403).end("Forbidden");
        return;
      }
      if (pathname.endsWith("/")) file = resolve(file, "index.html");
      if (!existsSync(file) || !statSync(file).isFile()) {
        response.writeHead(404).end("Not found");
        return;
      }
      response.writeHead(200, { "content-type": mime(file), "cache-control": "no-store" });
      createReadStream(file).pipe(response);
    });
    instance.once("error", reject);
    instance.listen(0, "127.0.0.1", () => {
      const address = instance.address();
      resolvePromise({ instance, url: "http://127.0.0.1:" + address.port + "/" });
    });
  });
}

async function shaFile(relativePath) {
  return sha256(await readFile(resolve(ROOT, relativePath)));
}

async function rasterizeBrowserState(page, { fieldOnly = false } = {}) {
  return page.evaluate(async ({ fieldOnly }) => {
    const width = 1152;
    const height = 1536;
    const sourceSvg = document.querySelector("#motionSvg");
    const svg = sourceSvg.cloneNode(true);
    svg.setAttribute("width", String(width));
    svg.setAttribute("height", String(height));
    if (fieldOnly) {
      svg.querySelector("#transferLayer")?.remove();
      svg.querySelector("#fishFocus")?.remove();
      [...svg.children].forEach(node => {
        if (node.localName === "g" && node.getAttribute("data-source") !== "REVIEW_ONLY_SPATIAL_FIELD_V2") node.remove();
      });
    }
    const svgUrl = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: "image/svg+xml;charset=utf-8" }));
    const overlay = new Image();
    await new Promise((resolve, reject) => {
      overlay.onload = resolve;
      overlay.onerror = () => reject(new Error("Chromium failed to rasterize the SVG preview layer"));
      overlay.src = svgUrl;
    });
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    let sourcePixels = null;
    if (!fieldOnly) {
      context.drawImage(document.querySelector("#catchPhoto"), 0, 0, width, height);
      sourcePixels = context.getImageData(0, 0, width, height).data;
    }
    context.drawImage(overlay, 0, 0, width, height);
    URL.revokeObjectURL(svgUrl);
    const pixels = context.getImageData(0, 0, width, height).data;
    let nonzero = 0;
    let maxAlpha = 0;
    let outsideDepth = 0;
    let left = width, top = height, right = -1, bottom = -1;
    for (let offset = 3, index = 0; offset < pixels.length; offset += 4, index++) {
      const alpha = pixels[offset];
      if (alpha > 0) {
        nonzero++;
        maxAlpha = Math.max(maxAlpha, alpha);
        const x = index % width, y = Math.floor(index / width);
        const edgeDistance = Math.min(x + .5, y + .5, width - x - .5, height - y - .5);
        if (fieldOnly && edgeDistance > Math.min(width, height) * .18) outsideDepth++;
        left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
    }
    const region = (x1, y1, x2, y2) => {
      let changed = 0, sum = 0, max = 0;
      for (let y = Math.max(0, y1); y < Math.min(height, y2); y++) {
        for (let x = Math.max(0, x1); x < Math.min(width, x2); x++) {
          const offset = (y * width + x) * 4;
          const delta = Math.abs(pixels[offset] - sourcePixels[offset]) +
            Math.abs(pixels[offset + 1] - sourcePixels[offset + 1]) +
            Math.abs(pixels[offset + 2] - sourcePixels[offset + 2]);
          if (delta > 0) changed++;
          sum += delta;
          max = Math.max(max, delta);
        }
      }
      const area = Math.max(1, (Math.min(width, x2) - Math.max(0, x1)) * (Math.min(height, y2) - Math.max(0, y1)));
      return { changed_pixels: changed, mean_rgb_l1: Number((sum / area).toFixed(5)), peak_rgb_l1: max };
    };
    const gapDepth = Math.ceil(Math.min(width, height) * .18 * (1 + .18 * 3));
    const quiet = [
      [Math.round(width * .32), 0, Math.round(width * .68), gapDepth],
      [0, Math.round(height * .38), gapDepth, Math.round(height * .62)],
      [width - gapDepth, Math.round(height * .46), width, Math.round(height * .58)],
      [Math.round(width * .40), height - gapDepth, Math.round(width * .62), height]
    ].map(([x1, y1, x2, y2]) => {
      let count = 0, peak = 0;
      for (let y = Math.max(0, y1); y < Math.min(height, y2); y++) {
        for (let x = Math.max(0, x1); x < Math.min(width, x2); x++) {
          const alpha = pixels[(y * width + x) * 4 + 3];
          if (alpha > 0) count++;
          peak = Math.max(peak, alpha);
        }
      }
      return { nonzero_pixels: count, peak_alpha: peak };
    });
    const photoDiff = sourcePixels ? {
      full_photo: region(0, 0, width, height),
      top_left_field: region(0, 0, Math.round(width * .32), gapDepth),
      top_right_field: region(Math.round(width * .68), 0, width, gapDepth),
      left_lower_field: region(0, Math.round(height * .62), gapDepth, height),
      fish_reference_bbox: region(58, 292, 435, 1445)
    } : null;
    return {
      png_data_url: canvas.toDataURL("image/png"),
      dimensions: [width, height],
      alpha: { nonzero_pixels: nonzero, peak: maxAlpha, outside_depth_pixels: outsideDepth, bbox: right < left ? null : [left, top, right, bottom] },
      quiet_gaps: fieldOnly ? quiet : null,
      photo_diff: photoDiff
    };
  }, { fieldOnly });
}

async function main() {
  await mkdir(OUTPUT, { recursive: true });
  let baseUrl = BASE;
  if (!baseUrl) {
    const started = await startServer(ROOT);
    server = started.instance;
    baseUrl = started.url;
  }
  const motionUrl = new URL(MOTION_PATH + "?source_sha=" + encodeURIComponent(SOURCE_SHA), baseUrl).href;
  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  const externalRequests = [];
  const expectedOrigin = new URL(baseUrl).origin;
  const monitor = page => {
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", error => errors.push(error.message));
    page.on("requestfailed", request => failedRequests.push(request.url() + " · " + (request.failure()?.errorText || "request failed")));
    page.on("request", request => {
      if (new URL(request.url()).origin !== expectedOrigin) externalRequests.push(request.url());
    });
    page.on("response", response => {
      if (response.status() >= 400 && new URL(response.url()).origin === expectedOrigin) {
        badResponses.push(response.status() + " " + response.url());
      }
    });
  };
  const results = {
    status: "RUNNING",
    source_sha: SOURCE_SHA,
    page_url: motionUrl,
    photo_sha256: await shaFile("design/system/fish_media_display_v1/assets/real_catch_source.jpg"),
    config_sha256: await shaFile("design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json"),
    fixture_sha256: await shaFile("design/manager/nh01_acceptance/edgeglow-fish-static/fixture.json"),
    renderer_sha256: await shaFile("design/manager/edgeglow-receiving-light.js"),
    frame_rate_step_ms: 1000 / 30,
    duration_ms: DURATION,
    timing_purpose: "WEB_REVIEW_DESIGN_RHYTHM_NOT_INFERENCE_TIMER",
    phase_durations_ms: [350, 600, 600, 1250],
    phase_boundaries_ms: PHASE_BOUNDARIES,
    screenshots: [],
    checks: [],
    errors,
    failed_requests: failedRequests,
    android_runtime: "NOT_RUN",
    pages_deploy: "NOT_RUN"
  };
  assert(results.photo_sha256 === PHOTO_SHA, "registered photo SHA mismatch: " + results.photo_sha256);

  browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  monitor(page);

  await page.goto(motionUrl, { waitUntil: "networkidle", timeout: 45000 });
  await page.locator("h1").filter({ hasText: "Receiving Light Studio" }).waitFor({ state: "visible" });
  await page.waitForFunction(() => document.querySelector("#runtimeStatus")?.textContent?.includes("已就绪"), null, { timeout: 30000 });
  assert((await page.locator("#photoDigest").innerText()).includes("SHA-256 MATCH"), "registered photo hash must match in the browser");
  assert(await page.locator("#imageLoadError").isHidden(), "photo fallback error is visible");
  assert((await page.locator("#rendererVersion").innerText()).includes("REVIEW_ONLY_SPATIAL_FIELD_V2"), "shared spatial-field renderer did not initialize");
  assert(await page.locator('[data-source="REVIEW_ONLY_SPATIAL_FIELD_V2"]').count() === 1, "spatial field root must be unique");
  assert(await page.locator('[data-source="REVIEW_ONLY_SPATIAL_FIELD_V2"]').getAttribute("data-alpha-mode") === "P1_BASE_0.07_CAP_17_255", "P1 alpha cap must remain 17/255");
  assert(await page.locator('[data-source="REVIEW_ONLY_SPATIAL_FIELD_V2"]').getAttribute("data-quiet-gap-variant") === "A", "strict Quiet Gap variant A must be active");
  assert((await page.locator("#timeline").getAttribute("max")) === String(DURATION), "timeline max must match 2,800 ms");
  const declaredPhases = await page.evaluate(() => window.RecognitionMotionStudio.phases.map(phase => [phase.start, phase.end]));
  assert(JSON.stringify(declaredPhases) === JSON.stringify([[0, 350], [350, 950], [950, 1550], [1550, 2800]]), "phase durations/boundaries do not match the reviewed timeline");
  assert((await page.locator(".intro").innerText()).includes("不代表推理结束"), "inference decoupling notice is missing");
  assert(await page.locator("#fishHalo").count() === 1, "fish Halo path ID must be unique from its SVG filter");
  assert(await page.locator("#toggleContour").isChecked(), "review contour control must be enabled by default");
  const browserPhoto = await page.locator("#catchPhoto").evaluate(image => ({
    width: image.naturalWidth,
    height: image.naturalHeight,
    src: image.currentSrc
  }));
  assert(browserPhoto.width === 1152 && browserPhoto.height === 1536, "browser loaded unexpected photo dimensions");
  results.photo = browserPhoto;
  results.checks.push("registered photo loaded at 1152x1536 and SHA-256 matched");

  const navUrl = new URL("design/manager/", baseUrl).href;
  await page.goto(navUrl, { waitUntil: "networkidle", timeout: 45000 });
  const motionLink = page.locator('a[aria-label="打开 识别动效 · Receiving Light Studio"]');
  assert(await motionLink.count() === 1, "Design Manager submenu link is missing");
  await motionLink.click();
  await page.waitForURL(url => url.pathname.endsWith("/optical-lab/recognition-motion/"), { timeout: 15000 });
  await page.waitForFunction(() => {
    const status = document.querySelector("#runtimeStatus")?.textContent || "";
    return status.includes("已就绪") || status.includes("加载失败");
  }, null, { timeout: 30000 });
  const submenuStatus = await page.locator("#runtimeStatus").innerText();
  assert(submenuStatus.includes("已就绪"), "submenu route did not load the studio: " + submenuStatus);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().includes("/optical-lab/recognition-motion/"), "refresh did not preserve the studio route");
  await page.goBack();
  await page.waitForURL(url => url.pathname.endsWith("/design/manager/"), { timeout: 15000 });
  await page.goForward();
  await page.waitForURL(url => url.pathname.endsWith("/optical-lab/recognition-motion/"), { timeout: 15000 });
  results.checks.push("accessible submenu, direct refresh, and browser back/forward");

  const seek = async ms => {
    await page.locator("#timeline").evaluate((input, value) => {
      input.value = String(value);
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
    }, ms);
    const state = await page.evaluate(() => window.RecognitionMotionStudio.getState());
    assert(Math.abs(state.currentMs - ms) < 1, "timeline seek was not synchronized at " + ms + "ms");
    return state;
  };
  const keyframeMeasurements = [];
  for (const [ms, name] of KEYFRAMES) {
    const state = await seek(ms);
    assert(state.phase === name, "wrong phase at " + ms + "ms: " + state.phase);
    const filename = "keyframe-" + String(ms).padStart(4, "0") + "ms-" + name.toLowerCase() + ".png";
    await page.locator("#deviceFrame").screenshot({ path: resolve(OUTPUT, filename), animations: "disabled" });
    results.screenshots.push(filename);
    const compositeName = "photo-composite-" + String(ms).padStart(4, "0") + "ms.png";
    const raster = await rasterizeBrowserState(page);
    const compositeBytes = Buffer.from(raster.png_data_url.split(",")[1], "base64");
    await writeFile(resolve(OUTPUT, compositeName), compositeBytes);
    assert(compositeBytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), "photo composite export is not a PNG at " + ms + "ms");
    results.screenshots.push(compositeName);
    keyframeMeasurements.push({
      at_ms: ms,
      phase: name,
      file: compositeName,
      sha256: sha256(compositeBytes),
      dimensions: raster.dimensions,
      photo_rgb_difference: raster.photo_diff
    });
  }
  results.keyframe_measurements = keyframeMeasurements;
  assert(keyframeMeasurements[0].photo_rgb_difference.full_photo.changed_pixels === 0,
    "0ms must remain an unchanged photo without early optical layers");
  const stablePngHashes = keyframeMeasurements.filter(item => [1550, 2050, 2800].includes(item.at_ms)).map(item => item.sha256);
  assert(new Set(stablePngHashes).size === 1, "stable final phase composite PNGs must be pixel-identical at 1550/2050/2800ms");
  results.checks.push("0ms composite is unchanged photo and final stable composite is pixel-identical at 1550/2050/2800ms");

  await seek(950);
  const fieldRaster = await rasterizeBrowserState(page, { fieldOnly: true });
  const fieldName = "receiving-spatial-field-only-0950ms.png";
  const fieldBytes = Buffer.from(fieldRaster.png_data_url.split(",")[1], "base64");
  await writeFile(resolve(OUTPUT, fieldName), fieldBytes);
  assert(fieldRaster.alpha.peak <= 17, "review spatial field exceeded the P1 output alpha cap: " + fieldRaster.alpha.peak);
  assert(fieldRaster.alpha.outside_depth_pixels === 0, "review spatial field exceeded the frozen 18% photo-edge depth: " + fieldRaster.alpha.outside_depth_pixels);
  assert(fieldRaster.quiet_gaps.every(gap => gap.nonzero_pixels === 0 && gap.peak_alpha === 0),
    "review spatial field contaminated a Quiet Gap: " + JSON.stringify(fieldRaster.quiet_gaps));
  results.receiving_field = {
    file: fieldName,
    sha256: sha256(fieldBytes),
    dimensions: fieldRaster.dimensions,
    alpha: fieldRaster.alpha,
    depth_ratio: .18,
    quiet_gaps: fieldRaster.quiet_gaps,
    source_mode: "REVIEW_ONLY_SPATIAL_FIELD_V2 · P1_ALPHA_0.07 · CAP_17_255 · DEPTH_0.18 · QUIET_GAP_A"
  };
  results.screenshots.push(fieldName);
  results.checks.push("transparent Receiving Light PNG is browser-rendered, capped at 17/255 and zero in all four Quiet Gaps");
  results.checks.push("2,800ms phase boundaries and seven deterministic keyframes at 0/350/950/1200/1550/2050/2800ms");
  await seek(2800);
  const frameDownload = page.waitForEvent("download");
  await page.locator("#exportFrame").click();
  const downloadedFrame = await frameDownload;
  const exportedFrameName = "exported-current-frame-2800ms.png";
  await downloadedFrame.saveAs(resolve(OUTPUT, exportedFrameName));
  const exportedFrameBytes = await readFile(resolve(OUTPUT, exportedFrameName));
  assert(exportedFrameBytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])),
    "frame export did not produce a PNG");
  results.screenshots.push(exportedFrameName);
  results.checks.push("in-page PNG export produced a real PNG file");

  await seek(950);
  await page.locator("#stepForward").click();
  const stepped = await page.evaluate(() => window.RecognitionMotionStudio.getState());
  assert(stepped.currentMs - 950 >= 30 && stepped.currentMs - 950 <= 36,
    "single-frame step must advance about 33ms: " + stepped.currentMs);
  await page.locator("#stepBack").click();
  const steppedBack = await page.evaluate(() => window.RecognitionMotionStudio.getState());
  assert(Math.abs(steppedBack.currentMs - 950) < 1, "reverse frame step must restore the prior frame");
  await page.locator("#playPause").click();
  await page.waitForFunction(() => window.RecognitionMotionStudio.getState().currentMs > 980, null, { timeout: 1000 });
  await page.locator("#playPause").click();
  const pausedAt = await page.evaluate(() => window.RecognitionMotionStudio.getState().currentMs);
  await page.waitForTimeout(140);
  const stillPausedAt = await page.evaluate(() => window.RecognitionMotionStudio.getState().currentMs);
  assert(Math.abs(stillPausedAt - pausedAt) < 1, "pause allowed the shared clock to drift");
  await page.locator("#speed").selectOption("0.5");
  assert((await page.locator("#speed").inputValue()) === "0.5", "0.5x speed option did not select");
  await page.locator("#speed").selectOption("1.5");
  assert((await page.locator("#speed").inputValue()) === "1.5", "1.5x speed option did not select");
  await page.locator("#speed").selectOption("1");
  const beforeResume = stillPausedAt;
  await page.locator("#playPause").click();
  await page.waitForFunction(previous => window.RecognitionMotionStudio.getState().currentMs > previous + 20, beforeResume, { timeout: 1000 });
  results.checks.push("single-frame stepping, pause stability, continue, and 0.5x/1x/1.5x speed selectors");

  const stableSignature = async ms => {
    await seek(ms);
    return page.evaluate(() => {
      const field = document.querySelector('[data-source="REVIEW_ONLY_SPATIAL_FIELD_V2"]');
      return {
        edgeOpacity: field.getAttribute("opacity"),
        islands: [...field.querySelectorAll(".review-spatial-island")].map(node => node.getAttribute("opacity")),
        flows: [...field.querySelectorAll(".edge-flow")].map(node => [node.getAttribute("opacity"), node.getAttribute("stroke-dashoffset")]),
        transfer: ["#transferWashTop", "#transferWashRight", "#transferWashBottom"].map(id => {
          const node = document.querySelector(id);
          return [node.getAttribute("opacity"), node.getAttribute("stroke-dashoffset")];
        }),
        fish: ["#fishHalo", "#fishMid", "#fishCore"].map(id => {
          const node = document.querySelector(id);
          return [node.getAttribute("opacity"), node.getAttribute("stroke-dashoffset")];
        })
      };
    });
  };
  const stable1550 = await stableSignature(1550);
  const stable2050 = await stableSignature(2050);
  const stable2800 = await stableSignature(2800);
  assert(JSON.stringify(stable1550) === JSON.stringify(stable2050) && JSON.stringify(stable2050) === JSON.stringify(stable2800),
    "the full 1,250ms final pose must remain visually static from 1550 through 2800ms");
  results.stable_pose_signature = stable1550;
  results.checks.push("1550/2050/2800ms visual-layer signature is identical for the full 1,250ms stable phase");
  await page.locator("#restart").click();
  await page.waitForFunction(() => window.RecognitionMotionStudio.getState().currentMs > 50 && window.RecognitionMotionStudio.getState().currentMs < 2800, null, { timeout: 1000 });
  await page.locator("#playPause").click();
  await seek(2800);
  results.checks.push("replay returns the timeline to zero and starts a fresh cycle without completing an inference result");

  const setLayer = async (id, expectedHidden) => {
    const input = page.locator("#" + id);
    const targetChecked = !expectedHidden;
    const currentChecked = await input.evaluate(node => node.checked);
    if (currentChecked !== targetChecked) {
      await page.locator("label.toggle-row").filter({ has: input }).click();
    }
    assert(await input.evaluate(node => node.checked) === targetChecked, id + " label did not toggle its checkbox");
  };
  await setLayer("toggleEdge", true);
  assert(await page.locator('[data-source="REVIEW_ONLY_SPATIAL_FIELD_V2"]').evaluate(node => getComputedStyle(node).display === "none"), "Edge Field toggle did not hide the shared layer");
  await setLayer("toggleEdge", false);
  await setLayer("toggleTransfer", true);
  assert(await page.locator("#transferLayer").evaluate(node => getComputedStyle(node).display === "none"), "Transfer Wash toggle did not hide its layer");
  await setLayer("toggleTransfer", false);
  await setLayer("toggleFish", true);
  assert(await page.locator("#fishHalo").evaluate(node => getComputedStyle(node).display === "none"), "Fish Focus toggle did not hide the fish layer");
  await setLayer("toggleFish", false);
  await setLayer("toggleContour", true);
  assert(await page.locator("#fishHalo").evaluate(node => getComputedStyle(node).display === "none") &&
    await page.locator("#fishMid").evaluate(node => getComputedStyle(node).display === "none") &&
    await page.locator("#fishCore").evaluate(node => getComputedStyle(node).display === "none"), "contour toggle did not hide all manual-contour-derived layers");
  await setLayer("toggleContour", false);
  results.checks.push("Edge Field, Transfer Wash, Fish Focus, and contour layer switches");

  const viewportReport = [];
  for (const [width, height] of VIEWPORTS) {
    await page.setViewportSize({ width, height });
    const key = width + "x" + height;
    const preset = page.locator('.viewport-option[data-viewport="' + key + '"]');
    await preset.click();
    await page.waitForTimeout(50);
    const geometry = await page.evaluate(() => {
      const photo = document.querySelector("#photoContent").getBoundingClientRect();
      const svg = document.querySelector("#motionSvg").getBoundingClientRect();
      const frame = document.querySelector("#deviceFrame").getBoundingClientRect();
      return {
        viewport: [innerWidth, innerHeight],
        documentWidth: document.documentElement.scrollWidth,
        bodyWidth: document.body.scrollWidth,
        frame: { x: frame.x, y: frame.y, width: frame.width, height: frame.height },
        photo: { x: photo.x, y: photo.y, width: photo.width, height: photo.height },
        svg: { x: svg.x, y: svg.y, width: svg.width, height: svg.height }
      };
    });
    assert(geometry.documentWidth <= width && geometry.bodyWidth <= width, "horizontal overflow at " + key);
    assert(Math.abs(geometry.photo.width - geometry.svg.width) < 1 && Math.abs(geometry.photo.height - geometry.svg.height) < 1,
      "photo and SVG coordinate boxes diverged at " + key);
    viewportReport.push(geometry);
    const filename = "studio-viewport-" + key + ".png";
    await page.screenshot({ path: resolve(OUTPUT, filename), fullPage: true });
    results.screenshots.push(filename);
  }
  results.viewports = viewportReport;
  results.checks.push("320x640, 390x844, 430x932, 1080x1920 layout and shared photo/SVG geometry");

  const reducedContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: "reduce"
  });
  const reducedPage = await reducedContext.newPage();
  monitor(reducedPage);
  await reducedPage.goto(motionUrl, { waitUntil: "networkidle", timeout: 45000 });
  await reducedPage.waitForFunction(() => document.querySelector("#runtimeStatus")?.textContent?.includes("已就绪"), null, { timeout: 30000 });
  assert(await reducedPage.locator("#reducedNote").isVisible(), "reduced-motion preference was not exposed");
  await reducedPage.waitForTimeout(120);
  assert((await reducedPage.locator("#currentTime").innerText()) === "0", "reduced-motion page started automatically");
  await reducedPage.locator("#stepForward").click();
  assert((await reducedPage.locator("#currentTime").innerText()) !== "0", "reduced-motion page must retain user controlled stepping");
  results.checks.push("prefers-reduced-motion disables autoplay and preserves manual controls");
  await reducedContext.close();

  const videoContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    recordVideo: { dir: OUTPUT, size: { width: 1440, height: 1000 } }
  });
  const videoPage = await videoContext.newPage();
  monitor(videoPage);
  await videoPage.goto(motionUrl, { waitUntil: "networkidle", timeout: 45000 });
  await videoPage.waitForFunction(() => document.querySelector("#runtimeStatus")?.textContent?.includes("已就绪"), null, { timeout: 30000 });
  await videoPage.locator("#timeline").evaluate(input => {
    input.value = "0";
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
  const video = videoPage.video();
  await videoPage.locator("#playPause").click();
  await videoPage.waitForFunction(() => document.querySelector("#currentTime")?.textContent === "2800", null, { timeout: 8000 });
  await videoPage.waitForFunction(() => document.querySelector("#playbackState")?.textContent === "播放完成", null, { timeout: 3000 });
  await videoPage.waitForTimeout(120);
  await videoContext.close();
  const videoTemp = await video.path();
  const videoPath = resolve(OUTPUT, "receiving-light-motion-studio-full-cycle.webm");
  await copyFile(videoTemp, videoPath);
  const videoStat = statSync(videoPath);
  assert(videoStat.size > 10000, "browser recording is unexpectedly small");
  results.recording = {
    file: "receiving-light-motion-studio-full-cycle.webm",
    bytes: videoStat.size,
    sha256: sha256(await readFile(videoPath)),
    codec: "Playwright Chromium WebM",
    duration_ms: DURATION
  };
  results.checks.push("Chromium recorded a complete 2,800ms design-rhythm playback from CAPTURED through the stable CLASSIFYING pose");

  assert(errors.length === 0, "browser console/page errors: " + errors.join(" | "));
  assert(failedRequests.length === 0, "failed requests: " + failedRequests.join(" | "));
  assert(badResponses.length === 0, "HTTP errors: " + badResponses.join(" | "));
  assert(externalRequests.length === 0, "unexpected external network requests: " + externalRequests.join(" | "));
  results.http_errors = badResponses;
  results.external_requests = externalRequests;
  results.status = "WEB_PROTOTYPE_PASS";
  results.visual_review = "REVIEW_REQUIRED";
  results.contour_source = "MANUAL_REFERENCE_CONTOUR · REVIEW_PROXY_NOT_PRODUCTION";
  results.android_runtime = "NOT_RUN";
  results.pages_deploy = "NOT_RUN";
  results.completed_at = new Date().toISOString();

  const resourcePaths = [
    "design/manager/optical-lab/recognition-motion/index.html",
    "design/manager/optical-lab/recognition-motion/studio.css",
    "design/manager/optical-lab/recognition-motion/studio.js",
    "design/manager/edgeglow-receiving-light.js",
    "design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json",
    "design/manager/nh01_acceptance/edgeglow-fish-static/fixture.json",
    "design/system/fish_media_display_v1/assets/real_catch_source.jpg",
    "scripts/capture_receiving_light_motion_studio_v1.cjs"
  ];
  const sums = [];
  for (const item of resourcePaths) sums.push(shaFile(item).then(hash => hash + "  " + item));
  const files = [...results.screenshots, results.recording.file];
  for (const item of files) sums.push(readFile(resolve(OUTPUT, item)).then(data => sha256(data) + "  " + item));
  const hashes = await Promise.all(sums);
  await writeFile(resolve(OUTPUT, "SHA256SUMS.txt"), hashes.join("\n") + "\n");
  await writeFile(resolve(OUTPUT, "motion-studio-browser-report.json"), JSON.stringify(results, null, 2) + "\n");
  process.stdout.write("WEB_PROTOTYPE_PASS | " + results.checks.length + " browser checks | " + results.screenshots.length + " screenshots | " + results.recording.file + "\n");

  await context.close();
  await browser.close();
  browser = null;
  if (server) await new Promise(resolvePromise => server.close(resolvePromise));
  server = null;
}

main().catch(async error => {
  if (browser) await browser.close().catch(() => {});
  if (server) await new Promise(resolvePromise => server.close(resolvePromise));
  process.stderr.write("FAIL: " + (error.stack || error.message) + "\n");
  process.exitCode = 1;
});
