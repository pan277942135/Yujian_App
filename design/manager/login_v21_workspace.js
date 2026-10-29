(() => {
  "use strict";
  const PAGE_ID = "auth_login_v2";
  const MAPPING_PATH = "scripts/design/page_candidates/auth_login_v2/component_mapping_v1.json";
  const PROFILE_PATH = "scripts/design/page_candidates/auth_login_v2/responsive_candidate_v1.json";

  function assert(condition, code, detail = "") {
    if (!condition) throw new Error(detail ? `${code}: ${detail}` : code);
  }

  async function readBoundJson(path, { fetchImpl, repoHref }) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort("LOGIN_CANDIDATE_JSON_TIMEOUT"), 10000);
    try {
      const response = await fetchImpl(repoHref(path), { cache: "no-store", signal: controller.signal });
      assert(response.ok, "LOGIN_CANDIDATE_JSON_HTTP_ERROR", `${path} (${response.status})`);
      const text = await response.text();
      let value;
      try { value = JSON.parse(text); }
      catch { throw new Error(`LOGIN_CANDIDATE_JSON_INVALID: ${path}`); }
      assert(value && typeof value === "object", "LOGIN_CANDIDATE_JSON_INVALID", path);
      const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
      const sha256 = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
      return { value, sha256 };
    } catch (error) {
      if (controller.signal.aborted) throw new Error(`LOGIN_CANDIDATE_JSON_TIMEOUT: ${path}`);
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }

  async function createOptions({ pageId, sourceSha, feature, fetchImpl = window.fetch.bind(window), repoHref }) {
    assert(pageId === PAGE_ID, "LOGIN_ADAPTER_PAGE_ID_INVALID", pageId);
    assert(feature?.id === PAGE_ID, "LOGIN_ADAPTER_REGISTRY_MISMATCH", feature?.id);
    assert(/^[0-9a-f]{40}$/.test(sourceSha || ""), "LOGIN_ADAPTER_SOURCE_SHA_INVALID");
    assert(typeof window.LoginV21Candidate?.createOptions === "function", "LOGIN_CANDIDATE_RENDERER_MISSING");
    const [loadedMapping, loadedProfile] = await Promise.all([
      readBoundJson(MAPPING_PATH, { fetchImpl, repoHref }),
      readBoundJson(PROFILE_PATH, { fetchImpl, repoHref })
    ]);
    const mapping = loadedMapping.value;
    const profile = loadedProfile.value;
    assert(mapping.page_id === PAGE_ID && mapping.status === "CANDIDATE_MAPPING_REVIEW_REQUIRED",
      "LOGIN_COMPONENT_MAPPING_NOT_CANDIDATE", mapping.status);
    assert(profile.page_id === PAGE_ID && profile.status === "RESPONSIVE_RULE_REVIEW_REQUIRED",
      "LOGIN_RESPONSIVE_CANDIDATE_NOT_REVIEWED", profile.status);
    const candidateOptions = await window.LoginV21Candidate.createOptions({ repoHref, mapping, profile });
    const renderer = context => window.LoginV21Candidate.render({ ...context, options: candidateOptions });
    return {
      pageId: PAGE_ID,
      sourceSha,
      feature,
      fetchImpl,
      repoHref,
      displayName: "Login V2.1 · Structured Hi-Fi Candidate",
      coordinateUnit: "css_px",
      componentTree: () => mapping.component_tree,
      renderer,
      viewportProfiles: profile.viewport_matrix.viewports_css_px.map(row => ({
        width: row.width, height: row.height, unit: "css_px"
      })),
      fontScaleProfiles: profile.viewport_matrix.font_scales,
      stateProfiles: profile.state_profiles.map(row => ({
        state_id: row.state_id,
        label: `${row.state_id} · ${row.status}`
      })),
      mapping,
      mappingSha256: loadedMapping.sha256,
      responsiveCandidateSha256: loadedProfile.sha256,
      candidateOptions
    };
  }

  window.LoginV21Workspace = { createOptions, pageId: PAGE_ID, mappingPath: MAPPING_PATH, profilePath: PROFILE_PATH };
})();
