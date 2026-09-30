# 02 · 头像修改

Status: **FROZEN**

## Frozen Authority

### Visual Authority
`design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp`

SHA-256:
`7261d5b21fa750d3eb25f89e52819ebdb095ecb615f35550de0bceae3e0e3b4f`

Canvas:
`1448 × 1086`

### Development / Behavior Authority
`design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/Avatar_Change_V1_Development_Contract.md`

### Frozen Manifest
`design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/frozen/manifest.json`

## 本页冻结范围

- 当前头像入口
- 来源选择 Bottom Sheet
- 拍照
- Android Photo Picker
- 1:1 裁切 / 缩放
- 使用此头像
- 返回编辑资料后的 Pending Preview
- 保存时上传
- 上传中
- 上传成功
- 上传失败
- 保留 Preview + 重试

## 核心产品规则

> 选择头像、裁切头像仅形成 Local Pending Preview，不立即上传。
> 真正上传发生在「编辑资料首页」点击「保存修改」时。

因此头像和昵称属于同一次显式资料编辑事务。

## 来源选择

Bottom Sheet 固定为：

1. 拍照
2. 从相册选择
3. 取消

V1 不提供：
- 删除头像
- 历史头像
- 默认头像库
- AI 头像
- 滤镜 / 美颜 / AI 修图

## 裁切规则

- 输出为 1:1 Square Image；
- 圆形仅为 UI Mask；
- 支持单指拖动；
- 支持双指缩放；
- 目标缩放范围 1.0x–3.0x；
- 图片必须始终覆盖裁切区域；
- 建议最终处理尺寸不超过 1024 × 1024；
- 正确处理 EXIF Orientation。

## 保存规则

`使用此头像`：
- 生成临时处理图；
- 返回 Edit Profile；
- 新头像即时显示为 Pending Preview；
- Save Changes 进入 Enabled；
- **不得上传服务器**。

`保存修改`：
- 才执行 avatar upload；
- 成功后以服务器返回 `avatar_url` 更新 Session/Profile；
- My / Home 同步新头像；
- 清除 Pending Preview。

失败：
- 不更新 Session；
- 保留 Pending Preview；
- 允许直接重试；
- 不要求重新选择 / 重新裁切。

## 公共组件依赖

- Background System V1 / BG_CONTENT
- Top Navigation V1 / BACK_TITLE
- Action Button V1
- Icon Action V1
- Color & Typography V1
- Spacing & Radius V1

禁止为头像流程创建页面私有的按钮、导航或字体变体。

## Runtime 说明

当前 Android Runtime 已具备 Photo Picker、Camera、Preview、Upload 与 Session 更新基础能力，但仍需要按 Frozen Contract 补齐真正的 1:1 Crop / Scale 与 Pending Preview 事务语义。

本页设计状态与 Runtime Closure 分离：

**Design = FROZEN**  
**Runtime = 待后续实现 / Evidence Gate**
