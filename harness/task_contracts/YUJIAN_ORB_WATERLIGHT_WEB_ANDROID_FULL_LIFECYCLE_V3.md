# AI Orb × WaterLight Web/Android Full Lifecycle V3 — Execution Contract

**Task ID:** `YUJIAN_ORB_WATERLIGHT_WEB_ANDROID_FULL_LIFECYCLE_V3`  
**Phase:** P6  
**Feature:** `recognition_flow_v1_2`  
**Base candidate:** PR #294, initially audited at `b820baa41d62ab7d056e3b084daa5b9f655d8aeb`

## Scope

Complete the existing Android AI Orb × WaterLight recognition lifecycle in the current recognition implementation. Reuse the existing Orb, WaterLight hosts, PhotoSession, model runners, scene frame, reveal gate and Result handoff. Migrate only verified active visual behavior from `Fish-Mask-Gold-Contour-Lab`; do not embed the Web runtime or use its synthetic BBox branch for production fish geometry.

Classifier and SlimSAM remain independent parallel model branches. The classifier result is retained until SlimSAM reaches an explicit terminal state and its mask trust/contour decision is made. Trusted real masks select fish contour/gold; untrusted or failed masks select the real-name Orb-only path. Pending work never becomes failure or success based on elapsed animation time.

The 13 stages remain named semantic visual states. Stage minima and local transitions may use durations, but production cannot stop at a global 2800/850/3650ms cutoff. Debug MANUAL is the default; Debug AUTO uses at least 3000ms per stage and still observes real model gates. One scene authority drives all rendering and seek/pause controls.

## Protected behavior

Preserve real PhotoSession identity and source pixels, EXIF normalization, Detector/Classifier/SlimSAM inputs and thresholds, real BBox/Mask coordinates, cancellation and stale-attempt isolation, the 700ms photo settling and 900ms Camera Orb entrance in parallel, Result CTA availability and Bitmap ownership safety. Do not add a second clock, fixed fish/name geometry or a new demo renderer.

## Evidence and delivery

Required evidence: source-SHA audit, semantic stage/model-gate tests, trusted-mask and Orb-only branch tests, scene control continuity tests, Web-to-Android visual layer port tests, unit tests, Android test compilation where available, lint, Debug build, APK source SHA and APK SHA-256. Product-owner visual and device acceptance is separate. The active user instruction for this Work is to skip API28/physical-device validation and deliver a basic-tested APK for owner acceptance.

## Completion rule

Do not mark product visual acceptance PASS from compilation or static tests. Keep unresolved runtime/visual work explicit. The PR remains Draft until the requested code, basic tests and APK are ready for owner validation.
