# Avatar Change V1 · Frozen Development Contract

Status: **FROZEN**
Scope: `编辑资料 → 02 · 头像修改`
Visual Authority: `design/pages/account_privacy/My/Edit_Profile/02_Avatar_Edit/frozen/Avatar_Change_V1_Frozen.webp`

## 1. Product contract

Avatar Change V1 is a short edit transaction inside Edit Profile:

```text
Edit Profile
→ Change Avatar
→ Source Bottom Sheet
→ Camera / Android Photo Picker
→ Crop & Scale
→ Use This Avatar
→ Return to Edit Profile as LOCAL PENDING PREVIEW
→ Save Changes
→ Upload
→ Success / Failure
```

**Frozen rule:** selecting or cropping an avatar MUST NOT upload it immediately.
Upload occurs only when the user taps **保存修改** on Edit Profile.

This keeps avatar and nickname changes in one explicit save transaction.

## 2. State ownership

### Server authority
- Existing avatar authority: authenticated profile / `GET /me`.
- Final avatar authority after save: server returned `avatar_url`.
- Local state MUST NOT replace the session avatar before server success.

### Local pending state
Until save succeeds:
- cropped avatar is a temporary local URI/file;
- Edit Profile shows it as a Pending Preview;
- `changed = true`;
- Save Changes is enabled.

On failure the Pending Preview remains available for retry.

## 3. Source Bottom Sheet

Trigger:
- tap current avatar; or
- tap `更换头像`.

Sheet:
1. `拍照`
2. `从相册选择`
3. `取消`

Rules:
- no delete-avatar action in V1;
- no avatar library;
- no AI avatar;
- no explanatory card;
- dismiss / cancel is silent and leaves state unchanged.

## 4. Gallery contract

Use Android Photo Picker where available:

`ActivityResultContracts.PickVisualMedia`

Input:
- image only;
- single selection.

Do not request broad storage permission when Photo Picker is available.

Picker cancellation:
- return to Edit Profile;
- keep current avatar;
- no error Toast.

## 5. Camera contract

Use the app/system camera contract already available in Android.

Permission:
- if CAMERA granted → launch capture;
- if not granted → request CAMERA;
- if denied → show actionable message and keep Gallery available.

Frozen copy:
`需要相机权限才能拍照更换头像`

Do not build a second custom camera stack solely for profile avatars.

## 6. Crop / scale contract

After a successful source selection, enter `调整头像`.

Output semantics:
- source output is a **1:1 square image**;
- circular appearance is a UI mask, not a circular bitmap;
- image must always cover the crop region;
- no blank edges.

Gestures:
- one-finger pan;
- two-finger pinch zoom;
- zoom range target: `1.0x–3.0x`.

V1 excludes:
- rotate;
- filters;
- beauty retouch;
- background removal;
- AI editing.

Actions:
- secondary: `重新选择`;
- primary: `使用此头像`.

`使用此头像`:
1. persist a temporary processed image;
2. return to Edit Profile;
3. show new Pending Preview;
4. enable Save Changes;
5. do **not** call avatar upload yet.

## 7. Image processing contract

Accepted input:
- JPG / JPEG;
- PNG;
- WebP;
- other Android Photo Picker images only if platform decoding succeeds.

Processed upload target:
- 1:1 square;
- maximum target dimension: 1024 × 1024;
- preserve adequate visual quality;
- do not upload the original full-resolution source when a processed avatar is available.

EXIF orientation MUST be normalized before crop output.

## 8. Save transaction

Save is owned by Edit Profile.

When Save Changes is tapped:

```text
validate nickname
→ PATCH nickname if changed
→ upload avatar if pending avatar exists
→ wait for server success
→ update UserSession/profile observable state
→ clear pending avatar
→ changed = false
```

Implementation may sequence nickname and avatar requests, but UI MUST represent one user-visible save action.

Do not show success until required server operations succeed.

## 9. Saving state

During save:
- Save Changes shows spinner + `保存中…`;
- Save button disabled against duplicate submit;
- avatar source trigger disabled;
- nickname editing disabled or submission-locked consistently;
- no percentage progress bar;
- do not navigate to a separate upload-progress page.

## 10. Success state

After server returns the new avatar:

- update global profile/session avatar from server response;
- My screen reflects the avatar immediately;
- Home profile avatar reflects the avatar immediately;
- Pending Preview becomes server-backed avatar;
- clear pending URI;
- Save Changes returns to Disabled when there are no other changes.

Feedback:
- lightweight Toast / Snackbar / inline success;
- copy: `已保存`;
- target display: about 1.5–2 s;
- no blocking success dialog.

## 11. Failure state

If avatar upload fails:
- do not update global session avatar;
- retain Pending Preview;
- return Save Changes to enabled state;
- allow direct retry without reselecting or recropping.

Frozen primary error:
`头像上传失败，请重试`

If local temporary image can no longer be read:
`无法读取这张图片，请重新选择`

Unsupported input:
`暂不支持这种图片格式`

Network/server failure:
retain pending edit and expose retry.

Session expiry:
route through the app-wide authentication-expired handling; do not invent a profile-local login flow.

## 12. Back / cancellation

Before source selection:
- Back/dismiss returns without change.

Crop page:
- Back does not commit the crop;
- previously selected server avatar remains authoritative.

After `使用此头像` returns to Edit Profile:
- the avatar is an unsaved edit;
- page-level Unsaved Changes behavior remains PARTIAL under the Interaction / Adaptation section in `design/pages/account_privacy/My/Edit_Profile/README.md`.

## 13. Accessibility

- avatar and camera trigger touch target ≥ 48dp;
- camera icon has meaningful content description;
- source rows expose role + label;
- crop actions are accessible without relying on color;
- Saving / error / success states announce semantic state changes;
- screen reader copy must distinguish Pending Preview from final server success where needed.

## 14. Shared component dependencies

Use existing frozen shared systems:
- `background_system_v1 / BG_CONTENT`;
- `top_navigation_v1 / BACK_TITLE`;
- `action_button_v1`;
- `icon_action_v1`;
- `color_typography_v1`;
- `spacing_radius_v1`.

Do not create profile-local forks of shared buttons, top navigation, icons or typography.

## 15. Android implementation delta

The existing runtime already provides:
- Android Photo Picker;
- camera permission + capture;
- avatar preview;
- server avatar upload;
- session update after success;
- failure message.

The frozen V1 contract additionally requires:
- a real crop / scale step instead of preview-only AlertDialog;
- 1:1 processed avatar output;
- Pending Preview after crop;
- upload only on Edit Profile Save Changes;
- failure retry without forcing reselection;
- shared component visual parity.

Runtime implementation is not considered closed until these deltas are evidenced.

## 16. Required tests

Minimum:
1. gallery selection → crop → pending preview;
2. camera granted → capture → crop → pending preview;
3. camera denied → actionable error;
4. picker/camera cancellation → no state change;
5. crop pan/zoom preserves 1:1 coverage;
6. new avatar enables Save Changes;
7. no upload before Save Changes;
8. save success updates session avatar;
9. upload failure preserves Pending Preview;
10. retry succeeds without reselection;
11. session-expired path uses global auth handling;
12. TalkBack labels for avatar/source/actions.

## 17. Runtime evidence gate

Before Android closure:
- source Bottom Sheet screenshot;
- crop screen screenshot;
- pending-preview Edit Profile screenshot;
- Saving screenshot;
- Success screenshot;
- Failure + retry screenshot;
- short recording of Gallery path;
- short recording of Camera path;
- evidence that My/Home avatar updates after server success.

This contract is the development authority for Avatar Change V1.
