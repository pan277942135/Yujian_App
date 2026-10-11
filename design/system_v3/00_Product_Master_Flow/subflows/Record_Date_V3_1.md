# 记录日期子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · 设计图冻结不等于运行时导航冻结。

```mermaid
flowchart TD
  H["Normal Home · 记录天数"] -.->|"设计目标；Android route 缺失"| M["Month V2"]
  M -->|"月 / 年切换"| Y["Year V2"]
  Y -->|"选月份"| M
  M -->|"选择有记录日期"| D["选中日期 / 鱼获列表"]
  M -->|"无记录日期"| E["空日期状态"]
  M --> S["年月统计合同"]
  Y --> S
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef pending fill:#fff6df,stroke:#b98924,color:#4b3510;
  class M,Y,D,E,S confirmed;
  class H pending;
```

RD01 月、RD02 年图冻结，RD03 交互和统计目标冻结；年图样例顶部 68 与 12 月合计 59 有已知数据例外，动态值必须遵守年度合计 = 月计数和。月/年源 PNG 的 declared SHA 存在，但审计 API 未返回原始大文件字节，当前登记为 `BLOCKED_ACCESS`。当前 `NavHost` 无 Record Date route；My Catches 的 `my_catches/day/{dayKey}` 是另一条日详情路径。
