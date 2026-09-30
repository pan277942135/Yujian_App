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
8. For page-state expansion, **关键内容独立出图；辅助状态、组件状态、边界状态按内容合并出图**.

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

## Normal Home V1 design package

`design/pages/home/normal_home` is the Design Frozen page package for the Home state with one or more valid FishRecords.

Its canonical visual remains `design/system/core_visual_v1/reference/normal_home_v1.png`.
Behavior, visual interpretation, motion, haptic, sound, assets, authority ordering and freeze status are versioned inside the page package.

Design Manager 二级菜单：
`design/pages/home/normal_home/navigation.json`

Current Normal Home menu:
- NH01 主页面｜多鱼获状态 — current Frozen visual authority
- NH02 第一条鱼首页 — independent Hi-Fi to add
- NH03 页面状态与异常 — combined board
- NH04 组件状态与内容边界 — combined board
- NH05 响应式与交互 — combined board
- NH06 背景与环境权威 — authority board / Morning Lake source relationship

The repository now contains `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png`. Normal Home page composition remains governed by the Frozen NH01 reference until the NH06 background-authority revision is formally closed.

## Recognition Result V1 design package

`design/pages/recognition/result` is the Design Frozen 3+2 package for the Recognition Result bridge.

Design Manager 二级菜单：
`design/pages/recognition/result/navigation.json`

Current Recognition Result menu:
- RR00 结果总览 — 3+2 shared rules / final closure board
- RR01 高置信结果 — independent Frozen Hi-Fi
- RR02 中置信结果 — independent Frozen Hi-Fi
- RR03 低置信结果 — independent Frozen Hi-Fi
- RR04 未检测到鱼 — independent Frozen Hi-Fi
- RR05 图片质量不足 — independent Frozen Hi-Fi

The five state-level Frozen references remain under `design/pages/recognition/design/05–09`.
The system-level `recognition_result_v1.png` defines shared Result visual language, while the five state references define state-specific composition.

Design closure and Android Runtime alignment are tracked separately.
