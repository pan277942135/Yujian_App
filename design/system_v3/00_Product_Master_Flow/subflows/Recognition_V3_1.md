# 识别子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · 当前实现与设计建议分栏核对；不变更阈值或结果语义。

```mermaid
flowchart TD
  A["Camera / Gallery"] --> B["Captured"]
  B --> C["Detecting"]
  C -->|"Eligible fish"| D["Outline"]
  D --> E["Classifying"]
  E -->|"p1 ≥ .45, not tie"| H["High"]
  E -->|"p1 ≥ .45, margin < .12"| M["Medium"]
  E -->|"p1 < .45"| L["Low"]
  C -->|"NO_FISH"| NF["No Fish recovery"]
  C -->|"TOO_FAR / ineligible"| IQ["Image Quality recovery"]
  C -->|"detector/model/crop exception"| TF["Technical Failure · separate"]
  E -->|"classifier exception"| TF
  M --> S["Choose candidate / species selector"]
  L --> S
  H -->|"Save Catch"| HOME["Home"]
  M -->|"after confirmation · Save"| HOME
  L -->|"after manual species · Save"| HOME
  H -->|"Continue Memory"| DTL["Detail?section=memory"]
  M -->|"after confirmation · Continue"| DTL
  L -->|"after manual species · Continue"| DTL
  NF -->|"Retake / Gallery"| A
  IQ -->|"Retake / Gallery"| A
  TF -->|"Retry / recovery"| A
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef recovery fill:#fff0ed,stroke:#b96251,color:#4a1f17;
  class A,B,C,D,E,H,M,L,S,HOME,DTL confirmed;
  class NF,IQ,TF recovery;
```

3+2 定义为三种鱼种结果状态 + 两种图片输入恢复状态，Technical Failure 单独处理。当前 route / state 证据来自 `FishRecognitionPipeline.kt`、`RecognitionStateMachine.kt`、`RecognitionUiState.kt`、`YujianApp.kt`。当前赋值阈值为 p1 .45、候选 margin .12；V1.1 decision 文档写明新的行为建议需单独批准。Result spec 要求 Medium/Low 先明确确认鱼种；保存失败留在 Result 且保留字段。

**语义缺口：** 当前 RR05 / Image Quality 映射含 TOO_FAR/小目标，但并非所有路由均由模糊、曝光等像素质量测量触发。已登记为 P1，需确保解释文本与真实失败原因一致。
