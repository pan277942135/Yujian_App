# 识别过程 · 07 · 判定逻辑（RR07）

**菜单归属：识别结果 / Recognition Result → 识别过程 · 07 · 判定逻辑**  
**类型：Behavior / Engineering Decision Reference（非第 6 个产品状态）**  
**设计状态：现行实现已核对；下一版候选调整未批准**  
**修改范围：设计系统，只读解释当前 Kotlin 实现；不修改 Android。**

## 开发接入时先读

- [完整解释与审议](../spec/Recognition_Result_3plus2_Decision_Spec_V1_1.md)
- [机器可读决策合同](../spec/recognition_result_3plus2_decision_contract_v1_1.json)
- [Detection Gate / Kotlin](../../../../../app/src/main/java/com/yujian/ai/ai/FishDetectionQualityGate.kt)
- [Detector / Kotlin](../../../../../app/src/main/java/com/yujian/ai/ai/FishDetectorEngine.kt)
- [Classifier / Kotlin](../../../../../app/src/main/java/com/yujian/ai/ai/FishRecognitionEngine.kt)
- [Pipeline / Kotlin](../../../../../app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt)
- [Result Routing / Kotlin](../../../../../app/src/main/java/com/yujian/ai/ui/identify/RecognitionUiState.kt)

> 页面显示的是**截至当前审议时已经执行的代码逻辑**，不是建议随意抄写到 Android 的新阈值。数值阈值未经真实样本校准，不得因本页面而改动。

## 一、3+2 不是“五档信心分”

**3：** HIGH（默认推荐鱼种） / MEDIUM（候选需主动确认） / LOW（无法确认；手动选择）。  
**2：** NO_FISH（未找到可用鱼体证据） / IMAGE_QUALITY（当前检测门不具备安全分类输入）。  
**另有：** TECHNICAL_FAILURE（推理/裁剪运行异常，不属于 3+2）。

一次识别按 **输入是否可信 → 是否能分类 → 鱼种判断是否可信** 顺序判定。

## 二、现行阈值速查

| 指标 | 目前代码 | 注意 |
| --- | --- | --- |
| 检测弱阈值 | `weak ≥ 0.20` | 0.20 包含在弱检测内 |
| 检测强阈值 | `strong ≥ 0.35` | 0.35 属强检测 |
| NMS IoU | `0.45` | 去除高度重叠的检测框 |
| 单鱼面积门 | `bboxAreaRatio < 0.08` → INVALID | **并非所有分支执行到这一门** |
| 贴边阈值 | `0.015`（任一边） | WARNING，仍允许分类 |
| 裁剪扩大 | 宽高每侧扩展 `15%` | 裁剪坐标截断至原图 |
| Low | `p1 < 0.45` | 0.45 恰好不属 Low |
| Medium | `p1 ≥ 0.45 && (p1-p2) < 0.12` | 存在第 2 名时 |
| High | `p1 ≥ 0.45 && ((p1-p2) ≥ 0.12 || 不存在第 2 名)` | 还**没有** `quality == GOOD` 准入要求 |
| 图片模糊/曝光 | **没有**独立像素检测评分 | RR05 文案不等于证明模糊 |

其中 `p1/p2` 是分类器对**模型已知鱼种**计算的 Softmax 分数；不是已校准的“识别正确率”。不要把检测器的 `0.35` 与分类器的 `0.45` 混为一谈。

## 三、运行时优先级（first-match wins）

```kotlin
// AS-IS decision pseudocode, not new runtime implementation
if (failureCode != null)                    return TECHNICAL_FAILURE
if (status == NO_FISH)                       return ERROR_NO_FISH
if (!assessment.isClassifierEligible ||
    prediction == null)                       return ERROR_IMAGE_QUALITY

val p1 = prediction.top1.confidence
val p2 = prediction.candidates.getOrNull(1)?.confidence
if (p1 < 0.45f)                              return RESULT_LOW
if (p2 != null && p1 - p2 < 0.12f)          return RESULT_MEDIUM
return RESULT_HIGH
```

分类前的质量门（**分支顺序不能错**）：

```text
strong: conf ≥ .35       weak: .20 ≤ conf < .35
      ↓
无 strong，存在 weak -> UNCERTAIN / WARNING -> 可分类
无 strong，无 weak  -> NO_FISH / INVALID -> 禁止分类
多个 strong         -> MULTIPLE_FISH / WARNING -> 可分类
单 strong 贴边      -> INCOMPLETE_FISH / WARNING -> 可分类
单 strong 面积 < .08-> FISH_TOO_SMALL / INVALID -> 禁止分类
其他单 strong       -> READY / GOOD -> 可分类
```

**优先分支带来的例外：** 目前弱鱼检测、多强鱼检测、贴边检测在进入分类前可能跳过 `0.08` 面积门。不得误写成“所有面积不足 8% 的鱼都会被挡住”。

## 四、边界样例（值已满足 Softmax 常识约束）

| 输入 | p1 | p2 | 当前结果 | 结论 |
| --- | ---: | ---: | --- | --- |
| GOOD | 0.80 | 0.10 | HIGH | 默认推荐 Top-1 |
| GOOD | 0.46 | 0.30 | HIGH | 绝对值低但差值 ≥.12，属待验证风险 |
| GOOD | 0.46 | 0.42 | MEDIUM | 候选间差距不足 |
| GOOD | 0.31 | 0.21 | LOW | 保留可编辑记录草稿，要求手动选鱼 |
| WARNING / 仅弱检测 | 0.80 | 0.10 | HIGH | 目前仍可能升 High，**待讨论** |
| INVALID / 鱼太小 | — | — | IMAGE_QUALITY | 不运行分类器 |
| 无鱼体（含允许的方向重试） | — | — | NO_FISH | 重拍/选图库 |

### 保存限制

- HIGH：默认 Top-1，但必须允许“修改鱼种”。
- MEDIUM：推荐 Top-K 不等于已确认；明确点选后才能保存。
- LOW：先允许编辑 Metadata/Story；未选鱼种时点双 CTA 进入 LOW_MANUAL，保留草稿及原目标，确认鱼种后才能保存。
- NO_FISH / IMAGE_QUALITY：只展示恢复动作，**不创建**鱼获。
- TECHNICAL_FAILURE：通用安全文案，不怪罪照片，不暴露模型调试信息。

## 五、待审议，不等于已经开发

P0：High 准入可靠性、弱检测或多鱼状态升 High、面积门的一致性。  
P1：图片质量真实原因文案、目录外鱼种拒识、缺失预测的技术异常归因、分类阈值来源统一。  
**不要凭直觉更改 0.45 / 0.12；先按真实鱼获 holdout 统计 High 精度与覆盖率及五态误分流。**

## 六、权威和验收

1. **这张 RR07 是判定逻辑说明页，不是新的 Result 页面视觉稿。** RR01–RR05 的冻结图继续生效。
2. 冻结 PNG 和功能性追踪互相独立；没有新增模型、截图或 APK。
3. UI 判定页面需与 [机器 JSON 合同](../spec/recognition_result_3plus2_decision_contract_v1_1.json) 数字一致。未来数值改动必须合同版本化、源代码和回归测试同步审核。
4. 设计管理器中本页应独立显示，点击菜单直接进入 **07 · 判定逻辑**，不再要求从 RR00 权威链接跳转。
