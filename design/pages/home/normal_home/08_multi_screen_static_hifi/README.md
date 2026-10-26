# NH01 Multi-Screen Static Hi-Fi V1

## Freeze record

- Static image status: `STATIC_HIFI_FROZEN`.
- Responsive rule status: `STATIC_RULES_FROZEN`.
- Version/date: V1 / 2026-10-22.
- Android runtime: `NOT_VERIFIED`.
- Contract: `NH01_MULTI_SCREEN_RESPONSIVE_CONTRACT_V1.md` and machine-readable `.json` companion.
- Asset manifest: `NH01_MULTI_SCREEN_STATIC_HIFI_V1_MANIFEST.json`.
- Acceptance matrix: `NH01_STATIC_HIFI_ACCEPTANCE_MATRIX_V1.json`.
- Workbench route: `#page/home_normal_v1/hifi/multi_screen_static_hifi`.

The five source PNGs are unchanged recovered originals. The manifest stores their Asset IDs, source paths, actual raster dimensions, target dp profile, byte counts, and SHA-256. Target dp size is never substituted for PNG pixel size. The original Normal Home V1 reference remains byte-identical at `design/system/core_visual_v1/reference/normal_home_v1.png`, SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`.

## Authority relationship

This archive is additive to NH01. It does not replace the original whole-page visual, redefine NH07, create product/runtime state, or mark Android acceptance complete. Current Gemini Adaptive V2 authorities continue to govern Android geometry and execution. The new M05 480 dp width and 24±2 dp title/card spacing are a static-only extension; its native height is unspecified and its relationship to the height-based V2.2 runtime rule is documented in the contract.

The photos and real-person avatar shown in recovered mockups remain sample content. No default-avatar identity, auth state, permission, tap behavior, or runtime FishRecord is derived from these images.

## Change record

| Version | Date | Change |
| --- | --- | --- |
| V1 | 2026-10-22 | Registered the five source PNGs; froze target viewport mapping, full responsive review rules, and Design Manager workspace; added the M05 static 480 dp width boundary and recorded its Android scope conflict. Kept runtime `NOT_VERIFIED`. |

## Responsive Layout V2 freeze (2026-10-26)

[NH01_RESPONSIVE_LAYOUT_V2.json](../responsive/NH01_RESPONSIVE_LAYOUT_V2.json) is the frozen sole responsive machine contract for the existing route. The linked decision checkpoint records the gate basis and exact status. The structured-preview rules file remains metadata-only. All five source PNGs and the V1 archive remain unchanged; Android Runtime remains `NOT_VERIFIED` until the exact-SHA native gate passes.


## Structured preview integration — 2026-10-26

The existing `#page/home_normal_v1/hifi/multi_screen_static_hifi` route uses `responsive/NH01_RESPONSIVE_LAYOUT_V2.json` as the only machine source for responsive numeric values. `NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2.json` is presentation metadata only and must not contain a second numeric layout policy. The five frozen M01–M05 images remain visual anchors, not per-viewport coordinate templates. Browser evidence is separate from Android Runtime; Android remains `NOT_VERIFIED` until its native gate passes.
