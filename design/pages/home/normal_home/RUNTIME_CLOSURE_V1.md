# Normal Home V1 Runtime Closure

Frozen authority: `design/system/core_visual_v1/reference/normal_home_v1.png`  
SHA-256: `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`

Runtime root: `app/src/main/assets/normal_home_runtime_v1/`

The runtime root owns the Normal Home lake scene, capture-button raster layers,
guest avatar, and fish-card visual layers. Product runtime code must not depend
on `home_empty_v1_3` or `home_normal_v1_2`.

The API 28 gate captures:
- 1080×1920 frozen-geometry parity
- 19.5:9 single-record state
- 20:9 multiple-record state
- 21:9 multiple-record state
- Frozen/runtime side-by-side comparison
- 10-second production motion evidence
