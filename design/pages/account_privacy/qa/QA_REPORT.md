# QA / Consistency Review

Status: PASS for visual-package handoff, with explicit backend/legal gates below.

## Checked
- All reference PNGs normalized to 1080 × 1920.
- Same Morning Lake background family across account/privacy secondary pages.
- Same top-bar position, primary typography, surface radius and action hierarchy.
- Primary actions use Lake Teal; destructive final actions use low-saturation coral red.
- AI consent copy is reduced to fish-crop + confirmed-species purpose only.
- Location copy is scenario-based, not startup-permission based.
- Export covers Processing / Ready / Failed / Expired.
- Delete Account covers Explanation / Re-auth / Final Confirmation.

## Known gates
1. Forgot Password is UI-complete but backend-blocked until a verified recovery channel exists.
2. Privacy Policy visual is frozen; production legal copy must be legally reviewed and must match actual SDK/cloud/data flows.
3. Data export and delete-account screens must not ship as decorative controls; real backend jobs are required.
4. AI model improvement consent must persist versioned consent evidence and honor withdrawal.
5. Android implementation should reuse current YuJian tokens/components where already defined rather than duplicating them.

## Visual implementation tolerance
- Layout / spacing target: within ±4 dp.
- Color / opacity: use shared token values rather than screenshot sampling.
- Text copy: exact unless product/legal copy is explicitly revised.
