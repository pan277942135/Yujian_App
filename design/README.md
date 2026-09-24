# YuJian Design Source of Truth

This directory is the design source of truth for YuJian / 渔见.

## Principles
1. Frozen visuals are product inputs, not suggestions.
2. Product behavior and visual hierarchy must not be silently redesigned during implementation.
3. Shared tokens/components are preferred over page-local lookalikes.
4. Empty Home and Normal Home are the highest-priority visual baselines.
5. Nature → real catch → memory → information → interaction → data → achievement is the required visual hierarchy.
6. 1080×1920 / 9:16 is the design reference canvas; runtime layouts must remain adaptive.
7. Design source/reference assets do not ship inside the APK unless explicitly mapped to runtime assets.

## Core UI V1
- Empty Home
- Normal Home
- Recognition Result
- FishRecordDetail
- My Catches
- Fish Guide

System specification: `design/system/core_visual_v1/YuJian_Core_Visual_System_V1.md`.

## Empty Home V2 runtime source

`design/pages/home/empty_home` is the frozen Empty Home V2 design-to-runtime source of truth.
It contains the original visual source, reproducible reusable masters, contracts, validation
outputs, and Android evidence. The Android runtime is the only platform represented by this V2
delivery; no iOS or HarmonyOS readiness claim is implied.
