# 渔见 AI Android App

> ## 新任务先读
>
> 先读取本仓库 [`PROJECT_GUIDE.md`](PROJECT_GUIDE.md)，再读取项目级主指导文档：
> https://github.com/pan277942135/Yujian/blob/main/docs/PROJECT_GUIDE.md
>
> 本 README 下方包含早期阶段记录，其中“尚未接入真实 TFLite”等描述已经属于历史状态。**当前事实以 `PROJECT_GUIDE.md`、最新 `main` 代码和 CI 为准。**

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

## 当前边界（历史记录）

> 以下内容是早期阶段记录，已经不是当前状态。请以根目录 `PROJECT_GUIDE.md`、项目级主指导文档、最新代码和 CI 为准。

P03 早期曾是完整的识别体验状态流，但当时还没有在本仓库接入生产 `fish_classifier.tflite`；P04 使用过 MVP 示例 Top1（草鱼 / 92%）作为识别输出，并支持用户纠正。后续阶段已经继续推进真实模型接入与 Runtime Parity 诊断。
