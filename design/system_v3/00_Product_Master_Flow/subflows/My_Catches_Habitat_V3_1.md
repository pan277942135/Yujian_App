# 我的鱼获 / 我的渔境子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · 13 个 Scenario Page 仍纳入索引，其中包含 Timeline、搜索、筛选、空态与 Loading / Error。

```mermaid
flowchart TD
  H["Home · 全部鱼获"] --> T["My Catches Timeline"]
  T -->|"Record card"| D["FishRecordDetail"]
  T -->|"Capture CTA"| C["Identify"]
  T -->|"同日 >10 条"| DD["My Catches day detail"]
  T -->|"搜索 / 筛选"| Q["同页查询状态"]
  T -.->|"Habitat 只有设计入口 / asset"| HAB["My Habitat"]
  HAB --> P["鱼缸 3 / 5 级"]
  HAB --> L["湖泊 7 级"]
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef pending fill:#fff6df,stroke:#b98924,color:#4b3510;
  class H,T,D,C,DD,Q confirmed;
  class HAB,P,L pending;
```

主页面、Timeline、搜索、筛选、加载错误、空态及 13 个场景按 registry / scenario index 入图。Home → My Catches、记录卡 → Detail、拍摄 CTA → Identify、日详情 route 均见现有代码。My Habitat level reference assets 存在，但注册状态为 DESIGN_ONLY / runtime route 未找到；本轮不把鱼缸、鱼塘、湖泊绘成已可达 Android 页面。
