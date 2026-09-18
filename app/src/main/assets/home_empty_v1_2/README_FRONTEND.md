# YuJian Empty Home Frontend Assets V1.2

这是给前端/Android直接接入的精简运行时资源包，不包含设计稿、预览图、冗余文档。

## 必须使用
- 背景：`assets/background/home_empty_bg_no_bobber.webp`
- 鱼漂：`assets/bobber/bobber_real.png`
- 水波：`assets/ripple/ripple_inner.png` + `ripple_outer.png`
- 拍照按钮：`camera_outer.png` + `camera_inner.png` + `camera_icon.svg`
- 相册图标：`album_icon.svg`
- 登录箭头：`login_chevron.svg`

## 动效配置
- `config/bobber_motion.json`
- `config/ripple_motion.json`
- `config/camera_breath.json`

JSON 是参数契约，前端需要真正用 Compose/动画 API 执行，不是把 JSON 放进工程就会自动动。

## 布局/适配
- 背景必须 Edge-to-Edge，状态栏/导航栏透明。
- 背景使用 Crop。
- UI 内容避让 Safe Area，但背景不避让。
- 鱼漂必须通过 `focal_points.json` 的 anchor 经过实际 Crop Transform 映射。
- 禁止继续使用旧的“带鱼漂背景”，避免双鱼漂。
- Camera Button 必须三层叠加，不能只显示 inner 或 outer。
- Empty / Normal Home 共用同一套 Camera Button。

## 推荐 Compose 层级
Background
→ Ripple
→ Bobber shadow/reflection
→ Bobber
→ Header / Title / CTA
→ Camera Button
→ Album Entry

## 验收
1. 无上下白边
2. 只有一个真实鱼漂
3. 鱼漂偶发轻动并触发一圈淡水波
4. Camera 外圈有轻微呼吸，icon 不闪烁
5. “从相册选择”带图标
6. 16:9 / 19.5:9 / 20:9 / 21:9 不错位
