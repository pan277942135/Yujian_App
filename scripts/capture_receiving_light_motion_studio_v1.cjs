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
    duration_ms: 1140,
    phase_boundaries_ms: [0, 220, 540, 890, 1140],
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
  assert((await page.locator("#rendererVersion").innerText()).includes("edgeglow-receiving-light-svg-v1"), "shared renderer did not initialize");
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
  assert((await page.locator("#runtimeStatus").innerText()).includes("已就绪"), "submenu route did not load the studio");
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
  for (const [ms, name] of [[0, "CAPTURED"], [220, "DETECTING"], [540, "OUTLINE"], [890, "CLASSIFYING"], [1140, "CLASSIFYING"]]) {
    const state = await seek(ms);
    assert(state.phase === name, "wrong phase at " + ms + "ms: " + state.phase);
    const filename = "keyframe-" + String(ms).padStart(4, "0") + "ms-" + name.toLowerCase() + ".png";
    await page.locator("#deviceFrame").screenshot({ path: resolve(OUTPUT, filename), animations: "disabled" });
    results.screenshots.push(filename);
  }
  results.checks.push("four frozen timing phases seek deterministically at 0/220/540/890/1140ms");
  const frameDownload = page.waitForEvent("download");
  await page.locator("#exportFrame").click();
  const downloadedFrame = await frameDownload;
  const exportedFrameName = "exported-current-frame-1140ms.png";
  await downloadedFrame.saveAs(resolve(OUTPUT, exportedFrameName));
  const exportedFrameBytes = await readFile(resolve(OUTPUT, exportedFrameName));
  assert(exportedFrameBytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])),
    "frame export did not produce a PNG");
  results.screenshots.push(exportedFrameName);
  results.checks.push("in-page PNG export produced a real PNG file");

  await seek(540);
  await page.locator("#stepForward").click();
  const stepped = await page.evaluate(() => window.RecognitionMotionStudio.getState());
  assert(stepped.currentMs - 540 >= 30 && stepped.currentMs - 540 <= 36,
    "single-frame step must advance about 33ms: " + stepped.currentMs);
  await page.locator("#stepBack").click();
  const steppedBack = await page.evaluate(() => window.RecognitionMotionStudio.getState());
  assert(Math.abs(steppedBack.currentMs - 540) < 1, "reverse frame step must restore the prior frame");
  await page.locator("#playPause").click();
  await page.waitForFunction(() => window.RecognitionMotionStudio.getState().currentMs > 570, null, { timeout: 1000 });
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
  results.checks.push("frame step, pause stability, resume, and 0.5x/1x/1.5x speed selectors");

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
  assert(await page.locator("#p1Receiving").evaluate(node => getComputedStyle(node).display === "none"), "Edge Field toggle did not hide the shared layer");
  await setLayer("toggleEdge", false);
  await setLayer("toggleTransfer", true);
  assert(await page.locator("#transferLayer").evaluate(node => getComputedStyle(node).display === "none"), "Transfer Wash toggle did not hide its layer");
  await setLayer("toggleTransfer", false);
  await setLayer("toggleFish", true);
  assert(await page.locator("#fishHalo").evaluate(node => getComputedStyle(node).display === "none"), "Fish Focus toggle did not hide the fish layer");
  await setLayer("toggleFish", false);
  await setLayer("toggleContour", true);
  assert(await page.locator("#fishCore").evaluate(node => getComputedStyle(node).display === "none"), "contour toggle did not hide the review outline");
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
  await videoPage.waitForFunction(() => document.querySelector("#currentTime")?.textContent === "1140", null, { timeout: 5000 });
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
  results.checks.push("Chromium recorded a complete 1,140ms playback from CAPTURED through CLASSIFYING");

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
