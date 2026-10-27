#!/usr/bin/env node

import { createRequire } from "node:module";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

const require = createRequire(import.meta.url);
const moduleRoot = process.env.PLAYWRIGHT_MODULE_DIR;
const playwrightPath = process.env.PLAYWRIGHT_PATH || (moduleRoot ? resolve(moduleRoot, "playwright") : "playwright");
const { chromium } = require(playwrightPath);
const WATER_ROUTE = "#page/recognition_flow_v1_2/hifi/fishtrace_sites_web_hifi_workspace";
const ASSET_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";

function parseArgs(argv) {
  const result = { url: null, expectedBuild: null, output: null };
  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--url") result.url = argv[++i];
    else if (arg === "--expected-build") result.expectedBuild = argv[++i];
    else if (arg === "--output") result.output = argv[++i];
    else throw new Error("Unknown argument: " + arg);
  }
  if (!result.url || !result.expectedBuild) throw new Error("Usage: verify_design_manager_preview_smoke.mjs --url URL --expected-build SHA [--output FILE]");
  return result;
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function readJson(url, label) {
  const response = await fetch(url, { redirect: "follow", cache: "no-store" });
  assert(response.ok, label + " returned HTTP " + response.status + ": " + url);
  return await response.json();
}

async function run() {
  const args = parseArgs(process.argv);
  const pageUrl = new URL(args.url);
  const managerSuffix = "/design/manager/index.html";
  assert(pageUrl.pathname.endsWith(managerSuffix), "Candidate URL must end at the Design Manager index.html");
  assert(pageUrl.searchParams.get("preview") === args.expectedBuild, "Candidate URL preview parameter must match the expected source SHA");
  assert(pageUrl.hash === WATER_ROUTE, "Candidate URL must open the FishTrace high-fidelity workspace route");

  const previewPath = pageUrl.pathname.slice(0, -managerSuffix.length);
  const previewRoot = new URL(previewPath.replace(/\/?$/, "/"), pageUrl.origin);
  const sourceManifest = await readJson(new URL("source-manifest.json", previewRoot), "candidate source manifest");
  const buildMarker = await readJson(new URL("design/manager/build.json", previewRoot), "candidate build marker");
  assert(sourceManifest.candidate_source_sha === args.expectedBuild, "Candidate source manifest SHA mismatch");
  assert(buildMarker.sha === args.expectedBuild, "Candidate Design Manager build SHA mismatch");

  const report = {
    schema: "FISHTRACE_CANDIDATE_RUNTIME_SMOKE_V1",
    candidate_sha: args.expectedBuild,
    candidate_url: pageUrl.href,
    source_manifest_sha: sourceManifest.candidate_source_sha,
    build_marker_sha: buildMarker.sha,
    checks: {},
    page_errors: [],
    console_errors: [],
    failed_requests: [],
    network_requests: [],
    status: "RUNNING"
  };
  let browser = null;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
    page.on("pageerror", error => report.page_errors.push(error.message));
    page.on("console", message => {
      if (message.type() === "error") report.console_errors.push(message.text());
    });
    page.on("requestfailed", request => report.failed_requests.push({
      url: request.url(),
      failure: request.failure()?.errorText || "unknown"
    }));
    page.on("request", request => report.network_requests.push({
      url: request.url(),
      method: request.method(),
      resource_type: request.resourceType()
    }));

    const response = await page.goto(pageUrl.href, { waitUntil: "domcontentloaded", timeout: 45000 });
    assert(response && response.status() === 200, "Candidate page must load with HTTP 200");
    await page.locator("#hifiViewTitle").waitFor({ state: "visible", timeout: 30000 });
    assert((await page.locator("#hifiViewTitle").innerText()).includes("FishTrace Sites"), "FishTrace high-fidelity route did not render");

    const iframeSelector = 'iframe[title="FishTrace Sites Web 高保真交互体验 V1.1"]';
    await page.locator(iframeSelector).waitFor({ state: "visible", timeout: 30000 });
    await page.waitForFunction(selector => {
      const frame = document.querySelector(selector);
      try {
        return Boolean(frame && frame.contentDocument && frame.contentDocument.readyState === "complete" &&
          frame.contentDocument.querySelector('[data-testid="fishtrace-sites-web-workspace"]'));
      } catch (_) { return false; }
    }, iframeSelector, { timeout: 30000 });
    const frame = page.frame({ url: /fishtrace_sites_web_workspace\.html/ });
    assert(Boolean(frame), "FishTrace workspace iframe did not finish loading");
    await frame.locator('[data-testid="fishtrace-sites-web-workspace"]').waitFor({ state: "visible", timeout: 30000 });
    report.checks.candidate_page_http_200 = true;
    report.checks.fishtrace_route_loaded = true;

    const requestsBeforeWater = report.network_requests.length;
    await frame.locator('[data-water-view="canvas-water"]').click();
    await frame.waitForFunction(() =>
      document.querySelector('[data-testid="canvas-water-lab"]')?.dataset.rendererStatus === "READY",
      null,
      { timeout: 30000 }
    );

    const asset = await frame.locator("#water-source-image").evaluate(image => ({
      complete: image.complete,
      natural_width: image.naturalWidth,
      natural_height: image.naturalHeight,
      sha256: image.dataset.sha256,
      loaded: image.dataset.assetVerified === "true"
    }));
    assert(asset.complete && asset.loaded, "Fixed demo photo did not finish verified loading");
    assert(asset.natural_width === 1152 && asset.natural_height === 1536, "Fixed demo photo dimensions mismatch");
    assert(asset.sha256 === ASSET_SHA, "Fixed demo photo SHA-256 mismatch");

    const stage = await frame.locator("#water-stage").evaluate(node => ({
      children: [...node.children].map(child => child.tagName.toLowerCase()).join(","),
      text: node.textContent.trim(),
      object_fit: getComputedStyle(document.querySelector("#water-source-image")).objectFit
    }));
    assert(stage.children === "img,canvas" && stage.text === "", "Photo stage must contain only the image and Canvas, with no in-photo text");
    assert(stage.object_fit === "contain", "Photo must use object-fit: contain");

    const canvasState = () => frame.locator("#water-overlay").evaluate(canvas => {
      const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
      let alpha_pixels = 0;
      for (let index = 3; index < data.length; index += 4) if (data[index] !== 0) alpha_pixels += 1;
      return {
        renderer: canvas.dataset.rendererStatus,
        current_time_ms: Number(canvas.dataset.currentTime),
        alpha_pixels,
        width: canvas.width,
        height: canvas.height,
        debug_region: canvas.dataset.debugRegion
      };
    });

    const controls = {
      play: await frame.locator("#water-play").count(),
      pause: await frame.locator("#water-pause").count(),
      reset: await frame.locator("#water-reset").count(),
      timeline: await frame.locator("#water-timeline").count(),
      p0: await frame.locator('[data-water-frame="P0"]').count(),
      p2: await frame.locator('[data-water-frame="P2"]').count()
    };
    assert(Object.values(controls).every(value => value === 1), "Playback, reset, timeline, or frame-jump control is missing");
    assert(await frame.locator("#water-play").isEnabled() && await frame.locator("#water-pause").isEnabled() &&
      await frame.locator("#water-reset").isEnabled() && await frame.locator("#water-timeline").isEnabled(),
      "Playback controls are disabled after Canvas initialization");

    await frame.locator('[data-water-frame="P0"]').click();
    const p0 = await canvasState();
    assert(p0.renderer === "READY" && p0.current_time_ms === 0 && p0.alpha_pixels === 0, "P0 must render the original photo with a clear Canvas");
    assert(p0.debug_region === "false", "Normal preview must have water-region debugging disabled");

    await frame.locator('[data-water-frame="P2"]').click();
    await frame.waitForFunction(() => Number(document.querySelector("#water-overlay")?.dataset.currentTime) === 950, null, { timeout: 3000 });
    const p2 = await canvasState();
    assert(p2.alpha_pixels > 0, "P2 Canvas must contain the current rendered water effect");
    report.checks.canvas_renderer_ready = true;
    report.checks.fixed_photo_sha_and_dimensions = true;
    report.checks.clean_p0_and_nonempty_p2 = true;

    await frame.locator('[data-water-frame="P0"]').click();
    await frame.locator("#water-play").click();
    await frame.waitForFunction(() => Number(document.querySelector("#water-overlay")?.dataset.currentTime) > 0, null, { timeout: 2500 });
    await frame.locator("#water-pause").click();
    const pausedText = await frame.locator("#water-current-time").innerText();
    await page.waitForTimeout(150);
    assert((await frame.locator("#water-current-time").innerText()) === pausedText, "Pause must freeze the timeline");
    await frame.locator("#water-timeline").evaluate(input => {
      input.value = "950";
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await frame.waitForFunction(() => Number(document.querySelector("#water-overlay")?.dataset.currentTime) === 950, null, { timeout: 3000 });
    await frame.locator("#water-reset").click();
    await frame.waitForFunction(() => Number(document.querySelector("#water-overlay")?.dataset.currentTime) === 0, null, { timeout: 3000 });
    const resetState = await canvasState();
    assert(resetState.alpha_pixels === 0, "Reset must return to a transparent P0 Canvas");
    report.checks.play_pause_reset_and_timeline = true;

    const waterRequests = report.network_requests.slice(requestsBeforeWater);
    const forbidden = waterRequests.filter(item =>
      item.method !== "GET" && item.method !== "HEAD" ||
      /inference|detector|classifier|upload/i.test(item.url)
    );
    assert(forbidden.length === 0, "Water preview made a photo upload or inference request: " + JSON.stringify(forbidden));
    assert(report.page_errors.length === 0, "Browser raised uncaught errors: " + JSON.stringify(report.page_errors));
    report.checks.no_photo_upload_or_inference_request = true;
    report.checks.no_uncaught_browser_errors = true;
    report.checks.controls = controls;
    report.checks.photo = asset;
    report.checks.p0 = p0;
    report.checks.p2 = p2;
    report.checks.reset = resetState;
    report.status = "PASS";
    await page.close();
  } catch (error) {
    report.status = "FAIL";
    report.error = error.stack || error.message;
  } finally {
    if (browser) await browser.close().catch(() => {});
    if (args.output) {
      await mkdir(dirname(resolve(args.output)), { recursive: true });
      await writeFile(resolve(args.output), JSON.stringify(report, null, 2) + "\n", "utf8");
    }
    process.stdout.write(JSON.stringify(report, null, 2) + "\n");
  }
  if (report.status !== "PASS") process.exitCode = 1;
}

run().catch(error => {
  process.stderr.write((error.stack || error.message) + "\n");
  process.exitCode = 1;
});
