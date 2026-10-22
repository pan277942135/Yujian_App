/* Static SWOS inspection only. The selected photo remains a browser-local object URL. */
(function () {
  "use strict";

  const RESULT_AUTHORITY = "design/pages/recognition/design/05_Result_High_Frozen.png";
  const FRAME_URL = "design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json";
  const TRANSITION_URL = "design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json";
  const esc = value => String(value ?? "").replace(/[&<>\"']/g, ch => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
  })[ch]);
  const urlFor = path => "../../" + path;

  function canvas(view) {
    return '<section class="swos-workspace" data-frame-contract="' + esc(view.machine_authority) + '" data-transition-contract="' + esc(view.transition_authority) + '">' +
      '<div class="swos-workspace-head"><div><div class="swos-kicker">SWOS · STATIC VISUAL REVIEW</div>' +
      '<h3>15 帧静态验收工作区</h3><p>2800ms Processing + 850ms RR09 = 3650ms。R0.5 是 100ms 采样帧，不增加等待或业务状态。</p></div>' +
      '<a class="swos-related-link" href="#page/recognition_result_v1/hifi/motion_continuation">跳转 RR09 · 动效接续 ↗</a></div>' +
      '<div class="swos-photo-control"><label class="swos-upload-button">选择本地真实鱼获照片<input class="swos-photo-input" type="file" accept="image/jpeg,image/png,image/webp"></label>' +
      '<div class="swos-privacy-note"><strong>本地预览</strong><span>照片仅由此页面的浏览器内存读取，不会上传或保存。公开预览不含默认照片。</span></div>' +
      '<div class="swos-photo-meta" aria-live="polite">尚未选择照片 · 帧结构与合同仍可检查</div></div>' +
      '<div class="swos-gate-strip"><span><b>DESIGN_ONLY</b> · 依赖 Draft PR #186</span><span>照片发布许可：未确认</span><span>鱼体轮廓：仅当 SHA 匹配 fixture 时显示人工参考线，不是生产 Mask</span></div>' +
      '<div class="swos-storyboard-wrap"><div class="swos-storyboard-title"><strong>完整横向静态总图</strong><span>点击任一帧可放大；左右滚动查看 15 帧</span></div><div class="swos-storyboard" data-storyboard></div></div>' +
      '<section class="swos-inspector" aria-label="逐帧检查"><div class="swos-inspector-header"><div><div class="swos-kicker">FRAME INSPECTOR</div><h4 data-inspector-title>等待载入帧合同</h4></div><span data-inspector-state class="swos-inspector-state">STATIC · NO PLAYBACK</span></div>' +
      '<div class="swos-inspector-grid"><div class="swos-inspector-phone" data-inspector-phone></div><div class="swos-inspector-details" data-inspector-details></div></div></section>' +
      '<section class="swos-transition-review"><div class="swos-storyboard-title"><strong>P7 → R0 → R0.5 → R1 · 照片几何连续性</strong><span>同源照片、同一前景、同一几何链</span></div><div class="swos-geometry-table" data-geometry-table></div></section>' +
      '<section class="swos-source-review"><div class="swos-storyboard-title"><strong>原始照片 / 光效合成对照</strong><span>合成仅为静态视觉示意；强度值不是生产 Alpha</span></div><div class="swos-compare" data-compare></div></section>' +
      '<section class="swos-contract-notes"><article><b>冻结方向</b><span>低中密度不规则面状焦散；金光 Core / Soft Glow / Halo 共享可信鱼体几何；不使用扫描框、密集网线或 AI Edge Field 七路径结构。</span></article>' +
      '<article><b>页面复用</b><span>Processing 继续使用原照片视口与状态卡结构。R5 以现行 Frozen HIGH Result 页为版式权威，照片进入既有 Hero Media Contract。</span></article>' +
      '<article><b>未校准参数</b><span>浏览器光效绘制强度、边缘柔化、各尺寸下的实际像素对照需动态原型阶段校准；此页不代表 Android 或生产渲染通过。</span></article></section>' +
      '<div class="swos-authority-links"><a href="' + esc(urlFor("design/pages/recognition/processing/optics/SWOS_Static_Visual_Contract_V1_1.md")) + '" target="_blank" rel="noreferrer">SWOS Static Visual Contract V1.1 ↗</a>' +
      '<a href="' + esc(urlFor(FRAME_URL)) + '" target="_blank" rel="noreferrer">15-Frame Keyframe Contract ↗</a>' +
      '<a href="' + esc(urlFor(TRANSITION_URL)) + '" target="_blank" rel="noreferrer">Shared Photo Transition Contract ↗</a>' +
      '<a href="' + esc(urlFor(RESULT_AUTHORITY)) + '" target="_blank" rel="noreferrer">Frozen HIGH Result source ↗</a></div>' +
      '<dialog class="swos-zoom-dialog" data-dialog><div class="swos-zoom-content"><header><div><strong data-zoom-title>SWOS frame</strong><small data-zoom-caption></small></div><button type="button" class="swos-zoom-close" data-close aria-label="关闭">×</button></header>' +
      '<div class="swos-zoom-mode"><button type="button" data-mode="composite" class="active">光效合成</button><button type="button" data-mode="original">原始照片 / 无光效</button></div><div class="swos-zoom-screen" data-zoom-screen></div>' +
      '<footer>静态采样 · 无播放时钟 · 不代表模型结果或生产鱼体 Mask</footer></div></dialog></section>';
  }

  async function mount(root, view) {
    const workspace = root.querySelector(".swos-workspace");
    if (!workspace) return;
    const frameUrl = urlFor(view.machine_authority || FRAME_URL);
    const transitionUrl = urlFor(view.transition_authority || TRANSITION_URL);
    const [frameResponse, transitionResponse] = await Promise.all([fetch(frameUrl), fetch(transitionUrl)]);
    if (!frameResponse.ok || !transitionResponse.ok) {
      workspace.querySelector("[data-storyboard]").innerHTML = '<div class="swos-load-error">15 帧合同文件无法加载。</div>';
      return;
    }
    const [contract, transition] = await Promise.all([frameResponse.json(), transitionResponse.json()]);
    const state = { contract, transition, photoUrl: "", photoHash: "", photoName: "", photoWidth: 0, photoHeight: 0, selected: contract.keyframes?.[0] || null, mode: "composite" };
    const frameMap = new Map(contract.keyframes.map(frame => [frame.id, frame]));
    const fixtureHash = transition.sample_source.source_photo_sha256;

    function displayRects(frame) {
      const processing = transition.processing_photo_content_rect;
      const processingOuter = transition.samples.R0.outer_rect;
      if (frame.track === "PROCESSING" || frame.id === "R0") return { outer: processingOuter, foreground: processing };
      if (frame.id === "R0.5") return { outer: transition.samples["R0.5"].outer_rect, foreground: transition.samples["R0.5"].foreground_rect };
      return { outer: transition.samples.R1.outer_rect, foreground: transition.samples.R1.foreground_rect };
    }

    function statusText(frame) {
      if (frame.track === "PROCESSING") {
        if (frame.global_nominal_ms < 1200) return ["正在理解照片", "查找鱼获线索"];
        if (frame.global_nominal_ms < 2600) return ["已定位到鱼体", "正在分析这次鱼获"];
        return ["正在认识这条鱼", "分析鱼体特征"];
      }
      return ["识别结果", frame.id === "R0" || frame.id === "R0.5" ? "照片自然接续中" : "识别结果高置信结构示意"];
    }

    function opticalSvg(frame, rects, localMode = state.mode) {
      if (localMode === "original" || !state.photoUrl) return "";
      const fx = frame.fish_gold_strength || 0;
      const wa = frame.ambient_water_strength || 0;
      const hasExactFixtureManualTrace = state.photoHash === fixtureHash && transition.fish_geometry.production_mask === false;
      const p = esc(transition.fish_geometry.contour_svg_path || "");
      const svgSuffix = String(frame.id).replace(/[^a-z0-9]/gi, "_");
      const fishScale = Math.min(rects.foreground.width / transition.sample_source.source_width, rects.foreground.height / transition.sample_source.source_height);
      const fishTransform = 'translate(' + rects.foreground.x + ' ' + rects.foreground.y + ') scale(' + fishScale + ')';
      const mappedGoldOpacity = Math.min(.82, .82 * fx / 100);
      const gold = hasExactFixtureManualTrace && fx > 0
        ? '<g transform="' + fishTransform + '" opacity="' + mappedGoldOpacity + '" aria-label="人工参考轮廓"><path class="swos-gold-halo" d="' + p + '"/><path class="swos-gold-soft" d="' + p + '"/><path class="swos-gold-core" d="' + p + '"/></g>'
        : "";
      const waterAlpha = Math.max(0, Math.min(.54, wa * .0072));
      const personExclusion = 'M154 170 L184 158 L226 167 L258 192 L279 228 L302 265 L325 324 L328 424 L314 510 L303 560 L128 560 L135 503 L151 434 L158 372 L147 309 L145 244 Z';
      const safeMask = '<mask id="swos-photo-safe-' + svgSuffix + '"><rect width="360" height="640" fill="white"/><path d="' + personExclusion + '" fill="black"/><path d="' + p + '" transform="' + fishTransform + '" fill="black"/></mask>';
      const water = frame.track === "PROCESSING" && wa > 0 && hasExactFixtureManualTrace
        ? '<svg class="swos-water-svg" viewBox="0 0 360 640" preserveAspectRatio="none" style="opacity:' + waterAlpha + '" aria-hidden="true"><defs>' +
          '<radialGradient id="swos-c1-' + svgSuffix + '"><stop stop-color="#f6e6b8" stop-opacity=".9"/><stop offset="1" stop-color="#8ec9cf" stop-opacity="0"/></radialGradient>' +
          '<radialGradient id="swos-c2-' + svgSuffix + '"><stop stop-color="#d5edf0" stop-opacity=".88"/><stop offset="1" stop-color="#7eb9c4" stop-opacity="0"/></radialGradient>' +
          '<radialGradient id="swos-c3-' + svgSuffix + '"><stop stop-color="#fff2d2" stop-opacity=".84"/><stop offset="1" stop-color="#b4d4d5" stop-opacity="0"/></radialGradient>' +
          '<clipPath id="swos-photo-content-' + svgSuffix + '"><rect x="0" y="80" width="360" height="480"/></clipPath>' + safeMask +
          '</defs><g clip-path="url(#swos-photo-content-' + svgSuffix + ')" mask="url(#swos-photo-safe-' + svgSuffix + ')">' +
          '<path d="M1 302 5 295 10 297 13 291 18 294 21 300 18 305 13 307 9 312 4 309Z" fill="url(#swos-c1-' + svgSuffix + ')"/>' +
          '<path d="M25 291 30 285 36 287 40 292 47 291 51 296 47 301 40 300 35 304 29 300Z" fill="url(#swos-c3-' + svgSuffix + ')"/>' +
          '<path d="M5 323 10 318 15 321 18 317 23 321 25 327 21 331 15 329 11 334 6 330Z" fill="url(#swos-c2-' + svgSuffix + ')"/>' +
          '<path d="M31 313 36 308 42 310 46 307 52 312 54 318 49 321 43 319 39 324 34 320Z" fill="url(#swos-c1-' + svgSuffix + ')"/>' +
          '<path d="M0 343 4 338 9 340 12 336 16 342 14 347 10 350 5 347Z" fill="url(#swos-c3-' + svgSuffix + ')"/>' +
          '<path d="M55 276 60 271 65 273 69 270 74 275 72 280 66 281 62 285 57 282Z" fill="url(#swos-c2-' + svgSuffix + ')"/>' +
          '<path d="M19 340 23 337 28 339 30 344 27 348 22 346Z" fill="url(#swos-c1-' + svgSuffix + ')"/>' +
          '</g></svg>'
        : "";
      let transfer = "";
      if (frame.id === "R2" && hasExactFixtureManualTrace) {
        const startX = rects.foreground.x + rects.foreground.width / 2;
        const startY = rects.foreground.y + (1090 * fishScale);
        transfer = '<path class="swos-transfer-path" d="M' + startX.toFixed(2) + ' ' + startY.toFixed(2) + ' C' + (startX + 13).toFixed(2) + ' ' + (startY + 28).toFixed(2) + ' 208 248 196 284"/>';
      }
      if (frame.id === "R3") {
        transfer = '<g class="swos-glyph-fragments" aria-label="不可读的中文笔画碎片"><path d="M151 287l8 8M169 293l6-9M188 286l9 7M210 294l7-8"/></g>';
      }
      return water + gold + transfer;
    }

    function phone(frame, options = {}) {
      const rects = displayRects(frame);
      const activeMode = options.mode || state.mode;
      const isProcessing = frame.track === "PROCESSING";
      const isResult = !isProcessing;
      const uiOpacity = frame.id === "R0" ? .22 : frame.id === "R0.5" ? .58 : 1;
      const [line1, line2] = statusText(frame);
      const title = frame.species_name_reveal === "CHINESE_ONLY_PLACEHOLDER" ? "鱼种名称" : "";
      const source = state.photoUrl;
      const r0Underlay = frame.id === "R0" && source
        ? '<div class="swos-r0-underlay" style="left:' + transition.result_hero_rect.x + 'px;top:' + transition.result_hero_rect.y + 'px;width:' + transition.result_hero_rect.width + 'px;height:' + transition.result_hero_rect.height + 'px"><img src="' + esc(source) + '" alt="" aria-hidden="true"></div>'
        : "";
      const photoShell = (frame.id === "R0" ? "" : '<div class="swos-photo-shell" style="left:' + rects.outer.x + 'px;top:' + rects.outer.y + 'px;width:' + rects.outer.width + 'px;height:' + rects.outer.height + 'px">' +
        (source ? '<img class="swos-photo-blur" src="' + esc(source) + '" alt="" aria-hidden="true">' : '<div class="swos-photo-placeholder">' + (isProcessing ? "Processing · PHOTO" : "RESULT HERO · PHOTO") + '</div>') + '</div>') +
        (source ? '<img class="swos-photo-foreground" src="' + esc(source) + '" alt="同一张原始照片" style="left:' + rects.foreground.x + 'px;top:' + rects.foreground.y + 'px;width:' + rects.foreground.width + 'px;height:' + rects.foreground.height + 'px">' : '<div class="swos-photo-empty" style="left:' + rects.foreground.x + 'px;top:' + rects.foreground.y + 'px;width:' + rects.foreground.width + 'px;height:' + rects.foreground.height + 'px">选择照片后显示实际主体</div>') +
        (source ? '<svg class="swos-overlay-svg" viewBox="0 0 360 640" preserveAspectRatio="none" aria-hidden="true">' + opticalSvg(frame, rects, activeMode) + '</svg>' : "");
      const resultBackdrop = isResult
        ? '<img class="swos-result-authority" src="' + esc(urlFor(RESULT_AUTHORITY)) + '" alt="" style="opacity:' + uiOpacity + '">' + r0Underlay +
          '<div class="swos-result-name-cover">' + (title ? '<strong>' + esc(title) + '</strong>' : '') + '</div>' +
          '<div class="swos-result-reveal-note" style="opacity:' + uiOpacity + '">' + (frame.id === "R0" || frame.id === "R0.5" ? "照片接续" : frame.id === "R1" ? "照片落位" : frame.id === "R2" ? "金光转移" : frame.id === "R3" ? "笔画凝结" : title ? "视觉占位" : "静态结果") + '</div>'
        : '<div class="swos-processing-top"><span class="swos-back" aria-hidden="true">‹</span><span>渔见 AI · Processing</span></div>' +
          '<div class="swos-processing-status"><small>FROZEN STATUS CARD · COPY REFERENCE</small><strong>' + esc(line1) + '</strong><span>' + esc(line2) + '</span></div>' +
          '<div class="swos-processing-fixture">静态视觉样例 · 不代表模型事件</div>';
      const scale = options.scale || (options.large ? .82 : .3833333333);
      return '<div class="swos-phone-shell' + (options.large ? ' swos-phone-large' : '') + '" style="--swos-scale:' + scale + ';width:' + (360 * scale) + 'px;height:' + (640 * scale) + 'px"><div class="swos-phone ' + (isProcessing ? 'swos-processing' : 'swos-result') + '" data-frame="' + esc(frame.id) + '">' +
        resultBackdrop + photoShell +
        '<div class="swos-frame-footer"><span>' + esc(frame.id) + '</span><span>' + frame.global_nominal_ms + 'ms</span></div></div></div>';
    }

    function frameCard(frame) {
      return '<button type="button" class="swos-frame-card" data-frame-id="' + esc(frame.id) + '" aria-label="放大查看 ' + esc(frame.id) + '，' + frame.global_nominal_ms + ' 毫秒">' +
        '<span class="swos-frame-card-head"><b>' + esc(frame.id) + '</b><small>' + frame.global_nominal_ms + 'ms</small></span>' +
        phone(frame) + '<span class="swos-frame-caption">' + esc(frame.label_zh) + '</span></button>';
    }

    function renderStoryboard() {
      const processing = contract.keyframes.filter(frame => frame.track === "PROCESSING");
      const rr09 = contract.keyframes.filter(frame => frame.track === "RR09");
      workspace.querySelector("[data-storyboard]").innerHTML =
        '<div class="swos-track-row"><div class="swos-track-label"><b>PROCESSING</b><span>0–2800ms · 8 帧</span></div><div class="swos-frame-row">' + processing.map(frameCard).join("") + '</div></div>' +
        '<div class="swos-track-row"><div class="swos-track-label"><b>RR09</b><span>2800–3650ms · 7 帧</span></div><div class="swos-frame-row">' + rr09.map(frameCard).join("") + '</div></div>';
      workspace.querySelectorAll("[data-frame-id]").forEach(button => button.addEventListener("click", () => openFrame(frameMap.get(button.dataset.frameId))));
    }

    function setMode(mode) {
      state.mode = mode;
      renderSelected();
      const dialog = workspace.querySelector("[data-dialog]");
      const zoomScreen = workspace.querySelector("[data-zoom-screen]");
      if (dialog.open && state.selected) zoomScreen.innerHTML = phone(state.selected, { large: true, scale: 1, mode });
      workspace.querySelectorAll("[data-mode]").forEach(button => button.classList.toggle("active", button.dataset.mode === mode));
    }

    function photoStateText(frame) {
      if (!state.photoUrl) return "待选择本地照片。公开预览仅展示合同与框架，不会下载或内嵌用户素材。";
      return 'sourcePhotoSHA256: ' + state.photoHash + ' · ' + state.photoWidth + '×' + state.photoHeight + ' · ' + (state.photoHash === fixtureHash ? '登记夹具：显示人工局部水光和人工参考轮廓（均非生产 Mask）' : '非登记夹具：不套用该照片专属人工水光/轮廓；仅检查照片几何');
    }

    function renderSelected() {
      const frame = state.selected;
      if (!frame) return;
      workspace.querySelector("[data-inspector-title]").textContent = frame.id + " · " + frame.label_zh;
      workspace.querySelector("[data-inspector-phone]").innerHTML = phone(frame, { large: true, scale: .76 });
      const hasTrace = state.photoHash === fixtureHash;
      const goldNote = frame.fish_gold_strength > 0
        ? (hasTrace ? '金光强度 ' + frame.fish_gold_strength + ' / 100 · Core / Soft Glow / Halo 共享一条人工参考几何。' : '金光目标强度 ' + frame.fish_gold_strength + ' / 100 · 当前照片没有可信鱼体几何，因此不画轮廓。')
        : '此帧鱼体金光强度 0 / 100。';
      workspace.querySelector("[data-inspector-details]").innerHTML = '<dl>' +
        '<div><dt>时间</dt><dd>' + frame.global_nominal_ms + 'ms 全局 · ' + frame.relative_ms + 'ms ' + esc(frame.track) + '</dd></div>' +
        '<div><dt>视觉状态</dt><dd>' + esc(frame.visual_state) + ' · ' + esc(frame.photo_geometry_state) + '</dd></div>' +
        '<div><dt>外围水光</dt><dd>' + frame.ambient_water_strength + ' / 100 相对强度</dd></div>' +
        '<div><dt>鱼体金光</dt><dd>' + goldNote + '</dd></div>' +
        '<div><dt>照片几何</dt><dd>outer=' + JSON.stringify(displayRects(frame).outer) + ' · foreground=' + JSON.stringify(displayRects(frame).foreground) + '</dd></div>' +
        '<div><dt>名称状态</dt><dd>' + esc(frame.species_name_reveal) + (frame.species_name_reveal === "CHINESE_ONLY_PLACEHOLDER" ? ' · “鱼种名称”仅是视觉占位' : '') + '</dd></div>' +
        '<div><dt>来源身份</dt><dd>' + esc(photoStateText(frame)) + '</dd></div></dl>' +
        '<p class="swos-frame-note">' + esc(frame.note || "该帧不新增业务状态、不触发模型逻辑。").replaceAll("\n", "<br>") + '</p>' +
        '<div class="swos-strength-bars"><span>水光</span><i><b style="width:' + frame.ambient_water_strength + '%"></b></i><span>' + frame.ambient_water_strength + '</span><span>鱼体金光</span><i><b style="width:' + frame.fish_gold_strength + '%"></b></i><span>' + frame.fish_gold_strength + '</span></div>' +
        '<div class="swos-inspector-actions"><button type="button" data-open-selected>放大这一帧</button><button type="button" data-toggle-mode>' + (state.mode === "composite" ? "查看原图对照" : "查看光效合成") + '</button></div>';
      workspace.querySelector("[data-open-selected]").addEventListener("click", () => openFrame(frame));
      workspace.querySelector("[data-toggle-mode]").addEventListener("click", () => setMode(state.mode === "composite" ? "original" : "composite"));
    }

    function openFrame(frame) {
      state.selected = frame;
      state.mode = "composite";
      renderSelected();
      workspace.querySelector("[data-zoom-title]").textContent = frame.id + " · " + frame.label_zh;
      workspace.querySelector("[data-zoom-caption]").textContent = frame.global_nominal_ms + "ms 全局 / " + frame.relative_ms + "ms " + frame.track;
      workspace.querySelector("[data-zoom-screen]").innerHTML = phone(frame, { large: true, scale: 1 });
      const dialog = workspace.querySelector("[data-dialog]");
      dialog.showModal();
      setMode("composite");
    }

    renderStoryboard();
    renderSelected();
    const input = workspace.querySelector(".swos-photo-input");
    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      if (!file) return;
      if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
        workspace.querySelector(".swos-photo-meta").textContent = "只接受 JPG、PNG 或 WebP。";
        return;
      }
      if (state.photoUrl) URL.revokeObjectURL(state.photoUrl);
      state.photoUrl = URL.createObjectURL(file);
      state.photoHash = await crypto.subtle.digest("SHA-256", await file.arrayBuffer()).then(buffer =>
        Array.from(new Uint8Array(buffer), byte => byte.toString(16).padStart(2, "0")).join("")
      );
      state.photoName = file.name;
      const probe = new Image();
      probe.onload = () => {
        state.photoWidth = probe.naturalWidth;
        state.photoHeight = probe.naturalHeight;
        workspace.querySelector(".swos-photo-meta").textContent = file.name + " · " + state.photoWidth + "×" + state.photoHeight + " · SHA-256 " + state.photoHash + " · 仅浏览器内存";
        renderStoryboard();
        renderSelected();
      };
      probe.onerror = () => {
        workspace.querySelector(".swos-photo-meta").textContent = "图片解码失败。";
      };
      probe.src = state.photoUrl;
    });
    workspace.querySelectorAll("[data-mode]").forEach(button => button.addEventListener("click", () => setMode(button.dataset.mode)));
    workspace.querySelector("[data-close]").addEventListener("click", () => workspace.querySelector("[data-dialog]").close());
    workspace.querySelector("[data-dialog]").addEventListener("click", event => {
      if (event.target === event.currentTarget) event.currentTarget.close();
    });

    const selectedGeometryIds = ["P7", "R0", "R0.5", "R1"];
    workspace.querySelector("[data-geometry-table]").innerHTML = '<table><thead><tr><th>帧</th><th>全局时间</th><th>外层容器</th><th>同源照片前景</th><th>几何说明</th></tr></thead><tbody>' + selectedGeometryIds.map(id => {
      const frame = frameMap.get(id);
      const rects = displayRects(frame);
      return '<tr><th>' + id + '</th><td>' + frame.global_nominal_ms + 'ms</td><td>' + JSON.stringify(rects.outer) + '</td><td>' + JSON.stringify(rects.foreground) + '</td><td>' + (id === "R0.5" ? "受控中间插值；真实缩小、移位与边界变化" : id === "R1" ? "落入 322×210dp Frozen Hero" : "同一 Processing 照片视口") + '</td></tr>';
    }).join("") + '</tbody></table>';
    workspace.querySelector("[data-compare]").innerHTML = '<div class="swos-compare-card"><b>原始照片</b><div data-original-compare><span>选择本地照片后显示</span></div><small>按 EXIF 方向解码；不裁切源文件用于身份比较</small></div><div class="swos-compare-card"><b>P4 · 光效合成</b><div data-composite-compare>' + phone(frameMap.get("P4")) + '</div><small>水光与人工轮廓仅为静态审阅示意；不是实时模型输出</small></div>';

    const geometryCompare = workspace.querySelector("[data-original-compare]");
    const previousInput = input;
    previousInput.addEventListener("change", () => {
      if (!state.photoUrl) return;
      geometryCompare.innerHTML = '<img src="' + esc(state.photoUrl) + '" alt="原始照片" class="swos-original-compare-image">';
      workspace.querySelector("[data-composite-compare]").innerHTML = phone(frameMap.get("P4"));
    });
    workspace.querySelector("[data-geometry-table]").setAttribute("data-contract-frame-count", String(contract.frame_count));
    workspace.querySelector("[data-storyboard]").setAttribute("data-expected-frames", String(contract.frame_count));
    workspace.dataset.frameCount = String(contract.keyframes.length);
    workspace.dataset.duration = String(contract.total_nominal_duration_ms);
  }

  window.SWOSStaticVisual = { canvas, mount };
})();
