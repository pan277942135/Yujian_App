# 首页子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · 来源：`app/src/main/java/com/yujian/ai/YujianApp.kt`、Home authority docs。

```mermaid
flowchart TD
  A["App → Home"] --> B{"有效 FishRecord 已解析"}
  B -->|"0"| E["Empty Home"]
  B -->|"≥1"| N["Normal Home"]
  B -->|"未解析 / 失败"| L["Resolving / stale/error behavior"]
  L -->|"已解析结果仍有效"| E
  L -->|"已解析结果含记录"| N
  E --> C["Camera → Identify"]
  N --> C
  E --> G["Gallery → Identify?openGallery=true"]
  N --> G
  E --> F["Fish Guide"]
  N --> F
  E --> M["My Catches"]
  N --> M
  N --> R["Recent Catch → Detail"]
  E -.->|"记录天数入口未接入"| D["Record Date V2"]
  E -->|"游客 Profile"| A1["Login"]
  N -->|"访客 / 已登录 Profile"| A1
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef pending fill:#fff6df,stroke:#b98924,color:#4b3510;
  classDef partial fill:#fff6df,stroke:#b98924,color:#4b3510;
  class A,E,N,C,G,F,M,R,A1 confirmed;
  class D pending;
  class L partial;
```

首页状态合同确认 EMPTY = 已解析且 0 条有效 FishRecord；NORMAL = 已解析且至少 1 条。登录 / 游客与 Empty / Normal 正交。NH02 是 1 / 1 / 1 第一条记录的 Normal Home 高保；NH01 是多记录主画面；NH07 头像状态为 SPEC_FROZEN，整页 runtime 尚未更新。Manager 直接路由见 `#page/home_normal_v1/hifi/NH01–NH07`。

**待确认：** Normal Home “记录天数”应进入 Record Date 的设计目标冻结，但 Android 仍未实现该入口；本轮不新增 route。
