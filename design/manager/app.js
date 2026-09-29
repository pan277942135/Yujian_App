const REGISTRY_URL = "../registry/experience_registry_v1.json";
const DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"];

const ICONS = {
  FROZEN: "✓",
  ACTIVE_CLOSURE: "◐",
  PARTIAL: "◒",
  MISSING: "×",
  RUNTIME_ONLY: "↗",
  DESIGN_ONLY: "◇",
  DEPRECATED: "—",
  CANDIDATE: "◐"
};

const STATUS_LABELS = {
  FROZEN: "已冻结",
  ACTIVE_CLOSURE: "收口中",
  PARTIAL: "部分完成",
  MISSING: "缺失",
  RUNTIME_ONLY: "仅运行时",
  DESIGN_ONLY: "仅设计",
  DEPRECATED: "已废弃",
  CANDIDATE: "候选"
};

const MODALITY_LABELS = {
  behavior: "行为",
  visual: "视觉",
  motion: "动效",
  haptic: "震动",
  sound: "声音",
  assets: "资产"
};

let registry = null;
let selectedId = null;

const el = id => document.getElementById(id);
const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
})[c]);

function statusText(status) {
  return STATUS_LABELS[status] || status || STATUS_LABELS.MISSING;
}

function statusBadge(status) {
  const safe = status || "MISSING";
  return '<span class="badge status-' + esc(safe) + '">' +
    (ICONS[safe] || "•") + ' ' + esc(statusText(safe)) + '</span>';
}

function repoHref(path) {
  return path ? "../../" + path : "#";
}

function isImage(path) {
  return !!path && /\.(png|jpe?g|webp|gif)$/i.test(path);
}

function currentVersion(feature) {
  return (feature.design_versions || []).find(v => v.current) ||
    (feature.design_versions || [])[0] || null;
}

function renderSummary(features) {
  const counts = {};
  features.forEach(f => counts[f.design_overall] = (counts[f.design_overall] || 0) + 1);
  el("summary").innerHTML =
    '<span class="summary-chip"><b>' + features.length + '</b> 个模块</span>' +
    Object.entries(counts).sort().map(([k,v]) =>
      '<span class="summary-chip">' + (ICONS[k] || "•") + ' ' +
      esc(statusText(k)) + ' <b>' + v + '</b></span>'
    ).join("");
}

function fillStatusFilter(features) {
  const values = [...new Set(features.map(f => f.design_overall))].sort();
  el("statusFilter").innerHTML = '<option value="">全部状态</option>' +
    values.map(v => '<option value="' + esc(v) + '">' + esc(statusText(v)) + '</option>').join("");
}

function filteredFeatures() {
  const q = el("searchInput").value.trim().toLowerCase();
  const status = el("statusFilter").value;
  return registry.features.filter(f => {
    const hit = !q || JSON.stringify(f).toLowerCase().includes(q);
    return hit && (!status || f.design_overall === status);
  });
}

function renderList() {
  const features = filteredFeatures();
  el("moduleList").innerHTML = features.map(f => {
    const active = f.id === selectedId ? " active" : "";
    return '<button class="module-item' + active + '" data-id="' + esc(f.id) + '">' +
      '<div class="module-name"><span>' + esc(f.display_name || f.id) + '</span>' +
      statusBadge(f.design_overall) + '</div>' +
      '<div class="module-path">' + esc(f.owner_path) + '</div></button>';
  }).join("") || '<div class="preview-empty">没有匹配的模块</div>';

  document.querySelectorAll(".module-item").forEach(btn => {
    btn.addEventListener("click", () => selectModule(btn.dataset.id));
  });
}

function renderPreview(feature) {
  const version = currentVersion(feature);
  const visual = version?.visual_authority || feature.modalities?.visual?.authority;
  if (isImage(visual)) {
    el("visualPreview").innerHTML =
      '<a href="' + esc(repoHref(visual)) + '" target="_blank" rel="noreferrer">' +
      '<img src="' + esc(repoHref(visual)) + '" alt="' +
      esc(feature.display_name || feature.id) + ' 当前视觉权威">' +
      '</a>';
  } else if (visual) {
    el("visualPreview").innerHTML =
      '<div class="preview-empty">当前视觉权威不是可直接预览的图片。<br><br>' +
      '<a href="' + esc(repoHref(visual)) + '" target="_blank" rel="noreferrer">' +
      esc(visual) + '</a></div>';
  } else {
    el("visualPreview").innerHTML =
      '<div class="preview-empty">尚未登记标准视觉稿。</div>';
  }
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
  if (!versions.length) {
    el("versionTimeline").innerHTML =
      '<div class="preview-empty">尚未建立版本历史。</div>';
    return;
  }

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
      '<div class="version-desc">' +
      (desc || "未登记权威来源") +
      (v.note ? '<br><br>' + esc(v.note) : '') +
      '</div></div>';
  }).join("");
}

function selectModule(id) {
  selectedId = id;
  history.replaceState(null, "", "#" + encodeURIComponent(id));
  renderList();

  const feature = registry.features.find(f => f.id === id);
  if (!feature) return;

  el("emptyState").classList.add("hidden");
  el("detail").classList.remove("hidden");
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
  } else {
    el("moduleNote").classList.add("hidden");
  }

  renderPreview(feature);
  renderModalities(feature);
  renderVersions(feature);
}

async function init() {
  try {
    const response = await fetch(REGISTRY_URL, { cache:"no-store" });
    if (!response.ok) throw new Error("HTTP " + response.status);
    registry = await response.json();
  } catch (error) {
    document.body.innerHTML =
      '<div style="padding:40px;font-family:system-ui">' +
      '<h2>设计管理无法读取注册表</h2>' +
      '<p>请从仓库根目录通过 HTTP 服务打开，不要直接双击 file:// 文件。</p>' +
      '<pre>' + esc(error.message) + '</pre></div>';
    return;
  }

  const features = registry.features || [];
  renderSummary(features);
  fillStatusFilter(features);
  renderList();

  const hashId = decodeURIComponent(location.hash.replace(/^#/, ""));
  const initial = features.some(f => f.id === hashId) ? hashId : features[0]?.id;
  if (initial) selectModule(initial);

  el("searchInput").addEventListener("input", renderList);
  el("statusFilter").addEventListener("change", renderList);
}

init();
