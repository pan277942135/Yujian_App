# Work B Handoff B — Web V2 全量视觉合同

**状态** `VISUAL_CONTRACT_DEFINED`；`MAIN_ADOPTED=YES (DESIGN_CONTRACT_ONLY)`；`PIXEL_FROZEN_SOURCE=NOT_CONFIRMED`；`RUNTIME_VISUAL_VERIFIED=NO`。PR #186/#187 的合同内容由 PR #190 采纳到 Main。本页是实现交接，不是运行验收或 User UAT 报告。SWOS Web 播放器架构与验收现由 [SWOS Web Hi-Fi Motion Technical Contract V1.0](SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md) 统一约束。当前审计的产品 PR #177 HEAD 为 `4de911dc497c150545d938b60b2694f3c4723e37`，产品 main 为 `6a2064c2a02a76437b6e09a5241bc199cfa05048`，分支 22 ahead / 3 behind；对应 CI run [39922218177](https://github.com/pan277942135/Yujian/actions/runs/39922218177) 通过，但实际预览、播放、Mask 和视觉验收仍未验证。Work B 实施前必须同步产品分支并重新 Admission/CI。

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
- Result CTA「继续记忆」与任务文案「继续记录记忆」、后端 0.55 与 App 0.45 的差异须由权威来源解决，不由 Web 视觉代码自行定夺。


## 原始视觉资产补档 V1
用户确认原图与历史14帧海报已核验，见 `VISUAL_REFERENCE_MANIFEST_V1.json`。使用 Design Manager 对应菜单中的本地查看器，不再用其他候选图替代原图。公开权限 NOT_CONFIRMED；像素 Runtime 未验收；机器业务合同保持。
