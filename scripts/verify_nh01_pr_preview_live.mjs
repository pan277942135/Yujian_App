#!/usr/bin/env node
import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
function assert(condition, message) { if (!condition) throw new Error(message); }
function parseArgs(argv) {
  const result = { root: process.cwd(), url: null, expectedBuild: null, output: "/tmp/nh01-pr-preview-report.json" };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--root") result.root = resolve(argv[++i]);
    else if (arg === "--url") result.url = argv[++i];
    else if (arg === "--expected-build") result.expectedBuild = argv[++i];
    else if (arg === "--output") result.output = resolve(argv[++i]);
    else throw new Error("Unknown argument: " + arg);
  }
  return result;
}
function startStaticServer(root) {
  const absoluteRoot = resolve(root);
  const mime = { ".html":"text/html; charset=utf-8", ".js":"text/javascript; charset=utf-8", ".css":"text/css; charset=utf-8", ".json":"application/json; charset=utf-8", ".png":"image/png", ".svg":"image/svg+xml", ".jpg":"image/jpeg", ".jpeg":"image/jpeg", ".webp":"image/webp", ".md":"text/markdown; charset=utf-8" };
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname); }
    catch { response.writeHead(400).end("Bad path"); return; }
    let file = resolve(absoluteRoot, "." + pathname);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) { response.writeHead(403).end("Forbidden"); return; }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, "index.html");
    if (!existsSync(file) || !statSync(file).isFile()) { response.writeHead(404).end("Not found"); return; }
    response.writeHead(200, { "Content-Type": mime[extname(file).toLowerCase()] || "application/octet-stream", "Cache-Control":"no-store" });
    if (request.method === "HEAD") response.end(); else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolvePromise({ server, origin: "http://127.0.0.1:" + server.address().port + "/" }));
  });
}
async function main() {
  const args = parseArgs(process.argv);
  assert(args.expectedBuild, "--expected-build is required");
  await mkdir(resolve(args.output, ".."), { recursive: true });
  let server = null;
  let origin = args.url;
  if (!origin) { const started = await startStaticServer(args.root); server = started.server; origin = started.origin; }
  if (!origin.endsWith("/")) origin += "/";
  const managerUrl = new URL("design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01", origin).href;
  const previewUrl = new URL("previews/pr-176/design/manager/nh01_acceptance/gemini-native-preview.html", origin).href;
  const browser = await chromium.launch({ headless: true });
  const errors = [];
  const failedRequests = [];
  const badResponses = [];
  const sameOrigin = new URL(origin).origin;
  const report = { expected_build: args.expectedBuild, manager_url: managerUrl, preview_url: previewUrl, checks: {}, errors, failed_requests: failedRequests, bad_responses: badResponses };
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
    page.on("pageerror", error => errors.push(error.message));
    page.on("requestfailed", request => { if (request.url().startsWith(sameOrigin)) failedRequests.push(request.url() + " · " + (request.failure()?.errorText || "failed")); });
    page.on("response", response => { if (response.status() >= 400 && response.url().startsWith(sameOrigin)) badResponses.push(response.status() + " " + response.url()); });

    const mainResponse = await page.goto(managerUrl, { waitUntil: "networkidle", timeout: 60000 });
    assert(mainResponse && mainResponse.status() === 200, "Design Manager main page HTTP status is not 200");
    await page.locator("#hifiViewTitle").waitFor({ state: "visible", timeout: 30000 });
    await page.locator("#pageList .module-item").first().waitFor({ state: "visible", timeout: 30000 });
    const mainBuild = await page.locator('meta[name="design-manager-build"]').getAttribute("content");
    assert(mainBuild === args.expectedBuild, "Design Manager build SHA mismatch: " + mainBuild);
    report.checks.main = { http_status: mainResponse.status(), build_sha: mainBuild, title: await page.title() };

    const previewResponse = await page.goto(previewUrl, { waitUntil: "domcontentloaded", timeout: 60000 });
    assert(previewResponse && previewResponse.status() === 200, "NH01 preview page HTTP status is not 200");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true, { timeout: 30000 });
    const previewBuild = await page.locator('meta[name="nh01-gemini-preview-build"]').getAttribute("content");
    assert(previewBuild === args.expectedBuild, "NH01 preview build SHA mismatch: " + previewBuild);
    const iframeCount = await page.locator(".responsive-frame").count();
    assert(iframeCount === 5, "Expected five responsive viewport preview frames, got " + iframeCount);
    await page.waitForFunction(() => Array.from(document.querySelectorAll(".responsive-frame")).every(frame => frame.contentWindow && frame.contentWindow.NH01GeminiPreviewLayoutReady === true), { timeout: 60000 });
    const imageState = await page.locator("img[data-asset-id]").evaluateAll(images => images.map(image => ({ asset: image.dataset.assetId, src: image.currentSrc, complete: image.complete, naturalWidth: image.naturalWidth, naturalHeight: image.naturalHeight })));
    const brokenImages = imageState.filter(image => !image.complete || image.naturalWidth <= 0);
    assert(brokenImages.length === 0, "Preview has unloaded registered image assets: " + JSON.stringify(brokenImages));

    const variant = page.locator("#layoutVariant");
    await variant.selectOption("V3_BASELINE");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true && window.NH01GeminiPreviewLayout?.variant === "V3_BASELINE", { timeout: 10000 });
    const baselineLayout = await page.evaluate(() => ({ variant: window.NH01GeminiPreviewLayout.variant, card_width_dp: window.NH01GeminiPreviewLayout.card_width_dp, card_height_dp: window.NH01GeminiPreviewLayout.card_height_dp }));
    await variant.selectOption("ADAPTIVE_V2");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true && window.NH01GeminiPreviewLayout?.variant === "ADAPTIVE_V2", { timeout: 10000 });
    const adaptiveLayout = await page.evaluate(() => ({ variant: window.NH01GeminiPreviewLayout.variant, card_width_dp: window.NH01GeminiPreviewLayout.card_width_dp, card_height_dp: window.NH01GeminiPreviewLayout.card_height_dp }));
    assert(baselineLayout.variant !== adaptiveLayout.variant, "Layout variant selector did not change active policy");

    await page.locator("#viewportPreset").selectOption("320x480");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true && window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[0] === 320 && window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[1] === 480, { timeout: 10000 });
    const compactLayout = await page.evaluate(() => ({ logical_viewport_dp: window.NH01GeminiPreviewLayout.logical_viewport_dp, card_width_dp: window.NH01GeminiPreviewLayout.card_width_dp, card_height_dp: window.NH01GeminiPreviewLayout.card_height_dp, needs_scroll: window.NH01GeminiPreviewLayout.needs_scroll }));
    await page.locator("#viewportPreset").selectOption("430x932");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true && window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[0] === 430 && window.NH01GeminiPreviewLayout?.logical_viewport_dp?.[1] === 932, { timeout: 10000 });
    await page.locator("#fontScale").selectOption("1.3");
    await page.waitForFunction(() => window.NH01GeminiPreviewLayoutReady === true && window.NH01GeminiPreviewLayout?.font_scale === 1.3, { timeout: 10000 });
    await page.locator('#layerTree button[data-target="#mainCatchCard"]').click();
    await page.waitForFunction(() => window.NH01SelectedLayer?.target === "#mainCatchCard", { timeout: 10000 });
    await page.locator("#cameraButton").click();
    await page.waitForFunction(() => document.querySelector(".preview-toast")?.dataset.visible === "true", { timeout: 10000 });
    report.checks.preview = {
      http_status: previewResponse.status(), build_sha: previewBuild, title: await page.title(), responsive_iframes: iframeCount,
      registered_images: imageState, baseline_layout: baselineLayout, adaptive_layout: adaptiveLayout,
      compact_320x480: compactLayout, large_430x932_font_1_3: await page.evaluate(() => ({ layout: window.NH01GeminiPreviewLayout.layout_version, font_scale: window.NH01GeminiPreviewLayout.font_scale, card_width_dp: window.NH01GeminiPreviewLayout.card_width_dp, card_height_dp: window.NH01GeminiPreviewLayout.card_height_dp })),
      layer_selection: await page.evaluate(() => window.NH01SelectedLayer), camera_button_toast: await page.locator(".preview-toast").innerText()
    };
    await page.screenshot({ path: resolve(args.output + ".png"), fullPage: true });
    assert(failedRequests.length === 0, "Same-origin requests failed: " + failedRequests.join("; "));
    assert(badResponses.length === 0, "Same-origin requests returned errors: " + badResponses.join("; "));
    assert(errors.length === 0, "Browser errors: " + errors.join("; "));
    report.status = "PASS";
  } finally {
    await browser.close();
    if (server) await new Promise(resolvePromise => server.close(resolvePromise));
  }
  await writeFile(args.output, JSON.stringify(report, null, 2) + "\n");
  process.stdout.write(JSON.stringify({ status: report.status, manager: report.checks.main, preview: { ...report.checks.preview, registered_images: report.checks.preview.registered_images.map(x => ({ asset: x.asset, naturalWidth: x.naturalWidth, naturalHeight: x.naturalHeight })) }, evidence: args.output }, null, 2) + "\n");
}
main().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
