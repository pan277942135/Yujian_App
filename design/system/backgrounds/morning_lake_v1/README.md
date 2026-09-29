# 渔见 Morning Lake Background System V1

Status: **ACTIVE CLOSURE**
Version: **V1**
Scope: **Shared background authority**

## 目标

统一渔见 APP 的环境基底。所有湖景页面属于同一个清晨湖面世界，不再由页面各自拥有一套独立背景。

公共背景系统只负责：

- 环境世界；
- 背景强度；
- 内容可读性处理；
- 背景兜底。

页面仍负责：

- 页面布局；
- 页面内容；
- 用户照片；
- 鱼获 Hero；
- 页面专属前景物体；
- 页面专属动效。

## 母版

Canonical candidate asset:

`assets/Morning_Lake_Master_V1.png`

- Canvas: 1080 × 1920
- Format: PNG RGB
- SHA-256: `48956004ca9fad9573156f90f3423efd33e9e2dfe1e1260985fd4caa43b01a22`
- Binary provenance: exact reuse of Empty Home `scene_base_master.png`
- Source authority: `Empty_Home_Final_Design_V2`
- Production rule: only UI / rod / line / bobber / ripple regions were cleaned; **no scene redesign**

The master is the common environmental source. It contains no page UI and no Empty Home fishing foreground.

## 变体

- `BG_ENV_HERO` — 空首页 / 有数据首页
- `BG_CONTENT` — 识别结果 / 鱼获详情 / Account / Form
- `BG_DATA` — 我的鱼获 / 鱼鉴 / 高密度信息页
- `BG_CAPTURE` — 识别过程，使用用户当前照片
- `BG_SOLID_FALLBACK` — 无图 / 加载失败 / 异常兜底

Detailed treatment authority:

`Background_System_Spec_V1.md`
`treatment_contract.json`

## 不属于 Background Master 的内容

以下内容禁止烘焙进 Morning Lake Master：

- 鱼竿
- 鱼线
- 鱼漂
- 水波
- 用户鱼获照片
- 卡片
- Logo / 文案
- 导航
- 按钮
- 页面级渐变遮罩
- AI 光场 / contour / halo

## Authority rule

1. Morning Lake Master V1 controls the shared environmental world.
2. The selected BG variant controls environmental salience.
3. Page frozen references control layout, information hierarchy and page-specific foreground.
4. If a legacy page contains a different lake-world asset, it is a migration gap, not a new background authority.
5. Recognition Processing is explicitly outside lake-background rendering and uses `BG_CAPTURE`.

## 当前状态

The source master is provenance-locked and reusable.

The **system remains ACTIVE_CLOSURE** until the three lake treatment variants are visually reviewed across:
Empty Home, Normal Home, FishRecordDetail, My Catches, Fish Guide and Account/Login.
