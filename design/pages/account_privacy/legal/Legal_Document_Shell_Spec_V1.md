# Legal Document Shell · Spec V1

Status: **FROZEN**
Authority type: **SHARED LEGAL PAGE SHELL**
Consumers:
- 隐私政策
- 用户协议

## 1. Purpose

Provide one reusable long-form legal document shell for Account & Privacy.

Privacy Policy and User Agreement are different content authorities, but they use the same page shell. Do not create two visually independent page designs.

## 2. Visual authority

Base Hi-Fi:

`design/pages/account_privacy/My/Account_Login/Data_Privacy/04_Privacy_Policy.png`

Frozen binary identity:

- 1080 × 1920
- SHA-256: `cd3a7e367492b89e9a126f7129d024072ce88232483e79ef5ca614fa487bf6af`

Authority mode:

**COMPOSITE BASE HI-FI + CURRENT SHARED DESIGN SYSTEM**

The existing PNG is preserved byte-identical.

Current Shared Design System overrides deprecated screenshot-level details for:
- BG_CONTENT;
- BACK_TITLE;
- typography;
- spacing/radius;
- safe areas;
- accessibility;
- dynamic long-text layout.

No separate User Agreement screenshot is required.

## 3. Page structure

TopNav:
- Shared `BACK_TITLE`
- dynamic title:
  - `隐私政策`
  - `用户协议`

Body:
- one outer vertical scroll;
- one readable legal-content surface;
- no nested vertical scroll;
- no decorative hero imagery;
- no CTA competing with legal text.

Recommended content hierarchy:
1. document title context;
2. optional version / effective date metadata;
3. section heading;
4. paragraphs / bullets;
5. contact / revision metadata where applicable.

## 4. Typography

Legal copy prioritizes readability over screenshot parity.

- body: shared body style;
- line height: comfortable long-form reading;
- headings visually distinct but restrained;
- no justified text;
- no tiny gray legal copy;
- text must survive system font scaling.

## 5. Background / surface

Use:
- `Morning_Lake_Master_V1 / BG_CONTENT`;
- restrained white/mist reading surface.

Do not:
- use hero sunrise emphasis;
- use glass transparency so strong that body contrast drops;
- use black/gold editorial Fish Guide styling;
- use game-like decoration.

## 6. Interaction

- scroll only;
- links, when introduced, must be explicit and accessible;
- system Back == TopNav Back;
- no modal acceptance interaction inside the document shell unless a future onboarding/auth flow explicitly owns consent.

Viewing the document is not itself consent.

## 7. Accessibility

- 44dp minimum interactive targets for links/actions;
- semantic heading order;
- text selectable where platform permits;
- screen reader reads title before body;
- dynamic type/font scale supported;
- no meaning conveyed only by color;
- safe-area aware.

## 8. Legal-copy boundary

This shell may be **Visual FROZEN** independently from legal content.

Legal text must carry its own status:
- DRAFT / REVIEW_REQUIRED
- APPROVED / FROZEN

A frozen shell never implies approved legal copy.
