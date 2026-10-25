# Recognition Capture · 首次拍照教学

**Status: FROZEN — three-page V1 for RECOGNITION-FIRST-USE-GUIDE-V1**

Design Manager 入口：`识别过程 → 07 · 首次拍照教学`

## Active pages

| ID | Title | Phone | Fish |
|---|---|---|---|
| P01 | 竖屏拍摄示意 | Portrait | Horizontal |
| P02 | 竖拍鱼类拍摄示意 | Portrait | Vertical |
| P03 | 横屏拍摄示意 | Landscape | Horizontal |

**Active sequence:** P01 → P02 → P03

P01 and P02 use `下一张`; P02/P03 expose `上一张`; P03 uses `开始拍摄`. Skip and final Start persist completion. Start returns to Capture Ready without taking a photo.

## Frozen assets

P01/P02/P03 are Design Manager visual authorities, not Android runtime backgrounds. Their original PNG bytes are imported and verified in `asset_import_report.json`:

- P01: 941×1671, 1,988,159 bytes, SHA-256 `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`
- P02: 941×1671, 2,042,852 bytes, SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`
- P03: 941×1672, 2,001,322 bytes, SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`

The task-scope resolution is recorded in `Scope_Resolution_20261016.md`. The later two-page freeze proposal remains preserved as history and does not replace this explicitly requested V1 task scope.

## Design Manager workspaces

`识别过程 → 07 · 首次拍照教学 → 资源` remains the existing hash route and opens the independent asset catalog by default. Its three tabs share the existing Design Manager renderer and registries; tab selection is preserved in `guide_workspace` and page selection in `guide_page` query parameters:

- **结构化高保真**: P01/P02/P03 selector, semantic layer tree, one frozen reference canvas with selectable measured-box overlay, and pixel/normalized geometry inspector.
- **响应式设计**: viewport preset/free-size controls, font-scale and inset simulation, responsive proposal preview, and the candidate/frozen boundary.
- **资源**: the three unchanged Frozen page PNGs plus all 12 photo-role mappings to eight independent generated candidate files, with direct originals, metadata and byte-level hashes. Archive-derived example crops remain `REFERENCE_ONLY` and are not promoted.

P03's landscape camera instruction and horizontal fish are inner teaching content; the outer page viewport remains responsive. No Android runtime background mapping is implied.

## Structured pre-development handoff

- `structured_pages.json` records three stable layer trees, parent/child links, visibility, candidate visual-order indices, shared component references and per-layer geometry status.
- `geometry_measurements.json` records native frozen-raster bounds and normalized coordinates. Source font declarations, original z-index, layer alpha/effects, independent hero/photo layers and invisible touch targets cannot be recovered from the flattened source PNGs.
- `responsive_contract.json` specifies inset-aware `dp` sizing, scrolling, wrapping, image containment, 48dp targets and a tested viewport × font-scale matrix. Browser simulation is not Android runtime acceptance.
- `frontend_assets_manifest.json` binds one hero plus three error examples per page. Eight generated independent PNGs are mapped to 12 slots; every slot is `CANDIDATE_NEEDS_APPROVAL`, `approved_for_frontend=false`, `approved_for_runtime=false`, and license/privacy review remains unresolved. The three original-source hero slots and nine original-source error slots remain blocked.
- The nine archived composite-derived error crops remain `REFERENCE_ONLY`. Neither they nor the Frozen full-page images are used as candidate source photos.
- `PREDEV_READINESS_MATRIX.json` and `Freeze_Decision.md` distinguish frozen measurable contracts, candidate visual choices, missing approved originals, and NOT_RUN runtime scope. `PREDEV_FULL_READY` / `ALL_ASSETS_FROZEN` are not claimed.

## Validation

Run `python3 scripts/verify_first_use_camera_guide_assets.py`, `python3 scripts/measure_first_use_camera_guide_geometry.py --check`, and the Design Manager navigation, hi-fi release, registry, and Chromium browser verification scripts. Exact-SHA CI, browser screenshots, deployment build marker, remaining blockers, and review links are recorded in the Issue #206 execution checkpoint and the implementation PR.
