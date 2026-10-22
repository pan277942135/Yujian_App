#!/usr/bin/env node

import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
let activeBrowser = null;
let activeServer = null;

function parseArgs(argv) {
  const result = { root: process.cwd(), url: null, outputDir: "/tmp/design-manager-browser-evidence", expectedBuild: null, swosPhoto: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--root") result.root = resolve(argv[++i]);
    else if (arg === "--url") result.url = argv[++i];
    else if (arg === "--output-dir") result.outputDir = resolve(argv[++i]);
    else if (arg === "--expected-build") result.expectedBuild = argv[++i];
    else if (arg === "--swos-photo") result.swosPhoto = resolve(argv[++i]);
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
  const requests = [];
  const browser = await chromium.launch({ headless: true });
  activeBrowser = browser;
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  page.on("pageerror", error => errors.push(error.message));
  page.on("requestfailed", request => failedRequests.push(`${request.url()} · ${request.failure()?.errorText || "request failed"}`));
  page.on("request", request => requests.push({ url: request.url(), method: request.method() }));
  page.on("response", response => {
    if (response.status() >= 400 && response.url().startsWith(new URL(baseUrl).origin)) {
      badResponses.push(`${response.status()} ${response.url()}`);
    }
  });

  const overviewHash = "#page/recognition_flow_v1_2/hifi/main_journey_overview";
  const p01Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01";
  const p02Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02";
  const p03Hash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p03";
  const resourcesHash = "#page/recognition_flow_v1_2/hifi/capture_first_use_guide/resources";
  const fullFlowModelsHash = "#page/recognition_flow_v1_2/hifi/recognition_full_flow_models";
  const open = hash => page.goto(baseUrl + hash, { waitUntil: "networkidle", timeout: 45000 });

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

  const resourcesMenuItem = page.locator('#pageList .page-subsubitem[data-hifi-child-id="resources"]');
  assert(await resourcesMenuItem.isVisible(), "07 guide must show its Resources submenu");
  await resourcesMenuItem.click();
  await page.waitForFunction(expected => location.hash === expected, resourcesHash);
  assert((await page.locator("#hifiViewTitle").innerText()).trim() === "资源", "Resources submenu must open its registered route");
  assert((await page.locator("#hifiViewSummary").innerText()).includes("不映射为 Android 运行时背景"), "Resources route must state its Android runtime boundary");
  assert(await page.locator(".guide-resource-card").count() === 3, "Resources route must show exactly three Frozen source PNGs");
  const resourceExpected = [
    ["p01", 941, 1671, "P01_First_Use_Camera_Guide_Frozen.png", "dc731cca5470"],
    ["p02", 941, 1671, "P02_First_Use_Camera_Guide_Frozen.png", "fb092235fd04"],
    ["p03", 941, 1672, "P03_First_Use_Camera_Guide_Frozen.png", "1a3008483ac5"]
  ];
  for (const [id, width, height, filename, shaPrefix] of resourceExpected) {
    const selector = `.guide-resource-card[data-resource-id="${id}"]`;
    const loaded = await waitForLoadedImage(page, `${selector} img`, `${id.toUpperCase()} resource`);
    assert(loaded.width === width && loaded.height === height,
      `${id.toUpperCase()} resource dimensions changed: ${JSON.stringify(loaded)}`);
    const card = page.locator(selector);
    assert((await card.innerText()).includes(shaPrefix), `${id.toUpperCase()} resource SHA is not shown`);
    const download = card.locator("a[download]");
    assert(await download.getAttribute("download") === filename, `${id.toUpperCase()} original download filename changed`);
    assert((await download.getAttribute("href"))?.includes(filename), `${id.toUpperCase()} download does not point to its original PNG`);
  }
  const manifestLink = page.locator(".guide-resource-manifest a");
  assert((await manifestLink.getAttribute("href"))?.includes("first_use_camera_guide/manifest.json"), "Resources route must link to the authoritative manifest");
  await page.screenshot({ path: resolve(args.outputDir, "guide-resources-1440.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  const resourceMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(resourceMobileWidth.document <= 390 && resourceMobileWidth.body <= 390,
    `Resources route overflows the mobile viewport: ${JSON.stringify(resourceMobileWidth)}`);
  await page.screenshot({ path: resolve(args.outputDir, "guide-resources-390.png"), fullPage: true });

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

  await open(fullFlowModelsHash);
  await waitForManager(page);
  assert(page.url().endsWith(fullFlowModelsHash), "full-flow/model direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("识别全流程-和模型"), "full-flow/model menu title is missing");
  const fullFlowImage = await waitForLoadedImage(page, ".recognition-full-flow-models .hifi-asset-figure img", "recognition full-flow diagram");
  assert(fullFlowImage.complete && fullFlowImage.width === 2200 && fullFlowImage.height === 1500,
    `full-flow SVG must load at 2200×1500: ${JSON.stringify(fullFlowImage)}`);
  assert(await page.locator(".recognition-full-flow-models .authority-index-card").count() >= 30,
    "full-flow/model page must render the stages, model roles, branches and acceptance cards");
  assert((await page.locator("#hifiViewContractDetails").innerText()).includes("Recognition_Full_Flow_Models_V1.md"),
    "full-flow/model page must expose its canonical specification link");
  await page.screenshot({ path: resolve(args.outputDir, "recognition-full-flow-models-1440.png"), fullPage: true });

  await open("#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review");
  const reviewImage = await waitForLoadedImage(page, ".hifi-asset-figure img", "review source");
  assert(reviewImage.complete && reviewImage.width === 2800 && reviewImage.height === 1600,
    `review image must load the real 2800×1600 source: ${JSON.stringify(reviewImage)}`);
  assert((await page.locator("#hifiViewStatus").innerText()).includes("部分完成"), "review image must remain PARTIAL, not Frozen");

  await open(p01Hash);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(p01Hash), "refresh must preserve the direct Page/State Hash");
  await waitForManager(page);
  await open(resourcesHash);
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(resourcesHash), "refresh must preserve the direct Resources Hash");
  assert(await page.locator(".guide-resource-card").count() === 3, "direct Resources Hash must render all source PNGs after refresh");

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

  const swosHash = "#page/recognition_flow_v1_2/hifi/swos_optical_contract";
  await open(swosHash);
  await waitForManager(page);
  assert(page.url().endsWith(swosHash), "SWOS Optical Contract direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("SWOS 光学合同"), "SWOS contract menu title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("SWOS_Optical_Contract_V1.md"), "SWOS contract authority link is missing");
  await page.screenshot({ path: resolve(args.outputDir, "swos-optical-contract-1440.png"), fullPage: true });
  await page.reload({ waitUntil: "networkidle" });
  assert(page.url().endsWith(swosHash), "refresh must preserve the SWOS contract Hash route");

  const swosStaticHash = "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1";
  await open(swosStaticHash);
  await waitForManager(page);
  assert(page.url().endsWith(swosStaticHash), "SWOS static workspace direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("SWOS"), "SWOS static menu title is missing");
  const swosWorkspace = page.locator(".swos-workspace");
  await swosWorkspace.waitFor({ state: "visible" });
  assert(await page.locator(".swos-frame-card").count() === 15, "static workspace must show exactly 15 clickable frames");
  assert(await page.locator(".swos-track-row").count() === 2, "static workspace must keep Processing and RR09 on separate rows");
  assert(await page.locator(".swos-track-row").nth(0).locator(".swos-frame-card").count() === 8, "Processing storyboard row must contain 8 frames");
  assert(await page.locator(".swos-track-row").nth(1).locator(".swos-frame-card").count() === 7, "RR09 storyboard row must contain 7 frames");
  const r0Frame = page.locator('.swos-frame-card[data-frame-id="R0"] .swos-phone');
  assert(await r0Frame.locator(".swos-photo-shell").count() === 0, "R0 must not paint a full-screen blurred photo over the entering Result UI");
  assert(await r0Frame.locator(".swos-result-authority").count() === 1, "R0 must begin with the Frozen Result page underneath the shared photo");
  assert(await page.locator("[data-geometry-table] tbody tr").count() === 4, "P7/R0/R0.5/R1 geometry comparison must contain 4 rows");
  assert(await page.locator("[data-geometry-table]").getAttribute("data-contract-frame-count") === "15", "geometry comparison must bind the 15-frame contract");
  const resultSource = await waitForLoadedImage(page, ".swos-result-authority", "Frozen Result HIGH structure");
  assert(resultSource.width === 941 && resultSource.height === 1672, `static Result preview must use the existing Frozen HIGH authority: ${JSON.stringify(resultSource)}`);
  await page.screenshot({ path: resolve(args.outputDir, "swos-static-visual-1440.png"), fullPage: true });

  const sourceRequestCount = requests.length;
  let uploadedPhotoSha = null;
  if (args.swosPhoto) {
    const photoBytes = await readFile(args.swosPhoto);
    uploadedPhotoSha = createHash("sha256").update(photoBytes).digest("hex");
    await page.locator(".swos-photo-input").setInputFiles(args.swosPhoto);
    await page.waitForFunction(sha => document.querySelector(".swos-photo-meta")?.textContent?.includes(sha), uploadedPhotoSha, { timeout: 30000 });
    assert(await page.locator(".swos-photo-foreground").count() >= 15, "local photo must be composed into the static storyboard frames");
    const r0Source = await r0Frame.locator(".swos-photo-foreground").getAttribute("src");
    const r0BlurSource = await r0Frame.locator(".swos-r0-underlay img").getAttribute("src");
    assert(r0Source && r0Source === r0BlurSource, "R0 foreground and emerging Hero underlay must share the same local photo URL");
    assert(await r0Frame.locator(".swos-photo-shell").count() === 0, "R0 local photo must leave the Result page visible outside the Processing photo rect");
    assert(!(await page.evaluate(() => Object.keys(localStorage).length)), "SWOS must not persist the local photo in localStorage");
    assert(requests.slice(sourceRequestCount).every(request => request.method === "GET" && !request.url.includes(args.swosPhoto.split(/[\\/]/).pop())), "local photo selection must not trigger a POST or request the local filename");
  }

  const r05 = page.locator('.swos-frame-card[data-frame-id="R0.5"]');
  await r05.click();
  const swosDialog = page.locator(".swos-zoom-dialog");
  await swosDialog.waitFor({ state: "visible" });
  assert((await swosDialog.locator("[data-zoom-caption]").innerText()).includes("2900ms"), "R0.5 inspector must show its 2900ms global sample time");
  assert(await swosDialog.locator('.swos-phone[data-frame="R0.5"]').count() === 1, "R0.5 zoom must show its mid-flight photo frame");
  await swosDialog.locator('[data-mode="original"]').click();
  assert(await swosDialog.locator(".swos-overlay-svg").count() === 0, "original comparison must hide optical overlays");
  await swosDialog.locator('[data-mode="composite"]').click();
  await page.screenshot({ path: resolve(args.outputDir, "swos-r05-zoom-1440.png"), fullPage: false });
  await page.keyboard.press("Escape");
  await swosDialog.waitFor({ state: "hidden" });

  const rr09RelatedRoute = '#page/recognition_result_v1/hifi/motion_continuation';
  await page.locator('.hifi-related-routes a[href="' + rr09RelatedRoute + '"]').click();
  await page.waitForFunction(expected => location.hash === expected, rr09RelatedRoute);
  assert(await page.locator('.hifi-related-routes a[href="' + swosStaticHash + '"]').count() === 1, "RR09 must expose the reverse link to SWOS static inspection");
  await page.locator('.hifi-related-routes a[href="' + swosStaticHash + '"]').click();
  await page.waitForFunction(expected => location.hash === expected, swosStaticHash);
  await page.setViewportSize({ width: 390, height: 844 });
  const swosMobileWidth = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
  assert(swosMobileWidth.document <= 390 && swosMobileWidth.body <= 390, `SWOS workspace overflows the mobile viewport: ${JSON.stringify(swosMobileWidth)}`);
  await page.screenshot({ path: resolve(args.outputDir, "swos-static-visual-390.png"), fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });

  const continuityHash = "#page/recognition_flow_v1_2/hifi/processing_result_continuity";
  await open(continuityHash);
  assert(page.url().endsWith(continuityHash), "Processing → Result continuity direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("光学接续"), "continuity menu title is missing");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("Processing_Result_Optical_Continuity_Contract_V1.md"), "continuity authority link is missing");
  await page.screenshot({ path: resolve(args.outputDir, "processing-result-continuity-1440.png"), fullPage: true });

  const rr09V3Hash = "#page/recognition_result_v1/hifi/motion_continuation";
  await open(rr09V3Hash);
  assert(page.url().endsWith(rr09V3Hash), "RR09 V3 direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("V3"), "current RR09 menu must identify V3");
  assert((await page.locator("#hifiViewAuthorities").innerText()).includes("Recognition_Result_Motion_Spec_V3.md"), "current RR09 V3 authority link is missing");
  assert(await page.locator("#hifiViewCanvas iframe").count() === 0, "current RR09 V3 must not render the legacy V2 playable prototype");
  await page.screenshot({ path: resolve(args.outputDir, "rr09-v3-authority-1440.png"), fullPage: true });

  const rr09V2HistoryHash = "#page/recognition_result_v1/hifi/motion_continuation_v2_history";
  await open(rr09V2HistoryHash);
  assert(page.url().endsWith(rr09V2HistoryHash), "RR09 V2 history direct Hash route did not resolve");
  assert((await page.locator("#hifiViewTitle").innerText()).includes("V2 历史基线"), "RR09 V2 history label is missing");
  assert((await page.locator("#hifiViewStatus").innerText()).includes("已废弃"), "RR09 V2 history must remain DEPRECATED");
  assert((await page.locator("#hifiViewCanvas").innerText()).includes("历史对照"), "RR09 V2 page must explain that it is historical only");
  assert(await page.locator("#hifiViewCanvas iframe").count() === 1, "RR09 V2 historical preview must remain separately reachable");
  await page.screenshot({ path: resolve(args.outputDir, "rr09-v2-history-1440.png"), fullPage: true });

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
    checks: ["direct Hash", "Resources submenu click and direct route", "original PNG previews/download links and manifest", "refresh", "back/forward", "modal zoom/fit/Esc/focus restore", "SWOS and continuity authority routes", "15-frame static workspace and exact 8+7 count", "R0.5 2900ms zoom and original/composite toggle", "P7/R0/R0.5/R1 geometry table", "RR09 ↔ SWOS routes", "local-only photo selection without persistent/network upload", "390px SWOS responsive preview", "RR09 V3 current / V2 historical route separation", "missing authority no fake UI", "invalid route guard", "no HTTP errors", "no console/page errors"],
    swos_local_photo_sha256: uploadedPhotoSha,
    swos_local_photo_uploaded: Boolean(args.swosPhoto),
    screenshots: ["desktop-1440.png", "tablet-768.png", "mobile-390.png", "lightbox-1440.png", "overview-1440.png", "guide-resources-1440.png", "guide-resources-390.png", "swos-optical-contract-1440.png", "swos-static-visual-1440.png", "swos-static-visual-390.png", "swos-r05-zoom-1440.png", "processing-result-continuity-1440.png", "rr09-v3-authority-1440.png", "rr09-v2-history-1440.png"],
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
