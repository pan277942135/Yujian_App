# Crystal Orb integrated GPU implementation

Status: CODE_IMPLEMENTATION_CANDIDATE / OWNER_AUTHORIZED_ADMISSION_BYPASS. Local Admission originally failed because the checkout has no branch-local `PROJECT_MASTER_STATE.yaml` or `WORK_EXECUTION_STATE.json`; the owner explicitly authorized this limited bypass. This is not an Admission PASS.

Base: PR #342 at `c0492aa469530e99472af23c07946190a9036088`. Stacked Draft PR #344, with exact final commit and CI recorded in the PR description after validation.

The original 384×384 `processing_water_core_v1.webp` remains unmodified, SHA-256 `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`. The GL owner checks raw bytes, dimensions and alpha, uploads GL_RGBA once per context, and deletes/recreates the texture with context lifecycle. Android premultiplied bitmap upload is respected. The unchanged source alpha determines silhouette; interior UV samples advect continuously from Scene Clock and sample the Back FBO for bounded local refraction and transmission. Glass grazing response and restrained interior caustic are included in the same Core material pass.

Five existing orbital paths are pinned as G01/G02/A01/A02/C01, each with its own speed, phase, tilt, depth and travel crest. Their back portions enter Back FBO and are refracted/occluded by the Core; front portions enter Scene FBO after Core. Grass is drawn back/front; droplets and orbiting starbursts are drawn in Scene FBO. Each actual draw checks GL errors and records the corresponding immutable RenderResult status. Bloom extracts highlights, uses two horizontal/vertical blur iterations, and composites to the full surface viewport. The existing low-performance resolution cap remains; no per-frame texture decode, FBO allocation or shader compilation was introduced.

The same `OrbCrystalGlRenderer`, `TransparentGlTextureView`, Stage 3-A first-frame gate and Compose fallback remain. No new scene clock or production renderer was introduced. Back FBO contains procedural Orb scene elements, not the Compose photograph. A physical device visual verdict is reserved for the owner; no visual PASS is claimed. Master Library reference review remains pending.

Automated validation: the first `qa/**` CI run compiled Kotlin, passed Unit Tests, assembled Debug APK and verified packaged assets, then failed Lint on `ResourceType` for drawable raw-byte SHA reading. The narrow lint annotation explains that Android Resources accepts the drawable ID for raw-byte reading. A fresh exact-head CI run is required before delivery.

The prior Stage 3-B+C offscreen GPU preview is not a prerequisite for this integrated delivery, per owner instruction. No offscreen or API36 device image is represented as evidence.
