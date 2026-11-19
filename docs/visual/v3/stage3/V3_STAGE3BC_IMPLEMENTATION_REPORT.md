# Stage 3-B+C implementation report

Status: IN_PROGRESS / OWNER_AUTHORIZED_ADMISSION_BYPASS. The original local Control Plane Admission returned FAIL because the checkout has no branch-local `PROJECT_MASTER_STATE.yaml` or `WORK_EXECUTION_STATE.json`. The owner explicitly authorized proceeding for those two missing dynamic files only. No PASS is inferred.

Base: PR #342 at `c0492aa469530e99472af23c07946190a9036088`. Stacked Draft PR #344.

The original 384×384 `processing_water_core_v1.webp` remains unmodified, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`. The GL owner decodes it once per context, checks dimensions, SHA, transparent corner and opaque center, uploads GL_RGBA with clamp and linear filtering, and deletes/recreates the texture with context lifecycle. Android's premultiplied bitmap upload is respected in the shader. The unchanged source alpha determines the silhouette, while interior UV samples advect deterministically with Scene Clock. The shader samples Back FBO for bounded local refraction and transmission. This Back FBO contains procedural scene elements, not the Compose photo.

The same `OrbCrystalGlRenderer` owns the new Core shader and Back/Scene FBO path. Resident, Mask, Droplet, Light, Caustics and Front passes remain NOT_READY in the receipt; production GL first-frame handoff stays blocked by Stage 3-A gate. No new clock or production renderer was introduced.

Known validation gap: no Android SDK or cached Gradle 9.3.1 distribution exists in this workspace; local Gradle cannot download from services.gradle.org. GPU PixelCopy, offscreen images, API36 visuals, frame time and memory remain DEVICE_PENDING. Exact-head CI and APK have not yet passed or been delivered. The master Library image was not reviewed here, so MASTER_VISUAL_REVIEW_PENDING.

Stage 3-D input: source alpha silhouette is preserved; Core UV maps `vNdc / 0.4 * 0.5 + 0.5`; resident BACK may enter Back FBO, resident FRONT enters Scene FBO after Core pass. Preserve five existing path IDs G01/G02/A01/A02/C01. The Back FBO is the only current refraction source.
