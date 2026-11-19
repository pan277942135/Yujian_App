# Stage 3-B+C visual QA

| Item | Status | Evidence |
| --- | --- | --- |
| Source WebP SHA and 384×384 dimensions | PASS | Local SHA256 and decoded alpha bbox `(35,37,345,345)` |
| GPU texture upload and context recreation | DEVICE_PENDING | Code path implemented; no EGL execution in this workspace |
| Static transparent Core and Alpha capture | DEVICE_PENDING | No actual GPU readback |
| Dark and light backgrounds | DEVICE_PENDING | No actual GPU capture |
| Three Scene Clock samples | DEVICE_PENDING | No actual GPU capture |
| Refraction enabled/disabled comparison | DEVICE_PENDING | No actual GPU capture |
| API36 PixelCopy, performance and memory | DEVICE_PENDING | Device unavailable |
| Master visual alignment | MASTER_VISUAL_REVIEW_PENDING | Library master not directly inspected |
| Production first-frame handoff | NOT_RUN | Stage 3-D–G frozen inputs remain NOT_READY |

Never interpret procedural Back FBO refraction as physical refraction of the Compose photo. Shader values are candidate tunables, not measured IOR.
