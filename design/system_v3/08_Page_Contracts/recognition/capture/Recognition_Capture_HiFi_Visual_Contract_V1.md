# Recognition Capture · Hi-Fi Visual Contract V1

**Status:** FROZEN  
**Freeze date:** 2026-10-16  
**Page:** `recognition.capture.v3_1`  
**States:** `CAPTURE_READY_PORTRAIT_HORIZONTAL`, `CAPTURE_READY_LANDSCAPE_HORIZONTAL`

## Purpose

Freeze the page-level Hi-Fi composition for the first-release Capture Ready surface without redesigning the V3.1 shared components.

## Two frozen orientation states

### 01 · Portrait phone + horizontal fish

- Runtime reference: 1080×1920
- Preview: full-screen live CameraX surface
- Fish: horizontal, complete head/body/tail visible
- Fish Guide Frame: fixed horizontal natural fish-shaped guide
- Back: top-left, existing `navigation.back`
- Capture: right-side vertical action rail, existing frozen primary capture-button artwork
- Gallery: below Capture in the same right-side rail, existing `action.icon_action`
- No phone frame, no external mockup, no Figma canvas chrome

### 02 · Landscape phone + horizontal fish

- Runtime reference: 1920×1080
- Preview: full-screen live CameraX surface
- Fish: horizontal, complete head/body/tail visible
- Fish Guide Frame: fixed horizontal natural fish-shaped guide
- Back: top-left, existing `navigation.back`
- Capture: right-side vertical action rail, existing frozen primary capture-button artwork
- Gallery: below Capture in the same right-side rail, existing `action.icon_action`
- No phone frame, no external mockup, no Figma canvas chrome

## Shared visual language

- Real camera photography remains dominant.
- Morning lake blue-gray / mist white / restrained gold are the visual language inherited from Normal Home and the first-use guide.
- `BG_CAPTURE` is the live camera stream. Do not place a static Morning Lake underlay beneath it.
- Fish Guide Frame is a light natural contour, not a rectangular bounding box and not a live AI detector result.
- No scanner, radar, HUD, neon, AI percentages, bbox coordinates, particles or technology decoration.
- The Guide Frame is fixed horizontal in V1. It does not rotate with device orientation and does not infer fish orientation.
- Capture and Gallery reuse the already-frozen component/icon identities; only page geometry changes by orientation.
- Capture controls remain inside resolved safe areas and do not claim system gesture/cutout regions.

## Control geometry

Both portrait and landscape use the same right-side vertical Capture + Gallery action rail. The fish remains visually centered and unobstructed.

## Component ownership

| Element | Authority |
|---|---|
| Live Preview | `media.camera_preview` |
| Capture composition | `camera.capture_surface` |
| Back | `navigation.back` |
| Capture | `action.primary_button` + existing `capture_button_main_v1.png` identity |
| Gallery | `action.icon_action` |
| Fish Guide Frame | Capture page-scoped visual overlay defined here |

## Binary authority

The following Hi-Fi source images are frozen by visual decision. Repository binary import is a separate mechanical step until the exact source bytes are written into the declared repository paths.

| State | Source file | Dimensions | Bytes | SHA-256 |
|---|---|---:|---:|---|
| Portrait | `a_realistic_high_resolution_vertical_smartphone_c.png` | 941×1672 | 2,184,165 | `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c` |
| Landscape | `湖畔拍照识鱼界面.png` | 1672×941 | 2,079,370 | `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18` |

Expected repository paths:

- `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png`
- `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png`

## Acceptance boundary

This is a visual/design freeze. It does not claim Android runtime acceptance. The existing implementation still needs orientation-specific right-rail geometry reconciliation before visual runtime acceptance.
