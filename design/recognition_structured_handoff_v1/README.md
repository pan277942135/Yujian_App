# 渔见 AI 13 阶段结构化设计交接 V1

任务 `YUJIAN_RECOGNITION_STRUCTURED_HIFI_HANDOFF_V1`。基线 Main `74cad95b4f673bd9c8c60d16839f036d5886d933`，PHOTO_STABLE PR #317 Draft HEAD `81a0c257417c0be050e8dd57ace95e97619babf4`。本目录只交接设计和只读工程映射，不修改 Android、不生成 APK。

## 导航

- `visual_boards/G01.png`–`G06.png`：六组现有视觉素材对照；板内的 V3 候选、历史帧和 KF06–KF10 合图均有来源标签，不是最终 21 张冻结稿。
- `stage_matrix/recognition_21_frame_matrix.csv` 与 `.json`：13 个正式 Runtime 主阶段上的 21 个设计子帧及业务门控。
- `component_mapping/component_runtime_mapping.md`：组件、现有 Kotlin 路径、两条模型分支、失败处理。
- `contracts/frozen_parameter_registry.md` 与 `.json`：冻结值、可调项、待审视项及 R06 源哈希冲突。
- `provenance/assets_manifest.json`：现有图片、Mask、路径的仓库路径和 SHA-256。
- `qa/handoff_qa_report.md`：结构验证、审美限制和未闭环项。

## 关键语义

`RecognitionSceneStage` 在当前 Main 中只有 13 个 enum：`PHOTO_STABLE` 至 `RESIDUE_SETTLE`。11.1–11.3 投影到 `ORB_TO_NAME`；12.1–12.3 投影到 `SPECIES_NAME_REVEAL`；13.1–13.5 投影到 `RESIDUE_SETTLE` 的设计进度。**21 是视觉关键帧数，不是 21 个 Runtime stage 或独立时钟。**实际子阶段时间分配 `PROPOSED`，不能覆盖已有业务事件门控。

V3/V3.1 图仅是对照资料：V3 鱼体 Polygon 未验证，V3.1 分层试排未通过视觉 QA；KF06–KF10 原合图中的人物不同于本次确认的蓝帽黑卫衣照片。运行时照片和鱼种名称必须取本次真实识别数据。上传照片目前是 1173×1536 相册截图，原始高清文件 `PENDING_SOURCE_ASSET`。

## 开发使用边界

交接可指导 13 阶段事件、现有资源复用、FULL/ORB_ONLY 分支和会话隔离的工程设计；**R06 同名文件 SHA 与 V1.1 权威 JSON 不符**，需对齐精确来源后才可称为冻结资源。S3 凝字和结果页精确几何仍需产品审视，不能从诊断稿抄入 Runtime。产品视觉冻结状态仍未通过。

复现矩阵与对照板：先从 Library 的 `YUJIAN_21_FRAME_CANDIDATE_REVIEW_V3.zip`（`libfile_cbc3fc8b8aac81919a124ea7e97c5027`）恢复本地 `design/recognition_flow/hifi_freeze_v1/`；将 KF06–KF10 合图（源 SHA `f92cc11c…`）放在脚本标注的本地参考路径，然后运行 `build_handoff.py` 和 `build_registry.py`。`validate_handoff.py` 仅用本仓库已追踪资源与本目录交付物独立验证；未入仓库的 V3 源文件在 manifest 中标为 `LOCAL_V3_PACKAGE_NOT_YET_TRACKED_IN_MAIN`，不得误称远端可直接读取。当前已发布六张对照 PNG 与 SHA 记录可直接审阅。
