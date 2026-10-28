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

Normal Home package V1.1 closes NH06: `Morning_Lake_Master_V1.png` is the background bitmap authority and the Frozen NH01 reference remains composition authority. Runtime uses a byte-identical source copy with centered viewport crop.

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

## Recognition Capture · 首次拍照教学 V1

`design/pages/recognition/first_use_camera_guide` 是 Recognition Capture 的首次进入教学 Overlay，已于 2026-10-16 冻结。

Design Manager 二级菜单：
`识别过程 → 07 · 首次拍照教学`

子菜单：
- P01 · 竖屏拍摄示意 — FROZEN
- P02 · 竖拍鱼类拍摄示意 — FROZEN
- P03 · 横屏拍摄示意 — FROZEN
- 资源 — Design Manager 静态资源目录，预览并下载 P01/P02/P03 原始 PNG；不映射到 Android 运行时

顶部 Hero 为唯一正确示范；下方固定 3 个错误示范：鱼头不在画面 / 鱼体过小 / 遮挡过多。P02 的错误示例全部使用竖直鱼体，P03 的错误示例全部使用横向鱼体。P01/P02 使用「下一张」，P03 使用「开始拍摄」；最后的「开始拍摄」仅关闭教学并回到 Capture Ready，不自动触发快门。

视觉权威、源文件 SHA 与冻结规则见 `design/pages/recognition/first_use_camera_guide/manifest.json` 与 `First_Use_Camera_Guide_Spec_V1.md`。

## Recognition Full Flow & Model Responsibilities

The Design Manager entry is `识别过程 → 09 · 识别全流程-和模型`. It includes the flowchart, model responsibility boundaries, quality/size decision branches, and the end-to-end acceptance matrix.

- `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.md`
- `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.json`
- `design/system_v3/00_Product_Master_Flow/subflows/Recognition_Full_Flow_Models_V1.svg`

This is a DESIGN_ONLY technical/business flow reference. It does not add a product Processing state or change the current production model default.
