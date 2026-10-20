/* Review-only Receiving Light layer. It is intentionally not an Android renderer. */
(function () {
  "use strict";
  const candidateLevels = { A: 0.54, B: 0.92, C: 1.18 };
  const breathAmplitudes = { A: 0.035, B: 0.065, C: 0.095 };
  const weights = [1, 0.78, 0.40, 0.22];
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
      if (node) node.addEventListener("change", () => paint(performance.now()));
      if (node && id === "playPause") node.addEventListener("click", () => requestAnimationFrame(() => paint(performance.now())));
    });
    const stateButtons = document.getElementById("stateButtons");
    if (stateButtons) stateButtons.addEventListener("click", () => requestAnimationFrame(() => paint(performance.now())));
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
      { x: left + imageWidth * 1.015, y: top + imageHeight * 0.015, rx: imageWidth * 0.57, ry: imageHeight * 0.30, angle: 0.24, color: blue },
      { x: left - imageWidth * 0.025, y: top + imageHeight * 0.025, rx: imageWidth * 0.51, ry: imageHeight * 0.29, angle: -0.20, color: gold },
      { x: left - imageWidth * 0.02, y: top + imageHeight * 1.01, rx: imageWidth * 0.43, ry: imageHeight * 0.24, angle: -0.32, color: gold },
      { x: left + imageWidth * 1.015, y: top + imageHeight * 1.02, rx: imageWidth * 0.39, ry: imageHeight * 0.22, angle: 0.34, color: blue },
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
      gradient.addColorStop(0, `rgba(${rgb},${0.58 * strength})`);
      gradient.addColorStop(0.34, `rgba(${rgb},${0.36 * strength})`);
      gradient.addColorStop(0.70, `rgba(${rgb},${0.13 * strength})`);
      gradient.addColorStop(1, `rgba(${rgb},0)`);
      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    });
    const ribbons = [
      { points: [[0.28, -0.01], [0.56, -0.035], [0.88, 0.02], [1.015, 0.36]], color: blue, weight: 1, alpha: 0.54, width: 0.105 },
      { points: [[-0.01, 0.39], [0.01, 0.14], [0.18, -0.02], [0.48, -0.01]], color: gold, weight: 0.78, alpha: 0.43, width: 0.105 },
      { points: [[-0.005, 0.68], [-0.03, 0.89], [0.19, 1.02], [0.45, 1.005]], color: gold, weight: 0.40, alpha: 0.33, width: 0.075 },
      { points: [[0.70, 1.01], [0.91, 1.035], [1.02, 0.87], [1.005, 0.67]], color: blue, weight: 0.22, alpha: 0.25, width: 0.060 },
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
