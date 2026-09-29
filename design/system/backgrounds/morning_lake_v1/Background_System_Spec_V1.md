# 渔见背景系统 V1

Status: **ACTIVE CLOSURE**
Visual world: **Morning Lake**
Reference canvas: **1080 × 1920**

## 1. 核心原则

渔见不是“每页一张湖景”。

所有非拍摄型页面共享一个清晨湖面世界：

```
Morning Lake Master
       │
       ├── BG_ENV_HERO
       ├── BG_CONTENT
       └── BG_DATA
```

Recognition Processing 使用 `BG_CAPTURE`，异常状态使用 `BG_SOLID_FALLBACK`。

## 2. Morning Lake Master

视觉语义：

- 清晨，而非黄昏；
- 湖面偏淡灰蓝绿；
- 远山低对比；
- 薄雾；
- 光线柔和；
- 暖金只能作为稀疏晨光，不形成橙黄色主色；
- 自然、安静、克制、纪录片感。

禁止：

- 旅游海报式阳光；
- 强 HDR；
- 高饱和青蓝湖水；
- 橙红晚霞；
- 夜景 HUD；
- 页面之间出现不同地貌 / 不同湖世界；
- 为了“好看”给内容页增加更抢眼的太阳或山体。

## 3. BG_ENV_HERO

用途：

- 空首页
- 有数据首页

环境参与叙事，是品牌世界本身。

Treatment:

- base master visibility: 100%
- mist veil: 0–4%
- saturation relative to master: 96–100%
- contrast relative to master: 96–100%
- luminance shift: ±2%
- global blur: prohibited
- dark overlay: prohibited by default
- warm-gold ambience: only existing master light; no new orange wash

页面允许叠加独立的环境动效，例如 cloud / sun beam，但这些不是 Background Master 的一部分。

## 4. BG_CONTENT

用途：

- 识别结果
- 鱼获详情
- Account / Login / Form
- 其他内容型详情页

目标：仍能感知“同一个湖”，但视觉注意力优先给内容。

Treatment target:

- base master visibility: 100%
- MistWhite veil: 12–18%
- saturation relative to master: 88–94%
- contrast relative to master: 86–92%
- luminance: +2% to +5%
- no additional landscape object
- no global hard blur
- card region may receive local readability veil from GLASS system

Background salience target: **Home 的约 70–80%**。

## 5. BG_DATA

用途：

- 我的鱼获
- 鱼鉴
- 高密度列表 / 档案页面

目标：保留品牌世界，但背景退到第三层。

Treatment target:

- base master visibility: 100%
- MistWhite veil: 26–34%
- saturation relative to master: 74–84%
- contrast relative to master: 74–82%
- luminance: +4% to +8%
- mountain detail: visible only as soft structure
- lake texture: low salience
- no decorative sun emphasis

Background salience target: **Home 的约 40–55%**。

## 6. BG_CAPTURE

用途：

- 拍照
- 识别过程
- 与真实照片直接关联的全屏 AI 处理

Authority:

- 用户当前照片 = background
- ContentScale.Crop
- opaque
- no processing tint on original-photo layer
- AI light field / contour / halo are overlays, not background

Fallback when photo is not available:

- deep teal-gray `#102D35`

## 7. BG_SOLID_FALLBACK

只用于没有正常背景输入的瞬时 / 异常状态。

Light content fallback:

- `LakeWhite #F7FAFB`

Dark capture fallback:

- `#102D35`

禁止把 fallback 当正式页面背景长期使用。

## 8. 页面前景与背景边界

Background 系统不包含：

- Empty Home rod / line / bobber / ripple
- catch photo / fish photo
- Hero
- glass card
- navigation
- button
- copy
- account avatar
- recognition AI overlay

这些都属于 page/component layer。

## 9. 迁移规则

Legacy 页面在迁移期间允许保留旧背景资产，但必须在 Design Manager 中明确标记为 migration gap。

禁止：

- 从 frozen screenshot 裁一个新背景；
- 页面自行新生成另一套 lake image；
- 页面复制 Morning Lake Master 后局部重画并继续称为同一版本。

需要改变环境世界时，升级 Background System 版本。

## 10. Freeze Gate

V1 可从 ACTIVE_CLOSURE 升为 FROZEN 的条件：

1. Morning Lake Master hash / dimensions / provenance registered;
2. BG_ENV_HERO reviewed on Empty + Normal Home;
3. BG_CONTENT reviewed on FishRecordDetail + Account/Login;
4. BG_DATA reviewed on My Catches + Fish Guide;
5. Recognition BG_CAPTURE unaffected;
6. no page introduces an unrelated lake world;
7. Design Manager usage map is complete.
