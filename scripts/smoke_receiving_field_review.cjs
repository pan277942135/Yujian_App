const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require(path.join(process.env.PLAYWRIGHT_MODULE_DIR || "", "playwright"));

const ROOT_URL = process.env.LAB_REPO_URL || "http://127.0.0.1:8765/design/manager/optical-lab/";
const OUT = process.env.LAB_OUTPUT_DIR || "optical-lab-artifacts";

function cornerSignal(canvas) {
  const ctx = canvas.getContext("2d");
  const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
  const photoWidth = Math.min(canvas.width, canvas.height * 0.75);
  const photoHeight = photoWidth / 0.75;
  const photo = { x: (canvas.width - photoWidth) / 2, y: (canvas.height - photoHeight) / 2, width: photoWidth, height: photoHeight };
  const corners = [
    { id: "top-left-gold", x: 0, w: 0.34, y: 0, h: 0.28, gold: true },
    { id: "top-right-blue", x: 0.66, w: 0.34, y: 0, h: 0.32, gold: false },
    { id: "bottom-left-gold", x: 0, w: 0.38, y: 0.70, h: 0.30, gold: true },
    { id: "bottom-right-blue", x: 0.62, w: 0.38, y: 0.68, h: 0.32, gold: false },
  ];
  return corners.map((corner) => {
    let covered = 0, samples = 0, role = 0, alpha = 0;
    const x0 = Math.max(0, Math.floor(photo.x + corner.x * photo.width));
    const x1 = Math.min(canvas.width, Math.ceil(photo.x + (corner.x + corner.w) * photo.width));
    const y0 = Math.max(0, Math.floor(photo.y + corner.y * photo.height));
    const y1 = Math.min(canvas.height, Math.ceil(photo.y + (corner.y + corner.h) * photo.height));
    for (let y = y0; y < y1; y += 3) for (let x = x0; x < x1; x += 3) {
      const i = (y * canvas.width + x) * 4;
      samples++;
      alpha += data[i + 3];
      if (data[i + 3] > 8) covered++;
      role += corner.gold ? data[i] - data[i + 2] : data[i + 2] - data[i];
    }
    return { id: corner.id, coverage_ratio: covered / Math.max(1, samples), mean_alpha: alpha / Math.max(1, samples), mean_color_role_delta: role / Math.max(1, samples) };
  });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true, args: ["--no-sandbox"] });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1080 }, deviceScaleFactor: 1 });
    const errors = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(ROOT_URL, { waitUntil: "networkidle", timeout: 30000 });
    await page.waitForFunction(() => {
      const overlay = document.querySelector("#receivingFieldReviewOverlay");
      const photo = document.querySelector("#previewCanvas");
      return overlay && overlay.width > 0 && overlay.height > 0 && overlay.dataset.reviewSource === "REVIEW_ONLY_RECEIVING_FIELD" && photo?.dataset.state;
    }, null, { timeout: 30000 });

    const sourceRevision = await windowRevision(page);
    const result = { task: "RECOGNITION_OPTICAL_STATIC_DYNAMIC_CLOSURE_V1", source_revision: sourceRevision, automation_signal_status: "RENDER_SIGNAL_ONLY_REVIEW_REQUIRED", visual_approval: "NOT_AUTOMATED", candidates: [], dynamic_states: [], browser_errors: errors };
    for (const candidate of ["A", "B", "C"]) {
      await page.locator("#previewModeButtons [data-preview-mode='static']").click();
      await page.locator("#staticCandidate").selectOption(candidate);
      await page.waitForTimeout(150);
      const overlay = await page.locator("#receivingFieldReviewOverlay").evaluate((canvas) => ({
        source: canvas.dataset.reviewSource,
        candidate: canvas.dataset.candidate,
        computed_background: getComputedStyle(canvas).backgroundColor,
        signal: (function () {
          const ctx = canvas.getContext("2d");
          const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
          let nonzero = 0, blue = 0, gold = 0;
          for (let i = 0; i < data.length; i += 16) {
            if (data[i + 3] > 8) nonzero++;
            if (data[i + 2] > data[i] + 10) blue++;
            if (data[i] > data[i + 2] + 10) gold++;
          }
          return { nonzero_samples: nonzero, blue_samples: blue, gold_samples: gold };
        })(),
      }));
      assert.equal(overlay.source, "REVIEW_ONLY_RECEIVING_FIELD");
      assert.equal(overlay.candidate, candidate);
      assert.equal(overlay.computed_background, "rgba(0, 0, 0, 0)", "Review overlay must remain transparent so it cannot hide the photo renderer.");
      assert.ok(overlay.signal.nonzero_samples > 100, `Receiving Field ${candidate} did not draw a visible overlay signal.`);
      assert.ok(overlay.signal.blue_samples > 10 && overlay.signal.gold_samples > 10, `Receiving Field ${candidate} lost the blue/gold hierarchy.`);
      const corners = await page.locator("#receivingFieldReviewOverlay").evaluate(cornerSignal);
      result.candidates.push({ id: `REVIEW_CANDIDATE_${candidate}`, corners, render_signal: overlay.signal });
      await page.locator("#boards").screenshot({ path: path.join(OUT, `static-candidate-${candidate}-desktop.png`) });
      await page.setViewportSize({ width: 390, height: 844 });
      await page.locator("#boards").screenshot({ path: path.join(OUT, `static-candidate-${candidate}-narrow.png`) });
      await page.setViewportSize({ width: 1440, height: 1080 });
    }

    await page.locator("#previewModeButtons [data-preview-mode='dynamic']").click();
    await page.locator("#dynamicCandidate").selectOption("B");
    await page.locator("#motionSpeed").selectOption("1");
    await page.locator("#playPause").click();
    const stateButtons = page.locator("#stateButtons button");
    const stateCount = await stateButtons.count();
    assert.equal(stateCount, 3, "Review preview must expose the three product visual states.");
    for (let index = 0; index < stateCount; index++) {
      await stateButtons.nth(index).click();
      await page.waitForTimeout(450);
      const state = await page.locator("#previewCanvas").getAttribute("data-state");
      const candidate = await page.locator("#receivingFieldReviewOverlay").getAttribute("data-candidate");
      result.dynamic_states.push({ state, candidate, playback: "WEB_REVIEW_ONLY" });
      await page.locator("#boards").screenshot({ path: path.join(OUT, `dynamic-balanced-state-${index + 1}.png`) });
    }
    assert.equal(new Set(result.dynamic_states.map((entry) => entry.state)).size, 3);
    assert.equal(errors.length, 0, `Browser page errors: ${errors.join(" | ")}`);
    fs.writeFileSync(path.join(OUT, "receiving-field-review-signal.json"), JSON.stringify(result, null, 2) + "\n");
  } finally {
    await browser.close();
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });

function windowRevision(page) {
  return page.evaluate(() => window.OPTICAL_LAB_BUILD_INFO__?.head_sha || "NOT_EMBEDDED");
}
