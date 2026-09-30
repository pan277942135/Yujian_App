# Normal Home V1.1 Runtime Closure

Frozen authority: `design/system/core_visual_v1/reference/normal_home_v1.png`  
SHA-256: `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`

Design authority is now closed by:

`design/pages/home/normal_home/DESIGN_PACKAGE_CLOSURE_V1_1.md`

Runtime root: `app/src/main/assets/normal_home_runtime_v1/`

The runtime root owns the byte-identical Morning Lake master background, capture-button raster layers,
guest avatar, and fish-card visual layers. Product runtime code must not depend on
`home_empty_v1_3` or `home_normal_v1_2` as design authority.

Dynamic fish photos and authenticated avatars remain runtime/user data and are not design assets.

The API 28 gate captures and verifies actual PNG dimensions (the runtime capture is never resized):

- 1080×1920 frozen-geometry parity
- 19.5:9 single-record state
- 20:9 multiple-record state
- 21:9 multiple-record state
- Frozen/runtime side-by-side comparison
- 10-second production motion evidence

Runtime evidence status remains separate from Design Package freeze status.
If API 28 cannot capture the requested pixel size, the gate returns `BLOCKED_INFRA`.
