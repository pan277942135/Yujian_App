# Edit Profile · State & Interaction Spec V1

Status: **FROZEN**
Scope:
- former 04 · 保存与反馈
- former 05 · 异常与边界状态
- former 06 · 交互与适配规范

This authority does not create new Design Manager menus.

## 1. State model

Canonical page states:

1. `UNCHANGED`
2. `DIRTY_VALID`
3. `DIRTY_INVALID`
4. `SAVING`
5. `SUCCESS`
6. `FAILURE_RETRYABLE`
7. `FAILURE_SESSION`
8. `PARTIAL_SUCCESS`

The exact Avatar and Nickname editing rules remain owned by their dedicated authorities.

## 2. UNCHANGED

Condition:
- nickname equals persisted nickname after trim;
- no pending avatar change.

UI:
- Save disabled;
- no success/error feedback;
- current persisted avatar/profile shown.

## 3. DIRTY_VALID

Condition:
- at least one editable field differs from persisted state;
- all local validation passes.

UI:
- Save enabled;
- preserve user draft;
- previous success message clears immediately after a new edit.

## 4. DIRTY_INVALID

Examples:
- trimmed nickname empty;
- nickname exceeds 20 characters after contract-defined handling;
- selected avatar cannot be accepted locally.

UI:
- Save disabled;
- show the closest useful field/source error;
- do not discard the rest of the valid draft.

## 5. SAVING

Trigger:
- user presses Save from DIRTY_VALID.

Rules:
- one user action represents one profile-save attempt;
- prevent duplicate submit;
- Save shows loading;
- disable controls that would mutate the draft while the request is in flight;
- preserve visible draft;
- do not optimistically claim success.

Back behavior:
- do not navigate away while the save request is unresolved;
- no fake cancel unless the backend request is actually cancellable.

## 6. SUCCESS

Condition:
- every requested change in this save attempt is confirmed successful.

UI:
- update displayed persisted profile from confirmed result;
- clear pending avatar selection;
- reset dirty state;
- Save returns Disabled;
- show restrained inline success: `已保存`.

Success feedback:
- remains until the next edit or page exit;
- no celebratory animation;
- no custom sound;
- no modal success dialog.

## 7. FAILURE_RETRYABLE

Examples:
- network failure;
- temporary service error;
- upload failure;
- generic API failure that does not invalidate the session.

Rules:
- preserve unsaved draft;
- keep already-entered nickname;
- keep selected avatar draft when still readable;
- Save becomes available again when local state is valid;
- retry uses the same Save action; a separate large Retry CTA is not required.

Default page-level copy:
`保存资料失败，请重试`

More specific safe errors may replace the generic copy.

Do not:
- clear edits on failure;
- silently fall back to old values while presenting the draft as saved;
- show success before server confirmation.

## 8. PARTIAL_SUCCESS

Use only when the backend/API sequence can commit one requested profile change while another fails.

Example:
- nickname confirmed;
- avatar upload fails.

UI must reconcile truthfully:
- refresh/retain server-confirmed successful fields;
- preserve only the failed draft item for retry;
- keep page in dirty state for the failed item;
- show specific feedback such as:
  `部分内容已保存，头像更新失败，请重试`

Do not claim atomic rollback unless backend guarantees atomicity.

A future atomic profile API may remove this state by versioned contract update.

## 9. FAILURE_SESSION

Condition:
- authentication/session is rejected or expired.

Copy:
`登录状态已失效，请重新登录`

Rules:
- do not repeatedly retry with the invalid session;
- stop save loading;
- do not claim draft was saved;
- retain non-sensitive visible draft in memory until the user leaves the page;
- password/token data is never part of Edit Profile draft.

Navigation to re-authentication is owned by Runtime/Auth flow and must not be invented if unavailable.

## 10. Avatar-specific edge states

### Read / decode failure

If a newly selected local image cannot be read:
- keep persisted avatar unchanged;
- do not replace it with an empty/broken placeholder;
- show a local error;
- allow reselection.

Suggested copy:
`无法读取这张图片，请重新选择`

### Unsupported / rejected image

If format/size/content is rejected by actual picker/backend contract:
- keep persisted avatar unchanged;
- keep the page usable;
- explain the rejection without inventing unsupported numeric limits.

Suggested generic copy:
`这张图片暂不支持，请重新选择`

Exact file limits belong to the implementation/backend contract if and when frozen.

### Permission denial

Camera permission denial:
- no page failure;
- keep Photo Picker path available where supported;
- show:
  `需要相机权限才能拍照更换头像`

Do not force Settings navigation as the only path.

## 11. Nickname edge states

Canonical validation:
- trim before comparison/submission;
- length: 1–20 characters;
- empty after trim = invalid.

Save is disabled while invalid.

Do not:
- silently save an empty nickname;
- treat whitespace-only nickname as valid;
- invent additional banned-character rules unless backend/product explicitly defines them.

Backend rejection:
- display server-safe user-facing error near nickname or page feedback;
- preserve draft for correction.

## 12. Unsaved changes / Back

Dirty includes:
- changed nickname;
- pending avatar selection.

If page is dirty and not saving, TopNav Back and system Back open one confirmation dialog/sheet:

Title:
`放弃未保存的修改？`

Body:
`当前修改还没有保存。`

Actions:
- Primary/keep action: `继续编辑`
- Destructive/secondary action: `放弃修改`

Rules:
- Continue Editing closes the confirmation and preserves draft.
- Discard clears local draft and returns to the parent.
- do not show this confirmation when UNCHANGED or SUCCESS with no new edit.

## 13. Keyboard / IME

Nickname:
- single line;
- IME Done hides keyboard;
- clear action remains accessible;
- character count remains visible.

When keyboard is open:
- focused field remains visible;
- page may scroll to keep input/action reachable;
- keyboard must not permanently cover Save or error feedback;
- no horizontal scroll.

## 14. Small-screen adaptation

Use one outer vertical scroll.

Do not create nested vertical scrolling.

Rules:
- maintain horizontal page padding from shared spacing authority;
- cards expand vertically as text wraps;
- actions remain reachable;
- no fixed pixel-height card that clips large text;
- avatar row and labels must not overlap at narrow widths.

No alternate grid/table layout is required.

## 15. Safe Area

All states respect:
- status/navigation insets;
- gesture/navigation bottom inset;
- keyboard/IME inset.

Bottom content must remain reachable above system navigation.

## 16. Accessibility

Minimum intended interactive target:
- 44dp

Required semantics:
- avatar edit control has an action label;
- clear nickname button has a semantic label;
- Save exposes disabled/loading state;
- loading/success/error feedback is announced;
- errors include text, not color only;
- confirmation actions have unambiguous labels.

Font scale:
- allow wrapping/growing surfaces;
- readability overrides screenshot parity.

## 17. Motion / haptic / sound

No custom motion authority is required for these states.

Use platform/shared component transitions only.

No custom haptic or sound is required for:
- save success;
- save failure;
- validation failure.

If shared buttons provide standard pressed feedback, reuse it.

## 18. Visual authority

No new standalone high-fidelity image is required for 04–06.

Their visual behavior is defined by:
- existing Edit Profile Home / Avatar / Nickname authorities;
- Shared Design System;
- this State & Interaction Spec.

The former 04/05/06 README files remain historical indexes and point here.

## 19. Runtime parity boundary

Current Android behavior may not yet implement:
- unsaved-change confirmation;
- partial-success reconciliation;
- all accessibility/IME behavior.

Those are Runtime Parity gaps.

Do not reopen this frozen design contract merely because runtime differs.
