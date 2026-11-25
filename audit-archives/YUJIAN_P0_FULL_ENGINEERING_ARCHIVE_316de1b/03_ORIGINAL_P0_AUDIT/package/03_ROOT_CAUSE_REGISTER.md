# Root cause register

**Source baseline:** `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10`  
**Interpretation:** `ROOT_CAUSE_CONFIRMED` means the source-level reason for a contract gap is directly visible. It does not prove a historical device incident took the same path. Historical incident causation remains unconfirmed without its runtime evidence.

## P0 — system correctness / acceptance blockers

### P0-01 — Photo identity collapses encoded bytes and canonical pixels

- **Contract:** C1 §§2,4; C2 §§2.2–2.3,6; C09; A02–A07; A14–A19.
- **Problem / trigger:** Every normal capture or gallery input is normalized and saved as JPEG. If source bytes and decoded pixels need separate identities, they cannot be reconstructed from the one stored hash.
- **Expected:** Persist `sourceBytesSha256` and a deterministic `canonicalPixelSha256`; bind model/mask/contour/P*/path/snapshot to canonical pixels.
- **Actual:** `RecognitionImageStore.persistNormalized` writes JPEG quality 92 and hashes the file bytes into `SelectedImage.photoSha256` (`app/src/main/java/com/yujian/ai/media/RecognitionImageStore.kt:140-187`). `Models.kt:20-38` exposes this as one identity. Downstream model identity and scene identity carry the same `photoSha256`.
- **First failing source point:** The identity is collapsed at the normalized-JPEG hash assignment, lines 170-185.
- **Producer → consumer / path:** `RecognitionImageStore.persistNormalized` → `SelectedImage.photoSha256` → `FishRecognitionPipeline` / `SlimSamSegmenter` → subject geometry and path ledger → `RecognitionSceneIdentity` → SessionStore and Result handoff.
- **Root cause:** `SelectedImage` has one field named `photoSha256`; no source-byte or canonical-pixel digest is computed or propagated. `RecognitionImageStore` hashes encoded JPEG bytes, not canonical decoded pixels.
- **Confidence / status:** `ROOT_CAUSE_CONFIRMED`; `SOURCE_CODE_VERIFIED`.
- **Impact:** Cross-layer “same photo” checks prove same normalized file bytes only. The V2 identity contract cannot be met, and content hashes change with JPEG encoding even for visually equal pixels.
- **Tests:** `RecognitionImageStoreTest`, `RecognitionExifOrientationTest`, `RecognitionImageTransformTest` cover normalization/orientation; they do not establish both V2 hashes. Not run.
- **Missing evidence:** Runtime logs carrying both identities and an APK/device trace binding model inputs to canonical pixel hash.
- **Fix direction:** Add a single canonical photo-identity value at ingest with both digests and dimensions/normalization version; pass it through model, mask, contour, path, snapshots, handoff, and evidence. Do not overload the JPEG hash field.
- **Follow-up tests:** Same source bytes with normalization variants; distinct JPEG encodings of same pixels; EXIF variants resolving to same canonical pixels; stale attempt/photo rejection at each consumer.

### P0-02 — Result snapshot is a mutable-store view, not a committed V2 identity

- **Contract:** C2 §§3.2,4–6; C06/C13; A07; handoff/evidence consumers.
- **Problem / trigger:** The Result consumer requests a snapshot while the shared SessionStore remains mutable across milestones and navigation.
- **Expected:** One committed immutable `RecognitionResultSnapshot` for the Attempt with `snapshotId` and canonical content hash, created only after model/mask/path branch is terminal; downstream receipts reference that identity.
- **Actual:** `RecognitionResultSnapshot` is a shallow `val` data class (`recognition-ui/.../RecognitionResultRuntime.kt:132-151`) but carries `SelectedImage`/mutable Bitmap references; `RecognitionRevealSessionStore.resultSnapshot` constructs it on demand from current store state (`.../RecognitionRevealSessionStore.kt:1129-1158`). It contains no `snapshotId` or `contentHash`; the mutable store separately changes handoff status, geometry, receipts, and stability.
- **First failing source point:** `resultSnapshot(key)` returns a new view without an explicit commit transition or content identity, lines 1129-1158.
- **Producer → consumer / path:** model/subject callbacks → mutable `RecognitionRevealSessionStore` → `resultSnapshot(key)` → `RecognitionResultScreen`, Timeline, Handoff, Evidence.
- **Root cause:** Current “snapshot” is a presentation projection of mutable session state. No commit-once object/hash is present in the production data flow; generic photo/attempt IDs stand in for the absent committed result identity.
- **Confidence / status:** `ROOT_CAUSE_CONFIRMED`; `SOURCE_CODE_VERIFIED`.
- **Impact:** Consumers cannot prove they read the same immutable result fact, and a receipt cannot bind to a stable snapshot revision. This weakens idempotency and late-result rejection across route changes.
- **Tests:** `RecognitionResultRuntimeTest`, `RecognitionFishPathSnapshotBindingTest`, and `RecognitionResultHandoffGuardrailRuntimeTest` cover route readiness, path identity, and event order, but not commit-once content hash. Not run.
- **Missing evidence:** Serialized snapshot fixture with version/hash and two-consumer identity checks.
- **Fix direction:** Introduce a separate commit operation and immutable V2 snapshot schema containing canonical photo/model/mask/contour/path identities, branch, `snapshotId`, and deterministic `contentHash`; make UI status and receipts reference that ID without mutating the committed object.
- **Follow-up tests:** Duplicate commit rejection; same Attempt immutable after UI events; late model/mask callback creates/rejects a new Attempt; canonical serialization and hash stability.


### P0-03 — Glyph draw submission is treated as rendered / NAME_STABLE

- **Contract:** C3 §§4,7; C4 §§8–9; C19–C20; A10, A13, A16–A17.
- **Problem / trigger:** A draw callback reaches full progress, but frame presentation can still be delayed, dropped, occluded, or differ from the submitted canvas.
- **Expected:** Distinct `DRAW_SUBMITTED`, `FRAME_PRESENTED`, `PIXELCOPY_CAPTURED`, and `VISUAL_VALIDATED`; NAME_STABLE requires visible glyph evidence at the relevant frame.
- **Actual:** `RecognitionGlyphDrawReceipt` carries path, progress, fill/contour flags and `drawPassSequence` (`recognition-runtime/.../RecognitionResultRuntimeContracts.kt:194-204`). `isRendered` accepts `drawPassSequence > 0` (`:228-243`). The receipt is created within `drawWithContent` and posted to a Handler (`RecognitionResultComponents.kt:1059-1097`); `RecognitionResultHandoffMilestoneEffects` then emits NAME_STABLE (`.../RecognitionResultHandoffMilestoneEffects.kt:121-143`).
- **First failing source point:** `isRendered` equates completed draw pass with rendered output, `RecognitionResultRuntimeContracts.kt:228-243`.
- **Producer → consumer / path:** Compose draw pass → `RecognitionGlyphDrawReceipt` → glyph readiness → milestone producer → Controller/SessionStore acceptance → KF20 / later stage.
- **Root cause:** Receipt schema has no frame sequence/present timestamp/PixelCopy identity; acceptance gate only requires positive draw-pass sequence.
- **Confidence / status:** `ROOT_CAUSE_CONFIRMED`; `SOURCE_CODE_VERIFIED`.
- **Impact:** A downstream business/timeline milestone can be accepted without proof that the named glyph was visible. Visual PASS could be inferred from a canvas submission.
- **Tests:** `RecognitionResultRuntimeTest` verifies draw-receipt contract; `RecognitionResultHandoffGuardrailRuntimeTest` verifies stale/timeout guards. They do not prove a platform-presented frame. Not run.
- **Missing evidence:** Actual device FramePresented/PixelCopy receipt tied to attempt, snapshot and KF20.
- **Fix direction:** Add an explicit platform presentation/frame receipt and correlate PixelCopy by frame sequence. Keep draw submission as a separate state; do not use it to set `glyphRevealRendered` or NAME_STABLE.
- **Follow-up tests:** Dropped-frame and stale-frame simulation; mismatched PixelCopy/KF rejection; NAME_STABLE stays blocked until present receipt.

### P0-04 — Attempt timeout/cancellation does not terminate a synchronous native inference

- **Contract:** C1 §§3.2–3.3,6.1–6.2; C16–C17; A01/A03/A04/A15.
- **Problem / trigger:** A Vision or Decoder `session.run` call exceeds the shared 60s deadline or its coroutine is cancelled while native code is executing.
- **Expected:** Track cancel request, ORT termination request, native return, session close, and resource release separately; quarantine a stuck Session/Attempt and prevent unsafe reuse/release.
- **Actual:** `RecognitionAttemptDeadline` explicitly says coroutine cancellation cannot forcibly stop synchronous native inference (`application-core/.../RecognitionAttemptDeadline.kt:3-6`). `SlimSamSegmenter` calls `session.run` / `decoder.run` synchronously without `RunOptions` or termination request (`.../SlimSamSegmenter.kt:598-612,711-730`). `SlimSamRuntimeManager.closeForProcessTermination` cancels/joins preload then waits on `operationMutex` before closing sessions (`.../SlimSamRuntimeManager.kt:287-299`).
- **First failing source point:** synchronous ORT call without a termination/call-state wrapper; Vision call at `SlimSamSegmenter.kt:611`.
- **Producer → consumer / path:** `RecognizingScreen.withTimeout(remaining)` → cancellation reaches segment coroutine → synchronous ORT call remains running until native return → manager operation lock/session lifecycle.
- **Root cause:** Deadline is implemented at coroutine/attempt boundary but there is no native termination mechanism, independent process boundary, stuck state, or quarantine/recovery policy. Existing close path waits rather than terminating a native run.
- **Confidence / status:** `ROOT_CAUSE_CONFIRMED` as a missing guarantee; whether it caused reported device hangs is `ROOT_CAUSE_UNCONFIRMED`.
- **Impact:** A native hang can exceed 60 seconds, delay subsequent serialized work, and prevent close/retry. The app cannot truthfully report native termination from a coroutine timeout.
- **Tests:** `RecognitionAttemptDeadlineTest`, `SlimSamRuntimeCoordinatorTest.cancelledOldPhotoCannotReplaceTheActivePhotosEmbedding`, `RecognitionRevealProductionRuntimeTest` cancellation workload. None provides a real stuck-native termination proof. Not run.
- **Missing evidence:** ORT version-specific termination capability, a blocked-native reproduction, process isolation/quarantine trace, and resource lifetime proof.
- **Fix direction:** Use supported per-run ORT termination where available; add explicit run state/identity and await native return before releasing tensors. Add quarantine and bounded recovery for stuck native work; if hard stop is required, use the contract's process boundary strategy.
- **Follow-up tests:** Cancel during encoder/decoder; native still active past deadline; late result rejected; tensor/session close delayed until native return; quarantine and next-attempt recovery.

## P1 — important reliability / observability gaps

### P1-01 — Embedding cache key omits model SHA and schema/shape identity

- **Contract:** C1 §5.2; C10; A03.
- **Trigger / expected:** A cached embedding should only be reused for the same canonical pixels, encoder artifact, preprocess version and input schema/shape.
- **Actual / first point:** `SlimSamEmbeddingCacheKey` contains photo SHA, width/height, modelVersion, preprocessingVersion (`SlimSamRuntimeCoordinator.kt:83-104`); `SlimSamSegmenter` populates those fields and falls back to bitmap `generationId` when photo SHA is blank (`SlimSamSegmenter.kt:498-507`). Encoder SHA, input schema hash and input shape are absent.
- **Producer → consumer:** encoder writes `SlimSamEmbedding` → one-entry cache `get/putIfActive` → Decoder input tensors.
- **Root cause / confidence:** cache identity schema omits required model and schema keys; source-confirmed. `ROOT_CAUSE_CONFIRMED`.
- **Impact:** A model/preprocess/schema replacement that does not change `MODEL_VERSION` can reuse incompatible embedding; blank-photo fallback is not persistent identity.
- **Tests:** `SlimSamRuntimeCoordinatorTest.embeddingCacheIdentityUsesPhotoContentGeometryAndModelContracts` currently tests the smaller key. Not run.
- **Fix / follow-up:** Add canonical pixel SHA, encoder model SHA, preprocess version, input schema hash and actual input shape; reject noncanonical fallback; test key invalidation per field.

### P1-02 — Model calls lack full run identity and per-run IO receipt

- **Contract:** C1 §§2,3.2,4,7; C08; A01–A04/A14–A15.
- **Trigger / expected:** Each detector/classifier/encoder/decoder run has unique `modelSessionId` and `inferenceId`, model ID/version/SHA, input/schema/preprocess, output schema/summary, monotonic start/end and terminal state.
- **Actual / first point:** `RecognitionModelIdentity` has only recognition `sessionId`, `attemptId`, `imageId`, `photoSha256` (`RecognitionModelWatchdog.kt:8-12`). SlimSAM events identify attempt/photo/modelVersion and timings (`SlimSamRuntimeManager.kt:54-70`) but no run ID; result logs do not provide a single validated input/output record for all four models. Detector/classifier do read asset metadata, and SlimSAM READY is based on both actual sessions; these are partial positives.
- **Producer → consumer:** Model engines/manager → pipeline and Evidence Event Journal.
- **Root cause / confidence:** current diagnostics wrap calls but do not establish V2 run-identity/receipt schema; `ROOT_CAUSE_CONFIRMED`.
- **Impact:** Cannot correlate retries, tensor ownership, late native returns and evidence to one model run or prove all required model outputs belong to the committed result.
- **Tests:** `RecognitionModelWatchdogTest`, `RecognitionRuntimeAssetContractTest`, `InferenceContractTest`; not run.
- **Fix / follow-up:** Add per-session/per-inference identity and immutable run receipt at each engine boundary; test model-by-model identity and output schema.

### P1-03 — Store does not accept PATH_BOUND before FishPath snapshot creation

- **Contract:** C2 §3.1; C3 §7; C06–C07; A06.
- **Expected:** Stage05 Controller and SessionStore both accept the same PATH_BOUND identity before the immutable FishPath snapshot is created; Stage10 only consumes it.
- **Actual:** Controller binds path in `onFishContactPathAvailable` (`RecognitionSceneAnimationController.kt:939-980`); RecognizingScreen builds/export snapshot before controller accepts snapshot and before App Store callback (`RecognizingScreen.kt:3420-3435,2038-2123`). The Controller then accepts the snapshot, then `YujianApp` invokes `updateFishPathSnapshot` (`YujianApp.kt:1217-1239`). No separate Store PATH_BOUND acceptance exists before export.
- **Root cause / confidence:** Store's first path-specific event is the snapshot itself; `ROOT_CAUSE_CONFIRMED` for ordering mismatch. No actual lost-path incident proven.
- **Impact:** The exact V2 two-owner commit order cannot be audited or acknowledged; current guard still rejects mismatched snapshot identity, and Stage10 uses the original ledger route.
- **Tests:** `RecognitionFishPathSnapshotBindingTest`, `WaterLightPathLedgerTest`, `RecognitionResultRuntimeTest.fullRevealReturnProjection...`; not run.
- **Fix / follow-up:** Introduce a PATH_BOUND receipt in Store and freeze/export only after both owners accept; test consumer loss/retry and Stage10 non-recreation.

### P1-04 — KF25 untriggered state remains NOT_REACHED

- **Contract:** C3 §4; C21; A14/A16/A18.
- **Expected:** Always write 26 rows; no save tap → KF25 `NOT_APPLICABLE`.
- **Actual / first point:** `beginAttempt` initializes all IDs `NOT_REACHED` (`RecognitionSessionEvidenceRecorder.kt:88-113`). `checkpointStatusAfterExport` explicitly returns KF25 unchanged when still `NOT_REACHED` (`RecognitionEvidencePolicies.kt:157-171`). Save outcome logging records a diagnostic but does not rewrite checkpoint status (`RecognitionSessionEvidenceRecorder.kt:481-507`).
- **Producer → consumer:** Result save action requests KF25 capture → evidence recorder checkpoint row → exported 26KF index.
- **Root cause / confidence:** conditional checkpoint lifecycle is not represented in post-export status policy; `ROOT_CAUSE_CONFIRMED`.
- **Impact:** 26-row output is present but semantically wrong for a run without user save action; acceptance validator cannot distinguish “not applicable” from missed capture.
- **Tests:** `RecognitionSessionEvidenceRecorderTest.reachedCheckpointWithoutPresentedEventIsMissingButUnreachedStageStaysUnreached` explicitly asserts the untriggered optional-save KF25 remains `NOT_REACHED` (test source around lines 171-185), encoding the mismatch. Not run.
- **Fix / follow-up:** Mark KF25 `NOT_APPLICABLE` unless a real save action arms it; if armed but not captured, retain explicit failure. Add tests for no tap, tap, save fail, capture fail.

### P1-05 — Result geometry, Host pre-mount, and Handoff envelope lack V2 receipts/IDs

- **Contract:** C2 §§3.2,5; C5 §§5–9; C13–C14; A11–A13.
- **Expected:** Independent `ResultGeometryReceipt`, `PhotoSettleReceipt`, HostMounted/GeometryReady states, `HandoffEnvelope` with snapshot/handoff IDs, and ACK after sequence/identity validation. Host mounting must not equal scene ownership.
- **Actual:** `RecognitionResultGeometryReadiness` returns a conjunction of booleans (`RecognitionResultRuntime.kt:20-29`). Event contains geometry version but no `snapshotId`/hash or `handoffId` (`RecognitionResultRuntimeContracts.kt:257-281`). A real consumer ACK and ordered 13.1–13.5 checks do exist (`RecognitionResultHandoffConsumer.kt:8-45`; Controller/Store checks). No separate pre-mounted Host receipt was located; App route attaches Result before Result UI emits milestones.
- **Root cause / confidence:** V2 receipt/envelope schema and owner-transfer ID are absent; `ROOT_CAUSE_CONFIRMED` for contract gap. Actual visible duplicate-owner failure not demonstrated.
- **Impact:** UI readiness and screen-space/pixel readiness are not distinguishable in evidence; acknowledgements cannot reference one committed snapshot.
- **Tests:** `RecognitionResultRuntimeTest.resultInfoAndStableMilestonesRequireMeasuredDrawReceipts`, `RecognitionResultHandoffProductionNavigationTest`; not run.
- **Fix / follow-up:** Add independent identity-bearing geometry, photo settle, host mount, ownership-transfer, envelope and ACK records; test layout readiness versus raster/frame readiness and single visible owner.

### P1-06 — Debug PixelCopy backpressure controls stage advancement

- **Contract:** C6 §1; C23; A14/A16.
- **Expected:** Evidence subscribes read-only; it must not advance, complete, or gate business/timeline state.
- **Actual:** `RecognizingScreen` calls recorder after `drawWithContent` and wires `onCheckpointCaptureGateChanged` to `controller.setVisualCheckpointCapturePending` (`RecognizingScreen.kt:1767-1778`). The recorder sets this gate while PixelCopy is pending (`RecognitionSessionEvidenceRecorder.kt:560-595`); the controller refuses stage advancement while that field matches the current stage (`RecognitionSceneAnimationController.kt:1056-1069,1083-1084`). Capture is enabled only when `BuildConfig.DEBUG` is true.
- **Root cause / confidence:** the debug evidence pipeline deliberately uses screenshot completion as a stage hold; source-confirmed. `ROOT_CAUSE_CONFIRMED`.
- **Impact:** debug scene timing depends on evidence capture latency, so diagnostic playback is not timing-neutral. Release capture is disabled by the call-site flag.
- **Tests:** `RecognitionSessionEvidenceRecorderTest` and `RecognitionVisualStateControllerTest` exercise checkpoint/gate behavior; not run.
- **Fix / follow-up:** Remove evidence-to-controller gate or make capture observe a stable copied frame without controlling stage state; test that injected capture delay leaves stage timestamps unchanged.

### P1-07 — Runtime memory counters exist; operator attribution does not

- **Contract:** C1 §7; C18; C6 §4; A15.
- **Expected:** Separate RSS/PSS, native heap, allocator/call-stack association, ORT operator profiling and only claim operator allocation attribution with linked allocator evidence.
- **Actual:** Runtime instrumentation source samples `Debug.MemoryInfo`, native heap, RSS/PSS and embedding/session counters (`app/src/androidTest/java/com/yujian/ai/RecognitionRevealProductionRuntimeTest.kt:150-180,1543-1555`). It can write memory evidence, but no production ORT operator profile or native allocator/call-stack attribution source was found. No claim that a named operator owns bytes is supported.
- **Root cause / confidence:** telemetry stops at process/heap counters and does not enable/attach ORT allocator profile; `ROOT_CAUSE_CONFIRMED` for the missing attribution layer.
- **Impact:** total memory changes can be measured by an instrumented run, but cannot be assigned to Attention/MatMul/Softmax or another operator.
- **Tests:** `RecognitionRevealProductionRuntimeTest` has source for memory sampling and workload evidence; not run. No `MEMORY_PROFILE_VERIFIED` result exists for this audit.
- **Fix / follow-up:** Record actual ORT provider/options/profile and native allocator traces with run IDs; retain separate labels for PSS/heap/operator-time/allocation attribution; validate on target device.

### P1-08 — ZIP verification checks integrity/indexes, not all evidence semantics

- **Contract:** C6 §§10,14; C20–C24; A18.
- **Expected:** ZIP workflow verifies CRC/extractability, all schemas, identity consistency, event ordering, checkpoint applicability, PixelCopy/video content, and finalization atomically. Missing files must be unavailable/not-run states, not synthesized evidence.
- **Actual:** Exporter writes empty JSONL and `{}` placeholders for missing expected files (`RecognitionEvidenceZipExporter.kt:535-545`), then hashes/packages and calls verifier (`:163-185`). Verifier checks exact ZIP entries, SHA, manifest bytes, screenshot indexes/encodings and optional original mask (`RecognitionEvidenceArchiveVerifier.kt:10-27,41-75`). It does not comprehensively validate every event schema or video content; no MP4 exists. Export integrity can be DEGRADED, which is a useful safeguard.
- **Root cause / confidence:** in-process verifier is narrow and placeholders are made before manifest; source-confirmed. `ROOT_CAUSE_CONFIRMED`.
- **Impact:** byte integrity can pass for files that are semantically empty; hash success is not evidence acceptance.
- **Tests:** `RecognitionEvidenceZipExporterTest` covers hashes, manifest, screenshot index, mask bytes, and degraded flows. Not run.
- **Fix / follow-up:** Do not fabricate files; write per-artifact availability; validate extracted schemas, monotonic identity chains, 26KF semantics, PixelCopy pixel stats, video decode/KF coverage, and finalization status in an independent validator.

### P1-09 — No Orb-to-Glyph video recorder / MP4 acceptance path

- **Contract:** C6 §8; C20; A17–A18.
- **Expected:** Device screen MP4 spans KF14–KF20 and associates video PTS with scene clock/KF; validate decodability and SHA.
- **Actual:** PixelCopy/checkpoint screenshots and instrumentation screenshots exist; repository-wide production-source search found no `MediaProjection`/`MediaCodec`/`MediaMuxer`-based Orb-to-Glyph recorder or MP4 validator. Existing `RecognitionRevealProductionRuntimeTest` writes screenshots/memory evidence but no video file.
- **Root cause / confidence:** no video producer/consumer exists in the audited Kotlin/Java tree; `SOURCE_CODE_ABSENCE_VERIFIED`, `ROOT_CAUSE_CONFIRMED` for missing implementation.
- **Impact:** Video acceptance is unavailable; still frames cannot prove continuous RETURN, deconstruction, migration, and glyph formation.
- **Tests:** instrumentation flows capture still images; no video test found. Not run.
- **Fix / follow-up:** Add screen-capture producer, timestamp/KF index, MP4 decode/content validator and archive binding; verify on device.

### P1-10 — FIRST_FAILURE and blocked-downstream root-cause record are absent

- **Contract:** C6 §12; C24; A14/A18.
- **Expected:** Persist first failure, last successful event, blocked downstream list, module, confidence and evidence references; downstream rows use `BLOCKED_BY_UPSTREAM_FAILURE`.
- **Actual:** Controller has concrete `waitingReason` and evidence records stage `BLOCKED`; the repository-wide source search did not find `FIRST_FAILURE`, `lastSuccessfulEvent`, or `BLOCKED_BY_UPSTREAM_FAILURE` event models/records. Last-stage/current wait diagnostics are not an ordered causal root record.
- **First relevant source point:** stage blocking is emitted at `RecognitionSceneAnimationController.kt:1089-1102`; evidence stores stage action/wait reason at `RecognitionSessionEvidenceRecorder.kt:145-173`, without first-failure accumulation.
- **Root cause / confidence:** recorder models events and latest waiting reasons but not first-failure causal classification; `SOURCE_CODE_ABSENCE_VERIFIED`.
- **Impact:** A downstream stall can be seen as a blocked stage, but the earliest causal event and dependent failures cannot be reconstructed from a standardized record.
- **Tests:** stage/evidence tests cover wait and export states; no first-failure test exists. Not run.
- **Fix / follow-up:** Add append-only causal failure record and downstream linkage at first rejection/timeout; test multiple cascading failures preserve the earliest root.

### P1-11 — Historical Stage10 fixture records a rejected return and unresolved geometry/Glyph gates

- **Contract:** C1 §§6,8; C3 §§6,7,9; C5 §§3,5,11; C6 §12.
- **Historical evidence source:** `recognition-ui/src/test/resources/recognition/stage10_failure_5344335d6a51.json` and `RecognitionStage10FailureFixtureTest.kt`. The fixture says it is deterministically derived from device JSONL at source commit `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`; it is not a full model inference replay and is not generated from the audited SHA.
- **Observed trace:** 9 stages completed; Stage10 (`ORB_RETURN`) entered at scene elapsed 22,444ms and was blocked at 23,027ms at progress 1.0. The fixture's unmet gates are `orbReturnRenderReceiptReady`, `orbReturnComplete`, `glyphAvailable`, and `resultGeometryReady`. Its last recorded return event had `pathSegmentCount=0`, path `photo-perimeter-to-original-orb`, and was rejected as `EVENT_STAGE_MISMATCH`; geometry mismatch was also rejected. Result geometry was `LAYOUT_ROOT_PENDING`, then `RESULT_HERO_PENDING`. First Result presentation was 60,006ms. The fixture marks SessionStore path binding and navigation `NOT_OBSERVABLE`, video unavailable, 10 checkpoints missing, 13 not reached, and 2 failed.
- **Current source path:** `RecognitionSceneAnimationController.recordEnergyEventWithReason` rejects an event whose stage differs from the frame/current stage (`recognition-runtime/.../RecognitionSceneAnimationController.kt:772-773`). `canAdvance(ORB_RETURN)` requires a return render receipt, completed return, Orb anchor, Glyph availability, and `SceneGeometryState.READY` (`:1466-1475`). `setResultGeometryState(PENDING)` freezes the current stage/scene clock until geometry changes (`:704-748`). On missing event after minimum duration, `currentSceneStage` stays at the stage and emits BLOCKED/wait reason (`:1083-1102`).
- **Expected:** Producer must submit the original non-empty Stage05 route on the active ORB_RETURN frame with current geometry; Controller accepts and records a real return render receipt; Result geometry and prepared Glyph arrive with the same Attempt identity; otherwise a bounded explicit failure identifies the first missing producer/consumer.
- **Actual:** The fixture proves the event was rejected and the four return gates were false for that prior run. Current source has matching gate structure. The fixture does **not** show which condition first failed upstream, and it cannot prove the same sequence occurred at the current exact SHA.
- **First explicit rejected event in the fixture:** `EVENT_STAGE_MISMATCH`, path segment count 0. The current rejection branch is at `recordEnergyEventWithReason` lines 772-773; a prior causal producer fault is not visible in the fixture.
- **Producer → consumer / call path:** `RecognizingScreen` path ledger / RETURN EnergyEvent → `RecognitionSceneAnimationController.recordEnergyEventWithReason` → `onOrbReturnRenderComplete` / `canAdvance(ORB_RETURN)` → `ORB_TO_NAME` and glyph gates. Result host geometry flows through Result UI readiness and `setResultGeometryState`.
- **Root cause / confidence:** Confirmed structural cause of the recorded stage hold: required return receipt/completion, Glyph, and geometry were absent; the RETURN event was rejected. The earliest upstream cause (wrong-stage event creation, missing route segments, delayed Result geometry, Glyph producer timing, or another prior event) is `ROOT_CAUSE_UNCONFIRMED`. The fixture's old commit prevents attribution to current SHA.
- **Priority / impact:** P1. The combination is sufficient to hold Stage10 and prevent Orb-to-Glyph progression; a model wait alone does not explain the recorded gate set.
- **Tests:** `RecognitionStage10FailureFixtureTest.fixturePreservesDeviceFactsWithoutInventingSessionStoreOrNavigationEvidence` asserts these fixture facts. It is a fixture test, not a live reproduction. Controller and Result guard tests cover the current individual gates. None were run in this audit.
- **Missing evidence:** Original raw JSONL, exact APK/source/model identities, accepted/rejected full event sequence, first failed producer, Store binding, navigation trace, PixelCopy, video, and a run on the audited SHA.
- **Fix direction:** Preserve the ledger path segments and identity through the RETURN producer; reject/log the precise expected/actual stage, geometry and path state; only emit on the current frame; connect geometry and Glyph readiness before the applicable gate; apply explicit per-gate deadlines and FIRST_FAILURE records.
- **Follow-up tests:** Replay the fixture facts against controller unit cases; inject zero-segment/wrong-stage/stale-geometry returns; delay layout root and Hero raster separately; withhold Glyph; verify first failure and bounded terminal outcome. Then run exact-HEAD device flow and collect the raw trace/video/PixelCopy archive.

### P1-12 — No exact-HEAD runtime/device evidence was produced in this audit

- **Contract:** C6 §§11,15; A01–A19; historical incident review.
- **Expected:** Distinct build/unit/model/runtime/KF/PixelCopy/video/archive/full-flow evidence tied to one exact APK/model/photo/attempt.
- **Actual:** Test source includes an Android production flow (`RecognitionRevealProductionRuntimeTest`), navigation tests, and frozen-flow emulator tests. They were inspected but not executed; no APK, emulator/device trace, screen recording, PixelCopy output set, or ZIP from this exact audit was present. Therefore no `DEVICE_REPRODUCED`, `RUNTIME_TRACE_VERIFIED`, or `FULL_FLOW_ACCEPTED` claim is made.
- **Root cause / confidence:** evidence absent from this audit workspace; `EVIDENCE_INSUFFICIENT`, not a product root-cause claim.
- **Impact:** implementation behavior under actual device timing/model/native runtime is unverified. Historical first failure cannot be named.
- **Tests:** listed in `04_TEST_AND_EVIDENCE_INDEX.md`; all `NOT_RUN` in this audit.
- **Fix / follow-up:** Use exact built APK/model/photo and run the existing production instrumentation flow, collect required artifacts, then independently validate all acceptance layers.

### P1-13 — Glyph path is prepared inside Result UI instead of before navigation

- **Contract:** C4 §§3,6,8–9; C11–C12; A09–A10.
- **Problem / trigger:** Orb-to-Glyph playback requires a glyph descriptor for the actual classifier name. The current producer runs when Result text is laid out.
- **Expected:** Shared `GlyphPathProvider` creates a `GlyphPathSnapshot` from the real font/name before Result navigation; Processing and Result consume the same path identity.
- **Actual:** `RecognitionResultComponents` obtains glyph geometry from `TextLayoutResult.getPathForRange` in `onTextLayout` and stores it in local Compose state (`recognition-result-components/.../RecognitionResultComponents.kt:1106-1135`). Navigation readiness is classifier/decision/path based, not Glyph-path based (`recognition-ui/.../RecognitionResultRuntime.kt:32-45`). No shared `GlyphPathProvider` or pre-navigation snapshot exists.
- **First failing source point:** Glyph producer is nested under Result `Text.onTextLayout`, lines 1106-1135; navigation can be accepted without that descriptor, `RecognitionResultRuntime.kt:32-45`.
- **Producer → consumer / path:** `RecognitionResultScreen` composition → `Text.onTextLayout` → `RecognitionGlyphPathInspector.inspect` → local path state → Result Glyph renderer → controller readiness → Stage11/12 gates.
- **Root cause:** Glyph production is coupled to the Result route's layout lifecycle; readiness is not produced by an independent shared geometry provider before navigation.
- **Confidence / status:** `ROOT_CAUSE_CONFIRMED` for the contract dependency; actual historical deadlock is `ROOT_CAUSE_UNCONFIRMED`.
- **Impact:** Stage11/12 and NAME_STABLE are coupled to Result composition and layout. This can delay or block Glyph readiness and breaks the required Processing/Result identity continuity.
- **Tests:** `RecognitionResultRuntimeTest.glyphReadinessRequiresClassifierPathAndCompletedCanvasDrawForSameAttempt`; `SpeciesNameRevealRuntimeTest`; `RecognitionRevealProductionRuntimeTest` (instrumentation source). They exercise the current Result-layout producer, not pre-navigation shared preparation. Not run.
- **Missing evidence:** A trace proving glyph path existed before route navigation and the same path/hash was consumed by both renderers.
- **Fix direction:** Generate the real font path from species result before navigation in a shared provider; freeze font/name/weight/size/path hash/version and attach to the committed result snapshot; inject the same snapshot to Processing and Result.
- **Follow-up tests:** Font/style/name path key changes; preparation-before-navigation ordering; stale Attempt path rejection; same path ID in Processing and Result.

## P2

No P2-only finding was separated in this pass. All identified mismatches affect a frozen contract boundary, acceptance evidence, or the ability to diagnose a blocking recognition path; they remain P0/P1 until addressed.
