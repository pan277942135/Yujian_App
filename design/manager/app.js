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
    return '<button class="module-item shared-item' + (selectedKey === key ? " active" : "") +
      '" data-kind="shared" data-id="' + esc(item.id) + '">' +
      '<div class="module-name"><span>' + esc(item.display_name) + '</span>' +
      statusBadge(item.overall) + '</div>' +
      '<div class="module-path">' + esc(CATEGORY_LABELS[item.category] || item.category) +
      ' · ' + esc(item.current_version) + '</div></button>';
  }).join("") || '<div class="preview-empty compact">没有匹配的公共系统</div>';

  el("pageList").innerHTML = pages.map(feature => {
    const key = "page/" + feature.id;
    return '<button class="module-item' + (selectedKey === key ? " active" : "") +
      '" data-kind="page" data-id="' + esc(feature.id) + '">' +
      '<div class="module-name"><span>' + esc(feature.display_name || feature.id) + '</span>' +
      statusBadge(feature.design_overall) + '</div>' +
      '<div class="module-path">' + esc(feature.owner_path) + '</div></button>';
  }).join("") || '<div class="preview-empty compact">没有匹配的页面</div>';

  document.querySelectorAll(".module-item[data-kind]").forEach(btn => {
    btn.addEventListener("click", () => {
      if (btn.dataset.kind === "shared") selectShared(btn.dataset.id);
      else selectPage(btn.dataset.id);
    });
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

function pagesUsing(sharedId) {
  return pageRegistry.features.filter(f =>
    (f.shared_system_refs || []).some(ref => ref.id === sharedId)
  );
}

function backgroundVariantPreview(item, variant) {
  if (item.id !== "background_system_v1") return "";

  if (variant.preview_treatment && item.master?.path) {
    const t = variant.preview_treatment;
    const filter = [
      "saturate(" + Math.round((t.saturation ?? 1) * 100) + "%)",
      "contrast(" + Math.round((t.contrast ?? 1) * 100) + "%)",
      "brightness(" + Math.round((t.brightness ?? 1) * 100) + "%)"
    ].join(" ");

    return '<div class="bg-variant-preview">' +
      '<img src="' + esc(repoHref(item.master.path)) + '" alt="' + esc(variant.name) +
      '" style="filter:' + esc(filter) + '">' +
      '<span class="bg-mist" style="opacity:' + Number(t.mist_alpha || 0) + '"></span>' +
      '</div>' +
      '<div class="bg-preview-caption">同一母版 · 雾化 ' +
      Math.round(Number(t.mist_alpha || 0) * 100) + '% · 饱和度 ' +
      Math.round(Number(t.saturation || 1) * 100) + '% · 对比度 ' +
      Math.round(Number(t.contrast || 1) * 100) + '%</div>';
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

function selectShared(id) {
  const item = sharedRegistry.items.find(x => x.id === id);
  if (!item) return;
  selectedKey = "shared/" + id;
  history.replaceState(null, "", "#shared/" + encodeURIComponent(id));
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
    sharedMetaRows.push(["母版尺寸", item.master.width + " × " + item.master.height]);
    sharedMetaRows.push(["母版 SHA", String(item.master.sha256 || "—").slice(0, 16) + "…"]);
  }
  el("sharedMeta").innerHTML = sharedMetaRows.map(([label,value]) =>
    '<div class="meta-card"><div class="meta-label">' + esc(label) +
    '</div><div class="meta-value">' + esc(value) + '</div></div>'
  ).join("");

  if (item.note) {
    el("sharedNote").textContent = item.note;
    el("sharedNote").classList.remove("hidden");
  } else el("sharedNote").classList.add("hidden");

  el("sharedPreview").innerHTML = previewHtml(item.preview, item.display_name, item.preview_note);

  el("variantGrid").innerHTML = (item.variants || []).map(v =>
    '<div class="variant-card">' +
    backgroundVariantPreview(item, v) +
    '<div class="variant-id">' + esc(v.id) + '</div>' +
    '<div class="variant-name">' + esc(v.name) + '</div>' +
    '<div class="variant-usage">' + esc(v.usage || "") + '</div>' +
    (v.note ? '<div class="variant-note">' + esc(v.note) + '</div>' : '') +
    '</div>'
  ).join("") || '<div class="preview-empty">尚未登记变体。</div>';

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

function renderPagePreview(feature) {
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

function selectPage(id) {
  const feature = pageRegistry.features.find(f => f.id === id);
  if (!feature) return;
  selectedKey = "page/" + id;
  history.replaceState(null, "", "#page/" + encodeURIComponent(id));
  renderLists();
  hideAllDetails();
  el("pageDetail").classList.remove("hidden");

  el("pageEyebrow").textContent = "渔见 · 页面模块";
  el("pageTitle").textContent = feature.display_name || feature.id;
  el("pageStatus").innerHTML = statusBadge(feature.design_overall);
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

  renderPagePreview(feature);
  renderSharedRefs(feature);
  renderModalities(feature);
  renderVersions(feature);
}

function selectFromHash() {
  const raw = decodeURIComponent(location.hash.replace(/^#/, ""));
  if (raw.startsWith("shared/")) {
    const id = raw.slice("shared/".length);
    if (sharedRegistry.items.some(x => x.id === id)) return selectShared(id);
  }
  if (raw.startsWith("page/")) {
    const id = raw.slice("page/".length);
    if (pageRegistry.features.some(x => x.id === id)) return selectPage(id);
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
