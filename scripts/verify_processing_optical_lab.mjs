import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const outputDir = path.resolve(process.env.LAB_OUTPUT_DIR || "optical-lab-artifacts");
const sourceRevision = process.env.LAB_SOURCE_REVISION || process.env.GITHUB_SHA || "LOCAL_UNPINNED";
const read = (relative) => readFile(path.join(root, relative));
const json = async (relative) => JSON.parse((await read(relative)).toString("utf8"));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const gitBlobSha = (bytes) => createHash("sha1").update(Buffer.concat([Buffer.from("blob " + bytes.length + "\0"), bytes])).digest("hex");
const dimensions = (bytes) => [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
const jpegDimensions = (bytes) => {
  if (bytes[0] !== 0xff || bytes[1] !== 0xd8) throw new Error("Not a JPEG image");
  let offset = 2;
  while (offset + 4 < bytes.length) {
    if (bytes[offset] !== 0xff) { offset++; continue; }
    let markerOffset = offset + 1;
    while (bytes[markerOffset] === 0xff) markerOffset++;
    const marker = bytes[markerOffset];
    offset = markerOffset + 1;
    if (marker === 0xd9 || marker === 0xda) break;
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    const length = bytes.readUInt16BE(offset);
    if (length < 2 || offset + length > bytes.length) throw new Error("Malformed JPEG segment");
    if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker)) {
      return [bytes.readUInt16BE(offset + 5), bytes.readUInt16BE(offset + 3)];
    }
    offset += length;
  }
  throw new Error("JPEG dimensions not found");
};

const configPath = "design/pages/recognition/processing/handoff/Processing_Engineering_Import_Config_V1.json";
const manifestPath = "design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json";
const registryPath = "design/system/fish_media_display_v1/real_photo_cases.json";
const photoAliasPath = "design/system/fish_media_display_v1/assets/real_catch_source.jpg";
const configBytes = await read(configPath);
const config = JSON.parse(configBytes.toString("utf8"));
const manifestBytes = await read(manifestPath);
const manifest = JSON.parse(manifestBytes.toString("utf8"));
const registryBytes = await read(registryPath);
const registry = JSON.parse(registryBytes.toString("utf8"));
const html = (await read("design/manager/optical-lab/index.html")).toString("utf8");
const manager = (await read("design/manager/index.html")).toString("utf8");
const dynamicReviewEnabled = /const DYNAMIC_REVIEW_ENABLED\s*=\s*true/.test(html);

assert.equal(config.source_snapshot.authority_commit, "31d1483b83038db78821625cd9339ad0e8c19ff3", "PR #165 authority SHA changed");
assert.equal(config.status, "ENGINEERING_HANDOFF_READY");
assert.equal(config.optical_lab_runtime_snapshot.status, "ANDROID_RUNTIME_SOURCE_SNAPSHOT_NOT_FROZEN_DESIGN");
assert.equal(config.optical_lab_runtime_snapshot.renderer_candidates.status, "BROWSER_ENGINEERING_CANDIDATE_NOT_FROZEN_PENDING_STATIC_VISUAL_REVIEW");
assert.equal(config.optical_lab_runtime_snapshot.receiving_light_layout.length, 4);
assert.equal(config.optical_lab_runtime_snapshot.companion_hairline_geometry.length, 4);
assert.ok(config.optical_lab_runtime_snapshot.fish_focus_static_values.status.includes("NOT_FROZEN_DESIGN"));
assert.deepEqual(config.p3_states_and_status.product_states.map((state) => state.id), [
  "IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING",
]);
assert.deepEqual(manifest.product_states.map((state) => state.id), [
  "IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING",
]);
assert.deepEqual(config.p1_edge_field.paths.map((item) => item.id), ["B1", "B2", "B3", "B4", "G1", "G2", "G3"]);
assert.ok(config.p1_edge_field.paths.some((item) => [...item.start, ...item.control1, ...item.control2, ...item.end].some((v) => v < 0 || v > 1)), "overshoot path control points stay unclamped");
assert.equal(config.p5_compose_import_bindings.image_bindings.frozen_references.length, 3);
assert.equal(config.p5_compose_import_bindings.image_bindings.test_photo.mask_or_bbox_binding, "none_trusted");
assert.equal(config.p5_compose_import_bindings.image_bindings.test_photo.sha256, "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef");
assert.equal(config.sources.length, 27, "P1–P5 config source inventory changed; review before extending it");
assert.equal(new Set(config.sources.map((source) => source.path)).size, config.sources.length, "source dependency inventory contains duplicate paths");

const expectedFiles = {
  IMAGE_RECOGNIZING: "02_AI_Understanding_Frozen.png",
  FISH_LOCATED: "03_Fish_Highlight_Frozen.png",
  SPECIES_RECOGNIZING: "04_Fish_Identifying_Frozen.png",
};
const assets = [];
const addAsset = (assetId, relativePath, role, provenance, sourceRevisionForAsset, dims = null, expectedSha = null, expectedBytes = null) => ({
  asset_id: assetId,
  repository_path: relativePath,
  source_revision: sourceRevisionForAsset,
  sha256: null,
  dimensions: dims,
  bytes: null,
  role,
  provenance,
  status: "VERIFIED",
  expected_sha256: expectedSha,
  expected_bytes: expectedBytes,
});
const configStat = await stat(path.join(root, configPath));
const configAsset = addAsset("processing-config", configPath, "P1–P5 existing structured contract", "Existing Processing_Engineering_Import_Config_V1.json; not duplicated or rewritten for this lab.", sourceRevision);
configAsset.sha256 = sha256(configBytes); configAsset.bytes = configStat.size; assets.push(configAsset);
const manifestAsset = addAsset("processing-state-manifest", manifestPath, "Three Frozen product-state bindings", "Existing PR #165 State Set Manifest; Git blob source binding verified below.", config.source_snapshot.authority_commit);
manifestAsset.sha256 = sha256(manifestBytes); manifestAsset.bytes = manifestBytes.length; assets.push(manifestAsset);

for (const state of manifest.product_states) {
  assert.ok(expectedFiles[state.id], "No fourth / Early state is permitted");
  assert.equal(state.keyframes.length > 0, true, "Frozen state requires a keyframe");
  const frame = state.keyframes[0];
  assert.equal(path.basename(frame.path), expectedFiles[state.id]);
  assert.deepEqual([frame.width, frame.height], [941, 1672]);
  const bytes = await read(frame.path);
  assert.equal(sha256(bytes), frame.sha256, "Frozen image hash changed: " + frame.path);
  assert.deepEqual(dimensions(bytes), [941, 1672], "Frozen PNG dimensions changed: " + frame.path);
  const configRef = config.p5_compose_import_bindings.image_bindings.frozen_references.find((item) => item.state === state.id);
  assert.equal(configRef.path, frame.path); assert.equal(configRef.sha256, frame.sha256);
  const asset = addAsset("frozen-" + state.id, frame.path, "read-only Frozen design reference", "PR #165 State Set Manifest + P5 SHA-256; original pixels are not modified.", config.source_snapshot.authority_commit, [941, 1672], frame.sha256, bytes.length);
  asset.sha256 = sha256(bytes); asset.bytes = bytes.length; assets.push(asset);
}

assert.equal(registry.status, "FROZEN_DESIGN");
assert.equal(registry.scope, "real_photo_css_containers_not_runtime_screenshots");
const photoBinding = config.p5_compose_import_bindings.image_bindings.test_photo;
const appPhotoBytes = await read(photoBinding.path);
const publishedPhotoBytes = await read(photoAliasPath);
assert.equal(appPhotoBytes.length, photoBinding.bytes);
assert.equal(sha256(appPhotoBytes), photoBinding.sha256);
assert.equal(registry.fixture.sha256, photoBinding.sha256);
assert.equal(registry.fixture.bytes, photoBinding.bytes);
assert.equal(registry.fixture.path, photoAliasPath);
assert.deepEqual(jpegDimensions(appPhotoBytes), [1152, 1536]);
assert.deepEqual(jpegDimensions(publishedPhotoBytes), [1152, 1536]);
assert.equal(sha256(publishedPhotoBytes), sha256(appPhotoBytes), "Published Design Manager photo alias must be byte-identical to P5 fixture");
assert.equal(gitBlobSha(publishedPhotoBytes), gitBlobSha(appPhotoBytes));
const registryAsset = addAsset("photo-provenance-registry", registryPath, "test-photo publication and scope registry", "Existing real_photo_cases.json; explicitly excludes runtime screenshot identity claims.", sourceRevision);
registryAsset.sha256 = sha256(registryBytes); registryAsset.bytes = registryBytes.length; assets.push(registryAsset);
const photoAliasAsset = addAsset("processing-test-photo", photoAliasPath, "runtime test fixture / local optical input", "Registered Design Manager copy; same bytes as P5 Android test fixture; no Frozen source-image binding.", sourceRevision, [1152, 1536], photoBinding.sha256, photoBinding.bytes);
photoAliasAsset.sha256 = sha256(publishedPhotoBytes); photoAliasAsset.bytes = publishedPhotoBytes.length; assets.push(photoAliasAsset);
const appPhotoAsset = addAsset("p5-android-test-photo", photoBinding.path, "P5 Android test photo", "P5 runtime fixture; registry confirms published copy equality; no BBox/Mask and no Frozen source-image binding.", sourceRevision, [1152, 1536], photoBinding.sha256, photoBinding.bytes);
appPhotoAsset.sha256 = sha256(appPhotoBytes); appPhotoAsset.bytes = appPhotoBytes.length; assets.push(appPhotoAsset);

const fit = config.p1_photoviewport.example;
assert.deepEqual(fit.source_px, [2000, 1000]);
assert.deepEqual(fit.viewport_px, [1000, 1000]);
assert.equal(fit.expected_scale, 0.5);
assert.deepEqual(fit.expected_translation_px, [0, 250]);
assert.deepEqual(fit.expected_photo_content_rect_px, [0, 250, 1000, 750]);

const sourceDependencies = [];
for (const source of config.sources) {
  const bytes = await read(source.path);
  assert.equal(gitBlobSha(bytes), source.blob_sha, "P1–P5 source Git blob SHA mismatch: " + source.path);
  sourceDependencies.push({
    repository_path: source.path,
    declared_git_blob_sha1: source.blob_sha,
    actual_git_blob_sha1: gitBlobSha(bytes),
    sha256: sha256(bytes),
    bytes: bytes.length,
    role: source.role,
    status: "VERIFIED",
  });
}

assert.ok(manager.includes('href="./optical-lab/"'), "Design Manager route should link to the lab");
for (const required of [
  "Processing_Engineering_Import_Config_V1.json", "Recognition_Processing_Visual_State_Set_V1_2.json",
  "hardware-gradient", "software-cache", "android-current", "PhotoViewport", "Receiving Light",
  "Fish Focus Halo", "Fish Focus Contour", "Status Card", "Pixel Diff", "50% Overlay",
  "drawFrozenReference", "makeAlignedFrozenCanvas", "renderer_candidates", "cubic_subdivisions",
  "FishSubjectResult", "State Strength", "paramTailPosition", "motionSpeed.disabled=true",
  "crypto.subtle.digest", "designRootUrl", "Optical Lab", "STATIC_REVIEW_CANDIDATES", "DYNAMIC_REVIEW_CANDIDATES",
  "drawReviewReceivingField", "drawDynamicFlow", "REVIEW_PROXY_NOT_PRODUCTION", "recordPreviewSequence",
  "previewModeButtons", "staticCandidate", "dynamicCandidate", "motionSpeed", "recordSequence", "fieldLayerView",
]) assert.ok(html.includes(required), "Optical Lab missing expected feature/config input: " + required);
assert.equal(html.includes("01_Capture_Transition_Frozen.png"), false, "Early visual must not return");
assert.ok(html.includes("requestAnimationFrame"), "Web review preview needs a real animation frame loop");
assert.ok(html.includes("stage-b-hidden"), "legacy P4 timeline remains separate from the Web Review controls");
assert.ok(html.includes("REVIEW ONLY · NOT ANDROID"), "web animation must be identified as review-only");
assert.ok(html.includes("elements.manifest.product_states.find(item=>item.id===state.id)"), "Frozen bindings must come from the existing State Manifest");
assert.ok(html.includes("source-photo identity unverified"), "Frozen comparisons must remain explicitly exploratory");
assert.equal(html.includes("createImageBitmap"), false, "no derived/fabricated Frozen source");

const report = {
  schema_version: "1.0",
  task: "RECOGNITION_OPTICAL_STATIC_DYNAMIC_CLOSURE_V1",
  repository: "pan277942135/Yujian_App",
  pull_request: 166,
  source_revision: sourceRevision,
  source_authority_pr165: config.source_snapshot.authority_commit,
  created_at_utc: new Date().toISOString(),
  assets,
  source_dependencies: sourceDependencies,
  contracts: {
    product_states: manifest.product_states.map((state) => state.id),
    frozen_pngs: Object.values(expectedFiles),
    edge_field_paths: config.p1_edge_field.paths.map((item) => item.id),
    p1_p5_source_count: sourceDependencies.length,
    test_photo: { sha256: photoBinding.sha256, bytes: photoBinding.bytes, dimensions: [1152, 1536], bbox: "none_trusted", mask: "none_trusted" },
  },
  gates: {
    source_contract_and_all_declared_blob_sha: "PASS",
    frozen_byte_integrity: "PASS",
    p5_photo_and_registered_alias: "PASS",
    viewport_fit_oracle: "PASS",
    three_states_seven_paths_no_early: "PASS",
    browser_route_and_runtime_assets: "PENDING_PLAYWRIGHT",
    frozen_source_photo_identity: "BLOCKED_UNBOUND",
    fish_focus_visual_comparison: "REVIEW_ONLY_PROXY_AVAILABLE_PRODUCTION_BLOCKED",
    static_optical_selection: "PENDING_HUMAN_REVIEW",
    motion_phase: dynamicReviewEnabled ? "WEB_REVIEW_ONLY_NOT_ANDROID" : "LOCKED_PENDING_STATIC_VISUAL_GATE",
    android_runtime: "NOT_RUN",
    figma_native_structure: "NOT_RUN",
    frozen_visual_qa: "BLOCKED",
    overall_m1: "NOT_PASS",
  },
};
await mkdir(outputDir, { recursive: true });
await writeFile(path.join(outputDir, "asset-manifest.json"), JSON.stringify(report, null, 2) + "\n");
await writeFile(path.join(root, "design/manager/optical-lab/asset-manifest.json"), JSON.stringify(report, null, 2) + "\n");
console.log(JSON.stringify({
  result: "SOURCE_VERIFICATION_PASSED_VISUAL_REVIEW_REQUIRED",
  source_revision: sourceRevision,
  gates: report.gates,
  sourceDependencyCount: sourceDependencies.length,
  verifiedAssets: assets.map(({ asset_id, sha256: digest, dimensions: dims, bytes }) => ({ asset_id, sha256: digest, dimensions: dims, bytes })),
}, null, 2));
