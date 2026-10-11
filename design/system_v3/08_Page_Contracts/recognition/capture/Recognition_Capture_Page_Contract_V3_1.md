# Recognition Capture · Page Contract V3.1

PAGE_ID: recognition.capture.v3_1  
PAGE_NAME: Recognition Capture  
VERSION: 3.1  
STATUS: FROZEN  
AUTHORITY_SOURCE: design/system_v3/08_Page_Contracts/recognition/capture/Recognition_Capture_V3_Authority.md

## Page Purpose
- **Goal:** let the user capture a readable photo or choose one image for recognition.
- **User task:** capture or select one image and enter Processing.
- **Entry condition:** the existing Home camera/gallery action opens the Identify route.
- **Exit condition:** one normalized image enters Recognition Processing, or the user leaves without saving. Capture never creates a FishRecord.

## User Flow
Entry → resolve camera permission/provider → CAPTURE_READY, CAPTURE_PERMISSION_REQUIRED, CAPTURE_PERMISSION_DENIED or CAPTURE_ERROR → Capture or Gallery → CAPTURE_SUCCESS → Recognition Processing. Back or picker cancellation exits/remains recoverable without persistence.

## Layout Contract
- Recognition_Capture_V3_Authority.md is the unique page-level visual/layout source.
- Page structure is Camera Preview → Overlay Layer → Bottom Action Area.
- BG_CAPTURE uses the runtime user image. Camera Preview is a runtime surface, not Frozen Artwork and has no static SHA.
- Public components and Foundation token/asset references resolve through Layout_Contract_V3_1.json and effective registries.
- The existing capture-button asset remains unchanged; source pixel dimensions do not define runtime dp bounds.
- No page-private token is added.

## State Machine Contract
State_Machine_V3_1.json freezes CAPTURE_READY, CAPTURE_PERMISSION_REQUIRED, CAPTURE_PERMISSION_DENIED, CAPTURE_ERROR and CAPTURE_SUCCESS, plus a separate CANCELLED exit. Permission, provider, Capture, Gallery and normalized-image handoff guards are explicit.

## Data Contract
Data_Contract_V3_1.json defines the runtime stream, permission/provider result, Gallery media and normalized-image handoff. Empty, loading, error, partial and network-failure behavior is explicit; no FishRecord is created.

## Engineering Contract
Preview is flexible and aspect-preserving; Overlay controls respect resolved system insets; Bottom Action Area is fixed above navigation inset. The Capture host owns camera lifecycle and permission handoff. Details are in Engineering_Contract_V3_1.md.

## Acceptance Contract
Acceptance_Contract_V3_1.json and Capture_Accessibility_Responsive_Matrix.md define visual, interaction, error and accessibility checks, including 320/360/411dp, fontScale 1.0/1.3/2.0, TalkBack and 44×44dp targets. Contract result is PASS; runtime execution is NOT_RUN.

## Conflicts and Freeze Gate
CAP-001 is closed by the unique written runtime-surface authority; the historical Processing keyframe is not a Capture visual. CAP-002 is closed by the frozen media.camera_preview / camera.capture_surface registry contracts and the existing capture-button asset mapping. CAP-003/C-008 is closed by the responsive/accessibility matrix. Frozen PNG bytes and Android/Backend implementation are unchanged.
