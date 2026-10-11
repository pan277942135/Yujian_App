# Recognition Capture · Engineering Contract V3.1

**Status:** REVIEW · contract is not implementation authorization.

## Page structure
- Route family: existing Identify camera route and its Gallery-entry variant, as recorded by the canonical Master Flow.
- Content roles: current camera preview, Gallery action, capture action, permission/provider recovery. No screenshot or new page visual is inferred here.
- A readable normalized image is the only successful exit payload to Recognition Processing. This page does not persist a catch.

## Scroll, fixed and flexible
- Camera preview is a flexible live viewport.
- Capture action is a fixed action role; exact anchor, margin and size belong to a missing approved camera-page authority.
- Gallery and permission affordances remain reachable across all registered 360/393/412/480dp widths.
- Do not derive positions from the Processing PNG or from the 208px capture-button raster.

## Insets and system bars
- Apply the Geometry System V3.1 screen → window insets → content root → component coordinate model.
- Camera preview scaling must use the visible camera viewport after host insets are resolved.
- Edge-to-edge/status-bar treatment and control safe-area anchors are unresolved because no camera-page visual contract is registered. Do not infer them.

## Keyboard
- No page-owned text keyboard is defined.
- System permission UI and Photo Picker keep platform ownership and must return to the same recoverable capture context.

## Media Fit
- BG_CAPTURE uses the current user camera/gallery image as its dynamic source and its registered crop policy.
- A crop region/aspect implementation cannot be frozen until the referenced camera contract is located or approved.
- Keep the capture-button artwork identity and its SHA from the Asset Registry; no recoloring, scaling derivation or alternate icon is introduced by this document.

## Shared component use
- No Capture surface component is registered in the V3 component registry. Do not map the live camera preview to the video playback component and do not substitute a generic primary button for the frozen capture-button artwork.
- CAP-002 records this registry gap. No component is added in this phase.

## Animation and interaction
- Request permission only as a consequence of entering/using the camera route; permission denial must not produce a capture.
- Disable repeated capture while one still-image request is pending.
- Gallery cancellation is non-mutating.
- No new haptic or sound treatment is prescribed here; use only a registered page/component authority once one exists.

## Error and recovery
- Permission denied, camera unavailable, capture failure, picker cancellation and unreadable media are distinguishable states.
- Gallery can recover from camera permission/provider unavailability when the platform picker is available.
- A failed attempt does not create a record. Back returns to the invoking surface without saving.
