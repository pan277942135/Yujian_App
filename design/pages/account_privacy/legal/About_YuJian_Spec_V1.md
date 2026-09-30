# 关于渔见 · Page Spec V1

Status: **DESIGN FROZEN**
Visual authority mode: **SHARED COMPONENT COMPOSITION — NO STANDALONE HI-FI REQUIRED**
Page ID: `account_privacy_v1.05a`

## 1. Purpose

Provide a quiet product identity and legal-entry page.

It is not:
- a marketing landing page;
- a feature tutorial;
- a changelog;
- an achievement/profile page.

## 2. Navigation

Parent:
`我的`

TopNav:
- Shared `BACK_TITLE`
- title `关于渔见`

## 3. Page composition

Use current:
- BG_CONTENT;
- PageScaffold / BACK_TITLE;
- restrained white/mist surfaces;
- shared settings rows.

### Brand card

Centered content:
1. `渔见`
2. product tagline
3. app version

Frozen tagline for V1:
`拍照收藏每次渔获`

Version:
- runtime-derived;
- format may be `Version X.Y.Z`;
- never hardcode a fake version in design/runtime.

No logo artwork is required for V1 unless a separate Brand Mark Authority is later frozen.

### Legal card

Rows:
1. `用户协议`
2. `隐私政策`

Each opens the shared Legal Document Shell with the corresponding content authority.

## 4. Visual hierarchy

Brand card is primary.

Legal rows are functional secondary navigation.

Do not add:
- social links;
- rating prompts;
- promotional banners;
- model/version diagnostics;
- internal build IDs;
- developer/debug information.

## 5. Accessibility

- legal rows >= 44dp target;
- app version remains readable but secondary;
- dynamic font scaling;
- safe-area aware;
- back semantics consistent with shared TopNav.

## 6. Visual freeze rationale

A standalone PNG is not required because this page is fully defined by existing Shared Design System components plus this exact composition contract.

Visual status is therefore **FROZEN by component composition**, not MISSING.

Android Runtime may differ in tokens/layout until Runtime Parity; runtime screenshot is not Design Authority.
