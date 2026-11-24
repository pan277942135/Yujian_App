# Cross-layer production call graph

**Baseline:** `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10`  
Edges below are based on source call sites. `PARTIAL` means a real producer/consumer exists but one or more V2 identities, receipts, or gates are missing. `MISSING` means no corresponding production implementation was located in the audited Kotlin/Java tree.

## 1. App entry → recognition attempt

```text
YujianApplication.onCreate
  → SlimSamRuntimeManager(context).startPreload(appCreateStartedAtNs)
  → application-owned Vision + Decoder sessions

YujianApp
  → remember { FishRecognitionPipeline(context) }
  → RecognizingScreen.recognize callback
  → FishRecognitionPipeline.recognize(bitmap, modelIdentity, onProgress)
  → FishDetectorEngine.detect(bitmap)
  → FishDetectionQualityGate.assess(...)
  → crop selected detector box
  → FishRecognitionEngine.recognize(crop)
  → ProductionRecognitionResult
```

Evidence: `app/src/main/java/com/yujian/ai/YujianApplication.kt:11-22`; `app/src/main/java/com/yujian/ai/YujianApp.kt:175,494,1057-1115`; `application-core/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt:28-45,63-80,104-185`.

Photo creation precedes this path:

```text
RecognitionImageStore.persistNormalized
  → apply EXIF orientation / optional camera viewport crop
  → compress normalized bitmap to JPEG quality 92
  → SHA256(normalized JPEG file bytes)
  → SelectedImage.photoSha256
```

`sourceBytesSha256` and `canonicalPixelSha256` are **MISSING**. This one JPEG hash is later carried as the photo identity in model, mask, path, Glyph, and handoff state.

## 2. Model runtime → mask output

```text
YujianApplication
  → SlimSamRuntimeManager.startPreload
  → verify packaged encoder/decoder SHA
  → create Vision Session and read input/output TensorInfo
  → validate Vision names/types/shapes
  → create Decoder Session and read input/output TensorInfo
  → validate Decoder names/types/shapes
  → READY only when both sessions are ready

RecognizingScreen
  → shared RecognitionAttemptDeadline (60,000ms)
  → SlimSamRuntimeManager segment request
  → SlimSamSegmenter.segment
  → Vision session.run(pixel_values)
  → embedding + positional embedding
  → Decoder session.run(points, labels, embeddings)
  → FishSubjectResult + mask bytes/hash/version/timing
```

Evidence: `SlimSamRuntimeManager.kt:104-145,287-339`; `SlimSamTensorContract.kt:10-83`; `SlimSamSegmenter.kt:238-278,498-510,598-612,700-730`; `RecognitionAttemptDeadline.kt:3-26`; `RecognizingScreen.kt:608-633,700-820,1077+`.

Partial/missing links: no per-run `inferenceId` or `modelSessionId`; no per-run `RunOptions` termination; no complete tensor-in/out schema hash and inference record tied to a committed result; cache key omits canonical pixel hash, encoder SHA, schema hash, and actual input shape. Exact installed APK model contents were not independently inspected in this source-only audit.

## 3. Mask → contour → P* → FishPath

```text
RecognizingScreen subject result callback
  → validate current session/attempt/image and mask SHA/version/dimensions
  → RecognitionMaskContourExtractor / contour validation
  → stableContact / P* from trusted mask geometry
  → WaterLightPathLedger binds mask + photo geometry + P*
  → WaterLightEnergy.at creates ABSORB event with deterministic path ID
  → RecognitionSceneAnimationController.onFishContactPathAvailable
  → controller.recordEnergyEventWithReason (identity/stage/sequence/path gates)
  → WaterLightPathLedger.exportRecognitionFishPathSnapshot
  → controller.onFishPathSnapshot (same photo/mask/P*/path/return path)
  → onFishPathSnapshot callback
  → YujianApp.revealSessionStore.updateFishPathSnapshot
```

Evidence: `RecognizingScreen.kt:900-1001,2038-2045,2074-2123,3420-3435`; `RecognitionSceneAnimationController.kt:939-1003`; `WaterLightPathLedger.kt:241-283,396-440`; `YujianApp.kt:1217-1239`; `RecognitionRevealSessionStore.kt` path binding/update methods (see source map).

The RETURN projection consumes the ledger snapshot's original path ID, mask identity, P*, geometry version, and segments. The Store accepts a full snapshot after the controller accepted the path event. **Missing V2 ordering edge:** Store does not accept a distinct `PATH_BOUND` receipt before the ledger constructs/freezes the FishPath snapshot; Store's first path-specific acceptance is `updateFishPathSnapshot`.

## 4. FishPath → result data view → timeline

```text
revealSessionStore.updateFishPathSnapshot
  → mutable RecognitionRevealSessionStore state
  → RecognitionResultNavigationReadiness.canNavigate
  → App navigates / attaches Result route once classifier + decision + path branch is navigable
  → RecognitionRevealSessionStore.resultSnapshot(key)
  → constructs RecognitionResultSnapshot on demand from current store state
  → RecognitionResultScreen receives resultSnapshot + RecognitionResultHandoffState

RecognitionSceneAnimationController.markPhotoFirstFramePresented
  → PHOTO_STABLE + scene clock
  → one of 13 event-gated RecognitionSceneStage values
  → shared RecognitionSceneFrame to WaterLight / Orb / result consumers
```

Evidence: `RecognitionResultRuntime.kt:32-45,132-151`; `RecognitionRevealSessionStore.kt:1129-1158`; `RecognitionSceneAnimationController.kt:240-261,1071-1129`; `RecognitionSceneFrame.kt:7-20,267-337`; `YujianApp.kt:594-615,1322-1440`.

`RecognitionResultSnapshot` is a data class but is not a V2 committed fact: it has no `snapshotId` or content hash, and the store can construct a new view after store state changes. The 13 stage enum and 6000ms minima exist; no named `ModelClock`/`MotionClock` types or device timing trace establish all-clock conformance.

## 5. Snapshot → Orb-to-Glyph → NAME_STABLE

```text
Result route composition
  → RecognitionResultComponents.Text
  → Compose onTextLayout
  → TextLayoutResult.getPathForRange(species name)
  → RecognitionGlyphPathInspector.inspect(identity, species, Path)
  → local speciesGlyphPath / descriptor state
  → RecognitionRevealOpticalRenderer / SpeciesNameReveal draws paths
  → drawWithContent builds RecognitionGlyphDrawReceipt(drawPassSequence)
  → handler invokes onGlyphRevealRendered
  → RecognitionGlyphReadinessContract.isRendered
  → RecognitionResultHandoffMilestoneEffects emits NAME_STABLE
  → RecognitionResultHandoffConsumer
```

Evidence: `RecognitionResultComponents.kt:1055-1135`; `RecognitionResultRuntime.kt:64-129`; `RecognitionResultRuntimeContracts.kt:194-250`; `RecognitionResultHandoffMilestoneEffects.kt:121-143`.

The path is a real font-derived Compose glyph outline, and the Renderer draws it. The required shared, pre-navigation `GlyphPathProvider`/`GlyphPathSnapshot` is **MISSING**. The receipt proves a draw pass (`drawPassSequence > 0`), not `FRAME_PRESENTED` or PixelCopy. This is the main Orb-to-Glyph conformance break.

## 6. Result geometry → Photo Settle → Handoff ACK → scene ownership

```text
Result UI onGloballyPositioned / reveal geometry resolver
  → readiness booleans + geometryVersion
  → Result UI emits ordered RecognitionResultHandoffEvent
  → RecognitionResultHandoffConsumer.consume on Main
      → SessionStore.canAcceptResultHandoffEvent
      → Controller.canAcceptResultHandoffEvent
      → Controller.acceptResultHandoffEvent
      → SessionStore.recordResultHandoffEvent
      → RecognitionResultHandoffReceipt
  → 13.1 NAME_STABLE
  → 13.2 PHOTO_SETTLE
  → 13.3 RESULT_SCENE_HANDOFF
  → 13.4 RESULT_INFO_ARRIVAL
  → 13.5 RESULT_SCENE_STABLE
```

Evidence: `RecognitionResultHandoffConsumer.kt:8-45`; `RecognitionRevealSessionStore.kt:905-990`; `RecognitionSceneAnimationController.kt:599-701`; `RecognitionResultRuntimeContracts.kt:257-287`; `RecognitionResultHandoffMilestoneEffects.kt:121-170`; `YujianApp.kt:1442-1475`.

Positive: same attempt/event sequence, monotonic timestamps, route attachment, geometry version, result species, Glyph readiness, path identity and ordered substage checks are wired into production acceptance. A real consumer ACK is returned. Partial: no `handoffId`, `snapshotId`/hash, or standalone `ResultGeometryReceipt` / `PhotoSettleReceipt`; readiness is represented by booleans and event geometry version. No independent pre-mounted Result Host receipt was found; Result route is attached by App navigation before Result UI emits these milestones. Host-mount and scene ownership are not represented as separately identified receipts.

## 7. Result state → Evidence recorder → ZIP → validator

```text
RecognizingScreen drawWithContent
  → RecognitionSessionEvidenceRecorder.onFramePresented
  → semantic KF policy / PixelCopy capture request (DEBUG build)
  → identity + frame sequence + geometry index and event logs
  → RecognitionEvidenceZipExporter.export
  → summary + manifest + per-file SHA256
  → ZIP creation
  → RecognitionEvidenceArchiveVerifier.verify
  → finalized archive / degraded status
```

Evidence: `RecognizingScreen.kt:1767-1778`; `RecognitionSessionEvidenceRecorder.kt:560-638,881-920`; `RecognitionEvidenceZipExporter.kt:145-185,535-545`; `RecognitionEvidenceArchiveVerifier.kt:10-27,41-75`.

Missing: real screen video recorder/MP4; ORT operator profile and native allocator attribution; a structured `FIRST_FAILURE` record; comprehensive event schema/identity/monotonic/content validation in the archive verifier. The Android runtime instrumentation test can write screenshots and memory counters, but no test was executed for this audit.

## Confirmed structural wait points (not historical root-cause proof)

`RecognitionSceneAnimationController.currentSceneStage` holds the current stage when the minimum duration has elapsed but `canAdvance` is false. `waitingReason` reports concrete missing events such as `WAITING_FOR_RENDERED_RELEASE_EVENT`, `WAITING_FOR_MASK_BOUND_PSTAR_ABSORB_EVENT`, `WAITING_FOR_REAL_CLASSIFIER_GLYPH`, `WAITING_FOR_RESULT_GEOMETRY`, or `WAITING_FOR_RESULT_SCENE_STABLE`. A P* branch has a deadline fallback in `RecognizingScreen`; geometry, render receipt, and result substage waits do not share a general fail/degrade deadline. A checked-in historical fixture (source commit `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`, not this audit SHA) records Stage10 blocked at 23,027ms: no RETURN render receipt/completion, no Glyph readiness, Result geometry PENDING; a zero-segment return event was rejected `EVENT_STAGE_MISMATCH`. It later records geometry `LAYOUT_ROOT_PENDING` then `RESULT_HERO_PENDING`, and first Result presentation at 60,006ms. This establishes those observed gates/rejection for that fixture only. It does not expose the original first producer failure, SessionStore binding, navigation, full model replay, or video. Current-source root causation for that old run remains unconfirmed.
