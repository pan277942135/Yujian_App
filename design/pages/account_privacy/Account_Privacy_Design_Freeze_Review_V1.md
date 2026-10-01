# Account & Privacy · Design Freeze Review V1

Status: **FROZEN**
Date: 2026-10-01
Scope: Account & Privacy design authority only

## 1. Freeze decision

Account & Privacy design is **FROZEN**.

This statement covers:
- visual authority;
- behavior / flow;
- motion;
- haptic;
- sound;
- asset authority;
- design evidence / governance.

It does **not** claim:
- Android Runtime Parity;
- final Privacy Policy legal approval;
- final User Agreement legal approval;
- implementation of Deferred product flows.

Those remain independent runtime/legal/product gates.

## 2. Menu / IA

Canonical visible structure:

1. 登录与注册
2. 我的与资料
3. 账号与安全
4. 数据与隐私
5. 法律与关于

Duplicate top-level Login / Register / Edit Profile / User Agreement design modules are hidden compatibility aliases only.

## 3. Login & Register

### Login
Status: FROZEN

### Register
Status: FROZEN

### Forgot Password
Status: DEFERRED

Deferred means:
- historical design reference exists;
- current MVP intentionally does not implement the future recovery flow;
- it is not incomplete design work.

Therefore the parent 01 group is Design FROZEN.

## 4. My & Profile

Status: FROZEN

Covered:
- My
- Edit Profile Home
- Avatar
- Nickname
- Save Feedback
- Edge States
- Interaction / Adaptation

Nickname visual integrity has been recovered byte-for-byte:
- approved source SHA-256: `5d96c4654abe9899042f29f33987bc49eb21d4b1c427963bda1a0a3fef23d55e`
- frozen WebP SHA-256: `5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`

No remaining Edit Profile design blocker exists.

## 5. Account & Security

Status: FROZEN

Covered:
- Account & Security Home
- Change Password
- logout distinction
- validation / failure / success rules

Historical embedded title `账号与登录` is superseded by current design authority `账号与安全`.

## 6. Data & Privacy

Status: FROZEN

Active:
- Data & Privacy Home
- AI Model Improvement consent
- Location privacy behavior

Deferred:
- Export My Data
- Delete Account

Deferred flows are deliberate product-scope states and do not block design freeze.

## 7. Legal & About

Page design status: **FROZEN**

### About YuJian
- layout / behavior / visual composition: FROZEN

### Privacy Policy
- Legal Document Shell: FROZEN
- content architecture: FROZEN
- final legal wording: LEGAL_REVIEW_REQUIRED

### User Agreement
- Legal Document Shell: FROZEN
- content architecture: FROZEN
- final legal wording: LEGAL_REVIEW_REQUIRED

Legal review is an external approval gate.

It does not mean the page design remains open.

No design changes should be made merely to resolve legal wording.

## 8. Motion

Status: FROZEN

Decision:
- no Account & Privacy private motion system;
- Shared component pressed/loading behavior only;
- platform/shared navigation and sheet transitions only;
- no ambient/decorative motion.

Authority:
`spec/Account_Privacy_Interaction_Feedback_Spec_V1.md`

## 9. Haptic

Status: FROZEN

Decision:
- no feature-specific Account & Privacy haptic;
- no save/error/consent/logout vibration.

## 10. Sound

Status: FROZEN

Decision:
- no Account & Privacy sound layer;
- no product sound assets required.

## 11. Assets

Status: FROZEN

Authority index:
`authority_manifest_v2.json`

Asset classes are explicitly separated:
- current frozen;
- composite frozen;
- shared component composition;
- historical/superseded;
- deferred references.

Nickname asset integrity is restored.

No unresolved design-asset identity issue remains.

## 12. Design evidence

Status: FROZEN

Evidence set includes:
- historical Account & Privacy QA package;
- current Authority Manifest V2;
- current Registry;
- frozen page/behavior specs;
- frozen feedback policy;
- GitHub Design Governance / Registry / Navigation gates.

Runtime screenshot evidence remains a Runtime Parity concern and is not required to keep Design FROZEN.

## 13. Remaining non-design gates

### Runtime
Status: PARTIAL

Android implementation must be compared against the frozen design authority.

### Privacy Policy final legal copy
Status: LEGAL_REVIEW_REQUIRED

### User Agreement final legal copy
Status: LEGAL_REVIEW_REQUIRED

### Deferred product flows
- Forgot Password
- Export My Data
- Delete Account

These require future product/backend decisions before activation.

## 14. Reopen rule

Account & Privacy design must not be reopened merely because:
- Android Runtime differs;
- final legal wording changes within the frozen Legal Document Shell;
- a Deferred feature is still not implemented;
- main advances.

Reopen design only for a real versioned product/design change such as:
- new IA;
- new active capability;
- materially different consent/privacy semantics;
- new visual system requirement;
- new custom motion/haptic/sound decision.

## 15. Final state

`ACCOUNT & PRIVACY DESIGN = FROZEN`

`ACCOUNT & PRIVACY RUNTIME = PARTIAL`

`PRIVACY POLICY LEGAL COPY = LEGAL_REVIEW_REQUIRED`

`USER AGREEMENT LEGAL COPY = LEGAL_REVIEW_REQUIRED`

`FORGOT PASSWORD / EXPORT / DELETE = DEFERRED`
