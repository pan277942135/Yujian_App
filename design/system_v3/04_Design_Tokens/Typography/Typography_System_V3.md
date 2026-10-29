> **DEPRECATED_NON_EFFECTIVE** — Historical V3 baseline only. The sole effective typography authority is registered in `Effective_Authority_Registry_V3_1.json` and points to `Typography_Tokens_V3_FINAL.json`.

# Typography System V3

**状态：REVIEW · Android-first baseline；C-007 保持开放。** 字体来源来自设计源码和当前运行时映射，不从 Frozen 图片猜字体。

## Font Family

| 脚本 | V3 基线 | 来源 |
|---|---|---|
| 中文 | Android platform SansSerif，CJK fallback 由 Android OS locale/script chain 选择 | Compose `FontFamily.SansSerif` |
| 英文 | Android platform SansSerif | 同上 |
| 数字 | Android platform SansSerif | 同上 |
| fallback | Android 系统管理的 font fallback chain；仓库未声明捆绑的命名 CJK 字体或字体文件 license | `YuJianTypography.kt` |

跨平台消费者需单独登记 family mapping；不得根据图片命名或更换字体。

## Tokens 与页面检查

通用 Display / Headline / Title / Subtitle / Body / Caption / Button / Numeric token 在 `Typography_Tokens_V3.json`，weight catalog 为 Regular 400、Medium 500、SemiBold 600、Bold 700。全局 tracking 0sp（TextStyle 默认），页面/组件显式 override 优先。

| 页面 | authority / 检查结果 |
|---|---|
| Empty Home | Core Visual V1 定义 BRAND / DISPLAY / UI 角色但未命名字体；Android 代码映射 SansSerif。禁止从 PNG 补猜。 |
| Normal Home | Font A / A1 已批准；72/80、36/42、28/32、48/52、40/44、56/64、46/52、36/42、40/44 是 1080 reference px，不改成通用 sp。 |
| Recognition Result | 使用 Result 状态几何与 Center Title 页面例外；不是全局导航 variant。 |
| FishRecordDetail | 继承共享 family 与 Text Action ON_MEDIA；A/B Frozen 页面仍各自作视觉 authority。 |
| Fish Guide | 大字体要求扩大容器并保全语义；无页面私有字体文件声明。 |

C-007 仍为 P1：Core Visual BRAND 的书法意图、SansSerif runtime 映射、OS fallback 与跨平台 mapping 尚未形成共同验收决定。页面差异已登记，不强行统一；因此 Typography 为 REVIEW。
