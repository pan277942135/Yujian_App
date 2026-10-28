(() => {
  "use strict";

  const ICON_REGISTRY_PATH = "design/system/icon_library_v0_1/frozen_assets_v1.json";
  const CANDIDATE_PAGE_ID = "auth_login_v2";
  const iconCache = new Map();
  const imageCache = new Map();
  const liveModel = { account: "", password: "", visible: false, focused: "", submitState: "IDLE" };

  function assert(condition, code, detail = "") {
    if (!condition) throw new Error(`${code}${detail ? `: ${detail}` : ""}`);
  }

  function safeRepoHref(repoHref, path) {
    assert(typeof path === "string" && path.length > 0 && !path.startsWith("/") && !path.includes("\\") &&
      !path.split("/").some(part => part === ".." || part === "."), "LOGIN_RESOURCE_PATH_UNSAFE", path);
    return repoHref(path);
  }

  async function sha256(bytes) {
    assert(globalThis.crypto?.subtle, "LOGIN_RESOURCE_SHA_UNAVAILABLE");
    const digest = await globalThis.crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  }

  function findBoundAsset(authority, role) {
    const matches = (authority?.binding?.asset_bindings || []).filter(row => row?.asset_role === role && row?.page_id === CANDIDATE_PAGE_ID);
    assert(matches.length === 1, "W2_ASSET_BINDING_REQUIRED", `${role} matches=${matches.length}`);
    return matches[0];
  }

  async function loadW2Image(authority, role, repoHref) {
    const asset = findBoundAsset(authority, role);
    assert(asset.requiredness === "REQUIRED", "W2_REQUIRED_RESOURCE_NOT_REQUIRED", role);
    assert(asset.lifecycle_status === "FROZEN", "W2_RESOURCE_NOT_FROZEN", `${role}=${asset.lifecycle_status}`);
    assert(asset.asset_role === "background_bg_content" && asset.asset_designation === "ORIGINAL",
      "W2_RENDER_ASSET_NOT_ORIGINAL", `${role}/${asset.asset_designation}`);
    assert(asset.publication_permission?.state === "PUBLIC_BY_EXISTING_PAGES_RULE", "W2_PUBLICATION_NOT_CONFIRMED", role);
    const cached = imageCache.get(asset.asset_id);
    if (cached) return { asset, url: cached.url };
    const response = await fetch(safeRepoHref(repoHref, asset.source_path), { cache: "no-store" });
    assert(response.ok, "W2_ASSET_HTTP_ERROR", `${asset.source_path} (${response.status})`);
    const mime = response.headers.get("content-type") || "";
    assert(mime.toLowerCase().includes(String(asset.media_type).toLowerCase()), "W2_ASSET_MIME_MISMATCH", asset.source_path);
    const bytes = await response.arrayBuffer();
    assert(bytes.byteLength === asset.bytes, "W2_ASSET_BYTE_COUNT_MISMATCH", asset.source_path);
    const actualSha = await sha256(bytes);
    assert(actualSha === asset.sha256, "W2_ASSET_SHA_MISMATCH", asset.source_path);
    const url = URL.createObjectURL(new Blob([bytes], { type: asset.media_type }));
    const image = new Image();
    image.src = url;
    await image.decode();
    assert(image.naturalWidth === asset.pixel_dimensions?.width && image.naturalHeight === asset.pixel_dimensions?.height,
      "W2_ASSET_DIMENSIONS_MISMATCH", asset.source_path);
    imageCache.set(asset.asset_id, { url, sha256: actualSha, bytes: bytes.byteLength, dimensions: [image.naturalWidth, image.naturalHeight] });
    return { asset, url };
  }

  async function loadSharedIcon(iconId, expectedSha, path, repoHref) {
    const key = `${iconId}:${expectedSha}`;
    if (iconCache.has(key)) return iconCache.get(key);
    const registryResponse = await fetch(safeRepoHref(repoHref, ICON_REGISTRY_PATH), { cache: "no-store" });
    assert(registryResponse.ok, "SHARED_ICON_REGISTRY_HTTP_ERROR", String(registryResponse.status));
    const registry = await registryResponse.json();
    const row = registry.assets?.find(item => item?.id === iconId && item?.path === path);
    assert(row && row.status === "FROZEN" && row.sha256 === expectedSha, "SHARED_ICON_REGISTRY_MISMATCH", iconId);
    const response = await fetch(safeRepoHref(repoHref, path), { cache: "no-store" });
    assert(response.ok, "SHARED_ICON_HTTP_ERROR", `${path} (${response.status})`);
    const bytes = await response.arrayBuffer();
    const actualSha = await sha256(bytes);
    assert(actualSha === expectedSha, "SHARED_ICON_SHA_MISMATCH", path);
    const text = new TextDecoder().decode(bytes);
    assert(/<svg[\s>]/i.test(text) && !/<script\b/i.test(text), "SHARED_ICON_SVG_INVALID", path);
    const result = { iconId, path, url: URL.createObjectURL(new Blob([bytes], { type: "image/svg+xml" })), sha256: actualSha,
      publication_permission: "NOT_CONFIRMED_IN_LOGIN_W2_BINDING" };
    iconCache.set(key, result);
    return result;
  }

  async function loadCandidateAssets(authority, repoHref) {
    const [background, person, lock, eye, eyeOff] = await Promise.all([
      loadW2Image(authority, "background_bg_content", repoHref),
      loadSharedIcon("PERSON", "e2dbc2eb3111622590836fd1cda051736dc75611d041f17c6ae1b46aff22d189", "design/system/icon_library_v0_1/assets/ic_person_v1.svg", repoHref),
      loadSharedIcon("LOCK", "4b0bac26e9c7b8c24f91effbd15e3a987b93cd4b146d17919e265ef33eed00cd", "design/system/icon_library_v0_1/assets/ic_lock_v1.svg", repoHref),
      loadSharedIcon("VISIBILITY", "5c05ab9421590ae2b02c46f68b3adf9261ff7d10758db9b9371d80eb310e3cbe", "design/system/icon_library_v0_1/assets/ic_visibility_v1.svg", repoHref),
      loadSharedIcon("VISIBILITY_OFF", "6e12ca6dcc7316e9ba323c0c64f1d81b1a2a72f3fb858a9f25337ecdb1c41dfe", "design/system/icon_library_v0_1/assets/ic_visibility_off_v1.svg", repoHref)
    ]);
    return { background, icons: { person, lock, eye, eyeOff } };
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  }

  function collectReferenceBounds(rootNode) {
    const rows = new Map();
    const visit = node => {
      if (node?.nodeId && node?.reference_bounds_px) rows.set(node.nodeId, node.reference_bounds_px);
      for (const child of node?.children || []) visit(child);
    };
    visit(rootNode);
    return rows;
  }

  function updateProjectedInspectorBounds(rootNode, viewport, referenceBoundsByNode) {
    const scaleX = viewport.width / 864;
    const scaleY = viewport.height / 1536;
    const visit = node => {
      const reference = referenceBoundsByNode.get(node.nodeId);
      if (reference) {
        node.expectedBounds = {
          x: reference.x * scaleX,
          y: reference.y * scaleY,
          width: reference.width * scaleX,
          height: reference.height * scaleY,
          candidate_projection: "CANDIDATE_ONLY"
        };
      }
      for (const child of node.children || []) visit(child);
    };
    visit(rootNode);
  }

  function iconNode(nodeId, icon, className, alt) {
    return `<img id="login-${nodeId}" data-workspace-rendered-node="${nodeId}" data-resource-status="${icon.publication_permission}" class="${className}" src="${icon.url}" alt="${alt}" draggable="false">`;
  }

  function renderMarkup({ state, assets, viewport }) {
    const passwordType = liveModel.visible || state === "PASSWORD_SHOWN" ? "text" : "password";
    const icon = liveModel.visible || state === "PASSWORD_SHOWN" ? assets.icons.eye : assets.icons.eyeOff;
    const disabled = state === "ACTION_DISABLED" || state === "LOADING" || liveModel.submitState === "LOADING";
    const showError = state === "INPUT_ERROR" || liveModel.submitState === "ERROR";
    const keyboard = state === "KEYBOARD_SIMULATION";
    const accountFocus = state === "ACCOUNT_FOCUS";
    const passwordFocus = state === "PASSWORD_FOCUS" || keyboard;
    const visibleState = state || "DEFAULT";
    return `<div id="login-LoginPageRoot" data-workspace-rendered-node="LoginPageRoot" data-state="${escapeHtml(visibleState)}" class="login-page" style="--login-vw:${viewport.width}px;--login-vh:${viewport.height}px;--login-font-scale:${viewport.fontScale};--login-page-height:${viewport.height}px">
      <section id="login-Background" data-workspace-rendered-node="Background" class="login-background" aria-hidden="true">
        <img id="login-MorningLakeBGContent" data-workspace-rendered-node="MorningLakeBGContent" data-resource-status="W2_RESOLVED" class="login-lake-image" src="${assets.background.url}" alt="" draggable="false">
        <div id="login-LakeToWhiteTransition" data-workspace-rendered-node="LakeToWhiteTransition" class="login-lake-transition"></div>
      </section>
      <div id="login-WhiteContentSurface" data-workspace-rendered-node="WhiteContentSurface" class="login-white-surface" aria-hidden="true"></div>
      <main id="login-MainContent" data-workspace-rendered-node="MainContent" class="login-main-content">
        <h1 id="login-WelcomeTitle" data-workspace-rendered-node="WelcomeTitle" class="login-welcome-title">欢迎回来</h1>
        <p id="login-WelcomeSubtitle" data-workspace-rendered-node="WelcomeSubtitle" class="login-welcome-subtitle">继续记录你的每一次渔获</p>
        <form id="login-LoginForm" data-workspace-rendered-node="LoginForm" class="login-form" novalidate autocomplete="off">
          <section id="login-AccountGroup" data-workspace-rendered-node="AccountGroup" class="login-account-group">
            <label id="login-AccountLabel" data-workspace-rendered-node="AccountLabel" class="login-field-label" for="login-AccountInput">账号</label>
            <div id="login-AccountField" data-workspace-rendered-node="AccountField" class="login-input-shell" data-focus="${accountFocus ? "true" : "false"}">
              ${iconNode("AccountLeadingIcon", assets.icons.person, "login-leading-icon", "")}
              <input id="login-AccountInput" data-workspace-rendered-node="AccountInput" class="login-text-input" type="text" inputmode="text" autocomplete="username" aria-label="账号" placeholder="请输入账号" value="${escapeHtml(liveModel.account)}">
            </div>
          </section>
          <section id="login-PasswordGroup" data-workspace-rendered-node="PasswordGroup" class="login-password-group">
            <label id="login-PasswordLabel" data-workspace-rendered-node="PasswordLabel" class="login-field-label" for="login-PasswordInput">密码</label>
            <div id="login-PasswordField" data-workspace-rendered-node="PasswordField" class="login-input-shell login-password-shell" data-focus="${passwordFocus ? "true" : "false"}">
              ${iconNode("PasswordLeadingIcon", assets.icons.lock, "login-leading-icon", "")}
              <input id="login-PasswordInput" data-workspace-rendered-node="PasswordInput" class="login-text-input" type="${passwordType}" autocomplete="current-password" aria-label="密码" placeholder="请输入密码" value="${escapeHtml(liveModel.password)}">
              <button id="login-VisibilityAction" data-workspace-rendered-node="VisibilityAction" class="login-visibility-action" type="button" aria-label="${liveModel.visible ? "隐藏密码" : "显示密码"}" aria-pressed="${liveModel.visible ? "true" : "false"}">
                ${iconNode("VisibilityIcon", icon, "login-visibility-icon", "")}
              </button>
            </div>
            <div class="login-forgot-row"><button id="login-ForgotPasswordAction" data-workspace-rendered-node="ForgotPasswordAction" class="login-text-action" type="button" aria-disabled="true" title="视觉候选；找回密码服务未接入">忘记密码？</button></div>
            ${showError ? `<p class="login-error-message" role="alert" data-login-error>候选错误状态 · 未连接认证服务</p>` : ""}
          </section>
          <button id="login-LoginPrimaryButton" data-workspace-rendered-node="LoginPrimaryButton" class="login-submit" type="submit" ${disabled ? "disabled" : ""}>${state === "LOADING" || liveModel.submitState === "LOADING" ? "登录中…" : "登录"}</button>
          <div id="login-RegisterActionGroup" data-workspace-rendered-node="RegisterActionGroup" class="login-register-row"><span>还没有账号？</span><button id="login-RegisterAction" data-workspace-rendered-node="RegisterAction" class="login-register-action" type="button" title="仅视觉预览；不导航">创建账号</button></div>
        </form>
      </main>
      <footer id="login-Footer" data-workspace-rendered-node="Footer" data-resource-status="CANDIDATE_UNAPPROVED_ASSET" class="login-footer" aria-label="页脚水草装饰 · 未批准临时表达">
        <div id="login-WatersideGrassDecoration" data-workspace-rendered-node="WatersideGrassDecoration" data-resource-status="CANDIDATE_UNAPPROVED_ASSET" class="login-grass-sketch" aria-hidden="true"></div>
        <div class="login-waterline" aria-hidden="true"></div>
      </footer>
      <div id="login-keyboard-overlay" class="login-keyboard-overlay" ${keyboard ? "" : "hidden"} aria-label="浏览器模拟键盘 · 非原生键盘" aria-hidden="${keyboard ? "false" : "true"}">
        <div class="login-keyboard-toolbar"><span>浏览器键盘模拟</span><span>⌄</span></div>
        <div class="login-keyboard-keys">${"qwertyuiopasdfghjklzxcvbnm".split("").slice(0, 20).map(letter => `<span>${letter}</span>`).join("")}</div>
      </div>
      <output class="login-state-chip" aria-live="polite">${escapeHtml(visibleState)} · candidate</output>
    </div>`;
  }

  function bindInteractions({ root, adapter }) {
    const account = root.querySelector("#login-AccountInput");
    const password = root.querySelector("#login-PasswordInput");
    account.addEventListener("input", () => { liveModel.account = account.value; liveModel.submitState = "IDLE"; });
    password.addEventListener("input", () => { liveModel.password = password.value; liveModel.submitState = "IDLE"; });
    account.addEventListener("focus", () => { liveModel.focused = "account"; });
    password.addEventListener("focus", () => { liveModel.focused = "password"; });
    root.querySelector("#login-VisibilityAction").addEventListener("click", () => {
      liveModel.visible = !liveModel.visible;
      const input = root.querySelector("#login-PasswordInput");
      input.type = liveModel.visible ? "text" : "password";
      const button = root.querySelector("#login-VisibilityAction");
      button.setAttribute("aria-pressed", String(liveModel.visible));
      button.setAttribute("aria-label", liveModel.visible ? "隐藏密码" : "显示密码");
      const icon = root.querySelector("#login-VisibilityIcon");
      const resource = liveModel.visible ? root.dataset.eyeSrc : root.dataset.eyeOffSrc;
      if (resource) icon.src = resource;
    });
    root.querySelector("#login-LoginForm").addEventListener("submit", event => {
      event.preventDefault();
      if (!liveModel.account.trim() || !liveModel.password) {
        liveModel.submitState = "ERROR";
        root.dispatchEvent(new CustomEvent("login-candidate-local-error", { bubbles: true }));
        return;
      }
      liveModel.submitState = "LOADING";
      root.dispatchEvent(new CustomEvent("login-candidate-local-loading", { bubbles: true }));
      setTimeout(() => {
        liveModel.submitState = "ERROR";
        document.querySelector("#login-LoginPageRoot")?.dispatchEvent(new CustomEvent("login-candidate-local-error", { bubbles: true }));
      }, 300);
    });
    root.addEventListener("login-candidate-local-loading", () => adapter?.renderPreview(null, null, "LOADING").catch(() => {}), { once: true });
    root.addEventListener("login-candidate-local-error", () => adapter?.renderPreview(null, null, "INPUT_ERROR").catch(() => {}), { once: true });
    root.querySelector("#login-ForgotPasswordAction").addEventListener("click", event => event.preventDefault());
    root.querySelector("#login-RegisterAction").addEventListener("click", event => event.preventDefault());
  }

  async function createOptions({ repoHref, mapping, profile }) {
    assert(mapping?.page_id === CANDIDATE_PAGE_ID, "LOGIN_MAPPING_PAGE_ID_INVALID");
    assert(profile?.page_id === CANDIDATE_PAGE_ID && profile.status === "RESPONSIVE_RULE_REVIEW_REQUIRED", "LOGIN_PROFILE_STATUS_INVALID");
    await assertLocalCandidateOnly();
    const opts = { repoHref, mapping, profile, assets: null, disposed: false,
      referenceBoundsByNode: collectReferenceBounds(mapping.component_tree) };
    return opts;
  }

  async function render({ canvas, viewport, fontScale, state, authority, componentTree, adapter, options }) {
    assert(componentTree?.nodeId === "LoginPageRoot", "LOGIN_COMPONENT_TREE_ROOT_INVALID");
    assert(viewport?.unit === "css_px", "LOGIN_VIEWPORT_UNIT_INVALID", String(viewport?.unit));
    updateProjectedInspectorBounds(componentTree, viewport, options.referenceBoundsByNode);
    options.assets ||= await loadCandidateAssets(authority, options.repoHref);
    if (state === "PASSWORD_SHOWN") liveModel.visible = true;
    if (state === "PASSWORD_HIDDEN" || state === "DEFAULT") liveModel.visible = false;
    if (state === "PASSWORD_FOCUS" || state === "KEYBOARD_SIMULATION") liveModel.focused = "password";
    if (state === "ACCOUNT_FOCUS") liveModel.focused = "account";
    if (state !== "LOADING" && state !== "INPUT_ERROR") liveModel.submitState = "IDLE";
    canvas.innerHTML = renderMarkup({ state, assets: options.assets, viewport: { ...viewport, fontScale } });
    const page = canvas.querySelector("#login-LoginPageRoot");
    page.style.setProperty("--login-vw", `${viewport.width}px`);
    page.style.setProperty("--login-vh", `${viewport.height}px`);
    page.style.setProperty("--login-font-scale", String(fontScale));
    page.style.setProperty("--login-page-height", `${viewport.height}px`);
    page.style.fontSize = `${fontScale}em`;
    const icons = options.assets.icons;
    page.dataset.eyeSrc = icons.eye.url;
    page.dataset.eyeOffSrc = icons.eyeOff.url;
    const main = page.querySelector("#login-MainContent");
    const footer = page.querySelector("#login-Footer");
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    const minHeight = Math.max(viewport.height, Math.ceil(main.offsetTop + main.offsetHeight + footer.offsetHeight + 16));
    page.style.setProperty("--login-page-height", `${minHeight}px`);
    page.style.height = `${minHeight}px`;
    bindInteractions({ root: page, adapter });
    const pwdVisible = state === "PASSWORD_SHOWN" || liveModel.visible;
    page.querySelector("#login-PasswordInput").type = pwdVisible ? "text" : "password";
    if (state === "ACCOUNT_FOCUS") page.querySelector("#login-AccountInput").focus({ preventScroll: true });
    if (state === "PASSWORD_FOCUS" || state === "KEYBOARD_SIMULATION") page.querySelector("#login-PasswordInput").focus({ preventScroll: true });
    page.dataset.loginVisualState = state;
    page.dataset.loginFontScale = String(fontScale);
    page.dataset.loginViewport = `${viewport.width}x${viewport.height}`;
    page.dataset.loginCandidateAssetScope = "LOCAL_ONLY";
  }

  async function assertLocalCandidateOnly() {
    const host = location.hostname;
    assert(host === "localhost" || host === "127.0.0.1" || host === "::1", "PRIVATE_PREVIEW_REQUIRED", "Login icon publication is not confirmed by W2; this candidate may run only on loopback.");
    return true;
  }

  async function validateNegativeCases(authority, repoHref) {
    const required = findBoundAsset(authority, "background_bg_content");
    const results = [];
    for (const [id, mutate, expected] of [
      ["missing_file", () => ({ ...required, source_path: "design/pages/account_privacy/Login/__w6a_missing.png" }), "W2_ASSET_HTTP_ERROR"],
      ["wrong_sha", () => ({ ...required, sha256: "0".repeat(64) }), "W2_ASSET_SHA_MISMATCH"],
      ["wrong_dimensions", () => ({ ...required, pixel_dimensions: { ...required.pixel_dimensions, width: required.pixel_dimensions.width + 1 } }), "W2_ASSET_DIMENSIONS_MISMATCH"],
      ["wrong_mime", () => ({ ...required, media_type: "image/jpeg" }), "W2_ASSET_MIME_MISMATCH"],
      ["private_permission", () => ({ ...required, publication_permission: { state: "NOT_CONFIRMED" } }), "W2_PUBLICATION_NOT_CONFIRMED"],
      ["missing_permission", () => { const copy = { ...required }; delete copy.publication_permission; return copy; }, "W2_PUBLICATION_NOT_CONFIRMED"]
    ]) {
      let observed = "NO_FAILURE";
      try {
        const bad = mutate();
        assert(bad.requiredness === "REQUIRED", "W2_REQUIRED_RESOURCE_NOT_REQUIRED", id);
        assert(bad.lifecycle_status === "FROZEN", "W2_RESOURCE_NOT_FROZEN", id);
        assert(bad.publication_permission?.state === "PUBLIC_BY_EXISTING_PAGES_RULE", "W2_PUBLICATION_NOT_CONFIRMED", id);
        const response = await fetch(safeRepoHref(repoHref, bad.source_path), { cache: "no-store" });
        assert(response.ok, "W2_ASSET_HTTP_ERROR", `${bad.source_path} (${response.status})`);
        assert((response.headers.get("content-type") || "").toLowerCase().includes(String(bad.media_type).toLowerCase()), "W2_ASSET_MIME_MISMATCH", id);
        const bytes = await response.arrayBuffer();
        assert(bytes.byteLength === bad.bytes, "W2_ASSET_BYTE_COUNT_MISMATCH", id);
        assert(await sha256(bytes) === bad.sha256, "W2_ASSET_SHA_MISMATCH", id);
        const bitmap = await createImageBitmap(new Blob([bytes], { type: bad.media_type }));
        assert(bitmap.width === bad.pixel_dimensions.width && bitmap.height === bad.pixel_dimensions.height,
          "W2_ASSET_DIMENSIONS_MISMATCH", id);
        bitmap.close();
      } catch (error) { observed = String(error?.message || error).split(":")[0]; }
      results.push({ id, expected, observed, result: observed === expected ? "EXPECTED_FAIL" : "UNEXPECTED_RESULT" });
    }
    const reference = findBoundAsset(authority, "page_visual_authority_v2_1");
    let referenceObserved = "NO_FAILURE";
    try {
      const mislabeled = { ...reference, asset_role: "background_bg_content" };
      assert(mislabeled.asset_designation === "ORIGINAL", "W2_RENDER_ASSET_NOT_ORIGINAL", mislabeled.asset_designation);
    } catch (error) { referenceObserved = String(error?.message || error).split(":")[0]; }
    results.push({ id: "reference_image_as_rendered_background", expected: "W2_RENDER_ASSET_NOT_ORIGINAL", observed: referenceObserved,
      result: referenceObserved === "W2_RENDER_ASSET_NOT_ORIGINAL" ? "EXPECTED_FAIL" : "UNEXPECTED_RESULT" });
    assert(results.every(row => row.result === "EXPECTED_FAIL"), "LOGIN_NEGATIVE_TEST_FAILED", JSON.stringify(results));
    return results;
  }

  window.LoginV21Candidate = { createOptions, render, assertLocalCandidateOnly, validateNegativeCases,
    getModel: () => ({ ...liveModel }),
    dispose: () => { for (const row of imageCache.values()) URL.revokeObjectURL(row.url); for (const row of iconCache.values()) URL.revokeObjectURL(row.url); imageCache.clear(); iconCache.clear(); } };
})();
