# Orphan Color Report V3.1

**Current scan result:** 100 canonical color tokens (92 existing + 8 approved status tokens); 0 unregistered production color literals; 0 PNG metadata color literals. D-015 recommended mappings are frozen.

## Method

Baseline: PR 145 Phase 2 certification branch. The static scanner reads explicit HEX/RGB/RGBA literals, SVG fill/stroke, and explicit PNG metadata within registered page/system/component/registry roots. Raster pixels are not sampled. Values are normalized to 8-bit RGBA before exact token comparison. The scanner read 619 files (494 text/spec/token/SVG and 125 raster metadata files). It found 102 distinct RGBA literals; 102 currently match a production token and 0 remain unregistered. The 64 historic source literals retain identity-only tokens; eight approved D-015 tokens resolve shared status colors.

## Legacy literals

These values came from existing source authorities. Each value has a stable ID in Color_Tokens_V3_FINAL.json with its full source-file list. The mapping preserves the authority and does not permit new use.

| RGBA HEX | Token ID | Occurrences | Source files |
|---|---|---:|---|
| #06474814 | color.legacy_06474814 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #064748FF | color.legacy_064748ff | 1 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg |
| #08192629 | color.legacy_08192629 | 2 | design/system/components/icon_action/Icon_Action_Spec_V1.md<br>design/system/components/icon_action/visual_contract.json |
| #0B2D4B0D | color.legacy_0b2d4b0d | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #0B2D4B1A | color.legacy_0b2d4b1a | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #0B2D4BE0 | color.legacy_0b2d4be0 | 2 | design/system/components/icon_action/Icon_Action_Spec_V1.md<br>design/system/components/icon_action/visual_contract.json |
| #0F7A7842 | color.legacy_0f7a7842 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #0F7A7847 | color.legacy_0f7a7847 | 1 | design/system/components/action_button/Action_Button_Spec_V1.md |
| #112222FF | color.legacy_112222ff | 2 | design/pages/home/normal_home/05_responsive_interaction/Normal_Home_V2_Coordinate_Origin_And_Anchor_Audit_20261010.md<br>design/pages/home/normal_home/engineering/README.md |
| #112288FF | color.legacy_112288ff | 3 | design/pages/home/normal_home/05_responsive_interaction/Normal_Home_Layout_Geometry_Contract_V2_DRAFT.md<br>design/pages/home/normal_home/05_responsive_interaction/Normal_Home_V2_Authority_Conflict_Matrix.md<br>design/pages/home/normal_home/engineering/Archive_And_Supersession_V1.md |
| #123048FF | color.legacy_123048ff | 1 | design/pages/home/empty_home/shared/contracts/hero_asset_contract.json |
| #12364AFF | color.legacy_12364aff | 3 | design/pages/account_privacy/Login/Login_V2_Closure.md<br>design/pages/account_privacy/Login/WORK_TASK_LOGIN_V2.md<br>design/pages/account_privacy/spec/VISUAL_TOKENS.md |
| #18343AFF | color.legacy_18343aff | 31 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+28 more in token source_paths |
| #1A2C35FF | color.legacy_1a2c35ff | 5 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_2.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>+1 more in token source_paths |
| #28474DFF | color.legacy_28474dff | 7 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+4 more in token source_paths |
| #294B5EFF | color.legacy_294b5eff | 26 | design/system/components/icon_action/visual/authority/01_Families.svg<br>design/system/components/icon_action/visual/authority/02_NAVIGATION_States.svg<br>design/system/components/icon_action/visual/authority/03_UTILITY_Library.svg<br>+21 more in token source_paths |
| #30485AFF | color.legacy_30485aff | 3 | design/pages/home/normal_home/02_first_catch/README.md<br>design/pages/home/normal_home/02_first_catch/default_avatar_contract.json<br>design/system/components/profile_avatar_v1/default_profile_avatar_contract.json |
| #385C6CFF | color.legacy_385c6cff | 2 | design/system/components/icon_action/visual/authority/06_Real_Usage.svg<br>design/system/components/icon_action/visual/candidate/06_Real_Usage.svg |
| #416B64FF | color.legacy_416b64ff | 1 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg |
| #47697AFF | color.legacy_47697aff | 2 | design/system/components/icon_action/visual/authority/06_Real_Usage.svg<br>design/system/components/icon_action/visual/candidate/06_Real_Usage.svg |
| #5E747AFF | color.legacy_5e747aff | 3 | design/system/components/top_navigation/title_only/visual/authority/01_Base_Geometry.svg<br>design/system/components/top_navigation/title_only/visual/authority/02_Real_Usage.svg<br>design/system/components/top_navigation/title_only/visual/authority/03_Long_Title_Small_Screen.svg |
| #61777CFF | color.legacy_61777cff | 40 | design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>design/system/components/action_button/visual/authority/03_Paired_Buttons.svg<br>+37 more in token source_paths |
| #6BFFFFFF | color.legacy_6bffffff | 3 | design/pages/home/normal_home/02_first_catch/README.md<br>design/pages/home/normal_home/02_first_catch/default_avatar_contract.json<br>design/system/components/profile_avatar_v1/default_profile_avatar_contract.json |
| #71868BFF | color.legacy_71868bff | 99 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+28 more in token source_paths |
| #74879AFF | color.legacy_74879aff | 3 | design/pages/account_privacy/Login/Login_V2_Closure.md<br>design/pages/account_privacy/Login/WORK_TASK_LOGIN_V2.md<br>design/pages/account_privacy/spec/VISUAL_TOKENS.md |
| #7488972E | color.legacy_7488972e | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #7488973D | color.legacy_7488973d | 1 | design/system/components/action_button/Action_Button_Spec_V1.md |
| #7A8F93FF | color.legacy_7a8f93ff | 32 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+28 more in token source_paths |
| #91A5A6FF | color.legacy_91a5a6ff | 5 | design/system/components/action_button/Action_Button_Spec_V1.md<br>design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>+2 more in token source_paths |
| #9EFFFFFF | color.legacy_9effffff | 3 | design/pages/home/normal_home/02_first_catch/README.md<br>design/pages/home/normal_home/02_first_catch/default_avatar_contract.json<br>design/system/components/profile_avatar_v1/default_profile_avatar_contract.json |
| #B6CBC6FF | color.legacy_b6cbc6ff | 3 | design/system/components/top_navigation/back_title/visual/authority/01_Base_Geometry.svg<br>design/system/components/top_navigation/title_only/visual/authority/01_Base_Geometry.svg |
| #BFD0D7FF | color.legacy_bfd0d7ff | 3 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md |
| #BFEAF2FF | color.legacy_bfeaf2ff | 3 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md |
| #CBD8E2FF | color.legacy_cbd8e2ff | 2 | design/system/components/icon_action/visual/authority/06_Real_Usage.svg<br>design/system/components/icon_action/visual/candidate/06_Real_Usage.svg |
| #CFE1DCFF | color.legacy_cfe1dcff | 1 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg |
| #D5DFE6FF | color.legacy_d5dfe6ff | 3 | design/pages/account_privacy/Login/Login_V2_Closure.md<br>design/pages/account_privacy/Login/WORK_TASK_LOGIN_V2.md<br>design/pages/account_privacy/spec/VISUAL_TOKENS.md |
| #D5E1E3FF | color.legacy_d5e1e3ff | 1 | design/pages/home/empty_home/shared/contracts/anchor_contract.json |
| #D8F5FAFF | color.legacy_d8f5faff | 3 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md |
| #D9E4E3FF | color.legacy_d9e4e3ff | 5 | design/system/components/action_button/Action_Button_Spec_V1.md<br>design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>+2 more in token source_paths |
| #D9E6E2FF | color.legacy_d9e6e2ff | 3 | design/system/components/action_button/visual/authority/06_SECONDARY_MUTED_States.svg<br>design/system/components/icon_action/visual/authority/03_UTILITY_Library.svg<br>design/system/components/icon_action/visual/candidate/03_UTILITY_Library.svg |
| #DAA02DFF | color.legacy_daa02dff | 1 | design/pages/home/empty_home/shared/contracts/hero_asset_contract.json |
| #DCE7E3FF | color.legacy_dce7e3ff | 66 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+38 more in token source_paths |
| #DCE8E8FF | color.legacy_dce8e8ff | 18 | design/system/components/icon_action/visual/authority/06_Real_Usage.svg<br>design/system/components/icon_action/visual/candidate/06_Real_Usage.svg<br>design/system/components/text_action/visual/authority/02_Real_Usage.svg<br>+3 more in token source_paths |
| #DDE9E7FF | color.legacy_dde9e7ff | 4 | design/system/components/text_action/visual/authority/02_Real_Usage.svg<br>design/system/components/text_action/visual/candidate/02_Real_Usage.svg |
| #DFE9E6FF | color.legacy_dfe9e6ff | 10 | design/system/components/top_navigation/back_title/visual/authority/01_Base_Geometry.svg<br>design/system/components/top_navigation/back_title/visual/authority/02_Real_Usage.svg<br>design/system/components/top_navigation/back_title/visual/authority/03_Long_Title_Small_Screen.svg<br>+7 more in token source_paths |
| #E2EBE8FF | color.legacy_e2ebe8ff | 93 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg<br>design/system/components/action_button/visual/authority/01_Base_Visual.svg<br>design/system/components/action_button/visual/authority/02_Single_Buttons.svg<br>+28 more in token source_paths |
| #E66B61FF | color.legacy_e66b61ff | 1 | design/pages/account_privacy/spec/VISUAL_TOKENS.md |
| #E8F0EFFF | color.legacy_e8f0efff | 2 | design/system/components/top_navigation/back_title_actions/visual/authority/03_Real_Usage.svg |
| #EAF1F1FF | color.legacy_eaf1f1ff | 2 | design/system/components/top_navigation/title_only/visual/authority/02_Real_Usage.svg |
| #EAF3F1FF | color.legacy_eaf3f1ff | 4 | design/system/components/action_button/Action_Button_Spec_V1.md<br>design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual/authority/05_SECONDARY_STRONG_Five_States.svg<br>+1 more in token source_paths |
| #EAF4F1FF | color.legacy_eaf4f1ff | 1 | design/system/components/action_button/visual/Action_Button_V1_1_Frozen_Visual.svg |
| #EEF4F2FF | color.legacy_eef4f2ff | 3 | design/system/components/action_button/visual/authority/06_SECONDARY_MUTED_States.svg<br>design/system/components/icon_action/visual/authority/03_UTILITY_Library.svg<br>design/system/components/icon_action/visual/candidate/03_UTILITY_Library.svg |
| #EEF4F3FF | color.legacy_eef4f3ff | 13 | design/system/components/top_navigation/back_title/visual/authority/01_Base_Geometry.svg<br>design/system/components/top_navigation/back_title/visual/authority/02_Real_Usage.svg<br>design/system/components/top_navigation/back_title/visual/authority/03_Long_Title_Small_Screen.svg<br>+7 more in token source_paths |
| #F4F8F7FF | color.legacy_f4f8f7ff | 1 | design/system/components/action_button/Action_Button_Spec_V1.md |
| #F6D99BFF | color.legacy_f6d99bff | 4 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md |
| #F7FAFB73 | color.legacy_f7fafb73 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #F7FAFBC7 | color.legacy_f7fafbc7 | 2 | design/system/components/icon_action/Icon_Action_Spec_V1.md<br>design/system/components/icon_action/visual_contract.json |
| #F7FAFBE0 | color.legacy_f7fafbe0 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #F7FAFBE6 | color.legacy_f7fafbe6 | 1 | design/system/components/action_button/visual_contract.json |
| #FFE7AEFF | color.legacy_ffe7aeff | 4 | design/pages/recognition/processing/contracts/Recognition_Processing_Contract_V1_1.json<br>design/pages/recognition/processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md<br>design/pages/recognition/processing/spec/Recognition_Processing_Visual_Spec_V1_1.md |
| #FFFFFF14 | color.legacy_ffffff14 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #FFFFFF1A | color.legacy_ffffff1a | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |
| #FFFFFF2E | color.legacy_ffffff2e | 2 | design/system/components/icon_action/Icon_Action_Spec_V1.md<br>design/system/components/icon_action/visual_contract.json |
| #FFFFFFA6 | color.legacy_ffffffa6 | 2 | design/system/components/action_button/Action_Button_Spec_V1_1.md<br>design/system/components/action_button/visual_contract.json |

## Approved semantic roles

- Brand Primary: `color.action_primary` on `color.lake_white`.
- Brand Secondary: `color.lake_blue` on `color.lake_white`.
- Success: `color.status_success_foreground` / `color.status_success_surface`.
- Warning: `color.status_warning_foreground` / `color.status_warning_surface`.
- Error: `color.status_error_foreground` / `color.status_error_surface`.
- Info: `color.status_info_foreground` / `color.status_info_surface`.
- Danger remains Page Scoped through existing page identity `color.legacy_e66b61ff`; it is not global Error.

D-015 is RESOLVED. Candidate alternatives are non-effective. See `Color_Tokens_V3_FINAL.json` for the only production mappings and `Color_System_V3_FINAL.md` for usage boundaries.
