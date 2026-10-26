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

## Structured responsive preview V2 (`PREVIEW_ONLY`)

The existing Design Manager route now calculates Header, Stats, Recent Catch, FishCatchPager, and CaptureActionGroup as separate component boundaries. Its browser-only rule set is [`NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2.json`](NH01_STRUCTURED_PREVIEW_LAYOUT_RULES_V2.json); it preserves the shared 247:293 Hero ratio, bounds extra group spacing, separates `peekGap` from the 1 dp overlap cap, and keeps any short-screen scroll inside FishCatchPager. It does not supersede the frozen runtime A/B/C rule or change Android code.

The capture prompt is a static label in the audited NH01 implementation and motion contract. The preview sizes it by its rendered line height, then lays out the camera's visible artwork inside its independent 72 dp minimum touch target. The action group does not add a second prompt touch box.

Three separate card images are registered in [`NH01_STRUCTURED_PREVIEW_MEDIA_V1.json`](NH01_STRUCTURED_PREVIEW_MEDIA_V1.json) as `GENERATED_ILLUSTRATIVE_ONLY`. They demonstrate image cropping and card layout only; failed image requests leave the CSS fallback and all measured component geometry intact. The pager marks and previous/next imagery are preview fixtures, not live record count or product data.

The Chromium verifier is [`scripts/verify_nh01_structured_responsive_layout.mjs`](../../../../../scripts/verify_nh01_structured_responsive_layout.mjs). It captures 8 viewport sizes × 3 font scales, measures component bounds and intervals, tests media-failure independence, and writes JSON/Markdown reports plus real rendered screenshots into the workflow artifact. Pixel difference from the frozen PNGs is not an acceptance threshold. Android Runtime remains `NOT_VERIFIED`.

## Change record

| Version | Date | Change |
| --- | --- | --- |
| V1 | 2026-10-22 | Registered the five source PNGs; froze target viewport mapping, full responsive review rules, and Design Manager workspace; added the M05 static 480 dp width boundary and recorded its Android scope conflict. Kept runtime `NOT_VERIFIED`. |
| Structured preview V2 | 2026-10-26 | Added the `PREVIEW_ONLY` component layout calculator, proportional short-screen Pager scrolling, bounded long-screen spacing, separate peek-gap/overlap measures, content-sized static CTA, 72 dp camera target, three illustrative-only card images, and 24-case Chromium evidence workflow. Android authority remains unchanged. |
