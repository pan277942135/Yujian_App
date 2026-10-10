# 渔见 · 公共设计系统 43 图标 SVG V1（正式冻结）
**状态：FROZEN DESIGN · 43/43** · 冻结日期：2026-10-10

## 范围及规则
- 唯一权威：43 张 24×24 SVG 矢量母版、`frozen_assets_v1.json` 的哈希清单、`icon_inventory_v0_1.json` 的语义和页面映射。
- 视觉：`viewBox="0 0 24 24"`，`stroke-width="1.75"`，线性圆角，透明背景，`currentColor` 支持 Tint。
- 文件全部由用户确认的批量审稿包原样迁移（6 张此前已冻结 + 10 张已有候选 + 27 张批量候选）；43/43 Git Blob SHA1 与审稿文件完全相同。
- 不为 16、18、20、24dp 重复导出素材；由各页面 Frozen 高保尺寸、间距、颜色和状态合同决定实际渲染。可点击图标沿用已有 Icon Action V1 的热区/状态约束。
- **冻结是 SVG 设计资源层级，不意味着 Android VectorDrawable、Kotlin 接入、实机截图或高保真验收已完成。** 旧 Android Material Icons 暂未替换。
- 原始候选 SVG 的 XML 注释保留审稿当时的候选状态用语以维护源码哈希；它们的批准后状态以本冻结合同、JSON 登记和当前 Design Manager 为准。

## 全量清单
| ID | 分类 | 图标名称 | SVG | SHA-256 |
|---|---|---|---|---|
| `species` | FACTS | 鱼种 | [源文件](assets/ic_species_v1.svg) | `b51dfda9a060cacae1df54d133335629d77f955e10c79d4068f6aea77ad8947f` |
| `length` | FACTS | 长度 | [源文件](assets/ic_length_v1.svg) | `7b34302b330a869b1dd5cf2170035c19e6f85c7c2978dc0b7a058d42cb5fbedc` |
| `weight` | FACTS | 重量 | [源文件](assets/ic_weight_v1.svg) | `ff9692ec1492dbd74edf3f2d70af6461f046d07ccf02a2171387305cad52bd2f` |
| `location` | FACTS | 地点 | [源文件](assets/ic_location_v1.svg) | `cefb2c24e7d8a53b2af4cda9eaae1b3e89b91cb8eff2072dfa447c79123952a8` |
| `date` | FACTS | 日期 | [源文件](assets/ic_date_v1.svg) | `934e3887ca403b58269616ee765d5e36c7f2fd23bd4be0f69d6b10d207020b84` |
| `time` | FACTS | 时间 | [源文件](assets/ic_time_v1.svg) | `f360aac0b6bb2e0cfa8597e86c58c84aa94724737643c1c967ad66d878aeee59` |
| `weather` | FACTS | 天气 | [源文件](assets/ic_weather_v1.svg) | `5a76b80bc159471879d61acdada1111abc9e31c1783b5856882d6a57cc2b6b03` |
| `note` | FACTS | 感言 / 笔记 | [源文件](assets/ic_note_v1.svg) | `1d6b0284a79011bc9984a823a8720792e30e684e4100b73311449ba3fcc98418` |
| `add_media` | MEDIA | 添加照片/视频 | [源文件](assets/ic_add_media_v1.svg) | `844ed4c546d77e60951c14d0276e3406ef95e68609625b643a2446ab883b7141` |
| `add_photo` | MEDIA | 添加照片 | [源文件](assets/ic_add_photo_v1.svg) | `a26b99da4b287f9fd349713312deb7642c36185c5bf4d052b3bcbc989fcfaa44` |
| `add_video` | MEDIA | 添加视频 | [源文件](assets/ic_add_video_v1.svg) | `6697b99f8dbb29354fb710a7143ae08650a4acd906fd8179469fbb2795e7f5ba` |
| `camera` | MEDIA | 拍照 / 继续拍照 | [源文件](assets/ic_camera_v1.svg) | `5b1f27e123bbf47824bc62883708782a23d27c6dbf679daa35b99b66bbf50965` |
| `gallery` | MEDIA | 从相册选择 | [源文件](assets/ic_gallery_v1.svg) | `5798abbdaad45305fe9ed5a578868247925d2d95644c8ea2559f446c1f069fda` |
| `photo` | MEDIA | 照片 | [源文件](assets/ic_photo_v1.svg) | `cb99c08f0ba8405be876a44396d53c1e6f21929cbfe33226fad5f385d94e6551` |
| `video` | MEDIA | 视频 / 录制 | [源文件](assets/ic_video_v1.svg) | `60b3c0ca1f5eec18a5165e8422918888429d84b842b94b94888c9ba749828272` |
| `play` | MEDIA | 播放视频 | [源文件](assets/ic_play_v1.svg) | `d7911ef4faf912e4137d51f8ea735bf350f04a5bb3fc7585d309bcc9d79d7a14` |
| `voice` | MEDIA | 语音输入 | [源文件](assets/ic_voice_v1.svg) | `3803cec2a271b9d6ec24f629711991f6fa055b864a2932bf0ff825967abf0ba1` |
| `back` | NAV | 返回 | [源文件](assets/ic_back_v1.svg) | `51c04e3263258f75a239392e7292d205ba1af83292f7bd11c104637d41f44d61` |
| `close` | NAV | 关闭 | [源文件](assets/ic_close_v1.svg) | `afce0cfd4c1a8cb627a3494b2b118849452c8ba872921221de2eae60d2b78156` |
| `chevron_right` | NAV | 进入 / 详情箭头 | [源文件](assets/ic_chevron_right_v1.svg) | `3d8c0bf43500c40b636a08f50d6fedf4d5f0ae2fd984a7e6c16cd03f4130d4a9` |
| `expand_more` | NAV | 展开 | [源文件](assets/ic_expand_more_v1.svg) | `e21f71b5ecffcdfff55304d9f3b64d4ce337e9d07ecb8c680b495d8c48f35ffc` |
| `expand_less` | NAV | 收起 | [源文件](assets/ic_expand_less_v1.svg) | `984e7ba7f64ec6e51fb23b774ab5201beac6af2d0ccb7ae91f95c41825b7fa4b` |
| `flip` | UTILITY | 翻面 | [源文件](assets/ic_flip_v1.svg) | `dab0e34e23431364e25b2e2f7be5f497e365f5a476a2ffdfe38477e5dd8729c8` |
| `share` | UTILITY | 分享 | [源文件](assets/ic_share_v1.svg) | `2d3c3db43439d3df3c49ba6c474504ca375db2b976b9b19318753a255166e0b4` |
| `fish_guide` | UTILITY | 鱼鉴入口 | [源文件](assets/ic_fish_guide_v1.svg) | `bb07ef265d14b45ae931eeb5db280c20a2a83f2ba1f0c65a312e25543456bd63` |
| `search` | UTILITY | 搜索 | [源文件](assets/ic_search_v1.svg) | `b14a1d4fe0b9322be3db62390fd017b0e665ef82de951b44daff3a4452cc25c3` |
| `filter` | UTILITY | 筛选 | [源文件](assets/ic_filter_v1.svg) | `c1f58a519a74a87175bccbdbc88c723221f88f18c1c00167fd372a568fba0f82` |
| `more` | UTILITY | 更多 | [源文件](assets/ic_more_v1.svg) | `43ef383a057db30ede0d7a5796799fa60e33e80dd0900c61f6eb93338a90042d` |
| `edit` | UTILITY | 编辑 | [源文件](assets/ic_edit_v1.svg) | `4a300476710448993c2d508e3252989c3f1312c3eb45b68bb847087e8d8d053b` |
| `retry_refresh` | UTILITY | 重试 / 刷新 | [源文件](assets/ic_retry_refresh_v1.svg) | `90331e7d5815f1ab79d1aa82ee275ef5a92b90b22a695aa26ebf077eef6c2fc8` |
| `save` | UTILITY | 保存 | [源文件](assets/ic_save_v1.svg) | `bb31e111d340636e7baf454ea5ccc108f5476a217eff99846c521698f2cbb1d3` |
| `person` | ACCOUNT | 个人 / 用户 | [源文件](assets/ic_person_v1.svg) | `e2dbc2eb3111622590836fd1cda051736dc75611d041f17c6ae1b46aff22d189` |
| `lock` | ACCOUNT | 密码锁 | [源文件](assets/ic_lock_v1.svg) | `4b0bac26e9c7b8c24f91effbd15e3a987b93cd4b146d17919e265ef33eed00cd` |
| `visibility` | ACCOUNT | 显示密码 | [源文件](assets/ic_visibility_v1.svg) | `5c05ab9421590ae2b02c46f68b3adf9261ff7d10758db9b9371d80eb310e3cbe` |
| `visibility_off` | ACCOUNT | 隐藏密码 | [源文件](assets/ic_visibility_off_v1.svg) | `6e12ca6dcc7316e9ba323c0c64f1d81b1a2a72f3fb858a9f25337ecdb1c41dfe` |
| `clear_field` | ACCOUNT | 清空输入 | [源文件](assets/ic_clear_field_v1.svg) | `742958678686749e99cfa97142eee476460d9ec1898b59d67cb6472707262b6a` |
| `logout` | ACCOUNT | 退出登录 | [源文件](assets/ic_logout_v1.svg) | `b12c5245f54a1a4806278560b88e043f832d272e2513ff0b89631a4ee4592dd3` |
| `success` | STATUS | 成功 / 完成 | [源文件](assets/ic_success_v1.svg) | `de9b1147dcfa61fb90d1feea4af85e2031f6d4101e77f835bd5984545aea2920` |
| `warning` | STATUS | 警告 | [源文件](assets/ic_warning_v1.svg) | `17682316ce79b506a3dd1f32d54f4ecfbe32c0bec27d0d29d40abff66b26f2c1` |
| `error` | STATUS | 错误 / 失败 | [源文件](assets/ic_error_v1.svg) | `b06fabf0d270e6173e10d7eb40d32c2f21a18713c1c7895de3a2e3f7f955c704` |
| `help` | STATUS | 说明 / 疑问 | [源文件](assets/ic_help_v1.svg) | `d894b0a88a0d4ca63641b170747fe1447b4736b89a4040b3f245bd288a27f542` |
| `image_missing` | STATUS | 图片不可用 | [源文件](assets/ic_image_missing_v1.svg) | `9013cd2734c3472cfe0aa4aafa44398b3de4682d86b7c10dd1f38730ea765dc5` |
| `selected_check` | STATUS | 已选中 | [源文件](assets/ic_selected_check_v1.svg) | `ba92645ccfbbd517e586012c47578c4ef6b4e0f919bcf92f0ec8875df1e52ea8` |

## 后续开发应用
Android 端应先读取本母版与页面级 Frozen Geometry 合同，再转换适配为 Android VectorDrawable / Compose 资源。不覆盖或删除历史识别图标占位资源，直到对应页面已实际验证。设计资产发布不应触发 Android App 开发或 CI 运行要求。
