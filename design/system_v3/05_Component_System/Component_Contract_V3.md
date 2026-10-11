# Component Contract V3

**状态：REVIEW。** Registry 每条均含 ID、Visual、Geometry、State、Token dependency、Asset dependency、Responsive rule、Acceptance rule。

| 类别 | Component ID | 当前源合同 | 状态 |
|---|---|---|---|
| Navigation | top_title / back / action | Title Only / Back Title / Back Title Actions V1 | FROZEN |
| Action | primary_button / text_action / icon_action | Action Button V1.1 / Text Action V1 / Icon Action V1 | FROZEN |
| Surface | glass_surface / card | GLASS_A/B/C、Result 与页面卡片 authority | REVIEW |
| Media | hero / image / video | YuJianCatchHeroCard / Fish Media Display V1 | Hero/Image FROZEN；Video REVIEW |
| Data | metadata_row / statistic_card | Result metadata、Detail、Normal Home page contracts | REVIEW |
| Input | selector / search / bottom_sheet | Species Picker / My Catches Search / F1 Filter | REVIEW |

页面必须使用 Registry ID 并保留其 authority 顺序。Species Picker 仍为 P2 C-014；通用 Metadata、Statistics、Search、Bottom Sheet 的共用几何 / state / accessibility contract 需要补齐，故 Component 未 READY。禁止 Android 自行替换注册 Icon。
