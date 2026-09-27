# YuJian Account & Privacy V1 — Frozen UI Package

Purpose: direct implementation reference for Work / Android.

## Scope

Login
├── Forgot Password
│   ├── Account
│   ├── Verification
│   ├── New Password
│   └── Success
My
└── Account & Login
    ├── Change Password
    └── Data & Privacy
        ├── AI Model Improvement
        │   ├── Enable Consent
        │   └── Disable Confirmation
        ├── Location Permission
        ├── Export My Data
        │   ├── Processing
        │   ├── Ready
        │   ├── Failed
        │   └── Expired
        ├── Delete Account
        │   ├── Explanation
        │   ├── Re-auth
        │   └── Final Confirmation
        └── Privacy Policy

## Source of truth rules

- PNGs are visual references, not bitmap UI to ship directly.
- Implement inputs, cards, buttons, sheets, toggles and text natively.
- Only the Morning Lake background may be treated as an image asset.
- All reference PNGs are normalized to 1080 × 1920.
- Baseline implementation grid: 360 × 640 dp (3× reference scale).
- Use existing YuJian Core UI tokens when they conflict with this package; do not create a second design system.
- Legal body copy in Privacy Policy requires final legal review before production release.

## Critical backend gate

Forgot Password verification requires a recovery identity (bound phone/email or another verified recovery channel).
The current frozen Register UI does not collect one. Do not expose the Forgot Password flow in production until the backend and account model provide a verified recovery channel.

## AI model improvement consent

- Default OFF.
- Enable only after explicit user confirmation.
- User-correction prompt is allowed only after a corrected fish species is saved successfully.
- Maximum 3 proactive prompts lifetime.
- Maximum 1 proactive prompt per rolling 7 days.
- Once user has enabled consent, stop proactive prompts.
- If the user later turns consent OFF, set prompt suppression and never proactively prompt again.
- Rejecting consent must not affect recognition, fish-record save, or correction.

## Location

- Do not request on app launch.
- Request only after an explicit “use current location” action.
- Prefer coarse location / water-body or district granularity when precise coordinates are not required.
- Denial must not block fish recognition or fish-record save.

## Export

Recommended state machine:
READY_TO_REQUEST → PROCESSING → READY | FAILED | EXPIRED
Only one active export job per user in V1.

## Delete account

Recommended state machine:
ACTIVE → DELETION_PENDING → DELETED
All active sessions should be invalidated when deletion is accepted.
