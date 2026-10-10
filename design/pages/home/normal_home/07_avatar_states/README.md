# NH07 · 头像状态（Avatar States）· Design Contract V1

**状态：SPEC_FROZEN / 头像资产已登记；页面视觉仍以 NH01 冻结整页为准。**  
范围：Normal Home Header，仅新增头像状态局部工作区；**不修改 Android、冻结整页或其他页面**。设计版本：2026-10-10。

## 设计目标与来源

- 先验收过的整页示意：Normal Home 原 1080×1920 冻结结构，右上头像替换为“清晨山湖垂钓者”。`NH01` 整页其他像素不变。
- 头像画面设计：冷灰蓝的群山与雾湖，岸边深色垂钓者剪影、极小暖金日照，**不可出现 HUD/霓虹、默认首字母、鱼 logo、AI 人脸**。
- 头像包以本次配套的独立高清母图（1254×1254，SHA-256 `12ec5fc8c1a08723ea461edf327870df19b7dfe309587b18b6de28c9f701b643`）下采样输出。**它与整页局部示意属于同一视觉方案，不声称与页面截图局部逐像素相同**。
- 原始 NH01 冻结图 `design/system/core_visual_v1/reference/normal_home_v1.png`，SHA-256 `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`，仍保持不可变。NH07 的头像视觉是该位置的后续**局部设计修订提案**，其余布局不重开。

## 1 · 状态决策矩阵

| 状态 | 条件 | 画面 | 点击 |
| --- | --- | --- | --- |
| A / REAL | 登录，真实头像 URL 已成功解码 | 实际用户照片，圆形裁切；不得用生成的人像冒充 | My / Profile |
| B / DEFAULT | 登录，但未设置头像、URL 无效或响应为空 | **NH07 晨湖垂钓者默认头像**（64/128/192px 的同源资源） | My / Profile |
| C / LOADING | 登录，远程头像尚在加载 | B 的占位图 + 低对比进度环；不得出现 Guest 图或旧用户照片 | My / Profile |
| D / FAILURE | 登录，头像网络错误或解码失败 | 直接回到 B，不叠加错误文字/红点/自动跳转编辑 | My / Profile |
| E / GUEST | 访客或未登录 | **独立的 Guest 已登记资源**，不复用默认个人头像 | Login / Register |

从 `LOADING` 到 `REAL` 仅在下载、解码成功后原位替换；失败返回 `DEFAULT`。登出必须立即切换至 `GUEST`，避免上一账号头像闪现。

## 2 · 图片规格与交付

- 主资源：`design/pages/home/normal_home/07_avatar_states/assets/nh07_default_avatar_3x_192.png`，**192×192 PNG，调色板 RGBA/透明索引**，不包含页面背景，透明外围，圆形画面与内置白色细边；禁止再加第二道白描边。
- 倍率：`@1x 64×64`、`@2x 128×128`、`@3x 192×192`，路径和精确 SHA 见 [asset manifest](assets/avatar_asset_manifest_v1.json)。这是**名义 64px 设计单元**，不是强制页面 64dp。
- 正式画面显示尺寸沿用 NH01 的 **1080×1920 参考画布 92px 视觉圆直径**；实际布局按已批准的 Normal Home 响应式映射。照片用等比 Fill/Crop，不拉伸；默认资产自带圆形透明蒙版时用 Fit，不重复裁掉边框。
- 点击区域需包住视觉圆并满足**宽高各 ≥48dp**，视觉直径和可点击透明容器是不同几何类型。保持 Header 水平/垂直坐标不变。
- 规范化色域为 sRGB，文件不得附加不可信 GPS/EXIF；透明像素必须是真 alpha，不能用白色方块代替透明。
- Asset 工作流：提交先核对尺寸、通道、SHA/文件大小，再更新 Manifest；每个新视觉修改必须升级版本，禁止直接覆盖已登记 SHA。

## 3 · 权威与历史兼容

1. `NH07` 只调整 Normal Home **登录账号缺失/失败头像**的页面局部视觉，不修改 shared Profile Avatar 的其他页面语义。
2. 现有 `app/src/main/res/drawable-nodpi/normal_home_default_avatar_v2.png`（V2）保留历史引用，**NH07 V1 一旦经整页实机视觉复核批准，作为 HOME 专属 V3 资源替代它；不直接删除/修改 V2**。目前尚未实施 Android，因此不能把“设计已登记”说成“App 已更换”。
3. `app/src/main/assets/normal_home_runtime_v1/avatar/guest_avatar.png` 为独立访客来源；`Guest ≠ Signed-in Default`。NH02/NH04 旧冻结图中的 V1/V2 头像图形可作为历史页面示例，其视觉子区由新版 NH07 明确覆盖；**不篡改 NH02/NH04 原 Frozen PNG**。
4. `NH01` 继续决定所有页面元素尺寸和位置；`NH07` 不触及 CTA/Camera、Hero、背景权威或全屏构图。
5. Design Manager 展示的五张卡为**状态规范演示，不是替代 NH01 的新全屏 Frozen authority**；菜单状态为 `SPEC_FROZEN`，直到单独的最终整页视觉与资产一并冻结前不能标 `FROZEN — VISUAL`。

## 4 · 设计验收

- [ ] 五种状态、两种点击目的地正确且不混用账号资源
- [ ] 64/128/192 源图均为透明 PNG，原尺寸正确；SHA 与 manifest 完全一致
- [ ] 圆形细白边在深湖和浅雾环境均可读，最终效果与 NH01 信息层级协调
- [ ] 头像无昵称文字、无额外 BADGE/红点/账号 HUD；24–48dp 下仍可辨认
- [ ] 实际触控 ≥48dp；RTL / safe insets 不改变物理位置计算规则
- [ ] 登录、登出、加载成功/失败无旧头像闪现、无遮挡、无无意义弹窗
- [ ] 后续 Android 与多设备测试标为 `NOT_RUN`，不得因本设计更新虚报通过

## 5 · 相关权威

- `design/pages/home/normal_home/navigation.json` — NH07 二级菜单
- `design/registry/experience_registry_v1.json` — Design Manager 驱动注册
- `design/pages/home/normal_home/assets/asset_manifest.json` — 页面资源映射
- `design/pages/home/normal_home/authority/authority_map.json` — 优先级与历史替代
- `design/pages/home/normal_home/04_component_content_states/README.md` — NH04 旧组件边界
- `design/system/components/profile_avatar_v1/default_profile_avatar_contract.json` — 其他页面共享语义
- `design/pages/home/normal_home/assets/normal_home_default_avatar_v2_manifest.json` — 较早 V2 页面资产
