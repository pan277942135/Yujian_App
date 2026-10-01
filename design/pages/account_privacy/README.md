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
- **Edit Profile State & Interaction V1** — `My/Edit_Profile/Edit_Profile_State_Interaction_Spec_V1.md`; Save Feedback / Edge States / Interaction & Adaptation are FROZEN without standalone image assets.
- **Nickname Edit V1** — `My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp`; byte-identical recovery verified with SHA-256 `5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`.

Login V2.1 and Register V2 are shown from their canonical PNG paths. `Login/00_Login.png` is labelled **Superseded / Historical** and is not the current Login Authority.

### ACTIVE MVP MAIN PATH · DESIGN FROZEN

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
- Visual = **FROZEN**
- existing repository PNGs are retained byte-identical as **Frozen Base Hi-Fi**;
- no replacement images are required;
- current differences are frozen in `Active_Path_Visual_Adjustment_Authority_V1.md`;
- final visual authority is composite: **Base Hi-Fi + page spec + adjustment authority + Shared Design System**.

The old design label **账号与登录** is superseded by **账号与安全**. Android runtime may keep the historical label until Runtime Parity; runtime wording does not override design authority.

### FROZEN COMPOSITE VISUAL AUTHORITIES

The following existing binaries are now frozen as the active-path visual base:

- `My/00_My.png`
- `My/Account_Login/00_Account_Login.png`
- `My/Account_Login/01_Change_Password.png`
- `My/Account_Login/Data_Privacy/00_Data_Privacy.png`
- `My/Account_Login/Data_Privacy/AI_Model_Improvement/01_Enable_Consent.png`
- `My/Account_Login/Data_Privacy/AI_Model_Improvement/02_Disable_Confirmation.png`
- `My/Account_Login/Data_Privacy/Location_Permission/01_Info.png`
- `My/Account_Login/Data_Privacy/Location_Permission/02_Denied.png`

They are **not edited or regenerated**. Current deltas such as BG_CONTENT, `账号与安全` naming, DEFERRED states, Shared Avatar and current consent/location semantics are owned by:

`Active_Path_Visual_Adjustment_Authority_V1.md`

### LEGAL & ABOUT

Page design status:

- **About YuJian** — DESIGN FROZEN via Shared Component Composition; no standalone Hi-Fi is required.
- **Privacy Policy** — Visual Shell FROZEN using the existing `04_Privacy_Policy.png` + Shared Legal Document Shell.
- **User Agreement** — Visual Shell FROZEN by reusing the same Shared Legal Document Shell; no duplicate Hi-Fi is required.

Content governance:

- Privacy Policy content architecture = FROZEN
- User Agreement content architecture = FROZEN
- Final production Legal Copy for both = **LEGAL_REVIEW_REQUIRED**

Authority index:

`legal/README.md`

Shared legal shell:

`legal/Legal_Document_Shell_Spec_V1.md`

### MVP DEFERRED DESIGN REFERENCES

Design Manager status for these flows is **DEFERRED**, not PARTIAL. Their design references are preserved, but they are intentionally outside the current MVP.

- **Forgot Password** — the four existing images are future-flow references. Production remains **ENTRY ONLY / COMING SOON** until a verified recovery channel exists.
- **Export My Data** — five existing state images remain a design reference. Production remains **ENTRY ONLY / COMING SOON**.
- **Delete Account** — three existing state images remain a design reference. Production remains **ENTRY ONLY / COMING SOON**; no fake destructive flow is implied.

### LEGAL COPY REVIEW REQUIRED

There is no remaining Visual Authority gap for About / Privacy Policy / User Agreement.

However, final production legal text is **not** frozen.

Before Privacy Policy or User Agreement can be marked final Legal Copy FROZEN, approved legal text must provide/confirm:

- operator/legal entity;
- version/effective date;
- real contact channel;
- actual vendor/SDK/data-flow disclosure where required;
- jurisdiction-specific minors/rights/dispute terms;
- final liability / governing-law language.

Design does not invent these legal facts.

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
- Active MVP main-path **Visual is FROZEN via composite authority**.
- No new active-path Hi-Fi images are required.
- About / Legal page design is FROZEN; final Privacy Policy / User Agreement Legal Copy remains LEGAL_REVIEW_REQUIRED.
- Overall Account & Privacy remains **PARTIAL** only because final production Legal Copy for Privacy Policy / User Agreement remains LEGAL_REVIEW_REQUIRED. Visual design authority no longer has an integrity blocker.
- Runtime status remains separately reported as **PARTIAL**. Design freeze does not imply Android runtime parity.


### INTERACTION FEEDBACK

Account & Privacy Motion / Haptic / Sound are explicitly **FROZEN**.

Authority:

`spec/Account_Privacy_Interaction_Feedback_Spec_V1.md`

Frozen decisions:

- Motion: no feature-specific motion; inherit Shared Action / Icon / Text press/loading behavior and standard platform/shared transitions only.
- Haptic: no feature-specific haptic for save, validation, consent, logout or navigation.
- Sound: no Account & Privacy product sound or audio asset.
- Reduce Motion: inherit Shared behavior; do not add decorative spatial motion.
- Success/error feedback remains textual/semantic, not celebratory sensory feedback.

This is an intentional product decision, not a missing design surface.
