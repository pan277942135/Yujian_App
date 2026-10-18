# YUJIAN_FIGMA_NATIVE_IMPORTER_V1 Delivery Report

Status: `PLUGIN_PACKAGE = PASS` (local static and automated verification); `FIGMA_NODE_IMPORT = NOT_RUN`; `NH01_FIGMA_STRUCTURAL_HANDOFF = REVIEW_REQUIRED`.

## Scope and authority

- Repository: `pan277942135/Yujian_App`
- Delivery PR: [#164](https://github.com/pan277942135/Yujian_App/pull/164), Draft / Open / Unmerged
- Branch: `design/m1-nh01-figma-structural-handoff-v2`
- Figma file: `Recognition Main Journey Master Board V1`
- Target page: `NH01 / NORMAL HOME / STRUCTURAL HANDOFF`
- Existing background target: node `41:32` (no node/page/component creation)
- Verification frame: node `41:30`, 1080 × 1920
- Source: `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`
- Source SHA-256: `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`
- Source PNG dimensions: 941 × 1672
- Expected fill: `IMAGE`, `FIT`
- ZIP: `release/design_manager/YUJIAN_FIGMA_NATIVE_IMPORTER_V1.zip`
- ZIP SHA-256: `c5c6c64ce3afdac0be6b9c7c8c55ee2784ef193563c658c74debf59ed8093873`

## Implemented behavior

The development plugin is packaged with the original PNG bytes and makes no network requests. Before mutation it checks the active document and page names, resolves the existing node ID, checks type/lock/fills, verifies the SHA-256 and PNG dimensions, then calls `figma.createImage(bytes)`. It writes one `IMAGE` fill using the configured scale mode and reads back the fill and Figma `imageHash`. An exact existing fill is idempotent; unrelated fills are preserved and cause a no-write failure; failed readback restores the original fill list. The source SHA and Figma's internal `imageHash` are reported as distinct values.

After a verified import, the plugin can export the existing node `41:30` at 1×, and only when its dimensions match 1080 × 1920. The export is comparison input, not a visual acceptance result.

The package includes installation and use instructions in `tools/figma_native_importer_v1/README.md`. The reusable interface is `importer.config.json` plus the UI/Main message protocol in `CONFIG_SCHEMA.md`; Work B can reuse the schema and protocol for its own configured targets. This first package is intentionally configured for the NH01 background only.

## Local verification

Commands run against the current PR worktree with Node.js `v24.19.0`:

| Check | Result |
|---|---|
| Plugin package build and source PNG guard | PASS |
| Automated tests with mocked Figma Plugin API | PASS, 9/9 |
| Compiled main script syntax check | PASS |
| ZIP integrity (`unzip -t`) | PASS |
| Source PNG SHA equals approved SHA | PASS |
| Packaged PNG SHA equals source SHA | PASS |

The tests cover exact source bytes and config, successful import and IMAGE/FIT readback, rejection of a mismatched source hash, wrong document/page protection, preservation of unrelated fills, idempotence, rollback after failed readback, 1080 × 1920 frame export parameters, and UI script syntax.

## Figma Desktop execution and remaining verification

Actual Figma Desktop execution was not available to this Work run. Therefore the following remain `NOT_RUN`: real node `41:32` mutation, actual IMAGE fill / imageHash readback, rendered background inspection, frame `41:30` export, Frozen geometry/glyph/ROI Pixel Diff, and independent QA. Installing this package does not by itself close NH01 visual acceptance.

After the user runs the development plugin in Figma Desktop, capture the plugin's source SHA, `nodeId`, `scaleMode`, and Figma `imageHash` separately; verify the rendered canvas; export frame `41:30`; then run the existing project Frozen visual acceptance checks. Keep `NH01_FIGMA_STRUCTURAL_HANDOFF = REVIEW_REQUIRED` until those checks and independent QA close.

Other existing NH01 closure items remain unchanged: Avatar Authority conflict, camera artwork import, authorized Hero photo provenance, and existing typography/anchor/chevron authority questions. This plugin does not resolve them.
