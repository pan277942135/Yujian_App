# Account & Privacy · Active Path Visual Closure Brief V1

Status: **FROZEN PRODUCTION BRIEF**
Scope: visual production / upload only
Does not create a Design Manager menu.

## 1. Goal

Produce the minimum current high-fidelity binary set required to close Visual Authority for:

`我的 → 账号与安全 → 修改密码 → 数据与隐私 → AI Consent → Location`

Behavior / IA / content contracts are already frozen. Visual production must implement them; it may not redesign them.

## 2. Required output set

### AP02A · 我的

Target:
`design/pages/account_privacy/My/frozen/My_V2_Frozen_Final.png`

One full-page current authority.

Must:
- use BG_CONTENT / no-sun Morning_Lake_Master_V1;
- use current BACK_TITLE;
- use Shared Default Profile Avatar V1 semantics;
- use V2 settings labels including `账号与安全`.

Historical `My/00_My.png` is composition reference only.

### AP03A · 账号与安全

Target:
`design/pages/account_privacy/My/Account_Login/frozen/Account_Security_Home_V1_Frozen.png`

One full-page current authority.

Must use title:
`账号与安全`

Do not reproduce historical title:
`账号与登录`

### AP03B · 修改密码

Target:
`design/pages/account_privacy/My/Account_Login/frozen/Change_Password_V1_Frozen.png`

Canonical state:
- fields empty;
- Save disabled;
- no error;
- no success toast/message.

Edge states remain behavior-spec states and do not require separate menu entries.

### AP04A · 数据与隐私

Target:
`design/pages/account_privacy/My/Account_Login/Data_Privacy/frozen/Data_Privacy_Home_V1_Frozen.png`

Canonical state:
- consent state resolved;
- use a stable sample (recommended OFF);
- Location status visible;
- Export / Delete visibly Deferred / Coming Soon without exposing future flows;
- Privacy Policy row visible.

### AP04B · AI 模型改进

Targets:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/frozen/AI_Consent_Enable_V1_Frozen.png`

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/frozen/AI_Consent_Disable_V1_Frozen.png`

These are two Bottom Sheet states in one Design Manager workspace.

The underlying Data & Privacy page must remain visually consistent between the two states.

### AP04C · Location

Targets:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/frozen/Location_Info_V1_Frozen.png`

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/frozen/Location_Denied_V1_Frozen.png`

One workspace, two states.

The Data & Privacy settings view is informational and must not depict an automatic permission request on page entry.

## 3. Total

Required current visual binaries:

**8 PNG files**

1. My
2. Account & Security
3. Change Password
4. Data & Privacy
5. AI Consent Enable
6. AI Consent Disable
7. Location Info
8. Location Denied

## 4. Source hierarchy

Priority:

1. frozen page/behavior spec;
2. Shared Design System;
3. current approved visual authority from adjacent Account surfaces;
4. historical Account & Privacy PNG only as composition/reference evidence.

Historical PNG does not override:

- BG_CONTENT;
- current Top Navigation;
- Shared Default Profile Avatar;
- new Account & Security naming;
- current consent/location semantics.

## 5. Binary rules

Required:

- actual PNG file;
- preserve approved source exactly after freeze;
- record dimensions / bytes / SHA-256;
- create per-workspace frozen manifest;
- register exact path in `authority_manifest_v2.json` and `experience_registry_v1.json`.

Forbidden:

- SVG recreation as final high-fidelity authority;
- HTML/CSS screenshot as substitute;
- Android runtime screenshot as design authority;
- recompress/re-encode after approval;
- screenshot crop from old board and rename;
- silently reuse a legacy PNG whose shared-system treatment is outdated.

## 6. Freeze rule

A child may move:

`ACTIVE_CLOSURE → FROZEN`

only when:

- its required current PNG(s) exist;
- product-approved binary identity is recorded;
- page spec remains unchanged or explicitly versioned;
- Design Manager renders the actual repository binary;
- governance validate passes.

Do not freeze all Account & Privacy merely because these eight visuals close; Legal/About, Edit Profile state closure and Nickname integrity remain independent gates.
