# 账号与隐私子流程 · V3.1 Phase 1

**状态：AUDIT_DRAFT** · 游客 / 登录态与 Home Empty / Normal 状态独立。

```mermaid
flowchart TD
  G["Guest Profile"] --> L["Login"]
  L --> R["Register"]
  R -->|"成功"| H["Home"]
  L -->|"成功"| H
  H -->|"signed-in profile"| A["My"]
  A --> E["Edit Profile"]
  A --> S["Account Login / Security"]
  S --> P["Data Privacy"]
  A --> I["About"]
  I --> U["User Agreement / Privacy Policy"]
  P --> U
  P -.->|"Coming Soon"| X["Export / Account delete"]
  L -.->|"Coming Soon"| F["Forgot password"]
  classDef confirmed fill:#e9f4ed,stroke:#4f7e5d,color:#183b24;
  classDef deferred fill:#f0f0f0,stroke:#888,color:#333;
  class G,L,R,H,A,E,S,P,I,U confirmed;
  class X,F deferred;
```

登录 / 注册、资料、账号安全、隐私设置和法律页入口在当前 Android NavHost 有 route；App 的 auth/profile/privacy repository 对应后端注册、登录、资料、密码、隐私 API。Forgot Password、数据导出、账号删除等现有 UI 明确 Coming Soon / Deferred，不能列为可用闭环。
