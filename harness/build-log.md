# Work Build Log

## 2026-10-19 — BBox localization Android fix baseline

- Android repository: `pan277942135/Yujian_App`; live `main` SHA: `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`.
- `scripts/validate_control_plane_admission.py` passed on canonical `main` after refreshing `origin/main`.
- Recognition Processing V1.3 branch `8428a3daa7d8b2170746b7d339d95bc6ece2da40` is an ancestor of current main; implementation audit uses current main.
- PR #145 is closed and merged; it is a Design System V3.1 PR, not the current Recognition code authority. PR #166 and PR #167 remain separate Draft/Open/Unmerged workstreams.
- New Work branch `fix/bbox-localization-android-v1` starts at `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`.
- No detector fix, model edit, threshold change, NMS change, runtime test, or GCP action has occurred yet.
- Ground Truth for the user-provided candidate images is unavailable; no IoU claim is made.

## 2026-10-19 — EXIF geometry fix and Android evidence harness prepared

- Confirmed in the camera/gallery normalization source that `rotationDegrees` alone omitted the EXIF horizontal/vertical reflection and transpose transforms (orientations 2, 4, 5 and 7). The two user images have historical V3 EXIF=1 metadata, so this general bug is not established as their cause.
- Implemented full EXIF 1–8 normalization and orientation diagnostics. Kept detector parameters, model assets, NMS, crop expansion, retry policy and UI geometry unchanged.
- Added EXIF runtime, decode/mapping, crop-edge, exact Case B three-direction raw-output, and metadata-only EXIF-2 before/after instrumentation coverage. The gate now includes 59 test methods and collects generated evidence files.
- Added static audit report and machine-readable manifest. User images remain private and absent from this filesystem; historical PR #164 V3 results are labeled HISTORICAL. Ground Truth and user-image IoU remain missing.
- Local checks passed: runtime gate shell syntax; fake-ADB harness (9 scenarios); runtime evidence source-closure tests (6); Recognition contract verifier; production classifier release verifier.
- Android Gradle build/unit tests/androidTest compile/lint and API 28 run remain NOT_RUN because this container has no Gradle, Android SDK, adb or emulator. No GCP resources were accessed.
- Exact implementation source SHA: `430a616ce8070ae20c354d0bda80ed4828dd44cc`. The isolated branch push, independent Draft PR, hosted Android build and existing API 28 runner remain pending.
