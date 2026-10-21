(() => {
  "use strict";

  const EXPECTED_SHA = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
  const OPTICAL_CONFIG_URL = "../../../system/recognition_optical_v2/RecognitionOpticalConfigV2.json";
  const OPTICAL_CONFIG_VERSION = "2.0.0";
  const PHOTO_URL = "../../../system/fish_media_display_v1/assets/real_catch_source.jpg";
  const SVG_NS = "http://www.w3.org/2000/svg";
  const EDGE_FIELD_SOURCE = "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt";
  const EDGE_FIELD_RENDER_SCALE = 0.5;
  // Receiving Light is delegated to the shared P1-bounded SVG renderer.
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
    edgeFieldBlueColor: "#5ab2fc", edgeFieldGoldColor: "#e0b644",
    fishGoldColor: "#ffd887", fishCoreColor: "#ffe7ae",
    preset: "enhanced", opticalCoreIntensity: 88, opticalCoreWidth: 2.6,
    opticalCoreBlend: "source-over",
    innerBloomRadius: 18, innerBloomIntensity: 62,
    outerBloomRadius: 54, outerBloomIntensity: 34,
    atmosphericSpill: 15, blueSaturation: 82, goldWarmth: 74,
    backgroundCompensation: 100,
    strengthG1: 85, strengthB1: 100, strengthG3: 75, strengthB3: 70,
    compareMode: "side", wipePosition: 50, contourVisible: true
  });
  const IDS = ["originalCanvas", "contourCanvas", "glowCanvas"];
  const el = Object.fromEntries([
    ...IDS.map(id => [id, document.getElementById(id)]),
    ...["sourceStatus", "photoSha", "photoDimensions", "buildSha", "presetStatus", "fishContourPath", "contourVisible", "compareMode", "wipePosition", "comparisonCaption", "resetButton", "exportPngButton", "exportJsonButton", "outerEnabled", "outerWidth", "outerIntensity", "coreEnabled", "coreWidth", "coreIntensity", "cometEnabled", "cometLength", "cometPosition", "cometFalloff", "edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled", "edgeFieldIntensity", "edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor", "opticalCoreIntensity", "opticalCoreWidth", "opticalCoreBlend", "innerBloomRadius", "innerBloomIntensity", "outerBloomRadius", "outerBloomIntensity", "atmosphericSpill", "blueSaturation", "goldWarmth", "backgroundCompensation", "strengthG1", "strengthB1", "strengthG3", "strengthB3", "outerWidthValue", "outerIntensityValue", "coreWidthValue", "coreIntensityValue", "cometLengthValue", "cometPositionValue", "cometFalloffValue", "edgeFieldIntensityValue", "opticalCoreIntensityValue", "opticalCoreWidthValue", "innerBloomRadiusValue", "innerBloomIntensityValue", "outerBloomRadiusValue", "outerBloomIntensityValue", "atmosphericSpillValue", "blueSaturationValue", "goldWarmthValue", "backgroundCompensationValue", "strengthG1Value", "strengthB1Value", "strengthG3Value", "strengthB3Value"].map(id => [id, document.getElementById(id)])
  ]);
  const contexts = Object.fromEntries(IDS.map(id => [id, el[id].getContext("2d", { alpha: false })]));
  const contourSvg = el.fishContourPath;
  const photo = new Image();
  const buffer = document.createElement("canvas");
  const bufferContext = buffer.getContext("2d", { alpha: false });
  const effectLayer = document.createElement("canvas");
  const edgeLayer = document.createElement("canvas");
  let effectContext = effectLayer.getContext("2d");
  let edgeContext = edgeLayer.getContext("2d", { alpha: true });
  let effectBounds = null;
  let edgeFieldInset = 0;
  let fixture;
  let opticalConfig;
  let islandLuminance = null;
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

  function hexToHsl(hex) {
    const [r0, g0, b0] = hexRgb(hex).map(value => value / 255);
    const max = Math.max(r0, g0, b0), min = Math.min(r0, g0, b0), delta = max - min;
    let h = 0;
    const l = (max + min) / 2;
    let s = 0;
    if (delta !== 0) {
      s = delta / (1 - Math.abs(2 * l - 1));
      if (max === r0) h = ((g0 - b0) / delta) % 6;
      else if (max === g0) h = (b0 - r0) / delta + 2;
      else h = (r0 - g0) / delta + 4;
      h = (h * 60 + 360) % 360;
    }
    return [h, s, l];
  }

  function hslToHex(h, s, l) {
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const x = c * (1 - Math.abs((h / 60) % 2 - 1));
    const m = l - c / 2;
    const rgb = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return `#${rgb.map(value => Math.round((value + m) * 255).toString(16).padStart(2, "0")).join("")}`;
  }

  function opticalColor(family, role) {
    const palette = opticalConfig.palette;
    const userBase = family === "blue" ? params.edgeFieldBlueColor : params.edgeFieldGoldColor;
    const key = family === "blue"
      ? (role === "core" ? "perimeter_blue_core" : "perimeter_blue_hot")
      : (role === "core" ? "perimeter_gold_core" : "perimeter_gold_hot");
    let [h, s, l] = hexToHsl(role === "base" ? userBase : palette[key]);
    if (family === "blue") s *= 0.70 + 0.30 * (params.blueSaturation / 100);
    else {
      h = 48 - 8 * (params.goldWarmth / 100);
      s *= 0.78 + 0.22 * (params.goldWarmth / 100);
    }
    return hslToHex(h, Math.max(0, Math.min(1, s)), l);
  }

  function applyPreset(id, render = true) {
    if (!opticalConfig?.presets?.[id]) return;
    const preset = opticalConfig.presets[id];
    params = {
      ...params,
      preset: id,
      opticalCoreWidth: preset.core_width_px,
      opticalCoreIntensity: Math.round(preset.core_intensity * 100),
      innerBloomRadius: preset.inner_bloom_radius_px,
      innerBloomIntensity: Math.round(preset.inner_bloom_intensity * 100),
      outerBloomRadius: preset.outer_bloom_radius_px,
      outerBloomIntensity: Math.round(preset.outer_bloom_intensity * 100),
      atmosphericSpill: Math.round(preset.atmospheric_spill_intensity * 100),
      strengthG1: Math.round(preset.island_strengths.G1 * 100),
      strengthB1: Math.round(preset.island_strengths.B1 * 100),
      strengthG3: Math.round(preset.island_strengths.G3 * 100),
      strengthB3: Math.round(preset.island_strengths.B3 * 100)
    };
    document.querySelectorAll("[data-optical-preset]").forEach(button => {
      const active = button.dataset.opticalPreset === id;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (el.presetStatus) {
      el.presetStatus.textContent = `${preset.label} · ${preset.renderer} · optical config ${OPTICAL_CONFIG_VERSION}`;
    }
    syncControls();
    if (render) scheduleRender("glow");
  }

  async function sha256(blob) {
    const bytes = await blob.arrayBuffer();
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return [...new Uint8Array(digest)].map(v => v.toString(16).padStart(2, "0")).join("");
  }

  async function loadAssets() {
    const [fixtureResponse, buildResponse, photoResponse, configResponse] = await Promise.all([
      fetch("./fixture.json", { cache: "no-store" }),
      fetch("./build-info.json", { cache: "no-store" }).catch(() => null),
      fetch(PHOTO_URL, { cache: "no-store" }),
      fetch(OPTICAL_CONFIG_URL, { cache: "no-store" })
    ]);
    if (!fixtureResponse.ok) throw new Error(`fixture.json HTTP ${fixtureResponse.status}`);
    if (!photoResponse.ok) throw new Error(`photo HTTP ${photoResponse.status}`);
    fixture = await fixtureResponse.json();
    if (!configResponse.ok) throw new Error(`optical config HTTP ${configResponse.status}`);
    opticalConfig = await configResponse.json();
    if (opticalConfig.schema_version !== OPTICAL_CONFIG_VERSION || opticalConfig.task_id !== "YUJIAN_EDGEGLOW_OPTICAL_ENHANCEMENT_ANDROID_V2") throw new Error("Optical config version or task identity mismatch.");
    applyPreset("enhanced", false);
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
    ctx.globalCompositeOperation = "source-over";
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
    ctx.globalCompositeOperation = "source-over";
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
    ctx.globalCompositeOperation = "source-over";
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
    const renderer = window.EdgeGlowReceivingLight;
    if (!renderer) throw new Error("Shared EdgeGlow Receiving Light renderer is not loaded.");
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const reviewOnlyAlphaExperiment = window.__EDGEGLOW_ALPHA_EXPERIMENT__ === true;
    const reviewOnlyRadialFieldExperiment = window.__EDGEGLOW_RADIAL_FIELD_EXPERIMENT__ === true;
    const reviewOnlyEdgeRibbonExperiment = window.__EDGEGLOW_EDGE_RIBBON_EXPERIMENT__ === true;
    const paths = edgeFieldGeometry.map(geometry => ({
      id: geometry.id,
      family: geometry.family,
      weight: geometry.weight,
      seed: geometry.seed,
      visibleRatio: geometry.visibleRatio,
      start: geometry.start,
      control1: geometry.control1,
      control2: geometry.control2,
      end: geometry.end
    }));
    renderer.paint(ctx, {
      width: photo.naturalWidth,
      height: photo.naturalHeight,
      photoContentRect: { left: 0, top: 0, right: photo.naturalWidth, bottom: photo.naturalHeight },
      paths,
      colors: {
        blue: params.edgeFieldBlueColor,
        blueHot: "#e7fbff",
        gold: params.edgeFieldGoldColor,
        goldHot: "#fff1c8"
      },
      baseAlpha: reviewOnlyAlphaExperiment
        ? renderer.reviewOnlyAlphaExperiment.baseAlpha
        : renderer.p1AlphaRange[1],
      reviewOnlyAlphaExperiment,
      reviewOnlyRadialFieldExperiment,
      reviewOnlyEdgeRibbonExperiment,
      depthRatio: 0.18,
      globalMultiplier: params.edgeFieldIntensity / 100,
      dpToCanvasPx: photo.naturalWidth / displayWidth,
      enabled: true,
      onReady: () => scheduleRender("glow")
    });
  }

  function edgeFieldPartsAt(geometry, seed = geometry.seed, visibleRatio = geometry.visibleRatio) {
    const visibleLength = geometry.length * Math.max(0, Math.min(1, visibleRatio));
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
    ctx.globalCompositeOperation = "source-over";
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
          ctx.filter = `blur(${1 * photoPxPerDp * EDGE_FIELD_RENDER_SCALE}px)`;
          ctx.strokeStyle = rgba(coreColor, Math.min(0.035, 0.025 * common));
          ctx.lineWidth = 2 * photoPxPerDp;
          ctx.stroke();
        } else {
          ctx.filter = `blur(${5 * photoPxPerDp * EDGE_FIELD_RENDER_SCALE}px)`;
          ctx.strokeStyle = rgba(coreColor, Math.min(0.055, 0.055 * common));
          ctx.lineWidth = 10 * photoPxPerDp;
          ctx.stroke();
          ctx.filter = `blur(${2 * photoPxPerDp * EDGE_FIELD_RENDER_SCALE}px)`;
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
    ctx.globalCompositeOperation = "source-over";
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
        ctx.filter = `blur(${(micro ? 1.4 : 2.1) * photoPxPerDp * EDGE_FIELD_RENDER_SCALE}px)`;
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
    ctx.globalCompositeOperation = "source-over";
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

  const OPTICAL_V2_ISLAND_IDS = ["G1", "B1", "G3", "B3"];

  function measureIslandLuminance(geometry) {
    if (islandLuminance === null) islandLuminance = {};
    if (Number.isFinite(islandLuminance[geometry.id])) return islandLuminance[geometry.id];
    const pixels = [];
    const [from, to] = edgeFieldPartsAt(geometry, geometry.seed + geometry.visibleRatio * 0.12)[0];
    for (let index = 1; index <= 9; index++) {
      const point = geometry.path.getPointAtLength(from + (to - from) * index / 10);
      const x = Math.max(0, Math.min(photo.naturalWidth - 1, Math.round(point.x)));
      const y = Math.max(0, Math.min(photo.naturalHeight - 1, Math.round(point.y)));
      pixels.push(bufferContext.getImageData(x, y, 1, 1).data);
    }
    const luminance = pixels.reduce((total, pixel) => {
      const r = pixel[0] / 255, g = pixel[1] / 255, b = pixel[2] / 255;
      return total + (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }, 0) / Math.max(1, pixels.length);
    islandLuminance[geometry.id] = luminance;
    return luminance;
  }

  function brightBackgroundGain(geometry) {
    const policy = opticalConfig.bright_background_compensation;
    if (!policy.enabled || params.backgroundCompensation <= 0) return 1;
    const luminance = Math.min(policy.max_luminance_for_gain, measureIslandLuminance(geometry));
    const over = Math.max(0, luminance - policy.luminance_threshold);
    const gain = 1 + policy.max_gain * (over / (policy.max_luminance_for_gain - policy.luminance_threshold)) * (params.backgroundCompensation / 100);
    return Math.max(1, Math.min(1 + policy.max_gain, gain));
  }

  function opticalPathGradient(ctx, first, last, family, alpha) {
    const gradient = ctx.createLinearGradient(first[0], first[1], last[0], last[1]);
    const base = opticalColor(family, "base");
    const core = opticalColor(family, "core");
    const hot = opticalColor(family, "hot");
    gradient.addColorStop(0, rgba(base, alpha * 0.08));
    gradient.addColorStop(0.18, rgba(base, alpha * 0.44));
    gradient.addColorStop(0.62, rgba(core, alpha * 0.80));
    gradient.addColorStop(0.86, rgba(hot, alpha));
    gradient.addColorStop(0.97, rgba(core, alpha * 0.72));
    gradient.addColorStop(1, rgba(base, alpha * 0.12));
    return gradient;
  }

  function drawOpticalPathLayer(ctx, geometry, from, to, layer) {
    const scale = photo.naturalWidth / opticalConfig.reference_canvas.width_px;
    const points = edgeFieldSegment(ctx, geometry, from, to, Math.max(2, scale * 1.5));
    if (points.length < 2) return;
    const first = points[0], last = points[points.length - 1];
    ctx.save();
    ctx.globalCompositeOperation = layer.blend;
    ctx.filter = layer.blurPx > 0 ? `blur(${layer.blurPx * scale * EDGE_FIELD_RENDER_SCALE}px)` : "none";
    ctx.strokeStyle = opticalPathGradient(ctx, first, last, geometry.family, layer.alpha);
    ctx.lineWidth = Math.max(0.8, layer.widthPx * scale);
    ctx.stroke();
    ctx.restore();
  }

  function drawEnhancedOpticalIslands(ctx, layerId) {
    if (!params.edgeFieldEnabled || !params.primaryIslandsEnabled || params.edgeFieldIntensity <= 0) return;
    const preset = opticalConfig.presets[params.preset] || opticalConfig.presets.enhanced;
    const scale = photo.naturalWidth / opticalConfig.reference_canvas.width_px;
    const globalIntensity = params.edgeFieldIntensity / 100;
    const islandStrength = { G1: params.strengthG1, B1: params.strengthB1, G3: params.strengthG3, B3: params.strengthB3 };
    const radiusTier = params.preset === "frozen_match" ? 1.12 : 1;
    for (const id of OPTICAL_V2_ISLAND_IDS) {
      const geometry = edgeFieldGeometry.find(path => path.id === id);
      if (!geometry) continue;
      const strength = islandStrength[id] / 100;
      const backgroundGain = brightBackgroundGain(geometry);
      const common = Math.min(1.28, globalIntensity * strength * backgroundGain);
      const parts = edgeFieldPartsAt(geometry, geometry.seed, Math.max(geometry.visibleRatio, 0.58));
      for (const [from, to] of parts) {
        if (layerId === "atmospheric_spill" && params.receivingLightEnabled) {
          drawOpticalPathLayer(ctx, geometry, from, to, {
            widthPx: preset.atmospheric_spill_px * 1.7,
            blurPx: preset.atmospheric_spill_px * 0.56,
            alpha: params.atmosphericSpill / 100 * common,
            blend: "source-over"
          });
        }
        if (layerId === "outer_bloom") drawOpticalPathLayer(ctx, geometry, from, to, {
          widthPx: (params.outerBloomRadius * 1.8 * radiusTier) + params.opticalCoreWidth,
          blurPx: params.outerBloomRadius * 0.46 * radiusTier,
          alpha: params.outerBloomIntensity / 100 * 0.74 * common,
          blend: "source-over"
        });
        if (layerId === "inner_bloom") drawOpticalPathLayer(ctx, geometry, from, to, {
          widthPx: (params.innerBloomRadius * 1.65) + params.opticalCoreWidth,
          blurPx: params.innerBloomRadius * 0.42,
          alpha: params.innerBloomIntensity / 100 * 0.78 * common,
          blend: "source-over"
        });
        if (layerId === "optical_core") drawOpticalPathLayer(ctx, geometry, from, to, {
          widthPx: params.opticalCoreWidth,
          blurPx: 0,
          alpha: params.opticalCoreIntensity / 100 * common,
          blend: "source-over"
        });
      }
    }
  }

  function opticalPhotoBlend(layerId) {
    const blendNames = opticalConfig.web_mapping.canvas_blend_mode_names;
    const configuredMode = opticalConfig.web_mapping.blend_modes[layerId] || "SOURCE_OVER";
    if (layerId !== "optical_core") return blendNames[configuredMode] || "source-over";
    const selectedMode = params.opticalCoreBlend === "additive"
      ? "ADDITIVE"
      : String(params.opticalCoreBlend || "source-over").replace("-", "_").toUpperCase();
    return blendNames[selectedMode] || blendNames[configuredMode] || "source-over";
  }

  function compositePhotoEdge(bufferContext, inset, blend) {
    bufferContext.save();
    bufferContext.setTransform(1, 0, 0, 1, 0, 0);
    bufferContext.filter = "none";
    bufferContext.globalAlpha = 1;
    bufferContext.globalCompositeOperation = blend;
    bufferContext.drawImage(edgeLayer, inset, inset, Math.ceil(photo.naturalWidth * EDGE_FIELD_RENDER_SCALE), Math.ceil(photo.naturalHeight * EDGE_FIELD_RENDER_SCALE), 0, 0, photo.naturalWidth, photo.naturalHeight);
    bufferContext.restore();
  }

  function drawPrimaryEdgeIslands(ctx) {
    if (!params.edgeFieldEnabled || !params.primaryIslandsEnabled || params.edgeFieldIntensity <= 0) return;
    drawEdgeFieldPathSet(ctx, edgeFieldGeometry, false);
  }

  function drawSecondaryEdgeFragments(ctx) {
    if (!params.edgeFieldEnabled || !params.secondaryFragmentsEnabled || params.edgeFieldIntensity <= 0) return;
    drawEdgeFieldPathSet(ctx, secondaryEdgeFieldGeometry, true);
  }

  function ensurePhotoEdgeLayer() {
    const displayWidth = el.glowCanvas.getBoundingClientRect().width || photo.naturalWidth;
    const photoPxPerDp = photo.naturalWidth / displayWidth;
    // Pad for the active preset's widest and softest corner pass before
    // upsampling, so V2 bloom is not clipped at the photo edge.
    const currentContract = opticalConfig?.presets?.current?.legacy_receiving_light;
    const enhanced = opticalConfig?.presets?.[params.preset] || opticalConfig?.presets?.enhanced;
    const referenceScale = photo.naturalWidth / opticalConfig.reference_canvas.width_px;
    const radiusTier = params.preset === "frozen_match" ? 1.12 : 1;
    const maxBlurDp = params.preset === "current"
      ? currentContract.blur_dp[0]
      : Math.max(enhanced.atmospheric_spill_px * 0.56, enhanced.outer_bloom_radius_px * 0.46 * radiusTier);
    const maxWidthDp = params.preset === "current"
      ? currentContract.width_dp[0]
      : Math.max(enhanced.atmospheric_spill_px * 1.7, enhanced.outer_bloom_radius_px * 1.8 * radiusTier);
    const layerScale = params.preset === "current" ? photoPxPerDp : referenceScale;
    const fullResolutionInset = Math.ceil((maxBlurDp * 3 + maxWidthDp / 2 + 24) * layerScale);
    const inset = Math.ceil(fullResolutionInset * EDGE_FIELD_RENDER_SCALE);
    const width = Math.ceil(photo.naturalWidth * EDGE_FIELD_RENDER_SCALE) + inset * 2;
    const height = Math.ceil(photo.naturalHeight * EDGE_FIELD_RENDER_SCALE) + inset * 2;
    if (edgeLayer.width !== width || edgeLayer.height !== height) {
      edgeLayer.width = width;
      edgeLayer.height = height;
      edgeContext = edgeLayer.getContext("2d", { alpha: true });
    }
    edgeFieldInset = fullResolutionInset;
    return inset;
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
    const inset = ensurePhotoEdgeLayer();
    edgeContext.setTransform(1, 0, 0, 1, 0, 0);
    edgeContext.filter = "none";
    edgeContext.globalAlpha = 1;
    edgeContext.globalCompositeOperation = "source-over";
    edgeContext.clearRect(0, 0, edgeLayer.width, edgeLayer.height);
    if (params.preset === "current") {
      edgeContext.setTransform(EDGE_FIELD_RENDER_SCALE, 0, 0, EDGE_FIELD_RENDER_SCALE, inset, inset);
      drawReceivingLight(edgeContext);
      drawPrimaryEdgeIslands(edgeContext);
      drawSecondaryEdgeFragments(edgeContext);
      drawCompanionHairlines(edgeContext, companionEdgeFieldGeometry);
      drawCompanionHairlines(edgeContext, microEdgeFieldGeometry, true);
      drawStaticEnergyNodes(edgeContext);
      edgeContext.setTransform(1, 0, 0, 1, 0, 0);
      compositePhotoEdge(bufferContext, inset, "source-over");
    } else {
      // Receiving Light is the broad perimeter base. Render it before the
      // Enhanced/Frozen optical detail layers so the default preset shows a
      // field around the photo instead of only thin path accents.
      edgeContext.setTransform(EDGE_FIELD_RENDER_SCALE, 0, 0, EDGE_FIELD_RENDER_SCALE, inset, inset);
      drawReceivingLight(edgeContext);
      edgeContext.setTransform(1, 0, 0, 1, 0, 0);
      compositePhotoEdge(bufferContext, inset, "source-over");
      for (const layerId of ["atmospheric_spill", "outer_bloom", "inner_bloom", "optical_core"]) {
        edgeContext.setTransform(1, 0, 0, 1, 0, 0);
        edgeContext.filter = "none";
        edgeContext.globalAlpha = 1;
        edgeContext.globalCompositeOperation = "source-over";
        edgeContext.clearRect(0, 0, edgeLayer.width, edgeLayer.height);
        edgeContext.setTransform(EDGE_FIELD_RENDER_SCALE, 0, 0, EDGE_FIELD_RENDER_SCALE, inset, inset);
        drawEnhancedOpticalIslands(edgeContext, layerId);
        edgeContext.setTransform(1, 0, 0, 1, 0, 0);
        compositePhotoEdge(bufferContext, inset, opticalPhotoBlend(layerId));
      }
    }
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
      const labels = { current: "Current", enhanced: "Enhanced", frozen_match: "Frozen Match", custom: "Custom" };
      el.comparisonCaption.textContent = `${labels[params.preset] || "Enhanced"} · 四角蓝金四层光场 · Fish Focus 暖金高亮`;
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
    el.opticalCoreIntensityValue.textContent = `${params.opticalCoreIntensity}%`;
    el.opticalCoreWidthValue.textContent = `${Number(params.opticalCoreWidth).toFixed(1)} px / 600`;
    el.innerBloomRadiusValue.textContent = `${params.innerBloomRadius} px / 600`;
    el.innerBloomIntensityValue.textContent = `${params.innerBloomIntensity}%`;
    el.outerBloomRadiusValue.textContent = `${params.outerBloomRadius} px / 600`;
    el.outerBloomIntensityValue.textContent = `${params.outerBloomIntensity}%`;
    el.atmosphericSpillValue.textContent = `${params.atmosphericSpill}%`;
    el.blueSaturationValue.textContent = `${params.blueSaturation}%`;
    el.goldWarmthValue.textContent = `${params.goldWarmth}%`;
    el.backgroundCompensationValue.textContent = `${params.backgroundCompensation}%`;
    for (const id of OPTICAL_V2_ISLAND_IDS) el[`strength${id}Value`].textContent = `${params[`strength${id}`]}%`;
  }

  function syncControls() {
    for (const id of ["outerEnabled", "coreEnabled", "cometEnabled", "edgeFieldEnabled", "receivingLightEnabled", "primaryIslandsEnabled", "secondaryFragmentsEnabled", "companionHairlinesEnabled", "contourVisible"]) el[id].checked = Boolean(params[id]);
    for (const id of ["outerWidth", "outerIntensity", "coreWidth", "coreIntensity", "cometLength", "cometPosition", "cometFalloff", "edgeFieldIntensity", "edgeFieldBlueColor", "edgeFieldGoldColor", "fishGoldColor", "fishCoreColor", "opticalCoreIntensity", "opticalCoreWidth", "opticalCoreBlend", "innerBloomRadius", "innerBloomIntensity", "outerBloomRadius", "outerBloomIntensity", "atmosphericSpill", "blueSaturation", "goldWarmth", "backgroundCompensation", "strengthG1", "strengthB1", "strengthG3", "strengthB3", "compareMode", "wipePosition"]) el[id].value = params[id];
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
    opticalCoreIntensity: "opticalCoreIntensity", opticalCoreWidth: "opticalCoreWidth", opticalCoreBlend: "opticalCoreBlend",
    innerBloomRadius: "innerBloomRadius", innerBloomIntensity: "innerBloomIntensity",
    outerBloomRadius: "outerBloomRadius", outerBloomIntensity: "outerBloomIntensity",
    atmosphericSpill: "atmosphericSpill", blueSaturation: "blueSaturation", goldWarmth: "goldWarmth",
    backgroundCompensation: "backgroundCompensation", strengthG1: "strengthG1", strengthB1: "strengthB1",
    strengthG3: "strengthG3", strengthB3: "strengthB3",
    compareMode: "compareMode", wipePosition: "wipePosition"
  };
  const opticalControls = new Set(["opticalCoreIntensity", "opticalCoreWidth", "opticalCoreBlend", "innerBloomRadius", "innerBloomIntensity", "outerBloomRadius", "outerBloomIntensity", "atmosphericSpill", "blueSaturation", "goldWarmth", "backgroundCompensation", "strengthG1", "strengthB1", "strengthG3", "strengthB3"]);
  for (const [controlId, param] of Object.entries(controlToParam)) {
    el[controlId].addEventListener("input", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      if (param === "cometFalloff") params[param] = Number(params[param]);
      if (opticalControls.has(param) && params.preset === "current") params.preset = "custom";
      if (params.preset === "custom" && el.presetStatus) el.presetStatus.textContent = "Custom · OPTICAL_FOUR_LAYER_V2 · 手动参数";
      scheduleRender(param === "contourVisible" ? "contour" : (param === "compareMode" || param === "wipePosition" ? "compare" : "glow"));
    });
    el[controlId].addEventListener("change", () => {
      const control = el[controlId];
      params[param] = control.type === "checkbox" ? control.checked : (control.type === "range" ? Number(control.value) : control.value);
      if (opticalControls.has(param) && params.preset === "current") params.preset = "custom";
      scheduleRender(param === "contourVisible" ? "contour" : (param === "compareMode" || param === "wipePosition" ? "compare" : "glow"));
    });
  }
  document.querySelectorAll("[data-optical-preset]").forEach(button => {
    button.addEventListener("click", () => applyPreset(button.dataset.opticalPreset));
  });
  window.addEventListener("resize", () => scheduleRender("all"), { passive: true });

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
      optical_config: { schema_version: OPTICAL_CONFIG_VERSION, source: "design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json", preset: params.preset, shared_mapping: opticalConfig.android_mapping, bright_background_compensation: opticalConfig.bright_background_compensation, blend_mode_trials: opticalConfig.blend_mode_trials, selected_optical_core_blend: params.opticalCoreBlend, selected_preset_contract: opticalConfig.presets[params.preset] || null },
      upstream: { repository: "yu-day/EdgeGlowView", commit: "46ce28221ed30bcd7763ff13ecdd96171756eb60", license: "MIT", copyright: "Copyright (c) 2025 WANG YU" },
      edge_field: { geometry_source: EDGE_FIELD_SOURCE, paths: [...EDGE_FIELD_PATHS, ...EDGE_FIELD_SECONDARY_PATHS, ...EDGE_FIELD_COMPANION_PATHS, ...EDGE_FIELD_MICRO_PATHS], primary_path_ids: EDGE_FIELD_PATHS.map(path => path.id), secondary_path_ids: EDGE_FIELD_SECONDARY_PATHS.map(path => path.id), companion_path_ids: EDGE_FIELD_COMPANION_PATHS.map(path => path.id), micro_path_ids: EDGE_FIELD_MICRO_PATHS.map(path => path.id), static_seed_offsets: true, secondary_seed_authority: "Android runtime values .07/.11/.09; not Frozen; see RP-A03", companion_geometry_authority: "RecognitionAmbientField.kt frozen companion/micro geometry", animation: false, receiving_light: "shared SVG path-field renderer; path masks, feGaussianBlur outer/mid, core ridge, photoContentRect clip, P1 Quiet Gap mask, source-over", receiving_light_renderer: window.EdgeGlowReceivingLight.version, receiving_base_alpha_range: window.EdgeGlowReceivingLight.p1AlphaRange, receiving_final_alpha_cap: window.EdgeGlowReceivingLight.outputAlphaCap, receiving_depth_range: window.EdgeGlowReceivingLight.p1DepthRange, receiving_layer_profile: window.EdgeGlowReceivingLight.layers, receiving_path_coverage_by_island: window.EdgeGlowReceivingLight.coverageByPath, receiving_inset_fraction: window.EdgeGlowReceivingLight.inset, receiving_min_path_weight: window.EdgeGlowReceivingLight.minPathWeight, corner_color_roles: { top_left: "gold", top_right: "blue", bottom_left: "gold", bottom_right: "blue" }, receiving_alpha_authority: "P1 base alpha range 0.03–0.07; shared renderer caps the final 8-bit transparent layer at floor(0.07*255)/255; Web review only, not Android runtime proof", edge_effect_compositor: "half-resolution padded transparent offscreen layer; upsample and crop back to original photo pixels; preserves Frozen-reference perimeter blur at photo bounds", edge_effect_render_scale: EDGE_FIELD_RENDER_SCALE, edge_effect_padding_px: edgeFieldInset, perimeter_blend_mode: "source-over R02 hue-preserving photo-edge candidate; fish contour remains separately warm gold", web_palette: { blue: "#5ab2fc", gold: "#e0b644", authority: "sampled from Frozen 02 photo-edge raster; web visual candidate" }, android_reference_palette: { blue: "#8bc9e8", blue_hot: "#b2e5f5", gold: "#efc14c", gold_hot: "#f7d05b" } },
      fish_highlight: { source: fixture.contour.source, composite_mode: "source-over hue-preserving warm-gold composition", palette: { outline: params.fishGoldColor, core: params.fishCoreColor }, blue_perimeter_excluded: true, layer_order: ["outer_bloom", "core_highlight", "static_comet"] },
      renderer: "Canvas photo compositor + shared SVG Receiving Light renderer; not Android runtime",
      parameters: { ...params },
      island_luminance_samples: islandLuminance,
      build: buildInfo
    };
    downloadBlob(new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" }), "EdgeGlowView_Fish_Static_Parameters.json");
  });
  el.resetButton.addEventListener("click", () => {
    params = { ...DEFAULTS };
    applyPreset("enhanced", false);
    syncControls();
    renderAll();
  });

  window.__EDGEGLOW_LAB__ = {
    get state() { return { ...params }; },
    get config() { return opticalConfig; },
    get pathLength() { return pathLength; },
    get photoDimensions() { return [photo.naturalWidth, photo.naturalHeight]; },
    get effectBounds() { return { ...effectBounds }; },
    get edgeFieldInset() { return edgeFieldInset; },
    get edgeFieldPaths() { return [...edgeFieldGeometry, ...secondaryEdgeFieldGeometry, ...companionEdgeFieldGeometry, ...microEdgeFieldGeometry].map(({ path, ...definition }) => ({ ...definition, length: definition.length })); },
    setPreset(id) { applyPreset(id); return { ...params }; },
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
