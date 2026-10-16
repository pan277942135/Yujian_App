# 鱼鉴子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · Fish Guide registry 当前 6 个 hifi views 与 2 个 nested child 已收录。

```mermaid
flowchart TD
  H["Home · 鱼鉴"] --> G["Fish Guide Home"]
  G -->|"点选鱼种"| S["Species Detail / Knowledge Cards"]
  S -->|"记录这条鱼"| C["Identify"]
  S -->|"查看鱼获"| D["FishRecordDetail"]
  S -->|"同种鱼获"| M["My Catches?speciesId"]
  G -.->|"加载 / 离线 / 错误"| F["Screen fallback + retry"]
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  class H,G,S,C,D,M,F confirmed;
```

当前代码已注册 guide 和 species detail route；FishKnowledgeRepository 对应后端 `GET /api/v1/fish/species` 与 `GET /api/v1/fish/species/{id}/detail`。API 是否在特定环境部署、图片内容来源授权是否齐备，属于独立运行与素材来源验证项；本次只记录 main 的代码路径。
