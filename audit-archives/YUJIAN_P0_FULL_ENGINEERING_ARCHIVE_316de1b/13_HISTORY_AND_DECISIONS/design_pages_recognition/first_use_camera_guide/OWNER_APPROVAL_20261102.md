# 首次拍照引导图片 Runtime 授权记录

**批准日期：** 2026-11-02  
**批准方：** 产品负责人  
**批准范围：** 渔见 Android 首次拍照引导的 8 张独立候选图，用于正式 Runtime 的正确示范图及错误示例；同时将第二轮 AI Studio ZIP 中的两张页面背景纳入该引导。

本记录落实产品负责人对八张候选图的明确指示：这些图片由产品负责人选定并提供给 AI Studio，允许加入 Main 并用于 Guide Runtime。此前设计准备记录中的候选图待审状态仅反映当时状态，由本记录对“候选图是否获准用于 Runtime”这一项作更新。

候选图仍保留 `GENERATED_CANDIDATE` 来源类型，不改称冻结素材，不替代 P01/P02/P03 的三页合同，也不改变 CameraX Preview Ready 门禁。P01/P03 共用横向图组，P02 使用竖向图组；12 个页面/角色槽位对应 8 个唯一 PNG。图片原字节和宽高比例应保留。

## 八张获准候选图

| 方向 | 角色 | Main 设计资源 | SHA-256 | Runtime 资源名 |
|---|---|---|---|---|
| 横向 | 正确示范 | `assets/candidates/horizontal/correct_hero_generated_candidate.png` | `ac8098537f5a2b33fc989479650604483b32fbb161663e944aef8bb20dc98b8e` | `first_use_guide_horizontal_correct_hero.png` |
| 横向 | 鱼头出框 | `assets/candidates/horizontal/fish_head_out_generated_candidate.png` | `c0491f107e19da5753b6b94f9dd980ca9dc290b5e3c710e0e9e805f3a3dcddda` | `first_use_guide_horizontal_fish_head_out.png` |
| 横向 | 鱼体过小 | `assets/candidates/horizontal/fish_too_small_generated_candidate.png` | `0233634df7918b5dc9b1f1fc9a9db1c37addf28efa07c849cb5710c65a2e3a0d` | `first_use_guide_horizontal_fish_too_small.png` |
| 横向 | 遮挡过多 | `assets/candidates/horizontal/excessive_occlusion_generated_candidate.png` | `3d2d84ec2b2979d75ed62517f4899785b7c2641b96b3f9188b7fafd320d9bacc` | `first_use_guide_horizontal_excessive_occlusion.png` |
| 竖向 | 正确示范 | `assets/candidates/vertical/correct_hero_generated_candidate.png` | `c2d1b3426de3466a8e38885674797bb289db50f3b2e7b30913beab82de64c292` | `first_use_guide_vertical_correct_hero.png` |
| 竖向 | 鱼头出框 | `assets/candidates/vertical/fish_head_out_generated_candidate.png` | `ac202c85d6d2379e168521bc42994c5af82a6216f21700b71a5d07cf2dbc8b31` | `first_use_guide_vertical_fish_head_out.png` |
| 竖向 | 鱼体过小 | `assets/candidates/vertical/fish_too_small_generated_candidate.png` | `4cb42997eb7b01ff480c0fb36db3075c1698203277755f034b0731646829730f` | `first_use_guide_vertical_fish_too_small.png` |
| 竖向 | 遮挡过多 | `assets/candidates/vertical/excessive_occlusion_generated_candidate.png` | `d5efedc6ed1659135d25ac2205c0fc3505b0bd4100039060f51036454af0d2c3` | `first_use_guide_vertical_excessive_occlusion.png` |

## 第二轮 ZIP 页面背景

两张背景从用户提供的 `remix-remix-渔见-1011.zip` 原样导入，记录源文件 SHA-256。P01/P03 共用 P01 横向鱼场景；P02 使用 P02 竖向鱼场景。它们是独立页面背景，不是 Frozen 全页 PNG 的裁图。

| 页面 | Runtime 背景资源 | SHA-256 |
|---|---|---|
| P01、P03 | `first_use_guide_bg_p01.jpg` | `544bbf91245ea0bc1a47fd5744effcd25b93f70748de926af1dd41c01e6498cf` |
| P02 | `first_use_guide_bg_p02.jpg` | `75c61cd08ed4a7bf9fd522cdbaff564928a00198ccd96f54d65aa9ef00d8d823` |

候选 PNG 使用完整内容适配，不做语义裁切；背景图片按整屏背景适配。此授权不代表真机 Runtime 验收完成：API 28 Runtime 保持 `NOT_RUN`。


---

## 活动范围修订 · 2026-11-03（V1.2）

产品负责人将活动引导明确收敛为 P01/P02 两步，手机两步均保持竖屏：
- P01：竖屏手机 + 横向鱼体，使用横向 4 张候选图。
- P02：竖屏手机 + 竖直鱼体，使用竖直 4 张候选图。
- 原 P03「横屏手机 + 横向鱼体」不再是活动页面；原 P03 Frozen PNG 仅保留历史档案。
- 活动页面/角色槽位为 8 个；八张候选图片原始字节和 2026-11-02 Runtime 授权不变。

本补充只收敛活动使用范围，不改变原始素材 SHA、尺寸、字节或候选来源类型。当前有效合同见 First_Use_Camera_Guide_Spec_V1_2.md 与 PRODUCT_OWNER_DECISION_20261103.md。
