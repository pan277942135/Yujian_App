# 渔见 AI Android UI V2

这是基于当前 MVP 视觉方向构建的 **真实 Jetpack Compose 页面实现**，用于把 Figma/视觉方案推进到可运行 Android 页面。

## 已实现页面

- P05 图鉴首页
- P06 鱼种详情
- P07 我的鱼获详情
- P08 分享模板中心
  - 单条
  - 今日
  - 本周
  - 本月
  - 本年
  - 累计

## 视觉基线

- 暖米白背景：`#F7F6F2`
- 水感青绿：`#2E8B83`
- 深墨：`#16313A`
- 柔水色：`#DCEFEB`
- 大圆角：22 / 24 / 28dp
- 轻阴影、贴纸式鱼插画、收藏手账气质

## Android 基线

- Namespace: `com.yujian.ai`
- Preview Application ID: `com.yujian.ai.uiv2`（可与现有生产 App 并存；合并回正式客户端时恢复 `com.yujian.ai`）
- Min SDK: 23
- Target / Compile SDK: 35
- UI: Jetpack Compose + Material 3
- Navigation: Navigation Compose

## 当前交互

1. 图鉴首页可搜索鱼名/别名。
2. 支持“全部 / 已发现 / 未发现”过滤。
3. 点击鱼种进入鱼种详情。
4. 草鱼详情可进入我的草鱼鱼获。
5. 鱼获详情可进入分享中心。
6. 分享中心可切换 6 种固定模板。
7. “分享这个模板”会调用 Android 系统 Sharesheet，当前分享结构化文字。

## 下一步

- 将旧客户端生产识别引擎 `fish_classifier.tflite` 与新 UI 合并。
- 接入真实鱼种 Catalog / Catch Repository，而不是 `DemoData`。
- P08 增加 Compose 卡片截图 -> Bitmap -> MediaStore / Sharesheet，实现真正分享图片卡。
- 把 P01 首页、P02 拍照识鱼、P03 识别中、P04 识别结果按同一视觉系统迁移。
- 增加 Compose Preview / Screenshot Test / Accessibility QA。

## 说明

当前执行环境没有 Android SDK/Gradle 缓存，因此本包完成了源码与结构构建，但没有在本环境冒充执行 `assembleDebug`。导入 Android Studio 后可进行 Gradle Sync 与设备运行。
