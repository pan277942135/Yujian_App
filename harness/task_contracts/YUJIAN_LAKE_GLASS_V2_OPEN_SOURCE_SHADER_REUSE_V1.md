# Work P0 — Lake Glass V2 Open-Source Shader Reuse V1

**Task ID:** `YUJIAN_LAKE_GLASS_V2_OPEN_SOURCE_SHADER_REUSE_V1`  
**Repository:** `pan277942135/Yujian_App`  
**Priority:** P0  
**Phase:** P6 — Feature Implementation  
**Parent scope:** `YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1`  
**Policy:** Reuse mature, permissively licensed shader algorithms. Keep Lake Glass V2 composition, anchors, scene clock, and static fallback authoritative.

## Source audit baseline

The following exact public commits were inspected on 2026-11-02. Re-resolve current default-branch heads before copying or pinning a dependency. Never copy source from an unlicensed revision.

| Repository | Inspected commit | License | Implementation decision |
|---|---|---|---|
| `ethanfox/water-caustics` | `de856eec62819b7c4176366db73b8fd37a1b1455` | MIT | Port the continuous, non-tiled domain-warped caustic field from `src/shaders.ts` to AGSL and constrain it with the existing Master-derived caustic mask. The package has no production dependency; the demo uses Vite/TypeScript. |
| `ScottieFox/caustic-volume` | `d87351bff19831aa9d11c0679605fad6797b133f` | MIT | Inspect the 24-direction wave and photon-projection model for algorithm comparison only. Do not embed Three.js, its 990-line Lite page, or its roughly 7,500-line volume sandbox. |
| `AndroidPoet/mirage` | `9da9b00f678d57ce9989838ac513ffa88a11b038` | Apache-2.0 | Reuse small, attributed AGSL/Compose patterns for RuntimeShader creation, parameter uniforms, noise, and localized light modulation. Library targets API 33+ for AGSL and has no dependency beyond Compose. |
| `styropyr0/PrismalAGSL` | `c853feb2e8b375fc8eb2dcce25a6d4f18250fb68` | MIT | Adapt the rounded SDF, image sampling, edge refraction, specular-mask, and shader-cache patterns. Its module declares minSdk 25 and AGSL still requires API 33. Do not add the library dependency solely for these patterns. |
| `frmlinn/raindrops-v2` | `6e95911694bac6bafd1aadbc0e85fbd9a2e56ada` | MIT; README identifies a Codrops RainEffect origin | Inspect uneven-capsule SDF and screen-space refraction only. No rain particles, 3D scene, or Three.js runtime enters Lake Glass. Preserve its notice if any source is adapted. |

The ScottieFox public Lite demo was opened at `https://ScottieFox.github.io/caustic-volume/lite/`. The observed page rendered its glass-tank water scene and exposed live Sun/Lamp, wave, turbulence, pause and renderer controls; it reported WebGL/three.js r186 and roughly 21–30 FPS in the inspected browser session. This is browser-demo evidence, not Android or target-device performance evidence. Other projects' README demos/screenshots are source-author claims until independently run in their own supported environments.

## Implementation requirements

1. Add a self-contained AGSL source and isolated Compose debug lab for the caustic field, ambient light modulation, and fixed-droplet refraction. Each effect must have independent enable/disable controls and bounded parameter sliders before production integration.
2. Port EthanFox's continuous caustic field without axis-aligned tiling; retain attribution and the exact upstream source commit. Use a spatial mask from the existing Master to modulate the baked caustic luminance. Do not add a second caustic sheet on top of baked veins.
3. Reuse Mirage's supported AGSL host/uniform/noise patterns for slow ambient motion. Keep the light origin fixed at the Master’s upper-right. Apply only low-amplitude luminance modulation through the Master-derived sunlight mask; do not draw a second sun, ray, or light column.
4. Reuse Prismal's SDF/refraction/specular technique and Raindrops V2's relevant screen-space/SDF formulation for the existing three droplets. Anchors and shape come from the resource manifest. Do not spawn particles or move anchors. Because the three droplets are baked into the static composite, the shader must transform/sample that one composite in place; it must not draw the extracted droplet RGBA as a second visible surface. Estimated normal maps remain estimates and are not sensor-measured optical truth.
5. Keep one `LakeGlassRenderer` and a separate `AmbientController`. API 24–32 always uses the verified static composite. API 33+ may enable AGSL only after capability, compile, visual, and exact-SHA checks; any failure returns to static rendering without blocking navigation. No `GLSurfaceView`, EGL lifecycle, extra Three.js, or modification of the AI Orb × WaterLight clock.
6. Integrate each effect only after its isolated lab result has been inspected. Default motion is disabled or visually negligible until exact-device validation. The static composite remains the baseline and fallback.
7. Keep third-party source modifications small and auditable. Include each copied/adapted repository's exact commit, paths, license text, attribution, and modifications in `third_party/NOTICE.md` and separate license files. Do not add an unneeded runtime Gradle dependency.

## Evidence and gates

- Repository, license, dependency, pinned-commit, and demo/runtime audit table for all five projects.
- Independently adjustable Android debug lab for all three effects, with screenshots or exact device/API test evidence.
- AGSL compile/output tests, fixed-anchor tests, no-double-draw/base-sampling tests, reduced-motion/static fallback tests, and API 24 versus API 33+ policy tests.
- Exact SHA for every upstream source file copied/adapted and every generated shader asset.
- Lake Glass Master comparison at 1440×3120 and the four required viewports. Record whether motion alters or only modulates baked structures; no global image metric can replace manual visual review.
- Exact-head APK, APK SHA-256, build/test logs, device/API and performance/memory observations. If a real device is unavailable, keep device status `NOT_RUN` while still delivering the APK.
- Resource readiness, Android integration, and Android device validation remain three independent statuses.
