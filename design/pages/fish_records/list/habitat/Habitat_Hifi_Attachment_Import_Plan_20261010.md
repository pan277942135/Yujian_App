# 我的渔境 · 8 张用户高保 PNG 附件核验与 GitHub 导入合同

**日期：2026-10-10** · **只做设计** · **当前状态：8 张完整 PNG 已通过本地检查，但 GitHub 的二进制上传尚未完成。**

## 1. 当前可用的高清附件

| 归属 | 本次附件名称 | 尺寸 | 字节 | SHA-256 |
|---|---|---|---:|---|
| 恢复场景 / `tank_round_mobile_a` | 晨雾湖畔我的渔境.png | 853×1844 | 2989878 | `40aa62b957b2c83117c5775ce37f944d96679d7c04a6afb384ed819bc8815d74` |
| 恢复场景 / `tank_landscape_b` | 湖畔晨光中的我的渔境 (1).png | 1672×941 | 3234263 | `70fc653c23762bf9affaf7b641d28cd140901da21c417fc7ea1b7d4f41d0c73f` |
| 恢复场景 / `tank_round_mobile_b` | 晨光湖畔的我的渔境 (1).png | 1024×1536 | 3098237 | `2a471a67ce15eaa51393696437bd187852f595c8d43706e9b726623053667d63` |
| 恢复场景 / `tank_landscape_a` | 我的渔境：晨光水族馆 (1).png | 1672×941 | 3505490 | `62ff20fac9f43142d7663db97fee0272d8369ff78728e283ed1b4106322a471d` |
| 恢复场景 / `pond_landscape_a` | 晨雾渔境：清晨池塘与鱼群 (1).png | 1672×941 | 3518810 | `1d5ab79b74bbdfdeb8828e7fe06d3200c790f6ce234efb74c550514549142757` |
| 恢复场景 / `pond_landscape_b` | 晨曦下的我的渔境 (1).png | 1672×941 | 3830816 | `e82c2b6735768ff1f5b9af7841eda5e92a4962aae7b4fea030f148e61fe3b59f` |
| 鱼塘阶段替代板 / `pond_levels_alternative_a` | 五级鱼塘自然生态进阶图 (1).png | 1536×1024 | 4223164 | `529a49fed1916ec3abbcb55d549c826e4eef5d46c63d106d6fad093a080cf576` |
| 鱼塘阶段替代板 / `pond_levels_alternative_b` | 五级生态鱼塘演变全景图 (1).png | 1536×1024 | 4275390 | `85d7c116638524df0b62974e3f84c87645f4aa725605764333421d49e9fa1c99` |

**完整导入计划：** `habitat_uploaded_attachment_manifest_20261010.json`。

这八张图片通过了 PNG 签名、IHDR 宽高、Pillow 解码验证与 SHA-256 计算，属于**用户这次作为附件直接提供的完整文件字节**，没有重绘、压缩、重采样或改动图片本体。

### 重要版本区别

6 张附件对应先前登记的 10 张源图中的六个 ID，但**它们的字节大小与先前 Library 原图登记值不同**。新附件确实是完整 PNG，却尚不能证明与历史 Library 对象逐字节完全相同。因此这里分别保留两组来源身份：

- `source_library_path` / `size_bytes`：先前恢复的历史图库记录；
- `received_attachment` / `attachment_sha256`：本次用户上传的可验证版本。

另外两张鱼塘五级对比图是**独立替代视觉参考**，存为 `pond_levels_alternative_a.png` 与 `pond_levels_alternative_b.png`；禁止覆盖已经入库的 `Habitat_Pond_Levels_1-5_Reference.png`。

## 2. 设计系统展示合同

- 【鱼缸 · 圆缸竖屏高保】：本次已有 2 张验证过的完整 PNG 附件；另一张 `tank_round_mobile_c` 仍无完整附件。
- 【鱼缸 · 横屏景观缸高保】：本次已有 2 张完整 PNG 附件；`tank_landscape_c / d` 尚未收到本轮完整附件。
- 【鱼塘 · 自然水域高保】：本次已有 2 张完整 PNG 附件；`pond_landscape_c` 尚未收到本轮完整附件。
- 【鱼塘 1–5 · 阶段参考】：既有权威参考文件不变；本轮另外接收两张**候选替代板**，不得覆盖已发布参考板。
- 【湖泊 1–7 · 阶段参考】：未改变。

**目前只能展示来源与附件指纹，不能展示不存在于远端的 PNG 链接。** 完成远端真正上传后，才可把 `image` 指向仓库 PNG、展示点击放大、填写真实 Git blob、标记 `COMMITTED_VERIFIED`。

## 3. 正式导入与远端 Gate

计划目录：`design/pages/fish_records/list/habitat/reference/recovered/`，八个无重名的英文文件名。

1. 用具备本地二进制访问的 GitHub 上传方式推送完整 PNG，**不要改文件名所映射的字节**。
2. 再从远端取出原始图片核验每一张 `SHA-256 / size / PNG IHDR` 与此合同一致。
3. 验算 Git blob SHA 并登记；源图目录的 `image` / `git_asset_path` 不得提前写成已存在。
4. 更新 `Design Manager` 图片预览为可点击原图，仍保持`DESIGN_ONLY`，除非后续用户选定主页面冻结版。

当前 GitHub connector 只接受文本或 Base64 内容，不能直接读取本次工作区的本地二进制路径，而本地容器访问 GitHub 网络被禁用。因此这次实际上传的 PNG 数量尚为 **0/8**；相关文本规范可先发布，但**发布规范不等于上传图片**。

## 4. 仍然有效的视觉规范

延续 `My_Habitat_Spec_V1.md` 与 `Habitat_Hifi_Asset_Governance_V1_1.md`：

- 鱼缸 3 级（圆缸/玻璃方缸）→ 鱼塘 5 级（自然池塘）→ 湖泊 7 级（双浅湾/岬角分水等地貌）。
- 设计鱼仅代表真实 FishInstance；鱼缸玻璃边界与天然鱼塘的岸线不能混淆。
- 以 Morning Lake 母版 + BG_DATA 控制公共资料界面背景；历史草稿的强太阳、皇冠、强烈发光轮廓和奖章不自动变成冻结设计系统组件。
- **不开发 Android，不做实机，不冻结新增主页面。**
