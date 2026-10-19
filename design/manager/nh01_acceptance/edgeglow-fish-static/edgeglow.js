(() => {
  "use strict";

  const EXPECTED_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
  const PHOTO_URL = "../../../system/fish_media_display_v1/assets/real_catch_source.jpg";
  const DEFAULTS = Object.freeze({
    outerEnabled: true, outerWidth: 52, outerIntensity: 72,
    coreEnabled: true, coreWidth: 8, coreIntensity: 92,
    cometEnabled: true, cometLength: 14, cometPosition: 21, cometFalloff: 1.55,
    blueColor: "#58c8f4", goldColor: "#f2cc78", coreColor: "#fff5d8",
    compareMode: "side", wipePosition: 50, contourVisible: true
  });
  const IDS = ["originalCanvas", "contourCanvas", "glowCanvas"];
  const el = Object.fromEntries([
    ...IDS.map(id => [id, document.getElementById(id)]),
    ...["sourceStatus", "photoSha", "photoDimensions", "buildSha", "fishContourPath", "contourVisible", "compareMode", "wipePosition", "comparisonCaption", "resetButton", "exportPngButton", "exportJsonButton", "outerEnabled", "outerWidth", "outerIntensity", "coreEnabled", "coreWidth", "coreIntensity", "cometEnabled", "cometLength", "cometPosition", "cometFalloff", "blueColor", "goldColor", "coreColor", "outerWidthValue", "outerIntensityValue", "coreWidthValue", "coreIntensityValue", "cometLengthValue", "cometPositionValue", "cometFalloffValue"].map(id => [id, document.getElementById(id)])
  ]);
  const contexts = Object.fromEntries(IDS.map(id => [id, el[id].getContext("2d", { alpha: false })]));
  const contourSvg = el.fishContourPath;
  const photo = new Image();
  const buffer = document.createElement("canvas");
  const bufferContext = buffer.getContext("2d", { alpha: false });
  let fixture;
  let buildInfo = {};
  let pathLength = 0;
  let params = { ...DEFAULTS };

  const hexRgb = hex => {
    const n = Number.parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  };
  const rgba = (hex, alpha) => {
    const [r, g, b] = hexRgb(hex);
    return `rgba(${r},${g},${b},${Math.max(0, Math.min(1, alpha))})`;
  };
  const mixHex = (a, b, t) => {
    const aa = hexRgb(a), bb = hexRgb(b);
    return `#${aa.map((v, i) => Math.round(v + (bb[i] - v) * t).toString(16).padStart(2, "0")).join("")}`;
  };

  async function sha256(blob) {
    const bytes = await blob.arrayBuffer();
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return [...new Uint8Array(digest)].map(v => v.toString(16).padStart(2, "0")).join("");
  }

  async function loadAssets() {
    const [fixtureResponse, buildResponse, photoResponse] = await Promise.all([
      fetch("./fixture.json", { cache: "no-store" }),
      fetch("./build-info.json", { cache: "no-store" }).catch(() => null),
      fetch(PHOTO_URL, { cache: "no-store" })
    ]);
    if (!fixtureResponse.ok) throw new Error(`fixture.json HTTP ${fixtureResponse.status}`);
    if (!photoResponse.ok) throw new Error(`photo HTTP ${photoResponse.status}`);
    fixture = await fixtureResponse.json();
    if (buildResponse?.ok) buildInfo = await buildResponse.json();
    if (fixture.photo.sha256 !== EXPECTED_SHA) throw new Error("Fixture photo SHA does not match the registered source.");
    if (fixture.photo.width !== 1152 || fixture.photo.height !== 1536 || fixture.contour.coordinate_space !== "original_photo_px_1152x1536") throw new Error("Photo and contour coordinate contracts disagree.");
    if (fixture.contour.source !== "MANUAL_REFERENCE_CONTOUR" || fixture.contour.production_mask !== false) throw new Error("Contour provenance must remain manual and non-production.");

    const blob = await photoResponse.blob();
    const digest = await sha256(blob);
    if (digest !== EXPECTED_SHA) throw new Error(`Photo SHA mismatch: ${digest}`);
    const objectUrl = URL.createObjectURL(blob);
    await new Promise((resolve, reject) => {
      photo.onload = resolve;
      photo.onerror = () => reject(new Error("Photo decode failed."));
      photo.src = objectUrl;
    });
    if (photo.naturalWidth !== fixture.photo.width || photo.naturalHeight !== fixture.photo.height) throw new Error("Photo dimensions do not match the fixture.");
    for (const id of IDS) {
      el[id].width = photo.naturalWidth;
      el[id].height = photo.naturalHeight;
    }
    buffer.width = photo.naturalWidth;
    buffer.height = photo.naturalHeight;
    contourSvg.setAttribute("d", fixture.contour.path_d);
    pathLength = contourSvg.getTotalLength();
    if (!(pathLength > 1000)) throw new Error("Fish contour path is unexpectedly short.");
    const box = contourSvg.getBBox();
    if (box.width > 600 || box.height > 1300 || box.width < 250 || box.height < 900) throw new Error("Contour bounds are outside the selected fish subject.");

    el.photoSha.textContent = digest;
    el.photoDimensions.textContent = `${photo.naturalWidth} × ${photo.naturalHeight} · EXIF ${fixture.photo.exif_orientation} · FIT / NO CROP`;
    el.buildSha.textContent = `${String(buildInfo.pr_source_sha || "local preview").slice(0, 12)} · center ${String(buildInfo.acceptance_center_source_sha || "unbound").slice(0, 12)}`;
    el.sourceStatus.textContent = `PHOTO VERIFIED · ${photo.naturalWidth} × ${photo.naturalHeight} · MANUAL CONTOUR`;
    el.sourceStatus.dataset.status = "PASS";
    URL.revokeObjectURL(objectUrl);
  }

  function paintBase(ctx) {
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.filter = "none";
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
    ctx.drawImage(photo, 0, 0, photo.naturalWidth, photo.naturalHeight);
    ctx.restore();
  }

  function createContourGradient(ctx) {
    const g = ctx.createLinearGradient(210, 290, 220, 1445);
    g.addColorStop(0, params.blueColor);
    g.addColorStop(0.43, params.blueColor);
    g.addColorStop(0.73, params.goldColor);
    g.addColorStop(1, params.goldColor);
    return g;
  }

  function drawContour() {
    const ctx = contexts.contourCanvas;
    paintBase(ctx);
    if (!params.contourVisible) return;
    ctx.save();
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.strokeStyle = "rgba(87, 224, 255, 0.96)";
    ctx.lineWidth = 5;
    ctx.shadowColor = "rgba(5, 28, 40, 0.95)";
    ctx.shadowBlur = 4;
    ctx.stroke(new Path2D(fixture.contour.path_d));
    ctx.restore();
  }

  function drawOuterBloom(ctx) {
    if (!params.outerEnabled || params.outerIntensity <= 0) return;
    const gradient = createContourGradient(ctx);
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.strokeStyle = gradient;
    ctx.globalAlpha = (params.outerIntensity / 100) * 0.44;
    ctx.lineWidth = params.outerWidth;
    ctx.filter = `blur(${Math.max(5, params.outerWidth * 0.38)}px)`;
    ctx.stroke(new Path2D(fixture.contour.path_d));
    ctx.filter = `blur(${Math.max(2, params.outerWidth * 0.14)}px)`;
    ctx.globalAlpha = (params.outerIntensity / 100) * 0.56;
    ctx.lineWidth = params.outerWidth * 0.48;
    ctx.stroke(new Path2D(fixture.contour.path_d));
    ctx.restore();
  }

  function drawCore(ctx) {
    if (!params.coreEnabled || params.coreIntensity <= 0) return;
    const gradient = createContourGradient(ctx);
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.filter = `blur(${Math.max(1, params.coreWidth * 0.42)}px)`;
    ctx.globalAlpha = (params.coreIntensity / 100) * 0.54;
    ctx.strokeStyle = gradient;
    ctx.lineWidth = params.coreWidth * 2.35;
    ctx.stroke(new Path2D(fixture.contour.path_d));
    ctx.filter = "none";
    ctx.globalAlpha = params.coreIntensity / 100;
    ctx.strokeStyle = gradient;
    ctx.lineWidth = params.coreWidth;
    ctx.stroke(new Path2D(fixture.contour.path_d));
    ctx.restore();
  }

  function pathPoint(distance) {
    let d = distance % pathLength;
    if (d < 0) d += pathLength;
    return contourSvg.getPointAtLength(d);
  }

  function drawCometPass(ctx, softness) {
    const tailLength = pathLength * (params.cometLength / 100);
    const head = pathLength * (params.cometPosition / 100);
    const start = head - tailLength;
    const segmentPx = Math.max(3, tailLength / 120);
    const segments = Math.ceil(tailLength / segmentPx);
    const glowWidth = Math.max(params.coreWidth * (softness ? 3 : 1.8), softness ? 15 : 7);
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.filter = softness ? `blur(${Math.max(5, params.coreWidth * 1.5)}px)` : "none";
    for (let i = 0; i < segments; i++) {
      const t0 = i / segments;
      const t1 = (i + 1) / segments;
      const p0 = pathPoint(start + tailLength * t0);
      const p1 = pathPoint(start + tailLength * t1);
      const headness = Math.pow((t0 + t1) / 2, params.cometFalloff);
      const color = mixHex(params.blueColor, params.goldColor, Math.min(1, 0.18 + headness * 1.05));
      ctx.globalAlpha = (params.coreIntensity / 100) * (softness ? 0.18 : 0.20 + headness * 0.8);
      ctx.strokeStyle = color;
      ctx.lineWidth = glowWidth * (0.55 + headness * 0.45);
      ctx.beginPath();
      ctx.moveTo(p0.x, p0.y);
      ctx.lineTo(p1.x, p1.y);
      ctx.stroke();
    }
    const headPoint = pathPoint(head);
    ctx.filter = softness ? "blur(12px)" : "none";
    ctx.globalAlpha = params.coreIntensity / 100;
    ctx.fillStyle = params.coreColor;
    ctx.beginPath();
    ctx.arc(headPoint.x, headPoint.y, softness ? params.coreWidth * 2 : params.coreWidth * 0.72, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function renderGlowBuffer() {
    const ctx = bufferContext;
    paintBase(ctx);
    drawOuterBloom(ctx);
    drawCore(ctx);
    if (params.cometEnabled) {
      drawCometPass(ctx, true);
      drawCometPass(ctx, false);
    }
  }

  function drawGlowComparison() {
    const ctx = contexts.glowCanvas;
    ctx.save();
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.filter = "none";
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
    if (params.compareMode === "overlay") {
      paintBase(ctx);
      ctx.globalAlpha = 0.5;
      ctx.drawImage(buffer, 0, 0);
      el.wipePosition.hidden = true;
      el.comparisonCaption.textContent = "50% Overlay · Original Photo + Current EdgeGlow Render";
    } else if (params.compareMode === "wipe") {
      ctx.drawImage(buffer, 0, 0);
      const boundary = photo.naturalWidth * (params.wipePosition / 100);
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, boundary, photo.naturalHeight);
      ctx.clip();
      ctx.drawImage(photo, 0, 0);
      ctx.restore();
      el.wipePosition.hidden = false;
      el.comparisonCaption.textContent = `Before / After Wipe · 原图 ${params.wipePosition}% / EdgeGlow ${100 - params.wipePosition}%`;
    } else {
      ctx.drawImage(buffer, 0, 0);
      el.wipePosition.hidden = true;
      el.comparisonCaption.textContent = "Outer Bloom · Core Highlight · Static Comet";
    }
    ctx.restore();
  }

  function updateOutputs() {
    el.outerWidthValue.value = `${params.outerWidth} px`;
    el.outerIntensityValue.value = `${params.outerIntensity}%`;
    el.coreWidthValue.value = `${params.coreWidth} px`;
    el.coreIntensityValue.value = `${params.coreIntensity}%`;
    el.cometLengthValue.value = `${params.cometLength}%`;
    el.cometPositionValue.value = `${params.cometPosition}%`;
    el.cometFalloffValue.value = Number(params.cometFalloff).toFixed(2);
  }

  function syncControls() {
    for (const id of ["outerEnabled", "coreEnabled", "cometEnabled", "contourVisible"]) el[id].checked = Boolean(params[id]);
    for (const id of ["outerWidth", "outerIntensity", "coreWidth", "coreIntensity", "cometLength", "cometPosition", "cometFalloff", "blueColor", "goldColor", "coreColor", "compareMode", "wipePosition"]) el[id].value = params[id];
    updateOutputs();
  }

  function renderAll() {
    updateOutputs();
    if (!fixture || !photo.complete || !photo.naturalWidth) return;
    paintBase(contexts.originalCanvas);
    drawContour();
    renderGlowBuffer();
    drawGlowComparison();
    document.documentElement.dataset.renderVersion = String(Number(document.documentElement.dataset.renderVersion || 0) + 1);
  }

  const controlToParam = {
    outerEnabled: "outerEnabled", outerWidth: "outerWidth", outerIntensity: "outerIntensity",
    coreEnabled: "coreEnabled", coreWidth: "coreWidth", coreIntensity: "coreIntensity",
    cometEnabled: "cometEnabled", cometLength: "cometLength", cometPosition: "cometPosition", cometFalloff: "cometFalloff",
    blueColor: "blueColor", goldColor: "goldColor", coreColor: "coreColor", contourVisible: "contourVisible",
    compareMode: "compareMode", wipePosition: "wipePosition"
  };
  for (const [controlId, param] of Object.entries(controlToParam)) {
    el[controlId].addEventListener("input", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      if (param === "cometFalloff") params[param] = Number(params[param]);
      renderAll();
    });
    el[controlId].addEventListener("change", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      renderAll();
    });
  }

  function downloadBlob(blob, filename) {
    const anchor = document.createElement("a");
    anchor.href = URL.createObjectURL(blob);
    anchor.download = filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(anchor.href), 1000);
  }

  el.exportPngButton.addEventListener("click", () => {
    el.glowCanvas.toBlob(blob => {
      if (blob) downloadBlob(blob, "EdgeGlowView_Fish_Static_Current_Render.png");
    }, "image/png");
  });
  el.exportJsonButton.addEventListener("click", () => {
    const payload = {
      schema_version: "1.0",
      task_id: "YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1",
      photo: { repository_path: fixture.photo.repository_path, sha256: EXPECTED_SHA, dimensions: [photo.naturalWidth, photo.naturalHeight], fit: fixture.photo.fit },
      contour: { source: fixture.contour.source, production_mask: false, coordinate_space: fixture.contour.coordinate_space, approximate_bbox_px: fixture.contour.approximate_bbox_px, path_d: fixture.contour.path_d },
      upstream: { repository: "yu-day/EdgeGlowView", commit: "46ce28221ed30bcd7763ff13ecdd96171756eb60", license: "MIT", copyright: "Copyright (c) 2025 WANG YU" },
      renderer: "Canvas 2D web adaptation; static Path arc sampling; not Android runtime",
      parameters: { ...params },
      build: buildInfo
    };
    downloadBlob(new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" }), "EdgeGlowView_Fish_Static_Parameters.json");
  });
  el.resetButton.addEventListener("click", () => {
    params = { ...DEFAULTS };
    syncControls();
    renderAll();
  });

  window.__EDGEGLOW_LAB__ = {
    get state() { return { ...params }; },
    get pathLength() { return pathLength; },
    get photoDimensions() { return [photo.naturalWidth, photo.naturalHeight]; },
    render: renderAll,
    getPixelData(canvasId = "glowCanvas") { return [...contexts[canvasId].getImageData(0, 0, el[canvasId].width, el[canvasId].height).data]; }
  };

  syncControls();
  loadAssets().then(renderAll).catch(error => {
    el.sourceStatus.dataset.status = "FAIL";
    el.sourceStatus.textContent = `ASSET CHECK FAILED · ${error.message}`;
    console.error("EdgeGlow static prototype failed to load", error);
  });
})();
