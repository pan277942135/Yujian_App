# Normal Home V1

Role: **Home / Catch Baseline**  
Status: **DESIGN FROZEN — NH01 / expansion in progress**  
Design package: **Closure V1**

## Frozen visual authority

- Canonical reference: `design/system/core_visual_v1/reference/normal_home_v1.png`
- SHA-256: `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377`
- Reference canvas: 1080 × 1920
- System authority: `design/system/core_visual_v1/YuJian_Core_Visual_System_V1.md`
- Page source registry: `source/frozen/source_manifest.json`

The canonical PNG is not duplicated into this page folder. The manifest binds this package to the immutable Core UI V1 binary by path, dimensions and SHA-256.

## Product state

Normal Home is shown when one or more valid FishRecords exist. Login/session state, loading state and statistics do not choose Empty vs Normal Home.

## Design Manager — 二级菜单

Machine-readable navigation: `navigation.json`

**出图原则：关键内容独立出图；辅助状态、组件状态、边界状态按内容合并出图。**

| ID | 二级菜单 | 出图方式 | 当前状态 |
| --- | --- | --- | --- |
| NH01 | 主页面｜多鱼获状态 | 独立高保 | FROZEN — 当前视觉权威 |
| NH02 | 第一条鱼首页 | 独立高保 | **FROZEN · SPEC + VISUAL** |
| NH03 | 页面状态与异常 | 合并规范图 | **SPEC FROZEN / BOARD PENDING** |
| NH04 | 组件状态与内容边界 | 合并规范图 | **SPEC FROZEN / BOARD PENDING** |
| NH05 | 响应式与交互 | 合并规范图 | **SPEC FROZEN / BOARD PENDING** |
| NH06 | 背景与环境权威 | Authority 规范图 | **SPEC FROZEN / AUTHORITY BOARD PENDING** |

### NH01｜主页面｜多鱼获状态

当前上传并冻结的 Normal Home 视觉权威。它本身已经表达多鱼获 Pager：
中央主卡 + 左右相邻鱼获露边。**不再额外生成一张“Multiple Catch”重复高保。**

### NH02｜第一条鱼首页

规范已冻结：`02_first_catch/README.md`。

唯一需要新增的关键首页高保状态：仅有 1 条有效 FishRecord。沿用 NH01 全部环境、布局与组件，仅将 Pager 收敛为**同尺寸居中单卡**；左右无假邻卡、无 Pager 暗示、无第一条鱼庆祝层。NH02 规范与实际生成的高保真 PNG 原图均已冻结；Design Manager 直接展示 Frozen raster Visual Authority，不使用 SVG / 程序化派生图替代。

### NH03｜页面状态与异常

规范已冻结：`03_page_states/README.md`。

一张 2×2 合并板表达 Resolving、Image Unavailable、Refresh Preserves Home、Error ≠ Empty；不拆成多张整页高保。

### NH04｜组件状态与内容边界

规范已冻结：`04_component_content_states/README.md`。

一张组件板合并表达 Real Avatar / Default Avatar / Guest，以及 HOME Hero 缺字段与长文本边界。

### NH05｜响应式与交互

规范已冻结：`05_responsive_interaction/README.md`。

一张规范板合并表达 9:16 / 19.5:9 / 20:9 / 21:9，以及 Pager selected / adjacent / swipe、Single Record 和 Reduce Motion。

### NH06｜背景与环境权威

规范已冻结：`06_background_authority/README.md`。

Normal Home 背景明确冻结为 `Morning_Lake_Master_V1 → BG_ENV_HERO`。NH01/NH02 继续负责完整页面构图；NH06 只负责背景 Source / Treatment / Crop / Authority 边界，禁止从页面截图反向裁出背景母版。

## Frozen hierarchy

1. morning-lake environment
2. real recent catch
3. memory/context
4. statistics
5. secondary navigation
6. primary capture action

Real catch media must remain visually above statistics. The page must not drift into a dashboard, HUD or collection-game hierarchy.

## Package authority

- Feature scope: `spec/Normal_Home_Feature_Spec_V1.md`
- Behavior: `spec/Behavior_Spec_V1.md`
- Visual: `spec/Visual_Spec_V1.md`
- Acceptance: `spec/Acceptance_Criteria_V1.md`
- Motion: `motion/Normal_Home_Motion_Spec_V1.md`
- Haptic: `haptic/Normal_Home_Haptic_Spec_V1.md`
- Sound: `sound/Normal_Home_Sound_Spec_V1.md`
- Assets: `assets/asset_manifest.json`
- Authority order: `authority/authority_map.json`
- NH02 First Catch spec: `02_first_catch/README.md`
- NH03 Page States spec: `03_page_states/README.md`
- NH04 Component & Content spec: `04_component_content_states/README.md`
- NH05 Responsive & Interaction spec: `05_responsive_interaction/README.md`
- NH06 Background Authority spec: `06_background_authority/README.md`
- Design Manager navigation: `navigation.json`
- Closure record: `DESIGN_PACKAGE_CLOSURE_V1.md`
- Machine-readable status: `status.json`

## Shared components

- `YuJianCatchHeroCard / HOME`
- `YuJianPrimaryCaptureButton`
- shared YuJian typography, color, spacing and radius tokens

## Background authority

NH06 is now spec-frozen.

- `Morning_Lake_Master_V1.png` is the **FROZEN reusable Normal Home background source**;
- Normal Home maps to `BG_ENV_HERO` under Background System V1;
- `normal_home_v1.png` and NH02 Frozen PNG remain the authorities for complete page composition;
- the NH06 Authority Board is still pending;
- no screenshot crop may be promoted into a reusable background master.

## Runtime boundary

Runtime closure remains separately documented in `RUNTIME_CLOSURE_V1.md`. This Design Manager navigation update does not redefine Android implementation, backend, model or worker behavior.
