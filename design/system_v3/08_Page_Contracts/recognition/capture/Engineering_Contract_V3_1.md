# Recognition Capture · Engineering Contract V3.1

**Status:** FROZEN page contract; runtime implementation is not tested in this work.

## Page structure
- Route family: existing Identify camera route and its Gallery-entry variant.
- The unique page visual/layout authority is Recognition_Capture_V3_Authority.md.
- Content order is Camera Preview → Overlay Layer → fixed Bottom Action Area.
- CAPTURE_SUCCESS hands one readable normalized image to Processing. Capture does not persist a record.

## Scroll, fixed and flexible
- Camera Preview is flexible and preserves the oriented live stream ratio using the registered cover behavior.
- Overlay controls use registered Navigation and Action components.
- Bottom Action Area is fixed above the resolved navigation inset; Gallery remains separately actionable.
- The capture-button PNG is a pixel master only; its 208×208px dimensions do not define dp size.

## Insets and system bars
- Apply Screen → Window Insets → Content Root → Component Layout.
- Preview uses available content bounds. Title, Back, Gallery and actions stay clear of status, cutout and navigation insets.
- Do not exclude system edge gestures or change platform bar ownership.

## Keyboard
- No page-owned text field or keyboard is defined.
- Permission and Photo Picker UI stay platform-owned and return to the same Capture context.

## Media Fit
- BG_CAPTURE uses the current runtime image with its registered no-Morning-Lake rule.
- Camera Preview follows the active stream ratio with cover and no distortion; no Frozen screenshot is a preview source.
- Gallery media is normalized once before the Processing handoff.

## Shared component use
- Use media.camera_preview and camera.capture_surface, plus navigation.top_title, navigation.back, action.primary_button and action.icon_action from the effective Component Registry.
- Preserve the existing capture-button Asset Registry identity; no icon substitution, asset edit or system-icon replacement is allowed.

## Animation and interaction
- Request permission only after entry/use of the camera route; never bind/capture before permission is granted.
- Capture is disabled while one request is pending; exactly one normalized image enters Processing.
- Gallery cancellation is non-mutating and works independently of camera permission.
- The Capture host owns provider lifecycle; release on exit, backgrounding, permission loss and picker handoff.

## Error and recovery
- CAPTURE_PERMISSION_DENIED never starts the camera and still offers Gallery/Back.
- Provider, capture and unreadable-media failures map to CAPTURE_ERROR with retry/Gallery/Back recovery.
- No failure creates a FishRecord; Back exits without persistence.
