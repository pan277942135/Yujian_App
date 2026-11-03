# 首次拍照引导 · Android Runtime / Design Authority

当前有效版本：First_Use_Camera_Guide_Spec_V1_2.md  
当前活动页数：2  
手机方向：两步均保持竖屏；不再教学手机横屏拍摄。

## 当前两步教学

| 步骤 | 手机方向 | 鱼体方向 | 正确示范 | 错误示例 | 主操作 |
|---|---|---|---|---|---|
| P01 · 竖屏拍摄示意 | 竖屏 | 横向 | 完整鱼体横向入镜 | 鱼头出框 / 鱼体过小 / 遮挡过多 | 下一张 |
| P02 · 竖拍鱼类拍摄示意 | 竖屏 | 竖直 | 完整鱼体竖直入镜 | 鱼头出框 / 鱼体过小 / 遮挡过多 | 开始拍摄 |

P02 的「开始拍摄」只关闭引导并回到 Capture Ready，不自动触发快门。跳过同样不触发快门。首次引导仍只在 Camera 权限已授予且 CameraX Preview Ready 后展示；相册入口不展示相机教学。

## 图片资源

当前活动 Runtime 使用 8 张独立候选图：
- P01：4 张横向鱼体素材（正确示范 + 3 个错误示例）。
- P02：4 张竖直鱼体素材（正确示范 + 3 个错误示例）。

两张页面背景分别来自第二轮 AI Studio ZIP。八张候选图保留原始字节、比例与 GENERATED_CANDIDATE 来源类型；已获准用于 Android Runtime，但不标记为 Frozen。资源身份、授权与 SHA 由 frontend_assets_manifest.json 和 OWNER_APPROVAL_20261102.md 管理。

原 P03「横屏拍摄示意」及其 Frozen PNG 仅作为历史设计档案保留，不属于活动导航、Runtime 页面、图片槽位或测试路径。

## 响应式与验收

- 手机拍摄教学始终竖屏；设备外层可用视口仍按真实 WindowInsets 和尺寸响应。
- 正确示范使用 contain，完整保留鱼体，不旋转或拉伸素材。
- 3 个错误示例按宽度在三列 / 两列间响应；短屏允许完整内容滚动。
- 先完成单元测试、AndroidTest APK 编译、资源打包核验与 Lint。真机相机验证由产品负责人执行；本轮不执行 API 28 真机验证。

资源核验入口：scripts/verify_first_use_camera_guide_assets.py。
