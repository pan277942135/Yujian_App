# NH01 Responsive Authority Crosswalk V4

**Status:** Current V4 user-frozen visual target registered; formal page G0/G6 and Android runtime gates remain independent.
**Purpose:** Bind the latest NH01 visual source to the existing verified responsive policy without treating historical images as current visual targets.

## Authority

| Source | V4 role |
|---|---|
| NH01_V4_VISUAL_BASELINE_MANIFEST.json + private 渔见湖光鱼获首页.png | Sole V4 visual authority. Original bytes stay local and are verified by SHA, dimensions, format and color metadata. |
| NH01_RESPONSIVE_LAYOUT_V2.json | Sole numeric responsive authority. ADAPTIVE_V2 geometry, 247:293 Hero ratio, 70% width / 337.2dp cap, 82% / 66.7% side cards, 15dp top offset, S/2 allocation, piecewise continuity and exact M01 exception remain in force. |
| 229571.png and repository V1 whole-page raster | Historical reference only. Neither is a V4 comparison target or source for V4 colors, positions, materials or composition. |
| M01–M05 V1 static anchors | Historical responsive evidence. V4 visual comparisons use an aspect-preserving projection of the single current user-frozen source at each runtime size. |
| Existing API 28 emulator workflow | Required execution path for Android runtime captures and measurements. It is emulator evidence, not physical-device acceptance. |

## Runtime and comparison

The existing NH01 API 28 AVD and runner are reused. A V4 matrix run records emulator/API identifiers, actual density, screenshot bounds, safe insets, font scale, screenshots and component bounds. The comparator reads only the manifest-selected source, verifies its SHA and metadata, and emits a centered aspect-preserving projection plus diagnostic diff files. Pixel similarity does not replace component, responsive, interaction or accessibility checks.

API 28 operating-system compatibility certification is outside this task. API 35/36 infrastructure is not a prerequisite. Physical phone acceptance remains a separate user-run step.

## Implementation invariants

Keep the existing page-owned responsive policy: Hero ratio and width cap, neighbor card dimensions/offset, continuous piecewise functions, equal S/2 remaining-space allocation and the exact M01 branch. Re-extract page-local visual targets from the latest frozen image. Do not inherit V1 image colors, material, photo composition or optical positions.

Static CTA text has no independent action target. The camera artwork and camera touch bound are measured separately; visible CTA-to-artwork separation and safe-area containment remain required.

## Round cap and evidence

The V4 repair loop has a maximum of three full audit/fix/regression rounds. Each round starts from a fresh complete matrix run, compares all scenario screenshots, records all findings, applies fixes, reruns the complete matrix and regression, and saves its own report. NH01_VISUAL_ISSUE_REGISTER.csv retains fixed, open, blocked and deferred findings. Round 3 ends iteration; no fourth round starts.
