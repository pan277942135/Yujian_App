const DATA_BASE = "./data/";
const ASSET_PATHS = {
  frozen: "../../system/core_visual_v1/reference/normal_home_v1.png",
  background: "../../system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
  brand: "../../system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png",
  camera: "../../system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png",
  chevron: "../../system/icon_library_v0_1/assets/ic_chevron_right_v1.svg"
};

const DOCS = {
  structure: "NH01_Structure_V1.json",
  assets: "NH01_Assets_V1.json",
  tokens: "NH01_Tokens_V1.json",
  tree: "NH01_Component_Tree_V1.json",
  bindings: "NH01_Data_Bindings_V1.json",
  responsive: "NH01_Responsive_V1.json",
  evidence: "NH01_Evidence_Provenance_V1.json",
  runtime: "NH01_Android_Runtime_V1.json",
  browserDiff: "NH01_Browser_Preview_Diff_V1.json",
  source: "NH01_Source_Manifest_V1.json"
};

let model = {};
let selectedId = "hero_selected";
let toastTimer;

async function loadDocuments() {
  const names = Object.entries(DOCS);
  const values = await Promise.all(names.map(async ([key, file]) => {
    const response = await fetch(`${DATA_BASE}${file}`, { cache: "no-store" });
    if (!response.ok) throw new Error(`${file}: HTTP ${response.status}`);
    return [key, await response.json()];
  }));
  model = Object.fromEntries(values);
}

function statusClass(status) {
  const upper = String(status || "NOT_RUN").toUpperCase();
  if (upper === "PASS" || upper === "SOURCE_VERIFIED") return "pass";
  if (upper.startsWith("BLOCKED") || upper === "FAIL" || upper === "SOURCE_FAIL") return "blocked";
  if (upper === "IN_PROGRESS") return "in-progress";
  if (upper === "NOT_RUN" || upper === "PENDING_REVIEW") return "not-run";
  return "review";
}

function pill(status, label = status) {
  const span = document.createElement("span");
  span.className = `status-pill ${statusClass(status)}`;
  span.textContent = label || "NOT_RUN";
  return span;
}

function component(id) {
  return model.structure?.components?.find(item => item.id === id);
}

function previewLayer(id) {
  return model.structure?.preview_layout?.find(item => item.id === id);
}

function setRect(element, rect) {
  if (!element || !rect) return;
  element.style.left = `${rect.x}px`;
  element.style.top = `${rect.y}px`;
  element.style.width = `${rect.width}px`;
  element.style.height = `${rect.height}px`;
}

function makeArtboard() {
  const artboard = document.createElement("div");
  artboard.className = "artboard";
  artboard.setAttribute("role", "img");
  artboard.setAttribute("aria-label", "NH01 contract-driven structured preview; unverified assets are visibly marked");
  const make = (tag, className, id, rect) => {
    const el = document.createElement(tag);
    el.className = `layer ${className}`;
    el.dataset.componentId = id;
    if (rect) el.dataset.contractRect = JSON.stringify(rect);
    return el;
  };
  const background = make("img", "background-layer", "environment", previewLayer("background")?.rect);
  setRect(background, previewLayer("background")?.rect);
  background.src = ASSET_PATHS.background;
  background.alt = "Morning Lake background source asset";
  artboard.append(background);

  const brand = make("img", "brand-layer", "header", previewLayer("brand")?.rect);
  setRect(brand, previewLayer("brand")?.rect);
  brand.src = ASSET_PATHS.brand;
  brand.alt = "渔见 brand title artwork";
  artboard.append(brand);
  const avatar = make("div", "avatar-slot", "header", previewLayer("avatar")?.rect);
  setRect(avatar, previewLayer("avatar")?.rect);
  avatar.textContent = "头像\nAuthority\n待确认";
  avatar.setAttribute("aria-label", "头像 Authority 冲突待确认");
  avatar.style.whiteSpace = "pre-line";
  artboard.append(avatar);

  const stats = make("div", "statistics", "statistics", previewLayer("statistics")?.rect);
  setRect(stats, previewLayer("statistics")?.rect);
  const fixtureStats = model.structure?.preview_fixture?.statistics || [];
  for (const row of fixtureStats) {
    const cell = document.createElement("div");
    cell.className = "stat-cell";
    const value = document.createElement("span");
    value.className = "stat-value";
    value.textContent = row.value;
    const label = document.createElement("span");
    label.className = "stat-label";
    label.textContent = row.label;
    cell.append(value, label);
    stats.append(cell);
  }
  artboard.append(stats);

  const recent = make("div", "recent-header", "recent_header", previewLayer("recent_header")?.rect);
  setRect(recent, previewLayer("recent_header")?.rect);
  const title = document.createElement("span");
  title.className = "recent-title";
  title.textContent = "最近鱼获";
  const action = document.createElement("span");
  action.className = "recent-action";
  action.append(document.createTextNode("全部"));
  const chevron = document.createElement("img");
  chevron.src = ASSET_PATHS.chevron;
  chevron.alt = "";
  action.append(chevron);
  recent.append(title, action);
  artboard.append(recent);

  const pagerRect = previewLayer("pager")?.rect;
  const pager = make("div", "pager-viewport", "fish_pager", pagerRect);
  setRect(pager, pagerRect);
  for (const [id, className] of [["hero_neighbor_left", "neighbor-left"], ["hero_selected", "is-selected"], ["hero_neighbor_right", "neighbor-right"]]) {
    const mapped = component(id);
    const localLayout = previewLayer(id);
    const card = make("div", `pager-card ${className}`, id, mapped?.rect);
    setRect(card, localLayout?.rect);
    card.setAttribute("role", "group");
    card.setAttribute("aria-label", mapped?.name || "Hero card");
    const source = document.createElement("div");
    source.className = "photo-unavailable";
    source.textContent = className === "is-selected"
      ? model.structure?.preview_fixture?.hero?.photo_placeholder || "Photo asset source not verified"
      : "邻卡照片来源待核验";
    card.append(source);
    if (className === "is-selected") {
      const footer = document.createElement("div");
      footer.className = "hero-footer";
      const sample = model.structure?.preview_fixture?.hero || {};
      for (const [key, css] of [["species", "species"], ["measurement", "measure"], ["context", "context"]]) {
        const line = document.createElement("span");
        line.className = css;
        line.textContent = sample[key] || "";
        footer.append(line);
      }
      card.append(footer);
    }
    pager.append(card);
  }
  artboard.append(pager);

  const captureRect = previewLayer("cta")?.rect;
  const cta = make("div", "capture-cta", "cta", captureRect);
  setRect(cta, captureRect);
  cta.textContent = "记录下一条鱼";
  artboard.append(cta);
  const camera = make("img", "camera-art", "camera", previewLayer("camera")?.rect);
  setRect(camera, previewLayer("camera")?.rect);
  camera.src = ASSET_PATHS.camera;
  camera.alt = "Registered Normal Home camera artwork";
  artboard.append(camera);

  artboard.addEventListener("click", event => {
    const target = event.target.closest("[data-component-id]");
    if (target?.dataset.componentId) selectComponent(target.dataset.componentId);
  });
  return artboard;
}

function renderAllPreviews() {
  for (const id of ["miniStructured", "structuredPreview", "inspectorPreview", "compareStructured"]) {
    const host = document.getElementById(id);
    if (!host) continue;
    host.replaceChildren(makeArtboard());
  }
  resizePreviewStages();
  applyOverlays();
}

function resizePreviewStages() {
  document.querySelectorAll(".preview-host").forEach(host => {
    const artboard = host.querySelector(".artboard");
    if (!artboard) return;
    const availableWidth = Math.max(260, host.clientWidth - 28);
    const availableHeight = Math.max(420, host.clientHeight - 28);
    const scale = Math.min(availableWidth / 1080, availableHeight / 1920, 0.72);
    host.style.setProperty("--preview-scale", String(scale));
  });
  const mini = document.getElementById("miniStructured")?.querySelector(".artboard");
  if (mini) mini.style.transform = "none";
  document.querySelectorAll(".compare-pane .artboard").forEach(node => {
    const pane = node.parentElement;
    const scale = Math.min(pane.clientWidth / 1080, pane.clientHeight / 1920);
    node.style.transform = `scale(${scale})`;
    node.style.transformOrigin = "top left";
  });
}

function componentLabel(item) {
  return item?.name || item?.id || "Unknown component";
}

function renderTree(targetId) {
  const target = document.getElementById(targetId);
  if (!target) return;
  target.replaceChildren();
  for (const item of model.structure?.components || []) {
    const row = document.createElement("button");
    row.className = `tree-row ${item.id === selectedId ? "is-selected" : ""}`;
    row.dataset.componentId = item.id;
    const nodeId = document.createElement("span");
    nodeId.className = "tree-id";
    nodeId.textContent = item.figma_node_id || "—";
    const label = document.createElement("span");
    label.className = "tree-name";
    label.textContent = item.name;
    const state = document.createElement("span");
    state.className = "tree-status";
    state.textContent = item.status || "NOT_RUN";
    row.append(nodeId, label, state);
    row.addEventListener("click", () => selectComponent(item.id));
    target.append(row);
  }
}

function renderInspector(targetId, item = component(selectedId)) {
  const target = document.getElementById(targetId);
  if (!target) return;
  target.replaceChildren();
  const title = document.createElement("h3");
  title.textContent = componentLabel(item);
  target.append(title, pill(item?.status || "NOT_RUN", item?.status || "NOT_RUN"));
  const rect = item?.rect || {};
  const pairs = [
    ["Component ID", item?.id || "—"],
    ["Figma Node ID", item?.figma_node_id || "—"],
    ["Parent", item?.parent_id || "—"],
    ["Kind", item?.kind || "—"],
    ["X / Y", `${rect.x ?? "—"} / ${rect.y ?? "—"} px`],
    ["W / H", `${rect.width ?? "—"} / ${rect.height ?? "—"} px`],
    ["Typography", typographyFor(item?.id)],
    ["Asset", assetFor(item?.id)],
    ["State", item?.id === "hero_selected" ? "Selected · sample copy only" : item?.status || "NOT_RUN"],
    ["Responsive Rule", responsiveFor(item?.id)],
    ["Compose", item?.compose || "—"],
    ["Authority", item?.evidence || item?.rect?.evidence || "—"],
    ["Evidence", item?.status === "PASS" ? "Recorded in PR #164 mapping / source contract" : "See per-field source status; runtime readback is NOT_RUN"]
  ];
  const dl = document.createElement("dl");
  dl.className = "property-list";
  for (const [label, value] of pairs) {
    const dt = document.createElement("dt"); dt.textContent = label;
    const dd = document.createElement("dd"); dd.textContent = value;
    if (["Compose", "Authority", "Evidence"].includes(label)) dd.className = "long";
    dl.append(dt, dd);
  }
  target.append(dl);
}

function typographyFor(id) {
  const map = {
    statistics: "Values 36/42; labels 28/32 px · A1 contract",
    recent_header: "Title 48/52; action 40/44 px · candidate Android mapping",
    hero_selected: "Species 56/64; measurement 46/52; context 36/42 px",
    cta: "40/44 px visual contract; current Compose candidate 36/40 px",
    camera: "Image asset; no text"
  };
  return map[id] || "See NH01_Tokens_V1.json; current font candidate REVIEW_REQUIRED";
}

function assetFor(id) {
  const map = {
    environment: "Morning_Lake_Master_V1.png · SHA-256 verified; Figma node 41:32 BLOCKED",
    header: "Brand IMAGE/FIT readback confirmed; Avatar authority conflict REVIEW_REQUIRED",
    fish_pager: "Hero photo fixture provenance and rights NOT_VERIFIED",
    hero_selected: "Photo media blocked; footer is Frozen sample copy",
    cta: "Editable text; visual mask thresholds registered",
    camera: "normal_home_camera_primary_v1.png · SHA-256 verified"
  };
  return map[id] || "No single asset";
}

function responsiveFor(id) {
  const map = {
    environment: "Full window · centered ContentScale.Crop; no tint or extra blur",
    header: "x/y from common transform; avatar touch bounds >=48dp",
    statistics: "Normal Home common transform; recordDays stays read-only",
    recent_header: "x inset 108px; height 70px; action may grow with fontScale",
    fish_pager: "Fixed 740×880 card; 16px page gap; manual horizontal paging",
    hero_selected: "Fixed frame 740×880px, radius 32px at S=1",
    capture_action_group: "Action group must stay inside safeDrawing; see NORMAL_FIXED / SAFE_OVERFLOW",
    cta: "Glyph vs container measured separately; no exact runtime bounds yet",
    camera: "216×216dp touch target; 208×208px artwork; actual bounds NOT_RUN"
  };
  return map[id] || "See NH01_Responsive_V1.json; required profiles NOT_RUN";
}

function selectComponent(id) {
  if (!component(id)) return;
  selectedId = id;
  renderTree("overviewTree");
  renderTree("inspectorTree");
  renderInspector("structuredInspector");
  renderInspector("propertyInspector");
  applyOverlays();
}

function overlayEnabled(id) {
  return Boolean(document.getElementById(id)?.checked);
}

function applyOverlays() {
  const anyBounds = overlayEnabled("showBounds") || overlayEnabled("inspectorBounds");
  const anyAnchors = overlayEnabled("showAnchors") || overlayEnabled("inspectorAnchors");
  const anyGuides = overlayEnabled("showGuides") || overlayEnabled("inspectorGuides");
  const anySafe = overlayEnabled("showSafeArea") || overlayEnabled("inspectorSafe");
  document.querySelectorAll(".artboard").forEach(artboard => {
    artboard.querySelectorAll(".bounds-layer,.safe-area-box,.guide-line,.anchor-dot").forEach(el => el.remove());
    if (anyBounds) {
      const layer = document.createElement("div"); layer.className = "bounds-layer";
      for (const item of model.structure?.components || []) {
        const r = item.rect;
        if (!r || r.width == null || r.height == null || !["environment", "header", "statistics", "recent_header", "fish_pager", "hero_selected", "cta", "camera"].includes(item.id)) continue;
        const box = document.createElement("div"); box.className = "bounds-box";
        box.style.left = `${r.x}px`; box.style.top = `${r.y}px`; box.style.width = `${r.width}px`; box.style.height = `${r.height}px`;
        if (item.id === selectedId) box.style.borderColor = "#a75e35";
        const label = document.createElement("span"); label.textContent = `${item.id} ${r.x},${r.y} ${r.width}×${r.height}`; box.append(label); layer.append(box);
      }
      artboard.append(layer);
    }
    if (anySafe) { const safe = document.createElement("div"); safe.className = "safe-area-box"; artboard.append(safe); }
    if (anyAnchors) {
      for (const item of model.structure?.components || []) {
        const r = item.rect; if (!r || r.width == null || r.height == null || !["header", "statistics", "recent_header", "hero_selected", "cta", "camera"].includes(item.id)) continue;
        const dot = document.createElement("div"); dot.className = "anchor-dot"; dot.style.left = `${r.x + r.width / 2}px`; dot.style.top = `${r.y + r.height / 2}px`; dot.title = `${item.id} center`; artboard.append(dot);
      }
    }
    if (anyGuides) {
      for (const y of [104, 304, 494, 596, 1495, 1564]) {
        const line = document.createElement("div"); line.className = "guide-line"; line.style.top = `${y}px`;
        const text = document.createElement("span"); text.textContent = `y=${y}`; line.append(text); artboard.append(line);
      }
    }
  });
}

function renderOverview() {
  const evidence = model.evidence || {};
  const gates = evidence.gate_status || {};
  const gateGrid = document.getElementById("gateGrid");
  gateGrid.replaceChildren();
  for (const [name, value] of Object.entries(gates)) {
    const card = document.createElement("div"); card.className = "gate-card";
    const title = document.createElement("small"); title.textContent = name.replaceAll("_", " ");
    const result = document.createElement("strong"); result.append(pill(value, value));
    card.append(title, result); gateGrid.append(card);
  }
  const frozen = model.source?.frozen_authority || {};
  document.getElementById("frozenShaShort").textContent = (frozen.sha256 || "NOT_RUN").slice(0, 16) + "…";
  document.getElementById("sourceShaFooter").textContent = `${frozen.sha256 || "NOT_RUN"}`;
  document.getElementById("componentCount").textContent = `${model.structure?.components?.length || 0} mapped elements`;
  document.getElementById("figmaStateFooter").textContent = model.evidence?.figma?.overall || "REVIEW_REQUIRED";
  document.getElementById("finalStateFooter").textContent = gates.G8_USER_ACCEPTANCE || "PENDING_REVIEW";
  document.getElementById("runtimeHeadline").textContent = evidence.android?.runtime || "NOT_RUN";
  document.getElementById("parityHeadline").textContent = gates.G5_VISUAL_PARITY_VALID || "NOT_RUN";
  document.getElementById("releaseHeadline").textContent = gates.FINAL || "IN_PROGRESS";
  document.getElementById("overallStatus").textContent = gates.FINAL || "IN_PROGRESS";
  document.getElementById("overallStatus").className = `status-pill ${statusClass(gates.FINAL)}`;
  const prSha = model.source?.pr164?.source_head_sha || "NOT_RUN";
  document.getElementById("viewerSha").textContent = document.querySelector('meta[name="nh01-source-sha"]')?.content || "draft";
  document.getElementById("runtimeStatusPill").textContent = evidence.android?.runtime || "NOT_RUN";
  document.getElementById("runtimeStatusPill").className = `status-pill ${statusClass(evidence.android?.runtime)}`;
  document.getElementById("runtimeCommitShort").textContent = `Source SHA: ${evidence.android?.source_sha || "NOT_RUN"}`;
  document.getElementById("runtimeRunId").textContent = `Run ID: ${evidence.android?.workflow_run_id || "NOT_RUN"}`;
  document.getElementById("diffStatusPill").textContent = gates.G5_VISUAL_PARITY_VALID || "NOT_RUN";
  document.getElementById("diffStatusPill").className = `status-pill ${statusClass(gates.G5_VISUAL_PARITY_VALID)}`;
  document.getElementById("runtimeEmptyTitle").textContent = `Runtime = ${evidence.android?.runtime || "NOT_RUN"}`;
  document.getElementById("runtimeSummary").replaceChildren();
  renderRuntime();
  renderDiffTable();
  renderBrowserDiff();
  renderIssues();
  renderTree("overviewTree");
  renderTree("inspectorTree");
  renderInspector("structuredInspector");
  renderInspector("propertyInspector");
  if (prSha) document.getElementById("runtimeRunLink").href = `https://github.com/pan277942135/Yujian_App/actions?query=branch%3Ddesign%2Fnh01-cloud-structured-parity-v1`;
}

function renderIssues() {
  const target = document.getElementById("issueList");
  target.replaceChildren();
  const conflicts = model.evidence?.open_issues || [];
  const fallback = [
    { id: "NH01-A01", priority: "P0", status: "BLOCKED_ENV", detail: "Background node 41:32 has no verified IMAGE Fill readback." },
    { id: "NH01-A02", priority: "P0", status: "BLOCKED_ASSET_SOURCE", detail: "Hero photo fixture provenance and usage rights are NOT_VERIFIED." },
    { id: "NH01-A04", priority: "P1", status: "REVIEW_REQUIRED", detail: "Camera Y coordinate differs between contract and current Compose implementation." },
    { id: "NH01-A07", priority: "P1", status: "REVIEW_REQUIRED", detail: "Avatar visual authority and sample photo treatment remain unresolved." }
  ];
  for (const item of (conflicts.length ? conflicts : fallback)) {
    const row = document.createElement("div"); row.className = "issue-row";
    const title = document.createElement("strong"); title.innerHTML = `<span class="issue-priority">${escapeHtml(item.priority || "P1")}</span>${escapeHtml(item.id || "OPEN")}`;
    const detail = document.createElement("p"); detail.textContent = `${item.status || "REVIEW_REQUIRED"} · ${item.detail || item.acceptance || "Open item from source mapping."}`;
    row.append(title, detail); target.append(row);
  }
}

function renderRuntime() {
  const runtime = model.runtime || {};
  const device = runtime.device || {};
  const values = [
    ["Commit SHA", runtime.source?.expected_source_sha || "NOT_RUN"],
    ["CI Run ID", runtime.source?.workflow_run_id || "NOT_RUN"],
    ["Device", device.profile || "Pixel 2 · NOT_RUN"],
    ["Android API", device.api_level || "28 · NOT_RUN"],
    ["Resolution", Array.isArray(device.resolution_px) ? device.resolution_px.join(" × ") : "1080 × 1920 · NOT_RUN"]
  ];
  const summary = document.getElementById("runtimeSummary");
  summary.replaceChildren();
  for (const [label, value] of values) {
    const chip = document.createElement("div"); chip.className = "runtime-chip";
    const small = document.createElement("small"); small.textContent = label;
    const strong = document.createElement("strong"); strong.textContent = String(value);
    chip.append(small, strong); summary.append(chip);
  }
  const details = document.getElementById("runtimeDetails");
  details.replaceChildren();
  const heading = document.createElement("h3"); heading.textContent = "Runtime metadata"; details.append(heading);
  const info = runtime.artifacts || {};
  const runUrl = runtime.source?.workflow_run_url;
  const rows = [["Status", runtime.status || "NOT_RUN"], ["CI Run", runUrl || runtime.source?.workflow_run_id || "NOT_RUN"], ["Density", device.density_dpi ?? "NOT_RUN"], ["Insets", device.insets_px ? JSON.stringify(device.insets_px) : "NOT_RUN"], ["Screenshot", info.screenshot || "NOT_RUN"], ["Bounds", info.bounds || "NOT_RUN"], ["Motion MP4", info.motion_mp4 || "NOT_RUN"], ["APK SHA-256", info.apk_sha256 || "NOT_RUN"], ["APK Artifact", info.apk_artifact_url || "NOT_RUN"]];
  const dl = document.createElement("dl"); dl.className = "property-list runtime-details";
  for (const [label, value] of rows) { const dt = document.createElement("dt"); dt.textContent=label; const dd=document.createElement("dd"); if ((label === "CI Run" || label === "APK Artifact") && String(value).startsWith("https://")) { const link = document.createElement("a"); link.href = value; link.target = "_blank"; link.rel = "noreferrer"; link.textContent = label === "CI Run" ? `Run ${runtime.source?.workflow_run_id || "evidence"} ↗` : "Download APK artifact ↗"; dd.append(link); } else dd.textContent=String(value); dl.append(dt,dd); }
  details.append(dl);
  if (info.screenshot) {
    const image = document.createElement("img"); image.src = info.screenshot; image.alt = "Android API28 native Normal Home screenshot";
    const caption = document.createElement("p"); caption.className = "source-note"; caption.textContent = `Exact source ${runtime.source?.expected_source_sha || "NOT_RUN"} · runtime ${runtime.status || "NOT_RUN"}. Hero fixture provenance remains unverified.`;
    document.getElementById("runtimeImageWrap").replaceChildren(image, caption);
  } else {
    const title = document.getElementById("runtimeEmptyTitle");
    if (title) title.textContent = `Runtime = ${runtime.status || "NOT_RUN"}`;
  }
}

function renderDiffTable() {
  const target = document.getElementById("diffTable");
  target.replaceChildren();
  const table = document.createElement("table"); table.className = "diff-table";
  table.innerHTML = "<thead><tr><th>Component</th><th>Geometry</th><th>Glyph</th><th>Pixel / Asset</th><th>Status</th></tr></thead>";
  const tbody = document.createElement("tbody");
  const names = ["environment", "header", "statistics", "recent_header", "hero_selected", "hero_neighbor_left", "hero_neighbor_right", "cta", "camera"];
  const runtime = model.runtime || {};
  const bounds = runtime.bounds_summary || {};
  const pixel = runtime.pixel_diff_summary || {};
  for (const id of names) {
    const item = component(id); if (!item) continue;
    const row = document.createElement("tr");
    const geometryStatus = !runtime.source?.expected_source_sha ? "NOT_RUN" : (bounds.status || "REVIEW_REQUIRED");
    const geometryValue = id === "hero_selected" && bounds.components?.hero?.delta_px
      ? geometryStatus + " · Δ " + bounds.components.hero.delta_px.join(", ") + " px"
      : geometryStatus;
    let pixelValue = !runtime.source?.expected_source_sha ? "NOT_RUN" : (pixel.status || "REVIEW_REQUIRED");
    if (id === "hero_selected") pixelValue = "BLOCKED · photo rights";
    else if (id === "cta" && pixel.diffs?.CTA_NEAR_WHITE) pixelValue = pixel.diffs.CTA_NEAR_WHITE.status;
    else if (id === "camera" && pixel.diffs?.CAMERA_CORE_NEAR_WHITE) pixelValue = pixel.diffs.CAMERA_CORE_NEAR_WHITE.status;
    const glyphValue = !runtime.source?.expected_source_sha ? "NOT_RUN" : (pixel.status || "REVIEW_REQUIRED");
    const values = [item.name, geometryValue, glyphValue, pixelValue, item.status || "NOT_RUN"];
    values.forEach((value, index) => { const cell = document.createElement("td"); if (index === 0) { const strong = document.createElement("strong"); strong.textContent=value; cell.append(strong); } else cell.textContent=value; row.append(cell); });
    tbody.append(row);
  }
  table.append(tbody); target.append(table);

  const heatmap = document.getElementById("heatmapImage");
  if (runtime.artifacts?.pixel_heatmap && heatmap) {
    const image = document.createElement("img"); image.src = runtime.artifacts.pixel_heatmap; image.alt = "Frozen to Android diagnostic pixel heatmap";
    const note = document.createElement("p"); note.className = "source-note"; note.textContent = "DIAGNOSTIC_ONLY_REVIEW_REQUIRED · source " + (runtime.source?.expected_source_sha || "NOT_RUN") + "; full-screen MAE is descriptive, not a parity threshold.";
    heatmap.replaceChildren(image, note);
  }
}

function renderBrowserDiff() {
  const report = model.browserDiff || {};
  const status = report.status || "NOT_RUN";
  const badge = document.getElementById("browserDiffStatus");
  badge.textContent = status;
  badge.className = `status-pill ${statusClass(status)}`;
  const note = document.getElementById("browserDiffNote");
  note.textContent = status === "NOT_RUN"
    ? "等待 exact-source Chromium 截图；本比较不会替代 Android Runtime 或视觉 Gate。"
    : `DIAGNOSTIC_ONLY_REVIEW_REQUIRED · Source ${report.source?.source_sha || "NOT_RUN"} · ${report.classification || status}。Chromium 字体光栅与 Android 不同；未应用新阈值。`;
  const summary = document.getElementById("browserDiffSummary");
  summary.replaceChildren();
  const metrics = report.metrics || {};
  for (const [label, value] of [
    ["Changed pixels > 8", metrics.global_changed_pixel_fraction_over_8 ?? "NOT_RUN"],
    ["RGB MAE", Array.isArray(metrics.global_rgb_mae) ? metrics.global_rgb_mae.join(" / ") : "NOT_RUN"],
    ["ROI count", Array.isArray(report.rois) ? report.rois.length : 0]
  ]) {
    const item = document.createElement("div"); item.className = "runtime-chip";
    const small = document.createElement("small"); small.textContent = label;
    const strong = document.createElement("strong"); strong.textContent = String(value);
    item.append(small, strong); summary.append(item);
  }
  const images = document.getElementById("browserDiffImages");
  images.replaceChildren();
  const artifacts = report.artifacts || {};
  for (const [key, label] of [["structured_preview", "Chromium structured preview"], ["heatmap", "Frozen vs structured heatmap"], ["overlay", "50% overlay"]]) {
    const src = artifacts[key];
    if (!src) continue;
    const figure = document.createElement("figure");
    const image = document.createElement("img"); image.src = src; image.alt = label;
    const caption = document.createElement("figcaption"); caption.textContent = label;
    figure.append(image, caption); images.append(figure);
  }
  const tableHost = document.getElementById("browserDiffRois");
  tableHost.replaceChildren();
  if (Array.isArray(report.rois) && report.rois.length) {
    const table = document.createElement("table"); table.className = "diff-table";
    table.innerHTML = "<thead><tr><th>ROI</th><th>Bounds</th><th>Changed fraction</th><th>RGB MAE</th><th>Classification</th></tr></thead>";
    const body = document.createElement("tbody");
    for (const roi of report.rois) {
      const row = document.createElement("tr");
      for (const value of [roi.component, JSON.stringify(roi.bbox_inclusive || []), roi.changed_pixel_fraction_over_8, (roi.mean_absolute_difference_rgb || []).join(" / "), roi.verdict || roi.status || "REVIEW_REQUIRED"]) {
        const cell = document.createElement("td"); cell.textContent = String(value ?? "NOT_RUN"); row.append(cell);
      }
      body.append(row);
    }
    table.append(body); tableHost.append(table);
  }
}

function bindModeNavigation() {
  document.querySelectorAll(".mode-button").forEach(button => button.addEventListener("click", () => showMode(button.dataset.mode)));
  document.querySelectorAll("[data-select-view]").forEach(button => button.addEventListener("click", event => { event.preventDefault(); showMode(button.dataset.selectView); }));
}

function showMode(name) {
  document.querySelectorAll(".mode-button").forEach(button => button.classList.toggle("is-active", button.dataset.mode === name));
  document.querySelectorAll("[data-view]").forEach(section => section.classList.toggle("hidden", section.dataset.view !== name));
  if (name === "compare") setupCompare();
  resizePreviewStages();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function bindControls() {
  const zoom = document.getElementById("frozenZoom");
  const image = document.getElementById("frozenLarge");
  zoom?.addEventListener("input", () => { const value = Number(zoom.value); image.style.transform = `scale(${value})`; document.getElementById("frozenZoomValue").textContent = `${Math.round(value*100)}%`; });
  document.getElementById("frozenFullscreen")?.addEventListener("click", async () => {
    const target = document.querySelector(".image-workspace");
    try { if (!document.fullscreenElement) await target.requestFullscreen(); else await document.exitFullscreen(); }
    catch { toast("当前浏览器没有开放全屏权限"); }
  });
  ["showBounds", "showAnchors", "showGuides", "showSafeArea", "inspectorBounds", "inspectorAnchors", "inspectorGuides", "inspectorSafe"].forEach(id => document.getElementById(id)?.addEventListener("change", applyOverlays));
  document.getElementById("comparePair")?.addEventListener("change", setupCompare);
  document.getElementById("compareMode")?.addEventListener("change", setupCompare);
  document.getElementById("compareOpacity")?.addEventListener("input", () => {
    const value = Number(document.getElementById("compareOpacity").value);
    document.getElementById("compareOpacityValue").textContent = `${Math.round(value*100)}%`;
    document.getElementById("compareWorkspace").style.setProperty("--compare-opacity", String(value));
    setupCompare();
  });
  document.getElementById("heatZoom")?.addEventListener("change", () => document.getElementById("heatmapImage").style.setProperty("--zoom", document.getElementById("heatZoom").value));
}

function setupCompare() {
  const pair = document.getElementById("comparePair")?.value || "frozen-structured";
  const mode = document.getElementById("compareMode")?.value || "side";
  const workspace = document.getElementById("compareWorkspace");
  workspace.dataset.mode = mode;
  const panes = workspace.querySelector(".compare-panes");
  const status = document.getElementById("compareStatus");
  if (pair === "frozen-structured") {
    panes.replaceChildren();
    panes.append(makeComparePane("Frozen", "image"), makeComparePane("Structured Preview", "structured"));
    status.textContent = "Frozen vs Structured 对比已显示为诊断预览。鱼获照片和头像来源未确认；目前不计算视觉 PASS。";
  } else {
    panes.replaceChildren();
    const labels = pair === "frozen-android" ? ["Frozen", "Android Runtime"] : ["Structured Preview", "Android Runtime"];
    panes.append(makeComparePane(labels[0], labels[0] === "Frozen" ? "image" : "structured"), makeComparePane("Android Runtime", "android"));
    const runtime = model.runtime || {};
    status.textContent = runtime.artifacts?.screenshot
      ? `Android native screenshot from exact source ${runtime.source?.expected_source_sha || "NOT_RUN"}; runtime ${runtime.status || "NOT_RUN"}. Full parity remains separately gated.`
      : "Android Runtime screenshot = NOT_RUN. 此比较未运行，未使用浏览器预览替代设备画面。";
  }
  resizePreviewStages();
}

function makeComparePane(label, kind) {
  const pane = document.createElement("div"); pane.className = "compare-pane";
  const title = document.createElement("span"); title.textContent = label; pane.append(title);
  if (kind === "image") { const img = document.createElement("img"); img.src = ASSET_PATHS.frozen; img.alt = label; pane.append(img); }
  else if (kind === "structured") pane.append(makeArtboard());
  else if (kind === "android" && model.runtime?.artifacts?.screenshot) { const img = document.createElement("img"); img.src = model.runtime.artifacts.screenshot; img.alt = "Native Android API28 screenshot"; pane.append(img); }
  else { const empty = document.createElement("div"); empty.className = "unavailable-stage"; empty.innerHTML = "<div><strong>NOT_RUN</strong><small>无真实 API28 Android 截图</small></div>"; pane.append(empty); }
  return pane;
}

function renderFrozenMeta() {
  const manifest = model.source?.frozen_authority || {};
  const target = document.getElementById("frozenMeta");
  target.innerHTML = "";
  const dl = document.createElement("dl");
  for (const [label, value] of [["Image", manifest.repo_path], ["Dimensions", `${manifest.width} × ${manifest.height}`], ["SHA-256", manifest.sha256], ["Expected", manifest.expected_sha256], ["Status", manifest.status], ["Use", manifest.use]]) {
    const dt = document.createElement("dt"); dt.textContent=label;
    const dd = document.createElement("dd"); dd.textContent=String(value || "—"); dl.append(dt,dd);
  }
  target.append(dl);
}

function toast(message) {
  const node = document.getElementById("toast"); node.textContent=message; node.classList.add("is-visible");
  clearTimeout(toastTimer); toastTimer=setTimeout(() => node.classList.remove("is-visible"), 2400);
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[char]));
}

async function start() {
  try {
    await loadDocuments();
    window.NH01ViewerData = model;
    renderAllPreviews();
    renderOverview();
    renderFrozenMeta();
    bindModeNavigation();
    bindControls();
    window.addEventListener("resize", resizePreviewStages);
    document.querySelector('meta[name="nh01-source-sha"]')?.content && document.getElementById("viewerSha").replaceChildren(document.createTextNode(document.querySelector('meta[name="nh01-source-sha"]').content.slice(0,12)));
    const frozen = model.source?.frozen_authority;
    if (frozen?.sha256 !== frozen?.expected_sha256) toast("Frozen PNG SHA mismatch; source gate BLOCKED");
    document.getElementById("frozenLarge").addEventListener("error", () => toast("Frozen source image failed to load"));
    window.NH01ViewerModelReady = true;
    if (new URLSearchParams(window.location.search).get("export") === "1") {
      document.body.classList.add("export-preview");
      showMode("structured");
    } else {
      const requestedMode = new URLSearchParams(window.location.search).get("mode");
      if (["frozen", "structured", "inspector", "runtime", "compare", "heatmap"].includes(requestedMode)) showMode(requestedMode);
    }
  } catch (error) {
    console.error(error);
    document.body.insertAdjacentHTML("beforeend", `<div role="alert" class="load-error">NH01 data bundle load failed: ${escapeHtml(error.message)}</div>`);
  }
}

document.addEventListener("DOMContentLoaded", start);
