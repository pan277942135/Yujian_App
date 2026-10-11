# Page / State / High-Fidelity Map · V3.1 Phase 1

**状态：AUDIT_DRAFT** · Baseline: `main@7737f293ea3831aeea2c6f58308961cdb2ba7d01`

## Registry coverage

Current main contains **15 features, 62 top-level hifi/spec views, 70 nested hifi children, 13 scenario pages, and 16 shared design systems**. These are registry counts; incomplete or menu-hidden entries are retained.

| Feature ID | Product task / name | Registry / design status | Hifi | Nested | Scenarios | Readiness |
|---|---|---:|---:|---:|---:|---|
| `home_empty_v2` | 空首页 | FROZEN / FROZEN | 0 | 0 | 0 | NOT_YET_ASSESSED |
| `home_normal_v1` | 有数据首页 | FROZEN / FROZEN | 7 | 0 | 0 | NOT_YET_ASSESSED |
| `record_date_v2` | 记录日期 | PARTIAL / FROZEN | 3 | 0 | 0 | NOT_YET_ASSESSED |
| `recognition_flow_v1_2` | 识别过程 | ACTIVE_CLOSURE / FROZEN | 6 | 18 | 0 | NOT_YET_ASSESSED |
| `recognition_result_v1` | 识别结果 | FROZEN / FROZEN | 8 | 0 | 0 | NOT_YET_ASSESSED |
| `fish_record_detail_v2` | 鱼获详情 | PARTIAL / PARTIAL | 6 | 4 | 0 | NOT_YET_ASSESSED |
| `share_templates_v1` | 分享模板 | PARTIAL / PARTIAL | 6 | 7 | 0 | NOT_YET_ASSESSED |
| `fish_memory_bside_v1` | 鱼获记忆 / B 面 | PARTIAL / PARTIAL | 0 | 0 | 0 | NOT_YET_ASSESSED |
| `my_catches_v2` | 我的鱼获 | PARTIAL / PARTIAL | 9 | 22 | 13 | NOT_YET_ASSESSED |
| `fish_guide_v2` | 鱼鉴 | PARTIAL / FROZEN | 6 | 2 | 0 | NOT_YET_ASSESSED |
| `account_privacy_v1` | 账号与隐私 | PARTIAL / FROZEN | 5 | 17 | 0 | NOT_YET_ASSESSED |
| `auth_login_v2` | 登录 · 欢迎回来 | ACTIVE_CLOSURE / FROZEN | 0 | 0 | 0 | NOT_YET_ASSESSED |
| `auth_register_v2` | 注册 · 创建账号 | PARTIAL / FROZEN | 0 | 0 | 0 | NOT_YET_ASSESSED |
| `profile_edit_v1` | 编辑资料 | ACTIVE_CLOSURE / FROZEN | 6 | 0 | 0 | NOT_YET_ASSESSED |
| `user_agreement_v1` | 用户协议 | PARTIAL / FROZEN | 0 | 0 | 0 | NOT_YET_ASSESSED |

## Shared design systems

| Shared ID | Name | Status | Variants | Authority paths |
|---|---|---:|---:|---:|
| `background_system_v1` | 背景系统 | FROZEN | 5 | 7 |
| `fish_media_display_v1` | 鱼获图片自适应显示设计 | FROZEN | 5 | 7 |
| `primary_capture_button_v1` | 主拍摄按钮 | FROZEN | 2 | 6 |
| `action_button_v1` | 主 / 次操作按钮 | FROZEN | 3 | 14 |
| `text_action_v1` | 文字操作 | FROZEN | 3 | 13 |
| `icon_action_v1` | 图标操作 | FROZEN | 3 | 13 |
| `icon_library_v0_1` | 图标 | FROZEN | 8 | 50 |
| `top_navigation_v1` | 顶部导航 | FROZEN | 3 | 31 |
| `mist_glass_surface_v1` | 雾面玻璃 | FROZEN | 3 | 2 |
| `color_typography_v1` | 颜色与字体 | FROZEN | 4 | 2 |
| `spacing_radius_v1` | 间距与圆角 | FROZEN | 2 | 2 |
| `species_picker_v1` | 共享鱼种选择器 | PARTIAL | 2 | 4 |
| `default_profile_avatar_v1` | 默认资料头像 | FROZEN | 1 | 1 |
| `legal_document_shell_v1` | 法律文档壳 | FROZEN | 1 | 2 |
| `yu_jian_catch_hero_card_v1` | YuJianCatchHeroCard | FROZEN | 2 | 4 |
| `fish_memory_capture_v1` | Fish Memory Capture | FROZEN | 2 | 4 |

Every listed system includes authority paths, variant status, file presence, Git blob SHA / byte size where available and source audit status in the JSON map. A Git blob SHA is not SHA-256.

## All registered feature, view, nested and scenario entries

| Feature | State / view ID | Entry type | Title | Registry design status | Manager route | Visual authority or fallback | Asset verification |
|---|---|---|---|---|---|---|---|
| `home_empty_v2` | — | feature_overview | 空首页 | FROZEN | `design/manager/#page/home_empty_v2` | `design/system/core_visual_v1/reference/empty_home_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH01 | hifi_view | NH01 · 主页面｜多鱼获状态 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH01` | `design/system/core_visual_v1/reference/normal_home_v1.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH02 | hifi_view | NH02 · 第一条鱼首页 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH02` | `design/pages/home/normal_home/02_first_catch/frozen/NH02_First_Catch_Home_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH03 | hifi_view | NH03 · 页面状态与异常 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH03` | `design/pages/home/normal_home/03_page_states/frozen/NH03_Page_States_Exceptions_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH04 | hifi_view | NH04 · 组件状态与内容边界 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH04` | `design/pages/home/normal_home/04_component_content_states/frozen/NH04_Component_Content_States_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH05 | hifi_view | NH05 · 响应式与交互 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH05` | `design/pages/home/normal_home/05_responsive_interaction/frozen/NH05_Responsive_Interaction_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH06 | hifi_view | NH06 · 背景与环境权威 | FROZEN | `design/manager/#page/home_normal_v1/hifi/NH06` | `design/pages/home/normal_home/06_background_authority/frozen/NH06_Background_Authority_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `home_normal_v1` | NH07 | hifi_view | NH07 · 头像状态 | SPEC_FROZEN | `design/manager/#page/home_normal_v1/hifi/NH07` | `design/pages/home/normal_home/07_avatar_states/assets/nh07_default_avatar_3x_192.png` | SHA256_MATCH |
| `record_date_v2` | month_v2 | hifi_view | 01 · 月视图 V2 | FROZEN | `design/manager/#page/record_date_v2/hifi/month_v2` | `design/pages/record_date/v2/assets/Record_Date_Month_V2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `record_date_v2` | year_v2 | hifi_view | 02 · 年视图 V2 | FROZEN | `design/manager/#page/record_date_v2/hifi/year_v2` | `design/pages/record_date/v2/assets/Record_Date_Year_V2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `record_date_v2` | interaction | hifi_view | 03 · 交互与数据规范 | FROZEN | `design/manager/#page/record_date_v2/hifi/interaction` | `design/pages/record_date/v2/assets/Record_Date_Month_V2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_flow_v1_2` | state_timeline | hifi_view | 01 · State Timeline | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/state_timeline` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | visual_states | hifi_view | 02 · Visual States | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/visual_states` | `design/pages/recognition/design/01_Capture_Transition_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_flow_v1_2` | layer_component_ownership | hifi_view | 03 · 图层与组件归属 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_v1_static_shape | nested_hifi_child | 01 · 静态形状 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_v1_static_shape` | `design/pages/recognition/design/02_AI_Understanding_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_v1_rendering_contract | nested_hifi_child | 02 · 渲染规范 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_v1_rendering_contract` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r01 | nested_hifi_child | R01 · 主视觉细节 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r01` | `design/pages/recognition/design/04_Fish_Identifying_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r02 | nested_hifi_child | R02 · 能量岛拆解 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r02` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r03 | nested_hifi_child | R03 · 四岛构图 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r03` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r04 | nested_hifi_child | R04 · 光场与鱼体聚焦 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r04` | `design/pages/recognition/design/03_Fish_Highlight_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r05 | nested_hifi_child | R05 · 强度校准 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r05` | `design/pages/recognition/design/01_Capture_Transition_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_flow_v1_2` | layer_component_ownership/ai_edge_field_ref_r06 | nested_hifi_child | R06 · 失败边界 | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/layer_component_ownership/ai_edge_field_ref_r06` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | motion_transition | hifi_view | 04 · Motion & Transition | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/motion_transition` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | motion_transition/ai_edge_field_v1_motion_contract | nested_hifi_child | AI Edge Field V1 · Motion Contract | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/motion_transition/ai_edge_field_v1_motion_contract` | `design/pages/recognition/processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png` | RAW_BYTES_VERIFIED_NO_DECLARED_SHA |
| `recognition_flow_v1_2` | degradation_accessibility | hifi_view | 05 · Degradation & Accessibility | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/degradation_accessibility` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | degradation_accessibility/quality_levels | nested_hifi_child | 01 · Quality Levels | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/degradation_accessibility/quality_levels` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | degradation_accessibility/reduce_motion | nested_hifi_child | 02 · Reduce Motion | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/degradation_accessibility/reduce_motion` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | degradation_accessibility/layer_degradation_order | nested_hifi_child | 03 · Layer Degradation Order | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/degradation_accessibility/layer_degradation_order` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | degradation_accessibility/semantic_invariants | nested_hifi_child | 04 · Semantic Invariants | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/degradation_accessibility/semantic_invariants` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence | hifi_view | 06 · Runtime Evidence | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence/runtime_visual_parity | nested_hifi_child | 01 · Visual Keyframes & Parity | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence/runtime_visual_parity` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence/runtime_motion_timing | nested_hifi_child | 02 · Motion & Timing | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence/runtime_motion_timing` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence/runtime_accessibility_degradation | nested_hifi_child | 03 · Accessibility & Degradation | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence/runtime_accessibility_degradation` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence/runtime_production_semantics | nested_hifi_child | 04 · Production Semantics | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence/runtime_production_semantics` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_flow_v1_2` | runtime_evidence/runtime_final_gate | nested_hifi_child | 05 · Final Gate & Failure Taxonomy | FROZEN | `design/manager/#page/recognition_flow_v1_2/hifi/runtime_evidence/runtime_final_gate` | `design/pages/recognition/design/Recognition_Processing_Visual_State_Set_V1_2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_result_v1` | overview | hifi_view | RR00 · 结果总览 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/overview` | `design/pages/recognition/result/authority/authority_map.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_result_v1` | high | hifi_view | RR01 · 高置信结果 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/high` | `design/pages/recognition/design/05_Result_High_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_result_v1` | medium | hifi_view | RR02 · 中置信结果 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/medium` | `design/pages/recognition/design/06_Result_Medium_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_result_v1` | low | hifi_view | RR03 · 低置信结果 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/low` | `design/pages/recognition/design/07_Result_Low_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_result_v1` | no_fish | hifi_view | RR04 · 未检测到鱼 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/no_fish` | `design/pages/recognition/design/08_Error_No_Fish_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_result_v1` | image_quality | hifi_view | RR05 · 图片质量不足 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/image_quality` | `design/pages/recognition/design/09_Error_Image_Quality_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `recognition_result_v1` | content_edit | hifi_view | RR06 · 内容修改 | FROZEN | `design/manager/#page/recognition_result_v1/hifi/content_edit` | `design/pages/recognition/result/authority/authority_map.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `recognition_result_v1` | decision_logic | hifi_view | 识别过程 · 07 · 判定逻辑 | ACTIVE_CLOSURE | `design/manager/#page/recognition_result_v1/hifi/decision_logic` | `design/pages/recognition/result/authority/authority_map.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_record_detail_v2` | overview | hifi_view | 00 · Overview | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/overview` | `design/system/core_visual_v1/reference/fish_record_detail_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | a_side | hifi_view | 01 · A 面 · 鱼获记录 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/a_side` | `design/system/core_visual_v1/reference/fish_record_detail_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | b_side | hifi_view | 02 · B 面 · 鱼获记忆 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/b_side` | `design/pages/fish_record/detail/frozen/FishRecordDetail_B_Side_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | b_side/populated_memory_media | nested_hifi_child | 鱼获记忆 · 已有照片 / 视频 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/b_side/populated_memory_media` | `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_Populated_Memory_V1_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_record_detail_v2` | asset_generation | hifi_view | 03 · 鱼体资产生成 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/asset_generation` | `design/system/core_visual_v1/reference/fish_record_detail_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | asset_generation/asset_generation_states_visual | nested_hifi_child | 资产生成状态 · NOT_GENERATED / GENERATING / FAILED | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/asset_generation/asset_generation_states_visual` | `design/pages/fish_record/detail/frozen/generation/FishRecordDetail_Asset_Generation_States_V1_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_record_detail_v2` | editing | hifi_view | 04 · 信息编辑 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/editing` | `design/system/core_visual_v1/reference/fish_record_detail_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | editing/editing_workspace_visual | nested_hifi_child | 编辑鱼获信息 · 高保真 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/editing/editing_workspace_visual` | `design/pages/fish_record/detail/frozen/editing/FishRecordDetail_Editing_V1_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_record_detail_v2` | page_states | hifi_view | 05 · 页面状态 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/page_states` | `design/system/core_visual_v1/reference/fish_record_detail_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_record_detail_v2` | page_states/no_uploaded_memory | nested_hifi_child | 无上传记忆 | FROZEN | `design/manager/#page/fish_record_detail_v2/hifi/page_states/no_uploaded_memory` | `design/pages/fish_record/detail/frozen/states/FishRecordDetail_State_No_Uploaded_Memory_V1_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 01_overview | hifi_view | 01 · 总览与产品模型 | FROZEN | `design/manager/#page/share_templates_v1/hifi/01_overview` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 02_t01 | hifi_view | 02 · T01 · 战绩卡 | PARTIAL | `design/manager/#page/share_templates_v1/hifi/02_t01` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 02_t01/02a | nested_hifi_child | 02A · 九月战绩 · 自然浅色版 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/02_t01/02a` | `design/pages/share_templates/assets/originals/t01_01.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 02_t01/02b | nested_hifi_child | 02B · 九月战绩 · 玻璃风格迭代版 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/02_t01/02b` | `design/pages/share_templates/assets/originals/t01_02.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02 | hifi_view | 03 · T02 · 水边故事 | PARTIAL | `design/manager/#page/share_templates_v1/hifi/03_t02` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02/03a | nested_hifi_child | 03A · 轻色三照片 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/03_t02/03a` | `design/pages/share_templates/assets/originals/t02_01.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02/03b | nested_hifi_child | 03B · 轻色双照片 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/03_t02/03b` | `design/pages/share_templates/assets/originals/t02_02.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02/03c | nested_hifi_child | 03C · 深色无字幕 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/03_t02/03c` | `design/pages/share_templates/assets/originals/t02_03.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02/03d | nested_hifi_child | 03D · 深色带字幕 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/03_t02/03d` | `design/pages/share_templates/assets/originals/t02_04.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 03_t02/03e | nested_hifi_child | 03E · 轻色带字幕 | DESIGN_ONLY | `design/manager/#page/share_templates_v1/hifi/03_t02/03e` | `design/pages/share_templates/assets/originals/t02_05.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 04_data_rules | hifi_view | 04 · 时间范围与数据规则 | FROZEN | `design/manager/#page/share_templates_v1/hifi/04_data_rules` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 05_interaction | hifi_view | 05 · 模板选择与分享交互 | FROZEN | `design/manager/#page/share_templates_v1/hifi/05_interaction` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `share_templates_v1` | 06_responsive | hifi_view | 06 · 响应式与无障碍 | FROZEN | `design/manager/#page/share_templates_v1/hifi/06_responsive` | `design/pages/share_templates/assets/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_memory_bside_v1` | — | feature_overview | 鱼获记忆 / B 面 | PARTIAL | `design/manager/#page/fish_memory_bside_v1` | `design/system/core_visual_v1/reference/supplemental/fish_memory_reference.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | overview | hifi_view | 00 · 设计总览 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/overview` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | overview/o1_information_architecture | nested_hifi_child | O1 · 信息架构与场景树 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/overview/o1_information_architecture` | `design/pages/fish_records/list/reference/overview/My_Catches_V2_Information_Architecture.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | overview/o2_bg_data_resource | nested_hifi_child | O2 · 湖畔背景资源规范 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/overview/o2_bg_data_resource` | `design/pages/fish_records/list/reference/overview/My_Catches_BG_DATA_Resource_Spec.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | main | hifi_view | 主页面高保真 | FROZEN | `design/manager/#page/my_catches_v2/hifi/main` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | timeline | hifi_view | Timeline Scroll V1 | ACTIVE_CLOSURE | `design/manager/#page/my_catches_v2/hifi/timeline` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | timeline/t0_final_board | nested_hifi_child | T0 · 时间线高保真定稿板 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/timeline/t0_final_board` | `design/pages/fish_records/list/frozen/timeline_v1/My_Catches_Timeline_Final_Board.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | filter | hifi_view | 筛选 V1 | ACTIVE_CLOSURE | `design/manager/#page/my_catches_v2/hifi/filter` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f0_filter_overview | nested_hifi_child | F0 · 筛选总览与边界 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/filter/f0_filter_overview` | `design/pages/fish_records/list/reference/filter_v1/My_Catches_Filter_V1_Product_Board.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | filter/f1_filter_panel | nested_hifi_child | F1 · 筛选面板 | FROZEN | `design/manager/#page/my_catches_v2/hifi/filter/f1_filter_panel` | `design/pages/fish_records/list/frozen/filter_v1/F1_Filter_Panel_Frozen_V1.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | filter/f2_species | nested_hifi_child | F2 · 鱼种选择 | PARTIAL | `design/manager/#page/my_catches_v2/hifi/filter/f2_species` | `design/system/components/species_picker_v1/visual/authority/Species_Picker_V1_Reference_Board.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f3_time | nested_hifi_child | F3 · 时间选择 | MISSING | `design/manager/#page/my_catches_v2/hifi/filter/f3_time` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f4_size | nested_hifi_child | F4 · 尺寸筛选 | MISSING | `design/manager/#page/my_catches_v2/hifi/filter/f4_size` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f5_special_record | nested_hifi_child | F5 · 特殊记录 | PARTIAL | `design/manager/#page/my_catches_v2/hifi/filter/f5_special_record` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f6_results | nested_hifi_child | F6 · 筛选有结果 | PARTIAL | `design/manager/#page/my_catches_v2/hifi/filter/f6_results` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | filter/f7_empty | nested_hifi_child | F7 · 筛选无结果 | PARTIAL | `design/manager/#page/my_catches_v2/hifi/filter/f7_empty` | `design/pages/fish_records/list/frozen/empty_states_v1/My_Catches_Empty_States_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | empty | hifi_view | Empty States V1 | FROZEN | `design/manager/#page/my_catches_v2/hifi/empty` | `design/pages/fish_records/list/frozen/empty_states_v1/My_Catches_Empty_States_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | growth | hifi_view | Growth Mark V1 | FROZEN | `design/manager/#page/my_catches_v2/hifi/growth` | `design/pages/fish_records/list/frozen/growth_mark_v1/My_Catches_Growth_Mark_V1_Frozen.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | search | hifi_view | 搜索交互 V1 | FROZEN | `design/manager/#page/my_catches_v2/hifi/search` | `design/pages/fish_records/list/frozen/search_v1/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b0_search_functional_board | nested_hifi_child | B0 · 搜索功能规范板 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/search/b0_search_functional_board` | `design/pages/fish_records/list/reference/search_v1/My_Catches_Search_V1_Functional_Board.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b1_recent | nested_hifi_child | B1 · 最近搜索 / 空输入 | FROZEN | `design/manager/#page/my_catches_v2/hifi/search/b1_recent` | `design/pages/fish_records/list/frozen/search_v1/B1_Recent_Search_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b2_results | nested_hifi_child | B2 · 搜索有结果 | FROZEN | `design/manager/#page/my_catches_v2/hifi/search/b2_results` | `design/pages/fish_records/list/frozen/search_v1/B2_Search_Results_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b3_empty | nested_hifi_child | B3 · 搜索无结果 | FROZEN | `design/manager/#page/my_catches_v2/hifi/search/b3_empty` | `design/pages/fish_records/list/frozen/search_v1/B3_Search_Empty_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b4_search_filter | nested_hifi_child | B4 · Search + Filter | FROZEN | `design/manager/#page/my_catches_v2/hifi/search/b4_search_filter` | `design/pages/fish_records/list/frozen/search_v1/B4_Search_Filter_Frozen.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | search/b5_interaction | nested_hifi_child | B5 · 交互与返回规则 | FROZEN | `design/manager/#page/my_catches_v2/hifi/search/b5_interaction` | `design/pages/fish_records/list/frozen/search_v1/manifest.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | system | hifi_view | 加载 / 异常 | PARTIAL | `design/manager/#page/my_catches_v2/hifi/system` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | habitat | hifi_view | 我的渔境 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | habitat/tank_round_hifi | nested_hifi_child | 鱼缸 · 圆缸竖屏高保 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat/tank_round_hifi` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | habitat/tank_landscape_hifi | nested_hifi_child | 鱼缸 · 横屏景观缸高保 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat/tank_landscape_hifi` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | habitat/pond_landscape_hifi | nested_hifi_child | 鱼塘 · 自然水域高保 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat/pond_landscape_hifi` | `design/system/core_visual_v1/reference/my_catches_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `my_catches_v2` | habitat/pond_levels_1_5 | nested_hifi_child | 鱼塘 1–5 · 阶段参考 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat/pond_levels_1_5` | `design/pages/fish_records/list/habitat/reference/Habitat_Pond_Levels_1-5_Reference.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | habitat/lake_levels_1_7 | nested_hifi_child | 湖泊 1–7 · 阶段参考 | DESIGN_ONLY | `design/manager/#page/my_catches_v2/hifi/habitat/lake_levels_1_7` | `design/pages/fish_records/list/habitat/reference/Habitat_Lake_Levels_1-7_Reference.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `my_catches_v2` | timeline_default | scenario_page | 默认时间线 | FROZEN | `design/manager/#page/my_catches_v2/scenario/timeline_default` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | day_1_5 | scenario_page | 单日 1–5 条 | FROZEN | `design/manager/#page/my_catches_v2/scenario/day_1_5` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | day_6_10 | scenario_page | 单日 6–10 条 | FROZEN | `design/manager/#page/my_catches_v2/scenario/day_6_10` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | day_gt10 | scenario_page | 单日 >10 条 | FROZEN | `design/manager/#page/my_catches_v2/scenario/day_gt10` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | search_focus | scenario_page | 点击搜索 / Focused | FROZEN | `design/manager/#page/my_catches_v2/scenario/search_focus` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | search_results | scenario_page | 搜索有结果 | FROZEN | `design/manager/#page/my_catches_v2/scenario/search_results` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | search_empty | scenario_page | 搜索无结果 | FROZEN | `design/manager/#page/my_catches_v2/scenario/search_empty` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | filter_sheet | scenario_page | 点击筛选 / 顶部筛选面板 | FROZEN | `design/manager/#page/my_catches_v2/scenario/filter_sheet` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | filter_results | scenario_page | 筛选有结果 | FROZEN | `design/manager/#page/my_catches_v2/scenario/filter_results` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | filter_empty | scenario_page | 筛选无结果 | FROZEN | `design/manager/#page/my_catches_v2/scenario/filter_empty` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | archive_empty | scenario_page | 空档案 / 无记录 | FROZEN | `design/manager/#page/my_catches_v2/scenario/archive_empty` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | growth_mark_examples | scenario_page | Growth Mark 示例 | FROZEN | `design/manager/#page/my_catches_v2/scenario/growth_mark_examples` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `my_catches_v2` | loading_error | scenario_page | 加载 / 加载失败 | PARTIAL | `design/manager/#page/my_catches_v2/scenario/loading_error` | — | NO_DIRECT_VISUAL_AUTHORITY |
| `fish_guide_v2` | home | hifi_view | 01 · 鱼鉴首页 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/home` | `design/system/core_visual_v1/reference/fish_guide_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_guide_v2` | home/unlit_state | nested_hifi_child | 01A · 未点亮状态 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/home/unlit_state` | `design/system/core_visual_v1/reference/supplemental/fish_guide_unlit_state.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_guide_v2` | species_detail | hifi_view | 02 · 鱼种详情 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/species_detail` | `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Baitiao_V1.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_guide_v2` | species_detail/zero_catch | nested_hifi_child | 02A · 无我的鱼获记录 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/species_detail/zero_catch` | `design/pages/fish_guide/species_detail/frozen/Fish_Species_Detail_Zero_Catch_V1.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `fish_guide_v2` | species_states | hifi_view | 03 · 页面状态 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/species_states` | `design/pages/fish_guide/species_states/frozen/Fish_Species_States_V1.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_guide_v2` | knowledge_content_assets | hifi_view | 04 · 知识卡内容与资产 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/knowledge_content_assets` | `design/system/core_visual_v1/reference/fish_guide_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_guide_v2` | motion_interaction | hifi_view | 05 · 动效与交互 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/motion_interaction` | `design/system/core_visual_v1/reference/fish_guide_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `fish_guide_v2` | responsive_accessibility | hifi_view | 06 · 响应式与无障碍 | FROZEN | `design/manager/#page/fish_guide_v2/hifi/responsive_accessibility` | `design/system/core_visual_v1/reference/fish_guide_v2.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `account_privacy_v1` | 01_login_register | hifi_view | 01 · 登录与注册 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/01_login_register` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 01_login_register/01a | nested_hifi_child | 01A · 登录 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/01_login_register/01a` | `design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `account_privacy_v1` | 01_login_register/01b | nested_hifi_child | 01B · 注册 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/01_login_register/01b` | `design/pages/account_privacy/Register/frozen/Register_V2_Frozen_Final.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `account_privacy_v1` | 01_login_register/01c | nested_hifi_child | 01C · 忘记密码 · Deferred | DEFERRED | `design/manager/#page/account_privacy_v1/hifi/01_login_register/01c` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 02_my_profile | hifi_view | 02 · 我的与资料 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/02_my_profile` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 02_my_profile/02a | nested_hifi_child | 02A · 我的 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/02_my_profile/02a` | `design/pages/account_privacy/My/00_My.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 02_my_profile/02b | nested_hifi_child | 02B · 编辑资料首页 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/02_my_profile/02b` | `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp` | SHA256_MATCH |
| `account_privacy_v1` | 02_my_profile/02c | nested_hifi_child | 02C · 头像修改 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/02_my_profile/02c` | `design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp` | SHA256_MATCH |
| `account_privacy_v1` | 02_my_profile/02d | nested_hifi_child | 02D · 昵称编辑 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/02_my_profile/02d` | `design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp` | SHA256_MATCH |
| `account_privacy_v1` | 03_account_login | hifi_view | 03 · 账号与安全 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/03_account_login` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 03_account_login/03a | nested_hifi_child | 03A · 账号与安全首页 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/03_account_login/03a` | `design/pages/account_privacy/My/Account_Login/00_Account_Login.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 03_account_login/03b | nested_hifi_child | 03B · 修改密码 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/03_account_login/03b` | `design/pages/account_privacy/My/Account_Login/01_Change_Password.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy | hifi_view | 04 · 数据与隐私 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy/04a | nested_hifi_child | 04A · 数据与隐私首页 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy/04a` | `design/pages/account_privacy/My/Account_Login/Data_Privacy/00_Data_Privacy.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy/04b | nested_hifi_child | 04B · AI 模型改进 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy/04b` | `design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/01_Enable_Consent.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy/04c | nested_hifi_child | 04C · 位置权限 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy/04c` | `design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/01_Info.png` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy/04d | nested_hifi_child | 04D · 导出我的数据 · Deferred | DEFERRED | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy/04d` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 04_data_privacy/04e | nested_hifi_child | 04E · 注销账号 · Deferred | DEFERRED | `design/manager/#page/account_privacy_v1/hifi/04_data_privacy/04e` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 05_legal_about | hifi_view | 05 · 法律与关于 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/05_legal_about` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 05_legal_about/05a | nested_hifi_child | 05A · 关于渔见 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/05_legal_about/05a` | `design/pages/account_privacy/authority_manifest_v2.json` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `account_privacy_v1` | 05_legal_about/05b | nested_hifi_child | 05B · 隐私政策 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/05_legal_about/05b` | `design/pages/account_privacy/My/Account_Login/Data_Privacy/04_Privacy_Policy.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `account_privacy_v1` | 05_legal_about/05c | nested_hifi_child | 05C · 用户协议 | FROZEN | `design/manager/#page/account_privacy_v1/hifi/05_legal_about/05c` | `design/pages/account_privacy/My/Account_Login/Data_Privacy/04_Privacy_Policy.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `auth_login_v2` | — | feature_overview | 登录 · 欢迎回来 | FROZEN | `design/manager/#page/auth_login_v2` | `design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `auth_register_v2` | — | feature_overview | 注册 · 创建账号 | FROZEN | `design/manager/#page/auth_register_v2` | `design/pages/account_privacy/Register/frozen/Register_V2_Frozen_Final.png` | BLOCKED_ACCESS_API_CONTENT_OMITTED |
| `profile_edit_v1` | 01_profile_home | hifi_view | 01 · 编辑资料首页 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/01_profile_home` | `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp` | SHA256_MATCH |
| `profile_edit_v1` | 02_avatar_edit | hifi_view | 02 · 头像修改 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/02_avatar_edit` | `design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp` | SHA256_MATCH |
| `profile_edit_v1` | 03_nickname_edit | hifi_view | 03 · 昵称编辑 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/03_nickname_edit` | `design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp` | SHA256_MATCH |
| `profile_edit_v1` | 04_save_feedback | hifi_view | 04 · 保存与反馈 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/04_save_feedback` | `design/pages/account_privacy/My/Edit_Profile/README.md` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `profile_edit_v1` | 05_edge_states | hifi_view | 05 · 异常与边界状态 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/05_edge_states` | `design/pages/account_privacy/My/Edit_Profile/README.md` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `profile_edit_v1` | 06_interaction_adaptation | hifi_view | 06 · 交互与适配规范 | FROZEN | `design/manager/#page/profile_edit_v1/hifi/06_interaction_adaptation` | `design/pages/account_privacy/My/Edit_Profile/README.md` | PATH_PRESENT_SHA256_NOT_RECALCULATED |
| `user_agreement_v1` | — | feature_overview | 用户协议 | FROZEN | `design/manager/#page/user_agreement_v1` | `design/pages/account_privacy/legal/Legal_Document_Shell_Spec_V1.md` | PATH_PRESENT_SHA256_NOT_RECALCULATED |

## How to read this map

- `feature_overview` rows preserve features with no hifi views. They are not dropped from counts.
- `hifi_view`, `nested_hifi_child` and `scenario_page` are direct routes supported by the current Design Manager `app.js` hash parser.
- `visual_authority_scope` distinguishes exact view visual paths from feature-level fallback paths; a behavior specification is never silently presented as an image.
- `visual_verification_status` distinguishes raw-byte SHA-256 recomputation from path presence. See the Asset Integrity Register for all repository design file blobs and declared-vs-actual checks.
- A Design Manager route is not evidence of Android route reachability.
- Scenario example data, frozen examples, known data exceptions and design-only states remain identified in their source authorities. The map does not promote their status.
- Machine-readable fields include owner feature, state, authorities, shared dependencies, Manager route, registry state, path state and verification state.

## Runtime reachability classifications

- **Runtime route confirmed:** Home, camera/gallery identify, recognize/result/issue, FishRecordDetail, My Catches/day, Fish Guide/species, profile/auth/legal routes.
- **Design entry, runtime missing:** Record Date V2, My Habitat, T01/T02 template flow.
- **Runtime only / design partial:** current text sharing, some account and loading/error states.
- **Menu hidden, direct route supported:** registry features with `navigation_hidden=true`; they are included in the machine map.

The actual product connections and pending edges are in [Product Master Flow](../00_Product_Master_Flow/Product_Master_Flow_V3_1.md).