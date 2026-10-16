# 渔见 · First-use Camera Guide V1.1

**Status:** FROZEN — ACTIVE DESIGN AUTHORITY  
**Freeze date:** 2026-10-16  
**Scope:** Recognition Capture 首次相机进入时的两页教学 Overlay。

## Active pages

| ID | Phone | Fish | Primary action |
|---|---|---|---|
| P01 | Portrait | Horizontal | 下一张 |
| P02 | Landscape | Horizontal | 开始拍摄 |

The guide is shown once after camera permission is granted and the camera preview is Ready. P01 and P02 both teach the same core composition rule: the fish itself is horizontal and the entire fish should fit inside the capture frame.

### P01 · 竖屏拍摄示意

- Phone: portrait
- Fish: horizontal
- Correct example: full fish, head/body/tail visible and well framed
- Error examples: fish head outside frame; fish too small; excessive occlusion
- Primary action: 下一张
- Secondary exit: 跳过

### P02 · 横屏拍摄示意

- Phone: landscape
- Fish: horizontal
- Correct example: full fish, head/body/tail visible and well framed
- Error examples: fish head outside frame; fish too small; excessive occlusion
- Primary action: 开始拍摄
- Secondary navigation: 上一张 / 跳过

## Interaction rules

- 开始拍摄 closes the guide and returns to Capture Ready.
- 开始拍摄 never fires the shutter automatically.
- 跳过 closes the guide without firing the shutter.
- Guide completion is persisted only after Skip or the final Start action.
- Permission denied, preview-not-ready, gallery entry, or abandoned guide does not mark completion.
- The guide does not redefine Camera Preview, Back, Gallery, capture-button identity, permission UI, or Photo Picker.

## Visual rules

- Full-bleed natural lake photography; restrained blue-gray / mist white / gold.
- Hero is the single correct example; lower three examples are error examples.
- No HUD, radar, neon, scanner, game styling, or black modal treatment.
- Both pages use horizontal fish.
- The guide inherits Capture safe-area and shared component contracts.

## Frozen assets

P01:
- Path: design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png
- 941×1671
- 1,988,159 bytes
- SHA-256: dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50

P02:
- Path: design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png
- 941×1672
- 2,001,322 bytes
- SHA-256: 1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3

## Superseded history

The former three-page V1 is historical only. Former P02 portrait + vertical fish and former P03 page ID must not be used as active design or implementation input. Their source bytes remain archived for provenance.

## Implementation boundary

This is a design freeze only. Android implementation, runtime evidence, CI and APK acceptance are outside this freeze and require separate authorization.
