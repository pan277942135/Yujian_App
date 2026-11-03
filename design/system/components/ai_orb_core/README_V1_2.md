# YuJian AI Orb V1 resource closure · V2

Task: `YUJIAN_AI_ORB_RESOURCE_FINAL_CLOSURE_V2`. Main baseline: `10cda91218b1b8d5b1600ef95a9d676b2c040a07`. PR #304 remains Draft and open for product-owner visual review.

This package closes the source, geometry, profile, visual-board and validation gaps for the AI Orb V1 design candidate. It does not modify Android Runtime, the existing 900 ms motion behavior, the core asset, model/inference code or APK output.

## Frozen resources

- `assets/source/` contains the three preserved source images, including the 1254 × 1254 core master and `236493.png` water-ribbon reference.
- `assets/derived/processing_water_core_v1.webp` is a byte-identical portable copy of the current Main Runtime WebP. Current Main and PR return the same Git blob SHA `a70a71c623d15355cecda5b0e834d9b0b871e568`.
- `assets/paths/R06_orb_filament_path_geometry.json` preserves the five current cubic Bezier geometries. The SVG contains editable centerlines only; the JSON profile owns material width, alpha, refraction, feather, highlights and motion.
- R04 has two life states, S1 and S2. RELEASE and RETURN are events within S2. S3 is excluded.

## Candidate limits

All five material profiles remain inside the 14 dp outer bound after width multipliers and deterministic curve deformation. S2 visual samples use the same immutable runtime WebP as S1. They are static source-derived boards and do not establish Android rendering behavior. Product visual acceptance remains pending.

## Verification and handoff

- `tools/validate_orb_kit.py` is unchanged. Its PRELIMINARY baseline passed all 231 checks. The expanded package is also checked by `tools/validate_orb_v2.py`.
- `audits/R00_runtime_webp_integrity.json` and `audits/R00_pixel_difference_report.md` record the current runtime binary, decoded alpha and measured source comparison, including transparent RGB residue.
- `manifest_v1_2.json` and `ORB_RESOURCE_REGISTER_V1_2.xlsx` track the package files, hashes, byte sizes, image dimensions, alpha mode, purpose, source lineage, repository path and review/runtime state. The register avoids self-referencing its own hash and the primary Manifest hash; their hashes are recorded in the release record.

