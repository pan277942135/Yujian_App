# 渔见 AI Android App

渔见 AI / YuJian 的 MVP 用户端 Android 工程。当前已经把 P01–P08 串成一条可交互的 Jetpack Compose 主流程。

## 已实现页面

- P01 首页
  - 品牌区：渔见 AI / 收藏每一次渔获
  - 拍照识鱼主入口
  - 我的探索
  - 最近鱼获
- P02 拍照识鱼
  - 调用系统相机拍照
  - 从系统相册选择图片
  - 识别前照片预览
- P03 识别中
  - 识别轮廓
  - 分析鱼鳍与嘴型
  - 比对颜色和花纹
  - 2–3 秒状态反馈
- P04 识别结果
  - Top1 结果展示
  - 用户语言表达识别把握
  - 鱼种简介
  - 手动纠正鱼种
  - 保存为鱼获
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

## 已串通主流程

```text
P01 首页
  ↓
P02 拍照 / 相册
  ↓
P03 识别中
  ↓
P04 识别结果 / 人工纠正
  ↓
保存鱼获
  ↓
P07 鱼获详情
  ↓
P08 分享模板

P01 / Bottom Navigation
  └── P05 图鉴 → P06 鱼种详情 → P07
```

## 当前真实交互

- 系统相机：`ActivityResultContracts.TakePicturePreview`
- 系统相册：`ActivityResultContracts.GetContent`
- 相机/相册图片进入 Compose 状态并真实显示
- Bottom Navigation 可真实切换首页 / 识鱼 / 图鉴 / 鱼获 / 我的
- P04 可人工纠正鱼种，保存后 P07 使用当前保存结果
- P08 调用 Android 系统 Sharesheet

## 视觉基线

- 暖米白：`#F7F6F2`
- 水感青绿：`#2E8B83`
- 深墨：`#16313A`
- 柔水色：`#DCEFEB`
- 大圆角：22 / 24 / 28dp
- 轻阴影、贴纸式鱼插画、收藏手账气质

## Android 基线

- Namespace: `com.yujian.ai`
- Preview Application ID: `com.yujian.ai.uiv2`
- Min SDK: 23
- Target / Compile SDK: 35
- UI: Jetpack Compose + Material 3
- Navigation: Navigation Compose

## 当前边界

P03 目前是完整的识别体验状态流，但还没有在本仓库接入生产 `fish_classifier.tflite`；P04 当前用 MVP 示例 Top1（草鱼 / 92%）作为识别输出，并支持用户纠正。下一阶段应把旧客户端已经验证过的生产 TFLite 推理引擎迁入本仓库，用真实推理结果替换这段示例输出。

当前执行环境没有 Android SDK / Gradle 依赖缓存，因此本阶段只做源码、导航和交互链路构建以及 GitHub 文件级检查，不把未执行的 `assembleDebug` 冒充为 Build PASS。
