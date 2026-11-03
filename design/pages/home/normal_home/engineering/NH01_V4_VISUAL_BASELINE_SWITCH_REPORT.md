# NH01 V4 Visual Baseline Switch Report

**Task:** `YUJIAN_NH01_POPULATED_HOME_RESPONSIVE_HIFI_V4`  
**Status:** `BASELINE_REGISTERED`  
**Registered:** 2026-11-03T03:00:35Z

## Baseline switch

The sole V4 visual acceptance target is the current user-frozen `渔见湖光鱼获首页.png`. The original file was read locally without modification or re-encoding.

| Property | Current V4 target | Historical `229571.png` |
|---|---|---|
| SHA-256 | `8bb3bb1b4a2303d006bd3f6dbd373a11a02997d97ca64a1d5130c9432847e37c` | `f529f5a8595572f02e9ea72f9a47f4f3f93aebb055699e2c9a210f087b379828` |
| Dimensions | 941 × 1672 px | 864 × 1536 px (existing authority metadata) |
| Format | PNG | Existing authority metadata |
| Color metadata | RGB, 8-bit; no embedded ICC/sRGB profile chunk | Existing authority metadata |
| V4 role | `CURRENT_USER_FROZEN_DESIGN` | `HISTORICAL_TRACE_ONLY` |

The source identity and dimensions differ. The old source bytes were not available in this workspace, so a direct pixel-by-pixel old/new comparison is `NOT_RUN`; the V4 target does not fall back to the old image.

## Registration and comparison path

- Metadata-only registration: `design/pages/home/normal_home/engineering/NH01_V4_VISUAL_BASELINE_MANIFEST.json`.
- Current visual authority: `design/pages/home/normal_home/visual/NH01_LAKE_LIGHT_VISUAL_AUTHORITY_V2.json`.
- V4 task checklist points to the baseline manifest and the dedicated comparison script `scripts/design/compare_nh01_v4_visual.py`.
- The comparator validates the manifest SHA before opening the named local source; it has no fallback to `229571.png`.
- Historical reference metadata and files remain unchanged.

## Responsive contract and runtime

The current Gemini route continues to use `ADAPTIVE_V2`, the 247:293 Hero ratio, existing width cap, neighbor geometry, S/2 allocation, piecewise-linear curves and exact M01 exception. V4 visual parameters are extracted from the current frozen image and recorded in responsive contract V2.1.

Runtime priority is the repository's existing API 28 emulator workflow (`run-nh01-api28`). It produces actual emulator screenshots and layout measurements. No separate API 28 OS compatibility certification is required. API 35/36 infrastructure is not part of this task. Emulator evidence is not physical-phone acceptance.

## Evidence

The current file was read at its original path with SHA-256 `8bb3bb1b4a2303d006bd3f6dbd373a11a02997d97ca64a1d5130c9432847e37c`, size 1,984,137 bytes, PNG format, 941 × 1672 px, RGB/8-bit, and no embedded ICC/sRGB profile chunk. The source image bytes remain local; only metadata is registered in the repository.
