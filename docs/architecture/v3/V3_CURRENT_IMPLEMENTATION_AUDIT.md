# YuJian V3 — Stage0 Targeted Source Audit
Main SHA: 1f930e4678264522d4d160c28c8d52901eb14c0d. Findings are source-level and not confirmed on a running Android device.

CONFIRMED:
- RecognitionSceneAnimationController owns event-gated 13-stage playback; RecognitionSceneFrame holds shared identity/time/geometry.
- OrbResidentSystemsV1 identifies resident G01/G02/A01/A02/C01; RecognitionTaskFilamentState excludes these five IDs from task route identities.
- RecognitionNearestContourContactPoint computes nearest eligible Mask exterior contact in source-photo pixels.
- WaterLightPathLedger snapshots path/mask/geometry and exact return segments.
- RecognitionResultRuntime has 13.1 NAME_STABLE, 13.2 PHOTO_SETTLE, 13.3 RESULT_SCENE_HANDOFF, 13.4 RESULT_INFO_ARRIVAL, 13.5 RESULT_SCENE_STABLE.
- Diagnostics Recorder/EventSink/Store, PixelCopy, ZipExporter/ArchiveVerifier exist; must reuse.

CONFIRMED SOURCE RISKS:
- WaterLightGlSupport: onDrawFrame Boolean followed by eglSwapBuffers can announce first frame although Bloom function returns Unit early on invalid programs.
- OrbCrystalWakeGlOverlay: orbital base theta is time-independent; flowShift changes mainly local wave/highlight. Normal FBO max dimension around 440px.
- Stage045EnergyStreamGlOverlay: fishPath ?: arrivalPath fallback is present (multiple uses).
- Stage045 pointOnPerimeter implements square-corner rectangular parameterization, not rounded-rectangle arc length.
- RecognizingScreen contains Stage045 and Perimeter/legacy drawing ownership overlap; actual visual duplication is not yet device confirmed.
- Bloom fragment shader performs five texture2D fetches (potential optimized 9-point effective kernel); do not claim nine literal samples without clarifying contract.
- Debug ZIP is implemented, but process-death recovery, exporting older failed Attempts, cross-device logging and full visual continuity are unproven.

SOURCE LINKS (exact audited SHA):
https://github.com/pan277942135/Yujian_App/blob/1f930e4678264522d4d160c28c8d52901eb14c0d/app/src/main/java/com/yujian/ai/ui/waterlight/renderer/WaterLightGlSupport.kt
https://github.com/pan277942135/Yujian_App/blob/1f930e4678264522d4d160c28c8d52901eb14c0d/app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbCrystalWakeGlOverlay.kt
https://github.com/pan277942135/Yujian_App/blob/1f930e4678264522d4d160c28c8d52901eb14c0d/app/src/main/java/com/yujian/ai/ui/recognition/Stage045EnergyStreamGlOverlay.kt
https://github.com/pan277942135/Yujian_App/blob/1f930e4678264522d4d160c28c8d52901eb14c0d/app/src/main/java/com/yujian/ai/ui/recognition/diagnostics/RecognitionEvidenceZipExporter.kt

NOT RUN: Library high-resolution 26 frames, parameter measurement, Gradle build, API36 physical device, GPU pixel validation.
GitHub Actions at Main: run 44228350037 failed; 44228349934 succeeded; 44229694767 skipped. Do not claim full Exact-HEAD CI PASS.
