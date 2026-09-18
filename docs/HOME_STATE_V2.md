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

`HomeStateResolver` 只读取鱼获记录列表，不读取登录状态、loading 或统计快照：

```kotlin
if (fishRecords.isEmpty()) EMPTY else NORMAL
```

Empty Home 保留 V1 的湖面、标题、原相机按钮、相册入口和轻微动效。Normal Home 复用同一背景和相机按钮，新增单张最近鱼获卡、统计和游客占位头像；不使用 Carousel、不左右滑卡、不自动播放视频或声音。

## Normal Home 数据

最近鱼获卡只使用真实 `RemoteCatch.imageUrl`。游客记录使用本地 `file://` 图片，登录记录继续使用现有 API URL；图片展示使用放大的柔和背景填充 + 前景 `ContentScale.Fit`，避免简单居中裁剪导致鱼头/鱼尾丢失。

## 资源

`assets/home_normal/` 直接接入 `YuJian_home_normal_assets_V1_addon.zip`；共享动态资源接入 `assets/home_motion/`，来自 `YuJian_home_motion_assets_V1.1_release.zip`。公共背景与标题继续使用 Empty Home 资源，音频默认关闭。

## First Journey 预留

`MemoryEntry` 仅作为未来第一次旅程的领域对象预留，本版本不增加视频、启动视频或首页入口。
