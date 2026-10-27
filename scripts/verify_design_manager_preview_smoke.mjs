#!/usr/bin/env node

import { createRequire } from "node:module";
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";

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
      let alpha_pixels = 0, hash = 2166136261;
      for (let index = 0; index < data.length; index += 1) {
        if (index % 4 === 3 && data[index] !== 0) alpha_pixels += 1;
        hash = Math.imul(hash ^ data[index], 16777619);
      }
      return { renderer: canvas.dataset.rendererStatus, current_time_ms: Number(canvas.dataset.currentTime), alpha_pixels,
        width: canvas.width, height: canvas.height, debug_region: canvas.dataset.debugRegion,
        backend: canvas.dataset.canvasBackend, algorithm: canvas.dataset.activeAlgorithm,
        preview_mode: canvas.dataset.previewMode, pixel_hash: (hash >>> 0).toString(16).padStart(8, "0") };
    });
    const controls = {
      play: await frame.locator("#water-play").count(), pause: await frame.locator("#water-pause").count(),
      reset: await frame.locator("#water-reset").count(), timeline: await frame.locator("#water-timeline").count(),
      p3: await frame.locator('[data-water-frame="P3"]').count(), enabled: await frame.locator("#water-enabled").count(),
      strength: await frame.locator("#water-strength").count()
    };
    assert(Object.values(controls).every(value => value === 1), "P3 preview must preserve transport, timeline, frame, and layer controls");
    assert(!(await frame.locator("#water-play").isEnabled()) && !(await frame.locator("#water-pause").isEnabled()) &&
      !(await frame.locator("#water-timeline").isEnabled()) && !(await frame.locator('[data-water-frame="P3"]').isEnabled()),
      "P3 static acceptance must keep the P0-P7 animation controls frozen");
    assert(await frame.locator("#water-reset").isEnabled() && await frame.locator("#water-enabled").isEnabled() &&
      await frame.locator("#water-strength").isEnabled(), "Static preview reset and optical comparison controls must remain usable");
    const labMode = await frame.locator('[data-testid="canvas-water-lab"]').evaluate(node => ({
      mode: node.dataset.previewMode, approval: node.dataset.visualApprovalStatus,
      stage: document.querySelector("#water-current-stage")?.textContent,
      time: document.querySelector("#water-current-time")?.textContent,
      playing: document.querySelector("#water-play-state")?.textContent
    }));
    assert(labMode.mode === "P3_STATIC_1200MS" && labMode.approval === "CANDIDATE_NOT_VISUALLY_APPROVED", "P3 static candidate mode is missing");
    assert(labMode.stage === "P3 静态验收" && labMode.time === "1200 ms" && labMode.playing.includes("已暂停"), "P3 time and paused state are not shown outside the photo");
    const p3 = await canvasState();
    assert(p3.renderer === "READY" && p3.current_time_ms === 1200 && p3.alpha_pixels > 0, "P3 must render a visible static caustic field at 1200 ms");
    assert(p3.backend === "2d" && p3.algorithm === "ethanfox_sampleCaustic_canvas2d" && p3.preview_mode === "P3_STATIC_1200MS", "The active renderer is not the pinned continuous Canvas 2D sampler");
    assert(p3.debug_region === "false", "Normal P3 preview must have water-region debugging disabled");
    assert((await frame.locator("#water-timeline").inputValue()) === "1200", "The existing timeline control must remain visibly fixed at P3");
    report.checks.canvas_renderer_ready = true;
    report.checks.fixed_photo_sha_and_dimensions = true;
    report.checks.p3_static_renderer_and_timeline_frozen = true;
    const rendererSource = await frame.evaluate(async () => {
      const url = new URL("fishtrace_canvas_water_lab.js", location.href);
      const response = await fetch(url);
      return response.ok ? response.text() : "";
    });
    assert(rendererSource.includes("sampleCaustic") && rendererSource.includes("de856eec62819b7c4176366db73b8fd37a1b1455") &&
      rendererSource.includes('getContext("2d"') && !/getContext\s*\(\s*["']webgl2?["']/.test(rendererSource),
      "Canvas source attribution or 2D-only renderer check failed");
    report.checks.pinned_upstream_sampler_and_canvas_2d_only = true;
    if (args.output) {
      const evidenceDir = dirname(resolve(args.output));
      await mkdir(evidenceDir, { recursive: true });
      const photoOverlay = join(evidenceDir, "fishtrace-p3-photo-overlay.png");
      const transparentCanvas = join(evidenceDir, "fishtrace-p3-transparent-canvas.png");
      await frame.locator("#water-stage").screenshot({ path: photoOverlay });
      await frame.locator("#water-overlay").screenshot({ path: transparentCanvas, omitBackground: true });
      report.checks.screenshots = { photo_overlay: photoOverlay, transparent_canvas: transparentCanvas };
    }
    await frame.locator("#water-enabled").uncheck();
    const disabledWater = await canvasState();
    assert(disabledWater.alpha_pixels === 0 && disabledWater.current_time_ms === 1200, "Disabling water must clear only the static P3 optical overlay");
    await frame.locator("#water-enabled").check();
    const repeatedP3 = await canvasState();
    assert(repeatedP3.pixel_hash === p3.pixel_hash, "Repeated static P3 render must be deterministic");
    await frame.locator("#water-reset").click();
    const resetState = await canvasState();
    assert(resetState.current_time_ms === 1200 && resetState.pixel_hash === p3.pixel_hash, "Reset must restore the original P3 preview state");
    report.checks.p3_toggle_repeatability_and_reset = true;
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
    report.checks.p3 = p3;
    report.checks.toggle_off = disabledWater;
    report.checks.repeated_p3 = repeatedP3;
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
