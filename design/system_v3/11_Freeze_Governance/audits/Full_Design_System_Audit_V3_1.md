# Full Design System Audit V3.1 — Checkpoint C

**Audit status:** AUDIT_DRAFT  
**Snapshot:** 2026-10-11  
**Baseline:** `pan277942135/Yujian_App` main `7737f293ea3831aeea2c6f58308961cdb2ba7d01`  
**Backend route snapshot:** `pan277942135/Yujian` main `05554ce1b7104c7bce05610979dfc3478b6d24a5`

## Scope and method

This audit reads the 15-feature registry, 16 shared-system entries, feature authorities, manager route patterns, the current Android source tree, and the backend route definitions. It audits product semantics and missing design contracts; it does not change frozen authority. It verifies route presence from source, not live API behavior or complete response-to-database field mapping.

The inventory contains 658 files under `design/**`. Raw SHA-256 was recomputed for 579 files. For 79 large binaries the GitHub Contents API returned metadata without bytes; their baseline Git blob SHA and byte size are recorded separately, with actual SHA-256 left null. Current SHA-256 declarations were found for 144 paths: 115 match recomputed bytes, while 29 belong to blocked binaries and remain unverified. Of the 37 blocked frozen references, 19 have no direct current SHA-256 declaration. Original source/lineage is explicit for 25 files, role metadata only for 30, and not stated for 603; no license declaration was found for the 658 scanned files. Five key images were inspected as static images: Empty Home, NH02 First Catch, My Catches, Record Date Month, and Fish Guide. The other 243 image/vector assets were not visually inspected in this checkpoint.

## Coverage summary

| Area | Registered | Current status |
|---|---:|---|
| Feature modules | 15 / 15 | All registry modules reviewed |
| Shared systems | 16 / 16 | 15 FROZEN, Species Picker PARTIAL |
| Hi-Fi views | 62 | All manager route patterns and registered view files found |
| Nested children | 70 | Preserved as child states; not inferred as new product surfaces |
| Scenario pages | 13 | Registered and linked |
| Page map | 150 nodes | Generated in Checkpoint B |
| Design files | 658 | 579 SHA-256 computed, 79 blocked for raw bytes |
| Visually reviewed images | 5 | Static image review only |

The registry marks behavior FROZEN for 14/15 features and visual FROZEN for 13/15; those labels are preserved as registry data. Across runtime/evidence, implementation readiness is mixed: 7/15 runtime PARTIAL, 4/15 RUNTIME_ONLY, 1/15 MISSING, and 6/15 evidence MISSING. A frozen design label does not mean its runtime or acceptance evidence is complete.

## Feature audit

| Feature | Registry state | Semantic audit finding | Evidence paths |
|---|---|---|---|
| `home_empty_v2` · 空首页 | FROZEN | Single Home surface; EMPTY is zero valid FishRecords and login/guest is orthogonal. Registry records runtime/evidence PASS. Frozen Empty Home screenshot was visually inspected.  | `design/pages/home/empty_home/spec/Empty_Home_Spec_Closure_V1.md; design/pages/home/empty_home/shared/contracts/runtime_manifest.json; design/pages/home/empty_home/evidence/manifest.json` |
| `home_normal_v1` · 有数据首页 | FROZEN | NH01–NH06 are design-frozen. NH07 avatar treatment has its own frozen contract, while full-page visual acceptance remains pending; runtime/evidence modalities are PARTIAL. Normal Home is rendered from the existing Home route.  | `design/pages/home/normal_home/README.md; design/pages/home/normal_home/07_avatar_states/` |
| `record_date_v2` · 记录日期 | PARTIAL | RD01 month and RD02 year layouts plus RD03 behavior are frozen; Android runtime route is MISSING and motion is PARTIAL. The year sample says 68 catches while its monthly entries total 59; the authority marks these as illustrative, not data truth. The monthly frozen visual was visually inspected.  | `design/pages/record_date/v2/spec/Record_Date_V2_Interaction_Contract.md; design/pages/record_date/v2/review/Record_Date_V2_Freeze_Decision_20261010.md` |
| `recognition_flow_v1_2` · 识别过程 | ACTIVE_CLOSURE | Design is frozen; runtime/evidence are in ACTIVE_CLOSURE. Current app source routes camera and gallery into the shared recognition pipeline with staged capture/detect/outline/classify states. The map separates issue handling from technical failure.  | `design/pages/recognition/spec/Recognition_State_Timeline_Spec_V1_3.md; design/pages/recognition/evidence/Recognition_Runtime_Evidence_V1_2.md` |
| `recognition_result_v1` · 识别结果 | FROZEN | Design is frozen; runtime/evidence remain PARTIAL. Source review confirms High/Medium/Low thresholds are defined in current app code; Save Catch goes Home while Continue Memory opens Detail B. Do not turn this audit into a threshold change.  | `design/pages/recognition/result/spec/Recognition_Result_Behavior_Spec_V1.md; app/src/main/java/com/yujian/ai/ui/RecognitionUiState.kt` |
| `fish_record_detail_v2` · 鱼获详情 | PARTIAL | A/B-side layouts and core behavior are frozen, but overall feature is PARTIAL; motion, haptics, sound and evidence are missing; runtime/assets are PARTIAL. B-side generation is conditional on signed-in session. Save Catch currently routes Home; Continue Memory opens Detail B. This conflicts with the requested master-flow shorthand and needs product-owner resolution.  | `design/pages/fish_record/detail/README.md; app/src/main/java/com/yujian/ai/ui/recorddetail/FishRecordDetailScreen.kt` |
| `share_templates_v1` · 分享模板 | PARTIAL | T01/T02 product behavior is frozen; visual, motion and evidence are PARTIAL. Current Android sharing is a plain-text chooser; no matching T01/T02 route or rendered template output was found. Preserve templates as design-only until the product decision and runtime contract are explicit.  | `design/pages/share_templates/01_Product_Model_V1.md; app/src/main/java/com/yujian/ai/ui/screens/ShareCenterScreen.kt` |
| `fish_memory_bside_v1` · 鱼获记忆 / B 面 | PARTIAL | Historical/compatibility feature folded under Fish Record Detail → 02 B-side Memory; it is not an independent first-level menu. Runtime/design remain PARTIAL and motion/haptic/sound/evidence are missing.  | `design/pages/fish_record/detail/README.md; app/src/main/java/com/yujian/ai/ui/recorddetail/FishMemorySection.kt` |
| `my_catches_v2` · 我的鱼获 | PARTIAL | Core list behavior and visual are frozen; overall/runtime/evidence/assets are PARTIAL; motion/haptic/sound are missing. Filter V1 defines four dimensions with F1 inline and immediate apply. The app has a day route, but no Record Date destination route was found. My Catches screenshot was visually inspected.  | `design/pages/fish_records/list/README.md; design/system/core_visual_v1/reference/my_catches_v2.png` |
| `fish_guide_v2` · 鱼鉴 | PARTIAL | Design is frozen; runtime is PARTIAL and evidence is MISSING. Main menu is 01–06 with 01A/02A nested states; content/asset and responsive/accessibility contracts exist. Fish guide visual was visually inspected. Backend routes are present at GET /api/v1/fish/species and /api/v1/fish/species/{id}/detail, but this is route presence, not dynamic field-by-field validation.  | `design/pages/fish_guide/README.md; design/pages/fish_guide/responsive/Fish_Guide_Responsive_Accessibility_Spec_V1.md; app/src/main/java/com/yujian/ai/ui/screens/FishGuideHomeScreen.kt` |
| `account_privacy_v1` · 账号与隐私 | PARTIAL | Page system is design-frozen; runtime is PARTIAL. External legal copy gate remains PARTIAL and privacy/export/delete/password reset flows have explicit deferred/ComingSoon states. Auth/profile/privacy routes exist, but release and UI wiring need separate acceptance evidence.  | `design/pages/account_privacy/spec/FLOW_SPEC.md; design/pages/account_privacy/Account_Privacy_Design_Freeze_Review_V1.md` |
| `auth_login_v2` · 登录 · 欢迎回来 | ACTIVE_CLOSURE | Login V2.1 design and runtime are marked FROZEN; evidence is PARTIAL. The final PNG is a large binary, so raw SHA-256 is not available through the contents API; its baseline path and Git blob ID are recorded.  | `design/pages/account_privacy/Login/frozen/manifest.json; app/src/main/java/com/yujian/ai/ui/auth/LoginV2Screen.kt` |
| `auth_register_v2` · 注册 · 创建账号 | PARTIAL | Register V2 design is frozen; runtime is RUNTIME_ONLY and evidence is MISSING. Maintain it as design freeze, not proof of runtime acceptance.  | `design/pages/account_privacy/Register/frozen/manifest.json; app/src/main/java/com/yujian/ai/ui/auth/RegisterV2Screen.kt` |
| `profile_edit_v1` · 编辑资料 | ACTIVE_CLOSURE | Profile edit design is frozen; runtime is RUNTIME_ONLY and evidence is MISSING. Nickname frozen WebP raw hash matches its current declared frozen hash; historical approved-source and previous repository hashes are kept distinct.  | `design/pages/account_privacy/My/Edit_Profile/README.md; design/pages/account_privacy/My/Edit_Profile/contracts/Nickname_Edit_V1_Contract.json` |
| `user_agreement_v1` · 用户协议 | PARTIAL | Hidden compatibility alias. Canonical User Agreement scope lives under Account & Privacy 05C; legal shell is frozen while final legal content is gated. It should not create a second top-level product surface.  | `design/pages/account_privacy/legal/Legal_Document_Shell_Spec_V1.md; design/pages/account_privacy/legal/User_Agreement_Content_Draft_V1.md` |

## Shared-system audit

| Shared system | State | Audit reading |
|---|---|---|
| `background_system_v1` · 背景系统 | FROZEN | V1 已冻结：父级仅展示系统总览与两张 Canonical Master；BG_ENV_HERO / BG_CONTENT / BG_DATA / BG_CAPTURE / BG_SOLID_FALLBACK 统一通过左侧子菜单直达各自工作区，不在右侧重复生成 Variant 子页面。 |
| `fish_media_display_v1` · 鱼获图片自适应显示设计 | FROZEN | Fit 保护整张原图；Result Crop 必须通过 FishSafeRect 安全裁切；Detail/HOME 采用同源 Adaptive；我的鱼获沿用 82dp 方形主体裁切。页面专属冻结合同优先。设计 FROZEN，不代表运行已验收。 |
| `primary_capture_button_v1` · 主拍摄按钮 | FROZEN | V1.0 旧公共按钮已废弃；当前唯一视觉 Authority 为 capture_button_main_v1.png V1.1。 |
| `action_button_v1` · 主 / 次操作按钮 | FROZEN | V1.1 完整冻结：三档基础视觉、Login/Register 单按钮、两组双按钮、PRIMARY 五态、SECONDARY_STRONG 五态、SECONDARY_MUTED 四态；Muted Loading=N/A。 Recognition Result V1.1 的 RESULT_SAVE / muted Continue / equal dual CTA 为已登记的页面例外，优先于旧 44/56 使用示例，但不改变三档公共按钮冻结资产。 |
| `text_action_v1` · 文字操作 | FROZEN | V1 FROZEN：创建账号/去登录=STRONG；修改鱼种/全部/编辑=NORMAL；忘记密码？=MUTED；44dp 最小点击区。 |
| `icon_action_v1` · 图标操作 | FROZEN | V1 FROZEN：NAVIGATION / UTILITY / CONTEXT 三大家族；44dp 点击区；默认透明容器；复杂媒体允许 36dp Mist 支撑面；Loading=N/A。 |
| `icon_library_v0_1` · 图标 | FROZEN | 43 个独立语义 SVG 母版已全部正式设计冻结，统一 24×24 viewBox、1.75 线宽、currentColor；SVG 完整性 43/43 验证。冻结范围仅限设计资源，不代表 Android 已改造或页面验收。 |
| `top_navigation_v1` · 顶部导航 | FROZEN | Top Navigation V1 已整体冻结：TITLE_ONLY、BACK_TITLE、BACK_TITLE_ACTIONS 三个组合均有独立 Frozen Spec 与 Visual Authority；左侧子菜单直达工作区。 Recognition Result 05–09 的居中标题仅为独立登记的页面例外，不新增第四种公共组合；Species Selector 仍属于 BACK_TITLE。 |
| `mist_glass_surface_v1` · 雾面玻璃 | FROZEN | 玻璃只用于可读性与层次，不允许成为视觉特效主体。 |
| `color_typography_v1` · 颜色与字体 | FROZEN | 金色只用于品牌、重要时刻和有意义的记录，普通导航与普通编辑不得滥用。 |
| `spacing_radius_v1` · 间距与圆角 | FROZEN | 页面应优先使用共享间距与圆角 token，不因单页适配而生成新的任意值。 |
| `species_picker_v1` · 共享鱼种选择器 | PARTIAL | No note in registry. |
| `default_profile_avatar_v1` · 默认资料头像 | FROZEN | No note in registry. |
| `legal_document_shell_v1` · 法律文档壳 | FROZEN | No note in registry. |
| `yu_jian_catch_hero_card_v1` · YuJianCatchHeroCard | FROZEN | One shared component; HOME and DETAIL use the same landscape / portrait / extreme portrait / embedded letterbox media rules. |
| `fish_memory_capture_v1` · Fish Memory Capture | FROZEN | Capture attaches source media to an existing FishRecord. Recognition UI is excluded. |

## Cross-system contract completeness

| Contract | Status | Evidence and gap |
|---|---|---|
| Product / canonical flow | PARTIAL | Phase 1 master flow has 34 nodes and 55 edges; 15 registered features mapped. Gap: The Save Catch target conflicts: current frozen result contract saves to Home; the requested master-flow shorthand says save then Detail A/B. Owner decision required before implementation. |
| Manager navigation and registry | VERIFIED_COMPLETE | 15 features, 62 top-level Hi-Fi views, 70 nested children, 13 scenarios, 16 shared systems; 150 map nodes. All registered Hi-Fi routes and manager route patterns exist. Gap: Legacy/hidden compatibility entries are routeable and must stay labeled as aliases or hidden. |
| Page/state and transitions | PARTIAL | A 150-node page/state map and feature-specific state specs are indexed. Gap: No single page-independent schema proves every empty/loading/error/offline/permission/signed-in/guest branch or its exit behavior. |
| Visual authority and provenance | PARTIAL | All 658 design/** files have path/size/Git blob identity; 248 image/vector candidates separated from documents. Gap: Raw SHA-256 was recomputed for 579 files; 79 binaries were omitted by GitHub contents API. Five key images were visually sampled. External Library-only or upstream originals are outside this repository audit. |
| Color / typography tokens | PARTIAL | Color, typography, spacing and radius token files are present; role-based color and scale usage exists. Gap: Global font family, font weight mapping, line height, letter spacing, license/fallback, and system font scaling behavior are not declared as one resolved contract. |
| Background system | VERIFIED_COMPLETE | Five frozen treatments and two canonical lake masters are registered with a usage map. Gap: Page override and exception rules should remain explicit when a page uses the Sunrise Hero or dynamic capture photo. |
| Icon system | PARTIAL | 43 SVG masters are design-frozen at 24×24, 1.75 stroke, round caps/joins and currentColor. Gap: Android VectorDrawable/resource mapping and page-level 44dp touch target verification are not established by the design-only checks. |
| Shared components | PARTIAL | 16 shared systems are registered; 15 are FROZEN and Species Picker is PARTIAL. Gap: Frozen design is not shared-component runtime acceptance; cross-page collision and state parity evidence is uneven. |
| Responsive layout / accessibility | PARTIAL | Feature-specific responsive/accessibility specs exist for Fish Guide and account flows; 44dp action targets are specified in common action components. Gap: A global viewport, landscape/tablet, font-scaling, contrast, focus/order, and accessibility error-state matrix is not present across all 15 features. |
| Behavior / state machine | PARTIAL | 14/15 feature behavior modalities are marked FROZEN; recognition has a separate state timeline. Gap: Feature status does not uniformly resolve route guards, loading, repeated entry, save failure, permission denial, offline, and retry outcomes. |
| Motion / haptic / sound | PARTIAL | 9/15 features have FROZEN motion, haptic and sound; some explicitly freeze NONE. Gap: Record Date motion is PARTIAL; Detail and legacy B-side omit motion/haptic/sound specs; absence must not be interpreted as permission to invent behavior. |
| Data / API / database mapping | PARTIAL | Android source and backend main commit 05554ce1b7104c7bce05610979dfc3478b6d24a5 expose catches, fish knowledge, auth/profile/privacy and inference upload route families. Gap: Route existence is verified, not every displayed field → response field → database column → empty/error state; Record Date and Habitat UI routes are absent. |
| Asset / media behavior | PARTIAL | Fish media fit/crop contracts and shared asset manifests exist. Gap: 79 raw binaries were inaccessible to direct SHA-256 calculation; 19 blocked frozen references have no direct current SHA-256 declaration in scanned authority. Their path and Git blob SHA remain recorded. |
| Acceptance / test evidence | PARTIAL | Some features carry runtime/evidence manifests and explicit design closure records. Gap: Evidence is modality-specific and uneven; this Phase 1 did not run Android, emulator, device, APK, backend, or database tests. |
| Legal / consent / data lifecycle | PARTIAL | Privacy consent and legal shell/content structures exist. Gap: External legal copy is gated; password reset, export, and account deletion are explicitly deferred/ComingSoon in current UI. |

## Highest-impact audit findings

1. **P0 — Save Catch destination needs product-owner resolution.** The requested Phase 1 master-flow shorthand describes saving into Detail A/B; the current frozen result contract sends Save Catch to Home, while Continue Memory opens Detail B. Do not silently rewrite either authority.
2. **P1 — Record Date is design-only in Android.** Its RD01/RD02 visuals and RD03 behavior are frozen, but the runtime modality is MISSING and no Android navigation destination was found.
3. **P1 — Record Date sample data conflicts.** The year illustration says 68 catches while the month samples sum to 59. The authority marks its numbers illustrative, not a source of truth.
4. **P1 — Share templates are design-only.** T01/T02 product behavior is described, but current Android share uses a plain-text chooser; no T01/T02 route or rendered output was found.
5. **P1 — My Habitat has no runtime route.** Do not assume the design reference is implemented.
6. **P1 — Global typography and responsive/accessibility coverage is incomplete.** Existing contracts are useful and frozen within modules, but family/weight/line-height/fallback/scaling and a shared viewport/accessibility acceptance matrix are not closed.
7. **P2 — Raw checksums are incomplete for large binaries.** 79 files have known baseline Git blob identities but raw SHA-256 could not be recalculated. Nineteen blocked frozen references also lack a direct current SHA-256 declaration in scanned authorities. Original-source provenance is explicitly recorded where a manifest declares it; license terms were not declared in the scanned authorities. This is a tool access limit, not a missing repository file.
8. **P2 — Icon SVG freeze is not Android parity evidence.** 43 SVG masters exist and pass their recorded design checks; resource conversion and touch-target parity remain implementation checks.

## Route and data boundary

Backend route presence was confirmed at the recorded backend commit for catch archive/B-side, Fish Guide, auth/profile/privacy, and inference feedback upload. This establishes that endpoint families exist in source. It does not establish production deployment, schema compatibility for every field, response behavior, API availability, or UI integration for every listed state. Account password reset, export, and deletion remain explicitly deferred/ComingSoon in the Android UI. Record Date and Habitat have no matching Android destination in the audited navigation graph.

## Input for Checkpoint D

Checkpoint D must create the conflict register, decisions-required list, page/readiness baseline, and preliminary dependency map from this evidence. The two non-negotiable gates to carry forward are the Save Catch destination decision and runtime absence for Record Date / Habitat / template output.