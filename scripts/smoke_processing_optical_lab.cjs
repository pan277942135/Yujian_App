const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "playwright"));

(async () => {
  const out = process.env.LAB_OUTPUT_DIR || "optical-lab-artifacts";
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 }, deviceScaleFactor: 1 });
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await page.goto("http://127.0.0.1:8765/design/manager/optical-lab/", { waitUntil: "networkidle", timeout: 120000 });
  await page.waitForFunction(() => document.querySelector("#staticGate")?.textContent.includes("ENGINEERING MODEL CHECKS PASS"), null, { timeout: 120000 });
  await page.waitForFunction(() => {
    const image = document.querySelector("#frozenImage");
    return image && image.complete && image.naturalWidth === 941 && image.naturalHeight === 1672 && document.querySelector("#photoName")?.textContent.includes("SHA-256 verified");
  }, null, { timeout: 120000 });

  const stateButtons = await page.locator("#stateButtons button").count();
  assert.equal(stateButtons, 3, "the browser UI must expose exactly three product states");
  assert.equal(await page.locator("#paramMotionSpeed").isDisabled(), true);
  assert.equal(await page.locator("#compareModes button").count(), 5);
  const referenceStates = [];
  await page.locator("#stateButtons button").nth(1).click();
  assert.match(await page.locator("#focusReadiness").textContent(), /无真实 BBox，因此关闭/);
  assert.equal(await page.locator("#motionTimeline").isDisabled(), true, "Stage B timeline stays locked before static visual review");
  for (let i = 0; i < 3; i++) {
    await page.locator("#stateButtons button").nth(i).click();
    await page.waitForTimeout(40);
    const record = await page.evaluate(() => ({
      state: document.querySelector("#stateLabel").textContent,
      frozenWidth: document.querySelector("#frozenImage").naturalWidth,
      frozenHeight: document.querySelector("#frozenImage").naturalHeight,
      previewWidth: document.querySelector("#previewCanvas").width,
      previewHeight: document.querySelector("#previewCanvas").height,
    }));
    assert.equal(record.frozenWidth, 941);
    assert.equal(record.frozenHeight, 1672);
    referenceStates.push(record.state);
    const png = await page.locator("#previewCanvas").evaluate(canvas => canvas.toDataURL("image/png"));
    fs.writeFileSync(path.join(out, "web-" + record.state + ".png"), Buffer.from(png.split(",")[1], "base64"));
    await page.locator("#frozenImage").screenshot({ path: path.join(out, "frozen-" + record.state + ".png") });
  }
  assert.deepEqual(referenceStates, ["IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING"]);

  await page.locator("#stateButtons button").nth(0).click();
  const basePixels = await page.locator("#previewCanvas").evaluate(canvas => Array.from(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data));
  await page.selectOption("#rendererSelect", "hardware-gradient");
  const hardwarePixels = await page.locator("#previewCanvas").evaluate(canvas => Array.from(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data));
  let changed = 0;
  for (let i = 0; i < basePixels.length; i += 4) {
    if (basePixels[i] !== hardwarePixels[i] || basePixels[i + 1] !== hardwarePixels[i + 1] || basePixels[i + 2] !== hardwarePixels[i + 2]) changed++;
  }
  assert.ok(changed > 0, "hardware-gradient should differ from current Android approximation");
  await page.selectOption("#rendererSelect", "software-cache");
  const softwarePixels = await page.locator("#previewCanvas").evaluate(canvas => Array.from(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data));
  let softwareChanged = 0;
  for (let i = 0; i < basePixels.length; i += 4) {
    if (basePixels[i] !== softwarePixels[i] || basePixels[i + 1] !== softwarePixels[i + 1] || basePixels[i + 2] !== softwarePixels[i + 2]) softwareChanged++;
  }
  assert.ok(softwareChanged > 0, "software-cache should differ from current Android approximation");
  await page.selectOption("#rendererSelect", "android-current");
  await page.locator("#paramAlpha").evaluate(input => { input.value = "0.5"; input.dispatchEvent(new Event("input", { bubbles: true })); });
  const alphaPixels = await page.locator("#previewCanvas").evaluate(canvas => Array.from(canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data));
  let alphaChanged = 0;
  for (let i = 0; i < basePixels.length; i += 4) {
    if (basePixels[i] !== alphaPixels[i] || basePixels[i + 1] !== alphaPixels[i + 1] || basePixels[i + 2] !== alphaPixels[i + 2]) alphaChanged++;
  }
  assert.ok(alphaChanged > 0, "parameter changes should redraw the preview immediately");
  await page.locator("#resetButton").click();

  await page.locator("#compareModes [data-mode='overlay']").click();
  await page.locator("#compareModes [data-mode='wipe']").click();
  await page.locator("#wipeInput").fill("68");
  await page.locator("#compareModes [data-mode='diff']").click();
  await page.waitForFunction(() => document.querySelector("#metricMean")?.textContent !== "—");
  const metrics = await page.evaluate(() => ({
    mean: document.querySelector("#metricMean").textContent,
    threshold: document.querySelector("#metricThreshold").textContent,
    roi: document.querySelector("#metricRoi").textContent,
    gate: document.querySelector("#metricGate").textContent,
  }));
  assert.ok(Number.isFinite(Number(metrics.mean)));
  assert.equal(metrics.gate, "探索性（源图未绑定）");
  await page.locator("#compareCanvas").evaluate(canvas => canvas.toDataURL("image/png")).then(data => {
    fs.writeFileSync(path.join(out, "pixel-diff-exploratory.png"), Buffer.from(data.split(",")[1], "base64"));
  });
  await page.locator("#roiCanvas").evaluate(canvas => canvas.toDataURL("image/png")).then(data => {
    fs.writeFileSync(path.join(out, "roi-side-by-side.png"), Buffer.from(data.split(",")[1], "base64"));
  });

  await page.locator("#compareModes [data-mode='candidates']").click();
  await page.locator("#candidateGrid").waitFor({ state: "visible" });
  await page.locator("#candidateGrid").screenshot({ path: path.join(out, "frozen-and-three-renderers.png") });
  async function saveDownload(selector) {
    const pending = page.waitForEvent("download");
    await page.locator(selector).click();
    const item = await pending;
    await item.saveAs(path.join(out, item.suggestedFilename()));
  }
  await saveDownload("#exportPreview");
  await saveDownload("#exportCompare");
  await saveDownload("#exportRoi");

  const responsive = [];
  for (const value of ["320x640", "720x1600", "1080x2340", "390x844", "800x360"]) {
    await page.selectOption("#viewportSelect", value);
    const dims = value.split("x").map(Number);
    const actual = await page.locator("#previewCanvas").evaluate(c => [c.width, c.height]);
    assert.deepEqual(actual, dims);
    responsive.push(value);
  }
  const summary = { result: "PASS", states: referenceStates, rendererChangedPixels: changed, softwareRendererChangedPixels: softwareChanged, parameterChangedPixels: alphaChanged, diff: metrics, responsive, fishFocus: "NO_BBOX_NO_FOCUS", stageB: "LOCKED_PENDING_STATIC_VISUAL_REVIEW", pageErrors: errors };
  fs.writeFileSync(path.join(out, "browser-smoke.json"), JSON.stringify(summary, null, 2));
  assert.deepEqual(errors, [], "no browser runtime or console errors");
  await browser.close();
  console.log(JSON.stringify(summary, null, 2));
})().catch(error => { console.error(error); process.exitCode = 1; });
