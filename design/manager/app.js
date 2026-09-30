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
let expandedLevelOne = new Set();

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
function isImage(path) { return !!path && /\.(png|jpe?g|webp|gif|svg)$/i.test(path); }

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

function navSearchActive() {
  return !!el("searchInput").value.trim();
}

function isLevelOneExpanded(navId, selectedDescendant = false) {
  return expandedLevelOne.has(navId) || selectedDescendant || navSearchActive();
}

function toggleLevelOneState(navId) {
  if (expandedLevelOne.has(navId)) expandedLevelOne.delete(navId);
  else expandedLevelOne.add(navId);
}

function navCaret(expanded) {
  return '<span class="nav-caret' + (expanded ? ' expanded' : '') + '" aria-hidden="true">›</span>';
}

function renderSharedNavItem(item) {
  const key = "shared/" + item.id;
  const hasChildren = item.navigation_mode === "submenu_direct" && (item.variants || []).length > 0;
  const navId = "shared:" + item.id;
  const selectedDescendant = hasChildren && !!selectedKey && selectedKey.startsWith(key + "/");
  const expanded = hasChildren && isLevelOneExpanded(navId, selectedDescendant);

  const parent =
    '<button class="module-item shared-item' + (hasChildren ? ' nav-parent' : '') +
    (selectedKey === key ? " active" : "") +
    '" data-kind="shared" data-id="' + esc(item.id) + '"' +
    (hasChildren ? ' data-nav-id="' + esc(navId) + '" aria-expanded="' + String(expanded) + '"' : '') + '>' +
    '<div class="module-name">' +
      (hasChildren ? navCaret(expanded) : '') +
      '<span>' + esc(item.display_name) + '</span>' +
      statusBadge(item.overall) +
    '</div>' +
    '<div class="module-path">' + esc(CATEGORY_LABELS[item.category] || item.category) +
    ' · ' + esc(item.current_version) + '</div></button>';

  if (!hasChildren) return parent;

  const children = (item.variants || []).map(v => {
    const variantKey = "shared/" + item.id + "/" + v.id;
    const showCode = item.id === "background_system_v1";
    return '<button class="shared-subitem' + (selectedKey === variantKey ? " active" : "") +
      '" data-kind="shared-variant" data-parent-id="' + esc(item.id) +
      '" data-id="' + esc(v.id) + '">' +
      (showCode ? '<span class="subitem-code">' + esc(v.id) + '</span>' : '') +
      '<span class="subitem-name">' + esc(v.name) + '</span>' +
      statusBadge(v.status || item.overall) +
      '</button>';
  }).join("");

  return parent + '<div class="shared-sublist nav-children' + (expanded ? ' expanded' : ' collapsed') + '">' +
    children + '</div>';
}

function renderSharedGroup(group, allItems) {
  const groupMatches = matchesSearch(group);
  const children = (group.item_ids || [])
    .map(id => allItems.find(item => item.id === id))
    .filter(Boolean)
    .filter(item => matchesStatus(item.overall))
    .filter(item => groupMatches || matchesSearch(item));

  if (!children.length) return "";

  const groupStatus = group.status || (
    children.every(item => item.overall === "FROZEN") ? "FROZEN" : "PARTIAL"
  );
  const navId = "group:" + group.id;
  const selectedDescendant = children.some(item => {
    const key = "shared/" + item.id;
    return selectedKey === key || (!!selectedKey && selectedKey.startsWith(key + "/"));
  });
  const expanded = isLevelOneExpanded(navId, selectedDescendant);

  return '<div class="shared-nav-group">' +
    '<button class="module-item shared-group-header nav-parent' + (selectedDescendant ? ' active-branch' : '') +
      '" data-kind="shared-group-toggle" data-nav-id="' + esc(navId) +
      '" aria-expanded="' + String(expanded) + '">' +
      '<div class="module-name">' + navCaret(expanded) +
        '<span>' + esc(group.display_name) + '</span>' +
        statusBadge(groupStatus) + '</div>' +
      '<div class="module-path">公共组件组 · ' + children.length + ' 项</div>' +
    '</button>' +
    '<div class="shared-sublist button-spec-sublist nav-children' + (expanded ? ' expanded' : ' collapsed') + '">' +
      children.map(item => {
        const key = "shared/" + item.id;
        return '<button class="shared-subitem shared-component-subitem' +
          (selectedKey === key ? " active" : "") +
          '" data-kind="shared-component" data-id="' + esc(item.id) + '">' +
          '<span class="subitem-code">' + esc(item.current_version || "V1") + '</span>' +
          '<span class="subitem-name">' + esc(item.display_name) + '</span>' +
          statusBadge(item.overall) +
          '</button>';
      }).join("") +
    '</div>' +
  '</div>';
}

function renderLists() {
  const allShared = sharedRegistry.items || [];
  const groups = sharedRegistry.navigation_groups || [];
  const groupedIds = new Set(groups.flatMap(group => group.item_ids || []));

  const ungroupedShared = allShared
    .filter(item => !groupedIds.has(item.id))
    .filter(item => matchesSearch(item) && matchesStatus(item.overall));

  const groupHtml = groups.map(group => renderSharedGroup(group, allShared)).filter(Boolean);
  const pages = pageRegistry.features.filter(x => x.navigation_hidden !== true).filter(x => matchesSearch(x) && matchesStatus(x.design_overall));

  el("sharedCount").textContent = ungroupedShared.length + groupHtml.length;
  el("pageCount").textContent = pages.length;

  const ungroupedById = new Map(ungroupedShared.map(item => [item.id, item]));
  const groupById = new Map(groups.map(group => [group.id, group]));
  const navigationOrder = sharedRegistry.navigation_order || [
    ...ungroupedShared.map(item => item.id),
    ...groups.map(group => "group:" + group.id)
  ];

  const orderedParts = navigationOrder.map(entry => {
    if (entry.startsWith("group:")) {
      const group = groupById.get(entry.slice("group:".length));
      return group ? renderSharedGroup(group, allShared) : "";
    }
    const item = ungroupedById.get(entry);
    return item ? renderSharedNavItem(item) : "";
  }).filter(Boolean);

  const orderedKeys = new Set(navigationOrder);
  const extras = [
    ...ungroupedShared
      .filter(item => !orderedKeys.has(item.id))
      .map(renderSharedNavItem),
    ...groups
      .filter(group => !orderedKeys.has("group:" + group.id))
      .map(group => renderSharedGroup(group, allShared))
      .filter(Boolean)
  ];

  const sharedHtml = [...orderedParts, ...extras].join("");

  el("sharedList").innerHTML =
    sharedHtml || '<div class="preview-empty compact">没有匹配的公共系统</div>';

  el("pageList").innerHTML = pages.map(feature => {
    const key = "page/" + feature.id;
    const hasChildren = (feature.hifi_views || []).length > 0;
    const navId = "page:" + feature.id;
    const selectedDescendant = hasChildren && !!selectedKey && selectedKey.startsWith(key + "/");
    const expanded = hasChildren && isLevelOneExpanded(navId, selectedDescendant);
    const parent = '<button class="module-item' + (hasChildren ? ' nav-parent' : '') +
      (selectedKey === key ? " active" : "") +
      '" data-kind="page" data-id="' + esc(feature.id) + '"' +
      (hasChildren ? ' data-nav-id="' + esc(navId) + '" aria-expanded="' + String(expanded) + '"' : '') + '>' +
      '<div class="module-name">' + (hasChildren ? navCaret(expanded) : '') +
      '<span>' + esc(feature.display_name || feature.id) + '</span>' +
      statusBadge(feature.design_overall) + '</div>' +
      '<div class="module-path">' + esc(feature.owner_path) + '</div></button>';

    const children = (feature.hifi_views || []).map(view => {
      const viewKey = "page/" + feature.id + "/hifi/" + view.id;
      const parentView =
        '<button class="page-subitem' +
          ((selectedKey === viewKey || (selectedKey && selectedKey.startsWith(viewKey + "/"))) ? " active" : "") +
          '" data-kind="page-hifi" data-page-id="' + esc(feature.id) +
          '" data-hifi-id="' + esc(view.id) + '">' +
          '<span class="subitem-name">' + esc(view.title) + '</span>' +
          statusBadge(view.status || "PARTIAL") +
        '</button>';

      const renderNestedChild = child => {
        const childKey = viewKey + "/" + child.id;
        const childCode = child.code || child.id.toUpperCase().replace("_"," · ");
        const childName = child.title.replace(/^[A-Z]\d+\s*·\s*/, "");
        return '<button class="page-subsubitem' + (selectedKey === childKey ? " active" : "") +
          '" data-kind="page-hifi-child" data-page-id="' + esc(feature.id) +
          '" data-hifi-id="' + esc(view.id) +
          '" data-hifi-child-id="' + esc(child.id) + '">' +
          '<span class="subsub-code">' + esc(childCode) + '</span>' +
          '<span class="subsub-name">' + esc(childName) + '</span>' +
          '<span class="subsub-type">' + esc(child.authority_type === "behavior" ? "行为" : "视觉") + '</span>' +
          statusBadge(child.status || view.status || "PARTIAL") +
        '</button>';
      };

      const childItems = view.children || [];
      const visualChildren = childItems.filter(child =>
        child.menu_group === "visual" ||
        (!child.menu_group && child.authority_type !== "behavior")
      );
      const stateChildren = childItems.filter(child => child.menu_group === "state");
      const specChildren = childItems.filter(child => child.menu_group === "spec");
      const referenceChildren = childItems.filter(child => child.menu_group === "visual_reference");
      const behaviorChildren = childItems.filter(child =>
        child.authority_type === "behavior" &&
        !["state","spec","visual_reference"].includes(child.menu_group)
      );
      const nested = [
        visualChildren.length
          ? '<div class="subsub-group-label">高保真页面</div>' + visualChildren.map(renderNestedChild).join("")
          : "",
        stateChildren.length
          ? '<div class="subsub-group-label">页面状态</div>' + stateChildren.map(renderNestedChild).join("")
          : "",
        specChildren.length
          ? '<div class="subsub-group-label">字段规范</div>' + specChildren.map(renderNestedChild).join("")
          : "",
        referenceChildren.length
          ? '<div class="subsub-group-label">视觉参考集</div>' + referenceChildren.map(renderNestedChild).join("")
          : "",
        behaviorChildren.length
          ? '<div class="subsub-group-label">交互规范</div>' + behaviorChildren.map(renderNestedChild).join("")
          : ""
      ].join("");

      return parentView + (nested ? '<div class="page-subsublist">' + nested + '</div>' : '');
    }).join("");

    return parent + (children
      ? '<div class="page-sublist nav-children' + (expanded ? ' expanded' : ' collapsed') + '">' + children + '</div>'
      : '');
  }).join("") || '<div class="preview-empty compact">没有匹配的页面</div>';

  document.querySelectorAll(".module-item[data-kind]").forEach(btn => {
    btn.addEventListener("click", () => {
      const kind = btn.dataset.kind;
      if (kind === "shared-group-toggle") {
        const isExpanded = btn.getAttribute("aria-expanded") === "true";
        if (isExpanded) expandedLevelOne.delete(btn.dataset.navId);
        else expandedLevelOne.add(btn.dataset.navId);
        renderLists();
        return;
      }

      if (btn.dataset.navId) {
        const isExpanded = btn.getAttribute("aria-expanded") === "true";
        if (isExpanded) expandedLevelOne.delete(btn.dataset.navId);
        else expandedLevelOne.add(btn.dataset.navId);
      }

      if (kind === "shared") selectShared(btn.dataset.id);
      else if (kind === "page") selectPage(btn.dataset.id);
    });
  });

  document.querySelectorAll(".shared-subitem[data-kind='shared-component']").forEach(btn => {
    btn.addEventListener("click", () => {
      const group = (sharedRegistry.navigation_groups || []).find(g =>
        (g.item_ids || []).includes(btn.dataset.id)
      );
      if (group) expandedLevelOne.add("group:" + group.id);
      selectShared(btn.dataset.id);
    });
  });

  document.querySelectorAll(".shared-subitem[data-kind='shared-variant']").forEach(btn => {
    btn.addEventListener("click", () => {
      const parentId = btn.dataset.parentId;
      expandedLevelOne.add("shared:" + parentId);
      selectShared(parentId, btn.dataset.id);
    });
  });

  document.querySelectorAll(".page-subitem[data-kind='page-hifi']").forEach(btn => {
    btn.addEventListener("click", () => {
      expandedLevelOne.add("page:" + btn.dataset.pageId);
      selectPage(btn.dataset.pageId, null, btn.dataset.hifiId, null);
    });
  });

  document.querySelectorAll(".page-subsubitem[data-kind='page-hifi-child']").forEach(btn => {
    btn.addEventListener("click", () => {
      expandedLevelOne.add("page:" + btn.dataset.pageId);
      selectPage(btn.dataset.pageId, null, btn.dataset.hifiId, btn.dataset.hifiChildId);
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

function actionButtonPreviewHtml(item) {
  const frozenSet = item.frozen_visual_authority_set || [];
  const frozenBlock = frozenSet.length
    ? '<section class="action-frozen-authority action-authority-set">' +
        '<div class="action-frozen-head">' +
          '<div><span>FROZEN VISUAL AUTHORITY SET</span><strong>01–06 · 完整静态 UI Authority</strong></div>' +
          '<div class="action-frozen-meta">V1.1 · ' + frozenSet.length + ' references · all SHA-256 registered</div>' +
        '</div>' +
        '<div class="action-authority-grid">' +
          frozenSet.map((ref,index) =>
            '<article class="action-authority-card">' +
              '<div class="action-authority-card-head">' +
                '<strong>' + esc(ref.id || String(index + 1).padStart(2,"0")) + '</strong>' +
                '<span>' + esc(ref.width) + '×' + esc(ref.height) + ' · SHA ' +
                  esc(String(ref.sha256 || "").slice(0,10)) + '…</span>' +
              '</div>' +
              '<a class="action-frozen-image" href="' + esc(repoHref(ref.path)) +
                '" target="_blank" rel="noreferrer">' +
                '<img src="' + esc(repoHref(ref.path)) +
                '" alt="' + esc(ref.id || "Action Button Authority") + '">' +
              '</a>' +
              '<div class="action-authority-coverage">' +
                (ref.covers || []).map(x => '<span>' + esc(x) + '</span>').join("") +
              '</div>' +
            '</article>'
          ).join("") +
        '</div>' +
        '<div class="action-frozen-note">01–06 六份静态图共同组成最终 UI Authority。任意状态均可独立用于开发和 QA；旧综合图仅保留为 Overview。</div>' +
      '</section>' +
      '<div class="action-live-divider"><span>LIVE PREVIEW · SUPPLEMENTAL</span></div>'
    : '';

  const demo = (label, variant, state = "normal", extra = "") => {
    const cls =
      variant === "PRIMARY" ? "action-primary" :
      variant === "SECONDARY_STRONG" ? "action-secondary-strong" :
      "action-secondary-muted";
    const stateCls = state === "normal" ? "" : " action-state-" + state;
    const content = state === "loading"
      ? '<span class="action-spinner" aria-hidden="true"></span>'
      : esc(label);
    return '<button type="button" class="action-demo ' + cls + stateCls + ' ' + extra + '">' +
      content + '</button>';
  };

  return frozenBlock + '<div class="action-preview-shell action-v11">' +
    '<div class="action-preview-title-row">' +
      '<div><div class="action-preview-kicker">ACTION BUTTON SYSTEM</div>' +
      '<div class="action-preview-title">Primary / Secondary Action Button V1.1</div></div>' +
      '<span class="action-version-pill">FROZEN · V1.1</span>' +
    '</div>' +

    '<section class="action-preview-section">' +
      '<div class="action-section-head"><strong>01 · 三档视觉</strong><span>56dp · R28 · Semibold 600</span></div>' +
      '<div class="action-preview-variants">' +
        '<div class="action-preview-card"><span>PRIMARY</span>' + demo("保存本次鱼获","PRIMARY") +
          '<small>Deep Lake Teal · restrained surface depth</small></div>' +
        '<div class="action-preview-card"><span>SECONDARY_STRONG</span>' + demo("继续记录记忆","SECONDARY_STRONG") +
          '<small>Mist / Lake White · teal light border</small></div>' +
        '<div class="action-preview-card"><span>SECONDARY_MUTED</span>' + demo("重新拍摄","SECONDARY_MUTED") +
          '<small>Recovery / fallback · deliberately quiet</small></div>' +
      '</div>' +
    '</section>' +

    '<section class="action-preview-section">' +
      '<div class="action-section-head"><strong>02 · 单按钮实际场景</strong><span>页面左右 24dp · Fill available width</span></div>' +
      '<div class="action-single-scenes">' +
        '<div class="action-scene-card"><b>Login</b>' + demo("登录","PRIMARY","normal","action-full") + '</div>' +
        '<div class="action-scene-card"><b>Register</b>' + demo("注册并登录","PRIMARY","normal","action-full") + '</div>' +
      '</div>' +
    '</section>' +

    '<section class="action-preview-section">' +
      '<div class="action-section-head"><strong>03 · 双按钮实际组合</strong><span>Gap 12dp · 非固定 50/50</span></div>' +
      '<div class="action-preview-pairs">' +
        '<div class="action-pair-block"><b>识别结果 · 正常保存</b><span class="action-ratio-label">44 / 56</span>' +
          '<div class="action-pair action-pair-44-56">' +
            '<div>' + demo("继续记录记忆","SECONDARY_STRONG") + '</div>' +
            '<div>' + demo("保存本次鱼获","PRIMARY") + '</div>' +
          '</div></div>' +
        '<div class="action-pair-block"><b>识别结果 · 低置信</b><span class="action-ratio-label">58 / 42</span>' +
          '<div class="action-pair action-pair-58-42">' +
            '<div>' + demo("手动选择","SECONDARY_STRONG") + '</div>' +
            '<div>' + demo("重新拍摄","SECONDARY_MUTED") + '</div>' +
          '</div></div>' +
      '</div>' +
    '</section>' +

    '<section class="action-preview-section">' +
      '<div class="action-section-head"><strong>04 · 状态</strong><span>Normal / Pressed / Loading / Disabled / Focus</span></div>' +
      '<div class="action-state-grid">' +
        '<div class="action-state-card"><span>Normal</span>' + demo("保存本次鱼获","PRIMARY") + '</div>' +
        '<div class="action-state-card"><span>Pressed</span>' + demo("保存本次鱼获","PRIMARY","pressed") + '</div>' +
        '<div class="action-state-card"><span>Loading</span>' + demo("","PRIMARY","loading") + '</div>' +
        '<div class="action-state-card"><span>Disabled</span>' + demo("保存本次鱼获","PRIMARY","disabled") + '</div>' +
        '<div class="action-state-card"><span>Focus</span>' + demo("保存本次鱼获","PRIMARY","focus") + '</div>' +
      '</div>' +
    '</section>' +

    '<div class="preview-note">普通业务 Primary 不使用金色；Reduce Motion 关闭 scale，仅保留按压颜色/表面反馈。SECONDARY_MUTED Loading = N/A；主拍摄按钮继续独立管理。</div>' +
  '</div>';
}

function textActionPreviewHtml(item) {
  const refs = item.frozen_visual_authority_set || item.candidate_visual_set || [];
  const cards = refs.map(ref =>
    '<article class="text-action-ref-card">' +
      '<div class="text-action-ref-head"><strong>' + esc(ref.title || ref.id) + '</strong>' +
      statusBadge(ref.status || "CANDIDATE") + '</div>' +
      '<a href="' + esc(repoHref(ref.path)) + '" target="_blank" rel="noreferrer">' +
        '<img src="' + esc(repoHref(ref.path)) + '" alt="' + esc(ref.title || ref.id) + '">' +
      '</a>' +
    '</article>'
  ).join("");

  return '<div class="text-action-preview">' +
    '<div class="text-action-review-banner">' +
      '<div><span>TEXT ACTION V1</span><strong>FROZEN · V1</strong></div>' +
      '<p>已冻结：忘记密码？=MUTED；Chevron=独立 14dp 图标；ON_MEDIA 使用反白文字与轻阴影。</p>' +
    '</div>' +
    '<div class="text-action-ref-grid">' + cards + '</div>' +
    '<div class="preview-note">当前状态 FROZEN。六份静态 UI Authority 与页面映射已同步；Live/页面实现不得私自改色或把 Chevron 写进文案。</div>' +
  '</div>';
}

function iconActionPreviewHtml(item) {
  const refs = item.frozen_visual_authority_set || item.candidate_visual_set || [];
  return '<div class="icon-action-preview">' +
    '<div class="text-action-review-banner">' +
      '<div><span>ICON ACTION V1</span><strong>FROZEN · V1</strong></div>' +
      '<p>已冻结：Navigation / Utility / Context 三大家族；B-side Card Flip=UTILITY/ON_MEDIA；默认透明容器，复杂媒体按需使用 36dp Mist 支撑面。</p>' +
    '</div>' +
    '<div class="text-action-ref-grid">' +
      refs.map(ref => '<article class="text-action-ref-card">' +
        '<div class="text-action-ref-head"><strong>' + esc(ref.title || ref.id) + '</strong>' +
        statusBadge(ref.status || "CANDIDATE") + '</div>' +
        '<a href="' + esc(repoHref(ref.path)) + '" target="_blank" rel="noreferrer">' +
          '<img src="' + esc(repoHref(ref.path)) + '" alt="' + esc(ref.title || ref.id) + '">' +
        '</a></article>').join("") +
    '</div>' +
    '<div class="preview-note">当前状态 FROZEN。六份静态组件 Authority 与消费页面映射已同步；不得把装饰图标、Text Action Chevron 或主拍摄按钮误归为 Icon Action。</div>' +
  '</div>';
}

function backgroundSystemOverviewHtml(item) {
  const masters = item.masters || [];
  const cards = masters.map(master =>
    '<article class="bg-master-card">' +
      '<div class="bg-source-label">' + esc(master.name || master.id) + '</div>' +
      '<a href="' + esc(repoHref(master.path)) + '" target="_blank" rel="noreferrer">' +
        '<img src="' + esc(repoHref(master.path)) + '" alt="' + esc(master.name || master.id) + '">' +
      '</a>' +
      '<div class="bg-master-meta">' +
        '<strong>' + esc(master.id) + '</strong>' +
        '<span>' + esc(master.usage || "") + '</span>' +
        '<span>' + esc(String(master.width || "")) + ' × ' + esc(String(master.height || "")) + '</span>' +
      '</div>' +
    '</article>'
  ).join("");

  return '<div class="bg-system-overview">' +
    '<div class="bg-system-intro">' +
      '<strong>Morning Lake Background System V1</strong>' +
      '<span>两张 Canonical Master；5 种背景类型统一从左侧子菜单进入独立工作区。</span>' +
    '</div>' +
    '<div class="bg-master-grid">' + cards + '</div>' +
    '<div class="preview-note">父级只负责系统总览与母版。BG_ENV_HERO / BG_CONTENT / BG_DATA / BG_CAPTURE / BG_SOLID_FALLBACK 不在这里重复生成子页面。</div>' +
  '</div>';
}

function sharedPreviewHtml(item) {
  if (item.id === "background_system_v1") {
    return backgroundSystemOverviewHtml(item);
  }
  if (item.id === "top_navigation_v1") {
    return topNavigationOverviewHtml(item);
  }
  if (item.preview_type === "layered_component" && item.id === "primary_capture_button_v1") {
    return layeredCapturePreviewHtml(item);
  }
  if (item.preview_type === "action_button_system" && item.id === "action_button_v1") {
    return actionButtonPreviewHtml(item);
  }
  if (item.preview_type === "text_action_system" && item.id === "text_action_v1") {
    return textActionPreviewHtml(item);
  }
  if (item.preview_type === "icon_action_system" && item.id === "icon_action_v1") {
    return iconActionPreviewHtml(item);
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

function topNavigationOverviewHtml(item) {
  return '<div class="top-nav-overview">' +
    '<div class="bg-system-intro">' +
      '<strong>Top Navigation V1</strong>' +
      '<span>顶部导航只负责布局组合，不重复定义 Icon Action。当前拆成 3 个直接子菜单。</span>' +
    '</div>' +
    '<div class="direct-variant-rules">' +
      '<div class="direct-rule-card"><b>01 · 标题</b><span>Root 页面；无 Back；Search / Filter 不属于顶部导航。</span></div>' +
      '<div class="direct-rule-card"><b>02 · 返回 + 标题</b><span>二级页面；Back 使用 Icon Action V1 / NAVIGATION。</span></div>' +
      '<div class="direct-rule-card"><b>03 · 返回 + 标题 + 工具动作</b><span>二级页面 + 1–2 个 Utility；超出后进入 More。</span></div>' +
    '</div>' +
    '<div class="preview-note">旧 search_filter Variant 已移除；我的鱼获 Search/Filter 与鱼鉴 Search 均不再作为 Top Navigation 结构。</div>' +
  '</div>';
}

function directSharedVariantPages(sharedId, variantId) {
  return pageRegistry.features.filter(page =>
    (page.shared_system_refs || []).some(ref =>
      ref.id === sharedId && ref.variant === variantId
    )
  );
}

function renderSharedDirectVariantWorkspace(item, variantId) {
  const panel = el("sharedDirectVariantPanel");
  const isDirectNonBackground =
    item.navigation_mode === "submenu_direct" &&
    item.id !== "background_system_v1" &&
    !!variantId;

  if (!isDirectNonBackground) {
    panel.classList.add("hidden");
    return;
  }

  const variant = (item.variants || []).find(v => v.id === variantId);
  if (!variant) {
    panel.classList.add("hidden");
    return;
  }

  panel.classList.remove("hidden");
  el("sharedDirectVariantEyebrow").textContent = item.display_name + " · 子菜单";
  el("sharedDirectVariantTitle").textContent = variant.name;
  el("sharedDirectVariantStatus").innerHTML = statusBadge(variant.status || item.overall);

  const summary = variant.usage || variant.note || "";
  if (summary) {
    el("sharedDirectVariantSummary").textContent = summary;
    el("sharedDirectVariantSummary").classList.remove("hidden");
  } else {
    el("sharedDirectVariantSummary").classList.add("hidden");
  }

  const rules = [];
  if (variant.note) rules.push(variant.note);
  if (variant.id === "TITLE_ONLY") {
    rules.push("无 Back；页面标题为顶部主要视觉。");
    rules.push("Search / Filter 归页面内容区或输入/工具系统。");
  } else if (variant.id === "BACK_TITLE") {
    rules.push("Back → Icon Action V1 / NAVIGATION。");
    rules.push("标题视觉权重高于返回图标。");
  } else if (variant.id === "BACK_TITLE_ACTIONS") {
    rules.push("Back → Icon Action V1 / NAVIGATION。");
    rules.push("右侧动作 → Icon Action V1 / UTILITY。");
    rules.push("直接展示 1–2 个 Utility；未来超出上限进入 More。");
  }

  el("sharedDirectVariantRules").innerHTML = rules.map(rule =>
    '<div class="direct-rule-card">' + esc(rule) + '</div>'
  ).join("") || '<div class="preview-empty compact">暂无补充规则。</div>';

  const visualRefs = variant.visual_authority_set || [];
  const visualWrap = el("sharedDirectVariantVisualWrap");
  if (visualRefs.length) {
    visualWrap.classList.remove("hidden");
    el("sharedDirectVariantVisual").innerHTML = visualRefs.map(ref =>
      '<article class="direct-variant-visual-card">' +
        '<div class="direct-variant-visual-head"><strong>' + esc(ref.title || ref.id) + '</strong>' +
          '<span>SHA ' + esc(String(ref.sha256 || "").slice(0,10)) + '…</span></div>' +
        '<a href="' + esc(repoHref(ref.path)) + '" target="_blank" rel="noreferrer">' +
          '<img src="' + esc(repoHref(ref.path)) + '" alt="' + esc(ref.title || ref.id) + '">' +
        '</a>' +
      '</article>'
    ).join("");
  } else {
    visualWrap.classList.add("hidden");
    el("sharedDirectVariantVisual").innerHTML = "";
  }

  const usage = directSharedVariantPages(item.id, variant.id);
  el("sharedDirectVariantUsage").innerHTML = usage.map(page =>
    '<button class="usage-row direct-variant-page-link" data-page-id="' + esc(page.id) + '">' +
      '<span>' + esc(page.display_name || page.id) + '</span>' +
      '<span class="usage-variant">' + esc(variant.id) + '</span></button>'
  ).join("") || '<div class="preview-empty compact">尚未映射页面。</div>';

  const authorityPaths = [
    variant.authority_path,
    ...(item.authority_paths || [])
  ].filter((value,index,array) => value && array.indexOf(value) === index);

  el("sharedDirectVariantAuthority").innerHTML = authorityPaths.map(path =>
    '<a class="authority-row" href="' + esc(repoHref(path)) +
    '" target="_blank" rel="noreferrer">' + esc(path) + '</a>'
  ).join("");

  document.querySelectorAll(".direct-variant-page-link[data-page-id]").forEach(btn =>
    btn.addEventListener("click", () => selectPage(btn.dataset.pageId))
  );
}

function selectShared(id, variantId = null) {
  const item = sharedRegistry.items.find(x => x.id === id);
  if (!item) return;

  const isBackground = item.id === "background_system_v1";
  const selectedVariant = variantId
    ? (item.variants || []).find(v => v.id === variantId)
    : null;
  const directVariantMode = item.navigation_mode === "submenu_direct";

  selectedKey = "shared/" + id + (variantId ? "/" + variantId : "");
  history.replaceState(
    null,
    "",
    "#shared/" + encodeURIComponent(id) + (variantId ? "/" + encodeURIComponent(variantId) : "")
  );
  renderLists();
  hideAllDetails();
  el("sharedDetail").classList.remove("hidden");

  el("pageEyebrow").textContent = selectedVariant
    ? "渔见 · 公共设计系统 · " + item.display_name
    : "渔见 · 公共设计系统";
  el("pageTitle").textContent = selectedVariant
    ? (selectedVariant.id + " · " + selectedVariant.name)
    : item.display_name;
  el("pageStatus").innerHTML = statusBadge(selectedVariant?.status || item.overall);

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

  // submenu_direct items use the sidebar as the only child-entry surface.
  // The old right-side Variant card index is intentionally suppressed.
  const overviewHero = el("sharedOverviewHero");
  const variantPanel = el("sharedVariantPanel");
  const authorityPanel = el("sharedAuthorityPanel");

  if (directVariantMode) {
    variantPanel.classList.add("hidden");
    el("variantGrid").innerHTML = "";

    if (selectedVariant) {
      overviewHero.classList.add("hidden");
      authorityPanel.classList.add("hidden");
    } else {
      overviewHero.classList.remove("hidden");
      authorityPanel.classList.remove("hidden");
    }
  } else {
    overviewHero.classList.remove("hidden");
    variantPanel.classList.remove("hidden");
    authorityPanel.classList.remove("hidden");

    el("variantGrid").innerHTML = (item.variants || []).map(v =>
      '<div class="variant-card">' +
        backgroundVariantPreview(item, v) +
        '<div class="variant-id">' + esc(v.id) + '</div>' +
        '<div class="variant-name">' + esc(v.name) + '</div>' +
        '<div class="variant-usage">' + esc(v.usage || "") + '</div>' +
        '<div class="variant-card-status">' + statusBadge(v.status || item.overall) + '</div>' +
        (v.note ? '<div class="variant-note">' + esc(v.note) + '</div>' : '') +
      '</div>'
    ).join("") || '<div class="preview-empty">尚未登记变体。</div>';
  }

  renderBackgroundVariantWorkspace(item, variantId);
  renderSharedDirectVariantWorkspace(item, variantId);

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

function setPanelHidden(id, hidden) {
  const node = el(id);
  if (!node) return;
  node.classList.toggle("hidden", !!hidden);
}

function applyPageDetailMode(mode) {
  const overview = mode === "overview";
  const hifi = mode === "visual" || mode === "behavior" || mode === "hifi-index";
  const scenario = mode === "scenario";

  setPanelHidden("pageOverviewHero", !overview);
  setPanelHidden("hifiViewPanel", !hifi);
  setPanelHidden("scenarioPanel", !scenario);

  ["designSectionsPanel","freezeReviewPanel","sharedRefsPanel","modalitiesPanel","versionsPanel"]
    .forEach(id => setPanelHidden(id, !overview));
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

function hifiSearchModeBar(value="") {
  return '<div class="hifi-search-mode">' +
    hifiSearchBar(value,true) +
    '<button>取消</button>' +
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

function hifiDayDetailContent(total=12) {
  const rows=[
    ["草鱼","42.6 cm · 1.28 kg","第100条"],
    ["鲫鱼","28.3 cm · 0.52 kg",""],
    ["黄骨鱼","24.1 cm · 0.32 kg","首条黄骨鱼"],
    ["翘嘴鲌","32.7 cm · 0.68 kg","最长"],
    ["鳜鱼","31.2 cm · 0.74 kg",""],
    ["鲤鱼","39.1 cm · 1.12 kg",""]
  ];
  return '<div class="hifi-page-content day-detail">' +
    '<div class="hifi-page-header"><span class="hifi-back">‹</span><strong>9月23日</strong><span></span></div>' +
    '<div class="hifi-archive-summary">千岛湖 · ' + total + '条鱼获 · 当天详情</div>' +
    '<div class="hifi-day-detail-note">当天全部鱼获</div>' +
    '<div class="hifi-day-detail-list">' + rows.map(x=>hifiRow(...x)).join("") + '</div>' +
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
    filter:["没有找到符合条件的鱼获","试试调整筛选条件","清除筛选","修改筛选"],
    search:["没有找到相关鱼获","你可以搜索：鱼种、地点、日期","清除搜索",""]
  }[kind];
  const top = kind==="search" ? hifiSearchModeBar("鳄鱼") : hifiSearchBar();
  const filter = kind==="filter" ? '<div class="hifi-filter-pills active"><span>青鱼 ×</span><span>近7天 ×</span><span>千岛湖 ×</span></div>' : '';
  return '<div class="hifi-page-content">' + hifiHeader() + top + filter +
    '<div class="hifi-month">2026年9月</div>' +
    '<div class="hifi-empty-state"><strong>'+cfg[0]+'</strong><p>'+cfg[1]+'</p><button>'+cfg[2]+'</button>' +
    (cfg[3]?'<a>'+cfg[3]+'</a>':'') + '</div>' +
    '<div class="hifi-camera-button">▣</div>' +
  '</div>';
}


function bgDataScreen(inner, label = "") {
  const bgSystem = sharedRegistry.items.find(x => x.id === "background_system_v1");
  const bgVariant = (bgSystem?.variants || []).find(v => v.id === "BG_DATA");
  const t = bgVariant?.preview_treatment || {};
  const source = (bgVariant?.preview_sources || [])[0]?.path || bgSystem?.master?.path;
  const filter = [
    "saturate(" + Math.round((t.saturation ?? 1) * 100) + "%)",
    "contrast(" + Math.round((t.contrast ?? 1) * 100) + "%)",
    "brightness(" + Math.round((t.brightness ?? 1) * 100) + "%)"
  ].join(" ");

  return '<div class="hifi-app-canvas-wrap">' +
    (label ? '<div class="hifi-app-canvas-label">' + esc(label) + '</div>' : '') +
    '<div class="hifi-app-canvas">' +
      (source ? '<img class="hifi-bg" src="' + esc(repoHref(source)) +
        '" style="filter:' + esc(filter) + '" alt="BG_DATA">' : '') +
      '<span class="hifi-mist" style="opacity:' + Number(t.mist_alpha || 0) + '"></span>' +
      inner +
    '</div></div>';
}

function hifiSystemStatusBar() {
  return '<div class="search-statusbar"><strong>9:41</strong><span>▮▮▮　⌁　▰</span></div>';
}

function hifiSearchHeader(value = "") {
  return '<div class="search-v1-header">' +
    '<span class="search-v1-back">‹</span>' +
    '<div class="search-v1-field">' +
      '<span class="search-v1-icon">⌕</span>' +
      '<span class="' + (value ? "value" : "placeholder") + '">' +
        esc(value || "搜索鱼种、地点或日期") + '</span>' +
      (value ? '<span class="search-v1-clear">×</span>' : '') +
    '</div>' +
    '<button class="search-v1-cancel">取消</button>' +
  '</div>';
}

function hifiRecentSearches() {
  const items=["草鱼","千岛湖","9月23日","首条草鱼","最长","第100条"];
  return '<div class="search-recent-card">' +
    '<div class="search-recent-head"><strong>最近搜索</strong><button>清空</button></div>' +
    '<div class="search-recent-tags">' +
      items.map(x=>'<span>'+esc(x)+'</span>').join("") +
    '</div>' +
  '</div>';
}

function searchTimelineDay(day, summary, records) {
  return '<div class="search-day">' +
    '<div class="search-day-rail"><strong>'+esc(day)+'</strong><span>SEP</span></div>' +
    '<div class="search-day-body">' +
      '<div class="search-day-summary">'+esc(summary)+'</div>' +
      '<div class="search-result-rows">' + records.map(r=>hifiRow(r[0],r[1],r[2]||"")).join("") + '</div>' +
    '</div>' +
  '</div>';
}

function hifiSearchStateContent(state) {
  if (state === "recent") {
    return '<div class="search-v1-screen">' +
      hifiSystemStatusBar() +
      hifiSearchHeader("") +
      hifiRecentSearches() +
      '<div class="search-month">2026年9月</div>' +
      searchTimelineDay("23","千岛湖 · 3条鱼获 · 2种鱼",[
        ["草鱼","42.6 cm · 1.28 kg","最长"],
        ["鲫鱼","28.3 cm · 0.52 kg",""],
        ["黄骨鱼","24.1 cm · 0.32 kg","首条黄骨鱼"]
      ]) +
      '<div class="search-camera-fixed">▣</div>' +
    '</div>';
  }

  if (state === "results") {
    return '<div class="search-v1-screen">' +
      hifiSystemStatusBar() +
      hifiSearchHeader("草鱼") +
      '<div class="search-result-summary">2 次鱼获</div>' +
      '<div class="search-month">2026年9月</div>' +
      searchTimelineDay("23","千岛湖 · 1条鱼获 · 1种鱼",[
        ["草鱼","42.6 cm · 1.28 kg","最长"]
      ]) +
      searchTimelineDay("03","千岛湖 · 1条鱼获 · 1种鱼",[
        ["草鱼","32.1 cm · 0.86 kg",""]
      ]) +
      '<div class="search-camera-fixed">▣</div>' +
    '</div>';
  }

  if (state === "empty") {
    return '<div class="search-v1-screen">' +
      hifiSystemStatusBar() +
      hifiSearchHeader("鳄鱼") +
      '<div class="search-empty-v1">' +
        '<div class="search-empty-symbol">⌕</div>' +
        '<strong>没有找到相关鱼获</strong>' +
        '<p>你可以搜索：鱼种、地点、日期</p>' +
        '<button>清除搜索</button>' +
      '</div>' +
      '<div class="search-camera-fixed">▣</div>' +
    '</div>';
  }

  if (state === "search_filter") {
    return '<div class="search-v1-screen">' +
      hifiSystemStatusBar() +
      hifiSearchHeader("草鱼") +
      '<button class="search-filter-summary">筛选中 · 3个条件　<span>今年 · 千岛湖 · ≥40cm</span>　›</button>' +
      '<div class="search-result-summary">1 次鱼获</div>' +
      '<div class="search-month">2026年9月</div>' +
      searchTimelineDay("23","千岛湖 · 1条鱼获 · 1种鱼",[
        ["草鱼","42.6 cm · 1.28 kg","最长"]
      ]) +
      '<div class="search-camera-fixed">▣</div>' +
    '</div>';
  }

  return "";
}

function hifiSearchRulesBoard() {
  const rules=[
    ["×","只清空 query","保持 Search Mode / 键盘 / Filter"],
    ["取消","退出 Search Mode","清 query，关闭键盘，保留 Filter"],
    ["Android Back · 1","收起键盘","不退出搜索"],
    ["Android Back · 2","退出 Search Mode","恢复进入搜索前 Timeline 位置"],
    ["点击鱼获","进入 FishRecordDetail","返回后 query / Filter / scroll 均恢复"],
    ["Recent Search","仅保存有效搜索","点击结果或有效 query 退出时写入，最多6个"]
  ];
  return '<div class="search-rules-board">' +
    '<div class="search-rules-title"><strong>Search V1 · 交互状态合同</strong>' +
      '<span>搜索是 My Catches 的查看状态，不是新页面。</span></div>' +
    '<div class="search-rules-grid">' +
      rules.map((r,i)=>'<div class="search-rule-card">' +
        '<span class="search-rule-index">'+String(i+1).padStart(2,"0")+'</span>' +
        '<strong>'+esc(r[0])+'</strong><b>'+esc(r[1])+'</b><p>'+esc(r[2])+'</p>' +
      '</div>').join("") +
    '</div>' +
    '<div class="search-rules-formula">SearchMatch <b>AND</b> FilterMatch　·　结果继续使用 Month → Day → FishRecordRowCard</div>' +
  '</div>';
}

function myCatchesSearchChildCanvas(child) {
  if (!child) return '<div class="preview-empty">请选择 Search V1 子项。</div>';

  if (child.render_mode === "frozen_image" && child.visual_authority) {
    const dims = child.visual_authority_dimensions || {};
    const dimensionText = dims.width && dims.height ? (dims.width + "×" + dims.height) : "尺寸已登记";
    const sourceText = child.source_high_fidelity_png ? (" · Source: " + child.source_high_fidelity_png) : "";
    return '<div class="frozen-authority-view">' +
      '<a href="' + esc(repoHref(child.visual_authority)) + '" target="_blank" rel="noreferrer">' +
        '<img src="' + esc(repoHref(child.visual_authority)) + '" alt="' + esc(child.title) + '">' +
      '</a>' +
      '<div class="frozen-authority-caption">FROZEN VISUAL AUTHORITY · ' + esc(dimensionText) +
        ' · 无设备外框' + esc(sourceText) + '</div>' +
    '</div>';
  }

  if (child.render_mode === "behavior_spec" || child.authority_type === "behavior") {
    return hifiSearchRulesBoard();
  }

  if (child.render_mode === "search_state") {
    return '<div class="search-state-stage">' +
      bgDataScreen(hifiSearchStateContent(child.state), child.title) +
    '</div>';
  }

  return '<div class="preview-empty">该子项尚未建立 Authority。</div>';
}

function myCatchesSearchOverview(view) {
  const children=view.children||[];
  return '<div class="authority-index">' +
    '<div class="authority-index-intro"><strong>Search V1 · Authority Index</strong>' +
      '<span>B1–B4 为冻结视觉 Authority；B5 为行为合同。选择左侧二级菜单查看对应 Authority。</span></div>' +
    '<div class="authority-index-grid">' +
      children.map(child =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(child.title) + '</strong>' +
            statusBadge(child.status || "PARTIAL") + '</div>' +
          '<div class="authority-kind">' + esc(child.authority_type === "behavior" ? "BEHAVIOR CONTRACT" : "VISUAL AUTHORITY") + '</div>' +
          '<p>' + esc(child.summary || "") + '</p>' +
        '</article>'
      ).join("") +
    '</div>' +
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
    return '<div class="hifi-board four">' +
      bgDataPhone(hifiTimelineContent({count:3,total:3}),"1–5 条 · 全部展开") +
      bgDataPhone(hifiTimelineContent({count:5,total:8,action:"查看另外 3 条鱼获"}),"6–10 条 · 原位展开") +
      bgDataPhone(hifiTimelineContent({count:5,total:12,action:"查看全部 12 条鱼获"}),">10 条 · 主 Timeline") +
      bgDataPhone(hifiDayDetailContent(12),">10 条 · 点击后的当天详情") +
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
        hifiRow("鲫鱼","28.3 cm · 0.52 kg","首条鲫鱼") +
        hifiRow("青鱼","61.2 cm · 3.84 kg","最长 · 最重") +
        '<div class="hifi-priority"><b>显示优先级</b><span>数量里程碑　›　首条某鱼种　›　尺寸纪录</span></div>' +
      '</div></div>';
  }

  if(view.render_mode==="submenu_index") {
    const children=view.children||[];
    return '<div class="authority-index">' +
      '<div class="authority-index-intro"><strong>' + esc(view.title) + ' · 子菜单骨架</strong>' +
        '<span>当前只建立 F1–F8 导航结构；页面内容、高保真与 Authority 将逐项补齐。</span></div>' +
      '<div class="authority-index-grid">' +
        children.map(child =>
          '<article class="authority-index-card">' +
            '<div class="authority-index-head"><strong>' + esc((child.code || "") + " · " + child.title.replace(/^[A-Z]\\d+\\s*·\\s*/, "")) + '</strong>' +
              statusBadge(child.status || "MISSING") + '</div>' +
            '<div class="authority-kind">CONTENT PENDING</div>' +
          '</article>'
        ).join("") +
      '</div>' +
    '</div>';
  }

  if(view.render_mode==="search_overview") {
    return myCatchesSearchOverview(view);
  }

  if(view.render_mode==="search_board") {
    const focus='<div class="hifi-page-content search-mode">'+hifiSearchModeBar("")+
      '<div class="hifi-search-hint">键盘打开 · 右侧取消 · 页面仍是我的鱼获</div>'+
      '<div class="hifi-title-under-search">我的鱼获</div>'+
      '<div class="hifi-month">2026年9月</div>'+
      '<div class="hifi-day-rows">'+hifiRow("草鱼","42.6 cm · 1.28 kg","最长")+hifiRow("鲫鱼","28.3 cm · 0.52 kg","")+'</div></div>';
    const results='<div class="hifi-page-content search-mode">'+hifiSearchModeBar("草鱼")+
      '<div class="hifi-title-under-search">我的鱼获</div>'+
      '<div class="hifi-result-count">2 次鱼获</div><div class="hifi-month">2026年9月</div>'+
      '<div class="hifi-day-rows">'+hifiRow("草鱼","42.6 cm · 1.28 kg","最长")+hifiRow("草鱼","37.8 cm · 0.96 kg","")+'</div></div>';
    return '<div class="hifi-board three">' +
      bgDataPhone(focus,"点击搜索 / Focused") +
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




function recognitionStateTimelineCanvas(view) {
  const model = view.timeline_model || {};
  const states = model.processing_states || [];
  const exits = model.exits || [];
  const pacing = model.pacing_rules || [];
  const transition = model.transition || {};
  const ownership = model.ownership || {};

  const flowItems = [
    ["ENTRY", "Camera / Gallery"],
    ...states.map(state => [state.display_name || state.phase, state.user_meaning || ""]),
    [transition.display_name || transition.id || "RESOLVE", "转场"],
    ["RESULT", "High / Medium / Low"]
  ];

  const flow =
    '<section class="recognition-timeline-lane visual">' +
      '<div class="recognition-lane-title">PRODUCT STATE FLOW · 产品体验关系</div>' +
      '<div class="recognition-lane-flow">' +
        flowItems.map((item,index) =>
          '<div class="recognition-state-node"><strong>' + esc(item[0]) + '</strong><span>' + esc(item[1]) + '</span></div>' +
          (index < flowItems.length - 1 ? '<div class="recognition-arrow">→</div>' : '')
        ).join("") +
      '</div>' +
    '</section>';

  const stateCards =
    '<div class="recognition-state-grid">' +
      states.map((state,index) =>
        '<article class="recognition-state-card">' +
          '<div class="recognition-state-head"><strong>' + esc(String(index + 1).padStart(2,"0") + " · " + (state.display_name || state.phase)) + '</strong><span>' + esc(state.duration || "") + '</span></div>' +
          '<p><b>用户理解</b> · ' + esc(state.user_meaning) + '</p>' +
          '<p><b>允许</b> · ' + esc(state.allowed) + '</p>' +
          '<p><b>禁止</b> · ' + esc(state.forbidden) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const transitionCard =
    '<section class="recognition-resolve-card">' +
      '<div><span>TRANSITION · NOT A PROCESSING STATE</span><strong>' + esc(transition.display_name || transition.id || "RESOLVE") + '</strong></div>' +
      '<p><b>用户理解</b> · ' + esc(transition.user_meaning || "") + '</p>' +
      '<p>' + esc(transition.rule || "") + '</p>' +
    '</section>';

  const exitCards =
    '<div class="recognition-branch-grid">' +
      exits.map(exit =>
        '<article><strong>' + esc(exit.source) + '</strong><span>→ ' + esc(exit.target) + '</span><p>' + esc(exit.meaning) + '</p></article>'
      ).join("") +
    '</div>';

  const pacingCards =
    '<div class="recognition-finding-grid">' +
      pacing.map(item =>
        '<article class="recognition-finding note"><div><span>RULE</span><strong>' + esc(item.title) + '</strong></div><p>' + esc(item.detail) + '</p></article>'
      ).join("") +
    '</div>';

  const ownershipCards = [
    ["PROCESSING", (ownership.processing || []).join(" · ")],
    ["TRANSITION", (ownership.transition || []).join(" · ")],
    ["DOWNSTREAM", (ownership.downstream || []).join(" · ")],
    ["EXIT", (ownership.exit || []).join(" · ")]
  ];

  const ownershipGrid =
    '<div class="authority-index-grid">' +
      ownershipCards.map(item =>
        '<article class="authority-index-card"><div class="authority-index-head"><strong>' + esc(item[0]) + '</strong></div>' +
        '<div class="authority-kind">STATE OWNERSHIP</div><p>' + esc(item[1]) + '</p></article>'
      ).join("") +
    '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>核心规则</strong><span>' + esc(model.rule || "") + '</span></div>' +
    flow +
    '<div class="recognition-section-title">' + esc(states.length + " 个 Processing States") + '</div>' + stateCards +
    transitionCard +
    '<div class="recognition-section-title">Exit Relationship</div>' + exitCards +
    '<div class="recognition-section-title">Fast / Normal / Slow Rules</div>' + pacingCards +
    '<div class="recognition-section-title">State Ownership</div>' + ownershipGrid +
  '</div>';
}
function recognitionVisualStatesCanvas(view) {
  const states = view.visual_states || [];

  const cards = states.map(state => {
    const sources = state.sources || (state.path ? [{label:state.title,path:state.path}] : []);
    return '<section class="recognition-visual-state-group">' +
      '<div class="recognition-visual-state-head"><span>' + esc(state.code || "") + '</span><strong>' + esc(state.display_name || state.title) + '</strong></div>' +
      '<div class="hifi-board ' + (sources.length > 1 ? 'two' : 'one') + '">' +
        sources.map(source =>
          '<div class="hifi-original">' +
            '<div class="hifi-phone-label">' + esc(source.label || state.display_name || state.title) + '</div>' +
            '<a href="' + esc(repoHref(source.path)) + '" target="_blank" rel="noreferrer">' +
              '<img src="' + esc(repoHref(source.path)) + '" alt="' + esc(source.label || state.title) + '">' +
            '</a>' +
          '</div>'
        ).join("") +
      '</div>' +
      '<div class="hifi-original-caption">' + esc(state.note || "") + '</div>' +
    '</section>';
  }).join("");

  const mapping = '<div class="authority-index">' +
    '<div class="authority-index-intro"><strong>Existing Frozen UI · 重新归组</strong>' +
      '<span>原始 PNG 不删除、不复制。产品状态从四步变三步后，01 + 02 被视为“图片识别中”的早/晚参考帧。</span></div>' +
    '<div class="authority-index-grid">' +
      states.map(state =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc((state.code || "") + " · " + (state.display_name || state.title)) + '</strong>' + statusBadge("ACTIVE_CLOSURE") + '</div>' +
          '<div class="authority-kind">3-STATE MAPPING</div>' +
          '<p>' + esc((state.sources || []).map(x=>x.path).join(" + ")) + '</p>' +
        '</article>'
      ).join("") +
    '</div>' +
  '</div>';

  return '<div class="recognition-visual-state-list">' + cards + '</div>' + mapping;
}

function recognitionRuntimeEvidenceCanvas(view) {
  const m = view.evidence_model || {};
  const groups = m.groups || [];
  const checks = m.frozen_checks || [];
  const gate = m.final_gate || {};

  const groupGrid = '<div class="recognition-state-grid">' +
    groups.map(group => '<article class="recognition-state-card">' +
      '<div class="recognition-state-head"><strong>' + esc(group.code + " · " + group.title) + '</strong>' + statusBadge("FROZEN") + '</div>' +
      '<p><b>Requirement</b> · ' + esc(group.requirement) + '</p>' +
      '<p><b>Artifacts</b> · ' + esc((group.artifacts || []).join(" · ")) + '</p>' +
      '<p><b>Acceptance</b> · ' + esc(group.acceptance) + '</p>' +
    '</article>').join("") + '</div>';

  const checkGrid = '<div class="authority-index-grid">' +
    checks.map((item,index) => '<article class="authority-index-card">' +
      '<div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
      '<div class="authority-kind">FROZEN RUNTIME CHECK</div><p>' + esc(item) + '</p></article>').join("") + '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Evidence Rule</strong><span>' + esc(m.principle || "") + '</span></div>' +
    '<div class="recognition-section-title">5 Evidence Groups</div>' + groupGrid +
    '<div class="recognition-section-title">Cross-contract Runtime Checks</div>' + checkGrid +
    '<div class="recognition-section-title">Final Gate</div>' +
      '<div class="recognition-state-card"><p><b>Classification</b> · ' + esc(gate.classification_file || "") + ' == ' + esc(gate.pass_value || "") + '</p>' +
      '<p><b>Build PASS sufficient</b> · ' + esc(String(gate.build_pass_is_sufficient)) + '</p>' +
      '<p>FAIL_EVIDENCE · ' + esc(gate.missing_required_artifact || "") + '</p>' +
      '<p>FAIL_TEST · ' + esc(gate.wrong_runtime_behavior || "") + '</p></div>' +
  '</div>';
}

function fishGuideMotionCanvas(view) {
  const m = view.motion_system || {};
  const home = m.home_carousel || {};
  const hint = m.discover_hint || {};
  const detail = m.detail_carousel || {};
  const gesture = m.gesture || {};
  const nav = m.navigation || {};
  const state = m.state_transition || {};
  const reduce = m.reduce_motion || {};

  const metric = (label, value) =>
    '<div class="direct-rule-card"><b>' + esc(label) + '</b><span>' + esc(value || "—") + '</span></div>';

  const section = (title, kicker, rows) =>
    '<section class="authority-index">' +
      '<div class="authority-index-intro"><strong>' + esc(title) + '</strong><span>' + esc(kicker) + '</span></div>' +
      '<div class="direct-variant-rules">' + rows.join("") + '</div>' +
    '</section>';

  return '<div class="fish-guide-motion-spec">' +
    '<div class="text-action-review-banner">' +
      '<div><span>FISH GUIDE · MOTION & INTERACTION V1</span><strong>FROZEN · USER-DRIVEN</strong></div>' +
      '<p>' + esc(m.principle || "Alive, not animated · User-driven by default") + '</p>' +
    '</div>' +
    section("01 · Home Species Carousel", "直接操控；中心卡是唯一 settled selection。", [
      metric("Active", home.active),
      metric("Adjacent", home.adjacent),
      metric("Settle", home.settle),
      metric("中心卡 Tap", home.centered_tap),
      metric("邻卡 Tap", home.adjacent_tap),
      metric("Selection", home.selection)
    ]) +
    section("02 · First-entry Discover Hint", "只教一次横向浏览，不自动切换鱼种。", [
      metric("Frequency", hint.frequency),
      metric("Delay", hint.delay),
      metric("Nudge", hint.nudge),
      metric("Timing", hint.timing),
      metric("Selection Change", hint.selection_change),
      metric("Cancel", hint.cancel),
      metric("Reduce Motion", hint.reduce_motion)
    ]) +
    section("03 · Species Detail · 5-card Carousel", "有限 01–05；完全用户驱动。", [
      metric("Slots", detail.slots),
      metric("Settle", detail.settle),
      metric("Auto Flip", detail.auto_flip),
      metric("Autoplay", detail.autoplay),
      metric("Loop", detail.loop),
      metric("Indicator", detail.indicator),
      metric("Adjacent Tap", detail.adjacent_tap)
    ]) +
    section("04 · Gesture & Navigation", "纵向页面滚动与横向卡片滑动必须稳定共存。", [
      metric("Touch Slop", gesture.touch_slop),
      metric("Horizontal Lock", gesture.horizontal_lock),
      metric("Axis Switch", gesture.switch_axis_after_lock),
      metric("Card Press", nav.card_press),
      metric("Forward", nav.forward),
      metric("Back", nav.back),
      metric("Haptic", nav.haptic)
    ]) +
    section("05 · Committed Data State", "只对真实已提交的数据变化做克制反馈。", [
      metric("UNLIT → LIT", state.unlit_to_lit),
      metric("LIT → UNLIT", state.lit_to_unlit),
      metric("Additional Catch", state.additional_catch),
      metric("Offscreen Replay", state.offscreen_replay),
      metric("Celebration", state.celebration)
    ]) +
    section("06 · Reduce Motion", "减少运动，不改变信息与导航。", [
      metric("Discover Hint", reduce.discover_hint),
      metric("Direct Drag", reduce.direct_drag),
      metric("Settle", reduce.settle),
      metric("Navigation", reduce.navigation),
      metric("State", reduce.state)
    ]) +
    '<div class="preview-note">禁止：Auto Flip / autoplay / circular loop / continuous glow / confetti / unlock sound / every-snap haptic。Runtime timing 与真机手势证据后续归 09 · 验收证据。</div>' +
  '</div>';
}

function genericSpecHifiCanvas(feature, view) {
  if (view.render_mode === "fish_guide_motion") return fishGuideMotionCanvas(view);
  if (view.render_mode === "recognition_timeline") return recognitionStateTimelineCanvas(view);
  if (view.render_mode === "recognition_visual_states") return recognitionVisualStatesCanvas(view);
  if (view.render_mode === "recognition_runtime_evidence") return recognitionRuntimeEvidenceCanvas(view);
  const points = view.menu_points || [];
  const visual = view.image || view.visual_authority ||
    (view.render_mode === "repo_image" ? currentVersion(feature)?.visual_authority : null);
  const source = view.visual_authority_source || null;
  const sourceCard = source
    ? '<div class="authority-index">' +
        '<div class="authority-index-intro"><strong>Current Visual Authority</strong>' +
          '<span>' + esc(view.visual_authority_note || "当前视觉 Authority 已登记并按文件指纹治理。") + '</span></div>' +
        '<div class="authority-index-grid">' +
          '<article class="authority-index-card">' +
            '<div class="authority-index-head"><strong>' + esc(source.name || source.source_name || "Visual Source") + '</strong>' +
              statusBadge(view.visual_status || "FROZEN") + '</div>' +
            '<div class="authority-kind">' +
              esc((source.kind || "SOURCE").replaceAll("_", " ").toUpperCase()) +
              ' · ' + esc(source.width || "—") + '×' + esc(source.height || "—") +
            '</div>' +
            ((source.path || source.library_path)
              ? '<p>' + esc(source.path || source.library_path) + '</p>'
              : '') +
            '<p>SHA-256 · ' + esc(source.sha256 || "—") + '</p>' +
          '</article>' +
          ((view.deferred_scope || []).length
            ? '<article class="authority-index-card">' +
                '<div class="authority-index-head"><strong>Deferred Scope</strong>' + statusBadge("PARTIAL") + '</div>' +
                '<div class="authority-kind">SEPARATE REVIEW</div>' +
                '<p>' + esc((view.deferred_scope || []).join(" · ")) + '</p>' +
              '</article>'
            : '') +
        '</div>' +
      '</div>'
    : '';

  if (view.render_mode === "repo_image" && visual && isImage(visual)) {
    const specIndex = points.length
      ? '<div class="authority-index">' +
          '<div class="authority-index-intro"><strong>正式规范覆盖</strong>' +
            '<span>' + esc(view.summary || "本层已建立正式规范。") + '</span></div>' +
          '<div class="authority-index-grid">' +
            points.map((point, index) =>
              '<article class="authority-index-card">' +
                '<div class="authority-index-head"><strong>' +
                  esc(String(index + 1).padStart(2, "0") + " · " + point) +
                '</strong></div>' +
                '<div class="authority-kind">FROZEN SPEC</div>' +
              '</article>'
            ).join("") +
          '</div>' +
        '</div>'
      : '';

    return '<div class="hifi-original">' +
      '<a href="' + esc(repoHref(visual)) + '" target="_blank" rel="noreferrer">' +
      '<img src="' + esc(repoHref(visual)) + '" alt="' + esc(view.title) + '"></a>' +
      '<div class="hifi-original-caption">当前 Frozen Visual Authority · 本层未生成新视觉资产</div></div>' +
      specIndex;
  }

  return sourceCard + '<div class="authority-index">' +
    '<div class="authority-index-intro"><strong>' + esc(view.title) + '</strong>' +
      '<span>' + esc(view.summary || "规范菜单已建立，Authority 待逐项补齐。") + '</span></div>' +
    (points.length
      ? '<div class="authority-index-grid">' +
          points.map((point, index) =>
            '<article class="authority-index-card">' +
              '<div class="authority-index-head"><strong>' +
                esc(String(index + 1).padStart(2, "0") + " · " + point) +
              '</strong></div>' +
              '<div class="authority-kind">SPEC MENU</div>' +
            '</article>'
          ).join("") +
        '</div>'
      : '<div class="preview-empty">规范子项尚未登记。</div>') +
  '</div>';
}


function recognitionAiEdgeFieldStaticCanvas(child) {
  const shape = child.static_shape || {};
  const islands = shape.primary_islands || [];
  const gaps = shape.quiet_gaps || [];
  const fragments = shape.secondary_fragments || [];
  const hierarchy = shape.hierarchy || {};
  const internal = shape.island_internal_structure || {};
  const forbidden = shape.hard_forbidden || [];

  const islandGrid =
    '<div class="authority-index-grid">' +
      islands.map(island =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(island.id + " · " + island.name) + '</strong>' + statusBadge("FROZEN") + '</div>' +
          '<div class="authority-kind">' + esc(island.color + " · " + island.weight) + '</div>' +
          '<p>' + esc(island.zone) + '</p>' +
          '<p>Source · ' + esc((island.source_paths || []).join(" / ")) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const gapGrid =
    '<div class="authority-index-grid">' +
      gaps.map(gap =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(gap.id) + '</strong></div>' +
          '<div class="authority-kind">MANDATORY QUIET GAP</div>' +
          '<p>' + esc(gap.zone) + '</p><p>' + esc(gap.rule) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const fragmentGrid =
    '<div class="authority-index-grid">' +
      fragments.map(fragment =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(fragment.id) + '</strong></div>' +
          '<div class="authority-kind">SECONDARY ONLY</div>' +
          '<p>' + esc(fragment.zone) + '</p><p>' + esc(fragment.rule) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const internalGrid =
    '<div class="authority-index-grid">' +
      (internal.island_composition || []).map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.island) + '</strong>' + statusBadge("FROZEN") + '</div>' +
          '<div class="authority-kind">ISLAND INTERNAL STRUCTURE · ' + esc(item.emphasis || "") + '</div>' +
          '<p>' + esc(item.composition) + '</p>' +
          (item.note ? '<p>' + esc(item.note) + '</p>' : '') +
        '</article>'
      ).join("") +
    '</div>';

  const hairlineCard =
    '<div class="recognition-state-grid">' +
      '<article class="recognition-state-card"><div class="recognition-state-head"><strong>Companion Hairline</strong><span>EVERY ISLAND</span></div>' +
        '<p><b>Core</b> · ' + esc(internal.companion_hairline?.core_width_dp || "") + 'dp</p>' +
        '<p><b>Local Glow</b> · ' + esc(internal.companion_hairline?.local_glow_width_dp || "") + 'dp · no Outer Bloom</p>' +
        '<p><b>Length</b> · ' + esc(internal.companion_hairline?.visible_length || "") + '</p>' +
        '<p><b>Offset</b> · ' + esc(internal.companion_hairline?.centerline_offset_dp || "") + 'dp · variable</p>' +
      '</article>' +
      '<article class="recognition-state-card"><div class="recognition-state-head"><strong>Micro Hairline</strong><span>B_UR ONLY</span></div>' +
        '<p><b>Core</b> · ' + esc(internal.micro_hairline?.core_width_dp || "") + 'dp</p>' +
        '<p><b>Local Glow</b> · ' + esc(internal.micro_hairline?.local_glow_width_dp || "") + 'dp · no Outer Bloom</p>' +
        '<p><b>Length</b> · ' + esc(internal.micro_hairline?.visible_length || "") + '</p>' +
        '<p><b>Offset</b> · ' + esc(internal.micro_hairline?.centerline_offset_dp || "") + 'dp · variable</p>' +
      '</article>' +
    '</div>';

  const hierarchyRows = [
    ["Primary Filament", hierarchy.primary_filament],
    ["Secondary Hairline", hierarchy.secondary_hairline],
    ["Energy Node", hierarchy.energy_node],
    ["Edge Bloom", hierarchy.edge_bloom],
    ["Sparse Particle", hierarchy.sparse_particle]
  ].filter(x=>x[1]);

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Static Identity</strong><span>' + esc(shape.identity || "") + '</span></div>' +
    '<div class="recognition-section-title">4 Primary Energy Islands</div>' + islandGrid +
    '<div class="recognition-section-title">Mandatory Quiet Gaps</div>' + gapGrid +
    '<div class="recognition-section-title">Island Internal Structure</div>' + internalGrid +
    hairlineCard +
    '<div class="recognition-section-title">Secondary Fragments</div>' + fragmentGrid +
    '<div class="recognition-section-title">Edge Band</div>' +
      '<div class="recognition-state-card"><p><b>Core</b> · ' + esc(shape.edge_band?.core_rule || "") + '</p>' +
      '<p><b>Bloom</b> · ' + esc(shape.edge_band?.bloom_rule || "") + '</p>' +
      '<p><b>Center</b> · ' + esc(shape.edge_band?.center_safe_area || "") + '</p></div>' +
    '<div class="recognition-section-title">Visual Hierarchy</div>' +
      '<div class="authority-index-grid">' +
        hierarchyRows.map(row =>
          '<article class="authority-index-card"><div class="authority-index-head"><strong>' + esc(row[0]) + '</strong></div>' +
          '<div class="authority-kind">MAX RELATIVE WEIGHT</div><p>' + esc(row[1]) + '</p></article>'
        ).join("") +
      '</div>' +
    '<div class="recognition-section-title">Hard Forbidden</div>' +
      '<div class="authority-index-grid">' +
        forbidden.map((item,index) =>
          '<article class="authority-index-card"><div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
          '<div class="authority-kind">FAIL CONDITION</div><p>' + esc(item) + '</p></article>'
        ).join("") +
      '</div>' +
  '</div>';
}


function recognitionAiEdgeFieldRenderingCanvas(child) {
  const c = child.rendering_contract || {};
  const architecture = c.architecture || {};
  const order = c.draw_order || [];
  const formula = c.optical_formula || {};
  const stateInputs = c.state_inputs || [];
  const platform = c.platform_mapping || [];
  const failures = c.hard_failures || [];

  const drawGrid =
    '<div class="authority-index-grid">' +
      order.map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(String(item.order).padStart(2,"0") + " · " + item.layer) + '</strong></div>' +
          '<div class="authority-kind">MANDATORY DRAW ORDER</div>' +
          '<p>' + esc(item.implementation) + '</p><p><b>禁止</b> · ' + esc(item.must_not) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const inputGrid =
    '<div class="authority-index-grid">' +
      stateInputs.map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.input) + '</strong></div>' +
          '<div class="authority-kind">OWNER · ' + esc(item.owner) + '</div>' +
          '<p>' + esc(item.behavior) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const platformGrid =
    '<div class="authority-index-grid">' +
      platform.map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.platform) + '</strong></div>' +
          '<div class="authority-kind">PLATFORM RENDERER</div>' +
          '<p>' + esc(item.renderer) + '</p><p>Shared · ' + esc(item.shared) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Renderer Architecture</strong><span>' +
      esc(architecture.renderer || "") + ' · ' + esc(architecture.coordinate_space || "") + '</span></div>' +
    '<div class="recognition-section-title">Z-order</div>' +
      '<div class="recognition-state-card"><p>' + esc((architecture.z_order || []).join(" → ")) + '</p><p>' + esc(architecture.rule || "") + '</p></div>' +
    '<div class="recognition-section-title">Mandatory Draw Order</div>' + drawGrid +
    '<div class="recognition-section-title">Critical Segment Rule</div>' +
      '<div class="recognition-state-card"><p><b>Visible</b> · ' + esc(c.primary_segment?.visible_ratio || "") + '</p>' +
      '<p><b>Full Path</b> · ' + esc(c.primary_segment?.persistent_full_path || "") + '</p>' +
      '<p>' + esc(c.primary_segment?.reason || "") + '</p></div>' +
    '<div class="recognition-section-title">Unified Alpha Chain</div>' +
      '<div class="recognition-state-card"><p><code>' + esc(formula.formula || "") + '</code></p>' +
      '<p>' + esc(formula.rule || "") + '</p></div>' +
    '<div class="recognition-section-title">External Inputs</div>' + inputGrid +
    '<div class="recognition-section-title">Cross-platform Renderer</div>' + platformGrid +
    '<div class="recognition-section-title">Hard Failures</div>' +
      '<div class="authority-index-grid">' +
        failures.map((item,index) =>
          '<article class="authority-index-card"><div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
          '<div class="authority-kind">IMPLEMENTATION FAIL</div><p>' + esc(item) + '</p></article>'
        ).join("") +
      '</div>' +
  '</div>';
}


function recognitionAiEdgeFieldMotionCanvas(child) {
  const m = child.motion_model || {};
  const states = m.state_timing || [];
  const segment = m.segment_offset || {};
  const strength = m.state_strength || {};
  const resolve = m.resolve_strength || {};
  const rules = m.continuous_motion_rules || [];

  const stateGrid =
    '<div class="authority-index-grid">' +
      states.map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.state) + '</strong>' + statusBadge("FROZEN") + '</div>' +
          '<div class="authority-kind">MOTION TARGET</div>' +
          '<p>Minimum · ' + esc(item.minimum_ms + "ms") + '</p>' +
          '<p>StateStrength · ' + esc(item.state_strength) + '</p>' +
          '<p>Segment Speed · ' + esc(item.segment_speed + "×") + '</p>' +
          '<p>' + esc(item.note || "") + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const seedGrid =
    '<div class="authority-index-grid">' +
      (segment.primary_seeds || []).map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.island + " · " + item.path) + '</strong></div>' +
          '<div class="authority-kind">SEGMENT OFFSET SEED</div>' +
          '<p>Seed · ' + esc(item.seed) + '</p><p>Direction · ' + esc(item.direction) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Motion Identity</strong><span>SegmentOffset 决定位置 · StateStrength 决定权重 · ResolveStrength 决定退场。三者不得合并成一个 progress。</span></div>' +
    '<div class="recognition-section-title">3-State Motion Targets</div>' + stateGrid +
    '<div class="recognition-section-title">SegmentOffset</div>' +
      '<div class="recognition-state-card"><p><b>Base Cycle</b> · ' + esc(segment.base_cycle_ms + "ms") + '</p>' +
      '<p><b>Formula</b> · <code>' + esc(segment.formula || "") + '</code></p>' +
      '<p><b>Reset on State</b> · ' + esc(String(segment.reset_on_state_change)) + '</p></div>' +
      seedGrid +
    '<div class="recognition-section-title">StateStrength Entry</div>' +
      '<div class="recognition-state-card"><p><b>0 → 1</b> · ' + esc(strength.entry?.duration_ms + "ms") + '</p>' +
      (strength.entry?.staging || []).map(x=>'<p><b>'+esc(x.range_ms)+'</b> · '+esc(x.layer)+' · '+esc(x.behavior)+'</p>').join("") +
      '</div>' +
    '<div class="recognition-section-title">ResolveStrength</div>' +
      '<div class="recognition-state-card"><p><b>Duration</b> · ' + esc(resolve.duration_ms + "ms") + '</p>' +
      '<p><b>Segment</b> · ' + esc(resolve.segment_behavior || "") + '</p>' +
      '<p><code>' + esc(resolve.formula || "") + '</code></p></div>' +
    '<div class="recognition-section-title">Continuity Rules</div>' +
      '<div class="authority-index-grid">' +
        rules.map((rule,index)=>'<article class="authority-index-card"><div class="authority-index-head"><strong>'+esc(String(index+1).padStart(2,"0"))+'</strong></div><div class="authority-kind">FROZEN RULE</div><p>'+esc(rule)+'</p></article>').join("") +
      '</div>' +
  '</div>';
}


function recognitionQualityLevelsCanvas(child) {
  const q = child.quality_levels || {};
  const profiles = q.profiles || [];
  const invariants = q.invariants || [];
  const neverRemove = q.never_remove || [];
  const priority = q.degradation_priority || [];

  const profileGrid =
    '<div class="recognition-state-grid">' +
      profiles.map(profile =>
        '<article class="recognition-state-card">' +
          '<div class="recognition-state-head"><strong>' + esc(profile.id) + '</strong><span>' + esc(profile.intent) + '</span></div>' +
          '<p><b>Receiving Light</b> · ' + esc(profile.receiving_light) + '</p>' +
          '<p><b>Primary Core</b> · ' + esc(profile.primary_core) + '</p>' +
          '<p><b>Mid / Outer</b> · ' + esc(profile.mid_glow + " / " + profile.outer_bloom) + '</p>' +
          '<p><b>Companion</b> · ' + esc(profile.companion_hairline) + '</p>' +
          '<p><b>Micro</b> · ' + esc(profile.micro_hairline) + '</p>' +
          '<p><b>Node</b> · ' + esc(profile.hot_nodes) + '</p>' +
          '<p><b>Particles</b> · ' + esc(profile.particles) + '</p>' +
          '<p>' + esc(profile.note || "") + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const itemGrid = (items, kind) =>
    '<div class="authority-index-grid">' +
      items.map((item,index) =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
          '<div class="authority-kind">' + esc(kind) + '</div>' +
          '<p>' + esc(item) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Quality Rule</strong><span>' + esc(q.rule || "") + '</span></div>' +
    '<div class="recognition-section-title">FULL / BALANCED / LITE</div>' + profileGrid +
    '<div class="recognition-section-title">Cross-level Invariants</div>' + itemGrid(invariants,"MUST NOT CHANGE") +
    '<div class="recognition-section-title">Degradation Priority</div>' + itemGrid(priority,"REMOVE / REDUCE IN ORDER") +
    '<div class="recognition-section-title">Identity Floor</div>' + itemGrid(neverRemove,"NEVER REMOVE") +
  '</div>';
}


function recognitionReduceMotionCanvas(child) {
  const r = child.reduce_motion || {};
  const behaviors = r.behaviors || [];
  const values = r.static_values || {};
  const states = r.state_semantics || [];
  const forbidden = r.forbidden || [];

  const behaviorGrid =
    '<div class="authority-index-grid">' +
      behaviors.map(item =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(item.target) + '</strong>' + statusBadge("FROZEN") + '</div>' +
          '<div class="authority-kind">REDUCE MOTION · ' + esc(item.reduce_motion) + '</div>' +
          '<p><b>Normal</b> · ' + esc(item.normal) + '</p>' +
          '<p>' + esc(item.rule) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  const stateGrid =
    '<div class="authority-index-grid">' +
      states.map((item,index) =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
          '<div class="authority-kind">STATE SEMANTICS</div><p>' + esc(item) + '</p>' +
        '</article>'
      ).join("") +
    '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Reduce Motion Identity</strong><span>' + esc(r.identity || "") + '</span></div>' +
    '<div class="recognition-section-title">5 Motion Overrides</div>' + behaviorGrid +
    '<div class="recognition-section-title">Static Fish Focus</div>' +
      '<div class="recognition-state-card"><p><b>Reveal</b> · ' + esc(values.fish_focus?.reveal_ms + "ms alpha only") + '</p>' +
      '<p><b>Halo</b> · ' + esc(values.fish_focus?.halo_alpha) + '</p>' +
      '<p><b>Contour</b> · ' + esc(values.fish_focus?.contour_core_alpha) + '</p>' +
      '<p><b>Breathing</b> · ' + esc(String(values.fish_focus?.breathing)) + '</p></div>' +
    '<div class="recognition-section-title">State Semantics</div>' + stateGrid +
    '<div class="recognition-section-title">Quality × Reduce Motion</div>' +
      '<div class="recognition-state-card"><p>' + esc(r.quality_interaction?.order || "") + '</p>' +
      (r.quality_interaction?.examples || []).map(x=>'<p>• '+esc(x)+'</p>').join("") +
      '<p><b>Rule</b> · ' + esc(r.quality_interaction?.rule || "") + '</p></div>' +
    '<div class="recognition-section-title">Forbidden</div>' +
      '<div class="authority-index-grid">' +
        forbidden.map((item,index)=>'<article class="authority-index-card"><div class="authority-index-head"><strong>'+esc(String(index+1).padStart(2,"0"))+'</strong></div><div class="authority-kind">FAIL CONDITION</div><p>'+esc(item)+'</p></article>').join("") +
      '</div>' +
  '</div>';
}


function recognitionLayerDegradationCanvas(child) {
  const d = child.degradation_order || {};
  const ladder = d.ladder || [];
  const focus = d.fish_focus_levels || [];
  const rules = d.ordering_rules || [];
  const never = d.never_degrade || [];

  const ladderGrid = '<div class="recognition-state-grid">' +
    ladder.map(item => '<article class="recognition-state-card">' +
      '<div class="recognition-state-head"><strong>' + esc(item.level) + '</strong><span>Edge ' + esc(item.edge_field) + ' · Focus ' + esc(item.fish_focus) + '</span></div>' +
      '<p>' + esc(item.description) + '</p><p><b>Allowed</b> · ' + esc(item.allowed_changes) + '</p></article>').join("") + '</div>';

  const focusGrid = '<div class="authority-index-grid">' +
    focus.map(item => '<article class="authority-index-card">' +
      '<div class="authority-index-head"><strong>Fish Focus ' + esc(item.level) + '</strong></div>' +
      '<div class="authority-kind">SEMANTIC STRENGTH · ' + esc(item.semantic_strength) + '</div>' +
      '<p>' + esc(item.visual) + '</p><p>' + esc(item.rule) + '</p></article>').join("") + '</div>';

  const itemGrid = (items, kind) => '<div class="authority-index-grid">' +
    items.map((item,index) => '<article class="authority-index-card"><div class="authority-index-head"><strong>' +
      esc(String(index+1).padStart(2,"0")) + '</strong></div><div class="authority-kind">' +
      esc(kind) + '</div><p>' + esc(item) + '</p></article>').join("") + '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Global Degradation Principle</strong><span>' + esc(d.principle || "") + '</span></div>' +
    '<div class="recognition-section-title">D0 → D4 Global Ladder</div>' + ladderGrid +
    '<div class="recognition-section-title">Fish Focus A / B / C</div>' + focusGrid +
    '<div class="recognition-section-title">Never Degrade</div>' + itemGrid(never,"SEMANTIC / VISUAL FLOOR") +
    '<div class="recognition-section-title">Ordering Rules</div>' + itemGrid(rules,"FROZEN ORDER") +
    '<div class="recognition-section-title">Design Floor</div><div class="recognition-state-card"><p><b>' +
      esc(d.floor?.level || "") + '</b> · ' + esc(d.floor?.rule || "") + '</p><p>Edge · ' +
      esc(d.floor?.edge_field_floor || "") + '</p><p>Focus · ' + esc(d.floor?.fish_focus_floor || "") + '</p></div>' +
  '</div>';
}


function recognitionSemanticInvariantsCanvas(child) {
  const s = child.semantic_invariants || {};
  const groups = s.groups || [];
  const immutable = s.immutable_relationships || [];
  const allowed = s.allowed_to_change || [];
  const never = s.never_change || [];

  const groupGrid = '<div class="recognition-state-grid">' +
    groups.map(group => '<article class="recognition-state-card">' +
      '<div class="recognition-state-head"><strong>' + esc(group.title) + '</strong><span>' + esc(group.id) + '</span></div>' +
      (group.items || []).map(item=>'<p>• ' + esc(item) + '</p>').join("") +
    '</article>').join("") + '</div>';

  const itemGrid = (items, kind) => '<div class="authority-index-grid">' +
    items.map((item,index)=>'<article class="authority-index-card">' +
      '<div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
      '<div class="authority-kind">' + esc(kind) + '</div><p>' + esc(item) + '</p></article>').join("") + '</div>';

  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>Semantic Boundary</strong><span>' + esc(s.principle || "") + '</span></div>' +
    '<div class="recognition-section-title">Frozen Invariant Groups</div>' + groupGrid +
    '<div class="recognition-section-title">Immutable Relationships</div>' + itemGrid(immutable,"RELATIONSHIP") +
    '<div class="recognition-section-title">Allowed to Change</div>' + itemGrid(allowed,"LEGAL VARIATION") +
    '<div class="recognition-section-title">Never Change</div>' + itemGrid(never,"HARD INVARIANT") +
    '<div class="recognition-section-title">Cross-mode Acceptance</div>' +
      '<div class="recognition-state-card"><p>' + esc(s.acceptance_question || "") + '</p></div>' +
  '</div>';
}


function recognitionEvidenceGroupCanvas(child) {
  const g = child.evidence_group || {};
  const artifacts = g.artifacts || [];
  return '<div class="recognition-timeline-review">' +
    '<div class="recognition-rule"><strong>' + esc(g.code + " · " + g.title) + '</strong><span>' + esc(g.requirement || "") + '</span></div>' +
    '<div class="recognition-section-title">Required Artifacts</div>' +
      '<div class="authority-index-grid">' +
        artifacts.map((item,index)=>'<article class="authority-index-card"><div class="authority-index-head"><strong>' +
          esc(String(index+1).padStart(2,"0")) + '</strong></div><div class="authority-kind">REQUIRED EVIDENCE</div><p>' +
          esc(item) + '</p></article>').join("") +
      '</div>' +
    '<div class="recognition-section-title">Acceptance</div>' +
      '<div class="recognition-state-card"><p>' + esc(g.acceptance || "") + '</p></div>' +
  '</div>';
}


function recognitionVisualReferenceSpecCanvas(child) {
  const s = child.generation_spec || {};
  const grid = (items, kind) =>
    '<div class="authority-index-grid">' +
      (items || []).map((item,index) =>
        '<article class="authority-index-card">' +
          '<div class="authority-index-head"><strong>' + esc(String(index+1).padStart(2,"0")) + '</strong></div>' +
          '<div class="authority-kind">' + esc(kind) + '</div><p>' + esc(item) + '</p></article>'
      ).join("") +
    '</div>';

  const visualCards = [];
  if (child.primary_visual_authority && isImage(child.primary_visual_authority)) {
    visualCards.push(
      '<div class="hifi-original">' +
        '<a href="' + esc(repoHref(child.primary_visual_authority)) + '" target="_blank" rel="noreferrer">' +
          '<img src="' + esc(repoHref(child.primary_visual_authority)) + '" alt="' + esc(child.title) + '"></a>' +
        '<div class="hifi-original-caption">主视觉 Authority · FROZEN</div>' +
      '</div>' +
      (child.visual_authority_scope
        ? '<div class="recognition-state-card"><p>' + esc(child.visual_authority_scope) + '</p></div>'
        : '')
    );
  }
  (child.supporting_visual_references || []).forEach(ref => {
    const path = typeof ref === "string" ? ref : ref.path;
    if (!path || !isImage(path)) return;
    const role = typeof ref === "string" ? "补充视觉参考" : (ref.role || "补充视觉参考");
    const scope = typeof ref === "string" ? "" : (ref.scope || "");
    visualCards.push(
      '<div class="hifi-original">' +
        '<a href="' + esc(repoHref(path)) + '" target="_blank" rel="noreferrer">' +
          '<img src="' + esc(repoHref(path)) + '" alt="' + esc(role) + '"></a>' +
        '<div class="hifi-original-caption">' + esc(role) + ' · FROZEN</div>' +
      '</div>' +
      (scope ? '<div class="recognition-state-card"><p>' + esc(scope) + '</p></div>' : '')
    );
  });
  const visualBlock = visualCards.length
    ? '<div class="recognition-section-title">已确认视觉参考</div>' + visualCards.join("")
    : '';

  return '<div class="recognition-timeline-review">' +
    visualBlock +
    '<div class="recognition-rule"><strong>生成目的</strong><span>' + esc(s.purpose || "") + '</span></div>' +
    '<div class="recognition-section-title">画面形式</div><div class="recognition-state-card"><p>' + esc(s.format || "") + '</p></div>' +
    '<div class="recognition-section-title">构图要求</div>' + grid(s.composition,"构图") +
    '<div class="recognition-section-title">视觉重点</div>' + grid(s.visual_focus,"视觉重点") +
    '<div class="recognition-section-title">必须保持</div>' + grid(s.must_keep,"不可变化") +
    '<div class="recognition-section-title">禁止方向</div>' + grid(s.forbidden,"禁止") +
    '<div class="recognition-section-title">当前状态</div><div class="recognition-state-card"><p>' + esc(s.closure || "") + '</p></div>' +
  '</div>';
}

function genericSpecChildCanvas(child) {
  if (child.render_mode === "recognition_visual_reference_spec") return recognitionVisualReferenceSpecCanvas(child);
  if (child.render_mode === "menu_placeholder") {
    return '<div class="authority-index"><div class="authority-index-intro"><strong>' +
      esc(child.title) + '</strong><span>当前只建立菜单；视觉规范尚未制定。</span></div></div>';
  }
  if (child.render_mode === "recognition_evidence_group") return recognitionEvidenceGroupCanvas(child);
  if (child.render_mode === "recognition_semantic_invariants") return recognitionSemanticInvariantsCanvas(child);
  if (child.render_mode === "recognition_layer_degradation") return recognitionLayerDegradationCanvas(child);
  if (child.render_mode === "recognition_reduce_motion") return recognitionReduceMotionCanvas(child);
  if (child.render_mode === "recognition_quality_levels") return recognitionQualityLevelsCanvas(child);
  if (child.render_mode === "recognition_ai_edge_field_static") return recognitionAiEdgeFieldStaticCanvas(child);
  if (child.render_mode === "recognition_ai_edge_field_rendering") return recognitionAiEdgeFieldRenderingCanvas(child);
  if (child.render_mode === "recognition_ai_edge_field_motion") return recognitionAiEdgeFieldMotionCanvas(child);

  const visual = child.image || child.visual_authority || null;
  if (child.render_mode === "repo_image" && visual && isImage(visual)) {
    const points = child.menu_points || [];
    return '<div class="hifi-original">' +
      '<a href="' + esc(repoHref(visual)) + '" target="_blank" rel="noreferrer">' +
      '<img src="' + esc(repoHref(visual)) + '" alt="' + esc(child.title) + '"></a>' +
      '<div class="hifi-original-caption">当前子状态 Frozen Visual Authority · 直接引用仓库原始 PNG</div></div>' +
      '<div class="authority-index">' +
        '<div class="authority-index-intro"><strong>' + esc(child.title) + '</strong>' +
          '<span>' + esc(child.summary || "本子状态已建立正式规范。") + '</span></div>' +
        (points.length
          ? '<div class="authority-index-grid">' +
              points.map((point, index) =>
                '<article class="authority-index-card">' +
                  '<div class="authority-index-head"><strong>' +
                    esc(String(index + 1).padStart(2, "0") + " · " + point) +
                  '</strong></div>' +
                  '<div class="authority-kind">FROZEN SPEC</div>' +
                '</article>'
              ).join("") +
            '</div>'
          : '') +
      '</div>';
  }

  return '<div class="authority-index">' +
    '<div class="authority-index-intro"><strong>' + esc(child.title) + '</strong>' +
      '<span>' + esc(child.summary || "规范入口已建立，Authority 待补齐。") + '</span></div>' +
  '</div>';
}

function renderHifiView(feature, hifiId, hifiChildId = null) {
  const panel=el("hifiViewPanel");
  if(!hifiId){ panel.classList.add("hidden"); return; }
  const view=(feature.hifi_views||[]).find(x=>x.id===hifiId);
  if(!view){ panel.classList.add("hidden"); return; }
  const child=hifiChildId ? (view.children||[]).find(x=>x.id===hifiChildId) : null;
  panel.classList.remove("hidden");
  const authorityType = child?.authority_type || view.authority_type || "visual";
  el("hifiViewEyebrow").textContent =
    authorityType === "behavior" ? "交互规范" :
    child ? "冻结高保真 Authority" : "高保真 / Authority 索引";
  el("hifiViewTitle").textContent=child ? child.title : view.title;
  el("hifiViewStatus").innerHTML=statusBadge(child?.status || view.status || "PARTIAL");
  const summary=child?.summary || view.summary;
  const implementationReady =
    view.implementation_ready === true ||
    feature.implementation_readiness?.status === "IMPLEMENTATION_READY";
  if(summary || implementationReady){
    const readyLine = implementationReady
      ? "IMPLEMENTATION READY · Work 无需承担页面级视觉设计判断。"
      : "";
    el("hifiViewSummary").textContent=[summary,readyLine].filter(Boolean).join("\n");
    el("hifiViewSummary").classList.remove("hidden");
  } else el("hifiViewSummary").classList.add("hidden");
  el("hifiViewCanvas").innerHTML = child
    ? (feature.id === "my_catches_v2" ? myCatchesSearchChildCanvas(child) : genericSpecChildCanvas(child))
    : (feature.id === "my_catches_v2" ? myCatchesHifiCanvas(feature,view) : genericSpecHifiCanvas(feature,view));

  const sceneIds=child?.scenario_ids || view.scenario_ids || [];
  const scenes=sceneIds.map(id=>(feature.scenario_pages||[]).find(x=>x.id===id)).filter(Boolean);
  el("hifiViewScenarios").innerHTML=scenes.map(scene=>
    '<button class="hifi-scenario-chip" data-scene-id="'+esc(scene.id)+'">'+esc(scene.title)+'</button>'
  ).join("") || '<span class="preview-empty compact">无附加场景映射</span>';

  const supportingAuthorityPaths = (child?.supporting_visual_references || [])
    .map(ref => typeof ref === "string" ? ref : ref.path)
    .filter(Boolean);
  const implementationAuthorities = [
    view.engineering_authority,
    feature.implementation_readiness?.authority_index,
    ...(feature.implementation_readiness?.contract_paths || []),
    feature.implementation_readiness?.asset_contract
  ].filter(Boolean);
  el("hifiViewAuthorities").innerHTML=[child?.primary_visual_authority,...supportingAuthorityPaths,child?.visual_authority,child?.behavior_authority,child?.machine_authority,child?.authority,child?.secondary_authority,view.visual_authority,view.behavior_authority,view.machine_authority,view.secondary_authority,view.visual_authority_manifest,view.authority,view.image,...implementationAuthorities].filter((v,i,a)=>v&&a.indexOf(v)===i).map(path=>
    '<a class="authority-row" href="'+esc(repoHref(path))+'" target="_blank" rel="noreferrer">'+esc(path)+'</a>'
  ).join("");

  if(child && child.render_mode === "frozen_image"){
    el("hifiViewSource").textContent="本子页面只显示自身冻结视觉 Authority；父页面主视觉与旧动态预览均不在此层渲染。";
    el("hifiViewSource").classList.remove("hidden");
  } else if(child && child.authority_type === "behavior"){
    el("hifiViewSource").textContent="本子项为 Behavior Contract，不渲染 App 高保真页面。";
    el("hifiViewSource").classList.remove("hidden");
  } else if(view.source_reference){
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

function selectPage(id, scenarioId = null, hifiId = null, hifiChildId = null) {
  const feature = pageRegistry.features.find(f => f.id === id);
  if (!feature) return;
  const scene = scenarioId ? (feature.scenario_pages || []).find(x => x.id === scenarioId) : null;
  const hifi = hifiId ? (feature.hifi_views || []).find(x => x.id === hifiId) : null;
  const hifiChild = hifiChildId && hifi ? (hifi.children || []).find(x => x.id === hifiChildId) : null;
  selectedKey = "page/" + id +
    (hifi ? "/hifi/" + hifi.id + (hifiChild ? "/" + hifiChild.id : "") :
      (scene ? "/scenario/" + scene.id : ""));
  history.replaceState(
    null, "",
    "#page/" + encodeURIComponent(id) +
      (hifi ? "/hifi/" + encodeURIComponent(hifi.id) + (hifiChild ? "/" + encodeURIComponent(hifiChild.id) : "") :
        (scene ? "/scenario/" + encodeURIComponent(scene.id) : ""))
  );
  renderLists();
  hideAllDetails();
  el("pageDetail").classList.remove("hidden");

  const detailMode = hifiChild
    ? ((hifiChild.authority_type === "behavior" || hifiChild.render_mode === "behavior_spec") ? "behavior" : "visual")
    : hifi ? "hifi-index"
    : scene ? "scenario"
    : "overview";
  applyPageDetailMode(detailMode);

  el("pageEyebrow").textContent = hifiChild
    ? (detailMode === "behavior" ? "渔见 · 行为 Authority" : "渔见 · 视觉 Authority")
    : (hifi ? "渔见 · Authority 索引" : (scene ? "渔见 · 场景 Authority" : "渔见 · 页面总览"));
  el("pageTitle").textContent = hifiChild ? (feature.display_name + " · " + hifi.title + " · " + hifiChild.title) :
    (hifi ? (feature.display_name + " · " + hifi.title) : (scene ? (feature.display_name + " · " + scene.title) : (feature.display_name || feature.id)));
  el("pageStatus").innerHTML = statusBadge(hifiChild?.status || hifi?.status || scene?.status || feature.design_overall);
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

  renderHifiView(feature, hifi?.id || null, hifiChild?.id || null);
  renderScenario(feature, scene?.id || null);
  renderDesignSections(feature);
  renderFreezeReview(feature);
  renderPagePreview(feature);
  renderSharedRefs(feature);
  renderModalities(feature);
  renderVersions(feature);

  // Renderers above may reveal their own panels. Re-apply the selected layer
  // after rendering so parent-page governance never leaks into child Authority views.
  applyPageDetailMode(detailMode);
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
        const view=(feature.hifi_views || []).find(x=>x.id===hifiId);
        if (view) {
          const childId=parts[4] || null;
          if (!childId || (view.children || []).some(x=>x.id===childId)) {
            return selectPage(id,null,hifiId,childId);
          }
        }
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
