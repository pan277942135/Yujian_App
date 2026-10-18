# YUJIAN_FIGMA_NATIVE_IMPORTER_V1

Local Figma Desktop development plugin for importing exact GitHub source-image bytes into existing Figma nodes. V1 packages the Morning Lake background for NH01 node `41:32`; it creates no overlay node and performs no network request.

## Current frozen asset

- Source: `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`
- SHA-256: `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`
- PNG dimensions: 941 × 1672
- Target: `41:32` on `NH01 / NORMAL HOME / STRUCTURAL HANDOFF`
- Fill: `IMAGE`, `FIT`
- Verification frame: `41:30`, exported at 1× only if it is 1080 × 1920

The PNG is copied byte-for-byte into the ZIP and separately embedded as Base64 in the plugin UI for the Figma message bridge. Base64 is a transport representation; the plugin decodes it, computes SHA-256 over the decoded PNG bytes, verifies the PNG dimensions, and only then calls `figma.createImage(bytes)`. Figma’s internal `imageHash` is reported separately and is never presented as the source SHA.

## Build and automated checks

Requirements: Node.js 20+ and the system `zip` command. No network access or npm package install is required.

From the repository root:

```sh
node tools/figma_native_importer_v1/build.mjs
node --test tools/figma_native_importer_v1/test/*.test.mjs
node --check tools/figma_native_importer_v1/build/YUJIAN_FIGMA_NATIVE_IMPORTER_V1/main.js
```

The builder refuses to package if the PNG SHA-256, IHDR dimensions, config schema, target IDs, or file paths differ from the approved values. The tests run the compiled plugin against a mocked Figma Plugin API and cover byte hashing, safe target checks, importing, readback, idempotent re-run, refusal to overwrite unrelated fills, rollback on failed readback, and the 1080 × 1920 export path. They do not substitute for running the plugin in Figma Desktop.

## Install in Figma Desktop

1. Download and unzip `YUJIAN_FIGMA_NATIVE_IMPORTER_V1.zip` to a local folder.
2. Open the Figma Desktop app and the file **Recognition Main Journey Master Board V1**.
3. Open page **NH01 / NORMAL HOME / STRUCTURAL HANDOFF**.
4. Use **Plugins → Development → Import new plugin from manifest…** and choose the extracted `YUJIAN_FIGMA_NATIVE_IMPORTER_V1/manifest.json`.
5. Run **YuJian Native Importer** from the Development plugins menu.
6. Confirm the UI reports the expected source SHA and target node `41:32`; click **Verify and import to 41:32**.
7. Require the UI result `IMAGE fill verified on 41:32 · FIT`. Record the separate Figma `imageHash` shown there.
8. Click **Export frame 41:30**. The plugin checks the frame is 1080 × 1920 and downloads `NH01_Figma_1080x1920.png` for comparison. If the Desktop download is blocked, use Figma’s native Export panel on frame `41:30` at 1×.

The plugin refuses a different file, page, node type, locked target, mismatched source hash/dimensions, and unrelated pre-existing fills. If the same image is already installed, it reports an idempotent readback instead of adding another fill. No existing component or page node is created or replaced.

## Local source verification

After unzipping:

```sh
sha256sum YUJIAN_FIGMA_NATIVE_IMPORTER_V1/assets/Morning_Lake_Master_V1.png
file YUJIAN_FIGMA_NATIVE_IMPORTER_V1/assets/Morning_Lake_Master_V1.png
```

Expected SHA-256 is `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`; expected type is PNG 941 × 1672.

## Reuse by Processing Work

`importer.config.json` is the reusable interface. Add an `assets[]` entry with a stable `id`, `displayName`, repository-relative `sourceFile`, `sourceSha256`, `mimeType`, `width`, `height`, `targetNodeId`, `targetNodeTypes`, and `scaleMode`. Source files must be copied in as-is; do not resize, recompress, or re-encode. The UI lists configured assets; the Plugin API request/response envelope and configuration rules are documented in `CONFIG_SCHEMA.md`. Keep source SHA separate from Figma `imageHash`.

## Runtime status

The package’s build checks and mocked Plugin API tests can pass locally. Actual Figma node mutation, IMAGE fill readback, export, and visual acceptance remain `NOT_RUN` until a user runs the plugin in Figma Desktop. This ZIP does not claim NH01 visual parity or handoff PASS.
