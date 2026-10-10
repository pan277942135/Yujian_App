# 渔见公共设计系统 · 已冻结 SVG V1
**冻结范围：6 个 SVG 设计母版**（2026-10-10）。Icon Library 整体仍 PARTIAL。

| Icon ID | 名称 | SVG 资源 | SHA-256 | 字节 |
|---|---|---|---|---:|
| `ic_length` | 长度 | [查看 SVG](assets/ic_length_v1.svg) | `7b34302b330a869b1dd5cf2170035c19e6f85c7c2978dc0b7a058d42cb5fbedc` | 558 |
| `ic_weight` | 重量 | [查看 SVG](assets/ic_weight_v1.svg) | `ff9692ec1492dbd74edf3f2d70af6461f046d07ccf02a2171387305cad52bd2f` | 812 |
| `ic_location` | 地点 | [查看 SVG](assets/ic_location_v1.svg) | `cefb2c24e7d8a53b2af4cda9eaae1b3e89b91cb8eff2072dfa447c79123952a8` | 540 |
| `ic_date` | 日期 | [查看 SVG](assets/ic_date_v1.svg) | `934e3887ca403b58269616ee765d5e36c7f2fd23bd4be0f69d6b10d207020b84` | 697 |
| `ic_time` | 时间 | [查看 SVG](assets/ic_time_v1.svg) | `f360aac0b6bb2e0cfa8597e86c58c84aa94724737643c1c967ad66d878aeee59` | 529 |
| `ic_weather` | 天气 | [查看 SVG](assets/ic_weather_v1.svg) | `5a76b80bc159471879d61acdada1111abc9e31c1783b5856882d6a57cc2b6b03` | 931 |

## 冻结与开发边界
1. 六个 SVG 保持用户确认的原始字节与路径，Git blob SHA1 校验全部一致，详见 `frozen_assets_v1.json`。
2. 统一 `24×24 viewBox`，`1.75` 线宽，圆角线性风格，`currentColor`，透明背景。
3. 不为不同页面生成不同大小的图片；Android 根据每页 Frozen 高保真决定实际 dp、颜色和间距。
4. 这是 **FROZEN DESIGN / SVG**，不表示 VectorDrawable 已生成或 Android 代码已替换，更不表示运行截图验收通过。
5. `ic_note` 仍为待用户批准的候选，不纳入本批。
6. 可从 Design Manager「公共设计系统 → 图标 → 01 · 鱼获字段 / 07 · 已冻结 SVG」点击打开每张 SVG 单独放大。
