# Photo Capture Unified Input Contract V1

**Contract ID:** `PHOTO-CAPTURE-UNIFIED-INPUT-V1`  
**Feature:** `recognition_flow_v1_2`  
**Child surface:** `photo_input`  
**Status:** `FROZEN_CONTRACT`  
**Runtime alignment:** `PARTIAL`  
**High-fidelity requirement:** `NOT_REQUIRED`  
**Scope:** CameraX / Gallery source acquisition, photo identity, normalization, and handoff only.

## 1. Purpose and authority boundary

This contract defines the single input boundary shared by CameraX capture and Gallery selection. It is a technical input contract, **not a new UI page**. No independent high-fidelity image, Design Manager visual route, or new confirmation screen is required.

It supplements the existing Recognition Capture V3.1 page contracts. It does not rewrite the Capture page's visual/layout authority, the 03 Camera Runtime V2 visual freeze, the 05 Photo Morph contract, the 06 Button Morph contract, or the 10 Recognition Handoff visual freeze.

Authority ownership:

- This contract owns source acquisition semantics, source identity, file integrity, EXIF normalization, canonical photo creation, PhotoSession lifecycle, and PhotoInputReady handoff.
- Capture page contracts own camera permission, camera provider lifecycle, and capture-page interaction.
- 03 Camera Runtime owns the camera-page visual layout and controls.
- 05 Photo Morph owns photo presentation geometry and the 700ms photo transition.
- 06 Button Morph owns the 900ms shutter-to-water-orb transition.
- Recognition/model contracts own Detector, Classifier, SAM and model-specific preprocessing.
- No Capture input state may create or persist a FishRecord.

## 2. Non-negotiable principles

1. **Two input sources, one downstream contract.** CameraX and Gallery use different source adapters but converge to one PhotoSession and one PhotoInputReady event.
2. **A real source file is mandatory.** A CameraX callback or Gallery URI alone is not a successful input. The source must be readable, non-empty, decodable, dimensionally valid, and normalized.
3. **Never substitute a screenshot.** Camera preview screenshots, PreviewView captures, guide-frame crops, UI overlays and Gallery thumbnails are not recognition inputs.
4. **Source bytes and canonical bytes are different identities.** `sourceSha256` fingerprints exact source bytes; `photoSha256` fingerprints the canonical normalized JPEG passed to the recognition pipeline.
5. **Session identity is not image identity.** Every capture/selection attempt receives a new `sessionId`. `imageId` remains the image/asset identity and must not be treated as the attempt/session key.
6. **EXIF is normalized once.** All downstream display and model coordinates refer to the canonical orientation.
7. **Display and model processing are separate concerns.** Display previews may be sampled for memory; model input is derived from the canonical photo through the model's own preprocessing contract, never from a UI screenshot or arbitrary display crop.
8. **Camera and Gallery crop rules differ.** Camera output may be normalized to the actual PreviewView visible viewport. Gallery input is never subjected to the camera-only viewport crop.
9. **No network upload in this input stage.** `networkPhotoUpload=false`; the input stage is local-only.
10. **Stale sessions cannot commit.** Any async completion must prove it still belongs to the active `sessionId` before updating UI or starting recognition.

## 3. Source adapters

### 3.1 CameraX

- Use the actual file produced by CameraX `ImageCapture.takePicture()` / `onImageSaved()`.
- A success callback is insufficient by itself. Verify file existence, non-zero bytes, readable bounds, successful decode, positive dimensions, successful canonical persistence, and canonical SHA-256.
- The camera preview and ImageCapture must use the same CameraX ViewPort / UseCaseGroup geometry where available.
- Normalize the captured output to the visible camera preview composition after EXIF orientation. The crop is derived from the **actual PreviewView visible content bounds / FILL_CENTER viewport**, never from the four-corner fish guide.
- Exclude all UI overlays: title, back button, guide corners, zoom control, gallery control, shutter, flash control and bottom action panel.
- Preserve the camera capture presentation geometry separately for 05 Photo Morph: preview bounds and shutter center/size are animation metadata only, never image pixels.
- Camera temporary output may be deleted only after canonical output has been written, decoded, hashed and accepted.

### 3.2 Gallery

- Use Android system Photo Picker through `ActivityResultContracts.PickVisualMedia` with image-only selection.
- Gallery must work independently of camera permission.
- Copy the returned URI stream into an app-private temporary file before decoding. This avoids depending on repeated reads from local, cloud-backed or provider-managed URIs.
- Compute `sourceSha256` from the exact copied source bytes before normalization.
- Normalize EXIF orientation, but **do not apply CameraX PreviewView crop**. Preserve the complete selected image.
- Picker cancellation is non-mutating: no PhotoSession is committed, no error is shown, no stale image is reused, and the user returns to the same Capture context.
- The temporary import copy may be deleted only after the canonical image has been verified and the source hash has been recorded.
- Provide Gallery presentation-origin geometry to 05 Photo Morph: the initial selected-image bounds after Fit/Contain in the content viewport. This geometry is animation metadata only.

## 4. PhotoSession identity and data contract

Create a new PhotoSession for every user capture or Gallery selection attempt, before asynchronous normalization begins.

| Field | Type | Requirement | Meaning |
|---|---|---|---|
| `sessionId` | UUID/string | Required, unique per attempt | Async lifecycle and stale-result isolation key |
| `imageId` | UUID/string | Required | Image/asset identity; distinct from sessionId |
| `sourceType` | enum | Required | `CAMERA` or `GALLERY` |
| `sourceUri` | URI/string | Session-local | Original CameraX file or Gallery URI; do not send to backend |
| `sourceSha256` | hex SHA-256 | Required | Hash of exact original source bytes before normalization |
| `photoSha256` | hex SHA-256 | Required before ready | Hash of the canonical normalized JPEG |
| `mimeType` | string | Required | Detected source media type, not filename-extension guess |
| `sourceBytes` | integer | Required | Exact source file size |
| `sourceWidth`, `sourceHeight` | integer | Required | Source pixel dimensions before EXIF transform |
| `exifOrientation` | integer/enum | Required | Original EXIF orientation value |
| `normalizedWidth`, `normalizedHeight` | integer | Required | Canonical pixel dimensions after EXIF and Camera-only viewport crop |
| `createdAt` | timestamp | Required | Session creation time |
| `presentationOrigin` | object | Required for visual handoff | Camera preview/shutter geometry or Gallery Fit/Contain origin |
| `canonicalPhotoRef` | app-private ref | Required before ready | Persisted canonical photo used by recognition |
| `displayPreviewRef` | local bitmap/ref | Required before visual handoff | Downsampled preview used by UI; not a substitute for source/canonical photo |

Rules:

- A repeated selection of the same file creates a new `sessionId`; SHA equality must not cause reuse of old BBox, mask, prediction or state.
- The original source bytes are never overwritten. A temporary source copy can be released after canonical output verification; retain the source hash as provenance.
- `photoSha256` always refers to the canonical normalized photo. Do not silently change its meaning to the raw source hash.
- Recognition events must bind `sessionId + photoSha256 + modelSha256` (modelSha256 is supplied by the model/runtime contract).

## 5. Canonical normalization pipeline

The shared pipeline is:

```text
CameraX ImageCapture output ─┐
                             ├─> Create PhotoSession
Gallery Photo Picker URI ────┘
       -> Copy/read exact source bytes
       -> sourceSha256
       -> Read MIME, source dimensions and EXIF
       -> Bounds-first decode / memory-safe sampling
       -> Apply full EXIF orientation exactly once
       -> CAMERA only: apply actual PreviewView visible viewport crop
       -> GALLERY: preserve full image; no camera crop
       -> Persist canonical app-private JPEG
       -> Verify canonical file and dimensions
       -> photoSha256
       -> Produce display preview and presentation-origin metadata
       -> Emit PhotoInputReady exactly once
```

### 5.1 EXIF

- Normalize all eight EXIF orientation cases, including mirrored orientations; rotation-only handling is insufficient for mirror cases.
- Apply orientation once, before calculating canonical dimensions, camera viewport crop, BBox coordinates or model crops.
- Keep original EXIF metadata as provenance.
- Canonical bitmap, saved canonical JPEG, Detector coordinates, Classifier crop coordinates and future SAM mask coordinates must share the same normalized source coordinate space.
- Never rotate the canonical image again in downstream screens.

### 5.2 Camera viewport and Gallery fit

- Camera: the visible capture composition follows the actual PreviewView viewport and FILL_CENTER behavior after orientation normalization. The guide frame is visual guidance only.
- Gallery: the full selected source image is retained. The 05 card uses Fit/Contain and never crops or stretches the image.
- Presentation geometry is stored separately from pixels. It must never be passed as model coordinates.

### 5.3 Decode and memory protection

- Read image bounds and metadata before full decode.
- Use sampled/bounds-first decode to avoid allocating a full-resolution Bitmap unnecessarily.
- Do not reject a valid photo solely because its compressed file size is large; use streaming copy, bounds inspection and sampled decode, then report a clear error only when the device cannot safely decode/process it.
- Keep the source file immutable. Display sampling must not overwrite or replace the source.
- Supported V1 formats: JPEG/JPG, PNG, WebP; HEIC/HEIF only when the device decoder supports the actual file. RAW/DNG is outside V1.
- File-size handling bands (≤3MB, 3–10MB, 10–20MB, 20–50MB, >50MB) are operational diagnostics, not hard rejection limits.
- Any display-preview cap or model-input resize is a derived representation and must not change the recorded source dimensions or source hash.

## 6. PhotoInputReady event

Emit exactly one `PhotoInputReady` for the active session only after:

1. Source exists and is non-empty.
2. Source MIME and dimensions are identified.
3. EXIF normalization is complete.
4. Camera-only viewport crop (if applicable) is complete.
5. Canonical photo is persisted, readable and non-empty.
6. Canonical dimensions are positive.
7. `sourceSha256` and `photoSha256` are available.
8. Display preview and presentation-origin metadata are ready.

Required event payload:

- `sessionId`
- `imageId`
- `sourceType`
- `sourceSha256`
- `photoSha256`
- `canonicalPhotoRef`
- `displayPreviewRef`
- `sourceWidth/sourceHeight`
- `normalizedWidth/normalizedHeight`
- `exifOrientation`
- `mimeType/sourceBytes`
- `presentationOrigin`

The event is the only input handoff consumed by 05 Photo Morph / 06 Button Morph. It does not carry a model prediction and does not create a FishRecord.

## 7. Internal input states

These are internal processing states, not new user-facing pages:

- `IDLE`
- `SOURCE_REQUESTED`
- `SOURCE_ACQUIRED`
- `SOURCE_COPYING` (Gallery/provider copy when needed)
- `METADATA_READING`
- `EXIF_NORMALIZING`
- `CANONICALIZING`
- `VERIFYING`
- `INPUT_READY`
- `CANCELLED`
- `INPUT_ERROR`

Transitions:

- Camera: `CAPTURE_READY -> CAPTURING -> SOURCE_ACQUIRED -> METADATA_READING -> EXIF_NORMALIZING -> CANONICALIZING -> VERIFYING -> INPUT_READY`.
- Gallery: `PICKER_OPEN -> SOURCE_ACQUIRED -> SOURCE_COPYING -> METADATA_READING -> EXIF_NORMALIZING -> CANONICALIZING -> VERIFYING -> INPUT_READY`.
- Gallery cancel: `PICKER_OPEN -> CANCELLED`, with no session commit.
- Any unrecoverable source/decode/normalization error: `INPUT_ERROR`, remain recoverable at Capture.
- No state may enter Processing without a valid PhotoInputReady.

## 8. Cancellation, retry and stale-session isolation

- Only one CameraX capture request may be in flight.
- A second shutter tap while capture/save/normalize is active is ignored or disabled; exactly one accepted request yields at most one PhotoInputReady.
- Opening Gallery is blocked while CameraX is actively capturing; Gallery is otherwise independent of camera permission.
- Starting a new selection/capture attempt makes the previous session stale. Cancel its coroutine/job where possible and reject any late completion.
- Before every state write, navigation callback, PhotoInputReady emission or recognition result commit, compare the callback's `sessionId` to the active session.
- If the active session differs, discard the stale result and clean up its temporary resources. It must never replace the current photo.
- Back/cancel releases camera resources and cancels uncommitted input work. No failed/cancelled input creates a FishRecord.
- Retry creates a new `sessionId`; it must not reuse a partially normalized file or stale output.

## 9. Failure and recovery

| Failure | Required behavior |
|---|---|
| Camera permission denied | Do not start CameraX; Gallery and Back remain available |
| Camera provider unavailable | Keep Capture recoverable; offer retry, Gallery and Back |
| CameraX callback error | Do not emit PhotoInputReady; clean temporary output |
| Empty/missing source file | Reject input and allow retry |
| Gallery picker cancelled | Return without error and without mutation |
| Provider URI cannot be read | Show retryable Gallery error |
| Unsupported/corrupt image | Show clear reselect/retry message |
| EXIF or decode failure | Do not fabricate orientation or image; no Processing entry |
| Canonical persistence/hash failure | Do not emit PhotoInputReady |
| User changes photo during normalization | Invalidate old session; only latest session can commit |
| Memory pressure / OOM risk | Use bounds-first sampled decode; release intermediates; preserve source bytes |

Recommended user-facing copy:
- Camera: “没有读取到拍照内容，请重新拍摄”
- Gallery: “照片读取失败，请重新选择”
- Unsupported: “暂不支持此图片格式”
- Decode: “照片无法解析，请重新选择”
- Persistence: “照片保存失败，请重试”

Do not show raw exception text.

## 10. Handoff to 05 / 06 / 10

- 04 owns only the input source, PhotoSession, canonical photo and PhotoInputReady event.
- On PhotoInputReady, 05 Photo Morph and 06 Button Morph may run concurrently using the same session.
- 05 owns the frozen 700ms photo settle-to-card animation.
- 06 owns the frozen 900ms shutter-to-water-orb morph.
- The visual durations are not file-read or model-inference deadlines. If PhotoInputReady is not available, do not animate a fake photo or substitute a PreviewView screenshot.
- 10 Recognition Handoff consumes the same canonical photo/session; it must not create a second photo identity or reload a different source.
- AI inference may start only against the active canonical photo. Every result must remain bound to `sessionId`, `photoSha256` and the exact model SHA.

## 11. Acceptance matrix

| Dimension | Required cases | Pass condition |
|---|---|---|
| Source | CameraX, Gallery | Both converge to one PhotoInputReady schema |
| Camera image | Real ImageCapture JPEG | Saved source is used; no screen/preview screenshot |
| Camera crop | Portrait/landscape preview, fish near all edges | Captured composition matches actual PreviewView viewport; guide corners are not crop bounds |
| Gallery | Local and cloud-backed provider URI | Exact bytes copied/read; no camera crop |
| Permissions | Camera denied, Gallery selected | Gallery still works without camera permission |
| EXIF | 1–8, including 90/180/270 and mirror cases | Correct orientation exactly once |
| Aspect ratio | 9:16, 3:4, 1:1, 4:3, 16:9, panorama | Correct canonical dimensions; Gallery remains full-frame |
| Format | JPEG, PNG, WebP, supported HEIC/HEIF, unsupported RAW | Supported formats decode; unsupported formats fail clearly |
| Size | 500KB, 3MB, 10MB, 30MB, 50MB+ samples | No hard file-size-only rejection; memory-safe decode |
| Identity | Same photo selected twice | New sessionId each time; no old state reuse |
| Hash | Source and canonical outputs | sourceSha256 and photoSha256 match their respective bytes |
| Concurrency | Rapid A→B selection, cancel, retry, late callback | Only active session commits |
| Cleanup | Success, failure, cancellation, Back | Temporary resources released; canonical artifact retained for active recognition |
| Privacy | Camera and Gallery input | `networkPhotoUpload=false` |
| Handoff | 05 / 06 / 10 | Same session/photo identity across all stages |

## 12. Current Android implementation baseline and explicit gaps

Verified current source references:

- `app/src/main/java/com/yujian/ai/ui/screens/IdentifyScreen.kt`
- `app/src/main/java/com/yujian/ai/media/RecognitionImageStore.kt`
- `app/src/main/java/com/yujian/ai/ui/identify/RecognitionCameraCaptureContract.kt`
- `app/src/main/java/com/yujian/ai/model/Models.kt`

Observed baseline:

- IdentifyScreen uses CameraX ImageCapture and Android Photo Picker.
- Gallery is normalized through the shared RecognitionImageStore entry and does not require camera permission.
- Gallery URI bytes are copied into app-private temporary storage before decode; the temporary import copy is deleted after normalization.
- Camera output is normalized from the actual saved JPEG; its temporary capture file is deleted after normalization.
- The current canonical JPEG is persisted under app-private `filesDir/recognition_inputs`.
- Current normalizer uses bounds-first decode, samples the working bitmap to a maximum long edge around 2048px, writes JPEG quality 92, and computes `photoSha256` over the normalized JPEG.
- Current camera normalization accepts PreviewView aspect ratio and applies a centered FILL_CENTER crop after rotation; Gallery does not receive that camera-only crop.
- `SelectedImage` currently carries `imageId`, `source`, `sourceOrientationDegrees`, `photoSha256` and optional camera `capturePresentation`.

Explicit runtime-alignment gaps (not silently treated as implemented):

1. A distinct `sessionId` is not present in the current `SelectedImage` data model; `imageId` and capture request ID are not substitutes.
2. A separate `sourceSha256` of the exact raw CameraX/Gallery bytes is not currently recorded; current `photoSha256` is the canonical normalized JPEG hash.
3. Full EXIF mirror-orientation handling is not proven by the current rotationDegrees-only path.
4. Gallery presentation-origin geometry is not represented in the same explicit handoff shape as camera `capturePresentation`.
5. Exact pixel parity between CameraX PreviewView visible crop and normalized JPEG crop requires runtime evidence; aspect-ratio matching alone is not a PASS.
6. Active-session stale-result isolation must be verified end-to-end against the current recognition handoff.

This contract freezes the target semantics. It does **not** claim Android runtime alignment, API28 acceptance, or implementation completion.

## 13. Out of scope

- No new visual page or high-fidelity image.
- No change to frozen 01 Home Capture, 02 Capture Activation, 03 Camera Runtime V2, 05 Photo Morph, 06 Button Morph or 10 Recognition Handoff visual authorities.
- No change to Detector, Classifier, SAM, thresholds, model packaging or backend/API/database contracts.
- No FishRecord persistence in Capture.
- No network upload or cloud dependency.
- No Design Manager visual route is required for this input-only contract.
