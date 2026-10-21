#!/usr/bin/env node

import { createServer } from "node:http";
import { createReadStream, existsSync, readFileSync, statSync } from "node:fs";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
let activeBrowser = null;
let activeServer = null;

function parseArgs(argv) {
  const result = { root: process.cwd(), url: null, outputDir: "/tmp/design-manager-browser-evidence", expectedBuild: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--root") result.root = resolve(argv[++i]);
    else if (arg === "--url") result.url = argv[++i];
    else if (arg === "--output-dir") result.outputDir = resolve(argv[++i]);
    else if (arg === "--expected-build") result.expectedBuild = argv[++i];
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return result;
}

function fail(message) { throw new Error(message); }
function assert(condition, message) { if (!condition) fail(message); }

function startStaticServer(root) {
  const absoluteRoot = resolve(root);
  const mime = {
    ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8",
    ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8",
    ".png": "image/png", ".svg": "image/svg+xml", ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg", ".webp": "image/webp", ".md": "text/markdown; charset=utf-8"
  };
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname); }
    catch (_) { response.writeHead(400).end("Bad path"); return; }
    let file = resolve(absoluteRoot, `.${pathname}`);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) {
      response.writeHead(403).end("Forbidden"); return;
    }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, "index.html");
    if (!existsSync(file) || !statSync(file).isFile()) {
      response.writeHead(404).end("Not found"); return;
    }
    response.writeHead(200, { "Content-Type": mime[extname(file).toLowerCase()] || "application/octet-stream", "Cache-Control": "no-store" });
    if (request.method === "HEAD") response.end();
    else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      resolvePromise({ server, url: `http://127.0.0.1:${address.port}/design/manager/` });
    });
  });
}

async function waitForManager(page) {
  await page.locator("#hifiViewTitle").waitFor({ state: "visible", timeout: 30000 });
  await page.locator("#pageList .module-item").first().waitFor({ state: "visible", timeout: 30000 });
}

async function waitForLoadedImage(page, selector, label) {
  await page.locator(selector).first().waitFor({ state: "attached", timeout: 30000 });
  try {
    await page.waitForFunction((imageSelector) => {
      const image = document.querySelector(imageSelector);
      return !!image && image.complete && image.naturalWidth > 0;
    }, selector, { timeout: 30000 });
  } catch (error) {
    throw new Error(`${label} original image did not finish loading: ${error.message}`);
  }
  return page.locator(selector).first().evaluate(node => ({
    width: node.naturalWidth,
    height: node.naturalHeight,
    complete: node.complete,
    src: node.currentSrc
  }));
}

async function main() {
  const args = parseArgs(process.argv);
  const { mkdir } = await import("node:fs/promises");
  await mkdir(args.outputDir, { recursive: true });
  let server = null;
  let baseUrl = args.url;
  if (!baseUrl) {
    const started = await startStaticServer(args.root);
    server = started.server;
    activeServer = server;
    baseUrl = started.url;
  } else if (!baseUrl.endsWith("/design/manager/")) {
    baseUrl = new URL("design/manager/", baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`).href;
  }

  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  const browser = await chromium.launch({ headless: true });
  activeBrowser = browser;
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  page.on("pageerror", error => errors.push(error.message));
  page.on("requestfailed", request => failedRequests.push(`${request.url()} · ${request.failure()?.errorText || "request failed"}`));
  page.on("response", response => {
    if (response.status() >= 400 && response.url().startsWith(new URL(baseUrl).origin)) {
      badResponses.push(`${response.status()} ${response.url()}`);
    }
  });

  const overviewHash = "#page/recognition_flow_v1_2/hifi/main_journey_overview";
  const p01Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01";
  const p02Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02";
  const p03Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p03";
  const open = hash => page.goto(baseUrl + hash, { waitUntil: "networkidle", timeout: 45000 });

  // Gemini Native V3 is sourced from the checked-in structure snapshot and approved assets,
  // not from live Figma. Verify the actual assets and emit a 1080x1920 diagnostic artifact.
  const previewUrl = new URL("nh01_acceptance/gemini-native-preview.html", baseUrl).href;
  const previewPage = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  previewPage.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  previewPage.on("pageerror", error => errors.push(error.message));
  previewPage.on("requestfailed", request => failedRequests.push(`${request.url()} · ${request.failure()?.errorText || "request failed"}`));
  previewPage.on("response", response => {
    if (response.status() >= 400 && response.url().startsWith(new URL(baseUrl).origin)) {
      badResponses.push(`${response.status()} ${response.url()}`);
    }
  });
  const previewResponse = await previewPage.goto(previewUrl, { waitUntil: "networkidle", timeout: 45000 });
  assert(previewResponse?.ok(), "NH01 Gemini Native preview route did not load");
  const previewBuild = await previewPage.locator('meta[name="nh01-gemini-preview-build"]').getAttribute("content");
  if (args.expectedBuild) assert(previewBuild === args.expectedBuild, `Gemini preview build mismatch: ${previewBuild} != ${args.expectedBuild}`);
  await previewPage.waitForFunction(() => window.NH01GeminiPreviewReady === true, { timeout: 30000 });
  const previewAudit = await previewPage.evaluate(() => window.NH01GeminiPreviewAudit);
  assert(previewAudit?.status === "PASS", `Gemini preview asset integrity failed: ${JSON.stringify(previewAudit)}`);
  assert(previewAudit.release_gate_effect === "NONE", "Gemini preview must not affect release gates");
  const requiredAssetIds = ["background", "brand", "avatar", "camera"];
  for (const id of requiredAssetIds) {
    const result = previewAudit.assets.find(asset => asset.id === id);
    assert(result?.status === "PASS_SOURCE_INTEGRITY", `Registered Gemini asset failed SHA/dimension check: ${id} ${JSON.stringify(result)}`);
  }
  assert(previewAudit.assets.find(asset => asset.id === "hero_photo")?.status === "BLOCKED_ASSET_SOURCE",
    "Hero photo must remain blocked until source provenance and usage rights are verified");
  assert(previewAudit.assets.find(asset => asset.id === "neighbor_photos")?.status === "BLOCKED_MISSING_APPROVED_INDEPENDENT_ORIGINALS",
    "Neighbor photos must not be invented or duplicated");
  const previewImageSources = await previewPage.locator("img").evaluateAll(images => images.map(image => image.currentSrc).filter(Boolean));
  assert(previewImageSources.every(src => new URL(src).origin === new URL(previewUrl).origin),
    `Gemini preview must not use external images: ${previewImageSources.join("; ")}`);
  assert(await previewPage.locator('img[src*="sample_recent_catch"]').count() === 0,
    "Sensitive user-provided Hero source must not be embedded in the public preview");
  await previewPage.screenshot({ path: resolve(args.outputDir, "nh01-gemini-native-preview-desktop.png"), fullPage: true });

  const previewViewportResults = [];
  for (const [width, height] of [[1440, 1000], [768, 1024], [390, 844], [320, 720]]) {
    await previewPage.setViewportSize({ width, height });
    await previewPage.waitForTimeout(50);
    const result = await previewPage.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
      body: document.body.scrollWidth,
      deviceBox: (() => { const r = document.querySelector("#deviceWrap").getBoundingClientRect(); return { width: r.width, height: r.height }; })()
    }));
    assert(result.document <= width && result.body <= width,
      `Gemini preview horizontal overflow at ${width}px: ${JSON.stringify(result)}`);
    previewViewportResults.push(result);
  }
  await previewPage.setViewportSize({ width: 1440, height: 1000 });
  await previewPage.locator("#fontScale").selectOption("1.5");
  assert(await previewPage.locator("#device").evaluate(node => getComputedStyle(node).getPropertyValue("--font-scale").trim()) === "",
    "font scale is stored on the document root, not the device node");
  assert(await previewPage.locator("html").evaluate(node => getComputedStyle(node).getPropertyValue("--font-scale").trim()) === "1.5",
    "Gemini preview font-scale control must update the root font scale");
  await previewPage.locator("#viewportPreset").selectOption("320x480");
  assert(await previewPage.locator("#device").evaluate(node => node.style.width) === "320px",
    "Gemini preview viewport preset must change the logical device width");
  await previewPage.locator("#cameraButton").click();
  assert(await previewPage.locator("#previewToast").getAttribute("data-visible") === "true",
    "Static camera button must clearly disclose that runtime interaction is not wired");

  const canvasPage = await browser.newPage({ viewport: { width: 360, height: 640 }, deviceScaleFactor: 3 });
  canvasPage.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  canvasPage.on("pageerror", error => errors.push(error.message));
  canvasPage.on("requestfailed", request => failedRequests.push(`${request.url()} · ${request.failure()?.errorText || "request failed"}`));
  canvasPage.on("response", response => {
    if (response.status() >= 400 && response.url().startsWith(new URL(baseUrl).origin)) {
      badResponses.push(`${response.status()} ${response.url()}`);
    }
  });
  await canvasPage.goto(previewUrl + "?mode=canvas", { waitUntil: "networkidle", timeout: 45000 });
  await canvasPage.waitForFunction(() => window.NH01GeminiPreviewReady === true, { timeout: 30000 });
  const canvasAudit = await canvasPage.evaluate(() => window.NH01GeminiPreviewAudit);
  assert(canvasAudit?.status === "PASS", `Gemini canvas preview asset integrity failed: ${JSON.stringify(canvasAudit)}`);
  const canvasPath = resolve(args.outputDir, "nh01-gemini-native-structure-1080x1920.png");
  await canvasPage.screenshot({ path: canvasPath, fullPage: false, scale: "device" });
  const canvasPng = readFileSync(canvasPath);
  assert(canvasPng.readUInt32BE(16) === 1080 && canvasPng.readUInt32BE(20) === 1920,
    "Gemini structure screenshot artifact must be exactly 1080x1920 pixels");
  await canvasPage.close();
  await previewPage.close();

  await open(p01Hash);
  await waitForManager(page);
  const buildMarker = await page.locator('meta[name="design-manager-build"]').getAttribute("content");
  if (args.expectedBuild) assert(buildMarker === args.expectedBuild, `live/build marker mismatch: ${buildMarker} != ${args.expectedBuild}`);
  const image = page.locator(".hifi-asset-figure img").first();
  const guideImageInfo = await waitForLoadedImage(page, ".hifi-asset-figure img", "P01");
  guideImageInfo.objectFit = await image.evaluate(node => getComputedStyle(node).objectFit);
  assert(guideImageInfo.complete && guideImageInfo.width === 941 && guideImageInfo.height === 1671,
    `P01 must load original 941×1671 pixels: ${JSON.stringify(guideImageInfo)}`);
  assert(guideImageInfo.objectFit === "contain", `P01 image fit must contain: ${guideImageInfo.objectFit}`);
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("dc731cca5470"), "P01 metadata must expose its registered SHA");
  const guideOriginalHref = await page.locator(".hifi-image-actions a").first().getAttribute("href");
  assert(guideOriginalHref?.includes("P01_First_Use_Camera_Guide_Frozen.png"), "P01 must retain a direct original PNG link");

  const viewportResults = [];
  for (const [width, height, screenshotName] of [
    [1440, 1000, "desktop-1440.png"], [768, 1024, "tablet-768.png"],
    [390, 844, "mobile-390.png"], [360, 800, null], [320, 720, null]
  ]) {
    await page.setViewportSize({ width, height });
    const result = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
      body: document.body.scrollWidth,
      imageBox: document.querySelector(".hifi-asset-figure")?.getBoundingClientRect().width || 0,
      title: document.querySelector("#hifiViewTitle")?.textContent?.trim() || ""
    }));
    assert(result.document <= width && result.body <= width,
      `Horizontal overflow at ${width}px: ${JSON.stringify(result)}`);
    assert(result.imageBox <= width, `Hi-Fi figure exceeds ${width}px viewport: ${JSON.stringify(result)}`);
    viewportResults.push(result);
    if (screenshotName) await page.screenshot({ path: resolve(args.outputDir, screenshotName), fullPage: true });
  }

  await page.setViewportSize({ width: 1440, height: 1000 });
  await open(p01Hash);
  await page.locator(".hifi-image-open").click();
  const dialog = page.locator("dialog.hifi-image-lightbox");
  await dialog.waitFor({ state: "visible" });
  await dialog.locator('[data-zoom="in"]').click();
  assert((await dialog.locator(".hifi-zoom-level").innerText()) === "125%", "zoom-in control must change the displayed scale");
  await dialog.locator('[data-zoom="fit"]').click();
  assert((await dialog.locator(".hifi-zoom-level").innerText()) === "100%", "Fit Window must restore the fitted scale");
  await page.screenshot({ path: resolve(args.outputDir, "lightbox-1440.png"), fullPage: false });
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "hidden" });
  assert(await page.locator(".hifi-image-open").evaluate(node => document.activeElement === node), "closing the lightbox must restore focus");

  for (const [hash, width, height, id] of [[p02Hash, 941, 1671, "P02"], [p03Hash, 941, 1672, "P03"]]) {
    await open(hash);
    const loaded = await waitForLoadedImage(page, ".hifi-asset-figure img", id);
    assert(loaded.complete && loaded.width === width && loaded.height === height, `${id} original dimensions are wrong: ${JSON.stringify(loaded)}`);
    assert(await page.locator("#hifiViewContractDetails").innerText().then(text => text.includes(id)), `${id} Page/State metadata missing`);
  }

  await open(overviewHash);
  await waitForManager(page);
  const overviewImageInfo = await waitForLoadedImage(page, ".hifi-asset-figure img", "overview PNG");
  assert(overviewImageInfo.complete && overviewImageInfo.width === 5120 && overviewImageInfo.height === 2880,
    `Overview must show the complete approved PNG: ${JSON.stringify(overviewImageInfo)}`);
  assert((await page.locator(".hifi-overview-intro").innerText()).includes("不是任何单页"), "overview must not be labeled a single-page authority");
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("2cf0ea161360"), "overview metadata must display its approved PNG SHA");
  await page.screenshot({ path: resolve(args.outputDir, "overview-1440.png"), fullPage: true });
  const firstJourneyLink = page.locator(".hifi-overview-link").first();
  const overviewUrl = page.url();
  const targetHash = await firstJourneyLink.getAttribute("href");
  await firstJourneyLink.click();
  await page.waitForFunction(expected => location.hash === expected, targetHash);
  assert(page.url().includes(targetHash), "overview journey link did not open its registered route");
  await page.goBack();
  await page.waitForFunction(expected => location.hash === expected, overviewHash);
  assert(page.url().endsWith(overviewHash), `Back did not restore the overview route from ${overviewUrl}`);
  await page.goForward();
  await page.waitForFunction(expected => location.hash === expected, targetHash);

  await open("#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review");
  const reviewImage = await waitForLoadedImage(page, ".hifi-asset-figure img", "review source");
  assert(reviewImage.complete && reviewImage.width === 2800 && reviewImage.height === 1600,
    `review image must load the real 2800×1600 source: ${JSON.stringify(reviewImage)}`);
  assert((await page.locator("#hifiViewStatus").innerText()).includes("部分完成"), "review image must remain PARTIAL, not Frozen");

  await open(p01Hash);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(p01Hash), "refresh must preserve the direct Page/State Hash");
  await waitForManager(page);

  const missingCases = [
    ["capture_portrait", "Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png"],
    ["capture_landscape", "Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png"],
    ["camera_permission_visual", "湖畔相机权限提示界面.png"]
  ];
  for (const [childId, absentName] of missingCases) {
    const hash = `#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/${childId}`;
    await open(hash);
    assert(await page.locator(".hifi-missing-authority").isVisible(), `${childId} missing-state explanation is not visible`);
    assert(await page.locator("#hifiViewCanvas img").count() === 0, `${childId} must not show a substitute image`);
    const missingLinkCount = await page.locator(`#hifiViewCanvas a[href*="${encodeURIComponent(absentName)}"], #hifiViewContractDetails a[href*="${encodeURIComponent(absentName)}"]`).count();
    assert(missingLinkCount === 0, `${childId} exposes an invalid link to an absent authority file`);
  }

  await open("#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/master_board_v1");
  assert(await page.locator(".hifi-master-board-gate").isVisible(), "Master Board missing PNG/SVG gate is not visible");
  assert((await page.locator(".hifi-state-table tbody tr").count()) > 0, "Master Board state JSON should render as a table");
  assert(await page.locator("#hifiViewCanvas img").count() === 0, "Overview must not be used as a Master Board image substitute");

  await open("#page/recognition_flow_v1_2/hifi/invalid-release-route");
  assert(await page.locator(".invalid-manager-route").isVisible(), "invalid Hash must show an explicit error state");
  assert(await page.locator("#hifiViewCanvas img").count() === 0, "invalid Hash must not fall back to an unrelated Frozen image");

  assert(badResponses.length === 0, `HTTP failures observed: ${badResponses.join("; ")}`);
  assert(failedRequests.length === 0, `Failed requests observed: ${failedRequests.join("; ")}`);
  assert(errors.length === 0, `Browser errors observed: ${errors.join("; ")}`);

  const summary = {
    result: "PASS",
    base_url: baseUrl,
    build_sha: buildMarker,
    viewports: viewportResults,
    p01: guideImageInfo,
    overview: overviewImageInfo,
    review: reviewImage,
    checks: ["direct Hash", "refresh", "back/forward", "modal zoom/fit/Esc/focus restore", "missing authority no fake UI", "invalid route guard", "no HTTP errors", "no console/page errors"],
    gemini_native_preview: { build_sha: previewBuild, asset_audit: previewAudit, viewports: previewViewportResults, canvas_screenshot: "nh01-gemini-native-structure-1080x1920.png", canvas_dimensions_px: [1080, 1920], release_gate_effect: "NONE" },
    screenshots: ["desktop-1440.png", "tablet-768.png", "mobile-390.png", "lightbox-1440.png", "overview-1440.png", "nh01-gemini-native-preview-desktop.png", "nh01-gemini-native-structure-1080x1920.png"],
    errors,
    bad_responses: badResponses,
    failed_requests: failedRequests
  };
  const { writeFile } = await import("node:fs/promises");
  await writeFile(resolve(args.outputDir, "browser-verification.json"), JSON.stringify(summary, null, 2) + "\n");
  process.stdout.write(`PASS: Design Manager browser verification | ${viewportResults.map(x => x.viewport).join(",")}px | ${summary.checks.join(" | ")}\n`);

  await browser.close();
  activeBrowser = null;
  if (server) await new Promise(resolvePromise => server.close(resolvePromise));
  activeServer = null;
}

main().catch(async error => {
  if (activeBrowser) await activeBrowser.close().catch(() => {});
  if (activeServer) await new Promise(resolvePromise => activeServer.close(resolvePromise));
  process.stderr.write(`FAIL: ${error.stack || error.message}\n`);
  process.exitCode = 1;
});
