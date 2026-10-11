# Asset Registry V3

**状态：REVIEW · 658 项。** 每项的 path / type / size / source / used_by / SHA 信息见 JSON。

- Phase 1 verifier 在 checkout 中核验了 **658/658** Git blob SHA-1 与大小、579 个实算 SHA-256、144 个直接声明 SHA-256。
- 本 registry 可记录 579 个实际 SHA-256 + 29 个仅有且已验证的声明值；**50 项没有登记或声明的 SHA-256**。这 50 项仍有 Git blob SHA-1，可追踪仓库对象；不得把 SHA-1 填入 sha256 字段。
- 43 SVG 母版的 SHA 完整（Icon Registry）。Morning Lake 两个背景 master 的 SHA 完整；Empty Home、Fish Guide、FishRecordDetail、Habitat 等重点 Frozen / reference PNG 中有 SHA 仍缺失。Normal Home master 与 Default Avatar variants 有 SHA。
- 所有 658 项的 Git 对象身份均可追溯；状态按来源证据映射到 FROZEN / APPROVED / REVIEW / MISSING / DEPRECATED。无来源证据的缺失或淘汰项不自动标注。
