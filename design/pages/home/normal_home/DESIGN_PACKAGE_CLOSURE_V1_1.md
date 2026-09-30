# Normal Home Design Package Closure V1.1

Status: **FROZEN — authority correction**  
Base package: **Normal Home Design Package Closure V1**  
Change type: **background bitmap authority registration; no visual redesign**

## Authorities

- Frozen page composition and hierarchy remain `design/system/core_visual_v1/reference/normal_home_v1.png` (1080 × 1920; SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`).
- Normal Home background bitmap authority is `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` (941 × 1672; SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`).
- Runtime uses `app/src/main/assets/normal_home_runtime_v1/static/scene_base.png`, a byte-identical copy (SHA-256 `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7`).
- Android's existing centered `ContentScale.Crop` fits the bitmap to each display viewport. The source pixels are not recolored, resampled, or reinterpreted.

## Correction record

The prior package incorrectly declared that no independent Morning Lake master existed. It also allowed a Normal Home runtime scene blob identical to Empty Home's scene. V1.1 registers the existing system master for the background bitmap and requires an independent runtime asset. The frozen page PNG, its hash, page composition, and hierarchy are unchanged.

## Runtime contract

- Runtime manifest must identify the registered source path, SHA-256, dimensions, runtime output path and SHA-256, and transform policy.
- The Normal Home scene blob must differ from `empty_home_runtime_v2/static/scene_base.webp`.
- Missing provenance, source hash drift, or shared Empty Home scene bytes fail the asset gate.
- Page behavior, shared Hero family, and capture controls continue to follow their existing frozen package contracts.
