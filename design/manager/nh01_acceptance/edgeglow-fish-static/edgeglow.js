(() => {
  "use strict";

  const EXPECTED_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
  const PHOTO_URL = "../../../system/fish_media_display_v1/assets/real_catch_source.jpg";
  const SVG_NS = "http://www.w3.org/2000/svg";
  const EDGE_FIELD_SOURCE = "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt";
  const EDGE_FIELD_PATHS = Object.freeze([
    { id: "B1", family: "blue", widthDp: 1.2, seed: 0.00, visibleRatio: 0.30, weight: 1.00, start: [0.78, -0.02], control1: [0.94, 0.06], control2: [1.02, 0.22], end: [0.96, 0.42] },
    { id: "B3", family: "blue", widthDp: 1.2, seed: 0.47, visibleRatio: 0.20, weight: 0.54, start: [1.02, 0.61], control1: [0.92, 0.75], control2: [0.84, 0.89], end: [0.66, 1.02] },
    { id: "G1", family: "gold", widthDp: 1.1, seed: 0.12, visibleRatio: 0.26, weight: 0.78, start: [-0.02, 0.18], control1: [0.03, 0.08], control2: [0.12, 0.02], end: [0.26, -0.02] },
    { id: "G3", family: "gold", widthDp: 1.2, seed: 0.64, visibleRatio: 0.22, weight: 0.63, start: [-0.01, 0.74], control1: [0.08, 0.86], control2: [0.20, 0.95], end: [0.38, 1.02] }
  ]);
  const EDGE_FIELD_SECONDARY_PATHS = Object.freeze([
    { id: "B2", family: "blue", widthDp: 1.0, seed: 0.07, visibleRatio: 0.18, weight: 0.30, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [1.01, 0.18], control1: [0.92, 0.31], control2: [0.95, 0.52], end: [1.02, 0.68] },
    { id: "G2", family: "gold", widthDp: 0.9, seed: 0.09, visibleRatio: 0.16, weight: 0.26, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [-0.02, 0.33], control1: [0.04, 0.47], control2: [0.02, 0.62], end: [-0.01, 0.78] },
    { id: "B4", family: "blue", widthDp: 0.8, seed: 0.11, visibleRatio: 0.14, weight: 0.22, seedAuthority: "ANDROID_RUNTIME_RP_A03_NOT_FROZEN", start: [0.22, 1.02], control1: [0.38, 0.95], control2: [0.52, 0.95], end: [0.66, 1.01] }
  ]);
  const EDGE_FIELD_COMPANION_PATHS = Object.freeze([
    { id: "B1C", parentId: "B1", family: "blue", widthDp: 0.60, phaseDelta: -0.055, visibleRatio: 0.44, weight: 0.30, start: [0.75, -0.01], control1: [0.91, 0.07], control2: [0.99, 0.20], end: [0.94, 0.37] },
    { id: "G1C", parentId: "G1", family: "gold", widthDp: 0.58, phaseDelta: 0.065, visibleRatio: 0.42, weight: 0.30, start: [-0.01, 0.20], control1: [0.04, 0.11], control2: [0.13, 0.04], end: [0.24, 0.00] },
    { id: "G3C", parentId: "G3", family: "gold", widthDp: 0.60, phaseDelta: 0.050, visibleRatio: 0.38, weight: 0.30, start: [0.01, 0.77], control1: [0.09, 0.87], control2: [0.20, 0.94], end: [0.34, 0.99] },
    { id: "B3C", parentId: "B3", family: "blue", widthDp: 0.58, phaseDelta: -0.060, visibleRatio: 0.34, weight: 0.30, start: [0.99, 0.65], control1: [0.91, 0.77], control2: [0.82, 0.90], end: [0.69, 0.99] }
  ]);
  const EDGE_FIELD_MICRO_PATHS = Object.freeze([
    { id: "B1M", parentId: "B1", family: "blue", widthDp: 0.42, phaseDelta: 0.085, visibleRatio: 0.12, weight: 0.18, start: [0.84, -0.01], control1: [0.94, 0.05], control2: [0.99, 0.13], end: [0.985, 0.24] }
  ]);
  const DEFAULTS = Object.freeze({
    outerEnabled: true, outerWidth: 28, outerIntensity: 62,
    coreEnabled: true, coreWidth: 3, coreIntensity: 92,
    cometEnabled: true, cometLength: 14, cometPosition: 21, cometFalloff: 1.55,
    edgeFieldEnabled: true, receivingLightEnabled: true, primaryIslandsEnabled: true, secondaryFragmentsEnabled: true, companionHairlinesEnabled: true, edgeFieldIntensity: 100,
    edgeFieldBlueColor: "#bfeaf2", edgeFieldGoldColor: "#f6d99b",
    fishGoldColor: "#ffd887", fishCoreColor: "#ffe7ae",
    compareMode: "side", wipePosition: 50, contourVisible: true
  });
  const IDS = ["originalCanvas", "contourCanvas", "glowCanvas"];
  const el = Object.fromEntries([
    ...IDS.map(id => [id, document.getElementById(id)]),
    ...["sourceStatus", "photoSha", "photoDimensions", "buildSha", "fishContourPath", "contourVisible", "compareMode", "wipePosition", "comparisonCaption", "resetButton", "exportPngButton", "exportJsonButton", "outerEnabled", "outerWidth", "outerIntensity", "coreEnabled", "coreWidth", "coreIntensity", "cometEnabled", "cometLength", "cometPosition", "cometFalloff", "edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled", "edgeFieldIntensity", "edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor", "outerWidthValue", "outerIntensityValue", "coreWidthValue", "coreIntensityValue", "cometLengthValue", "cometPositionValue", "cometFalloffValue", "edgeFieldIntensityValue"].map(id => [id, document.getElementById(id)])
  ]);
  const contexts = Object.fromEntries(IDS.map(id => [id, el[id].getContext("2d", { alpha: false })]));
  const contourSvg = el.fishContourPath;
  const photo = new Image();
  const buffer = document.createElement("canvas");
  const bufferContext = buffer.getContext("2d", { alpha: false });
  const effectLayer = document.createElement("canvas");
  let effectContext = effectLayer.getContext("2d");
  let effectBounds = null;
  let fixture;
  let buildInfo = {};
  let pathLength = 0;
  let edgeFieldGeometry = [];
  let secondaryEdgeFieldGeometry = [];
  let companionEdgeFieldGeometry = [];
  let microEdgeFieldGeometry = [];
  let params = { ...DEFAULTS };
  let renderQueued = false;

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
    const materializeEdgePaths = definitions => definitions.map(definition => {
      const point = ([x, y]) => `${x * photo.naturalWidth} ${y * photo.naturalHeight}`;
      const path = document.createElementNS(SVG_NS, "path");
      path.setAttribute("data-edge-field-path", definition.id);
      path.setAttribute("fill", "none");
      path.setAttribute("d", `M ${point(definition.start)} C ${point(definition.control1)} ${point(definition.control2)} ${point(definition.end)}`);
      contourSvg.parentElement.appendChild(path);
      const length = path.getTotalLength();
      if (!(length > 100)) throw new Error(`AI Edge Field path ${definition.id} is unexpectedly short.`);
      return { ...definition, path, length };
    });
    edgeFieldGeometry = materializeEdgePaths(EDGE_FIELD_PATHS);
    secondaryEdgeFieldGeometry = materializeEdgePaths(EDGE_FIELD_SECONDARY_PATHS);
    companionEdgeFieldGeometry = materializeEdgePaths(EDGE_FIELD_COMPANION_PATHS);
    microEdgeFieldGeometry = materializeEdgePaths(EDGE_FIELD_MICRO_PATHS);
    const box = contourSvg.getBBox();
    if (box.width > 600 || box.height > 1300 || box.width < 250 || box.height < 900) throw new Error("Contour bounds are outside the selected fish subject.");
    const margin = 96;
    const left = Math.max(0, Math.floor(box.x - margin));
    const top = Math.max(0, Math.floor(box.y - margin));
    const right = Math.min(photo.naturalWidth, Math.ceil(box.x + box.width + margin));
    const bottom = Math.min(photo.naturalHeight, Math.ceil(box.y + box.height + margin));
    effectBounds = { left, top, width: right - left, height: bottom - top, margin };
    effectLayer.width = effectBounds.width;
    effectLayer.height = effectBounds.height;
    effectContext = effectLayer.getContext("2d", { alpha: true });

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
    g.addColorStop(0, params.fishGoldColor);
    g.addColorStop(0.34, mixHex(params.fishGoldColor, params.fishCoreColor, 0.28));
    g.addColorStop(0.72, params.fishCoreColor);
    g.addColorStop(1, params.fishGoldColor);
    return g;
  }

  function drawContour() {
    const ctx = contexts.contourCanvas;
    paintBase(ctx);
    if (!params.contourVisible) return;
    ctx.save();
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.strokeStyle = rgba(params.fishGoldColor, 0.96);
    ctx.lineWidth = 4;
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
      const color = mixHex(params.fishGoldColor, params.fishCoreColor, Math.min(0.82, 0.16 + headness * 0.66));
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
    ctx.fillStyle = params.fishCoreColor;
    ctx.beginPath();
    ctx.arc(headPoint.x, headPoint.y, softness ? params.coreWidth * 2 : params.coreWidth * 0.72, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawReceivingLight(ctx) {
    if (!params.edgeFieldEnabled || !params.receivingLightEnabled || params.edgeFieldIntensity <= 0) return;
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const photoPxPerDp = photo.naturalWidth / displayWidth;
    const intensity = params.edgeFieldIntensity / 100;
    ctx.save();
    // The receiving light follows each frozen island instead of painting four
    // circular corner blobs. Screen blending keeps the edge light luminous on
    // both bright sky and dark foliage while remaining localized to the paths.
    ctx.globalCompositeOperation = "screen";
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    for (const geometry of edgeFieldGeometry) {
      const color = geometry.family === "blue" ? params.edgeFieldBlueColor : params.edgeFieldGoldColor;
      const hotColor = geometry.family === "blue" ? "#e7fbff" : "#fff1c8";
      const common = intensity * geometry.weight;
      for (const [from, to] of edgeFieldParts(geometry)) {
        const points = edgeFieldSegment(ctx, geometry, from, to, Math.max(2, photoPxPerDp));
        const first = points[0], last = points[points.length - 1];
        const receivingGradient = peakAlpha => {
          const gradient = ctx.createLinearGradient(first[0], first[1], last[0], last[1]);
          gradient.addColorStop(0, rgba(color, peakAlpha * 0.30 * common));
          gradient.addColorStop(0.50, rgba(color, peakAlpha * 0.72 * common));
          gradient.addColorStop(0.90, rgba(hotColor, peakAlpha * common));
          gradient.addColorStop(1, rgba(hotColor, peakAlpha * 0.76 * common));
          return gradient;
        };
        // R01 web review emphasis: brighter than Android runtime and the Frozen
        // contract cap so the edge illumination can be judged on this daylight photo.
        ctx.filter = `blur(${9 * photoPxPerDp}px)`;
        ctx.strokeStyle = receivingGradient(0.22);
        ctx.lineWidth = 18 * photoPxPerDp;
        ctx.stroke();
        ctx.filter = `blur(${2.5 * photoPxPerDp}px)`;
        ctx.strokeStyle = receivingGradient(0.18);
        ctx.lineWidth = 7 * photoPxPerDp;
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  function edgeFieldPartsAt(geometry, seed = geometry.seed) {
    const visibleLength = geometry.length * geometry.visibleRatio;
    const start = ((seed % 1) + 1) % 1 * geometry.length;
    const end = start + visibleLength;
    return end <= geometry.length
      ? [[start, end]]
      : [[start, geometry.length], [0, end - geometry.length]];
  }

  function edgeFieldParts(geometry) {
    return edgeFieldPartsAt(geometry, geometry.seed);
  }

  function edgeFieldSegment(ctx, geometry, from, to, stepPx) {
    const count = Math.max(2, Math.ceil((to - from) / stepPx));
    const points = [];
    for (let index = 0; index <= count; index++) {
      const distance = from + (to - from) * index / count;
      const point = geometry.path.getPointAtLength(distance);
      points.push([point.x, point.y]);
    }
    ctx.beginPath();
    ctx.moveTo(points[0][0], points[0][1]);
    for (let index = 1; index < points.length; index++) ctx.lineTo(points[index][0], points[index][1]);
    return points;
  }

  function drawEdgeFieldPathSet(ctx, geometries, secondary) {
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const photoPxPerDp = photo.naturalWidth / displayWidth;
    const intensity = params.edgeFieldIntensity / 100;
    const sampleStep = Math.max(2, photoPxPerDp * 1.25);
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    for (const geometry of geometries) {
      const coreColor = geometry.family === "blue" ? params.edgeFieldBlueColor : params.edgeFieldGoldColor;
      const hotColor = geometry.family === "blue" ? "#d8f5fa" : "#ffe7ae";
      const common = intensity * geometry.weight;
      for (const [from, to] of edgeFieldParts(geometry)) {
        const points = edgeFieldSegment(ctx, geometry, from, to, sampleStep);
        ctx.save();
        if (secondary) {
          ctx.filter = `blur(${1 * photoPxPerDp}px)`;
          ctx.strokeStyle = rgba(coreColor, Math.min(0.035, 0.025 * common));
          ctx.lineWidth = 2 * photoPxPerDp;
          ctx.stroke();
        } else {
          ctx.filter = `blur(${5 * photoPxPerDp}px)`;
          ctx.strokeStyle = rgba(coreColor, Math.min(0.055, 0.055 * common));
          ctx.lineWidth = 10 * photoPxPerDp;
          ctx.stroke();
          ctx.filter = `blur(${2 * photoPxPerDp}px)`;
          ctx.strokeStyle = rgba(coreColor, Math.min(0.10, 0.10 * common));
          ctx.lineWidth = 4 * photoPxPerDp;
          ctx.stroke();
        }
        ctx.filter = "none";
        const first = points[0], last = points[points.length - 1];
        const gradient = ctx.createLinearGradient(first[0], first[1], last[0], last[1]);
        const coreAlpha = secondary ? Math.min(0.12, 0.16 * common) : Math.min(0.50, 0.46 * common);
        gradient.addColorStop(0, rgba(coreColor, coreAlpha * 0.08));
        gradient.addColorStop(0.58, rgba(coreColor, coreAlpha * 0.58));
        gradient.addColorStop(0.88, rgba(hotColor, coreAlpha));
        gradient.addColorStop(1, rgba(hotColor, coreAlpha * 0.78));
        ctx.strokeStyle = gradient;
        ctx.lineWidth = geometry.widthDp * photoPxPerDp;
        ctx.stroke();
        ctx.restore();
      }
    }
    ctx.restore();
  }

  function drawCompanionHairlines(ctx, geometries, micro = false) {
    if (!params.edgeFieldEnabled || !params.companionHairlinesEnabled || params.edgeFieldIntensity <= 0) return;
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const photoPxPerDp = photo.naturalWidth / displayWidth;
    const intensity = params.edgeFieldIntensity / 100;
    const primaryById = new Map(EDGE_FIELD_PATHS.map(definition => [definition.id, definition]));
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    for (const geometry of geometries) {
      const parent = primaryById.get(geometry.parentId);
      const color = geometry.family === "blue" ? params.edgeFieldBlueColor : params.edgeFieldGoldColor;
      const hot = geometry.family === "blue" ? "#e7fbff" : "#fff1c8";
      const common = intensity * parent.weight * geometry.weight;
      for (const [from, to] of edgeFieldPartsAt(geometry, parent.seed + geometry.phaseDelta)) {
        const points = edgeFieldSegment(ctx, geometry, from, to, Math.max(2, photoPxPerDp));
        const first = points[0], last = points[points.length - 1];
        ctx.filter = `blur(${(micro ? 1.4 : 2.1) * photoPxPerDp}px)`;
        ctx.strokeStyle = rgba(color, (micro ? 0.035 : 0.065) * common);
        ctx.lineWidth = (micro ? 1.5 : 2.1) * photoPxPerDp;
        ctx.stroke();
        ctx.filter = "none";
        const gradient = ctx.createLinearGradient(first[0], first[1], last[0], last[1]);
        const coreAlpha = (micro ? 0.10 : 0.16) * common;
        gradient.addColorStop(0, rgba(color, coreAlpha * 0.10));
        gradient.addColorStop(0.64, rgba(color, coreAlpha * 0.62));
        gradient.addColorStop(1, rgba(hot, coreAlpha));
        ctx.strokeStyle = gradient;
        ctx.lineWidth = geometry.widthDp * photoPxPerDp;
        ctx.stroke();
      }
    }
    ctx.restore();
  }

  function drawStaticEnergyNodes(ctx) {
    if (!params.edgeFieldEnabled || !params.companionHairlinesEnabled || params.edgeFieldIntensity <= 0) return;
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const pxPerDp = photo.naturalWidth / displayWidth;
    const nodes = [
      { path: edgeFieldGeometry.find(path => path.id === "B1"), t: 0.78, color: "#e7fbff", weight: 1 },
      { path: edgeFieldGeometry.find(path => path.id === "G1"), t: 0.68, color: "#fff1c8", weight: 0.42 }
    ];
    ctx.save();
    ctx.globalCompositeOperation = "screen";
    for (const node of nodes) {
      const point = node.path.path.getPointAtLength(node.path.length * node.t);
      const alpha = (params.edgeFieldIntensity / 100) * node.weight;
      const glow = ctx.createRadialGradient(point.x, point.y, 0, point.x, point.y, 7 * pxPerDp);
      glow.addColorStop(0, rgba(node.color, 0.30 * alpha));
      glow.addColorStop(0.24, rgba(node.color, 0.13 * alpha));
      glow.addColorStop(1, rgba(node.color, 0));
      ctx.fillStyle = glow;
      ctx.beginPath();
      ctx.arc(point.x, point.y, 7 * pxPerDp, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = rgba(node.color, 0.46 * alpha);
      ctx.beginPath();
      ctx.arc(point.x, point.y, 1.2 * pxPerDp, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  function drawPrimaryEdgeIslands(ctx) {
    if (!params.edgeFieldEnabled || !params.primaryIslandsEnabled || params.edgeFieldIntensity <= 0) return;
    drawEdgeFieldPathSet(ctx, edgeFieldGeometry, false);
  }

  function drawSecondaryEdgeFragments(ctx) {
    if (!params.edgeFieldEnabled || !params.secondaryFragmentsEnabled || params.edgeFieldIntensity <= 0) return;
    drawEdgeFieldPathSet(ctx, secondaryEdgeFieldGeometry, true);
  }

  function renderGlowBuffer() {
    const ctx = effectContext;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.filter = "none";
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
    ctx.clearRect(0, 0, effectLayer.width, effectLayer.height);
    ctx.setTransform(1, 0, 0, 1, -effectBounds.left, -effectBounds.top);
    drawOuterBloom(ctx);
    drawCore(ctx);
    if (params.cometEnabled) {
      drawCometPass(ctx, true);
      drawCometPass(ctx, false);
    }
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    paintBase(bufferContext);
    drawReceivingLight(bufferContext);
    drawPrimaryEdgeIslands(bufferContext);
    drawSecondaryEdgeFragments(bufferContext);
    drawCompanionHairlines(bufferContext, companionEdgeFieldGeometry);
    drawCompanionHairlines(bufferContext, microEdgeFieldGeometry, true);
    drawStaticEnergyNodes(bufferContext);
    bufferContext.drawImage(effectLayer, effectBounds.left, effectBounds.top);
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
      el.comparisonCaption.textContent = "AI Edge Field · 四周蓝青/暖金 · Fish Focus 暖金高亮";
    }
    ctx.restore();
  }

  function updateOutputs() {
    el.outerWidthValue.textContent = `${params.outerWidth} px`;
    el.outerIntensityValue.textContent = `${params.outerIntensity}%`;
    el.coreWidthValue.textContent = `${params.coreWidth} px`;
    el.coreIntensityValue.textContent = `${params.coreIntensity}%`;
    el.cometLengthValue.textContent = `${params.cometLength}%`;
    el.cometPositionValue.textContent = `${params.cometPosition}%`;
    el.cometFalloffValue.textContent = Number(params.cometFalloff).toFixed(2);
    el.edgeFieldIntensityValue.textContent = `${params.edgeFieldIntensity}%`;
  }

  function syncControls() {
    for (const id of ["outerEnabled", "coreEnabled", "cometEnabled", "edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled", "contourVisible"]) el[id].checked = Boolean(params[id]);
    for (const id of ["outerWidth", "outerIntensity", "coreWidth", "coreIntensity", "cometLength", "cometPosition", "cometFalloff", "edgeFieldIntensity", "edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor", "compareMode", "wipePosition"]) el[id].value = params[id];
    updateOutputs();
  }

  function markRendered() {
    document.documentElement.dataset.renderVersion = String(Number(document.documentElement.dataset.renderVersion || 0) + 1);
  }

  function renderAll() {
    updateOutputs();
    if (!fixture || !photo.complete || !photo.naturalWidth) return;
    paintBase(contexts.originalCanvas);
    drawContour();
    renderGlowBuffer();
    drawGlowComparison();
    markRendered();
  }

  let queuedRenderKind = "none";
  const renderPriority = { none: 0, compare: 1, contour: 1, glow: 2, all: 3 };
  function scheduleRender(kind = "glow") {
    updateOutputs();
    if (renderPriority[kind] > renderPriority[queuedRenderKind]) queuedRenderKind = kind;
    if (renderQueued) return;
    renderQueued = true;
    requestAnimationFrame(() => {
      renderQueued = false;
      const requested = queuedRenderKind;
      queuedRenderKind = "none";
      if (requested === "all") renderAll();
      else if (requested === "compare") { drawGlowComparison(); markRendered(); }
      else if (requested === "contour") { drawContour(); markRendered(); }
      else if (requested === "glow") { renderGlowBuffer(); drawGlowComparison(); markRendered(); }
    });
  }

  const controlToParam = {
    outerEnabled: "outerEnabled", outerWidth: "outerWidth", outerIntensity: "outerIntensity",
    coreEnabled: "coreEnabled", coreWidth: "coreWidth", coreIntensity: "coreIntensity",
    cometEnabled: "cometEnabled", cometLength: "cometLength", cometPosition: "cometPosition", cometFalloff: "cometFalloff",
    edgeFieldEnabled: "edgeFieldEnabled", receivingLightEnabled: "receivingLightEnabled", primaryIslandsEnabled: "primaryIslandsEnabled", secondaryFragmentsEnabled: "secondaryFragmentsEnabled", companionHairlinesEnabled: "companionHairlinesEnabled", edgeFieldIntensity: "edgeFieldIntensity",
    edgeFieldBlueColor: "edgeFieldBlueColor", edgeFieldGoldColor: "edgeFieldGoldColor",
    fishGoldColor: "fishGoldColor", fishCoreColor: "fishCoreColor", contourVisible: "contourVisible",
    compareMode: "compareMode", wipePosition: "wipePosition"
  };
  for (const [controlId, param] of Object.entries(controlToParam)) {
    el[controlId].addEventListener("input", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      if (param === "cometFalloff") params[param] = Number(params[param]);
      scheduleRender(param === "contourVisible" ? "contour" : (param === "compareMode" || param === "wipePosition" ? "compare" : "glow"));
    });
    el[controlId].addEventListener("change", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      scheduleRender(param === "contourVisible" ? "contour" : (param === "compareMode" || param === "wipePosition" ? "compare" : "glow"));
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
      edge_field: { geometry_source: EDGE_FIELD_SOURCE, paths: [...EDGE_FIELD_PATHS, ...EDGE_FIELD_SECONDARY_PATHS, ...EDGE_FIELD_COMPANION_PATHS, ...EDGE_FIELD_MICRO_PATHS], primary_path_ids: EDGE_FIELD_PATHS.map(path => path.id), secondary_path_ids: EDGE_FIELD_SECONDARY_PATHS.map(path => path.id), companion_path_ids: EDGE_FIELD_COMPANION_PATHS.map(path => path.id), micro_path_ids: EDGE_FIELD_MICRO_PATHS.map(path => path.id), static_seed_offsets: true, secondary_seed_authority: "Android runtime values .07/.11/.09; not Frozen; see RP-A03", companion_geometry_authority: "RecognitionAmbientField.kt frozen companion/micro geometry", animation: false, receiving_light: "path-following local illumination; no circular corner glows", receiving_alpha_per_pass: [0.22, 0.18], receiving_alpha_authority: "R01 Web visual emphasis; above Android runtime 0.13 and Frozen Rendering Contract cap 0.07; open RP-A01", perimeter_blend_mode: "screen for luminous edge field; fish contour remains separately warm gold" },
      renderer: "Canvas 2D web adaptation; static SVG Path arc sampling; not Android runtime",
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
    get effectBounds() { return { ...effectBounds }; },
    get edgeFieldPaths() { return [...edgeFieldGeometry, ...secondaryEdgeFieldGeometry, ...companionEdgeFieldGeometry, ...microEdgeFieldGeometry].map(({ path, ...definition }) => ({ ...definition, length: definition.length })); },
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
