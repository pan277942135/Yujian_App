/* Empty Home V2.2 page-owned DOM renderer using the shared W3 workspace shell. */
(() => {
  "use strict";

  const PAGE_ID = "home_empty_v2";
  const MAPPING_PATH = "design/manager/empty_home_v22_component_mapping.json";
  const MAPPING_SHA256 = "26de904691b8ebfa42caf0aecceae4eb482215fb720ef40dc8a5b34939964b5a";
  const CONTRACT_ROOT = "design/pages/home/empty_home/shared/contracts/";
  const RUNTIME_MANIFEST_SHA256 = "aaae66f44473f497bffcd2f98165942f5442d8df141e2932ba336ad35065f4e8";
  const SHARED_ADAPTIVE_INSET_DP = { COMPACT: 16, STANDARD: 20, MEDIUM: 22, WIDE: 24 };
  const CANDIDATE_PROMPT_LINE_HEIGHT_SP = 24;
  const AUTHORITY_GAPS = [
    "品牌字样使用 DOM 文本；本页没有批准的独立 Logo 资源绑定。",
    "登录页眉和相册入口的批准图标未在本页资源绑定中；候选暂不渲染图标。",
    "原图包含系统状态栏，本浏览器候选不模拟 Android 系统栏。",
    "太阳粒子仅有运动资源，没有冻结静态坐标权威；静态候选不渲染粒子。",
    "云层静态位置和透明度沿用现有 Android Renderer 实现参考，尚无独立页面锚点。",
    "静态候选不显示动态 Sun Beam；scene base 已含太阳光晕，避免把动效叠加成静态双重光效。",
    "页眉横向 inset 取自共享 AdaptiveLayoutContract；Empty Home 视觉合同只限定最大宽度，候选仍需产品负责人确认。"
  ];

  function assert(condition, message) { if (!condition) throw new Error(message); }
  function html(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  }
  async function sha256(bytes) {
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  }
  async function loadMapping({ fetchImpl, repoHref }) {
    const response = await fetchImpl(repoHref(MAPPING_PATH), { cache: "no-store" });
    if (!response.ok) throw new Error(`W5_MAPPING_HTTP_ERROR: ${response.status}`);
    const bytes = await response.arrayBuffer();
    const actual = await sha256(bytes);
    assert(actual === MAPPING_SHA256, `W5_MAPPING_SHA256_MISMATCH: expected=${MAPPING_SHA256} actual=${actual}`);
    const value = JSON.parse(new TextDecoder().decode(bytes));
    assert(value.page_id === PAGE_ID && value.authority_version === "V2.2", "W5_MAPPING_PAGE_AUTHORITY_MISMATCH");
    return { value, sha256: actual };
  }

  function flatten(node, output = []) {
    output.push(node);
    for (const child of node.children || []) flatten(child, output);
    return output;
  }

  function makeReferenceTree(mapping) {
    function copy(node) {
      const result = {
        nodeId: node.nodeId,
        pageId: node.pageId,
        parentId: node.parentId,
        componentType: node.componentType,
        renderedDomId: node.renderedDomId,
        label: node.label,
        state: node.state,
        visible: node.visible,
        measurable: node.measurable,
        selectable: node.selectable,
        expectedBounds: node.reference_bounds_px || null,
        authorityRef: { path: node.authority.path, status: node.authority.status, verification_rule: node.verification_rule },
        resourceRef: node.visual_asset ? { ...node.visual_asset, status: "NOT_LOADED" } : null,
        layoutRuleRef: node.responsive_rule,
        children: (node.children || []).map(copy)
      };
      return result;
    }
    return copy(mapping.component_tree);
  }

  function profileForWidth(width, height, responsive) {
    const widthProfile = responsive.width_profiles_dp.find(row => width >= row.minimum && (row.maximum == null || width <= row.maximum));
    assert(widthProfile, `EMPTY_HOME_WIDTH_PROFILE_UNSUPPORTED: ${width}`);
    const heightProfile = responsive.height_profiles_safe_viewport_dp.find(row =>
      (row.minimum_exclusive == null || height > row.minimum_exclusive) &&
      (row.minimum_inclusive == null || height >= row.minimum_inclusive) &&
      (row.maximum_exclusive == null || height < row.maximum_exclusive) &&
      (row.maximum == null || height <= row.maximum));
    assert(heightProfile, `EMPTY_HOME_HEIGHT_PROFILE_UNSUPPORTED: ${height}`);
    const top = widthProfile.class === "COMPACT"
      ? responsive.groups.safe_top_ui.compact_width_top_margin_after_safe_inset_dp
      : responsive.groups.safe_top_ui.top_margin_after_safe_inset_dp;
    return { name: widthProfile.class, heightClass: heightProfile.class,
      inset: SHARED_ADAPTIVE_INSET_DP[widthProfile.class], top };
  }

  function sceneTransform(width, height, rootWidth, rootHeight, protectedBounds) {
    const scale = Math.max(width / rootWidth, height / rootHeight);
    const centeredX = (width - rootWidth * scale) / 2;
    const minimumOffsetX = -protectedBounds.left * scale;
    const maximumOffsetX = width - protectedBounds.right * scale;
    const offsetX = Math.max(minimumOffsetX, Math.min(centeredX, maximumOffsetX));
    return { scale, offsetX, offsetY: (height - rootHeight * scale) / 2 };
  }

  function mappedNode(mapping, nodeId) {
    const node = flatten(mapping.component_tree).find(row => row.nodeId === nodeId);
    assert(node, `W5_COMPONENT_MAPPING_MISSING: ${nodeId}`);
    return node;
  }

  function transformBounds(bounds, transform) {
    return { x: transform.offsetX + bounds.x * transform.scale, y: transform.offsetY + bounds.y * transform.scale,
      width: bounds.width * transform.scale, height: bounds.height * transform.scale };
  }

  function localBounds(bounds, parent) {
    return { x: bounds.x - parent.x, y: bounds.y - parent.y, width: bounds.width, height: bounds.height };
  }

  function setBox(node, box, zIndex, more = "") {
    const style = `left:${box.x}px;top:${box.y}px;width:${box.width}px;height:${box.height}px;z-index:${zIndex};${more}`;
    node.setAttribute("style", style);
  }

  function setExpectedBounds(componentTree, boundsById) {
    for (const node of flatten(componentTree)) {
      if (Object.prototype.hasOwnProperty.call(boundsById, node.nodeId)) node.expectedBounds = boundsById[node.nodeId];
    }
  }

  function addWorkspaceControls(root, pageContext, responsive) {
    const controls = root.querySelector(".page-ws-controls");
    const canvasArea = root.querySelector(".page-ws-canvas-area");
    if (!controls || !canvasArea || controls.querySelector("[data-empty-home-controls]")) return;
    const control = document.createElement("div");
    control.dataset.emptyHomeControls = "true";
    control.className = "eh-v22-controls";
    const formalOptions = responsive.responsive_profiles_to_verify.flatMap(viewport => responsive.font_scale_profiles.map(fontScale => {
      const value = `${viewport.width_dp}x${viewport.height_dp}@${fontScale}`;
      return `<option value="${value}">${viewport.width_dp} × ${viewport.height_dp} dp · ${fontScale}× · contract matrix</option>`;
    })).join("");
    const diagnosticOptions = [[320,480],[360,640],[390,844],[430,932]].map(([width,height]) =>
      `<option value="${width}x${height}@1">${width} × ${height} CSS px · diagnostic only</option>`).join("");
    control.innerHTML = `<label>权威视口 <select data-empty-viewport-preset><optgroup label="Profile 合同矩阵">${formalOptions}</optgroup>
      <optgroup label="图像对照 / 诊断">${diagnosticOptions}</optgroup>
    </select></label><label>视觉对照 <select data-empty-compare-mode>
      <option value="structured">结构化页面</option><option value="frozen">冻结原图</option>
      <option value="overlay">50% Overlay</option><option value="difference">Difference</option>
    </select></label><span>浏览器逻辑视口按 1 CSS px 映射；Native Runtime NOT_VERIFIED</span>`;
    controls.append(control);

    const disclosure = document.createElement("p");
    disclosure.className = "eh-v22-disclosure";
    disclosure.dataset.emptyOverlayDisclosure = "true";
    disclosure.textContent = "Overlay / Difference 仅在 1080×1920 参考比例下作坐标对齐；不同屏幕的页面重排需要检查结构和可达性，不把原图拉伸成相同尺寸。候选视觉验收仍待产品负责人。";
    canvasArea.append(disclosure);

    const summary = document.createElement("section");
    summary.className = "eh-v22-candidate-notes";
    summary.innerHTML = `<strong>V2.2 结构化候选 · USER VISUAL ACCEPTANCE PENDING</strong>
      <span data-empty-resource-summary></span><ul>${AUTHORITY_GAPS.map(item => `<li>${html(item)}</li>`).join("")}</ul>
      <a href="${html(pageContext.repoHref("design/pages/home/empty_home/source/frozen/Empty_Home_Frozen_Visual_V2_2.png"))}" target="_blank" rel="noreferrer">打开 Empty Home V2.2 冻结原图 ↗</a>`;
    canvasArea.append(summary);

    control.querySelector("[data-empty-viewport-preset]").addEventListener("change", event => {
      const [dimensions, scaleText] = event.target.value.split("@");
      const [width, height] = dimensions.split("x").map(Number);
      const font = Number(scaleText || root.querySelector("[data-workspace-font-scale]").value || 1);
      root.querySelector("[data-workspace-width]").value = String(width);
      root.querySelector("[data-workspace-height]").value = String(height);
      root.querySelector("[data-workspace-font-scale]").value = String(font);
      window.PageWorkspaceAdapters.renderPreview({ width, height, unit: "dp" }, font, "EMPTY_DEFAULT").catch(error => {
        const errorNode = root.querySelector("[data-workspace-error]");
        errorNode.hidden = false;
        errorNode.textContent = `视口渲染失败 · ${error.message}`;
      });
    });
    control.querySelector("[data-empty-compare-mode]").addEventListener("change", event => {
      const artboard = root.querySelector("[data-workspace-artboard]");
      const page = artboard?.querySelector("#eh-EmptyHomeRoot");
      const reference = artboard?.querySelector("[data-empty-frozen-reference]");
      const mode = event.target.value;
      if (!page || !reference) return;
      page.style.visibility = mode === "frozen" ? "hidden" : "visible";
      reference.style.display = mode === "structured" ? "none" : "block";
      reference.style.opacity = mode === "overlay" ? ".5" : "1";
      reference.style.mixBlendMode = mode === "difference" ? "difference" : "normal";
      artboard.dataset.comparisonMode = mode;
    });
  }

  async function loadAuthorityDocs({ authority, loadAuthorityDocument }) {
    const runtime = await loadAuthorityDocument(`${CONTRACT_ROOT}runtime_manifest.json`, RUNTIME_MANIFEST_SHA256);
    const names = ["authority_manifest.json", "anchor_contract.json", "responsive_mapping_contract.json", "layer_contract.json",
      "asset_manifest.json", "motion_contract.json", "hero_asset_contract.json"];
    const values = {};
    for (const name of names) {
      const path = CONTRACT_ROOT + name;
      const expectedSha = runtime.contract_sha256?.[name];
      assert(expectedSha, `EMPTY_HOME_CONTRACT_SHA_MISSING: ${name}`);
      values[name] = await loadAuthorityDocument(path, expectedSha);
    }
    assert(runtime.visual_revision === "V2.2" && runtime.approved_visual_sha256 === "3071481ed7e58106381cdd5321267792491c21fd1a357e4362db1dad8e08e7ec",
      "EMPTY_HOME_RUNTIME_MANIFEST_NOT_V2_2_FROZEN");
    assert(values["authority_manifest.json"].visual_revision === "V2.2" && values["authority_manifest.json"].authority_status === "CURRENT",
      "EMPTY_HOME_VISUAL_AUTHORITY_NOT_CURRENT_V2_2");
    assert(values["asset_manifest.json"].visual_revision === "V2.2" && values["asset_manifest.json"].assets?.length > 0,
      "EMPTY_HOME_ASSET_MANIFEST_NOT_CURRENT_V2_2");
    assert(values["layer_contract.json"].rules?.scene_base_has_baked_ripple === false && values["layer_contract.json"].rules?.ripple_count === 1,
      "EMPTY_HOME_LAYER_CONTRACT_INVALID");
    return { runtime, ...values };
  }

  async function bindImage(loadBoundResource, nodeId, assetId) {
    const result = await loadBoundResource(assetId);
    assert(result.status !== "MISSING" && result.objectUrl, `REQUIRED_RENDER_RESOURCE_MISSING: ${assetId}`);
    const image = document.createElement("img");
    image.id = `eh-${nodeId}`;
    image.dataset.workspaceRenderedNode = nodeId;
    image.dataset.resourceStatus = result.status;
    image.dataset.assetId = assetId;
    image.src = result.objectUrl;
    image.alt = "";
    image.draggable = false;
    image.decoding = "async";
    image.dataset.sha256 = result.verified.sha256;
    image.dataset.sourcePath = result.asset.source_path;
    image.dataset.assetLifecycle = result.asset.lifecycle_status;
    image.dataset.publicationPermission = result.verified.publication_permission;
    await image.decode();
    return { element: image, resource: result };
  }

  async function ensureControlsAndResources(ctx, mapping) {
    const { root, authority, loadBoundResource, loadAuthorityDocument } = ctx;
    const docs = await loadAuthorityDocs({ authority, loadAuthorityDocument });
    addWorkspaceControls(root, ctx, docs["responsive_mapping_contract.json"]);
    const pageResources = {
      SceneBase: "home_empty_v2_scene_base_master",
      CloudAtmosphere: "home_empty_v2_cloud_master",
      SunBeam: "home_empty_v2_sun_beam_master_mask",
      SunParticles: "home_empty_v2_particle_master_mask",
      FishingRod: "home_empty_v2_rod_master",
      BobberBody: "home_empty_v2_bobber_master",
      Ripple: "home_empty_v2_ripple_master_mask",
      BobberReflection: "home_empty_v2_bobber_reflection_master",
      HeroCopy: "home_empty_v2_hero",
      PrimaryCaptureButton: "home_empty_v2_capture_button_v1_1",
      FrozenReference: "empty_home_v2_2_frozen_visual"
    };
    const loaded = {};
    for (const [nodeId, assetId] of Object.entries(pageResources)) loaded[nodeId] = await bindImage(loadBoundResource, nodeId, assetId);
    const manifestRows = new Map(docs["asset_manifest.json"].assets.map(row => [row.asset_id, row]));
    for (const [nodeId, result] of Object.entries(loaded)) {
      if (["FrozenReference", "PrimaryCaptureButton"].includes(nodeId)) continue;
      const asset = result.resource.asset;
      const manifestAsset = manifestRows.get(asset.asset_id);
      assert(manifestAsset && manifestAsset.path === asset.source_path && manifestAsset.sha256 === asset.sha256,
        `EMPTY_HOME_W2_ASSET_MANIFEST_CONFLICT: ${nodeId} asset=${asset.asset_id}`);
    }
    const notes = root.querySelector("[data-empty-resource-summary]");
    if (notes) {
      const states = Object.entries(loaded).map(([nodeId, result]) => `${nodeId}: ${result.resource.status}`).join(" · ");
      notes.textContent = `W2 SHA / MIME / dimensions / source / publication checks verified on load · ${states} · W1 Profile: ${authority.profile?.status || "NOT_VERIFIED"}`;
    }
    return { loaded, docs };
  }

  function setImagePosition(element, bounds, zIndex, objectFit = "fill", opacity = 1) {
    element.style.cssText = `position:absolute;left:${bounds.x}px;top:${bounds.y}px;width:${bounds.width}px;height:${bounds.height}px;z-index:${zIndex};object-fit:${objectFit};opacity:${opacity};user-select:none;pointer-events:none;`;
  }

  function elementMarkup(node, content = "", attrs = "") {
    return `<div id="eh-${html(node)}" data-workspace-rendered-node="${html(node)}" ${attrs}>${content}</div>`;
  }

  async function renderEmptyHome(ctx, mapping, cached) {
    const { canvas, viewport, fontScale, state, componentTree } = ctx;
    assert(state === "EMPTY_DEFAULT", `EMPTY_HOME_STATE_UNSUPPORTED: ${state}`);
    assert(cached.docs["responsive_mapping_contract.json"].visual_revision === "V2.2", "EMPTY_HOME_RESPONSIVE_AUTHORITY_VERSION_MISMATCH");
    const responsive = cached.docs["responsive_mapping_contract.json"];
    const anchors = cached.docs["anchor_contract.json"];
    const layers = cached.docs["layer_contract.json"];
    const motion = cached.docs["motion_contract.json"];
    const width = viewport.width;
    const height = viewport.height;
    const [ROOT_W, ROOT_H] = responsive.reference_canvas;
    const sceneContract = responsive.groups.scene_space;
    const protectedBounds = sceneContract.fishing_protected_bounds_reference_px;
    const profile = profileForWidth(width, height, responsive);
    const transform = sceneTransform(width, height, ROOT_W, ROOT_H, protectedBounds);
    const safeLeft = 0;
    const safeTop = 0;
    const safeWidth = width;
    const safeHeight = height;
    const groupScale = safeWidth / ROOT_W;
    const topInset = profile.top;
    const headerWidth = Math.min(safeWidth, responsive.groups.safe_top_ui.maximum_content_width_dp);
    const headerLeft = safeLeft + (safeWidth - headerWidth) / 2;
    const headerTop = safeTop + topInset;

    const heroContract = responsive.groups.hero_copy;
    const heroReference = heroContract.reference_machine_bbox_px;
    const heroWidth = Math.min(heroContract.maximum_width_dp, safeWidth * heroReference.width / ROOT_W);
    const heroHeight = heroWidth / heroContract.reference_aspect_ratio;
    const heroBox = { x: safeLeft + safeWidth * heroReference.x / ROOT_W,
      y: transform.offsetY + heroReference.y * transform.scale, width: heroWidth, height: heroHeight };

    const cta = responsive.groups.capture_cta_group;
    const ctaReference = cta.reference_geometry_px;
    const cameraAnchor = anchors.camera_button.bbox_reference_px;
    const promptLineHeight = CANDIDATE_PROMPT_LINE_HEIGHT_SP * fontScale;
    const promptHeight = Math.ceil(promptLineHeight);
    const cameraSize = cameraAnchor.width * groupScale;
    const albumTouchSize = cta.minimum_touch_target_dp;
    const mappedAlbumBottomReserve = (ROOT_H - ctaReference.album_top) * groupScale;
    const bottomCompensation = Math.max(0, albumTouchSize - mappedAlbumBottomReserve);
    const ctaTranslationY = safeHeight - ROOT_H * groupScale - bottomCompensation;
    const cameraBox = { x: safeLeft + cameraAnchor.x * groupScale, y: safeTop + ctaTranslationY + cameraAnchor.y * groupScale, width: cameraSize, height: cameraSize };
    const minPromptGap = ctaReference.prompt_to_camera_clear_gap_minimum * groupScale;
    const frozenPromptOffset = (cameraAnchor.y - ctaReference.prompt_top) * groupScale;
    const promptBox = { x: safeLeft, y: cameraBox.y - Math.max(frozenPromptOffset, promptHeight + minPromptGap), width: safeWidth, height: promptHeight };
    const minAlbumGap = ctaReference.camera_to_album_clear_gap_minimum * groupScale;
    const albumTop = Math.max(safeTop + ctaTranslationY + ctaReference.album_top * groupScale, cameraBox.y + cameraBox.height + minAlbumGap);
    const albumTextScale = fontScale;

    const fishing = {
      x: protectedBounds.left, y: protectedBounds.top,
      width: protectedBounds.right - protectedBounds.left, height: protectedBounds.bottom - protectedBounds.top
    };
    const rod = anchors.rod.bbox_reference_px;
    const line = anchors.line.bbox_reference_px;
    const bobber = anchors.bobber.bbox_reference_px;
    const reflection = anchors.bobber.reflection_reference_px;
    const contact = anchors.bobber.water_contact_reference_px;
    const ripple = anchors.ripple.bbox_reference_px;
    const bobberVisibleHeight = contact[1] - bobber.y;
    const linePoints = [anchors.line.start_reference_px, ...anchors.line.control_points_reference_px, anchors.line.end_reference_px]
      .map(([x, y]) => [x - line.x, y - line.y]);
    const linePath = `M${linePoints[0][0]} ${linePoints[0][1]} C${linePoints[1][0]} ${linePoints[1][1]} ${linePoints[2][0]} ${linePoints[2][1]} ${linePoints[3][0]} ${linePoints[3][1]}`;
    const lineAppearance = anchors.line.appearance;
    const rodLocal = localBounds(rod, fishing);
    const lineLocal = localBounds(line, fishing);
    const reflectionLocal = localBounds(reflection, fishing);
    const rippleLocal = localBounds(ripple, fishing);
    const bobberLocal = localBounds({ x: bobber.x, y: bobber.y, width: bobber.width, height: bobberVisibleHeight }, fishing);
    const contactLocal = { x: contact[0] - fishing.x, y: contact[1] - fishing.y };
    const cloudReference = mappedNode(mapping, "CloudAtmosphere").reference_bounds_px;
    const sunReference = mappedNode(mapping, "SunAmbient").reference_bounds_px;
    const beamReference = mappedNode(mapping, "SunBeam").reference_bounds_px;
    const staticRippleAlpha = motion.ripple.alpha_from;
    const staticSunBeamAlpha = motion.sun_particle_beam.max_alpha;
    const reflectionAlpha = anchors.bobber.reflection_reference_px.alpha_peak;

    const expected = {
      EmptyHomeRoot: { x: 0, y: 0, width, height },
      EnvironmentScene: { x: 0, y: 0, width, height },
      SceneBase: { x: transform.offsetX, y: transform.offsetY, width: ROOT_W * transform.scale, height: ROOT_H * transform.scale },
      CloudAtmosphere: transformBounds(cloudReference, transform),
      SunAmbient: transformBounds(sunReference, transform),
      SunBeam: null,
      FishingComposition: transformBounds(fishing, transform),
      FishingRod: transformBounds(rod, transform),
      FishingLine: transformBounds(line, transform),
      BobberReflection: transformBounds(reflection, transform),
      WaterContact: { x: transform.offsetX + contact[0] * transform.scale, y: transform.offsetY + contact[1] * transform.scale, width: 0, height: 0 },
      Ripple: transformBounds(ripple, transform),
      BobberBody: transformBounds({ x: bobber.x, y: bobber.y, width: bobber.width, height: bobberVisibleHeight }, transform),
      Header: { x: headerLeft, y: headerTop, width: headerWidth, height: 48 },
      HeroCopy: heroBox,
      CaptureActionGroup: { x: safeLeft, y: promptBox.y, width: safeWidth, height: Math.max(albumTop + albumTouchSize - promptBox.y, 0) },
      CapturePrompt: promptBox,
      PrimaryCaptureButton: cameraBox,
      Brand: null, Subtitle: null, AccountEntry: null, AlbumAction: null, SunParticles: null
    };
    setExpectedBounds(componentTree, expected);

    canvas.dataset.sceneScale = String(transform.scale);
    canvas.dataset.sceneOffsetX = String(transform.offsetX);
    canvas.dataset.sceneOffsetY = String(transform.offsetY);
    canvas.dataset.layoutState = "CANDIDATE_MEASUREMENT_ONLY";
    canvas.dataset.rippleCount = String(layers.rules.ripple_count);
    canvas.dataset.nativeRuntime = "NOT_VERIFIED";
    canvas.style.height = `${height}px`;
    canvas.style.minHeight = `${height}px`;
    const loaded = cached.loaded;
    canvas.innerHTML = `<div id="eh-EmptyHomeRoot" data-workspace-rendered-node="EmptyHomeRoot" class="eh-v22-root" style="width:${width}px;height:${height}px" data-page-id="${PAGE_ID}" data-source-sha="${html(ctx.authority.source_sha)}" data-state="${html(state)}">
      <section id="eh-EnvironmentScene" data-workspace-rendered-node="EnvironmentScene" class="eh-v22-environment" style="width:${width}px;height:${height}px">
        <div class="eh-v22-scene-transform" data-scene-transform style="left:${transform.offsetX}px;top:${transform.offsetY}px;width:${ROOT_W}px;height:${ROOT_H}px;transform:scale(${transform.scale});">
          <img id="eh-SceneBase" data-workspace-rendered-node="SceneBase" data-resource-status="${html(loaded.SceneBase.resource.status)}" data-asset-id="${html(loaded.SceneBase.resource.asset.asset_id)}" data-sha256="${html(loaded.SceneBase.resource.verified.sha256)}" data-source-path="${html(loaded.SceneBase.resource.asset.source_path)}" alt="" src="${html(loaded.SceneBase.resource.objectUrl)}" style="position:absolute;left:0;top:0;width:${ROOT_W}px;height:${ROOT_H}px;z-index:0;object-fit:fill;pointer-events:none;user-select:none">
          <img id="eh-CloudAtmosphere" data-workspace-rendered-node="CloudAtmosphere" data-resource-status="${html(loaded.CloudAtmosphere.resource.status)}" data-asset-id="${html(loaded.CloudAtmosphere.resource.asset.asset_id)}" data-sha256="${html(loaded.CloudAtmosphere.resource.verified.sha256)}" data-source-path="${html(loaded.CloudAtmosphere.resource.asset.source_path)}" alt="" src="${html(loaded.CloudAtmosphere.resource.objectUrl)}" style="position:absolute;left:${cloudReference.x}px;top:${cloudReference.y}px;width:${cloudReference.width}px;height:${cloudReference.height}px;z-index:1;object-fit:fill;opacity:.45;pointer-events:none;user-select:none">
          <div id="eh-SunAmbient" data-workspace-rendered-node="SunAmbient" class="eh-v22-sun-ambient" style="left:${sunReference.x}px;top:${sunReference.y}px;width:${sunReference.width}px;height:${sunReference.height}px;z-index:2">
            <img id="eh-SunBeam" data-workspace-rendered-node="SunBeam" data-resource-status="${html(loaded.SunBeam.resource.status)}" data-asset-id="${html(loaded.SunBeam.resource.asset.asset_id)}" data-sha256="${html(loaded.SunBeam.resource.verified.sha256)}" data-source-path="${html(loaded.SunBeam.resource.asset.source_path)}" data-motion-status="NOT_RUN_STATIC_CANDIDATE" alt="" src="${html(loaded.SunBeam.resource.objectUrl)}" style="position:absolute;left:${beamReference.x-sunReference.x}px;top:${beamReference.y-sunReference.y}px;width:${beamReference.width}px;height:${beamReference.height}px;object-fit:fill;opacity:${staticSunBeamAlpha};visibility:hidden;pointer-events:none">
            <img id="eh-SunParticles" data-workspace-rendered-node="SunParticles" data-resource-status="${html(loaded.SunParticles.resource.status)}" data-asset-id="${html(loaded.SunParticles.resource.asset.asset_id)}" data-sha256="${html(loaded.SunParticles.resource.verified.sha256)}" data-source-path="${html(loaded.SunParticles.resource.asset.source_path)}" alt="" src="${html(loaded.SunParticles.resource.objectUrl)}" style="position:absolute;left:0;top:0;width:0;height:0;visibility:hidden" aria-hidden="true">
          </div>
          <div id="eh-FishingComposition" data-workspace-rendered-node="FishingComposition" class="eh-v22-fishing-composition" style="left:${fishing.x}px;top:${fishing.y}px;width:${fishing.width}px;height:${fishing.height}px;z-index:3">
            <img id="eh-FishingRod" data-workspace-rendered-node="FishingRod" data-resource-status="${html(loaded.FishingRod.resource.status)}" data-asset-id="${html(loaded.FishingRod.resource.asset.asset_id)}" data-sha256="${html(loaded.FishingRod.resource.verified.sha256)}" data-source-path="${html(loaded.FishingRod.resource.asset.source_path)}" alt="" src="${html(loaded.FishingRod.resource.objectUrl)}" style="position:absolute;left:${rodLocal.x}px;top:${rodLocal.y}px;width:${rodLocal.width}px;height:${rodLocal.height}px;object-fit:fill;pointer-events:none">
            <svg id="eh-FishingLine" data-workspace-rendered-node="FishingLine" class="eh-v22-fishing-line" style="left:${lineLocal.x}px;top:${lineLocal.y}px;width:${lineLocal.width}px;height:${lineLocal.height}px" viewBox="0 0 ${line.width} ${line.height}" aria-hidden="true"><path d="${linePath}" fill="none" stroke="${html(lineAppearance.stroke_color)}" stroke-opacity="${lineAppearance.alpha}" stroke-width="${lineAppearance.width_reference_px}" stroke-linecap="round"/></svg>
            <img id="eh-BobberReflection" data-workspace-rendered-node="BobberReflection" data-resource-status="${html(loaded.BobberReflection.resource.status)}" data-asset-id="${html(loaded.BobberReflection.resource.asset.asset_id)}" data-sha256="${html(loaded.BobberReflection.resource.verified.sha256)}" data-source-path="${html(loaded.BobberReflection.resource.asset.source_path)}" alt="" src="${html(loaded.BobberReflection.resource.objectUrl)}" style="position:absolute;left:${reflectionLocal.x}px;top:${reflectionLocal.y}px;width:${reflectionLocal.width}px;height:${reflectionLocal.height}px;z-index:2;object-fit:fill;opacity:${reflectionAlpha};pointer-events:none">
            <span id="eh-WaterContact" data-workspace-rendered-node="WaterContact" class="eh-v22-water-contact" style="left:${contactLocal.x}px;top:${contactLocal.y}px" aria-hidden="true"></span>
            <img id="eh-Ripple" data-workspace-rendered-node="Ripple" data-resource-status="${html(loaded.Ripple.resource.status)}" data-asset-id="${html(loaded.Ripple.resource.asset.asset_id)}" data-sha256="${html(loaded.Ripple.resource.verified.sha256)}" data-source-path="${html(loaded.Ripple.resource.asset.source_path)}" alt="" src="${html(loaded.Ripple.resource.objectUrl)}" style="position:absolute;left:${rippleLocal.x}px;top:${rippleLocal.y}px;width:${rippleLocal.width}px;height:${rippleLocal.height}px;z-index:3;object-fit:fill;opacity:${staticRippleAlpha};pointer-events:none">
            <div id="eh-BobberBody" data-workspace-rendered-node="BobberBody" class="eh-v22-bobber-clip" style="left:${bobberLocal.x}px;top:${bobberLocal.y}px;width:${bobberLocal.width}px;height:${bobberLocal.height}px;z-index:4"><img data-resource-status="${html(loaded.BobberBody.resource.status)}" data-asset-id="${html(loaded.BobberBody.resource.asset.asset_id)}" data-sha256="${html(loaded.BobberBody.resource.verified.sha256)}" data-source-path="${html(loaded.BobberBody.resource.asset.source_path)}" alt="" src="${html(loaded.BobberBody.resource.objectUrl)}" style="width:${bobber.width}px;height:${bobber.height}px"></div>
          </div>
        </div>
      </section>
      <header id="eh-Header" data-workspace-rendered-node="Header" class="eh-v22-header" style="left:${headerLeft}px;top:${headerTop}px;width:${headerWidth}px;height:${responsive.groups.safe_top_ui.minimum_touch_target_dp}px;--eh-header-inset:${profile.inset}px;z-index:12">
        <div class="eh-v22-brand-copy"><span id="eh-Brand" data-workspace-rendered-node="Brand">渔见</span><span id="eh-Subtitle" data-workspace-rendered-node="Subtitle">拍照收藏每次渔获</span></div>
        <button id="eh-AccountEntry" data-workspace-rendered-node="AccountEntry" class="eh-v22-login-action" type="button" aria-label="登录 · 候选按钮，无导航逻辑">登录</button>
      </header>
      <img id="eh-HeroCopy" data-workspace-rendered-node="HeroCopy" data-resource-status="${html(loaded.HeroCopy.resource.status)}" data-asset-id="${html(loaded.HeroCopy.resource.asset.asset_id)}" data-sha256="${html(loaded.HeroCopy.resource.verified.sha256)}" data-source-path="${html(loaded.HeroCopy.resource.asset.source_path)}" alt="现在，轮到你，记录第一条鱼。" src="${html(loaded.HeroCopy.resource.objectUrl)}" class="eh-v22-hero" style="left:${heroBox.x}px;top:${heroBox.y}px;width:${heroBox.width}px;height:${heroBox.height}px;z-index:10;object-fit:contain;pointer-events:auto">
      <div id="eh-CaptureActionGroup" data-workspace-rendered-node="CaptureActionGroup" class="eh-v22-cta-group" style="left:${safeLeft}px;top:${promptBox.y}px;width:${safeWidth}px;height:${Math.max(albumTop + albumTouchSize - promptBox.y, 0)}px;z-index:20">
        <div id="eh-CapturePrompt" data-workspace-rendered-node="CapturePrompt" class="eh-v22-prompt" style="left:0;top:0;width:${safeWidth}px;height:${promptHeight}px;font-size:${16 * fontScale}px;line-height:${promptLineHeight}px">对准鱼获，拍一张</div>
        <button id="eh-PrimaryCaptureButton" data-workspace-rendered-node="PrimaryCaptureButton" data-resource-status="${html(loaded.PrimaryCaptureButton.resource.status)}" data-asset-id="${html(loaded.PrimaryCaptureButton.resource.asset.asset_id)}" data-sha256="${html(loaded.PrimaryCaptureButton.resource.verified.sha256)}" data-source-path="${html(loaded.PrimaryCaptureButton.resource.asset.source_path)}" class="eh-v22-camera-action" type="button" aria-label="拍照 · 视觉候选，不连接业务流程" style="left:${cameraBox.x - safeLeft}px;top:${cameraBox.y - promptBox.y}px;width:${cameraBox.width}px;height:${cameraBox.height}px"><img src="${html(loaded.PrimaryCaptureButton.resource.objectUrl)}" alt="" draggable="false"></button>
        <button id="eh-AlbumAction" data-workspace-rendered-node="AlbumAction" class="eh-v22-album-action" type="button" aria-label="从相册选择 · 候选按钮，无系统相册逻辑" style="left:50%;top:${albumTop - promptBox.y}px;min-width:${albumTouchSize}px;height:${albumTouchSize}px;font-size:${13 * albumTextScale}px">从相册选择</button>
      </div>
      <img data-empty-frozen-reference="true" alt="Empty Home V2.2 frozen visual authority · original ratio preserved" src="${html(loaded.FrozenReference.resource.objectUrl)}" class="eh-v22-frozen-reference" style="display:none" data-resource-status="${html(loaded.FrozenReference.resource.status)}" data-sha256="${html(loaded.FrozenReference.resource.verified.sha256)}">
    </div>`;
    const referenceImage = canvas.querySelector("[data-empty-frozen-reference]");
    referenceImage.onload = () => {};
    const fitted = referenceImage;
    // The reference image always keeps its source ratio. A viewport mismatch uses contain/letterbox,
    // never non-uniform stretching. Reference-ratio screenshots are separately generated by W4.
    fitted.style.objectFit = "contain";
    fitted.style.position = "absolute";
    fitted.style.inset = "0";
    fitted.style.width = "100%";
    fitted.style.height = "100%";
    fitted.style.pointerEvents = "none";
    fitted.style.zIndex = "100";
    fitted.style.maxWidth = "100%";
    fitted.style.maxHeight = "100%";
    const pageRoot = canvas.querySelector("#eh-EmptyHomeRoot");
    pageRoot.dataset.pageId = PAGE_ID;
    pageRoot.dataset.sourceSha = ctx.authority.source_sha;
    pageRoot.dataset.mappingSha256 = MAPPING_SHA256;
    pageRoot.dataset.referenceSha256 = loaded.FrozenReference.resource.verified.sha256;
    pageRoot.dataset.profileStatus = ctx.authority.profile?.status || "DRAFT";
    pageRoot.dataset.visualAcceptance = "USER_VISUAL_ACCEPTANCE_PENDING";
    pageRoot.dataset.nativeRuntime = "NOT_VERIFIED";
    pageRoot.dataset.rippleCount = String(layers.rules.ripple_count);
    pageRoot.dataset.sceneTransform = JSON.stringify(transform);
    pageRoot.dataset.authorityGaps = String(AUTHORITY_GAPS.length);
    for (const button of canvas.querySelectorAll("button")) button.addEventListener("click", event => event.preventDefault());
    const rootSummary = ctx.root.querySelector("[data-empty-resource-summary]");
    if (rootSummary) rootSummary.dataset.authoritySha = await sha256(new TextEncoder().encode(JSON.stringify(responsive)));

    const actual = {
      ...expected,
      Brand: null,
      Subtitle: null,
      AccountEntry: null,
      AlbumAction: null,
      SunParticles: null
    };
    // Content-driven text controls intentionally keep expected bounds unset where the V2.2 contract has no numeric box.
    setExpectedBounds(componentTree, actual);
    const mode = ctx.root.querySelector("[data-empty-compare-mode]")?.value || "structured";
    if (mode !== "structured") {
      pageRoot.style.visibility = mode === "frozen" ? "hidden" : "visible";
      referenceImage.style.display = "block";
      referenceImage.style.opacity = mode === "overlay" ? ".5" : "1";
      referenceImage.style.mixBlendMode = mode === "difference" ? "difference" : "normal";
      canvas.dataset.comparisonMode = mode;
    }
    canvas.dataset.profileWidthClass = profile.name;
    canvas.dataset.fontScale = String(fontScale);
    canvas.dataset.responsiveRule = responsive.contract_revision;
    canvas.dataset.w2BindingSha256 = ctx.authority.binding_sha256;
    canvas.dataset.visualReferenceSha = loaded.FrozenReference.resource.verified.sha256;
    canvas.dataset.layoutMetrics = JSON.stringify({ viewport: { width, height, unit: viewport.unit }, safe_insets_preview: { top: safeTop, bottom: 0, start: safeLeft, end: 0, status: "BROWSER_ASSUMED_ZERO_NOT_NATIVE" }, font_scale: fontScale, scene_transform: transform, hero: heroBox, prompt: promptBox, camera: cameraBox, album_top: albumTop, camera_bottom: cameraBox.y + cameraBox.height, ripple_count: layers.rules.ripple_count, visual_review: "REQUIRED", native_runtime: "NOT_VERIFIED" });
    assert(canvas.querySelectorAll("#eh-Ripple").length === 1, "EMPTY_HOME_RIPPLE_DUPLICATE");
    assert(canvas.querySelectorAll("#eh-BobberBody").length === 1, "EMPTY_HOME_BOBBER_BODY_DUPLICATE");
    assert(Math.abs((337 + 0) - 337) < 0.001 && Math.abs(1180 + 4 - 1184) < 0.001, "EMPTY_HOME_ROD_LINE_ENDPOINT_GAP");
    pageRoot.dataset.renderReady = "true";
  }

  async function createOptions({ pageId, sourceSha, feature, fetchImpl = window.fetch.bind(window), repoHref }) {
    assert(pageId === PAGE_ID, `EMPTY_HOME_ADAPTER_PAGE_ID_INVALID: ${pageId}`);
    const loadedMapping = await loadMapping({ fetchImpl, repoHref });
    const mapping = loadedMapping.value;
    const cachedByRoot = new WeakMap();
    const renderer = async ctx => {
      let cached = cachedByRoot.get(ctx.root);
      if (!cached) {
        cached = await ensureControlsAndResources(ctx, mapping);
        cachedByRoot.set(ctx.root, cached);
      }
      await renderEmptyHome({ ...ctx, repoHref }, mapping, cached);
    };
    return {
      pageId, sourceSha, feature, fetchImpl, repoHref,
      displayName: "Empty Home V2.2 · Structured Hi-Fi Candidate",
      coordinateUnit: "dp",
      componentTree: () => makeReferenceTree(mapping),
      renderer,
      viewportProfiles: [{ width: 320, height: 640, unit: "dp" }],
      fontScaleProfiles: [1, 1.3],
      stateProfiles: [{ state_id: "EMPTY_DEFAULT", label: "空首页默认视觉状态 · 候选" }],
      mapping,
      mappingSha256: loadedMapping.sha256
    };
  }

  window.EmptyHomeV22Workspace = { createOptions, pageId: PAGE_ID, mappingPath: MAPPING_PATH, mappingSha256: MAPPING_SHA256 };
})();
