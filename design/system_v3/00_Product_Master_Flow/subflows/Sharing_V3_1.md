# 分享子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · Template × Time Range 是设计模型；现行 Android 分享能力仍是文本 chooser。

```mermaid
flowchart TD
  D["FishRecordDetail"] -->|"当前 share action"| SYS["系统 text/plain chooser"]
  D -.->|"T01/T02 尚无 runtime route"| T["选择模板"]
  T -->|"T01"| A["战绩卡"]
  T -->|"T02"| B["水边故事"]
  T --> R["选择时间范围：今天 / 本周 / 本月 / 自定义"]
  A --> O["生成 / 分享 / 中断 / 失败处理"]
  B --> O
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef pending fill:#fff6df,stroke:#b98924,color:#4b3510;
  class D,SYS confirmed;
  class T,A,B,R,O pending;
```

Product Model V1 定义两种模板和当前范围，但 T01/T02 visual status 为 PARTIAL，未找到 Android template-selection route。图片/视频 selection、export provenance、share interruption/error recovery 等不作已实现声明。
