# Source module map

**Audited source:** `pan277942135/Yujian_App` at `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10`  
**Scope:** all eight Gradle modules from `settings.gradle.kts`, their project dependencies, primary production entrypoints, and targeted tests for the six contracts.

## Gradle modules and dependency direction

`settings.gradle.kts:7-16` includes all modules below. Project dependencies are read from module `build.gradle.kts` files.

| Module | Responsibility | Main evidence / entrypoints | Project dependencies |
|---|---|---|---|
| `recognition-domain` | Recognition phase, image transform, normalized box, subject status shared types | `src/main/java/com/yujian/ai/ai/RecognitionPhase.kt`; `.../ui/identify/RecognitionImageTransform.kt`; `.../ai/subject/SubjectStatus.kt` | none |
| `recognition-runtime` | Scene frame/stage controller, handoff contracts, waterlight path ledger, timing/event types | `.../waterlight/core/RecognitionSceneAnimationController.kt`; `RecognitionSceneFrame.kt`; `WaterLightPathLedger.kt`; `.../recognition/reveal/RecognitionResultRuntimeContracts.kt` | `recognition-domain` |
| `waterlight-renderer` | Orb, resident/task energy and WaterLight visual renderers | `.../ui/waterlight/OrbFilamentRenderer.kt`; `OrbResidentSystemsV1.kt`; renderer/profile implementations | `recognition-domain`, `recognition-runtime` |
| `application-core` | Detector, classifier, pipeline, SlimSAM segmenter/runtime manager, attempt deadline | `.../ai/FishRecognitionPipeline.kt`; `FishDetectorEngine.kt`; `FishRecognitionEngine.kt`; `.../subject/SlimSamSegmenter.kt`; `SlimSamRuntimeManager.kt`; `RecognitionAttemptDeadline.kt` | `recognition-domain` |
| `recognition-ui` | Capture/processing screen, contour and reveal geometry, evidence recorder/export | `.../screens/RecognizingScreen.kt`; `RecognitionMaskContourExtractor.kt`; `.../reveal/RecognitionRevealSessionStore.kt`; `RecognitionResultRuntime.kt`; `.../diagnostics/RecognitionSessionEvidenceRecorder.kt`; `RecognitionPixelCopyCapture.kt`; `RecognitionEvidenceZipExporter.kt`; `RecognitionEvidenceArchiveVerifier.kt` | `application-core`, `recognition-domain`, `recognition-runtime`, `waterlight-renderer` |
| `recognition-result-components` | Result content, Glyph drawing, result milestones and producer events | `.../screens/RecognitionResultComponents.kt`; `RecognitionResultRevealCoordinator.kt`; `RecognitionResultHandoffMilestoneEffects.kt` | `application-core`, `recognition-domain`, `recognition-runtime`, `recognition-ui` |
| `recognition-result-ui` | Result screen, measured Result UI and save/checkpoint action | `.../screens/RecognitionResultScreen.kt` | `application-core`, `recognition-domain`, `recognition-runtime`, `recognition-ui`, `recognition-result-components` |
| `app` | Android Application, Activity, navigation, object ownership, real callback wiring | `YujianApplication.kt`; `MainActivity.kt`; `YujianApp.kt`; `media/RecognitionImageStore.kt` | `application-core`, `recognition-domain`, `recognition-runtime`, `waterlight-renderer`, `recognition-ui`, `recognition-result-ui` |

## Production call path by domain

### 1. Application / App Entry

- `YujianApplication.onCreate` creates and starts the app-owned `SlimSamRuntimeManager`; `onTrimMemory` forwards memory pressure.
- `YujianApp` owns `FishRecognitionPipeline`, `RecognitionRevealSessionStore`, recognition session/attempt state, navigation, and evidence recorder. The pipeline and subject model manager are closed when the App composition is disposed, not by Result route navigation.
- `MainActivity` launches the Compose App host.

### 2. Photo and attempt identity

- `RecognitionImageStore.persistNormalized` normalizes EXIF orientation, performs camera-only crop when needed, writes normalized JPEG quality 92, and sets `SelectedImage.photoSha256` to that file's byte hash.
- `Models.kt` carries `imageId`, `photoSha256`, and `bitmap`; it does not carry both `sourceBytesSha256` and `canonicalPixelSha256`.
- `RecognitionAttemptDeadline` supplies one 60-second monotonic deadline to detector/classifier/SlimSAM attempt work. `RecognizingScreen` checks remaining time and current attempt before callback commit.

### 3. Models and subject geometry

- `FishRecognitionPipeline.recognize` calls detector, quality gate, crop, then classifier. Detector/classifier read/check model assets and expose model hash/schema metadata, but run events are not assigned the full V2 per-run `modelSessionId + inferenceId + schemaHash`.
- `SlimSamRuntimeManager.initialize` verifies artifacts, creates encoder and decoder ONNX sessions, validates their actual `TensorInfo`, and exposes READY only when both are ready. `SlimSamSegmenter` has synchronous `session.run` calls and a one-entry embedding cache.
- `RecognizingScreen` handles terminal mask results, validates mask hash/dimensions/geometry and contour trust, derives contact point P*, and feeds the scene/path ledger.

### 4. Scene / Orb / WaterLight

- `RecognitionSceneAnimationController` is the stage authority. `markPhotoFirstFramePresented` starts `PHOTO_STABLE`; `currentSceneStage` advances one stage only after stage minimum and `canAdvance` event gates.
- `RecognitionSceneFrame` carries identity, stage, stage progress, scene elapsed, optical reference elapsed, geometry and handoff state.
- `WaterLightPathLedger` records the release/perimeter/fish/return route. `waterlight-renderer` draws Orb/Residents/Task Energy; `RecognitionRevealOpticalRenderer` and `SpeciesNameReveal` draw the Orb-to-Glyph transition.

### 5. Result Screen and Handoff

- The App calls `retryReadyRecognitionHandoff` when result, photo, path, or geometry state becomes available. Result UI sends `RecognitionResultHandoffEvent` to `RecognitionResultHandoffConsumer`.
- Consumer requires Main thread and preflights both Controller and Store before synchronous acceptance. The five result substages are stored in order, and final stable state closes the scene stage.
- Current Result route does not expose separate `ResultGeometryReceipt`, `PhotoSettleReceipt`, `snapshotId`, `handoffId`, or ownership-transfer identity fields.

### 6. Evidence

- `RecognitionSessionEvidenceRecorder` reads controller-authored events and frame state. In debug, its PixelCopy checkpoint callback can hold stage advancement until capture completion.
- `RecognitionEvidenceZipExporter` writes logs/reports, manifest and hashes, packages the ZIP and invokes `RecognitionEvidenceArchiveVerifier`. Missing expected log/report files are synthesized as empty JSONL or `{}` placeholders, although export integrity can then be marked degraded.
- No production video recorder, MP4 validator, ORT operator profiler, or native allocator attribution implementation was found in the audited Kotlin/Java source tree.

## Contract-specific producer / consumer map

| Producer | Consumer | Wiring result |
|---|---|---|
| `FishRecognitionPipeline` detector/classifier | `YujianApp` / `RecognizingScreen` | Integrated; callback identity guards exist; per-run IDs/schema trace incomplete |
| `SlimSamRuntimeManager` | `RecognizingScreen` subject attempt | Integrated and application-owned; both sessions required for READY; native termination incomplete |
| mask/contour/P* + `WaterLightPathLedger` | `RecognitionSceneAnimationController` | Integrated with trusted mask and geometry/path checks |
| FishPath export | Controller, then App SessionStore | Integrated; Store accepts snapshot, but not a distinct earlier PATH_BOUND receipt |
| Result text layout Glyph path | Glyph renderer / controller readiness | Integrated after Result composition; pre-navigation shared provider missing |
| Result UI milestone producer | Controller + SessionStore consumer | Integrated; true ACK and sequence checks; V2 envelope identities and presented-frame proof incomplete |
| stage/frame/evidence producers | `RecognitionSessionEvidenceRecorder` | Integrated; debug capture can gate stage progress |
| PixelCopy | checkpoint frame index | Integrated as debug capture; no current device capture evidence in this audit |
| video recorder | Evidence ZIP | **MISSING** |
| ORT profile + allocator attribution | independent evidence validator | **MISSING** |
| ZIP contents | archive verifier | Internal hash/index checks present; full independent schema/content validator incomplete |
| AUTO runner | acceptance matrix | Android instrumentation flows exist; no audit run artifact or full layered exact-APK acceptance result |

## Test locations

Relevant source tests are under `application-core/src/test`, `recognition-runtime/src/test`, `recognition-ui/src/test`, `recognition-result-ui/src/test`, and `waterlight-renderer/src/test`. Android/device-oriented flows are under `app/src/androidTest` and `recognition-ui/src/androidTest`. A curated list and coverage boundary are in `04_TEST_AND_EVIDENCE_INDEX.md`.
