# 渔见公共设计系统 · 图标系统 V0.1 — 全局盘点
状态：**PARTIAL / Inventory Only** · 2026-10-10

## 盘点结论
对产品已登记页面及关键 Android UI 文件的第一轮代码/冻结设计交叉审计，按**语义 ID 去重**得到 **43 个候选小图标**（不是 43 张已经做好的开发资产）。

| 目录 | 候选数 | 核心内容 |
|---|---:|---|
| 01 · 鱼获字段 | 8 | 鱼种、长度、重量、地点、时间、天气、笔记 |
| 02 · 照片与视频 | 9 | 添加照片/视频、相机、图库、视频、播放、语音 |
| 03 · 导航与层级 | 5 | 返回、关闭、进入、展开、收起 |
| 04 · 页面工具 | 9 | 翻面、分享、鱼鉴、搜索、筛选、更多、编辑、重试、保存 |
| 05 · 账户与输入 | 6 | 用户、密码、可见性、清空、退出 |
| 06 · 状态与反馈 | 6 | 成功、警告、错误、帮助、缺图、选中 |

权威机器清单：`icon_inventory_v0_1.json`。每个候选包含语义 ID、已见 Android 符号/实现、代码/规范来源、页面用途、当前资产成熟度和需解决的问题。

## 特别注意
1. **既有 Icon Action V1 已冻结，不重开**；它规定 Action 交互、44×44dp 热区、Navigation 22dp、Utility 20–22dp、Context 18–20dp。**新「图标」目录负责源图形、静态语义以及跨页面资源追踪**。
2. **鱼种/长度/重量/地点**等事实性小图标是非交互信息，默认无 44dp 单独点击热区。长度和重量的正式候选形态分别参考 `Straighten` 与 `Scale`，不采用旧 `L/W` 字母 SVG。
3. **添加照片/视频**可以使用一个 CONTEXT add_media 入口，也可能需要照片+、视频+独立媒体动作；它们不能与照片缩略图、视频缩略图、播放控件混为一类。
4. **同一 glyph 多种触发语义**：例如 X 在关闭弹层与清除输入中复用外观，但触发场景不同；Search 的输入框前缀可能仅为装饰。
5. 不擅自给 Growth Mark 添加奖杯/等级图标；冻结设计对它的要求是低权重文字印记。
6. 盘点状态 **PARTIAL**，只冻结本次清单的版本快照，不把候选 glyph 直接宣布为设计冻结、源码资产已完成或 Android 实机 PASS。

## 后续资产设计要求（等待审议）
- 每个确认需要的语义图标：标准 `24×24 viewBox` 单色 SVG + Android VectorDrawable XML，版本化、记录 SHA-256；同时给出 ON_LIGHT / ON_MEDIA / DISABLED 适配。
- 16 / 18 / 20 / 22dp 为**使用尺寸**而不是重新绘制 4 份形状；Interactive 仍复用 Icon Action 既有规范。
- 线宽、转角及具体轮廓应通过首批统一样稿验收，再冻结，不在统计阶段宣称已完成。
- 必须有场景对照：识别结果 Length/Weight/Location、详情 Add Media + Camera/Video、我的鱼获 Location/Search/Filter、Auth Eye / Lock、异常 Missing Image。
- 最终资源库应记录 `semantic_id`、`asset_svg`、`asset_android_xml`、`source_sha256`、`license/provenance`、`view_box`、`page_usage`、`asset_status`，并做来源审计与引用检查。

## 参考
- `design/system/components/icon_action/Icon_Action_Spec_V1.md`
- `design/system/components/icon_action/usage_map.json`
- `design/pages/fish_record/detail/FishRecordDetail_Page_States_Spec_V1.md`
- `design/pages/fish_records/list/My_Catches_Growth_Mark_Spec_V1.md`
- Android 代码证据详见 `icon_inventory_v0_1.json`。
