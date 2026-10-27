#!/usr/bin/env node
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import { extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("../../..", import.meta.url)));
const source = await readFile(resolve(root, "design/manager/page_workspace_adapters.js"), "utf8");
class StubHTMLElement { replaceChildren() {} }
const window = {};
vm.runInNewContext(source, {
  window,
  HTMLElement: StubHTMLElement,
  crypto: webcrypto,
  TextDecoder,
  URL,
  Blob,
  AbortController,
  console
});
const api = window.PageWorkspaceAdapters;
const contentType = path => ({
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".md": "text/markdown; charset=utf-8"
}[extname(path).toLowerCase()] || "application/octet-stream");
const registered = JSON.parse(await readFile(resolve(root, "design/registry/experience_registry_v1.json"), "utf8"));

async function realFetch(path) {
  const clean = String(path).split("?")[0];
  try {
    const bytes = await readFile(resolve(root, clean));
    return new Response(bytes, { status: 200, headers: { "content-type": contentType(clean) } });
  } catch {
    return new Response("missing", { status: 404, headers: { "content-type": "text/plain" } });
  }
}

await api.initialize(registered, { fetchImpl: realFetch, repoHref: path => path });
assert.deepEqual([...api.active || []], []);
assert.equal(api.supportsPage("home_empty_v2"), true);
assert.equal(api.supportsPage("auth_login_v2"), true);
assert.equal(api.getRegisteredPage("home_empty_v2").id, "home_empty_v2");
assert.throws(() => api.getRegisteredPage("unregistered_page"), /PAGE_NOT_REGISTERED/);
for (const pageId of ["home_empty_v2", "auth_login_v2"]) {
  const feature = api.getRegisteredPage(pageId);
  const adapter = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId,
    sourceSha: "a".repeat(40), feature, fetchImpl: realFetch, repoHref: path => path });
  const authority = await adapter.loadAuthority("a".repeat(40));
  assert.equal(authority.page_id, pageId);
  assert.equal(authority.page_acceptance, "NOT_VERIFIED");
  assert.equal(authority.visual_sha256, authority.visual_asset.sha256);
  assert.equal(authority.visual_path, feature.design_versions.find(row => row.current).visual_authority);
  await assert.rejects(adapter.loadAuthority("b".repeat(40)), /SOURCE_SHA_MISMATCH/);
}

const empty = api.getRegisteredPage("home_empty_v2");
const failedImageFetch = async path => {
  if (String(path).startsWith("design/system/core_visual_v1/reference/empty_home_v2.png")) {
    return new Response("missing", { status: 404, headers: { "content-type": "text/plain" } });
  }
  return realFetch(path);
};
const missingAsset = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId: "home_empty_v2",
  sourceSha: "a".repeat(40), feature: empty, fetchImpl: failedImageFetch, repoHref: path => path });
await assert.rejects(missingAsset.loadAuthority("a".repeat(40)), /AUTHORITY_HTTP_ERROR/);

const changedAssetFetch = async path => {
  const response = await realFetch(path);
  if (!String(path).startsWith("design/system/core_visual_v1/reference/empty_home_v2.png")) return response;
  const bytes = new Uint8Array(await response.arrayBuffer());
  bytes[bytes.length - 1] ^= 1;
  return new Response(bytes, { status: 200, headers: { "content-type": "image/png" } });
};
const changedAsset = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId: "home_empty_v2",
  sourceSha: "a".repeat(40), feature: empty, fetchImpl: changedAssetFetch, repoHref: path => path });
await assert.rejects(changedAsset.loadAuthority("a".repeat(40)), /VISUAL_ASSET_SHA_MISMATCH/);

const unpermittedFetch = async path => {
  const response = await realFetch(path);
  if (!String(path).endsWith("Page_Resource_Binding_V1.json")) return response;
  const binding = await response.json();
  binding.asset_bindings.find(asset => asset.asset_role === "page_visual_authority_base").publication_permission.state = "NOT_CONFIRMED";
  return new Response(JSON.stringify(binding), { status: 200, headers: { "content-type": "application/json" } });
};
const unpermitted = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId: "home_empty_v2",
  sourceSha: "a".repeat(40), feature: empty, fetchImpl: unpermittedFetch, repoHref: path => path });
await assert.rejects(unpermitted.loadAuthority("a".repeat(40)), /VISUAL_ASSET_PERMISSION_NOT_CONFIRMED/);

const mismatchedProfileFetch = async path => {
  const response = await realFetch(path);
  if (!String(path).endsWith("home_empty_v2.profile.json")) return response;
  const profile = await response.json();
  profile.page_id = "auth_login_v2";
  return new Response(JSON.stringify(profile), { status: 200, headers: { "content-type": "application/json" } });
};
const mismatchedProfile = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId: "home_empty_v2",
  sourceSha: "a".repeat(40), feature: empty, fetchImpl: mismatchedProfileFetch, repoHref: path => path });
await assert.rejects(mismatchedProfile.loadAuthority("a".repeat(40)), /PROFILE_PAGE_ID_MISMATCH/);

let requestAborted = false;
const pendingFetch = (path, { signal }) => new Promise((resolve, reject) => {
  signal.addEventListener("abort", () => {
    requestAborted = true;
    reject(new Error("request aborted by adapter dispose"));
  }, { once: true });
});
const pendingAdapter = api.createReferenceAdapter({ root: new StubHTMLElement(), pageId: "home_empty_v2",
  sourceSha: "a".repeat(40), feature: empty, fetchImpl: pendingFetch, repoHref: path => path });
const pendingAuthority = pendingAdapter.loadAuthority("a".repeat(40));
pendingAdapter.dispose();
await assert.rejects(pendingAuthority, /request aborted/);
assert.equal(requestAborted, true);

console.log("PAGE_WORKSPACE_ADAPTER=PASS pages=2 negative_cases=7");
