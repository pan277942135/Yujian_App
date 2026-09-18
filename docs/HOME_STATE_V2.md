# Home State V2

## 入口

应用启动直接进入同一个 `home` route。会话是否登录只影响头像、账户入口和远程同步，不再决定首页能否打开。

```text
App Start
  ↓
Session Resolver (登录 session 或持久化 guest_xxx)
  ↓
HomeStateResolver
  ├─ 无鱼获 → Empty Home V1
  └─ 有鱼获 → Normal Home V1
```

## 游客模式

- `UserSessionManager` 为设备持久化一个 `guest_<uuid>`。
- `UserSessionState` 预留 `hasSeenIntroVideo`，当前不播放启动视频。
- 游客可拍照、识别、保存和查看鱼获。
- 照片复制到 app-private `filesDir/guest_catches/`，记录保存在 SharedPreferences JSON 中。
- 登录/注册入口由 Empty/Normal 首页右上角提供；首次游客保存后只提示一次注册，不阻断记录流程。

## 首页状态

`HomeStateResolver` 只读取 `CatchStatistics` 和鱼获列表：

```kotlin
if (statistics.totalCatches > 0 || catches.isNotEmpty()) NORMAL else EMPTY
```

Empty Home 保留 V1 的湖面、标题、原相机按钮、相册入口和轻微动效。Normal Home 复用同一背景和相机按钮，新增单张最近鱼获卡、统计和游客占位头像；不使用 Carousel，不复制公共资源。

## Normal Home 数据

最近鱼获卡只使用真实 `RemoteCatch.imageUrl`。游客记录使用本地 `file://` 图片，登录记录继续使用现有 API URL；图片展示使用 `ContentScale.Fit` 和鱼获卡叠加资源，避免简单居中裁剪导致鱼头/鱼尾丢失。

## 资源

`assets/home_normal/` 直接接入 `YuJian_home_normal_assets_V1_addon.zip`。公共背景、相机按钮、呼吸动画和音频仍来自既有 Empty Home V1，音频默认关闭。
