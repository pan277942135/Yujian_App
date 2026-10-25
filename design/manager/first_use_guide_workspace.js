/* First-use Camera Guide V1 pre-development workspaces.  No runtime binding. */
(() => {
  const GUIDE_ROOT = "design/pages/recognition/first_use_camera_guide/";
  const PAGE_FILES = {
    p01: "frozen/P01_First_Use_Camera_Guide_Frozen.png",
    p02: "frozen/P02_First_Use_Camera_Guide_Frozen.png",
    p03: "frozen/P03_First_Use_Camera_Guide_Frozen.png"
  };
  const PRESETS = [
    ["320 × 480", 320, 480], ["360 × 640", 360, 640], ["390 × 844", 390, 844],
    ["412 × 915", 412, 915], ["430 × 932", 430, 932], ["480 × 960", 480, 960],
    ["640 × 360", 640, 360], ["844 × 390", 844, 390], ["915 × 412", 915, 412]
  ];
  const ROLE_LABELS = {
    correct_hero: "正确示范主图",
    fish_head_out_of_frame: "鱼头不在画面",
    fish_too_small: "鱼体过小",
    excessive_occlusion: "遮挡过多"
  };
  const WORKSPACES = [
    ["structure", "结构化高保真"], ["responsive", "响应式设计"], ["resources", "资源"]
  ];

  const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
  const formatBytes = value => Number(value || 0).toLocaleString("en-US");
  const textStatus = value => String(value || "NOT_RECORDED").replaceAll("_", " ");
  const absoluteRepoUrl = path => {
    const siteDesignRoot = new URL("../", window.location.href);
    const clean = String(path || "").replace(/^design\//, "");
    const url = new URL(clean, siteDesignRoot);
    const build = document.querySelector('meta[name="design-manager-build"]')?.content;
    if (build) url.searchParams.set("v", build);
    return url.href;
  };
  const readJson = async path => {
    const response = await fetch(absoluteRepoUrl(path), { cache: "no-store" });
    if (!response.ok) throw new Error(`${response.status} ${path}`);
    return response.json();
  };

  function pageHeading(page) {
    return `<div class="guide-workspace-page-head">
      <div><span class="guide-workspace-kicker">FIRST-USE CAMERA GUIDE · ${escapeHtml(page.id.toUpperCase())}</span><strong>${escapeHtml(page.title)}</strong></div>
      <div class="guide-page-picker" role="group" aria-label="选择教学页面">${["p01", "p02", "p03"].map(id =>
        `<button type="button" data-guide-page="${id}" aria-pressed="${page.id === id}">${id.toUpperCase()}</button>`
      ).join("")}</div>
    </div>`;
  }

  function manifestLinks(root) {
    const paths = [
      ["冻结页面图 Manifest", root.dataset.pageManifest],
      ["独立素材 Manifest", root.dataset.assetManifest],
      ["结构化页面合同", root.dataset.structuredManifest],
      ["像素测量", root.dataset.geometryManifest],
      ["响应式合同", root.dataset.responsiveContract],
      ["Readiness Matrix", GUIDE_ROOT + "PREDEV_READINESS_MATRIX.json"],
      ["Freeze Decision", GUIDE_ROOT + "Freeze_Decision.md"]
    ];
    return `<div class="guide-workspace-authorities" aria-label="可审计合同">${paths.filter(row => row[1]).map(([label, path]) =>
      `<a href="${escapeHtml(absoluteRepoUrl(path))}" target="_blank" rel="noreferrer">${escapeHtml(label)} ↗</a>`
    ).join("")}</div>`;
  }

  function layerDepth(node, byId) {
    let depth = 0;
    let parent = node.parent_id;
    while (parent && byId.has(parent) && depth < 8) {
      depth += 1;
      parent = byId.get(parent).parent_id;
    }
    return depth;
  }

  function structureWorkspace(root, page, geometry, frozenAsset, activeLayer) {
    const nodes = page.nodes || [];
    const byId = new Map(nodes.map(node => [node.id, node]));
    const safeActive = byId.has(activeLayer) ? activeLayer : "guide.hero";
    const selected = byId.get(safeActive) || nodes[0];
    const components = geometry.pages[page.id]?.components || {};
    const measured = Object.entries(components).filter(([id, item]) => !["guide.background", "guide.overlay"].includes(id) && item.bbox_px && item.measurement_status === "MEASURED_FROM_FROZEN_RASTER");
    const tree = nodes.map(node => {
      const hasChildren = (node.children || []).length > 0;
      return `<button type="button" class="guide-layer-row${node.id === safeActive ? " is-selected" : ""}${hasChildren ? " has-children" : ""}" data-guide-layer="${escapeHtml(node.id)}" style="--layer-depth:${layerDepth(node, byId)}" aria-pressed="${node.id === safeActive}">
        <span class="guide-layer-glyph" aria-hidden="true">${hasChildren ? "▾" : "·"}</span><span class="guide-layer-id">${escapeHtml(node.id)}</span><span class="guide-layer-visibility">${escapeHtml(node.visible_condition || "—")}</span>
      </button>`;
    }).join("");
    const selectedGeometry = selected.geometry_px;
    const selectedRect = selectedGeometry ? `<div class="guide-geometry-box is-active" style="left:${(selectedGeometry.x / page.source_dimensions_px.width) * 100}%;top:${(selectedGeometry.y / page.source_dimensions_px.height) * 100}%;width:${(selectedGeometry.width / page.source_dimensions_px.width) * 100}%;height:${(selectedGeometry.height / page.source_dimensions_px.height) * 100}%" aria-label="${escapeHtml(selected.id)} measured bounds"></div>` : "";
    const allRects = measured.map(([id, item]) => {
      if (id === safeActive) return "";
      const box = item.bbox_px;
      return `<div class="guide-geometry-box" data-guide-geometry-id="${escapeHtml(id)}" style="left:${(box.x / page.source_dimensions_px.width) * 100}%;top:${(box.y / page.source_dimensions_px.height) * 100}%;width:${(box.width / page.source_dimensions_px.width) * 100}%;height:${(box.height / page.source_dimensions_px.height) * 100}%"></div>`;
    }).join("");
    const bbox = selected.geometry_px || null;
    const normalized = selected.geometry_normalized || null;
    const fields = [
      ["父节点", selected.parent_id || "根"], ["子节点", (selected.children || []).join(" · ") || "无"],
      ["可见条件", selected.visible_condition], ["几何状态", selected.geometry_status],
      ["图层来源", selected.layer_separation_status], ["共享组件", selected.shared_component_ref || "页面专属 / 未映射"],
      ["资源绑定", selected.asset_binding || "无"], ["动作合同", selected.action || "无"],
      ["候选视觉顺序", selected.z_index_visual_order_candidate ?? "—"], ["z-index 证据", selected.z_index_status || "NOT_RECOVERABLE"],
      ["测量方法", selected.measurement_evidence?.method || "—"]
    ];
    const normalizedText = normalized ? `x ${normalized.x} · y ${normalized.y} · w ${normalized.width} · h ${normalized.height}` : "未显示 / 未测得";
    const pxText = bbox ? `x ${bbox.x} · y ${bbox.y} · w ${bbox.width} · h ${bbox.height} px` : "未显示 / 未测得";
    const styleRows = Object.entries(selected.style_contract || {}).map(([key, value]) => {
      const display = value && Object.prototype.hasOwnProperty.call(value, "value") ? (value.value ?? "未恢复") : "未恢复";
      return `<div><dt>${escapeHtml(key)}</dt><dd>${escapeHtml(display)} <small>${escapeHtml(value?.status || "NOT_RECOVERABLE")}</small></dd></div>`;
    }).join("");
    const imagePath = frozenAsset?.path || PAGE_FILES[page.id];
    return `<section class="guide-structure-workspace" data-testid="guide-structure-workspace">
      ${pageHeading(page)}
      <div class="guide-workspace-boundary"><strong>冻结 PNG 像素测量 · 参考图层仍是合成图</strong><span>框线覆盖用于定位测量范围；图像本身没有拆层。拖动范围条比较原始 Frozen 画面与几何标注可见度。</span></div>
      <div class="guide-structure-columns">
        <aside class="guide-layer-panel"><div class="guide-panel-title"><strong>图层树</strong><span>${nodes.length} layers</span></div><div class="guide-layer-tree" role="tree" aria-label="${escapeHtml(page.title)} 图层">${tree}</div></aside>
        <div class="guide-canvas-panel"><div class="guide-panel-title"><strong>Frozen 画布与测量框</strong><span>${page.source_dimensions_px.width} × ${page.source_dimensions_px.height} px</span></div>
          <div class="guide-artboard-scroll"><div class="guide-artboard" data-testid="guide-structure-canvas" style="aspect-ratio:${page.source_dimensions_px.width}/${page.source_dimensions_px.height}">
            <img src="${escapeHtml(absoluteRepoUrl(imagePath))}" alt="${escapeHtml(page.title)} 原始 Frozen 页面" loading="eager" decoding="async">
            <div class="guide-geometry-overlay" style="--geometry-overlay-opacity:.74">${allRects}</div><div class="guide-geometry-selected">${selectedRect}</div>
          </div></div>
          <label class="guide-compare-control">冻结图 / 测量框叠加<input aria-label="冻结图几何标注叠加强度" type="range" min="0" max="100" value="74" data-guide-compare><output data-guide-compare-value>74%</output></label>
          <div class="guide-canvas-note">${escapeHtml(page.coordinate_system)} · 叠加框源自 geometry_measurements.json。</div>
        </div>
        <aside class="guide-inspector-panel" data-testid="guide-layer-inspector"><div class="guide-panel-title"><strong>属性检查</strong><span>${escapeHtml(selected.id)}</span></div>
          <h4>${escapeHtml(selected.text || selected.label || selected.id)}</h4>
          <div class="guide-inspector-measure"><span>Native 像素边界</span><strong>${escapeHtml(pxText)}</strong><span>归一化边界</span><strong>${escapeHtml(normalizedText)}</strong></div>
          <dl class="guide-inspector-list">${fields.map(([label, value]) => `<div><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value || "未规定")}</dd></div>`).join("")}</dl>
          <div class="guide-style-contract"><strong>样式可恢复性</strong><dl>${styleRows}</dl></div>
        </aside>
      </div>
      ${manifestLinks(root)}
    </section>`;
  }

  function imageByRole(assets, pageId, role) {
    return assets.find(asset => asset.page_refs?.includes(pageId) && asset.role === role);
  }

  function responsiveMarkup(root, page, assets, viewport, fontScale, insets) {
    const copy = page.page_copy;
    const hero = imageByRole(assets, page.id, "correct_hero");
    const errors = ["fish_head_out_of_frame", "fish_too_small", "excessive_occlusion"].map(role => imageByRole(assets, page.id, role));
    const scale = Number(fontScale) || 1;
    const cssVars = `--guide-base-font:${14 * scale}px;--guide-small-font:${12 * scale}px;--guide-title-font:${22 * scale}px;--guide-viewport-h:${viewport.height}px;--guide-inset-top:${Number(insets.top) || 0}px;--guide-inset-right:${Number(insets.right) || 0}px;--guide-inset-bottom:${Number(insets.bottom) || 0}px;--guide-inset-left:${Number(insets.left) || 0}px`;
    return `<section class="guide-responsive-workspace" data-testid="guide-responsive-workspace">
      ${pageHeading(page)}
      <div class="guide-responsive-toolbar">
        <div class="guide-preset-list" role="group" aria-label="响应式尺寸预设">${PRESETS.map(([, width, height]) =>
          `<button type="button" class="guide-preset-button${viewport.width === width && viewport.height === height ? " is-active" : ""}" data-guide-preset="${width}x${height}">${width}×${height}</button>`
        ).join("")}</div>
        <div class="guide-free-size"><label>宽 dp <input type="number" min="320" max="1200" value="${viewport.width}" data-guide-width></label><label>高 dp <input type="number" min="300" max="1600" value="${viewport.height}" data-guide-height></label></div>
        <label class="guide-font-scale">字号倍率 <select data-guide-font-scale><option value="1"${scale === 1 ? " selected" : ""}>1.0×</option><option value="1.3"${scale === 1.3 ? " selected" : ""}>1.3×</option></select></label>
        <div class="guide-inset-controls"><strong>Insets dp</strong><label>上 <input type="number" min="0" max="96" value="${Number(insets.top) || 0}" data-guide-inset="top"></label><label>右 <input type="number" min="0" max="96" value="${Number(insets.right) || 0}" data-guide-inset="right"></label><label>下 <input type="number" min="0" max="96" value="${Number(insets.bottom) || 0}" data-guide-inset="bottom"></label><label>左 <input type="number" min="0" max="96" value="${Number(insets.left) || 0}" data-guide-inset="left"></label></div>
      </div>
      <div class="guide-responsive-boundary"><strong>Responsive proposal · not a screenshot scale</strong><span>P03 uses a horizontal capture instruction; the outer page remains in the selected viewport orientation. All numbers marked candidate in responsive_contract.json await visual tuning review.</span></div>
      <div class="guide-simulator-scroll"><div class="guide-simulator-viewport" data-testid="guide-simulator-viewport" data-guide-viewport-width="${viewport.width}" data-guide-viewport-height="${viewport.height}" style="width:${viewport.width}px;height:${viewport.height}px;${cssVars}">
        <main class="guide-sim-page" data-guide-page-content="${page.id}">
          <header class="guide-sim-header"><button type="button" class="guide-sim-back" aria-label="返回">‹</button>${copy.masthead ? `<div class="guide-sim-masthead"><strong>${escapeHtml(copy.masthead)}</strong><span>${escapeHtml(copy.tagline)}</span></div>` : `<span class="guide-sim-header-spacer"></span>`}<span class="guide-sim-capture-direction">${page.id === "p03" ? "横向拍摄" : "竖向拍摄"}</span></header>
          <div class="guide-sim-hero" data-testid="guide-sim-hero">${hero ? `<img src="${escapeHtml(absoluteRepoUrl(hero.source_path))}" alt="候选 ${escapeHtml(ROLE_LABELS[hero.role])}" data-fish-orientation="${escapeHtml(hero.fish_orientation)}" loading="eager" decoding="async">` : `<div class="guide-sim-missing">正确示范独立照片缺失</div>`}<span class="guide-candidate-watermark">CANDIDATE · 仅供开发预览</span></div>
          <section class="guide-sim-intro"><span class="guide-sim-step">${escapeHtml(copy.step)}</span><h2 class="guide-sim-title guide-sim-text">${escapeHtml(copy.title)}</h2><p class="guide-sim-copy guide-sim-text">${escapeHtml(copy.instruction)}</p></section>
          <section class="guide-sim-examples" aria-label="三个错误示范">${errors.map(asset => `<article class="guide-sim-example">${asset ? `<img src="${escapeHtml(absoluteRepoUrl(asset.source_path))}" alt="候选 ${escapeHtml(ROLE_LABELS[asset.role])}" data-fish-orientation="${escapeHtml(asset.fish_orientation)}" loading="eager" decoding="async">` : `<div class="guide-sim-missing">独立示范图片缺失</div>`}<span>${escapeHtml(asset ? `避免 · ${ROLE_LABELS[asset.role]}` : "独立照片缺失")}</span><small>CANDIDATE</small></article>`).join("")}</section>
          <p class="guide-sim-completion guide-sim-text">${escapeHtml(page.nodes.find(node => node.id === "guide.completion_hint")?.text || "提示文案未登记")}</p>
          <div class="guide-sim-pagination" aria-label="教学页码">${[1,2,3].map(index => `<span class="${index === page.sequence_index ? "is-current" : ""}" aria-label="第 ${index} 页"></span>`).join("")}</div>
          <footer class="guide-sim-actions"><button type="button" class="guide-sim-skip">跳过</button>${copy.previous ? `<button type="button" class="guide-sim-previous">${escapeHtml(copy.previous)}</button>` : ""}<button type="button" class="guide-sim-primary">${escapeHtml(copy.primary)}</button></footer>
        </main>
      </div></div>
      <div class="guide-responsive-test-notes"><span>候选图片均使用 contain，保持原方向与比例</span><span>短屏内容向下滚动，操作栏在正文之后</span><span>字体、间距和 Hero 高度的调优值待一次性视觉审议</span></div>
      ${manifestLinks(root)}
    </section>`;
  }

  function resourcesWorkspace(root, page, pageManifest, assetManifest) {
    const frozenCards = (pageManifest.assets || []).map(asset => {
      const path = asset.path || asset.visual_authority;
      return `<article class="guide-frozen-resource guide-resource-card" data-resource-id="${escapeHtml(asset.id)}">
        <div><strong>${escapeHtml(asset.id.toUpperCase())} · Frozen 页面图</strong><span>FROZEN_PAGE_REFERENCE</span></div>
        <a href="${escapeHtml(absoluteRepoUrl(path))}" target="_blank" rel="noreferrer"><img src="${escapeHtml(absoluteRepoUrl(path))}" alt="${escapeHtml(asset.title || asset.id)} Frozen 原始页面" loading="lazy" decoding="async"></a>
        <dl><div><dt>像素</dt><dd>${asset.width} × ${asset.height}</dd></div><div><dt>bytes</dt><dd>${formatBytes(asset.bytes)}</dd></div><div><dt>SHA-256</dt><dd>${escapeHtml(asset.sha256)}</dd></div><div><dt>源文件名</dt><dd>${escapeHtml(asset.source_filename || asset.filename)}</dd></div></dl>
        <a class="guide-original-link" href="${escapeHtml(absoluteRepoUrl(path))}" download="${escapeHtml((path || "").split("/").pop())}">下载 Frozen 原图 ↗</a>
      </article>`;
    }).join("");
    const assets = assetManifest.assets || [];
    const candidateCards = assets.map(asset => {
      const references = asset.reference_package_asset;
      const href = absoluteRepoUrl(asset.source_path);
      return `<article class="guide-asset-slot" data-asset-id="${escapeHtml(asset.id)}" data-authority="${escapeHtml(asset.authority_state)}" data-source-path="${escapeHtml(asset.source_path)}">
        <div class="guide-asset-card-head"><strong>${escapeHtml(asset.page_refs?.map(id => id.toUpperCase()).join(" / ") || asset.page_id.toUpperCase())} · ${escapeHtml(asset.label || ROLE_LABELS[asset.role] || asset.role)}</strong><span class="guide-candidate-state">CANDIDATE_NEEDS_APPROVAL</span></div>
        <a class="guide-candidate-preview" href="${escapeHtml(href)}" target="_blank" rel="noreferrer"><img src="${escapeHtml(href)}" alt="${escapeHtml(asset.page_refs?.join(" / ") || asset.page_id)} ${escapeHtml(ROLE_LABELS[asset.role] || asset.role)} 候选原图" loading="lazy" decoding="async"></a>
        <dl class="guide-resource-metadata"><div><dt>资产 ID</dt><dd>${escapeHtml(asset.id)}</dd></div><div><dt>角色 / 鱼体</dt><dd>${escapeHtml(asset.role)} · ${asset.fish_orientation === "vertical" ? "竖向" : "横向"}</dd></div><div><dt>前端槽位绑定</dt><dd>${escapeHtml((asset.page_refs || [asset.page_id]).map(id => `${id}_${asset.role}`).join(" · "))}</dd></div><div><dt>候选原文件</dt><dd>${escapeHtml(asset.source_filename)}</dd></div><div><dt>仓库路径</dt><dd>${escapeHtml(asset.source_path)}</dd></div><div><dt>类型 / 尺寸</dt><dd>${escapeHtml(asset.mime_type)} · ${asset.width} × ${asset.height} px</dd></div><div><dt>文件大小</dt><dd>${formatBytes(asset.bytes)} bytes</dd></div><div><dt>SHA-256</dt><dd>${escapeHtml(asset.sha256)}</dd></div><div><dt>原图状态</dt><dd>ASSET_SOURCE_BLOCKED · 尚无获批独立原图</dd></div><div><dt>许可 / 隐私</dt><dd>${escapeHtml(asset.license_status)} · ${escapeHtml(asset.privacy_status)}</dd></div><div><dt>可否生产使用</dt><dd>否 · approved_for_frontend=false · approved_for_runtime=false</dd></div></dl>
        ${references ? `<div class="guide-reference-only"><strong>归档对照 · REFERENCE_ONLY</strong><span>${escapeHtml(references.internal_path)} · SHA-256 ${escapeHtml(references.sha256)} · ${formatBytes(references.bytes)} bytes</span><small>复合截图衍生裁切；不作为候选原图，不链接为可用源文件。</small></div>` : ""}
        <div class="guide-resource-actions"><a href="${escapeHtml(href)}" download="${escapeHtml(asset.source_filename)}">下载候选原图 ↗</a><a href="${escapeHtml(href)}" target="_blank" rel="noreferrer">打开独立原图 ↗</a></div>
      </article>`;
    }).join("");
    return `<section class="guide-resources-workspace" data-testid="guide-resources-workspace">
      ${pageHeading(page)}
      <div class="guide-resource-summary"><strong>${assetManifest.counts?.unique_candidate_files || 8} 张独立候选源图 → ${assets.length} 个页面/角色绑定</strong><span>12 个原图来源槽位仍待补齐；候选仅用于布局和开发讨论。禁止视为 Frozen、获批、授权或生产可用。</span></div>
      <h4 class="guide-resource-section-title">前端独立素材 · 12 个角色槽位</h4><div class="guide-candidate-grid">${candidateCards}</div>
      <details class="guide-reference-archive"><summary>归档对照裁切 · 9 项 REFERENCE_ONLY（不可作独立生产照片）</summary><p>这些归档条目来自扁平合成页面；为避免将复合截图裁切误作原片，本工作区只显示来源记录，不把它们作为可下载照片。</p><ul>${assets.filter(asset => asset.reference_package_asset).map(asset => `<li>${escapeHtml(asset.id)} — ${escapeHtml(asset.reference_package_asset.internal_path)} · ${escapeHtml(asset.reference_package_asset.sha256)}</li>`).join("")}</ul></details>
      <h4 class="guide-resource-section-title">页面高保真资源 · 原图字节保持不变</h4><div class="guide-frozen-resource-grid">${frozenCards}</div>
      ${manifestLinks(root)}
    </section>`;
  }

  function mount(root) {
    if (!root) return;
    const state = {
      workspace: new URLSearchParams(window.location.search).get("guide_workspace") || "structure",
      pageId: new URLSearchParams(window.location.search).get("guide_page") || "p01",
      activeLayer: "guide.hero",
      viewport: { width: 390, height: 844 },
      fontScale: 1,
      insets: { top: 24, right: 0, bottom: 24, left: 0 },
      data: null
    };
    const setQuery = () => {
      const url = new URL(window.location.href);
      url.searchParams.set("guide_workspace", state.workspace);
      url.searchParams.set("guide_page", state.pageId);
      window.history.replaceState(null, "", url);
    };
    const activePage = () => state.data.pages.find(page => page.id === state.pageId) || state.data.pages[0];
    const render = () => {
      if (!state.data) return;
      const page = activePage();
      const pageAsset = state.data.pageManifest.assets.find(asset => asset.id === page.id);
      const pageName = page.title;
      const tabMarkup = WORKSPACES.map(([id, title]) => `<button type="button" data-guide-tab="${id}" aria-current="${state.workspace === id ? "page" : "false"}">${title}</button>`).join("");
      let workspaceContent = "";
      if (state.workspace === "structure") workspaceContent = structureWorkspace(root, page, state.data.geometry, pageAsset, state.activeLayer);
      else if (state.workspace === "responsive") workspaceContent = responsiveMarkup(root, page, state.data.assetManifest.assets, state.viewport, state.fontScale, state.insets);
      else workspaceContent = resourcesWorkspace(root, page, state.data.pageManifest, state.data.assetManifest);
      root.innerHTML = `<div class="guide-workspace" data-workspace="${escapeHtml(state.workspace)}" data-current-page="${escapeHtml(page.id)}">
        <header class="guide-workspace-header"><div><span class="guide-workspace-kicker">PRE-DEV CONTRACT · ${escapeHtml(pageName)}</span><h3>首次拍照教学开发工作区</h3><p>保留三页冻结视觉权威；结构、响应式与独立候选素材使用同一资源 Manifest。</p></div><div class="guide-workspace-status"><strong>部分冻结</strong><span>候选照片未获生产批准</span></div></header>
        <nav class="guide-workspace-tabs" aria-label="首次拍照教学工作区">${tabMarkup}</nav>
        <div class="guide-workspace-body">${workspaceContent}</div>
      </div>`;
      const overlay = root.querySelector(".guide-geometry-overlay");
      const range = root.querySelector("[data-guide-compare]");
      if (overlay && range) overlay.style.setProperty("--geometry-overlay-opacity", String(Number(range.value) / 100));
    };

    root.addEventListener("click", event => {
      const tab = event.target.closest("[data-guide-tab]");
      const pageButton = event.target.closest("[data-guide-page]");
      const layerButton = event.target.closest("[data-guide-layer]");
      const presetButton = event.target.closest("[data-guide-preset]");
      if (tab) { state.workspace = tab.dataset.guideTab; setQuery(); render(); }
      else if (pageButton) { state.pageId = pageButton.dataset.guidePage; state.activeLayer = "guide.hero"; setQuery(); render(); }
      else if (layerButton) { state.activeLayer = layerButton.dataset.guideLayer; render(); }
      else if (presetButton) {
        const [width, height] = presetButton.dataset.guidePreset.split("x").map(Number);
        state.viewport = { width, height };
        render();
      }
    });
    root.addEventListener("input", event => {
      const range = event.target.closest("[data-guide-compare]");
      if (range) {
        const overlay = root.querySelector(".guide-geometry-overlay");
        if (overlay) overlay.style.setProperty("--geometry-overlay-opacity", String(Number(range.value) / 100));
        const output = root.querySelector("[data-guide-compare-value]");
        if (output) output.textContent = `${range.value}%`;
      }
    });
    root.addEventListener("change", event => {
      const widthInput = event.target.closest("[data-guide-width]");
      const heightInput = event.target.closest("[data-guide-height]");
      const insetInput = event.target.closest("[data-guide-inset]");
      const scaleInput = event.target.closest("[data-guide-font-scale]");
      if (!(widthInput || heightInput || insetInput || scaleInput)) return;
      if (widthInput) state.viewport.width = Math.max(320, Number(widthInput.value) || 320);
      if (heightInput) state.viewport.height = Math.max(300, Number(heightInput.value) || 300);
      if (insetInput) state.insets[insetInput.dataset.guideInset] = Math.max(0, Number(insetInput.value) || 0);
      if (scaleInput) state.fontScale = Number(scaleInput.value) || 1;
      render();
    });

    root.innerHTML = `<div class="guide-workspace-loading" role="status">正在读取首次拍照教学结构与素材清单…</div>`;
    Promise.all([
      readJson(root.dataset.structuredManifest),
      readJson(root.dataset.geometryManifest),
      readJson(root.dataset.responsiveContract),
      readJson(root.dataset.assetManifest),
      readJson(root.dataset.pageManifest)
    ]).then(([structure, geometry, responsive, assetManifest, pageManifest]) => {
      state.data = { pages: structure.pages, geometry, responsive, assetManifest, pageManifest };
      if (!state.data.pages.some(page => page.id === state.pageId)) state.pageId = "p01";
      state.viewport = { width: 390, height: 844 };
      render();
    }).catch(error => {
      root.innerHTML = `<div class="guide-workspace-error" role="alert"><strong>工作区合同加载失败</strong><span>${escapeHtml(error.message)}</span><p>请检查 manifest 地址和当前 Design Manager 构建 SHA。</p></div>`;
    });
  }

  window.FirstUseGuideWorkspace = { mount };
})();
