# 我的渔境

「我的渔境」是「我的鱼获」里的一个视图入口，与鱼获时间流共用数据。由右上角鱼与水波图标切换进入，不增加独立一级菜单。

Design Manager 当前仅收录已讨论的结构规范，以及鱼塘 1–5、湖泊 1–7 两张既有阶段参考板。主页面高保真还没有选定；本菜单中的阶段板标为 DESIGN ONLY，不作为冻结视觉或运行时素材。

- 方案与交互规范：`My_Habitat_Spec_V1.md`
- 鱼塘阶段参考：`reference/Habitat_Pond_Levels_1-5_Reference.png`
- 湖泊阶段参考：`reference/Habitat_Lake_Levels_1-7_Reference.png`


## 2026-10-10 · 恢复高保源图分组

已检索整理圆缸竖屏3张、横屏景观缸4张、自然鱼塘3张，并在 Design Manager 中新增三个独立子菜单（`habitat/tank_round_hifi`、`habitat/tank_landscape_hifi`、`habitat/pond_landscape_hifi`）。目前 Project 图片可查看但原始字节无法被授权的导出路径读取，**并未上传这些新图片的原始 PNG 到 GitHub**；它们以设计来源目录方式收录，禁止误认为已冻结或已提交的视觉资产。现有鱼塘/湖泊两张阶段板继续可直接预览。详见 [找回高保来源目录](Recovered_Habitat_Hifi_Catalog_V1.md) 和 [机读来源清单](recovered_hifi_sources_v1.json)。


### 高保资产及当前图源边界（V1.1）

[Habitat Hifi Asset Governance V1.1](Habitat_Hifi_Asset_Governance_V1_1.md) 定义了已找回10张原始PNG的图库来源、尺寸和元数据，以及只有取得真实原始字节、完成SHA-256和Git blob校验后才允许在子菜单中标记 `COMMITTED_VERIFIED` 的规则。**目前10张仍为未上传的来源卡；现有两张阶段PNG在仓库可预览。**


## 2026-10-10 · 新接收高保完整 PNG（附件指纹）

已收到用户直接上传的**8 张完整 PNG 附件**，其中六张是已有鱼缸/鱼塘历史设计的同名对应版本，另外两张是鱼塘五级对比替代图。PNG结构、尺寸、完整文件SHA-256已在工作区核验。与先前资料库版本的字节大小不同，因此分别保留历史来源和本次附件版本身份。

[8张高保附件清单及严格入库规范](Habitat_Hifi_Attachment_Import_Plan_20261010.md) · [附件机读哈希清单](habitat_uploaded_attachment_manifest_20261010.json)。

**GitHub 远端二进制上传尚未完成，图像子菜单不可误标已上传；现有鱼塘1–5/湖泊1–7图照常保留。**
