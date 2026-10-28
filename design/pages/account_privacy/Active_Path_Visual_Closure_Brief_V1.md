# Account & Privacy · Active Path Visual Closure Brief V1

Status: **FROZEN — CLOSED**
Scope: current active-path visual authority
Production decision: **REUSE EXISTING HI-FI / NO NEW IMAGE GENERATION**

## 1. Closure decision

The existing 8 Account & Privacy high-fidelity PNGs are sufficient as visual base authorities.

No replacement PNGs are required.

The previous plan to create eight newly named Frozen PNGs is superseded.

Current visual authority model:

> **Existing Hi-Fi binary + Active Path Visual Adjustment Authority V1 + page/behavior spec + Shared Design System**

Adjustment authority:

`design/pages/account_privacy/Active_Path_Visual_Adjustment_Authority_V1.md`

## 2. Frozen binary set

### AP02A · 我的

`design/pages/account_privacy/My/00_My.png`

SHA-256:

`6a04e8a74d7337e5f3b41f48329b649444636bd85a83c69a24839a2603513b1b`

### AP03A · 账号与安全

Base binary:

`design/pages/account_privacy/My/Account_Login/00_Account_Login.png`

SHA-256:

`8c6c88c21db4bad9d7b2b1ed02079d22a13362217abcc4d03b60bb6ce9bba269`

The binary retains the historical title `账号与登录`; the current composite visual authority overrides the runtime/design title to `账号与安全`.

### AP03B · 修改密码

`design/pages/account_privacy/My/Account_Login/01_Change_Password.png`

SHA-256:

`00e99aa8c8eff1b0274daf9709f50b3399badd8e9f8c551bd345b6fa9a03aaf2`

### AP04A · 数据与隐私

`design/pages/account_privacy/My/Account_Login/Data_Privacy/00_Data_Privacy.png`

SHA-256:

`602d75be9b1694df3be3f0c85b5433340d7680bf61bdd86d4561d0b5b7368886`

### AP04B · AI 模型改进 · 开启

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/01_Enable_Consent.png`

SHA-256:

`d10366f930006685dc5f16dd299aece9b60182fc81c03cc4ce7bbf70d86f2009`

### AP04B · AI 模型改进 · 关闭

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/02_Disable_Confirmation.png`

SHA-256:

`ea424d68844ca24a687a15fc9bbbe6d9f124f156661e283336879dec2bd8fa3c`

### AP04C · Location · Info

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/01_Info.png`

SHA-256:

`0ba2f48241017118e4cf24611553a2cce9e969355924e32f950e8d465ecc51aa`

### AP04C · Location · Denied

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/02_Denied.png`

SHA-256:

`db3610f8f99751928abd8e3bab04492ca44ea4f4bee59c575bd3a585bb6f0679`

All eight binaries remain byte-identical.

## 3. Adjustment ownership

The binary is the composition/reference layer.

The following current authorities override outdated screenshot details:

1. page/behavior spec;
2. `Active_Path_Visual_Adjustment_Authority_V1.md`;
3. Shared Design System.

Examples:

- My: current implementation uses BG_CONTENT rather than reproducing the old strong sunrise/gold atmosphere.
- Account Security: current title is `账号与安全`, not the text embedded in the historical PNG.
- Data & Privacy: Export/Delete are DEFERRED / Coming Soon.
- AI Consent: current consent scope/copy and transaction behavior are authoritative.
- Location: settings/info state never triggers permission merely by opening.

## 4. Freeze status

The following children may now be reported as Visual **FROZEN** through composite authority:

- 02A · 我的
- 03A · 账号与安全首页
- 03B · 修改密码
- 04A · 数据与隐私首页
- 04B · AI 模型改进
- 04C · 位置权限

This does not freeze unrelated outstanding Account & Privacy work:

- Nickname visual integrity remains BLOCKED_INTEGRITY.
- Edit Profile 04–06 state/interaction closure remains separate.
- About YuJian / User Agreement remain separate.
- Privacy Policy legal-copy approval remains separate.

## 5. Forbidden work

Do not create replacement images solely to make the screenshot reflect every current token/copy decision.

Do not:

- regenerate;
- redraw;
- convert PNG/WebP;
- re-encode;
- copy/rename as a fake new frozen file;
- use a runtime screenshot as replacement authority;
- create SVG/HTML reconstruction as a replacement.

The design system owns the deltas; the existing approved visual assets remain preserved.

## 6. Runtime parity

Android implementation should reproduce:

> existing Hi-Fi composition + current written/shared overrides

not:

> every obsolete pixel/text detail embedded in the old PNG.

Runtime parity remains a separate implementation gate.
