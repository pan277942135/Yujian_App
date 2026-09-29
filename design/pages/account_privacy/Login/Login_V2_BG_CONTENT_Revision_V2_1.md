# Login V2 · BG_CONTENT Visual Revision V2.1

Status: **ACTIVE_CLOSURE**
Scope: **Design authority delta**

## 1. Objective

Login no longer owns an independent lake-world background.

It inherits the shared YuJian background system:

- System: `Morning Lake Background System V1`
- Master: **Morning_Lake_Master_V1**
- Master characteristic: **no sun / restrained cool morning lake**
- Variant: **BG_CONTENT**

The existing `00_Login.png` remains a structural/layout reference only. Its legacy background is superseded by this revision.

## 2. Background treatment

Target:

- MistWhite veil: **15% nominal** within the BG_CONTENT 12–18% range
- saturation: **92% nominal**
- effective contrast: **about 89–92%**
- luminance: **about +3%**
- blur: **none**
- scene redesign: **prohibited**

The page must not use `account_privacy_morning_lake` as an independent visual world.

## 3. Layout retained

Retain the current Login V2 hierarchy:

1. 渔见
2. 拍照收藏每次渔获
3. 欢迎回来
4. 继续记录你的每一次渔获
5. 账号
6. 密码
7. 忘记密码
8. 登录
9. 创建账号

Do not add new product states or change authentication semantics.

## 4. Surface rules

- field surface: MistWhite / white translucent surface
- field radius: 18dp reference
- main button: shared teal primary action, 56dp height reference
- primary text: DeepLake
- secondary text: LakeGray / muted blue-gray
- background remains visible but must not compete with form controls

## 5. Shared-system references

- Background System / `BG_CONTENT`
- Mist Glass Surface
- Color + Typography
- Spacing + Radius

## 6. Freeze gate

V2.1 becomes FROZEN only after:

1. no-sun Morning Lake background is visible in runtime/design evidence;
2. old Account Privacy background is absent;
3. form hierarchy remains unchanged;
4. Login and Register appear as one auth family;
5. updated canonical screenshot is archived and hashed.
