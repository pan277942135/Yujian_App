# Navigation / Flow Spec

Login
→ Forgot Password / Account
→ Verification
→ New Password
→ Success
→ Login

My
→ Account & Login
    → Change Password
    → Data & Privacy
        → AI Model Improvement Enable Consent
        → AI Model Improvement Disable Confirmation
        → Location Permission
        → Export My Data
            → Processing
            → Ready / Failed / Expired
        → Delete Account
            → Explanation
            → Re-auth
            → Final Confirmation
        → Privacy Policy

## Exit / back behavior
- Back from any non-terminal page returns to its parent.
- Success pages should not re-enter completed forms via back-stack.
- Final destructive confirmation stays modal until cancel/confirm.

## Design Manager Consolidation · 2026-09-30

- Forgot Password, Export My Data and Delete Account remain **MVP DEFERRED / ENTRY ONLY / COMING SOON**. Their historical state images are gallery references, not an instruction to expose full production flows.
- Forgot Password remains gated on a verified recovery channel.
- AI improvement consent is OFF by default, requires explicit user consent, and may be withdrawn. Refusal or withdrawal does not block recognition, FishRecord saving or correction.
- Location permission is requested only after an explicit “使用当前位置” action; denial does not block recognition or FishRecord saving.
- The legacy account package background is historical. Current account pages reference shared `Morning_Lake_Master_V1 / BG_CONTENT` where applicable.
- These organization notes do not freeze unresolved visual/behavior details or assert runtime completion.
