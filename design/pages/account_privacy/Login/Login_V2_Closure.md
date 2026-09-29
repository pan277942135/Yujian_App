# YuJian Login V2 — Independent Work Closure Contract

Status: **ACTIVE IMPLEMENTATION CONTRACT**

## Canonical route

`auth/login`

This is the new canonical login route.

The legacy route `login` is compatibility-only during migration and must not remain the visual implementation authority after this Work closes.

## Frozen visual authority

1. `design/pages/account_privacy/Login/00_Login.png`
2. `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` + `BG_CONTENT`
3. `design/pages/account_privacy/spec/VISUAL_TOKENS.md`
4. `design/pages/account_privacy/spec/COPY_AND_BEHAVIOR.md`
5. `design/pages/account_privacy/spec/FLOW_SPEC.md`

The Frozen PNG controls final appearance. Markdown controls tokens and behavior.

## Scope

Only:
- new Login V2 native Compose screen;
- new `auth/login` route;
- Empty Home guest-login navigation to `auth/login`;
- reuse of the current AuthRepository / session flow;
- Login-specific visual/runtime/evidence tests;
- compatibility handling for the legacy `login` route.

Out of scope:
- Register visual redesign;
- Forgot Password implementation;
- My / Account & Login;
- Data & Privacy;
- Empty Home visual changes;
- Recognition;
- global navigation architecture.

## Product behavior

- Login business semantics remain unchanged.
- A successful login must continue to persist the session and return the user to the normal product flow.
- Error/loading states must remain functional and visually consistent with Login V2.
- Back returns to the previous route.
- Register CTA may continue to the existing register route in this Work.
- Forgot Password must not become an active production flow while the verified recovery-channel backend gate is unresolved.

## Migration rule

During implementation:

`home → auth/login → LoginV2Screen`

After Login V2 passes its runtime/visual gates:

- Empty Home guest login entry uses `auth/login`.
- legacy `login` must redirect/alias to `auth/login` rather than render the old AuthScreens Login UI.
- the old Login composable may remain temporarily for source compatibility only if no production route renders it.

## Visual system

Reference canvas: 1080 × 1920.
Baseline: 360 × 640 dp.

Tokens:
- Primary / Lake Teal: #0F7A78
- Active Accent: #168B88
- Deep Lake: #12364A
- Secondary: #74879A
- Border: #D5DFE6
- Surface: white 90–94%
- Horizontal margin: 24dp
- Input height: 54–56dp
- Primary CTA height: 56dp
- Radius: 18–20dp
- Route motion: 220–280ms

The Morning Lake background is the only frozen reference image allowed to ship as a direct image asset. Inputs, fields, buttons, cards and text must be native UI.

## High-fidelity gate

Do not accept Build PASS as visual completion.

Required:
- Frozen reference vs runtime screenshot;
- structural ROI parity for brand/title/form/CTA/footer;
- correct background crop and haze;
- correct input/card/button geometry;
- correct typography hierarchy;
- loading/error states;
- API28 runtime PASS.

## Efficiency / autonomy

Work may autonomously:
- choose Compose structure;
- improve state hoisting;
- derive/generated-resource packaging for the frozen lake background;
- improve test/evidence reliability;
- refactor Login-local code.

Work must not:
- redesign the Frozen page;
- widen scope to Register/Forgot Password/Account;
- weaken visual gates;
- modify unrelated modules.

## Stop rule

Continue only while a concrete Login V2 fidelity, behavior, stability, performance or evidence defect remains.

When:
- visual parity PASS;
- login behavior PASS;
- API28 PASS;
- evidence PASS;

STOP modifying implementation, merge, run main validation, and produce the QA APK.


## Background authority revision

Current background authority is:

`design/pages/account_privacy/Login/Login_V2_BG_CONTENT_Revision_V2_1.md`

The legacy Account Privacy morning-lake asset is no longer a Login design authority.
