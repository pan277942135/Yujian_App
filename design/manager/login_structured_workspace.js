/* Login V2.1 candidate mounted inside the shared Design Manager workspace. */
(() => {
  "use strict";

  const MAPPING_PATH = "design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json";
  const PROFILE_PATH = "design/pages/account_privacy/Login/structured_candidate/responsive_candidate_v1.json";
  let mountedAdapter = null;

  function assert(condition, message) {
    if (!condition) throw new Error(message);
  }

  async function readJson(path, { fetchImpl, repoHref, signal }) {
    const response = await fetchImpl(repoHref(path), { cache: "no-store", signal });
    if (!response.ok) throw new Error(`LOGIN_CANDIDATE_AUTHORITY_HTTP_ERROR: ${path} (${response.status})`);
    try { return await response.json(); }
    catch (error) { throw new Error(`LOGIN_CANDIDATE_AUTHORITY_JSON_INVALID: ${path}: ${error.message}`); }
  }

  async function mount(root, { pageId, sourceSha, feature, repoHref, fetchImpl = window.fetch.bind(window) }) {
    assert(pageId === "auth_login_v2", "LOGIN_WORKSPACE_PAGE_ID_INVALID");
    assert(window.PageWorkspaceAdapters?.mount, "SHARED_PAGE_WORKSPACE_ADAPTER_UNAVAILABLE");
    assert(window.LoginV21Candidate?.createOptions, "LOGIN_CANDIDATE_RENDERER_UNAVAILABLE");
    if (mountedAdapter) dispose();

    const controller = new AbortController();
    const [mapping, profile] = await Promise.all([
      readJson(MAPPING_PATH, { fetchImpl, repoHref, signal: controller.signal }),
      readJson(PROFILE_PATH, { fetchImpl, repoHref, signal: controller.signal })
    ]);
    assert(mapping.page_id === pageId && mapping.component_tree?.nodeId === "LoginPageRoot",
      "LOGIN_COMPONENT_MAPPING_ID_OR_ROOT_INVALID");
    assert(profile.page_id === pageId && profile.status === "RESPONSIVE_RULE_REVIEW_REQUIRED",
      "LOGIN_RESPONSIVE_CANDIDATE_STATUS_INVALID");

    let rendererOptions;
    try {
      rendererOptions = await window.LoginV21Candidate.createOptions({ repoHref, mapping, profile });
    } catch (error) {
      if (String(error?.message || error).startsWith("PRIVATE_PREVIEW_REQUIRED")) {
        root.innerHTML = `<section class="page-workspace-root" data-page-workspace-root data-page-id="auth_login_v2" data-source-sha="${sourceSha}" data-view-state="BLOCKED_RESOURCE_PERMISSION" data-workspace-kind="blocked-login-candidate"><header class="page-ws-heading"><div><small>W6 LOGIN · RESOURCE PERMISSION GATE</small><h2>Login V2.1 · 候选预览受限</h2><p>页面与候选工作区已注册；此主机没有 Login 共享图标的 W2 发布授权，Renderer 保持 fail-closed。</p></div><code>Source SHA ${sourceSha}</code></header><div data-workspace-error role="status"></div></section>`;
        root.querySelector("[data-workspace-error]").textContent = "PRIVATE_PREVIEW_REQUIRED · Shared icon publication is NOT_CONFIRMED_IN_LOGIN_W2_BINDING. This candidate is limited to loopback or the isolated PR #234 Pages subtree; icon publication and footer asset authority remain unresolved.";
        return { pageId, sourceSha, status: "BLOCKED_RESOURCE_PERMISSION", dispose() { root.replaceChildren(); } };
      }
      throw error;
    }
    const states = (profile.state_profiles || []).map(row => ({
      state_id: row.state_id,
      label: `${row.state_id} · ${row.status}`
    }));
    const adapter = window.PageWorkspaceAdapters.createStructuredDomAdapter({
      root,
      pageId,
      sourceSha,
      feature,
      repoHref,
      fetchImpl,
      displayName: "Login V2.1 · 共享结构化候选",
      coordinateUnit: "css_px",
      componentTree: mapping.component_tree,
      viewportProfiles: (profile.viewport_matrix?.viewports_css_px || []).map(row => ({
        width: row.width, height: row.height, unit: "css_px", profile_id: row.id
      })),
      fontScaleProfiles: profile.viewport_matrix?.font_scales || [1],
      stateProfiles: states,
      renderer: context => window.LoginV21Candidate.render({ ...context, adapter, options: rendererOptions })
    });
    window.PageWorkspaceAdapters.setActiveAdapter(adapter);
    mountedAdapter = adapter;
    window.LoginV21Workspace = adapter;
    await adapter.mount();
    return adapter;
  }

  function dispose() {
    if (mountedAdapter) mountedAdapter.dispose?.();
    mountedAdapter = null;
    window.LoginV21Candidate?.dispose?.();
    delete window.LoginV21Workspace;
  }

  window.LoginStructuredWorkspace = { mount, dispose };
})();
