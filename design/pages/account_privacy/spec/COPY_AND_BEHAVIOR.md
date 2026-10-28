# Account & Privacy · Copy / Behavior Index V2

Status: **PARTIAL package**
Active MVP main path: **FROZEN**

This file is an index. Page-level specs own the exact active-path behavior; do not use this file to override them.

## 1. Active path authorities

### 我的
Authority:
`../My/My_Page_Spec_V2.md`

Frozen labels:
- 我的
- 编辑个人资料
- 账号与安全
- 关于渔见
- 鱼种
- 鱼获
- 记录天数

### 账号与安全
Authority:
`../My/Account_Login/Account_Security_Home_Spec_V1.md`

Frozen labels:
- 当前账号
- 修改密码
- 数据与隐私
- 退出当前账号

The historical label `账号与登录` is superseded.

### 修改密码
Authority:
`../My/Account_Login/Change_Password_Spec_V1.md`

Frozen validation:
- current password required;
- new password 6–72 characters;
- confirmation matches;
- new password differs from current.

Frozen success copy:
`密码已修改`

### 数据与隐私
Authority:
`../My/Account_Login/Data_Privacy/Data_Privacy_Home_Spec_V1.md`

Active:
- AI 模型改进
- 位置权限

Deferred:
- 导出我的数据
- 注销账号

Legal:
- 查看《隐私政策》

### AI 模型改进
Authority:
`../My/Account_Login/Data_Privacy/AI_Model_Improvement/AI_Model_Improvement_Consent_Spec_V1.md`

Enable copy:
`仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。`

Enable actions:
- 暂不开启
- 允许用于模型改进

Disable copy must state that closing does not affect:
- 拍照识鱼
- 保存鱼获
- 修改识别结果

Disable actions:
- 关闭
- 保持开启

### Location
Authority:
`../My/Account_Login/Data_Privacy/Location_Permission/Location_Privacy_Spec_V1.md`

Frozen copy:
`只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。`

Must state:
`拒绝不会影响拍照识鱼和保存鱼获。`

Status labels:
- 使用期间
- 已拒绝
- 未授权

## 2. Deferred copy — not active behavior

### Forgot Password
Historical copy remains reference only until a verified recovery channel exists.

### Export My Data
Do not expose the historical Processing / Ready / Failed / Expired flow in the current MVP.

### Delete Account
Do not expose the historical destructive flow in the current MVP.

## 3. Security / privacy rules

- Do not log or persist password field values.
- Do not silently change AI consent.
- Do not request location permission on app/page entry.
- Do not conflate logout with account deletion.
- Do not broaden AI-consent data scope through UI copy without a versioned consent revision.
- Runtime implementation does not override these design contracts.
