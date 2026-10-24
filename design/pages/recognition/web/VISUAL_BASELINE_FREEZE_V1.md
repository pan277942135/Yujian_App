# 渔见 AI · Web 最终视觉基准冻结记录 V1

**Baseline ID:** `YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1`  
**Status:** `FROZEN_PRIVATE_SHA_BOUND`  
**Owner decision:** 用户于 2026-10-24 明确要求冻结最终视觉基准  
**Scope:** Web AI 渔获识别页面静态构图基准；不代表 Web Runtime / 动效 / Mask 已验收

## 1. 唯一主视觉基准

| 字段 | 冻结值 |
|---|---|
| Reference ID | `web-three-stage` |
| 原始文件名 | `渔见AI鱼获识别流程展示.png` |
| SHA-256 | `27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2` |
| 原始尺寸 | 1536 × 1024 px |
| 字节数 | 1,804,497 |
| 使用范围 | Web V2 桌面总构图、三阶段关系、品牌区、五节点条带及其相对视觉层级 |
| 来源状态 | 用户确认的私有交接原图；既有交接记录曾验证 SHA / 尺寸 / 字节数 |
| 本次原始字节复核 | `NOT_RECHECKED_THIS_EXECUTION`；不得声称本次重新读取或重新计算过原图字节 |

主基准以 SHA-256、尺寸和字节数共同标识。文件身份冻结不等于允许把原图提交到公开仓库。实际对照前，必须由用户在 Design Manager 的本地查看器中选择原图，并重新验证 SHA-256、尺寸和字节数；任何一项不匹配都必须拒绝比较。

## 2. 不属于主像素基准的参考

| Reference ID | 文件 | 身份 | 规则 |
|---|---|---|---|
| `swos-history` | `渔见AI鱼获识别动画分镜海报.png` | SHA-256 `ea0cf2e5fa84aad64195487cbedaab07b1727e96e87c9b4e661dc3d0fa252c1c`；1672 × 941 px；2,340,788 bytes | 历史 SWOS 14-frame V0.3 方向参考，仅保留溯源用途；不得覆盖现行 15 帧 V1.1 |

现行动效时序唯一依据 `design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json`：Processing 2800ms + RR09 850ms = 名义 3650ms，共 15 帧；R0.5 位于全局 2900ms。历史 14 帧海报不是时序或逐帧像素权威。

## 3. 冻结范围与非目标

**冻结：**
- 原始 Web 三阶段总览图的精确文件身份；
- 以 1536 × 1024 原始坐标空间作为桌面总构图对照基准；
- 原图中可映射的品牌区、三阶段、五节点和总体视觉层级的参考关系；
- 本地浏览器对照必须使用 SHA-256、字节数、解码尺寸三项精确校验。

**不冻结，也不得从这张图推断：**
- 运行时页面已经与原图像素一致；
- 上传照片、模型输出、鱼种文案或真实鱼体 Mask 的像素；
- 15 帧动态渲染的实际光学强度、浏览器帧时间或移动端响应式结果；
- 原图公开发布、CI 工件上传或 Pages 发布权限；
- 旧候选图、历史分镜或自动生成图片替代此基准的资格。

响应式视口使用现有 Web V2 响应式合同验收；不能把 1536 × 1024 桌面参考图拉伸成手机像素基准。

## 4. 私有查看与像素对照协议

1. 在 Design Manager「13 · Web AI渔获识别 · 全量高保真合同 V2」的原始视觉查看器中，本地选择 `渔见AI鱼获识别流程展示.png`。
2. 查看器必须验证上述 SHA-256、字节数与解码尺寸；验证失败时不得显示为已验证基准。
3. 选择当前候选 Web 的完整截图，并记录产品 PR #177 的精确 HEAD SHA、候选 Revision / health SHA、浏览器版本、视口尺寸及截图 SHA-256。
4. 在原始 1536 × 1024 坐标空间核对总构图和可映射区域。不得拉伸、重采样或把不同源图混作比较；响应式视口另按响应式合同核验。
5. 将截图几何差异、视觉观察和来源身份记录为独立验收证据。没有精确源码绑定的截图不得判定 PASS。

此流程只在浏览器本地内存处理用户选择的私有原图；不上传、不持久化，不生成含私有源图的公共截图、视频或 CI 工件。

## 5. 独立状态，不得混为一谈

- `PIXEL_FROZEN_SOURCE=FROZEN_PRIVATE_SHA_BOUND`：**主基准身份和适用范围已冻结**。
- `PUBLIC_PERMISSION=NOT_CONFIRMED`：原图及衍生帧仍禁止公开发布。
- `RUNTIME_VISUAL_VERIFIED=NO`：当前 Web 实际像素对照尚未通过。
- `PLAYABLE_BROWSER_DEMO=NOT_VERIFIED`：真实照片完整播放与逐帧证据仍需验收。
- `PRODUCTION_MASK=NOT_QUALIFIED`：生产级鱼体 Mask 尚未获得资格。
- `USER_VISUAL_ACCEPTANCE=PENDING`：最终视觉验收仍由产品负责人完成。

**冻结结论：** 这份记录冻结的是唯一主视觉基准的来源身份和对照规则。它不替代当前源码部署、真实照片播放、Mask 质量、性能测试或用户视觉签收。
