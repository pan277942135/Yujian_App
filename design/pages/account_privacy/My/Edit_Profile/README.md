# 编辑资料 · Design Package V1

Status: **PARTIAL — structure established**

本目录是「编辑资料」的设计管理根目录。当前仅建立 Design Manager 二级目录结构，不代表子页面视觉或行为已经冻结。

## 二级目录

1. [01 · 编辑资料首页](./01_Profile_Home/README.md)
2. [02 · 头像修改](./02_Avatar_Edit/README.md)
3. [03 · 昵称编辑](./03_Nickname_Edit/README.md)
4. [04 · 保存与反馈](./04_Save_Feedback/README.md)
5. [05 · 异常与边界状态](./05_Edge_States/README.md)
6. [06 · 交互与适配规范](./06_Interaction_Adaptation/README.md)

## Scope

编辑资料只负责个人资料编辑。账号安全、修改密码、数据与隐私等继续归属各自模块。

当前后端已支持的资料边界优先保持：

- 头像
- 账号（只读）
- 昵称

未单独确认前，不新增手机号、邮箱、性别、生日、地区、签名等字段。
