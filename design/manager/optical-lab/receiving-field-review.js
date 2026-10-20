/* Review-only Receiving Light layer. It is intentionally not an Android renderer. */
(function () {
  "use strict";
  const candidateLevels = { A: 0.75, B: 1.5, C: 2.25 };
  const breathAmplitudes = { A: 0.045, B: 0.105, C: 0.16 };
  const weights = [1, 0.72, 0.38, 0.25];
  const phase = [0.1, 1.83, 3.47, 5.02];
  const periods = [9.2, 12.8, 10.6, 15.4];
  let frame = 0;
  let overlay;
  let overlayContext;
  let lastPaint = 0;

  function value(id, fallback) {
    const node = document.getElementById(id);
    return node ? node.value : fallback;
  }

  function install() {
    const photoCanvas = document.getElementById("previewCanvas");
    const screen = photoCanvas && photoCanvas.parentElement;
    if (!photoCanvas || !screen) {
      window.setTimeout(install, 100);
      return;
    }
    overlay = document.getElementById("receivingFieldReviewOverlay");
    if (!overlay) {
      overlay = document.createElement("canvas");
      overlay.id = "receivingFieldReviewOverlay";
      overlay.setAttribute("aria-hidden", "true");
      // .screen canvas sets an opaque background for the base renderer. Override
      // it here so this auxiliary canvas cannot cover the photo underneath.
      overlay.style.cssText = "position:absolute;inset:0;width:100%;height:100%;pointer-events:none;z-index:2;background:transparent;";
      screen.appendChild(overlay);
    }
    overlayContext = overlay.getContext("2d", { alpha: true });
    const provenanceRow = document.querySelector(".preview-meta-row");
    if (provenanceRow && !document.getElementById("receivingFieldReviewLabel")) {
      const label = document.createElement("span");
      label.id = "receivingFieldReviewLabel";
      label.className = "tiny";
      label.textContent = "Receiving Field candidate: REVIEW-ONLY overlay · Native renderer unchanged";
      provenanceRow.appendChild(label);
    }
    patchPreviewExports(photoCanvas);
    const observer = new MutationObserver(() => paint(performance.now()));
    observer.observe(photoCanvas, { attributes: true, attributeFilter: ["width", "height", "data-state", "data-candidate", "data-preview-mode", "data-preview-time"] });
    ["staticCandidate", "dynamicCandidate", "fieldLayerView", "fieldToggle", "playPause", "motionSpeed"].forEach((id) => {
      const node = document.getElementById(id);
      if (node) node.addEventListener("change", () => { updateCandidateCaption(); paint(performance.now()); });
      if (node && id === "playPause") node.addEventListener("click", () => requestAnimationFrame(() => paint(performance.now())));
    });
    const stateButtons = document.getElementById("stateButtons");
    if (stateButtons) stateButtons.addEventListener("click", () => requestAnimationFrame(() => paint(performance.now())));
    const modeButtons = document.getElementById("previewModeButtons");
    if (modeButtons) modeButtons.addEventListener("click", () => requestAnimationFrame(updateCandidateCaption));
    updateCandidateCaption();
    loop();
  }

  function patchPreviewExports(photoCanvas) {
    const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
    if (originalToDataURL && !originalToDataURL.__receivingFieldReviewPatched) {
      const patched = function (...args) {
        if (this.id !== "previewCanvas" || !overlay || !overlayContext) return originalToDataURL.apply(this, args);
        const composed = document.createElement("canvas");
        composed.width = this.width;
        composed.height = this.height;
        const context = composed.getContext("2d");
        context.drawImage(this, 0, 0);
        context.drawImage(overlay, 0, 0, composed.width, composed.height);
        return originalToDataURL.apply(composed, args);
      };
      patched.__receivingFieldReviewPatched = true;
      HTMLCanvasElement.prototype.toDataURL = patched;
    }
    const originalToBlob = HTMLCanvasElement.prototype.toBlob;
    if (originalToBlob && !originalToBlob.__receivingFieldReviewPatched) {
      const patched = function (callback, ...args) {
        if (this.id !== "previewCanvas" || !overlay || !overlayContext) return originalToBlob.call(this, callback, ...args);
        const composed = document.createElement("canvas");
        composed.width = this.width;
        composed.height = this.height;
        const context = composed.getContext("2d");
        context.drawImage(this, 0, 0);
        context.drawImage(overlay, 0, 0, composed.width, composed.height);
        return originalToBlob.call(composed, callback, ...args);
      };
      patched.__receivingFieldReviewPatched = true;
      HTMLCanvasElement.prototype.toBlob = patched;
    }
    const originalCapture = HTMLCanvasElement.prototype.captureStream;
    if (originalCapture && !originalCapture.__receivingFieldReviewPatched) {
      const patchedCapture = function (...args) {
        if (this.id !== "previewCanvas" || !overlay || !overlayContext) return originalCapture.apply(this, args);
        const composed = document.createElement("canvas");
        composed.width = this.width;
        composed.height = this.height;
        const context = composed.getContext("2d");
        let active = true;
        const draw = () => {
          if (!active) return;
          if (composed.width !== photoCanvas.width || composed.height !== photoCanvas.height) {
            composed.width = photoCanvas.width;
            composed.height = photoCanvas.height;
          }
          context.clearRect(0, 0, composed.width, composed.height);
          context.drawImage(photoCanvas, 0, 0, composed.width, composed.height);
          context.drawImage(overlay, 0, 0, composed.width, composed.height);
          requestAnimationFrame(draw);
        };
        draw();
        const stream = originalCapture.apply(composed, args);
        stream.getTracks().forEach((track) => track.addEventListener("ended", () => { active = false; }, { once: true }));
        return stream;
      };
      patchedCapture.__receivingFieldReviewPatched = true;
      HTMLCanvasElement.prototype.captureStream = patchedCapture;
    }
  }

  function fieldVisible() {
    const field = document.getElementById("fieldToggle");
    const decomposition = value("fieldLayerView", "composite");
    return (!field || field.checked) && (decomposition === "composite" || decomposition === "receiving");
  }

  function updateCandidateCaption() {
    const caption = document.getElementById("candidateDescription");
    if (!caption) return;
    const dynamic = Boolean(document.querySelector('[data-preview-mode="dynamic"].active'));
    const candidate = dynamic ? value("dynamicCandidate", "B") : value("staticCandidate", "B");
    const copy = dynamic
      ? { A: "Dynamic A · 独立慢呼吸，光丝克制；Web Review only。", B: "Dynamic B · 四角不同步呼吸与缓慢流动；Web Review only。", C: "Dynamic C · 呼吸幅度更大，用于氛围上限评审；Web Review only。" }
      : { A: "REVIEW_CANDIDATE_A · 轻柔边缘场，蓝金层次克制。", B: "REVIEW_CANDIDATE_B · 右上蓝场最强、左上暖金次之，下角保留短余辉。", C: "REVIEW_CANDIDATE_C · 提高底场可见度，用于强度上限评审。" };
    caption.textContent = copy[candidate] || copy.B;
  }

  function paint(now) {
    if (!overlay || !overlayContext) return;
    const photoCanvas = document.getElementById("previewCanvas");
    if (!photoCanvas || !photoCanvas.width || !photoCanvas.height) return;
    const width = photoCanvas.width;
    const height = photoCanvas.height;
    if (overlay.width !== width || overlay.height !== height) {
      overlay.width = width;
      overlay.height = height;
    }
    const ctx = overlayContext;
    ctx.clearRect(0, 0, width, height);
    if (!fieldVisible()) return;

    const imageWidth = Math.min(width, height * 0.75);
    const imageHeight = imageWidth / 0.75;
    const left = (width - imageWidth) / 2;
    const top = (height - imageHeight) / 2;
    const min = Math.min(imageWidth, imageHeight);
    const mode = document.querySelector('[data-preview-mode="dynamic"].active') ? "dynamic" : "static";
    const playing = document.getElementById("playPause")?.getAttribute("aria-pressed") === "true";
    const candidate = mode === "dynamic" ? value("dynamicCandidate", "B") : value("staticCandidate", "B");
    const level = candidateLevels[candidate] || candidateLevels.B;
    const state = photoCanvas.dataset.state || "IMAGE_RECOGNIZING";
    const stateScale = state === "FISH_LOCATED" ? 0.82 : (state === "SPECIES_RECOGNIZING" ? 0.66 : 1);
    const speed = Number(value("motionSpeed", "1")) || 1;
    const t = now / 1000 * speed;
    const breathAmplitude = breathAmplitudes[candidate] || breathAmplitudes.B;
    const breaths = phase.map((offset, index) => mode === "dynamic" && playing
      ? 1 + breathAmplitude * Math.sin((Math.PI * 2 * t) / periods[index] + offset)
      : 1);
    const blue = "#42a8ee";
    const gold = "#e4ad4b";
    const corners = [
      { x: left + imageWidth * 1.012, y: top + imageHeight * 0.012, rx: imageWidth * 0.43, ry: imageHeight * 0.25, angle: 0.20, color: blue },
      { x: left - imageWidth * 0.018, y: top + imageHeight * 0.018, rx: imageWidth * 0.38, ry: imageHeight * 0.22, angle: -0.16, color: gold },
      { x: left - imageWidth * 0.014, y: top + imageHeight * 1.008, rx: imageWidth * 0.31, ry: imageHeight * 0.18, angle: -0.28, color: gold },
      { x: left + imageWidth * 1.012, y: top + imageHeight * 1.014, rx: imageWidth * 0.27, ry: imageHeight * 0.15, angle: 0.30, color: blue },
    ];
    ctx.save();
    // This canvas is a transparent plane above the photo. Use source-over so
    // blue/gold energy is perceptible over bright sky and water as well as dark areas.
    ctx.globalCompositeOperation = "source-over";
    corners.forEach((corner, index) => {
      const strength = level * stateScale * weights[index] * breaths[index];
      const radius = Math.max(corner.rx, corner.ry);
      const rgb = corner.color === blue ? "66,171,244" : "238,185,91";
      ctx.save();
      ctx.translate(corner.x, corner.y);
      ctx.rotate(corner.angle);
      ctx.scale(corner.rx / radius, corner.ry / radius);
      const gradient = ctx.createRadialGradient(0, 0, 0, 0, 0, radius);
      gradient.addColorStop(0, `rgba(${rgb},${0.68 * strength})`);
      gradient.addColorStop(0.34, `rgba(${rgb},${0.43 * strength})`);
      gradient.addColorStop(0.70, `rgba(${rgb},${0.18 * strength})`);
      gradient.addColorStop(1, `rgba(${rgb},0)`);
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    });
    const ribbons = [
      { points: [[0.58, -0.02], [0.82, -0.06], [1.035, 0.04], [1.012, 0.34]], color: blue, weight: 1, alpha: 0.62, width: 0.070 },
      { points: [[-0.012, 0.34], [-0.045, 0.13], [0.11, -0.035], [0.38, -0.008]], color: gold, weight: 0.72, alpha: 0.46, width: 0.062 },
      { points: [[-0.01, 0.75], [-0.04, 0.92], [0.11, 1.025], [0.35, 1.006]], color: gold, weight: 0.38, alpha: 0.31, width: 0.050 },
      { points: [[0.80, 1.012], [0.95, 1.04], [1.03, 0.92], [1.008, 0.78]], color: blue, weight: 0.25, alpha: 0.23, width: 0.042 },
    ];
    ribbons.forEach((ribbon, index) => {
      const pts = ribbon.points.map(([x, y]) => ({ x: left + imageWidth * x, y: top + imageHeight * y }));
      const lineWidth = min * ribbon.width;
      const strength = level * stateScale * ribbon.weight * breaths[index];
      ctx.save();
      ctx.beginPath();
      ctx.moveTo(pts[0].x, pts[0].y);
      ctx.bezierCurveTo(pts[1].x, pts[1].y, pts[2].x, pts[2].y, pts[3].x, pts[3].y);
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.lineWidth = lineWidth;
      ctx.shadowColor = ribbon.color;
      ctx.shadowBlur = lineWidth * 1.9;
      ctx.strokeStyle = ribbon.color;
      ctx.globalAlpha = ribbon.alpha * strength * 0.48;
      ctx.stroke();
      ctx.shadowBlur = lineWidth * 0.52;
      ctx.lineWidth = lineWidth * 0.47;
      ctx.globalAlpha = ribbon.alpha * strength * 0.30;
      ctx.stroke();
      ctx.restore();
    });
    ctx.restore();
    overlay.dataset.reviewSource = "REVIEW_ONLY_RECEIVING_FIELD";
    overlay.dataset.candidate = candidate;
    overlay.dataset.state = state;
  }

  function loop(now) {
    if (now - lastPaint > 34) {
      paint(now);
      lastPaint = now;
    }
    frame = requestAnimationFrame(loop);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", install, { once: true });
  else install();
  window.addEventListener("pagehide", () => cancelAnimationFrame(frame), { once: true });
})();
