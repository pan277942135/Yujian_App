# 账号与隐私 · Design Authority Index V2

Status: **PARTIAL**
Scope: **Design Manager organization and design authority indexing only**

本目录统一管理「账号与隐私」的现有设计来源。旧 `manifest.json` 原样保留为历史 23 项资产清单，不代表其中 22 张页面图全部是当前 Frozen Authority。

## Authority Classification

### CURRENT FROZEN

- **Login V2.1** — `Login/frozen/Login_V2_1_Frozen_Final.png`; spec: `Login/Login_V2_BG_CONTENT_Revision_V2_1.md`.
- **Register V2** — `Register/frozen/Register_V2_Frozen_Final.png`; spec: `Register/Register_V2_Design_Spec_V1.md`.
- **Edit Profile Home V1.1** — `My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp`.
- **Avatar Change V1** — `My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp`.
- **Nickname Edit V1** — `My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp` is recorded as FROZEN in the existing spec and registry. Its current repository SHA-256 (`26abab813e915fed11c34e1c9febf2eac96d3876e6fa945ccb22372b8d62079b`) does **not** match the frozen manifest / README SHA-256 (`5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`). The original binary is preserved unchanged; this identity discrepancy remains open and is not reported as a parity PASS.

Login V2.1 and Register V2 are shown from their canonical PNG paths. `Login/00_Login.png` is labelled **Superseded / Historical** and is not the current Login Authority.

### LEGACY / REVIEW REQUIRED

The following exact repository images remain review references, not current Frozen Authorities:

- My (`My/00_My.png`): current BG_CONTENT uses the no-sun `Morning_Lake_Master_V1`; the legacy board visibly uses sunrise and a strong golden reflection. Its existing layout also needs current account/profile information-architecture review. No redraw was made.
- Account & Login and Change Password.
- Data & Privacy home.
- AI Model Improvement: Enable Consent / Disable Confirmation.
- Location Permission: Info / Denied.
- Privacy Policy shell; legal body copy is not final.

### MVP DEFERRED DESIGN REFERENCES

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
- `shared/morning_lake_background.png` is the legacy package background only; it is not current Background Authority.

## Design Manager Navigation

The only visible top-level module is **账号与隐私**. Its five workspaces are:

1. `01 · 登录与注册`
2. `02 · 我的与资料`
3. `03 · 账号与登录`
4. `04 · 数据与隐私`
5. `05 · 法律与关于`

Forgot Password, consent states, location states, Export, and Delete are grouped into one workspace each; their existing repository images are shown together with the correct status labels. The former `04 · 保存与反馈`, `05 · 异常与边界状态`, and `06 · 交互与适配规范` Edit Profile menus are folded into the Edit Profile authority index; their source files remain for history.

The parent page is the Overview. It shows package state, current/legacy/deferred/missing classifications, shared-system dependencies, and Runtime status separately; no duplicate Overview child is added.

## Manifest / Runtime Boundaries

- `manifest.json` remains the unchanged historical 23-asset inventory.
- `authority_manifest_v2.json` is the current page/flow classification index and records exact repository image fingerprints without copying binaries.
- Overall Account & Privacy design remains **PARTIAL**.
- Runtime status remains separately reported as **PARTIAL**. This task does not change or close Android runtime behavior.
