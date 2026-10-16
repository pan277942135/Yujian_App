# Recognition Capture V3 Authority

**Authority ID:** recognition.capture.v3_1  
**Version:** V3.1  
**Status:** FROZEN  
**Effective visual authority:** this document plus the referenced Shared Foundation and Component Registry.

## Unique visual source

This is the sole page-level visual and layout authority for Recognition Capture. The Product Master Flow continues to own route order only. Its historical reference to 01_Capture_Transition_Frozen.png is not a Camera screen reference: that image remains a Recognition Processing keyframe. Do not use it as Capture artwork.

Camera Preview is a live runtime surface supplied by the camera provider. It is not Frozen Artwork, a static image asset, a screenshot, or a Processing frame. The preview has no static SHA. BG_CAPTURE means the current user camera/gallery image with no Morning Lake underlay.

## Page structure

1. **Camera Preview** — flexible live source inside the resolved preview viewport; preserve source ratio and use the registered cover behavior without distortion.
2. **Overlay Layer** — registered Top Title/Back and Gallery/icon actions plus state feedback. Interactive controls stay inside resolved system insets and do not obscure preview status.
3. **Primary Action Area** — fixed relative to the content root and safe area. The frozen Hi-Fi composition uses a right-side vertical action rail in both portrait and landscape: Capture above Gallery. Existing capture-button artwork remains the primary action; Gallery is an independent secondary action. The 208×208px PNG is a pixel master and does not define runtime dp size.

Coordinate ownership is Screen → Window Insets → Content Root → Component Layout. Preview can flex; the bottom action role stays fixed; controls do not move into system gesture/cutout regions. No frozen page screenshot or unregistered coordinate is inferred.

## Frozen Capture states

| State ID | Meaning | Required behavior |
|---|---|---|
| CAPTURE_READY | Permission and provider are ready | Capture and Gallery are available; repeated capture is prevented while pending. |
| CAPTURE_PERMISSION_REQUIRED | Camera access is not granted and a platform request is available | Request only as a result of entering/using the camera route; Gallery remains an independent option. |
| CAPTURE_PERMISSION_DENIED | Permission was denied or is no longer requestable | Never bind/start camera; keep Gallery and Back available; settings remain platform-owned. |
| CAPTURE_ERROR | Provider, capture or selected-media normalization failed | Announce recoverable failure; allow retry, Gallery or Back; no record is created. |
| CAPTURE_SUCCESS | One readable normalized image is ready | Hand the image once to Recognition Processing; do not persist a FishRecord. |

Gallery cancellation is non-mutating. An unreadable selected image maps to CAPTURE_ERROR. Back exits without saving. Camera permission denial does not disable the system Photo Picker.

## Shared component references

- media.camera_preview — live runtime provider stream; not Frozen Artwork.
- camera.capture_surface — runtime composition, lifecycle and permission boundary.
- navigation.top_title and navigation.back — registered Navigation contracts.
- action.primary_button — registered Primary Button contract using the existing capture-button asset identity.
- action.icon_action — registered Gallery/action affordance contract.
- BG_CAPTURE — current user image runtime source; no static background master.

## Runtime surface contract

- **Preview ratio:** follow the oriented active stream ratio; cover the preview box, preserve aspect ratio, and never stretch. Recompute the cover crop after viewport/inset changes.
- **Safe area:** preview uses available content bounds; navigation/status/action controls respect resolved insets. Bottom Action Area remains above the navigation inset.
- **Gesture exclusion:** do not claim system edge-swipe areas or block Back. Only registered controls receive touch handling.
- **Lifecycle ownership:** the Capture host owns one camera provider binding. Release on exit, backgrounding, permission loss or transfer to an external picker. Resume only after permission/provider guards pass.
- **Permission handling:** no camera bind or capture before permission is granted. The platform owns permission and Photo Picker UI; denial preserves an independent Gallery route.
- **Touch and accessibility:** actionable targets are at least 44×44dp and expose name, role and state; the live preview does not announce every frame.

## Asset identity boundary

The capture-button master remains the existing Asset Registry entry and SHA. Its RGBA pixel size is not a dp dimension. Live camera pixels are runtime data and must never be added to the frozen artwork list. The Processing Early PNG remains read-only and is not a camera visual.

## Additive first-use guide authority

The first-use camera guide is a two-page additive overlay state inside Recognition Capture. Its active visual and interaction authority is `design/pages/recognition/first_use_camera_guide/First_Use_Camera_Guide_Spec_V1_2.md`, with these frozen PNGs:

- `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png`
- `design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png`

P01 is portrait phone + horizontal fish; P02 is landscape phone + horizontal fish. P01 advances with `下一张`; P02 exposes `上一张` and uses `开始拍摄`. The former portrait-phone + vertical-fish page is archived. Skip and final Start dismiss the guide, and Start never fires the shutter. This overlay does not replace the live Camera Preview or redefine Capture's Top Navigation, Gallery, primary capture action, permission UI, or Photo Picker.
## Acceptance

Capture_Accessibility_Responsive_Matrix.md is the contract acceptance authority. Matrix PASS denotes frozen design coverage only; Android/runtime/device execution is NOT_RUN in this work.


## Hi-Fi visual authority V1

The orientation-specific Capture Ready Hi-Fi composition is frozen in:

- `design/system_v3/08_Page_Contracts/recognition/capture/Recognition_Capture_HiFi_Visual_Contract_V1.md`
- `design/pages/recognition/capture/HiFi_Freeze_Manifest_V1.json`

Both portrait and landscape use a horizontal fish and a fixed horizontal Fish Guide Frame. Both use the same right-side vertical Capture + Gallery action rail. No phone frame or external mockup is part of the authority.
