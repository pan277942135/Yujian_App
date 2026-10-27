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
        if (viewportRef?.path) {
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
    mount(root, options) {
      const adapter = createReferenceAdapter({ root, ...options });
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
