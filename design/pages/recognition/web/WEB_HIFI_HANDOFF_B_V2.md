# Work B Handoff B — Web V2 全量视觉合同

**状态** `VISUAL_CONTRACT_DEFINED`；`MAIN_ADOPTED=YES (DESIGN_CONTRACT_ONLY)`；`PIXEL_FROZEN_SOURCE=FROZEN_PRIVATE_SHA_BOUND`；`RUNTIME_VISUAL_VERIFIED=NO`。本页是实现交接，不是部署验收或 User UAT 报告。当前审计的产品 PR #177 HEAD 为 `8edce561c3b6f25263eb32f1d72706fec4e329b1`，产品 main 为 `738954105d545d9c354136b8b93f3562058fcf30`，分支 49 ahead / 0 behind；对应 CI run [40057399157](https://github.com/pan277942135/Yujian/actions/runs/40057399157) 通过 targeted validation 与 Chromium fixture/UI playback。候选 Revision `yujian-model-factory-console-00349-hes` 绑定旧 SHA `6da640859f063d7f42ee5432e6b35ecb162802ff`；当前源尚未部署，生产 Mask 与视觉验收仍未验证。PR #177 保持 Draft/Open/Unmerged。

## 按顺序实施

1. 从 `WEB_HIFI_HANDOFF_A_V2.md` 在 PR #177 保留既有上传、接口、保存链路，改 Product Shell 与三阶段/五节点结构。
2. 将 `contracts/Visual_Tokens_V2.json`、`Component_Geometry_V2.json`、`Responsive_Rules_V2.json` 映射到 CSS 变量与布局。标记 `DESIGN_COMPLETION_NOT_PIXEL_MEASURED` 的值须对批准原图和真实视口校准，不可误称像素一致。
3. 按 `contracts/Page_State_Matrix_V2.json` 实现上传完整状态、3+2 与独立技术失败，HIGH-only 一次 RR09。模型阈值仅从正式业务合同读取；当前 0.45/0.55 差异记录决策。
4. 按 `contracts/Authority_References_V2.json` 与 `SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.json` 实现七层 SWOS、统一时钟、15 帧与 R0.5；保持真实事件门控，BBox-only 柔光，可信 Mask 后才启用三层精准鱼体金光。SAM 必须复用现有 `pan277942135/Yujian` 实验链路，经服务端 adapter 接入；不新建重复服务、不重训，debug endpoint 不得暴露为公开生产 API。
5. 对照 `SWOS_Shared_Photo_Transition_Contract_V1.json` 将照片、BBox、Mask 和光迹绑定同一照片坐标与过渡矩阵；R0/R0.5/R1 三帧截屏证明连续。R1 落位后才进入 R2–R5 的中文名转译。
6. 复用 Frozen Result Hero Media 与 HIGH/3+2 表单；`WEB_HIFI_GAP_MATRIX_V2.md` G01–G18 逐项打勾并记录真实代码 SHA 与浏览器证据。

## 最小验收

九个参考视口无横向溢出、键盘不遮 CTA、照片不拉伸和不突变；真实请求完成前无鱼种揭晓；MEDIUM/LOW/恢复/技术失败不播放 HIGH 庆祝；无可信 Mask 时没有精准轮廓；显式保存成功有云端响应；Reduce Motion 功能可用。基础合同测试通过不表示模型推理、像素还原或线上发布通过。

## 后续决策

- 确定用户批准 Web 高保真原图的字节身份、SHA-256 和可公开权限，再开展像素测量与对照。
- 合同内容已通过 PR #190 纳入 Main；Work B 实施前仍须读取 PR #190 发布评论所记的实际 Main SHA，并按当前控制面重新 Admission。PR #177 保持 Draft/Open/Unmerged，设计合同采纳不等于产品代码或视觉验收通过。
- **CTA 已收口：** 使用冻结 Result V1.1 页面级合同中的「继续记忆」与「保存本次鱼获」；其优先级高于通用按钮示例「继续记录记忆」。真实记忆路由未开放时禁用并解释，不假跳转。
- **置信度规则已按接口职责收口：** Web 只消费 `POST /api/product/catch-recognition/infer` 的 `result_state`。当前 Result 状态映射的 0.45 LOW 与 0.12 MEDIUM margin 规则保持原样；`app/inference_api.py` 的 0.55 `low_confidence` 是独立诊断标记，不覆盖产品 Result 状态。本次不修改任何模型阈值、质量门或路由；未来校准需产品负责人批准及标注留出集证据。


## 原始视觉资产补档 V1
用户确认原图与历史14帧海报已核验，见 `VISUAL_REFERENCE_MANIFEST_V1.json`。使用 Design Manager 对应菜单中的本地查看器，不再用其他候选图替代原图。公开权限 NOT_CONFIRMED；像素 Runtime 未验收；机器业务合同保持。


## 最终视觉基准冻结补充
Web V2 主视觉基准已按 `web-three-stage` 的 SHA-256 `27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2` 冻结为 `FROZEN_PRIVATE_SHA_BOUND`；完整范围、历史分镜边界和本地像素对照协议见 [VISUAL_BASELINE_FREEZE_V1.md](VISUAL_BASELINE_FREEZE_V1.md)。公开许可仍 `NOT_CONFIRMED`，当前 Runtime / 像素一致性 / User UAT 不因此自动通过。
