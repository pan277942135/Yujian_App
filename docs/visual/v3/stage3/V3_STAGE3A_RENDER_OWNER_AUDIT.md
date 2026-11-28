# V3 Stage 3-A Render Owner Audit

## Scope

This change adds the Stage 3-A owner and presentation gate to the existing Orb GL host. It does not change shaders, material parameters, Resident visuals, task energy, Scene Clock, stage timing, paths, mask, glyph, or model behavior.

## Previous call chain

`RecognitionSceneAnimationController` produced the shared `RecognitionSceneFrame`; `WaterLightFrame` carried it into `OrbCrystalWakeGlOverlay`; `TransparentGlTextureView` called `onDrawFrame()` and swapped when the Boolean returned true. `AIOrbCore` then permanently hid its Compose fallback after the first successful swap. The Boolean and swap did not prove that every required pass or the frozen Orb inputs were valid, and the sticky Compose state did not recover after a later GL failure.

## Current call chain

`RecognitionSceneAnimationController` → existing `RecognitionSceneFrame` → `WaterLightFrame` → `OrbCrystalGlFrame` → `OrbCrystalGlRenderer.onDrawFrameWithResult()` → immutable `OrbCrystalRenderResult` → `GlFramePresentationGate` → EGL swap only when all required pass statuses and frame identity are valid → owner state update in `AIOrbCore`.

`TransparentGlTextureView` is the physical EGL execution host. It does not own Scene Clock or stage state. `OrbCrystalGlRenderer` remains the single logical GL renderer. The existing Compose Orb remains the fallback and shares its owner state with the GL `TextureView` alpha.

## Owner rules

| Condition | Visible owner | EGL swap | Diagnostic outcome |
|---|---|---:|---|
| Context or surface unavailable | Compose | No | `SURFACE_NOT_READY` / `GPU_CONTEXT_LOST` |
| Any pass is `NOT_READY`, `NOT_RUN`, or `FAIL` | Compose | No | `FIRST_FRAME_REJECTED`, then throttled `GPU_RENDER_SUPPRESSED` or failure |
| Frame identity or geometry differs | Compose | No | `FRAME_IDENTITY_OR_SCENE_MISMATCH` |
| Every pass is `PASS`, EGL swap succeeds, presented sequence matches | GL | Yes | `FIRST_FRAME_ACCEPTED` |
| An accepted GL owner later fails | Compose | No for failing frame | `FALLBACK_ACTIVE` plus failure status |

## Stage boundary

The current procedural sphere is not treated as the frozen Core texture. The Stage 3-B Core texture and alpha inputs, frozen Resident BACK/FRONT paths, and later fluid/refraction/mask/droplet/light/caustics passes remain `NOT_READY`. Bloom pass status is measured from actual GL draw calls and `glGetError`; those measurements do not authorize ownership while any required frozen pass is unready. Thus this commit protects the transition without claiming Stage 3-B through 3-G visuals are implemented.

## Evidence path

The GL host posts throttled results to the UI thread; `RecognizingScreen` queues them through the existing `RecognitionSessionEvidenceRecorder` into `gpu_trace.jsonl`. The recorder binds the active Session/Attempt/Photo identity and geometry version before enqueueing. The GL thread performs no disk write, and the Stage 2 bounded sink remains the only evidence store.

