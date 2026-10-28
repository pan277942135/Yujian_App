# Nickname Edit V1 · Frozen Development Contract

Status: **FROZEN**
Scope: `编辑资料 → 03 · 昵称编辑`
Visual Authority: `design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp`
Machine Contract: `design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/contracts/Nickname_Edit_V1_Contract.json`

## 1. Product contract

Nickname Edit V1 is an inline edit flow owned by Edit Profile.

```text
Edit Profile
→ focus nickname field
→ edit local draft
→ validate normalized nickname
→ Save Changes
→ PATCH /api/v1/me/profile
→ server success
→ update UserSession / observable profile
→ My / Home reflect new nickname
```

The nickname field does not create a separate profile page or a second save transaction.

## 2. Server authority

Backend authority:
- endpoint: `PATCH /api/v1/me/profile`;
- request field: `nickname`;
- server normalizes with `strip()`;
- normalized nickname length: **1–20 characters**;
- empty / whitespace-only nickname is invalid;
- over 20 characters is invalid;
- backend currently imposes **no additional character-class restriction**.

Frozen backend copy:
- empty: `昵称不能为空`
- over limit: `昵称不能超过 20 个字符`

The server response remains the final profile authority.

## 3. Character counting

Android validation MUST align with the backend.

Use the normalized value:

```text
normalized = nickname.trim()
```

Length validation is based on Unicode code points, not Kotlin UTF-16 code-unit count.

Reference implementation semantics:

```text
codePointCount(normalized) in 1..20
```

This avoids inconsistent behavior for emoji and supplementary Unicode characters.

The UI counter may display the current draft count, but validation and Save eligibility MUST use the normalized value.

## 4. Input states

### Default
- show current server nickname;
- field not focused;
- show counter;
- Save Changes disabled when there is no effective change.

### Focus
- field receives focus;
- active border / cursor uses the shared input focus treatment;
- keyboard opens;
- clear action is available when text is non-empty.

### Filled / Valid Changed
- normalized nickname is valid;
- normalized nickname differs from current server nickname;
- Save Changes enabled.

### Error — Empty
Condition:
- normalized nickname is empty.

Copy:
`昵称不能为空`

Save Changes disabled.

### Error — Over limit
Condition:
- normalized nickname exceeds 20 Unicode code points.

Copy:
`昵称不能超过 20 个字符`

Save Changes disabled.

### Maximum valid
- exactly 20 normalized Unicode code points is valid;
- counter reaches `20 / 20`;
- Save remains enabled if the value differs from the server nickname.

### One-character boundary
- exactly 1 normalized Unicode code point is valid;
- do not require 2 or 3 minimum characters.

## 5. Character set

Do not invent a client-only blacklist.

The current server contract has no additional restriction on:
- Chinese;
- Latin letters;
- digits;
- punctuation;
- emoji / other Unicode characters,

provided the normalized value is non-empty and within the 20-code-point limit.

If the backend contract changes later, this design contract must be versioned rather than silently diverging.

## 6. Draft behavior

Nickname edits remain local until Save Changes succeeds.

Do not update:
- UserSession;
- My;
- Home;
- persistent local profile cache

before server success.

The displayed draft can differ from the current server nickname while editing.

## 7. Save eligibility

Save Changes is enabled only when all are true:

```text
not saving
AND normalized nickname valid
AND (
  normalized nickname != current server nickname
  OR pending avatar exists
)
```

For Nickname-only reasoning:
- unchanged normalized nickname → disabled;
- valid changed nickname → enabled;
- invalid nickname → disabled.

Leading/trailing whitespace that normalizes back to the current nickname does not count as a meaningful change.

## 8. Over-limit editing

Do not rely on silent truncation as the validation model.

Preferred V1 behavior:
- allow the user to see an over-limit state;
- show the actual counter;
- display the frozen error;
- keep Save disabled;
- let the user correct the value.

If an implementation adds a safety cap for pathological pasted input, it must be substantially above the 20-character business limit and must not replace the visible 20-character validation rule.

## 9. Error timing

Do not flash a destructive error for every transient keystroke.

Recommended behavior:
- while actively editing, counter updates live;
- empty / over-limit state may show immediately once clearly invalid;
- at minimum, error MUST be shown on IME Done, focus loss, or Save attempt;
- error clears as soon as the normalized draft becomes valid.

## 10. IME / keyboard

- single-line text input;
- IME action: `Done`;
- `Done` dismisses the keyboard;
- if valid + changed, `Done` may trigger the same Save Changes action;
- if invalid, keep the user on Edit Profile and expose the relevant error;
- field must remain visible above IME.

Keyboard / viewport adaptation remains PARTIAL in the Interaction / Adaptation section of `design/pages/account_privacy/My/Edit_Profile/README.md`.

## 11. Clear action

When the draft is non-empty:
- show shared Context / Clear icon action;
- content description: `清空昵称`;
- tap clears the draft;
- counter updates to `0 / 20`;
- Save becomes disabled because the normalized nickname is invalid.

Do not treat the clear icon as a separate save or reset-to-server action.

## 12. Save transaction

When the user taps Save Changes:

```text
normalize nickname
→ local validation
→ PATCH nickname if nickname changed
→ avatar upload if a pending avatar also exists
→ wait for required server operations
→ update profile/session from server response
→ clear saved draft state
```

Nickname and avatar remain one user-visible Edit Profile save transaction.

Do not show nickname success before the required profile request succeeds.

## 13. Success

After successful profile response:
- replace the local draft authority with the server-returned nickname;
- update UserSession / profile observable state;
- My reflects the new nickname immediately;
- Home reflects the new nickname where displayed;
- changed=false if no other pending profile edits remain;
- Save Changes returns to Disabled.

Feedback remains PARTIAL under the Save Feedback section of `design/pages/account_privacy/My/Edit_Profile/README.md`:
- lightweight `已保存`;
- no blocking success dialog.

## 14. Failure

If nickname update fails:
- retain the user's draft text;
- do not update UserSession nickname;
- server nickname remains authoritative outside the unsaved Edit Profile draft;
- show actionable error;
- re-enable Save for retry once not saving.

Generic fallback:
`保存资料失败，请重试`

Validation errors returned by the server should map to the concrete nickname error where possible.

Session expiry uses the global authentication-expired flow.

## 15. Back / unsaved state

A changed valid or invalid nickname draft is an unsaved edit.

Page-level Back behavior and discard confirmation are owned by:
the Interaction / Adaptation section in `design/pages/account_privacy/My/Edit_Profile/README.md`.

Nickname Edit itself must not auto-save on Back.

## 16. Shared component dependencies

Use the frozen shared systems:
- `background_system_v1 / BG_CONTENT`;
- `top_navigation_v1 / BACK_TITLE`;
- `action_button_v1`;
- `icon_action_v1 / CONTEXT / Clear`;
- `color_typography_v1`;
- `spacing_radius_v1`.

Do not create nickname-local forks of shared input, button, top navigation or icon styling.

## 17. Android delta from current runtime

Current runtime already has:
- server nickname as initial value;
- local draft;
- `trim()`;
- non-empty validation;
- max 20 check;
- clear action;
- Save Changes transaction;
- server-first profile update;
- session update after success.

Frozen V1 requires these corrections / clarifications:
1. minimum is **1**, not 2 or 3;
2. count/validation must align with backend Unicode semantics;
3. do not treat client-only character categories as invalid;
4. support a visible over-limit error instead of depending only on silent `.take(20)`;
5. error/success state styling must match the frozen visual and shared components;
6. nickname draft must remain intact after save failure.

## 18. Required tests

Minimum:
1. unchanged nickname → Save disabled;
2. one-character nickname → valid;
3. 20-code-point nickname → valid;
4. 21-code-point nickname → invalid;
5. whitespace-only nickname → invalid;
6. leading/trailing whitespace trims before comparison/save;
7. normalized value equal to server nickname → no effective change;
8. emoji / supplementary Unicode count matches backend semantics;
9. valid changed nickname → Save enabled;
10. clear action → 0/20 + invalid;
11. success updates UserSession and dependent UI;
12. failure preserves draft and allows retry;
13. no optimistic profile update before server success;
14. IME Done respects valid/invalid state;
15. TalkBack announces label, counter, clear action and error.

## 19. Runtime evidence gate

Before Android closure capture:
- Default;
- Focus + IME;
- valid changed nickname;
- one-character valid boundary;
- 20/20 maximum valid boundary;
- empty / whitespace error;
- over-limit error;
- Saving;
- Success;
- Failure + retained draft + retry;
- My / Home synchronization after server success.

This contract is the development authority for Nickname Edit V1.

## 20. Authority precedence

For implementation conflicts, use this order:

1. backend API contract;
2. machine contract;
3. this development / behavior contract;
4. visual authority for layout and visual-state depiction.

The approved first visual board remains the Frozen Visual Authority. Any illustrative copy inside the image that conflicts with the backend or machine contract MUST NOT override the 1–20-character validation contract.
