# Crystal Orb integrated QA

| Item | Status | Evidence |
| --- | --- | --- |
| Source WebP SHA and 384×384 dimensions | PASS | Local SHA256 and decoded alpha bbox `(35,37,345,345)`; CI resource verifier |
| Kotlin compile, Orb Unit Tests, Debug APK assembly | PASS on prior CI HEAD | Android CI run 44438160954; new exact-head rerun required |
| Lint | FAIL on prior CI HEAD | Drawable raw-byte `ResourceType`; narrow suppression in next HEAD |
| Release Kotlin | NOT_RUN | Prior CI stopped after Lint |
| GPU texture, shader, spatial quality | USER_DEVICE_QA_PENDING | No physical device execution |
| Master visual alignment | MASTER_VISUAL_REVIEW_PENDING | Library master not directly inspected |
| Production first-frame handoff | DEVICE_PENDING | Gate has automated coverage; EGL device execution not observed |

Back FBO refraction samples procedural Orb scene, not the Compose photo. The owner will install the exact-head Debug APK on API36 and report visual results.
