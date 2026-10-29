#!/usr/bin/env node
import { createServer } from "node:http";
import { createReadStream, existsSync, statSync } from "node:fs";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, extname, sep, basename } from "node:path";
import { createHash } from "node:crypto";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
let activeBrowser = null;
let activeServer = null;

function parseArgs(argv) {
  const args = { root: process.cwd(), photo: null, output: null };
  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--root") args.root = resolve(argv[++i]);
    else if (token === "--photo") args.photo = resolve(argv[++i]);
    else if (token === "--output-dir") args.output = resolve(argv[++i]);
    else throw new Error(`Unknown argument: ${token}`);
  }
  if (!args.photo || !args.output) throw new Error("Usage: export_swos_static_frames.mjs --photo <local-photo> --output-dir <private-output-dir> [--root <repo-or-site-root>]");
  return args;
}

function startServer(root) {
  const absoluteRoot = resolve(root);
  const mime = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8", ".json": "application/json; charset=utf-8", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".md": "text/markdown; charset=utf-8" };
  const server = createServer((request, response) => {
    let pathname;
    try { pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname); }
    catch { response.writeHead(400).end("Bad path"); return; }
    let file = resolve(absoluteRoot, `.${pathname}`);
    if (file !== absoluteRoot && !file.startsWith(absoluteRoot + sep)) { response.writeHead(403).end("Forbidden"); return; }
    if (existsSync(file) && statSync(file).isDirectory()) file = resolve(file, "index.html");
    if (!existsSync(file) || !statSync(file).isFile()) { response.writeHead(404).end("Not found"); return; }
    response.writeHead(200, { "Content-Type": mime[extname(file).toLowerCase()] || "application/octet-stream", "Cache-Control": "no-store" });
    if (request.method === "HEAD") response.end();
    else createReadStream(file).pipe(response);
  });
  return new Promise((resolvePromise, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => resolvePromise({ server, url: `http://127.0.0.1:${server.address().port}/design/manager/` }));
  });
}

function assert(condition, message) { if (!condition) throw new Error(message); }
function pngSize(bytes) { return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) }; }

async function main() {
  const args = parseArgs(process.argv);
  await mkdir(args.output, { recursive: true });
  const photoBytes = await readFile(args.photo);
  const photoSha = createHash("sha256").update(photoBytes).digest("hex");
  const { server, url } = await startServer(args.root);
  activeServer = server;
  const browser = await chromium.launch({ headless: true });
  activeBrowser = browser;
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 3 });
  const errors = [];
  const failures = [];
  const requests = [];
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  page.on("pageerror", error => errors.push(error.message));
  page.on("requestfailed", request => failures.push(`${request.url()} · ${request.failure()?.errorText || "failed"}`));
  page.on("request", request => requests.push({ url: request.url(), method: request.method() }));
  const route = "#page/recognition_flow_v1_2/hifi/swos_static_visual_v1_1";
  await page.goto(url + route, { waitUntil: "networkidle", timeout: 45000 });
  await page.locator(".swos-workspace").waitFor({ state: "visible", timeout: 30000 });
  await page.locator(".swos-photo-input").setInputFiles(args.photo);
  await page.waitForFunction(sha => document.querySelector(".swos-photo-meta")?.textContent?.includes(sha), photoSha, { timeout: 30000 });
  await page.waitForFunction(() => {
    const images = [...document.querySelectorAll(".swos-frame-card .swos-photo-foreground")];
    return images.length === 15 && images.every(img => img.complete && img.naturalWidth > 0);
  }, null, { timeout: 30000 });
  const downloadedOrPostedPhoto = requests.some(request => request.method !== "GET" || request.url.includes(basename(args.photo)));
  assert(!downloadedOrPostedPhoto, "local photo selection unexpectedly caused an HTTP upload/request");
  assert(await page.locator(".swos-frame-card").count() === 15, "expected exactly 15 static frame cards");
  assert(await page.locator(".swos-frame-card .swos-gold-core").count() >= 8, "fixture SHA should enable manual review contour on gold frames");
  const ids = ["P0", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "R0", "R0.5", "R1", "R2", "R3", "R4", "R5"];
  const frameFiles = [];
  for (const id of ids) {
    await page.locator(`.swos-frame-card[data-frame-id="${id}"]`).click();
    const dialog = page.locator(".swos-zoom-dialog");
    await dialog.waitFor({ state: "visible" });
    await page.waitForFunction(expected => document.querySelector("[data-zoom-title]")?.textContent?.startsWith(expected), id);
    const target = resolve(args.output, `${id.replaceAll(".", "_")}.png`);
    const phone = page.locator(".swos-zoom-screen .swos-phone");
    const box = await phone.boundingBox();
    assert(box && box.width === 360 && box.height === 640, `${id} capture frame geometry was ${JSON.stringify(box)}, expected 360×640 CSS px`);
    await phone.screenshot({ path: target, animations: "disabled", caret: "hide" });
    const dimensions = pngSize(await readFile(target));
    assert(dimensions.width === 1080 && dimensions.height === 1920, `${id} export dimensions were ${JSON.stringify(dimensions)}, expected 1080×1920 px`);
    frameFiles.push({ id, filename: basename(target), width: dimensions.width, height: dimensions.height });
    await page.keyboard.press("Escape");
    await dialog.waitFor({ state: "hidden" });
  }
  assert(errors.length === 0, `browser errors: ${errors.join("; ")}`);
  assert(failures.length === 0, `request failures: ${failures.join("; ")}`);
  const report = {
    result: "STATIC_VISUAL_REVIEW_PASS",
    source_photo_id: `sha256:${photoSha}`,
    source_photo_sha256: photoSha,
    source_filename: basename(args.photo),
    source_dimensions_css: await page.locator(".swos-photo-foreground").first().evaluate(img => ({ width: img.naturalWidth, height: img.naturalHeight })),
    browser: await page.evaluate(() => ({ userAgent: navigator.userAgent, devicePixelRatio: devicePixelRatio, canvas2d: (() => { try { return !!document.createElement("canvas").getContext("2d"); } catch { return false; } })(), webgl2: (() => { try { return !!document.createElement("canvas").getContext("webgl2"); } catch { return false; } })() })),
    frame_count: frameFiles.length,
    exports: frameFiles,
    geometry_review: ["P7", "R0", "R0.5", "R1"],
    fish_geometry: "MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK",
    photo_local_only: true,
    upload_request_detected: false,
    playback_implemented: false,
    android_runtime: "NOT_RUN",
    errors,
    failed_requests: failures
  };
  await writeFile(resolve(args.output, "SWOS_Static_Visual_Browser_Report.json"), JSON.stringify(report, null, 2) + "\n");
  process.stdout.write(`PASS: exported 15 SWOS static frames at 1080×1920; photo SHA ${photoSha}; local-only=${report.photo_local_only}; WebGL2=${report.browser.webgl2}; Canvas2D=${report.browser.canvas2d}\n`);
  await browser.close(); activeBrowser = null;
  await new Promise(resolvePromise => server.close(resolvePromise)); activeServer = null;
}

main().catch(async error => {
  if (activeBrowser) await activeBrowser.close().catch(() => {});
  if (activeServer) await new Promise(resolvePromise => activeServer.close(resolvePromise));
  process.stderr.write(`FAIL: ${error.stack || error.message}\n`);
  process.exitCode = 1;
});
