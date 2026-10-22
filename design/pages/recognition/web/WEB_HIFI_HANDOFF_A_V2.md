# Work B Handoff A — AI 渔获识别 Web 页面骨架

状态：`VISUAL_CONTRACT_DEFINED`；`MAIN_ADOPTED=YES (DESIGN_CONTRACT_ONLY)`；`PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`；`RUNTIME_VISUAL_VERIFIED=NO`。#186/#187 的设计合同内容经 PR #190 纳入 Main；两者仅保留来源关系，不再是阻塞依赖。审计基线为 Yujian_App `main@ad8bf1ba61e42a34d1fdd91de47fa386e25f11ef` 和 Yujian PR #177 HEAD `655c4af07a0244fcf29441b5652c48ca74f2e6a2`（Draft/Open/Unmerged）。PR #190 发布评论记录实际 adoption SHA 与 Pages 地址；本交接不代表 Work B 代码、真实 Web 视觉或 User UAT 已验收。

## 入口与外壳

入口保留「渔见 AI 智能生产平台 · V1 → AI渔获识别」及既有认证、返回平台导航。产品内容使用独立居中 `catch.product-shell`；进入时平台侧栏收起或让出主内容宽度，返回平台时恢复。产品模块本身不依赖侧栏宽度，官网复用仅替换外层路由、认证和导航适配，不复制上传/处理/结果组件。高级模型诊断默认折叠，普通路径先展示上传。

## 组件树

`catch.product-shell` → `catch.header`（品牌、标题、返回）→ `catch.stage-nav`（上传/识别/结果）→ `catch.stage-layout`：`catch.upload`、`catch.processing`、`catch.result` → `catch.five-step-strip`。上传含 `upload.drop-zone`、`upload.preview`、`upload.file-meta`、`upload.actions`、`upload.error`。处理含 `processing.photo`、七个独立 SWOS 图层、`processing.business-status`、`processing.diagnostics`。结果含 `result.nav`、`result.hero`、`result.species-zh`、`result.confidence-candidates`、`result.metadata`（长度/重量/时间/地点）、`result.story`、`result.dual-cta`、`result.recovery`。

桌面三阶段横向总览由真实阶段状态驱动 active/done/pending；窄屏聚焦活跃阶段，仍可看到阶段导航，已完成内容不伪装成功。不要把三列等宽强制塞进手机，也不要把确认的三阶段关系换成无导航的全屏向导。具体断点与比例属于 Handoff B 的设计补全，未经原稿测量不可称为像素冻结。

底部五节点：`step.upload` 上传照片；`step.water` 水光增强；`step.fish` 鱼体高亮；`step.photo` 照片缩小接续；`step.reveal` 结果揭晓。各节点有 pending/active/done/error；它们只解释视觉进程，不能模拟模型业务进度。缩略图必须指向有授权的同源照片或无照片图示，不能加入未经授权的生成式鱼获截图。

结果页复用冻结的 HIGH 结构及 3+2 状态：`design/pages/recognition/result/engineering/layout_geometry_contract.json`、`design/pages/recognition/result/media/hero_media_contract.json`。360dp 参考 HIGH Hero 322×210dp；中文鱼种名在 Hero 下方，无英文科学名及空行。两 CTA 文案以当前冻结 Result 页面为准；Web「继续记录记忆」无真实目标时呈未开放，不能假跳转。真实模型响应之前不得进入 HIGH。

## Work B 立即可做

1. 在 PR #177 现有模板中抽取独立 Product Shell，保留平台认证与路由，避免侧栏挤压。
2. 保留已有上传、请求与保存逻辑；将用户主界面的模型流程/JSON 迁到默认折叠的诊断区。
3. 用上述稳定 ID 梳理三阶段与五节点 DOM；不要触碰模型阈值或伪造分割。
4. 等待 Handoff B 的 Token、几何、响应式、状态及验收合同再完成视觉收口。
