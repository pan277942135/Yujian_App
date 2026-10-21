/* Shared static Receiving Light renderer for the Optical Lab and EdgeGlow acceptance page.
 * Path data comes from the existing P1 B1/G1/G3/B3 curves. This Web renderer is
 * review evidence only; it does not implement or certify Android rendering.
 */
(() => {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";
  const VERSION = "edgeglow-receiving-light-svg-v1";
  const P1_ALPHA_RANGE = Object.freeze([0.03, 0.07]);
  const P1_DEPTH_RANGE = Object.freeze([0.08, 0.18]);
  const OUTPUT_ALPHA_CAP = Math.floor(P1_ALPHA_RANGE[1] * 255) / 255;
  const REVIEW_ONLY_ALPHA_EXPERIMENT = Object.freeze({
    baseAlpha: 0.10,
    outputAlphaCap: Math.floor(0.10 * 255) / 255
  });
  const MIN_PATH_WEIGHT = 0.54;
  const COVERAGE_BY_PATH = Object.freeze({ B1: 1.0, G1: 0.84, G3: 0.60, B3: 0.38 });
  const INSET = Object.freeze({ x: 0.028, y: 0.022 });
  const QUIET_GAPS = Object.freeze({
    Q_TOP: Object.freeze([0.32, 0.68]),
    Q_LEFT: Object.freeze([0.38, 0.62]),
    Q_RIGHT: Object.freeze([0.46, 0.58]),
    Q_BOTTOM: Object.freeze([0.40, 0.62])
  });
  const ISLAND_ORDER = Object.freeze(["B1", "G1", "G3", "B3"]);
  const LAYERS = Object.freeze([
    Object.freeze({ id: "outer", alphaShare: 0.68, widthRatio: 1.0, blurRatio: 0.0857 }),
    Object.freeze({ id: "mid", alphaShare: 0.24, widthRatio: 0.46, blurRatio: 0.030 }),
    Object.freeze({ id: "core", alphaShare: 0.08, widthRatio: null, blurRatio: 0.35 })
  ]);
  const DEFAULT_COLORS = Object.freeze({
    blue: "#5ab2fc",
    blueHot: "#e7fbff",
    gold: "#e0b644",
    goldHot: "#fff1c8"
  });
  const GRADIENT_STOPS = Object.freeze([
    Object.freeze([0.00, "base", 0.52]),
    Object.freeze([0.38, "base", 0.88]),
    Object.freeze([0.82, "hot", 0.82]),
    Object.freeze([1.00, "hot", 0.68])
  ]);
  const cache = new Map();

  const clamp = (value, low, high) => Math.min(high, Math.max(low, value));
  const finite = value => Number.isFinite(Number(value));
  const number = value => Number(Number(value).toFixed(4));
  const escapeAttr = value => String(value).replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  const validColor = value => /^#[0-9a-f]{6}$/i.test(String(value || ""));
  const safeId = value => String(value).replace(/[^a-zA-Z0-9_-]/g, "_");

  function normalizeOptions(options) {
    if (!options || !finite(options.width) || !finite(options.height)) throw new Error("Receiving Light requires finite canvas dimensions.");
    const width = Math.round(Number(options.width));
    const height = Math.round(Number(options.height));
    if (width < 1 || height < 1) throw new Error("Receiving Light canvas dimensions must be positive.");
    const rect = options.photoContentRect || { left: 0, top: 0, right: width, bottom: height };
    const normalizedRect = {
      left: Number(rect.left), top: Number(rect.top),
      right: Number(rect.right), bottom: Number(rect.bottom)
    };
    if (!Object.values(normalizedRect).every(Number.isFinite) ||
        normalizedRect.right <= normalizedRect.left || normalizedRect.bottom <= normalizedRect.top) {
      throw new Error("Receiving Light requires the shared photoContentRect.");
    }
    if (normalizedRect.left < -0.01 || normalizedRect.top < -0.01 ||
        normalizedRect.right > width + 0.01 || normalizedRect.bottom > height + 0.01) {
      throw new Error("photoContentRect must be inside the Receiving Light canvas.");
    }
    const reviewOnlyAlphaExperiment = options.reviewOnlyAlphaExperiment === true;
    const outputAlphaCap = reviewOnlyAlphaExperiment
      ? REVIEW_ONLY_ALPHA_EXPERIMENT.outputAlphaCap
      : OUTPUT_ALPHA_CAP;
    const baseAlpha = Number(options.baseAlpha ?? P1_ALPHA_RANGE[1]);
    const maxBaseAlpha = reviewOnlyAlphaExperiment
      ? REVIEW_ONLY_ALPHA_EXPERIMENT.baseAlpha
      : P1_ALPHA_RANGE[1];
    if (!Number.isFinite(baseAlpha) || baseAlpha < P1_ALPHA_RANGE[0] || baseAlpha > maxBaseAlpha ||
        (reviewOnlyAlphaExperiment && baseAlpha !== REVIEW_ONLY_ALPHA_EXPERIMENT.baseAlpha)) {
      throw new Error(reviewOnlyAlphaExperiment
        ? "Review-only Alpha experiment accepts exactly base Alpha 0.10."
        : "P1 Receiving Light base Alpha must remain in [0.03, 0.07].");
    }
    const requestedDepth = Number(options.depthRatio ?? 0.14);
    if (!Number.isFinite(requestedDepth) || requestedDepth < P1_DEPTH_RANGE[0] || requestedDepth > P1_DEPTH_RANGE[1]) {
      throw new Error("P1 Receiving Light edge depth must remain in [0.08, 0.18].");
    }
    const colors = { ...DEFAULT_COLORS, ...(options.colors || {}) };
    for (const key of Object.keys(DEFAULT_COLORS)) {
      if (!validColor(colors[key])) throw new Error("Invalid Receiving Light color for " + key + ".");
    }
    const byId = new Map((options.paths || []).map(path => [path.id, path]));
    const paths = ISLAND_ORDER.map(id => {
      const path = byId.get(id);
      if (!path) throw new Error("Missing frozen primary path " + id + ".");
      for (const key of ["start", "control1", "control2", "end"]) {
        if (!Array.isArray(path[key]) || path[key].length !== 2 || !path[key].every(finite)) {
          throw new Error("Invalid P1 coordinates on path " + id + ".");
        }
      }
      return {
        id,
        family: path.family === "gold" ? "gold" : "blue",
        weight: clamp(Number(path.weight ?? path.island_weight ?? 1), 0, 1),
        seed: Number(path.seed ?? path.runtime_seed ?? 0),
        visibleRatio: clamp(Number(path.visibleRatio ?? path.visible_ratio ?? 0), 0, 1),
        start: path.start.map(Number),
        control1: path.control1.map(Number),
        control2: path.control2.map(Number),
        end: path.end.map(Number)
      };
    });
    return {
      width, height, rect: normalizedRect, paths, colors,
      baseAlpha, depthRatio: requestedDepth,
      reviewOnlyAlphaExperiment, outputAlphaCap,
      globalMultiplier: clamp(Number(options.globalMultiplier ?? 1), 0, 1),
      dpToCanvasPx: clamp(Number(options.dpToCanvasPx ?? 1), 0.1, 100),
      enabled: options.enabled !== false
    };
  }

  function pathData(path, rect) {
    const width = rect.right - rect.left;
    const height = rect.bottom - rect.top;
    const p = point => [rect.left + point[0] * width, rect.top + point[1] * height];
    const a = p(path.start), b = p(path.control1), c = p(path.control2), d = p(path.end);
    return "M " + a[0] + " " + a[1] + " C " + b[0] + " " + b[1] + " " + c[0] + " " + c[1] + " " + d[0] + " " + d[1];
  }

  function mapInset(point, rect) {
    const rectWidth = rect.right - rect.left;
    const rectHeight = rect.bottom - rect.top;
    return [
      rect.left + INSET.x * rectWidth + (point[0] - rect.left) * (1 - 2 * INSET.x),
      rect.top + INSET.y * rectHeight + (point[1] - rect.top) * (1 - 2 * INSET.y)
    ];
  }

  function quietMaskMarkup(rect, width, height, maxDepth) {
    const photoWidth = rect.right - rect.left;
    const photoHeight = rect.bottom - rect.top;
    const gaps = QUIET_GAPS;
    const regions = [
      [rect.left + gaps.Q_TOP[0] * photoWidth, rect.top, (gaps.Q_TOP[1] - gaps.Q_TOP[0]) * photoWidth, maxDepth],
      [rect.left, rect.top + gaps.Q_LEFT[0] * photoHeight, maxDepth, (gaps.Q_LEFT[1] - gaps.Q_LEFT[0]) * photoHeight],
      [rect.right - maxDepth, rect.top + gaps.Q_RIGHT[0] * photoHeight, maxDepth, (gaps.Q_RIGHT[1] - gaps.Q_RIGHT[0]) * photoHeight],
      [rect.left + gaps.Q_BOTTOM[0] * photoWidth, rect.bottom - maxDepth, (gaps.Q_BOTTOM[1] - gaps.Q_BOTTOM[0]) * photoWidth, maxDepth]
    ];
    const cuts = regions.map(region =>
      '<rect x="' + number(region[0]) + '" y="' + number(region[1]) + '" width="' + number(region[2]) + '" height="' + number(region[3]) + '" fill="#000"/>'
    ).join("");
    return '<mask id="quiet-gaps" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse" ' +
      'mask-type="luminance" x="0" y="0" width="' + width + '" height="' + height + '">' +
      '<rect x="0" y="0" width="' + width + '" height="' + height + '" fill="#fff"/>' + cuts + '</mask>';
  }

  function stopsMarkup(id, x1, y1, x2, y2, baseColor, hotColor) {
    const stops = GRADIENT_STOPS.map(stop =>
      '<stop offset="' + number(stop[0] * 100) + '%" stop-color="' +
      (stop[1] === "base" ? baseColor : hotColor) + '" stop-opacity="' + stop[2] + '"/>'
    ).join("");
    return '<linearGradient id="' + id + '" gradientUnits="userSpaceOnUse" x1="' + number(x1) +
      '" y1="' + number(y1) + '" x2="' + number(x2) + '" y2="' + number(y2) + '">' + stops + '</linearGradient>';
  }

  function buildSvg(config) {
    const width = config.width, height = config.height, rect = config.rect;
    const photoWidth = rect.right - rect.left, photoHeight = rect.bottom - rect.top;
    const minDimension = Math.min(photoWidth, photoHeight);
    const depthPx = config.depthRatio * minDimension;
    const outerSigma = depthPx * LAYERS[0].blurRatio;
    const midSigma = depthPx * LAYERS[1].blurRatio;
    const coreSigma = LAYERS[2].blurRatio * config.dpToCanvasPx;
    const padding = Math.ceil(depthPx / 2 + outerSigma * 4 + 2);
    const maxDepth = Math.min(minDimension * P1_DEPTH_RANGE[1], depthPx / 2 + outerSigma * 3);
    const photoClip = '<clipPath id="photo-clip" clipPathUnits="userSpaceOnUse"><rect x="' +
      number(rect.left) + '" y="' + number(rect.top) + '" width="' + number(photoWidth) +
      '" height="' + number(photoHeight) + '"/></clipPath>';
    const filterFrame = ' x="' + number(rect.left - padding) + '" y="' + number(rect.top - padding) +
      '" width="' + number(photoWidth + padding * 2) + '" height="' + number(photoHeight + padding * 2) + '"';
    const filters = [
      '<filter id="blur-outer" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse"' + filterFrame +
        ' color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="' + number(outerSigma) + '"/></filter>',
      '<filter id="blur-mid" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse"' + filterFrame +
        ' color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="' + number(midSigma) + '"/></filter>',
      '<filter id="blur-core" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse"' + filterFrame +
        ' color-interpolation-filters="sRGB"><feGaussianBlur in="SourceAlpha" stdDeviation="' + number(coreSigma) + '"/></filter>'
    ].join("");
    const alphaTable = Array.from({ length: 256 }, (_, index) =>
      number(Math.min(index / 255, config.outputAlphaCap))
    ).join(" ");
    const alphaCap = '<filter id="alpha-cap" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" ' +
      'x="0" y="0" width="' + width + '" height="' + height + '">' +
      '<feComponentTransfer><feFuncA type="table" tableValues="' + alphaTable + '"/></feComponentTransfer></filter>';
    const defs = [photoClip, quietMaskMarkup(rect, width, height, maxDepth), filters, alphaCap].join("");
    const measureSvg = document.createElementNS(SVG_NS, "svg");
    measureSvg.setAttribute("width", String(width));
    measureSvg.setAttribute("height", String(height));
    measureSvg.setAttribute("viewBox", "0 0 " + width + " " + height);
    measureSvg.setAttribute("aria-hidden", "true");
    measureSvg.style.cssText = "position:fixed;left:-100000px;top:-100000px;visibility:hidden;pointer-events:none;overflow:visible";
    document.body.appendChild(measureSvg);
    const defsMarkup = [];
    const layersMarkup = [];
    try {
      for (const path of config.paths) {
        const sourcePath = document.createElementNS(SVG_NS, "path");
        sourcePath.setAttribute("d", pathData(path, rect));
        measureSvg.appendChild(sourcePath);
        const length = sourcePath.getTotalLength();
        if (!(length > 1)) throw new Error("P1 path has no measurable geometry: " + path.id);
        const coverage = COVERAGE_BY_PATH[path.id] ?? path.visibleRatio;
        const visibleLength = length * clamp(coverage, 0, 1);
        const start = ((path.seed % 1) + 1) % 1 * length;
        const end = start + visibleLength;
        const intervals = end <= length
          ? [[start, end]]
          : [[start, length], [0, end - length]];
        const sampleStep = Math.max(2, minDimension * 0.0075);
        const islandWeight = Math.max(MIN_PATH_WEIGHT, path.weight);
        const baseAlpha = config.baseAlpha * config.globalMultiplier * islandWeight;
        const baseColor = path.family === "gold" ? config.colors.gold : config.colors.blue;
        const hotColor = path.family === "gold" ? config.colors.goldHot : config.colors.blueHot;

        intervals.forEach((interval, partIndex) => {
          const count = Math.max(2, Math.ceil((interval[1] - interval[0]) / sampleStep));
          const points = [];
          for (let index = 0; index <= count; index++) {
            const distance = interval[0] + (interval[1] - interval[0]) * index / count;
            const point = sourcePath.getPointAtLength(distance);
            points.push(mapInset([point.x, point.y], rect));
          }
          if (points.length < 2) return;
          const pathD = "M " + points.map((point, index) =>
            (index ? "L " : "") + number(point[0]) + " " + number(point[1])
          ).join(" ");
          const idRoot = safeId(path.id + "-" + partIndex);
          for (const layer of LAYERS) {
            const maskId = "mask-" + idRoot + "-" + layer.id;
            const gradientId = "gradient-" + idRoot + "-" + layer.id;
            const strokeWidth = layer.widthRatio === null
              ? Math.max(1, 1.8 * config.dpToCanvasPx)
              : depthPx * layer.widthRatio;
            const blurFilter = layer.id === "outer" ? "blur-outer" :
              layer.id === "mid" ? "blur-mid" : "blur-core";
            defsMarkup.push(
              '<mask id="' + maskId + '" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse" ' +
              'mask-type="alpha" x="' + number(rect.left - padding) + '" y="' + number(rect.top - padding) +
              '" width="' + number(photoWidth + padding * 2) + '" height="' + number(photoHeight + padding * 2) + '">' +
              '<path d="' + pathD + '" fill="none" stroke="#fff" stroke-width="' + number(strokeWidth) +
              '" stroke-linecap="round" stroke-linejoin="round" filter="url(#' + blurFilter + ')"/></mask>'
            );
            const gradientMarkup = stopsMarkup(gradientId, points[0][0], points[0][1],
              points[points.length - 1][0], points[points.length - 1][1], baseColor, hotColor);
            defsMarkup.push(gradientMarkup);
            const opacity = baseAlpha * layer.alphaShare;
            layersMarkup.push(
              '<rect x="' + number(rect.left) + '" y="' + number(rect.top) + '" width="' + number(photoWidth) +
              '" height="' + number(photoHeight) + '" fill="url(#' + gradientId + ')" mask="url(#' + maskId +
              ')" opacity="' + number(opacity) + '"/>'
            );
          }
        });
        measureSvg.removeChild(sourcePath);
      }
    } finally {
      measureSvg.remove();
    }
    return '<svg xmlns="http://www.w3.org/2000/svg" width="' + width + '" height="' + height +
      '" viewBox="0 0 ' + width + ' ' + height + '">' +
      '<defs>' + defs + defsMarkup.join("") + '</defs>' +
      '<g filter="url(#alpha-cap)"><g clip-path="url(#photo-clip)" mask="url(#quiet-gaps)">' +
      layersMarkup.join("") + '</g></g></svg>';
  }

  function stableKey(config) {
    return JSON.stringify({
      version: VERSION, width: config.width, height: config.height, rect: config.rect,
      paths: config.paths, colors: config.colors, baseAlpha: config.baseAlpha,
      depthRatio: config.depthRatio, globalMultiplier: config.globalMultiplier,
      reviewOnlyAlphaExperiment: config.reviewOnlyAlphaExperiment,
      outputAlphaCap: config.outputAlphaCap,
      dpToCanvasPx: config.dpToCanvasPx, enabled: config.enabled,
      coverage: COVERAGE_BY_PATH, inset: INSET, gaps: QUIET_GAPS, layers: LAYERS
    });
  }

  function setStatus(status, details) {
    window.__EDGEGLOW_RECEIVING_LIGHT_STATUS__ = {
      renderer: VERSION, status, alpha_cap: OUTPUT_ALPHA_CAP,
      base_alpha_range: P1_ALPHA_RANGE, depth_range: P1_DEPTH_RANGE,
      ...(details || {})
    };
  }

  function paint(context, options) {
    if (!context || typeof context.drawImage !== "function" || !context.canvas) {
      throw new Error("Receiving Light requires a CanvasRenderingContext2D.");
    }
    const config = normalizeOptions(options);
    if (!config.enabled || config.globalMultiplier <= 0) {
      context.canvas.dataset.receivingLightRenderer = VERSION;
      context.canvas.dataset.receivingLightStatus = "disabled";
      setStatus("disabled");
      return { status: "disabled", renderer: VERSION };
    }
    const key = stableKey(config);
    let entry = cache.get(key);
    if (!entry) {
      const svg = buildSvg(config);
      const blobUrl = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml;charset=utf-8" }));
      const image = new Image();
      entry = { image, blobUrl, ready: false, failed: false, callbacks: new Set() };
      image.onload = () => {
        entry.ready = true;
        URL.revokeObjectURL(entry.blobUrl);
        entry.blobUrl = "";
        setStatus("ready", {
          cache_key: key, canvas: [config.width, config.height],
          alpha_mode: config.reviewOnlyAlphaExperiment ? "REVIEW_ONLY_ALPHA_EXPERIMENT" : "P1",
          base_alpha: config.baseAlpha, alpha_cap: config.outputAlphaCap
        });
        for (const callback of entry.callbacks) callback();
        entry.callbacks.clear();
      };
      image.onerror = () => {
        entry.failed = true;
        if (entry.blobUrl) URL.revokeObjectURL(entry.blobUrl);
        setStatus("failed", { cache_key: key, message: "SVG Receiving Light failed to rasterize." });
        console.error("Shared SVG Receiving Light failed to rasterize.");
      };
      image.src = blobUrl;
      cache.set(key, entry);
      while (cache.size > 16) {
        const oldest = cache.keys().next().value;
        const old = cache.get(oldest);
        if (old && old.blobUrl) URL.revokeObjectURL(old.blobUrl);
        cache.delete(oldest);
      }
    }
    context.canvas.dataset.receivingLightRenderer = VERSION;
    context.canvas.dataset.receivingLightStatus = entry.ready ? "ready" : entry.failed ? "failed" : "pending";
    if (entry.failed) return { status: "failed", renderer: VERSION, cache_key: key };
    if (!entry.ready) {
      if (typeof options.onReady === "function") entry.callbacks.add(options.onReady);
      setStatus("pending", {
        cache_key: key, canvas: [config.width, config.height],
        alpha_mode: config.reviewOnlyAlphaExperiment ? "REVIEW_ONLY_ALPHA_EXPERIMENT" : "P1",
        base_alpha: config.baseAlpha, alpha_cap: config.outputAlphaCap
      });
      return { status: "pending", renderer: VERSION, cache_key: key };
    }
    context.save();
    context.globalAlpha = 1;
    context.globalCompositeOperation = "source-over";
    context.filter = "none";
    context.shadowBlur = 0;
    context.drawImage(entry.image, 0, 0, config.width, config.height);
    context.restore();
    setStatus("ready", {
      cache_key: key, canvas: [config.width, config.height],
      alpha_mode: config.reviewOnlyAlphaExperiment ? "REVIEW_ONLY_ALPHA_EXPERIMENT" : "P1",
      base_alpha: config.baseAlpha, alpha_cap: config.outputAlphaCap
    });
    return { status: "ready", renderer: VERSION, cache_key: key };
  }

  window.EdgeGlowReceivingLight = Object.freeze({
    version: VERSION,
    p1AlphaRange: P1_ALPHA_RANGE,
    p1DepthRange: P1_DEPTH_RANGE,
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    reviewOnlyAlphaExperiment: REVIEW_ONLY_ALPHA_EXPERIMENT,
    minPathWeight: MIN_PATH_WEIGHT,
    coverageByPath: COVERAGE_BY_PATH,
    inset: INSET,
    quietGaps: QUIET_GAPS,
    layers: LAYERS,
    defaultColors: DEFAULT_COLORS,
    paint
  });
})();
