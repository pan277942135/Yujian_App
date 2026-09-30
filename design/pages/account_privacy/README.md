# 账号与隐私 · Design Authority Index V2

Status: **PARTIAL**
Scope: **Design authority indexing + current MVP active-path closure**

本目录统一管理「账号与隐私」的现有设计来源。旧 `manifest.json` 原样保留为历史 23 项资产清单，不代表其中 22 张页面图全部是当前 Frozen Authority。

## Authority Classification

### CURRENT FROZEN

- **Login V2.1** — `Login/frozen/Login_V2_1_Frozen_Final.png`; spec: `Login/Login_V2_BG_CONTENT_Revision_V2_1.md`.
- **Register V2** — `Register/frozen/Register_V2_Frozen_Final.png`; spec: `Register/Register_V2_Design_Spec_V1.md`.
- **Edit Profile Home V1.1** — `My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp`.
- **Avatar Change V1** — `My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp`.
### INTEGRITY BLOCKED

- **Nickname Edit V1** — behavior / machine / development contracts remain frozen, but the current repository WebP SHA-256 (`26abab813e915fed11c34e1c9febf2eac96d3876e6fa945ccb22372b8d62079b`) does **not** match the declared frozen visual SHA-256 (`5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`). Therefore Visual Authority status is **BLOCKED_INTEGRITY**, not FROZEN. Recover the originally approved byte-identical source; do not regenerate or re-encode it.

Login V2.1 and Register V2 are shown from their canonical PNG paths. `Login/00_Login.png` is labelled **Superseded / Historical** and is not the current Login Authority.

### ACTIVE MVP MAIN PATH · BEHAVIOR / IA FROZEN

Canonical active path:

`我的 → 账号与安全 → 修改密码 / 数据与隐私 → AI 模型改进 / 位置权限`

Parent contract:

`Active_Path_Contract_V1.md`

Page-level authorities:

- **我的** — `My/My_Page_Spec_V2.md`
- **账号与安全** — `My/Account_Login/Account_Security_Home_Spec_V1.md`
- **修改密码** — `My/Account_Login/Change_Password_Spec_V1.md`
- **数据与隐私** — `My/Account_Login/Data_Privacy/Data_Privacy_Home_Spec_V1.md`
- **AI 模型改进** — `My/Account_Login/Data_Privacy/AI_Model_Improvement/AI_Model_Improvement_Consent_Spec_V1.md`
- **位置权限** — `My/Account_Login/Data_Privacy/Location_Permission/Location_Privacy_Spec_V1.md`

For these six items:

- IA / Behavior / Content Structure = **FROZEN**
- Visual = **ACTIVE_CLOSURE**
- historical PNGs remain reference evidence only;
- current shared-system-aligned high-fidelity binaries are still required before Visual FROZEN.

The old design label **账号与登录** is superseded by **账号与安全**. Android runtime may keep the historical label until Runtime Parity; runtime wording does not override design authority.

### LEGACY / REVIEW REQUIRED

The following exact repository images remain review references, not current Frozen Authorities:

- My (`My/00_My.png`): current BG_CONTENT uses the no-sun `Morning_Lake_Master_V1`; the legacy board visibly uses sunrise and a strong golden reflection. The V2 IA is now frozen, but this old visual is not current authority.
- Historical Account & Login visual: composition reference only; its title is superseded by **账号与安全**.
- Change Password visual reference.
- Data & Privacy visual reference.
- AI Model Improvement: Enable Consent / Disable Confirmation visual references.
- Location Permission: Info / Denied visual references.
- Privacy Policy shell; legal body copy is not final.

### MVP DEFERRED DESIGN REFERENCES

Design Manager status for these flows is **DEFERRED**, not PARTIAL. Their design references are preserved, but they are intentionally outside the current MVP.

- **Forgot Password** — the four existing images are future-flow references. Production remains **ENTRY ONLY / COMING SOON** until a verified recovery channel exists.
- **Export My Data** — five existing state images remain a design reference. Production remains **ENTRY ONLY / COMING SOON**.
- **Delete Account** — three existing state images remain a design reference. Production remains **ENTRY ONLY / COMING SOON**; no fake destructive flow is implied.

### DESIGN AUTHORITY MISSING

- **About YuJian** — runtime surface exists; independent Frozen visual is missing.
- **User Agreement** — runtime surface exists; independent Frozen visual is missing.

No replacement artwork, runtime screenshot, or placeholder is used for either page. Privacy Policy visual structure and final legal text are separate; legal copy awaits legal review.

## Shared System Dependencies

- Current shared lake source: `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` with `BG_CONTENT` as appropriate.
- Shared Top Navigation V1 / `BACK_TITLE`, Color & Typography, Spacing & Radius, Action / Text / Icon components remain linked through `design/registry/shared_design_system_v1.json`.
- Shared Default Profile Avatar V1: `design/system/components/profile_avatar_v1/default_profile_avatar_contract.json`. Logged-in avatar missing/load failure uses this shared fallback; Guest account entry is a separate semantic state.
- `shared/morning_lake_background.png` is the legacy package background only; it is not current Background Authority.

## Design Manager Navigation

The only visible top-level module is **账号与隐私**. Its five workspaces are:

1. `01 · 登录与注册`
2. `02 · 我的与资料`
3. `03 · 账号与安全`
4. `04 · 数据与隐私`
5. `05 · 法律与关于`

Forgot Password, consent states, location states, Export, and Delete are grouped into one workspace each; their existing repository images are shown together with the correct status labels. The former `04 · 保存与反馈`, `05 · 异常与边界状态`, and `06 · 交互与适配规范` Edit Profile menus are folded into the Edit Profile authority index; their source files remain for history.

The parent page is the Overview. It shows package state, current/legacy/deferred/missing classifications, shared-system dependencies, and Runtime status separately; no duplicate Overview child is added.

## Manifest / Runtime Boundaries

- `manifest.json` remains the unchanged historical 23-asset inventory.
- `authority_manifest_v2.json` is the current page/flow classification index and records exact repository image fingerprints without copying binaries.
- Active MVP main-path **IA / Behavior / Content Structure is FROZEN**.
- Active-path Visual closure remains **ACTIVE_CLOSURE** until new approved Hi-Fi binaries are present.
- Overall Account & Privacy design remains **PARTIAL** because Legal/About, Edit Profile state closure, Nickname integrity recovery and active-path visual closure remain outstanding.
- Runtime status remains separately reported as **PARTIAL**. Design freeze does not imply Android runtime parity.
