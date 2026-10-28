# YuJian Android SlimSAM Pixel Segmentation V1

**Task ID:** `YUJIAN_ANDROID_SLIMSAM_PIXEL_SEGMENTATION_V1`  
**Phase:** P6 — Feature Implementation  
**Owner feature:** `recognition_flow_v1_2`  
**Gate:** `G-ANDROID-SLIMSAM-PIXEL-SEGMENTATION-V1`

## 1. Goal and boundaries

Add local, pixel-level fish segmentation to the existing Android recognition flow. Reuse the repository's current photo/session owners: the EXIF-normalized `SelectedImage.bitmap` and stable `imageId`, plus `RecognitionRevealSessionStore`'s `recognitionSessionId` and `recognitionAttemptId`. Compute a deterministic SHA-256 for the normalized source bitmap pixels and bind each segmentation result to that digest and the existing session/attempt. Do not introduce a parallel photo-session authority.

The only new inference path is:

`PhotoSession original photo + Detector BBOX → SlimSAM image encoder → BBOX prompt/mask decoder → full-resolution mask`

The existing `PhotoSession → Detector → Quality Gate / Crop → Classifier → Result` path remains authoritative. SlimSAM does not replace or feed back into the Detector, crop/quality gate, Classifier, or result semantics. Segmentation may run alongside existing recognition work; its failure must not fail or delay the existing path unnecessarily.

Do not redesign SWOS/RR09, processing timing, photo perimeter effects, or production UI. Do not introduce a parallel demo, photo session, or recognition product. A rectangle, hand-authored outline, GrabCut output, or other fallback is never a SlimSAM mask. A model or inference failure is reported as a failure state.

## 2. Model source and immutable provenance

Use the `boxes` branch of `Xenova/slimsam-77-uniform` as the required source candidate. Before integration, resolve its immutable repository commit and verify that the paired files are from that same commit:

- `onnx/vision_encoder_quantized.onnx`
- `onnx/prompt_encoder_mask_decoder_quantized.onnx`
- `preprocessor_config.json`
- `config.json`

For every file, retain the source URL, immutable commit, byte size, SHA-256, and license evidence in the task evidence report. Verify the upstream license before packaging and retain its required copyright/license notices. Do not assume that a moving branch name is immutable provenance. Do not mix files from another branch or model version, retrain, requantize, or convert the model. If the `boxes` decoder does not actually accept BBOX prompts or the files are not a compatible set, stop and report the mismatch.

Ship the verified models with the APK or install them into app-private storage from packaged assets. Runtime must not download model files or require network access. Reuse the repository's existing ONNX Runtime version if present. Add `onnxruntime-android:1.31.1` only if no compatible version is already present and API 28 compatibility is verified. Establish a CPU correctness baseline before considering any accelerator.

## 3. ONNX interface and image geometry

Read session metadata from the exact model files before writing the adapter. Record every input/output name, tensor type, and dimensions, then run CPU smoke tests for both sessions. Expected checks include encoder `pixel_values` `[1,3,1024,1024]`, encoder embeddings `[1,256,64,64]`, decoder BBOX prompt input, three candidate `pred_masks` at `256×256`, and `iou_scores`; these are verification targets, not permission to assume the signature.

Use the current attempt's EXIF-corrected `SelectedImage.bitmap`, not the classifier crop or detector tensor. Follow that model revision's preprocessor configuration for RGB conversion, longest-edge resize, padding, and normalization. Preserve source bitmap dimensions and the exact resize/padding transform. The production detector currently maps its normalized boxes back to that source bitmap's normalized coordinate space; verify and test this mapping before use. Clip and reject invalid boxes explicitly. Never confuse RGB/BGR, normalized coordinates, center/size boxes, or detector input pixels with the SlimSAM image geometry.

Run the image encoder once per current PhotoSession photo and reuse its embedding for all valid BBOX prompts for that photo. Run the paired decoder independently for every valid fish BBOX. Select the candidate using the model's `iou_scores`, and record the selected index and score. Postprocess and map the selected mask through the model's valid image region and padding transform to the original photo dimensions. Preserve a full-resolution lossless binary mask or equivalent probability representation; an outline-only rendering is insufficient.

## 4. Session isolation and recognition invariants

Every segmentation result is tied to the existing `sessionId` and `photoSha256`. Keep segmentation status independent from existing recognition states, with explicit success, model-unavailable, inference-failure, and cancellation outcomes. On cancellation, retry, recapture, or rapid photo switching, cancel or discard stale work. A late result for an old photo must never overwrite the active photo's state.

Segmentation errors must be contained so Detector, Classifier, PhotoSession, and Result continue with their existing behavior. Test that the existing Classifier output is unchanged for the same photo and Detector result. Do not make the Classifier consume the mask in this task.

## 5. Required evidence and acceptance

The exact task HEAD must have a traceable evidence report containing:

1. Model revision, file hashes/sizes, upstream license and retained notices.
2. Full encoder/decoder metadata and CPU smoke-test output.
3. Preprocessing and detector-to-model BBOX transform evidence, including portrait/landscape cases and edge clipping.
4. Per-photo encoder invocation count and multi-BBOX embedding reuse evidence.
5. Real-photo masks with original-image overlays, chosen candidate and score, output dimensions, and encoder/decoder/postprocess timing. Cover varied aspect ratio/orientation, fish size/pose, reflective/complex background, multiple/occluded/partially out-of-frame fish, and no-fish or unreliable detections. Do not claim mIoU/Dice without labeled ground truth.
6. Cancellation, retry, rapid photo switch, model-load/inference failure, and stale-result isolation evidence.
7. Proof that Detector/Classifier/result semantics remain unchanged and that packaged models load without network access.
8. API 28 build, unit/instrumentation/runtime results, device/runtime identity, cold/hot timing, peak memory, APK size delta, installable APK SHA-256, and exact source SHA.

Build or static checks, a Web preview, synthetic boxes, or model download alone do not establish runtime acceptance. A skipped or unavailable device run is `NOT_RUN` / `BLOCKED_RUNTIME_ENV`, never PASS. Product-owner visual review remains separate from technical gate results.

## 6. Stop conditions

Stop model integration and preserve the observed evidence if the immutable `boxes` model set is unavailable, the license cannot be verified, paired model signatures do not match, BBOX prompting is absent, or the model/runtime dependency is incompatible with API 28. If a physical API 28 runtime is unavailable, continue the independent source, build, unit, and APK preparation work and mark device acceptance `BLOCKED_RUNTIME_ENV` / `NOT_RUN`. Do not replace model behavior with a different prompt model or fake mask. Record the exact blocker and the narrowest action needed to resume.
