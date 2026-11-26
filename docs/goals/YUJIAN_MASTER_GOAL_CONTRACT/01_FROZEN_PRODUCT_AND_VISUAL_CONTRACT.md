# 01｜产品、13 Stage、26KF、视觉资产冻结

## 正式时间权威
以 PR #346 中的 [Reveal Timeline Contract V1](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/04_REVEAL_TIMELINE_CONTRACT_V1.md) 为准：
| Stage | ID | 主动呈现最低时长 |
|---:|---|---:|
| 01 | PHOTO_STABLE | 0ms |
| 02 | ORB_WAKE | 350ms |
| 03 | ORB_RELEASE | 600ms |
| 04 | PERIMETER_ARRIVAL | 125ms |
| 05 | PATH_TO_FISH | 125ms |
| 06 | CONTOUR_A | 450ms |
| 07 | CONTOUR_B | 450ms |
| 08 | CONTOUR_C | 450ms |
| 09 | FISH_GOLD_PEAK | 1000ms |
| 10 | ORB_RETURN | 350ms |
| 11 | ORB_TO_NAME | 850ms |
| 12 | GLYPH_REVEAL (Runtime 也称 SPECIES_NAME_REVEAL) | 800ms |
| 13 | RESIDUE_SETTLE / RESULT_HANDOFF | 450ms |
| | **总计** | **6000ms** |

- KF00 HOME、KF01 IDENTIFY 在 SceneClock 之前；KF02 PHOTO_STABLE 为照片稳定第一帧，SceneClock T0；KF25 用户保存动作，不计入 6000ms。
- **模型时间、Scene 主动呈现时间、Motion 连续运动与 Evidence 截屏**是不同的计量域；等待 Gate 时不能伪装已经完成时长。
- 60,000ms 是 **单次 Attempt 必要模型依赖等待上限**，不是正常推理目标，不是 6000ms 总墙钟承诺。
- 辅助 [Non-model Gate Deadline Policy V1](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/docs/contracts/yujian-ai-v2/12_NON_MODEL_GATE_DEADLINE_POLICY_V1.md)：单 Gate 最大 3000ms、AUTO 非模型 Gate 阻塞区间并集上限 10000ms、PixelCopy 独立 1500ms；不得重试续命或用 Evidence 阻塞业务。

## 26 个唯一检查点（禁止 KF01–KF26 重编号）
| KF | 冻结语义 | KF | 冻结语义 |
|---|---|---|---|
| 00 | HOME | 13 | FISH_GOLD_PEAK |
| 01 | IDENTIFY | 14 | ORB_RETURN |
| 02 | PHOTO_STABLE | 15 | WATER_CORE_STRETCH |
| 03 | ORB_WAKE | 16 | HORIZONTAL_STRETCH |
| 04 | ORB_RELEASE 起始 | 17 | WATERLIGHT_DECONSTRUCTION |
| 05 | ORB_RELEASE 传播 | 18 | GLYPH_EMERGENCE |
| 06 | PERIMETER_ARRIVAL | 19 | SPECIES_NAME_FINAL |
| 07 | PERIMETER_COVERAGE | 20 | NAME_STABLE (13.1) |
| 08 | PATH_TO_FISH 起始 | 21 | PHOTO_SETTLE (13.2) |
| 09 | PATH_TO_FISH 抵达 | 22 | RESULT_SCENE_HANDOFF (13.3) |
| 10 | CONTOUR_A | 23 | RESULT_INFO_ARRIVAL (13.4) |
| 11 | CONTOUR_B | 24 | RESULT_SCENE_STABLE (13.5) |
| 12 | CONTOUR_C | 25 | SAVE_CURRENT_CATCH（真实用户保存时适用） |

每个 Attempt 输出固定 26 行，状态仅为 `CAPTURED / CAPTURE_FAILED / NOT_REACHED / NOT_APPLICABLE` 等明示状态。FULL、ORB_ONLY、STATIC_RESULT 与 FAILURE 有不同适用范围，**不能强求 STATIC_RESULT 伪造 KF10–KF24 的 FULL 动画**。

## 视觉不可退让约束
- Orb 是同一颗真实水核：玻璃、内部水体、折射、阳光、水滴、水草、前后景遮挡、真实自转与分层解体；禁止“平面圆 + 文字淡入”冒充 Orb-to-Glyph。
- **G01/G02/A01/A02/C01 是 5 条 Resident 常驻光丝，不是固定 5 条 Task Energy。** Task Energy 分流数量与几何从冻结母版量测，不预设 4 或 5。
- KF06–KF10 必须绑定真实当前 Attempt Mask、PhotoIdentity/GeometryVersion、P* 和 Fish Path；不准固定 BBox、雷达线/HUD、霓虹矩形、固定中文鱼种。
- Stage10 真实 RETURN 消费 Stage05 已冻结的同一三段 Path，不临时重建。Stage11–12 的 Particle/Glyph Source/Target/Rendered Receipt 真实可验。
- Result Stage13.1–13.5 五个有序子阶段，没有独立 Stage14–18 或二次计时。
- AUTO 真实生产画面优先；MANUAL/设计截图不代替 GPU/PixelCopy。

## 历史素材与冲突
- Library 原目录 `/冻结高保/13阶段高保真` 含 `AI_26KF_HighRes_Overview.png` 及多幅独立阶段参考图；另有 `/冻结高保/AI Orb × WaterLight 13阶段冻结/YUJIAN_AI_ORB_WATERLIGHT_13_STAGE_FREEZE_MANIFEST.md`。
- 旧 Library 清单采用**历史 3650ms**、旧独立图片名；后续正式 6000ms 合同已经取代其 Android Runtime 时间权威，原图本身仍为参考，**不得覆盖或删除旧清单**。
- PR #346 已有 [26KF 映射](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/artifacts/26kf_visual_reference_v1/26kf_reference_mapping.json)、[参考资产 SHA/59 个完整性检查](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/artifacts/26kf_visual_reference_v1/reference_asset_manifest.json) 和 [已知缺失源](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/artifacts/26kf_visual_reference_v1/reference_missing_assets.json)；不得将 STRONG/PROPOSAL/MISSING 伪报为独立 EXACT 原图。
- 参考冲突登记：母版物理尺寸为 **8677×5626 RGB**（旧 JSON 曾误写 2048×1327 RGBA）、V2 文件名缺失、KF00/KF01 无独立全屏图、KF14 动态方向证据不全、KF25 保存成功帧缺失、组件级视觉 Tokens 仍有缺口。来源：[reference_conflicts.json](https://github.com/pan277942135/Yujian_App/blob/cafc289c27d7b7fc80d8bbc76183e799f5d28ae9/artifacts/26kf_visual_reference_v1/reference_conflicts.json)。

不能基于当前缺失资产自创几何数值阈值；差异图/Overlay/配准/ROI、真实 GPU/PixelCopy 指标和人工高保真签收见 [06](06_ACCEPTANCE_AND_METRICS.md)。