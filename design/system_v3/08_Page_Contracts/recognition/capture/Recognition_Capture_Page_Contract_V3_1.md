# Recognition Capture · Page Contract V3.1

PAGE_ID: recognition.capture.v3_1  
PAGE_NAME: Recognition Capture  
VERSION: 3.1  
STATUS: REVIEW  
AUTHORITY_SOURCE: design/system_v3/00_Product_Master_Flow/master_flow_v3_1.json

## Page Purpose
- **Goal:** provide the existing camera and Gallery entry points and yield one readable normalized image to Recognition Processing.
- **User task:** take a new photo or choose one existing image.
- **Entry condition:** the Home camera/gallery CTA opens the existing Identify route.
- **Exit condition:** a normalized image is ready and handed to Recognition Processing, or the user returns without creating a FishRecord.

## User Flow
Home camera CTA → permission/provider guard → live camera preview → Capture → normalized image → Recognition Processing.  
Home Gallery CTA or Capture-page Gallery action → system picker → readable image normalization → Recognition Processing.  
Permission denial, picker cancellation, provider failure and unreadable input remain recoverable. Save Catch and FishRecordDetail are outside this page.

## Layout Contract
- Machine-readable dependencies: Layout_Contract_V3_1.json.
- BG_CAPTURE is the effective background contract; its source is the current user photo, with the registry-defined no-Morning-Lake rule.
- The existing Frozen capture button asset is identified by Asset Registry path and SHA. It is not a Component Registry entry and is not converted into a new V3 component here.
- The Master Flow's capture_camera/capture_gallery visual_authority currently points at 01_Capture_Transition_Frozen.png. Recognition Design Closure V1.2 remaps that exact PNG to Processing IMAGE_RECOGNIZING / Early. It is therefore not a camera-page visual. This conflict is recorded as CAP-001; no screenshot is changed.
- No page-private tokens are added.

## State Machine Contract
See State_Machine_V3_1.json for EMPTY, permission, provider, capture, Gallery and normalized-image states and guarded transitions. No capture state creates a FishRecord.

## Data Contract
See Data_Contract_V3_1.json. The image is required only to enter Recognition Processing. Permission is required only for the camera route; the Gallery picker remains a separate user action. Empty, loading, error, partial and network-failure behavior are explicitly recorded.

## Engineering Contract
See Engineering_Contract_V3_1.md. The preview is flexible and the capture role is fixed; exact page coordinates, permission copy, page-level inset treatment and shared component mapping remain blocked until an approved camera-page authority and V3 component mapping exist.

## Acceptance Contract
See Acceptance_Contract_V3_1.json. State transitions are testable now; visual and touch-target checks remain blocked by CAP-001/CAP-002/CAP-003. The page is not FROZEN.

## Conflicts and Freeze Gate
- **CAP-001:** Capture nodes point at a Processing keyframe instead of a camera screen authority.
- **CAP-002:** existing Frozen capture-button artwork/spec is absent from the effective V3 component registry; do not add a component without approval.
- **CAP-003 / C-008:** permission-denial, edge-to-edge and capture-specific accessibility/responsive rules lack an approved authority.
- Result: REVIEW. Owner decision/authority is required; no Frozen raster, Shared Foundation token or Android code was changed.
