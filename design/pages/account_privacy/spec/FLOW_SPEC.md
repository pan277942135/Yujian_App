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
