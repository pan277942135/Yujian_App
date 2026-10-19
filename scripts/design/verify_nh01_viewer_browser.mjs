import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const moduleDir = process.env.PLAYWRIGHT_MODULE_DIR;
if (!moduleDir) throw new Error("Set PLAYWRIGHT_MODULE_DIR to a Playwright node_modules directory");
const { chromium } = await import(pathToFileURL(path.join(moduleDir, "playwright/index.mjs")).href);
const baseUrl = process.argv.find(value => value.startsWith("--base-url="))?.slice("--base-url=".length) || "http://127.0.0.1:8765";
const expectedSourceSha = process.argv.find(value => value.startsWith("--expected-source-sha="))?.slice("--expected-source-sha=".length) || null;
const requireBrowserDiff = process.argv.includes("--require-browser-diff");
const outputDir = process.argv.find(value => value.startsWith("--output-dir="))?.slice("--output-dir=".length) || ".tmp/nh01-browser";
fs.mkdirSync(outputDir, { recursive: true });
const browser = await chromium.launch({ headless: true });
const errors = [];
try {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  const url = `${baseUrl}/design/manager/nh01_acceptance/?mode=structured&export=1`;
  const response = await page.goto(url, { waitUntil: "networkidle" });
  if (!response?.ok()) throw new Error(`Viewer route failed: ${response?.status()}`);
  await page.waitForFunction(() => window.NH01ViewerModelReady === true, { timeout: 10000 });
  const loadError = await page.locator(".load-error").count();
  if (loadError) throw new Error(await page.locator(".load-error").innerText());
  const artboard = page.locator("#structuredPreview .artboard");
  await artboard.waitFor({ state: "visible" });
  const artboardBox = await artboard.boundingBox();
  if (!artboardBox || Math.round(artboardBox.width) !== 1080 || Math.round(artboardBox.height) !== 1920) {
    throw new Error(`Structured artboard dimensions differ from Frozen reference: ${JSON.stringify(artboardBox)}`);
  }
  const loadedAssets = await artboard.locator("img").evaluateAll(nodes => nodes.map(img => ({ src: img.getAttribute("src"), loaded: img.complete && img.naturalWidth > 0, width: img.naturalWidth, height: img.naturalHeight })));
  const brokenAssets = loadedAssets.filter(asset => !asset.loaded);
  if (brokenAssets.length) throw new Error(`Structured assets failed to load: ${JSON.stringify(brokenAssets)}`);
  const frozenImage = await page.locator("#frozenLarge").evaluate(img => ({ naturalWidth: img.naturalWidth, naturalHeight: img.naturalHeight }));
  if (frozenImage.naturalWidth !== 1080 || frozenImage.naturalHeight !== 1920) throw new Error(`Frozen reference dimensions unexpected: ${JSON.stringify(frozenImage)}`);

  await artboard.screenshot({ path: path.join(outputDir, "NH01_Structured_Preview_1080x1920.png") });
  const browserGeometry = await page.evaluate(() => {
    const art = document.querySelector("#structuredPreview .artboard");
    const data = window.NH01ViewerData;
    const rectOf = node => {
      const r = node.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom };
    };
    const layout = data.structure.preview_layout.map(item => {
      const nodes = [...art.querySelectorAll(`[data-component-id="${item.component_id}"]`)];
      return { id: item.id, component_id: item.component_id, contract_rect: item.rect, measured_dom_rects: nodes.map(rectOf), evidence_status: item.evidence_status, status: nodes.length ? (item.evidence_status.includes("AUTHORITY") || item.evidence_status.includes("BLOCKED") ? "REVIEW_REQUIRED" : "MEASURED") : "NOT_RUN" };
    });
    return { schema_version: 1, classification: "BROWSER_DOM_BOUNDS_NOT_ANDROID_BOUNDSINWINDOW", source_sha: document.querySelector('meta[name="nh01-source-sha"]')?.content || null, viewport: { width: innerWidth, height: innerHeight, device_scale_factor: devicePixelRatio }, artboard: rectOf(art), components: layout };
  });
  fs.writeFileSync(path.join(outputDir, "NH01_Browser_Bounds.json"), `${JSON.stringify(browserGeometry, null, 2)}\n`);

  // The export-only route has no visible controls. Reopen the interactive
  // Viewer before exercising navigation and Inspector controls.
  const interactiveUrl = `${baseUrl}/design/manager/nh01_acceptance/?mode=structured`;
  const interactiveResponse = await page.goto(interactiveUrl, { waitUntil: "networkidle" });
  if (!interactiveResponse?.ok()) throw new Error(`Interactive Viewer route failed: ${interactiveResponse?.status()}`);
  await page.waitForFunction(() => window.NH01ViewerModelReady === true, { timeout: 10000 });

  const tabs = await page.locator(".mode-button").evaluateAll(nodes => nodes.map(node => node.dataset.mode));
  for (const mode of ["frozen", "structured", "inspector", "runtime", "compare", "heatmap"]) {
    if (!tabs.includes(mode)) throw new Error(`Missing viewer mode: ${mode}`);
  }
  const sourceSha = await page.locator('meta[name="nh01-source-sha"]').getAttribute("content");
  if (expectedSourceSha && sourceSha !== expectedSourceSha) throw new Error(`Viewer source SHA ${sourceSha} does not match expected ${expectedSourceSha}`);
  const measuredSourceSha = sourceSha?.match(/^[0-9a-f]{40}$/i) ? sourceSha : null;
  const frozenShaText = await page.locator("#frozenShaShort").innerText();
  if (!frozenShaText.startsWith("6ab9d3348b4a9a7e")) throw new Error(`Frozen source SHA is not shown: ${frozenShaText}`);

  await page.locator('[data-mode="structured"]').click();
  await page.locator("#showBounds").check();
  if (await page.locator("#structuredPreview .bounds-box").count() < 5) throw new Error("Structured bounds overlay did not render mapped component boxes");
  await page.locator("#showBounds").uncheck();

  await page.locator('[data-mode="inspector"]').click();
  await page.locator('#inspectorTree .tree-row[data-component-id="camera"]').click();
  const cameraInspector = await page.locator("#propertyInspector").innerText();
  if (!cameraInspector.includes("Camera artwork") || !cameraInspector.includes("REVIEW_REQUIRED")) throw new Error("Inspector did not retain camera contract conflict/status");

  await page.locator('[data-mode="compare"]').click();
  await page.locator("#comparePair").selectOption("frozen-android");
  const runtimeModel = await page.evaluate(() => window.NH01ViewerData.runtime);
  const browserDiffModel = await page.evaluate(() => window.NH01ViewerData.browserDiff);
  if (requireBrowserDiff && browserDiffModel?.status !== "REVIEW_REQUIRED") throw new Error(`Structured preview diff is not recorded: ${browserDiffModel?.status || "NOT_RUN"}`);
  if (browserDiffModel?.status === "REVIEW_REQUIRED") {
    const diffImages = await page.locator("#browserDiffImages img").evaluateAll(nodes => nodes.map(img => ({ complete: img.complete, width: img.naturalWidth })));
    if (diffImages.length !== 3 || diffImages.some(img => !img.complete || img.width !== 1080)) throw new Error(`Structured comparison images failed to load: ${JSON.stringify(diffImages)}`);
    if (!(await page.locator("#browserDiffNote").innerText()).includes("DIAGNOSTIC_ONLY_REVIEW_REQUIRED")) throw new Error("Structured preview diff was not clearly marked diagnostic-only");
  }
  if (runtimeModel.artifacts?.screenshot) {
    const androidCompare = page.locator("#compareWorkspace .compare-pane").nth(1).locator("img");
    await androidCompare.waitFor({ state: "visible" });
    const androidLoaded = await androidCompare.evaluate(img => img.complete && img.naturalWidth === 1080 && img.naturalHeight === 1920);
    if (!androidLoaded) throw new Error("Exact-SHA Android screenshot failed to load in compare mode");
    if (!(await page.locator("#compareStatus").innerText()).includes(runtimeModel.source.expected_source_sha)) throw new Error("Android compare mode lost its exact source SHA");
  } else if (!(await page.locator("#compareStatus").innerText()).includes("NOT_RUN")) {
    throw new Error("Android compare mode did not preserve NOT_RUN status");
  }
  await page.locator("#comparePair").selectOption("frozen-structured");
  for (const mode of ["overlay", "opacity", "wipe", "side"]) {
    await page.locator("#compareMode").selectOption(mode);
    if (await page.locator("#compareWorkspace").getAttribute("data-mode") !== mode) throw new Error(`Compare display mode did not switch to ${mode}`);
  }
  await page.locator("#compareOpacity").evaluate(node => { node.value = "0.73"; node.dispatchEvent(new Event("input", { bubbles: true })); });
  if (await page.locator("#compareOpacityValue").innerText() !== "73%") throw new Error("Compare opacity slider did not update its displayed value");

  await page.locator('[data-mode="heatmap"]').click();
  await page.locator("#heatZoom").selectOption("4");
  if (await page.locator("#heatmapImage").evaluate(node => getComputedStyle(node).getPropertyValue("--zoom").trim()) !== "4") throw new Error("Heatmap zoom control did not apply the selected multiplier");
  await page.locator('[data-mode="runtime"]').click();
  if (runtimeModel.artifacts?.screenshot) {
    const runtimeImage = page.locator("#runtimeImageWrap img");
    await runtimeImage.waitFor({ state: "visible" });
    const runtimeLoaded = await runtimeImage.evaluate(img => img.complete && img.naturalWidth === 1080 && img.naturalHeight === 1920);
    if (!runtimeLoaded) throw new Error("Native Android screenshot failed to render in Runtime panel");
  } else if (!(await page.locator("#runtimeEmptyTitle").innerText()).includes(runtimeModel.status || "NOT_RUN")) {
    throw new Error("Runtime panel lost its actual NOT_RUN/BLOCKED status");
  }
  await page.locator('[data-mode="heatmap"]').click();
  if (runtimeModel.artifacts?.pixel_heatmap && await page.locator("#heatmapImage img").count() !== 1) throw new Error("Android Pixel Heatmap was not rendered");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/design/manager/nh01_acceptance/`, { waitUntil: "networkidle" });
  await page.waitForFunction(() => window.NH01ViewerModelReady === true, { timeout: 10000 });
  const mobile = await page.evaluate(() => {
    const overflowCandidates = [...document.querySelectorAll("body *")]
      .map(node => {
        const rect = node.getBoundingClientRect();
        const style = getComputedStyle(node);
        return {
          tag: node.tagName.toLowerCase(),
          id: node.id || null,
          class_name: typeof node.className === "string" ? node.className : null,
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
          client_width: node.clientWidth,
          scroll_width: node.scrollWidth,
          overflow_x: style.overflowX
        };
      })
      .filter(node => node.right > innerWidth + 1 || node.left < -1)
      .sort((a, b) => b.right - a.right)
      .slice(0, 12);
    return {
      viewport: innerWidth,
      document: document.documentElement.scrollWidth,
      overflow_candidates: overflowCandidates
    };
  });
  if (mobile.document > mobile.viewport + 1) throw new Error(`Viewer has document-level horizontal overflow on mobile: ${JSON.stringify(mobile)}`);
  if (errors.length) throw new Error(`Browser errors: ${errors.join(" | ")}`);

  fs.writeFileSync(path.join(outputDir, "NH01_Browser_Viewer_Report.json"), `${JSON.stringify({
    status: "PASS",
    gate: "G2_CLOUD_PREVIEW_VALID",
    source_sha: measuredSourceSha,
    scope: "Static Viewer route, asset loading, six navigation modes, Inspector, NOT_RUN controls and mobile shell",
    runtime_status: runtimeModel.status || "NOT_RUN",
    mobile_measurement: mobile,
    browser_preview_diff_status: browserDiffModel?.status || "NOT_RUN",
    visual_parity_status: await page.evaluate(() => window.NH01ViewerData.evidence.gate_status.G5_VISUAL_PARITY_VALID || "NOT_RUN"),
    errors,
    screenshots: ["NH01_Structured_Preview_1080x1920.png"],
    browser_bounds: "NH01_Browser_Bounds.json",
    checked_at_utc: new Date().toISOString()
  }, null, 2)}\n`);
  console.log(`NH01_BROWSER_VIEWER=PASS screenshot=${path.join(outputDir, "NH01_Structured_Preview_1080x1920.png")}`);
} finally {
  await browser.close();
}
