# Work Task — Login V2

Task ID:

`feat(auth): implement frozen login v2 on new route`

Repository:
`pan277942135/Yujian_App`

Existing branch:
`feature/auth-login-v2`

Existing PR:
`#51`

Start from the current remote HEAD of this branch. Do not recreate the branch or open another PR.

## Goal

Replace the legacy visual Login experience with the frozen Login V2 while preserving existing authentication semantics.

## Canonical route

`auth/login`

This is a new production route.

The old route:

`login`

must become compatibility-only after Login V2 is proven. It must redirect/alias to `auth/login` and must not continue rendering the legacy LoginScreen.

## Visual authority

Use these repository sources directly:

1. `design/pages/account_privacy/Login/00_Login.png`
2. `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` + `BG_CONTENT`
3. `design/pages/account_privacy/spec/VISUAL_TOKENS.md`
4. `design/pages/account_privacy/spec/COPY_AND_BEHAVIOR.md`
5. `design/pages/account_privacy/spec/FLOW_SPEC.md`
6. `design/pages/account_privacy/Login/Login_V2_Closure.md`

The Frozen PNG is final visual authority.

Do not redesign the screen.

Do not ship the full reference PNG as a screenshot UI. Only the Morning Lake background may be packaged as an image asset. Form fields, cards, text, buttons and icons must be native Compose UI.

## New implementation path

Create a dedicated Login V2 implementation outside the legacy `AuthScreens.kt` visual implementation.

Recommended package:

`app/src/main/java/com/yujian/ai/ui/auth/`

Recommended screen:

`LoginV2Screen.kt`

Route ownership remains in `YujianApp.kt`.

Do not refactor unrelated navigation.

## Required navigation

Production flow:

`Empty Home → auth/login → LoginV2Screen`

On successful login:
- keep the existing AuthRepository request;
- keep existing UserSessionManager persistence;
- keep existing guest-archive adoption behavior;
- return to the existing product flow exactly as the current login behavior does.

Back:
- pop to the previous screen.

Register CTA:
- may continue to the existing `register` route for this Work.
- do not redesign Register here.

Forgot Password:
- must remain unavailable in production while the verified recovery-channel backend gate is unresolved.
- visual reference may contain the entry, but do not activate an unsupported recovery flow.

Legacy `login`:
- after Login V2 passes runtime/visual validation, redirect/alias it to `auth/login`.
- no production path should still render the old Login visual.

## Scope

Allowed:
- Login V2 Compose implementation;
- Login-specific resource packaging;
- `auth/login` route;
- Empty Home guest login callback route;
- legacy `login` compatibility redirect;
- Login loading/error/password visibility states;
- Login-specific tests and evidence.

Forbidden:
- Register redesign;
- Forgot Password implementation;
- My / Account & Login;
- Data & Privacy;
- Empty Home visual changes;
- Recognition;
- model pipeline;
- global CI/harness redesign.

## Frozen tokens

Reference canvas: 1080 × 1920
Baseline grid: 360 × 640 dp

- Lake Teal: #0F7A78
- Active Accent: #168B88
- Deep Lake: #12364A
- Secondary: #74879A
- Border: #D5DFE6
- Surface: white at 90–94%
- horizontal margin: 24dp
- card radius: 18–20dp
- input height: 54–56dp
- primary button height: 56dp
- route/sheet transition: 220–280ms

## High-fidelity implementation rules

The runtime must match the Frozen reference in:
- background crop;
- visual haze;
- brand placement;
- title/subtitle hierarchy;
- form vertical rhythm;
- text-field geometry;
- button geometry;
- surface opacity;
- footer / secondary action placement;
- safe-inset behavior.

Do not accept a generic Material login card merely because the colors match.

## Functional states

Must verify:
- idle;
- username entered;
- password entered;
- password hidden/shown;
- loading;
- invalid/disabled primary CTA;
- backend error;
- successful login;
- back;
- register navigation.

Do not expose technical backend exception details directly.

## Runtime evidence

Add a targeted Login V2 API28 runtime gate or equivalent deterministic instrumentation evidence.

Required evidence:
- `login_v2_idle.png`
- `login_v2_filled.png`
- `login_v2_password_visible.png`
- `login_v2_loading.png`
- `login_v2_error.png`
- `login_v2_frozen_side_by_side.png`
- visual parity report
- runtime result JSON

Functional login may use a deterministic test seam; do not hit production credentials in CI.

## Acceptance

Required:
- Build PASS
- Unit PASS
- Login V2 route PASS
- Empty Home → auth/login PASS
- visual parity PASS
- API28 Login V2 runtime PASS
- existing auth business behavior preserved
- no production caller renders legacy Login UI
- main validation PASS

## Autonomy

You may autonomously choose:
- Compose component decomposition;
- background packaging/build generation;
- state hoisting;
- local test seam;
- evidence capture implementation;
- Login-local refactors.

You may not:
- alter Frozen visual authority;
- broaden scope;
- remove assertions to get green;
- enable unsupported Forgot Password;
- redesign Register.

## Stop rule

If a concrete Login fidelity/behavior/runtime/evidence defect remains, fix only the owning layer.

When all required gates pass:
- STOP modifying the implementation;
- do not add unrelated cleanup;
- merge #51;
- run main validation;
- output the final QA APK and evidence.


## Background authority revision

Current background authority is:

`design/pages/account_privacy/Login/Login_V2_BG_CONTENT_Revision_V2_1.md`

The legacy Account Privacy morning-lake asset is no longer a Login design authority.
