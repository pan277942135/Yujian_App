# Work P0 — Lake Glass V2 Asset Pack and Android Runtime Integration V1

**Task ID:** `YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1`
**Repository:** `pan277942135/Yujian_App`
**Priority:** P0
**Phase:** P6 — Feature Implementation
**Design authority:** PR #302, `design/system/backgrounds/lake_glass_v2/Lake_Glass_Background_System_V2.md`
**Resource baseline:** PR #305, initially `4f2dafbf017fd2b05bdb19f5d067eda410dfb9fb`; re-read the live PR head before every integration checkpoint.
**Runtime change:** separate Android PR, stacked on the resource PR, then retargeted to `main` after the resource PR is merged.

## Goal

Complete the Lake Glass V2 engineering asset pack and integrate one reusable Android background host for every applicable theme-background owner in the current app. Keep the existing Empty Home composition and its source/runtime assets unchanged. Preserve Morning Lake V1 history byte-for-byte. Background work must not alter page layout, capture controls, camera/photo/media content, recognition behavior, AI Orb, WaterLight, or the shared recognition clock.

Use the current Lake Glass V2 Master and resource PR as inputs. Do not redesign the scene or recreate assets that already pass their source, alpha, geometry, and visual checks. Retain baked elements in the static base whenever a trustworthy clean plate is unavailable. The static composite is always the safe fallback; extracted diagnostic assets do not enter the visible runtime stack unless the resource authority explicitly marks them safe and the Android path verifies them.

## Authoritative task sources

- Lake Glass V2 visual and all-app contract: PR #302, especially its Empty Home exclusion, unified coordinate system, shared host, source provenance, route coverage, and API fallback requirements.
- Layer asset pack: PR #305 and its live manifest, feasibility review, static composite, crop matrix, verifier, and exact input hashes.
- The single machine-readable layer state in `assets/metadata/asset_manifest_v3.json` / `lake_layer_pack_v3.json` on the resource branch is authoritative for separation and runtime eligibility. Visual review and Android compatibility output must be generated from that data, not independent boolean flags.

## Scope and protected behavior

1. Audit the current `YujianApp.kt` routes and composables; record each route/state's prior background owner, V2 host decision, content protection, and evidence in `route_background_coverage_v2.json`.
2. Migrate every current theme-background owner to one shared `LakeGlassEnvironmentHost`. Classify opaque camera preview, photo/video/media, OS picker/dialog, and other content-owned surfaces as protected foreground with an explicit reason; do not omit those routes.
3. Keep the real Empty Home branch on its existing `EmptyHomeSceneRenderer` and unchanged frozen assets, interaction geometry, bobber, ripple, haptics, and SHA.
4. Preserve Morning Lake V1 source files, runtime artifacts, and recorded hashes. They may remain historical/test inputs but cannot be the new runtime fallback.
5. Keep camera/photo/session geometry, 13-stage recognition sequence, Photo Morph, AI Orb × WaterLight, stage clock, result handoff, and all page-owned foreground geometry unchanged.
6. Use one 1440×3120 scene coordinate space and one viewport transform for all active layers. Never give individual layers separate crop, translation, or scale.

## Runtime requirements

- Implement one reusable Compose/Canvas host, scene manifest reader, asynchronous asset repository/cache, viewport transform, static renderer, and explicit API/capability policy in the existing Android architecture.
- API 24–32 must render the verified static composite without RuntimeShader, OpenGL, EGL, or blocking first-frame decode.
- API 33+ may enable only layers whose canonical state allows runtime and whose Android behavior has exact-SHA verification. Until verified, use the static composite. If shader setup, asset load, or rendering fails, fall back to static without blocking navigation or page startup.
- Do not add a `GLSurfaceView` or independent EGL lifecycle. Do not allocate/decode bitmaps or shaders per frame or per recomposition. Share cache entries across pages and avoid loading diagnostic masks/maps into the APK.
- Decode asynchronously at a suitable display size; report decode/first-display time, memory, page-switch allocations, and available frame/jank observations. Keep incremental background memory near 32 MiB where practical and record device/measurement limits.
- Use a single manifest-derived authority for separation status, runtime mode, fallback, z-order, and asset SHA. Do not use `runtimeEligible=true` as a substitute for the enumerated layer states.

## Resource-layer state semantics

Only these values are allowed: `STATIC_COMPOSITE_ONLY`, `MATCHED_BASE_STATIC_OVERLAY`, `SHADER_INPUTS_PROVISIONAL`, `ANDROID_RUNTIME_VERIFIED`, `BAKED_IN_BASE`, and `NOT_RUNTIME_ELIGIBLE`.

- `BAKED_IN_BASE`: never draw the extracted copy over its source composite.
- `SHADER_INPUTS_PROVISIONAL`: diagnostic/future input only; no production visual overlay.
- `MATCHED_BASE_STATIC_OVERLAY`: static overlay only on its named matching base, with shared coordinates.
- `ANDROID_RUNTIME_VERIFIED`: only after the Android path, exact-SHA tests and visual output pass.
- Static mode remains available if all extracted optical layers are disabled.

Estimated normal maps must be identified as estimates, not measured refraction. A static droplet reconstruction does not prove a verified dynamic refraction shader.

## Responsive and visual acceptance

Use one shared CenterCrop/TopCenter policy selected by evidence for 320×480, 360×640, 390×844, and 430×932 dp, plus the actual attached device if available. Include font scales 1.0, 1.3, and 1.5 for route/content protection review. Record natural crop trade-offs; never stretch or move layers independently. Produce route screenshots, crop sheets, focused 100%/200% edge checks, and comparison to the 1440×3120 Master.

Run asset integrity, manifest parity, static recomposition, layer ordering, transform, route map, API fallback, build, unit tests, Android test compilation, lint, and APK SHA checks. Automated assertions and global PSNR/SSIM are not substitutes for a recorded human visual review. Keep real device status separate; if no device is available, record `NOT_RUN` and still deliver the Debug APK.

## Independent completion states

### A — `ASSET_PACK_READY_FOR_ANDROID`

Set only when the formal runtime asset is present, its manifest/status/SHA are consistent, the static fallback is visually accepted, baked assets cannot be double-composited, route-independent crop metadata is valid, the verifier passes, and a human visual inspection is recorded. The pack may be static-composite-only; it must not claim unsafe extracted layers are runtime-ready.

### B — `ANDROID_RUNTIME_INTEGRATED`

Set only when the common host is wired to every applicable current theme-background owner, Empty Home and protected foreground routes are preserved, API 24–32 static rendering and API 33+ fallback are validated, required source tests/lint/build pass, and an exact-HEAD Debug APK with SHA-256 is delivered.

### C — `ANDROID_DEVICE_VALIDATED`

Set only after installing the exact APK on a real Android device and recording API/device, route screenshots, geometry, page switching, black/white edge checks, and measured performance/memory. Emulator/build evidence alone cannot promote this state. If a real device is unavailable, leave this state `NOT_RUN` without blocking A/B or APK delivery.

## Required evidence

- Exact PR #305 head, asset manifest, source/asset SHA-256, layer-state parity, static composite and verification JSON.
- `route_background_coverage_v2.json`, including Empty Home untouched and content-owned opaque exceptions.
- Shared host/asset repository/transform/API policy implementation and tests.
- Four required viewport captures, font-scale review, Android API-mode/fallback results, route-level screenshots, and visual review record.
- Build/test/lint logs, decode and memory measurements, APK artifact and APK SHA-256, source SHA, workflow or local runner identity.
- Separate resource, runtime integration, and device validation states. Never infer device validation from APK packaging.

## Not permitted

- No redesign, page refactor, business-flow change, Empty Home change, Morning Lake V1 mutation, camera/photo transformation, Orb/WaterLight/timing modification, or new GLSurfaceView/EGL path.
- No generated/estimated layer may be described as a native source or measured optical map.
- No forced clean plate, blur patch, or repeatable tile may be promoted to runtime after visual failure.
- Do not weaken thresholds or relabel a failed/not-run gate as pass. Preserve failure logs and exact SHAs.
