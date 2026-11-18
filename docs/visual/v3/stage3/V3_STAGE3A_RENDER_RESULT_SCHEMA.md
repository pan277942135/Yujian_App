# V3 Stage 3-A RenderResult Schema

## Envelope

`OrbCrystalRenderResult` is an immutable receipt for one submitted `RecognitionSceneFrame` and one process-wide GL frame sequence. The persistent `gpu_trace.jsonl` event carries the Stage 2 `SceneKey` and hashed Session, Attempt, and Photo identities; raw IDs and the photo SHA are not copied into this trace.

| Field | Type | Meaning |
|---|---|---|
| `identity` | `RecognitionSceneIdentity` | Session, Attempt, Photo identity and photo content version used by the submitted frame |
| `sceneClockMs` | `Long` | Existing controller-authored Scene Clock value |
| `frameSequence` | `Long` | Monotonic process-wide GL receipt sequence; not a clock |
| `stage` | `RecognitionSceneStage` | Stage supplied by the shared SceneFrame |
| `stageProgress` | `Float` | Stage progress supplied by the shared SceneFrame |
| `eventSequence` | `Long` | Runtime event sequence supplied by the shared SceneFrame |
| `geometryVersion` | `Long` | Geometry version supplied by the shared SceneFrame |
| `timestampMs` | `Long` | Host uptime when the receipt was built |
| `coreTextureStatus`, `coreAlphaStatus` | `NOT_READY / NOT_RUN / PASS / FAIL` | Frozen Core texture input and alpha preservation |
| `shaderCompileLinkStatus` | status | Actual required GL shader/program availability |
| `backFboStatus` | status | Actual back/scene framebuffer allocation result |
| `residentBackStatus`, `residentFrontStatus` | status | Frozen Resident BACK/FRONT pass readiness |
| `coreFluidStatus`, `refractionStatus` | status | Core fluid and refraction pass status |
| `r01bMaskStatus`, `r02DropletStatus`, `r03LightStatus`, `r05CausticsStatus` | status | Corresponding frozen pass status |
| `bloomExtractStatus`, `bloomBlurHStatus`, `bloomBlurVStatus` | status | Independently checked Bloom pass results |
| `compositeStatus` | status | Final composite pass result |
| `eglSwapStatus` | status | Real EGL host swap result; `NOT_RUN` when the RenderResult gate rejects first |
| `presentedFrameSequence` | nullable `Long` | Sequence actually swapped, or null if not presented |
| `fallbackStatus` | `ACTIVE / RELEASED` | Compose fallback remains active until a complete, presented result passes |
| `failureReason` | nullable `String` | First specific pass, identity, host, EGL, or context failure reason |

## Acceptance rule

The EGL host may call `eglSwapBuffers` only when the renderer call succeeded, the result matches the exact submitted SceneFrame, and every required pass is `PASS`. Ownership transfers only after EGL reports success and `presentedFrameSequence == frameSequence`. All other outcomes preserve Compose fallback.

## Event names

Key transitions are persisted with the existing evidence sink as `GPU_RENDER_STARTED`, `FIRST_FRAME_ACCEPTED`, `FIRST_FRAME_REJECTED`, `GPU_RENDER_ACTIVE`, `GPU_RENDER_SUPPRESSED`, `GPU_RENDER_FAILED`, `GPU_CONTEXT_LOST`, `SURFACE_NOT_READY`, and `FALLBACK_ACTIVE`. Sampling is throttled by stage/status and one-second Scene Clock buckets.

