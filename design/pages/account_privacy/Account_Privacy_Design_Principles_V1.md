# Account & Privacy · Design Principles V1

Status: **FROZEN**
Version: **V1.0 FINAL**
Scope: Account & Privacy design system
Date: 2026-10-01

## 1. Purpose

This document freezes the product-design principles for YuJian Account & Privacy.

It applies to:

- Login / Register
- My / Profile
- Edit Profile / Avatar / Nickname
- Account & Security / Change Password
- Data & Privacy
- AI Model Improvement consent
- Location privacy
- About YuJian
- Privacy Policy / User Agreement shell
- Deferred account/privacy entries

This document does not define Android implementation details or final legal wording.

## 2. Core design principles

### 2.1 Quiet & trustworthy

Account and privacy surfaces are utility surfaces.

They must feel:

- calm;
- stable;
- explicit;
- readable;
- predictable.

Do not add decorative effects merely to make settings feel more active.

Forbidden:

- game progression;
- rewards;
- badges;
- streaks;
- celebratory motion;
- urgency decoration;
- neon/HUD language;
- persuasive visual pressure around consent.

### 2.2 State first

The information hierarchy is:

1. current state;
2. what it means;
3. available action;
4. resulting feedback.

A page must not make the user infer whether a setting is enabled, saved, denied, deferred or unavailable.

### 2.3 Explicit user control

User-affecting changes require an intentional action.

Examples:

- profile changes are saved explicitly;
- AI model improvement requires explicit valid consent;
- withdrawal is explicit;
- location permission is requested only after an explicit point-of-use action;
- unsaved edit drafts are not discarded silently.

Do not use silent state changes, pre-checked consent or optimistic success that has not been confirmed.

### 2.4 No surprise

Opening an Account & Privacy page must not unexpectedly:

- request Android location permission;
- change consent;
- log out;
- discard an edit draft;
- expose a destructive flow;
- start background tracking;
- upload a new avatar;
- submit a password change.

Navigation and reading are not consent.

### 2.5 Risk transparency

Security/privacy consequences must be stated in plain language close to the action.

Button labels should describe the action rather than use vague words such as:

- `继续`
- `确认一下`
- `好的`

when the actual action is material.

Prefer semantic action labels such as:

- `允许用于模型改进`
- `关闭`
- `放弃修改`
- `退出当前账号`

Delete Account remains DEFERRED and no active destructive wording may imply that deletion is currently executable.

### 2.6 Minimize data and permission scope

Design copy must not authorize broader collection than the current product contract.

Current boundaries include:

- AI model improvement: optional and explicit;
- location: point-of-use, not background;
- legal copy: must reflect actual technical/data behavior;
- account screens: do not display internal IDs, tokens or diagnostic data.

### 2.7 Honest capability state

A feature state must be truthful.

Use:

- `FROZEN` for closed design authority;
- `DEFERRED` for intentionally postponed product capability;
- `LEGAL_REVIEW_REQUIRED` for final legal wording awaiting legal approval;
- Runtime state independently from Design state.

Do not use disabled-looking fake controls to suggest a capability exists when it does not.

Deferred flows remain entry-only / Coming Soon until a future version explicitly reopens them.

### 2.8 Shared system first

Account & Privacy consumes shared authorities before defining local variants.

Shared dependencies include:

- BG_CONTENT
- Top Navigation / BACK_TITLE
- Mist Glass / settings surfaces
- Color & Typography
- Spacing & Radius
- Action Button / Text Action / Icon Action
- Default Profile Avatar
- Legal Document Shell

Page-local rules may specialize semantics, but may not fork shared visual tokens without a new versioned authority.

### 2.9 Preserve semantic identity

The following states must remain distinct:

- real user avatar;
- default logged-in profile avatar;
- Guest account entry;
- logout;
- account deletion;
- consent off;
- consent load failure;
- location denied;
- location never requested;
- Deferred feature.

Visual similarity must not collapse different meanings.

### 2.10 Feedback is semantic, not theatrical

Success and error feedback must be understandable through text/state.

Account & Privacy adds no private:

- motion system;
- haptic system;
- sound system.

Shared/platform feedback remains allowed under the frozen Interaction Feedback authority.

## 3. Page families

### Authentication

- Login
- Register
- Forgot Password · DEFERRED

### Identity / profile

- My
- Edit Profile Home
- Avatar
- Nickname
- Save / error / adaptation states

### Account security

- Account & Security
- Change Password
- Logout

### Data & privacy

- Data & Privacy
- AI Model Improvement
- Location
- Export My Data · DEFERRED
- Delete Account · DEFERRED

### Legal / about

- About YuJian
- Privacy Policy
- User Agreement

## 4. Authority precedence

When two references conflict, use this order:

1. current page/behavior spec;
2. Account & Privacy final design-system contracts;
3. page Visual Adjustment Authority;
4. Shared Design System;
5. frozen/base high-fidelity binary for composition;
6. historical package reference.

Final legal wording is governed separately by approved legal copy and may replace draft text without reopening the frozen page shell.

## 5. Design completion rule

A design is complete when:

- the page purpose is unambiguous;
- state semantics are defined;
- action semantics are defined;
- navigation/back behavior is defined;
- loading/error/success behavior is defined where applicable;
- accessibility/localization behavior is defined;
- visual authority is known;
- external gates are explicitly separated.

A new screenshot is not required when existing high-fidelity composition plus written/shared authority fully defines the intended result.

## 6. Deferred governance

Current Deferred flows:

- Forgot Password
- Export My Data
- Delete Account

Rules:

- preserve historical reference boards;
- show only the current allowed entry/Coming Soon state;
- do not expose the historical full state machine in production;
- do not classify Deferred as incomplete design;
- activation requires a versioned product/design revision and corresponding backend/legal capability where applicable.

## 7. Legal boundary

Privacy Policy and User Agreement:

- page shell = FROZEN;
- content architecture = FROZEN;
- final legal wording = LEGAL_REVIEW_REQUIRED.

Design must not invent:

- legal entity;
- jurisdiction;
- retention promises;
- vendor/data-sharing facts;
- age threshold;
- dispute terms;
- final liability language.

Legal review may update final body copy inside the frozen Legal Document Shell without reopening visual/interaction design unless the approved legal requirements materially change the page structure.

## 8. Reopen rule

Do not reopen Account & Privacy design because:

- Android Runtime differs;
- CI fails;
- final legal text changes within the frozen shell;
- a Deferred capability remains unavailable;
- main advances.

Reopen only for a versioned product/design change, including:

- new IA;
- new active capability;
- materially different privacy/consent semantics;
- new shared visual-system requirement;
- new custom motion/haptic/sound requirement;
- legal requirements that materially alter interaction or page structure.

## 9. Final principle

> Account & Privacy should make the user's identity, security, privacy state and choices easy to understand without persuasion, surprise or decorative noise.
