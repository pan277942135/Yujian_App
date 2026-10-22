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
  const REVIEW_ONLY_RADIAL_FIELD_EXPERIMENT = Object.freeze({
    baseAlpha: P1_ALPHA_RANGE[1],
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    depthRatio: P1_DEPTH_RANGE[1]
  });
  const REVIEW_ONLY_EDGE_RIBBON_EXPERIMENT = Object.freeze({
    baseAlpha: P1_ALPHA_RANGE[1],
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    depthRatio: P1_DEPTH_RANGE[1]
  });
  const REVIEW_ONLY_CONTINUOUS_FIELD_EXPERIMENT = Object.freeze({
    baseAlpha: P1_ALPHA_RANGE[1],
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    depthRatio: P1_DEPTH_RANGE[1],
    version: "perimeter-anchored-continuous-field-v1"
  });
  const REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT = Object.freeze({
    baseAlpha: P1_ALPHA_RANGE[1],
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    depthRatio: P1_DEPTH_RANGE[1],
    version: "p1-path-projected-spatial-field-v2"
  });
  const CONTINUOUS_FIELD_PROFILE = Object.freeze({
    ambientEnergy: 0.035,
    minimumPerimeterSigma: 0.018,
    maximumPerimeterSigma: 0.095,
    pathSpreadScale: 0.70,
    outerSigmaDepth: 0.62,
    midSigmaDepth: 0.30,
    coreSigmaDepth: 0.08,
    outerShare: 0.42,
    midShare: 0.40,
    coreShare: 0.18,
    localDepthScaleMinimum: 0.78,
    quietFeatherRatio: 0.02,
    quietLowLevel: 0.12,
    energyResponseExponent: 0.52,
    energyBins: 256,
    maximumCachedFieldPixels: 4_194_304
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
    const reviewOnlyRadialFieldExperiment = options.reviewOnlyRadialFieldExperiment === true;
    const reviewOnlyEdgeRibbonExperiment = options.reviewOnlyEdgeRibbonExperiment === true;
    const reviewOnlyContinuousFieldExperiment = options.reviewOnlyContinuousFieldExperiment === true;
    const reviewOnlySpatialFieldExperiment = options.reviewOnlySpatialFieldExperiment === true;
    if ([reviewOnlyAlphaExperiment, reviewOnlyRadialFieldExperiment, reviewOnlyEdgeRibbonExperiment, reviewOnlyContinuousFieldExperiment, reviewOnlySpatialFieldExperiment].filter(Boolean).length > 1) {
      throw new Error("Alpha-only, radial-field, edge-ribbon, continuous-field, and spatial-field review experiments must remain isolated.");
    }
    const quietGapVariant = String(options.quietGapVariant || "B").toUpperCase();
    if (!/^[ABC]$/.test(quietGapVariant)) throw new Error("Quiet Gap review variant must be A, B, or C.");
    const outputAlphaCap = reviewOnlyAlphaExperiment
      ? REVIEW_ONLY_ALPHA_EXPERIMENT.outputAlphaCap
      : OUTPUT_ALPHA_CAP;
    const baseAlpha = Number(options.baseAlpha ?? P1_ALPHA_RANGE[1]);
    const maxBaseAlpha = reviewOnlyAlphaExperiment
      ? REVIEW_ONLY_ALPHA_EXPERIMENT.baseAlpha
      : P1_ALPHA_RANGE[1];
    if (!Number.isFinite(baseAlpha) || baseAlpha < P1_ALPHA_RANGE[0] || baseAlpha > maxBaseAlpha ||
        (reviewOnlyAlphaExperiment && baseAlpha !== REVIEW_ONLY_ALPHA_EXPERIMENT.baseAlpha) ||
        (reviewOnlyRadialFieldExperiment && baseAlpha !== REVIEW_ONLY_RADIAL_FIELD_EXPERIMENT.baseAlpha) ||
        (reviewOnlyEdgeRibbonExperiment && baseAlpha !== REVIEW_ONLY_EDGE_RIBBON_EXPERIMENT.baseAlpha) ||
        (reviewOnlyContinuousFieldExperiment && baseAlpha !== REVIEW_ONLY_CONTINUOUS_FIELD_EXPERIMENT.baseAlpha) ||
        (reviewOnlySpatialFieldExperiment && baseAlpha !== REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT.baseAlpha)) {
      throw new Error(reviewOnlyAlphaExperiment
        ? "Review-only Alpha experiment accepts exactly base Alpha 0.10."
        : reviewOnlyRadialFieldExperiment
          ? "Review-only radial geometry requires P1 base Alpha 0.07."
          : reviewOnlyEdgeRibbonExperiment
            ? "Review-only edge-ribbon geometry requires P1 base Alpha 0.07."
            : reviewOnlyContinuousFieldExperiment
              ? "Review-only continuous field requires P1 base Alpha 0.07."
            : reviewOnlySpatialFieldExperiment
              ? "Review-only spatial field requires P1 base Alpha 0.07."
            : "P1 Receiving Light base Alpha must remain in [0.03, 0.07].");
    }
    const requestedDepth = Number(options.depthRatio ?? 0.14);
    if (!Number.isFinite(requestedDepth) || requestedDepth < P1_DEPTH_RANGE[0] || requestedDepth > P1_DEPTH_RANGE[1] ||
        (reviewOnlyRadialFieldExperiment && requestedDepth !== REVIEW_ONLY_RADIAL_FIELD_EXPERIMENT.depthRatio) ||
        (reviewOnlyEdgeRibbonExperiment && requestedDepth !== REVIEW_ONLY_EDGE_RIBBON_EXPERIMENT.depthRatio) ||
        (reviewOnlyContinuousFieldExperiment && requestedDepth !== REVIEW_ONLY_CONTINUOUS_FIELD_EXPERIMENT.depthRatio) ||
        (reviewOnlySpatialFieldExperiment && requestedDepth !== REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT.depthRatio)) {
      throw new Error("P1 Receiving Light edge depth must remain in [0.08, 0.18]; review field experiments require exactly 0.18.");
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
      reviewOnlyAlphaExperiment, reviewOnlyRadialFieldExperiment, reviewOnlyEdgeRibbonExperiment,
      reviewOnlyContinuousFieldExperiment, reviewOnlySpatialFieldExperiment, quietGapVariant, outputAlphaCap,
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

  const wrapUnit = value => ((value % 1) + 1) % 1;

  function perimeterCoordinate(side, t, photoWidth, photoHeight) {
    const perimeter = 2 * (photoWidth + photoHeight);
    const along = clamp(t, 0, 1);
    if (side === "top") return photoWidth * along / perimeter;
    if (side === "right") return (photoWidth + photoHeight * along) / perimeter;
    if (side === "bottom") return (photoWidth + photoHeight + photoWidth * (1 - along)) / perimeter;
    return (2 * photoWidth + photoHeight + photoHeight * (1 - along)) / perimeter;
  }

  function perimeterProjection(point, rect) {
    const photoWidth = rect.right - rect.left;
    const photoHeight = rect.bottom - rect.top;
    const x = clamp((point[0] - rect.left) / photoWidth, 0, 1);
    const y = clamp((point[1] - rect.top) / photoHeight, 0, 1);
    const edges = [
      { side: "top", distance: Math.abs(point[1] - rect.top), t: x },
      { side: "right", distance: Math.abs(point[0] - rect.right), t: y },
      { side: "bottom", distance: Math.abs(point[1] - rect.bottom), t: x },
      { side: "left", distance: Math.abs(point[0] - rect.left), t: y }
    ];
    edges.sort((a, b) => a.distance - b.distance);
    const nearest = edges[0];
    return {
      side: nearest.side,
      t: nearest.t,
      s: perimeterCoordinate(nearest.side, nearest.t, photoWidth, photoHeight)
    };
  }

  function cubicPoint(path, t) {
    const u = 1 - t;
    const a = u * u * u;
    const b = 3 * u * u * t;
    const c = 3 * u * t * t;
    const d = t * t * t;
    return [
      a * path.start[0] + b * path.control1[0] + c * path.control2[0] + d * path.end[0],
      a * path.start[1] + b * path.control1[1] + c * path.control2[1] + d * path.end[1]
    ];
  }

  function deriveFieldAnchors(config) {
    const rect = config.rect;
    const photoWidth = rect.right - rect.left;
    const photoHeight = rect.bottom - rect.top;
    const perimeter = 2 * (photoWidth + photoHeight);
    return config.paths.map(path => {
      const sampleCount = 256;
      const samples = [];
      const cumulative = [0];
      for (let index = 0; index <= sampleCount; index++) {
        const t = index / sampleCount;
        const normalized = cubicPoint(path, t);
        const point = mapInset([
          rect.left + normalized[0] * photoWidth,
          rect.top + normalized[1] * photoHeight
        ], rect);
        samples.push(point);
        if (index > 0) {
          const previous = samples[index - 1];
          cumulative.push(cumulative[index - 1] + Math.hypot(point[0] - previous[0], point[1] - previous[1]));
        }
      }
      const length = cumulative[cumulative.length - 1];
      if (!(length > 1)) throw new Error("Continuous-field anchor path has no measurable length: " + path.id);
      const coverage = COVERAGE_BY_PATH[path.id] ?? path.visibleRatio;
      const visibleLength = length * clamp(coverage, 0, 1);
      const start = wrapUnit(path.seed) * length;
      const pointAtDistance = distance => {
        const target = ((distance % length) + length) % length;
        let low = 0, high = cumulative.length - 1;
        while (low + 1 < high) {
          const middle = (low + high) >> 1;
          if (cumulative[middle] < target) low = middle;
          else high = middle;
        }
        const span = cumulative[high] - cumulative[low];
        const ratio = span > 0 ? (target - cumulative[low]) / span : 0;
        return [
          samples[low][0] + (samples[high][0] - samples[low][0]) * ratio,
          samples[low][1] + (samples[high][1] - samples[low][1]) * ratio
        ];
      };
      const projected = [];
      const visibleSamples = 64;
      for (let index = 0; index <= visibleSamples; index++) {
        const point = pointAtDistance(start + visibleLength * index / visibleSamples);
        projected.push(perimeterProjection(point, rect).s);
      }
      let sine = 0, cosine = 0;
      projected.forEach(s => {
        const angle = s * Math.PI * 2;
        sine += Math.sin(angle);
        cosine += Math.cos(angle);
      });
      const center = wrapUnit(Math.atan2(sine, cosine) / (Math.PI * 2));
      const deviations = projected.map(s => ((s - center + 1.5) % 1) - 0.5);
      const spread = Math.max(...deviations.map(Math.abs));
      const sigma = clamp(
        spread * CONTINUOUS_FIELD_PROFILE.pathSpreadScale,
        CONTINUOUS_FIELD_PROFILE.minimumPerimeterSigma,
        CONTINUOUS_FIELD_PROFILE.maximumPerimeterSigma
      );
      return {
        id: path.id,
        family: path.family,
        center,
        sigma,
        projectedSpan: spread * 2,
        amplitude: Math.max(MIN_PATH_WEIGHT, path.weight) * clamp(coverage, 0, 1),
        coverage,
        weight: path.weight,
        seed: path.seed
      };
    });
  }

  function circularDistance(a, b) {
    const distance = Math.abs(wrapUnit(a) - wrapUnit(b));
    return Math.min(distance, 1 - distance);
  }

  function smoothstep(value) {
    const x = clamp(value, 0, 1);
    return x * x * (3 - 2 * x);
  }

  function quietGapEnergy(side, t, variant, energy) {
    const key = side === "top" ? "Q_TOP" : side === "right" ? "Q_RIGHT" : side === "bottom" ? "Q_BOTTOM" : "Q_LEFT";
    const [start, end] = QUIET_GAPS[key];
    const inside = t >= start && t <= end;
    if (variant === "A") return inside ? 0 : energy;
    const low = variant === "C" ? CONTINUOUS_FIELD_PROFILE.quietLowLevel : 0;
    const feather = CONTINUOUS_FIELD_PROFILE.quietFeatherRatio;
    if (inside) return low;
    if (t < start && t > start - feather) {
      const progress = smoothstep((t - (start - feather)) / feather);
      return energy + (low - energy) * progress;
    }
    if (t > end && t < end + feather) {
      const progress = smoothstep((t - end) / feather);
      return low + (energy - low) * progress;
    }
    return energy;
  }

  function hexRgb(hex) {
    const value = String(hex).replace(/^#/, "");
    return [0, 2, 4].map(index => parseInt(value.slice(index, index + 2), 16));
  }

  function mixRgb(a, b, amount) {
    const t = clamp(amount, 0, 1);
    return [0, 1, 2].map(channel => a[channel] + (b[channel] - a[channel]) * t);
  }

  function continuousFieldSideProfile(config, anchors, side, count) {
    const rect = config.rect;
    const photoWidth = rect.right - rect.left;
    const photoHeight = rect.bottom - rect.top;
    const sideLength = side === "top" || side === "bottom" ? photoWidth : photoHeight;
    const perimeter = 2 * (photoWidth + photoHeight);
    const baseColors = { blue: hexRgb(config.colors.blue), gold: hexRgb(config.colors.gold) };
    const hotColors = { blue: hexRgb(config.colors.blueHot), gold: hexRgb(config.colors.goldHot) };
    const profile = new Array(count);
    for (let index = 0; index < count; index++) {
      const t = clamp((index + 0.5) / sideLength, 0, 1);
      const s = perimeterCoordinate(side, t, photoWidth, photoHeight);
      let energy = CONTINUOUS_FIELD_PROFILE.ambientEnergy;
      let blueEnergy = 0, goldEnergy = 0;
      let blueColorEnergy = 0, goldColorEnergy = 0;
      for (const anchor of anchors) {
        const distance = circularDistance(s, anchor.center) * perimeter;
        const sigma = Math.max(1, anchor.sigma * perimeter);
        const contribution = anchor.amplitude * Math.exp(-0.5 * (distance / sigma) ** 2);
        energy += contribution;
        const colorSigma = Math.max(sigma, perimeter * 0.12);
        const colorContribution = anchor.amplitude * Math.exp(-0.5 * (distance / colorSigma) ** 2);
        if (anchor.family === "blue") {
          blueEnergy += contribution;
          blueColorEnergy += colorContribution;
        } else {
          goldEnergy += contribution;
          goldColorEnergy += colorContribution;
        }
      }
      const totalFamily = blueColorEnergy + goldColorEnergy;
      const blueShare = totalFamily > 1e-8 ? blueColorEnergy / totalFamily : 0.5;
      const goldShare = 1 - blueShare;
      const base = [0, 1, 2].map(channel =>
        baseColors.blue[channel] * blueShare + baseColors.gold[channel] * goldShare
      );
      const hot = [0, 1, 2].map(channel =>
        hotColors.blue[channel] * blueShare + hotColors.gold[channel] * goldShare
      );
      const maxAmplitude = Math.max(0.01, ...anchors.map(anchor => anchor.amplitude));
      const normalizedEnergy = clamp(energy, 0, 1);
      const ambient = CONTINUOUS_FIELD_PROFILE.ambientEnergy;
      const responseInput = clamp((normalizedEnergy - ambient) / (1 - ambient), 0, 1);
      const shapedEnergy = ambient + (1 - ambient) *
        Math.pow(responseInput, CONTINUOUS_FIELD_PROFILE.energyResponseExponent);
      const localEnergy = quietGapEnergy(side, t, config.quietGapVariant, shapedEnergy);
      const familyEnergy = clamp((blueEnergy + goldEnergy) / maxAmplitude, 0, 1);
      profile[index] = {
        energy: localEnergy,
        blueShare,
        hotMix: 0.08 + 0.24 * familyEnergy,
        baseColor: base,
        hotColor: hot
      };
    }
    return profile;
  }

  function buildContinuousFieldCanvas(config) {
    const width = config.width, height = config.height, rect = config.rect;
    const photoWidth = rect.right - rect.left, photoHeight = rect.bottom - rect.top;
    const pixelCount = width * height;
    if (pixelCount > CONTINUOUS_FIELD_PROFILE.maximumCachedFieldPixels) {
      throw new Error("Review-only continuous field exceeds the 4,194,304-pixel cache budget.");
    }
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    canvas.dataset.receivingLightGeometry = "PERIMETER_ANCHORED_CONTINUOUS_FIELD";
    canvas.dataset.edgeglowReceivingLightSource = "continuous-field";
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) throw new Error("Continuous field could not allocate its offscreen Canvas.");
    const anchors = deriveFieldAnchors(config);
    const sides = {
      top: continuousFieldSideProfile(config, anchors, "top", Math.ceil(photoWidth)),
      right: continuousFieldSideProfile(config, anchors, "right", Math.ceil(photoHeight)),
      bottom: continuousFieldSideProfile(config, anchors, "bottom", Math.ceil(photoWidth)),
      left: continuousFieldSideProfile(config, anchors, "left", Math.ceil(photoHeight))
    };
    const depthPx = config.depthRatio * Math.min(photoWidth, photoHeight);
    const maxDepth = Math.max(1, Math.ceil(depthPx));
    const depthStride = maxDepth + 1;
    const attenuationTable = new Float32Array(CONTINUOUS_FIELD_PROFILE.energyBins * depthStride);
    for (let energyBin = 0; energyBin < CONTINUOUS_FIELD_PROFILE.energyBins; energyBin++) {
      const energy = energyBin / (CONTINUOUS_FIELD_PROFILE.energyBins - 1);
      const depthScale = CONTINUOUS_FIELD_PROFILE.localDepthScaleMinimum +
        (1 - CONTINUOUS_FIELD_PROFILE.localDepthScaleMinimum) * energy;
      const outerSigma = Math.max(1, depthPx * CONTINUOUS_FIELD_PROFILE.outerSigmaDepth * depthScale);
      const midSigma = Math.max(1, depthPx * CONTINUOUS_FIELD_PROFILE.midSigmaDepth * depthScale);
      const coreSigma = Math.max(1, depthPx * CONTINUOUS_FIELD_PROFILE.coreSigmaDepth * depthScale);
      for (let distance = 0; distance <= maxDepth; distance++) {
        attenuationTable[energyBin * depthStride + distance] =
          CONTINUOUS_FIELD_PROFILE.outerShare * Math.exp(-0.5 * (distance / outerSigma) ** 2) +
          CONTINUOUS_FIELD_PROFILE.midShare * Math.exp(-0.5 * (distance / midSigma) ** 2) +
          CONTINUOUS_FIELD_PROFILE.coreShare * Math.exp(-0.5 * (distance / coreSigma) ** 2);
      }
    }
    const imageData = context.createImageData(width, height);
    const output = imageData.data;
    const cap = Math.floor(P1_ALPHA_RANGE[1] * 255);
    const alphaScale = clamp(config.globalMultiplier * config.baseAlpha / P1_ALPHA_RANGE[1], 0, 1);
    const xStart = Math.max(0, Math.floor(rect.left));
    const xEnd = Math.min(width, Math.ceil(rect.right));
    const yStart = Math.max(0, Math.floor(rect.top));
    const yEnd = Math.min(height, Math.ceil(rect.bottom));
    for (let y = yStart; y < yEnd; y++) {
      const py = y + 0.5;
      if (py < rect.top || py >= rect.bottom) continue;
      const localY = Math.floor(py - rect.top);
      const topDistance = py - rect.top;
      const bottomDistance = rect.bottom - py;
      for (let x = xStart; x < xEnd; x++) {
        const px = x + 0.5;
        if (px < rect.left || px >= rect.right) continue;
        const localX = Math.floor(px - rect.left);
        const leftDistance = px - rect.left;
        const rightDistance = rect.right - px;
        let side, distance, localIndex;
        if (topDistance <= rightDistance && topDistance <= bottomDistance && topDistance <= leftDistance) {
          side = "top"; distance = topDistance; localIndex = localX;
        } else if (rightDistance <= bottomDistance && rightDistance <= leftDistance) {
          side = "right"; distance = rightDistance; localIndex = localY;
        } else if (bottomDistance <= leftDistance) {
          side = "bottom"; distance = bottomDistance; localIndex = localX;
        } else {
          side = "left"; distance = leftDistance; localIndex = localY;
        }
        if (distance > depthPx) continue;
        const sideProfile = sides[side];
        const profile = sideProfile[clamp(localIndex, 0, sideProfile.length - 1)];
        if (!profile || profile.energy <= 0) continue;
        const energyBin = Math.round(profile.energy * (CONTINUOUS_FIELD_PROFILE.energyBins - 1));
        const depth = clamp(distance, 0, maxDepth);
        const depthIndex = Math.floor(depth);
        const depthMix = depth - depthIndex;
        const rowOffset = energyBin * depthStride;
        const attenuation = attenuationTable[rowOffset + depthIndex] * (1 - depthMix) +
          attenuationTable[rowOffset + Math.min(maxDepth, depthIndex + 1)] * depthMix;
        const alpha = Math.min(cap, Math.round(cap * alphaScale * profile.energy * attenuation));
        if (alpha <= 0) continue;
        const color = mixRgb(profile.baseColor, profile.hotColor, profile.hotMix);
        const offset = (y * width + x) * 4;
        output[offset] = Math.round(color[0]);
        output[offset + 1] = Math.round(color[1]);
        output[offset + 2] = Math.round(color[2]);
        output[offset + 3] = alpha;
      }
    }
    context.putImageData(imageData, 0, 0);
    canvas.dataset.continuousFieldAnchors = JSON.stringify(anchors);
    canvas.dataset.continuousFieldQuietVariant = config.quietGapVariant;
    return { canvas, anchors, depthPx, profile: CONTINUOUS_FIELD_PROFILE };
  }

  function quietMaskMarkup(rect, width, height, maxDepth, antiAliasGuardPx = 0) {
    const photoWidth = rect.right - rect.left;
    const photoHeight = rect.bottom - rect.top;
    const gaps = QUIET_GAPS;
    const regions = [
      [rect.left + gaps.Q_TOP[0] * photoWidth, rect.top, (gaps.Q_TOP[1] - gaps.Q_TOP[0]) * photoWidth, maxDepth],
      [rect.left, rect.top + gaps.Q_LEFT[0] * photoHeight, maxDepth, (gaps.Q_LEFT[1] - gaps.Q_LEFT[0]) * photoHeight],
      [rect.right - maxDepth, rect.top + gaps.Q_RIGHT[0] * photoHeight, maxDepth, (gaps.Q_RIGHT[1] - gaps.Q_RIGHT[0]) * photoHeight],
      [rect.left + gaps.Q_BOTTOM[0] * photoWidth, rect.bottom - maxDepth, (gaps.Q_BOTTOM[1] - gaps.Q_BOTTOM[0]) * photoWidth, maxDepth]
    ].map(([x, y, regionWidth, regionHeight]) => {
      const guard = Math.max(0, Number(antiAliasGuardPx) || 0);
      const left = Math.max(rect.left, x - guard);
      const top = Math.max(rect.top, y - guard);
      const right = Math.min(rect.right, x + regionWidth + guard);
      const bottom = Math.min(rect.bottom, y + regionHeight + guard);
      return [left, top, right - left, bottom - top];
    });
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

  function edgeRibbonGradientMarkup(id, x1, y1, x2, y2, baseColor, hotColor) {
    const stops = [
      [0.00, baseColor, 0.90],
      [0.08, hotColor, 0.95],
      [0.30, baseColor, 0.92],
      [0.58, baseColor, 0.78],
      [0.84, baseColor, 0.42],
      [1.00, baseColor, 0.00]
    ].map(stop =>
      '<stop offset="' + number(stop[0] * 100) + '%" stop-color="' + stop[1] +
      '" stop-opacity="' + number(stop[2]) + '"/>'
    ).join("");
    return '<linearGradient id="' + id +
      '" gradientUnits="userSpaceOnUse" x1="' + number(x1) + '" y1="' + number(y1) +
      '" x2="' + number(x2) + '" y2="' + number(y2) + '">' + stops + '</linearGradient>';
  }

  function edgeBoundaryProjection(point, rect) {
    const x = clamp(point[0], rect.left, rect.right);
    const y = clamp(point[1], rect.top, rect.bottom);
    const edges = [
      { side: "top", distance: y - rect.top, boundary: [x, rect.top], normal: [0, 1] },
      { side: "right", distance: rect.right - x, boundary: [rect.right, y], normal: [-1, 0] },
      { side: "bottom", distance: rect.bottom - y, boundary: [x, rect.bottom], normal: [0, -1] },
      { side: "left", distance: x - rect.left, boundary: [rect.left, y], normal: [1, 0] }
    ];
    edges.sort((a, b) => a.distance - b.distance);
    return edges[0];
  }

  function cornerBetweenEdges(sideA, sideB, rect) {
    const key = [sideA, sideB].sort().join("|");
    const corners = {
      "bottom|left": [rect.left, rect.bottom],
      "bottom|right": [rect.right, rect.bottom],
      "left|top": [rect.left, rect.top],
      "right|top": [rect.right, rect.top]
    };
    return corners[key] || null;
  }

  let spatialFieldInstance = 0;

  function createReviewOnlySpatialField(svgRoot, options) {
    if (!svgRoot || svgRoot.namespaceURI !== SVG_NS || svgRoot.localName !== "svg") {
      throw new Error("Review-only spatial Receiving Light requires the shared photo SVG.");
    }
    const config = normalizeOptions({ ...options, reviewOnlySpatialFieldExperiment: true });
    if (config.quietGapVariant !== "A") {
      throw new Error("Review-only spatial field requires strict Quiet Gap variant A.");
    }
    const width = config.width, height = config.height, rect = config.rect;
    const photoWidth = rect.right - rect.left, photoHeight = rect.bottom - rect.top;
    const depthPx = config.depthRatio * Math.min(photoWidth, photoHeight);
    // Keep the vector clip one raster pixel inside the contractual maximum.
    // Chromium antialiases a fractional SVG clip edge; this avoids a fringe
    // crossing 18% while leaving the P1 depth token and generated geometry intact.
    const clippedDepthPx = Math.max(0.5, depthPx - 1);
    const outerSigma = Math.max(8, depthPx * 0.18);
    const midSigma = Math.max(4, depthPx * 0.075);
    const padding = Math.ceil(outerSigma * 3 + 4);
    const prefix = "review-spatial-field-" + (++spatialFieldInstance);
    const numberString = value => String(number(value));
    const alphaTable = Array.from({ length: 256 }, (_, index) =>
      numberString(Math.min(index / 255, config.outputAlphaCap))
    ).join(" ");
    const depthClipId = prefix + "-depth-clip";
    const quietMaskId = prefix + "-quiet-mask";
    const alphaCapId = prefix + "-alpha-cap";
    const outerBlurId = prefix + "-outer-blur";
    const midBlurId = prefix + "-mid-blur";
    const defs = [
      '<clipPath id="' + depthClipId + '" clipPathUnits="userSpaceOnUse">' +
        '<rect x="' + numberString(rect.left) + '" y="' + numberString(rect.top) + '" width="' + numberString(photoWidth) + '" height="' + numberString(clippedDepthPx) + '"/>' +
        '<rect x="' + numberString(rect.left) + '" y="' + numberString(rect.bottom - clippedDepthPx) + '" width="' + numberString(photoWidth) + '" height="' + numberString(clippedDepthPx) + '"/>' +
        '<rect x="' + numberString(rect.left) + '" y="' + numberString(rect.top) + '" width="' + numberString(clippedDepthPx) + '" height="' + numberString(photoHeight) + '"/>' +
        '<rect x="' + numberString(rect.right - clippedDepthPx) + '" y="' + numberString(rect.top) + '" width="' + numberString(clippedDepthPx) + '" height="' + numberString(photoHeight) + '"/>' +
      '</clipPath>',
      // The one-pixel guard only removes SVG mask antialias fringe; it expands
      // the quiet area and cannot reduce the frozen Quiet Gap or Depth contract.
      quietMaskMarkup(rect, width, height, depthPx, 1).replace(/quiet-gaps/g, quietMaskId),
      '<filter id="' + outerBlurId + '" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" x="' +
        numberString(rect.left - padding) + '" y="' + numberString(rect.top - padding) + '" width="' +
        numberString(photoWidth + padding * 2) + '" height="' + numberString(photoHeight + padding * 2) +
        '" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceGraphic" stdDeviation="' +
        numberString(outerSigma) + '"/></filter>',
      '<filter id="' + midBlurId + '" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" x="' +
        numberString(rect.left - padding) + '" y="' + numberString(rect.top - padding) + '" width="' +
        numberString(photoWidth + padding * 2) + '" height="' + numberString(photoHeight + padding * 2) +
        '" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceGraphic" stdDeviation="' +
        numberString(midSigma) + '"/></filter>',
      '<filter id="' + alphaCapId + '" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" x="' +
        numberString(rect.left) + '" y="' + numberString(rect.top) + '" width="' + numberString(photoWidth) +
        '" height="' + numberString(photoHeight) + '"><feComponentTransfer><feFuncA type="table" tableValues="' +
        alphaTable + '"/></feComponentTransfer></filter>'
    ];
    const sideNormal = {
      top: [0, 1], right: [-1, 0], bottom: [0, -1], left: [1, 0]
    };
    const anchorPoint = (side, t) => {
      if (side === "top") return [rect.left + t * photoWidth, rect.top];
      if (side === "right") return [rect.right, rect.top + t * photoHeight];
      if (side === "bottom") return [rect.left + t * photoWidth, rect.bottom];
      return [rect.left, rect.top + t * photoHeight];
    };
    const makeSample = (boundary, side, depth, energy, u) => {
      const normal = sideNormal[side];
      return {
        side,
        boundary,
        inner: [
          clamp(boundary[0] + normal[0] * depth, rect.left, rect.right),
          clamp(boundary[1] + normal[1] * depth, rect.top, rect.bottom)
        ],
        depth,
        energy,
        u
      };
    };
    const polygonPath = samples => {
      const points = [...samples.map(sample => sample.boundary), ...samples.slice().reverse().map(sample => sample.inner)];
      if (points.length < 4) return "";
      return "M " + points.map((point, index) => (index ? "L " : "") +
        numberString(point[0]) + " " + numberString(point[1])).join(" ") + " Z";
    };
    const gradientMarkup = (id, side, color, hotColor, opacityStops) => {
      const normal = sideNormal[side];
      const start = anchorPoint(side, 0);
      const end = [start[0] + normal[0] * depthPx, start[1] + normal[1] * depthPx];
      const stops = opacityStops.map(stop =>
        '<stop offset="' + numberString(stop[0] * 100) + '%" stop-color="' +
        (stop[1] ? hotColor : color) + '" stop-opacity="' + numberString(stop[2]) + '"/>'
      ).join("");
      return '<linearGradient id="' + id + '" gradientUnits="userSpaceOnUse" x1="' +
        numberString(start[0]) + '" y1="' + numberString(start[1]) + '" x2="' +
        numberString(end[0]) + '" y2="' + numberString(end[1]) + '">' + stops + '</linearGradient>';
    };
    const measureSvg = document.createElementNS(SVG_NS, "svg");
    measureSvg.setAttribute("width", String(width));
    measureSvg.setAttribute("height", String(height));
    measureSvg.setAttribute("viewBox", "0 0 " + width + " " + height);
    measureSvg.setAttribute("aria-hidden", "true");
    measureSvg.style.cssText = "position:fixed;left:-100000px;top:-100000px;visibility:hidden;pointer-events:none;overflow:visible";
    document.body.appendChild(measureSvg);
    const islandMarkup = [];
    const diagnostics = [];
    try {
      for (const path of config.paths) {
        const pathNode = document.createElementNS(SVG_NS, "path");
        pathNode.setAttribute("d", pathData(path, rect));
        measureSvg.appendChild(pathNode);
        const pathLength = pathNode.getTotalLength();
        if (!(pathLength > 1)) throw new Error("Spatial-field source path is empty: " + path.id);
        const sampleCount = Math.max(48, Math.ceil(pathLength / 7));
        const samples = [];
        for (let index = 0; index <= sampleCount; index++) {
          const u = index / sampleCount;
          const source = pathNode.getPointAtLength(pathLength * u);
          const mapped = mapInset([source.x, source.y], rect);
          const edge = edgeBoundaryProjection(mapped, rect);
          const endpointFade = smoothstep(Math.min(1, Math.min(u, 1 - u) / 0.14));
          const phase = Number(path.seed) * Math.PI * 2;
          const depthWave = 0.5 + 0.5 * Math.sin(Math.PI * 2 * (u * 1.15 + Number(path.seed)));
          const depthDetail = 0.5 + 0.5 * Math.sin(Math.PI * 2 * (u * 2.35 + Number(path.seed) * 0.73));
          const energy = clamp((0.80 + 0.13 * Math.sin(Math.PI * 2 * (u * 1.35 + Number(path.seed)) + phase) +
            0.07 * Math.sin(Math.PI * 2 * (u * 2.1 + Number(path.seed) * 0.61))) * endpointFade, 0, 1);
          const depthScale = 0.54 + 0.27 * depthWave + 0.13 * depthDetail;
          samples.push(makeSample(edge.boundary, edge.side, depthPx * depthScale * endpointFade, energy, u));
        }
        pathNode.remove();

        const sideSegments = [];
        let current = [samples[0]];
        for (let index = 1; index < samples.length; index++) {
          const previous = samples[index - 1];
          const next = samples[index];
          if (previous.side === next.side) {
            current.push(next);
            continue;
          }
          const corner = cornerBetweenEdges(previous.side, next.side, rect);
          if (corner) {
            const depth = Math.min(depthPx * 0.90, Math.max(previous.depth, next.depth));
            const energy = (previous.energy + next.energy) / 2;
            current.push(makeSample(corner, previous.side, depth, energy, (previous.u + next.u) / 2));
            sideSegments.push({ side: previous.side, samples: current });
            current = [makeSample(corner, next.side, depth, energy, (previous.u + next.u) / 2), next];
          } else {
            sideSegments.push({ side: previous.side, samples: current });
            current = [next];
          }
        }
        sideSegments.push({ side: current[0]?.side || samples[0].side, samples: current });

        const family = path.family === "gold" ? "gold" : "blue";
        const baseColor = config.colors[family];
        const hotColor = config.colors[family === "gold" ? "goldHot" : "blueHot"];
        const islandWeight = Math.max(MIN_PATH_WEIGHT, path.weight);
        const baseAlpha = config.baseAlpha * islandWeight;
        const segmentMarkup = [];
        sideSegments.forEach((segment, segmentIndex) => {
          if (segment.samples.length < 3) return;
          const d = polygonPath(segment.samples);
          if (!d) return;
          const side = segment.side;
          const first = segment.samples[0].boundary;
          const last = segment.samples[segment.samples.length - 1].boundary;
          const tangentDx = last[0] - first[0];
          const tangentDy = last[1] - first[1];
          const meanDepth = segment.samples.reduce((sum, sample) => sum + sample.depth, 0) / segment.samples.length;
          const normalId = prefix + "-" + path.id + "-" + segmentIndex + "-normal";
          const alongId = prefix + "-" + path.id + "-" + segmentIndex + "-along";
          const maskId = prefix + "-" + path.id + "-" + segmentIndex + "-mask";
          defs.push(gradientMarkup(normalId, side, baseColor, hotColor, [
            [0, 0, 0.98], [0.07, 1, 0.86], [0.22, 0, 0.66], [0.48, 0, 0.34], [0.76, 0, 0.11], [1, 0, 0]
          ]));
          const alongStops = segment.samples.map((sample, index) => {
            const offset = segment.samples.length > 1 ? index / (segment.samples.length - 1) : 0;
            return '<stop offset="' + numberString(offset * 100) + '%" stop-color="#fff" stop-opacity="' +
              numberString(sample.energy) + '"/>';
          }).join("");
          defs.push('<linearGradient id="' + alongId + '" gradientUnits="userSpaceOnUse" x1="' +
            numberString(first[0]) + '" y1="' + numberString(first[1]) + '" x2="' +
            numberString(first[0] + tangentDx) + '" y2="' + numberString(first[1] + tangentDy) + '">' +
            alongStops + '</linearGradient>');
          defs.push('<mask id="' + maskId + '" maskUnits="userSpaceOnUse" maskContentUnits="userSpaceOnUse" mask-type="alpha" x="' +
            numberString(rect.left - padding) + '" y="' + numberString(rect.top - padding) + '" width="' +
            numberString(photoWidth + padding * 2) + '" height="' + numberString(photoHeight + padding * 2) +
            '"><path d="' + d + '" fill="url(#' + alongId + ')"/></mask>');
          const normalX = sideNormal[side][0] * depthPx;
          const normalY = sideNormal[side][1] * depthPx;
          const normalIdLocal = prefix + "-" + path.id + "-" + segmentIndex + "-normal-local";
          const localStops = [
            [0, 0, 0.98], [0.07, 1, 0.86], [0.22, 0, 0.66], [0.48, 0, 0.34], [0.76, 0, 0.11], [1, 0, 0]
          ].map(stop => '<stop offset="' + numberString(stop[0] * 100) + '%" stop-color="' +
            (stop[1] ? hotColor : baseColor) + '" stop-opacity="' + numberString(stop[2]) + '"/>').join("");
          defs.push('<linearGradient id="' + normalIdLocal + '" gradientUnits="userSpaceOnUse" x1="' +
            numberString(first[0]) + '" y1="' + numberString(first[1]) + '" x2="' +
            numberString(first[0] + normalX) + '" y2="' + numberString(first[1] + normalY) + '">' + localStops + '</linearGradient>');
          const layerOpacity = baseAlpha;
          segmentMarkup.push(
            '<g mask="url(#' + maskId + ')">' +
              '<path d="' + d + '" fill="url(#' + normalIdLocal + ')" filter="url(#' + outerBlurId + ')" opacity="' + numberString(layerOpacity * 0.38) + '"/>' +
              '<path d="' + d + '" fill="url(#' + normalIdLocal + ')" filter="url(#' + midBlurId + ')" opacity="' + numberString(layerOpacity * 0.42) + '"/>' +
              '<path d="' + d + '" fill="url(#' + normalIdLocal + ')" opacity="' + numberString(layerOpacity * 0.36) + '"/>' +
            '</g>'
          );
          if (!(meanDepth >= 0)) throw new Error("Spatial field depth became invalid.");
        });
        islandMarkup.push('<g id="' + prefix + '-island-' + path.id + '" class="review-spatial-island" data-path="' +
          path.id + '" data-family="' + family + '" data-weight="' + numberString(islandWeight) +
          '" data-base-alpha="' + numberString(baseAlpha) + '" opacity="0">' + segmentMarkup.join("") + '</g>');
        diagnostics.push({ id: path.id, family, weight: islandWeight, path_length: number(pathLength), sample_count: sampleCount, max_depth_px: number(depthPx) });
      }
    } finally {
      measureSvg.remove();
    }
    const markup = '<defs>' + defs.join("") + '</defs>' +
      '<g id="' + prefix + '-root" data-source="REVIEW_ONLY_SPATIAL_FIELD_V2" data-alpha-mode="P1_BASE_0.07_CAP_17_255" data-depth-ratio="0.18" data-quiet-gap-variant="A" ' +
      'filter="url(#' + alphaCapId + ')" mask="url(#' + quietMaskId + ')" clip-path="url(#' + depthClipId + ')">' +
      islandMarkup.join("") + '</g>';
    const parsed = new DOMParser().parseFromString('<svg xmlns="' + SVG_NS + '">' + markup + '</svg>', "image/svg+xml");
    if (parsed.querySelector("parsererror")) throw new Error("Review-only spatial field SVG markup is invalid.");
    const importedDefs = document.importNode(parsed.documentElement.querySelector("defs"), true);
    const importedRoot = document.importNode(parsed.documentElement.querySelector("g"), true);
    svgRoot.insertBefore(importedDefs, svgRoot.firstChild);
    svgRoot.insertBefore(importedRoot, importedDefs.nextSibling);
    const islands = config.paths.map(path => svgRoot.querySelector("#" + prefix + "-island-" + path.id));
    setStatus("ready", {
      alpha_mode: "REVIEW_ONLY_SPATIAL_FIELD_V2_P1_ALPHA",
      geometry_mode: "P1_PATH_PROJECTED_VARIABLE_DEPTH_EDGE_FIELD",
      field_version: REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT.version,
      base_alpha: config.baseAlpha,
      alpha_cap: config.outputAlphaCap,
      depth_ratio: config.depthRatio,
      depth_px: number(depthPx),
      quiet_gap_variant: config.quietGapVariant,
      paths: diagnostics
    });
    return Object.freeze({
      root: importedRoot,
      islands,
      version: REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT.version,
      diagnostics: Object.freeze(diagnostics),
      baseAlpha: config.baseAlpha,
      alphaCap: config.outputAlphaCap,
      depthRatio: config.depthRatio
    });
  }

  function radialFieldGradientMarkup(id, baseColor, hotColor) {
    const stops = [
      [0.00, hotColor, 0.88],
      [0.22, hotColor, 0.82],
      [0.48, baseColor, 0.62],
      [0.72, baseColor, 0.32],
      [0.90, baseColor, 0.08],
      [1.00, baseColor, 0.00]
    ].map(stop =>
      '<stop offset="' + number(stop[0] * 100) + '%" stop-color="' + stop[1] +
      '" stop-opacity="' + number(stop[2]) + '"/>'
    ).join("");
    return '<radialGradient id="' + id +
      '" gradientUnits="objectBoundingBox" cx="50%" cy="50%" r="50%">' + stops + '</radialGradient>';
  }

  function buildSvg(config) {
    const width = config.width, height = config.height, rect = config.rect;
    const photoWidth = rect.right - rect.left, photoHeight = rect.bottom - rect.top;
    const minDimension = Math.min(photoWidth, photoHeight);
    const depthPx = config.depthRatio * minDimension;
    const outerSigma = depthPx * LAYERS[0].blurRatio;
    const midSigma = depthPx * LAYERS[1].blurRatio;
    const coreSigma = LAYERS[2].blurRatio * config.dpToCanvasPx;
    const ribbonBlurSigma = Math.max(2, minDimension * 0.0065);
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
    const postCapSigma = config.reviewOnlyEdgeRibbonExperiment ? ribbonBlurSigma * 0.55 : 0;
    const postCapBlur = config.reviewOnlyEdgeRibbonExperiment
      ? '<feGaussianBlur in="capped" stdDeviation="' + number(postCapSigma) + '"/>'
      : '';
    const alphaCap = '<filter id="alpha-cap" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse" ' +
      'x="0" y="0" width="' + width + '" height="' + height + '">' +
      '<feComponentTransfer result="capped"><feFuncA type="table" tableValues="' + alphaTable + '"/></feComponentTransfer>' +
      postCapBlur + '</filter>';
    const quietMaskDepth = config.reviewOnlyEdgeRibbonExperiment
      ? depthPx + (ribbonBlurSigma + postCapSigma) * 3
      : maxDepth;
    const defs = [photoClip, quietMaskMarkup(rect, width, height, quietMaskDepth), filters, alphaCap].join("");
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
        const islandWeight = Math.max(MIN_PATH_WEIGHT, path.weight);
        const baseAlpha = config.baseAlpha * config.globalMultiplier * islandWeight;
        if (config.reviewOnlyEdgeRibbonExperiment) {
          // Review-only perimeter distribution: construct edge bands from photoContentRect.
          // This intentionally does not reuse the four corner-island paths.
          if (path.id === "B1") {
            const ribbonDepth = depthPx;
            const taperFraction = 0.12;
            const segments = [
              { side: "top", start: 0, end: QUIET_GAPS.Q_TOP[0], color: "gold" },
              { side: "top", start: QUIET_GAPS.Q_TOP[1], end: 1, color: "blue" },
              { side: "right", start: 0, end: QUIET_GAPS.Q_RIGHT[0], color: "blue" },
              { side: "right", start: QUIET_GAPS.Q_RIGHT[1], end: 1, color: "blue" },
              { side: "bottom", start: 0, end: QUIET_GAPS.Q_BOTTOM[0], color: "gold" },
              { side: "bottom", start: QUIET_GAPS.Q_BOTTOM[1], end: 1, color: "blue" },
              { side: "left", start: 0, end: QUIET_GAPS.Q_LEFT[0], color: "gold" },
              { side: "left", start: QUIET_GAPS.Q_LEFT[1], end: 1, color: "gold" }
            ];
            const edgePoint = (side, t, depth) => {
              if (side === "top") return [rect.left + t * photoWidth, rect.top + depth];
              if (side === "right") return [rect.right - depth, rect.top + t * photoHeight];
              if (side === "bottom") return [rect.left + t * photoWidth, rect.bottom - depth];
              return [rect.left + depth, rect.top + t * photoHeight];
            };
            for (const segment of segments) {
              const spanPx = (segment.end - segment.start) * (segment.side === "top" || segment.side === "bottom" ? photoWidth : photoHeight);
              if (spanPx < 2) continue;
              const taperT = Math.min(spanPx * taperFraction, ribbonDepth * 0.75) / spanPx;
              const points = [
                edgePoint(segment.side, segment.start, 0),
                edgePoint(segment.side, segment.end, 0),
                edgePoint(segment.side, segment.end - taperT, ribbonDepth),
                edgePoint(segment.side, segment.start + taperT, ribbonDepth)
              ];
              const pathD = "M " + points.map((point, index) =>
                (index ? "L " : "") + number(point[0]) + " " + number(point[1])
              ).join(" ") + " Z";
              const bounds = points.reduce((value, point) => ({
                left: Math.min(value.left, point[0]), top: Math.min(value.top, point[1]),
                right: Math.max(value.right, point[0]), bottom: Math.max(value.bottom, point[1])
              }), { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity });
              const id = safeId("perimeter-" + segment.side + "-" + segment.start);
              const clipId = "perimeter-clip-" + id;
              const blurId = "perimeter-blur-" + id;
              const gradientId = "perimeter-gradient-" + id;
              const filterPadding = ribbonBlurSigma * 3;
              defsMarkup.push('<clipPath id="' + clipId + '" clipPathUnits="userSpaceOnUse"><path d="' + pathD + '"/></clipPath>');
              defsMarkup.push(
                '<filter id="' + blurId + '" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse"' +
                ' x="' + number(bounds.left - filterPadding) + '" y="' + number(bounds.top - filterPadding) +
                '" width="' + number(bounds.right - bounds.left + filterPadding * 2) +
                '" height="' + number(bounds.bottom - bounds.top + filterPadding * 2) +
                '" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceGraphic" stdDeviation="' +
                number(ribbonBlurSigma) + '"/></filter>'
              );
              const gradient = segment.side === "top"
                ? [rect.left, rect.top, rect.left, rect.top + ribbonDepth]
                : segment.side === "right"
                  ? [rect.right, rect.top, rect.right - ribbonDepth, rect.top]
                  : segment.side === "bottom"
                    ? [rect.left, rect.bottom, rect.left, rect.bottom - ribbonDepth]
                    : [rect.left, rect.top, rect.left + ribbonDepth, rect.top];
              const baseColor = segment.color === "gold" ? config.colors.gold : config.colors.blue;
              const hotColor = segment.color === "gold" ? config.colors.goldHot : config.colors.blueHot;
              defsMarkup.push(edgeRibbonGradientMarkup(gradientId, ...gradient, baseColor, hotColor));
              layersMarkup.push(
                '<g filter="url(#' + blurId + ')"><rect x="' + number(bounds.left) + '" y="' + number(bounds.top) +
                '" width="' + number(bounds.right - bounds.left) + '" height="' + number(bounds.bottom - bounds.top) +
                '" fill="url(#' + gradientId + ')" clip-path="url(#' + clipId + ')" opacity="' +
                number(baseAlpha) + '"/> </g>'
              );
            }
          }
          continue;
        }
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
        const baseColor = path.family === "gold" ? config.colors.gold : config.colors.blue;
        const hotColor = path.family === "gold" ? config.colors.goldHot : config.colors.blueHot;

        // Review-only edge ribbon: one continuous polygon per visible path interval.
        // The P1 curve chooses the perimeter span; side-normal gradients fade inward.
        if (config.reviewOnlyEdgeRibbonExperiment) {
          const ribbonDepth = depthPx;
          const ribbonStep = Math.max(6, minDimension * 0.0125);
          intervals.forEach((interval, intervalIndex) => {
            const intervalLength = interval[1] - interval[0];
            if (!(intervalLength > 1)) return;
            const count = Math.max(2, Math.ceil(intervalLength / ribbonStep));
            const samples = [];
            for (let index = 0; index <= count; index++) {
              const distance = interval[0] + intervalLength * index / count;
              const sourcePoint = sourcePath.getPointAtLength(distance);
              const point = mapInset([sourcePoint.x, sourcePoint.y], rect);
              const edge = edgeBoundaryProjection(point, rect);
              const taper = Math.min(1, index / 3, (count - index) / 3);
              const depth = ribbonDepth * taper;
              const inner = [
                clamp(edge.boundary[0] + edge.normal[0] * depth, rect.left, rect.right),
                clamp(edge.boundary[1] + edge.normal[1] * depth, rect.top, rect.bottom)
              ];
              samples.push({ point, ...edge, inner, taper });
            }
            const outerRoute = [samples[0].boundary];
            const innerForward = [samples[0].inner];
            for (let index = 0; index < count; index++) {
              const a = samples[index], b = samples[index + 1];
              const corner = a.side === b.side ? null : cornerBetweenEdges(a.side, b.side, rect);
              if (corner) {
                outerRoute.push(corner);
                const cornerDepth = ribbonDepth * Math.min(a.taper, b.taper);
                const innerCorner = [
                  clamp(corner[0] + (corner[0] === rect.left ? cornerDepth : corner[0] === rect.right ? -cornerDepth : 0), rect.left, rect.right),
                  clamp(corner[1] + (corner[1] === rect.top ? cornerDepth : corner[1] === rect.bottom ? -cornerDepth : 0), rect.top, rect.bottom)
                ];
                innerForward.push(innerCorner);
              }
              outerRoute.push(b.boundary);
              innerForward.push(b.inner);
            }
            const polygonPoints = [...outerRoute, ...innerForward.reverse()];
            const pathD = "M " + polygonPoints.map((point, pointIndex) =>
              (pointIndex ? "L " : "") + number(point[0]) + " " + number(point[1])
            ).join(" ") + " Z";
            const ribbonBounds = polygonPoints.reduce((bounds, point) => ({
              left: Math.min(bounds.left, point[0]),
              top: Math.min(bounds.top, point[1]),
              right: Math.max(bounds.right, point[0]),
              bottom: Math.max(bounds.bottom, point[1])
            }), { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity });
            const clipId = "edge-ribbon-clip-" + safeId(path.id + "-" + intervalIndex);
            const blurFilterId = "edge-ribbon-blur-" + safeId(path.id + "-" + intervalIndex);
            const filterPadding = ribbonBlurSigma * 3;
            defsMarkup.push('<clipPath id="' + clipId + '" clipPathUnits="userSpaceOnUse"><path d="' + pathD + '"/></clipPath>');
            defsMarkup.push(
              '<filter id="' + blurFilterId + '" filterUnits="userSpaceOnUse" primitiveUnits="userSpaceOnUse"' +
              ' x="' + number(ribbonBounds.left - filterPadding) + '" y="' + number(ribbonBounds.top - filterPadding) +
              '" width="' + number(ribbonBounds.right - ribbonBounds.left + filterPadding * 2) +
              '" height="' + number(ribbonBounds.bottom - ribbonBounds.top + filterPadding * 2) +
              '" color-interpolation-filters="sRGB"><feGaussianBlur in="SourceGraphic" stdDeviation="' +
              number(ribbonBlurSigma) + '"/></filter>'
            );
            const activeSides = [...new Set(samples.map(sample => sample.side))];
            const gradients = {
              top: [rect.left, rect.top, rect.left, rect.top + ribbonDepth],
              right: [rect.right, rect.top, rect.right - ribbonDepth, rect.top],
              bottom: [rect.left, rect.bottom, rect.left, rect.bottom - ribbonDepth],
              left: [rect.left, rect.top, rect.left + ribbonDepth, rect.top]
            };
            for (const side of activeSides) {
              const gradientId = "edge-ribbon-gradient-" + safeId(path.id + "-" + intervalIndex + "-" + side);
              defsMarkup.push(edgeRibbonGradientMarkup(
                gradientId, ...gradients[side], baseColor, hotColor
              ));
              layersMarkup.push(
                '<g filter="url(#' + blurFilterId + ')"><rect x="' + number(ribbonBounds.left) + '" y="' + number(ribbonBounds.top) +
                '" width="' + number(ribbonBounds.right - ribbonBounds.left) + '" height="' + number(ribbonBounds.bottom - ribbonBounds.top) +
                '" fill="url(#' + gradientId + ')" clip-path="url(#' + clipId +
                ')" opacity="' + number(baseAlpha) + '"/></g>'
              );

            }
          });
          measureSvg.removeChild(sourcePath);
          continue;
        }

        if (config.reviewOnlyRadialFieldExperiment) {
          const totalVisibleSpan = intervals.reduce((sum, interval) => sum + Math.max(0, interval[1] - interval[0]), 0);
          if (totalVisibleSpan > 1) {
            let weightedX = 0;
            let weightedY = 0;
            let accumulatedSpan = 0;
            for (const interval of intervals) {
              const span = interval[1] - interval[0];
              if (!(span > 1)) continue;
              const sampleCount = Math.max(4, Math.ceil(span / Math.max(1, minDimension * 0.10)));
              let sumX = 0;
              let sumY = 0;
              for (let index = 0; index <= sampleCount; index++) {
                const distance = interval[0] + span * index / sampleCount;
                const point = sourcePath.getPointAtLength(distance);
                sumX += point.x;
                sumY += point.y;
              }
              weightedX += (sumX / (sampleCount + 1)) * span;
              weightedY += (sumY / (sampleCount + 1)) * span;
              accumulatedSpan += span;
            }
            const center = mapInset([weightedX / accumulatedSpan, weightedY / accumulatedSpan], rect);
            const tangentDistance = length * 0.5;
            const tangentStep = Math.max(1, Math.min(length * 0.05, minDimension * 0.025));
            const beforePoint = sourcePath.getPointAtLength(Math.max(0, tangentDistance - tangentStep));
            const afterPoint = sourcePath.getPointAtLength(Math.min(length, tangentDistance + tangentStep));
            const before = mapInset([beforePoint.x, beforePoint.y], rect);
            const after = mapInset([afterPoint.x, afterPoint.y], rect);
            const angle = Math.atan2(after[1] - before[1], after[0] - before[0]) * 180 / Math.PI;
            const radiusX = Math.min(Math.max(totalVisibleSpan / 2, minDimension * 0.20), minDimension * 0.25);
            const radiusY = maxDepth;
            const gradientId = "radial-gradient-" + safeId(path.id);
            defsMarkup.push(radialFieldGradientMarkup(gradientId, baseColor, hotColor));
            layersMarkup.push(
              '<ellipse cx="' + number(center[0]) + '" cy="' + number(center[1]) +
              '" rx="' + number(radiusX) + '" ry="' + number(radiusY) +
              '" transform="rotate(' + number(angle) + ' ' + number(center[0]) + ' ' + number(center[1]) +
              ')" fill="url(#' + gradientId + ')" opacity="' + number(baseAlpha) + '"/>'
            );
          }
          measureSvg.removeChild(sourcePath);
          continue;
        }

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
      '<g filter="url(#alpha-cap)" clip-path="url(#photo-clip)" mask="url(#quiet-gaps)"><g>' +
      layersMarkup.join("") + '</g></g></svg>';
  }

  function stableKey(config) {
    return JSON.stringify({
      version: VERSION, width: config.width, height: config.height, rect: config.rect,
      paths: config.paths, colors: config.colors, baseAlpha: config.baseAlpha,
      depthRatio: config.depthRatio, globalMultiplier: config.globalMultiplier,
      reviewOnlyAlphaExperiment: config.reviewOnlyAlphaExperiment,
      reviewOnlyRadialFieldExperiment: config.reviewOnlyRadialFieldExperiment,
      reviewOnlyEdgeRibbonExperiment: config.reviewOnlyEdgeRibbonExperiment,
      reviewOnlyContinuousFieldExperiment: config.reviewOnlyContinuousFieldExperiment,
      reviewOnlySpatialFieldExperiment: config.reviewOnlySpatialFieldExperiment,
      quietGapVariant: config.quietGapVariant,
      continuousField: config.reviewOnlyContinuousFieldExperiment ? CONTINUOUS_FIELD_PROFILE : null,
      outputAlphaCap: config.outputAlphaCap,
      dpToCanvasPx: config.dpToCanvasPx, enabled: config.enabled,
      coverage: COVERAGE_BY_PATH, inset: INSET, gaps: QUIET_GAPS, layers: LAYERS
    });
  }

  function modeDetails(config, entry, key) {
    if (config.reviewOnlyContinuousFieldExperiment) {
      return {
        alpha_mode: "REVIEW_ONLY_PERIMETER_CONTINUOUS_FIELD",
        geometry_mode: "PERIMETER_ANCHORED_CONTINUOUS_FIELD",
        field_version: REVIEW_ONLY_CONTINUOUS_FIELD_EXPERIMENT.version,
        quiet_gap_variant: config.quietGapVariant,
        field_anchors: entry.anchors,
        field_profile: CONTINUOUS_FIELD_PROFILE,
        field_depth_px: entry.depthPx,
        field_build_ms: entry.buildDurationMs,
        field_cache_hits: entry.cacheHits,
        estimated_cache_bytes: entry.estimatedBytes,
        cache_key: key,
        canvas: [config.width, config.height],
        base_alpha: config.baseAlpha,
        alpha_cap: config.outputAlphaCap
      };
    }
    return {
      alpha_mode: config.reviewOnlyEdgeRibbonExperiment ? "REVIEW_ONLY_EDGE_ANCHORED_RIBBON" : config.reviewOnlyRadialFieldExperiment ? "REVIEW_ONLY_RADIAL_FIELD_GEOMETRY" : config.reviewOnlyAlphaExperiment ? "REVIEW_ONLY_ALPHA_EXPERIMENT" : "P1",
      geometry_mode: config.reviewOnlyEdgeRibbonExperiment ? "EDGE_ANCHORED_RIBBON_FIELD" : config.reviewOnlyRadialFieldExperiment ? "DIRECTIONAL_RADIAL_FIELDS" : "PATH_STROKE",
      cache_key: key,
      canvas: [config.width, config.height],
      base_alpha: config.baseAlpha,
      alpha_cap: config.outputAlphaCap
    };
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
      if (config.reviewOnlyContinuousFieldExperiment) {
        const buildStartedAt = performance.now();
        const field = buildContinuousFieldCanvas(config);
        entry = {
          image: field.canvas, ready: true, failed: false, blobUrl: "", callbacks: new Set(),
          anchors: field.anchors, depthPx: field.depthPx,
          estimatedBytes: config.width * config.height * 4,
          buildDurationMs: performance.now() - buildStartedAt,
          cacheHits: 0
        };
        cache.set(key, entry);
      } else {
        const svg = buildSvg(config);
        const blobUrl = URL.createObjectURL(new Blob([svg], { type: "image/svg+xml;charset=utf-8" }));
        const image = new Image();
        image.dataset.edgeglowReceivingLightSource = "p1-path-svg";
        entry = { image, blobUrl, ready: false, failed: false, callbacks: new Set(), estimatedBytes: 0 };
        image.onload = () => {
          entry.ready = true;
          URL.revokeObjectURL(entry.blobUrl);
          entry.blobUrl = "";
          setStatus("ready", modeDetails(config, entry, key));
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
      }
      const cachedFieldBytes = () => [...cache.values()].reduce((sum, item) => sum + (item.estimatedBytes || 0), 0);
      while (cache.size > 16 || cachedFieldBytes() > 64 * 1024 * 1024) {
        const oldest = cache.keys().next().value;
        const old = cache.get(oldest);
        if (old && old.blobUrl) URL.revokeObjectURL(old.blobUrl);
        cache.delete(oldest);
      }
    } else if (config.reviewOnlyContinuousFieldExperiment) {
      entry.cacheHits++;
    }
    context.canvas.dataset.receivingLightRenderer = VERSION;
    context.canvas.dataset.receivingLightStatus = entry.ready ? "ready" : entry.failed ? "failed" : "pending";
    if (entry.failed) return { status: "failed", renderer: VERSION, cache_key: key };
    if (!entry.ready) {
      if (typeof options.onReady === "function") entry.callbacks.add(options.onReady);
      setStatus("pending", {
        cache_key: key, canvas: [config.width, config.height],
        alpha_mode: config.reviewOnlyEdgeRibbonExperiment ? "REVIEW_ONLY_EDGE_ANCHORED_RIBBON" : config.reviewOnlyRadialFieldExperiment ? "REVIEW_ONLY_RADIAL_FIELD_GEOMETRY" : config.reviewOnlyAlphaExperiment ? "REVIEW_ONLY_ALPHA_EXPERIMENT" : "P1",
          geometry_mode: config.reviewOnlyEdgeRibbonExperiment ? "EDGE_ANCHORED_RIBBON_FIELD" : config.reviewOnlyRadialFieldExperiment ? "DIRECTIONAL_RADIAL_FIELDS" : "PATH_STROKE",
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
    setStatus("ready", modeDetails(config, entry, key));
    return { status: "ready", renderer: VERSION, cache_key: key };
  }

  window.EdgeGlowReceivingLight = Object.freeze({
    version: VERSION,
    p1AlphaRange: P1_ALPHA_RANGE,
    p1DepthRange: P1_DEPTH_RANGE,
    outputAlphaCap: OUTPUT_ALPHA_CAP,
    reviewOnlyAlphaExperiment: REVIEW_ONLY_ALPHA_EXPERIMENT,
    reviewOnlyRadialFieldExperiment: REVIEW_ONLY_RADIAL_FIELD_EXPERIMENT,
    reviewOnlyEdgeRibbonExperiment: REVIEW_ONLY_EDGE_RIBBON_EXPERIMENT,
    reviewOnlyContinuousFieldExperiment: REVIEW_ONLY_CONTINUOUS_FIELD_EXPERIMENT,
    reviewOnlySpatialFieldExperiment: REVIEW_ONLY_SPATIAL_FIELD_EXPERIMENT,
    continuousFieldProfile: CONTINUOUS_FIELD_PROFILE,
    minPathWeight: MIN_PATH_WEIGHT,
    coverageByPath: COVERAGE_BY_PATH,
    inset: INSET,
    quietGaps: QUIET_GAPS,
    layers: LAYERS,
    defaultColors: DEFAULT_COLORS,
    paint,
    createReviewOnlySpatialField
  });
})();
