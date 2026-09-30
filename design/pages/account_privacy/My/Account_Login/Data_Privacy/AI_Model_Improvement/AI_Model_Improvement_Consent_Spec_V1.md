# AI 模型改进授权 · Consent Spec V1

Status: **FROZEN — Consent Semantics / Copy / Interaction**
Visual status: **FROZEN — COMPOSITE AUTHORITY**

Visual authority set:
- `design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/01_Enable_Consent.png`
- `design/pages/account_privacy/My/Account_Login/Data_Privacy/AI_Model_Improvement/02_Disable_Confirmation.png`
- adjustments: `design/pages/account_privacy/Active_Path_Visual_Adjustment_Authority_V1.md`
Page ID: `account_privacy_v1.04b`

## 1. Principle

AI model improvement is optional.

Default:

> **OFF unless explicit valid consent exists.**

Recognition, FishRecord saving and correction must work regardless of consent.

No dark pattern may pressure consent.

## 2. Allowed training contribution

Current consent copy is scoped to:

- the fish body/crop selected from the photo;
- the species confirmed by the user;
- necessary metadata required to associate that correction with model-improvement processing.

Do not broaden this copy to unrestricted photo/library/account-data use without a versioned consent revision.

## 3. Enable flow

Trigger:

- user taps AI 模型改进 row/switch while current state is OFF.

Open confirmation Bottom Sheet.

Title:

`帮助改善鱼种识别`

Body:

`仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。`

Actions:

Primary:
`允许用于模型改进`

Secondary:
`暂不开启`

No pre-checked consent.

## 4. Disable flow

Trigger:

- user taps row/switch while current state is ON.

Open confirmation Bottom Sheet.

Title:

`关闭模型改进？`

Body must state that new eligible corrections stop being used and explicitly state no effect on:

- 拍照识鱼
- 保存鱼获
- 修改识别结果

Actions:

Primary:
`关闭`

Secondary:
`保持开启`

## 5. State transaction

Before confirmation:

- switch displays persisted state, not optimistic target state.

After primary confirmation:

- submit one state-change request;
- block duplicate actions while saving.

Success:

- update row/switch from confirmed response;
- withdrawal callback may clear any local prompt state.

Failure:

- preserve previous confirmed consent state;
- show `隐私设置保存失败，请重试` or server-safe equivalent;
- never visually show a changed state that the server did not confirm.

## 6. Consent evidence

Backend/product must preserve versioned consent evidence sufficient to determine:

- enabled/disabled state;
- consent version/source;
- timestamp where applicable;
- withdrawal.

Design does not expose internal evidence IDs.

## 7. Motion / haptic / sound

No custom success celebration.

No custom sound.

No coercive animation.

Use standard sheet transition only.

## 8. Accessibility

- switch state has a semantic label;
- sheet actions are explicit text;
- enabled/disabled meaning is not color-only;
- sheet respects font scaling and safe areas.

## 9. Visual authority

Existing `01_Enable_Consent.png` and `02_Disable_Confirmation.png` are now the byte-preserved base Hi-Fi set.

Visual is **FROZEN via composite authority**. Current consent semantics/copy and Shared Sheet/Action tokens are governed by this spec plus `Active_Path_Visual_Adjustment_Authority_V1.md`. No replacement images are required.
