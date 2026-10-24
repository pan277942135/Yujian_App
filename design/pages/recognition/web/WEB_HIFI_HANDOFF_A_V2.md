# Work B Handoff A — AI 渔获识别 Web 页面骨架

状态：`VISUAL_CONTRACT_DEFINED`；`MAIN_ADOPTED=YES (DESIGN_CONTRACT_ONLY)`；`PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`；`RUNTIME_VISUAL_VERIFIED=NO`。#186/#187 合同已由 PR #190 纳入 Main。本交接不代表部署视觉、生产 Mask 或 User UAT 已验收。SWOS Web 播放器架构与验收统一由 [SWOS Web Hi-Fi Motion Technical Contract V1.0](SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md) 定义。当前审计：产品 PR #177 HEAD `5aa44cac6c8895ab28566ba1f5f9baf40953065e`，产品 main `15d4d1152cce08a3786d51bf1285381ed75d97e6`，47 ahead / 0 behind；精确 SHA CI [#40053248593](https://github.com/pan277942135/Yujian/actions/runs/40053248593) 通过定向测试、离线 probe 单测和 Chromium/UI simulation。候选 Revision `yujian-model-factory-console-00349-hes` 仍绑定旧 SHA `6da640859f063d7f42ee5432e6b35ecb162802ff`；上次真实照片探测因未登录（401）和素材限流（429）失败，真实推理/Mask 与视觉验收仍未验证。产品 PR #177 保持 Draft/Open/Unmerged。

## 入口与外壳

入口保留「渔见 AI 智能生产平台 · V1 → AI渔获识别」及既有认证、返回平台导航。产品内容使用独立居中 `catch.product-shell`；进入时平台侧栏收起或让出主内容宽度，返回平台时恢复。产品模块本身不依赖侧栏宽度，官网复用仅替换外层路由、认证和导航适配，不复制上传/处理/结果组件。高级模型诊断默认折叠，普通路径先展示上传。

## 组件树

`catch.product-shell` → `catch.header`（品牌、标题、返回）→ `catch.stage-nav`（上传/识别/结果）→ `catch.stage-layout`：`catch.upload`、`catch.processing`、`catch.result` → `catch.five-step-strip`。上传含 `upload.drop-zone`、`upload.preview`、`upload.file-meta`、`upload.actions`、`upload.error`。处理含 `processing.photo`、七个独立 SWOS 图层、`processing.business-status`、`processing.diagnostics`。结果含 `result.nav`、`result.hero`、`result.species-zh`、`result.confidence-candidates`、`result.metadata`（长度/重量/时间/地点）、`result.story`、`result.dual-cta`、`result.recovery`。

桌面三阶段横向总览由真实阶段状态驱动 active/done/pending；窄屏聚焦活跃阶段，仍可看到阶段导航，已完成内容不伪装成功。不要把三列等宽强制塞进手机，也不要把确认的三阶段关系换成无导航的全屏向导。具体断点与比例属于 Handoff B 的设计补全，未经原稿测量不可称为像素冻结。

底部五节点：`step.upload` 上传照片；`step.water` 水光增强；`step.fish` 鱼体高亮；`step.photo` 照片缩小接续；`step.reveal` 结果揭晓。各节点有 pending/active/done/error；它们只解释视觉进程，不能模拟模型业务进度。缩略图必须指向有授权的同源照片或无照片图示，不能加入未经授权的生成式鱼获截图。

结果页复用冻结的 HIGH 结构及 3+2 状态：`design/pages/recognition/result/engineering/layout_geometry_contract.json`、`design/pages/recognition/result/media/hero_media_contract.json`。360dp 参考 HIGH Hero 322×210dp；中文鱼种名在 Hero 下方，无英文科学名及空行。CTA 文案按冻结 Result V1.1 页面级合同统一为「继续记忆」与「保存本次鱼获」；该页面级权威优先于通用按钮示例「继续记录记忆」。Web 记忆目标路由未开放时禁用并解释，不得假跳转。Web 只消费产品 API 的 result_state，不在客户端重新计算置信度状态。当前产品 Result 状态映射的 0.45 低置信与 0.12 候选差距规则保持不变；推理 API 的 0.55 low_confidence 标记属于另一接口字段，不覆盖 result_state。真实模型响应之前不得进入 HIGH。

## Work B 立即可做

1. 在 PR #177 现有模板中抽取独立 Product Shell，保留平台认证与路由，避免侧栏挤压。
2. 保留已有上传、请求与保存逻辑；将用户主界面的模型流程/JSON 迁到默认折叠的诊断区。
3. 用上述稳定 ID 梳理三阶段与五节点 DOM；不要触碰模型阈值或伪造分割。
4. 按 Handoff B 和 SWOS Web 技术合同 V1.0 的 Token、几何、响应式、状态、15 帧播放、Mask 信任门与性能证据要求完成视觉收口；复用现有 SAM 实验链路，不新增重复分割服务。


## 原始视觉资产补档 V1
用户确认原图与历史14帧海报已核验，见 `VISUAL_REFERENCE_MANIFEST_V1.json`。使用 Design Manager 对应菜单中的本地查看器，不再用其他候选图替代原图。公开权限 NOT_CONFIRMED；像素 Runtime 未验收；机器业务合同保持。
