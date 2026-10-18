import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '../..');
const buildRoot = path.join(scriptDir, 'build');
const packageRoot = path.join(buildRoot, 'YUJIAN_FIGMA_NATIVE_IMPORTER_V1');
const config = JSON.parse(readFileSync(path.join(scriptDir, 'importer.config.json'), 'utf8'));
const expectedM1Sha = '5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7';
const allowedScaleModes = new Set(['FIT', 'FILL', 'CROP', 'TILE']);

function fail(message) {
  console.error(`BUILD_BLOCKED: ${message}`);
  process.exit(1);
}

function safeRepoPath(relativePath) {
  if (typeof relativePath !== 'string' || path.isAbsolute(relativePath)) fail(`Expected a repository-relative path, got ${relativePath}`);
  const absolute = path.resolve(repoRoot, relativePath);
  if (!absolute.startsWith(`${repoRoot}${path.sep}`)) fail(`Path escapes repository root: ${relativePath}`);
  return absolute;
}

function parsePng(bytes, assetId) {
  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  if (bytes.length < 24 || !bytes.subarray(0, 8).equals(signature)) fail(`${assetId}: invalid PNG signature`);
  if (bytes.readUInt32BE(12) !== 0x49484452) fail(`${assetId}: missing PNG IHDR`);
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) };
}

if (config.schemaVersion !== 1) fail(`Unsupported schemaVersion ${config.schemaVersion}`);
if (!Array.isArray(config.assets) || config.assets.length < 1) fail('At least one asset entry is required');
if (config.plugin?.id !== 'YUJIAN_FIGMA_NATIVE_IMPORTER_V1') fail('Unexpected plugin identifier');
if (!config.document?.expectedName || !config.page?.expectedName) fail('Expected document and page names are required');
if (!config.verificationFrame?.nodeId) fail('A verification frame is required');

const assetEntries = [];
const seenIds = new Set();
const seenTargets = new Set();
for (const asset of config.assets) {
  if (!asset.id || seenIds.has(asset.id)) fail(`Missing or duplicate asset id: ${asset.id}`);
  if (!asset.targetNodeId || seenTargets.has(asset.targetNodeId)) fail(`Missing or duplicate target node id: ${asset.targetNodeId}`);
  if (!Array.isArray(asset.targetNodeTypes) || asset.targetNodeTypes.length === 0) fail(`${asset.id}: targetNodeTypes is required`);
  if (!allowedScaleModes.has(asset.scaleMode)) fail(`${asset.id}: unsupported scaleMode ${asset.scaleMode}`);
  if (asset.mimeType !== 'image/png') fail(`${asset.id}: this version accepts PNG sources only`);
  if (!/^([a-f0-9]{64})$/.test(asset.sourceSha256)) fail(`${asset.id}: sourceSha256 must be lowercase SHA-256`);
  if (!asset.packageFile?.startsWith('assets/')) fail(`${asset.id}: packageFile must be inside assets/`);

  const sourcePath = safeRepoPath(asset.sourceFile);
  const originalBytes = readFileSync(sourcePath);
  const actualSha = createHash('sha256').update(originalBytes).digest('hex');
  if (actualSha !== asset.sourceSha256) fail(`${asset.id}: source SHA mismatch; expected ${asset.sourceSha256}, got ${actualSha}`);
  if (asset.id === 'nh01.morning_lake' && actualSha !== expectedM1Sha) fail('NH01 Morning Lake source SHA differs from the approved M1 value');
  const dimensions = parsePng(originalBytes, asset.id);
  if (dimensions.width !== asset.width || dimensions.height !== asset.height) {
    fail(`${asset.id}: PNG dimensions are ${dimensions.width} × ${dimensions.height}; expected ${asset.width} × ${asset.height}`);
  }
  seenIds.add(asset.id);
  seenTargets.add(asset.targetNodeId);
  assetEntries.push({ asset, originalBytes });
}

rmSync(buildRoot, { recursive: true, force: true });
mkdirSync(path.join(packageRoot, 'assets'), { recursive: true });
for (const { asset, originalBytes } of assetEntries) {
  const packageAssetPath = path.resolve(packageRoot, asset.packageFile);
  if (!packageAssetPath.startsWith(`${packageRoot}${path.sep}`)) fail(`${asset.id}: packageFile escapes package root`);
  mkdirSync(path.dirname(packageAssetPath), { recursive: true });
  writeFileSync(packageAssetPath, originalBytes);
}

const manifest = JSON.parse(readFileSync(path.join(scriptDir, 'manifest.template.json'), 'utf8'));
writeFileSync(path.join(packageRoot, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

const runtimeConfig = {
  schemaVersion: config.schemaVersion,
  plugin: config.plugin,
  document: config.document,
  page: config.page,
  assets: config.assets,
  verificationFrame: config.verificationFrame,
};
const mainTemplate = readFileSync(path.join(scriptDir, 'main.template.js'), 'utf8');
const uiTemplate = readFileSync(path.join(scriptDir, 'ui.template.html'), 'utf8');
const runtimeConfigJson = JSON.stringify(runtimeConfig);
if (!mainTemplate.includes('__IMPORT_CONFIG_JSON__') || !uiTemplate.includes('__IMPORT_CONFIG_JSON__') || !uiTemplate.includes('__ASSET_BUNDLE_JSON__')) {
  fail('Build templates are missing required replacement markers');
}
const sourceBundle = Object.fromEntries(assetEntries.map(({ asset, originalBytes }) => [asset.id, originalBytes.toString('base64')]));
writeFileSync(
  path.join(packageRoot, 'main.js'),
  mainTemplate.replace('__IMPORT_CONFIG_JSON__', runtimeConfigJson),
);
writeFileSync(
  path.join(packageRoot, 'ui.html'),
  uiTemplate
    .replace('__IMPORT_CONFIG_JSON__', runtimeConfigJson)
    .replace('__ASSET_BUNDLE_JSON__', JSON.stringify(sourceBundle)),
);
copyFileSync(path.join(scriptDir, 'README.md'), path.join(packageRoot, 'README.md'));
copyFileSync(path.join(scriptDir, 'CONFIG_SCHEMA.md'), path.join(packageRoot, 'CONFIG_SCHEMA.md'));
copyFileSync(path.join(scriptDir, 'importer.config.json'), path.join(packageRoot, 'importer.config.json'));

const releaseDir = path.join(repoRoot, 'release', 'design_manager');
mkdirSync(releaseDir, { recursive: true });
const zipPath = path.join(releaseDir, 'YUJIAN_FIGMA_NATIVE_IMPORTER_V1.zip');
rmSync(zipPath, { force: true });
try {
  execFileSync('zip', ['-qr', zipPath, 'YUJIAN_FIGMA_NATIVE_IMPORTER_V1'], { cwd: buildRoot });
} catch (error) {
  fail(`Could not create ZIP with system zip: ${error.message}`);
}

console.log(`BUILD_PASS: package=${packageRoot}`);
console.log(`BUILD_PASS: zip=${zipPath}`);
for (const { asset } of assetEntries) console.log(`ASSET_PASS: ${asset.id} sha256=${asset.sourceSha256} node=${asset.targetNodeId} scaleMode=${asset.scaleMode}`);
