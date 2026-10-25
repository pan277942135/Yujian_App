Warning: truncated output (original token count: 56464)
Total output lines: 3983

const DESIGN_MANAGER_BUILD =
  document.querySelector('meta[name="design-manager-build"]')?.content || "local";
const PAGE_REGISTRY_URL =
  "../registry/experience_registry_v1.json?v=" + encodeURIComponent(DESIGN_MANAGER_BUILD);
const SHARED_REGISTRY_URL =
  "../registry/shared_design_system_v1.json?v=" + encodeURIComponent(DESIGN_MANAGER_BUILD);
const DESIGN_MODALITIES = ["behavior", "visual", "motion", "haptic", "sound", "assets"];

const ICONS = {
  FROZEN: "✓", ACTIVE_CLOSURE: "◐", PARTIAL: "◒", MISSING: "×",
  DEFERRED: "↷", BLOCKED_INTEGRITY: "!", RUNTIME_ONLY: "↗",
  DESIGN_ONLY: "◇", DEPRECATED: "—", CANDIDATE: "◐",
  STATIC_HIFI_FROZEN: "✓", STATIC_RULES_FROZEN: "✓", PIXEL_BASELINE_FROZEN_PRIVATE: "✓"
};

const STATUS_LABELS = {
  FROZEN: "已冻结", ACTIVE_CLOSURE: "收口中", PARTIAL: "部分完成",
  MISSING: "缺失", DEFERRED: "已延后", BLOCKED_INTEGRITY: "完整性阻塞",
  RUNTIME_ONLY: "仅运行时", DESIGN_ONLY: "仅设计",
  DEPRECATED: "已废弃", CANDIDATE: "候选",
  STATIC_HIFI_FROZEN: "静态高保已冻结", STATIC_RULES_FROZEN: "响应式规则已冻结",
  PIXEL_BASELINE_FROZEN_PRIVATE: "像素基准已冻结（私有源）"
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
let recognitionDecisionContract = null;
let recognitionMasterBoard = null;
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

function repoHref(path) {
  if (!path) return "#";
  const sep = path.includes("?") ? "&" : "?";
  return "../../" + path + sep + "v=" + encodeURIComponent(DESIGN_MANAGER_BUILD);
}
function isImage(path) { return !!path && /\.(png|jpe?g|webp|gif|svg)$/i.test(path); }

function hifiImageFigure(path, title, caption, sha256 = "") {
  const src = repoHref(path);
  const safeTitle = title || "Frozen design asset";
  return '<figure class="hifi-original hifi-asset-figure">' +
    '<button type="button" class="hifi-image-open" data-image-src="' + esc(src) +
      '" data-image-title="' + esc(safeTitle) + '" aria-label="放大查看 ' + esc(safeTitle) + '">' +
      '<img src="' + esc(src) + '" alt="' + esc(safeTitle) + ' · 原始比例完整预览" decoding="async">' +
    '</button>' +
    '<div class="hifi-image-actions">' +
      '<span>等比例完整预览 · 点击图片放大</span>' +
      '<a href="' + esc(src) + '" target="_blank" rel="noreferrer">打开原始文件 ↗</a>' +
    '</div>' +
    '<figcaption class="hifi-original-caption">' + esc(caption || path) +
      (sha256 ? '<br>SHA-256 · ' + esc(sha256) : '') +
    '</figcaption>' +
  '</figure>';
}

function hifiContractMetadataHtml(item, parentView = null) {
  if (!item || !(item.page_id || item.state_id || item.asset_id || item.entry_conditions || item.contract_links || parentView?.contract_links || item.machine_authority || parentView?.machine_authority)) return "";
  const display = value => Array.isArray(value) ? value.join(" · ") : (value || "未在当前 Authority 中规定");
  const path = item.authority_path || item.visual_authority || item.expected_asset_path || item.image || item.machine_authority || item.authority || "未提供";
  const pathMarkup = item.authority_path_available === false
    ? '<span class="hifi-contract-plain-path">' + esc(path) + '</span>'
    : '<a href="' + esc(repoHref(path)) + '" target="_blank" rel="noreferrer">' + esc(path) + '</a>';
  const machineAuthorityPath = item.machine_authority || parentView?.machine_authority;
  const machineAuthorityFallback = machineAuthorityPath
    ? [{ label: "机器可读技术合同", path: machineAuthorityPath }]
    : [];
  const contractLinks = [...(item.contract_links || []), ...(parentView?.contract_links || []), ...machineAuthorityFallback]
    .filter((link, index, all) => link.path && all.findIndex(other => other.path === link.path) === index);
  const fields = [
    ["Page ID", item.page_id], ["页面名称", item.page_name || item.title || parentView?.title],
    ["State ID", item.state_id], ["状态名称", item.state_name || item.title],
    ["State ID 作用域", item.state_id_scope], ["Asset ID", item.asset_id],
    ["设计版本", item.design_version], ["当前设计状态", item.status],
    ["设计审批状态", item.design_approval_status], ["进入条件", item.entry_conditions],
    ["触发动作", item.trigger], ["输入", item.input_contract],
    ["用户操作", item.user_actions], ["下一页面 / 状态", item.next_page],
    ["返回", item.back_behavior], ["取消", item.cancel_behavior],
    ["重拍", item.retake_behavior], ["Runtime 实现状态", item.runtime_status || "NOT_RUN"]
  ].filter(([, value]) => value !== undefined && value !== null && value !== "");
  const cards = fields.map(([label, value]) =>
    '<div class="hifi-contract-card"><span>' + esc(label) + '</span><strong>' +
      esc(display(value)) + '</strong></div>'
  ).join("");
  const links = contractLinks.map(link =>
    '<a href="' + esc(repoHref(link.path)) + '" target="_blank" rel="noreferrer">' +
      esc(link.label || link.path) + ' ↗</a>'
  ).join("");
  return '<section class="hifi-contract-details" aria-label="页面与状态合同信息">' +
    '<div class="hifi-contract-heading"><strong>页面与状态合同</strong>' +
      '<span>设计 Authority 与 Android Runtime 分开记录</span></div>' +
    '<div class="hifi-contract-grid">' + cards + '</div>' +
    '<div class="hifi-contract-authority"><span>Authority Path</span>' + pathMarkup +
      ((item.visual_authority_sha256 || item.sha256) ? '<span>视觉 SHA-256</span><code>' + esc(item.visual_authority_sha256 || item.sha256) + '</code>' : '') +
    '</div>' +
    (links ? '<div class="hifi-contract-links"><span>设计规范 / Contracts</span>' + links + '</div>' : '') +
    (item.missing_reason ? '<p class="hifi-missing-reason">' + esc(item.missing_reason) + '</p>' : '') +
  '</section>';
}

function nh01RuleList(items) {
  return '<ul>' + (items || []).map(item => '<li>' + esc(item) + '</li>').join('') + '</ul>';
}

function nh01StaticWorkspaceMarkup(view, manifest, contract) {
  const assets = manifest.assets || [];
  const original = manifest.original_reference || {};
  const assetCards = assets.map(asset => {
    const target = asset.target_viewport_dp || {};
    const pixels = asset.actual_canvas_px || {};
    const targetLabel = target.height == null
      ? (asset.viewport_note || (target.width + ' dp 宽；高度未固定'))
      : target.width + '×' + target.height + ' dp';
    return '<article class="nh01-static-asset" data-nh01-asset-card="' + esc(asset.asset_id) + '">' +
      '<div class="nh01-static-asset-head"><div><span class="nh01-static-asset-id">' + esc(asset.asset_id) + '</span>' +
        '<h3>' + esc(targetLabel) + '</h3></div>' + statusBadge(asset.status) + '</div>' +
      '<p class="nh01-static-dimensions">目标视口：' + esc(targetLabel) + '<br>实际画布：' + esc(pixels.width + '×' + pixels.height + ' px') + '</p>' +
      hifiImageFigure(asset.path, asset.asset_id, targetLabel + ' · PNG ' + pixels.width + '×' + pixels.height + ' px', asset.sha256) +
      '<dl class="nh01-static-asset-facts"><div><dt>Asset ID</dt><dd>' + esc(asset.asset_id) + '</dd></div>' +
        '<div><dt>SHA-256</dt><dd><code>' + esc(asset.sha256) + '</code></dd></div>' +
        '<div><dt>冻结版本</dt><dd>' + esc(asset.version || 'V1') + ' · ' + esc(asset.status) + '</dd></div></dl>' +
    '</article>';
  }).join('');
  const typographyRows = (contract.typography?.roles || []).map(role =>
    '<tr><th scope="row">' + esc(role.role) + '</th><td>' + esc(role.base_ratio == null ? role.visual_size_dp : 'safeHeight×' + role.base_ratio) + '</td>' +
      '<td>' + esc(role.minimum_sp == null ? '—' : role.minimum_sp + '–' + role.maximum_sp + ' sp') + '</td>' +
      '<td>' + esc(role.line_height_ratio == null ? '—' : role.line_height_ratio + '×') + '</td>' +
      '<td>' + esc(role.system_font_scale) + '</td></tr>'
  ).join('');
  const spacingRows = (contract.spacing?.title_to_card_targets_dp_at_font_scale_1_0 || []).map(row =>
    '<tr><th scope="row">' + esc(row.viewport) + '</th><td>' + esc(row.target + '±' + row.tolerance + ' dp') + '</td><td>' + esc(row.authority) + '</td></tr>'
  ).join('');
  const intervals = (contract.spacing?.required_adjacent_interval_measurements || []).map(row =>
    '<tr><th scope="row">' + esc(row.id) + '</th><td>' + esc(row.bounds) + '</td><td>' + esc(row.reference) + '</td><td>' + nh01RuleList(row.checks) + '</td></tr>'
  ).join('');
  const structure = (contract.page_structure || []).map(item => '<li>' + esc(item) + '</li>').join('');
  const hero = contract.hero_and_peek || {};
  const camera = contract.camera_and_touch || {};
  const bg = contract.background_avatar_photos || {};
  const native = contract.native_acceptance || {};
  return '<div class="nh01-static-workspace" data-testid="nh01-multiscreen-workspace">' +
    '<section class="nh01-static-freeze-banner"><div><span class="nh01-static-eyebrow">NH01 · 有数据首页 · V1</span>' +
      '<h2>五屏静态高保真与响应式合同</h2><p>五张原始冻结稿按原始比例展示。目标 dp 视口与 PNG 实际像素尺寸分别登记。</p></div>' +
      '<div class="nh01-static-statuses">' + statusBadge(manifest.status) + statusBadge(manifest.rules_status) +
        '<span class="nh01-runtime-pill">Android · ' + esc(manifest.android_runtime_status) + '</span></div></section>' +
    '<section class="nh01-static-original"><div class="nh01-static-original-copy"><span class="nh01-static-eyebrow">原始参考 · 保持不变</span>' +
      '<h3>1080×1920 px → 360×640 dp 逻辑参考</h3><p>原始 NH01 V1 整页冻结图继续是原始视觉 Authority；M01–M05 是新增样本，不替换它。</p>' +
      '<code>' + esc(original.path) + '</code><p>SHA-256 · <code>' + esc(original.historical_sha256) + '</code></p></div>' +
      hifiImageFigure(original.path, 'NH01 原始冻结参考 V1', '原始画布 1080×1920 px · 逻辑参考 360×640 dp', original.historical_sha256) + '</section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">ASSET REGISTER</span><h2>五张冻结设计稿</h2></div><p>逐张登记目标逻辑视口、Asset ID、冻结状态与实际 PNG 尺寸</p></div>' +
      '<div class="nh01-static-gallery">' + assetCards + '</div></section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">RESPONSIVE CONTRACT</span><h2>页面结构与单调响应式</h2></div></div>' +
      '<div class="nh01-static-two-col"><article class="nh01-static-rule-card"><h3>页面结构</h3><ol>' + structure + '</ol></article>' +
      '<article class="nh01-static-rule-card"><h3>尺寸变化</h3><p>' + esc(contract.responsive_monotonicity.key_rule) + '</p>' +
        '<p><strong>文字倍率：</strong><code>' + esc(contract.responsive_monotonicity.font_viewport_scale_formula) + '</code></p>' +
        '<p><strong>Hero 主卡：</strong><code>' + esc(contract.responsive_monotonicity.hero_width_formula) + '</code></p>' +
        '<p><strong>相机视觉：</strong><code>' + esc(contract.responsive_monotonicity.camera_visual_formula) + '</code></p>' +
        '<p>' + esc(contract.responsive_monotonicity.forbidden) + '</p></article></div></section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">TYPE SCALE</span><h2>字体角色</h2></div><p>系统 fontScale 由 Compose sp 应用一次</p></div>' +
      '<div class="nh01-static-table-wrap"><table><thead><tr><th>角色</th><th>基准字号来源</th><th>范围</th><th>行高</th><th>fontScale 行为</th></tr></thead><tbody>' + typographyRows + '</tbody></table></div>' +
      '<div class="nh01-static-note">' + esc(contract.typography.font_scale_behavior) + '</div></section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">HERO · PEEK · TOUCH</span><h2>主卡、邻卡与底部触控</h2></div></div>' +
      '<div class="nh01-static-facts-grid"><article><span>主 Hero</span><strong>' + esc(hero.hero_ratio) + '</strong><small>最大目标宽 ' + esc(hero.hero_target_width_max_dp) + ' dp · 最大高 ' + esc(hero.hero_height_max_dp) + ' dp</small></article>' +
        '<article><span>真实 Peek</span><strong>宽 ≤' + esc(Math.round(hero.peek.width_max_relative_to_main * 100)) + '%</strong><small>高 ' + esc(Math.round(hero.peek.height_relative_to_main * 100)) + '% · 顶偏移 ' + esc(hero.peek.top_offset_dp) + ' dp · 重叠 ≤' + esc(hero.peek.max_geometry_overlap_dp) + ' dp</small></article>' +
        '<article><span>相机视觉 / 触控</span><strong>' + esc(camera.camera_visual_size_dp.minimum + '–' + camera.camera_visual_size_dp.maximum) + ' dp / ≥' + esc(camera.camera_touch_target_dp.minimum_width) + '×' + esc(camera.camera_touch_target_dp.minimum_height) + ' dp</strong><small>其他动作触控 ≥' + esc(camera.other_action_touch_target_dp.minimum) + ' dp</small></article></div>' +
      '<div class="nh01-static-note">' + esc(hero.single_record) + ' ' + esc(camera.grouping) + ' ' + esc(camera.safe_area) + '</div></section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">VISIBLE GAP TARGETS</span><h2>Recent Catch 标题到主卡片</h2></div><p>fontScale 1.0× · 原生 boundsInWindow 测量定义</p></div>' +
      '<div class="nh01-static-table-wrap"><table><thead><tr><th>目标视口</th><th>可见间距</th><th>权威范围</th></tr></thead><tbody>' + spacingRows + '</tbody></table></div>' +
      '<div class="nh01-static-note nh01-static-note-warning">' + esc(contract.spacing.extension_conflict) + '</div></section>' +
    '<section class="nh01-static-section"><div class="nh01-static-section-head"><div><span class="nh01-static-eyebrow">MEASUREMENT METHOD</span><h2>六组相邻区域验收</h2></div><p>每次记录真实 dp、fontScale、布局和截图/bounds</p></div>' +
      '<div class="nh01-static-table-wrap"><table><thead><tr><th>区域</th><th>测量边界</th><th>当前引用</th><th>检查项</th></tr></thead><tbody>' + intervals + '</tbody></table></div></section>' +
    '<section class="nh01-static-two-col"><article class="nh01-static-rule-card"><span class="nh01-static-eyebrow">SHORT WINDOW</span><h2>短屏与安全区</h2>' +
      nh01RuleList(contract.short_screen_and_safe_area.priority_order.concat(contract.short_screen_and_safe_area.forbidden)) +
      '<p>真实窗口不满足时，记录 ' + esc(contract.short_screen_and_safe_area.failure_states.join(' / ')) + '；静态图不是原生通过证据。</p></article>' +
      '<article class="nh01-static-rule-card"><span class="nh01-static-eyebrow">BACKGROUND · AVATAR · PHOTO</span><h2>背景与头像边界</h2>' +
        '<p>' + esc(bg.background) + '</p><p>' + esc(bg.avatar) + '</p><p>' + esc(bg.photo) + '</p></article></section>' +
    '<section class="nh01-static-native-status"><div><span class="nh01-static-eyebrow">ANDROID NATIVE ACCEPTANCE</span><h2>状态：' + esc(native.status) + '</h2>' +
      '<p>现行矩阵仍是 ' + esc(native.existing_matrix_cases) + ' 次 API 28 原生执行：4 个窗口 × 3 种系统字体缩放 × 2 种布局。实际证据齐全前保持 NOT_VERIFIED。</p>' +
      '<p>M05 是 480 dp 宽度边界，不指定窗口高度，也不追加第五组原生测试。</p></div>' +
      '<a href="' + esc(repoHref(view.native_acceptance_authority)) + '" target="_blank" rel="noreferrer">查看完整原生验收矩阵 ↗</a></section>' +
    '<footer class="nh01-static-workspace-footer">完整响应式合同与新旧权威交叉表 · <a href="' + esc(repoHref(view.machine_authority)) + '" target="_blank" rel="noreferrer">机器可读 JSON ↗</a> · <a href="' + esc(repoHref(view.authority)) + '" target="_blank" rel="noreferrer">完整 Markdown 合同 ↗</a></footer></div>';
}

const nh01LoadHandles = new WeakMap();

function cancelNH01ResourceLoad(root = el("hifiViewCanvas")) {
  const active = root && nh01LoadHandles.get(root);
  if (!active) return;
  active.base?.abort();
  active.assets?.abort();
  active.cancelled = true;
  nh01LoadHandles.delete(root);
}

function nh01ResponsiveRuleCanvas(view) {
  const root = el("hifiViewCanvas");
  cancelNH01ResourceLoad(root);
  const token = `${view.id}:${view.visual_authority_manifest}:${Date.now()}:${Math.random()}`;
  root.dataset.nh01ResponsiveToken = token;
  root.dataset.nh01LoadState = "IDLE";
  const workspacePath = "design/manager/nh01_structured_workspace.js";
  const stylePath = "design/manager/nh01_structured_workspace.css";
  const bindingPath = "design/system_v3/09_Android_Implementation_Contracts/Normal_Home/Normal_Home_Resource_Binding_V1.json";
  const componentTreePath = "design/manager/nh01_acceptance/data/NH01_Component_Tree_V1.json";
  const structurePath = "design/manager/nh01_acceptance/data/NH01_Structure_V1.json";
  const avatarAuthorityPath = "design/pages/home/normal_home/engineering/Visual_Anchor_Asset_Resolution_V1.md";
  const avatarV2Path = "app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png";
  const avatarV2Sha256 = "fe94b11ba9f6c0635cd230bcc786fd2ea9a64d1a6f512dc6078556379676f528";
  const timeoutMs = Number(window.__NH01_TEST_TIMEOUT_MS__) || 20000;
  const urlFor = path => path.startsWith("http") ? path : repoHref(path);
  const responseMime = pattern => ({ mime: pattern });
  const baseSpecs = [
    { id:"frozen-manifest", label:"冻结资产 Manifest", url:urlFor(view.visual_authority_manifest), type:"json", ...responseMime(/application\/json|\+json/i), validate:value => Array.isArray(value?.assets) && value.assets.length === 5 && value.status === "STATIC_HIFI_FROZEN" && value.original_reference?.path && value.original_reference?.historical_sha256 && value.assets.every(asset=>asset.path&&asset.sha256) || "Manifest 缺少 5 张冻结资产、原始参考 SHA 或必要路径" },
    { id:"machine-contract", label:"响应式机器合同 JSON", url:urlFor(view.machine_authority), type:"json", ...responseMime(/application\/json|\+json/i), validate:value => Boolean(value?.contract_id && value?.foundation_layout_principles && value?.spacing?.existing_adaptive_formulas) || "响应式机器合同结构不完整" },
    { id:"markdown-contract", label:"正式 Markdown 合同", url:urlFor(view.authority), type:"text", ...responseMime(/text\//i), validate:value => value.includes("A.2 Long-screen surplus allocation") || "Markdown 合同缺少 A.2 正式公式段落" },
    { id:"component-tree", label:"NH01 组件父子树", url:urlFor(componentTreePath), type:"json", ...responseMime(/application\/json|\+json/i), validate:value => value?.document_type === "component_tree" && value?.root?.id === "41:30" && Array.isArray(value.root.children) || "NH01 组件树缺少已登记页面根节点及子节点" },
    { id:"structure-reference", label:"NH01 结构与样例文本", url:urlFor(structurePath), type:"json", ...responseMime(/application\/json|\+json/i), validate:value => value?.document_type === "structure" && value?.frame?.id === "41:30" && Array.isArray(value?.preview_fixture?.statistics) && value?.preview_fixture?.hero || "NH01 结构样例缺少页面结构、统计或 Hero 样例数据" },
    { id:"avatar-authority", label:"NH01 专用头像视觉锚点", url:urlFor(avatarAuthorityPath), type:"text", ...responseMime(/text\//i), validate:value => value.includes(avatarV2Path) && value.includes(avatarV2Sha256) && value.includes("approved visual identity for Normal Home") || "NH01 专用头像批准原图或视觉权威记录缺失" },
    { id:"resource-binding", label:"Normal Home 资源绑定", url:urlFor(bindingPath), type:"json", ...responseMime(/application\/json|\+json/i), validate:value => value?.brand_title_binding?.registry_path && value?.camera_primary_binding?.registry_path && Array.isArray(value.resources) || "资源绑定缺少品牌、相机或资源列表" },
    { id:"workspace-js", label:"结构化工作区 JavaScript", url:urlFor(workspacePath), type:"bytes", ...responseMime(/javascript|ecmascript/i), validate:() => typeof window.NH01StructuredWorkspace?.mount === "function" && typeof window.NH01StructuredWorkspace?.createResourceLoader === "function" || "JS 请求已成功，但 NH01StructuredWorkspace.mount/createResourceLoader 未执行或缺失" },
    { id:"workspace-css", label:"结构化工作区 CSS", url:urlFor(stylePath), type:"bytes", ...responseMime(/text\/css/i), validate:() => { const link=document.querySelector('link[data-resource-id="nh01-workspace-css"]'); return !!link?.sheet || "CSS 请求已成功，但正式样式表未应用"; } }
  ];
  const script = document.querySelector('script[data-resource-id="nh01-workspace-js"]');
  const link = document.querySelector('link[data-resource-id="nh01-workspace-css"]');
  const wantedBuild = new URL(urlFor(workspacePath), document.baseURI).searchParams.get("v");
  const actualScriptBuild = script ? new URL(script.src).searchParams.get("v") : null;
  const actualStyleBuild = link ? new URL(link.href).searchParams.get("v") : null;
  root.innerHTML = '<div class="nh01-static-loading" data-testid="nh01-resource-loader" role="status" aria-live="polite"><strong>正在载入冻结资产清单与响应式合同…</strong><p>逐项校验正式依赖、HTTP 状态与冻结素材 SHA-256。请求超时后会显示错误和重试入口。</p><div data-resource-report></div></div>';
  const renderProgress = (state, resources, error = null, retry = false) => {
    if (root.dataset.nh01ResponsiveToken !== token) return;
    root.dataset.nh01LoadState = state;
    const rows = resources.map(item => `<tr data-resource-id="${esc(item.id)}"><td>${esc(item.label)}</td><td>${esc(item.status)}</td><td>${esc(item.http_status ?? "—")}</td><td>${esc(item.parse_result || "—")}</td><td><code>${esc(item.url)}</code><br>Content-Type ${esc(item.content_type||"未返回")} · SHA-256 ${esc(item.sha256||"未计算")}<br>开始 ${esc(item.started_at||"未开始")} · 完成 ${esc(item.completed_at||"进行中")}${item.failure_reason ? `<br><strong>${esc(item.failure_kind)} · ${esc(item.failure_reason)}</strong>` : ""}</td></tr>`).join("");
    const report = `<table class="nh01-resource-table"><thead><tr><th>资源</th><th>状态</th><th>HTTP</th><th>解析</th><th>URL / SHA / 原因</th></tr></thead><tbody>${rows || '<tr><td colspan="5">等待首个资源状态…</td></tr>'}</tbody></table>`;
    const loading = state === "LOADING" || state === "RETRYING";
    const title = state === "READY" ? "资源核验完成" : state === "IDLE" ? "等待开始资源校验" : loading ? (state === "RETRYING" ? "正在重试资源…" : "正在载入冻结资产清单与响应式合同…") : "冻结资产与响应式合同载入失败";
    const button = state === "ERROR" ? '<button type="button" data-action="retry">重试全部资源</button>' : "";
    const body = `<div class="nh01-static-${state === "ERROR" ? "load-error" : "loading"}" data-testid="nh01-resource-loader" role="${state === "ERROR" ? "alert" : "status"}" aria-live="polite"><strong>${title}</strong>${error ? `<p>${esc(error)}</p>` : `<p>${loading ? "逐项校验 HTTP、内容类型、JSON 与冻结素材 SHA-256。" : "已核验必要合同、工作区模块、样式及冻结/正式图片资源。"}</p>`}${report}${button}</div>`;
    const current = root.querySelector('[data-testid="nh01-multiscreen-workspace"]');
    if (current && state === "READY") {
      const details = current.querySelector("[data-resource-report]");
      if (details) details.innerHTML = report;
    } else root.innerHTML = body;
  };
  renderProgress("IDLE",[]);
  const missingModuleFallback = async () => {
    const url = script?.src || urlFor(workspacePath);
    const record = { id:"workspace-js", label:"结构化工作区 JavaScript", url, status:"LOADING", http_status:null, parse_result:"NOT_STARTED", sha256:null, failure_kind:null, failure_reason:null };
    renderProgress("LOADING", [record]);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await fetch(url, { cache:"no-store", signal:controller.signal });
      record.http_status = response.status;
      if (!response.ok) throw Object.assign(new Error(`HTTP ${response.status}`), { kind:"http" });
      throw new Error("文件已返回 2xx，但脚本未执行并未暴露 mount()；检查脚本语法、执行顺序和控制台错误。");
    } catch (error) {
      record.status="ERROR";
      record.failure_kind=controller.signal.aborted ? "timeout" : (error.kind || "script_execution");
      record.failure_reason=controller.signal.aborted ? `请求超时（${timeoutMs} ms）` : String(error.message || error);
      record.completed_at=new Date().toISOString();
      renderProgress("ERROR",[record],"工作区模块无法挂载。修复后点击重试即可重新载入页面依赖。");
      root.querySelector('[data-action="retry"]')?.addEventListener("click", () => location.reload());
    } finally { clearTimeout(timer); }
  };
  if (wantedBuild !== DESIGN_MANAGER_BUILD || actualScriptBuild !== DESIGN_MANAGER_BUILD || actualStyleBuild !== DESIGN_MANAGER_BUILD) {
    const mismatch = [{ id:"workspace-build", label:"工作区 JS/CSS 构建标识", url:script?.src || urlFor(workspacePath), status:"ERROR", http_status:null, parse_result:"BUILD_MISMATCH", sha256:null, failure_kind:"version", failure_reason:`HTML=${DESIGN_MANAGER_BUILD}; JS=${actualScriptBuild}; CSS=${actualStyleBuild}` }];
    renderProgress("ERROR", mismatch, "结构化资源与当前 Design Manager 构建 SHA 不一致；请清除旧缓存后重试。");
    root.querySelector('[data-action="retry"]')?.addEventListener("click", () => location.reload());
    return;
  }
  if (typeof window.NH01StructuredWorkspace?.createResourceLoader !== "function" || typeof window.NH01StructuredWorkspace?.mount !== "function") {
    missingModuleFallback();
    return;
  }
  const active = {
    cancelled:false,
    base:null,
    assets:null,
    abort(){ this.cancelled=true; this.base?.abort(); this.assets?.abort(); }
  };
  nh01LoadHandles.set(root, active);
  let baseDiagnostics=[];
  let assetDiagnostics=[];
  const update = snapshot => renderProgress(snapshot.state === "READY" ? "LOADING" : snapshot.state, [...baseDiagnostics,...assetDiagnostics], null, snapshot.state === "RETRYING");
  active.base = window.NH01StructuredWorkspace.createResourceLoader({ timeoutMs, onUpdate:snapshot=>{baseDiagnostics=snapshot.resources;update(snapshot);} });
  active.assets = window.NH01StructuredWorkspace.createResourceLoader({ timeoutMs, onUpdate:snapshot=>{assetDiagnostics=snapshot.resources;update(snapshot);} });
  const toAssetSpec=(id,label,path,sha,mime=/image\/png/i)=>({ id,label,url:urlFor(path),type:"bytes",mime,expectedSha256:sha,validate:value=>value?.sha256===sha || `Asset bytes SHA-256 mismatch for ${path}` });
  async function run(retrying=false) {
    if (active.cancelled || root.dataset.nh01ResponsiveToken !== token) return;
    baseDiagnostics=[]; assetDiagnostics=[];
    renderProgress(retrying ? "RETRYING" : "LOADING", []);
    let baseResult;
    try { baseResult=await active.base.load(baseSpecs,{retry:retrying}); }
    catch (error) { renderProgress("ERROR",baseDiagnostics,String(error)); root.querySelector('[data-action="retry"]')?.addEventListener("click",()=>run(true)); return; }
    if (active.cancelled || root.dataset.nh01ResponsiveToken !== token) return;
    if (baseResult.state !== "READY") {
      renderProgress("ERROR",baseResult.resources,"至少一个权威合同、组件资源或工作区模块未通过请求/内容校验。");
      const failedBootstrapAsset=baseResult.resources.some(item=>["workspace-js","workspace-css"].includes(item.id)&&item.status!=="READY");
      root.querySelector('[data-action="retry"]')?.addEventListener("click",()=>failedBootstrapAsset?location.reload():run(true));
      return;
    }
    const manifest=baseResult.data["frozen-manifest"];
    const contract=baseResult.data["machine-contract"];
    const markdownContract=baseResult.data["markdown-contract"];
    const resourceBinding=baseResult.data["resource-binding"];
    let specs=[];
    try {
      const avatar=(resourceBinding.resources||[]).find(item=>item.path?.endsWith("nh07_default_avatar_1x_64.png"));
      const background=(resourceBinding.resources||[]).find(item=>item.path?.endsWith("Morning_Lake_Master_V1.png"));
      specs=manifest.assets.map(asset=>toAssetSpec(asset.asset_id,`冻结静态图 · ${asset.asset_id}`,asset.path,asset.sha256));
      specs.push(toAssetSpec("original-reference","NH01 原始冻结参考",manifest.original_reference.path,manifest.original_reference.historical_sha256));
      const chevron=(resourceBinding.resources||[]).find(item=>item.path?.endsWith("/ic_chevron_right_v1.svg"));
      for (const item of [background,avatar,chevron]) {
        if (!item) throw new Error("Resource Binding 中缺少冻结背景、已登记组件资产或历史头像校验项");
        specs.push(toAssetSpec(item.asset_id,item.role||item.asset_id,item.path,item.sha256));
      }
      specs[specs.length-1].mime=/image\/svg\+xml/i;
      specs.push(toAssetSpec("normal_home_default_avatar_v2","NH01 专用批准头像 V2",avatarV2Path,avatarV2Sha256));
      const brand=resourceBinding.brand_title_binding, camera=resourceBinding.camera_primary_binding;
      specs.push(toAssetSpec(brand.asset_id,"品牌标题冻结 PNG",brand.registry_path,brand.sha256));
      specs.push(toAssetSpec(camera.asset_id,"相机按钮冻结 PNG",camera.registry_path,camera.sha256));
      if(specs.some(item=>!item.url||!item.expectedSha256)) throw new Error("Manifest 或 Resource Binding 中有缺少 URL/SHA-256 的图片资产");
    } catch(error) {
      renderProgress("ERROR",baseResult.resources,String(error.message||error));
      root.querySelector('[data-action="retry"]')?.addEventListener("click",()=>run(true));
      return;
    }
    let assetResult;
    try { assetResult=await active.assets.load(specs,{retry:retrying}); }
    catch (error) { renderProgress("ERROR",[...baseResult.resources,...assetDiagnostics],String(error)); return; }
    if (active.cancelled || root.dataset.nh01ResponsiveToken !== token) return;
    if (assetResult.state !== "READY") {
      renderProgress("ERROR",[...baseResult.resources,...assetResult.resources],"至少一个冻结 PNG 缺失或 SHA-256 与正式 Manifest/Resource Binding 不一致。");
      root.querySelector('[data-action="retry"]')?.addEventListener("click",()=>run(true));
      return;
    }
    const diagnostics=[...baseResult.resources,...assetResult.resources];
    try {
      if (typeof window.NH01StructuredWorkspace?.mount !== "function") throw new Error("NH01StructuredWorkspace.mount is not callable after dependency loading");
      root.innerHTML=nh01StaticWorkspaceMarkup(view,manifest,contract);
      const staticRoot=root.querySelector(".nh01-static-workspace");
      if (!staticRoot) throw new Error("NH01 static contract workspace container is missing");
      window.NH01StructuredWorkspace.mount(root,view,contract,manifest,DESIGN_MANAGER_BUILD,markdownContract,resourceBinding,diagnostics,baseResult.data["component-tree"],baseResult.data["structure-reference"],baseResult.data["avatar-authority"]);
      if (!root.querySelector('[data-testid="nh01-structured-workspace"]') || !root.querySelector("[data-layer-tree]") || !root.querySelector("[data-artboard]") || !root.querySelector("[data-inspector]")) {
        throw new Error("mount() returned without rendering the layer tree, canvas, and property inspector");
      }
      root.dataset.nh01LoadState="READY";
      renderProgress("READY",diagnostics);
      root.querySelectorAll(".hifi-image-open").forEach(button=>button.addEventListener("click",()=>openHifiImageLightbox(button)));
    } catch (error) {
      renderProgress("ERROR",diagnostics,`工作区挂载失败：${error.message}`);
      root.querySelector('[data-action="retry"]')?.addEventListener("click",()=>run(true));
    }
  }
  run(false);
}

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
    const selectedBranch = hasChildren && !!selectedKey &&
      (selectedKey === key || selectedKey.startsWith(key + "/"));
    const expanded = hasChildren && isLevelOneExpanded(navId, selectedBranch);
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
        const childCode = child.show_code === false ? "" : (child.code || child.id.toUpperCase().replace("_"," · "));
        const childName = child.title.replace(/^[A-Z]\d+\s*·\s*/, "");
        return '<button class="page-subsubitem' + (selectedKey === childKey ? " active" : "") +
          '" data-kind="page-hifi-child" data-page-id="' + esc(feature.id) +
          '" data-hifi-id="' + esc(view.id) +
          '" data-hifi-child-id="' + esc(child.id) + '">' +
          (childCode ? '<span class="subsub-code">' + esc(childCode) + '</span>' : '') +
          '<span class="subsub-name">' + esc(childName) + '</span>' +
          '<span class="subsub-type">' + esc(child.authority_type === "behavior" ? "行为" : child.authority_type === "asset_catalog" ? "资源" : "视觉") + '</span>' +
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
      const resourceChildren = childItems.filter(child => child.menu_group === "resource");
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
          : "",
        resourceChildren.map(renderNestedChild).join("")
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

/**
 * Fish media V1: real-source display examples only.
 * CSS geometry demonstrates Fit/Crop/Adaptive; it does NOT attest safe fish-bbox cropping.
 */
let fishMediaCases = null;

function fishMediaFrameHtml(photoPath, mode, frame) {
  const safeModes = ["fit", "crop", "adaptive"];
  const safeFrames = ["portrait", "landscape", "hero", "square"];
  const m = safeModes.includes(mode) ? mode : "fit";
  const f = safeFrames.includes(frame) ? frame : "hero";
  const url = esc(repoHref(photoPath));
  return '<div class="fish-media-frame fish-media-frame-' + f + ' fish-media-mode-' + m + '">' +
    (m === "adaptive"
      ? '<img class="fish-media-ambient" src="' + url + '" alt="" aria-hidden="true" loading="lazy">'
      : '') +
    '<img class="fish-media-foreground" src="' + url + '" alt="仓库真实鱼获原始照片，容器内显示效果" loading="lazy">' +
    '</div>';
}

function fishMediaOverviewHtml(item) {
  const photo = item.preview;
  const definitions = [
    {m…26464 tokens truncated…_composition || []).map(item =>
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

function accountPrivacyGalleryCanvas(item) {
  const refs = (item.supporting_visual_references || [])
    .map(ref => typeof ref === "string" ? { path: ref, label: ref } : ref)
    .filter(ref => ref.path && isImage(ref.path));
  const cards = refs.map(ref =>
    '<article class="authority-index-card">' +
      '<div class="authority-index-head"><strong>' + esc(ref.label || "Visual reference") + '</strong>' +
        statusBadge(ref.status || "PARTIAL") + '</div>' +
      '<div class="hifi-original">' +
        '<a href="' + esc(repoHref(ref.path)) + '" target="_blank" rel="noreferrer">' +
          '<img src="' + esc(repoHref(ref.path)) + '" alt="' + esc(ref.label || "Visual reference") + '" loading="lazy" decoding="async"></a>' +
        '<div class="hifi-original-caption">' + esc(ref.role || "EXISTING VISUAL REFERENCE") + '</div>' +
        '<div class="authority-kind">' + esc(ref.path) + '</div>' +
        (ref.scope ? '<p>' + esc(ref.scope) + '</p>' : '') +
      '</div>' +
    '</article>'
  ).join("");
  const points = (item.menu_points || []).map((point, index) =>
    '<article class="authority-index-card"><div class="authority-index-head"><strong>' +
      esc(String(index + 1).padStart(2, "0") + " · " + point) +
    '</strong></div><div class="authority-kind">SCOPE / GATE</div></article>'
  ).join("");
  const governance = (item.behavior_status || item.visual_status || item.content_status || item.legal_copy_status)
    ? '<div class="authority-index-head">' +
        (item.behavior_status ? '<span>Behavior / IA&nbsp;' + statusBadge(item.behavior_status) + '</span>' : '') +
        (item.visual_status ? '<span>Visual&nbsp;' + statusBadge(item.visual_status) + '</span>' : '') +
        (item.content_status ? '<span>Content&nbsp;' + statusBadge(item.content_status) + '</span>' : '') +
        (item.legal_copy_status ? '<span>Legal Copy&nbsp;<span class="status status-PARTIAL">' + esc(item.legal_copy_status) + '</span></span>' : '') +
      '</div>'
    : "";
  const adjustment = item.visual_adjustment_authority
    ? '<div class="authority-kind">COMPOSITE VISUAL AUTHORITY</div>' +
      '<p>Base Hi-Fi 保持原始二进制；当前差异由 <a href="' + esc(repoHref(item.visual_adjustment_authority)) +
      '" target="_blank" rel="noreferrer">' + esc(item.visual_adjustment_authority) + '</a> 覆盖。</p>'
    : "";
  const legalGate = item.legal_gate
    ? '<div class="authority-kind">LEGAL GATE</div><p>' + esc(item.legal_gate) + '</p>'
    : "";
  return '<div class="authority-index">' +
    '<div class="authority-index-intro"><strong>' + esc(item.title || "Account & Privacy visual references") + '</strong>' +
      '<span>' + esc(item.summary || "Existing repository images are shown with their current review status.") + '</span>' +
      governance + adjustment + legalGate + '</div>' +
    (cards ? '<div class="authority-index-grid">' + cards + '</div>' : '<div class="preview-empty">没有可展示的仓库视觉图。</div>') +
    (points ? '<div class="authority-index-intro"><strong>产品范围与 Gate</strong></div><div class="authority-index-grid">' + points + '</div>' : '') +
  '</div>';
}

function recognitionGuideResourcesCanvas(child, feature) {
  return '<div class="guide-workspace-root" data-testid="first-use-guide-workspace"' +
    ' data-structured-manifest="' + esc(child.structured_page_manifest || "") + '"' +
    ' data-geometry-manifest="' + esc(child.geometry_measurement_manifest || "") + '"' +
    ' data-responsive-contract="' + esc(child.responsive_contract_manifest || "") + '"' +
    ' data-asset-manifest="' + esc(child.frontend_asset_manifest || "") + '"' +
    ' data-page-manifest="' + esc(child.resource_manifest || "") + '"></div>';
}
function genericSpecChildCanvas(child, feature = null) {
  if (child.render_mode === "recognition_guide_resources") return recognitionGuideResourcesCanvas(child, feature);
  if (child.render_mode === "recognition_master_board") return recognitionMasterBoardCanvas(child);
  if (child.render_mode === "recognition_new_states_review") return recognitionNewStatesReviewCanvas(child);
  if (child.render_mode === "missing_authority") return recognitionMissingAuthorityCanvas(child);
  if (feature?.id === "account_privacy_v1" && child.render_mode === "account_privacy_gallery") {
    return accountPrivacyGalleryCanvas(child);
  }
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
    return hifiImageFigure(visual, child.title,
      child.status === "FROZEN"
        ? "当前子状态 Frozen Visual Authority · 直接引用仓库原始文件 · " + visual
        : "当前视觉参考 · " + statusText(child.status || "PARTIAL") + " · 不构成可信 Frozen Authority · " + visual,
      child.visual_authority_sha256) +
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
                  '<div class="authority-kind">' + esc(child.status === "FROZEN" ? "FROZEN SPEC" : statusText(child.status || "PARTIAL").toUpperCase() + " SPEC / GATE") + '</div>' +
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
  cancelNH01ResourceLoad(el("hifiViewCanvas"));
  const panel=el("hifiViewPanel");
  if(!hifiId){ panel.classList.add("hidden"); return; }
  const view=(feature.hifi_views||[]).find(x=>x.id===hifiId);
  if(!view){ panel.classList.add("hidden"); return; }
  const child=hifiChildId ? (view.children||[]).find(x=>x.id===hifiChildId) : null;
  panel.classList.remove("hidden");
  const authorityType = child?.authority_type || view.authority_type || "visual";
  el("hifiViewEyebrow").textContent =
    view.render_mode === "recognition_full_flow_models" ? "业务流程与模型职责 · DESIGN ONLY" :
    view.render_mode === "nh01_multi_screen_static_hifi" ? "STATIC_HIFI_FROZEN · STATIC_RULES_FROZEN · Android NOT_VERIFIED" :
    view.render_mode === "recognition_main_journey_overview" ? "业务流程总览 · 不代表单页视觉 Authority" :
    view.render_mode === "recognition_boundary_release_gate" ? "主链路边界状态 · 设计与资产 Gate" :
    child?.render_mode === "recognition_new_states_review" ? "Review Source · 非 Frozen 单页 Authority" :
    child?.status === "BLOCKED_INTEGRITY" ? "视觉资源完整性阻塞 · 无预览" :
    child?.status === "MISSING" ? "视觉 Authority 缺失 · 无预览" :
    child?.status === "PARTIAL" && !child?.authority_type ? "Review / 状态审视 · 非 Frozen" :
    authorityType === "behavior" ? "交互规范" :
    child && feature.id === "account_privacy_v1" && child.visual_status === "FROZEN" && child.legal_copy_status ? "冻结视觉 / Legal Copy Gate" :
    child && feature.id === "account_privacy_v1" && child.status !== "FROZEN" ? "视觉参考 / Authority" :
    child?.status === "DESIGN_ONLY" ? "视觉参考 / Authority" :
    child ? "冻结高保真 Authority" : "高保真 / Authority 索引";
  el("hifiViewTitle").textContent=child ? child.title : view.title;
  el("hifiViewStatus").innerHTML=statusBadge(child?.status || view.status || "PARTIAL");
  const summary=child?.summary || view.summary;
  if(summary){
    el("hifiViewSummary").textContent=summary;
    el("hifiViewSummary").classList.remove("hidden");
  } else el("hifiViewSummary").classList.add("hidden");
  el("hifiViewCanvas").innerHTML = child
    ? (feature.id === "my_catches_v2" ? myCatchesSearchChildCanvas(child) : genericSpecChildCanvas(child, feature))
    : (feature.id === "my_catches_v2" ? myCatchesHifiCanvas(feature,view) : genericSpecHifiCanvas(feature,view));

  if (child?.render_mode === "recognition_guide_resources") {
    window.FirstUseGuideWorkspace?.mount(el("hifiViewCanvas").querySelector(".guide-workspace-root"));
  }

  if (!child && ["web_hifi_contract_v2", "swos_static_visual_v1_1"].includes(view.id)) {
    const viewer = document.createElement("iframe");
    viewer.src = "./private_visual_references.html?reference=" + (view.id === "web_hifi_contract_v2" ? "web-three-stage" : "swos-history");
    viewer.title = "原始高保真私有查看与合同对照";
    viewer.style = "width:100%;height:980px;border:1px solid #b9cdc6;border-radius:12px";
    el("hifiViewCanvas").prepend(viewer);
    if (view.id === "web_hifi_contract_v2") {
      const provenance = document.createElement("p");
      provenance.className = "hifi-source-provenance";
      provenance.innerHTML = statusBadge("PIXEL_BASELINE_FROZEN_PRIVATE") + " 主视觉基准：web-three-stage · 1536×1024 · SHA-256 27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2. 冻结的是私有源身份和对照协议，不代表运行页面像素一致。原图字节本次未重新读取；本地查看器必须校验 SHA、尺寸和字节数。公开许可 NOT_CONFIRMED；RUNTIME_VISUAL_VERIFIED=NO；仅本地内存查看，不上传服务器。";
      el("hifiViewCanvas").prepend(provenance);
    }
  }
  if (!child && view.render_mode === "swos_optical_parameters_v1_2") {
    window.SWOSOpticalV12?.mount(el("hifiViewCanvas"), view);
  }
  if (!child && view.render_mode === "swos_static_visual_workspace") {
    window.SWOSStaticVisual?.mount(el("hifiViewCanvas"), view);
  }
  if (!child && Array.isArray(view.related_routes) && view.related_routes.length) {
    const related = '<nav class="hifi-related-routes" aria-label="关联 Design Manager 页面">' +
      '<strong>关联页面</strong>' + view.related_routes.map(route =>
        '<a href="' + esc(route.route) + '">' + esc(route.label) + ' ↗</a>' +
        (route.note ? '<span>' + esc(route.note) + '</span>' : '')
      ).join("") + '</nav>';
    el("hifiViewCanvas").insertAdjacentHTML("beforeend", related);
  }
  if (!child && view.render_mode === "nh01_multi_screen_static_hifi") {
    nh01ResponsiveRuleCanvas(view);
  } else {
    delete el("hifiViewCanvas").dataset.nh01ResponsiveToken;
  }

  const contractDetailsItem = view.id === "swos_web_hifi_motion_contract_v1_0"
    ? {
        ...(child || view),
        contract_links: [
          ...((child || view).contract_links || []),
          ...(view.contract_links || []),
          { label: "机器可读技术合同 V1.0", path: view.machine_authority || "design/pages/recognition/web/contracts/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json" }
        ]
      }
    : (child || view);
  const contractDetails = hifiContractMetadataHtml(contractDetailsItem, view);
  el("hifiViewContractDetails").innerHTML = contractDetails;
  el("hifiViewContractDetails").classList.toggle("hidden", !contractDetails);
  el("hifiViewCanvas").querySelectorAll(".hifi-image-open").forEach(button =>
    button.addEventListener("click", () => openHifiImageLightbox(button))
  );

  if (!child && view.render_mode === "nh07_avatar_states") {
    el("hifiViewCanvas").querySelectorAll(".nh07-master-open").forEach(anchor => {
      anchor.addEventListener("click", event => {
        event.preventDefault();
        openNh07MasterLightbox(anchor, anchor.href);
      });
    });
  }

  const sceneIds=child?.scenario_ids || view.scenario_ids || [];
  const scenes=sceneIds.map(id=>(feature.scenario_pages||[]).find(x=>x.id===id)).filter(Boolean);
  el("hifiViewScenarios").innerHTML=scenes.map(scene=>
    '<button class="hifi-scenario-chip" data-scene-id="'+esc(scene.id)+'">'+esc(scene.title)+'</button>'
  ).join("") || '<span class="preview-empty compact">无附加场景映射</span>';

  const supportingAuthorityPaths = [...(child?.supporting_visual_references || []), ...(view.supporting_visual_references || [])]
    .map(ref => typeof ref === "string" ? ref : ref.path)
    .filter(Boolean);
  el("hifiViewAuthorities").innerHTML=[child?.primary_visual_authority,...supportingAuthorityPaths,child?.visual_authority,child?.behavior_authority,child?.machine_authority,child?.authority,child?.secondary_authority,view.visual_authority,view.behavior_authority,view.machine_authority,view.secondary_authority,view.visual_authority_manifest,view.authority,view.image].filter((v,i,a)=>v&&a.indexOf(v)===i).map(path=>
    '<a class="authority-row" href="'+esc(repoHref(path))+'" target="_blank" rel="noreferrer">'+esc(path)+'</a>'
  ).join("");

  if(child && child.render_mode === "frozen_image"){
    el("hifiViewSource").textContent="本子页面只显示自身冻结视觉 Authority；父页面主视觉与旧动态预览均不在此层渲染。";
    el("hifiViewSource").classList.remove("hidden");
  } else if(child && child.authority_type === "behavior"){
    el("hifiViewSource").textContent="本子项为 Behavior Contract，不渲染 App 高保真页面。";
    el("hifiViewSource").classList.remove("hidden");
  } else if(child && feature.id === "account_privacy_v1" && child.render_mode === "account_privacy_gallery"){
    el("hifiViewSource").textContent = child.visual_status === "FROZEN"
      ? "展示当前冻结视觉基底及其复合 Authority；Shared / written Authority 可覆盖旧图中已明确废弃的细节。Legal Copy、Deferred 与 Runtime 状态仍独立治理。"
      : "展示仓库内既有视觉文件及其状态标注；未标 FROZEN 的历史参考或 MVP Deferred 不构成当前冻结 Authority，也不代表 Runtime 实现。";
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

function selectPage(id, scenarioId = null, hifiId = null, hifiChildId = null, writeHistory = true) {
  const feature = pageRegistry.features.find(f => f.id === id);
  if (!feature) return;
  const scene = scenarioId ? (feature.scenario_pages || []).find(x => x.id === scenarioId) : null;
  const hifi = hifiId ? (feature.hifi_views || []).find(x => x.id === hifiId) : null;
  const hifiChild = hifiChildId && hifi ? (hifi.children || []).find(x => x.id === hifiChildId) : null;
  selectedKey = "page/" + id +
    (hifi ? "/hifi/" + hifi.id + (hifiChild ? "/" + hifiChild.id : "") :
      (scene ? "/scenario/" + scene.id : ""));
  writeManagerRoute(
    "#page/" + encodeURIComponent(id) +
      (hifi ? "/hifi/" + encodeURIComponent(hifi.id) + (hifiChild ? "/" + encodeURIComponent(hifiChild.id) : "") :
        (scene ? "/scenario/" + encodeURIComponent(scene.id) : "")),
    writeHistory
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

function renderInvalidManagerRoute(raw) {
  selectedKey = null;
  renderLists();
  hideAllDetails();
  el("emptyState").innerHTML =
    '<div class="invalid-manager-route"><strong>无法打开这个 Design Manager 页面</strong>' +
      '<span>Hash 路由无效或 Registry 中没有对应资源；没有回退到其他 Frozen 页面。</span>' +
      '<code>#' + esc(raw || "(empty)") + '</code></div>';
  el("emptyState").classList.remove("hidden");
}

function selectFromHash() {
  const encoded = location.hash.replace(/^#/, "");
  let raw;
  try { raw = decodeURIComponent(encoded); }
  catch (_) { return renderInvalidManagerRoute(encoded); }
  // A browser hash traversal fires both popstate and hashchange. The first
  // event renders the route; ignoring its duplicate prevents active resource
  // loaders on non-iframe pages from being cancelled and restarted.
  if (raw === selectedKey) return;
  if (!raw) return selectShared(sharedRegistry.items[0]?.id, null, false);
  if (raw.startsWith("shared/")) {
    const parts = raw.split("/");
    const id = parts[1];
    const variantId = parts[2] || null;
    const item = sharedRegistry.items.find(x => x.id === id);
    if (item && parts.length <= 3) {
      const validVariant = !variantId || (item.variants || []).some(v => v.id === variantId);
      if (validVariant) return selectShared(id, variantId, false);
    }
  }
  if (raw.startsWith("page/")) {
    const parts = raw.split("/");
    const id = parts[1];
    const feature = pageRegistry.features.find(x => x.id === id);
    if (feature) {
      if (parts[2] === "hifi" && parts[3] && (parts.length === 4 || parts.length === 5)) {
        const hifiId = parts[3];
        const view = (feature.hifi_views || []).find(x => x.id === hifiId);
        if (view) {
          const childId = parts[4] || null;
          if (!childId || (view.children || []).some(x => x.id === childId)) {
            return selectPage(id, null, hifiId, childId, false);
          }
        }
      }
      if (parts[2] === "scenario" && parts[3] && parts.length === 4) {
        const scenarioId = parts[3];
        if ((feature.scenario_pages || []).some(x => x.id === scenarioId)) {
          return selectPage(id, scenarioId, null, null, false);
        }
      }
      if (parts.length === 2) return selectPage(id, null, null, null, false);
    }
  }
  renderInvalidManagerRoute(raw);
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
    const mainJourneyBoard = pageRegistry.features.find(x => x.id === "recognition_flow_v1_2")
      ?.hifi_views?.find(x => x.id === "boundary_state_release_gate")
      ?.children?.find(x => x.id === "master_board_v1");
    if (mainJourneyBoard?.machine_authority) {
      try {
        const boardResponse = await fetch(repoHref(mainJourneyBoard.machine_authority), { cache: "no-store" });
        if (!boardResponse.ok) throw new Error("Master Board schema HTTP " + boardResponse.status);
        recognitionMasterBoard = await boardResponse.json();
        if (recognitionMasterBoard.board_id !== mainJourneyBoard.board_id) {
          throw new Error("Master Board schema ID mismatch");
        }
      } catch (boardError) {
        recognitionMasterBoard = null;
        console.warn("Master Board status schema unavailable:", boardError);
      }
    }
    // RR07 loads the machine contract once; lack of deployment never breaks other pages.
    const rr07 = pageRegistry.features.find(x => x.id === "recognition_result_v1")
      ?.hifi_views?.find(x => x.id === "decision_logic");
    if (rr07?.machine_authority) {
      try {
        const decisionResponse = await fetch(repoHref(rr07.machine_authority), { cache: "no-store" });
        if (!decisionResponse.ok) throw new Error("RR07 contract HTTP " + decisionResponse.status);
        recognitionDecisionContract = await decisionResponse.json();
        if (recognitionDecisionContract.contract !== "recognition_result_3plus2_decision_contract") {
          throw new Error("RR07 wrong contract ID");
        }
      } catch (decisionError) {
        recognitionDecisionContract = null;
        console.warn("RR07 decision contract unavailable:", decisionError);
      }
    }



    // Media demo is an optional versioned fixture manifest. Keep other
    // Design Manager modules accessible if the preview data cannot load.
    if (sharedRegistry.items.some(x => x.id === "fish_media_display_v1")) {
      try {
        const mediaResponse = await fetch(
          repoHref("design/system/fish_media_display_v1/real_photo_cases.json"),
          { cache: "no-store" }
        );
        if (mediaResponse.ok) fishMediaCases = await mediaResponse.json();
      } catch (_) { fishMediaCases = null; }
    }

    if (sharedRegistry.items.some(x => x.id === "icon_library_v0_1")) {
      try {
        const iconsResponse = await fetch(
          repoHref("design/system/icon_library_v0_1/icon_inventory_v0_1.json"), {cache:"no-store"}
        );
        if (iconsResponse.ok) iconLibraryInventory = await iconsResponse.json();
      } catch (_) { iconLibraryInventory = null; }
    }

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
  window.addEventListener("popstate", selectFromHash);
}

init();
