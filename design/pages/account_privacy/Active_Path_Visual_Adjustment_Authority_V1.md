# Account & Privacy · Active Path Visual Adjustment Authority V1

Status: **FROZEN**
Authority mode: **COMPOSITE — EXISTING HI-FI BINARY + THIS ADJUSTMENT SPEC**
Scope: `02A / 03A / 03B / 04A / 04B / 04C`
Binary policy: **NO REGENERATION / NO RE-ENCODE / NO COPY-RENAME**

## 1. Freeze decision

The existing Account & Privacy high-fidelity PNGs are retained as the visual base authorities.

No new high-fidelity images are required for this active-path design freeze.

Final design authority for each page/state is:

> **existing repository Hi-Fi binary + current page/behavior spec + this Visual Adjustment Authority + referenced Shared Design System authorities**

Where an old binary conflicts with a newer written/shared authority, the newer written/shared authority wins.

This is an intentional composite authority model. It does not modify the original PNG bytes.

## 2. Global override order

Priority, highest first:

1. Current page/behavior spec
2. This Visual Adjustment Authority
3. Shared Design System authority
4. Existing high-fidelity PNG composition
5. Historical package notes

The existing PNG remains authoritative for:

- overall composition;
- spatial hierarchy;
- major card/surface grouping;
- relative visual rhythm;
- intended page density;
- state-specific visual composition.

The PNG does **not** override newer decisions for:

- page naming;
- background treatment;
- shared component tokens;
- permission/consent semantics;
- Deferred product state;
- default-avatar semantics;
- accessibility requirements.

## 3. Shared overrides — all 8 existing Hi-Fi states

Apply without regenerating the authority PNG:

### Background

Current implementation authority:

`Morning_Lake_Master_V1 / BG_CONTENT`

If the old PNG contains stronger sunrise / golden reflection treatment, treat that as historical atmosphere only.

Do not reproduce:

- strong visible sun;
- hero-level golden reflection;
- BG_ENV_HERO prominence

on current Account & Privacy runtime surfaces.

### Top Navigation

Use current Shared Top Navigation V1 / `BACK_TITLE`.

Old pixel details do not override current component authority.

### Typography / spacing / surfaces

Use current:

- Color & Typography V1
- Spacing & Radius V1
- Mist Glass / settings surface authority
- Action / Text / Icon authorities

Do not sample old screenshot pixels to recreate deprecated token values.

### Accessibility

Current shared accessibility requirements override any historical screenshot limitation:

- minimum intended 44dp interactive target;
- semantic labels for icon actions/switches;
- state not communicated by color alone;
- font-scale-safe layout;
- dynamic safe-area/inset handling.

## 4. AP02A · 我的

Base Hi-Fi:

`design/pages/account_privacy/My/00_My.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `6a04e8a74d7337e5f3b41f48329b649444636bd85a83c69a24839a2603513b1b`

Adjustments:

1. Background implementation uses current `BG_CONTENT`; do not reproduce the old strong sunrise/gold reflection.
2. Navigation label `账号与登录` is superseded by **`账号与安全`**.
3. Avatar semantics use Shared Default Profile Avatar V1:
   - real avatar when available;
   - shared default avatar when missing/load-failed;
   - Guest is a different state.
4. Identity secondary copy should represent account identity when useful; do not preserve outdated marketing-style copy merely because it appears in the screenshot.
5. Fishing summary semantics are frozen:
   - 鱼种 → Fish Guide
   - 鱼获 → My Catches
   - 记录天数 → informational
6. No badges, XP, streak, level, rarity or achievement-game semantics.

Result:

**Visual = FROZEN via composite authority.**

## 5. AP03A · 账号与安全

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/00_Account_Login.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `8c6c88c21db4bad9d7b2b1ed02079d22a13362217abcc4d03b60bb6ce9bba269`

Adjustments:

1. Historical page title `账号与登录` is superseded by **`账号与安全`**.
2. Parent navigation is `我的 → 账号与安全`.
3. Keep the existing composition for:
   - 当前账号
   - 修改密码
   - 数据与隐私
   - 退出当前账号
4. `退出当前账号` remains a restrained logout action; it is not `注销账号`.
5. Use current BG_CONTENT / BACK_TITLE / settings-row components.

Result:

**Visual = FROZEN via composite authority.**

## 6. AP03B · 修改密码

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/01_Change_Password.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `00e99aa8c8eff1b0274daf9709f50b3399badd8e9f8c551bd345b6fa9a03aaf2`

Adjustments:

1. Preserve existing form composition.
2. Canonical default state:
   - 当前密码 empty
   - 新密码 empty
   - 确认新密码 empty
   - no error
   - no success message
   - Save disabled
3. Current behavior contract owns:
   - 6–72 character new-password rule;
   - confirmation match;
   - new password differs from current;
   - error mapping;
   - Loading and success state.
4. Password visibility action uses current Shared Icon Action semantics.
5. Use current BG_CONTENT / BACK_TITLE / form tokens.

Result:

**Visual = FROZEN via composite authority.**

## 7. AP04A · 数据与隐私

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/00_Data_Privacy.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `602d75be9b1694df3be3f0c85b5433340d7680bf61bdd86d4561d0b5b7368886`

Adjustments:

1. Preserve the existing page grouping/composition.
2. Active rows:
   - AI 模型改进
   - 位置权限
3. Deferred rows:
   - 导出我的数据
   - 注销账号
4. Deferred rows remain visible but must communicate **已延后 / 即将推出** and must not imply that the historical future state machines are currently available.
5. Privacy Policy remains a legal-document entry.
6. AI model improvement state must be server-confirmed:
   - 读取中…
   - 已开启
   - 已关闭
7. Location state:
   - 使用期间
   - 已拒绝
   - 未授权
8. Use current BG_CONTENT / BACK_TITLE / settings-row / switch authority.

Result:

**Visual = FROZEN via composite authority.**

## 8. AP04B · AI 模型改进 · 开启确认

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/01_Enable_Consent.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `d10366f930006685dc5f16dd299aece9b60182fc81c03cc4ce7bbf70d86f2009`

Adjustments:

1. Preserve Bottom Sheet composition.
2. Consent default is OFF unless valid consent exists.
3. Frozen title:
   `帮助改善鱼种识别`
4. Frozen scope copy:
   `仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。`
5. Actions:
   - `允许用于模型改进`
   - `暂不开启`
6. No pre-checked consent.
7. No guilt/persuasion copy.
8. Current Shared Bottom Sheet / Action tokens override old pixel styling.

Result:

**Visual = FROZEN via composite authority.**

## 9. AP04B · AI 模型改进 · 关闭确认

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/02_Disable_Confirmation.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `ea424d68844ca24a687a15fc9bbbe6d9f124f156661e283336879dec2bd8fa3c`

Adjustments:

1. Preserve existing confirmation composition.
2. Frozen title:
   `关闭模型改进？`
3. Copy must state that new eligible correction data stops contributing after withdrawal.
4. Copy must explicitly communicate no effect on:
   - 拍照识鱼
   - 保存鱼获
   - 修改识别结果
5. Actions:
   - `关闭`
   - `保持开启`
6. Failed withdrawal must preserve the previously confirmed ON state.

Result:

**Visual = FROZEN via composite authority.**

## 10. AP04C · 位置权限 · Info

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/01_Info.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `0ba2f48241017118e4cf24611553a2cce9e969355924e32f950e8d465ecc51aa`

Adjustments:

1. Preserve existing information-state composition.
2. Opening this state does **not** request Android location permission.
3. Frozen core copy:
   `只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。`
4. Must state:
   `拒绝不会影响拍照识鱼和保存鱼获。`
5. Primary action:
   `知道了`
6. No background-location semantics.

Result:

**Visual = FROZEN via composite authority.**

## 11. AP04C · 位置权限 · Denied

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/02_Denied.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `db3610f8f99751928abd8e3bab04492ca44ea4f4bee59c575bd3a585bb6f0679`

Adjustments:

1. Preserve denied-state composition.
2. Denial must not block:
   - recognition;
   - FishRecord save;
   - species correction;
   - manual location path where available.
3. Do not repeatedly request permission on page entry.
4. Do not use destructive red treatment or guilt copy.
5. If a settings/deep-link action exists, it is optional recovery—not a blocking primary requirement.

Result:

**Visual = FROZEN via composite authority.**

## 12. Binary preservation

These 8 PNG binaries are frozen **as-is**.

Do not:

- edit;
- regenerate;
- optimize;
- compress;
- convert;
- rename/copy into a fake new authority path;
- crop from them into new authority assets.

Their current repository paths and SHA-256 values are the binary evidence.

## 13. Implementation rule

When implementing current Android UI:

- use the existing high-fidelity PNG for composition/reference;
- apply the delta rules in this document;
- use current Shared Design System components/tokens;
- do not attempt pixel-for-pixel reproduction of known superseded screenshot details.

This composite model is the final visual authority for the active Account & Privacy path V1.
