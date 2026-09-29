const PAGE_REGISTRY_URL = "../registry/experience_registry_v1.json";
const SHARED_REGISTRY_URL = "../registry/shared_design_system_v1.json";
const DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"];

const ICONS = {
  FROZEN: "✓", ACTIVE_CLOSURE: "◐", PARTIAL: "◒", MISSING: "×",
  RUNTIME_ONLY: "↗", DESIGN_ONLY: "◇", DEPRECATED: "—", CANDIDATE: "◐"
};

const STATUS_LABELS = {
  FROZEN: "已冻结", ACTIVE_CLOSURE: "收口中", PARTIAL: "部分完成",
  MISSING: "缺失", RUNTIME_ONLY: "仅运行时", DESIGN_ONLY: "仅设计",
  DEPRECATED: "已废弃", CANDIDATE: "候选"
};

const MODALITY_LABELS = {
  behavior: "行为", visual: "视觉", motion: "动效",
  haptic: "震动", sound: "声音", assets: "资产"
};

const CATEGORY_LABELS = { foundation: "基础系统", component: "公共组件" };

let pageRegistry = null;
let sharedRegistry = null;
let backgroundContract = null;
let backgroundUsage = null;
let selectedKey = null;

const el = id => document.getElementById(id);
const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
})[c]);

function statusText(status) { return STATUS_LABELS[status] || status || STATUS_LABELS.MISSING; }

function statusBadge(status) {
  const safe = status || "MISSING";
  return '<span class="badge status-' + esc(safe) + '">' +
    (ICONS[safe] || "•") + ' ' + esc(statusText(safe)) + '</span>';
}

function repoHref(path) { return path ? "../../" + path : "#"; }
function isImage(path) { return !!path && /\.(png|jpe?g|webp|gif)$/i.test(path); }

function currentVersion(feature) {
  return (feature.design_versions || []).find(v => v.current) ||
    (feature.design_versions || [])[0] || null;
}

function allStatuses() {
  return [...new Set([
    ...sharedRegistry.items.map(x => x.overall),
    ...pageRegistry.features.map(x => x.design_overall)
  ])].sort();
}

function renderSummary() {
  el("summary").innerHTML =
    '<span class="summary-chip"><b>' + sharedRegistry.items.length + '</b> 个公共系统</span>' +
    '<span class="summary-chip"><b>' + pageRegistry.features.length + '</b> 个页面</span>';
}

function fillStatusFilter() {
  el("statusFilter").innerHTML = '<option value="">全部状态</option>' +
    allStatuses().map(v => '<option value="' + esc(v) + '">' + esc(statusText(v)) + '</option>').join("");
}

function matchesSearch(item) {
  const q = el("searchInput").value.trim().toLowerCase();
  return !q || JSON.stringify(item).toLowerCase().includes(q);
}

function matchesStatus(status) {
  const selected = el("statusFilter").value;
  return !selected || selected === status;
}

function renderLists() {
  const shared = sharedRegistry.items.filter(x => matchesSearch(x) && matchesStatus(x.overall));
  const pages = pageRegistry.features.filter(x => matchesSearch(x) && matchesStatus(x.design_overall));

  el("sharedCount").textContent = shared.length;
  el("pageCount").textContent = pages.length;

  el("sharedList").innerHTML = shared.map(item => {
    const key = "shared/" + item.id;
    const parent =
      '<button class="module-item shared-item' + (selectedKey === key ? " active" : "") +
      '" data-kind="shared" data-id="' + esc(item.id) + '">' +
      '<div class="module-name"><span>' + esc(item.display_name) + '</span>' +
      statusBadge(item.overall) + '</div>' +
      '<div class="module-path">' + esc(CATEGORY_LABELS[item.category] || item.category) +
      ' · ' + esc(item.current_version) + '</div></button>';

    if (item.id !== "background_system_v1") return parent;

    const children = (item.variants || []).map(v => {
      const variantKey = "shared/" + item.id + "/" + v.id;
      return '<button class="shared-subitem' + (selectedKey === variantKey ? " active" : "") +
        '" data-kind="background-variant" data-id="' + esc(v.id) + '">' +
        '<span class="subitem-code">' + esc(v.id) + '</span>' +
        '<span class="subitem-name">' + esc(v.name) + '</span>' +
        statusBadge(v.status || item.overall) +
        '</button>';
    }).join("");

    return parent + '<div class="shared-sublist">' + children + '</div>';
  }).join("") || '<div class="preview-empty compact">没有匹配的公共系统</div>';

  el("pageList").innerHTML = pages.map(feature => {
    const key = "page/" + feature.id;
    const parent = '<button class="module-item' + (selectedKey === key ? " active" : "") +
      '" data-kind="page" data-id="' + esc(feature.id) + '">' +
      '<div class="module-name"><span>' + esc(feature.display_name || feature.id) + '</span>' +
      statusBadge(feature.design_overall) + '</div>' +
      '<div class="module-path">' + esc(feature.owner_path) + '</div></button>';

    const children = (feature.hifi_views || []).map(view => {
      const viewKey = "page/" + feature.id + "/hifi/" + view.id;
      return '<button class="page-subitem' + (selectedKey === viewKey ? " active" : "") +
        '" data-kind="page-hifi" data-page-id="' + esc(feature.id) +
        '" data-hifi-id="' + esc(view.id) + '">' +
        '<span class="subitem-name">' + esc(view.title) + '</span>' +
        statusBadge(view.status || "PARTIAL") +
      '</button>';
    }).join("");

    return parent + (children ? '<div class="page-sublist">' + children + '</div>' : '');
  }).join("") || '<div class="preview-empty compact">没有匹配的页面</div>';

  document.querySelectorAll(".module-item[data-kind]").forEach(btn => {
    btn.addEventListener("click", () => {
      if (btn.dataset.kind === "shared") selectShared(btn.dataset.id);
      else selectPage(btn.dataset.id);
    });
  });

  document.querySelectorAll(".shared-subitem[data-kind='background-variant']").forEach(btn => {
    btn.addEventListener("click", () => selectShared("background_system_v1", btn.dataset.id));
  });

  document.querySelectorAll(".page-subitem[data-kind='page-hifi']").forEach(btn => {
    btn.addEventListener("click", () => selectPage(btn.dataset.pageId, null, btn.dataset.hifiId));
  });
}

function hideAllDetails() {
  el("emptyState").classList.add("hidden");
  el("sharedDetail").classList.add("hidden");
  el("pageDetail").classList.add("hidden");
}

function previewHtml(path, alt, note) {
  if (!path) return '<div class="preview-empty">尚未登记独立预览资产。</div>';
  if (!isImage(path)) {
    return '<div class="preview-empty">当前权威不是可直接预览的图片。<br><br>' +
      '<a href="' + esc(repoHref(path)) + '" target="_blank" rel="noreferrer">' +
      esc(path) + '</a></div>';
  }
  return '<div class="preview-image-wrap"><a href="' + esc(repoHref(path)) +
    '" target="_blank" rel="noreferrer"><img src="' + esc(repoHref(path)) +
    '" alt="' + esc(alt) + '"></a></div>' +
    (note ? '<div class="preview-note">' + esc(note) + '</div>' : '');
}

function layeredCapturePreviewHtml(item) {
  const layers = item.preview_layers || [];
  const byRole = Object.fromEntries(layers.map(layer => [layer.role, layer.path]));
  if (!byRole.base || !byRole.rim) {
    return '<div class="preview-empty">主拍摄按钮预览层不完整。</div>';
  }

  const stack = (withGlow) =>
    '<div class="capture-preview-stack">' +
      (withGlow && byRole.glow
        ? '<img class="capture-layer capture-glow" src="' + esc(repoHref(byRole.glow)) + '" alt="">'
        : '') +
      '<img class="capture-layer capture-base" src="' + esc(repoHref(byRole.base)) + '" alt="主拍摄按钮主体">' +
      '<img class="capture-layer capture-rim" src="' + esc(repoHref(byRole.rim)) + '" alt="暖金细边">' +
    '</div>';

  return '<div class="capture-preview-grid">' +
    '<div class="capture-preview-card">' +
      '<div class="capture-preview-label">静态态 · Base + Gold Rim</div>' +
      stack(false) +
    '</div>' +
    '<div class="capture-preview-card">' +
      '<div class="capture-preview-label">呼吸态 · Glow + Base + Gold Rim</div>' +
      stack(true) +
    '</div>' +
  '</div>' +
  (item.preview_note ? '<div class="preview-note">' + esc(item.preview_note) + '</div>' : '');
}

function sharedPreviewHtml(item) {
  if (item.preview_type === "layered_component" && item.id === "primary_capture_button_v1") {
    return layeredCapturePreviewHtml(item);
  }
  return previewHtml(item.preview, item.display_name, item.preview_note);
}

function pagesUsing(sharedId) {
  return pageRegistry.features.filter(f =>
    (f.shared_system_refs || []).some(ref => ref.id === sharedId)
  );
}

function backgroundVariantPreview(item, variant) {
  if (item.id !== "background_system_v1") return "";

  if (variant.preview_treatment) {
    const t = variant.preview_treatment;
    const filter = [
      "saturate(" + Math.round((t.saturation ?? 1) * 100) + "%)",
      "contrast(" + Math.round((t.contrast ?? 1) * 100) + "%)",
      "brightness(" + Math.round((t.brightness ?? 1) * 100) + "%)"
    ].join(" ");

    const sources = (variant.preview_sources && variant.preview_sources.length)
      ? variant.preview_sources
      : (item.master?.path ? [{ label: "公共母版", path: item.master.path }] : []);

    if (sources.length) {
      const cards = sources.map(source =>
        '<div class="bg-source-preview-card">' +
          '<div class="bg-source-label">' + esc(source.label || "母版") + '</div>' +
          '<div class="bg-variant-preview">' +
            '<img src="' + esc(repoHref(source.path)) + '" alt="' + esc(variant.name) +
            '" style="filter:' + esc(filter) + '">' +
            '<span class="bg-mist" style="opacity:' + Number(t.mist_alpha || 0) + '"></span>' +
          '</div>' +
        '</div>'
      ).join("");

      return '<div class="bg-source-preview-grid' + (sources.length > 1 ? " dual" : "") + '">' +
        cards + '</div>' +
        '<div class="bg-preview-caption">冻结参数 · 雾化 ' +
        Math.round(Number(t.mist_alpha || 0) * 100) + '% · 饱和度 ' +
        Math.round(Number(t.saturation || 1) * 100) + '% · 对比度 ' +
        Math.round(Number(t.contrast || 1) * 100) + '% · 亮度 ' +
        Math.round(Number(t.brightness || 1) * 100) + '%</div>';
    }
  }

  if (variant.preview_mode === "dynamic_photo") {
    return '<div class="bg-mode-preview dynamic-photo-preview">' +
      '<div class="dynamic-photo-icon">照片</div><div>用户当前照片作为背景</div></div>';
  }

  if (variant.preview_mode === "solid_fallback") {
    return '<div class="bg-mode-preview fallback-preview">' +
      '<span style="background:#F7FAFB"></span>' +
      '<span style="background:#102D35"></span></div>' +
      '<div class="bg-preview-caption">浅色内容兜底 / 深色拍摄兜底</div>';
  }

  return "";
}

function percentValue(value, signed = false) {
  const n = Number(value);
  const pct = Math.round(n * 100);
  return (signed && pct > 0 ? "+" : "") + pct + "%";
}

function formatContractValue(key, value) {
  const percentKeys = new Set([
    "mist_white_veil_alpha",
    "saturation_relative",
    "contrast_relative",
    "brightness_relative",
    "target_salience_vs_home"
  ]);

  if (percentKeys.has(key) && typeof value === "number") {
    return Math.round(value * 100) + "%";
  }

  if (Array.isArray(value) && value.length === 2) {
    return String(value[0]) + " ～ " + String(value[1]);
  }

  if (typeof value === "boolean") return value ? "是" : "否";

  if (value && typeof value === "object") {
    return Object.entries(value)
      .map(([k,v]) => k + " → " + v)
      .join("；");
  }

  const aliases = {
    morning_lake: "Morning Lake / 清晨湖景",
    runtime_photo: "用户实时照片",
    solid: "纯色",
    current_user_photo: "用户当前照片",
    crop: "Crop / 居中裁切",
    none: "禁止",
    FROZEN: "已冻结",
    transient_missing_background: "瞬时缺少背景",
    load_failure: "背景加载失败",
    error_fallback: "异常兜底"
  };
  return aliases[value] || String(value);
}

const PARAMETER_LABELS = {
  family: "背景家族",
  status: "状态",
  default_master: "默认母版",
  source_overrides: "页面母版例外",
  mist_white_veil_alpha: "雾白覆盖",
  saturation_relative: "相对饱和度",
  contrast_relative: "相对对比度",
  brightness_relative: "相对亮度",
  global_blur: "全局模糊",
  target_salience_vs_home: "相对 Hero 存在感",
  source: "背景来源",
  opaque: "原图不透明",
  content_scale: "图片填充",
  processing_tint: "原图染色",
  fallback: "无照片兜底",
  light: "浅色兜底",
  dark_capture: "深色拍摄兜底",
  permanent_page_background: "允许长期作为页面背景"
};

function backgroundVariantPages(variantId) {
  const mapped = Object.entries(backgroundUsage?.mappings || {})
    .filter(([, variant]) => variant === variantId)
    .map(([pageId]) => pageRegistry.features.find(f => f.id === pageId))
    .filter(Boolean);

  if (mapped.length) return mapped.map(page => ({
    type: "page",
    id: page.id,
    label: page.display_name || page.id
  }));

  const contractUse = backgroundContract?.variants?.[variantId]?.use || [];
  const scenarioNames = {
    transient_missing_background: "瞬时缺少背景",
    load_failure: "背景加载失败",
    error_fallback: "异常兜底"
  };
  return contractUse.map(id => {
    const page = pageRegistry.features.find(f => f.id === id);
    return page
      ? { type: "page", id: page.id, label: page.display_name || page.id }
      : { type: "scenario", id, label: scenarioNames[id] || id };
  });
}

function renderBackgroundVariantWorkspace(item, variantId) {
  const panel = el("backgroundVariantPanel");
  if (item.id !== "background_system_v1" || !variantId) {
    panel.classList.add("hidden");
    return;
  }

  const variant = (item.variants || []).find(v => v.id === variantId);
  const contract = backgroundContract?.variants?.[variantId];
  if (!variant || !contract) {
    panel.classList.add("hidden");
    return;
  }

  panel.classList.remove("hidden");
  el("backgroundVariantTitle").textContent = variant.id + " · " + variant.name;
  el("backgroundVariantStatus").innerHTML = statusBadge(variant.status || item.overall);
  el("backgroundVariantPreview").innerHTML = backgroundVariantPreview(item, variant) ||
    '<div class="preview-empty">暂无可视化预览。</div>';

  const parameterEntries = Object.entries(contract).filter(([key]) => key !== "use");
  el("backgroundParameterGrid").innerHTML = parameterEntries.map(([key,value]) =>
    '<div class="parameter-row">' +
      '<div class="parameter-label">' + esc(PARAMETER_LABELS[key] || key) + '</div>' +
      '<div class="parameter-value">' + esc(formatContractValue(key, value)) + '</div>' +
    '</div>'
  ).join("");

  const usage = backgroundVariantPages(variantId);
  el("backgroundUsageMap").innerHTML = usage.map(entry =>
    entry.type === "page"
      ? '<button class="usage-row background-page-link" data-page-id="' + esc(entry.id) + '">' +
          '<span>' + esc(entry.label) + '</span><span class="usage-variant">' + esc(variantId) + '</span></button>'
      : '<div class="usage-row static"><span>' + esc(entry.label) + '</span>' +
          '<span class="usage-variant">场景</span></div>'
  ).join("") || '<div class="preview-empty compact">尚未映射页面或场景。</div>';

  const authorityPaths = [
    variant.authority_path,
    item.contract_path,
    item.usage_map_path
  ].filter((value,index,array) => value && array.indexOf(value) === index);

  if (["BG_ENV_HERO","BG_CONTENT","BG_DATA"].includes(variantId)) {
    const sourcePaths = (variant.preview_sources || []).map(source => source.path).filter(Boolean);
    if (!sourcePaths.length && item.master?.path) sourcePaths.push(item.master.path);
    for (const path of sourcePaths.reverse()) {
      if (!authorityPaths.includes(path)) authorityPaths.unshift(path);
    }
  }

  el("backgroundVariantAuthority").innerHTML = authorityPaths.map(path =>
    '<a class="authority-row" href="' + esc(repoHref(path)) +
    '" target="_blank" rel="noreferrer">' + esc(path) + '</a>'
  ).join("");

  document.querySelectorAll(".background-page-link[data-page-id]").forEach(btn =>
    btn.addEventListener("click", () => selectPage(btn.dataset.pageId))
  );
}

function selectShared(id, variantId = null) {
  const item = sharedRegistry.items.find(x => x.id === id);
  if (!item) return;
  selectedKey = "shared/" + id + (variantId ? "/" + variantId : "");
  history.replaceState(
    null,
    "",
    "#shared/" + encodeURIComponent(id) + (variantId ? "/" + encodeURIComponent(variantId) : "")
  );
  renderLists();
  hideAllDetails();
  el("sharedDetail").classList.remove("hidden");

  el("pageEyebrow").textContent = "渔见 · 公共设计系统";
  el("pageTitle").textContent = item.display_name;
  el("pageStatus").innerHTML = statusBadge(item.overall);
  el("sharedName").textContent = item.display_name;
  el("sharedStatus").innerHTML = statusBadge(item.overall);
  const sharedMetaRows = [
    ["系统 ID", item.id],
    ["类型", CATEGORY_LABELS[item.category] || item.category],
    ["当前版本", item.current_version],
    ["使用页面", pagesUsing(item.id).length + " 个"]
  ];
  if (item.master) {
    sharedMetaRows.push(["默认母版尺寸", item.master.width + " × " + item.master.height]);
    sharedMetaRows.push(["默认母版 SHA", String(item.master.sha256 || "—").slice(0, 16) + "…"]);
  }
  if (item.masters?.length) {
    sharedMetaRows.push(["Canonical Masters", item.masters.length + " 张"]);
  }
  el("sharedMeta").innerHTML = sharedMetaRows.map(([label,value]) =>
    '<div class="meta-card"><div class="meta-label">' + esc(label) +
    '</div><div class="meta-value">' + esc(value) + '</div></div>'
  ).join("");

  if (item.note) {
    el("sharedNote").textContent = item.note;
    el("sharedNote").classList.remove("hidden");
  } else el("sharedNote").classList.add("hidden");

  el("sharedPreview").innerHTML = sharedPreviewHtml(item);

  el("variantGrid").innerHTML = (item.variants || []).map(v => {
    const selectable = item.id === "background_system_v1";
    const active = selectable && variantId === v.id ? " selected" : "";
    const tag = selectable ? "button" : "div";
    const attrs = selectable ? ' type="button" data-bg-variant="' + esc(v.id) + '"' : "";
    return '<' + tag + ' class="variant-card' + (selectable ? " variant-selectable" : "") + active + '"' + attrs + '>' +
      backgroundVariantPreview(item, v) +
      '<div class="variant-id">' + esc(v.id) + '</div>' +
      '<div class="variant-name">' + esc(v.name) + '</div>' +
      '<div class="variant-usage">' + esc(v.usage || "") + '</div>' +
      '<div class="variant-card-status">' + statusBadge(v.status || item.overall) + '</div>' +
      (v.note ? '<div class="variant-note">' + esc(v.note) + '</div>' : '') +
      '</' + tag + '>';
  }).join("") || '<div class="preview-empty">尚未登记变体。</div>';

  document.querySelectorAll(".variant-selectable[data-bg-variant]").forEach(btn =>
    btn.addEventListener("click", () => selectShared("background_system_v1", btn.dataset.bgVariant))
  );

  renderBackgroundVariantWorkspace(item, variantId);

  el("sharedAuthorities").innerHTML = (item.authority_paths || []).map(path =>
    '<a class="authority-row" href="' + esc(repoHref(path)) +
    '" target="_blank" rel="noreferrer">' + esc(path) + '</a>'
  ).join("");

  el("sharedUsage").innerHTML = pagesUsing(item.id).map(page => {
    const ref = (page.shared_system_refs || []).find(x => x.id === item.id);
    return '<button class="usage-row" data-page-id="' + esc(page.id) + '">' +
      '<span>' + esc(page.display_name || page.id) + '</span>' +
      '<span class="usage-variant">' + esc(ref?.variant || "默认") + '</span></button>';
  }).join("") || '<div class="preview-empty compact">暂未被页面引用。</div>';

  document.querySelectorAll(".usage-row[data-page-id]").forEach(btn =>
    btn.addEventListener("click", () => selectPage(btn.dataset.pageId))
  );
}

function authLivePreviewHtml(feature) {
  const model = feature.live_preview;
  if (!model || model.type !== "auth_page") return "";

  const bgSystem = sharedRegistry.items.find(x => x.id === "background_system_v1");
  const bgVariant = (bgSystem?.variants || []).find(v => v.id === model.background_variant);
  const treatment = bgVariant?.preview_treatment || {};
  const source = (bgVariant?.preview_sources || [])[0]?.path || bgSystem?.master?.path;

  if (!source) return "";

  const filter = [
    "saturate(" + Math.round((treatment.saturation ?? 1) * 100) + "%)",
    "contrast(" + Math.round((treatment.contrast ?? 1) * 100) + "%)",
    "brightness(" + Math.round((treatment.brightness ?? 1) * 100) + "%)"
  ].join(" ");

  const fields = (model.fields || []).map(field =>
    '<div class="auth-preview-field-group">' +
      '<div class="auth-preview-label">' + esc(field.label) + '</div>' +
      '<div class="auth-preview-field">' +
        '<span class="auth-preview-icon">' + (field.icon === "lock" ? "●" : "○") + '</span>' +
        '<span class="auth-preview-placeholder">' + esc(field.placeholder) + '</span>' +
        (field.secure ? '<span class="auth-preview-eye">◌</span>' : '') +
      '</div>' +
    '</div>'
  ).join("");

  const secondary = model.secondary_action
    ? '<div class="auth-preview-secondary">' + esc(model.secondary_action) + '</div>'
    : '';

  const authority = feature.modalities?.visual?.authority;
  const authorityLink = authority
    ? '<a class="auth-preview-authority" href="' + esc(repoHref(authority)) +
      '" target="_blank" rel="noreferrer">当前设计 Authority · ' + esc(authority) + '</a>'
    : '';

  return '<div class="auth-live-preview-shell">' +
    '<div class="auth-live-preview-phone">' +
      '<img class="auth-preview-bg" src="' + esc(repoHref(source)) +
      '" alt="' + esc(feature.display_name || feature.id) + ' background" style="filter:' + esc(filter) + '">' +
      '<span class="auth-preview-mist" style="opacity:' + Number(treatment.mist_alpha || 0) + '"></span>' +
      '<div class="auth-preview-content">' +
        '<div class="auth-preview-brand">' + esc(model.eyebrow || "渔见") + '</div>' +
        '<div class="auth-preview-brand-subtitle">' + esc(model.brand_subtitle || "") + '</div>' +
        '<div class="auth-preview-spacer"></div>' +
        '<div class="auth-preview-title">' + esc(model.title || "") + '</div>' +
        '<div class="auth-preview-subtitle">' + esc(model.subtitle || "") + '</div>' +
        '<div class="auth-preview-fields">' + fields + '</div>' +
        secondary +
        '<div class="auth-preview-primary">' + esc(model.primary_action || "") + '</div>' +
        '<div class="auth-preview-footer"><span>' + esc(model.footer_prefix || "") +
          '</span><strong>' + esc(model.footer_action || "") + '</strong></div>' +
      '</div>' +
      '<div class="auth-preview-badge">' + esc(model.background_variant || "") + '</div>' +
    '</div>' +
    '<div class="auth-live-preview-meta">' +
      '<div><b>实时设计预览</b> · 使用无太阳 Morning Lake + ' + esc(model.background_variant || "") + '</div>' +
      '<div>雾化 ' + Math.round(Number(treatment.mist_alpha || 0) * 100) +
      '% · 饱和度 ' + Math.round(Number(treatment.saturation || 1) * 100) +
      '% · 对比度 ' + Math.round(Number(treatment.contrast || 1) * 100) +
      '% · 亮度 ' + Math.round(Number(treatment.brightness || 1) * 100) + '%</div>' +
      (model.note ? '<div class="auth-live-preview-note">' + esc(model.note) + '</div>' : '') +
      authorityLink +
    '</div>' +
  '</div>';
}

function renderPagePreview(feature) {
  if (feature.live_preview?.type === "auth_page") {
    el("visualPreview").innerHTML = authLivePreviewHtml(feature);
    return;
  }

  const version = currentVersion(feature);
  const visual = version?.visual_authority || feature.modalities?.visual?.authority;
  el("visualPreview").innerHTML = previewHtml(visual, feature.display_name || feature.id);
}

function renderSharedRefs(feature) {
  const refs = feature.shared_system_refs || [];
  el("sharedRefGrid").innerHTML = refs.map(ref => {
    const item = sharedRegistry.items.find(x => x.id === ref.id);
    if (!item) return "";
    return '<button class="shared-ref-card" data-shared-id="' + esc(item.id) + '">' +
      '<div class="shared-ref-head"><strong>' + esc(item.display_name) + '</strong>' +
      statusBadge(item.overall) + '</div>' +
      '<div class="shared-ref-variant">' + esc(ref.variant || "默认规则") + '</div>' +
      (ref.master ? '<div class="shared-ref-master">母版：' + esc(ref.master) + '</div>' : '') +
      (ref.note ? '<div class="shared-ref-note">' + esc(ref.note) + '</div>' : '') +
      '</button>';
  }).join("") || '<div class="preview-empty">本页面尚未登记公共设计引用。</div>';

  document.querySelectorAll(".shared-ref-card[data-shared-id]").forEach(btn =>
    btn.addEventListener("click", () => selectShared(btn.dataset.sharedId))
  );
}

function renderModalities(feature) {
  el("modalityGrid").innerHTML = DESIGN_MODALITIES.map(name => {
    const item = feature.modalities?.[name] || { status:"MISSING", authority:null };
    const authority = item.authority
      ? '<a href="' + esc(repoHref(item.authority)) +
        '" target="_blank" rel="noreferrer">' + esc(item.authority) + '</a>'
      : '—';
    const note = item.note ? '<div class="authority">' + esc(item.note) + '</div>' : '';
    return '<div class="modality-card">' +
      '<div class="modality-top"><div class="modality-name">' +
      esc(MODALITY_LABELS[name] || name) + '</div>' + statusBadge(item.status) + '</div>' +
      '<div class="authority"><b>权威来源</b><br>' + authority + '</div>' + note +
      '</div>';
  }).join("");
}

const FREEZE_STATUS_LABELS = {
  CONFIRMED: "已确认",
  PENDING_CONFIRMATION: "待确认",
  PENDING_SPEC: "待补规范",
  IN_REVIEW: "审阅中"
};

function freezeStatusBadge(status) {
  const label = FREEZE_STATUS_LABELS[status] || status || "待确认";
  const css =
    status === "CONFIRMED" ? "freeze-confirmed" :
    status === "PENDING_SPEC" ? "freeze-pending-spec" :
    status === "PENDING_CONFIRMATION" ? "freeze-pending-confirmation" :
    "freeze-in-review";
  return '<span class="freeze-status ' + css + '">' + esc(label) + '</span>';
}


function myCatchesAuthorityImage(feature) {
  const version = currentVersion(feature);
  return version?.visual_authority || feature.modalities?.visual?.authority || "";
}

function hifiBasePhone(feature, overlay = "", label = "") {
  const image = myCatchesAuthorityImage(feature);
  return '<div class="hifi-phone-wrap">' +
    (label ? '<div class="hifi-phone-label">' + esc(label) + '</div>' : '') +
    '<div class="hifi-phone">' +
      (image ? '<img class="hifi-phone-shot" src="' + esc(repoHref(image)) + '" alt="我的鱼获高保真">' : '') +
      overlay +
    '</div>' +
  '</div>';
}

function bgDataPhone(inner, label = "") {
  const bgSystem = sharedRegistry.items.find(x => x.id === "background_system_v1");
  const bgVariant = (bgSystem?.variants || []).find(v => v.id === "BG_DATA");
  const t = bgVariant?.preview_treatment || {};
  const source = (bgVariant?.preview_sources || [])[0]?.path || bgSystem?.master?.path;
  const filter = [
    "saturate(" + Math.round((t.saturation ?? 1) * 100) + "%)",
    "contrast(" + Math.round((t.contrast ?? 1) * 100) + "%)",
    "brightness(" + Math.round((t.brightness ?? 1) * 100) + "%)"
  ].join(" ");
  return '<div class="hifi-phone-wrap">' +
    (label ? '<div class="hifi-phone-label">' + esc(label) + '</div>' : '') +
    '<div class="hifi-phone">' +
      (source ? '<img class="hifi-bg" src="' + esc(repoHref(source)) + '" style="filter:' + esc(filter) + '" alt="BG_DATA">' : '') +
      '<span class="hifi-mist" style="opacity:' + Number(t.mist_alpha || 0) + '"></span>' +
      inner +
    '</div>' +
  '</div>';
}

function hifiSearchBar(value="", focused=false) {
  return '<div class="hifi-search' + (focused ? " focused" : "") + '">' +
    '<span class="hifi-search-icon">⌕</span>' +
    '<span class="' + (value ? "value" : "placeholder") + '">' + esc(value || "搜索鱼种、地点或日期") + '</span>' +
    (value ? '<span class="hifi-clear">×</span>' : '') +
  '</div>';
}

function hifiHeader() {
  return '<div class="hifi-page-header"><span class="hifi-back">‹</span><strong>我的鱼获</strong><span class="hifi-tools">⌕　▽</span></div>' +
    '<div class="hifi-archive-summary">38 次鱼获 · 12 种鱼 · 7 记录天数</div>';
}

function hifiRow(species, meta, mark="") {
  return '<div class="hifi-row">' +
    '<div class="hifi-fish-thumb"><span></span></div>' +
    '<div class="hifi-row-copy"><strong>' + esc(species) + '</strong><span>' + esc(meta) + '</span></div>' +
    (mark ? '<div class="hifi-mark">' + esc(mark) + '</div>' : '') +
    '<div class="hifi-row-chevron">›</div>' +
  '</div>';
}

function hifiTimelineContent(opts={}) {
  const count=opts.count || 3;
  const rows=[
    ["草鱼","42.6 cm · 1.28 kg",opts.mark || "最长记录"],
    ["鲫鱼","28.3 cm · 0.52 kg",""],
    ["黄骨鱼","24.1 cm · 0.32 kg",""],
    ["翘嘴鲌","32.7 cm · 0.68 kg",""],
    ["鳜鱼","31.2 cm · 0.74 kg",""]
  ].slice(0,Math.min(count,5)).map(x=>hifiRow(...x)).join("");
  return '<div class="hifi-page-content">' + hifiHeader() +
    hifiSearchBar() +
    '<div class="hifi-filter-pills"><span>鱼种</span><span>时间</span><span>地点</span><span>特殊记录</span></div>' +
    '<div class="hifi-month">2026年9月</div>' +
    '<div class="hifi-day-head"><b>23</b><span>SEP<br>周三</span><i></i><p>千岛湖 · ' + esc(String(opts.total || count)) + '条鱼获 · ' + esc(String(Math.min(opts.total || count,5))) + '种鱼</p></div>' +
    '<div class="hifi-day-rows">' + rows + '</div>' +
    (opts.action ? '<div class="hifi-expand-action">' + esc(opts.action) + '⌄</div>' : '') +
  '</div>';
}

function hifiFilterSheet(level2=false) {
  if(level2) {
    return '<div class="hifi-sheet full">' +
      '<div class="hifi-sheet-handle"></div><div class="hifi-sheet-title"><span>‹</span><b>选择鱼种</b><em>完成 (3)</em></div>' +
      hifiSearchBar("",false) +
      '<div class="hifi-recent"><b>最近选择</b><span>草鱼</span><span>鲫鱼</span><span>黄骨鱼</span></div>' +
      '<div class="hifi-option-title">全部鱼种（支持多选）</div>' +
      ["草鱼","鲫鱼","鲤鱼","鳊鱼","黄骨鱼","翘嘴鲌","鳜鱼"].map((x,i)=>'<div class="hifi-option"><i class="'+(i<3?"checked":"")+'"></i><span>'+x+'</span><b>›</b></div>').join("") +
    '</div>';
  }
  const dims=[
    ["鱼种","草鱼、鲫鱼"],
    ["时间","今年"],
    ["地点","千岛湖"],
    ["特殊记录","第100条、最重"],
    ["尺寸","长度 ≥ 40cm"]
  ];
  return '<div class="hifi-sheet">' +
    '<div class="hifi-sheet-handle"></div><div class="hifi-sheet-title"><b>筛选</b><em>清除全部</em></div>' +
    dims.map(x=>'<div class="hifi-dimension"><strong>'+x[0]+'</strong><span>'+x[1]+'</span><b>›</b></div>').join("") +
    '<div class="hifi-sheet-footer"><button class="secondary">重置</button><button>查看 12 条鱼获</button></div>' +
  '</div>';
}

function hifiEmptyContent(kind) {
  const cfg={
    archive:["还没有鱼获记录","拍下第一条鱼，开始你的鱼获时间线","记录第一条鱼",""],
    filter:["没有找到符合条件的鱼获","试试调整筛选条件","修改筛选","清除筛选"],
    search:["没有找到相关鱼获","你可以搜索：鱼种、地点、日期","清除搜索",""]
  }[kind];
  const top = kind==="search" ? hifiSearchBar("鳄鱼",true) : hifiSearchBar();
  const filter = kind==="filter" ? '<div class="hifi-filter-pills active"><span>青鱼 ×</span><span>近7天 ×</span><span>千岛湖 ×</span></div>' : '';
  return '<div class="hifi-page-content">' + hifiHeader() + top + filter +
    '<div class="hifi-month">2026年9月</div>' +
    '<div class="hifi-empty-state"><strong>'+cfg[0]+'</strong><p>'+cfg[1]+'</p><button>'+cfg[2]+'</button>' +
    (cfg[3]?'<a>'+cfg[3]+'</a>':'') + '</div>' +
    (kind==="archive"?'<div class="hifi-camera-button">▣</div>':'') +
  '</div>';
}

function myCatchesHifiCanvas(feature, view) {
  const authorityImage=myCatchesAuthorityImage(feature);
  if(view.render_mode==="repo_image") {
    return '<div class="hifi-original">' +
      '<a href="' + esc(repoHref(authorityImage)) + '" target="_blank" rel="noreferrer">' +
      '<img src="' + esc(repoHref(authorityImage)) + '" alt="我的鱼获主页面高保真"></a>' +
      '<div class="hifi-original-caption">当前 GitHub Frozen Visual Authority · 点击查看原图</div></div>';
  }

  if(view.render_mode==="timeline_board") {
    return '<div class="hifi-board three">' +
      bgDataPhone(hifiTimelineContent({count:3,total:3}),"1–5 条 · 全部展开") +
      bgDataPhone(hifiTimelineContent({count:5,total:8,action:"查看另外 3 条"}),"6–10 条 · 默认 5 条") +
      bgDataPhone(hifiTimelineContent({count:5,total:12,action:"查看全部"}),">10 条 · 查看全部") +
    '</div>';
  }

  if(view.render_mode==="filter_board") {
    return '<div class="hifi-board three">' +
      hifiBasePhone(feature,"","默认时间线") +
      hifiBasePhone(feature,hifiFilterSheet(false),"一级 · 五维 Filter Sheet") +
      hifiBasePhone(feature,hifiFilterSheet(true),"二级 · 鱼种选择器") +
    '</div>';
  }

  if(view.render_mode==="empty_board") {
    return '<div class="hifi-board three">' +
      bgDataPhone(hifiEmptyContent("archive"),"Archive Empty") +
      bgDataPhone(hifiEmptyContent("filter"),"Filter Empty") +
      bgDataPhone(hifiEmptyContent("search"),"Search Empty") +
    '</div>';
  }

  if(view.render_mode==="growth_board") {
    return '<div class="hifi-growth-layout">' +
      hifiBasePhone(feature,"","列表真实落位") +
      '<div class="hifi-growth-examples">' +
        '<div class="hifi-growth-intro"><b>Growth Mark V1</b><span>轻量个人记录印记，不做游戏化 Badge。</span></div>' +
        hifiRow("草鱼","42.6 cm · 1.28 kg","第100条") +
        hifiRow("鲫鱼","28.3 cm · 0.52 kg","第一次记录") +
        hifiRow("青鱼","61.2 cm · 3.84 kg","最长 · 最重") +
        '<div class="hifi-priority"><b>显示优先级</b><span>数量里程碑　›　首次记录　›　尺寸纪录</span></div>' +
      '</div></div>';
  }

  if(view.render_mode==="search_board") {
    const focus='<div class="hifi-page-content">'+hifiHeader()+hifiSearchBar("",true)+'<div class="hifi-search-hint">键盘打开 · Timeline 不跳页</div>'+hifiTimelineContent({count:2,total:2}).replace('<div class="hifi-page-content">','').replace('</div>','')+'</div>';
    const results='<div class="hifi-page-content">'+hifiHeader()+hifiSearchBar("草鱼",true)+'<div class="hifi-result-count">2 次鱼获</div><div class="hifi-month">2026年9月</div><div class="hifi-day-rows">'+hifiRow("草鱼","42.6 cm · 1.28 kg","最长记录")+hifiRow("草鱼","37.8 cm · 0.96 kg","")+'</div></div>';
    return '<div class="hifi-board three">' +
      bgDataPhone(focus,"Focused") +
      bgDataPhone(results,"搜索有结果") +
      bgDataPhone(hifiEmptyContent("search"),"搜索无结果") +
    '</div>';
  }

  if(view.render_mode==="system_board") {
    const loading='<div class="hifi-page-content">'+hifiHeader()+hifiSearchBar()+'<div class="hifi-state-card"><i class="spinner"></i><b>正在整理你的时间档案…</b></div></div>';
    const error='<div class="hifi-page-content">'+hifiHeader()+hifiSearchBar()+'<div class="hifi-state-card error"><b>鱼获档案暂时无法加载</b><p>请稍后重试</p><button>重新加载</button></div></div>';
    return '<div class="hifi-board two">' + bgDataPhone(loading,"加载中") + bgDataPhone(error,"加载失败") + '</div>';
  }
  return '<div class="preview-empty">该高保真子页面尚未建立。</div>';
}

function renderHifiView(feature, hifiId) {
  const panel=el("hifiViewPanel");
  if(!hifiId){ panel.classList.add("hidden"); return; }
  const view=(feature.hifi_views||[]).find(x=>x.id===hifiId);
  if(!view){ panel.classList.add("hidden"); return; }
  panel.classList.remove("hidden");
  el("hifiViewTitle").textContent=view.title;
  el("hifiViewStatus").innerHTML=statusBadge(view.status||"PARTIAL");
  if(view.summary){
    el("hifiViewSummary").textContent=view.summary;
    el("hifiViewSummary").classList.remove("hidden");
  } else el("hifiViewSummary").classList.add("hidden");
  el("hifiViewCanvas").innerHTML=myCatchesHifiCanvas(feature,view);

  const scenes=(view.scenario_ids||[]).map(id=>(feature.scenario_pages||[]).find(x=>x.id===id)).filter(Boolean);
  el("hifiViewScenarios").innerHTML=scenes.map(scene=>
    '<button class="hifi-scenario-chip" data-scene-id="'+esc(scene.id)+'">'+esc(scene.title)+'</button>'
  ).join("") || '<span class="preview-empty compact">无附加场景映射</span>';

  el("hifiViewAuthorities").innerHTML=[view.authority,view.image].filter((v,i,a)=>v&&a.indexOf(v)===i).map(path=>
    '<a class="authority-row" href="'+esc(repoHref(path))+'" target="_blank" rel="noreferrer">'+esc(path)+'</a>'
  ).join("");

  if(view.source_reference){
    el("hifiViewSource").textContent="历史高保真源稿：" + view.source_reference + "；当前 Design Manager 视图按最新冻结规范重新审视呈现。";
    el("hifiViewSource").classList.remove("hidden");
  } else {
    el("hifiViewSource").classList.add("hidden");
  }

  document.querySelectorAll(".hifi-scenario-chip[data-scene-id]").forEach(btn=>
    btn.addEventListener("click",()=>selectPage(feature.id,btn.dataset.sceneId,null))
  );
}

function myCatchesScenarioPreviewHtml(feature, scene) {
  const bgSystem = sharedRegistry.items.find(x => x.id === "background_system_v1");
  const bgVariant = (bgSystem?.variants || []).find(v => v.id === "BG_DATA");
  const treatment = bgVariant?.preview_treatment || {};
  const source = (bgVariant?.preview_sources || [])[0]?.path || bgSystem?.master?.path;
  const filter = [
    "saturate(" + Math.round((treatment.saturation ?? 1) * 100) + "%)",
    "contrast(" + Math.round((treatment.contrast ?? 1) * 100) + "%)",
    "brightness(" + Math.round((treatment.brightness ?? 1) * 100) + "%)"
  ].join(" ");

  const p = scene.preview || {};
  const recordCard = (record, idx) =>
    '<div class="mc-record">' +
      '<div class="mc-thumb">鱼获' + (idx + 1) + '</div>' +
      '<div class="mc-record-body"><strong>' + esc(record.species || "草鱼") + '</strong>' +
      '<span>' + esc(record.meta || "42 cm · 1.3 kg") + '</span>' +
      '<span>' + esc(record.location || "千岛湖") + '</span></div>' +
      (record.mark ? '<span class="mc-mark">' + esc(record.mark) + '</span>' : '') +
      '<span class="mc-chevron">›</span>' +
    '</div>';

  let body = '';
  if (p.mode === "timeline" || p.mode === "search_results") {
    const records = p.records || [];
    body =
      '<div class="mc-month">' + esc(p.month || "2026年9月") + '</div>' +
      '<div class="mc-day"><b>' + esc(p.day || "9月28日") + '</b><span>' + esc(p.day_summary || "") + '</span></div>' +
      records.map(recordCard).join("");
  } else if (p.mode === "timeline_overflow") {
    const records = Array.from({length:p.visible_count || 5}, (_,i)=>({
      species:["草鱼","鲤鱼","白条","翘嘴鲌","鳜鱼"][i%5],
      meta:["52 cm · 2.3 kg","41 cm · 1.6 kg","18 cm · 0.2 kg","37 cm · 0.9 kg","29 cm · 0.7 kg"][i%5],
      location:"千岛湖"
    }));
    body =
      '<div class="mc-month">' + esc(p.month || "2026年9月") + '</div>' +
      '<div class="mc-day"><b>' + esc(p.day || "9月28日") + '</b><span>' + esc(p.day_summary || "") + '</span></div>' +
      records.map(recordCard).join("") +
      '<div class="mc-expand">' + esc(p.action || "查看更多") + '⌄</div>';
  } else if (p.mode === "search_focus") {
    body='<div class="mc-search focused"><span>⌕</span><span class="mc-placeholder">' + esc(p.placeholder || "") + '</span><span class="cursor"></span></div>' +
      '<div class="mc-filter-row"><span>筛选</span><span>鱼种</span><span>地点</span><span>时间</span></div>' +
      '<div class="mc-ghost">Timeline 保持在页面中</div>';
  } else if (p.mode === "filter_sheet") {
    body='<div class="mc-ghost">原 Timeline</div><div class="mc-sheet">' +
      '<div class="mc-sheet-title">筛选</div>' +
      (p.dimensions||[]).map(d=>'<div class="mc-dim"><span>'+esc(d.label)+'</span><b>'+esc(d.value||"全部")+'</b><i>›</i></div>').join("") +
      '<div class="mc-sheet-actions"><span>清除全部</span><strong>完成</strong></div></div>';
  } else if (p.mode === "filter_results") {
    body='<div class="mc-filter-row active">' + (p.chips||[]).map(x=>'<span>'+esc(x)+' ×</span>').join("") + '</div>' +
      '<div class="mc-summary">' + esc(String(p.result_count||0)) + ' 次鱼获</div>' +
      '<div class="mc-month">' + esc(p.month||"2026年9月") + '</div><div class="mc-day"><b>' + esc(p.day||"9月28日") + '</b></div>' +
      recordCard({species:"草鱼",meta:"52 cm · 2.3 kg",location:"千岛湖"},0);
  } else if (p.mode === "search_empty" || p.mode === "filter_empty" || p.mode === "archive_empty") {
    const query = p.query ? '<div class="mc-search focused"><span>⌕</span><b>'+esc(p.query)+'</b><span>×</span></div>' : '';
    const chips = p.chips ? '<div class="mc-filter-row active">' + p.chips.map(x=>'<span>'+esc(x)+' ×</span>').join("") + '</div>' : '';
    body=query+chips+'<div class="mc-empty"><strong>'+esc(p.title||"")+'</strong><span>'+esc(p.subtitle||"")+'</span>' +
      '<button>'+esc(p.primary||"")+'</button>' + (p.secondary?'<a>'+esc(p.secondary)+'</a>':'') + '</div>';
  } else if (p.mode === "growth_marks") {
    body=(p.examples||[]).map((x,i)=>recordCard({species:x.species,meta:"42 cm · 1.3 kg",location:"千岛湖",mark:x.mark},i)).join("");
  } else if (p.mode === "loading_error") {
    body='<div class="mc-state-card">◌ 正在整理你的时间档案…</div>' +
      '<div class="mc-state-card"><strong>鱼获档案暂时无法加载</strong><span>请稍后重试</span><button>重新加载</button></div>';
  }

  const searchTop = (p.mode === "search_results")
    ? '<div class="mc-search focused"><span>⌕</span><b>'+esc(p.query||"")+'</b><span>×</span></div>'
    : (!["search_focus","search_empty"].includes(p.mode)
      ? '<div class="mc-search"><span>⌕</span><span class="mc-placeholder">搜索鱼种、地点或日期</span></div>' : '');

  return '<div class="mc-preview-phone">' +
    (source ? '<img class="mc-bg" src="'+esc(repoHref(source))+'" style="filter:'+esc(filter)+'" alt="BG_DATA">' : '') +
    '<span class="mc-mist" style="opacity:'+Number(treatment.mist_alpha||0)+'"></span>' +
    '<div class="mc-content"><div class="mc-title">我的鱼获</div><div class="mc-subtitle">按时间留存每一次真实鱼获</div>' +
    searchTop + body + '</div><div class="mc-bg-badge">BG_DATA</div></div>';
}

function renderScenario(feature, scenarioId) {
  const panel = el("scenarioPanel");
  if (!scenarioId) {
    panel.classList.add("hidden");
    return;
  }
  const scene=(feature.scenario_pages||[]).find(x=>x.id===scenarioId);
  if(!scene){
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  el("scenarioTitle").textContent=scene.title;
  el("scenarioStatus").innerHTML=statusBadge(scene.status||"PARTIAL");
  if(scene.summary){
    el("scenarioSummary").textContent=scene.summary;
    el("scenarioSummary").classList.remove("hidden");
  } else el("scenarioSummary").classList.add("hidden");
  el("scenarioPreview").innerHTML=myCatchesScenarioPreviewHtml(feature,scene);
  el("scenarioRules").innerHTML=(scene.rules||[]).map((rule,i)=>
    '<div class="scenario-rule"><span>'+String(i+1).padStart(2,"0")+'</span><p>'+esc(rule)+'</p></div>'
  ).join("");
  const paths=[scene.authority,scene.secondary_authority].filter((v,i,a)=>v&&a.indexOf(v)===i);
  el("scenarioAuthorities").innerHTML=paths.map(path=>
    '<a class="authority-row" href="'+esc(repoHref(path))+'" target="_blank" rel="noreferrer">'+esc(path)+'</a>'
  ).join("");
}

function renderDesignSections(feature) {
  const panel = el("designSectionsPanel");
  const sections = feature.design_sections || [];
  if (!sections.length) {
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  el("designSectionsGrid").innerHTML = sections.map(section => {
    const authority = section.authority
      ? '<a class="design-section-authority" href="' + esc(repoHref(section.authority)) +
        '" target="_blank" rel="noreferrer">打开完整规范</a>'
      : '';
    const points = (section.points || []).map(point =>
      '<li>' + esc(point) + '</li>'
    ).join("");
    return '<article class="design-section-card">' +
      '<div class="design-section-head">' +
        '<div><div class="design-section-id">' + esc(section.id || "") + '</div>' +
        '<h4>' + esc(section.title || "") + '</h4></div>' +
        statusBadge(section.status || "PARTIAL") +
      '</div>' +
      '<p class="design-section-summary">' + esc(section.summary || "") + '</p>' +
      (points ? '<ul class="design-section-points">' + points + '</ul>' : '') +
      authority +
    '</article>';
  }).join("");
}

function renderFreezeReview(feature) {
  const panel = el("freezeReviewPanel");
  const review = feature.freeze_review;
  if (!review) {
    panel.classList.add("hidden");
    return;
  }

  panel.classList.remove("hidden");
  el("freezeReviewTitle").textContent = review.title || "设计冻结检查";
  el("freezeReviewState").innerHTML = freezeStatusBadge(review.state || "IN_REVIEW");

  if (review.note) {
    el("freezeReviewNote").textContent = review.note;
    el("freezeReviewNote").classList.remove("hidden");
  } else {
    el("freezeReviewNote").classList.add("hidden");
  }

  const items = review.items || [];
  const blockingPending = items.filter(item =>
    item.blocking && item.status !== "CONFIRMED"
  ).length;

  el("freezeChecklist").innerHTML =
    '<div class="freeze-summary">' +
      '<div><strong>' + items.filter(x => x.status === "CONFIRMED").length + '</strong><span>已确认</span></div>' +
      '<div><strong>' + blockingPending + '</strong><span>冻结阻塞项</span></div>' +
      '<div><strong>' + items.length + '</strong><span>总检查项</span></div>' +
    '</div>' +
    items.map(item => {
      const authority = item.authority
        ? '<a class="freeze-authority" href="' + esc(repoHref(item.authority)) +
          '" target="_blank" rel="noreferrer">查看 Authority</a>'
        : '';
      return '<article class="freeze-item">' +
        '<div class="freeze-item-head">' +
          '<div><div class="freeze-item-id">' + esc(item.id) + '</div>' +
          '<h4>' + esc(item.title) + '</h4></div>' +
          freezeStatusBadge(item.status) +
        '</div>' +
        '<p>' + esc(item.decision || "") + '</p>' +
        '<div class="freeze-item-foot">' +
          '<span class="freeze-blocking">' + (item.blocking ? "阻塞冻结" : "非阻塞 / 后续交接") + '</span>' +
          authority +
        '</div>' +
      '</article>';
    }).join("");
}

function renderVersions(feature) {
  const versions = feature.design_versions || [];
  el("versionTimeline").innerHTML = versions.map(v => {
    const refs = [v.visual_authority, v.spec_authority].filter(Boolean);
    const desc = refs.map(r =>
      '<a href="' + esc(repoHref(r)) + '" target="_blank" rel="noreferrer">' +
      esc(r) + '</a>'
    ).join("<br>");
    return '<div class="version-row">' +
      '<div><div class="version-id">' + esc(v.version) + '</div>' +
      (v.current ? '<div class="version-current">当前版本</div>' : '') + '</div>' +
      '<div>' + statusBadge(v.status) + '</div>' +
      '<div class="version-desc">' + (desc || "未登记权威来源") +
      (v.note ? '<br><br>' + esc(v.note) : '') + '</div></div>';
  }).join("") || '<div class="preview-empty">尚未建立版本历史。</div>';
}

function selectPage(id, scenarioId = null, hifiId = null) {
  const feature = pageRegistry.features.find(f => f.id === id);
  if (!feature) return;
  const scene = scenarioId ? (feature.scenario_pages || []).find(x => x.id === scenarioId) : null;
  const hifi = hifiId ? (feature.hifi_views || []).find(x => x.id === hifiId) : null;
  selectedKey = "page/" + id + (hifi ? "/hifi/" + hifi.id : (scene ? "/scenario/" + scene.id : ""));
  history.replaceState(
    null, "",
    "#page/" + encodeURIComponent(id) +
      (hifi ? "/hifi/" + encodeURIComponent(hifi.id) : (scene ? "/scenario/" + encodeURIComponent(scene.id) : ""))
  );
  renderLists();
  hideAllDetails();
  el("pageDetail").classList.remove("hidden");

  el("pageEyebrow").textContent = hifi ? "渔见 · 高保真子页面" : (scene ? "渔见 · 场景 Authority" : "渔见 · 页面模块");
  el("pageTitle").textContent = hifi ? (feature.display_name + " · " + hifi.title) : (scene ? (feature.display_name + " · " + scene.title) : (feature.display_name || feature.id));
  el("pageStatus").innerHTML = statusBadge(hifi?.status || scene?.status || feature.design_overall);
  el("moduleName").textContent = feature.display_name || feature.id;
  el("overallBadge").innerHTML = statusBadge(feature.design_overall);

  const version = currentVersion(feature);
  el("moduleMeta").innerHTML = [
    ["模块 ID", feature.id],
    ["归属路径", feature.owner_path],
    ["当前版本", version?.version || "—"],
    ["版本状态", version ? statusText(version.status) : "—"]
  ].map(([label,value]) =>
    '<div class="meta-card"><div class="meta-label">' + esc(label) +
    '</div><div class="meta-value">' + esc(value) + '</div></div>'
  ).join("");

  if (feature.note) {
    el("moduleNote").textContent = feature.note;
    el("moduleNote").classList.remove("hidden");
  } else el("moduleNote").classList.add("hidden");

  renderHifiView(feature, hifi?.id || null);
  renderScenario(feature, scene?.id || null);
  renderDesignSections(feature);
  renderFreezeReview(feature);
  renderPagePreview(feature);
  renderSharedRefs(feature);
  renderModalities(feature);
  renderVersions(feature);
}

function selectFromHash() {
  const raw = decodeURIComponent(location.hash.replace(/^#/, ""));
  if (raw.startsWith("shared/")) {
    const parts = raw.split("/");
    const id = parts[1];
    const variantId = parts[2] || null;
    const item = sharedRegistry.items.find(x => x.id === id);
    if (item) {
      const validVariant = !variantId || (item.variants || []).some(v => v.id === variantId);
      if (validVariant) return selectShared(id, variantId);
    }
  }
  if (raw.startsWith("page/")) {
    const parts = raw.split("/");
    const id = parts[1];
    const feature = pageRegistry.features.find(x => x.id === id);
    if (feature) {
      if (parts[2] === "hifi" && parts[3]) {
        const hifiId=parts[3];
        if ((feature.hifi_views || []).some(x=>x.id===hifiId)) return selectPage(id,null,hifiId);
      }
      if (parts[2] === "scenario" && parts[3]) {
        const scenarioId=parts[3];
        if ((feature.scenario_pages || []).some(x=>x.id===scenarioId)) return selectPage(id,scenarioId,null);
      }
      if (!parts[2]) return selectPage(id);
    }
  }
  selectShared(sharedRegistry.items[0]?.id);
}

async function init() {
  try {
    const [pageResponse, sharedResponse] = await Promise.all([
      fetch(PAGE_REGISTRY_URL, { cache:"no-store" }),
      fetch(SHARED_REGISTRY_URL, { cache:"no-store" })
    ]);
    if (!pageResponse.ok || !sharedResponse.ok) throw new Error("Registry HTTP error");
    pageRegistry = await pageResponse.json();
    sharedRegistry = await sharedResponse.json();

    const backgroundItem = sharedRegistry.items.find(x => x.id === "background_system_v1");
    if (backgroundItem?.contract_path && backgroundItem?.usage_map_path) {
      const [contractResponse, usageResponse] = await Promise.all([
        fetch(repoHref(backgroundItem.contract_path), { cache:"no-store" }),
        fetch(repoHref(backgroundItem.usage_map_path), { cache:"no-store" })
      ]);
      if (!contractResponse.ok || !usageResponse.ok) {
        throw new Error("Background contract HTTP error");
      }
      backgroundContract = await contractResponse.json();
      backgroundUsage = await usageResponse.json();
    }
  } catch (error) {
    document.body.innerHTML =
      '<div style="padding:40px;font-family:system-ui"><h2>设计管理无法读取注册表</h2>' +
      '<p>请确认页面 Registry 与公共设计系统 Registry 均已发布。</p><pre>' +
      esc(error.message) + '</pre></div>';
    return;
  }

  renderSummary();
  fillStatusFilter();
  renderLists();
  selectFromHash();

  el("searchInput").addEventListener("input", renderLists);
  el("statusFilter").addEventListener("change", renderLists);
  window.addEventListener("hashchange", selectFromHash);
}

init();
