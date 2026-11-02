# Lake Glass V2 Android compatibility and fallback

| Android/API | Mode | Shader behavior | Fallback evidence |
|---|---|---|---|
| API 24–32 | `STATIC_COMPOSITE` | No AGSL class is instantiated; no GL/EGL surface is created. Uses one verified 1440×3120 static composite and one CenterCrop matrix. | Unit API-policy coverage; exact API 28 runtime screenshot remains pending the repository Android harness. |
| API 33+ | Static by default; optional AGSL | `RuntimeShader` is created only after static asset and mask SHA checks. The effect is applied to the same composite input; all dynamic controls default off. | Instrumented API 33 shader compile/configuration test and shader-lab screenshot are produced by the Android CI job. Any failure or missing mask returns to static mode. |
| API 33+ physical device | `NOT_RUN` until physical install | Device GPU compatibility, jank and memory peak cannot be inferred from emulator/source review. | Requires a real-device install and screenshot/performance review. |

The runtime repository hashes and decodes assets asynchronously, uses one process cache for page changes, decodes to the smallest power-of-two sample that still covers the measured viewport, and drops its process-owned cache reference on `TRIM_MEMORY_UI_HIDDEN`. Bitmap allocation bytes and decode/first-display timings are emitted by the host and shown in the debug lab. Physical-device memory/jank remains unmeasured until that device run occurs.
