# YuJian Android｜新任务启动指南

> 本文件是 Android 仓库的新任务入口。
>
> **项目级唯一主指导文档（canonical Source of Truth）位于：**
>
> https://github.com/pan277942135/Yujian/blob/main/docs/PROJECT_GUIDE.md

## 新任务必须先做

```text
1. 读取 Yujian/docs/PROJECT_GUIDE.md
2. 读取本仓库 PROJECT_GUIDE.md
3. 读取本仓库 README.md 与任务相关源码
4. 核对最新 main HEAD
5. 核对最新 CI 状态与当前失败点
6. 以 GitHub 最新代码/CI 为准，不使用历史聊天中的旧状态替代事实
```

## 本仓库定位

`pan277942135/Yujian_App` 是渔见 AI 面向真实钓友的 Android 用户端。

项目分为两大主线：

```text
后台侧：pan277942135/Yujian
→ 内部 AI Model Factory
→ 数据 / 审核 / Dataset / Training / Evaluation
→ YOLO Detector / Species Classifier / Model Release / Feedback Review

前端侧：pan277942135/Yujian_App
→ 真实用户 Android App
→ 拍照识鱼 / 图鉴 / 鱼获 / 用户纠错 / Feedback / 消费级体验
```

## 当前阶段

Android App 仍处于第一版 0→1 产品阶段，离商用 MVP 还有较长距离。

已有真实推理、拍照、相册、Top-K、纠错、Feedback 等核心技术能力，但以下内容仍需要重点优化：

- 信息架构
- UI / UX
- 首页与识鱼主路径
- Camera UX
- 识别中与结果页
- Top-3 / 低置信度交互
- 用户纠错
- 图鉴内容
- 我的鱼获资产体系
- 持久化
- 图片生命周期
- 权限 / 异常处理
- Feedback 稳定上传
- Fish Knowledge 内容 API 与图鉴详情页
- Crash / 性能 / 真实设备测试
- Release signing / 正式 package

**Runtime Parity PASS 只代表 AI 能力可信接入 App，不代表 App 已达到商用 MVP。**

## Fish Knowledge 内容展示

图鉴首页与鱼种详情页从 Backend 的公开只读接口读取内容：

```text
GET /api/v1/fish/species
GET /api/v1/fish/species/{species_id}/detail
```

构建时可通过 `YUJIAN_FISH_KNOWLEDGE_BASE_URL` 指定内容 API 地址；未指定时复用 `YUJIAN_FEEDBACK_BASE_URL`。详情页按 `ACTIVE` 内容渲染列表封面、五张鱼鉴卡、Gallery、结构化知识和视频，并保留鱼获入口与排行榜占位。未配置地址或网络不可用时会明确显示离线预览，不影响本地 Detector / Classifier 推理。

## 当前模型方向

MVP 目标：

> 先把中国钓友最常见约 20–50 类鱼种做深，在真实钓鱼场景下达到可商用识别水平。

识别架构将逐步演化为：

```text
用户原始照片
→ YOLO Fish Detector
→ 鱼体 bbox
→ 合理扩边 / 保留完整鱼体
→ Letterbox / classifier preprocess
→ Species Classifier
→ Top-K / Confidence
```

App 不应自己猜模型输入与预处理 contract；后台发布模型时应提供明确的模型版本、SHA、class map、preprocess/export contract 与 Golden Parity 结果。

## MVP 模型发布策略

当前采用：

```text
后台训练 / 评测
→ Mobile Export
→ Python Runtime Parity
→ Android Runtime Parity
→ PASS
→ 集成 Android
→ App 发新版时一起更新模型
```

当前不做远程动态模型下载、灰度切换和自动 rollback。

规模化以后再考虑：

```text
App 内置基础模型
+ 后台远程下载新模型
+ SHA 校验
+ 版本管理
+ rollback
```

## 当前 P0 技术检查点（2026-08-31）

当前主分类模型：

```text
MODEL_M1_v0.2
```

当前移动模型：

```text
fish_classifier_v0_2.tflite
SHA256: 9575ede5c6c85b850647016d76e8e5175fa9ea6b609c47c83f54b4062e47d14e
```

当前已知问题：

```text
同一 Golden Image：
TorchScript Top-1 = yellow_catfish / index 7
Android TFLite Runtime 当前无法复现
```

常见 RGB/BGR、NCHW/NHWC、ImageNet/0..1 组合已经诊断过，不能解释问题。

下一步必须先比较：

```text
TorchScript 完整输出
vs
Python LiteRT/TFLite 完整输出
vs
Android 同输入 tensor 完整输出
```

若最新 GitHub / CI 已经解决该问题，以最新状态为准，并更新项目指导文档。

## 工程规则

```text
开发
→ 测试
→ commit
→ push
→ CI
→ 返回真实 Commit SHA / CI 结果
```

禁止：

- 只在临时本地修改而不 push
- 用历史聊天中的旧 SHA 当作当前事实
- 未执行测试却宣称 PASS
- Runtime Parity FAIL 时把 APK 能运行当作模型验收
