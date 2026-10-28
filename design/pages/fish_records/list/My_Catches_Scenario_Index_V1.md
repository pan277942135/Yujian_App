# 我的鱼获 · 场景子页面索引 V1

Status: **FROZEN**
Parent: `my_catches_v2`

Design Manager 下的场景子页面不是产品新路由，而是设计状态 / 交互场景的独立 Authority View。

## Frozen scenarios

1. 默认时间线
2. 单日 1–5 条
3. 单日 6–10 条
4. 单日 >10 条
5. 点击搜索 / Focused
6. 搜索有结果
7. 搜索无结果
8. 点击筛选 / F1 顶部内联筛选面板
9. 筛选有结果
10. 筛选无结果
11. Archive Empty
12. Growth Mark 示例
13. 加载 / 加载失败

## Shared authority

- List image: `My_Catches_List_Image_Spec_V1.md`
- Filter: `My_Catches_Filter_Spec_V2.md`
- Search: `My_Catches_Search_Spec_V1.md`
- Timeline: `My_Catches_Timeline_Scroll_Spec_V1.md`
- Growth Mark: `My_Catches_Growth_Mark_Spec_V1.md`
- Empty States: `My_Catches_Empty_States_Spec_V1.md`

All scenario pages inherit:
- Morning Lake / BG_DATA
- FishRecordRowCard
- shared typography / spacing
- chronological archive identity


## 2026-09-29 recovered high-fidelity review

Recovered prior high-fidelity sources:
- 主页面：`湖畔晨曦中的鱼获日志(1).png`
- Timeline：`鱼获时间轴滚动规范展示板.png`
- Filter：`我的鱼获过滤器 V1 产品规格海报.png`
- Empty：`我的鱼获空状态规范图.png`
- Growth Mark：`与自然相遇：Growth Mark V1 设计稿.png`

Reconciled decisions:
- Growth Mark “首次” = **首条某鱼种**，不是用户第一条总鱼获；
- 6–10 条 = 主 Timeline 默认 5 条，可原位展开；
- >10 条 = 主 Timeline 默认 5 条，进入当天鱼获详情；
- Search Focused 保留 `取消`；
- Archive / Search / Filter Empty 都保留底部全局 Camera Button；
- Search / Filter 的恢复 CTA 与 Camera Button 语义分层。

Design Manager 左侧子菜单按高保真稿组织，细场景继续作为 Authority 映射。
