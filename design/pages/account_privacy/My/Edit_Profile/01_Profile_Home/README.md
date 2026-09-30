# 01 · 编辑资料首页 · Frozen Design Spec V1.1

Status: **FROZEN**
Scope: **Design only / default unchanged state**

## 1. Frozen Authority

- Visual Authority: `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp`
- Frozen manifest: `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/manifest.json`
- Approved source dimensions: **941 × 1672**
- Approved source SHA-256: `e92cbbe99e59fdcf5b9d75feaeb1edf8b08c786ae285cb128b7271d794c570a7`
- Frozen WebP SHA-256: `791a6b873878341e84ac48cc47861789b20dfee904effd071af5427111bbaf96`

The frozen image governs page composition, hierarchy, visual weight and default-state styling.

## 2. Scope

This page covers only the default Edit Profile home:

- current avatar;
- account (read-only);
- nickname;
- Save changes button.

The following remain separate page authorities and are **not frozen by this page**:

- 02 · 头像修改;
- 03 · 昵称编辑.

Save Feedback, Edge States, and Interaction / Adaptation are consolidated as PARTIAL sections in `../README.md`; they are not separate Design Manager menus.

## 3. Top Navigation

Use **Top Navigation V1 / BACK_TITLE / FROZEN**.

- Back → Icon Action V1 / Navigation.
- Center title: **编辑资料**.
- Do not add a page-level Save action to the top navigation.
- System status/navigation bars shown in a mockup are not part of the visual authority.

## 4. Background

Use the shared **Morning_Lake_Master_V1 / BG_CONTENT** family.

Frozen direction:

- no visible sun;
- cool morning mist / lake atmosphere;
- scenery is subordinate to profile editing;
- lower content area transitions into a clean white/MistWhite field;
- no brand title or slogan in the scenery.

The background must not be replaced with a new lake world.

## 5. Avatar area

Frozen hierarchy:

1. current avatar;
2. Camera Icon Action anchored at avatar lower-right;
3. Text Action: **更换头像**.

Rules:

- runtime uses the user's current avatar;
- the person shown in the frozen mockup is illustrative, not a fixed identity asset;
- avatar editing behavior belongs to `02 · 头像修改`;
- the Camera icon reuses the shared Icon Action visual language.

## 6. Account · read-only

The account row is visible but immutable.

- Label: **账号**
- Runtime value: current account identifier
- Right-side state: **不可修改**
- The sample value `yujian_2025` in the frozen image is illustrative only.

Do not provide cursor, clear action, edit affordance or keyboard focus for the account row.

## 7. Nickname

The nickname row is editable.

- Label: **昵称**
- Value: current nickname
- Clear action is allowed when text exists
- Counter: `current / 20`
- Maximum length: **20 characters**

Detailed Default / Focus / Filled / Error behavior remains owned by `03 · 昵称编辑`.

## 8. Save changes

Primary page action: **保存修改**.

Frozen default-state rule:

- no profile changes → **Disabled**;
- avatar or nickname becomes dirty → may transition to enabled Primary state.

Saving / Success / Failure / Retry visuals are listed under the PARTIAL `04 · Save Feedback` section in `../README.md`; this page does not freeze those states.

## 9. Footer decoration

The approved visual keeps a low-salience waterside grass foreground at the absolute bottom.

Rules:

- decorative only;
- subordinate to the form;
- does not cover fields, counter or CTA;
- not baked into `Morning_Lake_Master_V1`;
- no automatic animation.

## 10. Motion / haptic / sound

For this frozen home state:

- no automatic environmental motion;
- no automatic haptic;
- no sound.

Interactive feedback for avatar, nickname and save is defined in their dedicated subpages.

## 11. Runtime boundary

This design freeze does **not** assert Android runtime parity, emulator evidence or production closure.

Runtime should consume actual account/avatar/nickname values; the visual authority does not freeze sample user data.
