/* Shared page workspace adapter boundary. Page authorities remain page-owned. */
(() => {
  "use strict";

  const MATRIX_PATH = "design/system_v3/07_Responsive_System/Page_Responsive_Contract_Schema_Validation_Matrix_V1.json";
  const SHA1 = /^[0-9a-f]{40}$/;
  const SHA256 = /^[0-9a-f]{64}$/;
  let registry = null;
  let pageIds = new Set();
  let activeAdapter = null;

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[char]);
  }

  function getRegisteredPage(pageId) {
    const feature = registry?.features?.find(row => row?.id === pageId);
    if (!feature) throw new Error(`PAGE_NOT_REGISTERED: ${pageId}`);
    return feature;
  }

  async function responseBytes(path, { fetchImpl, repoHref, signal }) {
    assert(typeof path === "string" && path.length > 0 && !path.startsWith("/") && !path.includes("\\") &&
      !path.includes(":") && !path.includes("?") && !path.includes("#") && !path.split("/").some(part => part === ".." || part === "."),
    `AUTHORITY_PATH_UNSAFE: ${String(path)}`);
    const response = await fetchImpl(repoHref(path), { cache: "no-store", signal });
    if (!response.ok) throw new Error(`AUTHORITY_HTTP_ERROR: ${path} (${response.status})`);
    const contentType = response.headers.get("content-type") || "";
    return { bytes: await response.arrayBuffer(), contentType };
  }

  async function sha256(bytes) {
    if (!globalThis.crypto?.subtle) throw new Error("INTEGRITY_UNAVAILABLE: Web Crypto SHA-256 is unavailable");
    const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, "0")).join("");
  }

  function safeAuthorityPath(path) {
    assert(typeof path === "string" && path.length > 0 && !path.startsWith("/") && !path.includes("\\") &&
      !path.includes(":") && !path.includes("?") && !path.includes("#") &&
      !path.split("/").some(part => part === ".." || part === "."), `AUTHORITY_PATH_UNSAFE: ${String(path)}`);
    return path;
  }

  function pointerValue(value, pointer) {
    if (pointer === "") return value;
    assert(typeof pointer === "string" && pointer.startsWith("/"), `AUTHORITY_JSON_POINTER_INVALID: ${String(pointer)}`);
    return pointer.slice(1).split("/").map(part => part.replace(/~1/g, "/").replace(/~0/g, "~")).reduce((current, part) => {
      if (Array.isArray(current)) return current[Number(part)];
      return current && typeof current === "object" ? current[part] : undefined;
    }, value);
  }

  function dottedValue(value, path) {
    if (!path) return value;
    return path.split(".").reduce((current, part) => current && typeof current === "object" ? current[part] : undefined, value);
  }

  function sniffMediaType(bytes, path) {
    const view = new Uint8Array(bytes);
    const starts = (...prefix) => prefix.every((byte, index) => view[index] === byte);
    if (starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "image/png";
    if (starts(0xff, 0xd8, 0xff)) return "image/jpeg";
    if (view.length >= 12 && String.fromCharCode(...view.slice(0, 4)) === "RIFF" && String.fromCharCode(...view.slice(8, 12)) === "WEBP") return "image/webp";
    const head = new TextDecoder().decode(view.slice(0, 512)).trimStart();
    if (head.startsWith("<svg") || (path.toLowerCase().endsWith(".svg") && head.includes("<svg"))) return "image/svg+xml";
    return null;
  }

  async function readJson(path, context) {
    const fetched = await responseBytes(path, context);
    let value;
    try { value = JSON.parse(new TextDecoder().decode(fetched.bytes)); }
    catch (error) { throw new Error(`AUTHORITY_JSON_INVALID: ${path}: ${error.message}`); }
    return { value, bytes: fetched.bytes, sha256: await sha256(fetched.bytes), contentType: fetched.contentType };
  }

  function currentVisualPath(feature) {
    const current = (feature.design_versions || []).find(row => row?.current === true);
    return current?.visual_authority || feature.modalities?.visual?.authority || null;
  }

  async function resolvePageAuthority({ pageId, sourceSha, feature, fetchImpl, repoHref, signal }) {
    assert(SHA1.test(sourceSha || ""), `SOURCE_SHA_INVALID: expected a full source SHA for ${pageId}`);
    assert(feature?.id === pageId, `PAGE_ID_MISMATCH: registry=${feature?.id || "missing"}, requested=${pageId}`);
    const context = { fetchImpl, repoHref, signal };
    const matrixResult = await readJson(MATRIX_PATH, context);
    const cases = matrixResult.value?.cases || [];
    const matches = cases.filter(row => row?.page_id === pageId);
    assert(matches.length === 1, `W1_PROFILE_NOT_REGISTERED: ${pageId}`);
    const pageCase = matches[0];
    assert(typeof pageCase.envelope === "string" && typeof pageCase.profile === "string", `W1_PROFILE_PATH_MISSING: ${pageId}`);
    const [envelopeResult, profileResult] = await Promise.all([
      readJson(pageCase.envelope, context), readJson(pageCase.profile, context)
    ]);
    const envelope = envelopeResult.value;
    const profile = profileResult.value;
    assert(envelope?.page?.page_id === pageId, `ENVELOPE_PAGE_ID_MISMATCH: ${pageId}`);
    assert(profile?.page_id === pageId, `PROFILE_PAGE_ID_MISMATCH: ${pageId}`);
    assert(envelope?.conformance_profile_ref?.path === pageCase.profile, `PROFILE_AUTHORITY_MISMATCH: ${pageId}`);
    assert(envelope?.conformance_profile_ref?.sha256 === profileResult.sha256, `PROFILE_SHA_MISMATCH: ${pageId}`);

    const ownerPath = feature.owner_path;
    assert(typeof ownerPath === "string" && ownerPath.length > 0, `PAGE_OWNER_PATH_MISSING: ${pageId}`);
    const bindingPath = `${ownerPath.replace(/\/$/, "")}/Page_Resource_Binding_V1.json`;
    const bindingResult = await readJson(bindingPath, context);
    const binding = bindingResult.value;
    assert(binding?.page_id === pageId, `RESOURCE_BINDING_PAGE_ID_MISMATCH: ${pageId}`);
    const currentVersion = (feature.design_versions || []).find(row => row?.current === true)?.version;
    assert(currentVersion && binding.authority_version === currentVersion, `RESOURCE_AUTHORITY_VERSION_MISMATCH: ${pageId}`);
    const visualPath = currentVisualPath(feature);
    assert(visualPath, `VISUAL_AUTHORITY_PATH_MISSING: ${pageId}`);
    const assetMatches = (binding.asset_bindings || []).filter(asset => asset?.source_path === visualPath);
    assert(assetMatches.length === 1, `VISUAL_ASSET_BINDING_MISSING_OR_AMBIGUOUS: ${pageId}`);
    const asset = assetMatches[0];
    assert(asset.page_id === pageId || binding.page_id === pageId, `RESOURCE_ASSET_PAGE_ID_MISMATCH: ${pageId}`);
    assert(asset.requiredness === "REQUIRED", `VISUAL_AUTHORITY_NOT_REQUIRED: ${pageId}`);
    assert(asset.media_type?.startsWith("image/"), `VISUAL_ASSET_MEDIA_TYPE_INVALID: ${pageId}`);
    assert(SHA256.test(asset.sha256 || ""), `VISUAL_ASSET_SHA_MISSING: ${pageId}`);
    assert(Number.isInteger(asset.bytes) && asset.bytes > 0, `VISUAL_ASSET_BYTE_COUNT_MISSING: ${pageId}`);
    assert(Number.isInteger(asset.pixel_dimensions?.width) && Number.isInteger(asset.pixel_dimensions?.height), `VISUAL_ASSET_DIMENSIONS_MISSING: ${pageId}`);
    assert(asset.publication_permission?.state === "PUBLIC_BY_EXISTING_PAGES_RULE", `VISUAL_ASSET_PERMISSION_NOT_CONFIRMED: ${pageId}`);
    const w1Visual = (envelope.authority_refs || []).filter(row => row?.role === "PAGE_VISUAL_AUTHORITY");
    assert(w1Visual.some(row => row.path === visualPath && row.sha256 === asset.sha256), `W1_W2_VISUAL_AUTHORITY_CONFLICT: ${pageId}`);

    const visualFetched = await responseBytes(visualPath, context);
    const actualSha = await sha256(visualFetched.bytes);
    assert(actualSha === asset.sha256, `VISUAL_ASSET_SHA_MISMATCH: ${pageId}`);
    assert(visualFetched.bytes.byteLength === asset.bytes, `VISUAL_ASSET_SIZE_MISMATCH: ${pageId}`);
    const expectedMime = asset.media_type.toLowerCase();
    assert(visualFetched.contentType.toLowerCase().includes(expectedMime), `VISUAL_ASSET_MIME_MISMATCH: ${pageId}`);

    return {
      page_id: pageId,
      source_sha: sourceSha,
      feature,
      page_case: pageCase,
      envelope,
      profile,
      envelope_sha256: envelopeResult.sha256,
      profile_sha256: profileResult.sha256,
      binding,
      binding_sha256: bindingResult.sha256,
      visual_asset: asset,
      visual_bytes: visualFetched.bytes,
      visual_content_type: visualFetched.contentType,
      visual_path: visualPath,
      visual_sha256: actualSha,
      page_acceptance: "NOT_VERIFIED"
    };
  }

  function createReferenceAdapter({ root, pageId, sourceSha, fetchImpl = window.fetch.bind(window), repoHref, feature = getRegisteredPage(pageId) }) {
    assert(root instanceof HTMLElement, "WORKSPACE_ROOT_INVALID");
    assert(typeof repoHref === "function", "REPOSITORY_URL_RESOLVER_MISSING");
    const abortController = new AbortController();
    let authority = null;
    let objectUrl = null;
    let selectedNodeId = "visual-authority";
    let currentViewport = null;
    let currentFontScale = null;
    let currentState = null;
    let disposed = false;
    const signal = abortController.signal;

    function requireLive() {
      if (disposed) throw new Error("WORKSPACE_DISPOSED");
      if (!authority) throw new Error("AUTHORITY_NOT_LOADED");
    }

    function setInspector(id) {
      selectedNodeId = id;
      const inspector = root.querySelector("[data-workspace-inspector]");
      const treeNode = root.querySelector(`[data-node-id="${CSS.escape(id)}"]`);
      if (inspector) {
        inspector.dataset.selectedNodeId = id;
        const rect = root.querySelector(`[data-workspace-rendered-node="${CSS.escape(id)}"]`)?.getBoundingClientRect();
        const asset = authority?.visual_asset;
        inspector.innerHTML = `<dl class="page-ws-inspector"><dt>页面</dt><dd>${escapeHtml(pageId)} · ${escapeHtml(authority?.feature?.display_name || pageId)}</dd><dt>选中节点</dt><dd>${escapeHtml(id)} · 冻结视觉权威图片</dd><dt>页面 Profile</dt><dd>${escapeHtml(authority?.profile?.profile_id)} · ${escapeHtml(authority?.profile?.status)}</dd><dt>资源身份</dt><dd>${escapeHtml(asset?.asset_id)} · ${escapeHtml(asset?.asset_role)}</dd><dt>来源路径</dt><dd><code>${escapeHtml(authority?.visual_path)}</code></dd><dt>SHA-256</dt><dd><code>${escapeHtml(authority?.visual_sha256)}</code></dd><dt>生命周期 / designation</dt><dd>${escapeHtml(asset?.lifecycle_status)} · ${escapeHtml(asset?.asset_designation)}</dd><dt>发布权限</dt><dd>${escapeHtml(asset?.publication_permission?.state)} · ${escapeHtml(asset?.publication_permission?.policy_marker)}</dd><dt>图片原始尺寸</dt><dd>${asset?.pixel_dimensions?.width} × ${asset?.pixel_dimensions?.height} px · ${escapeHtml(asset?.coordinate_space || "未登记")}</dd><dt>DOM 实测边界</dt><dd data-inspector-bounds>${rect ? `${rect.x.toFixed(1)}, ${rect.y.toFixed(1)} · ${rect.width.toFixed(1)} × ${rect.height.toFixed(1)} CSS px` : "资源加载后测量"}</dd><dt>交互 / 状态</dt><dd>仅显示只读冻结图片。页面专属交互状态未从 Profile 机器解析，NOT_RUN。</dd><dt>页面验收</dt><dd>NOT_VERIFIED · 本预览不代表正式页面或 Native Runtime 验收。</dd></dl>`;
      }
      root.querySelectorAll("[data-workspace-tree-node]").forEach(button => {
        const selected = button.dataset.nodeId === id;
        button.classList.toggle("selected", selected);
        button.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      if (treeNode) treeNode.focus({ preventScroll: true });
    }

    function renderPreview(viewport, fontScale, state) {
      requireLive();
      if (viewport != null) {
        assert(Number.isFinite(viewport.width) && viewport.width > 0 && Number.isFinite(viewport.height) && viewport.height > 0,
          "VIEWPORT_INPUT_INVALID");
        currentViewport = { width: viewport.width, height: viewport.height, unit: viewport.unit || "dp" };
      }
      if (fontScale != null) {
        assert(Number.isFinite(fontScale) && fontScale > 0, "FONT_SCALE_INVALID");
        currentFontScale = fontScale;
      }
      currentState = state ?? currentState;
      const artboard = root.querySelector("[data-workspace-artboard]");
      const image = root.querySelector("[data-workspace-rendered-node='visual-authority']");
      if (artboard && currentViewport) {
        artboard.style.width = `${currentViewport.width}px`;
        artboard.style.height = `${currentViewport.height}px`;
        artboard.dataset.viewport = `${currentViewport.width}x${currentViewport.height}${currentViewport.unit}`;
      } else if (artboard && authority.visual_asset.pixel_dimensions) {
        artboard.style.width = "min(100%, 864px)";
        artboard.style.height = "auto";
      }
      if (image) image.style.fontSize = currentFontScale ? `${currentFontScale}em` : "";
      if (root.firstElementChild) {
        root.firstElementChild.dataset.viewState = currentState || "REFERENCE_IMAGE_ONLY";
        root.firstElementChild.dataset.fontScale = currentFontScale == null ? "UNBOUND" : String(currentFontScale);
      }
      return { viewport: currentViewport, font_scale: currentFontScale, state: currentState, rendering: "REFERENCE_IMAGE_ONLY" };
    }

    async function loadComponentTree() {
      requireLive();
      return {
        document_type: "page_resource_authority_tree",
        status: "REFERENCE_IMAGE_ONLY",
        page_id: pageId,
        root: { id: "visual-authority", label: "本页冻结视觉权威 · 只读原图", children: [] },
        contract_component_ids: (authority.profile?.conformance?.regions || []).flatMap(region => region?.component_ids || []),
        component_mapping_status: "PAGE_COMPONENT_DOM_NOT_AVAILABLE_IN_STATIC_AUTHORITY_PREVIEW"
      };
    }

    function measureElements() {
      requireLive();
      const node = root.querySelector("[data-workspace-rendered-node='visual-authority']");
      if (!node) throw new Error("RENDERED_NODE_MISSING: visual-authority");
      const bounds = node.getBoundingClientRect();
      return [{ node_id: "visual-authority", bounds: { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height,
        right: bounds.right, bottom: bounds.bottom }, natural_width: node.naturalWidth || null, natural_height: node.naturalHeight || null }];
    }

    function selectLayer(nodeId) {
      requireLive();
      assert(nodeId === "visual-authority", `COMPONENT_NODE_NOT_MAPPED: ${nodeId}`);
      const node = root.querySelector(`[data-workspace-rendered-node="${CSS.escape(nodeId)}"]`);
      assert(node, `RENDERED_NODE_MISSING: ${nodeId}`);
      setInspector(nodeId);
      return node;
    }

    function getInspector(nodeId = selectedNodeId) {
      requireLive();
      assert(nodeId === selectedNodeId, `INSPECTOR_SELECTION_MISMATCH: ${nodeId}`);
      return { page_id: pageId, node_id: nodeId, bounds: measureElements()[0].bounds,
        resource: authority.visual_asset, source_path: authority.visual_path, source_sha256: authority.visual_sha256 };
    }

    function getResponsiveDiagnostics() {
      requireLive();
      return { page_id: pageId, profile_status: authority.profile.status,
        numeric_authority_status: authority.profile.numeric_authority?.status || "UNBOUND_DRAFT",
        viewport: currentViewport, font_scale: currentFontScale, state: currentState,
        layout_validation: "NOT_RUN", browser_acceptance: "NOT_VERIFIED", native_runtime: "NOT_VERIFIED" };
    }

    async function mount() {
      root.innerHTML = `<section class="page-workspace-root" data-page-workspace-root data-page-id="${escapeHtml(pageId)}" data-source-sha="${escapeHtml(sourceSha)}" data-view-state="LOADING" data-workspace-kind="reference-image-only"><header class="page-ws-heading"><div><small>STRUCTURED PAGE WORKSPACE · READ ONLY AUTHORITY VIEW</small><h2>${escapeHtml(feature.display_name || pageId)}</h2><p>加载 Experience Registry、W1 Envelope/Profile 与 W2 Resource Binding。页面专属状态和几何只在机器权威可解析时启用；当前视觉预览保持只读原图。</p></div><code>源码 SHA ${escapeHtml(sourceSha)}</code></header><div class="page-ws-controls"><label>页面身份 <input type="text" value="${escapeHtml(pageId)}" readonly></label><label>Profile 状态 <output data-profile-status>加载中</output></label><label>字体缩放 <select data-control="font-scale" disabled><option>NOT_RUN · 原图不做字体重排</option></select></label><label>页面状态 <select data-control="page-state" disabled><option>NOT_RUN · 状态 ID 未机器解析</option></select></label><div data-authority-controls></div></div><div class="page-ws-grid"><nav class="page-ws-tree" aria-label="页面组件树"><h3>页面图层</h3><button type="button" data-workspace-tree-node data-node-id="visual-authority" aria-pressed="true" disabled>冻结视觉权威 · 只读原图</button><p data-tree-scope>页面专属组件列表来自 Profile；静态图片中的子组件没有独立 DOM 映射。</p></nav><div class="page-ws-canvas-area"><div class="page-ws-canvas-scroll"><div class="page-ws-artboard" data-workspace-artboard><img data-workspace-rendered-node="visual-authority" alt="${escapeHtml(feature.display_name || pageId)} · W2 verified frozen visual authority" decoding="async"></div></div><p data-preview-disclosure>REFERENCE_IMAGE_ONLY · 不重建或推断表单、按钮、文本状态或页面几何。</p></div><aside class="page-ws-inspector-panel" aria-label="页面资源属性检查器"><h3>资源与检查器</h3><div data-workspace-inspector data-selected-node-id="visual-authority" aria-live="polite"></div></aside></div><div data-workspace-error role="alert" hidden></div></section>`;
      root.firstElementChild.dataset.pageId = pageId;
      root.firstElementChild.dataset.sourceSha = sourceSha;
      const treeButton = root.querySelector("[data-workspace-tree-node]");
      treeButton.addEventListener("click", () => selectLayer("visual-authority"), { signal });
      try {
        authority = await loadAuthority(sourceSha);
        const permission = authority.visual_asset.publication_permission;
        objectUrl = URL.createObjectURL(new Blob([authority.visual_bytes], { type: authority.visual_asset.media_type }));
        const image = root.querySelector("[data-workspace-rendered-node='visual-authority']");
        image.src = objectUrl;
        await image.decode();
        assert(image.naturalWidth === authority.visual_asset.pixel_dimensions.width && image.naturalHeight === authority.visual_asset.pixel_dimensions.height,
          `VISUAL_ASSET_DIMENSIONS_MISMATCH: ${pageId}`);
        root.querySelector("[data-profile-status]").textContent = `${authority.profile.profile_id} · ${authority.profile.status}`;
        root.querySelector("[data-tree-scope]").textContent = `Profile component IDs (not independently mapped to raster sub-elements): ${(authority.profile.conformance?.regions || []).flatMap(region => region.component_ids || []).join(" · ") || "未登记"}`;
        root.querySelector("[data-preview-disclosure]").textContent = `REFERENCE_IMAGE_ONLY · ${authority.visual_asset.asset_designation} · 生命周期 ${authority.visual_asset.lifecycle_status} · 发布权限 ${permission.state}。组件、交互和页面状态没有独立 DOM 权威，故不作推断。`;
        const viewportAuthorityId = authority.envelope?.viewport_inputs?.viewport_authority_ref_id;
        const viewportRef = (authority.envelope?.authority_refs || []).find(row => row.authority_ref_id === viewportAuthorityId);
        if (viewportRef?.path?.toLowerCase().endsWith(".json")) {
          const viewportDoc = await readJson(viewportRef.path, { fetchImpl, repoHref, signal });
          const profiles = viewportDoc.value?.responsive_profiles_to_verify || [];
          if (profiles.length) {
            const first = profiles[0];
            currentViewport = { width: first.width_dp, height: first.height_dp, unit: "dp" };
            currentFontScale = viewportDoc.value?.font_scale_profiles?.[0] ?? null;
            const controls = root.querySelector("[data-authority-controls]");
            controls.insertAdjacentHTML("beforeend", `<label>验证视口 <select data-control="authority-viewport">${profiles.map(row => `<option value="${row.width_dp}x${row.height_dp}">${row.width_dp} × ${row.height_dp} dp · authority profile</option>`).join("")}</select></label>`);
            root.querySelector("[data-control='authority-viewport']").addEventListener("change", event => {
              const selected = profiles.find(row => `${row.width_dp}x${row.height_dp}` === event.target.value);
              if (selected) renderPreview({ width: selected.width_dp, height: selected.height_dp, unit: "dp" }, currentFontScale, null);
            }, { signal });
            const fontScales = viewportDoc.value?.font_scale_profiles || [];
            if (fontScales.length) {
              const fontControl = root.querySelector("[data-control='font-scale']");
              fontControl.disabled = false;
              fontControl.innerHTML = fontScales.map(value => `<option value="${value}">${value}× · authority profile</option>`).join("");
              fontControl.value = String(currentFontScale ?? fontScales[0]);
              fontControl.addEventListener("change", event => renderPreview(currentViewport, Number(event.target.value), null), { signal });
            }
            root.querySelector("[data-control='authority-viewport']").value = `${first.width_dp}x${first.height_dp}`;
          }
        }
        renderPreview(currentViewport, currentFontScale, null);
        treeButton.disabled = false;
        setInspector("visual-authority");
        return this;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        root.firstElementChild.dataset.viewState = "ERROR";
        const errorNode = root.querySelector("[data-workspace-error]");
        errorNode.hidden = false;
        errorNode.textContent = `页面资源加载失败 · ${message}`;
        treeButton.disabled = true;
        throw error;
      }
    }

    async function loadAuthority(sha) {
      assert(sha === sourceSha, `SOURCE_SHA_MISMATCH: expected ${sourceSha}, got ${sha}`);
      const loaded = await resolvePageAuthority({ pageId, sourceSha: sha, feature, fetchImpl, repoHref, signal });
      return authority = loaded;
    }

    function dispose() {
      if (disposed) return;
      disposed = true;
      abortController.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      root.replaceChildren();
    }

    return { pageId, sourceSha, root, mount, loadAuthority, loadComponentTree, renderPreview, measureElements,
      selectLayer, getInspector, getResponsiveDiagnostics, dispose };
  }

  function normalizeStructuredTree(pageId, rootNode) {
    assert(rootNode && typeof rootNode === "object", "COMPONENT_TREE_ROOT_MISSING");
    const nodes = [];
    const ids = new Set();
    const domIds = new Set();
    function visit(source, parentId = null) {
      assert(source && typeof source === "object", "COMPONENT_TREE_NODE_INVALID");
      const nodeId = source.nodeId || source.id;
      assert(typeof nodeId === "string" && nodeId.length > 0, "COMPONENT_NODE_ID_MISSING");
      assert(!ids.has(nodeId), `COMPONENT_NODE_ID_DUPLICATE: ${nodeId}`);
      ids.add(nodeId);
      const page = source.pageId || pageId;
      assert(page === pageId, `COMPONENT_NODE_PAGE_ID_MISMATCH: ${nodeId}`);
      const renderedDomId = source.renderedDomId || nodeId;
      assert(typeof renderedDomId === "string" && renderedDomId.length > 0, `RENDERED_DOM_ID_MISSING: ${nodeId}`);
      assert(!domIds.has(renderedDomId), `RENDERED_DOM_ID_DUPLICATE: ${renderedDomId}`);
      domIds.add(renderedDomId);
      const declaredParent = source.parentId ?? parentId;
      assert(declaredParent === parentId, `COMPONENT_PARENT_MISMATCH: ${nodeId}`);
      const node = {
        nodeId,
        pageId: page,
        parentId: declaredParent,
        componentType: source.componentType || "UNSPECIFIED",
        renderedDomId,
        authorityRef: source.authorityRef || null,
        resourceRef: source.resourceRef || null,
        layoutRuleRef: source.layoutRuleRef || null,
        state: source.state || "DEFAULT",
        stateProfiles: source.stateProfiles || source.state_profiles || null,
        visible: source.visible !== false,
        selectable: source.selectable !== false,
        measurable: source.measurable !== false,
        label: source.label || source.name || nodeId,
        expectedBounds: source.expectedBounds || source.expected_bounds || null,
        children: []
      };
      nodes.push(node);
      node.children = (Array.isArray(source.children) ? source.children : []).map(child => visit(child, nodeId));
      return node;
    }
    const root = visit(rootNode, null);
    return { root, nodes };
  }

  function createStructuredDomAdapter({
    root, pageId, sourceSha, componentTree, renderer, viewportProfiles = [], fontScaleProfiles = [1],
    stateProfiles = ["DEFAULT"], fetchImpl = window.fetch.bind(window), repoHref, feature,
    authorityLoader, displayName = pageId, coordinateUnit = "css_px"
  }) {
    assert(root instanceof HTMLElement, "WORKSPACE_ROOT_INVALID");
    assert(SHA1.test(sourceSha || ""), "SOURCE_SHA_INVALID: a full source SHA is required");
    assert(typeof renderer === "function", "PAGE_RENDERER_MISSING");
    assert(Array.isArray(fontScaleProfiles) && fontScaleProfiles.length > 0 && fontScaleProfiles.every(value => Number.isFinite(value) && value > 0),
      "FONT_SCALE_PROFILE_INVALID");
    assert(Array.isArray(stateProfiles) && stateProfiles.length > 0, "STATE_PROFILE_MISSING");
    const registeredFeature = feature || (authorityLoader ? null : getRegisteredPage(pageId));
    if (authorityLoader) {
      assert(typeof pageId === "string" && pageId.startsWith("test_") && !pageIds.has(pageId),
        "CUSTOM_AUTHORITY_RESOLVER_TEST_FIXTURE_ONLY");
    }
    const resolveAuthority = authorityLoader || (sha => resolvePageAuthority({
      pageId, sourceSha: sha, feature: registeredFeature, fetchImpl, repoHref, signal
    }));
    assert(typeof resolveAuthority === "function", "PAGE_AUTHORITY_RESOLVER_MISSING");

    const abortController = new AbortController();
    const signal = abortController.signal;
    const listenerDisposers = [];
    const resourceCache = new Map();
    const sourceAuthorityCache = new Map();
    const objectUrls = new Set();
    let authority = null;
    let tree = null;
    let canvas = null;
    let selectedNodeId = null;
    let currentViewport = null;
    let currentFontScale = fontScaleProfiles[0];
    let currentState = typeof stateProfiles[0] === "string" ? stateProfiles[0] : stateProfiles[0]?.state_id;
    let disposed = false;

    function requireAuthority() {
      if (disposed) throw new Error("WORKSPACE_DISPOSED");
      if (!authority) throw new Error("AUTHORITY_NOT_LOADED");
    }

    function requireLive() {
      requireAuthority();
      if (!tree) throw new Error("COMPONENT_TREE_NOT_LOADED");
    }

    function listen(node, type, handler) {
      node.addEventListener(type, handler);
      listenerDisposers.push(() => node.removeEventListener(type, handler));
    }

    function treeMarkup(node) {
      const children = node.children.map(treeMarkup).join("");
      const treeButton = `<button type="button" data-workspace-tree-node data-node-id="${escapeHtml(node.nodeId)}" data-node-state="${escapeHtml(node.state)}" data-node-authority-status="${escapeHtml(node.authorityRef?.status || "UNVERIFIED")}" data-node-visible="${node.visible ? "true" : "false"}" data-node-measurable="${node.measurable ? "true" : "false"}" data-node-selectable="${node.selectable ? "true" : "false"}" aria-pressed="false"${node.selectable ? "" : " disabled"}>${escapeHtml(node.label)} <small>${escapeHtml(node.componentType)} · ${escapeHtml(node.state)}</small></button>`;
      const childrenToggle = children
        ? `<button type="button" data-workspace-tree-toggle aria-expanded="true" aria-label="折叠 ${escapeHtml(node.label)} 的子图层">−</button>` : "";
      return `<li data-workspace-tree-item="${escapeHtml(node.nodeId)}"><div class="page-ws-tree-row">${treeButton}${childrenToggle}</div>${children ? `<ul data-workspace-tree-children>${children}</ul>` : ""}</li>`;
    }

    function findNode(nodeId) {
      return tree?.nodes.find(node => node.nodeId === nodeId) || null;
    }

    function renderedElement(node) {
      const matches = [...root.querySelectorAll("[data-workspace-rendered-node]")]
        .filter(element => element.dataset.workspaceRenderedNode === node.nodeId);
      assert(matches.length === 1, matches.length === 0
        ? `COMPONENT_DOM_MAPPING_MISSING: ${node.nodeId}`
        : `COMPONENT_DOM_MAPPING_AMBIGUOUS: ${node.nodeId}`);
      assert(matches[0].id === node.renderedDomId,
        `COMPONENT_DOM_ID_MISMATCH: ${node.nodeId} expected ${node.renderedDomId}, got ${matches[0].id || "(empty)"}`);
      return matches[0];
    }

    function measureElements() {
      requireLive();
      const boardRect = root.querySelector("[data-workspace-artboard]").getBoundingClientRect();
      return tree.nodes.filter(node => node.visible && node.measurable).map(node => {
        const element = renderedElement(node);
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        const bounds = {
          x: rect.left - boardRect.left,
          y: rect.top - boardRect.top,
          width: rect.width,
          height: rect.height,
          right: rect.right - boardRect.left,
          bottom: rect.bottom - boardRect.top,
          unit: "css_px"
        };
        const expected = node.expectedBounds;
        return {
          node_id: node.nodeId,
          page_id: node.pageId,
          rendered_dom_id: element.id,
          component_type: node.componentType,
          state: node.state,
          visible: node.visible,
          bounds,
          expected_bounds: expected,
          deviation: expected && ["x", "y", "width", "height"].every(key => Number.isFinite(expected[key]))
            ? Object.fromEntries(["x", "y", "width", "height"].map(key => [key, bounds[key] - expected[key]])) : null,
          typography: { font_size: style.fontSize, line_height: style.lineHeight, font_family: style.fontFamily },
          layer: { z_index: style.zIndex, transform: style.transform },
          authority_ref: node.authorityRef,
          resource_ref: node.resourceRef,
          resource_status: element.dataset.resourceStatus || node.resourceRef?.status || "NOT_APPLICABLE",
          resource_lifecycle: element.dataset.assetLifecycle || node.resourceRef?.lifecycle_status || "NOT_APPLICABLE",
          resource_designation: element.dataset.assetDesignation || node.resourceRef?.asset_designation || "NOT_APPLICABLE",
          publication_permission: element.dataset.publicationPermission || node.resourceRef?.publication_permission?.state || "NOT_APPLICABLE",
          resource_sha256: element.dataset.sha256 || node.resourceRef?.sha256 || null,
          layout_rule_ref: node.layoutRuleRef
        };
      });
    }

    function inspectorData(nodeId = selectedNodeId) {
      requireLive();
      const node = findNode(nodeId);
      assert(node, `COMPONENT_NODE_NOT_FOUND: ${nodeId}`);
      const measured = measureElements().find(row => row.node_id === nodeId);
      assert(measured || !node.measurable, `COMPONENT_NOT_MEASURABLE: ${nodeId}`);
      const element = renderedElement(node);
      const style = getComputedStyle(element);
      return {
        page_id: pageId,
        node_id: node.nodeId,
        parent_id: node.parentId,
        component_type: node.componentType,
        bounds: measured?.bounds || null,
        expected_bounds: measured?.expected_bounds || node.expectedBounds || null,
        deviation: measured?.deviation || null,
        typography: measured?.typography || { font_size: style.fontSize, line_height: style.lineHeight, font_family: style.fontFamily },
        layer: measured?.layer || { z_index: style.zIndex, transform: style.transform },
        authority_ref: node.authorityRef,
        resource_ref: node.resourceRef,
        resource_status: measured?.resource_status || node.resourceRef?.status || "NOT_APPLICABLE",
        resource_lifecycle: measured?.resource_lifecycle || node.resourceRef?.lifecycle_status || "NOT_APPLICABLE",
        resource_designation: measured?.resource_designation || node.resourceRef?.asset_designation || "NOT_APPLICABLE",
        publication_permission: measured?.publication_permission || node.resourceRef?.publication_permission?.state || "NOT_APPLICABLE",
        resource_sha256: measured?.resource_sha256 || node.resourceRef?.sha256 || null,
        layout_rule_ref: node.layoutRuleRef,
        source_sha: sourceSha,
        profile_status: authority.profile?.status || "UNVERIFIED"
      };
    }

    function showInspector(nodeId) {
      selectedNodeId = nodeId;
      const inspector = root.querySelector("[data-workspace-inspector]");
      if (!inspector) return;
      const data = inspectorData(nodeId);
      inspector.dataset.selectedNodeId = nodeId;
      inspector.replaceChildren();
      for (const [label, value] of Object.entries({
        "组件 ID": data.node_id,
        "组件类型": data.component_type,
        "父组件": data.parent_id || "ROOT",
        "实际 DOM 边界": data.bounds ? `${data.bounds.x.toFixed(1)}, ${data.bounds.y.toFixed(1)} · ${data.bounds.width.toFixed(1)} × ${data.bounds.height.toFixed(1)} CSS px` : "NOT_MEASURABLE · 当前没有冻结静态几何权威",
        "合同期望边界": data.expected_bounds ? JSON.stringify(data.expected_bounds) : "未登记",
        "偏差": data.deviation ? JSON.stringify(data.deviation) : "不可计算",
        "字体 / 行高": `${data.typography.font_size} / ${data.typography.line_height}`,
        "层级 / Transform": `${data.layer.z_index} / ${data.layer.transform}`,
        "资源状态": data.resource_status,
        "资源生命周期 / designation": `${data.resource_lifecycle} / ${data.resource_designation}`,
        "发布权限": data.publication_permission,
        "资源来源": data.resource_ref?.source_path || data.resource_ref?.path || "无",
        "资源 SHA-256": data.resource_ref?.sha256 || "无",
        "合同来源": data.authority_ref?.path || data.authority_ref?.authority_id || "未登记",
        "响应规则": data.layout_rule_ref?.path || data.layout_rule_ref?.rule_id || "未登记",
        "源码 SHA": data.source_sha,
        "页面规范状态": data.profile_status
      })) {
        const row = document.createElement("div");
        row.dataset.inspectorField = label;
        const name = document.createElement("dt");
        name.textContent = label;
        const content = document.createElement("dd");
        content.textContent = value;
        row.append(name, content);
        inspector.append(row);
      }
      root.querySelectorAll("[data-workspace-tree-node]").forEach(button => {
        const selected = button.dataset.nodeId === nodeId;
        button.classList.toggle("selected", selected);
        button.setAttribute("aria-pressed", selected ? "true" : "false");
      });
      root.querySelectorAll("[data-workspace-rendered-node]").forEach(element => {
        element.toggleAttribute("data-workspace-selected", element.dataset.workspaceRenderedNode === nodeId);
      });
    }

    async function loadAuthority(sha = sourceSha) {
      assert(sha === sourceSha, `SOURCE_SHA_MISMATCH: expected ${sourceSha}, got ${sha}`);
      authority = await resolveAuthority(sha);
      assert(authority?.page_id === pageId, `AUTHORITY_PAGE_ID_MISMATCH: ${pageId}`);
      if (authority.source_sha && authority.source_sha !== sourceSha) {
        throw new Error(`AUTHORITY_SOURCE_SHA_MISMATCH: expected ${sourceSha}, got ${authority.source_sha}`);
      }
      if (root.firstElementChild) root.firstElementChild.dataset.authorityMode = authorityLoader ? "TEST_FIXTURE_ONLY" : "W1_W2_RESOLVED";
      return authority;
    }

    async function loadSourceAuthority(authorityId) {
      requireAuthority();
      const descriptor = authority.binding?.source_authorities?.find(row => row?.authority_id === authorityId);
      assert(descriptor, `SOURCE_AUTHORITY_UNKNOWN: ${authorityId}`);
      if (!sourceAuthorityCache.has(authorityId)) sourceAuthorityCache.set(authorityId, (async () => {
        const path = safeAuthorityPath(descriptor.path);
        const fetched = await responseBytes(path, { fetchImpl, repoHref, signal });
        const actualSha = await sha256(fetched.bytes);
        assert(actualSha === descriptor.sha256, `SOURCE_AUTHORITY_SHA_MISMATCH: ${path} expected=${descriptor.sha256} actual=${actualSha}`);
        const authorityText = new TextDecoder().decode(fetched.bytes);
        let value;
        if (descriptor.purpose === "workflow" && /\.ya?ml$/i.test(path)) {
          // W2 pins the Pages workflow as a publication-policy source. Preserve its exact
          // bytes for marker verification; YAML is not a JSON authority document.
          value = authorityText;
        } else {
          try { value = JSON.parse(authorityText); }
          catch (error) { throw new Error(`SOURCE_AUTHORITY_JSON_INVALID: ${path}: ${error.message}`); }
        }
        return { descriptor, value, bytes: fetched.bytes, sha256: actualSha };
      })());
      return sourceAuthorityCache.get(authorityId);
    }

    async function loadAuthorityDocument(path, expectedSha256 = null) {
      requireAuthority();
      const safePath = safeAuthorityPath(path);
      const pinned = authority.binding?.source_authorities?.find(row => row?.path === safePath);
      const expected = expectedSha256 || pinned?.sha256;
      assert(expected && SHA256.test(expected), `SOURCE_AUTHORITY_NOT_PINNED: ${safePath}`);
      if (pinned) {
        assert(pinned.sha256 === expected, `AUTHORITY_DOCUMENT_PIN_CONFLICT: ${safePath}`);
        return (await loadSourceAuthority(pinned.authority_id)).value;
      }
      const fetched = await responseBytes(safePath, { fetchImpl, repoHref, signal });
      const actualSha = await sha256(fetched.bytes);
      assert(actualSha === expected, `AUTHORITY_DOCUMENT_SHA_MISMATCH: ${safePath} expected=${expected} actual=${actualSha}`);
      try { return JSON.parse(new TextDecoder().decode(fetched.bytes)); }
      catch (error) { throw new Error(`AUTHORITY_DOCUMENT_JSON_INVALID: ${safePath}: ${error.message}`); }
    }

    async function loadBoundResource(assetId) {
      requireAuthority();
      assert(typeof assetId === "string" && assetId.length > 0, "RESOURCE_ASSET_ID_MISSING");
      if (resourceCache.has(assetId)) return resourceCache.get(assetId);
      const pending = (async () => {
        const matches = (authority.binding?.asset_bindings || []).filter(row => row?.asset_id === assetId);
        assert(matches.length === 1, matches.length ? `RESOURCE_BINDING_AMBIGUOUS: ${assetId}` : `RESOURCE_BINDING_MISSING: ${assetId}`);
        const asset = matches[0];
        assert(asset.page_id === pageId, `RESOURCE_PAGE_ID_MISMATCH: ${assetId}`);
        if (!asset.source_path) {
          if (asset.requiredness === "OPTIONAL" && asset.degradation_policy) return { asset, status: "MISSING", objectUrl: null, reason: asset.missing_reason || "OPTIONAL_RESOURCE_MISSING", degradation: asset.degradation_policy };
          throw new Error(`REQUIRED_RESOURCE_MISSING: ${assetId}: ${asset.missing_reason || "source path missing"}`);
        }
        const permission = asset.publication_permission || {};
        const policy = authority.binding?.publication_policy || {};
        assert(permission.state === "PUBLIC_BY_EXISTING_PAGES_RULE", `RESOURCE_PUBLICATION_NOT_AUTHORIZED: ${assetId} state=${permission.state || "NOT_CONFIRMED"}`);
        assert(permission.authority_id === policy.authority_id, `RESOURCE_PUBLICATION_AUTHORITY_MISMATCH: ${assetId}`);
        const policyAuthority = await loadSourceAuthority(policy.authority_id);
        const marker = permission.policy_marker || policy.public_scope_marker;
        assert(marker && marker === policy.public_scope_marker, `RESOURCE_PUBLICATION_MARKER_MISMATCH: ${assetId}`);
        const policyText = new TextDecoder().decode(policyAuthority.bytes);
        assert(policyText.includes(marker), `RESOURCE_PUBLICATION_POLICY_NOT_EXPLICIT: ${assetId}`);
        for (const source of asset.source_authorities || []) {
          const sourceAuthority = await loadSourceAuthority(source.authority_id);
          const record = pointerValue(sourceAuthority.value, source.json_pointer || "");
          assert(record !== undefined, `RESOURCE_SOURCE_RECORD_MISSING: ${assetId} authority=${source.authority_id}`);
          for (const [bindingField, sourcePointer] of Object.entries(source.field_map || {})) {
            const expectedValue = dottedValue(asset, bindingField);
            const actualValue = pointerValue(record, sourcePointer);
            assert(expectedValue !== undefined && actualValue === expectedValue,
              `RESOURCE_SOURCE_FIELD_MISMATCH: ${assetId} field=${bindingField} authority=${source.authority_id}`);
          }
        }
        const path = safeAuthorityPath(asset.source_path);
        const fetched = await responseBytes(path, { fetchImpl, repoHref, signal });
        assert(Number.isInteger(asset.bytes) && fetched.bytes.byteLength === asset.bytes,
          `RESOURCE_BYTE_COUNT_MISMATCH: ${assetId} expected=${asset.bytes} actual=${fetched.bytes.byteLength}`);
        const actualSha = await sha256(fetched.bytes);
        assert(SHA256.test(asset.sha256 || "") && actualSha === asset.sha256,
          `RESOURCE_SHA256_MISMATCH: ${assetId} expected=${asset.sha256 || "UNPINNED"} actual=${actualSha}`);
        const actualMime = sniffMediaType(fetched.bytes, path);
        assert(actualMime && actualMime === asset.media_type, `RESOURCE_MEDIA_TYPE_MISMATCH: ${assetId} expected=${asset.media_type} actual=${actualMime || fetched.contentType || "UNKNOWN"}`);
        assert((fetched.contentType || "").toLowerCase().includes(asset.media_type.toLowerCase()),
          `RESOURCE_HTTP_MIME_MISMATCH: ${assetId} expected=${asset.media_type} actual=${fetched.contentType || "MISSING"}`);
        if (asset.lifecycle_status === "MISSING" || asset.lifecycle_status === "NOT_VERIFIED") {
          throw new Error(`RESOURCE_LIFECYCLE_UNVERIFIED: ${assetId} status=${asset.lifecycle_status}`);
        }
        const blobUrl = URL.createObjectURL(new Blob([fetched.bytes], { type: asset.media_type }));
        objectUrls.add(blobUrl);
        if (asset.media_type.startsWith("image/")) {
          const image = new Image();
          image.src = blobUrl;
          try { await image.decode(); }
          catch (error) { throw new Error(`RESOURCE_IMAGE_DECODE_FAILED: ${assetId}: ${error.message}`); }
          if (asset.pixel_dimensions) assert(image.naturalWidth === asset.pixel_dimensions.width && image.naturalHeight === asset.pixel_dimensions.height,
            `RESOURCE_DIMENSIONS_MISMATCH: ${assetId} expected=${asset.pixel_dimensions.width}x${asset.pixel_dimensions.height} actual=${image.naturalWidth}x${image.naturalHeight}`);
        }
        const status = asset.asset_designation === "ILLUSTRATIVE_ONLY" ? "ILLUSTRATIVE_ONLY" : asset.lifecycle_status;
        const treeNode = tree?.nodes.find(node => node.resourceRef?.asset_id === assetId);
        if (treeNode) treeNode.resourceRef = { ...asset, status, verification: { bytes: fetched.bytes.byteLength, sha256: actualSha, media_type: actualMime,
          pixel_dimensions: asset.pixel_dimensions || null, source_authorities: (asset.source_authorities || []).map(source => source.authority_id),
          publication_permission: permission.state } };
        return { asset, status, objectUrl: blobUrl,
          verified: { bytes: fetched.bytes.byteLength, sha256: actualSha, media_type: actualMime, pixel_dimensions: asset.pixel_dimensions || null,
            source_authorities: (asset.source_authorities || []).map(source => source.authority_id), publication_permission: permission.state } };
      })();
      resourceCache.set(assetId, pending);
      try { return await pending; }
      catch (error) { resourceCache.delete(assetId); throw error; }
    }

    async function loadComponentTree() {
      requireAuthority();
      if (!tree) tree = normalizeStructuredTree(pageId, typeof componentTree === "function" ? componentTree(authority) : componentTree);
      return { document_type: "page_component_tree", status: "STRUCTURED_DOM", page_id: pageId, root: tree.root, nodes: tree.nodes };
    }

    async function renderPreview(viewport, fontScale, state) {
      requireLive();
      if (viewport != null) {
        assert(Number.isFinite(viewport.width) && viewport.width > 0 && Number.isFinite(viewport.height) && viewport.height > 0,
          "VIEWPORT_INPUT_INVALID");
        currentViewport = { width: viewport.width, height: viewport.height, unit: viewport.unit || coordinateUnit };
      }
      if (fontScale != null) {
        assert(Number.isFinite(fontScale) && fontScale > 0, "FONT_SCALE_INVALID");
        currentFontScale = fontScale;
      }
      if (state != null) currentState = typeof state === "string" ? state : state.state_id;
      const supportedStates = stateProfiles.map(item => typeof item === "string" ? item : item?.state_id).filter(Boolean);
      assert(supportedStates.includes(currentState), `VIEW_STATE_UNSUPPORTED: ${currentState}`);
      tree.nodes.forEach(node => {
        if (node.stateProfiles && typeof node.stateProfiles === "object" && node.stateProfiles[currentState]) {
          node.state = node.stateProfiles[currentState];
          const treeButton = root.querySelector(`[data-workspace-tree-node][data-node-id="${CSS.escape(node.nodeId)}"]`);
          if (treeButton) treeButton.dataset.nodeState = node.state;
        }
      });
      const artboard = root.querySelector("[data-workspace-artboard]");
      artboard.style.width = `${currentViewport.width}px`;
      artboard.style.minHeight = `${currentViewport.height}px`;
      artboard.dataset.viewport = `${currentViewport.width}x${currentViewport.height}`;
      artboard.dataset.inputViewportUnit = currentViewport.unit;
      root.style.setProperty("--page-workspace-font-scale", String(currentFontScale));
      root.firstElementChild.dataset.viewState = currentState;
      root.firstElementChild.dataset.fontScale = String(currentFontScale);
      root.firstElementChild.dataset.logicalViewportUnit = currentViewport.unit;
      const controls = root.querySelector("[data-workspace-viewport-summary]");
      if (controls) controls.textContent = `${currentViewport.width} × ${currentViewport.height} ${currentViewport.unit} 输入视口 · DOM 测量为 CSS px`;
      root.querySelector("[data-workspace-width]").value = String(currentViewport.width);
      root.querySelector("[data-workspace-height]").value = String(currentViewport.height);
      root.querySelector("[data-workspace-font-scale]").value = String(currentFontScale);
      root.querySelector("[data-workspace-state]").value = currentState;
      await renderer({ root, canvas, viewport: currentViewport, fontScale: currentFontScale, state: currentState, authority,
        componentTree: tree.root, loadBoundResource, loadAuthorityDocument });
      root.firstElementChild.dataset.workspaceKind = "structured-dom-candidate";
      measureElements();
      if (selectedNodeId) showInspector(selectedNodeId);
      return { viewport: currentViewport, font_scale: currentFontScale, state: currentState, rendering: "STRUCTURED_DOM" };
    }

    function selectLayer(nodeId) {
      requireLive();
      const node = findNode(nodeId);
      assert(node && node.selectable, `COMPONENT_NODE_NOT_SELECTABLE: ${nodeId}`);
      renderedElement(node);
      showInspector(nodeId);
      return renderedElement(node);
    }

    function getInspector(nodeId = selectedNodeId) {
      requireLive();
      assert(nodeId && nodeId === selectedNodeId, `INSPECTOR_SELECTION_MISMATCH: ${nodeId}`);
      return inspectorData(nodeId);
    }

    function getResourceDiagnostics() {
      requireLive();
      return tree.nodes.filter(node => node.resourceRef).map(node => {
        let status = node.resourceRef.status || "UNVERIFIED";
        try { status = renderedElement(node).dataset.resourceStatus || status; } catch { /* missing node is reported by geometry validation */ }
        return { node_id: node.nodeId, resource_ref: node.resourceRef, status };
      });
    }

    function getResponsiveDiagnostics() {
      requireLive();
      return {
        page_id: pageId,
        profile_status: authority.profile?.status || "UNVERIFIED",
        coordinate_unit: currentViewport?.unit || coordinateUnit,
        dom_measurement_unit: "css_px",
        viewport: currentViewport,
        font_scale: currentFontScale,
        state: currentState,
        layout_validation: "MEASUREMENT_ONLY_NOT_VISUAL_PASS",
        browser_acceptance: "NOT_VERIFIED",
        native_runtime: "NOT_VERIFIED",
        resources: getResourceDiagnostics()
      };
    }

    async function mount() {
      root.innerHTML = `<section class="page-workspace-root" data-page-workspace-root data-page-id="${escapeHtml(pageId)}" data-source-sha="${escapeHtml(sourceSha)}" data-view-state="LOADING" data-workspace-kind="structured-dom-candidate"><header class="page-ws-heading"><div><small>STRUCTURED PAGE WORKSPACE · CANDIDATE</small><h2>${escapeHtml(displayName)}</h2><p>Page-owned renderer with shared tree, viewport, measurement, selection, and Inspector shell.</p></div><code>Source SHA ${escapeHtml(sourceSha)}</code></header><div class="page-ws-controls"><label>宽 <input data-workspace-width type="number" min="1" value="${Number(viewportProfiles[0]?.width || 390)}"></label><label>高 <input data-workspace-height type="number" min="1" value="${Number(viewportProfiles[0]?.height || 844)}"></label><label>字体缩放 <select data-workspace-font-scale>${fontScaleProfiles.map(value => `<option value="${value}">${value}×</option>`).join("")}</select></label><label>页面状态 <select data-workspace-state>${stateProfiles.map(item => { const id = typeof item === "string" ? item : item.state_id; const label = typeof item === "string" ? item : item.label || item.state_id; return `<option value="${escapeHtml(id)}">${escapeHtml(label)}</option>`; }).join("")}</select></label><button type="button" data-workspace-apply-viewport>应用视口</button><span data-workspace-viewport-summary></span></div><div class="page-ws-grid"><nav class="page-ws-tree" aria-label="页面组件树"><h3>组件树</h3><ul data-workspace-tree></ul></nav><div class="page-ws-canvas-area"><div class="page-ws-canvas-scroll"><div class="page-ws-artboard" data-workspace-artboard></div></div></div><aside class="page-ws-inspector-panel" aria-label="组件 Inspector"><h3>Inspector</h3><dl data-workspace-inspector data-selected-node-id="" aria-live="polite"></dl></aside></div><div data-workspace-error role="alert" hidden></div></section>`;
      const shell = root.firstElementChild;
      canvas = root.querySelector("[data-workspace-artboard]");
      try {
        await loadAuthority(sourceSha);
        await loadComponentTree();
        root.querySelector("[data-workspace-tree]").innerHTML = treeMarkup(tree.root);
        listen(root, "click", event => {
          const toggle = event.target.closest?.("[data-workspace-tree-toggle]");
          if (toggle) {
            const item = toggle.closest("[data-workspace-tree-item]");
            const childList = item?.querySelector(":scope > [data-workspace-tree-children]");
            if (!childList) return;
            const expanded = toggle.getAttribute("aria-expanded") === "true";
            toggle.setAttribute("aria-expanded", expanded ? "false" : "true");
            toggle.setAttribute("aria-label", `${expanded ? "展开" : "折叠"} ${item.dataset.workspaceTreeItem} 的子图层`);
            toggle.textContent = expanded ? "+" : "−";
            childList.hidden = expanded;
            return;
          }
          const treeButton = event.target.closest?.("[data-workspace-tree-node]");
          if (treeButton) {
            selectLayer(treeButton.dataset.nodeId);
            return;
          }
          const rendered = event.target.closest?.("[data-workspace-rendered-node]");
          if (rendered) selectLayer(rendered.dataset.workspaceRenderedNode);
        });
        listen(root.querySelector("[data-workspace-apply-viewport]"), "click", () => {
          renderPreview({ width: Number(root.querySelector("[data-workspace-width]").value),
            height: Number(root.querySelector("[data-workspace-height]").value), unit: coordinateUnit },
          Number(root.querySelector("[data-workspace-font-scale]").value), root.querySelector("[data-workspace-state]").value)
            .catch(setError);
        });
        listen(root.querySelector("[data-workspace-font-scale]"), "change", () => {
          renderPreview(currentViewport, Number(root.querySelector("[data-workspace-font-scale]").value), currentState).catch(setError);
        });
        listen(root.querySelector("[data-workspace-state]"), "change", () => {
          renderPreview(currentViewport, currentFontScale, root.querySelector("[data-workspace-state]").value).catch(setError);
        });
        const firstViewport = viewportProfiles[0] || { width: 390, height: 844, unit: coordinateUnit };
        currentViewport = { width: firstViewport.width, height: firstViewport.height, unit: firstViewport.unit || coordinateUnit };
        currentFontScale = fontScaleProfiles[0];
        currentState = typeof stateProfiles[0] === "string" ? stateProfiles[0] : stateProfiles[0]?.state_id;
        root.querySelector("[data-workspace-font-scale]").value = String(currentFontScale);
        root.querySelector("[data-workspace-state]").value = currentState;
        await renderPreview(currentViewport, currentFontScale, currentState);
        const firstSelectable = tree.nodes.find(node => node.selectable && node.visible && node.measurable);
        if (firstSelectable) selectLayer(firstSelectable.nodeId);
        return this;
      } catch (error) {
        setError(error);
        throw error;
      }
    }

    function setError(error) {
      const message = error instanceof Error ? error.message : String(error);
      const shell = root.querySelector("[data-page-workspace-root]") || root.firstElementChild;
      if (shell) shell.dataset.viewState = "ERROR";
      const errorNode = root.querySelector("[data-workspace-error]");
      if (errorNode) { errorNode.hidden = false; errorNode.textContent = `Workspace validation failed · ${message}`; }
    }

    function dispose() {
      if (disposed) return;
      disposed = true;
      abortController.abort();
      listenerDisposers.splice(0).forEach(disposeListener => disposeListener());
      objectUrls.forEach(url => URL.revokeObjectURL(url));
      objectUrls.clear();
      root.replaceChildren();
    }

    return { pageId, sourceSha, root, mount, loadAuthority, loadAuthorityDocument, loadBoundResource, loadComponentTree, renderPreview, measureElements,
      selectLayer, getInspector, getResourceDiagnostics, getResponsiveDiagnostics, dispose };
  }

  function setActiveAdapter(adapter) {
    if (activeAdapter && activeAdapter !== adapter) activeAdapter.dispose?.();
    activeAdapter = adapter || null;
    return activeAdapter;
  }

  function callActive(method, ...args) {
    if (!activeAdapter || typeof activeAdapter[method] !== "function") throw new Error(`WORKSPACE_ADAPTER_UNAVAILABLE: ${method}`);
    return activeAdapter[method](...args);
  }

  const api = {
    setRegistry(value) { registry = value; },
    async initialize(value, { fetchImpl = window.fetch.bind(window), repoHref } = {}) {
      registry = value;
      assert(typeof repoHref === "function", "REPOSITORY_URL_RESOLVER_MISSING");
      const matrix = await readJson(MATRIX_PATH, { fetchImpl, repoHref });
      const ids = (matrix.value?.cases || []).map(row => row?.page_id).filter(id => typeof id === "string");
      assert(ids.length > 0 && new Set(ids).size === ids.length, "W1_PAGE_MATRIX_INVALID");
      pageIds = new Set(ids);
      for (const id of pageIds) getRegisteredPage(id);
      return [...pageIds];
    },
    getRegisteredPage,
    supportsPage(pageId) { return pageIds.has(pageId) && Boolean(registry?.features?.some(row => row?.id === pageId)); },
    createReferenceAdapter,
    createStructuredDomAdapter,
    mount(root, options) {
      const adapter = typeof options?.renderer === "function"
        ? createStructuredDomAdapter({ root, ...options })
        : createReferenceAdapter({ root, ...options });
      setActiveAdapter(adapter);
      return adapter.mount();
    },
    setActiveAdapter,
    loadAuthority: sha => callActive("loadAuthority", sha),
    loadComponentTree: () => callActive("loadComponentTree"),
    renderPreview: (...args) => callActive("renderPreview", ...args),
    measureElements: (...args) => callActive("measureElements", ...args),
    selectLayer: (...args) => callActive("selectLayer", ...args),
    getInspector: (...args) => callActive("getInspector", ...args),
    getResponsiveDiagnostics: (...args) => callActive("getResponsiveDiagnostics", ...args),
    dispose() { if (activeAdapter) activeAdapter.dispose?.(); activeAdapter = null; },
    get active() { return activeAdapter; }
  };
  window.PageWorkspaceAdapters = api;
})();
