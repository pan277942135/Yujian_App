const DURATION = 1140;
const FRAME_MS = 1000 / 30;
const PHOTO_WIDTH = 1152;
const PHOTO_HEIGHT = 1536;
const photoUrl = new URL("../../../../design/system/fish_media_display_v1/assets/real_catch_source.jpg", import.meta.url);
const configUrl = new URL("../../../../design/system/recognition_optical_v2/RecognitionOpticalConfigV2.json", import.meta.url);
const fixtureUrl = new URL("../../nh01_acceptance/edgeglow-fish-static/fixture.json", import.meta.url);
const ui = Object.fromEntries([
  "runtimeStatus", "photoDigest", "buildSha", "rendererVersion", "catchPhoto", "p1Receiving",
  "edgeMotion", "transferLayer", "transferWash", "fishHalo", "fishMid", "fishCore",
  "stageName", "stageCaption", "captionStage", "currentTime", "playbackState",
  "playPause", "restart", "stepBack", "stepForward", "timeline", "speed",
  "toggleEdge", "toggleTransfer", "toggleFish", "toggleContour", "deviceFrame",
  "viewportLabel", "photoContent", "photoStage", "previewArea", "imageLoadError", "motionPreference",
  "reducedNote", "exportFrame", "phaseList", "motionSvg"
].map(id => [id, document.getElementById(id)]));

const phases = [
  { id: "CAPTURED", name: "外围光场唤醒", start: 0, end: 220 },
  { id: "DETECTING", name: "外围光场流动", start: 220, end: 540 },
  { id: "OUTLINE", name: "光场过渡至鱼体", start: 540, end: 890 },
  { id: "CLASSIFYING", name: "鱼体高亮稳定", start: 890, end: 1140 }
];
const viewportSizes = {
  "320x640": [320, 640],
  "390x844": [390, 844],
  "430x932": [430, 932],
  "1080x1920": [1080, 1920]
};
let currentMs = 0;
let playing = false;
let playbackSpeed = 1;
let rafId = 0;
let lastFrameMs = 0;
let config = null;
let fixture = null;
let pathNodes = [];
let reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
let contourVisible = true;

const clamp = (v, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v));
const ease = t => {
  const x = clamp(t);
  return x * x * (3 - 2 * x);
};
const fmt = n => Number(n.toFixed(3));
const stageAt = ms => phases.findIndex(x => ms >= x.start && (ms < x.end || x.end === DURATION && ms <= x.end));
const pathD = path => {
  const pt = xy => [fmt(xy[0] * PHOTO_WIDTH), fmt(xy[1] * PHOTO_HEIGHT)];
  const a = pt(path.start), b = pt(path.control1), c = pt(path.control2), d = pt(path.end);
  return "M " + a.join(" ") + " C " + b.join(" ") + " " + c.join(" ") + " " + d.join(" ");
};
const alpha8 = v => Math.round(clamp(v) * 255);

function addPathLayers(island) {
  const path = island.path;
  const d = pathD(path);
  const seed = Number(island.seed);
  const weight = Number(island.strength);
  const family = island.family === "gold" ? "gold" : "blue";
  const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
  group.setAttribute("class", "edge-island");
  group.dataset.id = island.id;
  group.dataset.seed = String(seed);
  group.dataset.weight = String(weight);
  const make = (className, width, filter, opacity, stroke) => {
    const node = document.createElementNS("http://www.w3.org/2000/svg", "path");
    node.setAttribute("d", d);
    node.setAttribute("fill", "none");
    node.setAttribute("stroke", stroke || "url(#" + family + "Trail)");
    node.setAttribute("stroke-linecap", "round");
    node.setAttribute("stroke-linejoin", "round");
    node.setAttribute("stroke-width", String(width));
    node.setAttribute("pathLength", "1000");
    node.setAttribute("opacity", "0");
    if (filter) node.setAttribute("filter", "url(#" + filter + ")");
    node.setAttribute("class", className);
    node.dataset.baseOpacity = String(opacity * weight);
    group.appendChild(node);
    return node;
  };
  const wide = make("edge-wide", 220, "wideBloom", .27);
  const mid = make("edge-mid", 112, "midBloom", .30);
  const ridge = make("edge-ridge", 28, "midBloom", .36);
  const flow = make("edge-flow", 14, null, .58, family === "gold" ? "#fff0bd" : "#d5f4ff");
  flow.setAttribute("stroke-dasharray", "108 892");
  flow.setAttribute("stroke-dashoffset", "0");
  pathNodes.push({ island, wide, mid, ridge, flow });
  ui.edgeMotion.appendChild(group);
}

function setLayerEnabled(layer, enabled) {
  layer.style.display = enabled ? "" : "none";
}

function updateStage(ms) {
  let index = stageAt(ms);
  if (index < 0) index = 0;
  const phase = phases[index];
  ui.stageName.textContent = phase.id;
  ui.stageCaption.textContent = phase.name;
  ui.captionStage.textContent = ms <= 0 ? "等待开始" : ms >= DURATION ? "识别稳定" : phase.name;
  ui.phaseList.querySelectorAll(".phase-item").forEach((button, i) => {
    button.classList.toggle("active", i === index);
    button.setAttribute("aria-current", i === index ? "step" : "false");
  });
}

function edgeEnvelope(ms) {
  const wake = ease((ms - 0) / 220);
  const detecting = ease((ms - 220) / 320);
  const transition = 1 - .58 * ease((ms - 540) / 350);
  const stable = .42 - .28 * ease((ms - 890) / 250);
  if (ms < 220) return wake * .78;
  if (ms < 540) return .78 + detecting * .22;
  if (ms < 890) return transition;
  return Math.max(.14, stable);
}

function renderAt(ms) {
  currentMs = clamp(ms, 0, DURATION);
  ui.timeline.value = String(Math.round(currentMs));
  ui.currentTime.textContent = String(Math.round(currentMs));
  updateStage(currentMs);
  const edge = edgeEnvelope(currentMs);
  const transition = ease((currentMs - 510) / 260) * (1 - ease((currentMs - 870) / 250));
  const fishBuild = ease((currentMs - 540) / 350);
  const fishStable = currentMs < 890 ? fishBuild : .88 + .08 * ease((currentMs - 890) / 250);
  const flowProgress = clamp(currentMs / DURATION);

  ui.p1Receiving.style.opacity = String(.68 * edge);
  pathNodes.forEach(({ island, wide, mid, ridge, flow }) => {
    const seed = Number(island.seed);
    const weight = Number(island.strength);
    const phase = seed * Math.PI * 2;
    const cornerBreath = .91 + .07 * Math.sin(currentMs / 640 + phase) + .035 * Math.sin(currentMs / 370 + phase * .63);
    const delayedWake = ease((currentMs - seed * 42) / 190);
    const local = clamp(edge * cornerBreath * (.72 + .28 * delayedWake));
    wide.setAttribute("opacity", String(fmt(Number(wide.dataset.baseOpacity) * local)));
    mid.setAttribute("opacity", String(fmt(Number(mid.dataset.baseOpacity) * local * (.90 + .07 * Math.sin(currentMs / 520 + phase)))));
    ridge.setAttribute("opacity", String(fmt(Number(ridge.dataset.baseOpacity) * local * .9)));
    flow.setAttribute("opacity", String(fmt(Number(flow.dataset.baseOpacity) * local * (.45 + .2 * delayedWake))));
    const travel = (flowProgress * 210 + seed * 920) % 1000;
    flow.setAttribute("stroke-dashoffset", String(fmt(1000 - travel)));
  });

  const washX = 25 + 225 * ease((currentMs - 540) / 350);
  const washY = 905 - 28 * ease((currentMs - 540) / 350);
  ui.transferWash.setAttribute("cx", String(fmt(washX)));
  ui.transferWash.setAttribute("cy", String(fmt(washY)));
  ui.transferWash.setAttribute("rx", String(fmt(205 + 52 * transition)));
  ui.transferWash.setAttribute("ry", String(fmt(175 + 36 * transition)));
  ui.transferWash.setAttribute("opacity", String(fmt(.74 * transition)));

  const reveal = clamp((currentMs - 535) / 385);
  const dash = String(fmt(1000 * (1 - reveal)));
  [ui.fishHalo, ui.fishMid, ui.fishCore].forEach(node => {
    node.setAttribute("d", fixture.contour.path_d);
    node.setAttribute("pathLength", "1000");
    node.setAttribute("stroke-dasharray", "1000");
    node.setAttribute("stroke-dashoffset", dash);
  });
  ui.fishHalo.setAttribute("opacity", String(fmt(.42 * fishStable)));
  ui.fishMid.setAttribute("opacity", String(fmt(.46 * fishStable)));
  ui.fishCore.setAttribute("opacity", String(fmt(.88 * fishStable)));
  ui.fishCore.style.display = contourVisible ? "" : "none";
  ui.edgeMotion.style.opacity = ui.toggleEdge.checked ? "1" : "0";
  ui.p1Receiving.style.display = ui.toggleEdge.checked ? "" : "none";
  ui.transferLayer.style.display = ui.toggleTransfer.checked ? "" : "none";
  ui.fishHalo.style.display = ui.toggleFish.checked ? "" : "none";
  ui.fishMid.style.display = ui.toggleFish.checked ? "" : "none";
  if (!ui.toggleFish.checked) ui.fishCore.style.display = "none";
}

function stopPlayback(status = "已暂停") {
  if (rafId) cancelAnimationFrame(rafId);
  rafId = 0;
  playing = false;
  ui.playPause.textContent = "▶ 播放";
  ui.playPause.setAttribute("aria-label", "播放识别动效");
  ui.playbackState.textContent = status;
}

function tick(now) {
  if (!playing) return;
  const delta = Math.max(0, now - lastFrameMs);
  lastFrameMs = now;
  renderAt(currentMs + delta * playbackSpeed);
  if (currentMs >= DURATION) {
    renderAt(DURATION);
    stopPlayback("播放完成");
    return;
  }
  rafId = requestAnimationFrame(tick);
}

function play() {
  if (currentMs >= DURATION) renderAt(0);
  if (playing) return;
  playing = true;
  ui.playPause.textContent = "Ⅱ 暂停";
  ui.playPause.setAttribute("aria-label", "暂停识别动效");
  ui.playbackState.textContent = "播放中";
  lastFrameMs = performance.now();
  rafId = requestAnimationFrame(tick);
}

function fitPhotoToStage() {
  const rect = ui.photoStage.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  const scale = Math.min(rect.width / PHOTO_WIDTH, rect.height / PHOTO_HEIGHT);
  ui.photoContent.style.width = (PHOTO_WIDTH * scale) + "px";
  ui.photoContent.style.height = (PHOTO_HEIGHT * scale) + "px";
}

function fitDevice() {
  const key = ui.deviceFrame.dataset.viewport || "390x844";
  const [targetWidth, targetHeight] = viewportSizes[key];
  const area = ui.previewArea.getBoundingClientRect();
  if (!area.width || !area.height) return;
  const scale = Math.min((area.width - 24) / targetWidth, (area.height - 24) / targetHeight);
  ui.deviceFrame.style.width = Math.max(1, targetWidth * scale) + "px";
  ui.deviceFrame.style.height = Math.max(1, targetHeight * scale) + "px";
  requestAnimationFrame(fitPhotoToStage);
}

function changeViewport(key) {
  const [width, height] = viewportSizes[key];
  ui.deviceFrame.dataset.viewport = key;
  ui.deviceFrame.style.aspectRatio = width + " / " + height;
  ui.viewportLabel.textContent = width + " × " + height;
  document.querySelectorAll(".viewport-option").forEach(button => {
    const active = button.dataset.viewport === key;
    button.classList.toggle("active", active);
    button.setAttribute("aria-pressed", String(active));
  });
  requestAnimationFrame(fitDevice);
}

function enableControls() {
  ui.playPause.addEventListener("click", () => playing ? stopPlayback() : play());
  ui.restart.addEventListener("click", () => { stopPlayback("从头播放"); renderAt(0); play(); });
  ui.stepBack.addEventListener("click", () => { stopPlayback(); renderAt(currentMs - FRAME_MS); });
  ui.stepForward.addEventListener("click", () => { stopPlayback(); renderAt(currentMs + FRAME_MS); });
  ui.timeline.addEventListener("input", event => {
    stopPlayback("时间轴检查");
    renderAt(Number(event.currentTarget.value));
  });
  ui.speed.addEventListener("change", event => {
    playbackSpeed = Number(event.currentTarget.value);
    ui.motionPreference.textContent = "统一时钟 · " + playbackSpeed + "× · 1,140 ms";
  });
  ui.phaseList.addEventListener("click", event => {
    const button = event.target.closest("[data-time]");
    if (!button) return;
    stopPlayback("阶段检查");
    renderAt(Number(button.dataset.time));
  });
  ["toggleEdge", "toggleTransfer", "toggleFish", "toggleContour"].forEach(id => {
    document.getElementById(id).addEventListener("change", event => {
      if (id === "toggleContour") contourVisible = event.currentTarget.checked;
      renderAt(currentMs);
    });
  });
  document.querySelectorAll(".viewport-option").forEach(button => {
    button.addEventListener("click", () => changeViewport(button.dataset.viewport));
  });
  ui.exportFrame.addEventListener("click", exportCurrentFrame);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && playing) stopPlayback("切至后台，已暂停");
  });
  document.addEventListener("keydown", event => {
    if (event.target.matches("input,select,textarea,button,a")) return;
    if (event.code === "Space") {
      event.preventDefault();
      playing ? stopPlayback() : play();
    } else if (event.code === "ArrowRight") {
      stopPlayback();
      renderAt(currentMs + FRAME_MS);
    } else if (event.code === "ArrowLeft") {
      stopPlayback();
      renderAt(currentMs - FRAME_MS);
    }
  });
  const resizeObserver = new ResizeObserver(fitDevice);
  resizeObserver.observe(ui.photoStage);
  resizeObserver.observe(ui.previewArea);
}

async function sha256Hex(buffer) {
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, "0")).join("");
}

async function exportCurrentFrame() {
  const canvas = document.createElement("canvas");
  canvas.width = PHOTO_WIDTH;
  canvas.height = PHOTO_HEIGHT;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(ui.catchPhoto, 0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
  ctx.drawImage(ui.p1Receiving, 0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
  const svgCopy = ui.motionSvg.cloneNode(true);
  svgCopy.setAttribute("width", String(PHOTO_WIDTH));
  svgCopy.setAttribute("height", String(PHOTO_HEIGHT));
  const svgBlob = new Blob([new XMLSerializer().serializeToString(svgCopy)], { type: "image/svg+xml;charset=utf-8" });
  const url = URL.createObjectURL(svgBlob);
  const overlay = new Image();
  overlay.onload = () => {
    ctx.drawImage(overlay, 0, 0, PHOTO_WIDTH, PHOTO_HEIGHT);
    URL.revokeObjectURL(url);
    canvas.toBlob(blob => {
      if (!blob) return;
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = "receiving-light-frame-" + Math.round(currentMs) + "ms.png";
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 1000);
    }, "image/png");
  };
  overlay.onerror = () => URL.revokeObjectURL(url);
  overlay.src = url;
}

async function load() {
  try {
    const [configResponse, fixtureResponse] = await Promise.all([fetch(configUrl), fetch(fixtureUrl)]);
    if (!configResponse.ok || !fixtureResponse.ok) throw new Error("配置或轮廓资源请求失败");
    config = await configResponse.json();
    fixture = await fixtureResponse.json();
    if (fixture.contour.source !== "MANUAL_REFERENCE_CONTOUR" || fixture.contour.production_mask !== false) {
      throw new Error("当前轮廓来源标记与 fixture 不一致");
    }
    ui.catchPhoto.src = photoUrl.href;
    await new Promise((resolve, reject) => {
      ui.catchPhoto.onload = resolve;
      ui.catchPhoto.onerror = () => reject(new Error("真实鱼获照片加载失败"));
      if (ui.catchPhoto.complete && ui.catchPhoto.naturalWidth) resolve();
    });
    if (ui.catchPhoto.naturalWidth !== PHOTO_WIDTH || ui.catchPhoto.naturalHeight !== PHOTO_HEIGHT) {
      throw new Error("照片尺寸不符合已登记的 1152 × 1536");
    }
    const photoResponse = await fetch(photoUrl);
    const digest = await sha256Hex(await photoResponse.arrayBuffer());
    const expectedDigest = "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef";
    ui.photoDigest.textContent = digest + (digest === expectedDigest ? " · SHA-256 MATCH" : " · SHA-256 MISMATCH");
    ui.photoDigest.dataset.sha256 = digest;
    if (digest !== expectedDigest) throw new Error("照片 SHA-256 与登记值不匹配");

    const islands = config.primary_islands || [];
    const pathMap = Object.fromEntries(islands.map(item => [item.id, item]));
    const ordered = ["B1", "G1", "G3", "B3"].map(id => pathMap[id]);
    if (ordered.some(item => !item)) throw new Error("V2 冻结主路径 B1/G1/G3/B3 不完整");
    ordered.forEach(addPathLayers);
    ui.fishHalo.setAttribute("d", fixture.contour.path_d);
    ui.fishMid.setAttribute("d", fixture.contour.path_d);
    ui.fishCore.setAttribute("d", fixture.contour.path_d);

    if (!window.EdgeGlowReceivingLight) throw new Error("共享 Receiving Light 渲染器未加载");
    const ctx = ui.p1Receiving.getContext("2d");
    const receivingOptions = {
      width: PHOTO_WIDTH,
      height: PHOTO_HEIGHT,
      photoContentRect: { left: 0, top: 0, right: PHOTO_WIDTH, bottom: PHOTO_HEIGHT },
      paths: ordered.map(item => ({
        id: item.id, family: item.family, weight: item.strength, seed: item.seed,
        visibleRatio: item.visible_ratio,
        start: item.path.start, control1: item.path.control1, control2: item.path.control2, end: item.path.end
      })),
      baseAlpha: .07,
      depthRatio: .18,
      quietGapVariant: "B",
      dpToCanvasPx: 1
    };
    const paintResult = window.EdgeGlowReceivingLight.paint(ctx, {
      ...receivingOptions,
      onReady: () => window.EdgeGlowReceivingLight.paint(ctx, receivingOptions)
    });
    ui.rendererVersion.textContent = window.EdgeGlowReceivingLight.version;
    if (paintResult.status === "pending") {
      await new Promise((resolve, reject) => {
        const started = performance.now();
        const check = () => {
          if (ui.p1Receiving.dataset.receivingLightStatus === "ready") resolve();
          else if (ui.p1Receiving.dataset.receivingLightStatus === "failed" || performance.now() - started > 5000) reject(new Error("共享 Receiving Light 渲染超时"));
          else requestAnimationFrame(check);
        };
        check();
      });
    }
    const params = new URLSearchParams(location.search);
    const metaSha = document.querySelector('meta[name="recognition-motion-source-sha"]')?.content || "";
    const sourceSha = params.get("source_sha") || (metaSha.startsWith("__") ? "" : metaSha);
    ui.buildSha.textContent = sourceSha || "本地预览 · " + (document.querySelector('meta[name="recognition-motion-studio"]')?.content || "WEB_REVIEW");
    ui.buildSha.dataset.sourceSha = sourceSha || "local-preview";
    reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reducedMotion) {
      ui.reducedNote.hidden = false;
      ui.motionPreference.textContent = "减少动态偏好 · 手动播放";
    }
    ui.runtimeStatus.textContent = "照片、路径与共享渲染器已就绪";
    ui.runtimeStatus.classList.add("success");
    enableControls();
    changeViewport("390x844");
    renderAt(0);
    window.RecognitionMotionStudio = Object.freeze({
      duration: DURATION,
      phases,
      renderAt,
      play,
      pause: stopPlayback,
      getState: () => ({ currentMs, playing, phase: phases[Math.max(0, stageAt(currentMs))].id, speed: playbackSpeed }),
      photoSha256: digest,
      contourSource: fixture.contour.source
    });
  } catch (error) {
    ui.runtimeStatus.textContent = "加载失败 · " + error.message;
    ui.runtimeStatus.classList.add("error");
    ui.imageLoadError.hidden = false;
    console.error("Recognition Motion Studio initialization failed:", error);
  }
}

load();
