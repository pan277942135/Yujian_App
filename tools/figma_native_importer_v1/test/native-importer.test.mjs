import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '../../..');
const packageRoot = path.join(repoRoot, 'tools/figma_native_importer_v1/build/YUJIAN_FIGMA_NATIVE_IMPORTER_V1');
const config = JSON.parse(readFileSync(path.join(packageRoot, 'importer.config.json'), 'utf8'));
const asset = config.assets[0];
const sourceBytes = new Uint8Array(readFileSync(path.join(packageRoot, asset.packageFile)));
const mainSource = readFileSync(path.join(packageRoot, 'main.js'), 'utf8');

function createHarness({ documentName = config.document.expectedName, pageName = config.page.expectedName, fills = [], mismatchReadback = false, omitTarget = false } = {}) {
  const messages = [];
  const page = { id: 'PAGE:NH01', type: 'PAGE', name: pageName, parent: null };
  let currentFills = fills.slice();
  let assignedCount = 0;
  let createImageCalls = 0;
  let exportSettings = null;
  let exportedBytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0]);
  const target = {
    id: asset.targetNodeId,
    type: 'FRAME',
    name: 'Morning Lake background source',
    parent: page,
    locked: false,
    width: 1080,
    height: 1920,
    get fills() { return currentFills; },
    set fills(value) {
      assignedCount += 1;
      currentFills = value;
      if (mismatchReadback && value.length === 1 && value[0].type === 'IMAGE') {
        currentFills = [{ ...value[0], scaleMode: 'FILL' }];
      }
    },
  };
  const frame = {
    id: config.verificationFrame.nodeId,
    type: 'FRAME',
    name: 'NH01_Normal_Home_1080x1920',
    parent: page,
    locked: false,
    width: config.verificationFrame.width,
    height: config.verificationFrame.height,
    fills: [],
    exportAsync: async (settings) => {
      exportSettings = settings;
      return exportedBytes;
    },
  };
  const nodeMap = new Map([[frame.id, frame]]);
  if (!omitTarget) nodeMap.set(target.id, target);
  const figma = {
    root: { name: documentName },
    currentPage: page,
    mixed: Symbol('mixed'),
    showUI: () => {},
    ui: {
      onmessage: null,
      postMessage: (message) => messages.push(message),
    },
    getNodeByIdAsync: async (id) => nodeMap.get(id) || null,
    createImage: (bytes) => {
      createImageCalls += 1;
      assert.equal(Buffer.from(bytes).equals(Buffer.from(sourceBytes)), true);
      return {
        hash: 'figma-internal-image-hash-abc123',
        getSizeAsync: async () => ({ width: asset.width, height: asset.height }),
      };
    },
  };
  vm.runInNewContext(mainSource, { figma, __html__: '<div>mock</div>', Uint8Array, DataView }, { timeout: 5000 });
  return {
    figma, messages, target, frame,
    get createImageCalls() { return createImageCalls; },
    get assignedCount() { return assignedCount; },
    get fills() { return currentFills; },
    get exportSettings() { return exportSettings; },
    set exportedBytes(value) { exportedBytes = value; },
    send: (message) => figma.ui.onmessage(message),
  };
}

async function passSource(harness, bytes = sourceBytes) {
  await harness.send({ type: 'SOURCE_BYTES', assetId: asset.id, bytes });
}

function lastMessage(harness, type) {
  return [...harness.messages].reverse().find((message) => message.type === type);
}

test('package carries exact Morning Lake source and manifest settings', () => {
  const hash = createHash('sha256').update(sourceBytes).digest('hex');
  assert.equal(hash, '5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7');
  assert.equal(asset.targetNodeId, '41:32');
  assert.equal(asset.scaleMode, 'FIT');
  assert.equal(existsSync(path.join(packageRoot, 'manifest.json')), true);
  assert.equal(existsSync(path.join(repoRoot, 'release/design_manager/YUJIAN_FIGMA_NATIVE_IMPORTER_V1.zip')), true);
  const manifest = JSON.parse(readFileSync(path.join(packageRoot, 'manifest.json'), 'utf8'));
  assert.equal(manifest.documentAccess, 'dynamic-page');
  assert.deepEqual(manifest.networkAccess.allowedDomains, ['none']);
  assert.equal(config.document.expectedName, 'Recognition Main Journey Master Board V1');
  assert.equal(config.document.alternateExpectedName, '渔见 · Recognition Main Journey Master Board V1');
});

test('valid source imports to the existing node and readback confirms IMAGE/FIT', async () => {
  const h = createHarness();
  await passSource(h);
  assert.equal(lastMessage(h, 'SOURCE_VERIFIED').sourceSha256, asset.sourceSha256);
  assert.equal(lastMessage(h, 'TARGET_STATUS').ready, true);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  const result = lastMessage(h, 'IMPORT_RESULT');
  assert.equal(result.ok, true);
  assert.equal(result.nodeId, '41:32');
  assert.equal(result.scaleMode, 'FIT');
  assert.equal(result.sourceSha256, asset.sourceSha256);
  assert.notEqual(result.imageHash, result.sourceSha256);
  assert.equal(JSON.stringify(h.fills.map(({ type, imageHash, scaleMode }) => ({ type, imageHash, scaleMode }))), JSON.stringify([
    { type: 'IMAGE', imageHash: 'figma-internal-image-hash-abc123', scaleMode: 'FIT' },
  ]));
  const assignmentsAfterFirstImport = h.assignedCount;
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  const repeatedResult = lastMessage(h, 'IMPORT_RESULT');
  assert.equal(repeatedResult.ok, true);
  assert.equal(repeatedResult.alreadyPresent, true);
  assert.equal(h.assignedCount, assignmentsAfterFirstImport);
  assert.equal(h.fills.length, 1);
});

test('Chinese-prefixed approved document name imports by exact match', async () => {
  const h = createHarness({ documentName: config.document.alternateExpectedName });
  await passSource(h);
  assert.equal(lastMessage(h, 'TARGET_STATUS').ready, true);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(h, 'IMPORT_RESULT').ok, true);
  assert.equal(h.fills.length, 1);
});

test('source hash mismatch is rejected before figma.createImage or node mutation', async () => {
  const h = createHarness();
  const altered = new Uint8Array(sourceBytes);
  altered[100] ^= 1;
  await passSource(h, altered);
  assert.match(lastMessage(h, 'ERROR').error, /Source SHA mismatch/);
  assert.equal(h.createImageCalls, 0);
  assert.equal(h.fills.length, 0);
});

test('near-match document name and wrong page are refused before node mutation', async () => {
  const wrongDocument = createHarness({ documentName: '渔见 · Recognition Main Journey Master Board V1 (Copy)' });
  await passSource(wrongDocument);
  assert.equal(lastMessage(wrongDocument, 'TARGET_STATUS').ready, false);
  await wrongDocument.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(wrongDocument, 'IMPORT_RESULT').ok, false);
  assert.equal(wrongDocument.fills.length, 0);

  const wrongPage = createHarness({ pageName: 'MASTER BOARD V1' });
  await passSource(wrongPage);
  assert.equal(lastMessage(wrongPage, 'TARGET_STATUS').ready, false);
  await wrongPage.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(wrongPage, 'IMPORT_RESULT').ok, false);
  assert.equal(wrongPage.fills.length, 0);
});

test('missing target node is refused before image creation or mutation', async () => {
  const h = createHarness({ omitTarget: true });
  await passSource(h);
  assert.equal(lastMessage(h, 'TARGET_STATUS').ready, false);
  assert.match(lastMessage(h, 'TARGET_STATUS').error, /Target node 41:32 was not found/);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(h, 'IMPORT_RESULT').ok, false);
  assert.equal(h.createImageCalls, 0);
  assert.equal(h.fills.length, 0);
});

test('unrelated pre-existing fill is preserved and not overwritten', async () => {
  const original = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 }, opacity: 0 }];
  const h = createHarness({ fills: original });
  await passSource(h);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(h, 'IMPORT_RESULT').ok, false);
  assert.match(lastMessage(h, 'IMPORT_RESULT').error, /unrelated fill/);
  assert.deepEqual(h.fills, original);
  assert.equal(h.assignedCount, 0);
});

test('an exact existing image fill is idempotent', async () => {
  const existing = [{ type: 'IMAGE', imageHash: 'figma-internal-image-hash-abc123', scaleMode: 'FIT' }];
  const h = createHarness({ fills: existing });
  await passSource(h);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  const result = lastMessage(h, 'IMPORT_RESULT');
  assert.equal(result.ok, true);
  assert.equal(result.alreadyPresent, true);
  assert.equal(h.assignedCount, 0);
});

test('failed fill readback rolls the node back to its previous fills', async () => {
  const h = createHarness({ mismatchReadback: true });
  await passSource(h);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  assert.equal(lastMessage(h, 'IMPORT_RESULT').ok, false);
  assert.equal(h.fills.length, 0);
  assert.equal(h.assignedCount, 2);
});

test('verification export uses the configured 1080 × 1920 frame at 1×', async () => {
  const h = createHarness();
  await passSource(h);
  await h.send({ type: 'IMPORT_ASSET', assetId: asset.id });
  const png = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
  h.exportedBytes = png;
  await h.send({ type: 'EXPORT_FRAME' });
  const result = lastMessage(h, 'FRAME_EXPORT');
  assert.equal(result.ok, true);
  assert.equal(result.fileName, 'NH01_Figma_1080x1920.png');
  assert.equal(result.width, 1080);
  assert.equal(result.height, 1920);
  assert.deepEqual(Array.from(result.bytes), Array.from(png));
  assert.equal(JSON.stringify(h.exportSettings), JSON.stringify({ format: 'PNG', constraint: { type: 'SCALE', value: 1 } }));
});

test('built plugin UI script parses without syntax errors', () => {
  const html = readFileSync(path.join(packageRoot, 'ui.html'), 'utf8');
  const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
  assert.equal(scripts.length, 1);
  assert.doesNotThrow(() => new vm.Script(scripts[0][1]));
});
