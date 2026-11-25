# Test and evidence index

**Source baseline:** `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10`  
**Execution in this audit:** no Unit, Instrumentation, emulator, or device tests were run. No APK was built. This is a source/test inventory, not a test result.

## Existing tests reviewed

| Test source | Relevant source-level coverage | Contract coverage limits | Audit result |
|---|---|---|---|
| `application-core/src/test/java/com/yujian/ai/ai/RecognitionAttemptDeadlineTest.kt` | One shared remaining budget and stable failure families | Does not terminate native ONNX work or prove cancellation of a blocked native call | Inspected; NOT_RUN |
| `application-core/src/test/java/com/yujian/ai/ai/RecognitionRuntimeContractTest.kt` | Recognition runtime projection and result-ready handoff boundary | Older projection contract; not full V2 model/session/run receipt schema | Inspected; NOT_RUN |
| `application-core/src/test/java/com/yujian/ai/ai/RecognitionModelWatchdogTest.kt` | Model start/end identity timing and pending until call returns | No modelSessionId/inferenceId, tensor IO receipt, ORT termination, or native profile | Inspected; NOT_RUN |
| `application-core/src/test/java/com/yujian/ai/ai/subject/SlimSamRuntimeCoordinatorTest.kt` | One-time session creation, init retry, failure metadata, cache identity, late old-photo write rejection | Test currently encodes the reduced cache key; no native termination/quarantine or model SHA/schema key proof | Inspected; NOT_RUN |
| `application-core/src/test/java/com/yujian/ai/ai/subject/SlimSamTensorContractTest.kt` | Dynamic/static batch acceptance, type/name/rank/shape validation, both-session READY | Does not inspect packaged APK on a device or prove all model schema hashes and per-run IO | Inspected; NOT_RUN |
| `recognition-runtime/src/test/java/com/yujian/ai/ui/waterlight/RecognitionSceneFrameTest.kt` | Exactly 13 stages, six-second minima, first-photo-frame start, event gate and identity | No device timing proof for SceneClock/MotionClock or complete C3 evidence | Inspected; NOT_RUN |
| `recognition-runtime/src/test/java/com/yujian/ai/ui/waterlight/WaterLightPathLedgerTest.kt` | Path reversal, trusted mask/P*, route identity, snapshot hash and ORB_ONLY branch | Does not prove Store accepted PATH_BOUND before snapshot creation in production | Inspected; NOT_RUN |
| `recognition-runtime/src/test/java/com/yujian/ai/ui/waterlight/core/Stage04ArrivalPathWithoutMaskTest.kt`; `RecognitionOrbReturnPathDrawCoverageTest.kt` | Arrival/path event and RETURN drawing guardrails | Canvas draw receipt is not display-present evidence | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionFishPathSnapshotBindingTest.kt` | Photo/mask/attempt/geometry and P* identity binding; callback ordering fixtures | Does not add a separate Store PATH_BOUND receipt before frozen snapshot | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/reveal/RecognitionResultRuntimeTest.kt` | Navigation readiness, five ordered result milestones, Glyph descriptor/draw receipt, geometry and stable gates | Validates current weaker draw-pass receipt; no frame-presented/PixelCopy NAME_STABLE proof or snapshot hash | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/RecognitionVisualStateControllerTest.kt`; `ProductionAutoFixesP0Test.kt` | Controller and current P0 guardrail logic | Unit assertions cannot establish real device frame visibility or all six contracts | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/RecognitionStage10FailureFixtureTest.kt` + `recognition-ui/src/test/resources/recognition/stage10_failure_5344335d6a51.json` | Preserves device-derived Stage10 facts: Stage10 at 22,444ms, blocked 23,027ms, four missing gates, wrong-stage zero-segment RETURN rejection, geometry pending, Result first-presented 60,006ms | Fixture source commit is `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`; no full model replay, video, Store binding or navigation proof; not current-HEAD runtime verification | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionSessionEvidenceRecorderTest.kt` | 26KF policy, event identity gate, blocked checkpoint and evidence queue | The optional-save test explicitly asserts untriggered KF25 remains `NOT_REACHED`; no real PixelCopy/device event | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionPixelCopyCaptureTest.kt` | Screenshot stage/session/attempt/image/geometry identity and explicit failures | Does not provide an actual capture artifact or presented-frame receipt in this audit | Inspected; NOT_RUN |
| `recognition-ui/src/test/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionEvidenceZipExporterTest.kt` | File hashes, manifest bytes, screenshot index, original mask, degraded export, budgets | No full schema/content validator, video decode, FIRST_FAILURE, or external independent validation | Inspected; NOT_RUN |
| `app/src/androidTest/java/com/yujian/ai/RecognitionResultHandoffGuardrailRuntimeTest.kt` | Stale attempt rejection and 60-second shared deadline guard | Source test only; not an actual native timeout reproduction | Inspected; NOT_RUN |
| `app/src/androidTest/java/com/yujian/ai/RecognitionResultHandoffProductionNavigationTest.kt` | End-to-end Android production home/picker/model/navigation/handoff flow; writes runtime evidence | No exact audit-run output; no MP4 evidence | Inspected; NOT_RUN |
| `app/src/androidTest/java/com/yujian/ai/RecognitionRevealProductionRuntimeTest.kt` | Android production reveal orchestration; SlimSAM READY/SHA checks; event trace, memory counters (Java/native heap, PSS/RSS), screenshot and cancellation workloads | Does not show ORT operator profile or allocator call-stack attribution. Test was not run, so no memory/device result is accepted here | Inspected; NOT_RUN |
| `app/src/androidTest/java/com/yujian/ai/RecognitionFrozenFlowEmulatorTest.kt` | Frozen recognition/result UI flows, screenshots, responsive/semantic controls | Screenshot/UI checks are not continuous Orb-to-Glyph MP4 or full V2 evidence archive validation | Inspected; NOT_RUN |
| `app/src/androidTest/java/com/yujian/ai/RecognitionRuntimeAssetContractTest.kt`; `InferenceContractTest.kt`; `InferenceParityTest.kt` | Packaged model contract and inference/parity checks | Not run against a newly built exact-HEAD APK in this audit | Inspected; NOT_RUN |
| `recognition-ui/src/androidTest/java/com/yujian/ai/ui/recognition/reveal/SpeciesNameRevealRuntimeTest.kt`; `RecognitionFishContourRuntimeTest.kt`; `RecognitionOpticalSegmentRuntimeTest.kt` | UI/device renderer and contour behavior | Not a full shared GlyphPathProvider + presented-frame acceptance run | Inspected; NOT_RUN |
| `waterlight-renderer/src/test/java/com/yujian/ai/ui/waterlight/OrbResidentSystemsV1Test.kt`; `OrbFilamentRendererSamplingTest.kt`; `OrbFluidOpticalIntegrationV1Test.kt` | Resident identities and Orb/filament rendering profiles | Does not prove live source-to-glyph target assignment on target device | Inspected; NOT_RUN |

## Existing evidence implementation

- **Attempt/model events:** SlimSAM runtime events record model stage, start/end uptime, result, error and tensor metadata, but do not provide complete per-run V2 IDs for every model.
- **Mask/path:** runtime logs and `RecognitionSnapshot` carry mask SHA/version, geometry version, path IDs, P*, segments, event sequence and content SHA for the FishPath object.
- **Timeline:** stage events contain stage, local progress, scene time, event sequence, session and attempt; KF policy enumerates KF00–KF25.
- **PixelCopy:** implementation and identity/index tests exist. Production screen call enables it only for `BuildConfig.DEBUG`; capture can hold the stage while in flight.
- **Memory:** Android test source records Java heap, native heap, PSS/RSS and runtime counters. No ORT operator profile/native allocation attribution was found.
- **Video:** no Orb-to-Glyph MP4 producer/validator found.
- **Archive:** ZIP SHA/manifest/screenshot-index/original-mask checks exist. Missing expected files are synthesized as empty JSONL or `{}` and archive semantic validation is incomplete.
- **Failure classification:** controller stores wait reasons and blocked stage events; a first-failure causal record and downstream `BLOCKED_BY_UPSTREAM_FAILURE` linkage were not found.

## Tests actually run in this audit

None. No Gradle, Android instrumentation, emulator, or device test command was executed. The only checks executed were read-only Git/reference inspection, checksum validation, source listing/search, and targeted source reads, recorded in `05_AUDIT_MANIFEST.json`.

## Missing runtime/device evidence

A checked-in historical deterministic fixture records a prior device-derived Stage10 blockage (details in `03_ROOT_CAUSE_REGISTER.md`, P1-11), but its source commit differs from this audit SHA and it lacks raw trace/model/navigation/video evidence. No exact-HEAD APK hash, current production device trace, evidence ZIP, PixelCopy output, KF25 save/no-save export, Orb-to-Glyph video, native stuck-call trace, or independent validator output was supplied or generated. Consequently, this audit reports no `TEST_VERIFIED`, `RUNTIME_TRACE_VERIFIED`, `MEMORY_PROFILE_VERIFIED`, `DEVICE_REPRODUCED`, or `FULL_FLOW_ACCEPTED` result.

## Suggested follow-up run order

1. Run existing focused Unit Tests for runtime, path, timeline, result, and evidence guards on the exact source SHA.
2. Build one exact-HEAD APK; record APK SHA and packaged model contract.
3. Run `RecognitionRevealProductionRuntimeTest` and production navigation instrumentation on a named device/model/photo; retain raw logs, memory samples, screenshot indexes and failure records.
4. Add/execute scenarios for no KF25 tap, dropped/delayed frame, missing result geometry, missing Glyph callback, and cancelled/stuck native call.
5. Collect real KF14–KF20 MP4 and PixelCopy, then validate the ZIP from extracted content with an independent schema/content validator.
6. Only then assess layered AUTO/VISUAL/FULL_FLOW acceptance.
