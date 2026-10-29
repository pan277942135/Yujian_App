# Background System V3

**状态：READY · 继承 Morning Lake Background System V1；Frozen PNG 未修改。**

| ID | 主资源 / 来源 | 关键页面 |
|---|---|---|
| BG_ENV_HERO | Morning_Lake_Master_V1；Empty Home 以 Sunrise Hero 作明确 override | Normal Home、Empty Home |
| BG_CONTENT | Morning_Lake_Master_V1；15% veil / 91% saturation / 89% contrast / 103% brightness | Recognition Result、FishRecordDetail A/B、Login、Account |
| BG_DATA | Morning_Lake_Master_V1；30% veil / 79% saturation / 78% contrast / 106% brightness | Fish Guide、My Catches、Record Date、My Habitat |
| BG_CAPTURE | 当前用户照片；不 tint，不叠晨湖 | Recognition Flow |
| BG_SOLID | Color Token 暂态 fallback：#F7FAFB / #102D35 | 缺图 / 加载失败，不可作常驻背景 |

Empty Home Sunrise 太阳已在 source master 中；Normal Home 使用晨湖标准 master；Recognition Result 和 FishRecordDetail 用 BG_CONTENT；Fish Guide 与 Habitat/My Catches 用 BG_DATA。B 面继承 Detail。My Habitat 是 design-only，没有 runtime route。源母版 SHA 来自冻结 treatment contract；动态用户媒体的 SHA 不适用。
