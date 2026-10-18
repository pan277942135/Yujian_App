const IMPORT_CONFIG = __IMPORT_CONFIG_JSON__;
const SOURCE_BYTES = new Map();

figma.showUI(__html__, { width: 480, height: 410, themeColors: true });

function post(type, payload) {
  figma.ui.postMessage({ type, ...payload });
}

function assetFor(assetId) {
  return IMPORT_CONFIG.assets.find((asset) => asset.id === assetId) || null;
}

function sha256Hex(bytes) {
  const constants = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ];
  const bitLength = bytes.length * 8;
  const paddedLength = Math.ceil((bytes.length + 9) / 64) * 64;
  const data = new Uint8Array(paddedLength);
  data.set(bytes);
  data[bytes.length] = 0x80;
  const view = new DataView(data.buffer);
  view.setUint32(paddedLength - 8, Math.floor(bitLength / 0x100000000));
  view.setUint32(paddedLength - 4, bitLength >>> 0);

  const state = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
  const words = new Uint32Array(64);
  const rotr = (value, amount) => (value >>> amount) | (value << (32 - amount));

  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let i = 0; i < 16; i += 1) words[i] = view.getUint32(offset + i * 4);
    for (let i = 16; i < 64; i += 1) {
      const x = words[i - 15];
      const y = words[i - 2];
      const s0 = rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3);
      const s1 = rotr(y, 17) ^ rotr(y, 19) ^ (y >>> 10);
      words[i] = (words[i - 16] + s0 + words[i - 7] + s1) >>> 0;
    }
    let [a, b, c, d, e, f, g, h] = state;
    for (let i = 0; i < 64; i += 1) {
      const sum1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const choice = (e & f) ^ (~e & g);
      const t1 = (h + sum1 + choice + constants[i] + words[i]) >>> 0;
      const sum0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const majority = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (sum0 + majority) >>> 0;
      h = g; g = f; f = e; e = (d + t1) >>> 0;
      d = c; c = b; b = a; a = (t1 + t2) >>> 0;
    }
    state[0] = (state[0] + a) >>> 0;
    state[1] = (state[1] + b) >>> 0;
    state[2] = (state[2] + c) >>> 0;
    state[3] = (state[3] + d) >>> 0;
    state[4] = (state[4] + e) >>> 0;
    state[5] = (state[5] + f) >>> 0;
    state[6] = (state[6] + g) >>> 0;
    state[7] = (state[7] + h) >>> 0;
  }
  return state.map((word) => word.toString(16).padStart(8, '0')).join('');
}

function pngDimensions(bytes) {
  const signature = [137, 80, 78, 71, 13, 10, 26, 10];
  if (!(bytes instanceof Uint8Array) || bytes.length < 24) return null;
  for (let i = 0; i < signature.length; i += 1) if (bytes[i] !== signature[i]) return null;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getUint32(12) !== 0x49484452) return null;
  return { width: view.getUint32(16), height: view.getUint32(20) };
}

function pageFor(node) {
  let current = node;
  while (current && current.type !== 'PAGE') current = current.parent;
  return current || null;
}

function inspectTarget(targetId, allowedTypes) {
  return figma.getNodeByIdAsync(targetId).then((node) => {
    if (!node) throw new Error(`Target node ${targetId} was not found in this file.`);
    if (figma.root.name !== IMPORT_CONFIG.document.expectedName &&
        figma.root.name !== IMPORT_CONFIG.document.alternateExpectedName) {
      throw new Error(`Wrong Figma file: expected exactly “${IMPORT_CONFIG.document.expectedName}” or “${IMPORT_CONFIG.document.alternateExpectedName}”, got “${figma.root.name}”. No changes made.`);
    }
    const page = pageFor(node);
    if (!page || page.name !== IMPORT_CONFIG.page.expectedName || figma.currentPage.id !== page.id) {
      throw new Error(`Open the “${IMPORT_CONFIG.page.expectedName}” page before importing. No changes made.`);
    }
    if (!allowedTypes.includes(node.type)) {
      throw new Error(`Target ${targetId} has type ${node.type}; expected ${allowedTypes.join(' or ')}. No changes made.`);
    }
    if (!Array.isArray(node.fills)) {
      throw new Error(`Target ${targetId} does not expose an editable fills array. No changes made.`);
    }
    if (node.locked) throw new Error(`Target ${targetId} is locked. No changes made.`);
    return { node, page };
  });
}

async function probeTarget(asset) {
  try {
    const { node } = await inspectTarget(asset.targetNodeId, asset.targetNodeTypes);
    post('TARGET_STATUS', {
      ready: true,
      nodeId: node.id,
      nodeType: node.type,
      nodeName: node.name,
      fillState: node.fills.length === 0 ? 'empty' : `${node.fills.length} existing fill(s) (will be preserved unless they match the exact imported image)`,
    });
  } catch (error) {
    post('TARGET_STATUS', { ready: false, error: error.message || String(error) });
  }
}

async function acceptSource(message) {
  const asset = assetFor(message.assetId);
  if (!asset) throw new Error(`Unknown asset ID: ${message.assetId}`);
  if (!(message.bytes instanceof Uint8Array)) throw new Error('Source payload must be a Uint8Array.');
  const actualSha = sha256Hex(message.bytes);
  if (actualSha !== asset.sourceSha256) throw new Error(`Source SHA mismatch: expected ${asset.sourceSha256}, got ${actualSha}.`);
  const dimensions = pngDimensions(message.bytes);
  if (!dimensions || dimensions.width !== asset.width || dimensions.height !== asset.height) {
    throw new Error(`PNG dimensions mismatch: expected ${asset.width} × ${asset.height}.`);
  }
  SOURCE_BYTES.set(asset.id, new Uint8Array(message.bytes));
  post('SOURCE_VERIFIED', {
    assetId: asset.id,
    sourceSha256: actualSha,
    byteLength: message.bytes.length,
    width: dimensions.width,
    height: dimensions.height,
  });
  await probeTarget(asset);
}

async function importAsset(assetId) {
  const asset = assetFor(assetId);
  const bytes = SOURCE_BYTES.get(assetId);
  if (!asset || !bytes) throw new Error('The exact source image has not passed SHA validation.');
  if (sha256Hex(bytes) !== asset.sourceSha256) throw new Error('Cached source SHA validation failed.');
  const { node } = await inspectTarget(asset.targetNodeId, asset.targetNodeTypes);
  const originalFills = node.fills.slice();
  const image = figma.createImage(bytes);
  const dimensions = await image.getSizeAsync();
  if (dimensions.width !== asset.width || dimensions.height !== asset.height) {
    throw new Error(`Figma image size is ${dimensions.width} × ${dimensions.height}; expected ${asset.width} × ${asset.height}.`);
  }
  const sameImageAlreadyInstalled = originalFills.length === 1 &&
    originalFills[0].type === 'IMAGE' &&
    originalFills[0].imageHash === image.hash &&
    originalFills[0].scaleMode === asset.scaleMode;
  if (sameImageAlreadyInstalled) {
    return { ok: true, alreadyPresent: true, nodeId: node.id, imageHash: image.hash, scaleMode: asset.scaleMode, sourceSha256: asset.sourceSha256 };
  }
  if (originalFills.length > 0) {
    throw new Error(`Target ${node.id} already has unrelated fill(s); they were preserved. Clear only after reviewing the node, then run again.`);
  }

  try {
    node.fills = [{ type: 'IMAGE', imageHash: image.hash, scaleMode: asset.scaleMode }];
    const readback = node.fills;
    const matches = Array.isArray(readback) && readback.length === 1 &&
      readback[0].type === 'IMAGE' &&
      readback[0].imageHash === image.hash &&
      readback[0].scaleMode === asset.scaleMode;
    if (!matches) throw new Error('IMAGE fill readback did not match the requested imageHash and scaleMode.');
    return { ok: true, alreadyPresent: false, nodeId: node.id, imageHash: image.hash, scaleMode: asset.scaleMode, sourceSha256: asset.sourceSha256 };
  } catch (error) {
    node.fills = originalFills;
    throw error;
  }
}

async function exportVerificationFrame() {
  const frameConfig = IMPORT_CONFIG.verificationFrame;
  const { node } = await inspectTarget(frameConfig.nodeId, frameConfig.expectedNodeTypes);
  if (Math.round(node.width) !== frameConfig.width || Math.round(node.height) !== frameConfig.height) {
    throw new Error(`Frame ${node.id} is ${node.width} × ${node.height}; expected ${frameConfig.width} × ${frameConfig.height}. No export created.`);
  }
  const bytes = await node.exportAsync({ format: 'PNG', constraint: { type: 'SCALE', value: 1 } });
  post('FRAME_EXPORT', {
    ok: true,
    fileName: frameConfig.fileName,
    width: frameConfig.width,
    height: frameConfig.height,
    bytes,
  });
}

figma.ui.onmessage = async (message) => {
  try {
    if (!message || typeof message.type !== 'string') return;
    if (message.type === 'SOURCE_BYTES') {
      await acceptSource(message);
      return;
    }
    if (message.type === 'IMPORT_ASSET') {
      const result = await importAsset(message.assetId);
      post('IMPORT_RESULT', result);
      return;
    }
    if (message.type === 'EXPORT_FRAME') {
      await exportVerificationFrame();
    }
  } catch (error) {
    if (message && message.type === 'IMPORT_ASSET') {
      post('IMPORT_RESULT', { ok: false, error: error.message || String(error) });
    } else if (message && message.type === 'EXPORT_FRAME') {
      post('FRAME_EXPORT', { ok: false, error: error.message || String(error) });
    } else {
      post('ERROR', { error: error.message || String(error) });
    }
  }
};
