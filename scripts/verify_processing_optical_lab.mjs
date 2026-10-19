import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const readJson = async (relative) => JSON.parse(await readFile(path.join(root, relative), "utf8"));
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");
const gitBlobSha = (bytes) => createHash("sha1").update(Buffer.concat([Buffer.from("blob " + bytes.length + "\0"), bytes])).digest("hex");
const configPath = "design/pages/recognition/processing/handoff/Processing_Engineering_Import_Config_V1.json";
const manifestPath = "design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json";
const configBytes = await readFile(path.join(root, configPath));
const config = JSON.parse(configBytes.toString("utf8"));
const manifest = await readJson(manifestPath);
const htmlPath = "design/manager/optical-lab/index.html";
const html = await readFile(path.join(root, htmlPath), "utf8");
const manager = await readFile(path.join(root, "design/manager/index.html"), "utf8");

assert.equal(config.source_snapshot.authority_commit, "31d1483b83038db78821625cd9339ad0e8c19ff3", "PR #165 authority SHA changed");
assert.equal(config.status, "ENGINEERING_HANDOFF_READY");
assert.equal(config.optical_lab_input_baseline.pr_165_head_sha, "31d1483b83038db78821625cd9339ad0e8c19ff3");
assert.equal(config.optical_lab_input_baseline.pr_166_head_sha_before_optical_lab, "f6ee9ddebb6036e9f3ce5629e5ff9f9b3c10a209");
assert.equal(config.optical_lab_runtime_snapshot.status, "ANDROID_RUNTIME_SOURCE_SNAPSHOT_NOT_FROZEN_DESIGN");
assert.equal(config.optical_lab_runtime_snapshot.renderer_candidates.status, "BROWSER_ENGINEERING_CANDIDATE_NOT_FROZEN_PENDING_STATIC_VISUAL_REVIEW");
assert.equal(config.optical_lab_runtime_snapshot.receiving_light_layout.length, 4);
assert.equal(config.optical_lab_runtime_snapshot.companion_hairline_geometry.length, 4);
assert.ok(config.optical_lab_runtime_snapshot.fish_focus_static_values.status.includes("NOT_FROZEN_DESIGN"));
assert.deepEqual(config.p3_states_and_status.product_states.map((state) => state.id), [
  "IMAGE_RECOGNIZING", "FISH_LOCATED", "SPECIES_RECOGNIZING",
]);
assert.equal(manifest.product_states.length, 3, "exactly three Frozen product states");
assert.equal(config.p1_edge_field.paths.length, 7, "exactly seven AI Edge Field paths");
assert.deepEqual(config.p1_edge_field.paths.map((item) => item.id), ["B1", "B2", "B3", "B4", "G1", "G2", "G3"]);
assert.equal(config.p1_edge_field.paths.some((item) => [...item.start, ...item.control1, ...item.control2, ...item.end].some((v) => v < 0 || v > 1)), true, "overshoot control points must remain unclamped");
assert.equal(config.p5_compose_import_bindings.image_bindings.frozen_references.length, 3);
assert.equal(config.p5_compose_import_bindings.image_bindings.test_photo.mask_or_bbox_binding, "none_trusted");
assert.equal(config.p5_compose_import_bindings.image_bindings.test_photo.sha256, "6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef");

const expectedFiles = {
  IMAGE_RECOGNIZING: "02_AI_Understanding_Frozen.png",
  FISH_LOCATED: "03_Fish_Highlight_Frozen.png",
  SPECIES_RECOGNIZING: "04_Fish_Identifying_Frozen.png",
};
for (const state of manifest.product_states) {
  assert.ok(expectedFiles[state.id], "no fourth or Early product state");
  const frozen = state.keyframes[0];
  assert.equal(path.basename(frozen.path), expectedFiles[state.id]);
  assert.equal(frozen.width, 941);
  assert.equal(frozen.height, 1672);
  const bytes = await readFile(path.join(root, frozen.path));
  assert.equal(sha256(bytes), frozen.sha256, "Frozen asset must remain byte-identical: " + frozen.path);
  assert.equal(bytes.readUInt32BE(16), 941, "PNG width " + frozen.path);
  assert.equal(bytes.readUInt32BE(20), 1672, "PNG height " + frozen.path);
  const configRef = config.p5_compose_import_bindings.image_bindings.frozen_references.find((item) => item.state === state.id);
  assert.equal(configRef.path, frozen.path);
  assert.equal(configRef.sha256, frozen.sha256);
}

const photoBinding = config.p5_compose_import_bindings.image_bindings.test_photo;
const photoBytes = await readFile(path.join(root, photoBinding.path));
assert.equal(photoBytes.length, photoBinding.bytes);
assert.equal(sha256(photoBytes), photoBinding.sha256);
const fit = config.p1_photoviewport.example;
assert.deepEqual(fit.source_px, [2000, 1000]);
assert.deepEqual(fit.viewport_px, [1000, 1000]);
assert.equal(fit.expected_scale, 0.5);
assert.deepEqual(fit.expected_translation_px, [0, 250]);
assert.deepEqual(fit.expected_photo_content_rect_px, [0, 250, 1000, 750]);

const expectedRuntimeBlobs = {
  "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionAmbientField.kt": "1969409c2f903a16ea38ed2f73b705daa424c050",
  "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionFishFocus.kt": "151d3f8494dc0b9edc4165b0d5028a5faeed916c",
  "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionProcessingVisualTokens.kt": "68a61dae637672337dd8117ae789493f112d97e3",
  "app/src/main/java/com/yujian/ai/ui/recognition/RecognitionStatusOverlay.kt": "891caf7473bf2a46c5730bc3586df7bc069f575a",
  "app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt": "cce00725a08f5d4c25e3ba6c0003ca6d8f7bf286",
};
for (const [relative, expected] of Object.entries(expectedRuntimeBlobs)) {
  const bytes = await readFile(path.join(root, relative));
  assert.equal(gitBlobSha(bytes), expected, "Android runtime source snapshot changed: " + relative);
}

assert.ok(manager.includes('href="./optical-lab/"'), "Design Manager route should link to the lab");
for (const required of [
  "Processing_Engineering_Import_Config_V1.json",
  "Recognition_Processing_Visual_State_Set_V1_2.json",
  "hardware-gradient",
  "software-cache",
  "android-current",
  "PhotoViewport",
  "Receiving Light",
  "Fish Focus Halo",
  "Fish Focus Contour",
  "Status Card",
  "Pixel Diff",
  "50% Overlay",
  "drawFrozenReference",
  "makeAlignedFrozenCanvas",
  "renderer_candidates",
  "cubic_subdivisions",
  "FishSubjectResult",
  "State Strength",
  "paramTailPosition",
  "paramHalo",
  "paramContour",
  "motionSpeed.disabled=true",
  "crypto.subtle.digest",
]) assert.ok(html.includes(required), "Optical Lab missing expected feature/config input: " + required);
assert.equal(html.includes("01_Capture_Transition_Frozen.png"), false);
assert.equal(html.includes("requestAnimationFrame"), false, "Stage A must remain static until reviewed");
assert.equal(html.includes("max=\"10000\""), false, "Stage B clock duration must be sourced from P4 config");
assert.ok(html.includes("elements.manifest.product_states.find(item=>item.id===state.id)"), "Frozen bindings must come from the existing state Manifest");
assert.ok(html.includes("ENGINEERING MODEL CHECKS PASS · VISUAL REVIEW PENDING"));
assert.equal(html.includes("createImageBitmap"), false, "no derived/fabricated Frozen source");

const summary = {
  result: "PASS",
  gates: {
    sourceContracts: "PASS",
    frozenAssetSHA256AndDimensions: "PASS",
    testPhotoSHA256AndBytes: "PASS",
    fitCoordinateOracle: "PASS",
    androidRuntimeSourceGitBlobSHAs: "PASS",
    threeProductStatesAndSevenPaths: "PASS",
    stageAStaticAndStageBLocked: "PASS",
    webPixelVisualReview: "NOT_RUN",
    androidRuntime: "NOT_RUN",
  },
  verifiedFrozen: manifest.product_states.map((state) => ({
    state: state.id, path: state.keyframes[0].path, sha256: state.keyframes[0].sha256,
  })),
  runtimeSourceBlobs: expectedRuntimeBlobs,
};
console.log(JSON.stringify(summary, null, 2));
